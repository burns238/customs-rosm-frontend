/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.customs.rosmfrontend.services.registration

import org.joda.time.LocalDate
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.customs.rosmfrontend.DateConverter._
import uk.gov.hmrc.customs.rosmfrontend.connector.MatchingServiceConnector
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.Individual
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.matching._
import uk.gov.hmrc.customs.rosmfrontend.services.RequestCommonGenerator
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.mapping.RegistrationDetailsCreator
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class MatchingService @Inject()(
  matchingConnector: MatchingServiceConnector,
  requestCommonGenerator: RequestCommonGenerator,
  detailsCreator: RegistrationDetailsCreator,
  cache: SessionCache,
  requestSessionData: RequestSessionData
) {

  private val UTR = "UTR"
  private val EORI = "EORI"
  private val NINO = "NINO"
  private val CustomsIdsMap: Map[Class[_ <: CustomsId], String] =
    Map(classOf[Utr] -> UTR, classOf[Eori] -> EORI, classOf[Nino] -> NINO)

  private def convert(customsId: CustomsId, capturedDate: Option[LocalDate])(
    response: MatchingResponse
  ): RegistrationDetails =
    detailsCreator.registrationDetails(response.registerWithIDResponse, customsId, capturedDate)

  def sendOrganisationRequestForMatchingService(
    implicit request: Request[AnyContent],
    loggedInUser: LoggedInUser,
    headerCarrier: HeaderCarrier
  ): Future[Boolean] =
    for {
      subscriptionDetailsHolder <- cache.subscriptionDetails
      orgType = EtmpOrganisationType(
        requestSessionData.userSelectedOrganisationType
          .getOrElse(throw new IllegalStateException("OrganisationType number missing"))
      ).toString
      org = Organisation(subscriptionDetailsHolder.name, orgType)
      eori = subscriptionDetailsHolder.eoriNumber.getOrElse(
        throw new IllegalStateException("EORI number missing from subscription")
      )
      result <- matchBusiness(
        Eori(eori),
        org,
        subscriptionDetailsHolder.dateEstablished,
        InternalId(loggedInUser.internalId)
      )
    } yield result

  def sendIndividualRequestForMatchingService(
    implicit request: Request[AnyContent],
    loggedInUser: LoggedInUser,
    headerCarrier: HeaderCarrier
  ): Future[Boolean] =
    for {
      subscription <- cache.subscriptionDetails
      nameDob = subscription.nameDobDetails.getOrElse(
        throw new IllegalStateException("Name / DOB missing from subscription")
      )
      eori = subscription.eoriNumber.getOrElse(throw new IllegalStateException("EORI number missing from subscription"))
      individual = Individual(nameDob.firstName, None, nameDob.lastName, nameDob.dateOfBirth.toString)
      result <- matchIndividualWithId(Eori(eori), individual, InternalId(loggedInUser.internalId))
    } yield result

  def matchBusinessWithIdOnly(customsId: CustomsId, loggedInUser: LoggedInUser)(
    implicit hc: HeaderCarrier, request: Request[_]
  ): Future[Boolean] =
    for {
      maybeMatchFound <- matchingConnector.lookup(idOnlyMatchRequest(customsId, loggedInUser.isAgent))
      foundAndStored <- storeInCacheIfFound(
        convert(customsId, capturedDate = None),
        InternalId(loggedInUser.internalId)
      )(maybeMatchFound)
    } yield foundAndStored

  def matchBusinessWithIdOnly(customsId: CustomsId, loggedInUser: LoggedInUser, capturedDate: Option[LocalDate] = None)(
    implicit hc: HeaderCarrier, request: Request[_]
  ): Future[Boolean] =
    for {
      maybeMatchFound <- matchingConnector.lookup(idOnlyMatchRequest(customsId, loggedInUser.isAgent))
      foundAndStored <- storeInCacheIfFound(
        convert(customsId, capturedDate = capturedDate),
        InternalId(loggedInUser.internalId)
      )(maybeMatchFound)
    } yield foundAndStored

  def matchBusiness(
    customsId: CustomsId,
    org: Organisation,
    establishmentDate: Option[LocalDate],
    internalId: InternalId
  )(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = {
    def stripKFromUtr: CustomsId => CustomsId = {
      case Utr(id) => Utr(id.stripSuffix("k").stripSuffix("K"))
      case other   => other
    }

    val orgWithCode = org.copy(organisationType = EtmpOrganisationType.orgTypeToEtmpOrgCode(org.organisationType))

    matchingConnector
      .lookup(idAndNameMatchRequest(stripKFromUtr(customsId), orgWithCode))
      .flatMap(
        storeInCacheIfFound(
          convert(customsId, establishmentDate),
          internalId,
          requestSessionData.userSelectedOrganisationType
        )
      )
  }

  def matchIndividualWithId(customsId: CustomsId, individual: Individual, internalId: InternalId)(
    implicit hc: HeaderCarrier, request: Request[_]
  ): Future[Boolean] =
    matchingConnector
      .lookup(individualIdMatchRequest(customsId, individual))
      .flatMap(storeInCacheIfFound(convert(customsId, toLocalDate(individual.dateOfBirth)), internalId))

  def matchIndividualWithNino(nino: String, individual: Individual, internalId: InternalId)(
    implicit hc: HeaderCarrier, request: Request[_]
  ): Future[Boolean] =
    matchingConnector
      .lookup(individualNinoMatchRequest(nino, individual))
      .flatMap(
        storeInCacheIfFound(
          convert(customsId = Nino(nino), capturedDate = toLocalDate(individual.dateOfBirth)),
          internalId
        )
      )

  private def storeInCacheIfFound(
    convert: MatchingResponse => RegistrationDetails,
    internalId: InternalId,
    orgType: Option[CdsOrganisationType] = None
  )(mayBeMatchSuccess: Option[MatchingResponse])(implicit hc: HeaderCarrier, request: Request[_]): Future[Boolean] =
    mayBeMatchSuccess.map(convert).fold(Future.successful(false)) { details =>
      cache.saveRegistrationDetails(details, internalId, orgType)
    }

  private def idOnlyMatchRequest(customsId: CustomsId, isAnAgent: Boolean): MatchingRequestHolder =
    MatchingRequestHolder(
      MatchingRequest(
        requestCommonGenerator.generate(),
        RequestDetail(nameOfCustomsIdType(customsId), customsId.id, requiresNameMatch = false, isAnAgent)
      )
    )

  private def idAndNameMatchRequest(customsId: CustomsId, org: Organisation): MatchingRequestHolder =
    MatchingRequestHolder(
      MatchingRequest(
        requestCommonGenerator.generate(),
        RequestDetail(
          nameOfCustomsIdType(customsId),
          customsId.id,
          requiresNameMatch = true,
          isAnAgent = false,
          Some(org)
        )
      )
    )

  private def nameOfCustomsIdType(customsId: CustomsId): String =
    CustomsIdsMap.getOrElse(customsId.getClass, throw new IllegalArgumentException(s"Invalid matching id $customsId"))

  private def individualIdMatchRequest(customsId: CustomsId, individual: Individual): MatchingRequestHolder =
    MatchingRequestHolder(
      MatchingRequest(
        requestCommonGenerator.generate(),
        RequestDetail(
          nameOfCustomsIdType(customsId),
          customsId.id,
          requiresNameMatch = true,
          isAnAgent = false,
          individual = Some(individual)
        )
      )
    )

  private def individualNinoMatchRequest(nino: String, individual: Individual): MatchingRequestHolder =
    MatchingRequestHolder(
      MatchingRequest(
        requestCommonGenerator.generate(),
        RequestDetail(NINO, nino, requiresNameMatch = true, isAnAgent = false, individual = Some(individual))
      )
    )
}

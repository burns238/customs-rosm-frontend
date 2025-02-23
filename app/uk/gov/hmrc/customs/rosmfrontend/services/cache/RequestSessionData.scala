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

package uk.gov.hmrc.customs.rosmfrontend.services.cache

import play.api.mvc.{AnyContent, Request, Session}
import uk.gov.hmrc.customs.rosmfrontend.domain.CdsOrganisationType
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.SubscriptionFlow

import javax.inject.Singleton

@Singleton
class RequestSessionData {
  def uriBeforeSubscriptionFlow(implicit request: Request[AnyContent]): Option[String] =
    request.session.data.get(RequestSessionDataKeys.uriBeforeSubscriptionFlow)

  def storeUserSubscriptionFlow(subscriptionFlow: SubscriptionFlow, uriBeforeSubscriptionFlow: String)(
    implicit request: Request[AnyContent]
  ): Session =
    request.session + (RequestSessionDataKeys.subscriptionFlow -> subscriptionFlow.name) +
      (RequestSessionDataKeys.uriBeforeSubscriptionFlow -> uriBeforeSubscriptionFlow)

  def userSubscriptionFlow(implicit request: Request[AnyContent]): SubscriptionFlow =
    request.session.data.get(RequestSessionDataKeys.subscriptionFlow) match {
      case Some(flowName) => SubscriptionFlow(flowName)
      case None           => throw new IllegalStateException("Subscription flow is not cached")
    }

  def userSelectedOrganisationType(implicit request: Request[AnyContent]): Option[CdsOrganisationType] =
    request.session.data.get(RequestSessionDataKeys.selectedOrganisationType).map(CdsOrganisationType.forId)

  def mayBeUnMatchedUser(implicit request: Request[AnyContent]): Option[String] =
    request.session.data.get(RequestSessionDataKeys.unmatchedUser)

  def sessionWithOrganisationTypeAdded(existingSession: Session, organisationType: CdsOrganisationType): Session =
    existingSession + (RequestSessionDataKeys.selectedOrganisationType -> organisationType.id)

  def sessionWithOrganisationTypeAdded(
    organisationType: CdsOrganisationType
  )(implicit request: Request[AnyContent]): Session =
    sessionWithOrganisationTypeAdded(request.session, organisationType)

  def sessionWithoutOrganisationType(implicit request: Request[AnyContent]): Session =
    request.session - RequestSessionDataKeys.selectedOrganisationType

  def sessionForStartAgain(implicit request: Request[AnyContent]): Session =
    request.session -
      RequestSessionDataKeys.subscriptionFlow -
      RequestSessionDataKeys.selectedOrganisationType -
      RequestSessionDataKeys.uriBeforeSubscriptionFlow

  def selectedUserLocation(implicit request: Request[AnyContent]): Option[String] = {
    val userLocation = request.session.data.get(RequestSessionDataKeys.selectedUserLocation)

    userLocation match {
      case Some("islands") => Some("third-country")
      case Some("eu")      => Some("third-country")
      case _               => userLocation
    }
  }

  def selectedUserLocationWithIslands(implicit request: Request[AnyContent]): Option[String] =
    request.session.data.get(RequestSessionDataKeys.selectedUserLocation)

  def sessionWithUserLocationAdded(userLocation: String)(implicit request: Request[AnyContent]): Session =
    request.session + (RequestSessionDataKeys.selectedUserLocation -> userLocation)

  def existingSessionWithUserLocationAdded(existingSession: Session, userLocation: String)(
    implicit request: Request[AnyContent]
  ): Session =
    existingSession + (RequestSessionDataKeys.selectedUserLocation -> userLocation)

  def sessionWithUnMatchedUser(unmatched: Boolean = false)(implicit request: Request[AnyContent]): Session =
    request.session + (RequestSessionDataKeys.unmatchedUser -> unmatched.toString)

  def isPartnership(implicit request: Request[AnyContent]) = userSelectedOrganisationType.fold(false) { oType =>
    oType == CdsOrganisationType.Partnership || oType == CdsOrganisationType.LimitedLiabilityPartnership
  }

  def isCompany(implicit request: Request[AnyContent]) = userSelectedOrganisationType.fold(false) { oType =>
    oType == CdsOrganisationType.Company
  }

  def isIndividualOrSoleTrader(implicit request: Request[AnyContent]) =
    userSelectedOrganisationType.fold(false) { oType =>
      oType == CdsOrganisationType.Individual ||
      oType == CdsOrganisationType.SoleTrader ||
      oType == CdsOrganisationType.ThirdCountryIndividual ||
      oType == CdsOrganisationType.ThirdCountrySoleTrader
    }
}

object RequestSessionDataKeys {
  val selectedOrganisationType = "selected-organisation-type"
  val selectedUserLocation = "selected-user-location"
  val subscriptionFlow = "subscription-flow"
  val uriBeforeSubscriptionFlow = "uri-before-subscription-flow"
  val unmatchedUser = "unmatched-user"
}

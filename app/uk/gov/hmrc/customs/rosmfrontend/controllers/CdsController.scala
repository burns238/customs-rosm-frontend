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

package uk.gov.hmrc.customs.rosmfrontend.controllers

import play.api.Application
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{affinityGroup, allEnrolments, internalId, email => ggEmail, _}
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.customs.rosmfrontend.controllers.auth.{AccessController, AuthRedirectSupport, EnrolmentExtractor}
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

abstract class CdsController(mcc: MessagesControllerComponents)(implicit ec: ExecutionContext) extends FrontendController(mcc) with I18nSupport with AuthorisedFunctions with AuthRedirectSupport
  with EnrolmentExtractor with AccessController {

  def authConnector: AuthConnector

  def currentApp: Application

  override def messagesApi: MessagesApi =
    currentApp.injector.instanceOf[MessagesApi]

  private type RequestProcessorSimple =
    Request[AnyContent] => LoggedInUserWithEnrolments => Future[Result]
  private type RequestProcessorExtended =
    Request[AnyContent] => Option[String] => LoggedInUserWithEnrolments => Future[Result]

  private val baseRetrievals = ggEmail and credentialRole and affinityGroup
  private val extendedRetrievals = baseRetrievals and internalId and allEnrolments and groupIdentifier

  def ggAuthorisedUserWithEnrolmentsAction(requestProcessor: RequestProcessorSimple): Action[AnyContent] =
    Action.async { implicit request =>
      authorised(AuthProviders(GovernmentGateway))
        .retrieve(extendedRetrievals) {
          case currentUserEmail ~ userCredentialRole ~ userAffinityGroup ~ userInternalId ~ userAllEnrolments ~ groupId =>
            transformRequest(
              Right(requestProcessor),
              userAffinityGroup,
              userInternalId,
              userAllEnrolments,
              currentUserEmail,
              userCredentialRole,
              groupId
            )
        } recover withAuthRecovery(request)
    }

  private def transformRequest(
    requestProcessor: Either[RequestProcessorExtended, RequestProcessorSimple],
    userAffinityGroup: Option[AffinityGroup],
    userInternalId: Option[String],
    userAllEnrolments: Enrolments,
    currentUserEmail: Option[String],
    userCredentialRole: Option[CredentialRole],
    groupId: Option[String]
  )(implicit request: Request[AnyContent]) = {
    val cdsLoggedInUser = LoggedInUserWithEnrolments(userAffinityGroup, userInternalId, userAllEnrolments, currentUserEmail, groupId)
    permitUserOrRedirect(cdsLoggedInUser, userAffinityGroup, userCredentialRole, currentUserEmail)(
      requestProcessor.fold(req => req.apply(request)(userInternalId)(cdsLoggedInUser), req => req.apply(request)(cdsLoggedInUser)) //permitted 'Action' which is executed if all checks pass
    )
  }

}

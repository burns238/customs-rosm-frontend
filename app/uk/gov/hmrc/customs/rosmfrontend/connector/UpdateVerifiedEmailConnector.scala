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

package uk.gov.hmrc.customs.rosmfrontend.connector

import play.mvc.Http.Status._
import uk.gov.hmrc.customs.rosmfrontend.audit.Auditable
import uk.gov.hmrc.customs.rosmfrontend.config.AppConfig
import uk.gov.hmrc.customs.rosmfrontend.connector.httpparsers._
import uk.gov.hmrc.customs.rosmfrontend.logging.CdsLogger
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{ForbiddenException, HttpClient, _}

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

class UpdateVerifiedEmailConnector @Inject()(appConfig: AppConfig, http: HttpClient, audit: Auditable) {

  private val url: String = appConfig.getServiceUrl("update-verified-email")

  def updateVerifiedEmail(request: VerifiedEmailRequest, currentEmail: Option[String])(
    implicit hc: HeaderCarrier
  ): Future[Either[HttpErrorResponse, VerifiedEmailResponse]] = {
    val newEmail = request.updateVerifiedEmailRequest.requestDetail.emailAddress
    val eori = request.updateVerifiedEmailRequest.requestDetail.IDNumber

    auditRequest(currentEmail, newEmail, eori, "changeEmailAddressVerified")
    http.PUT[VerifiedEmailRequest, VerifiedEmailResponse](url, request) map { resp =>
      auditRequest(currentEmail, newEmail, eori, "changeEmailAddressConfirmed")
      Right(resp)
    } recover {
      case _: BadRequestException | Upstream4xxResponse(_, BAD_REQUEST, _, _) => Left(BadRequest)
      case _: ForbiddenException | Upstream4xxResponse(_, FORBIDDEN, _, _)    => Left(Forbidden)
      case _: InternalServerException | Upstream5xxResponse(_, _, _, _) =>
        Left(ServiceUnavailable)
      case NonFatal(e) =>
        CdsLogger.error(
          s"[UpdateVerifiedEmailConnector][updateVerifiedEmail] update-verified-email. url: $url, error: ${e.getMessage}"
        )
        Left(UnhandledException)
    }
  }

  private def auditRequest(currentEmail: Option[String], newEmail: String, eoriNumber: String, auditType: String)(
    implicit hc: HeaderCarrier
  ): Unit =
    currentEmail.fold(
      audit.sendDataEvent(
        transactionName = "UpdateVerifiedEmailRequestSubmitted",
        path = url,
        detail = Map("newEmailAddress" -> newEmail, "eori" -> eoriNumber),
        eventType = auditType
      )
    )(
      emailAddress =>
        audit.sendDataEvent(
          transactionName = "UpdateVerifiedEmailRequestSubmitted",
          path = url,
          detail = Map("currentEmailAddress" -> emailAddress, "newEmailAddress" -> newEmail, "eori" -> eoriNumber),
          eventType = auditType
      )
    )
}

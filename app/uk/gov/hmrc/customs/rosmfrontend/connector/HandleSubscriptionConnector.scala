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

import play.api.http.HeaderNames._
import play.mvc.Http.MimeTypes
import play.mvc.Http.Status.{NO_CONTENT, OK}
import uk.gov.hmrc.customs.rosmfrontend.config.AppConfig
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.subscription.HandleSubscriptionRequest
import uk.gov.hmrc.customs.rosmfrontend.logging.CdsLogger
import uk.gov.hmrc.http.HeaderNames.explicitlyIncludedHeaders
import uk.gov.hmrc.http.{HttpClient, _}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class HandleSubscriptionConnector @Inject()(http: HttpClient, appConfig: AppConfig) {

  val LoggerComponentId = "HandleSubscriptionConnector"

  def call(request: HandleSubscriptionRequest)(implicit hc: HeaderCarrier): Future[Unit] = {
    val url = s"${appConfig.handleSubscriptionBaseUrl}/${appConfig.handleSubscriptionServiceContext}"
    CdsLogger.info(s"[$LoggerComponentId][call] postUrl: $url")
    val headers = Seq(ACCEPT -> "application/vnd.hmrc.1.0+json", CONTENT_TYPE -> MimeTypes.JSON)
    http.POST[HandleSubscriptionRequest, HttpResponse](url, request, headers) map { response =>
      response.status match {
        case OK | NO_CONTENT => {
          CdsLogger.info(
            s"[$LoggerComponentId][call] complete for call to $url. Status:${response.status}"
          )
          ()
        }
        case _ => throw new BadRequestException(s"Status:${response.status}")
      }
    } recoverWith {
      case e: BadRequestException =>
        CdsLogger.error(
          s"[$LoggerComponentId][call] request failed with BAD_REQUEST status for call to $url: ${e.getMessage}",
          e
        )
        Future.failed(e)
      case NonFatal(e) =>
        CdsLogger.error(
          s"[$LoggerComponentId][call] request failed for call to $url: ${e.getMessage}",
          e
        )
        Future.failed(e)
    }
  }
}

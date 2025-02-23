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

package uk.gov.hmrc.customs.rosmfrontend.controllers.subscription

import play.api.Application
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, PathBindable}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class CacheController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  sessionCache: SessionCache,
  mcc: MessagesControllerComponents,
  requestSessionData: RequestSessionData
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def clearCache(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      {
        sessionCache.saveSubscriptionDetails(SubscriptionDetails()).map { _ =>
          Redirect("/customs/" + implicitly[PathBindable[Journey.Value]].unbind("journey", journey))
            .withSession(requestSessionData.sessionForStartAgain)
        }
      }
  }
}

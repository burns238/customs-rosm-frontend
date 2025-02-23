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

package unit.allowlist

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.YouNeedADifferentServiceController
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.you_need_different_service
import util.ControllerSpec
import util.builders.{AuthBuilder, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global

class AllowlistVerificationWithEnrolmentsWithFeatureOffSpec extends ControllerSpec {
  implicit override lazy val app: Application = new GuiceApplicationBuilder()
    .disable[com.kenshoo.play.metrics.PlayModule]
    .configure("metrics.enabled" -> false)
    .configure(Map("allowlistEnabled" -> false, "allowlist" -> "  mister_allow@example.com, bob@example.com"))
    .build()

  private val auth = mock[AuthConnector]

  private val youNeedDifferentServiceView = app.injector.instanceOf[you_need_different_service]

  private val controller = new YouNeedADifferentServiceController(app, auth, youNeedDifferentServiceView, mcc)

  "Allowlist verification" should {

    "return OK (200) when a non-allowlisted user attempts to access a route and the feature is OFF" in {
      AuthBuilder.withAuthorisedUser(defaultUserId, auth, userEmail = Some("not@example.com"))

      val result = controller
        .form(Journey.Migrate)
        .apply(SessionBuilder.buildRequestWithSessionAndPath("/customs/subscribe-for-cds/", defaultUserId))

      status(result) shouldBe OK
    }

    "return OK (200) when a allowlisted user attempts to access a route and the feature is OFF" in {
      AuthBuilder.withAuthorisedUser(defaultUserId, auth, userEmail = Some("mister_allow@example.com"))

      val result = controller
        .form(Journey.Migrate)
        .apply(SessionBuilder.buildRequestWithSessionAndPath("/customs/subscribe-for-cds/", defaultUserId))

      status(result) shouldBe OK
    }
  }
}

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

package unit.controllers

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.customs.rosmfrontend.controllers.FeatureFlags
import util.ControllerSpec

class FeatureFlagsSpec extends ControllerSpec {

  "FeatureFlags" should {
    "retrieve values for feature flags from application conf" in {
      val testFeatureFlags = new FeatureFlags {
        override def currentApp: Application =
          new GuiceApplicationBuilder()
            .configure(Map("features.matchingEnabled" -> true, "features.rowHaveUtrEnabled" -> true))
            .build()
      }

      testFeatureFlags.matchingEnabled shouldBe true
      testFeatureFlags.rowHaveUtrEnabled shouldBe true
    }
  }
}

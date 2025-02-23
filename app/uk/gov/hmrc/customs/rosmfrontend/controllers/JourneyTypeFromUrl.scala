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

import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey

trait JourneyTypeFromUrl {
  def journeyTypeFromUrl(implicit request: Request[AnyContent]): String =
    "(?<=/customs/)(.*?)(?=/)".r.findFirstIn(request.path).getOrElse("")

  def journeyFromUrl(implicit request: Request[AnyContent]): Journey.Value = journeyTypeFromUrl match {
    case "register-for-cds" => Journey.GetYourEORI
    case _                  => Journey.Migrate
  }
}

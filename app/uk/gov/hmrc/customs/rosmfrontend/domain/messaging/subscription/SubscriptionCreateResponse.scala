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

package uk.gov.hmrc.customs.rosmfrontend.domain.messaging.subscription

import org.joda.time.DateTime
import play.api.libs.json.Json
import uk.gov.hmrc.customs.rosmfrontend.domain.CaseClassAuditHelper
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.{CommonHeader, MessagingServiceParam}

case class MDGResponseCommon(
  status: String,
  processingDate: DateTime,
  returnParameters: List[MessagingServiceParam],
  statusText: Option[String] = None
) extends CaseClassAuditHelper {
  val ignoredFields = List("returnParameters")

  def keyValueMap(): Map[String, String] = {
    val m = toMap(this, ignoredFields = ignoredFields)
    val rd = convertToMap(returnParameters.map(_.keyValueMap()))
    m ++ rd
  }

}

object MDGResponseCommon extends CommonHeader {
  implicit val formats = Json.format[MDGResponseCommon]
}

case class SubscriptionCreateResponse(responseCommon: MDGResponseCommon, responseDetail: Option[ResponseDetail])
    extends CaseClassAuditHelper {
  def keyValueMap(): Map[String, String] = {
    val m = responseCommon.keyValueMap()
    val rd = responseDetail.fold(Map.empty[String, String])(_.toMap())
    m ++ rd
  }
}

object SubscriptionCreateResponse {
  implicit val jsonFormat = Json.format[SubscriptionCreateResponse]
  val EoriAlreadyExists = "069 - EORI already exists for the VAT Number"
  val SubscriptionInProgress = "068 - Subscription already in-progress or active"
  val EoriAlreadyAssociated = "070 - There is another EORI already associated to this business partner"
  val RequestNotProcessed = "003 - Request could not be processed"
}

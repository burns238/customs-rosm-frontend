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

package uk.gov.hmrc.customs.rosmfrontend.domain.subscription

import org.joda.time.LocalDate
import play.api.libs.json.{Format, Json}
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.{AddressViewModel, ContactDetailsModel, VatDetails, VatEUDetailsModel}

case class SubscriptionDetails(
  businessShortName: Option[BusinessShortName] = None,
  dateEstablished: Option[LocalDate] = None,
  vatRegisteredUk: Option[Boolean] = None,
  vatGroup: Option[Boolean] = None,
  ukVatDetails: Option[VatDetails] = None,
  vatRegisteredEu: Option[Boolean] = None,
  vatEUDetails: Seq[VatEUDetailsModel] = Nil,
  personalDataDisclosureConsent: Option[Boolean] = None,
  contactDetails: Option[ContactDetailsModel] = None,
  dateOfBirth: Option[LocalDate] = None,
  sicCode: Option[String] = None,
  eoriNumber: Option[String] = None,
  existingEoriNumber: Option[String] = None,
  email: Option[String] = None,
  addressDetails: Option[AddressViewModel] = None,
  nameIdOrganisationDetails: Option[NameIdOrganisationMatchModel] = None,
  nameOrganisationDetails: Option[NameOrganisationMatchModel] = None,
  nameDobDetails: Option[NameDobMatchModel] = None,
  nameDetails: Option[NameMatchModel] = None,
  idDetails: Option[IdMatchModel] = None,
  customsId: Option[CustomsId] = None
) {

  def name: String =
    nameIdOrganisationDetails.map(_.name) orElse nameOrganisationDetails.map(_.name) orElse nameDobDetails.map(_.name) orElse nameDetails
      .map(_.name) getOrElse (throw new IllegalArgumentException("Name is missing"))

  def vatIdentificationList: List[VatIdentification] =
    vatEUDetails.map(x => VatIdentification(countryCode = x.vatCountry, number = x.vatNumber)).toList
}

object SubscriptionDetails {
  val EuVatDetailsLimit = 5

  implicit val format: Format[SubscriptionDetails] = Json.format[SubscriptionDetails]
}

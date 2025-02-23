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

package util.builders

import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.{NonUKIdentification, _}
import org.joda.time.DateTime

object RegistrationInfoResponseHolderBuilder {
  val ProcessingDate = new DateTime().withDate(2016, 3, 17).withTime(9, 31, 5, 0)
  val defaultTaxPayerId = "1111111111"
  val defaultTaxPayerIdPadded = "1111111111" + "0" * 32

  val address = RegistrationInfoAddress(
    addressLine1 = "Line 1",
    addressLine2 = Some("line 2"),
    addressLine3 = Some("line 3"),
    addressLine4 = Some("line 4"),
    postalCode = Some("SW1A 2BQ"),
    countryCode = "ZZ"
  )
  val contactDetails = RegistrationInfoContactDetails(
    phoneNumber = Some("01632961234"),
    mobileNumber = None,
    faxNumber = None,
    emailAddress = Some("john.doe@example.com")
  )

  val AnIndividual = Some(
    RegistrationInfoIndividual(
      firstName = "John",
      middleName = None,
      lastName = "Doe",
      dateOfBirth = Some("1989-09-21")
    )
  )
  val AnOrganisation = Some(
    RegistrationInfoOrganisation(
      organisationName = "organisationName",
      isAGroup = false,
      organisationType = Some("Partnership"),
      None
    )
  )

  val IndividualRegistrationInfoResponseHolder = registrationInfoResponseHolder(AnIndividual, None)
  val OrganisationRegistrationInfoResponseHolder = registrationInfoResponseHolder(None, AnOrganisation)

  val OrganisationRegistrationMandatoryOnlyInfoResponseHolder = registrationInfoResponseHolder(
    optionalIndividual = None,
    optionalOrganisation = Some(
      RegistrationInfoOrganisation(
        organisationName = "organisationName",
        isAGroup = false,
        organisationType = None,
        code = None
      )
    ),
    address = RegistrationInfoAddress(
      addressLine1 = "addressLine1",
      addressLine2 = None,
      addressLine3 = None,
      addressLine4 = None,
      postalCode = None,
      countryCode = "ZZ"
    ),
    contact =
      RegistrationInfoContactDetails(phoneNumber = None, mobileNumber = None, faxNumber = None, emailAddress = None)
  )

  val IndividualRegistrationMandatoryOnlyInfoResponseHolder = registrationInfoResponseHolder(
    optionalIndividual =
      Some(RegistrationInfoIndividual(firstName = "John", middleName = None, lastName = "Doe", dateOfBirth = None)),
    optionalOrganisation = None,
    address = RegistrationInfoAddress(
      addressLine1 = "addressLine1",
      addressLine2 = None,
      addressLine3 = None,
      addressLine4 = None,
      postalCode = None,
      countryCode = "ZZ"
    ),
    contact =
      RegistrationInfoContactDetails(phoneNumber = None, mobileNumber = None, faxNumber = None, emailAddress = None)
  )

  def registrationInfoResponseHolder(
    optionalIndividual: Option[RegistrationInfoIndividual],
    optionalOrganisation: Option[RegistrationInfoOrganisation],
    address: RegistrationInfoAddress = address,
    contact: RegistrationInfoContactDetails = contactDetails,
    taxPayerId: String = defaultTaxPayerId
  ): RegistrationInfoResponseHolder = RegistrationInfoResponseHolder(
    RegistrationInfoResponse(
      RegistrationInfoResponseCommon("OK", ProcessingDate, taxPayerId),
      RegistrationInfoResponseDetail(
        "XY0001111111111",
        Some("123"),
        Some(NonUKIdentification(IDNumber = "123", issuingInstitution = "AAA", issuingCountryCode = "GB")),
        isEditable = true,
        isAnAgent = false,
        isAnIndividual = optionalIndividual.isDefined,
        optionalIndividual,
        optionalOrganisation,
        address,
        contact
      )
    )
  )

  def registrationInfoResponseHolderForOrganisation(taxPayerId: String): RegistrationInfoResponseHolder =
    registrationInfoResponseHolder(
      optionalIndividual = None,
      optionalOrganisation = AnOrganisation,
      taxPayerId = taxPayerId
    )
}

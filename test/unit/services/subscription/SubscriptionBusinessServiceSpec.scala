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

package unit.services.subscription

import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Request
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.{BusinessShortName, SubscriptionDetails}
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.{AddressViewModel, ContactDetailsModel}
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionBusinessService
import uk.gov.hmrc.http.HeaderCarrier
import util.UnitSpec

import scala.concurrent.Future
import scala.util.Random

class SubscriptionBusinessServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  implicit val hc: HeaderCarrier = mock[HeaderCarrier]

  private val mockCdsFrontendDataCache = mock[SessionCache]
  private val registrationInfo = mock[RegistrationInfo]
  private val mockRegistrationDetails = mock[RegistrationDetails]
  private val mockSubscriptionDetailsHolder = mock[SubscriptionDetails]
  private val mockpersonalDataDisclosureConsent = mock[Option[Boolean]]
  private val mockContactDetailsModel = mock[ContactDetailsModel]
  private val mockBusinessShortName = mock[BusinessShortName]

  private val expectedDate = LocalDate.now()
  private val maybeExpectedDate = Some(expectedDate)

  val sicCode = Some("someSicCode")

  private val subscriptionBusinessService =
    new SubscriptionBusinessService(mockCdsFrontendDataCache)
  private val eoriNumericLength = 15
  private val eoriId = "GB" + Random.nextString(eoriNumericLength)
  private val eori = Eori(eoriId)
  val maybeEoriId = Some(eoriId)
  val mayBeCachedAddressViewModel = Some(AddressViewModel("Address Line 1", "city", Some("postcode"), "GB"))
  val nameIdOrganisationDetails = Some(NameIdOrganisationMatchModel("OrgName", "ID"))
  val customsIDUTR = Some(Utr("ID"))

  val email = Some("OrgName@example.com")

  val emulatedFailure = new UnsupportedOperationException("Emulation of failure")

  override def beforeEach {
    reset(
      mockCdsFrontendDataCache,
      registrationInfo,
      mockRegistrationDetails,
      mockSubscriptionDetailsHolder
    )

    when(
      mockCdsFrontendDataCache
        .saveRegistrationDetails(ArgumentMatchers.any[RegistrationDetails])(ArgumentMatchers.any[Request[_]])
    ).thenReturn(Future.successful(true))

    when(
      mockCdsFrontendDataCache
        .saveSubscriptionDetails(ArgumentMatchers.any[SubscriptionDetails])(ArgumentMatchers.any[Request[_]])
    ).thenReturn(Future.successful(true))

    val existingHolder = SubscriptionDetails(contactDetails = Some(mock[ContactDetailsModel]))

    when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(existingHolder)
    when(mockSubscriptionDetailsHolder.personalDataDisclosureConsent).thenReturn(mockpersonalDataDisclosureConsent)
  }

  "Calling maybeCachedContactDetailsModel" should {
    "retrieve cached data if already stored in cdsFrontendCache" in {
      val contactDetailsModel = Some(mockContactDetailsModel)

      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.contactDetails).thenReturn(contactDetailsModel)
      await(subscriptionBusinessService.cachedContactDetailsModel(any[Request[_]])) shouldBe contactDetailsModel
      verify(mockCdsFrontendDataCache).subscriptionDetails(any[Request[_]])
    }

    "return None if no data has been found in the cache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.contactDetails).thenReturn(None)
      await(subscriptionBusinessService.cachedContactDetailsModel(any[Request[_]])) shouldBe None
    }
  }

  "Calling maybeCachedSicCode" should {
    "retrieve cached data if already stored in cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.sicCode).thenReturn(sicCode)
      await(subscriptionBusinessService.cachedSicCode(any[Request[_]])) shouldBe sicCode
      verify(mockCdsFrontendDataCache).subscriptionDetails(any[Request[_]])
    }

    "return None if no data has been found in the cache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.sicCode).thenReturn(None)
      await(subscriptionBusinessService.cachedSicCode(any[Request[_]])) shouldBe None
    }
  }

  "Calling getCachedCompanyShortName" should {
    "retrieve any previously cached Sic Code from the cdsFrontendCache" in {
      val shortName = BusinessShortName(Some("ABCD"))
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.businessShortName).thenReturn(Some(shortName))
      await(subscriptionBusinessService.getCachedCompanyShortName(any[Request[_]])) shouldBe shortName

    }

    "throw exception when there are no Company Short Name details in the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.businessShortName).thenReturn(None)
      val thrown = intercept[IllegalStateException] {
        await(subscriptionBusinessService.getCachedCompanyShortName(any[Request[_]]))
      }
      thrown.getMessage shouldBe "No Short Name Cached"
    }
  }

  "Calling maybeCompanyShortName" should {
    "retrieve cached short name if already stored in cdsFrontendCache" in {
      val maybeShortName = Some(mockBusinessShortName)
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.businessShortName).thenReturn(maybeShortName)
      await(subscriptionBusinessService.companyShortName(any[Request[_]])) shouldBe maybeShortName
      verify(mockCdsFrontendDataCache).subscriptionDetails(any[Request[_]])
    }

    "return None if no data has been found in the cache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.businessShortName).thenReturn(None)
      await(subscriptionBusinessService.companyShortName(any[Request[_]])) shouldBe None
    }
  }

  "Calling retrieveSubscriptionDetailsHolder" should {
    "fail when cache fails accessing current SubscriptionDetailsHolder" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(Future.failed(emulatedFailure))

      val caught = intercept[RuntimeException] {
        await(subscriptionBusinessService.retrieveSubscriptionDetailsHolder(any[Request[_]]))
      }
      caught shouldBe emulatedFailure
    }
  }

  "Calling getCachedDateEstablished" should {
    "retrieve any previously cached Date Of Establishment Details from the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.dateEstablished).thenReturn(Some(expectedDate))
      await(subscriptionBusinessService.getCachedDateEstablished(any[Request[_]])) shouldBe expectedDate

    }

    "throw exception when there are no Date Of Establishment details in the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.dateEstablished).thenReturn(None)
      val thrown = intercept[IllegalStateException] {
        await(subscriptionBusinessService.getCachedDateEstablished(any[Request[_]]))
      }
      thrown.getMessage shouldBe "No Date Of Establishment Cached"
    }
  }

  "Calling maybeCachedDateEstablished" should {
    "retrieve cached date established if already stored in cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.dateEstablished).thenReturn(maybeExpectedDate)
      await(subscriptionBusinessService.maybeCachedDateEstablished(any[Request[_]])) shouldBe maybeExpectedDate
      verify(mockCdsFrontendDataCache).subscriptionDetails(any[Request[_]])
    }

    "return None if no data has been found in the cache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.dateEstablished).thenReturn(None)
      await(subscriptionBusinessService.maybeCachedDateEstablished(any[Request[_]])) shouldBe None
    }
  }

  "Calling getCachedSicCode" should {
    "retrieve any previously cached Sic Code from the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.sicCode).thenReturn(sicCode)
      await(subscriptionBusinessService.getCachedSicCode(any[Request[_]])) shouldBe "someSicCode"

    }

    "throw exception when there are no SIC Code details in the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.sicCode).thenReturn(None)
      val thrown = intercept[IllegalStateException] {
        await(subscriptionBusinessService.getCachedSicCode(any[Request[_]]))
      }
      thrown.getMessage shouldBe "No SIC Code Cached"
    }
  }

  "Calling getCachedEoriNumber" should {
    "retrieve any previously cached Eori Number from the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.eoriNumber).thenReturn(Some(eoriId))
      await(subscriptionBusinessService.getCachedEoriNumber(any[Request[_]])) shouldBe eoriId

    }

    "throw exception when there are no Eori Number details in the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.eoriNumber).thenReturn(None)
      val thrown = intercept[IllegalStateException] {
        await(subscriptionBusinessService.getCachedEoriNumber(any[Request[_]]))
      }
      thrown.getMessage shouldBe "No Eori Number Cached"
    }
  }

  "Calling maybeCachedEoriNumber" should {
    "retrieve cached data if already stored in cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.eoriNumber).thenReturn(maybeEoriId)
      await(subscriptionBusinessService.cachedEoriNumber(any[Request[_]])) shouldBe maybeEoriId
      verify(mockCdsFrontendDataCache).subscriptionDetails(any[Request[_]])
    }

    "return None if no data has been found in the cache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.eoriNumber).thenReturn(None)
      await(subscriptionBusinessService.cachedEoriNumber(any[Request[_]])) shouldBe None
    }
  }

  "Calling getCachedPersonalDataDisclosureConsent" should {
    "retrieve any previously cached consent Details from the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.personalDataDisclosureConsent).thenReturn(Some(false))
      await(subscriptionBusinessService.getCachedPersonalDataDisclosureConsent(any[Request[_]])) shouldBe false
    }

    "throw exception when there are no consent details in the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.personalDataDisclosureConsent).thenReturn(None)
      val thrown = intercept[IllegalStateException] {
        await(subscriptionBusinessService.getCachedPersonalDataDisclosureConsent(any[Request[_]]))
      }
      thrown.getMessage shouldBe "No Personal Data Disclosure Consent Cached"
    }
  }

  "Calling mayBeCachedAddressViewModel" should {
    "retrieve cached data if already stored in cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.addressDetails).thenReturn(mayBeCachedAddressViewModel)
      await(subscriptionBusinessService.address(any[Request[_]])) shouldBe mayBeCachedAddressViewModel
      verify(mockCdsFrontendDataCache).subscriptionDetails(any[Request[_]])
    }

    "return None if no data has been found in the cache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.addressDetails).thenReturn(None)
      await(subscriptionBusinessService.address(any[Request[_]])) shouldBe None
    }
  }

  "Calling maybeCachedNameIdViewModel" should {
    "retrieve cached data if already stored in cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.nameIdOrganisationDetails).thenReturn(nameIdOrganisationDetails)
      await(subscriptionBusinessService.cachedNameIdOrganisationViewModel(any[Request[_]])) shouldBe nameIdOrganisationDetails
      verify(mockCdsFrontendDataCache).subscriptionDetails(any[Request[_]])
    }

    "return None if no data has been found in the cache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.nameIdOrganisationDetails).thenReturn(None)
      await(subscriptionBusinessService.cachedNameIdOrganisationViewModel(any[Request[_]])) shouldBe None
    }
  }

  "Calling getCachedAddressViewModel" should {
    "retrieve any previously cached Address Details from the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.addressDetails).thenReturn(mayBeCachedAddressViewModel)
      await(subscriptionBusinessService.addressOrException(any[Request[_]])) shouldBe mayBeCachedAddressViewModel.get
    }

    "throw exception when cache address details is not saved in cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.addressDetails).thenReturn(None)
      val thrown = intercept[IllegalStateException] {
        await(subscriptionBusinessService.addressOrException(any[Request[_]]))
      }
      thrown.getMessage shouldBe "No Address Details Cached"
    }
  }

  "Calling getCachedAddressViewModel" should {
    "retrieve any previously cached Named Id from the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.nameIdOrganisationDetails).thenReturn(nameIdOrganisationDetails)
      await(subscriptionBusinessService.getCachedNameIdViewModel(any[Request[_]])) shouldBe nameIdOrganisationDetails.get
    }

    "throw exception when cache Name Id is not saved in cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.nameIdOrganisationDetails).thenReturn(None)
      val thrown = intercept[IllegalStateException] {
        await(subscriptionBusinessService.getCachedNameIdViewModel(any[Request[_]]))
      }
      thrown.getMessage shouldBe "No Name/Utr/Id Details Cached"
    }
  }

  "Calling getCachedCustomsId" should {
    "retrieve any previously cached Named Id from the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.customsId).thenReturn(customsIDUTR)
      await(subscriptionBusinessService.getCachedCustomsId(any[Request[_]])) shouldBe customsIDUTR
    }

    "return None customsId is not in cache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.customsId).thenReturn(None)
      await(subscriptionBusinessService.getCachedCustomsId(any[Request[_]])) shouldBe None

    }
  }

  "Calling getCachedVatRegisteredEu" should {
    "retrieve any previously cached vat registered EU boolean from the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.vatRegisteredEu).thenReturn(Some(true))
      await(subscriptionBusinessService.getCachedVatRegisteredEu(any[Request[_]])) shouldBe true
    }

    "throw exception when there is no vat registered EU boolean in the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.vatRegisteredEu).thenReturn(None)
      val thrown = intercept[IllegalStateException] {
        await(subscriptionBusinessService.getCachedVatRegisteredEu(any[Request[_]]))
      }
      thrown.getMessage shouldBe "Whether the business is VAT registered in the EU has not been Cached"
    }
  }

  "Calling getCachedVatRegisteredUk" should {
    "retrieve any previously cached vat registered UK boolean from the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.vatRegisteredUk).thenReturn(Some(true))
      await(subscriptionBusinessService.getCachedVatRegisteredUk(any[Request[_]])) shouldBe true
    }

    "throw exception when there is no vat registered UK boolean in the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.vatRegisteredUk).thenReturn(None)
      val thrown = intercept[IllegalStateException] {
        await(subscriptionBusinessService.getCachedVatRegisteredUk(any[Request[_]]))
      }
      thrown.getMessage shouldBe "Whether the business is VAT registered in the UK has not been Cached"
    }
  }

  "Calling getCachedNameViewModel" should {
    "retrieve any previously cached organisation details from the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.nameOrganisationDetails).thenReturn(Some(NameOrganisationMatchModel("name")))
      await(subscriptionBusinessService.getCachedNameViewModel(any[Request[_]])) shouldBe NameOrganisationMatchModel("name")
    }

    "throw exception when there is no organisation details in the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.nameOrganisationDetails).thenReturn(None)
      val thrown = intercept[IllegalStateException] {
        await(subscriptionBusinessService.getCachedNameViewModel(any[Request[_]]))
      }
      thrown.getMessage shouldBe "No Name Cached"
    }
  }

  "Calling getCachedSubscriptionNameDobViewModel" should {
    "retrieve any previously cached dob details from the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.nameDobDetails)
        .thenReturn(Some(NameDobMatchModel("fname", Some("mName"), "lname", LocalDate.parse("2019-01-01"))))
      await(subscriptionBusinessService.getCachedSubscriptionNameDobViewModel(any[Request[_]])) shouldBe NameDobMatchModel(
        "fname",
        Some("mName"),
        "lname",
        LocalDate.parse("2019-01-01")
      )
    }

    "throw exception when there is no dob details in the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.nameDobDetails).thenReturn(None)
      val thrown = intercept[IllegalStateException] {
        await(subscriptionBusinessService.getCachedSubscriptionNameDobViewModel(any[Request[_]]))
      }
      thrown.getMessage shouldBe "No Name/Dob Details Cached"
    }
  }

  "Calling getCachedSubscriptionIdViewModel" should {
    "retrieve any previously cached subscription Id details from the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.idDetails).thenReturn(Some(IdMatchModel("id")))
      await(subscriptionBusinessService.getCachedSubscriptionIdViewModel(any[Request[_]])) shouldBe IdMatchModel("id")
    }

    "throw exception when there is no subscription Id details in the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.idDetails).thenReturn(None)
      val thrown = intercept[IllegalStateException] {
        await(subscriptionBusinessService.getCachedSubscriptionIdViewModel(any[Request[_]]))
      }
      thrown.getMessage shouldBe "No Nino/Id Details Cached"
    }
  }

  "Calling maybeCachedSubscriptionIdViewModel" should {
    "retrieve cached data if already stored in cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.idDetails).thenReturn(Some(IdMatchModel("id")))
      await(subscriptionBusinessService.maybeCachedSubscriptionIdViewModel(any[Request[_]])) shouldBe Some(IdMatchModel("id"))
    }

    "return None if no data has been found in the cache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[_]])).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.idDetails).thenReturn(None)
      await(subscriptionBusinessService.maybeCachedSubscriptionIdViewModel(any[Request[_]])) shouldBe None
    }
  }
}

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

package unit.controllers.address

import common.pages.matching.ConfirmPage
import common.pages.subscription.AddressPage
import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalacheck.Gen
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.customs.rosmfrontend.config.AppConfig
import uk.gov.hmrc.customs.rosmfrontend.controllers.AddressController
import uk.gov.hmrc.customs.rosmfrontend.controllers.routes.AddressController.submit
import uk.gov.hmrc.customs.rosmfrontend.domain.CdsOrganisationType.IndividualId
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.AddressDetailsSubscriptionFlowPage
import uk.gov.hmrc.customs.rosmfrontend.domain.{CdsOrganisationType, RegistrationDetails, SafeId}
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.AddressViewModel
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.countries.{AllCountriesExceptIomInCountryPicker, Countries, Country}
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.address
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.confirm_contact_details
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import unit.controllers.subscription.{SubscriptionFlowCreateModeTestSupport, SubscriptionFlowReviewModeTestSupport, SubscriptionFlowTestSupport}
import util.StringThings._
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.RegistrationDetailsBuilder._
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AddressControllerSpec
    extends SubscriptionFlowTestSupport with BeforeAndAfterEach
    with SubscriptionFlowCreateModeTestSupport with SubscriptionFlowReviewModeTestSupport {

  protected override val formId: String = AddressPage.formId

  protected override def submitInCreateModeUrl: String = submit(isInReviewMode = false, Journey.GetYourEORI).url

  protected override def submitInReviewModeUrl: String = submit(isInReviewMode = true, Journey.GetYourEORI).url

  private val mockRequestSessionData = mock[RequestSessionData]
  private val mockCdsFrontendDataCache = mock[SessionCache]
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]
  private val mockCountries = mock[Countries]
  private val emulatedFailure = new UnsupportedOperationException("Emulation of service call failure")
  private val mockOrganisationType = mock[CdsOrganisationType]
  private val mockAppConfig = mock[AppConfig]

  private val viewConfirmContectDetails = app.injector.instanceOf[confirm_contact_details]
  private val viewAddress = app.injector.instanceOf[address]

  private val controller = new AddressController(
    app,
    mockAuthConnector,
    mockSubscriptionBusinessService,
    mockCdsFrontendDataCache,
    mockSubscriptionFlowManager,
    mockRequestSessionData,
    mockSubscriptionDetailsHolderService,
    mockCountries,
    mcc,
    mockSubscriptionDetailsService,
    viewConfirmContectDetails,
    viewAddress,
    mockAppConfig
  )

  def stringOfLengthXGen(minLength: Int): Gen[String] =
    for {
      single: Char <- Gen.alphaNumChar
      baseString: String <- Gen.listOfN(minLength, Gen.alphaNumChar).map(c => c.mkString)
      additionalEnding <- Gen.alphaStr
    } yield single + baseString + additionalEnding

  val mandatoryFields = Map("city" -> "city", "street" -> "street", "postcode" -> "SW1A 2BQ", "countryCode" -> "GB")
  val mandatoryFieldsEmpty = Map("city" -> "", "street" -> "", "postcode" -> "", "countryCode" -> "")

  val aFewCountries = List(
    Country("France", "country:FR"),
    Country("Germany", "country:DE"),
    Country("Italy", "country:IT"),
    Country("Japan", "country:JP")
  )

  override def beforeEach: Unit = {
    reset(
      mockSubscriptionBusinessService,
      mockSubscriptionFlowManager,
      mockRequestSessionData,
      mockSubscriptionDetailsHolderService,
      mockSubscriptionDetailsService
    )
    when(mockSubscriptionBusinessService.address(any[Request[_]])).thenReturn(None)
    when(mockSubscriptionDetailsService.cachedCustomsId(any[Request[_]])).thenReturn(None)
    when(mockCdsFrontendDataCache.registrationDetails(any[Request[_]])).thenReturn(organisationRegistrationDetails)
    when(mockCdsFrontendDataCache.saveRegistrationDetails(any[RegistrationDetails])(any[Request[_]]))
      .thenReturn(Future.successful(true))
    when(mockRequestSessionData.mayBeUnMatchedUser(any[Request[AnyContent]])).thenReturn(None)
    when(mockRequestSessionData.selectedUserLocationWithIslands(any[Request[AnyContent]])).thenReturn(None)
    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
      .thenReturn(Some(mockOrganisationType))
    when(mockCountries.all).thenReturn(aFewCountries)
    when(mockCountries.getCountryParameters(any())).thenReturn(aFewCountries -> AllCountriesExceptIomInCountryPicker)
    when(mockAppConfig.autoCompleteEnabled).thenReturn(true)
    registerSaveDetailsMockSuccess()
    setupMockSubscriptionFlowManager(AddressDetailsSubscriptionFlowPage)
  }

  "Subscription Address Controller form in create mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(mockAuthConnector, controller.createForm(Journey.GetYourEORI))

    "display title as 'Enter your business address'" in {
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.title() should startWith("Enter your organisation address")
      }
    }

    "submit in create mode" in {
      showCreateForm()(verifyFormActionInCreateMode)
    }

    "display the back link" in {
      showCreateForm()(verifyBackLinkInCreateModeRegister)
    }

    "have Address input field without data if not cached previously" in {
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        verifyAddressFieldExistsWithNoData(page)
      }
    }

    "have Address input field prepopulated if cached previously" in {
      when(mockSubscriptionBusinessService.address(any[Request[_]]))
        .thenReturn(Future.successful(Some(AddressPage.filledValues)))
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        verifyAddressFieldExistsAndPopulatedCorrectly(page)
      }
    }

    "display the correct text for the continue button" in {
      showCreateForm()({ result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(AddressPage.continueButtonXpath) shouldBe ContinueButtonTextInCreateMode
      })
    }

  }

  "Subscription Address Controller form in create mode for Individual" should {

    "display title as 'Enter your address'" in {
      showCreateForm(userSelectedOrganisationType = Some(CdsOrganisationType.Individual)) { result =>
        val page = CdsPage(bodyOf(result))
        page.title() should startWith("Enter your address")
        page.h1() shouldBe "Enter your address"
      }
    }
  }

  "Subscription Address form in review mode for Individual" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.reviewForm(Journey.Migrate))

    "display title as 'Enter your address'" in {
      showReviewForm(userSelectedOrganisationType = Some(CdsOrganisationType.Individual)) { result =>
        val page = CdsPage(bodyOf(result))
        page.title() should startWith("Enter your address")
        page.h1() shouldBe "Enter your address"
      }
    }
  }

  "Subscription Address form in review mode" should {

    "display title as 'Enter your business address'" in {
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.title() should startWith("Enter your organisation address")
      }
    }

    "submit in review mode" in {
      showReviewForm()(verifyFormSubmitsInReviewMode)
    }

    "retrieve the cached data" in {
      showReviewForm() { result =>
        CdsPage(bodyOf(result))
        verify(mockSubscriptionBusinessService).addressOrException(any[Request[_]])
      }
    }

    "have all the required input fields without data" in {
      showReviewForm(AddressViewModel("", "", None, "")) { result =>
        val page = CdsPage(bodyOf(result))
        verifyAddressFieldExistsWithNoData(page)
      }
    }

    "display the correct text for the continue button" in {
      showReviewForm()({ result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(AddressPage.continueButtonXpath) shouldBe ContinueButtonTextInReviewMode
      })
    }
  }

  "submitting the form with all mandatory fields filled when in create mode for organisation type" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.submit(isInReviewMode = false, Journey.GetYourEORI)
    )

    "wait until the saveSubscriptionDetailsHolder is completed before progressing" in {
      registerSaveDetailsMockFailure(emulatedFailure)

      val caught = intercept[RuntimeException] {
        submitFormInCreateModeForOrganisation(mandatoryFields) { result =>
          await(result)
        }
      }

      caught shouldEqual emulatedFailure
    }

    "redirect to next screen" in {
      submitFormInCreateModeForOrganisation(mandatoryFields) { result =>
        val page = CdsPage(bodyOf(result))
        page.title should startWith(ConfirmPage.title)
      }
    }
  }

  "submitting the form with all mandatory fields filled for individual type" should {

    "redirect to next screen" in {
      submitFormInCreateModeForIndividual(mandatoryFields) { result =>
        val page = CdsPage(bodyOf(result))
        page.title should startWith("These are the details we have about you")
      }
    }
  }

  "submitting the form for Migration journey" should {

    "redirect to email address page when No Nino and No Utr is provided" in {

      submitFormInCreateModeForIndividualSubscription(
        Map("street" -> "My street", "city" -> "My city", "postcode" -> "SE281AA", "countryCode" -> "GB")
      ) { result =>
        status(result) shouldBe SEE_OTHER
      }
    }
  }

  "submitting the form for GYE journey without organisation type in the request session" should {

    "thrown an exception with 'No Etmp org type' message" in {
      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(None)

      val caught = intercept[IllegalStateException] {
        submitFormInCreateModeForOrganisation(mandatoryFields) { result =>
          await(result)
        }
      }

      caught.getMessage shouldBe "No Etmp org type"
    }
  }

  "submitting the form with all mandatory fields filled when in review mode for all organisation types" should {

    "wait until the saveSubscriptionDetailsHolder is completed before progressing" in {

      registerSaveDetailsMockFailure(emulatedFailure)
      the[UnsupportedOperationException] thrownBy {
        submitFormInReviewMode(mandatoryFields)(await)
      } should have message emulatedFailure.getMessage
    }

    "redirect to review screen" in {
      submitFormInReviewMode(mandatoryFields, userSelectedOrgType = Some(mockOrganisationType))(
        verifyRedirectToReviewPage(Journey.GetYourEORI)
      )
    }
  }

  "Address Detail form" should {
    "be mandatory" in {
      submitFormInCreateModeForOrganisation(mandatoryFieldsEmpty) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(AddressPage.pageLevelErrorSummaryListXPath) should include(
          "Enter the first line of your address"
        )
        page.getElementsText(AddressPage.pageLevelErrorSummaryListXPath) should include("Enter your town or city")
        page.getElementsText(AddressPage.pageLevelErrorSummaryListXPath) should include("Enter your country")

        page.getElementsText(AddressPage.streetFieldLevelErrorXPath) shouldBe "Error: Enter the first line of your address"
        page.getElementsText(AddressPage.cityFieldLevelErrorXPath) shouldBe "Error: Enter your town or city"
        page.getElementsText(AddressPage.countryFieldLevelErrorXPath) shouldBe "Enter your country"
      }
    }

    "have postcode mandatory when country is GB" in {
      submitFormInCreateModeForOrganisation(
        Map("street" -> "My street", "city" -> "My city", "postcode" -> "", "countryCode" -> "GB")
      ) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(AddressPage.pageLevelErrorSummaryListXPath) shouldBe "Enter a valid postcode"

        page.getElementsText(AddressPage.postcodeFieldLevelErrorXPath) shouldBe "Error: Enter a valid postcode"
      }
    }

    "have postcode mandatory when country is a channel island" in {
      submitFormInCreateModeForOrganisation(
        Map("street" -> "My street", "city" -> "My city", "postcode" -> "", "countryCode" -> "JE")
      ) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(AddressPage.pageLevelErrorSummaryListXPath) shouldBe "Enter a valid postcode"
        page.getElementsText(AddressPage.postcodeFieldLevelErrorXPath) shouldBe "Error: Enter a valid postcode"
      }
    }

    "not allow spaces to satisfy minimum length requirements" in {
      submitFormInCreateModeForOrganisation(
        Map(
          "city" -> 10.spaces,
          "street" -> 7.spaces // we allow spaces for postcode
        )
      ) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(AddressPage.pageLevelErrorSummaryListXPath) should include(
          "Enter the first line of your address"
        )
        page.getElementsText(AddressPage.pageLevelErrorSummaryListXPath) should include("Enter your town or city")

        page.getElementsText(AddressPage.streetFieldLevelErrorXPath) shouldBe "Error: Enter the first line of your address"
        page.getElementsText(AddressPage.cityFieldLevelErrorXPath) shouldBe "Error: Enter your town or city"
      }
    }

    "be restricted to 70 character for street validation only" in {
      val streetLine = stringOfLengthXGen(70)
      submitFormInCreateModeForOrganisation(mandatoryFields ++ Map("street" -> streetLine.sample.get)) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(AddressPage.streetFieldLevelErrorXPath) shouldBe "Error: The first line of the address must be 70 characters or less"
      }
    }

    "be restricted to 35 character for city validation only" in {
      val city = stringOfLengthXGen(35)
      submitFormInCreateModeForOrganisation(mandatoryFields ++ Map("city" -> city.sample.get)) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(AddressPage.cityFieldLevelErrorXPath) shouldBe "Error: The town or city must be 35 characters or less"

      }
    }

    "be validating postcode length to 8 when country is a channel island" in {
      submitFormInCreateModeForOrganisation(
        Map("street" -> "My street", "city" -> "City", "postcode" -> "123456789", "countryCode" -> "JE")
      ) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(AddressPage.pageLevelErrorSummaryListXPath) shouldBe "Enter a valid postcode"
        page.getElementsText(AddressPage.postcodeFieldLevelErrorXPath) shouldBe "Error: Enter a valid postcode"
      }
    }

    "be validating postcode length to 9 when country is not GB" in {
      submitFormInCreateModeForOrganisation(
        Map("street" -> "My street", "city" -> "City", "postcode" -> "1234567890", "countryCode" -> "FR")
      ) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(AddressPage.pageLevelErrorSummaryListXPath) shouldBe "The postcode must be 9 characters or less"
        page.getElementsText(AddressPage.postcodeFieldLevelErrorXPath) shouldBe "Error: The postcode must be 9 characters or less"
      }
    }
  }

  private def submitFormInCreateModeForOrganisation(
    form: Map[String, String],
    userId: String = defaultUserId,
    userSelectedOrgType: Option[CdsOrganisationType] = None
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockCdsFrontendDataCache.registrationDetails(any[Request[_]])).thenReturn(organisationRegistrationDetails)

    test(
      controller.submit(isInReviewMode = false, Journey.GetYourEORI)(
        SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
      )
    )
  }

  private def submitFormInCreateModeForIndividualSubscription(
    form: Map[String, String],
    userId: String = defaultUserId,
    userSelectedOrgType: Option[CdsOrganisationType] = None
  )(test: Future[Result] => Any) {
    val individualRegistrationDetails = RegistrationDetails.individual(
      sapNumber = "0123456789",
      safeId = SafeId("safe-id"),
      name = "John Doe",
      address = defaultAddress,
      dateOfBirth = LocalDate.parse("1980-07-23"),
      customsId = None
    )

    withAuthorisedUser(userId, mockAuthConnector)
    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
      .thenReturn(Some(CdsOrganisationType("individual")))
    when(mockCdsFrontendDataCache.registrationDetails(any[Request[_]])).thenReturn(individualRegistrationDetails)
    when(mockSubscriptionDetailsService.cachedCustomsId(any[Request[_]])).thenReturn(None)

    test(
      controller.submit(isInReviewMode = false, Journey.Migrate)(
        SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
      )
    )
  }

  private def submitFormInCreateModeForIndividual(
    form: Map[String, String],
    userId: String = defaultUserId,
    userSelectedOrgType: Option[CdsOrganisationType] = None
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockCdsFrontendDataCache.registrationDetails(any[Request[_]])).thenReturn(individualRegistrationDetails)

    test(
      controller.submit(isInReviewMode = false, Journey.GetYourEORI)(
        SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
      )
    )
  }

  private def submitFormInReviewMode(
    form: Map[String, String],
    userId: String = defaultUserId,
    userSelectedOrgType: Option[CdsOrganisationType] = None
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
      .thenReturn(Some(CdsOrganisationType("company")))

    test(
      controller.submit(isInReviewMode = true, Journey.GetYourEORI)(
        SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
      )
    )
  }

  private def registerSaveDetailsMockSuccess() {
    when(mockSubscriptionDetailsHolderService.cacheAddressDetails(any())(any[Request[_]]))
      .thenReturn(Future.successful(()))
  }

  private def registerSaveDetailsMockFailure(exception: Throwable) {
    when(mockSubscriptionDetailsHolderService.cacheAddressDetails(any())(any[Request[_]]))
      .thenReturn(Future.failed(exception))
  }

  private def showCreateForm(
    userId: String = defaultUserId,
    userSelectedOrganisationType: Option[CdsOrganisationType] = None
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
      .thenReturn(userSelectedOrganisationType)

    if (userSelectedOrganisationType.contains(CdsOrganisationType(IndividualId))) {
      when(mockRequestSessionData.isIndividualOrSoleTrader(any[Request[AnyContent]]))
        .thenReturn(true)
    } else {
      when(mockRequestSessionData.isIndividualOrSoleTrader(any[Request[AnyContent]]))
        .thenReturn(false)
    }
    when(mockCdsFrontendDataCache.registrationDetails(any[Request[_]])).thenReturn(organisationRegistrationDetails)

    test(controller.createForm(Journey.GetYourEORI).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def showReviewForm(
    dataToEdit: AddressViewModel = AddressPage.filledValues,
    userId: String = defaultUserId,
    userSelectedOrganisationType: Option[CdsOrganisationType] = None
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
      .thenReturn(userSelectedOrganisationType)

    if (userSelectedOrganisationType.contains(CdsOrganisationType(IndividualId))) {
      when(mockRequestSessionData.isIndividualOrSoleTrader(any[Request[AnyContent]]))
        .thenReturn(true)
    } else {
      when(mockRequestSessionData.isIndividualOrSoleTrader(any[Request[AnyContent]]))
        .thenReturn(false)
    }

    when(mockSubscriptionBusinessService.addressOrException(any[Request[_]])).thenReturn(dataToEdit)
    when(mockCdsFrontendDataCache.registrationDetails(any[Request[_]])).thenReturn(individualRegistrationDetails)

    test(controller.reviewForm(Journey.GetYourEORI).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def verifyAddressFieldExistsAndPopulatedCorrectly(page: CdsPage): Unit = {
    page.getElementValue(AddressPage.cityFieldXPath) shouldBe "city name"
    page.getElementValue(AddressPage.streetFieldXPath) shouldBe "Line 1"
    page.getElementValue(AddressPage.postcodeFieldXPath) shouldBe "SW1A 2BQ"
  }

  private def verifyAddressFieldExistsWithNoData(page: CdsPage): Unit = {
    page.getElementValue(AddressPage.cityFieldXPath) shouldBe empty
    page.getElementValue(AddressPage.streetFieldXPath) shouldBe empty
    page.getElementValue(AddressPage.postcodeFieldXPath) shouldBe empty
    page.getElementValue(AddressPage.countryCodeFieldXPath) shouldBe empty
  }
}

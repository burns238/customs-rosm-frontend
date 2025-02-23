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

package unit.controllers.subscription

import common.pages.subscription.SicCodePage._
import common.pages.subscription.{SicCodePage, SubscriptionAmendCompanyDetailsPage}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.prop.Tables.Table
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SicCodeController
import uk.gov.hmrc.customs.rosmfrontend.domain.CdsOrganisationType.{Company, Individual, SoleTrader, ThirdCountryOrganisation, Partnership => CdsPartnership}
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.SicCodeSubscriptionFlowPage
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.services.organisation.OrgTypeLookup
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.sic_code
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.StringThings._
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder
import util.builders.SubscriptionAmendCompanyDetailsFormBuilder._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SicCodeControllerSpec
    extends SubscriptionFlowTestSupport with BeforeAndAfterEach
    with SubscriptionFlowCreateModeTestSupport with SubscriptionFlowReviewModeTestSupport {

  protected override val formId: String = SicCodePage.formId

  protected override def submitInCreateModeUrl: String =
    uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.SicCodeController
      .submit(isInReviewMode = false, Journey.GetYourEORI)
      .url

  protected override def submitInReviewModeUrl: String =
    uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.SicCodeController
      .submit(isInReviewMode = true, Journey.GetYourEORI)
      .url

  private val mockOrgTypeLookup = mock[OrgTypeLookup]
  private val mockRequestSessionData = mock[RequestSessionData]
  private val sicCodeView = app.injector.instanceOf[sic_code]

  private val controller = new SicCodeController(
    app,
    mockAuthConnector,
    mockSubscriptionBusinessService,
    mockSubscriptionFlowManager,
    mockSubscriptionDetailsHolderService,
    mockOrgTypeLookup,
    mcc,
    sicCodeView,
    mockRequestSessionData
  )

  private val emulatedFailure = new UnsupportedOperationException("Emulation of service call failure")

  override def beforeEach: Unit = {
    reset(
      mockSubscriptionBusinessService,
      mockSubscriptionFlowManager,
      mockOrgTypeLookup,
      mockSubscriptionDetailsHolderService,
      mockRequestSessionData
    )
    when(mockSubscriptionBusinessService.cachedSicCode(any[Request[_]])).thenReturn(None)
    registerSaveDetailsMockSuccess()
    setupMockSubscriptionFlowManager(SicCodeSubscriptionFlowPage)
  }

  "Subscription Sic Code form in create mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(mockAuthConnector, controller.createForm(Journey.GetYourEORI))

    "display title as 'What is the Standard Industrial Classification (SIC) code for your organisation?' for non-partnership org type" in {
      showCreateForm(orgType = CorporateBody, userSelectedOrgType = Company) { result =>
        val page = CdsPage(bodyOf(result))
        page.title should startWith("What is the Standard Industrial Classification (SIC) code for your organisation?")
      }
    }

    "display correct hint text when org type is company and user location is UK" in {
      showCreateForm(
        orgType = CorporateBody,
        userSelectedOrgType = Company,
        journey = Journey.GetYourEORI,
        userLocation = Some("UK")
      ) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementText(sicDescriptionLabelXpath) shouldBe "A SIC code is a 5 digit number that helps HMRC identify what your organisation does. You can search the register on Companies House for your SIC code (opens in a new window or tab)."
      }
    }

    "display heading as 'What is the Standard Industrial Classification (SIC) code for your organisation?' for non-partnership org type" in {
      showCreateForm(orgType = CorporateBody, userSelectedOrgType = Company) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementText(SicCodePage.headingXpath) shouldBe "What is the Standard Industrial Classification (SIC) code for your organisation?"
      }
    }

    "display title as 'What is the Standard Industrial Classification (SIC) code for your partnership?' for Partnership org type" in {
      showCreateForm(orgType = Partnership, userSelectedOrgType = CdsPartnership) { result =>
        val page = CdsPage(bodyOf(result))
        page.title should startWith("What is the Standard Industrial Classification (SIC) code for your partnership?")
      }
    }

    "display heading as 'What is the Standard Industrial Classification (SIC) code for your partnership?' for Partnership org type" in {
      showCreateForm(orgType = Partnership, userSelectedOrgType = CdsPartnership) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementText(SicCodePage.headingXpath) shouldBe "What is the Standard Industrial Classification (SIC) code for your partnership?"
      }
    }

    "display title as 'What is the Standard Industrial Classification (SIC) code for your partnership?' for LLP org type" in {
      showCreateForm(orgType = LLP, userSelectedOrgType = CdsPartnership) { result =>
        val page = CdsPage(bodyOf(result))
        page.title should startWith("What is the Standard Industrial Classification (SIC) code for your partnership?")
      }
    }

    "display heading as 'What is the Standard Industrial Classification (SIC) code for your partnership?' for LLP org type" in {
      showCreateForm(orgType = LLP, userSelectedOrgType = CdsPartnership) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementText(SicCodePage.headingXpath) shouldBe "What is the Standard Industrial Classification (SIC) code for your partnership?"
      }
    }

    "display correct description for non-partnership org type" in {
      showCreateForm(orgType = CorporateBody, userSelectedOrgType = Company) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementText(SicCodePage.sicLabelXpath) should startWith("SIC code")
      }
    }

    "display correct description for Partnership org type" in {
      showCreateForm(orgType = Partnership, userSelectedOrgType = CdsPartnership) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementText(SicCodePage.sicLabelXpath) should startWith("SIC code")
      }
    }

    "display correct description for LLP org type" in {
      showCreateForm(orgType = LLP, userSelectedOrgType = CdsPartnership) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementText(SicCodePage.sicLabelXpath) should startWith("SIC code")
      }
    }

    val formModes = Table(("userSelectedOrgType", "orgType"), (SoleTrader, "SoleTrader"), (Individual, "Individual"))

    forAll(formModes) { (userSelectedOrgType, orgType) =>
      s"display title as 'Enter a Standard Industrial Classification (SIC) code that describes what your business does' for $orgType (NA) org type" in {
        showCreateForm(orgType = NA, userSelectedOrgType = userSelectedOrgType) { result =>
          val page = CdsPage(bodyOf(result))
          page.title should startWith(
            "Enter a Standard Industrial Classification (SIC) code that describes what your business does"
          )
        }
      }

      s"display heading as 'Enter a Standard Industrial Classification (SIC) code that describes what your business does' for $orgType (NA) org type" in {
        showCreateForm(orgType = NA, userSelectedOrgType = userSelectedOrgType) { result =>
          val page = CdsPage(bodyOf(result))
          page.getElementText(SicCodePage.headingXpath) shouldBe "Enter a Standard Industrial Classification (SIC) code that describes what your business does"
        }
      }

      s"display correct description for $orgType (NA) org type" in {
        showCreateForm(orgType = NA, userSelectedOrgType = userSelectedOrgType) {
          val hintText = userSelectedOrgType match {
            case (SoleTrader) =>
              "A SIC code is a 5 digit number that helps HMRC identify what your business does. If you do not have one, you can search for a relevant SIC code on Companies House (opens in a new window or tab)."
            case _ =>
              "A SIC code is a 5 digit number that helps HMRC identify what your organisation does. If you do not have one, you can search for a relevant SIC code on Companies House (opens in a new window or tab)."
          }
          result =>
            val page = CdsPage(bodyOf(result))
            page.getElementText(SicCodePage.sicDescriptionLabelXpath) should startWith(hintText)
            page.getElementsHref("//*[@id='description']/a") shouldBe "https://resources.companieshouse.gov.uk/sic/"
        }
      }

      s"display correct label description for $orgType (NA) org type" in {
        showCreateForm(orgType = NA, userSelectedOrgType = userSelectedOrgType) { result =>
          val page = CdsPage(bodyOf(result))
          page.getElementText(SicCodePage.sicLabelXpath) should startWith("SIC code")
        }
      }
    }

    "submit in create mode" in {
      showCreateForm(userSelectedOrgType = Company)(verifyFormActionInCreateMode)
    }

    "display the back link" in {
      showCreateForm(userSelectedOrgType = Company)(verifyBackLinkInCreateModeRegister)
    }

    "have SIC code input field without data if not cached previously" in {
      showCreateForm(userSelectedOrgType = Company) { result =>
        val page = CdsPage(bodyOf(result))
        verifyPrincipalEconomicActivityFieldExistsWithNoData(page)
      }
    }

    "have SIC code input field prepopulated if cached previously" in {
      when(mockSubscriptionBusinessService.cachedSicCode(any[Request[_]])).thenReturn(Future.successful(Some(sic)))
      showCreateForm(userSelectedOrgType = Company) { result =>
        val page = CdsPage(bodyOf(result))
        verifyPrincipalEconomicActivityFieldExistsAndPopulatedCorrectly(page)
      }
    }

    "display the correct text for the continue button" in {
      showCreateForm(userSelectedOrgType = Company)({ result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(SicCodePage.continueButtonXpath) shouldBe ContinueButtonTextInCreateMode
      })
    }
  }

  "Subscription Sic Code form in review mode" should {

    "submit in review mode" in {
      showReviewForm(userSelectedOrgType = Company)(verifyFormSubmitsInReviewMode)
    }

    "retrieve the cached data" in {
      showReviewForm(userSelectedOrgType = Company) { result =>
        CdsPage(bodyOf(result))
        verify(mockSubscriptionBusinessService).getCachedSicCode(any[Request[_]])
      }
    }

    "have all the required input fields without data" in {
      showReviewForm(sic, userSelectedOrgType = Company) { result =>
        val page = CdsPage(bodyOf(result))
        verifyPrincipalEconomicActivityFieldExistsAndPopulatedCorrectly(page)
      }
    }

    "display the correct text for the continue button" in {
      showReviewForm(userSelectedOrgType = Company)({ result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(SicCodePage.continueButtonXpath) shouldBe ContinueButtonTextInReviewMode
      })
    }
  }

  "submitting the form with all mandatory fields filled when in create mode for all organisation types" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.submit(isInReviewMode = false, Journey.GetYourEORI)
    )

    "wait until the saveSubscriptionDetailsHolder is completed before progressing" in {
      registerSaveDetailsMockFailure(emulatedFailure)

      val caught = intercept[RuntimeException] {
        submitFormInCreateMode(mandatoryFieldsMap, userSelectedOrgType = Company) { result =>
          await(result)
        }
      }

      caught shouldBe emulatedFailure
    }

    "redirect to next screen" in {
      submitFormInCreateMode(mandatoryFieldsMap, userSelectedOrgType = Company)(verifyRedirectToNextPageInCreateMode)
    }
  }

  "submitting the form with all mandatory fields filled when in review mode for all organisation types" should {

    "wait until the saveSubscriptionDetailsHolder is completed before progressing" in {
      registerSaveDetailsMockFailure(emulatedFailure)

      val caught = intercept[RuntimeException] {
        submitFormInReviewMode(populatedSicCodeFieldsMap, userSelectedOrgType = Company) { result =>
          await(result)
        }
      }

      caught shouldBe emulatedFailure
    }

    "redirect to review screen" in {
      submitFormInReviewMode(populatedSicCodeFieldsMap, userSelectedOrgType = Company)(
        verifyRedirectToReviewPage(Journey.GetYourEORI)
      )
    }
  }

  "Submitting in Create Mode when entries are invalid" should {

    "allow resubmission in create mode" in {
      submitFormInCreateMode(unpopulatedSicCodeFieldsMap, userSelectedOrgType = Company)(verifyFormActionInCreateMode)
    }
  }

  "Submitting in Review Mode when entries are invalid" should {

    "allow resubmission in review mode" in {
      submitFormInReviewMode(unpopulatedSicCodeFieldsMap, userSelectedOrgType = Company)(verifyFormSubmitsInReviewMode)
    }
  }

  "SIC Code" should {

    "be mandatory" in {
      submitFormInCreateMode(Map("sic" -> ""), userSelectedOrgType = Company) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath) shouldEqual "Enter a SIC code"
        page.getElementsText(SubscriptionAmendCompanyDetailsPage.sicFieldLevelErrorXpath) shouldEqual "Error: Enter a SIC code"
      }
    }

    "not allow spaces instead of numbers" in {
      submitFormInCreateMode(Map("sic" -> 5.spaces), userSelectedOrgType = Company) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath) shouldEqual "Enter a SIC code"
        page.getElementsText(SubscriptionAmendCompanyDetailsPage.sicFieldLevelErrorXpath) shouldEqual "Error: Enter a SIC code"
      }
    }

    "be numeric" in {
      submitFormInCreateMode(Map("sic" -> "G1A3"), userSelectedOrgType = Company) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath) shouldBe "Enter a SIC code in the right format"
        page.getElementsText(SubscriptionAmendCompanyDetailsPage.sicFieldLevelErrorXpath) shouldEqual "Error: Enter a SIC code in the right format"
      }
    }

    "be at least 4 digits" in {
      submitFormInCreateMode(Map("sic" -> "123"), userSelectedOrgType = Company) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath) shouldBe "Enter a SIC code in the right format"
        page.getElementsText(SubscriptionAmendCompanyDetailsPage.sicFieldLevelErrorXpath) shouldEqual "Error: Enter a SIC code in the right format"
      }
    }

    "be maximum of 5 digits" in {
      submitFormInCreateMode(Map("sic" -> "123456"), userSelectedOrgType = Company) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath) shouldBe "Enter a SIC code in the right format"
        page.getElementsText(SubscriptionAmendCompanyDetailsPage.sicFieldLevelErrorXpath) shouldEqual "Error: Enter a SIC code in the right format"
      }
    }

    "allow 5 digit number with non significant digits" in {
      submitFormInCreateMode(Map("sic" -> "00120"), userSelectedOrgType = Company) { result =>
        status(result) shouldBe SEE_OTHER
      }
    }
  }

  val formModelsROW = Table(
    ("userSelectedOrgType", "orgType", "userLocation"),
    (SoleTrader, "SoleTrader", "iom"),
    (ThirdCountryOrganisation, "Organisation", "third-country")
  )

  forAll(formModelsROW) { (userSelectedOrgType, orgType, userLocation) =>
    s"display correct description for $orgType (NA) org type and when user-location is NON-UK: $userLocation" in {
      showCreateForm(userSelectedOrgType = userSelectedOrgType, userLocation = Some(userLocation)) { result =>
        val page = CdsPage(bodyOf(result))
        userSelectedOrgType match {
          case SoleTrader =>
            page.getElementsText(sicDescriptionLabelXpath) shouldBe "A SIC code is a 5 digit number that helps HMRC identify what your business does. In some countries it is also known as a trade number. If you do not have one, you can search for a relevant SIC code on Companies House (opens in a new window or tab)."
            page.getElementText(SicCodePage.headingXpath) shouldBe "Enter a Standard Industrial Classification (SIC) code that describes what your business does"
            page.getElementsHref("//*[@id='description']/a") shouldBe "https://resources.companieshouse.gov.uk/sic/"
          case ThirdCountryOrganisation =>
            page.getElementsText(sicDescriptionLabelXpath) shouldBe "A SIC code is a 5 digit number that helps HMRC identify what your organisation does. In some countries it is also known as a trade number. If you do not have one, you can search for a relevant SIC code on Companies House (opens in a new window or tab)."
            page.getElementText(SicCodePage.headingXpath) shouldBe "What is the Standard Industrial Classification (SIC) code for your organisation?"
            page.getElementsHref("//*[@id='description']/a") shouldBe "https://resources.companieshouse.gov.uk/sic/"
        }
      }
    }
  }

  private def submitFormInCreateMode(
    form: Map[String, String],
    userId: String = defaultUserId,
    orgType: EtmpOrganisationType = CorporateBody,
    journey: Journey.Value = Journey.GetYourEORI,
    userSelectedOrgType: CdsOrganisationType
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]])).thenReturn(Some(orgType))
    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
      .thenReturn(Some(userSelectedOrgType))

    test(
      controller
        .submit(isInReviewMode = false, journey)(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    )
  }

  private def submitFormInReviewMode(
    form: Map[String, String],
    userId: String = defaultUserId,
    orgType: EtmpOrganisationType = CorporateBody,
    journey: Journey.Value = Journey.GetYourEORI,
    userSelectedOrgType: CdsOrganisationType
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]])).thenReturn(Some(orgType))
    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
      .thenReturn(Some(userSelectedOrgType))

    test(
      controller
        .submit(isInReviewMode = true, journey)(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    )
  }

  private def registerSaveDetailsMockSuccess() {
    when(mockSubscriptionDetailsHolderService.cacheSicCode(anyString())(any[Request[_]]))
      .thenReturn(Future.successful(()))
  }

  private def registerSaveDetailsMockFailure(exception: Throwable) {
    when(mockSubscriptionDetailsHolderService.cacheSicCode(anyString)(any[Request[_]]))
      .thenReturn(Future.failed(exception))
  }

  private def showCreateForm(
    userId: String = defaultUserId,
    orgType: EtmpOrganisationType = CorporateBody,
    journey: Journey.Value = Journey.GetYourEORI,
    userSelectedOrgType: CdsOrganisationType,
    userLocation: Option[String] = Some("uk")
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]])).thenReturn(Some(orgType))
    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
      .thenReturn(Some(userSelectedOrgType))
    when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(userLocation)

    test(controller.createForm(journey).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def showReviewForm(
    dataToEdit: String = sic,
    userId: String = defaultUserId,
    orgType: EtmpOrganisationType = CorporateBody,
    journey: Journey.Value = Journey.GetYourEORI,
    userSelectedOrgType: CdsOrganisationType
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]])).thenReturn(Some(orgType))
    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
      .thenReturn(Some(userSelectedOrgType))
    when(mockSubscriptionBusinessService.getCachedSicCode(any[Request[_]])).thenReturn(dataToEdit)

    test(controller.reviewForm(journey).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def verifyPrincipalEconomicActivityFieldExistsAndPopulatedCorrectly(page: CdsPage): Unit =
    page.getElementValueForLabel(SubscriptionAmendCompanyDetailsPage.sicLabelXpath) shouldBe sic

  private def verifyPrincipalEconomicActivityFieldExistsWithNoData(page: CdsPage): Unit =
    page.getElementValueForLabel(SubscriptionAmendCompanyDetailsPage.sicLabelXpath) shouldBe empty
}

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

package unit.controllers.registration

import common.pages.matching.OrganisationNamePage._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.FeatureFlags
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.WhatIsYourOrgNameController
import uk.gov.hmrc.customs.rosmfrontend.domain.NameOrganisationMatchModel
import uk.gov.hmrc.customs.rosmfrontend.domain.registration.UserLocation
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.what_is_your_org_name
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder
import util.builders.matching.OrganisationNameFormBuilder._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class WhatIsYourOrgNameControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  implicit override lazy val app: Application = new GuiceApplicationBuilder()
    .configure("features.rowHaveUtrEnabled" -> false)
    .build()

  private val mockAuthConnector = mock[AuthConnector]
  private val mockRequestSessionData = mock[RequestSessionData]
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]
  private val mockNameOrganisationMatchModel = mock[NameOrganisationMatchModel]
  private val mockFeatureFlags = mock[FeatureFlags]
  private val whatIsYourOrgNameView = app.injector.instanceOf[what_is_your_org_name]

  private val controller = new WhatIsYourOrgNameController(
    app,
    mockAuthConnector,
    mockRequestSessionData,
    mcc,
    whatIsYourOrgNameView,
    mockSubscriptionDetailsService
  )

  private val organisationTypeOrganisations =
    Table(
      (
        "organisationType",
        "organisation",
        "nameDescription",
        "submitLocation",
        "userLocation",
        "reviewMode",
        "expectedName"
      ),
      (
        "charity-public-body-not-for-profit",
        CharityPublicBodyNotForProfitOrganisation,
        "organisation",
        "/customs/register-for-cds/matching/utr-yes-no/charity-public-body-not-for-profit",
        UserLocation.Uk,
        false,
        ""
      ),
      (
        "third-country-organisation",
        ThirdCountryOrg,
        "organisation",
        "/customs/register-for-cds/matching/address/third-country-organisation",
        UserLocation.ThirdCountry,
        false,
        ""
      ),
      (
        "third-country-organisation",
        ThirdCountryOrg,
        "organisation",
        "/customs/register-for-cds/matching/review-determine",
        UserLocation.ThirdCountry,
        true,
        "Test Org Name"
      )
    )

  private val NameMaxLength = 105

  private def defaultOrganisationType: String = organisationTypeOrganisations.head._1

  private def nameError(nameDescription: String): String =
    s"Enter your registered $nameDescription name"

  override def beforeEach: Unit = {
    reset(mockRequestSessionData, mockSubscriptionDetailsService)
    when(mockSubscriptionDetailsService.cacheNameDetails(any())(any[Request[_]]())).thenReturn(Future.successful(()))
    when(mockSubscriptionDetailsService.cachedNameDetails(any[Request[_]]()))
      .thenReturn(Future.successful(Some(mockNameOrganisationMatchModel)))
  }

  "Viewing the Organisation Name Matching form" should {

    forAll(organisationTypeOrganisations) { (organisationType, _, _, _, _, reviewMode, expectedName) =>
      {

        assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
          mockAuthConnector,
          controller.showForm(reviewMode, organisationType, Journey.GetYourEORI),
          s", for reviewMode $reviewMode and organisationType $organisationType"
        )

        s"display the form for $organisationType and reviewMode is $reviewMode" in {
          when(mockNameOrganisationMatchModel.name).thenReturn(expectedName)
          showForm(reviewMode) { result =>
            status(result) shouldBe OK
            val page = CdsPage(bodyOf(result))
            page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe empty
            page.getElementsText(fieldLevelErrorName) shouldBe empty
            page.getElementValueForLabel(labelForName) shouldBe expectedName
          }
        }

        s"ensure the labels are correct for $organisationType and reviewMode is $reviewMode" in {
          showForm(reviewMode) { result =>
            status(result) shouldBe OK
            val page = CdsPage(bodyOf(result))
            page.getElementsText(labelForNameOuter) shouldBe "What is your registered organisation name?"
          }
        }
      }
    }
  }

  "Submitting the form with rowHaveUtrEnabled as false" should {

    forAll(organisationTypeOrganisations) {
      (organisationType, _, nameDescription, submitLocation, userLocation, reviewMode, _) =>
        {

          assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
            mockAuthConnector,
            controller.submit(reviewMode, organisationType, Journey.GetYourEORI),
            s", for reviewMode $reviewMode and organisationType $organisationType"
          )

          s"ensure name cannot be empty when organisation type is $organisationType and reviewMode is $reviewMode" in {
            submitForm(reviewMode, form = Map("name" -> ""), organisationType) { result =>
              status(result) shouldBe BAD_REQUEST
              val page = CdsPage(bodyOf(result))
              page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe nameError(nameDescription)
              page.getElementsText(fieldLevelErrorName) shouldBe "Error: Enter your registered organisation name"
              page.getElementsText("title") should startWith("Error: ")
            }
          }

          s"ensure name does not exceed maximum length when organisation type is $organisationType and reviewMode is $reviewMode" in {
            submitForm(reviewMode, form = Map("name" -> oversizedString(NameMaxLength)), organisationType) { result =>
              status(result) shouldBe BAD_REQUEST
              val page = CdsPage(bodyOf(result))
              page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Registered company name must be 105 characters or fewer"
              page.getElementsText(fieldLevelErrorName) shouldBe "Error: Registered company name must be 105 characters or fewer"
              page.getElementsText("title") should startWith("Error: ")
            }
          }

          s"redirect to the next page when successful when organisation type is $organisationType and reviewMode is $reviewMode" in {
            when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(userLocation))

            submitForm(reviewMode, form = ValidNameRequest, organisationType) { result =>
              status(result) shouldBe SEE_OTHER
              result.header.headers("Location") should endWith(submitLocation)
              verify(mockSubscriptionDetailsService).cacheNameDetails(any())(any[Request[_]])
            }
          }
        }
    }
  }

  def showForm(
    isInReviewMode: Boolean = false,
    organisationType: String = defaultOrganisationType,
    userId: String = defaultUserId
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    val result = controller
      .showForm(isInReviewMode, organisationType, Journey.GetYourEORI)
      .apply(SessionBuilder.buildRequestWithSession(userId))
    test(result)
  }

  def submitForm(
    isInReviewMode: Boolean = false,
    form: Map[String, String],
    organisationType: String = defaultOrganisationType,
    userId: String = defaultUserId
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    val result = controller
      .submit(isInReviewMode, organisationType, Journey.GetYourEORI)
      .apply(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    test(result)
  }
}

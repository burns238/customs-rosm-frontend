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

package unit.views.registration

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.prop.Tables.Table
import org.scalatest.{BeforeAndAfter, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.OrganisationTypeController
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.customs.rosmfrontend.domain.CdsOrganisationType
import uk.gov.hmrc.customs.rosmfrontend.domain.registration.UserLocation
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.services.registration.RegistrationDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.organisation_type
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OrganisationTypeViewSpec extends ControllerSpec with MockitoSugar with BeforeAndAfterEach with BeforeAndAfter {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockRequestSessionData = mock[RequestSessionData]
  private val mockSubscriptionFlowManager = mock[SubscriptionFlowManager]
  private val mockRegistrationDetailsService = mock[RegistrationDetailsService]
  private val organisationTypeView = app.injector.instanceOf[organisation_type]

  private val organisationTypeController = new OrganisationTypeController(
    app,
    mockAuthConnector,
    mockSubscriptionFlowManager,
    mockRequestSessionData,
    mcc,
    organisationTypeView,
    mockRegistrationDetailsService
  )

  override def beforeEach(): Unit = {
    reset(mockAuthConnector, mockRequestSessionData)
    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(None)
  }

  "Organisation Type Form" should {

    "load the html form when entering details" in {
      invokeOrganisationTypeWithAuthenticatedUser(userLocation = Some(UserLocation.Uk)) { result =>
        status(result) shouldBe OK
        contentType(result) shouldBe Some("text/html")
      }
    }

    "have the expected page title" in {
      invokeOrganisationTypeWithAuthenticatedUser(userLocation = Some(UserLocation.Uk)) { result =>
        contentAsString(result) should include("<title>What do you want to apply as?")
      }
    }

    val userLocations =
      Table("userLocation", Some(UserLocation.Uk), Some(UserLocation.ThirdCountry))

    forAll(userLocations) { userLocation =>
      val forUk = userLocation.fold(true)(_ == UserLocation.Uk)
      val forThirdCountry = userLocation.fold(true)(_ == UserLocation.ThirdCountry)

      s"have all the required input fields while on main screen for user location ${userLocation.getOrElse("None")}" in {
        invokeOrganisationTypeWithAuthenticatedUser(userLocation = userLocation) { result =>
          val html: String = contentAsString(result)
          html.contains("id=\"organisation-type-field\"") shouldBe true
          html.contains("id=\"organisation-type-company\"") shouldBe forUk
          html.contains("id=\"organisation-type-sole-trader\"") shouldBe forUk
          html.contains("id=\"organisation-type-individual\"") shouldBe forUk
          html.contains("id=\"organisation-type-partnership\"") shouldBe forUk
          html.contains("id=\"organisation-type-limited-liability-partnership\"") shouldBe forUk
          html.contains("id=\"organisation-type-charity-public-body-not-for-profit\"") shouldBe forUk
          html.contains("id=\"organisation-type-third-country-organisation\"") shouldBe forThirdCountry
          html.contains("id=\"organisation-type-third-country-sole-trader\"") shouldBe forThirdCountry
          html.contains("id=\"organisation-type-third-country-individual\"") shouldBe forThirdCountry
        }
      }
    }

    "redirect status code when user has not selected a location" in {
      invokeOrganisationTypeWithAuthenticatedUser() { result =>
        status(result) shouldBe OK
      }
    }

  }

  def invokeOrganisationTypeWithAuthenticatedUser(
    maybeOrgType: Option[CdsOrganisationType] = None,
    userLocation: Option[String] = None,
    userId: String = defaultUserId
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(userLocation)

    val request = maybeOrgType.map { orgType =>
      SessionBuilder.buildRequestWithSessionAndOrgType(userId, orgType.id)
    }.getOrElse(SessionBuilder.buildRequestWithSession(userId))
    test(organisationTypeController.form(Journey.GetYourEORI).apply(request))
  }
}

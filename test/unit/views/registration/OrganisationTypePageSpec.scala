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

import org.jsoup.Jsoup
import play.api.data.Form
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.customs.rosmfrontend.domain.CdsOrganisationType
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms._
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.organisation_type
import util.ViewSpec

class OrganisationTypePageSpec extends ViewSpec {
  private val form: Form[CdsOrganisationType] = organisationTypeDetailsForm
  private val thirdCountryOrganisationLabel = "label[for=organisation-type-third-country-organisation]"
  private val thirdCountrySoleTraderLabel = "label[for=organisation-type-third-country-sole-trader]"
  private val thirdCountryIndividualLabel = "label[for=organisation-type-third-country-individual]"

  private val view = app.injector.instanceOf[organisation_type]

  "Rest of World (ROW) What do you want to apply as? page" should {
    "display 'an organisation' as an option" in {
      doc.select(thirdCountryOrganisationLabel).text() mustBe "an organisation"
    }

    "display 'a sole trader' as an option" in {
      doc.select(thirdCountrySoleTraderLabel).text() mustBe "a sole trader"
    }

    "display 'an individual' as an option" in {
      doc.select(thirdCountryIndividualLabel).text() mustBe "an individual"
    }
  }

  private lazy val doc = {
    implicit val request = withFakeCSRF(FakeRequest().withSession(("selected-user-location", "third-country")))
    val result = view(form, Some("third-country"), Journey.GetYourEORI)
    Jsoup.parse(contentAsString(result))
  }
}

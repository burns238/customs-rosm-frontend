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

package common.pages.registration

import common.pages.WebPage
import common.support.Env

trait VatRegisteredUkPage extends WebPage {

  override val fieldLevelErrorYesNoAnswer: String = "//*[@id='yes-no-answer-field']//span[@class='error-message']"

  override val url: String = Env.frontendHost + "/customs/register-for-cds/are-you-vat-registered-in-uk"
  override val title = "Is your organisation VAT registered in the UK?"
  val problemWithSelectionError = "Tell us if your organisation is VAT registered in the UK"
  val problemWithSelectionFieldError = "Error:Tell us if your organisation is VAT registered in the UK"

}

object VatRegisteredUkPage extends VatRegisteredUkPage

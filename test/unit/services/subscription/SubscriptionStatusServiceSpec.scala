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

import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.mvc.Request
import play.mvc.Http.Status._
import uk.gov.hmrc.customs.rosmfrontend.connector.SubscriptionStatusConnector
import uk.gov.hmrc.customs.rosmfrontend.domain.{SubscriptionStatusOutcome, SubscriptionStatusQueryParams, SubscriptionStatusResponseHolder, TaxPayerId}
import uk.gov.hmrc.customs.rosmfrontend.services.RequestCommonGenerator
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.services.subscription._
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import util.UnitSpec

import scala.concurrent.Future

class SubscriptionStatusServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {
  private val mockConnector = mock[SubscriptionStatusConnector]
  private val mockRequestCommonGenerator: RequestCommonGenerator = mock[RequestCommonGenerator]
  private val mockSessionCache = mock[SessionCache]
  private val AValidTaxPayerID = "123456789"
  private val MDGZeroPaddedTaxPayerId = AValidTaxPayerID + "000000000000000000000000000000000"
  private val receiptDate = new DateTime().withDate(2016, 3, 17).withTime(9, 30, 47, 0)
  private val request =
    SubscriptionStatusQueryParams(receiptDate = receiptDate, regime = "CDS", "taxPayerID", MDGZeroPaddedTaxPayerId)

  lazy val service =
    new SubscriptionStatusService(mockConnector, mockRequestCommonGenerator, mockSessionCache)

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val req: Request[_] = mock[Request[_]]

  override protected def beforeEach() {
    reset(mockConnector)
    reset(mockSessionCache)
  }

  "SubscriptionStatusService getStatus" should {

    val statusesWithoutEori = Table(
      ("status", "statusObject"),
      ("00", NewSubscription),
      ("01", SubscriptionProcessing),
      ("04", SubscriptionExists),
      ("05", SubscriptionRejected),
      ("11", SubscriptionProcessing),
      ("14", SubscriptionProcessing),
      ("99", SubscriptionRejected)
    )

    forAll(statusesWithoutEori) { (status, statusObject: PreSubscriptionStatus) =>
      s"return $statusObject when response status is $status" in {
        when(mockConnector.status(meq(request))(any[HeaderCarrier])).thenReturn(
          Future.successful(responseHolderWithStatusAndProcessingDateWithoutEori(status).subscriptionStatusResponse)
        )
        when(mockRequestCommonGenerator.receiptDate).thenReturn(receiptDate)

        await(service.getStatus("taxPayerID", TaxPayerId(AValidTaxPayerID).mdgTaxPayerId)) shouldBe statusObject
      }
    }

    "store processing date in cache" in {
      when(mockConnector.status(meq(request))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          responseHolderWithStatusAndProcessingDateWithoutEori("01", "2018-05-22T09:30:00Z").subscriptionStatusResponse
        )
      )
      when(mockRequestCommonGenerator.receiptDate).thenReturn(receiptDate)

      await(service.getStatus("taxPayerID", TaxPayerId(AValidTaxPayerID).mdgTaxPayerId))

      verify(mockSessionCache).saveSubscriptionStatusOutcome(SubscriptionStatusOutcome("22 May 2018"))
    }

    "return failed future for getStatus when connector fails with INTERNAL_SERVER_ERROR" in {
      when(mockConnector.status(any[SubscriptionStatusQueryParams])(any[HeaderCarrier]))
        .thenReturn(Future.failed(UpstreamErrorResponse("failure", INTERNAL_SERVER_ERROR, 1)))

      val caught = intercept[UpstreamErrorResponse] {
        await(service.getStatus("taxPayerID", TaxPayerId(AValidTaxPayerID).mdgTaxPayerId))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
      caught.getMessage shouldBe "failure"
    }

    "return failed future for getStatus when connector fails with BAD_REQUEST" in {
      when(mockConnector.status(any[SubscriptionStatusQueryParams])(any[HeaderCarrier]))
        .thenReturn(Future.failed(UpstreamErrorResponse("failure", BAD_REQUEST, 1)))

      val caught = intercept[UpstreamErrorResponse] {
        await(service.getStatus("taxPayerID", TaxPayerId(AValidTaxPayerID).mdgTaxPayerId))
      }
      caught.statusCode shouldBe BAD_REQUEST
      caught.getMessage shouldBe "failure"
    }
  }

  private def responseHolderWithStatusAndProcessingDateWithoutEori(
    status: String,
    processingDate: String = "2016-03-17T09:30:47Z"
  ) =
    Json.parse(s"""
         |{
         |  "subscriptionStatusResponse": {
         |    "responseCommon": {
         |      "status": "OK",
         |      "processingDate": "$processingDate"
         |    },
         |    "responseDetail": {
         |      "subscriptionStatus": "$status"
         |    }
         |  }
         |}
      """.stripMargin).as[SubscriptionStatusResponseHolder]
}

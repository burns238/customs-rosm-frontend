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

package unit.services

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.SEE_OTHER
import play.api.libs.json.{Reads, Writes}
import play.api.mvc.Results.Redirect
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers.{LOCATION, defaultAwaitTimeout, redirectLocation}
import uk.gov.hmrc.customs.rosmfrontend.connector.Save4LaterConnector
import uk.gov.hmrc.customs.rosmfrontend.domain.{CacheIds, GroupId, InternalId, SafeId}
import uk.gov.hmrc.customs.rosmfrontend.services.UserGroupIdSubscriptionStatusCheckService
import uk.gov.hmrc.customs.rosmfrontend.services.subscription._
import uk.gov.hmrc.http.HeaderCarrier
import util.UnitSpec
import uk.gov.hmrc.customs.rosmfrontend.config.AppConfig
import uk.gov.hmrc.customs.rosmfrontend.forms.models.email.EmailStatus
import uk.gov.hmrc.customs.rosmfrontend.models.Journey

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class UserGroupIdSubscriptionStatusCheckServiceSpec
    extends UnitSpec with MockitoSugar with BeforeAndAfterEach with ScalaFutures {
  private val mockSubscriptionStatusService = mock[SubscriptionStatusService]
  private val mockEnrolmentStoreProxyService = mock[EnrolmentStoreProxyService]
  private val mockSave4LaterConnector = mock[Save4LaterConnector]
  private val mockAppConfig = mock[AppConfig]
  private implicit val hc: HeaderCarrier = mock[HeaderCarrier]
  private implicit val rq: Request[AnyContent] = mock[Request[AnyContent]]
  private implicit val reads: Reads[CacheIds] = mock[Reads[CacheIds]]
  private implicit val writes: Writes[CacheIds] = mock[Writes[CacheIds]]
  private val safeId = SafeId("safeId")
  private val groupId = GroupId("groupId-123")
  private val internalId = InternalId("internalId-123")
  private val cacheIds = CacheIds(internalId, safeId)
  private val service = new UserGroupIdSubscriptionStatusCheckService(
    mockSubscriptionStatusService,
    mockEnrolmentStoreProxyService,
    mockSave4LaterConnector,
    mockAppConfig
  )

  private def continue: Future[Result] = Future.successful(Redirect("/continue"))
  private def groupIsEnrolled: Future[Result] = Future.successful(Redirect("/blocked/groupIsEnrolled"))
  private def userIsInProcess: Future[Result] = Future.successful(Redirect("/continue"))
  private def existingApplicationInProgress: Future[Result] = Future.successful(Redirect("/blocked/existingApplicationInProgress"))

  private def otherUserWithinGroupIsInProcess: Future[Result] =
    Future.successful(Redirect("/blocked/otherUserWithinGroupIsInProcess"))

  override implicit def patienceConfig: PatienceConfig =
    super.patienceConfig.copy(timeout = Span(defaultTimeout.toMillis, Millis))

  override protected def beforeEach(): Unit = {
    reset(mockEnrolmentStoreProxyService, mockSave4LaterConnector, mockSubscriptionStatusService)
    when(
      mockEnrolmentStoreProxyService
        .isEnrolmentAssociatedToGroup(any[GroupId])(any[HeaderCarrier], any[ExecutionContext])
    ).thenReturn(Future.successful(true))
  }

  "checksToProceed" should {

    "block the user for the groupID if enrolment exists" in {
      val result: Result = service.checksToProceed(
          groupId, internalId, false, false, Journey.Migrate
      ) { continue } { groupIsEnrolled } { userIsInProcess }
      { existingApplicationInProgress } { otherUserWithinGroupIsInProcess }.futureValue

      result.header.headers(LOCATION) shouldBe "/blocked/groupIsEnrolled"
    }

    "block the user for the groupID is cache and subscription status is SubscriptionProcessing" in {
      when(
        mockEnrolmentStoreProxyService
          .isEnrolmentAssociatedToGroup(any[GroupId])(any[HeaderCarrier], any[ExecutionContext])
      ).thenReturn(Future.successful(false))

      when(
        mockSave4LaterConnector
          .get[CacheIds](any[String], any[String])(any[HeaderCarrier], any[Reads[CacheIds]], any[Writes[CacheIds]])
      ).thenReturn(Future.successful(Some(cacheIds)))

      when(mockSubscriptionStatusService.getStatus(any[String], any[String])(any[HeaderCarrier], any[Request[_]]))
        .thenReturn(Future.successful(SubscriptionProcessing))

      val result: Result = service.checksToProceed(
        groupId, internalId, false, false, Journey.Migrate
      ) { continue } { groupIsEnrolled } { userIsInProcess }
      { existingApplicationInProgress} { otherUserWithinGroupIsInProcess }.futureValue

      result.header.headers(LOCATION) shouldBe "/blocked/existingApplicationInProgress"
    }

    "allow the user for the groupID is cache any other subscription status" in {
      when(
        mockEnrolmentStoreProxyService
          .isEnrolmentAssociatedToGroup(any[GroupId])(any[HeaderCarrier], any[ExecutionContext])
      ).thenReturn(Future.successful(false))

      when(
        mockSave4LaterConnector
          .get[CacheIds](any[String], any[String])(any[HeaderCarrier], any[Reads[CacheIds]], any[Writes[CacheIds]])
      ).thenReturn(Future.successful(Some(cacheIds)))

      when(mockSubscriptionStatusService.getStatus(any[String], any[String])(any[HeaderCarrier], any[Request[_]]))
        .thenReturn(Future.successful(SubscriptionExists))

      val result: Result = service.checksToProceed(
        groupId, internalId, false, false, Journey.Migrate
      ) { continue } { groupIsEnrolled } { userIsInProcess }
      { existingApplicationInProgress} { otherUserWithinGroupIsInProcess }.futureValue

      result.header.headers(LOCATION) shouldBe "/continue"
    }

    "block the user for the groupID is cache and subscription status is SubscriptionProcessing for some other user within the group" in {
      when(
        mockEnrolmentStoreProxyService
          .isEnrolmentAssociatedToGroup(any[GroupId])(any[HeaderCarrier], any[ExecutionContext])
      ).thenReturn(Future.successful(false))

      when(
        mockSave4LaterConnector
          .get[CacheIds](any[String], any[String])(any[HeaderCarrier], any[Reads[CacheIds]], any[Writes[CacheIds]])
      ).thenReturn(Future.successful(Some(cacheIds.copy(internalId = InternalId("otherUserInternalId")))))

      when(mockSubscriptionStatusService.getStatus(any[String], any[String])(any[HeaderCarrier], any[Request[_]]))
        .thenReturn(Future.successful(SubscriptionProcessing))

      val result: Result = service.checksToProceed(
        groupId, internalId, false, false, Journey.Migrate
      ) { continue } { groupIsEnrolled } { userIsInProcess }
      { existingApplicationInProgress} {otherUserWithinGroupIsInProcess }.futureValue

      result.header.headers(LOCATION) shouldBe "/blocked/otherUserWithinGroupIsInProcess"
    }

    "allow the user for the groupID is cached and subscription status is SubscriptionRejected" in {
      when(
        mockEnrolmentStoreProxyService
          .isEnrolmentAssociatedToGroup(any[GroupId])(any[HeaderCarrier], any[ExecutionContext])
      ).thenReturn(Future.successful(false))

      when(
        mockSave4LaterConnector
          .get[CacheIds](any[String], any[String])(any[HeaderCarrier], any[Reads[CacheIds]], any[Writes[CacheIds]])
      ).thenReturn(Future.successful(Some(cacheIds)))

      when(mockSubscriptionStatusService.getStatus(any[String], any[String])(any[HeaderCarrier], any[Request[_]]))
        .thenReturn(Future.successful(SubscriptionRejected))

      when(mockSave4LaterConnector.delete(any[String])(any[HeaderCarrier])).thenReturn(Future.successful(()))

      val result: Result = service.checksToProceed(
        groupId, internalId,false, false, Journey.Migrate
      ) { continue } { groupIsEnrolled } { userIsInProcess }
      { existingApplicationInProgress } { otherUserWithinGroupIsInProcess }.futureValue

      result.header.headers(LOCATION) shouldBe "/continue"
    }

    "allow the user for the groupID is cached and subscription status is NewSubscription" in {
      when(
        mockEnrolmentStoreProxyService
          .isEnrolmentAssociatedToGroup(any[GroupId])(any[HeaderCarrier], any[ExecutionContext])
      ).thenReturn(Future.successful(false))

      when(
        mockSave4LaterConnector
          .get[CacheIds](any[String], any[String])(any[HeaderCarrier], any[Reads[CacheIds]], any[Writes[CacheIds]])
      ).thenReturn(Future.successful(Some(cacheIds)))

      when(mockSubscriptionStatusService.getStatus(any[String], any[String])(any[HeaderCarrier], any[Request[_]]))
        .thenReturn(Future.successful(NewSubscription))

      when(mockSave4LaterConnector.delete(any[String])(any[HeaderCarrier])).thenReturn(Future.successful(()))

      val result: Result = service.checksToProceed(
        groupId, internalId, false, false, Journey.Migrate
      ) { continue } { groupIsEnrolled } { userIsInProcess }
      { existingApplicationInProgress} { otherUserWithinGroupIsInProcess }.futureValue

      result.header.headers(LOCATION) shouldBe "/continue"
    }

    "continue to CDS when groupID is not cached and redirectSubToECC is set to false for Sub journey" in {
      when(
        mockEnrolmentStoreProxyService
          .isEnrolmentAssociatedToGroup(any[GroupId])(any[HeaderCarrier], any[ExecutionContext])
      ).thenReturn(Future.successful(false))

      when(
        mockSave4LaterConnector
          .get[CacheIds](any[String], any[String])(
            any[HeaderCarrier], any[Reads[CacheIds]], any[Writes[CacheIds]]
          )
      ).thenReturn(Future.successful(None))

      val result: Result =
        service.checksToProceed(
          groupId, internalId, redirectSubToECC = false, redirectRegToECC = false, Journey.Migrate
        ) { continue } { groupIsEnrolled } {userIsInProcess}
          { existingApplicationInProgress}{ otherUserWithinGroupIsInProcess }.futureValue

      status(result) shouldBe SEE_OTHER
      result.header.headers(LOCATION) shouldBe "/continue"
    }

    "continue to CDS when groupID is not cached and redirectRegToECC is set to false for Reg journey" in {
      when(
        mockEnrolmentStoreProxyService
          .isEnrolmentAssociatedToGroup(any[GroupId])(any[HeaderCarrier], any[ExecutionContext])
      ).thenReturn(Future.successful(false))

      when(
        mockSave4LaterConnector
          .get[CacheIds](any[String], any[String])(
            any[HeaderCarrier], any[Reads[CacheIds]], any[Writes[CacheIds]]
          )
      ).thenReturn(Future.successful(None))

      val result: Result =
        service.checksToProceed(
          groupId, internalId, redirectSubToECC = false, redirectRegToECC = false, Journey.GetYourEORI
        ) { continue } { groupIsEnrolled } { userIsInProcess }
        { existingApplicationInProgress } { otherUserWithinGroupIsInProcess }.futureValue

      status(result) shouldBe SEE_OTHER
      result.header.headers(LOCATION) shouldBe "/continue"
    }

    "redirect to ECC when groupID is not cached and redirectSubToECC is set to true for Sub journey" in {
      when(
        mockEnrolmentStoreProxyService
          .isEnrolmentAssociatedToGroup(any[GroupId])(any[HeaderCarrier], any[ExecutionContext])
      ).thenReturn(Future.successful(false))

      when(
        mockSave4LaterConnector
          .get[CacheIds](any[String], any[String])(
            any[HeaderCarrier], any[Reads[CacheIds]], any[Writes[CacheIds]]
          )
      ).thenReturn(Future.successful(None))

      when(mockAppConfig.subscribeLinkSubscribe)
        .thenReturn("/customs-enrolment-services/cds/subscribe")

      val result: Result =
        service. checksToProceed(groupId, internalId, redirectSubToECC = true, redirectRegToECC = false, Journey.Migrate)
        { continue } { groupIsEnrolled } { userIsInProcess }
        { existingApplicationInProgress }{ otherUserWithinGroupIsInProcess }.futureValue

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/customs-enrolment-services/cds/subscribe")
    }

    "redirect to ECC when groupID is not cached and redirectRegToECC is set to true for Reg journey" in {
      when(
        mockEnrolmentStoreProxyService
          .isEnrolmentAssociatedToGroup(any[GroupId])(any[HeaderCarrier], any[ExecutionContext])
      ).thenReturn(Future.successful(false))

      when(
        mockSave4LaterConnector
          .get[CacheIds](any[String], any[String])(any[HeaderCarrier], any[Reads[CacheIds]], any[Writes[CacheIds]])
      ).thenReturn(Future.successful(None))

      when(mockAppConfig.eccRegistrationEntryPoint)
        .thenReturn("/customs-registration-services/eori-only/register/check-user")

      val result: Result =
        service.checksToProceed(groupId, internalId, redirectSubToECC = false, redirectRegToECC = true, Journey.GetYourEORI)
        { continue } { groupIsEnrolled } { userIsInProcess } { existingApplicationInProgress }
        { otherUserWithinGroupIsInProcess }.futureValue

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/customs-registration-services/eori-only/register/check-user")
    }

    "redirect to ECC when groupID is cached and redirectToECCEnabled is set to true for Migration journey only" in {
      when(
        mockEnrolmentStoreProxyService
          .isEnrolmentAssociatedToGroup(any[GroupId])(any[HeaderCarrier], any[ExecutionContext])
      ).thenReturn(Future.successful(false))

      when(
        mockSave4LaterConnector
          .get[CacheIds](any[String], any[String])(
            any[HeaderCarrier], any[Reads[CacheIds]], any[Writes[CacheIds]]
          )
      ).thenReturn(Future.successful(Future.successful(Some(cacheIds))))

      when(mockAppConfig.subscribeLinkSubscribe)
        .thenReturn("/customs-enrolment-services/cds/subscribe")

      val result: Result =
        service. checksToProceed(groupId, internalId,redirectSubToECC = true, redirectRegToECC = false, Journey.Migrate)
        { continue } { groupIsEnrolled } { userIsInProcess }
        { existingApplicationInProgress }{ otherUserWithinGroupIsInProcess }.futureValue

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/customs-enrolment-services/cds/subscribe")
    }
  }
}

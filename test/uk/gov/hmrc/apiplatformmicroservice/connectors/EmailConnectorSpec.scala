/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatformmicroservice.connectors

import org.mockito.ArgumentMatchersSugar
import org.mockito.scalatest.MockitoSugar
import org.scalatest.{Matchers, OptionValues, WordSpec}
import org.scalatestplus.play.WsScalaTestClient
import play.api.http.Status._
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EmailConnectorSpec
  extends WordSpec
    with Matchers
    with OptionValues
    with WsScalaTestClient
    with MockitoSugar
    with ArgumentMatchersSugar
    with DefaultAwaitTimeout
    with FutureAwaits {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  private val baseUrl = s"http://example.com"
  private val hubTestTitle = "Unit Test Hub Title"
  private val hubUrl = "http://localhost:9685"
  private val hubLink = s"$hubUrl/developer/registration"

  trait Setup {
    val mockHttpClient = mock[HttpClient]
    val config = EmailConfig(baseUrl, hubUrl, hubTestTitle)
    val connector = new EmailConnector(mockHttpClient, config)

    def emailWillReturn(result: Future[HttpResponse]) = {
      when(mockHttpClient.POST[SendEmailRequest, HttpResponse](*, *, *)(*, *, *, *)).thenReturn(result)
    }

    def verifyEmailCalled(request: SendEmailRequest) = {
      val expectedUrl = s"${config.baseUrl}/hmrc/email"
      verify(mockHttpClient).POST[SendEmailRequest, HttpResponse](eqTo(expectedUrl), eqTo(request), *)(*, *, *, *)
    }
  }

  "emailConnector" should {
    val adminEmail = "admin1@example.com"
    val application = "Test Application"


//    "send unused application to be deleted email" in new Setup {
//
//      val expectedTemplateId = "apiApplicationToBeDeletedNotification"
//      val expectedToEmails = Set(adminEmail)
//      val expectedParameters: Map[String, String] = Map(
//        "applicationName" -> application,
//        "developerHubTitle" -> hubTestTitle
//      )
//
//      emailWillReturn(Future(HttpResponse(OK)))
//
//      await(connector.sendApplicationToBeDeletedNotification(application, Set(adminEmail)))
//
//      val expectedRequest = SendEmailRequest(expectedToEmails, expectedTemplateId, expectedParameters)
//      verifyEmailCalled(expectedRequest)
//    }

  }
}

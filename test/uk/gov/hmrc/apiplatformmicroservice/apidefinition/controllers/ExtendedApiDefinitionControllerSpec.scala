/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatformmicroservice.apidefinition.controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.apiplatformmicroservice.apidefinition.mocks._
import uk.gov.hmrc.apiplatformmicroservice.apidefinition.models.ApiDefinitionTestDataHelper
import uk.gov.hmrc.apiplatformmicroservice.apidefinition.models.ApiDefinitionJsonFormatters._
import uk.gov.hmrc.apiplatformmicroservice.common.StreamedResponseHelper.PROXY_SAFE_CONTENT_TYPE
import uk.gov.hmrc.apiplatformmicroservice.common.utils.AsyncHmrcSpec
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException, NotFoundException}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.apiplatformmicroservice.thirdpartyapplication.domain.models.EmailIdentifier

class ExtendedApiDefinitionControllerSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with ApiDefinitionTestDataHelper {

  trait Setup
      extends ApiDefinitionsForCollaboratorFetcherModule
      with ExtendedApiDefinitionForCollaboratorFetcherModule
      with ApiDocumentationResourceFetcherModule
      with SubscribedApiDefinitionsForCollaboratorFetcherModule {
    implicit val headerCarrier = HeaderCarrier()
    implicit val system = ActorSystem("test")
    implicit val mat = ActorMaterializer()

    val request = FakeRequest("GET", "/")
    val apiName = "hello-api"
    val version = "1.0"
    val anApiDefinition = apiDefinition(apiName)
    val anExtendedApiDefinition = extendedApiDefinition(apiName)

    val controller = new ExtendedApiDefinitionController(
      Helpers.stubControllerComponents(),
      ApiDefinitionsForCollaboratorFetcherMock.aMock,
      ExtendedApiDefinitionForCollaboratorFetcherMock.aMock,
      ApiDocumentationResourceFetcherMock.aMock,
      SubscribedApiDefinitionsForCollaboratorFetcherMock.aMock
    )
    val mockWSResponse = mock[WSResponse]
    when(mockWSResponse.status).thenReturn(OK)
    when(mockWSResponse.headers).thenReturn(
      Map(
        "Content-Length" -> Seq("500")
      )
    )
    when(mockWSResponse.header(eqTo(PROXY_SAFE_CONTENT_TYPE))).thenReturn(None)
    when(mockWSResponse.contentType).thenReturn("application/json")
  }

  "fetchApiDefinitionsForCollaborator" should {
    val email = Some(EmailIdentifier("joebloggs@example.com"))

    "return the API definitions when email provided" in new Setup {
      ApiDefinitionsForCollaboratorFetcherMock.willReturnApiDefinitions(anApiDefinition)

      val result = controller.fetchApiDefinitionsForCollaborator(email)(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(List(anApiDefinition))
    }

    "return the API definitions when no email provided" in new Setup {
      ApiDefinitionsForCollaboratorFetcherMock.willReturnApiDefinitions(anApiDefinition)

      val result = controller.fetchApiDefinitionsForCollaborator(None)(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(List(anApiDefinition))
    }

    "return an empty when there are no api definitions available" in new Setup {
      ApiDefinitionsForCollaboratorFetcherMock.willReturnApiDefinitions(List.empty: _*)

      val result = controller.fetchApiDefinitionsForCollaborator(email)(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.parse("[]")
    }

    "return error when the service throws and exception" in new Setup {
      ApiDefinitionsForCollaboratorFetcherMock.willThrowException(new RuntimeException("Something went wrong oops..."))

      val result = controller.fetchApiDefinitionsForCollaborator(email)(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(result) mustBe Json.obj("code" -> "UNKNOWN_ERROR", "message" -> "An unexpected error occurred")
    }
  }

  "fetchSubscribedApiDefinitionsForCollaborator" should {
    val email = EmailIdentifier("joebloggs@example.com")

    "return the API definitions the user is subscribed to" in new Setup {
      SubscribedApiDefinitionsForCollaboratorFetcherMock.willReturnApiDefinitions(anApiDefinition)

      val result = controller.fetchSubscribedApiDefinitionsForCollaborator(email)(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(List(anApiDefinition))
    }

    "return an empty List when there are no api definitions available" in new Setup {
      SubscribedApiDefinitionsForCollaboratorFetcherMock.willReturnApiDefinitions(List.empty: _*)

      val result = controller.fetchSubscribedApiDefinitionsForCollaborator(email)(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.parse("[]")
    }

    "return error when the service throws and exception" in new Setup {
      SubscribedApiDefinitionsForCollaboratorFetcherMock.willThrowException(new RuntimeException("Something went wrong oops..."))

      val result = controller.fetchSubscribedApiDefinitionsForCollaborator(email)(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(result) mustBe Json.obj("code" -> "UNKNOWN_ERROR", "message" -> "An unexpected error occurred")
    }
  }

  "fetchExtendedApiDefinitionForCollaborator" should {
    val email = Some(EmailIdentifier("joebloggs@example.com"))

    "return the extended API definition when email provided" in new Setup {
      ExtendedApiDefinitionForCollaboratorFetcherMock.willReturnExtendedApiDefinition(anExtendedApiDefinition)

      val result = controller.fetchExtendedApiDefinitionForCollaborator(apiName, email)(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(anExtendedApiDefinition)
    }

    "return the extended API definition when no email provided" in new Setup {
      ExtendedApiDefinitionForCollaboratorFetcherMock.willReturnExtendedApiDefinition(anExtendedApiDefinition)

      val result = controller.fetchExtendedApiDefinitionForCollaborator(apiName, None)(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(anExtendedApiDefinition)
    }

    "return 404 when there is no matching API definition" in new Setup {
      ExtendedApiDefinitionForCollaboratorFetcherMock.willReturnNoExtendedApiDefinition()

      val result = controller.fetchExtendedApiDefinitionForCollaborator(apiName, email)(request)

      status(result) mustBe NOT_FOUND
    }

    "return error when the service throws and exception" in new Setup {
      ExtendedApiDefinitionForCollaboratorFetcherMock.willThrowException(new RuntimeException("Something went wrong oops..."))

      val result = controller.fetchExtendedApiDefinitionForCollaborator(apiName, email)(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(result) mustBe Json.obj("code" -> "UNKNOWN_ERROR", "message" -> "An unexpected error occurred")
    }
  }

  "fetchApiDocumentationResource" should {
    "return resource when found" in new Setup {
      ApiDocumentationResourceFetcherMock.willReturnWsResponse(mockWSResponse)

      val result: Future[Result] =
        controller.fetchApiDocumentationResource(apiName, version, "some/resource")(request)

      status(result) shouldEqual OK
    }

    "return resource using content type when no proxy safe content type is present" in new Setup {
      ApiDocumentationResourceFetcherMock.willReturnWsResponse(mockWSResponse)

      when(mockWSResponse.header(eqTo(PROXY_SAFE_CONTENT_TYPE)))
        .thenReturn(None)
      when(mockWSResponse.contentType).thenReturn("application/magic")

      val result: Future[Result] =
        controller.fetchApiDocumentationResource(apiName, version, "some/resource")(request)

      contentType(result) shouldEqual Some("application/magic")
    }

    "return resource using proxy safe content type when present" in new Setup {
      ApiDocumentationResourceFetcherMock.willReturnWsResponse(mockWSResponse)

      when(mockWSResponse.header(eqTo(PROXY_SAFE_CONTENT_TYPE)))
        .thenReturn(Some("application/zip"))

      val result: Future[Result] =
        controller.fetchApiDocumentationResource(apiName, version, "some/resource")(request)

      contentType(result) shouldEqual Some("application/zip")
    }

    "throw NotFoundException when not found" in new Setup {
      ApiDocumentationResourceFetcherMock.willThrowException(new NotFoundException("Not Found"))

      intercept[NotFoundException] {
        await(
          controller.fetchApiDocumentationResource(
            apiName,
            version,
            "some/resourceNotThere"
          )(request)
        )
      }
    }

    "throw InternalServerException for any other response" in new Setup {
      ApiDocumentationResourceFetcherMock.willThrowException(new InternalServerException("Unexpected Error"))

      intercept[InternalServerException] {
        await(
          controller.fetchApiDocumentationResource(
            apiName,
            version,
            "some/resourceInvalid"
          )(request)
        )
      }
    }
  }
}

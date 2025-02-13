package uk.gov.hmrc.apiplatformmicroservice.thirdpartyapplication

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.http._
import play.api.http.Status._
import uk.gov.hmrc.apiplatformmicroservice.common.domain.models.{ApplicationId, Environment}
import uk.gov.hmrc.apiplatformmicroservice.thirdpartyapplication.domain.models.applications.ClientId
import uk.gov.hmrc.apiplatformmicroservice.utils.PrincipalAndSubordinateWireMockSetup

trait ApplicationMock {
  self: PrincipalAndSubordinateWireMockSetup => // To allow for stubFor to work with environment

  def mockFetchApplicationNotFound(env: Environment, applicationId: ApplicationId) {
    stubFor(env)(get(urlEqualTo(s"/application/${applicationId.value}"))
      .willReturn(
        aResponse()
          .withStatus(NOT_FOUND)
      ))
  }

  def mockFetchApplication(deployedTo: Environment, applicationId: ApplicationId, clientId: ClientId = ClientId("dummyProdId")) {
    stubFor(deployedTo)(get(urlEqualTo(s"/application/${applicationId.value}"))
      .willReturn(
        aResponse()
          .withBody(s"""{
                       |  "id": "${applicationId.value}",
                       |  "clientId": "${clientId.value}",
                       |  "gatewayId": "w7dwd9GFZX",
                       |  "name": "giu",
                       |  "deployedTo": "$deployedTo",
                       |  "description": "Some test data",
                       |  "collaborators": [
                       |      {
                       |          "emailAddress": "bobby.taxation@digital.hmrc.gov.uk",
                       |          "role": "ADMINISTRATOR"
                       |      }
                       |  ],
                       |  "createdOn": 1504526587272,
                       |  "lastAccess": 1561071600000,
                       |  "redirectUris": [],
                       |  "access": {
                       |      "accessType": "STANDARD",
                       |      "overrides": [],
                       |      "redirectUris": []
                       |  },
                       |  "state": {
                       |      "name": "PRODUCTION",
                       |      "updatedOn": 1504784641632
                       |  },
                       |  "rateLimitTier": "BRONZE",
                       |  "blocked": false,
                       |  "ipWhitelist": [],
                       |  "ipAllowlist": {
                       |      "required": false,
                       |      "allowlist": []
                       |  },
                       |  "trusted": false
                       |}""".stripMargin)
          .withHeader(HeaderNames.CONTENT_TYPE, MimeTypes.JSON)
          .withStatus(OK)
      ))
  }

  def mockFetchApplicationSubscriptions(env: Environment, applicationId: ApplicationId) {
    stubFor(env)(get(urlEqualTo(s"/application/${applicationId.value}/subscription"))
      .willReturn(
        aResponse()
          .withBody("""
                      |[
                      |    {
                      |        "context": "individual-benefits",
                      |        "version": "1.0"
                      |    },
                      |    {
                      |        "context": "individual-employment",
                      |        "version": "1.0"
                      |    }
                      |]
          """.stripMargin)
          .withHeader(HeaderNames.CONTENT_TYPE, MimeTypes.JSON)
          .withStatus(OK)
      ))
  }
}

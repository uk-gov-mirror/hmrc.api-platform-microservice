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

package uk.gov.hmrc.apiplatformmicroservice.util

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.apiplatformmicroservice.apidefinition.models.APIDefinition
import uk.gov.hmrc.apiplatformmicroservice.apidefinition.services.ApiDefinitionsForCollaboratorFetcher

import scala.concurrent.Future

trait ApiDefinitionsForCollaboratorFetcherModule extends PlaySpec with MockitoSugar with ArgumentMatchersSugar {

  object ApiDefinitionsForCollaboratorFetcherMock {
    val aMock = mock[ApiDefinitionsForCollaboratorFetcher]

    def returnApiDefinitions(apis: APIDefinition*) = {
      when(aMock(*)(*)).thenReturn(Future.successful(apis.toSeq))
    }

    def throwException(e: Exception) = {
      when(aMock(*)(*)).thenReturn(Future.failed(e))
    }
  }

}

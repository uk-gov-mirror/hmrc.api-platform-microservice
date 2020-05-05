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

package uk.gov.hmrc.apiplatformmicroservice.scheduled

import java.util.UUID

import com.typesafe.config.{Config, ConfigFactory}
import org.joda.time.DateTime
import org.mockito.Mockito.{times, verify, verifyNoInteractions, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchersSugar}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.MultiBulkWriteResult
import uk.gov.hmrc.apiplatformmicroservice.connectors.{ProductionThirdPartyApplicationConnector, SandboxThirdPartyApplicationConnector, ThirdPartyDeveloperConnector}
import uk.gov.hmrc.apiplatformmicroservice.models.Environment.Environment
import uk.gov.hmrc.apiplatformmicroservice.models.{Administrator, ApplicationUsageDetails, Environment, UnusedApplication}
import uk.gov.hmrc.apiplatformmicroservice.repository.UnusedApplicationsRepository
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

class UpdateUnusedApplicationRecordsJobSpec extends PlaySpec
  with MockitoSugar with ArgumentMatchersSugar with MongoSpecSupport with FutureAwaits with DefaultAwaitTimeout {

  trait Setup {
    val environmentName = "Test Environment"

    def jobConfiguration(): Config = {
      ConfigFactory.parseString(
        s"""
           |deleteUnusedApplicationsAfter = 365d
           |
           |UpdateUnusedApplicationsRecords-SANDBOX {
           |  startTime = "00:30"
           |  executionInterval = 1d
           |  enabled = false
           |  externalEnvironmentName = "Sandbox"
           |  notifyDeletionPendingInAdvance = 30d
           |}
           |
           |UpdateUnusedApplicationsRecords-PRODUCTION {
           |  startTime = "01:00"
           |  executionInterval = 1d
           |  enabled = false
           |  externalEnvironmentName = "Production"
           |  notifyDeletionPendingInAdvance = 30d
           |}
           |
           |""".stripMargin)
    }

    val mockSandboxThirdPartyApplicationConnector: SandboxThirdPartyApplicationConnector = mock[SandboxThirdPartyApplicationConnector]
    val mockProductionThirdPartyApplicationConnector: ProductionThirdPartyApplicationConnector = mock[ProductionThirdPartyApplicationConnector]
    val mockThirdPartyDeveloperConnector: ThirdPartyDeveloperConnector = mock[ThirdPartyDeveloperConnector]
    val mockUnusedApplicationsRepository: UnusedApplicationsRepository = mock[UnusedApplicationsRepository]

    val reactiveMongoComponent: ReactiveMongoComponent = new ReactiveMongoComponent {
      override def mongoConnector: MongoConnector = mongoConnectorForTest
    }
  }

  trait SandboxJobSetup extends Setup {
    val configuration = new Configuration(jobConfiguration())

    val underTest = new UpdateUnusedSandboxApplicationRecordJob(
      mockSandboxThirdPartyApplicationConnector,
      mockThirdPartyDeveloperConnector,
      mockUnusedApplicationsRepository,
      configuration,
      reactiveMongoComponent
    )
  }

  trait ProductionJobSetup extends Setup {
    val configuration = new Configuration(jobConfiguration())

    val underTest = new UpdateUnusedProductionApplicationRecordJob(
      mockProductionThirdPartyApplicationConnector,
      mockThirdPartyDeveloperConnector,
      mockUnusedApplicationsRepository,
      configuration,
      reactiveMongoComponent
    )
  }

  "notificationCutoffDate" should {
    "correctly calculate date to retrieve applications not used since" in new SandboxJobSetup {
      val expectedCutoffDate = DateTime.now.minusDays(335)

      val calculatedCutoffDate = underTest.notificationCutoffDate()

      calculatedCutoffDate.getMillis must be (expectedCutoffDate.getMillis +- 500) // tolerance of 500 milliseconds
    }
  }

  "SANDBOX job" should {
    "add all newly discovered unused applications to database" in new SandboxJobSetup {
      val adminUserEmail = "foo@bar.com"
      val applicationWithLastUseDate: (ApplicationUsageDetails, UnusedApplication) =
        applicationDetails(Environment.SANDBOX, DateTime.now.minusMonths(13), Some(DateTime.now.minusMonths(13)), Set(adminUserEmail))
      val applicationWithoutLastUseDate: (ApplicationUsageDetails, UnusedApplication) =
        applicationDetails(Environment.SANDBOX, DateTime.now.minusMonths(13), None, Set(adminUserEmail))

      when(mockSandboxThirdPartyApplicationConnector.applicationsLastUsedBefore(*))
        .thenReturn(Future.successful(List(applicationWithLastUseDate._1, applicationWithoutLastUseDate._1)))
      when(mockThirdPartyDeveloperConnector.fetchVerifiedDevelopers(Set(adminUserEmail))).thenReturn(Future.successful(Seq((adminUserEmail, "Foo", "Bar"))))
      when(mockUnusedApplicationsRepository.applicationsByEnvironment(Environment.SANDBOX)).thenReturn(Future(List.empty))

      val insertCaptor: ArgumentCaptor[Seq[UnusedApplication]] = ArgumentCaptor.forClass(classOf[Seq[UnusedApplication]])
      when(mockUnusedApplicationsRepository.bulkInsert(insertCaptor.capture())(*)).thenReturn(Future.successful(MultiBulkWriteResult.empty))

      await(underTest.runJob)

      val capturedInsertValue = insertCaptor.getValue
      capturedInsertValue.size must be (2)
      capturedInsertValue must contain (applicationWithLastUseDate._2)
      capturedInsertValue must contain (applicationWithoutLastUseDate._2)

      verifyNoInteractions(mockProductionThirdPartyApplicationConnector)
    }

    "not persist application details already stored in database" in new SandboxJobSetup {
      val application: (ApplicationUsageDetails, UnusedApplication) =
        applicationDetails(Environment.SANDBOX, DateTime.now.minusMonths(13), Some(DateTime.now.minusMonths(13)), Set())

      when(mockSandboxThirdPartyApplicationConnector.applicationsLastUsedBefore(*))
        .thenReturn(Future.successful(List(application._1)))
      when(mockUnusedApplicationsRepository.applicationsByEnvironment(Environment.SANDBOX)).thenReturn(Future(List(application._2)))

      await(underTest.runJob)

      verify(mockUnusedApplicationsRepository, times(0)).bulkInsert(*)(*)
      verifyNoInteractions(mockThirdPartyDeveloperConnector)
      verifyNoInteractions(mockProductionThirdPartyApplicationConnector)
    }

  }

  "PRODUCTION job" should {
    "add all newly discovered unused applications to database" in new ProductionJobSetup {
      val adminUserEmail = "foo@bar.com"
      val applicationWithLastUseDate: (ApplicationUsageDetails, UnusedApplication) =
        applicationDetails(Environment.PRODUCTION, DateTime.now.minusMonths(13), Some(DateTime.now.minusMonths(13)), Set(adminUserEmail))
      val applicationWithoutLastUseDate: (ApplicationUsageDetails, UnusedApplication) =
        applicationDetails(Environment.PRODUCTION, DateTime.now.minusMonths(13), None, Set(adminUserEmail))

      when(mockProductionThirdPartyApplicationConnector.applicationsLastUsedBefore(*))
        .thenReturn(Future.successful(List(applicationWithLastUseDate._1, applicationWithoutLastUseDate._1)))
      when(mockThirdPartyDeveloperConnector.fetchVerifiedDevelopers(Set(adminUserEmail))).thenReturn(Future.successful(Seq((adminUserEmail, "Foo", "Bar"))))
      when(mockUnusedApplicationsRepository.applicationsByEnvironment(Environment.PRODUCTION)).thenReturn(Future(List.empty))

      val insertCaptor: ArgumentCaptor[Seq[UnusedApplication]] = ArgumentCaptor.forClass(classOf[Seq[UnusedApplication]])
      when(mockUnusedApplicationsRepository.bulkInsert(insertCaptor.capture())(*)).thenReturn(Future.successful(MultiBulkWriteResult.empty))

      await(underTest.runJob)

      val capturedInsertValue = insertCaptor.getValue
      capturedInsertValue.size must be (2)
      capturedInsertValue must contain (applicationWithLastUseDate._2)
      capturedInsertValue must contain (applicationWithoutLastUseDate._2)

      verifyNoInteractions(mockSandboxThirdPartyApplicationConnector)
    }

    "not persist application details already stored in database" in new ProductionJobSetup {
      val application: (ApplicationUsageDetails, UnusedApplication) =
        applicationDetails(Environment.PRODUCTION, DateTime.now.minusMonths(13), Some(DateTime.now.minusMonths(13)), Set())

      when(mockProductionThirdPartyApplicationConnector.applicationsLastUsedBefore(*))
        .thenReturn(Future.successful(List(application._1)))
      when(mockUnusedApplicationsRepository.applicationsByEnvironment(Environment.PRODUCTION)).thenReturn(Future(List(application._2)))

      await(underTest.runJob)

      verify(mockUnusedApplicationsRepository, times(0)).bulkInsert(*)(*)
      verifyNoInteractions(mockThirdPartyDeveloperConnector)
      verifyNoInteractions(mockSandboxThirdPartyApplicationConnector)
    }

  }

  private def applicationDetails(environment: Environment,
                         creationDate: DateTime,
                         lastAccessDate: Option[DateTime],
                         administrators: Set[String]): (ApplicationUsageDetails, UnusedApplication) = {
    val applicationId = UUID.randomUUID()
    val applicationName = Random.alphanumeric.take(10).mkString
    val administratorDetails = administrators.map(admin => Administrator(admin, "Foo", "Bar"))

    (ApplicationUsageDetails(applicationId, applicationName, administrators, creationDate, lastAccessDate),
      UnusedApplication(applicationId, applicationName, administratorDetails, environment, lastAccessDate.getOrElse(creationDate)))
  }
}

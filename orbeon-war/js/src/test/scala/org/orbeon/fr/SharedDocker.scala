package org.orbeon.fr

import org.orbeon.fr.DockerSupport.removeContainerByImage
import org.scalatest.*
import org.scalatest.funspec.FixtureAsyncFunSpecLike

import java.util.concurrent.atomic.AtomicInteger
import scala.async.Async.*


trait SharedDocker extends FixtureAsyncFunSpecLike with ClientTestSupport {

  // Update this count when adding/removing tests
  // (1 from NumberClientTest + 15 from FormRunnerApiClientTest)
  private val TotalTests         = 16
  private val ServerExternalPort = 8888

  type FixtureParam = Unit

  def withFixture(test: OneArgAsyncTest): FutureOutcome = {
    if (SharedDocker.testsStarted.incrementAndGet() == 1)
      async {
        val r = await(runTomcatContainer(
          containerName     =  "FormRunnerTomcat",
          port              = ServerExternalPort,
          checkImageRunning = true,
          network           = None,
          ehcacheFilename   = "ehcache.xml"
        ))
        assert(r.isSuccess)
      }
    complete {
      super.withFixture(test.toNoArgAsyncTest(()))
    } lastly {
      if (SharedDocker.testsStarted.get() == TotalTests) {
        removeContainerByImage(TomcatImageName)
      }
    }
  }
}

object SharedDocker {
  private val testsStarted = new AtomicInteger(0)
}
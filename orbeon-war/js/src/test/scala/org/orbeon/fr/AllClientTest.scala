package org.orbeon.fr

import org.orbeon.fr.DockerSupport.removeContainerByImage
import org.scalatest.*
import org.scalatest.funspec.FixtureAsyncFunSpecLike

import java.util.concurrent.atomic.AtomicInteger
import scala.async.Async.*


class AllClientTest
  extends FixtureAsyncFunSpecLike
  with ClientTestSupport
  with FormRunnerApiTests
  with DynamicDropdownTests
  with NumberControlTests {

  val ServerExternalPort = 8888
  val OrbeonServerUrl    = s"http://localhost:$ServerExternalPort/orbeon"

  type FixtureParam = Unit

  val testsStarted = new AtomicInteger(0)

  def withFixture(test: OneArgAsyncTest): FutureOutcome = {
    if (testsStarted.incrementAndGet() == 1)
      async {
        val r = await(runTomcatContainer(
          containerName     = "FormRunnerTomcat",
          port              = ServerExternalPort,
          checkImageRunning = true,
          network           = None,
          configDirectory   = DefaultConfigDirectory
        ))
        assert(r.isSuccess)
      }
    complete {
      withFixture(test.toNoArgAsyncTest(()))
    } lastly {
      if (testsStarted.get() == testNames.size)
        removeContainerByImage(TomcatImageName)
    }
  }
}

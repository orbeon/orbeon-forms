package org.orbeon.fr

import org.orbeon.fr.DockerSupport.removeContainerByImage
import org.orbeon.web.DomSupport.*
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalatest.*
import org.scalatest.funspec.FixtureAsyncFunSpecLike

import java.util.concurrent.atomic.AtomicInteger
import scala.async.Async.*
import scala.scalajs.js


class NumberClientTest extends FixtureAsyncFunSpecLike with ClientTestSupport {

  val ServerExternalPort = 8888
  val OrbeonServerUrl    = s"http://localhost:$ServerExternalPort/orbeon"

  type FixtureParam = Unit

  val testsStarted = new AtomicInteger(0)

  def withFixture(test: OneArgAsyncTest): FutureOutcome = {
    if (testsStarted.incrementAndGet() == 1)
      async {
        val r = await(runTomcatContainer("FormRunnerTomcat", ServerExternalPort, checkImageRunning = true, network = None, ehcacheFilename = "ehcache.xml"))
        assert(r.isSuccess)
      }
    complete {
      withFixture(test.toNoArgAsyncTest(()))
    } lastly {
      if (testsStarted.get() == testNames.size)
        removeContainerByImage(TomcatImageName)
    }
  }

  describe("Number control") {
    it("must round value to 2 decimal places on blur") { _ =>
      withFormReady(app = "tests", form = "number") { case FormRunnerWindow(xformsWindow, formRunnerApi) =>
        async {

          val form          = formRunnerApi.getForm(js.undefined)
          val controls      = form.findControlsByName("my-number")
          val numberControl = controls.head
          val visibleInput  = numberControl.querySelectorT(".xbl-fr-number-visible-input").asInstanceOf[html.Input]

          visibleInput.focus()
          visibleInput.value = "1.234"
          visibleInput.blur()
          await(xformsWindow.ajaxServer.allEventsProcessedP().toFuture)
          assert(visibleInput.value == "1.23")

          visibleInput.focus()
          visibleInput.value = "1.2345"
          visibleInput.blur()
          await(xformsWindow.ajaxServer.allEventsProcessedP().toFuture)
          assert(visibleInput.value == "1.23")
        }
      }
    }
  }
}

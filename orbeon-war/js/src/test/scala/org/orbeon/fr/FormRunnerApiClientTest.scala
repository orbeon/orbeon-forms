package org.orbeon.fr

import org.orbeon.fr.DockerSupport.removeContainerByImage
import org.scalatest.*
import org.scalatest.funspec.FixtureAsyncFunSpecLike

import java.util.concurrent.atomic.AtomicInteger
import scala.async.Async.*
import scala.scalajs.js


class FormRunnerApiClientTest extends FixtureAsyncFunSpecLike with ClientTestSupport {

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
    // Can't find a way to wait for Tomcat to be ready here as there is no way to directly create or map
    // `FutureOutcome`. So we just launch the container above, then  individual tests have to check for the
    // container and app to be ready.
    complete {
      withFixture(test.toNoArgAsyncTest(()))
    } lastly {
      if (testsStarted.get() == testNames.size)
        removeContainerByImage(TomcatImageName)
    }
  }

  describe("Form Runner client tests") {
    it("must find form controls by name") { _ =>
      withFormReady("control-names") { case FormRunnerWindow(_, formRunnerApi) =>
        async {

          val form = formRunnerApi.getForm(js.undefined)

          assert(form.findControlsByName("first-name").head.classList.contains("xforms-control"))

          assert(form.findControlsByName("last-name").head.classList.contains("xforms-control"))
          assert(form.findControlsByName("i-dont-exist").isEmpty)

          assert(form.findControlsByName("comments").length == 1)
          assert(form.findControlsByName("comments").forall(_.classList.contains("xforms-textarea")))

          await(form.setControlValue("comments", "Hello world!").map(_.toFuture).get)

          // Use same value to make sure that we get a resolving `Promise` in this case as well
          await(form.setControlValue("comments", "Hello world!").map(_.toFuture).get)
          await(form.setControlValue("comments", "Hello world!").map(_.toFuture).get)

          assert(form.getControlValue("comments").contains("Hello world!"))

          await(form.activateControl("add-comment").map(_.toFuture).get)

          // Only one "Add Comment" button
          assert(form.activateControl("add-comment", 1).isEmpty)

          assert(form.findControlsByName("comments").length == 2)
          assert(form.findControlsByName("comments").forall(_.classList.contains("xforms-textarea")))

          await(form.setControlValue("comments", "Hello world, again!", 1).map(_.toFuture).get)
          assert(form.getControlValue("comments", 1).contains("Hello world, again!"))

          assert(form.findControlsByName("comments").map(_.id).sameElements(List("message-section≡grid-3-grid≡comments-control⊙1", "message-section≡grid-3-grid≡comments-control⊙2")))
        }
      }
    }

    it("must pass the strict Wizard focus rules") { _ =>
      withFormReady("wizard") { case FormRunnerWindow(_, formRunnerApi) =>
        async {

          val form = formRunnerApi.getForm(js.undefined)

          // Initial test of control visibility
          assert(form.findControlsByName("control-1").nonEmpty)
          assert(form.findControlsByName("control-2").isEmpty)

          // This must work because the field is visible
          await(formRunnerApi.wizard.focus("control-1").toFuture)

          // NOTE: `document.activeElement` seems to remain at the `body` element, so we cannot test on that. This might
          // be a JSDOM-specific issue.

          // This must fail because the field is not visible and not reachable
          await(formRunnerApi.wizard.focus("control-2").toFuture)

          // `control-2` is not visible
          assert(form.findControlsByName("control-2").isEmpty)

          // After filling `control-1`, `control-2` will be reachable
          form.setControlValue("control-1", "value-1")
          await(formRunnerApi.wizard.focus("control-2").toFuture)

          // `control-2` is visible
          assert(form.findControlsByName("control-2").nonEmpty)
        }
      }
    }
  }
}

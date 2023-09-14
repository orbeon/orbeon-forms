package org.orbeon.fr

import cats.syntax.option._
import org.orbeon.fr.DockerSupport._
import org.scalajs.dom.experimental._
import org.scalatest.funspec.AsyncFunSpec

import scala.async.Async._
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.|


// 2020-10-06: Fetch API is not supported by JSDOM out of the box. We use `node-fetch` as
// implementation.
@js.native
@JSImport("node-fetch", JSImport.Namespace)
object NodeFetch extends js.Function2[RequestInfo, RequestInit, js.Promise[Response]] {
  def apply(arg1: RequestInfo, arg2: RequestInit): js.Promise[Response] = js.native
}

//@js.native
//trait DocumentTrait extends js.Object {
//
//  def getValue(
//    controlIdOrElem : String | html.Element,
//    formElem        : js.UndefOr[html.Element] = js.undefined
//  ): js.UndefOr[String] = js.native
//
//  // Set the value of an XForms control
//  def setValue(
//    controlIdOrElem : String | html.Element,
//    newValue        : String | Double | Boolean,
//    formElem        : js.UndefOr[html.Element] = js.undefined
//  ): Unit = js.native
//}

@js.native
trait AjaxServerTrait extends js.Object {
  def allEventsProcessedP(): js.Promise[Unit] = js.native
}

class OrbeonReplicationTest extends AsyncFunSpec with ClientTestSupport {

  val Server1ExternalPort = 8888
  val Server2ExternalPort = 8889
  val HAProxyExternalPort = 8800

  val OrbeonServer1Url    = s"http://localhost:$Server1ExternalPort/orbeon"
  val OrbeonServer2Url    = s"http://localhost:$Server2ExternalPort/orbeon"
  val OrbeonHAProxyUrl    = s"http://localhost:$HAProxyExternalPort/orbeon"

  val HAProxyImageName    = "haproxy:1.7"

  def updateWindow(window: XFormsWindow, controlId: String, newValue: String | Double | Boolean): Future[Unit] = {
    window.documentAPI.setValue(controlId, newValue)
    window.ajaxServer.allEventsProcessedP().toFuture
  }

  def updateWindowsAndAssert(windows: List[XFormsWindow], line: Int): Future[Unit] = async {

    var rest = windows
    while (rest.nonEmpty) {

      val window = rest.head

      val valueToSet  = line * 10
      val expectedSum = line * (line + 1) / 2 * 10

      await(updateWindow(window, s"quantity-input⊙$line", valueToSet))

      val totalUnits = window.documentAPI.getValue("total-units-output").get
      assert(totalUnits contains expectedSum.toString)

      rest = rest.tail
    }
  }

  describe("The replication setup") {

    // In this test, we simulate two browser windows. The first one accesses the first server via HAProxy. Then the
    // second server is started, and the second window is loaded and accesses the second server. Stopping either of
    // the servers keeps the system working.
    it("handles failover via HAProxy when one or the other server is stopped") {
      async {

        await(createNetworkIfNeeded())

        await {
          withRunContainer(
            HAProxyImageName,
            s"""
              |--name OrbeonHAProxy
              |--network=orbeon_test_nw
              |-it
              |-v $LocalResourcesDir/haproxy:/usr/local/etc/haproxy:delegated
              |-p $HAProxyExternalPort:8080
            """.stripMargin,
            checkImageRunning = true
          ) {
            async {

              val (containerIdA, windowA) =
                locally {
                  // Start image A
                  val tryContainerIdA =
                    await(runTomcatContainer("TomcatA", Server1ExternalPort, checkImageRunning = true))

                  assert(tryContainerIdA.isSuccess)

                  val containerIdA = tryContainerIdA.get.head

                  // Wait for a successful request to image A
                  val s1Cookie = await(waitForServerCookie("s1".some, OrbeonHAProxyUrl))
                  assert(s1Cookie.isSuccess)

                  val windowA =
                    XFormsWindow(await(loadDocumentViaJSDOM("/xforms-espresso/", OrbeonHAProxyUrl, s1Cookie.toOption)))

                  await(updateWindowsAndAssert(List(windowA), 1))
                  await(updateWindowsAndAssert(List(windowA), 2))

                  (containerIdA, windowA)
                }

              val (containerIdB, windowB) =
                locally {
                  // Start image B
                  val tryContainerIdB =
                    await(runTomcatContainer("TomcatB", Server2ExternalPort, checkImageRunning = false))

                  assert(tryContainerIdB.isSuccess)
                  val containerIdB = tryContainerIdB.get.head

                  // Wait for a successful request to TomcatB
                  // We expect `s2` because haproxy rounds robin the requests
                  val s2Cookie = await(waitForServerCookie("s2".some, OrbeonHAProxyUrl))
                  assert(s2Cookie.isSuccess)

                  val windowB =
                    XFormsWindow(await(loadDocumentViaJSDOM("/xforms-espresso/", OrbeonHAProxyUrl, s2Cookie.toOption)))

                  await(updateWindowsAndAssert(List(windowB), 1))
                  await(updateWindowsAndAssert(List(windowB), 2))

                  (containerIdB, windowB)
                }

              // Shutdown image A
              await(removeContainerByIdAndWait(containerIdA))

              // Update windows
              // In both cases, both windows must still work and work off the previous state
              await(updateWindowsAndAssert(List(windowA, windowB), 3))

              // Start image A again
              await(runTomcatContainer("TomcatA", Server1ExternalPort, checkImageRunning = false))
              assert(await(waitForServerCookie("s1".some, OrbeonHAProxyUrl)).isSuccess)

              // Update windows
              // In both cases, both windows must still work and work off the previous state
              await(updateWindowsAndAssert(List(windowA, windowB), 4))

              // Shutdown image B
//              await(delay(5.seconds)) // waiting doesn't seem necessary with the synchronous replication
              await(removeContainerByIdAndWait(containerIdB))

              // Update windows
              // In both cases, both windows must still work and work off the previous state
              await(updateWindowsAndAssert(List(windowA, windowB), 5))

              windowA.window.close()
              windowB.window.close()

              await(removeContainerByImage(TomcatImageName))
            }
          }
        }

        await(removeNetworkIfNeeded())

        assert(true)
      }
    }
  }
}

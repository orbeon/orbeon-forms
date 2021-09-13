/**
  * Copyright (C) 2007 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.fr

import monix.execution.Scheduler.Implicits.global
import org.orbeon.fr.DockerSupport._
import org.orbeon.oxf.util.FutureUtils._
import org.orbeon.xforms.facade.AjaxServerTrait
import org.orbeon.xforms.{InitSupport, facade}
import org.scalajs.dom
import org.scalajs.dom.experimental._
import org.scalajs.dom.raw.Window
import org.scalatest.funspec.AsyncFunSpec

import scala.async.Async._
import scala.collection.compat._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.|
import scala.util.Try


// 2020-10-06: Fetch API is not supported by JSDOM out of the box. We use `node-fetch` as
// implementation.
@js.native
@JSImport("node-fetch", JSImport.Namespace)
object NodeFetch extends js.Function2[RequestInfo, RequestInit, js.Promise[Response]] {
  def apply(arg1: RequestInfo, arg2: RequestInit): js.Promise[Response] = js.native
}

class OrbeonClientTest extends AsyncFunSpec {

  val Server1ExternalPort = 8888
  val Server2ExternalPort = 8889
  val HAProxyExternalPort = 8800

  val OrbeonServer1Url    = s"http://localhost:$Server1ExternalPort/orbeon"
  val OrbeonServer2Url    = s"http://localhost:$Server2ExternalPort/orbeon"
  val OrbeonHAProxyUrl    = s"http://localhost:$HAProxyExternalPort/orbeon"

  val LocalResourcesDir   = "$BASE_DIRECTORY/orbeon-war/js/src/test/resources"
  val ImageTomcatDir      = "/usr/local/tomcat"
  val ImageResourcesDir   = s"$ImageTomcatDir/webapps/orbeon/WEB-INF/resources"
  val TomcatImageName     = "tomcat:8.5-jdk8-openjdk-slim"
  val HAProxyImageName    = "haproxy:1.7"

  val CookieTimeout       = 60.seconds

  case class OrbeonWindow(window: Window, documentAPI: facade.DocumentTrait, ajaxServer: AjaxServerTrait)

  object OrbeonWindow {
    def apply(window: Window): OrbeonWindow = {

      val ORBEON = window.asInstanceOf[js.Dynamic].ORBEON

      OrbeonWindow(
        window       = window,
        documentAPI  = ORBEON.xforms.Document.asInstanceOf[facade.DocumentTrait],
        ajaxServer   = ORBEON.xforms.AjaxClient.asInstanceOf[AjaxServerTrait]
      )
    }
  }

  def updateWindow(window: OrbeonWindow, controlId: String, newValue: String | Double | Boolean): Future[Unit] = {
    window.documentAPI.setValue(controlId, newValue)
    window.ajaxServer.allEventsProcessedP().toFuture
  }

  def updateWindowsAndAssert(windows: List[OrbeonWindow], line: Int): Future[Unit] = async {

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

  def runContainerByImageName(containerName: String, port: Int, checkImageRunning: Boolean): Future[Try[List[String]]] =
    runContainer(
      TomcatImageName,
      s"""
        |--name $containerName
        |--network=$OrbeonDockerNetwork
        |-it
        |-v $$BASE_DIRECTORY/orbeon-war/jvm/target/webapp:$ImageTomcatDir/webapps/orbeon:delegated
        |-v $$HOME/.orbeon/license.xml:/root/.orbeon/license.xml:delegated
        |-v $LocalResourcesDir/config/properties-local.xml:$ImageResourcesDir/config/properties-local.xml:delegated
        |-v $LocalResourcesDir/config/ehcache.xml:$ImageResourcesDir/config/ehcache.xml:delegated
        |-v $LocalResourcesDir/config/log4j.xml:$ImageResourcesDir/config/log4j.xml:delegated
        |-v $LocalResourcesDir/tomcat/server.xml:$ImageTomcatDir/conf/server.xml:delegated
        |-v $LocalResourcesDir/tomcat/setenv.sh:$ImageTomcatDir/bin/setenv.sh:delegated
        |-p $port:8080""".stripMargin,
      checkImageRunning
    )

  def withRunContainer[T](image: String, params: String, checkImageRunning: Boolean)(block: => Future[T]): Future[T] = async {
    await(runContainer(image, params, checkImageRunning))
    val result = await(block)
    await(removeContainerByImage(image))
    result
  }

  implicit override def executionContext: ExecutionContext =
    scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  def simpleServerRequest(
    urlToLoad     : String,
    sessionCookie : Option[String] = None
  ): Future[Response] = {

    val fetchPromise =
      NodeFetch(
        urlToLoad,
        new RequestInit {
          method         = HttpMethod.GET
          body           = js.undefined
          headers        = sessionCookie map (c => js.defined(js.Dictionary("Cookie" -> c))) getOrElse js.undefined
          referrer       = js.undefined
          referrerPolicy = js.undefined
          mode           = js.undefined
          credentials    = js.undefined
          cache          = js.undefined
          redirect       = RequestRedirect.follow // only one supported with the polyfill
          integrity      = js.undefined
          keepalive      = js.undefined
          signal         = js.undefined
          window         = null
        }
      )

    fetchPromise.toFuture filter (_.status == 200)
  }

  def loadDocumentViaJSDOM(
    urlToLoad     : String,
    sessionCookie : Option[String] = None
  ): Future[dom.Window] = {

    val myCookieJar = new CookieJar

    sessionCookie foreach { cookie =>
      myCookieJar.setCookieSync(cookie, OrbeonHAProxyUrl)
    }

    val options = new js.Object {

      val referrer             = OrbeonHAProxyUrl
      val includeNodeLocations = false
      val storageQuota         = 10000000
      val pretendToBeVisual    = true

      val cookieJar            = myCookieJar
      val virtualConsole       = new VirtualConsole().sendTo(g.console.asInstanceOf[js.Object])

      val resources            = "usable"
      val runScripts           = "dangerously" // "outside-only"
    }

    for {
      jsdom <- JSDOM.fromURL(urlToLoad, options).toFuture
      _     <- InitSupport.atLeastDomInteractiveF(jsdom.window.document)
      if jsdom.window.document.querySelector(".orbeon") ne null // this will throw if not satisfied
    } yield
      jsdom.window
  }

  // NOTE: While we retry new server sessions may be created if we hit another server
  def waitForServerCookie(serverPrefix: String): Future[Try[String]] =
    eventuallyAsTry(interval = 5.seconds, timeout = CookieTimeout) {

      simpleServerRequest(s"$OrbeonHAProxyUrl/xforms-espresso/") flatMap { res =>

        val setCookieHeaders =
          Option(res.headers.get("Set-Cookie")).toList

        val HeaderStart = s"JSESSIONID=$serverPrefix~"

        val s2SetCookieHeaderOpt =
          setCookieHeaders find (_.startsWith(HeaderStart))

        s2SetCookieHeaderOpt map
          Future.successful  getOrElse
          Future.failed(new NoSuchElementException(s"No `$HeaderStart` header found"))
      }
    }

  describe("The replication setup") {

    // In this test, we simulate two browser windows. The first one accesses the first server via HAProxy. Then the
    // second server is started, and the second window is loaded and accesses the second server. Stopping either of
    // the servers keeps the system working.
    ignore("handles failover via HAProxy when one or the other server is stopped") {
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
                    await(runContainerByImageName("TomcatA", Server1ExternalPort, checkImageRunning = true))

                  assert(tryContainerIdA.isSuccess)

                  val containerIdA = tryContainerIdA.get.head

                  // Wait for a successful request to image A
                  val s1Cookie = await(waitForServerCookie("s1"))
                  assert(s1Cookie.isSuccess)

                  val windowA =
                    OrbeonWindow(await(loadDocumentViaJSDOM(s"$OrbeonHAProxyUrl/xforms-espresso/", s1Cookie.toOption)))

                  await(updateWindowsAndAssert(List(windowA), 1))
                  await(updateWindowsAndAssert(List(windowA), 2))

                  (containerIdA, windowA)
                }

              val (containerIdB, windowB) =
                locally {
                  // Start image B
                  val tryContainerIdB =
                    await(runContainerByImageName("TomcatB", Server2ExternalPort, checkImageRunning = false))

                  assert(tryContainerIdB.isSuccess)
                  val containerIdB = tryContainerIdB.get.head

                  // Wait for a successful request to TomcatB
                  // We expect `s2` because haproxy rounds robin the requests
                  val s2Cookie = await(waitForServerCookie("s2"))
                  assert(s2Cookie.isSuccess)

                  val windowB =
                    OrbeonWindow(await(loadDocumentViaJSDOM(s"$OrbeonHAProxyUrl/xforms-espresso/", s2Cookie.toOption)))

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
              await(runContainerByImageName("TomcatA", Server1ExternalPort, checkImageRunning = false))
              assert(await(waitForServerCookie("s1")).isSuccess)

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

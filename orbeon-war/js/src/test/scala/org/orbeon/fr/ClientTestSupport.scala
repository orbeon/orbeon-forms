package org.orbeon.fr

import org.orbeon.fr.DockerSupport.{removeContainerByImage, runContainer}
import org.orbeon.oxf.util.FutureUtils.{eventually, eventuallyAsTry}
import org.orbeon.web
import org.scalajs.dom
import org.scalajs.dom.{HttpMethod, RequestInit, RequestRedirect, Response}
import org.scalajs.dom.html
import org.scalajs.dom.Window
import org.scalatest.AsyncTestSuite

import scala.async.Async.{async, await}
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.scalajs.js
import scala.scalajs.js.Dynamic.global as g
import scala.scalajs.js.|
import scala.util.Try


trait DocumentApiTrait extends js.Object {

  def dispatchEvent(eventObject: js.Dictionary[js.Any]): Unit

  def getValue(
    controlIdOrElem : String | html.Element,
    formElem        : js.UndefOr[html.Form] = js.undefined
  ): js.UndefOr[String]

  def setValue(
    controlIdOrElem : String | html.Element,
    newValue        : String | Double | Boolean,
    formElem        : js.UndefOr[html.Form] = js.undefined
  ): Future[Unit]

  def focus(
    controlIdOrElem : String | html.Element,
    formElem        : js.UndefOr[html.Form] = js.undefined
  ): Unit
}

trait FormRunnerApiTrait extends js.Object {
  def getForm(elemOrNamespacedId: js.UndefOr[html.Element | String]): FormRunnerFormTrait
  val wizard      : FormRunnerWizardAPITrait
//  val errorSummary: FormRunnerErrorSummaryAPI.type = FormRunnerErrorSummaryAPI
}

trait FormRunnerWizardAPITrait extends js.Object {
  def focus(
    controlName       : String,
    repeatIndexes     : js.UndefOr[js.Array[Int]] = js.undefined,
    elemOrNamespacedId: js.UndefOr[html.Element | String] = js.undefined
  ): js.Promise[Unit]
}

trait FormRunnerFormTrait extends js.Object {
  def addCallback(name: String, fn: js.Function): Unit
  def removeCallback(name: String, fn: js.Function): Unit
  def isFormDataSafe(): Boolean
  def activateProcessButton(buttonName: String): Unit
  def findControlsByName(controlName: String): js.Array[html.Element]
  def setControlValue(controlName: String, controlValue: String | Int, index: js.UndefOr[Int] = js.undefined): js.UndefOr[js.Promise[Unit]]
  def activateControl(controlName: String, index: js.UndefOr[Int] = js.undefined): js.UndefOr[js.Promise[Unit]]
  def getControlValue(controlName: String, index: js.UndefOr[Int] = js.undefined): js.UndefOr[String]
}

case class XFormsWindow(window: Window, documentAPI: DocumentApiTrait, ajaxServer: AjaxServerTrait)

object XFormsWindow {
  def apply(window: Window): XFormsWindow = {

    val ORBEON = window.asInstanceOf[js.Dynamic].ORBEON

    XFormsWindow(
      window       = window,
      documentAPI  = ORBEON.xforms.Document.asInstanceOf[DocumentApiTrait],
      ajaxServer   = ORBEON.xforms.AjaxClient.asInstanceOf[AjaxServerTrait]
    )
  }
}

case class FormRunnerWindow(xformsWindow: XFormsWindow, formRunnerApi: FormRunnerApiTrait)

object FormRunnerWindow {
  def apply(window: Window): FormRunnerWindow = {

    val ORBEON = window.asInstanceOf[js.Dynamic].ORBEON

    FormRunnerWindow(
      xformsWindow  = XFormsWindow(window),
      formRunnerApi = ORBEON.fr.API.asInstanceOf[FormRunnerApiTrait],
    )
  }
}

trait ClientTestSupport {

  self: AsyncTestSuite =>

  val TomcatImageName         = "tomcat:9.0.109-jdk11-temurin-noble"
  val LocalResourcesDir       = "$BASE_DIRECTORY/orbeon-war/js/src/test/resources"
  val LocalOrbeonResourcesDir = s"$LocalResourcesDir/resources"
  val ImageTomcatDir          = "/usr/local/tomcat"
  val ImageResourcesDir       = s"$ImageTomcatDir/webapps/orbeon/WEB-INF/resources"
  val DefaultConfigDirectory  = "config"

  val CookieTimeout           = 120.seconds

  implicit override def executionContext: scala.concurrent.ExecutionContext =
    org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

  private val ServerExternalPort = 8888
  private val OrbeonServerUrl    = s"http://localhost:$ServerExternalPort/orbeon"

  def withFormRunnerSession[T](body: => Future[T]): Future[T] =
    async {
      val sessionCookie = await(waitForServerCookie(None, OrbeonServerUrl))
      assert(sessionCookie.isSuccess)
      await(body)
    }

  def withFormReady[T](app: String, form: String)(body: FormRunnerWindow => Future[T]): Future[T] =
    withFormRunnerSession {
      async {

        val sessionCookie = await(waitForServerCookie(None, OrbeonServerUrl))
        assert(sessionCookie.isSuccess)

        val window =
          FormRunnerWindow(await(loadDocumentViaJSDOM(s"/fr/$app/$form/new", OrbeonServerUrl, sessionCookie.toOption)))

        // Wait until there is a form returned by the API
        await {
          eventually(1.second, 10.seconds) {
            assert(window.formRunnerApi.getForm(js.undefined).asInstanceOf[js.UndefOr[FormRunnerFormTrait]].isDefined)
          }
        }

        await(body(window))
      }
    }

  protected def runTomcatContainer(
    containerName    : String,
    port             : Int,
    checkImageRunning: Boolean,
    network          : Option[String],
    configDirectory  : String
  ): Future[Try[List[String]]] =
    runContainer(
      TomcatImageName,
      containerName,
      s"""
        |${network.map(n => s"--network=$n ").getOrElse("")}-it
        |--mount type=bind,src=$$BASE_DIRECTORY/orbeon-war/jvm/target/webapp,dst=$ImageTomcatDir/webapps/orbeon
        |--mount type=bind,src=$$HOME/.orbeon/license.xml,dst=/root/.orbeon/license.xml,ro
        |--mount type=bind,src=$$BASE_DIRECTORY/orbeon-war/js/src/test/resources/tomcat/orbeon.xml,dst=$ImageTomcatDir/conf/Catalina/localhost/orbeon.xml,ro
        |--mount type=bind,src=$LocalOrbeonResourcesDir/forms,dst=$ImageTomcatDir/webapps/orbeon/WEB-INF/test-resources/forms,ro
        |--mount type=bind,src=$LocalOrbeonResourcesDir/$configDirectory,dst=$ImageTomcatDir/webapps/orbeon/WEB-INF/test-resources/config,ro
        |--mount type=bind,src=$LocalResourcesDir/tomcat/server.xml,dst=$ImageTomcatDir/conf/server.xml,ro
        |--mount type=bind,src=$LocalResourcesDir/tomcat/setenv.sh,dst=$ImageTomcatDir/bin/setenv.sh,ro
        |-p $port:8080""".stripMargin,
      checkImageRunning
    )

  def withRunTomcatContainer[T](containerName: String, port: Int, checkImageRunning: Boolean, network: Option[String])(block: => Future[T]): Future[T] = async {
    val r = await(runTomcatContainer(containerName, port, checkImageRunning, network, configDirectory = DefaultConfigDirectory))
    assert(r.isSuccess)
    val result = await(block)
    await(removeContainerByImage(TomcatImageName))
    result
  }

  def withRunContainer[T](image: String, containerName: String, params: String, checkImageRunning: Boolean)(block: => Future[T]): Future[T] = async {
    await(runContainer(image, containerName, params, checkImageRunning))
    val result = await(block)
    await(removeContainerByImage(image))
    result
  }

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
    pathToLoad   : String,
    serverUrl    : String,
    sessionCookie: Option[String] = None,
  ): Future[dom.Window] = {

    val myCookieJar = new CookieJar

    sessionCookie foreach { cookie =>
      myCookieJar.setCookieSync(cookie, serverUrl)
    }

    val options = new js.Object {

      val referrer             = serverUrl
      val includeNodeLocations = false
      val storageQuota         = 10000000
      val pretendToBeVisual    = true

      val cookieJar            = myCookieJar
      val virtualConsole       = new VirtualConsole().sendTo(g.console.asInstanceOf[js.Object], new js.Object {
        val omitJSDOMErrors = true
      })

      val resources            = "usable"
      val runScripts           = "dangerously" // "outside-only"
    }

    for {
      jsdom <- JSDOM.fromURL(serverUrl + pathToLoad, options).toFuture
      _     <- web.DomSupport.atLeastDomReadyStateF(jsdom.window.document, web.DomSupport.DomReadyState.Interactive)
      if jsdom.window.document.querySelector(".orbeon") ne null // this will throw if not satisfied
    } yield
      jsdom.window
  }

  // NOTE: While we retry new server sessions may be created if we hit another server
  def waitForServerCookie(serverPrefix: Option[String], serverUrl: String): Future[Try[String]] =
    eventuallyAsTry(interval = 5.seconds, timeout = CookieTimeout) {

      simpleServerRequest(s"$serverUrl/xforms-espresso/") flatMap { res =>

        val setCookieHeaders =
          Option(res.headers.get("Set-Cookie")).toList

        val HeaderStart = s"JSESSIONID=${serverPrefix.map(_ + '~').getOrElse("")}"

        val s2SetCookieHeaderOpt =
          setCookieHeaders find (_.startsWith(HeaderStart))

        s2SetCookieHeaderOpt map
          Future.successful  getOrElse
          Future.failed(new NoSuchElementException(s"No `$HeaderStart` header found"))
      }
    }
}

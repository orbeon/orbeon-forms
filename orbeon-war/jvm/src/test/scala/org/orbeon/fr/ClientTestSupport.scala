package org.orbeon.fr

import com.microsoft.playwright.*
import com.microsoft.playwright.options.Cookie
import org.orbeon.fr.DockerSupport.{eventually, eventuallyAsTry, removeContainerByImage, runContainer}
import org.scalatest.Suite

import java.io.File
import java.net.{HttpCookie, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.util.Try


class DocumentApi(page: Page) {

  def getValue(controlId: String): Option[String] =
    Option(
      page.evaluate(
        "([id]) => ORBEON.xforms.Document.getValue(id)",
        java.util.Arrays.asList(controlId)
      ).asInstanceOf[String]
    )

  def setValue(controlId: String, newValue: Any): Unit =
    page.evaluate(
      "([id, v]) => ORBEON.xforms.Document.setValue(id, v)",
      java.util.Arrays.asList(controlId, newValue)
    )
}

class AjaxServer(page: Page) {
  def waitForAllEventsProcessed(): Unit =
    page.evaluate("() => ORBEON.xforms.AjaxClient.allEventsProcessedP()")
}

class FormRunnerWizardAPI(page: Page) {
  def focus(controlName: String): Unit =
    page.evaluate(
      "([name]) => ORBEON.fr.API.wizard.focus(name)",
      java.util.Arrays.asList(controlName)
    )
}

class FormRunnerForm(page: Page) {

  def findControlsByName(controlName: String): List[ElementHandle] = {
    val arrayHandle = page.evaluateHandle(
      "([name]) => ORBEON.fr.API.getForm().findControlsByName(name)",
      java.util.Arrays.asList(controlName)
    )
    arrayHandle.getProperties.values().asScala.flatMap(h => Option(h.asElement())).toList
  }

  def setControlValue(controlName: String, controlValue: Any, index: Option[Int] = None): Option[Unit] = {
    val resolved = page.evaluate(
      """([name, value, idx]) => {
        |  const p = ORBEON.fr.API.getForm().setControlValue(name, value, idx !== null ? idx : undefined);
        |  return p !== undefined ? p.then(() => true) : false;
        |}""".stripMargin,
      java.util.Arrays.asList(controlName, controlValue, index.map(Int.box).orNull)
    ).asInstanceOf[Boolean]
    if (resolved) Some(()) else None
  }

  def setControlValue(controlName: String, controlValue: Any, index: Int): Option[Unit] =
    setControlValue(controlName, controlValue, Some(index))

  def activateControl(controlName: String, index: Option[Int] = None): Option[Unit] = {
    val resolved = page.evaluate(
      """([name, idx]) => {
        |  const p = ORBEON.fr.API.getForm().activateControl(name, idx !== null ? idx : undefined);
        |  return p !== undefined ? p.then(() => true) : false;
        |}""".stripMargin,
      java.util.Arrays.asList(controlName, index.map(Int.box).orNull)
    ).asInstanceOf[Boolean]
    if (resolved) Some(()) else None
  }

  def activateControl(controlName: String, index: Int): Option[Unit] =
    activateControl(controlName, Some(index))

  def getControlValue(controlName: String, index: Option[Int] = None): Option[String] =
    Option(
      page.evaluate(
        """([name, idx]) => {
          |  const v = ORBEON.fr.API.getForm().getControlValue(name, idx !== null ? idx : undefined);
          |  return v !== undefined ? v : null;
          |}""".stripMargin,
        java.util.Arrays.asList(controlName, index.map(Int.box).orNull)
      ).asInstanceOf[String]
    )

  def getControlValue(controlName: String, index: Int): Option[String] =
    getControlValue(controlName, Some(index))
}

class FormRunnerApi(page: Page) {
  def getForm: FormRunnerForm = new FormRunnerForm(page)
  def isSameFormReference: Boolean =
    page.evaluate("() => ORBEON.fr.API.getForm() === ORBEON.fr.API.getForm()").asInstanceOf[Boolean]
  val wizard: FormRunnerWizardAPI = new FormRunnerWizardAPI(page)
}

case class XFormsWindow(page: Page, documentAPI: DocumentApi, ajaxServer: AjaxServer) {
  def close(): Unit = page.context().close()
}

object XFormsWindow {
  def apply(page: Page): XFormsWindow =
    XFormsWindow(
      page        = page,
      documentAPI = new DocumentApi(page),
      ajaxServer  = new AjaxServer(page)
    )
}

case class FormRunnerWindow(xformsWindow: XFormsWindow, formRunnerApi: FormRunnerApi)

object FormRunnerWindow {
  def apply(page: Page): FormRunnerWindow =
    FormRunnerWindow(
      xformsWindow  = XFormsWindow(page),
      formRunnerApi = new FormRunnerApi(page)
    )
}

object PlaywrightDomSupport {

  implicit class ElementHandleOps(private val elem: ElementHandle) extends AnyVal {
    def querySelectorT(selector: String): ElementHandle =
      Option(elem.querySelector(selector)).get

    def querySelectorOpt(selector: String): Option[ElementHandle] =
      Option(elem.querySelector(selector))

    def querySelectorAllT(selector: String): List[ElementHandle] =
      elem.querySelectorAll(selector).asScala.toList

    def hasClass(className: String): Boolean =
      elem.evaluate("(el, c) => el.classList.contains(c)", className).asInstanceOf[Boolean]

    def id: String =
      Option(elem.getAttribute("id")).getOrElse("")

    def value: String =
      elem.inputValue()

    def value_=(v: String): Unit =
      elem.evaluate("(el, v) => { el.value = v; }", v)

    def blur(): Unit =
      elem.evaluate("el => el.blur()")
  }

  implicit class PageOps(private val page: Page) extends AnyVal {
    def querySelectorT(selector: String): ElementHandle =
      Option(page.querySelector(selector)).get

    def querySelectorOpt(selector: String): Option[ElementHandle] =
      Option(page.querySelector(selector))

    def querySelectorAllT(selector: String): List[ElementHandle] =
      page.querySelectorAll(selector).asScala.toList
  }
}

trait ClientTestSupport {

  self: Suite =>

  import Private.*

  val TomcatImageName         = "tomcat:9.0.109-jdk17-temurin-noble"
  val LocalResourcesDir       = "$BASE_DIRECTORY/orbeon-war/jvm/src/test/resources"
  val LocalOrbeonResourcesDir = s"$LocalResourcesDir/resources"
  val ImageTomcatDir          = "/usr/local/tomcat"
  val ImageResourcesDir       = s"$ImageTomcatDir/webapps/orbeon/WEB-INF/resources"
  val DefaultConfigDirectory  = "config"

  val CookieTimeout           = 300.seconds
  val RequestTimeout          = 120.seconds
  val PageLoadTimeout         = 120.seconds

  def withFormRunnerSession[T](body: => T): T = {
    val sessionCookie = waitForServerCookie(None, OrbeonServerUrl)
    assert(sessionCookie.isSuccess)
    body
  }

  def withFormReady[T](
    app            : String,
    form           : String,
    mode           : String = "new",
    queryStringOpt : Option[String] = None,
    timezoneIdOpt  : Option[String] = None
  )(
    body           : FormRunnerWindow => T
  ): T = {
    val sessionCookie = waitForServerCookie(None, OrbeonServerUrl)
    assert(sessionCookie.isSuccess)

    val path   = s"/fr/$app/$form/$mode" + queryStringOpt.map(qs => s"?$qs").getOrElse("")
    val page   = loadDocumentViaPlaywright(path, OrbeonServerUrl, sessionCookie.toOption, timezoneIdOpt)
    val window = FormRunnerWindow(page)

    // Wait until there is a form returned by the API
    eventually(1.second, 10.seconds) {
      assert(page.evaluate("() => Boolean(ORBEON.fr && ORBEON.fr.API && ORBEON.fr.API.getForm())").asInstanceOf[Boolean])
    }

    try {
      body(window)
    } finally {
      window.xformsWindow.close()
    }
  }

  protected def runTomcatContainer(
    containerName    : String,
    port             : Int,
    checkImageRunning: Boolean,
    network          : Option[String],
    configDirectory  : String
  ): Try[List[String]] = {

    val mounts =
      List(
        mount(s"$$BASE_DIRECTORY/orbeon-war/jvm/target/webapp"                       , s"$ImageTomcatDir/webapps/orbeon", ro = false),
        mount(s"$$BASE_DIRECTORY/orbeon-war/jvm/src/test/resources/tomcat/orbeon.xml", s"$ImageTomcatDir/conf/Catalina/localhost/orbeon.xml"),
        mount(s"$LocalOrbeonResourcesDir/forms"                                      , s"$ImageTomcatDir/webapps/orbeon/WEB-INF/test-resources/forms"),
        mount(s"$LocalOrbeonResourcesDir/$configDirectory"                           , s"$ImageTomcatDir/webapps/orbeon/WEB-INF/test-resources/config"),
        mount(s"$LocalResourcesDir/tomcat/server.xml"                                , s"$ImageTomcatDir/conf/server.xml"),
        mount(s"$LocalResourcesDir/tomcat/setenv.sh"                                 , s"$ImageTomcatDir/bin/setenv.sh"),
      ) ::: (if (licenseFileExists) List(mount(LicensePath, "/root/.orbeon/license.xml")) else Nil)

    val args = (
      network.map(n => s"--network=$n").toList :::
      List("-it")                              :::
      mounts                                   :::
      List(s"-p $port:8080")
    ).mkString(" ")

    runContainer(
      TomcatImageName,
      containerName,
      args,
      checkImageRunning
    )
  }

  def withRunTomcatContainer[T](containerName: String, port: Int, checkImageRunning: Boolean, network: Option[String])(block: => T): T = {
    val r = runTomcatContainer(containerName, port, checkImageRunning, network, configDirectory = DefaultConfigDirectory)
    assert(r.isSuccess)
    try {
      block
    } finally {
      removeContainerByImage(TomcatImageName)
    }
  }

  def withRunContainer[T](image: String, containerName: String, params: String, checkImageRunning: Boolean)(block: => T): T = {
    runContainer(image, containerName, params, checkImageRunning)
    try {
      block
    } finally {
      removeContainerByImage(image)
    }
  }

  def simpleServerRequest(
    urlToLoad     : String,
    sessionCookie : Option[String] = None
  ): HttpResponse[String] = {
    val client = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NORMAL)
      .connectTimeout(java.time.Duration.ofSeconds(RequestTimeout.toSeconds))
      .build()

    val requestBuilder = HttpRequest.newBuilder()
      .uri(URI.create(urlToLoad))
      .timeout(java.time.Duration.ofSeconds(RequestTimeout.toSeconds))
      .GET()

    sessionCookie.foreach(c => requestBuilder.header("Cookie", c))

    val response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
    assert(response.statusCode() == 200)
    response
  }

  def loadDocumentViaPlaywright(
    pathToLoad   : String,
    serverUrl    : String,
    sessionCookie: Option[String] = None,
    timezoneIdOpt: Option[String] = None
  ): Page = {
    val browser = ensureBrowser()
    val contextOptions = new Browser.NewContextOptions()
      .setTimezoneId(timezoneIdOpt.getOrElse("America/Los_Angeles"))
    val context = browser.newContext(contextOptions)

    sessionCookie.foreach { cookieHeader =>
      HttpCookie.parse(cookieHeader).asScala.headOption.foreach { httpCookie =>
        val pwCookie = new Cookie(httpCookie.getName, httpCookie.getValue)
          .setDomain(URI.create(serverUrl).getHost)
          .setPath(Option(httpCookie.getPath).getOrElse("/orbeon"))
        context.addCookies(java.util.Collections.singletonList(pwCookie))
      }
    }

    val page = context.newPage()
    page.setDefaultTimeout(PageLoadTimeout.toMillis.toDouble)
    page.onConsoleMessage(msg => println(s"[Browser Console] ${msg.text()}"))
    page.onPageError(err => println(s"[Browser PageError] $err"))

    page.navigate(serverUrl + pathToLoad)
    page.waitForSelector(".orbeon")
    page.waitForFunction("() => typeof ORBEON !== 'undefined' && typeof ORBEON.xforms !== 'undefined'")
    page
  }

  protected def closePlaywright(): Unit = {
    browserOpt.foreach(_.close())
    playwrightOpt.foreach(_.close())
    browserOpt    = None
    playwrightOpt = None
  }

  // NOTE: While we retry new server sessions may be created if we hit another server
  def waitForServerCookie(serverPrefix: Option[String], serverUrl: String): Try[String] =
    eventuallyAsTry(interval = 5.seconds, timeout = CookieTimeout) {
      val res              = simpleServerRequest(s"$serverUrl/xforms-espresso/")
      val setCookieHeaders = collectSetCookieHeaders(res)
      val HeaderStart      = s"JSESSIONID=${serverPrefix.map(_ + '~').getOrElse("")}"

      setCookieHeaders
        .find(_.startsWith(HeaderStart))
        .getOrElse(throw new NoSuchElementException(s"No `$HeaderStart` header found"))
    }

  private object Private {

    val ServerExternalPort = 8888
    val OrbeonServerUrl    = s"http://localhost:$ServerExternalPort/orbeon"
    val LicensePath        = s"${System.getProperty("user.home")}/.orbeon/license.xml"

    var playwrightOpt: Option[Playwright] = None
    var browserOpt   : Option[Browser]    = None

    def ensureBrowser(): Browser =
      browserOpt.getOrElse {
        val pw = Playwright.create()
        val br = pw.chromium().launch()
        playwrightOpt = Some(pw)
        browserOpt    = Some(br)
        br
      }

    def licenseFileExists: Boolean =
      new File(LicensePath).exists()

    def mount(src: String, dst: String, ro: Boolean = true): String =
      s"--mount type=bind,src=$src,dst=$dst${if (ro) ",ro" else ""}"

    def collectSetCookieHeaders(res: HttpResponse[?]): List[String] =
      res.headers().allValues("Set-Cookie").asScala.toList ++
        res.previousResponse().toScala.toList.flatMap(collectSetCookieHeaders)
  }
}

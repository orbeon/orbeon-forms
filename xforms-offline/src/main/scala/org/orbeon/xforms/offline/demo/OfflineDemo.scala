package org.orbeon.xforms.offline.demo

import org.log4s.Logger
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.library.{EXFormsFunctions, XFormsFunctionLibrary, XXFormsFunctionLibrary}
import org.orbeon.oxf.xforms.processor.XFormsURIResolver
import org.orbeon.oxf.xforms.processor.handlers.XHTMLOutput
import org.orbeon.oxf.xforms.state.XFormsStateManager
import org.orbeon.oxf.xforms.{RequestInformation, XFormsContainingDocument, XFormsStaticState, XFormsStaticStateDeserializer}
import org.orbeon.oxf.xml.XMLReceiverAdapter
import org.orbeon.saxon.functions.{FunctionLibrary, FunctionLibraryList}
import org.orbeon.xforms.EmbeddingSupport._
import org.orbeon.xforms._
import org.scalajs.dom
import org.scalajs.dom.experimental.URL
import org.scalajs.dom.{XMLHttpRequest, html}
import org.xml.sax.Attributes

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.scalajs.js.|


@JSExportTopLevel("OrbeonOffline")
object OfflineDemo extends App {

  val logger: Logger = LoggerFactory.createLogger("org.orbeon.offline.OfflineDemo")
  implicit val indentedLogger = new IndentedLogger(logger, true)

  type SerializedForm = String
  type CompiledForm   = XFormsStaticState
  type RuntimeForm    = String

  def onOrbeonApiLoaded(): Unit = {
    XFormsApp.onOrbeonApiLoaded(LocalClientServerChannel)

    info("Orbeon API loaded")

    // Expose the API in the usual place
    val orbeonDyn = g.window.ORBEON
    orbeonDyn.xforms.Offline = js.Dynamic.global.OrbeonOffline

    // Initialize logging
//    setLoggerThreshold("", LevelThreshold(Info))
  }

  def onPageContainsFormsMarkup(): Unit =
    XFormsApp.onPageContainsFormsMarkup()

  @JSExport
  def helloDemoForm: String = DemoForms.HelloForm

  @JSExport
  def multipleFieldsDemoForm: String = DemoForms.MultipleFieldsForm

  @JSExport
  def destroyForm(container: html.Element): Unit =
    EmbeddingSupport.destroyForm(container)
    // TODO: Remove from `XFormsStateManager`.

  @JSExport
  def testLoadAndRenderForm(
    container : html.Element,
    formName  : String
  ): Unit =
    fetchSerializedFormForTesting(s"$findBasePathForTesting/xforms-compiler/service/compile/$formName.xhtml") foreach { compiledForm =>
      println(s"xxx fetched string length: ${compiledForm.size}")
      renderDemoForm(container, compiledForm)
    }

  val XFormsFunctionLibraries =
    List(
      XFormsFunctionLibrary,
      XXFormsFunctionLibrary,
      EXFormsFunctions
    )

  private val XFormsFunctionLibraryList: FunctionLibrary =
    new FunctionLibraryList |!>
      (fll => XFormsFunctionLibraries.iterator.foreach(fll.addFunctionLibrary))

  @JSExport
  def renderDemoForm(
    container    : html.Element,
    serializedForm : SerializedForm,
  ): RuntimeForm =
    renderCompiledForm(container, serializedForm, XFormsFunctionLibraryList, None)

  @JSExport
  def compileForm(
    serializedForm  : SerializedForm,
    functionLibrary : FunctionLibrary
  ): CompiledForm =
    withDebug("form deserialization") {
      XFormsStaticStateDeserializer.deserialize(serializedForm, functionLibrary)
    }

  def renderCompiledForm(
    container       : html.Element,
    inputForm       : SerializedForm | CompiledForm,
    functionLibrary : FunctionLibrary,
    uriResolver     : Option[XFormsURIResolver]
  ): RuntimeForm = {

    withDebug("form initialization and rendering") {
      destroyForm(container)

      val newUuid = CoreCrossPlatformSupport.randomHexId

      val staticState =
        (inputForm: Any) match {
          case serialized: SerializedForm => compileForm(serialized, functionLibrary)
          case compiled  : CompiledForm   => compiled
        }

      val containingDocument =
        new XFormsContainingDocument(staticState, newUuid, disableUpdates = false)

      val req =
        RequestInformation(
          deploymentType        = DeploymentType.Standalone,
          requestMethod         = HttpMethod.GET,
          requestContextPath    = "/orbeon",
          requestPath           = "/demo",
          requestHeaders        = Map.empty,
          requestParameters     = Map.empty,
          containerType         = "servlet", // TODO: not really used except for `isPortletContainer`
          containerNamespace    = "", // TODO: no prefix for now, so the CSS works
          versionedPathMatchers = Nil,
          isEmbedded            = true,
          forceInlineResources  = true
        )

      CoreCrossPlatformSupport.withExternalContext(DemoExternalContext.newExternalContext) {

        withDebug("XFormsContainingDocument.initialize") {
          containingDocument.setRequestInformation(req)
          containingDocument.initialize(uriResolver, response = None)
        }

        // See also `XFormsToXHTML.outputResponseDocument`
        XFormsAPI.withContainingDocument(containingDocument) {

          val nonJavaScriptLoads =
            containingDocument.getNonJavaScriptLoadsToRun

          if (containingDocument.isGotSubmissionReplaceAll) {
            // 1. Got a submission with replace="all"
            // NOP: Response already sent out by a submission
    //        indentedLogger.logDebug("", "handling response for submission with replace=\"all\"")

            println(s"xxx `isGotSubmissionReplaceAll`")

          } else if (nonJavaScriptLoads.nonEmpty) {
            // 2. Got at least one xf:load which is not a JavaScript call

            // This is the "load upon initialization in Servlet container, embedded or not" case.
            // See `XFormsLoadAction` for details.
  //          val location = nonJavaScriptLoads.head.resource
    //        indentedLogger.logDebug("", "handling redirect response for xf:load", "url", location)
    //        externalContext.getResponse.sendRedirect(location, isServerSide = false, isExitPortal = false)

            // Set isNoRewrite to true, because the resource is either a relative path or already contains the servlet context
    //        SAXUtils.streamNullDocument(xmlReceiver)

            println(s"xxx `nonJavaScriptLoad s`")

          } else {
            // 3. Regular case: produce a document
            containingDocument.hostLanguage match {
              case "xhtml" =>

                val rcv = new DomDocumentFragmentXMLReceiver
                withDebug("generate markup") {
                  XHTMLOutput.send(containingDocument, staticState.template.get, CoreCrossPlatformSupport.externalContext)(rcv)
                }

                // Find CSS/JS to load
                // TODO: For now, don't load any extra CSS as it will likely not be found. At the very least we would need to
                //   have the list of offline CSS baseline assets and not insert the CSS if already present.
  //              val stylesheetsToLoad = findAndDetachCssToLoad(rcv.frag)
                val stylesheetsToLoad = Nil
                val scriptsToLoad     = findAndDetachJsToLoad (rcv.frag)

                // Asynchronously load styles, insert HTML, then load scripts
                for {
  //                _ <- loadStylesheets(stylesheetsToLoad) // XXX TODO
                  _ <- Future()
                  _ =  moveChildren(source = rcv.frag.querySelector("body"), target = container)
                  _ <- loadScripts(scriptsToLoad)
                } yield ()

                containingDocument.afterInitialResponse()

                // Notify state manager
                XFormsStateManager.afterInitialResponse(containingDocument, disableDocumentCache = false)

              case unknown =>
                throw new OXFException(s"Unknown host language specified: $unknown")
            }
          }
        }
      }

      newUuid
    }
  }

  def fetchSerializedFormForTesting(url: String): Future[String] = {
    val p = Promise[String]()
    val xhr = new XMLHttpRequest()
    xhr.open(
      method = "GET",
      url    = url
    )
    xhr.onload = { _ =>
      p.success(xhr.responseText)
    }
    xhr.send()

    p.future
  }

  def findBasePathForTesting: String = {
    val location = g.window.location.asInstanceOf[URL]
    location.origin + "/" + location.pathname.split("/")(1) // assume only a simple context path
  }
}


// This receiver stores its result in an `dom.DocumentFragment` which can later be efficiently inserted into
// the main document.
class DomDocumentFragmentXMLReceiver extends XMLReceiverAdapter {

  private val doc = dom.window.document

  val frag: dom.DocumentFragment = doc.createDocumentFragment()

  private var stack: List[dom.Node] = List(frag)

  override def startElement(uri: String, localName: String, qName: String, atts: Attributes): Unit = {

    val newElem = doc.createElement(localName)

    for (i <- 0 until atts.getLength)
      newElem.setAttribute(atts.getLocalName(i), atts.getValue(i))

    stack.head.appendChild(newElem)
    stack ::= newElem
  }

  override def endElement(uri: String, localName: String, qName: String): Unit =
    stack = stack.tail

  override def characters(ch: Array[Char], start: Int, length: Int): Unit =
    stack.head.appendChild(doc.createTextNode(new String(ch, start, length)))
}

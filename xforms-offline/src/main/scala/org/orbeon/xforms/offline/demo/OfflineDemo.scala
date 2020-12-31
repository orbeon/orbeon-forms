package org.orbeon.xforms.offline.demo

import org.log4s.Info
import org.log4s.log4sjs.LevelThreshold
import org.log4s.log4sjs.LogThreshold.AllThreshold
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger}
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.processor.handlers.XHTMLOutput
import org.orbeon.oxf.xforms.state.XFormsStateManager
import org.orbeon.oxf.xforms.{Loggers, RequestInformation, XFormsContainingDocument, XFormsStaticStateDeserializer}
import org.orbeon.oxf.xml.XMLReceiverAdapter
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.xforms.EmbeddingSupport._
import org.orbeon.xforms._
import org.orbeon.oxf.xforms.processor.XFormsURIResolver
import org.scalajs.dom
import org.scalajs.dom.html
import org.xml.sax.Attributes

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}


@JSExportTopLevel("OrbeonOffline")
object OfflineDemo extends App {

  type CompiledForm = String
  type RuntimeForm  = String

  def onOrbeonApiLoaded(): Unit = {
    XFormsApp.onOrbeonApiLoaded(LocalClientServerChannel)

    // Expose the API in the usual place
    val orbeonDyn = g.window.ORBEON
    orbeonDyn.xforms.Offline = js.Dynamic.global.OrbeonOffline

    // Initialize logging
    import org.log4s.log4sjs.Log4sConfig._
    setLoggerThreshold("", LevelThreshold(Info))
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
  def renderDemoForm(
    container    : html.Element,
    compiledForm : CompiledForm,
  ): RuntimeForm =
    renderCompiledForm(container, compiledForm, Nil, None)

  def renderCompiledForm(
    container    : html.Element,
    compiledForm : CompiledForm,
    libraries    : Iterable[FunctionLibrary],
    uriResolver  : Option[XFormsURIResolver]
  ): RuntimeForm = {

    implicit val logger: IndentedLogger = Loggers.getIndentedLogger("offline")

    destroyForm(container)

    val uuid = CoreCrossPlatformSupport.randomHexId
    val staticState = XFormsStaticStateDeserializer.deserialize(compiledForm, libraries)

    val containingDocument = new XFormsContainingDocument(staticState, uuid, disableUpdates = false)

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

      containingDocument.setRequestInformation(req)
      containingDocument.initialize(uriResolver, response = None)

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
              XHTMLOutput.send(containingDocument, staticState.template.get, CoreCrossPlatformSupport.externalContext)(rcv)

              // Find CSS/JS to load
              val stylesheetsToLoad = findAndDetachCssToLoad(rcv.frag)
              val scriptsToLoad     = findAndDetachJsToLoad (rcv.frag)

              // Asynchronously load styles, insert HTML, then load scripts
              for {
                _ <- loadStylesheets(stylesheetsToLoad)
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

    uuid
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

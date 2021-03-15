package org.orbeon.xforms.offline

import org.log4s.Logger
import org.orbeon.facades.TextDecoder
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.http.{Headers, HttpMethod}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.{ContentTypes, CoreCrossPlatformSupport, IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.library.{EXFormsFunctions, SaxonFunctionLibrary, XFormsFunctionLibrary, XXFormsFunctionLibrary}
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

import java.{lang => jl}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.scalajs.js.Dynamic.{global => g}
import scala.scalajs.js.annotation.JSExport
import scala.scalajs.js.typedarray.{ArrayBuffer, Uint8Array}
import scala.scalajs.js.|


object OfflineSupport {

  val logger: Logger = LoggerFactory.createLogger("org.orbeon.offline.OfflineSupport")
  implicit val indentedLogger = new IndentedLogger(logger, true)

  type SerializedBundle = String | Uint8Array
  type SerializedForm   = String
  type CompiledForm     = XFormsStaticState
  type RuntimeForm      = String

  def destroyForm(container: html.Element): Unit =
    EmbeddingSupport.destroyForm(container)
    // TODO: Remove from `XFormsStateManager`.

  val XFormsFunctionLibraries =
    List(
      XFormsFunctionLibrary,
      XXFormsFunctionLibrary,
      EXFormsFunctions,
      SaxonFunctionLibrary
    )

  val XFormsFunctionLibraryList: FunctionLibrary =
    new FunctionLibraryList |!>
      (fll => XFormsFunctionLibraries.iterator.foreach(fll.addFunctionLibrary))

  @JSExport
  def compileForm(
    serializedForm  : SerializedForm,
    functionLibrary : FunctionLibrary
  ): CompiledForm =
    withDebug("form deserialization") {
      XFormsStaticStateDeserializer.deserialize(serializedForm, functionLibrary)
    }

  def renderCompiledForm(
    containerOpt    : Option[html.Element],
    inputForm       : SerializedForm | CompiledForm,
    functionLibrary : FunctionLibrary,
    uriResolver     : Option[XFormsURIResolver]
  ): RuntimeForm = {

    withDebug("form initialization and rendering") {

      containerOpt foreach destroyForm

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

      CoreCrossPlatformSupport.withExternalContext(OfflineExternalContext.newExternalContext) {

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

                containerOpt match {
                  case Some(container) =>

                    val rcv = new DomDocumentFragmentXMLReceiver
                    withDebug("generate markup") {
                      XHTMLOutput.send(containingDocument, staticState.template.get, CoreCrossPlatformSupport.externalContext)(rcv)
                    }

                    // Find CSS/JS to load
                    // TODO: For now, don't load any extra CSS as it will likely not be found. At the very least we would need to
                    //   have the list of offline CSS baseline assets and not insert the CSS if already present.
      //              val stylesheetsToLoad = findAndDetachCssToLoad(rcv.frag)
                    val stylesheetsToLoad = Nil
                    val scriptsToLoad     = findAndDetachJsToLoad(rcv.frag)

                    // Asynchronously load styles, insert HTML, then load scripts
                    for {
      //                _ <- loadStylesheets(stylesheetsToLoad) // XXX TODO
                      _ <- Future(())
                      _ =  moveChildren(source = rcv.frag.querySelector("body"), target = container)
                      _ <- loadScripts(scriptsToLoad)
                    } yield ()

                  case None =>
                    withDebug("generate markup for warmup only") {
                      XHTMLOutput.send(containingDocument, staticState.template.get, CoreCrossPlatformSupport.externalContext)(new XMLReceiverAdapter)
                    }
                }

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

  def fetchSerializedFormForTesting(url: String): Future[SerializedBundle] = {
    val p = Promise[SerializedBundle]()
    val xhr = new XMLHttpRequest()
    xhr.open(
      method = "GET",
      url    = url
    )
    xhr.responseType = "arraybuffer"
    xhr.onload = { _ =>

      val uint8Array =
        new Uint8Array(xhr.response.asInstanceOf[ArrayBuffer])

      p.success(
        if (xhr.getResponseHeader(Headers.ContentType) == ContentTypes.ZipContentType)
          uint8Array
        else
          new TextDecoder().decode(uint8Array) // MDN: "default 'utf-8' or 'utf8'"
      )
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
  private val sb = new jl.StringBuilder

  private def flushCharactersIfNeeded() =
    if (sb.length > 0) {
      stack.head.appendChild(doc.createTextNode(sb.toString))
      sb.setLength(0)
    }

  override def startElement(uri: String, localName: String, qName: String, atts: Attributes): Unit = {

    flushCharactersIfNeeded()

    val newElem = doc.createElement(localName)

    for (i <- 0 until atts.getLength)
      newElem.setAttribute(atts.getLocalName(i), atts.getValue(i))

    stack.head.appendChild(newElem)
    stack ::= newElem
  }

  override def endElement(uri: String, localName: String, qName: String): Unit = {
    flushCharactersIfNeeded()
    stack = stack.tail
  }

  override def characters(ch: Array[Char], start: Int, length: Int): Unit =
    sb.append(new String(ch, start, length))
}

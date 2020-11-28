package org.orbeon.xforms.offline.demo

import org.log4s.log4sjs.LogThreshold.AllThreshold
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger}
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.processor.handlers.XHTMLOutput
import org.orbeon.oxf.xforms.state.XFormsStateManager
import org.orbeon.oxf.xforms.{Loggers, RequestInformation, XFormsContainingDocument, XFormsStaticStateDeserializer}
import org.orbeon.oxf.xml.XMLReceiverAdapter
import org.orbeon.xforms.{App, DeploymentType, XFormsApp, XFormsCrossPlatformSupport}
import org.scalajs.dom
import org.scalajs.dom.ext._
import org.xml.sax.Attributes

import scala.concurrent.ExecutionContext.Implicits.global


object OfflineDemo extends App {

  def onOrbeonApiLoaded(): Unit =
    XFormsApp.onOrbeonApiLoaded(LocalClientServerChannel)

  def onPageContainsFormsMarkup(): Unit =
    initializeOffline()

  private def initializeOffline(): Unit = {

    import org.log4s.log4sjs.Log4sConfig._
    setLoggerThreshold("", AllThreshold)

    implicit val logger: IndentedLogger = Loggers.getIndentedLogger("offline")

    val uuid = CoreCrossPlatformSupport.randomHexId
    val staticState = XFormsStaticStateDeserializer.deserialize(DemoForms.HelloForm)

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
        containerNamespace    = "orbeon-", // TODO
        versionedPathMatchers = Nil,
        isEmbedded            = true,
        forceInlineResources  = true
      )

    containingDocument.setRequestInformation(req)
    containingDocument.initialize(uriResolver = None, response = None)

    XFormsCrossPlatformSupport.withExternalContext(DemoExternalContext.newExternalContext) {
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

          println(s"xxx `nonJavaScriptLoads`")

        } else {
          // 3. Regular case: produce a document
          containingDocument.hostLanguage match {
            case "xhtml" =>

              val rcv = new DomDocumentFragmentXMLReceiver
              XHTMLOutput.send(containingDocument, staticState.template.get, XFormsCrossPlatformSupport.externalContext)(rcv)

              println(s"xxxx before updating the DOM")

              List("body", "head") foreach { elemName =>

                val dst = dom.window.document.querySelectorAll(s"html $elemName")(0)
                val src = rcv.frag.querySelectorAll(s"html $elemName > *")

                src foreach dst.appendChild
              }

//              println(s"xxxx doc innerHTML = ${dom.window.document.documentElement.innerHTML}")

              XFormsApp.onPageContainsFormsMarkup()

              containingDocument.afterInitialResponse()

              // Notify state manager
              XFormsStateManager.afterInitialResponse(containingDocument, disableDocumentCache = false)

            case unknown =>
              throw new OXFException(s"Unknown host language specified: $unknown")
          }
        }
      }
    }
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

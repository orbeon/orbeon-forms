package org.orbeon.xforms.offline.demo

import cats.data.NonEmptyList
import cats.syntax.option._
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.Loggers
import org.orbeon.oxf.xforms.event.XFormsServer
import org.orbeon.oxf.xforms.state.RequestParameters
import org.orbeon.xforms.{Namespaces, StateHandling, XFormsCrossPlatformSupport}
import org.orbeon.xforms.rpc.{ClientServerChannel, WireAjaxEvent}
import org.scalajs.dom
import org.scalajs.dom.ext._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


object LocalClientServerChannel extends ClientServerChannel[dom.Document] {

  def sendEvents(
    requestFormId     : String,
    eventsToSend      : NonEmptyList[WireAjaxEvent],
    sequenceNumberOpt : Option[Int],
    showProgress      : Boolean,
    ignoreErrors      : Boolean
  ): Future[dom.Document] = {
    XFormsCrossPlatformSupport.withExternalContext(DemoExternalContext.newExternalContext) {

      implicit val ec     = XFormsCrossPlatformSupport.externalContext
      implicit val logger: IndentedLogger = Loggers.getIndentedLogger("offline")

      val xmlReceiver = new DomDocumentFragmentXMLReceiver

      XFormsServer.processEvents(
        logRequestResponse      = false,
        requestParameters       = RequestParameters(StateHandling.getFormUuid(requestFormId), sequenceNumberOpt map (_.toLong), None, None),
        requestParametersForAll = RequestParameters(StateHandling.getFormUuid(requestFormId), sequenceNumberOpt map (_.toLong), None, None), // XXX TODO
        extractedEvents         = eventsToSend.toList,
        xmlReceiverOpt          = xmlReceiver.some,
        responseForReplaceAll   = ec.getResponse,
        beforeProcessRequest    = _ => (),
        extractWireEvents       = _ => Nil, // XXX TODO
      )

      // The JavaScript DOM API requires creating a document with a root element!
      val doc = dom.document.implementation.createDocument(Namespaces.XXF, "xxf:event-response", null)

      // Append the content
      xmlReceiver.frag.childNodes flatMap (_.childNodes) foreach
        doc.documentElement.appendChild

      Future(doc)
    }
  }
}
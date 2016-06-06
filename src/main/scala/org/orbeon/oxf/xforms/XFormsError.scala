/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms

import java.util.{List ⇒ JList}

import org.orbeon.errorified.Exceptions
import org.orbeon.oxf.common.{OXFException, OrbeonLocationException}
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.oxf.util.ScalaUtils.StringOps
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.webapp.HttpStatusCode
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.event.XFormsEventTarget
import org.orbeon.oxf.xforms.model.DataModel.Reason
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XHTMLBodyHandler
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml._
import org.orbeon.oxf.xml.dom4j.LocationData
import org.orbeon.saxon.dom4j.DocumentWrapper
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.saxon.trans.XPathException
import org.orbeon.scaxon.XML._

import scala.collection.JavaConverters._

// Represent a non-fatal server XForms error
case class ServerError(
  message  : String,
  fileOpt  : Option[String],
  lineOpt  : Option[Int],
  colOpt   : Option[Int],
  classOpt : Option[String]
)

object ServerError {

  def apply(t: Throwable): ServerError = {
    val root = Exceptions.getRootThrowable(t)
    ServerError(
      root.getMessage,
      OrbeonLocationException.getRootLocationData(t),
      Some(root.getClass.getName)
    )
  }

  def apply(message: String, location : Option[LocationData], classOpt : Option[String] = None): ServerError =
    ServerError(
      message.trimAllToEmpty,
      location flatMap (l ⇒ Option(l.file)),
      location map     (_.line) filter (_ >= 0),
      location map     (_.col)  filter (_ >= 0),
      classOpt
    )

  private val attributes  = List("file", "line", "column", "exception")
  private val description = List("in",   "line", "column", "cause")

  private def collectList(error: ServerError, names: List[String]) = (
    names
    zip List(error.fileOpt, error.lineOpt, error.colOpt, error.classOpt)
    collect { case (k, Some(v)) ⇒ List(k, v.toString) }
    flatten
  )

  def getDetailsAsArray(error: ServerError) =
    collectList(error, attributes) toArray

  // NOTE: A similar concatenation logic is in AjaxServer.js
  def getDetailsAsUserMessage(error: ServerError) =
    error.message :: collectList(error, description) mkString " "

  def errorsAsHTMLElem(errors: TraversableOnce[ServerError]) =
    <ul>{
      for (error ← errors)
        yield <li>{XMLUtils.escapeXMLMinimal(ServerError.getDetailsAsUserMessage(error))}</li>
    }</ul>

  def errorsAsXHTMLElem(errors: TraversableOnce[ServerError]) =
    <ul xmlns="http://www.w3.org/1999/xhtml">{
      for (error ← errors)
        yield <li>{XMLUtils.escapeXMLMinimal(ServerError.getDetailsAsUserMessage(error))}</li>
    }</ul>
}

object XFormsError {

  // What kind of errors get here:
  //
  // - setvalue errors (binding exceptions except in actions)
  //    - @calculate, @xxf:default
  //    - write xf:upload file metadata
  //    - store external value from control
  //    - xf:setvalue
  //    - use of XFormsAPI's setvalue
  //    - instance mirror (XBL, xxf:dynamic)
  //    - xf:switch/@caseref and xf:repeat/@indexref
  //    - xf:submission[@replace = 'text']
  // - XPath errors
  //    - during model rebuild
  //    - evaluating MIPs
  //    - evaluating variables
  //    - evaluating bindings except for actions
  //        - control and LHHA bindings
  //        - itemsets
  //        - xf:submission/@ref
  //        - submission headers
  //        - xf:upload/xf:output metadata
  //    - evaluating value attributes, as with xf:label/@value
  //    - evaluating control AVTs
  //    - evaluating control @format, @unformat, @value
  // - action errors
  //    - XPath errors
  //    - any other action error

  def handleNonFatalSetvalueError(target: XFormsEventTarget, locationData: LocationData, reason: Reason): Unit = {
    val containingDocument = target.container.getContainingDocument
    containingDocument.indentedLogger.logDebug("", reason.message)
    containingDocument.addServerError(ServerError(reason.message, Option(locationData)))
  }

  def handleNonFatalXPathError(container: XBLContainer, t: Throwable): Unit =
    handleNonFatalXFormsError(container, "exception while evaluating XPath expression", t)

  def handleNonFatalActionError(target: XFormsEventTarget, t: Throwable): Unit =
    handleNonFatalXFormsError(target.container, "exception while running action", t)

  private def handleNonFatalXFormsError(container: XBLContainer, message: String, t: Throwable): Unit = {

    // NOTE: We want to catch a status code exception which happen during an XPathException. And in that case, the XPathException
    // is dynamic, so we cannot simply exclude dynamic XPath exceptions. So we have to be inclusive and consider which types of
    // errors are fatal. See https://github.com/orbeon/orbeon-forms/issues/2194
    def causesContainFatalError =
      Exceptions.causesIterator(t) exists {
        case e: XPathException if e.isStaticError ⇒ true
        case e: HttpStatusCode                    ⇒ true
        case _                                    ⇒ false
      }

    if (container.getPartAnalysis.isTopLevel           &&   // LATER: Other sub-parts could be fatal, depending on settings on xxf:dynamic.
      container.getContainingDocument.isInitializing &&
      causesContainFatalError) {
      throw new OXFException(t)
    } else {
      val containingDocument = container.getContainingDocument
      containingDocument.indentedLogger.logDebug("", message, t)
      containingDocument.addServerError(ServerError(t))
    }
  }

  // Output the Ajax error panel with a placeholder for errors
  def outputAjaxErrorPanel(
    containingDocument : XFormsContainingDocument,
    helper             : XMLReceiverHelper,
    htmlPrefix         : String
  ): Unit =
    helper.element("", XMLNames.XIncludeURI, "include", Array(
      "href", XHTMLBodyHandler.getIncludedResourceURL(containingDocument.getRequestPath, "error-dialog.xml"),
      "fixup-xml-base", "false"
    ))

  // Output the Noscript error panel and insert the errors
  def outputNoscriptErrorPanel(
    containingDocument : XFormsContainingDocument,
    helper             : XMLReceiverHelper,
    htmlPrefix         : String
  ): Unit = {
    val errors = containingDocument.getServerErrors.asScala
    if (errors nonEmpty) {

      // Read the template
      val resourcePath =
        XHTMLBodyHandler.getIncludedResourcePath(containingDocument.getRequestPath, "error-dialog-noscript.xml")

      val template =
        new DocumentWrapper(
          ResourceManagerWrapper.instance().getContentAsDOM4J(resourcePath),
          null,
          XPath.GlobalConfiguration
        )

      // Find insertion point and insert list of errors
      // NOTE: This is a poor man's template system. Ideally, we would use XForms or XSLT for this.
      template \\ * find (_.attClasses("xforms-error-panel-details")) foreach { div ⇒
        insert(into = div, origin = ServerError.errorsAsXHTMLElem(errors): NodeInfo)
      }

      // Write out result using XInclude semantics
      // NOTE: Parent namespace information is not passed here, and that is probably not right
      TransformerUtils.writeDom4j(unsafeUnwrapElement(template.rootElement),
        new EmbeddedDocumentXMLReceiver(new XIncludeReceiver(null, helper.getXmlReceiver, null, null)))
    }
  }

  import XFormsConstants.XXFORMS_NAMESPACE_URI

  // Insert server errors into the Ajax response
  def outputAjaxErrors(ch: XMLReceiverHelper, errors: JList[ServerError]): Unit = {
    ch.startElement("xxf", XXFORMS_NAMESPACE_URI, "errors")
    for (error ← errors.asScala) {
      ch.startElement("xxf", XXFORMS_NAMESPACE_URI, "error",  ServerError.getDetailsAsArray(error))
      ch.text(error.message)
      ch.endElement()
    }
    ch.endElement()
  }
}
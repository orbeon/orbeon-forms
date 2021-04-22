/**
  * Copyright (C) 2010 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  * 2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.xforms.processor.handlers.xhtml

import java.{lang => jl}

import org.apache.commons.lang3.StringUtils
import org.orbeon.dom.QName
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.controls.AppearanceTrait
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XHTMLElementHandler._
import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, XFormsBaseHandler, XHTMLOutput}
import org.orbeon.oxf.xforms.state.XFormsStateManager
import org.orbeon.oxf.xml.XMLConstants.{XHTML_NAMESPACE_URI => XH}
import org.orbeon.oxf.xml.XMLReceiverSupport._
import org.orbeon.oxf.xml._
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.{Constants, DeploymentType}
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl


object XHTMLBodyHandler {

  private def prepareAttributes(atts: Attributes, xformsHandlerContext: HandlerContext): Attributes = {
    val newAtts = SAXUtils.appendToClassAttribute(atts, Constants.YuiSkinSamClass)
    XFormsBaseHandler.handleAVTsAndIDs(newAtts, XHTMLElementHandler.RefIdAttributeNames, xformsHandlerContext)
  }

  def getIncludedResourcePath(requestPath: String, fileName: String): String = {
    // Path will look like "/app-name/whatever"
    val pathElements = StringUtils.split(requestPath, '/')
    if (pathElements.length >= 2) {
      val appName = pathElements(0) // it seems that split() does not return first blank match
      val path = "/apps/" + appName + "/" + fileName
      if (ResourceManagerWrapper.instance.exists(path))
        return path
    }
    // Default
    "/config/" + fileName
  }

  def getIncludedResourceURL(requestPath: String, fileName: String): String =
    "oxf:" + getIncludedResourcePath(requestPath, fileName)
}

class XHTMLBodyHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  attributes     : Attributes,
  handlerContext : HandlerContext
) extends
  XFormsBaseHandlerXHTML(
    uri,
    localname,
    qName,
    attributes,
    handlerContext,
    repeating  = false,
    forwarding = true
  ) {

  override def start(): Unit = {

    // Register control handlers on controller
    handlerContext.controller.combinePf(XHTMLOutput.bodyPf)

    implicit val xmlReceiver: XMLReceiver = handlerContext.controller.output
    val htmlPrefix = XMLUtils.prefixFromQName(qName)

    // Start `xh:body`
    xmlReceiver.startElement(uri, localname, qName, XHTMLBodyHandler.prepareAttributes(attributes, handlerContext))

    // Get formatting prefix and declare it if needed
    // TODO: would be nice to do this here, but then we need to make sure this prefix is available to other handlers
    //        formattingPrefix = handlerContext().findFormattingPrefixDeclare();

    val isEmbeddedClient = containingDocument.isEmbedded
    val requestPath      = containingDocument.getRequestPath

    val xformsSubmissionPath =
      if (containingDocument.getDeploymentType != DeploymentType.Standalone || containingDocument.isPortletContainer || isEmbeddedClient)
        XFORMS_SERVER_SUBMIT
      else
        requestPath // submission posts to URL of the current page and `xforms-xml-submission.xpl` intercepts that

    outputXInclude(XHTMLBodyHandler.getIncludedResourceURL(requestPath, "noscript-panel.xml"))

    val formElemClasses = {
      val sb = new jl.StringBuilder("xforms-form xforms-initially-hidden")

      // LHHA appearance classes
      //
      // NOTE: There can be multiple appearances at the same time:
      //
      // - `xforms-hint-appearance-full xforms-hint-appearance-minimal`
      // - `xforms-hint-appearance-tooltip xforms-hint-appearance-minimal`
      //
      // That's because the `minimal` appearance doesn't apply to all controls, but only (as of 2016.2) to input fields.

      AppearanceTrait.encodeAndAppendAppearances(sb, "label", containingDocument.getLabelAppearances)

      val hintAppearances = containingDocument.getHintAppearances
      AppearanceTrait.encodeAndAppendAppearances(sb, "hint", hintAppearances)

      if (hintAppearances("tooltip"))
        sb.append(" xforms-enable-hint-as-tooltip")
      else
        sb.append(" xforms-disable-hint-as-tooltip")

      AppearanceTrait.encodeAndAppendAppearance(sb, "help", QName(containingDocument.getHelpAppearance))

      sb.toString
    }

    // Create `xh:form` element
    // NOTE: Do multipart as well with portlet client to simplify the proxying so we don't have to re-encode parameters
    val doMultipartPOST = containingDocument.staticOps.hasControlByName("upload") || isEmbeddedClient

    openElement(
      localName = "form",
      prefix    = htmlPrefix,
      uri       = XH,
      atts      =
        ("id"       -> XFormsUtils.getNamespacedFormId(containingDocument)) ::
        ("class"    -> formElemClasses)                                     ::
        ("action"   -> xformsSubmissionPath)                                ::
        ("method"   -> "POST")                                              ::
        ("onsubmit" -> "return false")                                      ::
        (doMultipartPOST list ("enctype" -> "multipart/form-data"))
    )

    // Only for 2-pass submission
    outputHiddenField(htmlPrefix, Constants.UuidFieldName, containingDocument.uuid)

    // We don't need `$sequence` here as HTML form posts are either:
    //
    // - 2nd phase of replace="all" submission: we don't (and can't) retry
    // - background upload: we don't want a sequence number as this run in parallel
    //
    // Output encoded static and dynamic state, only for client state handling (no longer supported in JavaScript)
    XFormsStateManager.instance.getClientEncodedStaticState(containingDocument) foreach
      (outputHiddenField(htmlPrefix, "$static-state", _))

    XFormsStateManager.instance.getClientEncodedDynamicState(containingDocument) foreach
      (outputHiddenField(htmlPrefix, "$dynamic-state", _))

    XFormsError.outputAjaxErrorPanel(containingDocument)

    outputXInclude(XHTMLBodyHandler.getIncludedResourceURL(requestPath, "help-panel.xml"))

    // HACK: We would be ok with just one template, but IE 6 doesn't allow setting the input/@type attribute properly
    // `xf:select[@appearance = 'full']`, `xf:input[@type = 'xs:boolean']`
    val newAtts = new AttributesImpl
    List(true, false) foreach { isMultiple =>
      XFormsSelect1Handler.outputItemFullTemplate(
        this,
        htmlPrefix,
        containingDocument,
        reusableAttributes,
        newAtts,
        s"xforms-select${if (isMultiple) "" else "1"}-full-template",
        "$xforms-item-name$",
        isMultiple,
        if (isMultiple) "checkbox" else "radio"
      )
    }
  }

  override def end(): Unit = {

    // Add global top-level XBL markup
    containingDocument.staticOps.getGlobals.iterator foreach
      (global => XXFormsComponentHandler.processShadowTree(handlerContext.controller, global.templateTree))

    implicit val xmlReceiver: XMLReceiver = handlerContext.controller.output
    val htmlPrefix = XMLUtils.prefixFromQName(qName)

    // Close `xh:form`
    closeElement(
      localName = "form",
      prefix    = htmlPrefix,
      uri       = XH
    )

    // Close `xh:body`
    handlerContext.controller.output.endElement(uri, localname, qName)
  }
}

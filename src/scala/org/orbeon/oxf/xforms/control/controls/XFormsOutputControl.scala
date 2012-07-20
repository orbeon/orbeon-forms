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
package org.orbeon.oxf.xforms.control.controls

import XFormsOutputControl._
import collection.JavaConverters._
import java.util.Collections
import java.util.{Map ⇒ JMap}
import org.apache.commons.lang3.StringUtils
import org.dom4j.Element
import org.dom4j.QName
import org.orbeon.oxf.externalcontext.ServletURLRewriter
import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsError
import org.orbeon.oxf.xforms.XFormsProperties
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.analysis.XPathDependencies
import org.orbeon.oxf.xforms.analysis.controls.OutputControl
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.oxf.xforms.processor.XFormsResourceServer.proxyURI
import org.orbeon.oxf.xforms.submission.{SubmissionUtils, Headers}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.xml.sax.helpers.AttributesImpl

/**
 * Represents an xforms:output control.
 */
class XFormsOutputControl(container: XBLContainer, parent: XFormsControl, element: Element, id: String)
        extends XFormsValueControl(container, parent, element, id) with FileMetadata {

    override type Control <: OutputControl

    def supportedFileMetadata = Seq("mediatype", "filename")

    // Optional format and mediatype
    private def format = Option(staticControl) flatMap (_.format)
    private def mediatypeAttribute = Option(staticControl) flatMap (_.mediatype)

    // Value attribute
    private val valueAttribute = element.attributeValue(VALUE_QNAME)
    // TODO: resolve statically
    private val urlNorewrite = XFormsUtils.resolveUrlNorewrite(element)

    override def getExtensionAttributes: Array[QName] =
        if (getAppearances.contains(XXFORMS_DOWNLOAD_APPEARANCE_QNAME))
            DownloadAppearanceExtensionAttributes
        else
            null

    override def evaluateImpl(): Unit = {
        super.evaluateImpl()
        evaluateFileMetadata()
    }

    override def markDirtyImpl(xpathDependencies: XPathDependencies): Unit ={
        super.markDirtyImpl(xpathDependencies)
        markFileMetadataDirty()
    }

    override def evaluateValue(): Unit = {
        val value =
            if (valueAttribute eq null)
                // Get value from single-node binding
                Option(DataModel.getValue(bindingContext.getSingleItem))
            else
                // Value comes from the XPath expression within the value attribute
                evaluateAsString(valueAttribute, bindingContext.getNodeset.asScala, bindingContext.getPosition)

        setValue(value getOrElse "")
    }

    override def evaluateExternalValue(): Unit = {
        assert(isRelevant)

        val internalValue = getValue
        assert(internalValue ne null)

        val updatedValue =
            if (getAppearances.contains(XXFORMS_DOWNLOAD_APPEARANCE_QNAME)) {
                // Download appearance
                // NOTE: Never put timestamp for downloads otherwise browsers may cache the file to download which is not
                proxyValueIfNeeded(internalValue, "", filename, Option(fileMediatype) orElse mediatypeAttribute orNull)
            } else if (mediatypeAttribute exists (_.startsWith("image/"))) {
                // Image mediatype
                // Use dummy image as default value so that client always has something to load
                proxyValueIfNeeded(internalValue, DUMMY_IMAGE_URI, filename, mediatypeAttribute.get)
            } else if (mediatypeAttribute exists (_ == "text/html")) {
                // HTML mediatype
                internalValue
            } else {
                // Other mediatypes
                if (valueAttribute eq null)
                    // There is a single-node binding, so the format may be used
                    getValueUseFormat(format) getOrElse internalValue
                else
                    // There is a @value attribute, don't use format
                    internalValue
            }

        setExternalValue(updatedValue)
    }

    // Keep public for unit tests
    def evaluatedHeaders: JMap[String, Array[String]] = {
        // TODO: pass BindingContext directly
        getContextStack.setBinding(getBindingContext)
        val headersToForward = SubmissionUtils.clientHeadersToForward(containingDocument.getRequestHeaders, forwardClientHeaders = true)
        try Headers.evaluateHeaders(container, getContextStack, getEffectiveId, staticControl.element, headersToForward).asJava
        catch {
            case e: Exception ⇒ {
                XFormsError.handleNonFatalXPathError(container, e)
                Collections.emptyMap()
            }
        }
    }

    private def proxyValueIfNeeded(internalValue: String, defaultValue: String, filename: String, mediatype: String): String = {

        // If the value is a file:, we make sure it is signed before returning it
        def hmacValueOrDefault(initial: String, value: ⇒ String, default: ⇒ String) =
            if (initial.startsWith("file:/") && ! XFormsUploadControl.verifyMAC(initial))
                default
            else
                value

        def doProxyURI(uri: String, lastModified: Long) =
            proxyURI(getIndentedLogger, uri, filename, mediatype, lastModified, evaluatedHeaders, XFormsProperties.getForwardSubmissionHeaders(containingDocument))

        val typeName = getBuiltinTypeName

        if ((internalValue ne null) && internalValue.length > 0 && internalValue.trim.length > 0) {
            if ((typeName eq null) || typeName == "anyURI") {
                // xs:anyURI type (the default)
                if (! urlNorewrite) {
                    // Resolve xml:base and try to obtain a path which is an absolute path without the context
                    val rebasedURI = XFormsUtils.resolveXMLBase(containingDocument, element, internalValue)
                    val servletRewriter = new ServletURLRewriter(NetUtils.getExternalContext.getRequest)
                    val resolvedURI = servletRewriter.rewriteResourceURL(rebasedURI.toString, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT)
                    val lastModified = NetUtils.getLastModifiedIfFast(resolvedURI)
                    hmacValueOrDefault(
                        resolvedURI,
                        doProxyURI(resolvedURI, lastModified),
                        defaultValue)
                } else
                    // Otherwise we leave the value as is
                    hmacValueOrDefault(internalValue, internalValue, defaultValue)
            } else if (typeName == "base64Binary") {
                // xs:base64Binary type
                // NOTE: -1 for lastModified will cause XFormsResourceServer to set Last-Modified and Expires properly to "now"
                doProxyURI(NetUtils.base64BinaryToAnyURI(internalValue, NetUtils.SESSION_SCOPE), -1)
            } else
                // Dummy image
                defaultValue
        } else
            // Dummy image
            defaultValue
    }

    override def getEscapedExternalValue =
        if (getAppearances.contains(XXFORMS_DOWNLOAD_APPEARANCE_QNAME) || (mediatypeAttribute exists (_.startsWith("image/")))) {
            val externalValue = getExternalValue
            if (StringUtils.isNotBlank(externalValue)) {
                // External value is not blank, rewrite as absolute path. Two cases:
                // o URL is proxied:        /xforms-server/dynamic/27bf...  => [/context]/xforms-server/dynamic/27bf...
                // o URL is default value:  /ops/images/xforms/spacer.gif   => [/context][/version]/ops/images/xforms/spacer.gif
                XFormsUtils.resolveResourceURL(containingDocument, element, externalValue, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH)
            } else
                // Empty value, return as is
                externalValue
        } else if (mediatypeAttribute exists (_ == "text/html"))
            // Rewrite the HTML value with resolved @href and @src attributes
            XFormsControl.getEscapedHTMLValue(getLocationData, getExternalValue)
        else
            // Return external value as is
            getExternalValue

    override def getNonRelevantEscapedExternalValue =
        if (mediatypeAttribute exists (_.startsWith("image/")))
            // Return rewritten URL of dummy image URL
            XFormsUtils.resolveResourceURL(containingDocument, element, DUMMY_IMAGE_URI, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH)
        else
            super.getNonRelevantEscapedExternalValue

    def getMediatypeAttribute = mediatypeAttribute.orNull
    def getValueAttribute = valueAttribute

    // No type information is returned when there is a value attribute
    // Question: what if we have both @ref and @value? Should a type still be provided? This is not supported in
    // XForms 1.1 but we do support it, with the idea that the bound node does not provide the value but provides
    // mips. Not sure if the code below makes sense after all then.
    override def valueType: QName = if (valueAttribute eq null) super.valueType else null

    // It usually doesn't make sense to focus on xf:output, at least not in the sense "focus to enter data". So we
    // disallow this for now.
    override def setFocus(inputOnly: Boolean) = false

    override def addAjaxCustomAttributes(attributesImpl: AttributesImpl, isNewRepeatIteration: Boolean, other: XFormsControl) =
        addFileMetadataAttributes(attributesImpl, isNewRepeatIteration, other.asInstanceOf[FileMetadata])

    override def equalsExternal(other: XFormsControl) =
        other match {
            case other if this eq other ⇒ true
            case other: XFormsOutputControl ⇒ compareFileMetadata(other) && super.equalsExternal(other)
            case _ ⇒ false
        }

    override def getBackCopy: AnyRef = {
        val cloned = super.getBackCopy.asInstanceOf[XFormsOutputControl]
        updateFileMetadataCopy(cloned)
        cloned
    }
}

object XFormsOutputControl {

    def getExternalValueOrDefault(control: XFormsOutputControl, mediatypeValue: String) =
        if (control ne null)
            // Ask control
            control.getExternalValue
        else if ((mediatypeValue ne null) && mediatypeValue.startsWith("image/"))
            // Dummy image
            DUMMY_IMAGE_URI
        else
            // Default for other mediatypes
            null

    val DownloadAppearanceExtensionAttributes = Array(XXFORMS_TARGET_QNAME)
}
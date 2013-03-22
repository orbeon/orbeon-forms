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

import org.apache.commons.lang3.StringUtils
import org.dom4j.Element
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsUtils._
import org.orbeon.oxf.xforms.analysis.controls.AttributeControl
import org.orbeon.oxf.xforms.control._
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.ContentHandlerHelper
import org.xml.sax.helpers.AttributesImpl
import org.orbeon.oxf.externalcontext.URLRewriter._
import org.orbeon.oxf.xml.ContentHandlerHelper._
import org.orbeon.oxf.xforms.XFormsUtils

/**
 * xxf:attribute control
 */
class XXFormsAttributeControl(container: XBLContainer, parent: XFormsControl, element: Element, effectiveId: String)
        extends XFormsSingleNodeControl(container, parent, element, effectiveId)
        with XFormsValueControl {

    import XXFormsAttributeControl._

    //override type Control <: AttributeControl

    private val attributeControl = staticControl.asInstanceOf[AttributeControl]
    private var attributeName    = if (attributeControl ne null) attributeControl.attributeName  else null
    private var attributeValue   = if (attributeControl ne null) attributeControl.attributeValue else null
    private var forName          = if (attributeControl ne null) attributeControl.forName        else null

    /**
     * Special constructor used for label, etc. content AVT handling.
     *
     * @param container             container
     * @param element               control element (should not be used here)
     * @param attributeName         name of the attribute
     * @param avtExpression         attribute template expression
     * @param forName               name of the element the attribute is on
     */
    def this(container: XBLContainer, element: Element, attributeName: String, avtExpression: String, forName: String) = {
        this(container, null, element, null)
        this.attributeName  = attributeName
        this.attributeValue = avtExpression
        this.forName        = forName
    }

    // Value comes from the AVT value attribute
    override def evaluateValue() =
        super.setValue(Option(evaluateAvt(attributeValue)) getOrElse "")

    override def getRelevantEscapedExternalValue = {
        // Rewrite URI attribute if needed
        val externalValue = getExternalValue()
        attributeName match {
            case "src" ⇒
                resolveResourceURL(containingDocument, element, externalValue, REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE)
            case "href" if ! externalValue.startsWith("#") ⇒
                // NOTE: Keep value unchanged if it's just a fragment (see also XFormsLoadAction)
                attributeControl.urlType match {
                case "action"   ⇒ resolveActionURL(containingDocument, element, externalValue, false)
                case "resource" ⇒ resolveResourceURL(containingDocument, element, externalValue, REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE)
                case _          ⇒ resolveRenderURL(containingDocument, element, externalValue, false) // default is "render"
            }
            case _ ⇒ externalValue
        }

    }

    override def evaluateExternalValue() =
        setExternalValue(getExternalValueHandleSrc(getValue, attributeName, forName))

    def getAttributeName = attributeName

    def getEffectiveForAttribute =
        getRelatedEffectiveId(getEffectiveId, attributeControl.forStaticId)

    override def getNonRelevantEscapedExternalValue =
        attributeName match {
            case "src" if forName == "img" ⇒
                // Return rewritten URL of dummy image URL
                resolveResourceURL(containingDocument, element, DUMMY_IMAGE_URI, REWRITE_MODE_ABSOLUTE_PATH)
            case _ ⇒
                super.getNonRelevantEscapedExternalValue
        }

    override def outputAjaxDiff(ch: ContentHandlerHelper, other: XFormsControl, attributesImpl: AttributesImpl, newlyVisibleSubtree: Boolean) = {

        import AjaxSupport._

        require(attributesImpl.getLength == 0)

        val attributeControl2 = this

        if (attributeName != "class") {
            // Whether it is necessary to output information about this control
            var doOutputElement = false

            // Control id
            attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, namespaceId(containingDocument, attributeControl2.getEffectiveId))

            // The client does not store an HTML representation of the xxf:attribute control, so we
            // have to output these attributes.

            // HTML element id
            val effectiveFor2 = attributeControl2.getEffectiveForAttribute
            doOutputElement |= addOrAppendToAttributeIfNeeded(attributesImpl, "for", namespaceId(containingDocument, effectiveFor2), newlyVisibleSubtree, isDefaultValue = false)

            // Attribute name
            val name2 = attributeControl2.getAttributeName
            doOutputElement |= addOrAppendToAttributeIfNeeded(attributesImpl, "name", name2, newlyVisibleSubtree, isDefaultValue = false)

            // Output element
            outputValueElement(ch, attributeControl2, doOutputElement, newlyVisibleSubtree, attributesImpl, "attribute")
        } else {
            // Handle class separately

            // Just output the class diffs if any
            // See https://github.com/orbeon/orbeon-forms/issues/889
            //val doOutputElement = AjaxSupport.addAjaxClasses(attributesImpl, newlyVisibleSubtree, other, attributeControl2)

            // The classes are stored as the control's value
            val classes1 = Option(other.asInstanceOf[XXFormsAttributeControl]) flatMap (control ⇒ Option(control.getEscapedExternalValue)) getOrElse ""
            val classes2 = Option(attributeControl2.getEscapedExternalValue) getOrElse ""

            if (newlyVisibleSubtree || classes1 != classes2) {
                val attributeValue = diffClasses(classes1, classes2)
                val doOutputElement = addOrAppendToAttributeIfNeeded(attributesImpl, "class", attributeValue, newlyVisibleSubtree, attributeValue == "")

                if (doOutputElement) {
                    // Pass the HTML element id, not the control id, because that's what the client will expect
                    val effectiveFor2 = attributeControl2.getEffectiveForAttribute
                    attributesImpl.addAttribute("", "id", "id", CDATA, XFormsUtils.namespaceId(containingDocument, effectiveFor2))

                    ch.startElement("xxf", XXFORMS_NAMESPACE_URI, "control", attributesImpl)
                    ch.endElement()
                }
            }
        }
    }

    override def setFocus(inputOnly: Boolean) = false
    override def supportFullAjaxUpdates = false
}

object XXFormsAttributeControl {

    private def getExternalValueHandleSrc(controlValue: String, attributeName: String, forName: String): String =
        if (attributeName == "src") {
            // Special case of xhtml:img/@src
            if (forName == "img" && StringUtils.isBlank(controlValue))
                DUMMY_IMAGE_URI
            else
                controlValue
        } else if (controlValue eq null)
            // No usable value
            ""
        else
            // Use value as is
            controlValue

    def getExternalValueHandleSrc(concreteControl: XXFormsAttributeControl, attributeControl: AttributeControl): String =
        getExternalValueHandleSrc(Option(concreteControl) map (_.getValue) orNull, attributeControl.attributeName, attributeControl.forName)
}
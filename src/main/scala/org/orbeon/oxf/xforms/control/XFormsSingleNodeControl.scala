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
package org.orbeon.oxf.xforms.control

import collection.JavaConverters._
import org.dom4j.Element
import org.dom4j.QName
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.controls.SingleNodeTrait
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.ContentHandlerHelper
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.saxon.om.Item
import org.orbeon.saxon.om.NodeInfo
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl
import org.orbeon.saxon.value.AtomicValue
import org.orbeon.oxf.xforms.event.{XFormsEvents, Dispatch}
import org.orbeon.oxf.xforms.event.events._
import org.orbeon.oxf.xforms.BindingContext

/**
* Control with a single-node binding (possibly optional). Such controls can have MIPs.
*/
abstract class XFormsSingleNodeControl(container: XBLContainer, parent: XFormsControl, element: Element, effectiveId: String)
        extends XFormsControl(container, parent, element, effectiveId) {

    import XFormsSingleNodeControl._

    override type Control <: SingleNodeTrait

    // Bound item
    private var _boundItem: Item = null
    final def getBoundItem = _boundItem

    // Standard MIPs
    private var _readonly = false
    final def isReadonly = _readonly

    private var _required = false
    final def isRequired = _required

    private var _valid    = true
    def isValid = _valid

    // Previous values for refresh
    private var _wasReadonly = false
    private var _wasRequired = false
    private var _wasValid = true

    // Type
    private var _valueType: QName = null
    def valueType = _valueType

    // Custom MIPs
    private var _customMIPs: Map[String, String] = Map()
    def customMIPs: Map[String, String] =  _customMIPs
    def customMIPsClasses = customMIPs map { case (k, v) ⇒ k + '-' + v }
    def jCustomMIPsClassesAsString = customMIPsClasses mkString " "

    override def onDestroy(): Unit = {
        super.onDestroy()
        // Set default MIPs so that diff picks up the right values
        setDefaultMIPs()
    }

    override def onCreate(): Unit = {
        super.onCreate()

        readBinding()

        _wasReadonly = false
        _wasRequired = false
        _wasValid = true
    }

    override def onBindingUpdate(oldBinding: BindingContext, newBinding: BindingContext): Unit = {
        super.onBindingUpdate(oldBinding, newBinding)
        readBinding()
    }

    private def readBinding(): Unit = {
        // Set bound item, only considering actual bindings (with @bind, @ref or @nodeset)
        val bc = bindingContext
        if (bc.isNewBind)
            this._boundItem = bc.getSingleItem

        // Get MIPs
        getBoundItem match {
            case nodeInfo: NodeInfo ⇒
                // Control is bound to a node - get model item properties
                this._readonly  = InstanceData.getInheritedReadonly(nodeInfo)
                this._required  = InstanceData.getRequired(nodeInfo)
                this._valid     = InstanceData.getValid(nodeInfo)
                this._valueType = InstanceData.getType(nodeInfo)

                // Custom MIPs
                this._customMIPs = Option(InstanceData.getAllCustom(nodeInfo)) map (_.asScala.toMap) getOrElse Map()

                // Handle global read-only setting
                if (XFormsProperties.isReadonly(containingDocument))
                    this._readonly = true
            case atomicValue: AtomicValue ⇒
                // Control is not bound to a node (i.e. bound to an atomic value)
                setAtomicValueMIPs()
            case _ ⇒
                // Control is not bound to a node because it doesn't have a binding (group, trigger, dialog, etc. without @ref)
                setDefaultMIPs()
        }
    }

    private def setAtomicValueMIPs(): Unit = {
        setDefaultMIPs()
        this._readonly = true
    }

    private def setDefaultMIPs(): Unit = {
        this._readonly = false
        this._required = false
        this._valid = true // by default, a control is not invalid
        this._valueType = null
        this._customMIPs = Map()
    }

    override def commitCurrentUIState(): Unit = {
        super.commitCurrentUIState()

        isValueChanged()
        wasRequired()
        wasReadonly()
        wasValid()
    }

    override def binding: Seq[Item] = Option(_boundItem).toList

    // Single-node controls support refresh events
    override def supportsRefreshEvents = true

    final def wasReadonly(): Boolean = {
        val result = _wasReadonly
        _wasReadonly = _readonly
        result
    }

    final def wasRequired(): Boolean = {
        val result = _wasRequired
        _wasRequired = _required
        result
    }

    final def wasValid(): Boolean = {
        val result = _wasValid
        _wasValid = _valid
        result
    }

    def isValueChanged() = false
    def typeExplodedQName = Dom4jUtils.qNameToExplodedQName(valueType)

    /**
     * Convenience method to return the local name of a built-in XML Schema or XForms type.
     *
     * @return the local name of the built-in type, or null if not found
     */
    def getBuiltinTypeName =
        Option(valueType) filter
        (valueType ⇒ Set(XSD_URI, XFORMS_NAMESPACE_URI)(valueType.getNamespaceURI)) map
        (_.getName) orNull

    /**
     * Convenience method to return the local name of the XML Schema type.
     *
     * @return the local name of the type, or null if not found
     */
    def getTypeLocalName = Option(valueType) map (_.getName) orNull

    override def computeRelevant: Boolean = {
        // If parent is not relevant then we are not relevant either
        if (! super.computeRelevant)
            return false

        val bc = bindingContext
        val currentItem = bc.getSingleItem
        if (bc.isNewBind) {
            // There is a binding

            isAllowedBoundItem(currentItem) && isRelevantItem(currentItem)
        } else {
            // Control is not bound because it doesn't have a binding
            // If the binding is optional (group, trigger, dialog, etc. without @ref), the control is relevant,
            // otherwise there is a binding error and the control is marked as not relevant.
            // If staticControl is missing, consider the control relevant too (we're not happy with this but we have to
            // deal with it for now).
            (staticControl eq null) || staticControl.isBindingOptional
        }
    }

    // Allow override only for dangling XFormsOutputControl
    def isAllowedBoundItem(item: Item) = staticControl.isAllowedBoundItem(item)

    // NOTE: We don't compare the type here anymore, because only some controls (xf:input) need to tell the client
    // about value type changes.
    override def equalsExternal(other: XFormsControl): Boolean =
        other match {
            case other if this eq other ⇒ true
            case other: XFormsSingleNodeControl ⇒
                isReadonly == other.isReadonly &&
                isRequired == other.isRequired &&
                isValid    == other.isValid &&
                customMIPs == other.customMIPs &&
                super.equalsExternal(other)
            case _ ⇒ false
        }

    // Static read-only if we are read-only and static (global or local setting)
    override def isStaticReadonly = isReadonly && hasStaticReadonlyAppearance

    def hasStaticReadonlyAppearance =
        XFormsProperties.isStaticReadonlyAppearance(containingDocument) ||
            (XFormsProperties.READONLY_APPEARANCE_STATIC_VALUE == element.attributeValue(XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_QNAME))

    override def setFocus(inputOnly: Boolean): Boolean = Focus.focusWithEvents(this)

    override def outputAjaxDiff(ch: ContentHandlerHelper, other: XFormsControl, attributesImpl: AttributesImpl, isNewlyVisibleSubtree: Boolean) {
        assert(attributesImpl.getLength == 0)

        val control1 = other.asInstanceOf[XFormsSingleNodeControl]
        val control2 = this

        // Add attributes
        val doOutputElement = addAjaxAttributes(attributesImpl, isNewlyVisibleSubtree, other)

        // Get current value if possible for this control
        // NOTE: We issue the new value in all cases because we don't have yet a mechanism to tell the
        // client not to update the value, unlike with attributes which can be omitted
        // FIXME: Remove isInstanceOf calls
        if (control2.isInstanceOf[XFormsValueControl] && ! control2.isInstanceOf[XFormsUploadControl] && !control2.isInstanceOf[XFormsComponentControl]) {

            // TODO: Output value only when changed

            // Output element
            val xformsValueControl = control2.asInstanceOf[XFormsValueControl]
            outputValueElement(ch, xformsValueControl, doOutputElement, isNewlyVisibleSubtree, attributesImpl, "control")
        } else {
            // No value, just output element with no content (but there may be attributes)
            if (doOutputElement)
                ch.element("xxf", XXFORMS_NAMESPACE_URI, "control", attributesImpl)
        }

        // Output attributes (like style) in no namespace which must be output via xxf:attribute
        // TODO: If only some attributes changed, then we also output xxf:control above, which is unnecessary
        addExtensionAttributesExceptClassForAjax(control1, "", isNewlyVisibleSubtree)(ch)
    }

    override def addAjaxAttributes(attributesImpl: AttributesImpl, isNewlyVisibleSubtree: Boolean, other: XFormsControl): Boolean = {
        val control1 = other.asInstanceOf[XFormsSingleNodeControl]
        val control2 = this

        // Call base class for the standard stuff
        var added = super.addAjaxAttributes(attributesImpl, isNewlyVisibleSubtree, other)

        // MIPs
        added |= addAjaxMIPs(attributesImpl, isNewlyVisibleSubtree, control1, control2)

        added
    }

    private def addAjaxMIPs(attributesImpl: AttributesImpl, isNewlyVisibleSubtree: Boolean, control1: XFormsSingleNodeControl, control2: XFormsSingleNodeControl): Boolean = {
        var added = false
        if (isNewlyVisibleSubtree && control2.isReadonly || control1 != null && control1.isReadonly != control2.isReadonly) {
            attributesImpl.addAttribute("", READONLY_ATTRIBUTE_NAME, READONLY_ATTRIBUTE_NAME, ContentHandlerHelper.CDATA, control2.isReadonly.toString)
            added = true
        }
        if (isNewlyVisibleSubtree && control2.isRequired || control1 != null && control1.isRequired != control2.isRequired) {
            attributesImpl.addAttribute("", REQUIRED_ATTRIBUTE_NAME, REQUIRED_ATTRIBUTE_NAME, ContentHandlerHelper.CDATA, control2.isRequired.toString)
            added = true
        }

        // NOTE: We used to have a configurable default for the relevance. Not sure why this was needed. Here consider the default is true.
        if (isNewlyVisibleSubtree && !control2.isRelevant || control1 != null && control1.isRelevant != control2.isRelevant) {
            attributesImpl.addAttribute("", RELEVANT_ATTRIBUTE_NAME, RELEVANT_ATTRIBUTE_NAME, ContentHandlerHelper.CDATA, control2.isRelevant.toString)
            added = true
        }
        if (isNewlyVisibleSubtree && !control2.isValid || control1 != null && control1.isValid != control2.isValid) {
            attributesImpl.addAttribute("", VALID_ATTRIBUTE_NAME, VALID_ATTRIBUTE_NAME, ContentHandlerHelper.CDATA, control2.isValid.toString)
            added = true
        }

        // Custom MIPs
        added |= addAjaxCustomMIPs(attributesImpl, isNewlyVisibleSubtree, control1, control2)

        added
    }

    protected def outputValueElement(ch: ContentHandlerHelper, xformsValueControl: XFormsValueControl, doOutputElement: Boolean, isNewlyVisibleSubtree: Boolean, attributesImpl: Attributes, elementName: String) {

        // Create element with text value
        val value = xformsValueControl.getEscapedExternalValue

        if (doOutputElement || ! isNewlyVisibleSubtree || value != "") {
            ch.startElement("xxf", XXFORMS_NAMESPACE_URI, elementName, attributesImpl)
            if (value.length > 0)
                ch.text(value)
            ch.endElement()
        }
    }

    override def writeMIPs(write: (String, String) ⇒ Unit) {
        super.writeMIPs(write)

        write("valid",            isValid.toString)
        write("read-only",        isReadonly.toString)
        write("static-read-only", isStaticReadonly.toString)
        write("required",         isRequired.toString)

        // Output custom MIPs classes
        for ((name, value) ← customMIPs)
            write(name, value)

        // Output type class
        val typeName = getBuiltinTypeName
        if (typeName ne null) {
            // Control is bound to built-in schema type
            write("xforms-type", typeName)
        } else {
            // Output custom type class
           val customTypeName = getTypeLocalName
           if (customTypeName ne null) {
               // Control is bound to a custom schema type
               write("xforms-type-custom", customTypeName)
           }
        }
    }

    // Dispatch creation events
    override def dispatchCreationEvents() = {
        super.dispatchCreationEvents()

        // Dispatch events only if the MIP value is different from the default

        // Dispatch xforms-required if needed
        // TODO: must reacquire control and test for relevance again
        if (isRequired)
            Dispatch.dispatchEvent(new XFormsRequiredEvent(this))

        // Dispatch xforms-readonly if needed
        // TODO: must reacquire control and test for relevance again
        if (isReadonly)
            Dispatch.dispatchEvent(new XFormsReadonlyEvent(this))

        // Dispatch xforms-invalid if needed
        // TODO: must reacquire control and test for relevance again
        if (!isValid)
            Dispatch.dispatchEvent(new XFormsInvalidEvent(this))
    }

    // NOTE: For the purpose of dispatching value change and MIP events, we used to make a
    // distinction between value controls and plain single-node controls. However it seems that it is
    // still reasonable to dispatch those events to xf:group, xf:switch, and even repeat
    // iterations if they are bound.
    override def dispatchChangeEvents() = {

        super.dispatchChangeEvents()

        // xforms-value-changed
        if (isValueChanged)
            Dispatch.dispatchEvent(new XFormsValueChangeEvent(this))

        // Dispatch moved xxforms-iteration-changed if needed
        if (previousEffectiveIdCommit != getEffectiveId
                && container.getPartAnalysis.observerHasHandlerForEvent(getPrefixedId, XFormsEvents.XXFORMS_ITERATION_MOVED))
            Dispatch.dispatchEvent(new XXFormsIterationMovedEvent(this))

        // Dispatch events only if the MIP value is different from the previous value

        // TODO: must reacquire control and test for relevance again
        locally {
            val previousValidState = wasValid
            val newValidState = isValid

            if (previousValidState != newValidState)
                Dispatch.dispatchEvent(if (newValidState) new XFormsValidEvent(this) else new XFormsInvalidEvent(this))
        }
        // TODO: must reacquire control and test for relevance again
        locally {
            val previousRequiredState = wasRequired
            val newRequiredState = isRequired

            if (previousRequiredState != newRequiredState)
                Dispatch.dispatchEvent(if (newRequiredState) new XFormsRequiredEvent(this) else new XFormsOptionalEvent(this))
        }
        // TODO: must reacquire control and test for relevance again
        locally {
            val previousReadonlyState = wasReadonly
            val newReadonlyState = isReadonly

            if (previousReadonlyState != newReadonlyState)
                Dispatch.dispatchEvent(if (newReadonlyState) new XFormsReadonlyEvent(this) else new XFormsReadwriteEvent(this))
        }
    }
}

object XFormsSingleNodeControl {

    // Item relevance (atomic values are considered relevant)
    def isRelevantItem(item: Item) =
        if (item.isInstanceOf[NodeInfo])
            InstanceData.getInheritedRelevant(item.asInstanceOf[NodeInfo])
        else
            true

    // Convenience method to figure out when a control is relevant, assuming a "null" control is non-relevant.
    def isRelevant(control: XFormsSingleNodeControl) = Option(control) exists (_.isRelevant)

    // NOTE: Similar to AjaxSupport.addAjaxClasses. Should unify handling of classes.
    def addAjaxCustomMIPs(attributesImpl: AttributesImpl, newlyVisibleSubtree: Boolean, control1: XFormsSingleNodeControl, control2: XFormsSingleNodeControl): Boolean = {

        require(control2 ne null)

        val customMIPs2 = control2.customMIPs

        def addOrAppend(s: String) =
            AjaxSupport.addOrAppendToAttributeIfNeeded(attributesImpl, "class", s, newlyVisibleSubtree, s == "")

        // This attribute is a space-separate list of class names prefixed with either '-' or '+'
        if (newlyVisibleSubtree) {
            // Control is newly shown (it may or may not be relevant!)
            assert(control1 eq null)

            // Add all classes
            addOrAppend(control2.customMIPsClasses map ('+' + _) mkString " ")
        } else if (control1.customMIPs != customMIPs2) {
            // Custom MIPs changed
            assert(control1 ne null)

            val customMIPs1 = control1.customMIPs

            def diff(mips1: Map[String, String], mips2: Map[String, String], prefix: Char) =
                for {
                    (name, value1) ← mips1
                    value2 = mips2.get(name)
                    if Option(value1) != value2
                } yield
                    prefix + name + '-' + value1 // TODO: encode so that there are no spaces

            val classesToRemove = diff(customMIPs1, customMIPs2, '-')
            val classesToAdd    = diff(customMIPs2, customMIPs1, '+')

            addOrAppend(classesToRemove ++ classesToAdd mkString " ")
        } else
            false
    }
}

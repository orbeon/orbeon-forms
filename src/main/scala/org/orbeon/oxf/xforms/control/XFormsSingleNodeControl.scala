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

import org.dom4j.Element
import org.dom4j.QName
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.controls.SingleNodeTrait
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.XMLReceiverHelper
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.saxon.om.Item
import org.orbeon.saxon.om.NodeInfo
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl
import org.orbeon.saxon.value.AtomicValue
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.event.XFormsEvents.XXFORMS_ITERATION_MOVED
import org.orbeon.oxf.xforms.event.events._
import org.orbeon.oxf.xforms.BindingContext
import org.orbeon.oxf.xforms.analysis.model.{StaticBind, Model}
import org.orbeon.oxf.xforms.analysis.model.StaticBind._

/**
* Control with a single-node binding (possibly optional). Such controls can have MIPs (properties coming from a model).
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

    private var _valid = true
    def isValid = _valid

    // NOTE: At this time, the control only stores the constraints for a single level (the "highest" level). There is no
    // mixing of constraints among levels, like error and warning.
    private var _alertLevel: Option[ValidationLevel] = None
    def alertLevel = _alertLevel

    private var _failedConstraints: List[StaticBind#ConstraintXPathMIP] = Nil
    def failedConstraints = _failedConstraints

    // Previous values for refresh
    private var _wasReadonly = false
    private var _wasRequired = false
    private var _wasValid = true
    private var _wasAlertLevel: Option[ValidationLevel] = None
    private var _wasFailedConstraints: List[StaticBind#ConstraintXPathMIP] = Nil

    // Type
    private var _valueType: QName = null
    def valueType = _valueType

    // Custom MIPs
    private var _customMIPs = Map.empty[String, String]
    def customMIPs: Map[String, String] = _customMIPs
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
        if (bc.newBind)
            this._boundItem = bc.getSingleItem

        // Get MIPs
        getBoundItem match {
            case nodeInfo: NodeInfo ⇒
                // Control is bound to a node - get model item properties
                this._readonly  = InstanceData.getInheritedReadonly(nodeInfo)
                this._required  = InstanceData.getRequired(nodeInfo)
                this._valid     = InstanceData.getValid(nodeInfo)
                this._valueType = InstanceData.getType(nodeInfo)

                // Constraints

                // Instance data stores all failed constraints
                val allFailedConstraints = InstanceData.failedConstraints(nodeInfo)

                // First identify the highest level. This means that e.g. if a control is on error, but doesn't have any
                // matching alerts for the error level, then no alert shown. Alerts for lower levels, in particular, such as
                // warnings, don't show. There may be no matching level.
                val alertLevel = {
                    def controlHasLevel(level: ValidationLevel) =
                        level == ErrorLevel && ! isValid || allFailedConstraints.contains(level)

                    LevelsByPriority find controlHasLevel
                }

                this._alertLevel = alertLevel

                if (allFailedConstraints.nonEmpty)
                    this._failedConstraints = alertLevel flatMap allFailedConstraints.get getOrElse Nil
                else
                    this._failedConstraints = Nil

                // Custom MIPs
                this._customMIPs = Option(InstanceData.collectAllCustomMIPs(nodeInfo)) map (_.toMap) getOrElse Map()

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
        this._readonly          = Model.DEFAULT_READONLY
        this._required          = Model.DEFAULT_REQUIRED
        this._valid             = Model.DEFAULT_VALID
        this._valueType         = null
        this._customMIPs        = Map.empty[String, String]

        this._alertLevel   = None
        this._failedConstraints = Nil
    }

    override def commitCurrentUIState(): Unit = {
        super.commitCurrentUIState()

        isValueChangedCommit()
        wasRequiredCommit()
        wasReadonlyCommit()
        wasValidCommit()
    }

    override def binding: Seq[Item] = Option(_boundItem).toList

    // Single-node controls support refresh events
    override def supportsRefreshEvents = true

    final def wasReadonlyCommit(): Boolean = {
        val result = _wasReadonly
        _wasReadonly = _readonly
        result
    }

    final def wasRequiredCommit(): Boolean = {
        val result = _wasRequired
        _wasRequired = _required
        result
    }

    final def wasValidCommit(): Boolean = {
        val result = _wasValid
        _wasValid = _valid
        result
    }

    final def wasAlertLevelCommit() = {
        val result = _wasAlertLevel
        _wasAlertLevel = _alertLevel
        result
    }

    final def wasFailedConstraintsCommit() = {
        val result = _wasFailedConstraints
        _wasFailedConstraints = _failedConstraints
        result
    }

    def isValueChangedCommit() = false // TODO: move this to trait shared by value control and variable
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
        if (bc.newBind) {
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
                isReadonly      == other.isReadonly &&
                isRequired      == other.isRequired &&
                isValid         == other.isValid &&
                alertLevel == other.alertLevel &&
                customMIPs      == other.customMIPs &&
                super.equalsExternal(other)
            case _ ⇒ false
        }

    // Static read-only if we are read-only and static (global or local setting)
    override def isStaticReadonly = isReadonly && hasStaticReadonlyAppearance

    def hasStaticReadonlyAppearance =
        XFormsProperties.isStaticReadonlyAppearance(containingDocument) ||
            XFormsProperties.READONLY_APPEARANCE_STATIC_VALUE == element.attributeValue(XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_QNAME)

    override def setFocus(inputOnly: Boolean): Boolean = Focus.focusWithEvents(this)

    override def outputAjaxDiff(ch: XMLReceiverHelper, other: XFormsControl, attributesImpl: AttributesImpl, isNewlyVisibleSubtree: Boolean) {
        assert(attributesImpl.getLength == 0)

        val control1 = other.asInstanceOf[XFormsSingleNodeControl]
        val control2 = this

        // Add attributes
        val doOutputElement = addAjaxAttributes(attributesImpl, isNewlyVisibleSubtree, other)
        
        def outputElement() =
            if (doOutputElement)
                ch.element("xxf", XXFORMS_NAMESPACE_URI, "control", attributesImpl)

        // Get current value if possible for this control
        // NOTE: We issue the new value in all cases because we don't have yet a mechanism to tell the
        // client not to update the value, unlike with attributes which can be omitted
        control2 match {
            case _: XFormsUploadControl | _: XFormsComponentControl ⇒
                outputElement()
            case valueControl: XFormsValueControl ⇒
                // TODO: Output value only when changed
                outputValueElement(ch, valueControl, doOutputElement, isNewlyVisibleSubtree, attributesImpl, "control")
            case _ ⇒
                // No value, just output element with no content (but there may be attributes)
                outputElement()
        }

        // Output attributes (like style) in no namespace which must be output via xxf:attribute
        // TODO: If only some attributes changed, then we also output xxf:control above, which is unnecessary
        addExtensionAttributesExceptClassAndAcceptForAjax(control1, "", isNewlyVisibleSubtree)(ch)
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

        def addAttribute(name: String, value: String) = {
            attributesImpl.addAttribute("", name, name, XMLReceiverHelper.CDATA, value)
            added = true
        }

        if (isNewlyVisibleSubtree && control2.isReadonly || control1 != null && control1.isReadonly != control2.isReadonly)
            addAttribute(READONLY_ATTRIBUTE_NAME, control2.isReadonly.toString)

        if (isNewlyVisibleSubtree && control2.isRequired || control1 != null && control1.isRequired != control2.isRequired)
            addAttribute(REQUIRED_ATTRIBUTE_NAME, control2.isRequired.toString)

        if (isNewlyVisibleSubtree && ! control2.isRelevant || control1 != null && control1.isRelevant != control2.isRelevant)
            addAttribute(RELEVANT_ATTRIBUTE_NAME, control2.isRelevant.toString)

        if (isNewlyVisibleSubtree && control2.alertLevel.isDefined || control1 != null && control1.alertLevel != control2.alertLevel)
            addAttribute(CONSTRAINT_LEVEL_ATTRIBUTE_NAME, control2.alertLevel map (_.name) getOrElse "")

        added |= addAjaxCustomMIPs(attributesImpl, isNewlyVisibleSubtree, control1, control2)

        added
    }

    protected def outputValueElement(ch: XMLReceiverHelper, valueControl: XFormsValueControl, doOutputElement: Boolean, isNewlyVisibleSubtree: Boolean, attributesImpl: Attributes, elementName: String) {

        // Create element with text value
        val value = valueControl.getEscapedExternalValue

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

        // MIP events
        if (isRequired)
            Dispatch.dispatchEvent(new XFormsRequiredEvent(this))

        if (isReadonly)
            Dispatch.dispatchEvent(new XFormsReadonlyEvent(this))

        if (! isValid)
            Dispatch.dispatchEvent(new XFormsInvalidEvent(this))
    }

    // NOTE: For the purpose of dispatching value change and MIP events, we used to make a
    // distinction between value controls and plain single-node controls. However it seems that it is
    // still reasonable to dispatch those events to xf:group, xf:switch, and even repeat
    // iterations if they are bound.
    override def dispatchChangeEvents() = {

        super.dispatchChangeEvents()

        // Gather changes
        // 2013-06-20: This is a change from what we were doing before, but it makes things clearer. Later we might
        // gather all events upon onCreate/onDestroy/onBindingUpdate. The behavior can change if a new refresh is
        // triggered when processing one of the events below. The order of events in that case is hard to predict.
        val valueChanged        = isValueChangedCommit()
        val iterationMoved      = previousEffectiveIdCommit() != getEffectiveId && part.observerHasHandlerForEvent(getPrefixedId, XXFORMS_ITERATION_MOVED)
        val validityChanged     = wasValidCommit()            != isValid
        val requiredChanged     = wasRequiredCommit()         != isRequired
        val readonlyChanged     = wasReadonlyCommit()         != isReadonly

        val previousConstraints = wasFailedConstraintsCommit()
        val constraintsChanged  = previousConstraints         != failedConstraints

        // This is needed because, unlike the other LHH, the alert doesn't only depend on its expressions: it also depends
        // on the control's current validity and constraints. Because we don't have yet a way of taking those in as
        // dependencies, we force dirty alerts whenever such constraints change upon refresh.
        if (validityChanged || constraintsChanged)
            forceDirtyAlert()

        // Value change
        if (isRelevant && valueChanged)
            Dispatch.dispatchEvent(new XFormsValueChangeEvent(this)) // NOTE: should have context info

        // Iteration change
        if (isRelevant && iterationMoved)
            Dispatch.dispatchEvent(new XXFormsIterationMovedEvent(this)) // NOTE: should have context info

        // MIP change
        if (isRelevant && validityChanged)
            Dispatch.dispatchEvent(if (isValid) new XFormsValidEvent(this) else new XFormsInvalidEvent(this))

        if (isRelevant && requiredChanged)
            Dispatch.dispatchEvent(if (isRequired) new XFormsRequiredEvent(this) else new XFormsOptionalEvent(this))

        if (isRelevant && readonlyChanged)
            Dispatch.dispatchEvent(if (isReadonly) new XFormsReadonlyEvent(this) else new XFormsReadwriteEvent(this))

        if (isRelevant && constraintsChanged)
            Dispatch.dispatchEvent(new XXFormsConstraintsChangedEvent(this, alertLevel, previousConstraints, failedConstraints))
    }
}

object XFormsSingleNodeControl {

    // Item relevance (atomic values are considered relevant)
    def isRelevantItem(item: Item) =
        item match {
            case info: NodeInfo ⇒ InstanceData.getInheritedRelevant(info)
            case _              ⇒ true
        }

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

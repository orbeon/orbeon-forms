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

import XFormsValueControl._
import collection.JavaConverters._
import org.dom4j.Element
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.event.XFormsEvent
import org.orbeon.oxf.xforms.event.events.XXFormsValueEvent
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xforms.{XFormsModelBinds, XFormsProperties}
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.oxf.xml.{ContentHandlerHelper, NamespaceMapping}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.saxon.value._
import org.orbeon.oxf.xforms.analysis.ControlAnalysisFactory.ValueControl

// For Java classes that can't directly implement XFormsValueControl
abstract class XFormsValueControlBase(container: XBLContainer, parent: XFormsControl, element: Element, effectiveId: String)
    extends XFormsSingleNodeControl(container, parent, element, effectiveId)
    with XFormsValueControl

// For Java classes that can't directly implement FocusableTrait
abstract class XFormsValueFocusableControlBase(container: XBLContainer, parent: XFormsControl, element: Element, effectiveId: String)
    extends XFormsValueControlBase(container, parent, element, effectiveId)
    with FocusableTrait

abstract class XFormsSingleNodeFocusableControlBase(container: XBLContainer, parent: XFormsControl, element: Element, effectiveId: String)
    extends XFormsSingleNodeControl(container, parent, element, effectiveId)
    with FocusableTrait

// Trait for for all controls that hold a value
trait XFormsValueControl extends XFormsSingleNodeControl {

    override type Control <: ValueControl

    // Value
    private[XFormsValueControl] var _value: String = null // TODO: use ControlProperty<String>?

    // Previous value for refresh
    private[XFormsValueControl] var _previousValue: String = null

    // External value (evaluated lazily)
    private[XFormsValueControl] var isExternalValueEvaluated: Boolean = false
    private[XFormsValueControl] var externalValue: String = null

    override def onCreate(): Unit = {
        super.onCreate()

        _value = null
        _previousValue = null

        markExternalValueDirty()
    }

    override def evaluateImpl(relevant: Boolean, parentRelevant: Boolean): Unit = {

        // Evaluate other aspects of the control if necessary
        super.evaluateImpl(relevant, parentRelevant)

        // Evaluate control values
        if (relevant) {
            // Control is relevant
            // NOTE: Ugly test on staticControl is to handle the case of xf:output within LHHA
            if ((_value eq null) || (staticControl eq null) || containingDocument.getXPathDependencies.requireValueUpdate(getPrefixedId)) {
                evaluateValue()
                markExternalValueDirty()
            }
        } else {
            // Control is not relevant
            isExternalValueEvaluated = true
            _value = null
        }

        // NOTE: We no longer evaluate the external value here, instead we do lazy evaluation. This is good in particular when there
        // are multiple refreshes during an Ajax request, and LHHA values are only needed in the end.
    }

    def evaluateValue(): Unit =
        setValue(DataModel.getValue(getBoundItem))

    def evaluateExternalValue(): Unit =
        // By default, same as value
        setExternalValue(getValue)

    protected def markExternalValueDirty(): Unit = {
        isExternalValueEvaluated = false
        externalValue = null
    }

    protected def isExternalValueDirty: Boolean =
        ! isExternalValueEvaluated

    override def isValueChanged(): Boolean = {
        val result = _previousValue != _value
        _previousValue = _value
        result
    }

    // This usually doesn't need to be overridden (only XFormsUploadControl as of 2012-08-15)
    def storeExternalValue(externalValue: String) = doStoreExternalValue(externalValue)

    // Subclasses can override this to translate the incoming external value
    def translateExternalValue(externalValue: String) = externalValue

    // Set the external value into the instance
    final def doStoreExternalValue(externalValue: String): Unit = {
        // NOTE: Standard value controls should be bound to simple content only. Is there anything we should / can do
        // about this? See: https://github.com/orbeon/orbeon-forms/issues/13

        val boundItem = getBoundItem
        if (! boundItem.isInstanceOf[NodeInfo])// this should not happen
            throw new OXFException("Control is no longer bound to a node. Cannot set external value.")

        val translatedValue = translateExternalValue(externalValue)

        DataModel.jSetValueIfChanged(containingDocument, getIndentedLogger, this, getLocationData, boundItem.asInstanceOf[NodeInfo], translatedValue, "client", isCalculate = false)

        // NOTE: We do *not* call evaluate() here, as that will break the difference engine. doSetValue() above marks
        // the controls as dirty, and they will be evaluated when necessary later.
    }

    final protected def getValueUseFormat(format: Option[String]) =
        format flatMap valueWithSpecifiedFormat orElse valueWithDefaultFormat

    // Format value according to format attribute
    final protected def valueWithSpecifiedFormat(format: String): Option[String] = {
        assert(isRelevant)
        assert(getValue ne null)

        evaluateAsString(format, Seq(StringValue.makeStringValue(getValue)), 1)
    }

    // Try default format for known types
    final protected def valueWithDefaultFormat: Option[String] = {
        assert(isRelevant)
        assert(getValue ne null)

        Option(getBuiltinTypeName) flatMap
            (typeName ⇒ Option(XFormsProperties.getTypeOutputFormat(containingDocument, typeName))) flatMap
                (evaluateAsString(
                    _,
                    Option(StringValue.makeStringValue(getValue)),
                    FormatNamespaceMapping,
                    getContextStack.getCurrentVariables))
    }

    /**
     * Return the control's internal value.
     */
    final def getValue = _value
    final def isEmpty = XFormsModelBinds.isEmptyValue(_value)

    /**
     * Return the control's external value is the value as exposed to the UI layer.
     */
    final def getExternalValue(): String = {
        if (! isExternalValueEvaluated) {
            if (isRelevant)
                evaluateExternalValue()
            else
                // NOTE: if the control is not relevant, nobody should ask about this in the first place
                setExternalValue(null)

            isExternalValueEvaluated = true
        }
        externalValue
    }

    /**
     * Return the external value ready to be inserted into the client after an Ajax response.
     */
    def getRelevantEscapedExternalValue = getExternalValue
    def getNonRelevantEscapedExternalValue: String =  ""

    final def getEscapedExternalValue =
        if (isRelevant)
            // NOTE: Not sure if it is still possible to have a null value when the control is relevant
            Option(getRelevantEscapedExternalValue) getOrElse ""
        else
            // Some controls don't have "" as non-relevant value
            getNonRelevantEscapedExternalValue

    protected final def setValue(value: String): Unit =
        this._value = value

    protected final def setExternalValue(externalValue: String): Unit =
        this.externalValue = externalValue

    override def getBackCopy: AnyRef = {
        // Evaluate lazy values
        getExternalValue()
        super.getBackCopy
    }

    override def equalsExternal(other: XFormsControl): Boolean =
        other match {
            case other if this eq other ⇒ true
            case other: XFormsValueControl ⇒
                getExternalValue == other.getExternalValue &&
                super.equalsExternal(other)
            case _ ⇒ false
        }

    override def performDefaultAction(event: XFormsEvent): Unit = event match {
        case xxformsValue: XXFormsValueEvent ⇒ storeExternalValue(xxformsValue.value)
        case _ ⇒ super.performDefaultAction(event)
    }

    override def toXML(helper: ContentHandlerHelper, attributes: List[String])(content: ⇒ Unit): Unit =
        super.toXML(helper, attributes) {
            helper.text(getExternalValue())
        }

    override def writeMIPs(write: (String, String) ⇒ Unit) {
        super.writeMIPs(write)

        if (isRequired)
            write("required-and-empty", XFormsModelBinds.isEmptyValue(getValue).toString)
    }
}

object XFormsValueControl {

    val FormatNamespaceMapping =
        new NamespaceMapping(
            Map(
                XSD_PREFIX           → XSD_URI,
                XFORMS_PREFIX        → XFORMS_NAMESPACE_URI,
                XFORMS_SHORT_PREFIX  → XFORMS_NAMESPACE_URI,
                XXFORMS_PREFIX       → XXFORMS_NAMESPACE_URI,
                XXFORMS_SHORT_PREFIX → XXFORMS_NAMESPACE_URI,
                EXFORMS_PREFIX       → EXFORMS_NAMESPACE_URI
            ).asJava)
}
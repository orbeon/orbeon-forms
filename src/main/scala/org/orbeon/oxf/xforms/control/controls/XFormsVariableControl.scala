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

import org.dom4j.Element
import org.orbeon.oxf.xforms.analysis.VariableAnalysisTrait
import org.orbeon.oxf.xforms.analysis.controls.VariableControl
import org.orbeon.oxf.xforms.control.{NoLHHATrait, XFormsControl, XFormsSingleNodeControl}
import org.orbeon.oxf.xforms.state.ControlState
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xforms.{BindingContext, Variable}
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.om._
import org.orbeon.saxon.value.EmptySequence

/**
 * Representation of a variable in a tree of controls.
 *
 * Some of the logic is similar to what is in XFormsValueControl, except this works with ValueRepresentation.
 */
class XFormsVariableControl(container: XBLContainer, parent: XFormsControl, element: Element, effectiveId: String)
    extends XFormsSingleNodeControl(container, parent, element, effectiveId) with NoLHHATrait {

  override type Control <: VariableControl

  // Actual variable name/value representation
  private val variable = new Variable(staticControl.asInstanceOf[VariableAnalysisTrait], containingDocument)

  // This is the context within or after this control, which is affected by the value of the variable
  private var _bindingContextForChild: BindingContext = null
  private var _bindingContextForFollowing: BindingContext = null

  private var _value: ValueRepresentation = null
  // Previous value for refresh
  private var _previousValue: ValueRepresentation = null

  final def getValue = _value
  def getVariableName = variable.getVariableName

  override def bindingContextForFollowing = _bindingContextForFollowing
  override def bindingContextForChild = _bindingContextForChild
  override def supportAjaxUpdates = false
  override def focusableControls = Iterator.empty

  override def onCreate(restoreState: Boolean, state: Option[ControlState]): Unit = {
    super.onCreate(restoreState, state)
    // FIXME: Case should be caught by the requireValueUpdate() below, but it's more fail-safe to mark things dirty here too
    _value = null
  }

  override def evaluateImpl(relevant: Boolean, parentRelevant: Boolean): Unit = {

    super.evaluateImpl(relevant, parentRelevant)

    if (relevant) {
      // Evaluate variable value if needed if relevant
      if ((_value eq null) || containingDocument.getXPathDependencies.requireValueUpdate(getPrefixedId)) {
        variable.markDirty()
        val contextStack = getContextStack
        contextStack.setBinding(bindingContext)
        containingDocument.getRequestStats.withXPath(variable.expression) {
          _value = variable.getVariableValue(contextStack, getEffectiveId, false, true)
        }
      }
    } else {
      // Value is empty sequence if non-relevant
      _value = EmptySequence.getInstance
    }

    // Push variable value on the stack
    // NOTE: The following should be reasonably cheap, in case the variable had not been made dirty. It would be
    // nice to keep _bindingContextForChild/_bindingContextForFollowing if no reevaluation is needed, but then we
    // must make sure the chain of contexts is correct.
    val bc = bindingContext
    getContextStack.setBinding(bc)
    if (parentRelevant) {
      _bindingContextForChild     = bc.pushVariable(staticControl, staticControl.name, _value, staticControl.scope)
      _bindingContextForFollowing = bc.parent.pushVariable(staticControl, staticControl.name, _value, staticControl.scope)
    } else {
      // If we are within a non-relevant container, don't even bother pushing variables
      val empty = BindingContext.empty(staticControl.element, staticControl.scope)
      _bindingContextForChild     = empty
      _bindingContextForFollowing = empty
    }
  }

  override def isValueChangedCommit() = {
    val result = ! SaxonUtils.compareValueRepresentations(_previousValue, _value)
    _previousValue = _value
    result
  }

  // Variables don't support Ajax updates
  override def equalsExternal(other: XFormsControl) = throw new IllegalStateException
}

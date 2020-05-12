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

import org.orbeon.oxf.xforms.BindingContext
import org.orbeon.oxf.xforms.state.ControlState
import org.orbeon.saxon.om.Item
import collection.JavaConverters._

trait ControlBindingSupport {

  self: XFormsControl =>

  // This control's binding context
  private[ControlBindingSupport] var _bindingContext: BindingContext = null
  final def bindingContext = _bindingContext

  // The control's binding, by default none
  // NOTE: This is only used by `event('xxf:binding')` and `xxf:binding()`. Following
  // https://github.com/orbeon/orbeon-forms/issues/3829, we decide to return the binding even if the
  // control is non-relevant.
  def bindingEvenIfNonRelevant: Seq[Item] = Nil

  // Find the control's binding context
  final def contextForBinding: Option[Item] =
    for {
      currentBinding <- Option(_bindingContext)
      parentBinding  <- Option(currentBinding.parent)
      singleItem     <- parentBinding.nodeset.asScala.lift(parentBinding.position - 1)
    } yield
      singleItem

  // Find the bind object for this control, if it has one
  final def bind = Option(_bindingContext.bind)

  // Relevance
  private[ControlBindingSupport] var _isRelevant = false
  final def isRelevant = _isRelevant

  private[ControlBindingSupport] var _wasRelevant = false
  private[ControlBindingSupport] var _wasContentRelevant = false
  final def wasRelevant = _wasRelevant
  final def wasContentRelevant = _wasContentRelevant

  // Whether this control's content is visible (by default it is)
  def contentVisible = true

  // Whether this control's content is relevant
  def contentRelevant = _isRelevant && contentVisible

  // Evaluate the control's binding and value either during create or update
  final def evaluateBindingAndValues(
    parentContext : BindingContext,
    update        : Boolean,
    restoreState  : Boolean,
    state         : Option[ControlState]
  ): Unit = {
    // Evaluate and set binding context as needed
    val pr = parentContentRelevant
    setBindingContext(
      if (pr)
        computeBinding(parentContext)
      else
        BindingContext.empty(element, staticControl.scope),
      pr,
      update,
      restoreState,
      state
    )
  }

  // Refresh the control's binding during update, in case a re-evaluation is not needed
  final def refreshBindingAndValues(parentContext: BindingContext): Unit = {
    // Make sure the parent is updated, as ancestor bindings might have changed, and it is important to
    // ensure that the chain of bindings is consistent
    setBindingContext(
      bindingContext = bindingContext.copy(parent = parentContext),
      parentRelevant = parentContentRelevant,
      update         = true,
      restoreState   = false,
      state          = None
    )
  }

  // Default binding evaluation
  protected def computeBinding(parentContext: BindingContext): BindingContext = {
    val contextStack = container.getContextStack
    contextStack.setBinding(parentContext)
    contextStack.pushBinding(element, effectiveId, staticControl.scope)
    contextStack.getCurrentBindingContext
  }

  final protected def computeBindingCopy(context: BindingContext): BindingContext = {
    val contextStack = container.getContextStack
    contextStack.setBinding(context)
    contextStack.pushCopy()
  }

  // Return the bindings in effect within and after this control
  def bindingContextForChildOpt  : Option[BindingContext] = Option(_bindingContext)
  def bindingContextForFollowing : BindingContext         = _bindingContext.parent

  final def bindingContextForChildOrEmpty: BindingContext =
    bindingContextForChildOpt getOrElse (throw new IllegalStateException)

  // Set this control's binding context and handle create/destroy/update lifecycle
  final def setBindingContext(
    bindingContext : BindingContext,
    parentRelevant : Boolean,
    update         : Boolean,
    restoreState   : Boolean,
    state          : Option[ControlState]
  ): Unit = {
    val oldBinding = this._bindingContext
    this._bindingContext = bindingContext

    // Relevance is a property of all controls
    val oldRelevant = this._isRelevant
    val newRelevant = parentRelevant && computeRelevant

    if (! oldRelevant && newRelevant) {
      // Control becomes relevant
      this._isRelevant = true
      onCreate(restoreState, state, update)
      if (update)
        markDirtyImpl()
      evaluate()
    } else if (oldRelevant && ! newRelevant) {
      // Control becomes non-relevant
      onDestroy(update)
      this._isRelevant = false
      evaluateNonRelevant(parentRelevant)
    } else if (newRelevant) {
      // Control remains relevant
      onBindingUpdate(oldBinding, bindingContext)
      if (update)
        markDirtyImpl()
      evaluate()
    } else if (! update) {
      // Control is created non-relevant
      evaluateNonRelevant(parentRelevant)
    }
  }

  // Control lifecycle
  def onCreate(restoreState: Boolean, state: Option[ControlState], update: Boolean): Unit = {
    _wasRelevant = false
    _wasContentRelevant = false
  }

  def onDestroy(update: Boolean): Unit = ()

  def onBindingUpdate(oldBinding: BindingContext, newBinding: BindingContext): Unit = ()

  // Compute relevance in addition to the parentRelevant logic
  // For subclasses to call super.computeRelevant()
  def computeRelevant = true

  // By default: if there is a parent, we have the same relevance as the parent, otherwise we are top-level so
  // we are relevant by default. Also, we are not relevant if the parent says its content is not visible.
  private final def parentContentRelevant = (parent eq null) || parent.contentRelevant

  final def wasRelevantCommit() = {
    val result = _wasRelevant
    _wasRelevant = _isRelevant
    _wasContentRelevant = contentRelevant
    result
  }
}

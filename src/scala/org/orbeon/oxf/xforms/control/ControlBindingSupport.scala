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

import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.BindingContext
import org.orbeon.saxon.om.Item
import collection.JavaConverters._

trait ControlBindingSupport {

    self: XFormsControl ⇒

    // This control's binding context
    var bindingContext: BindingContext = null
    final def getBindingContext: BindingContext = bindingContext
    final def getBindingContext(containingDocument: XFormsContainingDocument): BindingContext = bindingContext

    // The control's binding, by default none
    def binding: Seq[Item] = Seq()

    // Find the control's binding context
    def contextForBinding: Seq[Item] = Option(bindingContext) flatMap
        (binding ⇒ Option(binding.parent)) map
            (binding ⇒ binding.nodeset.asScala) getOrElse
                Seq()

    // Find the bind object for this control, if it has one
    def bind = Option(bindingContext.bind)

    // Relevance
    private var _isRelevant = false
    final def isRelevant = _isRelevant

    private var _wasRelevant = false
    final def wasRelevant = _wasRelevant

    // Evaluate the control's binding, either during create or update
    final def evaluateBinding(parentContext: BindingContext, update: Boolean) = {
        pushBinding(parentContext, update)
        evaluateChildFollowingBinding()
    }

    // Refresh the control's binding during update, in case a re-evaluation is not needed
    final def refreshBinding(parentContext: BindingContext) = {
        // Make sure the parent is updated, as ancestor bindings might have changed, and it is important to
        // ensure that the chain of bindings is consistent
        setBindingContext(getBindingContext.copy(parent = parentContext))
        markDirtyImpl(containingDocument.getXPathDependencies)
        evaluateChildFollowingBinding()
    }

    final protected def pushBinding(parentContext: BindingContext, update: Boolean) = {
        pushBindingImpl(parentContext)

        if (update)
            markDirtyImpl(containingDocument.getXPathDependencies)
    }

    // Default behavior for pushing a binding
    protected def pushBindingImpl(parentContext: BindingContext) = {

        // Compute new binding
        val newBindingContext = {
            val contextStack = container.getContextStack
            contextStack.setBinding(parentContext)
            contextStack.pushBinding(element, effectiveId, staticControl.scope)
            contextStack.getCurrentBindingContext
        }

        // Set binding context
        setBindingContext(newBindingContext)

        newBindingContext
    }

    final protected def pushBindingCopy(context: BindingContext) = {
        // Compute new binding
        val newBindingContext = {
            val contextStack = container.getContextStack
            contextStack.setBinding(context)
            contextStack.pushCopy()
        }

        // Set binding context
        setBindingContext(newBindingContext)

        newBindingContext
    }

    // Update the bindings in effect within and after this control
    // Only variables modify the default behavior
    def evaluateChildFollowingBinding() = ()

    // Return the bindings in effect within and after this control
    def bindingContextForChild = bindingContext
    def bindingContextForFollowing = bindingContext.parent

    // Set this control's binding context and handle create/destroy/update lifecycle
    final def setBindingContext(bindingContext: BindingContext) {
        val oldBinding = this.bindingContext
        this.bindingContext = bindingContext

        // Relevance is a property of all controls
        val oldRelevant = this._isRelevant
        val newRelevant = computeRelevant

        if (! oldRelevant && newRelevant) {
            // Control is created
            this._isRelevant = newRelevant
            onCreate()
        } else if (oldRelevant && ! newRelevant) {
            // Control is destroyed
            onDestroy()
            this._isRelevant = newRelevant
        } else if (newRelevant)
            onBindingUpdate(oldBinding, bindingContext)
    }

    def onCreate() = { _wasRelevant = false; visited = false }
    def onDestroy() = ()
    def onBindingUpdate(oldBinding: BindingContext, newBinding: BindingContext) = ()

    def computeRelevant =
        // By default: if there is a parent, we have the same relevance as the parent, otherwise we are top-level so
        // we are relevant by default
        (parent eq null) || parent.isRelevant

    def wasRelevantCommit() = {
        val result = _wasRelevant
        _wasRelevant = _isRelevant
        result
    }
}

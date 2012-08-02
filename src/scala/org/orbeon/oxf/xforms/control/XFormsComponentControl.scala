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
import org.orbeon.oxf.xforms.analysis.controls.ComponentControl
import org.orbeon.oxf.xforms.event.XFormsEvents
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.BindingContext
import org.xml.sax.helpers.AttributesImpl

/**
 * Control that represents a custom components.
 *
 * A component control contains a nested container, which handles:
 *
 * o models nested within component (which we are not 100% happy with as models should be allowed in other places)
 * o HOWEVER this might still be all right for models within xbl:implementation if any
 * o event dispatching
 */
class XFormsComponentControl(container: XBLContainer, parent: XFormsControl, element: Element, effectiveId: String)
        extends XFormsSingleNodeContainerControl(container, parent, element, effectiveId) {

    override type Control = ComponentControl

    private var _nestedContainer: Option[XBLContainer] = None
    def nestedContainer = _nestedContainer.get

    // Create nested container upon creation
    createNestedContainer()

    def createNestedContainer(): Unit = {

        assert(_nestedContainer.isEmpty)

        val newContainer = container.createChildContainer(this)
        // There may or may not be nested models
        newContainer.addAllModels()
        // Make sure there is location data
        newContainer.locationData = getLocationData

        _nestedContainer = Some(newContainer)
    }

    // Destroy container and models if any
    def destroyNestedContainer(): Unit = {
        nestedContainer.destroy()
        _nestedContainer = None
    }

    def recreateNestedContainer(): Unit = {
        createNestedContainer()
        if (isRelevant)
            initializeModels()
    }

    // Only handle binding if we support modeBinding
    override protected def pushBindingImpl(parentContext: BindingContext): BindingContext = {
        val newBindingContext =
            if (staticControl.binding.abstractBinding.modeBinding)
                super.pushBindingImpl(parentContext)
            else
                super.pushBindingCopy(parentContext)

        newBindingContext
    }

    override def bindingContextForChild = {
        // Start with inner context
        // For nested event handlers, this still works, because the nested handler can never match the inner scope. So
        // the context goes inner context → component binding.
        val contextStack = nestedContainer.getContextStack
        contextStack.setParentBindingContext(getBindingContext)
        contextStack.resetBindingContext
        contextStack.getCurrentBindingContext
    }

    override def onCreate() {
        super.onCreate()
        if (containingDocument.isRestoringDynamicState)
            nestedContainer.restoreModelsState()
        else
            initializeModels()
    }

    private def initializeModels(): Unit =
        nestedContainer.initializeModels(Array[String](
            XFormsEvents.XFORMS_MODEL_CONSTRUCT,
            XFormsEvents.XFORMS_MODEL_CONSTRUCT_DONE
        ))

    override def onBindingUpdate(oldBinding: BindingContext, newBinding: BindingContext) {
        super.onBindingUpdate(oldBinding, newBinding)
        val isNodesetChange = ! Controls.compareNodesets(oldBinding.getNodeset, newBinding.getNodeset)
        if (isNodesetChange) {
            // Control's binding changed
            // NOP for now
        }
    }

    // This is called iif the iteration index changes
    override def updateEffectiveId() {

        // Update rest of control tree
        super.updateEffectiveId()

        // Update container with new effective id
        nestedContainer.updateEffectiveId(getEffectiveId)
    }

    override def iterationRemoved() {
        // Inform descendants
        super.iterationRemoved()

        // Destroy container and models if any
        destroyNestedContainer()
    }

    // Simply delegate but switch the container
    override def buildChildren(buildTree: (XBLContainer, BindingContext, ElementAnalysis, Seq[Int]) ⇒ Option[XFormsControl], idSuffix: Seq[Int]) =
        Controls.buildChildren(this, staticControl.children, (_, bindingContext, staticElement, idSuffix) ⇒ buildTree(nestedContainer, bindingContext, staticElement, idSuffix), idSuffix)

    private lazy val handleLHHA = staticControl.binding.abstractBinding.modeLHHA && ! staticControl.binding.abstractBinding.modeLHHACustom

    // Don't add Ajax LHHA for custom-lhha mode
    override def addAjaxLHHA(other: XFormsControl, attributesImpl: AttributesImpl, isNewlyVisibleSubtree: Boolean) =
        handleLHHA && super.addAjaxLHHA(other, attributesImpl, isNewlyVisibleSubtree)

    // Consider LHHA hasn't externally changed for custom-lhha mode
    override def compareLHHA(other: XFormsControl) =
        ! handleLHHA || super.compareLHHA(other)
}
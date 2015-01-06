/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.analysis

import org.orbeon.oxf.xforms.analysis.controls.{AttributeControl, ComponentControl}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xforms.analysis.model.StaticBind
import org.orbeon.oxf.xforms.xbl.{AbstractBinding, Scope, XBLBindings}
import org.orbeon.oxf.xforms.XFormsUtils
import org.dom4j.{Element, QName}
import scala.collection.mutable

trait PartXBLAnalysis extends TransientState {

    self: PartAnalysisImpl ⇒

    val xblBindings = new XBLBindings(getIndentedLogger, this, metadata, staticStateDocument.xblElements)

    private[PartXBLAnalysis] val scopesById              = mutable.HashMap[String, Scope]()
    private[PartXBLAnalysis] val prefixedIdToXBLScopeMap = mutable.HashMap[String, Scope]()

    protected def initializeScopes() {
        // Add existing ids to scope map
        val prefix = startScope.fullPrefix
        metadata.idGenerator.add("#document") // top-level is not added to the id generator until now
        for {
            staticId   ← metadata.idGenerator.ids
            prefixedId = prefix + staticId
        } locally {
            mapScopeIds(staticId, prefixedId, startScope, ignoreIfPresent = false)
        }

        registerScope(startScope)
    }

    def newScope(parent: Scope, scopeId: String) =
        registerScope(new Scope(parent, scopeId))

    private def registerScope(scope: Scope) = {
        assert(! scopesById.contains(scope.scopeId))

        scopesById += scope.scopeId → scope
        scope
    }

    def deregisterScope(scope: Scope) =
        scopesById -= scope.scopeId

    def mapScopeIds(staticId: String, prefixedId: String, scope: Scope, ignoreIfPresent: Boolean): Unit =
        if (prefixedIdToXBLScopeMap.contains(prefixedId)) {
            if (! ignoreIfPresent)
                throw new OXFException("Duplicate id found for prefixed id: " + prefixedId)
        } else {
            scope += staticId → prefixedId
            prefixedIdToXBLScopeMap += prefixedId → scope
        }

    // Deindex the given control's XBL-related information
    def unmapScopeIds(control: ElementAnalysis): Unit = {
        control match {
            case component: ComponentControl ⇒
                xblBindings.removeBinding(component.prefixedId)
                deregisterScope(component.binding.innerScope)
            case attribute: AttributeControl ⇒
                control.scope -= attribute.forStaticId
                prefixedIdToXBLScopeMap -= attribute.forPrefixedId
            case bind: StaticBind ⇒
                bind.iterateNestedIds foreach { mipId ⇒
                    control.scope -= mipId
                    prefixedIdToXBLScopeMap -= XFormsUtils.getRelatedEffectiveId(control.prefixedId, mipId)
                }
            case _ ⇒
        }
        control.scope -= control.staticId
        prefixedIdToXBLScopeMap -= control.prefixedId
    }

    def containingScope(prefixedId: String) = {
        val prefix = XFormsUtils.getEffectiveIdPrefix(prefixedId)

        val scopeId = if (prefix.length == 0) "" else prefix.init
        scopesById.get(scopeId).orNull
    }

    def scopeForPrefixedId(prefixedId: String) =
        prefixedIdToXBLScopeMap.get(prefixedId) orNull // NOTE: only one caller tests for null: XBLContainer.findResolutionScope

    def isComponent(binding: QName) = xblBindings.isComponent(binding)
    def getBinding(prefixedId: String) = xblBindings.getBinding(prefixedId)
    def getBindingId(prefixedId: String) = xblBindings.getBindingId(prefixedId)
    def jBindingQNames = xblBindings.abstractBindings.keys toSeq
    def getAbstractBinding(binding: QName) = xblBindings.abstractBindings.get(binding)

    def abstractBindings: Iterable[AbstractBinding] = xblBindings.abstractBindings.values

    // Search scope in ancestor or self parts
    def searchResolutionScopeByPrefixedId(prefixedId: String) =
        ancestorOrSelfIterator map (_.scopeForPrefixedId(prefixedId)) find (_ ne null) get

    def getGlobals = xblBindings.allGlobals

    // For the given bound node prefixed id, remove the current shadow tree and create a new one
    // NOTE: Can be used only in a sub-part, as this mutates the tree
    def updateShadowTree(prefixedId: String, element: Element) = {
        assert(! isTopLevel)
        getControlAnalysis(prefixedId) match {
            case existingComponent: ComponentControl ⇒

                // Remove the component's binding, including all controls
                existingComponent.removeBinding()

                // Create new ConcreteBinding
                existingComponent.setBinding(element)

                // Rebuild subtree
                analyzeSubtree(existingComponent)

                existingComponent

            case _ ⇒
                throw new IllegalArgumentException
        }
    }

    override def freeTransientState() = {
        super.freeTransientState()
        xblBindings.freeTransientState()
    }
}
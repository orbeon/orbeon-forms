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

import scala.collection.JavaConverters._
import collection.mutable.HashMap
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xforms.{XFormsConstants, XFormsContainingDocument}
import org.dom4j.QName
import org.orbeon.oxf.xforms.xbl.{Scope, XBLBindings}

trait PartXBLAnalysis extends TransientState {

    this: PartAnalysisImpl =>

    val xblBindings = new XBLBindings(getIndentedLogger, this, metadata, staticStateDocument.xblElements)
    private[PartXBLAnalysis] val scopesById = HashMap[String, Scope]()
    private[PartXBLAnalysis] val prefixedIdToXBLScopeMap = HashMap[String, Scope]()

    protected def initializeScopes() {
        // Add existing ids to scope map
        val prefix = startScope.fullPrefix
        for {
            id ← metadata.idGenerator.ids.asScala
            prefixedId = prefix + id
        } yield {
            startScope += id → prefixedId
            indexScope(prefixedId, startScope)
        }

        // Add top-level if needed
        if (startScope.isTopLevelScope)
            indexScope(XFormsContainingDocument.CONTAINING_DOCUMENT_PSEUDO_ID, startScope)

        // Tell top-level static id generator to stop checking for duplicate ids
        // TODO: not nice, check what this is about
        metadata.idGenerator.setCheckDuplicates(false)

        registerScope(startScope)
    }

    def newScope(parent: Scope, scopeId: String) =
        registerScope(new Scope(parent, scopeId))

    private def registerScope(scope: Scope) = {
        assert(! scopesById.contains(scope.scopeId))

        scopesById += scope.scopeId → scope
        scope
    }

    def indexScope(prefixedId: String, scope: Scope) {
        if (prefixedIdToXBLScopeMap.contains(prefixedId))
            throw new OXFException("Duplicate id found for prefixed id: " + prefixedId)

        prefixedIdToXBLScopeMap += prefixedId → scope
    }

    def getResolutionScopeByPrefix(prefix: String) = {
        require(prefix.length == 0 || prefix.charAt(prefix.length - 1) == XFormsConstants.COMPONENT_SEPARATOR)

        val scopeId = if (prefix.length == 0) "" else prefix.substring(0, prefix.length - 1)
        scopesById.get(scopeId).orNull
    }

    def getResolutionScopeByPrefixedId(prefixedId: String) =
        prefixedIdToXBLScopeMap.get(prefixedId) orNull // NOTE: only one caller tests for null: XBLContainer.findResolutionScope

    def isComponent(binding: QName) = xblBindings.isComponent(binding)
    def getBinding(prefixedId: String) = xblBindings.getBinding(prefixedId)
    def getBindingId(prefixedId: String) = xblBindings.getBindingId(prefixedId)
    def getBindingQNames = xblBindings.abstractBindings.keys toSeq
    def getAbstractBinding(binding: QName) = xblBindings.abstractBindings.get(binding)

    def getComponentBindings = xblBindings.abstractBindings

    // Search scope in ancestor or self parts
    def searchResolutionScopeByPrefixedId(prefixedId: String) =
        ancestorOrSelf map (_.getResolutionScopeByPrefixedId(prefixedId)) filter (_ ne null) head

    def getGlobals = xblBindings.allGlobals

    def getXBLStyles = xblBindings.allStyles
    def getXBLScripts = xblBindings.allScripts
    def baselineResources = xblBindings.baselineResources

    override def freeTransientState() = {
        super.freeTransientState()
        xblBindings.freeTransientState()
    }
}
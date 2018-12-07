/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.control.{Controls, XFormsSingleNodeControl}
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.model.{BindVariableResolver, RuntimeBind}
import org.orbeon.oxf.xforms.{NodeInfoFactory, XFormsUtils}
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.{Item, NodeInfo, ValueRepresentation}
import org.orbeon.saxon.value.SequenceExtent
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsId

trait FormRunnerActionsOps extends FormRunnerBaseOps {

  // Resolve target data nodes from an action source and a target control.
  //
  // Must be called from an XPath expression.
  //
  // As of 2014-01-31:
  //
  // - the source of an action is a concrete
  //     - control
  //     - or model
  // - the target
  //     - is a control name
  //     - which must correspond to an existing control (statically)
  //     - the control must be a value control (or at least a single-node binding control)
  //
  // We first try to resolve a concrete target control based on the source and the control name. If that works, great,
  // we then find the bound nodes if any. This returns:
  //
  // - 0 to 1 node if `followIndexes = false`
  // - 0 to n nodes if `followIndexes = true` (since 2016-02-25).
  //
  // If no node is returned above, this means that no target controls are relevant. We fall back to searching binds.
  // We first identify a bind for the source, if any. Then resolve the target bind. This returns 0 to n nodes.
  //
  // Other considerations:
  //
  // - in the implementation below, the source can also directly refer to a bind
  // - if the source is not found, the target is resolved from the enclosing model
  //
  // If would be good if the "find closest" algorithm was written once only for both binds and controls! Currently
  // (2016-02-29) it's not the case.
  //
  //@XPathFunction
  def resolveTargetRelativeToActionSource(
    actionSourceAbsoluteId : String,
    targetControlName      : String,
    followIndexes          : Boolean
  ): ValueRepresentation =
    resolveTargetRelativeToActionSourceOpt(
      actionSourceAbsoluteId,
      targetControlName,
      followIndexes
    ) map (new SequenceExtent(_)) orNull

  def resolveTargetRelativeToActionSourceOpt(
    actionSourceAbsoluteId : String,
    targetControlName      : String,
    followIndexes          : Boolean
  ): Option[Iterator[Item]] = {

    val container = XFormsFunction.context.container

    def fromControl: Option[Iterator[NodeInfo]] = {

      def findControls =
        Controls.resolveControlsById(
          containingDocument       = container.containingDocument,
          sourceControlEffectiveId = XFormsId.absoluteIdToEffectiveId(actionSourceAbsoluteId),
          targetStaticId           = controlId(targetControlName),
          followIndexes            = followIndexes
        )

      val boundNodes =
        findControls collect {
          case control: XFormsSingleNodeControl if control.isRelevant ⇒ control.boundNodeOpt
        } flatten

      boundNodes.nonEmpty option boundNodes.iterator
    }

    def fromBind: Option[Iterator[Item]] = {

      val sourceEffectiveId = XFormsFunction.context.sourceEffectiveId

      def findBindForSource =
        container.resolveObjectByIdInScope(sourceEffectiveId, actionSourceAbsoluteId) collect {
          case control: XFormsSingleNodeControl if control.isRelevant ⇒ control.bind
          case runtimeBind: RuntimeBind                               ⇒ Some(runtimeBind)
        } flatten

      def findBindNodeForSource =
        for (sourceRuntimeBind ← findBindForSource)
        yield
          sourceRuntimeBind.getOrCreateBindNode(1) // a control bound via `bind` always binds to the first item

      for {
        model             ← XFormsFunction.context.modelOpt
        modelBinds        ← model.modelBindsOpt
        targetStaticBind  ← model.staticModel.bindsById.get(bindId(targetControlName))
        value             ← BindVariableResolver.resolveClosestBind(modelBinds, findBindNodeForSource, targetStaticBind)
      } yield
        value
    }

    fromControl orElse fromBind
  }

  // Find the node which must store itemset map information
  //
  // - if the target is not under a repeat rooted at a common ancestor, there is no node to return
  // - otherwise, if there is a common ancestor repeat, return the node to which the common iteration binds
  // - otherwise, return the root element of the instance
  //
  //@XPathFunction
  def findItemsetMapNode(
    actionSourceAbsoluteId : String,
    targetControlName      : String,
    formInstance           : NodeInfo
  ): Option[NodeInfo] = {

    val doc = XFormsFunction.context.containingDocument

    val sourceEffectiveId = XFormsId.absoluteIdToEffectiveId(actionSourceAbsoluteId)
    val sourcePrefixedId  = XFormsId.getPrefixedId(sourceEffectiveId)
    val scope             = doc.getStaticOps.scopeForPrefixedId(sourcePrefixedId)
    val targetPrefixedId  = scope.prefixedIdForStaticId(controlId(targetControlName))

    val (ancestorRepeatPrefixedIdOpt, commonIndexesLeafToRoot, remainingRepeatPrefixedIdsLeafToRoot) =
      Controls.getStaticRepeatDetails(
        ops               = doc .getStaticOps,
        sourceEffectiveId = sourceEffectiveId,
        targetPrefixedId  = targetPrefixedId
      )

    remainingRepeatPrefixedIdsLeafToRoot.lastOption match {
      case None ⇒
        None
      case Some(_) ⇒
        ancestorRepeatPrefixedIdOpt match {
          case Some(ancestorRepeatPrefixedId) ⇒

            val tree = doc.getControls.getCurrentControlTree

            val repeat =
              tree.findRepeatControl(ancestorRepeatPrefixedId + Controls.buildSuffix(commonIndexesLeafToRoot.tail.reverse)) getOrElse
                (throw new IllegalStateException)

            val iterationBoundNode = repeat.children(commonIndexesLeafToRoot.head - 1).boundNodeOpt

            // For section templates we must make sure we don't return a node which is not within the template instance
            iterationBoundNode filter (_.root == formInstance.root) orElse Some(formInstance)

          case None ⇒
            Some(formInstance)
        }
    }
  }

  //@XPathFunction
  def findRepeatedControlsForTarget(actionSourceAbsoluteId: String, targetControlName: String): List[String] = {

    val controlsIt =
      Controls.iterateAllRepeatedControlsForTarget(
        XFormsFunction.context.containingDocument,
        XFormsId.absoluteIdToEffectiveId(actionSourceAbsoluteId),
        controlId(targetControlName)
      )

    controlsIt map (_.getEffectiveId) map XFormsId.effectiveIdToAbsoluteId toList
  }

  //@XPathFunction
  def addToItemsetMap(map: String, controlName: String, itemsetId: String): String =
    encodeSimpleQuery((controlName → itemsetId) :: decodeSimpleQuery(removeFromItemsetMap(map, controlName)))

  //@XPathFunction
  def removeFromItemsetMap(map: String, controlName: String): String =
    encodeSimpleQuery(decodeSimpleQuery(map) filterNot (_._1 == controlName))

  // Check all ancestors of `startNode` to find all itemset mappings. If the result is not empty, update elements
  // with matching names in the given template to add an `fr:itemsetid` attribute.
  //@XPathFunction
  def updateTemplateFromInScopeItemsetMaps(startNode: NodeInfo, template: NodeInfo): NodeInfo = {

    import XMLNames._

    val allMappings =
      startNode ancestor *            flatMap
      (_ attValueOpt ItemsetMapQName) flatMap
      decodeSimpleQuery               keepDistinctBy // for a given name, keep the first (from leaf to root) mapping
      (_._1)

    if (allMappings.isEmpty) {
      template
    } else {
      val newDoc = TransformerUtils.extractAsMutableDocument(template)

      val map = allMappings.toMap
      val allNames = map.keySet

      newDoc descendant * filter { e ⇒
        allNames(e.localname)
      } foreach { e ⇒
        XFormsAPI.insert(
          into   = e,
          origin = NodeInfoFactory.attributeInfo(ItemsetIdQName, map(e.localname))
        )
      }

      newDoc
    }
  }

  // All itemset ids referenced either by `@fr:itemsetid` or `@fr:itemsetmap`
  def itemsetIdsInUse(instance: NodeInfo) = {

    def attributesValues(test: Test) =
      instance.rootElement descendantOrSelf * att test map (_.stringValue)

    val uniqueIdsFromItemsetIds  = attributesValues(XMLNames.FRItemsetId).to[Set]
    val uniqueIdsFromItemsetMaps = (attributesValues(XMLNames.FRItemsetMap) flatMap (mapValue ⇒ decodeSimpleQuery(mapValue) map (_._2))).to[Set]

    uniqueIdsFromItemsetIds ++ uniqueIdsFromItemsetMaps
  }

  //@XPathFunction
  def garbageCollectMetadataItemsets(instance: NodeInfo): Unit =
    (instance.rootElement child XMLNames.FRMetadata headOption) foreach { metadataElem ⇒

      val uniqueIdsInUse = itemsetIdsInUse(instance)

      XFormsAPI.delete(metadataElem child "itemset" filterNot (uniqueIdsInUse contains _.id))

      if (! metadataElem.hasChildElement)
        XFormsAPI.delete(metadataElem)
    }
}

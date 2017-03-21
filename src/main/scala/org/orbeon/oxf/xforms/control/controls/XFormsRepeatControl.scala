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

import java.{util ⇒ ju}

import org.orbeon.dom.Element
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.CollectionUtils
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.action.actions.{XFormsDeleteAction, XFormsInsertAction}
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.{RepeatControl, RepeatIterationControl}
import org.orbeon.oxf.xforms.control._
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl._
import org.orbeon.oxf.xforms.event.events.{XXFormsDndEvent, XXFormsIndexChangedEvent, XXFormsNodesetChangedEvent, XXFormsSetindexEvent}
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent}
import org.orbeon.oxf.xforms.state.ControlState
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xforms.{BindingContext, ControlTree, XFormsContainingDocument}
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.om.{Item, NodeInfo}

import scala.collection.JavaConverters._
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.collection.{mutable ⇒ m}

// Represents an xf:repeat container control.
class XFormsRepeatControl(
  container   : XBLContainer,
  parent      : XFormsControl,
  element     : Element,
  effectiveId : String
) extends XFormsNoSingleNodeContainerControl(
  container,
  parent,
  element,
  effectiveId
) with NoLHHATrait {

  override type Control <: RepeatControl

  // TODO: this must be handled following the same pattern as usual refresh events
  private var refreshInfo: RefreshInfo = null

  // Initial local state
  setLocal(new XFormsRepeatControlLocal)

  // The repeat's sequence binding
  final override def binding = Option(bindingContext) filter (_.newBind) map (_.nodeset.asScala) getOrElse Nil

  // Store initial repeat index information
  private val startIndexString = element.attributeValue("startindex")
  private val startIndex = Option(startIndexString) map (_.toInt) getOrElse  1
  def getStartIndex = startIndex

  override def supportsRefreshEvents = true
  override def children = super.children.asInstanceOf[Seq[XFormsRepeatIterationControl]]

  override def onCreate(restoreState: Boolean, state: Option[ControlState]): Unit = {
    super.onCreate(restoreState, state)

    // Ensure that the initial state is set, either from default value, or for state deserialization.
    state match {
      case Some(state) ⇒
        setLocal(new XFormsRepeatControlLocal(state.keyValues("index").toInt))
      case None if restoreState ⇒
        // This can happen with xxf:dynamic, which does not guarantee the stability of ids, therefore state for
        // a particular control might not be found.
        setLocal(new XFormsRepeatControlLocal(ensureIndexBounds(getStartIndex)))
      case None ⇒
        setIndexInternal(getStartIndex)
    }

    // Reset refresh information
    refreshInfo = null
  }

  // Set the repeat index. The index is automatically adjusted to fall within bounds.
  def setIndex(index: Int): Unit = {

    val oldRepeatIndex = getIndex // 1-based

    // Set index
    setIndexInternal(index)
    if (oldRepeatIndex != getIndex) {
      // Dispatch custom event to notify that the repeat index has changed
      Dispatch.dispatchEvent(new XXFormsIndexChangedEvent(this, oldRepeatIndex, getIndex))
    }

    // Handle rebuild flags for container affected by changes to this repeat
    val resolutionScopeContainer = container.findScopeRoot(getPrefixedId)
    resolutionScopeContainer.setDeferredFlagsForSetindex()
  }

  private def setIndexInternal(index: Int): Unit = {
    val local = getLocalForUpdate.asInstanceOf[XFormsRepeatControl.XFormsRepeatControlLocal]
    local.index = ensureIndexBounds(index)
  }

  private def ensureIndexBounds(index: Int) =
    math.min(math.max(index, if (getSize > 0) 1 else 0), getSize)

  // Return the size based on the nodeset size, so we can call this before all iterations have been added.
  // Scenario:
  // - call index() or xxf:index() from within a variable within the iteration:
  // - not all iterations have been added, but the size must be known
  override def getSize =
    Option(bindingContext) map (_.nodeset.size) getOrElse  0

  def getIndex =
    if (isRelevant) {
      val local = getCurrentLocal.asInstanceOf[XFormsRepeatControl.XFormsRepeatControlLocal]
      if (local.index != -1)
        local.index
      else
        throw new OXFException("Repeat index was not set for repeat id: " + effectiveId)
    } else
      0

  // Return the iteration corresponding to the current index if any, null otherwise
  def getIndexIteration =
    if (children.isEmpty || getIndex < 1)
      null
    else
      children(getIndex - 1)

  def doDnD(dndEvent: XXFormsDndEvent): Unit = {

    require(isDnD, s"attempt to process `xxforms-dnd` event on non-DnD-enabled control `$effectiveId`")

    // Get all repeat iteration details
    val dndStart = dndEvent.getDndStart.splitTo[List]("-")
    val dndEnd   = dndEvent.getDndEnd.splitTo[List]("-")

    require(dndStart.size == 1 && dndEnd.size == 1, "DnD over repeat boundaries not supported yet")

    // Find source information
    val sourceItems               = bindingContext.nodeset.asScala
    val sourceItemsSize           = sourceItems.size
    val requestedSourceIndex      = dndStart.last.toInt
    val requestedDestinationIndex = dndEnd.last.toInt

    require(
      requestedSourceIndex >= 1 && requestedSourceIndex <= sourceItemsSize,
      s"Out of range DnD start iteration: $requestedSourceIndex"
    )

    require(
      requestedDestinationIndex >= 1 && requestedDestinationIndex <= sourceItemsSize,
      s"Out of range DnD end iteration: $requestedDestinationIndex"
    )

    val destinationItemsCopy = new ju.ArrayList[Item](sourceItems.asJava)

    require(requestedSourceIndex != requestedDestinationIndex, "`dnd-start` must be different from `dnd-end`")

    val deletedNodeInfo = {
      val deletionDescriptors =
        XFormsDeleteAction.doDelete(
          containingDocument = containingDocument,
          collectionToUpdate = sourceItems,
          deleteIndexOpt     = Some(requestedSourceIndex),
          doDispatch         = false // don't dispatch event because one call to updateRepeatNodeset() is enough
        )
      deletionDescriptors.head.nodeInfo // above deletes exactly one node
    }

    // This removes from our copy of the nodeset, not from the control's nodeset, which must not be
    // touched until control bindings are updated.
    destinationItemsCopy.remove(requestedSourceIndex - 1)

    // Below we still try to use `before` when we can so that handles better the case of a repeat over
    // a hierarchy of nodes where children come after containers.
    val (actualDestinationIndex, destinationBeforeAfter) = {
      if (requestedDestinationIndex < requestedSourceIndex)
        (requestedDestinationIndex, "before")                // insertion point is before or on (degenerate case) deleted node
      else if (requestedDestinationIndex == sourceItemsSize) // must become last element of collection
        (requestedDestinationIndex - 1, "after")
      else
        (requestedDestinationIndex, "before")                // insertion point is after deleted node
    }

    // 3. Insert node into destination
    XFormsInsertAction.doInsert(
      /* containingDocument    = */ containingDocument,
      /* indentedLogger        = */ containingDocument.getControls.getIndentedLogger,
      /* positionAttribute     = */ destinationBeforeAfter,
      /* collectionToBeUpdated = */ destinationItemsCopy,
      /* insertContextNodeInfo = */ null, // `insertContextNodeInfo` doesn't actually matter because `collectionToBeUpdated` is not empty
      /* originItems           = */ List(deletedNodeInfo: Item).asJava,
      /* insertionIndex        = */ actualDestinationIndex,
      /* doClone               = */ false, // do not clone the node as we know the node it is ready for insertion
      /* doDispatch            = */ true,
      /* requireDefaultValues  = */ false
    )

    // TODO: should dispatch xxforms-move instead of xforms-insert?
  }

  def isDnD = {
    val dndAttribute = element.attributeValue(XXFORMS_DND_QNAME)
    dndAttribute != null && dndAttribute != "none"
  }

  // Push binding but ignore non-relevant iterations
  override protected def computeBinding(parentContext: BindingContext) = {
    val contextStack = container.getContextStack
    contextStack.setBinding(parentContext)
    contextStack.pushBinding(element, effectiveId, staticControl.scope)

    // Keep only the relevant items
    import XFormsSingleNodeControl.isRelevantItem

    val items       = contextStack.getCurrentBindingContext.nodeset
    val allRelevant = items.asScala forall isRelevantItem

    if (allRelevant)
      contextStack.getCurrentBindingContext
    else
      contextStack.getCurrentBindingContext.copy(nodeset = items.asScala filter isRelevantItem asJava)
  }

  def updateSequenceForInsertDelete(insertedNodeInfos: Seq[NodeInfo]): Unit = {

    // NOTE: This can be called even if we are not relevant!

    // Don't do any work if our parent is non-relevant because that means we are necessarily not relevant either
    if (! parent.isRelevant)
      return

    // Get old nodeset
    val oldRepeatNodeset = bindingContext.nodeset.asScala

    // Set new binding context on the repeat control
    locally {
      // NOTE: Here we just reevaluate against the parent; maybe we should reevaluate all the way down. See also:
      // https://github.com/orbeon/orbeon-forms/issues/2156
      val contextStack = container.getContextStack
      if (bindingContext.parent eq null)
        // This might happen at the top-level if there is no model and no variables in scope?
        contextStack.resetBindingContext
      else {
        contextStack.setBinding(bindingContext)
        // If there are some preceding variables in scope, the top of the stack is now the last scoped variable
        contextStack.popBinding
      }

      // Do this before evaluating the binding because after that controls are temporarily in an inconsistent
      // state
      containingDocument.getControls.cloneInitialStateIfNeeded()

      evaluateBindingAndValues(
        parentContext = contextStack.getCurrentBindingContext,
        update        = true,
        restoreState  = false,
        state         = None
      )
    }

    // Move things around and create new iterations if needed
    if (! SaxonUtils.compareItemSeqs(oldRepeatNodeset, bindingContext.nodeset.asScala)) {
      // Update iterationsInitialStateIfNeeded()

      val focusedBefore = containingDocument.getControls.getFocusedControl

      val (newIterations, partialFocusRepeatOption) =
        updateIterations(oldRepeatNodeset, insertedNodeInfos, isInsertDelete = true)

      // Evaluate all controls and then dispatches creation events
      val currentControlTree = containingDocument.getControls.getCurrentControlTree
      for (newIteration ← newIterations)
        currentControlTree.initializeSubTree(newIteration, includeCurrent = true)

      // This will dispatch xforms-enabled/xforms-disabled/xxforms-nodeset-changed/xxforms-index-changed events
      // if needed on the repeat control itself (subtrees are handled above).
      containingDocument.getControls.getCurrentControlTree.dispatchRefreshEvents(List(getEffectiveId))

      // Handle focus changes
      Focus.updateFocusWithEvents(focusedBefore, partialFocusRepeatOption)
    }
  }

  /**
   * Update this repeat's iterations given the old and new node-sets, and a list of inserted nodes if any (used for
   * index updates). This returns a list of entirely new repeat iterations added, if any. The repeat's index is
   * adjusted.
   *
   * NOTE: The new binding context must have been set on this control before calling.
   *
   * @param oldRepeatItems        old items
   * @param insertedItems         items just inserted by xf:insert if any, or null
   * @return                      new iterations if any, or an empty list
   */
  def updateIterations(
    oldRepeatItems : Seq[Item],
    insertedItems  : Seq[NodeInfo],
    isInsertDelete : Boolean
  ): (Seq[XFormsRepeatIterationControl], Option[XFormsRepeatControl]) = {

    // NOTE: The following assumes the nodesets have changed

    val controls = containingDocument.getControls

    // Get current (new) nodeset
    val newRepeatNodeset = bindingContext.nodeset.asScala

    val isInsert = insertedItems ne null

    val currentControlTree = controls.getCurrentControlTree

    val oldRepeatIndex = getIndex// 1-based
    var updated = false

    val (newIterations, movedIterationsOldPositions, movedIterationsNewPositions, partialFocusRepeatOption) =
      if (newRepeatNodeset.nonEmpty) {

        // This may be set to this repeat or to a nested repeat if focus was within a removed iteration
        var partialFocusRepeatOption: Option[XFormsRepeatControl] = None

        // For each new node, what its old index was, -1 if it was not there
        val oldIndexes = findNodeIndexes(newRepeatNodeset, oldRepeatItems)

        // For each old node, what its new index is, -1 if it is no longer there
        val newIndexes = findNodeIndexes(oldRepeatItems, newRepeatNodeset)

        // Remove control information for iterations that move or just disappear
        val oldChildren = children

        for (i ← newIndexes.indices) {
          val currentNewIndex = newIndexes(i)
          if (currentNewIndex != i) {
            // Node has moved or is removed
            val isRemoved = currentNewIndex == -1
            val movedOrRemovedIteration = oldChildren(i)
            if (isRemoved) {
              withDebug("removing iteration", Seq("id" → getEffectiveId, "index" → (i + 1).toString)) {

                // If focused control is in removed iteration, remember this repeat and partially remove
                // focus before deindexing the iteration. The idea here is that we don't want to dispatch
                // events to controls that have been removed from the index. So we dispatch all the
                // possible focus out events here.
                if (partialFocusRepeatOption.isEmpty && Focus.isFocusWithinContainer(movedOrRemovedIteration)) {
                  partialFocusRepeatOption = Some(XFormsRepeatControl.this)
                  Focus.removeFocusPartially(containingDocument, boundary = partialFocusRepeatOption)
                }

                // Dispatch destruction events
                currentControlTree.dispatchDestructionEventsForRemovedContainer(movedOrRemovedIteration, includeCurrent = true)

                // Indicate to iteration that it is being removed
                // As of 2012-03-07, only used by XFormsComponentControl to destroy the XBL container
                // This also removes the nested models from XPath dependencies
                movedOrRemovedIteration.iterationRemoved()
              }
            }

            // Deindex old iteration
            currentControlTree.deindexSubtree(movedOrRemovedIteration, includeCurrent = true)
            updated = true
          }
        }

        // Set new repeat index (do this before creating new iterations so that index is available then)
        val didSetIndex =
          if (isInsert) {
            // Insert logic

            // We want to point to a new node (case of insert)

            // First, try to point to the last inserted node if found
            findNodeIndexes(insertedItems, newRepeatNodeset).reverse find (_ != -1) exists { index ⇒
              val newRepeatIndex = index + 1

              debug("setting index to new node", Seq(
                "id"        → getEffectiveId,
                "new index" → newRepeatIndex.toString
              ))

              setIndexInternal(newRepeatIndex)
              true
            }
          } else
            false

        if (! didSetIndex) {
          // Non-insert logic (covers delete and other arbitrary changes to the repeat sequence)

          // Try to point to the same node as before
          if (oldRepeatIndex > 0 && oldRepeatIndex <= newIndexes.length && newIndexes(oldRepeatIndex - 1) != -1) {
            // The index was pointing to a node which is still there, so just move the index
            val newRepeatIndex = newIndexes(oldRepeatIndex - 1) + 1
            if (newRepeatIndex != oldRepeatIndex) {

              debug("adjusting index for existing node", Seq(
                "id"        → getEffectiveId,
                "old index" → oldRepeatIndex.toString,
                "new index" → newRepeatIndex.toString
              ))

              setIndexInternal(newRepeatIndex)
            }
          } else if (oldRepeatIndex > 0 && oldRepeatIndex <= newIndexes.length) {
            // The index was pointing to a node which has been removed
            if (oldRepeatIndex > newRepeatNodeset.size) {
              // "if the repeat index was pointing to one of the deleted repeat items, and if the new size
              // of the collection is smaller than the index, the index is changed to the new size of the
              // collection."

              debug("setting index to the size of the new nodeset", Seq(
                "id"        → getEffectiveId,
                "new index" → newRepeatNodeset.size.toString
              ))

              setIndexInternal(newRepeatNodeset.size)
            } else {
              // "if the new size of the collection is equal to or greater than the index, the index is
              // not changed"
              // NOP
            }
          } else {
            // Old index was out of bounds?
            setIndexInternal(getStartIndex)
            debug("resetting index", Seq("id" → getEffectiveId, "new index" → getIndex.toString))
          }
        }

        // Iterate over new nodeset to move or add iterations
        val newSize = newRepeatNodeset.size
        val newChildren = new ArrayBuffer[XFormsControl](newSize)
        val newIterations = ListBuffer[XFormsRepeatIterationControl]()
        val movedIterationsOldPositions = ListBuffer[Int]()
        val movedIterationsNewPositions = ListBuffer[Int]()

        for (repeatIndex ← 1 to newSize) {
          val currentOldIndex = oldIndexes(repeatIndex - 1)
          if (currentOldIndex == -1) {
            // This new node was not in the old nodeset so create a new one

            // Add new iteration
            newChildren +=
              withDebug("creating new iteration", Seq(
                "id"    → getEffectiveId,
                "index" → repeatIndex.toString
              )) {

                // Create repeat iteration
                val newIteration = controls.createRepeatIterationTree(this, repeatIndex)
                updated = true

                newIterations += newIteration

                newIteration
              }
          } else {
            // This new node was in the old nodeset so keep it

            val existingIteration = oldChildren(currentOldIndex)
            val newIterationOldIndex = existingIteration.iterationIndex

            def updateBindingsIfNeeded(): Unit = {
              // NOTE: We used to only update the binding on the iteration itself
              if (isInsertDelete) {
                val updater = Controls.updateBindings(existingIteration)
                if (partialFocusRepeatOption.isEmpty && updater.partialFocusRepeat.isDefined)
                  partialFocusRepeatOption = updater.partialFocusRepeat
              }
            }

            if (newIterationOldIndex != repeatIndex) {
              // Iteration index changed
              debug("moving iteration", Seq(
                "id"        → getEffectiveId,
                "old index" → newIterationOldIndex.toString,
                "new index" → repeatIndex.toString
              ))

              // Set new index
              existingIteration.setIterationIndex(repeatIndex)

              // Update iteration bindings
              updateBindingsIfNeeded()

              // Index iteration
              currentControlTree.indexSubtree(existingIteration, includeCurrent = true)
              updated = true

              // Add information for moved iterations
              movedIterationsOldPositions += newIterationOldIndex
              movedIterationsNewPositions += repeatIndex
            } else {
              // Iteration index stayed the same

              // Update iteration bindings
              updateBindingsIfNeeded()
            }

            // Add existing iteration
            newChildren += existingIteration
          }
        }

        // Set the new children iterations
        setChildren(newChildren)

        (
          newIterations,
          movedIterationsOldPositions.toList,
          movedIterationsNewPositions.toList,
          partialFocusRepeatOption
        )
      } else {
        // New repeat nodeset is now empty

        // If focused control is in removed iterations, remove focus first
        if (Focus.isFocusWithinContainer(XFormsRepeatControl.this))
          Focus.removeFocus(containingDocument)

        // Remove control information for iterations that disappear
        for (removedIteration ← children) {

          withDebug("removing iteration", Seq(
            "id"    → getEffectiveId,
            "index" → removedIteration.iterationIndex.toString
          )) {
            // Dispatch destruction events and deindex old iteration
            currentControlTree.dispatchDestructionEventsForRemovedContainer(removedIteration, includeCurrent = true)
            currentControlTree.deindexSubtree(removedIteration, includeCurrent = true)
          }

          updated = true
        }

        if (getIndex != 0)
          debug("setting index to 0", Seq("id" → getEffectiveId))

        clearChildren()
        setIndexInternal(0)

        (Nil, Nil, Nil, None)
      }

    // Keep information available until refresh events are dispatched, which must happen soon after this method was
    // called
    this.refreshInfo =
      if (updated || oldRepeatIndex != getIndex)
        RefreshInfo(
          updated,
          if (updated) newIterations else Nil,
          if (updated) movedIterationsOldPositions else Nil,
          if (updated) movedIterationsNewPositions else Nil,
          oldRepeatIndex
        )
      else
        null

    (newIterations, partialFocusRepeatOption)
  }

  override def dispatchChangeEvents() =
    if (refreshInfo ne null) {
      val refreshInfo = this.refreshInfo
      this.refreshInfo = null
      if (refreshInfo.isNodesetChanged) {
        // Dispatch custom event to xf:repeat to notify that the nodeset has changed
        Dispatch.dispatchEvent(new XXFormsNodesetChangedEvent(this, refreshInfo.newIterations,
          refreshInfo.movedIterationsOldPositions, refreshInfo.movedIterationsNewPositions))
      }

      if (refreshInfo.oldRepeatIndex != getIndex) {
        // Dispatch custom event to notify that the repeat index has changed
        Dispatch.dispatchEvent(new XXFormsIndexChangedEvent(this, refreshInfo.oldRepeatIndex, getIndex))
      }
    }

  private def findNodeIndexes(nodeset1: Seq[Item], nodeset2: Seq[Item]) = {

    def indexOfItem(otherItem: Item) =
      nodeset2 indexWhere (SaxonUtils.compareItems(_, otherItem))

    nodeset1 map indexOfItem toArray
  }

  // Serialize index
  override def serializeLocal: ju.Map[String, String] =
    ju.Collections.singletonMap("index", Integer.toString(getIndex))

  // "4.3.7 The xforms-focus Event [...] Setting focus to a repeat container form control sets the focus to the
  // repeat object associated with the repeat index"
  override def focusableControls =
    if (isRelevant && getIndex > 0)
      children(getIndex - 1).focusableControls
    else
      Iterator.empty

  // NOTE: pushBindingImpl ensures that any item we are bound to is relevant
  override def computeRelevant = super.computeRelevant && getSize > 0

  override def performDefaultAction(event: XFormsEvent) = event match {
    case e: XXFormsSetindexEvent ⇒ setIndex(e.index)
    case e: XXFormsDndEvent      ⇒ doDnD(e)
    case _                       ⇒ super.performDefaultAction(event)
  }

  override def buildChildren(
    buildTree : (XBLContainer, BindingContext, ElementAnalysis, Seq[Int]) ⇒ Option[XFormsControl],
    idSuffix  : Seq[Int]
  ): Unit = {

    // Build all children that are not repeat iterations
    Controls.buildChildren(
      this,
      staticControl.children filterNot (_.isInstanceOf[RepeatIterationControl]),
      buildTree,
      idSuffix
    )

    // Build one sub-tree per repeat iteration (iteration itself handles its own binding with pushBinding,
    // depending on its index/suffix)
    val iterationAnalysis = staticControl.iteration.get
    for (iterationIndex ← 1 to bindingContext.nodeset.size)
      buildTree(container, bindingContext, iterationAnalysis, idSuffix :+ iterationIndex)

    // TODO LATER: handle isOptimizeRelevance()
  }
}

object XFormsRepeatControl {

  class XFormsRepeatControlLocal(var index: Int = -1)
    extends ControlLocalSupport.XFormsControlLocal

  case class RefreshInfo(
    isNodesetChanged: Boolean,
    newIterations: Seq[XFormsRepeatIterationControl],
    movedIterationsOldPositions: Seq[Int],
    movedIterationsNewPositions: Seq[Int],
    oldRepeatIndex: Int
  )

  // Find the initial repeat indexes for the given doc
  def initialIndexes(doc: XFormsContainingDocument) =
    findIndexes(
      doc.getControls.getCurrentControlTree,
      doc.getStaticOps.repeats,
      _.initialLocal.asInstanceOf[XFormsRepeatControlLocal].index
    )

  // Find the current repeat indexes for the given doc
  def currentIndexes(doc: XFormsContainingDocument) =
    findIndexes(doc.getControls.getCurrentControlTree, doc.getStaticOps.repeats, _.getIndex)

  // Find the current repeat indexes for the given doc, as a string
  def currentNamespacedIndexesString(doc: XFormsContainingDocument) = {

    val ns = doc.getContainerNamespace

    val repeats =
      for ((repeatId, index) ← currentIndexes(doc))
        yield ns + repeatId + ' ' + index

    repeats mkString ","
  }

  // For the given control, return the matching control that follows repeat indexes
  // This might be the same as the given control if it is within the repeat indexes chain, or another control if not
  def findControlFollowIndexes(control: XFormsControl) = {
    val doc  = control.containingDocument
    val tree = doc.getControls.getCurrentControlTree

    val ancestorRepeatsFromRoot = control.staticControl.ancestorRepeatsAcrossParts.reverse

    // Find just the indexes we need
    val indexes = findIndexes(tree, ancestorRepeatsFromRoot, _.getIndex)

    // Build a suffix based on the ancestor repeats' current indexes
    val suffix = suffixForRepeats(indexes, ancestorRepeatsFromRoot)

    tree.findControl(addSuffix(control.prefixedId, suffix))
  }

  // Return all the controls with the same prefixed id as the control specified
  def findAllRepeatedControls(control: XFormsControl): Iterator[XFormsControl] = {
    val doc  = control.containingDocument
    val tree = doc.getControls.getCurrentControlTree

    val controlPrefixedId = control.prefixedId

    def search(ancestorRepeats: List[RepeatControl], suffix: String): Iterator[String] =
      ancestorRepeats match {
        case Nil          ⇒
          Iterator(addSuffix(controlPrefixedId, suffix))
        case head :: tail ⇒

          val repeatEffectiveId = addSuffix(head.prefixedId, suffix)

          val repeatControl =
            tree.findRepeatControl(repeatEffectiveId) getOrElse
              (throw new IllegalStateException)

          for {
            index ← Iterator.from(1).take(repeatControl.getSize)
            i     ← search(tail, suffix + (if (suffix.isEmpty) "" else REPEAT_INDEX_SEPARATOR) + index)
          } yield
            i
      }

    search(control.staticControl.ancestorRepeatsAcrossParts.reverse, "") flatMap tree.findControl
  }

  // Find indexes for the given repeats in the current document
  private def findIndexes(tree: ControlTree, repeats: Seq[RepeatControl], index: XFormsRepeatControl ⇒ Int) =
    repeats.foldLeft(m.LinkedHashMap[String, Int]()) {
      (indexes, repeat) ⇒

        // Build the suffix based on all the ancestor repeats' indexes
        val suffix = suffixForRepeats(indexes, repeat.ancestorRepeatsAcrossParts.reverse)

        // Build the effective id
        val effectiveId = addSuffix(repeat.prefixedId, suffix)

        // Add the index to the map (0 if the control is not found)
        indexes += (repeat.prefixedId → {
          tree.findRepeatControl(effectiveId) match {
            case Some(control) ⇒ index(control)
            case _             ⇒ 0
          }
        })
    }

  private def suffixForRepeats(indexes: collection.Map[String, Int], repeats: Seq[RepeatControl]) =
    repeats map (repeat ⇒ indexes(repeat.prefixedId)) mkString REPEAT_INDEX_SEPARATOR_STRING

  private def addSuffix(prefixedId: String, suffix: String) =
    prefixedId + (if (suffix.length > 0) REPEAT_SEPARATOR + suffix else "")
}
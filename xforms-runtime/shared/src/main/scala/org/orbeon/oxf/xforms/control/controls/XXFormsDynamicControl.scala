/**
 * Copyright (C) 2011 Orbeon, Inc.
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

import cats.syntax.option._
import org.orbeon.dom._
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.StaticXPath.VirtualNodeType
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.controls.{ComponentControl, LHHA}
import org.orbeon.oxf.xforms.analysis.{ElementAnalysisTreeBuilder, NestedPartAnalysis, PartAnalysis, PartAnalysisBuilder}
import org.orbeon.oxf.xforms.control.Controls._
import org.orbeon.oxf.xforms.control.controls.InstanceMirror._
import org.orbeon.oxf.xforms.control.controls.XXFormsDynamicControl._
import org.orbeon.oxf.xforms.control.{Controls, XFormsComponentControl, XFormsControl, XFormsSingleNodeContainerControl}
import org.orbeon.oxf.xforms.event.Dispatch.EventListener
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xforms.event.events.{XFormsDeleteEvent, XFormsInsertEvent, XXFormsValueChangedEvent}
import org.orbeon.oxf.xforms.model.{NoDefaultsStrategy, XFormsModel}
import org.orbeon.oxf.xforms.state.{ControlState, InstancesControls}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml._
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.saxon.om
import org.orbeon.scaxon.NodeInfoConversions._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.xbl.Scope
import org.w3c.dom.Node.ELEMENT_NODE
import shapeless.syntax.typeable._

import scala.collection.generic.Growable
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * xxf:dynamic control
 *
 * This control must bind to an XHTML+XForms document held in an instance. The document is processed as an XForms
 * sub-document and handle as a shadow tree of xxf:dynamic. Changes taking place in the document are dynamically
 * reported into the shadow tree.
 *
 * The following changes are handled specially:
 *
 * - changes to inline instance content on both sides are directly mirrored
 * - changes to content nested within top-level bound nodes cause re-evaluation of the binding only
 * - changes to nested binds cause incremental add/remove of binds
 *
 * All other changes cause the entire sub-document to be reprocessed.
 *
 * In the future the hope is to make any change fully incremental.
 */
class XXFormsDynamicControl(container: XBLContainer, parent: XFormsControl, element: Element, effectiveId: String)
  extends XFormsSingleNodeContainerControl(container, parent, element, effectiveId) {

  case class Nested(container: XBLContainer, partAnalysis: NestedPartAnalysis, template: SAXStore, outerListener: EventListener)

  private var _nested: Option[Nested] = None
  def nested: Option[Nested] = _nested

  private var fullUpdateChange = false
  private val xblChanges       = mutable.Buffer[(String, Element)]()
  private val bindChanges      = mutable.Buffer[(String, Element)]()

  // New scripts created during an update (not functional as of 2018-05-03)
  // NOTE: This should instead be accumulated at the level of the request.
  private var _newScripts: List[ShareableScript] = Nil
  def newScripts: List[ShareableScript] = _newScripts
  def clearNewScripts(): Unit = _newScripts = Nil

  // TODO: This might blow if the control is non-relevant
  override def bindingContextForChildOpt: Option[BindingContext] =
    _nested map { case Nested(container, _, _, _) =>
      val contextStack = container.getContextStack
      contextStack.setParentBindingContext(bindingContext)
      contextStack.resetBindingContext()
      contextStack.getCurrentBindingContext
    }

  override def onCreate(restoreState: Boolean, state: Option[ControlState], update: Boolean): Unit = {
    super.onCreate(restoreState, state, update)
    getBoundElement match {
      case Some(boundElem) =>
        updateSubTree(create = true, boundElem)
      case _ =>
        // Don't create binding (maybe we could for a read-only instance)
        _nested = None
    }
  }

  override def onDestroy(update: Boolean): Unit = {
    // TODO: XXX remove child container from parent
    _nested = None
    fullUpdateChange = false
  }

  override def onBindingUpdate(oldBinding: BindingContext, newBinding: BindingContext): Unit = {

    if (! SaxonUtils.compareItemSeqs(oldBinding.nodeset.asScala, newBinding.nodeset.asScala)) {
      fullUpdateChange = true
      containingDocument.addControlStructuralChange(prefixedId)
    }

    getBoundElement foreach { boundElem =>
      updateSubTree(create = false, boundElem)
    }
  }

  private def updateSubTree(create: Boolean, boundElem: VirtualNodeType): Unit =
    if (create || fullUpdateChange) {
      // Document has changed and needs to be fully recreated
      processFullUpdate(create, boundElem)
    } else {
      // Changes to nested binds
      if (bindChanges.nonEmpty)
        processBindsUpdates()

      // Changes to top-level XBL bindings
      if (xblChanges.nonEmpty)
        processXBLUpdates()
    }

  private def processFullUpdate(create: Boolean, boundElem: VirtualNodeType): Unit = {
    fullUpdateChange = false
    xblChanges.clear()
    bindChanges.clear()

    if (create && ! containingDocument.initializing)
      containingDocument.addControlStructuralChange(prefixedId)

    // Outer instance
    val outerInstance =
      containingDocument.instanceForNodeOpt(boundElem) getOrElse (throw new IllegalArgumentException)

    // Gather relevant switch state before removing children
    val relevantSwitchState = if (create) Map.empty[String, ControlState] else gatherRelevantSwitchState(this)
    // LATER: Could also restore everything including models, but need to think about implications. It's
    // conservative to attempt to restore only switch state, but restoring models should not take place for every
    // full update.
    //val dynamicState = DynamicState(this)

    // Remove children controls if any
    val tree = containingDocument.controls.getCurrentControlTree
    if (getSize > 0) {
      // PERF: dispatching destruction events takes a lot of time, what can we do besides not dispatching them?
      //tree.dispatchDestructionEventsForRemovedContainer(this, false)
      tree.deindexSubtree(this, includeCurrent = false)
      clearChildren()
    }

    _nested foreach { case Nested(container, partAnalysis, _, outerListener) =>
      // Remove container and associated models
      container.destroy()
      // Remove part and associated scopes
      containingDocument.staticOps.removePart(partAnalysis)
      // Remove listeners we added to the outer instance (better do this or we will badly leak)
      // WARNING: Make sure outerListener is the exact same object passed to addListener. There can be a
      // conversion from a function to a listener, in which case identity won't be preserved!
      InstanceMirror.removeListener(outerInstance, outerListener)
    }

    // Create new part
    val (template, partAnalysis) =
      createPartAnalysis(
        org.orbeon.dom.Document(unsafeUnwrapElement(boundElem).createCopy),
        part
      )

    // Save new scripts if any
//            val newScriptCount = containingDocument.getStaticState.getScripts.size
//            if (newScriptCount > scriptCount)
//                newScripts = containingDocument.getStaticState.getScripts.values.slice(scriptCount, newScriptCount).toSeq
//            also addControlStructuralChange() if new scripts? or other update mechanism?

    // Nested container is initialized after binding and before control tree
    val childContainer = container.createChildContainer(this, partAnalysis)

    childContainer.addAllModels()
    childContainer.initializeModels(
      List(
        XFORMS_MODEL_CONSTRUCT,
        XFORMS_MODEL_CONSTRUCT_DONE
      )
    )

    // Add listener to the single outer instance
    val newListenerWithCycleDetector = new ListenerCycleDetector

    // NOTE: Make sure to convert to an EventListener so that addListener/removeListener deal with the exact same object
    val outerListener: EventListener = {

      // Mark an unknown change, which will require a complete rebuild of the part
      val unknownChange: MirrorEventListener = { _ =>
        fullUpdateChange = true
        containingDocument.addControlStructuralChange(prefixedId)
        ListenerResult.Stop
      }

      def recordChanges(findChange: om.NodeInfo => Option[(String, Element)], changes: Growable[(String, Element)])(nodes: Seq[om.NodeInfo]): ListenerResult = {
        val newChanges = nodes flatMap (findChange(_))
        changes ++= newChanges
        if (newChanges.nonEmpty) ListenerResult.Stop else ListenerResult.NextListener
      }

      def changeListener(record: Seq[om.NodeInfo] => ListenerResult): MirrorEventListener = {
        case insert: XFormsInsertEvent              => record(insert.insertedNodes collect { case n: om.NodeInfo => n })
        case delete: XFormsDeleteEvent              => record(delete.deletedNodes)
        case valueChanged: XXFormsValueChangedEvent => record(List(valueChanged.node))
        case _                                      => ListenerResult.NextListener
      }

      // Instance mirror listener
      val instanceListener =
        newListenerWithCycleDetector(
          toInnerInstanceNode(boundElem, childContainer, findOuterInstanceDetailsDynamic)
        )

      // Compose listeners
      toEventListener(composeListeners(
        List(
          instanceListener,
          changeListener(recordChanges(findXBLChange(partAnalysis, _), xblChanges)),
          changeListener(recordChanges(findBindChange,                 bindChanges)),
          unknownChange
        )
      ))
    }

    InstanceMirror.addListener(outerInstance, outerListener)

    // Add mutation listeners to all top-level inline instances, which upon value change propagate the value
    // change to the related node in the source
    val innerListener = toEventListener(
      newListenerWithCycleDetector(
        toOuterInstanceNodeDynamic(outerInstance, boundElem)
      )
    )

    partAnalysis.getModelsForScope(partAnalysis.startScope) foreach {
      _.instances.values filter (_.useInlineContent) foreach { instance =>
        val innerInstance = childContainer.findInstance(instance.staticId).get
        InstanceMirror.addListener(innerInstance, innerListener)
      }
    }

    // Remember all that we created
    _nested = Some(Nested(childContainer, partAnalysis, template, outerListener))

    // Create new control subtree, attempting to restore switch state
    // LATER: See above comments
    //
    // Cases:
    //
    // 1. XFCD deserialization     : use full InstanceControls state
    // 2. xxf:dynamic full update  : use only switch state
    // 3. Initial controls creation: don't restore any state
    val stateToRestore =
      Controls.restoringInstanceControls orElse ((! create) option InstancesControls(Nil, relevantSwitchState))

    def createAndInitializeDynamicSubTree(): Unit =
      tree.createAndInitializeDynamicSubTree(
        container        = childContainer,
        containerControl = this,
        elementAnalysis  = partAnalysis.getTopLevelControls.head,
        state            = Controls.restoringControls
      )

    stateToRestore match {
      case Some(state) => withDynamicStateToRestore(state)(createAndInitializeDynamicSubTree())
      case None        => createAndInitializeDynamicSubTree()
    }
  }

  // We want to remember the state of switches
  private def gatherRelevantSwitchState(start: XFormsControl) =
    ControlsIterator(start, includeSelf = false) collect
      { case switch: XFormsSwitchControl if switch.isRelevant => switch.getEffectiveId -> switch.controlState.get } toMap

  // If more than one change touches a given id, processed it once using the last element
  private def groupChanges(changes: Iterable[(String, Element)]): List[(String, Element)] =
    changes.toList groupByKeepOrder (_._1) map { case (prefixedId, prefixedIdsToElemsInSource) =>
      prefixedId -> (prefixedIdsToElemsInSource map (_._2) last)
    }

  private def processXBLUpdates(): Unit = {

    val partAnalysis = _nested.get.partAnalysis

    val tree = containingDocument.controls.getCurrentControlTree

    for ((prefixedId, elemInSource) <- groupChanges(xblChanges)) {
      tree.findControl(prefixedId) match { // TODO: should use effective id if in repeat and process all
        case Some(componentControl: XFormsComponentControl) =>
          // Update and restore switch state
          // LATER: See above comments
          // withDynamicStateToRestore(DynamicState(componentControl).decodeInstancesControls) {
          withDynamicStateToRestore(InstancesControls(Nil, gatherRelevantSwitchState(componentControl))) {
            removeDynamicShadowTree(componentControl)
            createOrUpdateStaticShadowTree(partAnalysis, componentControl, elemInSource.some)
            componentControl.recreateNestedContainer()
            updateDynamicShadowTree(componentControl)
          }
        case _ =>
      }
    }

    xblChanges.clear()
  }

  private def processBindsUpdates(): Unit = {

    val partAnalysis = _nested.get.partAnalysis

    for ((modelId, modelElement) <- groupChanges(bindChanges)) {

      val modelPrefixedId = partAnalysis.startScope.prefixedIdForStaticId(modelId)
      val staticModel = partAnalysis.getModel(modelPrefixedId)

      PartAnalysisBuilder.rebuildBindTree(partAnalysis, staticModel, modelElement)

      // Q: When should we best notify the concrete model that its binds need build? Since at this point, we
      // are within a bindings update, it would be nice if the binds are rebuilt before nested controls are
      // rebuilt below. However, it might not be safe to RRR right here. So for now, we just set the flag.
      val concreteModel = containingDocument.getObjectByEffectiveId(modelPrefixedId).asInstanceOf[XFormsModel]

      concreteModel.markStructuralChange(None, NoDefaultsStrategy)
    }

    bindChanges.clear()
   }

  private def getBoundElement: Option[VirtualNodeType] =
    bindingContext.singleNodeOpt match {
      case Some(node: VirtualNodeType) if node.getNodeKind == ELEMENT_NODE => Some(node)
      case _                                                               => None
    }

  private def createPartAnalysis(doc: Document, parent: PartAnalysis) = {
    val newScope = new Scope(Some(getResolutionScope ensuring (_ ne null)), getPrefixedId)
    val (template, newPart) = PartAnalysisBuilder.createPart(containingDocument.staticState, parent, doc, newScope)
    containingDocument.staticOps.addPart(newPart)

    (template, newPart)
  }

  override def getBackCopy: XXFormsDynamicControl = {
    val cloned = super.getBackCopy.asInstanceOf[XXFormsDynamicControl]
    cloned.fullUpdateChange = false // unused
    cloned._nested = None

    cloned
  }

  // For now we don't need to output anything particular
  // LATER: what about new scripts?
  final override def outputAjaxDiff(
    previousControlOpt : Option[XFormsControl],
    content            : Option[XMLReceiverHelper => Unit])(implicit
    ch                 : XMLReceiverHelper
  ): Unit = ()

  // Only if we had a structural change
  override def supportFullAjaxUpdates: Boolean = containingDocument.getControlsStructuralChanges.contains(prefixedId)
}

object XXFormsDynamicControl {

  // Compose a Seq of listeners by calling them in order until one has successfully processed the event
  def composeListeners(listeners: Seq[MirrorEventListener]): MirrorEventListener =
    e =>
      listeners find (_(e) == ListenerResult.Stop) match {
        case Some(_) => ListenerResult.Stop
        case None    => ListenerResult.NextListener
      }

  // Find whether the given node is a bind element or attribute and return the associated model id -> element mapping
  def findBindChange(node: om.NodeInfo): Option[(String, Element)] = {

    val XF = XFORMS_NAMESPACE_URI

    // Any change to a model bind element or a descendant element or attribute
    val modelOption =
      node ancestorOrSelf (XF -> "bind") ancestor (XF -> "model") headOption

    modelOption map { modelNode =>
      val modelElement = unsafeUnwrapElement(modelNode)
      modelElement.idOrNull -> modelElement
    }
  }

  // Find whether a change occurred in a descendant of a top-level XBL binding
  def findXBLChange(partAnalysis: PartAnalysis, node: om.NodeInfo): Option[(String, Element)] = {

    if (node.getNodeKind == SaxonUtils.NamespaceType)
      None // can't find ancestors of namespace nodes with Orbeon DOM
    else {

      val isNodeLHHA =
        node.isElement && LHHA.isLHHA(unsafeUnwrapElement(node))

      // Go from root to leaf
      val ancestorsFromRoot = node ancestor * reverse

      // Find first element whose prefixed id has a binding and return the mapping prefixedId -> element
      val all =
        for {
          ancestor         <- ancestorsFromRoot
          id               = ancestor.id
          if id.nonEmpty
          prefixedId       = partAnalysis.startScope.prefixedIdForStaticId(id)
          control          <- partAnalysis.findControlAnalysis(prefixedId)
          componentControl <- control.narrowTo[ComponentControl]
          if componentControl.hasConcreteBinding && ! (isNodeLHHA && ancestor == node.getParent)
        } yield
          prefixedId -> unsafeUnwrapElement(ancestor)

      all.headOption
    }
  }

  private def removeDynamicShadowTree(componentControl: XFormsComponentControl): Unit = {

    val doc = componentControl.containingDocument

    // Remove concrete models and controls
    // PERF: dispatching destruction events takes a lot of time, what can we do besides not dispatching them?
    // Also: check whether dispatchDestructionEventsForRemovedContainer dispatches to already non-relevant controls
    //tree.dispatchDestructionEventsForRemovedContainer(componentControl, false)
    componentControl.destroyNestedContainer()

    // Remove dynamic controls
    doc.controls.getCurrentControlTree.deindexSubtree(componentControl, includeCurrent = false)
    componentControl.clearChildren()
  }

  def updateDynamicShadowTree(componentControl: XFormsComponentControl): Unit = {

    val doc             = componentControl.containingDocument
    val templateTreeOpt = componentControl.staticControl.children find (_.element.getQName == XBL_TEMPLATE_QNAME)

    templateTreeOpt foreach { templateTree =>
      doc.controls.getCurrentControlTree.createAndInitializeDynamicSubTree(
        container        = componentControl.nestedContainerOpt getOrElse (throw new IllegalStateException),
        containerControl = componentControl,
        elementAnalysis  = templateTree,
        state            = Controls.restoringControls
      )
    }

    doc.addControlStructuralChange(componentControl.prefixedId)
  }

  def createOrUpdateStaticShadowTree(
    partAnalysis     : NestedPartAnalysis,
    componentControl : XFormsComponentControl,
    elemInSource     : Option[Element])(implicit
    logger           : IndentedLogger
  ): Unit = {

    val doc             = componentControl.containingDocument
    val staticComponent = componentControl.staticControl

    // Update the shadow tree
    // Can return `None` if the binding does not have a template.
    ElementAnalysisTreeBuilder.createOrUpdateStaticShadowTree(partAnalysis, staticComponent, elemInSource)

    doc.addControlStructuralChange(componentControl.prefixedId)
  }
}

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

import org.orbeon.dom.Element
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.FunctionContext
import org.orbeon.oxf.util.StaticXPath.VirtualNodeType
import org.orbeon.oxf.xforms.BindingContext
import org.orbeon.oxf.xforms.analysis.controls.*
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, ElementAnalysisTreeBuilder, NestedPartAnalysis}
import org.orbeon.oxf.xforms.control.ControlAjaxSupport.{outputAriaDiff, outputPlaceholderDiff}
import org.orbeon.oxf.xforms.control.controls.InstanceMirror.*
import org.orbeon.oxf.xforms.control.controls.{InstanceMirror, XXFormsComponentRootControl, XXFormsDynamicControl}
import org.orbeon.oxf.xforms.event.Dispatch.EventListener
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.events.{XFormsModelConstructDoneEvent, XFormsModelConstructEvent, XFormsReadyEvent}
import org.orbeon.oxf.xforms.event.{Dispatch, EventCollector, XFormsEvent, XFormsEvents}
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.model.{DefaultsStrategy, XFormsInstance, XFormsInstanceSupport}
import org.orbeon.oxf.xforms.state.{ContainersState, ControlState, InstanceState, InstancesControls}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.{SaxonUtils, XMLReceiver, XMLReceiverHelper}
import org.orbeon.saxon.om
import org.orbeon.scaxon.Implicits.stringToStringValue
import org.orbeon.scaxon.NodeInfoConversions.unsafeUnwrapElement
import org.orbeon.xforms.XFormsId
import org.orbeon.xml.NamespaceMapping
import org.w3c.dom.Node.ELEMENT_NODE
import shapeless.syntax.typeable.*

import scala.jdk.CollectionConverters.*


// A component control with native support for a value
class XFormsValueComponentControl(
  container   : XBLContainer,
  parent      : XFormsControl,
  element     : Element,
  _effectiveId: String
) extends XFormsComponentControl(
  container,
  parent,
  element,
  _effectiveId
) with XFormsValueControl {

  // TODO: Fix hierarchy! We don't necessarily have `StaticLHHASupport`, in particular.
  // 2024-04-30: `staticControl` without `StaticLHHASupport` can be passed, but we claim it has `StaticLHHASupport`.
  // Concrete value controls always have LHHA support, while static value controls don't.
  override type Control <: ComponentControl with ValueComponentTrait with ViewTrait with StaticLHHASupport

  // Don't expose an external value unless explicitly allowed
  override def handleExternalValue: Boolean = staticControl.commonBinding.modeExternalValue

  private def evaluateWithContext(eval: (NamespaceMapping, FunctionContext) => Option[String], collector: ErrorEventCollector): Option[String] =
    for {
      _                    <- staticControl.bindingOpt
      nestedContainer      <- nestedContainerOpt
      nestedBindingContext <- bindingContextForChildOpt(collector)
      result               <- eval(
                               staticControl.commonBinding.bindingElemNamespaceMapping,
                               XFormsFunction.Context(
                                 container         = nestedContainer,
                                 bindingContext    = nestedBindingContext,
                                 sourceEffectiveId = innerRootControl.effectiveId,
                                 modelOpt          = nestedBindingContext.modelOpt,
                                 bindNodeOpt       = None
                               )
                             )
    } yield
      result

  // See https://github.com/orbeon/orbeon-forms/issues/4041
  override def getFormattedValue(collector: ErrorEventCollector): Option[String] = {

    // TODO: Unclear when we return `None` (no binding, etc.). Right now we flatten.
    def fromBinding =
      staticControl.format flatMap { formatExpr =>
        evaluateWithContext(
          (namespaceMapping, functionContext) =>
            valueWithSpecifiedFormat(
              format           = formatExpr,
              collector        = collector,
              namespaceMapping = namespaceMapping,
              functionContext  = functionContext
            ),
          collector
        )
      }

    def fromExternal =
      Option(getExternalValue(collector))

    fromBinding orElse valueWithDefaultFormat(collector) orElse fromExternal
  }

  override def evaluateExternalValue(collector: ErrorEventCollector): Unit = {

    def fromBinding =
      staticControl.commonBinding.serializeExternalValueOpt flatMap { serializeExpr =>
        evaluateWithContext(
          (namespaceMapping, functionContext) =>
            evaluateAsString(
              xpathString        = serializeExpr,
              contextItems       = List(stringToStringValue(getValue(collector))),
              contextPosition    = 1,
              collector          = collector,
              contextMessage     = "evaluating external value",
              namespaceMapping   = namespaceMapping,
              variableToValueMap = bindingContext.getInScopeVariables,
              functionContext    = functionContext
            ),
          collector
        )
      }

    setExternalValue(fromBinding getOrElse getValue(collector))
  }

  override def translateExternalValue(
    boundItem    : om.Item,
    externalValue: String,
    collector    : ErrorEventCollector
  ): Option[String] = {

    def fromBinding =
      staticControl.commonBinding.deserializeExternalValueOpt flatMap { deserializeExpr =>
        evaluateWithContext(
          (namespaceMapping, functionContext) =>
            evaluateAsString(
              xpathString        = deserializeExpr,
              contextItems       = List(externalValue),
              contextPosition    = 1,
              collector          = collector,
              contextMessage     = "translating external value",
              namespaceMapping   = namespaceMapping,
              variableToValueMap = bindingContext.getInScopeVariables,
              functionContext    = functionContext
            ),
          collector
        )
      }

    fromBinding orElse super.translateExternalValue(boundItem, externalValue, collector)
  }

  override def outputAjaxDiffUseClientValue(
    previousValue   : Option[String],
    previousControl : Option[XFormsValueControl],
    content         : Option[XMLReceiverHelper => Unit],
    collector       : ErrorEventCollector
  )(implicit
    ch              : XMLReceiverHelper
  ): Unit = {
    // NOTE: Don't output any nested content. The value is handled separately, see:
    // https://github.com/orbeon/orbeon-forms/issues/3909
    super.outputAjaxDiff(
      previousControl,
      None,
      collector
    )

    // https://github.com/orbeon/orbeon-forms/issues/6279
    // When the component uses `xxf:label-for`, find the target control if any, and output updates for that control's
    // `aria-required` and `aria-invalid` attributes.
    for {
      staticLhhaSupport     <- staticControl.cast[StaticLHHASupport]
      staticRc              <- staticLhhaSupport.referencedControl
      concreteRcEffectiveId = XFormsId.buildEffectiveId(staticRc.prefixedId, XFormsId.getEffectiveIdSuffixParts(this.effectiveId))
      concreteRc            <- containingDocument.findControlByEffectiveId(concreteRcEffectiveId)
    } locally {

      implicit val receiver: XMLReceiver = ch.getXmlReceiver

      outputAriaDiff(previousControl, this, concreteRc.effectiveId)

      // https://github.com/orbeon/orbeon-forms/issues/6304
      staticRc.cast[GroupControl].foreach { staticRg =>
        val containingElementName = staticRg.elementQNameOrDefault.localName
        if (containingElementName == "input" || containingElementName == "textarea")
          outputPlaceholderDiff(previousControl, this, concreteRc.effectiveId)
      }
    }
  }
}

// A component control with or without a value
class XFormsComponentControl(
  container   : XBLContainer,
  parent      : XFormsControl,
  element     : Element,
  _effectiveId: String
) extends XFormsSingleNodeContainerControl(container, parent, element, _effectiveId) {

  override type Control <: ComponentControl

  private var _nestedContainerOpt: Option[XBLContainer] = None
  def nestedContainerOpt: Option[XBLContainer] = _nestedContainerOpt

  // If the component handles LHHA itself, they are considered under of the nested container
  override def lhhaContainer: XBLContainer = nestedContainerOpt.get // TODO: What if the container is missing due to a lazy binding?

  private var _listeners: Seq[(XFormsInstance, EventListener)] = Nil

  // Create nested container upon object instantiation
  // NOTE: 2018-02-23: We wanted to create this upon `onCreate` and destroy it upon `onDestroy`, but there are
  // too many assumptions that the container is available even if the control is non-relevant for this to work
  // easily. Might want to revisit later. However, if we have a lazy `ConcreteBinding`, the container will still
  // not be created immediately because the container needs the inner scope, which is known only when the
  // `ConcreteBinding` is available.
  // See also https://github.com/orbeon/orbeon-forms/issues/4018
  createNestedContainer()

  // React to external events only if we are relevant OR upon receiving xforms-disabled
  def canRunEventHandlers(event: XFormsEvent): Boolean =
    isRelevant || event.name == "xforms-disabled"

  private def createNestedContainer(): Option[XBLContainer] = {

    assert(_nestedContainerOpt.isEmpty)

    _nestedContainerOpt =
      staticControl.bindingOpt map { concreteBinding =>
        container.createChildContainer(this, concreteBinding) |!> (_.addAllModels())
      }

    _nestedContainerOpt
  }

  // Destroy container and models if any
  def destroyNestedContainer(): Unit =
    _nestedContainerOpt foreach { nestedContainer =>
      destroyMirrorListenerIfNeeded()
      nestedContainer.destroy()
      _nestedContainerOpt = None
    }

  // For xxf:dynamic updates of top-level XBL shadow trees
  def recreateNestedContainer(): Option[XBLContainer] =
    createNestedContainer() |!> { _ =>
      if (isRelevant)
        initializeModels()
    }

  private def modeBinding = staticControl.commonBinding.modeBinding

  // Only handle binding if we support modeBinding
  override protected def computeBinding(parentContext: BindingContext, collector: ErrorEventCollector): BindingContext =
    if (modeBinding)
      super.computeBinding(parentContext, collector)
    else
      super.computeBindingCopy(parentContext)

  override def bindingContextForChildOpt(collector: ErrorEventCollector): Option[BindingContext] =
    _nestedContainerOpt.map { nestedContainer =>
      // Start with inner context
      // For nested event handlers, this still works, because the nested handler can never match the inner scope. So
      // the context goes inner context -> component binding.
      if (isRelevant) {
        val contextStack = nestedContainer.contextStack
        contextStack.setParentBindingContext(bindingContext)
        contextStack.resetBindingContext(collector)
        contextStack.getCurrentBindingContext
      } else {
        BindingContext.empty(staticControl.element, staticControl.scope)
      }
    }

  override def onCreate(
    restoreState: Boolean,
    state       : Option[ControlState],
    update      : Boolean,
    collector   : ErrorEventCollector
  ): Unit = {

    super.onCreate(restoreState, state, update, collector)

    if (staticControl.hasLazyBinding && ! staticControl.hasConcreteBinding) {

      // We can only have a lazy binding in a nested part, but this is not expressed by types at this time
      val nestedPartAnalysis = container.partAnalysis.cast[NestedPartAnalysis].getOrElse(throw new IllegalStateException)

      // Only update the static tree. The dynamic tree is created later.
      // This is only possible right now in a nested part, not the top-level part.
      XXFormsDynamicControl.createOrUpdateStaticShadowTree(nestedPartAnalysis, this, None)
      assert(staticControl.hasConcreteBinding)

      // See https://github.com/orbeon/orbeon-forms/issues/4018
      //
      // The nested `XBLContainer` needs to be created if the lazy static binding got just created. The reason is that upon control
      // construction, if the static binding was missing, the nested `XBLContainer` was not created either.
      recreateNestedContainer()

      if (update)
        XXFormsDynamicControl.updateDynamicShadowTree(this, collector)
    }

    val nestedContainer = nestedContainerOpt.getOrElse(throw new IllegalStateException)

    // Restore only containers marked as needed restoration:
    // https://github.com/orbeon/orbeon-forms/issues/5856

    Controls.restoringInstanceControls match {
      case Some(InstancesControls(ContainersState.All, instances, _)) =>
        // Case of a full state restoration of the `DynamicState`
        restoreModels(instances)
      case Some(InstancesControls(ContainersState.Some(containers), instances, _)) if containers(nestedContainer.effectiveId) =>
        // Case of an `xxf:dynamic` state restoration where only some containers must be restored
        restoreModels(instances)
      case _ =>
        initializeModels()
    }

    addEnabledListener()
  }

  // Attach a mirror listener if needed
  // Return the reference node if a listener was created
  private def createMirrorListener(mirrorInstance: XFormsInstance, referenceNode: VirtualNodeType): Option[VirtualNodeType] =
    _nestedContainerOpt map { nestedContainer =>

      val outerDocument = referenceNode.getRoot
      val outerInstance = containingDocument.instanceForNodeOpt(outerDocument).orNull // TODO: `Option`

      val newListenerWithCycleDetector = new ListenerCycleDetector

      val outerListener = toEventListener(
        newListenerWithCycleDetector(
          toInnerInstanceNode(
            referenceNode,
            nestedContainer,
            findOuterInstanceDetailsXBL(mirrorInstance, referenceNode)
          )
        )
      )

      val innerListener = toEventListener(
        newListenerWithCycleDetector(
          toOuterInstanceNodeXBL(outerInstance, referenceNode)
        )
      )

      // Set outer and inner listeners

      _listeners = List(outerInstance -> outerListener, mirrorInstance -> innerListener)

      _listeners foreach { case (instance, listener) =>
        InstanceMirror.addListener(instance, listener)
      }

      referenceNode
    }

  private def destroyMirrorListenerIfNeeded(): Unit = {
    _listeners foreach { case (instance, listener) =>
      InstanceMirror.removeListener(instance, listener)
    }

    _listeners = Nil
  }

  override def onDestroy(update: Boolean): Unit = {

    // NOTE: We do not *remove* the nested `XBLContainer` (see comments above). However, we should still destroy the container's
    // models. We should look into that!

    removeEnabledListener()
    destroyMirrorListenerIfNeeded()

    super.onDestroy(update)

    if (staticControl.hasLazyBinding && staticControl.hasConcreteBinding) {

      if (update) {
        containingDocument.controls.getCurrentControlTree.deindexSubtree(this, includeCurrent = false)
        clearChildren()
        destroyNestedContainer()
      }

      // We can only have a lazy binding in a nested part, but this is not expressed by types at this time
      val nestedPartAnalysis = container.partAnalysis.cast[NestedPartAnalysis].getOrElse(throw new IllegalStateException)

      ElementAnalysisTreeBuilder.clearShadowTree(nestedPartAnalysis, staticControl)
      containingDocument.addControlStructuralChange(prefixedId)
    }
  }

  private def initializeModels(): Unit =
    nestedContainerOpt foreach { nestedContainer =>

      // `xforms-model-construct` without RRR
      for (model <- nestedContainer.models) {
        Dispatch.dispatchEvent(new XFormsModelConstructEvent(model, rrr = false), EventCollector.ToReview)
        // 2023-04-05: Not sure below comment is up to date. Can's see a call to `markStructuralChange()`,
        // NOTE: `xforms-model-construct` already does a `markStructuralChange()` but without `DefaultsStrategy.All`
        model.markStructuralChange(None, DefaultsStrategy.All)
      }

      initializeMirrorListenerIfNeeded(dispatch = false)

      // Do RRR as xforms-model-construct didn't do it
      for (model <- nestedContainer.models)
        model.doRebuildRecalculateRevalidateIfNeeded()

      // `xforms-model-construct-done`
      for (model <- nestedContainer.models)
        Dispatch.dispatchEvent(new XFormsModelConstructDoneEvent(model), EventCollector.ToReview)
    }

  private def restoreModels(instances: List[InstanceState]): Unit =
    nestedContainerOpt foreach { nestedContainer =>
      nestedContainer.restoreModelsState(instances, deferRRR = true)
      initializeMirrorListenerIfNeeded(dispatch = false)
      // Do RRR as isRestoringDynamicState() didn't do it
      for (model <- nestedContainer.models) {
        model.doRebuildRecalculateRevalidateIfNeeded()
      }
    }

  private def initializeMirrorListenerIfNeeded(dispatch: Boolean): Option[XFormsInstance] = {

    // NOTE: Must be called after xforms-model-construct so that instances are present
    def findMirrorInstance: Option[XFormsInstance] =
      nestedContainerOpt
        .iterator
        .flatMap(_.models.iterator)
        .flatMap(_.instancesIterator)
        .find(_.instance.mirror)

    // Process mirror instance if any
    findMirrorInstance map { mirrorInstance =>

      // Reference node must be a wrapped element
      // Also support case where there is no binding, and in which case use the binding context. This is done
      // because Form Builder doesn't place a ref or bind on section template components as of 2013-01-17.
      val referenceNode = (if (modeBinding) boundItemOpt else contextForBinding) collect {
        case node: VirtualNodeType if node.getNodeKind == ELEMENT_NODE => node
      }

      // Create new doc rooted at reference node
      val doc =
        XFormsInstanceSupport.extractDocument(
          element               = unsafeUnwrapElement(referenceNode.get),
          excludeResultPrefixes = Set.empty,
          readonly              = false,
          exposeXPathTypes      = mirrorInstance.exposeXPathTypes,
          removeInstanceData    = true
        )

      // Update initial instance
      mirrorInstance.replace(doc, EventCollector.ToReview, dispatch)

      // Create the listeners
      createMirrorListener(mirrorInstance, referenceNode.get)

      mirrorInstance
    }
  }

  private var _enabledListener: Option[Dispatch.EventListener] = None

  private def addEnabledListener(): Unit = {

    assert(_enabledListener.isEmpty)

    // Logic: when the component control receives the `xforms-enabled` event (which occurs during refresh after
    // the nested models have already been initialized) we dispatch `xforms-ready` to the nested models. This is
    // for consistency with the top-level `xforms-ready` which occurs when the control tree has been initialized.
    // We could have considered using another event, as the name `xforms-ready` does not fully reflect the
    // meaning associated with the top-level. On the other hand, this makes it easier to translate a top-level
    // model into a nested model.
    val newListener: Dispatch.EventListener =
      _ => {

        // Remove during first run
        removeEnabledListener()

        for {
          container <- _nestedContainerOpt.iterator
          model     <- container.models
        } locally {
          Dispatch.dispatchEvent(new XFormsReadyEvent(model), EventCollector.ToReview)
        }
      }

    _enabledListener = Some(newListener)

    addListener(XFormsEvents.XFORMS_ENABLED, newListener)
  }

  private def removeEnabledListener(): Unit =
    _enabledListener foreach { _ =>
      removeListener(XFormsEvents.XFORMS_ENABLED, _enabledListener)
      _enabledListener = None
    }

  override def onBindingUpdate(
    oldBinding: BindingContext,
    newBinding: BindingContext,
    collector : ErrorEventCollector
  ): Unit = {
    super.onBindingUpdate(oldBinding, newBinding, collector)
    val isNodesetChange = ! SaxonUtils.compareItemSeqs(oldBinding.nodeset.asScala, newBinding.nodeset.asScala)
    if (isNodesetChange) {
      destroyMirrorListenerIfNeeded()
      initializeMirrorListenerIfNeeded(dispatch = true) foreach { mirrorInstance =>
        // If the instance was updated, it is due for an RRR, but nobody will check that before the refresh is done, so do it here.
        mirrorInstance.model.doRebuildRecalculateRevalidateIfNeeded()
      }
    }
  }

  // This is called iif the iteration index changes
  override def updateEffectiveId(): Unit = {

    // Update rest of control tree
    super.updateEffectiveId()

    // Update container with new effective id
    nestedContainerOpt foreach (_.updateEffectiveId(effectiveId))
  }

  override def iterationRemoved(): Unit = {
    // Inform descendants
    super.iterationRemoved()

    // Destroy container and models if any
    destroyNestedContainer()
  }

  // Simply delegate but switch the container
  override def buildChildren(
    buildTree: (XBLContainer, BindingContext, ElementAnalysis, collection.Seq[Int], ErrorEventCollector) => Option[XFormsControl],
    idSuffix : collection.Seq[Int],
    collector: ErrorEventCollector
  ): Unit =
    if (staticControl.hasConcreteBinding)
      Controls.buildChildren(
        control   = this,
        children  = staticControl.children,
        buildTree = (_, bindingContext, staticElement, idSuffix, collector) =>
          buildTree(nestedContainerOpt getOrElse (throw new IllegalStateException), bindingContext, staticElement, idSuffix, collector),
        idSuffix  = idSuffix,
        collector = collector
      )

  // Get the control at the root of the inner scope of the component
  def innerRootControl: XXFormsComponentRootControl =
    children
      .collectFirst { case root: XXFormsComponentRootControl => root }
      .get

  override def ajaxLhhaSupport: Seq[LHHA] = staticControl.commonBinding.standardLhhaAsSeq
  override def htmlLhhaSupport: Set[LHHA] = staticControl.commonBinding.standardLhhaAsSet
}
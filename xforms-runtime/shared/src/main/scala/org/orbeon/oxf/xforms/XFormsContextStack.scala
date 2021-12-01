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
package org.orbeon.oxf.xforms

import java.util
import java.util.Collections

import cats.syntax.option._
import org.orbeon.datatypes.LocationData
import org.orbeon.dom.Element
import org.orbeon.oxf.common.{OXFException, OrbeonLocationException, ValidationException}
import org.orbeon.oxf.util.StaticXPath.ValueRepresentationType
import org.orbeon.oxf.util.{FunctionContext, StaticXPath, XPathCache}
import org.orbeon.oxf.xforms.analysis.controls.VariableAnalysisTrait
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.model.{RuntimeBind, XFormsModel}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.oxf.xml.dom.XmlExtendedLocationData
import org.orbeon.saxon.om
import org.orbeon.xforms.XFormsNames
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping

import scala.util.control.NonFatal


/**
 * Handle a stack of XPath evaluation context information. This is used by controls (with one stack rooted at each
 * XBLContainer), models, and actions.
 *
 * TODO: This has to go, and instead we will just use `BindingContext`.
 */
object XFormsContextStack {

  // If there is no XPath context defined at the root (in the case there is no default XForms model/instance
  // available), we should use an empty context. However, currently for non-relevance in particular we must not run
  // expressions with an empty context. To allow running expressions at the root of a container without models, we
  // create instead a context with an empty document node instead. This way there is a context for evaluation. In the
  // future, we should allow running expressions with no context, possibly after statically checking that they do not
  // depend on the context, as well as prevent evaluations within non-relevant content by other means.
  //    final List<Item> DEFAULT_CONTEXT = XFormsConstants.EMPTY_ITEM_LIST;
  private val DefaultContext = Collections.singletonList(StaticXPath.EmptyDocument: om.Item)

  /**
   * Return an empty context for the given model.
   */
  def defaultContext(
    parentBindingContext : BindingContext,
    container            : XBLContainer,
    modelOpt             : Option[XFormsModel]
  ): BindingContext =
    new BindingContext(
      parent               = parentBindingContext,
      modelOpt             = modelOpt,
      bind                 = null,
      nodeset              = DefaultContext,
      position             = DefaultContext.size,
      elementId            = null,
      newBind              = true,
      controlElement       = null,
      _locationData        = modelOpt map (_.getLocationData) orNull,
      hasOverriddenContext = false,
      contextItem          = null,
      scope                = container.innerScope
    )
}

class XFormsContextStack {

  private val keepLocationData = XFormsGlobalProperties.isKeepLocation

  var container: XBLContainer = null
  private var containingDocument: XFormsContainingDocument = null

  private var parentBindingContext: BindingContext = null
  private var head: BindingContext = null

  // Constructor for `XFormsModel` and `XBLContainer`
  def this(container: XBLContainer) = {
    this()
    this.container = container
    this.containingDocument = container.getContainingDocument
  }

  // Constructor for `XFormsModelAction` and `XFormsActionInterpreter`
  def this(container: XBLContainer, parentBindingContext: BindingContext) = {
    this()
    this.container = container
    this.containingDocument = this.container.getContainingDocument
    this.parentBindingContext = parentBindingContext
    // Push a copy of the parent binding
    this.head = pushCopy(parentBindingContext)
  }

  // Push a copy of the current binding
  def pushCopy(): BindingContext =
    pushCopy(this.head)

  private def pushCopy(parent: BindingContext): BindingContext = {
    this.head =
      new BindingContext(
        parent               = parent,
        modelOpt             = parent.modelOpt,
        bind                 = parent.bind,
        nodeset              = parent.nodeset,
        position             = parent.position,
        elementId            = parent.elementId,
        newBind              = false,
        controlElement       = parent.controlElement,
        _locationData        = parent.locationData,
        hasOverriddenContext = false,
        contextItem          = parent.contextItem,
        scope                = parent.scope
      )
    this.head
  }

  // For XBL/xxf:dynamic
  def setParentBindingContext(parentBindingContext: BindingContext): Unit =
    this.parentBindingContext = parentBindingContext

  def getFunctionContext(sourceEffectiveId: String): XFormsFunction.Context =
    getFunctionContext(sourceEffectiveId, this.head)

  def getFunctionContext(sourceEffectiveId: String, data: Any): XFormsFunction.Context =
    getFunctionContext(sourceEffectiveId, this.head, data)

  def getFunctionContext(sourceEffectiveId: String, binding: BindingContext): XFormsFunction.Context =
    XFormsFunction.Context(container, binding, sourceEffectiveId, binding.modelOpt, null)

  def getFunctionContext(sourceEffectiveId: String, binding: BindingContext, data: Any): XFormsFunction.Context =
    XFormsFunction.Context(container, binding, sourceEffectiveId, binding.modelOpt, data)

  /**
   * Reset the binding context to the root of the first model's first instance, or to the parent binding context.
   */
  def resetBindingContext(): BindingContext = {
    resetBindingContext(container.defaultModel)
    this.head
  }

  /**
   * Reset the binding context to the root of the given model's first instance.
   */
  def resetBindingContext(modelOpt: Option[XFormsModel]): Unit = {

    val defaultInstanceOpt = modelOpt flatMap (_.defaultInstanceOpt)

    (modelOpt, defaultInstanceOpt) match {
      case (Some(model), Some(defaultInstance)) =>
        // Push the default context if there is a model with an instance
        val defaultNode = defaultInstance.rootElement
        val defaultNodeset = Collections.singletonList(defaultNode: om.Item)
        this.head =
          new BindingContext(
            parent               = parentBindingContext,
            modelOpt             = Option(model),
            bind                 = null,
            nodeset              = defaultNodeset,
            position             = 1,
            elementId            = null,
            newBind              = true,
            controlElement       = null,
            _locationData        = defaultInstance.getLocationData,
            hasOverriddenContext = false,
            contextItem          = defaultNode,
            scope                = container.innerScope
          )
      case _ =>
        // Push empty context
        this.head = XFormsContextStack.defaultContext(parentBindingContext, container, modelOpt)
    }

    // Add model variables for default model
    modelOpt foreach { model =>
      model.setTopLevelVariables(evaluateModelVariables(model))
    }
  }

  // NOTE: This only scopes top-level model variables, but not binds-as-variables.
  private def evaluateModelVariables(model: XFormsModel): Map[String, ValueRepresentationType] = {
    // TODO: Check dirty flag to prevent needless re-evaluation
    // All variables in the model are in scope for the nested binds and actions.
    val variables = model.staticModel.variablesSeq
    if (variables.nonEmpty) {

      val variableInfos =
        variables map { variable =>
          variable.name -> scopeVariable(variable, model.getEffectiveId, handleNonFatal = true).value
        } toMap

      val indentedLogger = containingDocument.getIndentedLogger(XFormsModel.LoggingCategory)
      if (indentedLogger.debugEnabled)
        indentedLogger.logDebug("", "evaluated model variables", "count", variableInfos.size.toString)

      for (_ <- 0 until variableInfos.size)
        popBinding()

      variableInfos
    } else
      Map.empty
  }

  def scopeVariable(
    staticVariable    : VariableAnalysisTrait,
    sourceEffectiveId : String,
    handleNonFatal    : Boolean
  ): VariableNameValue = {

    // Create variable object
    val variable = new Variable(staticVariable, containingDocument)

    // Find variable scope
    val newScope = staticVariable.scope

    // Push the variable on the context stack. Note that we do as if each variable was a "parent" of the
    // following controls and variables.
    // NOTE: The value is computed immediately. We should use Expression objects and do lazy evaluation
    // in the future.
    // NOTE: We used to simply add variables to the current bindingContext, but this could cause issues
    // because getVariableValue() can itself use variables declared previously. This would work at first,
    // but because BindingContext caches variables in scope, after a first request for in-scope variables,
    // further variables values could not be added. The method below temporarily adds more elements on the
    // stack but it is safer.
    getFunctionContext(sourceEffectiveId)
    this.head =
      this.head.pushVariable(
        staticVariable,
        variable.staticVariable.name,
        variable.valueEvaluateIfNeeded(
          contextStack      = this,
          sourceEffectiveId = sourceEffectiveId,
          pushOuterContext  = true,
          handleNonFatal    = handleNonFatal
        ),
        newScope
      )

    this.head.variable.get
  }

  def setBinding(bindingContext: BindingContext): BindingContext = {
    this.head = bindingContext
    this.head
  }

  /**
   * Push an element containing either single-node or nodeset binding attributes.
   *
   * @param bindingElement    current element containing node binding attributes
   * @param sourceEffectiveId effective id of source control for id resolution of models and binds
   * @param scope             XBL scope
   */
  // TODO: Could all callers use the `ElementAnalysis`?
  def pushBinding(bindingElement: Element, sourceEffectiveId: String, scope: Scope): Unit =
    pushBinding(bindingElement, sourceEffectiveId, scope, handleNonFatal = true)

  // NOTE: actions pass handleNonFatal = "false", other callers pass handleNonFatal = "true".
  // TODO: move away from element and use static analysis information
  def pushBinding(bindingElement: Element, sourceEffectiveId: String, scope: Scope, handleNonFatal: Boolean): Unit =
    pushBinding(
      ref                            = bindingElement.attributeValue(XFormsNames.REF_QNAME),
      context                        = bindingElement.attributeValue(XFormsNames.CONTEXT_QNAME),
      nodeset                        = bindingElement.attributeValue(XFormsNames.NODESET_QNAME),
      modelId                        = bindingElement.attributeValue(XFormsNames.MODEL_QNAME),
      bindId                         = bindingElement.attributeValue(XFormsNames.BIND_QNAME),
      bindingElement                 = bindingElement,
      bindingElementNamespaceMapping = container.partAnalysis.getNamespaceMapping(scope, bindingElement.attributeValue(XFormsNames.ID_QNAME)),
      sourceEffectiveId              = sourceEffectiveId,
      scope                          = scope,
      handleNonFatal                 = handleNonFatal
    )

  private def getBindingContext(scope: Scope): BindingContext = {
    var bindingContext = this.head
    // Don't use `ne` as the scope object can be recreated within components that have lazy content!
    while (bindingContext.scope != scope) {
      bindingContext = bindingContext.parent
      // There must be a matching scope down the line
      assert(bindingContext ne null)
    }
    bindingContext
  }

  def pushBinding(
    ref                            : String,
    context                        : String,
    nodeset                        : String,
    modelId                        : String,
    bindId                         : String,
    bindingElement                 : Element,
    bindingElementNamespaceMapping : NamespaceMapping,
    sourceEffectiveId              : String,
    scope                          : Scope,
    handleNonFatal                 : Boolean
  ): Unit = {

    assert(scope != null)

    val locationData =
      if (keepLocationData && bindingElement != null)
        XmlExtendedLocationData(
          bindingElement.getData.asInstanceOf[LocationData],
          "pushing XForms control binding".some,
          element = bindingElement.some
        )
      else
        null

    try {
      // Handle scope
      // The new binding evaluates against a base binding context which must be in the same scope
      val baseBindingContext = getBindingContext(scope)
      // Handle model
      var newModelOpt: Option[XFormsModel] = null
      var isNewModel = false
      if (modelId != null) {
        val resolutionScopeContainer = container.findScopeRoot(scope)
        resolutionScopeContainer.resolveObjectById(sourceEffectiveId, modelId, Option(null)) match {
          case model: XFormsModel =>
            newModelOpt = model.some
            // Don't say it's a new model unless it has really changed
            isNewModel =
              baseBindingContext.modelOpt.isEmpty && newModelOpt.nonEmpty   ||
                baseBindingContext.modelOpt.nonEmpty && newModelOpt.isEmpty ||
                (baseBindingContext.modelOpt.nonEmpty && newModelOpt.nonEmpty && (baseBindingContext.modelOpt.get ne newModelOpt.get))
          case _ =>
            // Invalid model id
            // NOTE: We used to dispatch `xforms-binding-exception`, but we want to be able to recover
            if (! handleNonFatal)
              throw new ValidationException("Reference to non-existing model id: " + modelId, locationData)
            // Default to not changing the model
            newModelOpt = baseBindingContext.modelOpt
            isNewModel = false
        }
      } else {
        newModelOpt = baseBindingContext.modelOpt
        isNewModel = false
      }

      // Handle nodeset
      var isNewBind = false
      var bind: RuntimeBind = null
      var newPosition = 0
      var newNodeset: util.List[om.Item] = null
      var hasOverriddenContext = false
      var contextItem: om.Item = null
      if (bindId != null) {
        // Resolve the bind id to a nodeset
        // NOTE: For now, only the top-level models in a resolution scope are considered
        val resolutionScopeContainer = container.findScopeRoot(scope)
        resolutionScopeContainer.resolveObjectById(sourceEffectiveId, bindId, baseBindingContext.singleItemOpt) match {
          case runtimeBind: RuntimeBind =>
            bind = runtimeBind
            newNodeset = bind.items
            contextItem = baseBindingContext.getSingleItemOrNull
            newPosition = Math.min(newNodeset.size, 1)
          case null if resolutionScopeContainer.containsBind(bindId) =>
            // The bind attribute was valid for this scope, but no runtime object was found for the bind
            // This can happen e.g. if a nested bind is within a bind with an empty nodeset
            bind = null
            newNodeset = java.util.Collections.emptyList[om.Item]
            contextItem = null
            newPosition = 0
          case _ =>
            // The bind attribute did not resolve to a bind
            if (! handleNonFatal)
              throw new ValidationException(s"Reference to non-existing bind id: `$bindId`", locationData)
            // Default to an empty binding
            bind = null
            newNodeset = java.util.Collections.emptyList[om.Item]
            contextItem = null
            newPosition = 0
        }
        hasOverriddenContext = false
        isNewBind = true
      } else if (ref != null || nodeset != null) {
        bind = null
        var evaluationContextBinding: BindingContext = null
        if (context != null) {
          // Push model and context
          pushTemporaryContext(
            parent      = this.head,
            base        = baseBindingContext,
            contextItem = baseBindingContext.getSingleItemOrNull // provide context information for the `context()` function
          )
          pushBinding(
            ref                            = null,
            context                        = null,
            nodeset                        = context,
            modelId                        = modelId,
            bindId                         = null,
            bindingElement                 = null,
            bindingElementNamespaceMapping = bindingElementNamespaceMapping,
            sourceEffectiveId              = sourceEffectiveId,
            scope                          = scope,
            handleNonFatal                 = handleNonFatal
          )
          hasOverriddenContext = true
          val newBindingContext = this.head
          contextItem = newBindingContext.getSingleItemOrNull
          evaluationContextBinding = newBindingContext
        } else if (isNewModel) {
          // Push model only
          pushBinding(
            ref                            = null,
            context                        = null,
            nodeset                        = null,
            modelId                        = modelId,
            bindId                         = null,
            bindingElement                 = null,
            bindingElementNamespaceMapping = bindingElementNamespaceMapping,
            sourceEffectiveId              = sourceEffectiveId,
            scope                          = scope,
            handleNonFatal                 = handleNonFatal
          )
          hasOverriddenContext = false
          val newBindingContext = this.head
          contextItem = newBindingContext.getSingleItemOrNull
          evaluationContextBinding = newBindingContext
        } else {
          hasOverriddenContext = false
          contextItem = baseBindingContext.getSingleItemOrNull
          evaluationContextBinding = baseBindingContext
        }
        //                    if (false) {
        //                        // NOTE: This is an attempt at allowing evaluating a binding even if no context is present.
        //                        // But this doesn't work properly. E.g.:
        //                        //
        //                        // <xf:group ref="()">
        //                        //   <xf:input ref="."/>
        //                        // Above must end up with an empty binding for xf:input, while:
        //                        //   <xf:input ref="instance('foobar')"/>
        //                        // Above must end up with a non-empty binding IF it was to be evaluated.
        //                        // Now the second condition above should not happen anyway, because the content of the group
        //                        // is non-relevant anyway. But we do have cases now where this happens, so we can't enable
        //                        // the code below naively.
        //                        // We could enable it if we knew statically that the expression did not depend on the
        //                        // context though, but right now we don't.
        //
        //                        final boolean isDefaultContext;
        //                        final List<Item> evaluationNodeset;
        //                        final int evaluationPosition;
        //                        if (evaluationContextBinding.getNodeset().size() > 0) {
        //                            isDefaultContext = false;
        //                            evaluationNodeset = evaluationContextBinding.getNodeset();
        //                            evaluationPosition = evaluationContextBinding.getPosition();
        //                        } else {
        //                            isDefaultContext = true;
        //                            evaluationNodeset = DEFAULT_CONTEXT;
        //                            evaluationPosition = 1;
        //                        }
        //                        if (!isDefaultContext) {
        //                            // Provide context information for the context() function
        //                            pushTemporaryContext(this.head, evaluationContextBinding, evaluationContextBinding.getSingleItem());
        //                        // Use updated binding context to set model
        //                        functionContext.setModel(evaluationContextBinding.model());
        //                        List<Item> result;
        //                            try {
        //                                result = XPathCache.evaluateKeepItems(evaluationNodeset, evaluationPosition,
        //                                        ref != null ? ref : nodeset, bindingElementNamespaceMapping, evaluationContextBinding.getInScopeVariables(), XFormsContainingDocument.getFunctionLibrary(),
        //                                        functionContext, null, locationData, containingDocument.getRequestStats().getReporter());
        //                            } catch (Exception e) {
        //                                if (handleNonFatal) {
        //                                    XFormsError.handleNonFatalXPathError(container, e);
        //                                    result = XFormsConstants.EMPTY_ITEM_LIST;
        //                                } else {
        //                                    throw e;
        //                                }
        //                            }
        //                        newNodeset = result;
        //                            popBinding();
        //                    } else {
        if (! evaluationContextBinding.nodeset.isEmpty) {
          // Evaluate new XPath in context if the current context is not empty
          // TODO: in the future, we should allow null context for expressions that do not depend on the context
          // NOTE: We prevent evaluation if the context was empty. However there are cases where this
          // should be allowed, if the expression does not depend on the context. Ideally, we would know
          // statically whether an expression depends on the context or not, and take separate action if
          // that's the case. Currently, such an expression will produce an XPathException.
          // It might be the case that when we implement non-evaluation of relevant subtrees, this won't
          // be an issue anymore, and we can simply allow evaluation of such expressions. Otherwise,
          // static analysis of expressions might provide enough information to handle the two situations.
          val functionContext =
            getFunctionContext(
              sourceEffectiveId,
              updateBindingWithContextItem(this.head, evaluationContextBinding, evaluationContextBinding.getSingleItemOrNull)
            )

          val expression = if (ref != null) ref else nodeset
          val result =
            try
              XPathCache.evaluateKeepItemsJava(
                evaluationContextBinding.nodeset,
                evaluationContextBinding.position,
                expression,
                bindingElementNamespaceMapping,
                evaluationContextBinding.getInScopeVariables,
                containingDocument.functionLibrary,
                functionContext,
                null,
                locationData,
                containingDocument.getRequestStats.getReporter
              )
            catch {
              case e: Exception =>
                if (handleNonFatal) {
                  XFormsError.handleNonFatalXPathError(container, e, Some(expression))
                  java.util.Collections.emptyList[om.Item]
                } else
                  throw e
            }
          newNodeset = result
        } else {
          // Otherwise we consider we can't evaluate
          newNodeset = java.util.Collections.emptyList[om.Item]
        }

        // Restore optional context
        if (context != null || isNewModel) {
          popBinding()
          if (context != null)
            popBinding()
        }
        isNewBind = true
        newPosition = 1
      } else if (isNewModel && context == null) {
        // Only the model has changed
        bind = null
        val modelBindingContextOpt = this.head.currentBindingContextForModel(newModelOpt)
        if (modelBindingContextOpt.isDefined) {
          val modelBindingContext = modelBindingContextOpt.get
          newNodeset = modelBindingContext.nodeset
          newPosition = modelBindingContext.position
        }
        else {
          newNodeset = this.head.currentNodeset(newModelOpt)
          newPosition = 1
        }
        hasOverriddenContext = false
        contextItem = baseBindingContext.getSingleItemOrNull
        isNewBind = false
      } else if (context != null) {
        bind = null
        // Only the context has changed, and possibly the model
        pushBinding(
          ref                            = null,
          context                        = null,
          nodeset                        = context,
          modelId                        = modelId,
          bindId                         = null,
          bindingElement                 = null,
          bindingElementNamespaceMapping = bindingElementNamespaceMapping,
          sourceEffectiveId              = sourceEffectiveId,
          scope                          = scope,
          handleNonFatal                 = handleNonFatal
        )
        newNodeset = this.head.nodeset
        newPosition = this.head.position
        isNewBind = false
        hasOverriddenContext = true
        contextItem = this.head.getSingleItemOrNull
        popBinding()
      } else {
        // No change to anything
        bind = null
        isNewBind = false
        newNodeset = baseBindingContext.nodeset
        newPosition = baseBindingContext.position
        // We set a new context item as the context into which other attributes must be evaluated. E.g.:
        // <xf:select1 ref="type">
        //   <xf:action ev:event="xforms-value-changed" if="context() = 'foobar'">
        // In this case, you expect context() to be updated as follows.
        hasOverriddenContext = false
        contextItem = baseBindingContext.getSingleItemOrNull
      }
      // Push new context
      val bindingElementId =
        if (bindingElement == null)
          null
        else
          bindingElement.idOrNull

      this.head =
        new BindingContext(
          parent               = this.head,
          modelOpt             = newModelOpt,
          bind                 = bind,
          nodeset              = newNodeset,
          position             = newPosition,
          elementId            = bindingElementId,
          newBind              = isNewBind,
          controlElement       = bindingElement,
          _locationData        = locationData,
          hasOverriddenContext = hasOverriddenContext,
          contextItem          = contextItem,
          scope                = scope
        )
    } catch {
      case NonFatal(t) =>
        if (bindingElement != null)
          throw OrbeonLocationException.wrapException(
            t,
            XmlExtendedLocationData(
              locationData,
              "evaluating binding expression".some,
              element = bindingElement.some
            )
          )
        else
          throw OrbeonLocationException.wrapException(
            t,
            XmlExtendedLocationData(
              locationData, "" + "evaluating binding expression",
              bindingElement,
              Array("ref", ref, "context", context, "nodeset", nodeset, "modelId", modelId, "bindId", bindId)
            )
          )
    }
  }

  private def pushTemporaryContext(parent: BindingContext, base: BindingContext, contextItem: om.Item): Unit =
    this.head = updateBindingWithContextItem(parent, base, contextItem)

  private def updateBindingWithContextItem(parent: BindingContext, base: BindingContext, contextItem: om.Item) =
    new BindingContext(
      parent               = parent,
      modelOpt             = base.modelOpt,
      bind                 = null,
      nodeset              = base.nodeset,
      position             = base.position,
      elementId            = base.elementId,
      newBind              = false,
      controlElement       = base.controlElement,
      _locationData        = base.locationData,
      hasOverriddenContext = false,
      contextItem          = contextItem,
      scope                = base.scope
    )

  /**
   * Push an iteration of the current node-set. Used for example by xf:repeat, xf:bind, iterate.
   *
   * @param currentPosition 1-based iteration index
   */
  def pushIteration(currentPosition: Int): BindingContext = {
    val currentBindingContext = this.head
    val currentNodeset = currentBindingContext.nodeset
    // Set a new context item, although the context() function is never called on the iteration itself
    var newContextItem: om.Item = null
    if (currentNodeset.size == 0)
      newContextItem = null
    else
      newContextItem = currentNodeset.get(currentPosition - 1)

    this.head =
      new BindingContext(
        parent               = currentBindingContext,
        modelOpt             = currentBindingContext.modelOpt,
        bind                 = null,
        nodeset              = currentNodeset,
        position             = currentPosition,
        elementId            = currentBindingContext.elementId,
        newBind              = true,
        controlElement       = null,
        _locationData        = currentBindingContext.locationData,
        hasOverriddenContext = false,
        contextItem          = newContextItem,
        scope                = currentBindingContext.scope
      )
    this.head
  }

  def getCurrentBindingContext: BindingContext = head

  def popBinding(): BindingContext = {
    if (this.head.parent == null)
      throw new OXFException("Attempt to clear context stack.")
    val popped = this.head
    this.head = this.head.parent
    popped
  }
}
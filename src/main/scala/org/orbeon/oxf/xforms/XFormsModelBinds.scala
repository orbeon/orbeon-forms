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
package org.orbeon.oxf.xforms

import org.apache.commons.validator.routines.{EmailValidator, RegexValidator}
import org.orbeon.dom.QName
import org.orbeon.dom.saxon.TypedNodeWrapper
import org.orbeon.errorified.Exceptions
import org.orbeon.oxf.common.{OrbeonLocationException, ValidationException}
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.Whitespace._
import org.orbeon.oxf.util.XPath.Reporter
import org.orbeon.oxf.util.{IndentedLogger, Logging, XPath}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsModelBinds._
import org.orbeon.oxf.xforms.analysis.model.Model._
import org.orbeon.oxf.xforms.analysis.model.ValidationLevel.ErrorLevel
import org.orbeon.oxf.xforms.analysis.model.{DependencyAnalyzer, Model, StaticBind}
import org.orbeon.oxf.xforms.event.events.XXFormsXPathErrorEvent
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent}
import org.orbeon.oxf.xforms.model._
import org.orbeon.oxf.xml.dom4j.{Dom4jUtils, ExtendedLocationData}
import org.orbeon.oxf.xml.{NamespaceMapping, XMLConstants, XMLParsing}
import org.orbeon.saxon.`type`.{BuiltInAtomicType, BuiltInType, ValidationFailure}
import org.orbeon.saxon.expr.XPathContextMajor
import org.orbeon.saxon.om.{Item, NodeInfo, StandardNames}
import org.orbeon.saxon.sxpath.{IndependentContext, XPathEvaluator}
import org.orbeon.saxon.value.{AtomicValue, BooleanValue, QNameValue, StringValue}
import org.orbeon.scaxon.XML
import org.w3c.dom.Node

import scala.collection.JavaConverters._
import scala.collection.{mutable ⇒ m}
import scala.language.postfixOps
import scala.util.control.NonFatal

class XFormsModelBinds(protected val model: XFormsModel)
  extends RebuildBindOps
     with ValidationBindOps
     with CalculateBindOps {

  protected val containingDocument = model.containingDocument
  protected val dependencies       = containingDocument.getXPathDependencies
  protected val staticModel        = model.getStaticModel

  protected implicit def logger = model.getIndentedLogger
  protected implicit def reporter: XPath.Reporter = containingDocument.getRequestStats.addXPathStat

  // Support for `xxf:evaluate-bind-property` function
  def evaluateBindByType(bind: RuntimeBind, position: Int, mipType: QName): Option[AtomicValue] = {

    val bindNode = bind.getOrCreateBindNode(position)

    // We don't want to dispatch events while we are performing the actual recalculate/revalidate operation,
    // so we collect them here and dispatch them altogether once everything is done.
    val eventsToDispatch = m.ListBuffer[XFormsEvent]()
    def collector(event: XFormsEvent): Unit =
      eventsToDispatch += event

    def makeQNameValue(qName: QName) =
      new QNameValue(qName.getNamespacePrefix, qName.getNamespaceURI, qName.getName, null)

    def hasSuccessfulErrorConstraints =
      bind.staticBind.constraintsByLevel.nonEmpty option {
        bindNode.staticBind.constraintsByLevel.get(ErrorLevel).to[List] flatMap { mips ⇒
          failedConstraintMIPs(mips, bindNode, collector)
        } isEmpty
      }

    // NOTE: This only evaluates the first custom MIP of the given name associated with the bind. We do store multiple
    // ones statically, but don't have yet a solution to combine them. Should we string-join them?
    def evaluateCustomMIPByName(mipType: QName) =
      evaluateCustomMIP(
        bindNode,
        bindNode.staticBind.customMIPNameToXPathMIP(buildCustomMIPName(mipType.getQualifiedName)).head,
        collector
      )

    val result =
      mipType match {
        case TYPE_QNAME            ⇒ bind.staticBind.dataType                                            map makeQNameValue
        case RELEVANT_QNAME        ⇒ evaluateBooleanMIP(bindNode, Relevant, DEFAULT_RELEVANT, collector) map BooleanValue.get
        case READONLY_QNAME        ⇒ evaluateBooleanMIP(bindNode, Readonly, DEFAULT_READONLY, collector) map BooleanValue.get
        case REQUIRED_QNAME        ⇒ evaluateBooleanMIP(bindNode, Required, DEFAULT_REQUIRED, collector) map BooleanValue.get
        case CONSTRAINT_QNAME      ⇒ hasSuccessfulErrorConstraints                                       map BooleanValue.get
        case CALCULATE_QNAME       ⇒ evaluateCalculatedBind(bindNode, Calculate, collector)              map StringValue.makeStringValue
        case XXFORMS_DEFAULT_QNAME ⇒ evaluateCalculatedBind(bindNode, Default, collector)                map StringValue.makeStringValue
        case mipType               ⇒ evaluateCustomMIPByName(mipType)                                    map StringValue.makeStringValue
      }

    // Dispatch all events
    for (event ← eventsToDispatch)
      Dispatch.dispatchEvent(event)

    result
  }
}

trait RebuildBindOps {

  self: XFormsModelBinds ⇒

  private var _topLevelBinds: List[RuntimeBind] = Nil
  final def topLevelBinds = _topLevelBinds

  final val singleNodeContextBinds   = m.HashMap[String, RuntimeBind]()
  final val iterationsForContextItem = m.HashMap[Item, List[BindIteration]]()

  // Whether this is the first rebuild for the associated XForms model
  private var isFirstRebuild = model.containingDocument.isInitializing

  // Rebuild all binds, computing all bind nodesets (but not computing the MIPs)
  def rebuild(): Unit =
    withDebug("performing rebuild", List("model id" → model.getEffectiveId)) {

      // NOTE: Assume that model.getContextStack().resetBindingContext(model) was called

      // Clear all instances that might have InstanceData
      // Only need to do this after the first rebuild
      if (! isFirstRebuild)
        for (instance ← model.getInstances.asScala) {
          // Only clear instances that are impacted by xf:bind/(@ref|@nodeset), assuming we were able to
          // figure out the dependencies.
          // The reason is that clearing this state can take quite some time
          val instanceMightBeSchemaValidated =
            model.hasSchema && instance.isSchemaValidation

          val instanceMightHaveMips =
            dependencies.hasAnyCalculationBind(staticModel, instance.getPrefixedId) ||
            dependencies.hasAnyValidationBind(staticModel, instance.getPrefixedId)

          if (instanceMightBeSchemaValidated || instanceMightHaveMips)
            DataModel.visitElement(instance.rootElement, InstanceData.clearStateForRebuild)
        }

      // Not ideal, but this state is updated when the bind tree is updated below
      singleNodeContextBinds.clear()
      iterationsForContextItem.clear()

      // Iterate through all top-level bind elements to create new bind tree
      // TODO: In the future, XPath dependencies must allow for partial rebuild of the tree as is the case with controls
      // Even before that, the bind tree could be modified more dynamically as is the case with controls
      _topLevelBinds =
        for (staticBind ← staticModel.topLevelBinds)
          yield new RuntimeBind(model, staticBind, null, isSingleNodeContext = true)

      isFirstRebuild = false
    }

  // Implement "4.7.2 References to Elements within a bind Element":
  //
  // "When a source object expresses a Single Node Binding or Node Set Binding with a bind attribute, the IDREF of the
  // bind attribute is resolved to a target bind object whose associated nodeset is used by the Single Node Binding or
  // Node Set Binding. However, if the target bind element has one or more bind element ancestors, then the identified
  // bind may be a target element that is associated with more than one target bind object.
  //
  // If a target bind element is outermost, or if all of its ancestor bind elements have nodeset attributes that
  // select only one node, then the target bind only has one associated bind object, so this is the desired target
  // bind object whose nodeset is used in the Single Node Binding or Node Set Binding. Otherwise, the in-scope
  // evaluation context node of the source object containing the bind attribute is used to help select the appropriate
  // target bind object from among those associated with the target bind element.
  //
  // From among the bind objects associated with the target bind element, if there exists a bind object created with
  // the same in-scope evaluation context node as the source object, then that bind object is the desired target bind
  // object. Otherwise, the IDREF resolution produced a null search result."
  def resolveBind(bindId: String, contextItem: Item): RuntimeBind =
    singleNodeContextBinds.get(bindId) match {
      case Some(singleNodeContextBind) ⇒
        // This bind has a single-node context (incl. top-level bind), so ignore context item and just return
        // the bind nodeset
        singleNodeContextBind
      case None ⇒
        // Nested bind: use context item

        // "From among the bind objects associated with the target bind element, if there exists a bind object
        // created with the same in-scope evaluation context node as the source object, then that bind object is
        // the desired target bind object. Otherwise, the IDREF resolution produced a null search result."
        val it =
          for {
            iterations ← iterationsForContextItem.get(contextItem).iterator
            iteration  ← iterations
            childBind  ← iteration.findChildBindByStaticId(bindId)
          } yield
            childBind

        it.nextOption().orNull
    }
}

trait CalculateBindOps {

  self: XFormsModelBinds ⇒

  // Whether this is the first recalculate for the associated XForms model
  private var isFirstCalculate = model.containingDocument.isInitializing

  // Apply calculate binds
  def applyDefaultAndCalculateBinds(defaultsStrategy: DefaultsStrategy, collector: XFormsEvent ⇒ Unit): Unit = {
    if (! staticModel.mustRecalculate) {
        debug("skipping bind recalculate", List("model id" → model.getEffectiveId, "reason" → "no recalculation binds"))
    } else {
      withDebug("performing bind recalculate", List("model id" → model.getEffectiveId)) {

        if (staticModel.hasNonPreserveWhitespace)
          applyWhitespaceBinds(collector)

        if (staticModel.hasDefaultValueBind)
          (if (isFirstCalculate) AllDefaultsStrategy else defaultsStrategy) match {
            case strategy: SomeDefaultsStrategy ⇒
              applyCalculatedBindsUseOrderIfNeeded(
                Model.Default,
                staticModel.defaultValueOrder,
                strategy,
                collector
              )
            case _ ⇒
          }

        if (staticModel.hasCalculateBind)
          applyCalculatedBindsUseOrderIfNeeded(
            Model.Calculate,
            staticModel.recalculateOrder,
            AllDefaultsStrategy,
            collector
          )

        applyComputedExpressionBinds(collector)
      }
    }
    isFirstCalculate = false
  }

  private def evaluateAndSetCustomMIPs(
    bindNode  : BindNode,
    collector : XFormsEvent ⇒ Unit
  ): Unit =
    if (bindNode.staticBind.customMIPNameToXPathMIP.nonEmpty) // in most cases there are no custom MIPs
      for {
        (name, mips) ← bindNode.staticBind.customMIPNameToXPathMIP
        mip          ← mips.headOption
        result       ← evaluateCustomMIP(bindNode, mip, collector)
      } locally {
        bindNode.setCustom(name, result)
      }

  protected def evaluateCustomMIP(
    bindNode  : BindNode,
    mip       : StaticXPathMIP,
    collector : XFormsEvent ⇒ Unit
  ): Option[String] = {
    try {
      Option(evaluateStringExpression(model, bindNode, mip))
    } catch {
      case NonFatal(t) ⇒
        handleMIPXPathException(t, bindNode, mip, "evaluating XForms custom bind", collector)
        None
    }
  }

  private def applyComputedExpressionBinds(collector: XFormsEvent ⇒ Unit): Unit = {
    // Reset context stack just to re-evaluate the variables as instance values may have changed with @calculate
    model.resetAndEvaluateVariables()
    iterateBinds(topLevelBinds, bindNode ⇒
      if (bindNode.staticBind.hasCalculateComputedMIPs || bindNode.staticBind.hasCustomMIPs)
        handleComputedExpressionBind(bindNode, collector)
    )
  }

  private def handleComputedExpressionBind(
    bindNode  : BindNode,
    collector : XFormsEvent ⇒ Unit
  ): Unit = {
    val staticBind = bindNode.staticBind

    if (staticBind.hasXPathMIP(Relevant) && dependencies.requireModelMIPUpdate(model, staticBind, Relevant, null))
      evaluateBooleanMIP(bindNode, Relevant, DEFAULT_RELEVANT, collector) foreach bindNode.setRelevant

    if (staticBind.hasXPathMIP(Readonly) && dependencies.requireModelMIPUpdate(model, staticBind, Readonly, null) ||
        staticBind.hasXPathMIP(Calculate))
      evaluateBooleanMIP(bindNode, Readonly, DEFAULT_READONLY, collector) match {
        case Some(value) ⇒
          bindNode.setReadonly(value)
        case None if bindNode.staticBind.hasXPathMIP(Calculate) ⇒
          // The bind doesn't have a readonly attribute, but has a calculate: set readonly to true()
          bindNode.setReadonly(true)
        case None ⇒
      }

    if (staticBind.hasXPathMIP(Required) && dependencies.requireModelMIPUpdate(model, staticBind, Required, null))
      evaluateBooleanMIP(bindNode, Required, DEFAULT_REQUIRED, collector) foreach bindNode.setRequired

    evaluateAndSetCustomMIPs(bindNode, collector)
  }

  protected def evaluateBooleanMIP(bindNode: BindNode, mipType: BooleanMIP, defaultForMIP: Boolean, collector: XFormsEvent ⇒ Unit): Option[Boolean] = {
    bindNode.staticBind.firstXPathMIP(mipType) map { mip ⇒
      try {
        evaluateBooleanExpression(model, bindNode, mip)
      } catch {
        case NonFatal(t) ⇒
          handleMIPXPathException(t, bindNode, mip, s"evaluating XForms ${mipType.name} bind", collector)
          ! defaultForMIP // https://github.com/orbeon/orbeon-forms/issues/835
      }
    }
  }

  def applyWhitespaceBinds(collector: XFormsEvent ⇒ Unit): Unit = {
    iterateBinds(topLevelBinds, bindNode ⇒
      if (! bindNode.hasChildrenElements) { // quick test to rule out containing elements
        bindNode.staticBind.nonPreserveWhitespaceMIPOpt foreach { mip ⇒
          if (dependencies.requireModelMIPUpdate(model, bindNode.staticBind, Model.Whitespace, null)) {
            DataModel.setValueIfChangedHandleErrors(
              containingDocument = containingDocument,
              eventTarget        = model,
              locationData       = bindNode.locationData,
              nodeInfo           = bindNode.node,
              valueToSet         = applyPolicy(DataModel.getValue(bindNode.item), mip.policy),
              source             = "whitespace",
              isCalculate        = true,
              collector          = collector
            )
          }
        }
      }
    )
  }

  // Q: Can bindNode.node ever be null here?
  private def mustEvaluateNode(node: NodeInfo, defaultsStrategy: SomeDefaultsStrategy) =
    defaultsStrategy == AllDefaultsStrategy || (node ne null) && InstanceData.getRequireDefaultValue(node)

  private def applyCalculatedBindsUseOrderIfNeeded(
    mip              : StringMIP,
    orderOpt         : Option[List[StaticBind]],
    defaultsStrategy : SomeDefaultsStrategy,
    collector        : XFormsEvent ⇒ Unit
  ): Unit = {
    orderOpt match {
      case Some(order) ⇒
        applyCalculatedBindsFollowDependencies(order, mip, defaultsStrategy, collector)
      case None ⇒
        iterateBinds(topLevelBinds, bindNode ⇒
          if (
            bindNode.staticBind.hasXPathMIP(mip)                                            &&
            dependencies.requireModelMIPUpdate(model, bindNode.staticBind, mip, null) &&
            mustEvaluateNode(bindNode.node, defaultsStrategy)
          ) {
            evaluateAndSetCalculatedBind(bindNode, mip, collector)
          }
        )
    }
  }

  private def applyCalculatedBindsFollowDependencies(
    order            : List[StaticBind],
    mip              : StringMIP,
    defaultsStrategy : SomeDefaultsStrategy,
    collector        : XFormsEvent ⇒ Unit
  ): Unit = {
    order foreach { staticBind ⇒
      val logger = DependencyAnalyzer.Logger
      val isDebug = logger.isDebugEnabled
      if (dependencies.requireModelMIPUpdate(model, staticBind, mip, null)) {
        var evaluationCount = 0
        BindVariableResolver.resolveNotAncestorOrSelf(this, None, staticBind) foreach { runtimeBindIt ⇒
          runtimeBindIt flatMap (_.bindNodes) foreach { bindNode ⇒

            // Skip if we must process only flagged nodes and the node is not flagged
            if (mustEvaluateNode(bindNode.node, defaultsStrategy)) {
              evaluationCount += 1
              evaluateAndSetCalculatedBind(bindNode, mip, collector)
            }
          }
        }
        if (isDebug) logger.debug(s"run  ${mip.name} for ${staticBind.staticId} ($evaluationCount total)")
      } else {
        if (isDebug) logger.debug(s"skip ${mip.name} for ${staticBind.staticId}")
      }
    }
  }

  private def evaluateAndSetCalculatedBind(
    bindNode  : BindNode,
    mip       : StringMIP,
    collector : XFormsEvent ⇒ Unit
  ): Unit =
    evaluateCalculatedBind(bindNode, mip, collector) foreach { stringResult ⇒

      val valueToSet =
        bindNode.staticBind.nonPreserveWhitespaceMIPOpt match {
          case Some(mip) ⇒ applyPolicy(stringResult, mip.policy)
          case None      ⇒ stringResult
        }

      DataModel.setValueIfChangedHandleErrors(
        containingDocument = containingDocument,
        eventTarget        = model,
        locationData       = bindNode.locationData,
        nodeInfo           = bindNode.node,
        valueToSet         = valueToSet,
        source             = mip.name,
        isCalculate        = true,
        collector          = collector
      )
    }

  protected def evaluateCalculatedBind(bindNode: BindNode, mip: StringMIP, collector: XFormsEvent ⇒ Unit): Option[String] =
    bindNode.staticBind.firstXPathMIP(mip) flatMap { xpathMIP ⇒
      try Option(evaluateStringExpression(model, bindNode, xpathMIP))
      catch {
        case NonFatal(t) ⇒
          handleMIPXPathException(t, bindNode, xpathMIP, s"evaluating XForms ${xpathMIP.name} MIP", collector)
          // Blank value so we don't have stale calculated values
          Some("")
      }
    }
}

trait ValidationBindOps extends Logging {

  self: XFormsModelBinds ⇒

  private lazy val xformsValidator = {
    val validator = new XFormsModelSchemaValidator("oxf:/org/orbeon/oxf/xforms/xforms-types.xsd")
    validator.loadSchemas(containingDocument)
    validator
  }

  def applyValidationBinds(invalidInstances: m.Set[String], collector: XFormsEvent ⇒ Unit): Unit = {
    if (! staticModel.mustRevalidate) {
      debug("skipping bind revalidate", List("model id" → model.getEffectiveId, "reason" → "no validation binds"))
    } else {

      // Reset context stack just to re-evaluate the variables
      model.resetAndEvaluateVariables()

      // 1. Validate based on type and requiredness
      if (staticModel.hasTypeBind || staticModel.hasRequiredBind)
        iterateBinds(topLevelBinds, bindNode ⇒
          if (bindNode.staticBind.dataType.isDefined || bindNode.staticBind.hasXPathMIP(Required))
            validateTypeAndRequired(bindNode, invalidInstances)
        )

      // 2. Validate constraints
      if (staticModel.hasConstraintBind)
        iterateBinds(topLevelBinds, bindNode ⇒
          if (bindNode.staticBind.constraintsByLevel.nonEmpty)
            validateConstraint(bindNode, invalidInstances, collector)
        )
    }
  }

  private def validateTypeAndRequired(bindNode: BindNode, invalidInstances: m.Set[String]): Unit = {

    val staticBind = bindNode.staticBind

    assert(staticBind.typeMIPOpt.isDefined || staticBind.hasXPathMIP(Required))

    // Don't try to apply validity to a node if it has children nodes or if it's not a node
    // "The type model item property is not applied to instance nodes that contain child elements"
    val currentNodeInfo = bindNode.node
    if ((currentNodeInfo eq null) || bindNode.hasChildrenElements)
      return

    // NOTE: 2011-02-03: Decided to also apply this to required validation.
    // See: http://forge.ow2.org/tracker/index.php?func=detail&aid=315821&group_id=168&atid=350207

    // Current required value (computed during previous recalculate)
    val isRequired = InstanceData.getRequired(currentNodeInfo)

    val requiredMIPOpt = staticBind.firstXPathMIP(Required)

    // 1. Check type validity

    // Type MIP @type attribute is special:
    //
    // - it is not an XPath expression
    // - but because type validation can be expensive, we want to optimize that if we can
    // - so requireModelMIPUpdate(Model.TYPE) actually means "do we need to update type validity"
    //
    // xxf:xml and xxf:xpath2 also depend on requiredness, which is probably not a good idea. To handle
    // this condition (partially), if the same bind has @type and @required, we also reevaluate type validity if
    // requiredness has changed. Ideally:
    //
    // - we would not depend on requiredness
    // - but if we did, we should handle also the case where another bind is setting requiredness on the node
    //
    val typeValidity =
      staticBind.dataType match {
        case Some(_) ⇒
          if (dependencies.requireModelMIPUpdate(model, staticBind, Type, null) ||
            requiredMIPOpt.isDefined && dependencies.requireModelMIPUpdate(model, staticBind, Required, null)) {
            // Compute new type validity if the value of the node might have changed OR the value of requiredness
            // might have changed
            val typeValidity = validateType(bindNode.parentBind, currentNodeInfo, isRequired)
            bindNode.setTypeValid(typeValidity, staticBind.typeMIPOpt.get)
            typeValidity
          } else {
            // Keep current value
            bindNode.typeValid
          }
        case None ⇒
          // Keep current value (defaults to true when no type attribute)
          bindNode.typeValid
      }

    // 2. Check required validity
    // We compute required validity every time
    val requiredValidity =
      ! isRequired || ! isEmptyValue(DataModel.getValue(currentNodeInfo))

    bindNode.setRequiredValid(requiredValidity, requiredMIPOpt)

    // Remember invalid instances
    if (! typeValidity || ! requiredValidity) {
      val instanceForNodeInfo = containingDocument.getInstanceForNode(currentNodeInfo)
      if (instanceForNodeInfo ne null)
        invalidInstances += instanceForNodeInfo.getEffectiveId
    }
  }

  private def validateType(bind: RuntimeBind, currentNodeInfo: NodeInfo, required: Boolean): Boolean = {

    val staticBind = bind.staticBind

    // NOTE: xf:bind/@type is a literal type value, and it is the same that applies to all nodes pointed to by xf:bind/@ref
    val typeQName = staticBind.dataType.get

    val typeNamespaceURI = typeQName.getNamespaceURI
    val typeLocalname    = typeQName.getName

    // Get value to validate if not already computed above

    val nodeValue = DataModel.getValue(currentNodeInfo)

    // TODO: "[...] these datatypes can be used in the type model item property without the addition of the
    // XForms namespace qualifier if the namespace context has the XForms namespace as the default
    // namespace."
    val isBuiltInSchemaType  = XMLConstants.XSD_URI == typeNamespaceURI
    val isBuiltInXFormsType  = XFormsConstants.XFORMS_NAMESPACE_URI == typeNamespaceURI
    val isBuiltInXXFormsType = XFormsConstants.XXFORMS_NAMESPACE_URI == typeNamespaceURI

    // TODO: Check what XForms event must be dispatched.
    def throwError() =
      throw new ValidationException(s"Invalid schema type `${typeQName.getQualifiedName}`", staticBind.locationData)

    if (isBuiltInXFormsType && nodeValue.isEmpty) {
      // Don't consider the node invalid if the string is empty with xf:* types
      true
    } else if (isBuiltInXFormsType && typeLocalname == "email") {
      EmailValidatorNoDomainValidation.isValid(nodeValue)
    } else if (isBuiltInXFormsType && (typeLocalname == "HTMLFragment")) {
      // Just a marker type
      true
    } else if (isBuiltInXFormsType && Model.jXFormsSchemaTypeNames.contains(typeLocalname)) {
      // xf:dayTimeDuration, xf:yearMonthDuration, xf:email, xf:card-number
      val validationError = xformsValidator.validateDatatype(
        nodeValue,
        typeNamespaceURI,
        typeLocalname,
        typeQName.getQualifiedName,
        staticBind.locationData
      )
      validationError eq null
    } else if (isBuiltInSchemaType || isBuiltInXFormsType) {
      // Built-in schema or XForms type

      // Use XML Schema namespace URI as Saxon doesn't know anything about XForms types
      val newTypeNamespaceURI = XMLConstants.XSD_URI

      // Get type information
      val requiredTypeFingerprint = StandardNames.getFingerprint(newTypeNamespaceURI, typeLocalname)
      if (requiredTypeFingerprint == -1)
        throwError()

      // Need an evaluator to check and convert type below
      val xpathEvaluator =
        try {
          val evaluator = new XPathEvaluator(XPath.GlobalConfiguration)

          // NOTE: Not sure declaring namespaces here is necessary just to perform the cast
          val context = evaluator.getStaticContext.asInstanceOf[IndependentContext]
          for ((prefix, uri) ← staticBind.namespaceMapping.mapping.asScala)
            context.declareNamespace(prefix, uri)

          evaluator
        } catch {
          case NonFatal(t) ⇒
            throw OrbeonLocationException.wrapException(t, staticBind.locationData)
            // TODO: Check what XForms event must be dispatched.
        }

      // Try to perform casting
      // TODO: Should we actually perform casting? This for example removes leading and trailing space around tokens.
      // Is that expected?
      val stringValue = new StringValue(nodeValue)
      stringValue.convertPrimitive(
        BuiltInType.getSchemaType(requiredTypeFingerprint).asInstanceOf[BuiltInAtomicType],
        true,
        new XPathContextMajor(stringValue, xpathEvaluator.getExecutable)
      ) match {
        case _: ValidationFailure ⇒ false
        case _                    ⇒ true
      }
    } else if (isBuiltInXXFormsType) {
      // Built-in extension types

      val isOptionalAndEmpty = ! required && nodeValue == ""
      if (typeLocalname == "xml") {
        isOptionalAndEmpty || XMLParsing.isWellFormedXML(nodeValue)
      } else if (typeLocalname == "xpath2") {

        // Find element which scopes namespaces
        val namespaceNodeInfo =
          if (currentNodeInfo.getNodeKind == Node.ELEMENT_NODE)
            currentNodeInfo
          else
            currentNodeInfo.getParent

        if ((namespaceNodeInfo ne null) && namespaceNodeInfo.getNodeKind == Node.ELEMENT_NODE) {
          // ASSUMPTION: Binding to dom4j-backed node (which InstanceData assumes too)
          val namespaceElement = XML.unsafeUnwrapElement(namespaceNodeInfo)
          val namespaceMapping = new NamespaceMapping(Dom4jUtils.getNamespaceContextNoDefault(namespaceElement))
          isOptionalAndEmpty || XPath.isXPath2Expression(
            nodeValue,
            namespaceMapping,
            containingDocument.getFunctionLibrary,
            staticBind.locationData
          )
        } else {
          // This means that we are bound to a node which is not an element and which does not have a
          // parent element. This could be a detached attribute, or an element node, etc. Unsure if we
          // would have made it this far anyway! We can't validate the expression so we only consider
          // the "optional-and-empty" case.
          isOptionalAndEmpty
        }
      } else {
        throwError()
      }
    } else if (model.hasSchema) {
      // Other type and there is a schema

      // There are possibly types defined in the schema
      val validationError = model.schemaValidator.validateDatatype(
        nodeValue,
        typeNamespaceURI,
        typeLocalname,
        typeQName.getQualifiedName,
        staticBind.locationData
      )

      validationError eq null
    } else {
      throwError()
    }
  }

  private def validateConstraint(
    bindNode         : BindNode,
    invalidInstances : m.Set[String],
    collector        : XFormsEvent ⇒ Unit
  ): Unit = {

    assert(bindNode.staticBind.constraintsByLevel.nonEmpty)

    // Don't try to apply constraints if it's not a node (it's set to null in that case)
    val currentNode = bindNode.node
    if (currentNode eq null)
      return

    // NOTE: 2011-02-03: Decided to allow setting a constraint on an element with children. Handles the case of
    // assigning validity to an enclosing element.
    // See: http://forge.ow2.org/tracker/index.php?func=detail&aid=315821&group_id=168&atid=350207

    // NOTE: 2015-05-27: We used to not run constraints if the datatype was not valid. This could cause a bug
    // when the type would switch from valid to invalid and back. In addition, we do want to run constraints so
    // that validation properties such as `max-length` are computed even when the datatype is not valid. So now we
    // keep the list of constraints up to date even when the datatype is not valid.

    for {
      (level, mips) ← bindNode.staticBind.constraintsByLevel
    } locally {
      if (dependencies.requireModelMIPUpdate(model, bindNode.staticBind, Constraint, level)) {
        // Re-evaluate and set
        val failedConstraints = failedConstraintMIPs(mips, bindNode, collector)
        if (failedConstraints.nonEmpty)
          bindNode.failedConstraints += level → failedConstraints
        else
          bindNode.failedConstraints -= level
      } else {
        // Don't change list of failed constraints for this level
      }
    }

    // Remember invalid instances
    if (! bindNode.constraintsSatisfiedForLevel(ErrorLevel)) {
      val instanceForNodeInfo = containingDocument.getInstanceForNode(currentNode)
      invalidInstances += instanceForNodeInfo.getEffectiveId
    }
  }

  protected def failedConstraintMIPs(
    mips      : List[StaticXPathMIP],
    bindNode  : BindNode,
    collector : XFormsEvent ⇒ Unit
  ): List[StaticXPathMIP] =
    for {
      mip       ← mips
      succeeded = evaluateBooleanExpressionStoreProperties(bindNode, mip, collector)
      if ! succeeded
    } yield
      mip

  private def evaluateBooleanExpressionStoreProperties(
    bindNode  : BindNode,
    xpathMIP  : StaticXPathMIP,
    collector : XFormsEvent ⇒ Unit
  ): Boolean =
    try {
      // LATER: If we implement support for allowing binds to receive events, source must be bind id.
      val functionContext =
        model.getContextStack.getFunctionContext(model.getEffectiveId, Some(bindNode))

      val result =
        XPath.evaluateSingle(
          contextItems        = bindNode.parentBind.items,
          contextPosition     = bindNode.position,
          compiledExpression  = xpathMIP.compiledExpression,
          functionContext     = functionContext,
          variableResolver    = model.variableResolver
        ).asInstanceOf[Boolean]

      functionContext.properties foreach { propertiesMap ⇒
        propertiesMap foreach {
          case (name, Some(s)) ⇒ bindNode.setCustom(name, s)
          case (name, None)    ⇒ bindNode.clearCustom(name)
        }
      }

      result
    } catch {
      case NonFatal(t) ⇒
        handleMIPXPathException(t, bindNode, xpathMIP, "evaluating XForms constraint bind", collector)
        ! Model.DEFAULT_VALID
    }
}

object XFormsModelBinds {

  type StaticMIP      = StaticBind#MIP
  type StaticXPathMIP = StaticBind#XPathMIP
  type StaticTypeMIP  = StaticBind#TypeMIP

  // Create an instance of XFormsModelBinds if the given model has xf:bind elements.
  def apply(model: XFormsModel) =
    if (model.getStaticModel.hasBinds) new XFormsModelBinds(model) else null

  // Modified email validator which:
  //
  // 1. Doesn't check whether a TLD is known, as there are two many of those now (2015) and changing constantly.
  // 2. Doesn't support IP addresses (we probably could, but we don't care right now).
  //
  object EmailValidatorNoDomainValidation extends EmailValidator(false) {

    private val DomainLabelRegex = "\\p{Alnum}(?>[\\p{Alnum}-]*\\p{Alnum})*"
    private val TopLabelRegex    = "\\p{Alpha}{2,}"
    private val DomainNameRegex  = "^(?:" + DomainLabelRegex + "\\.)+" + "(" + TopLabelRegex + ")$"

    private val DomainRegex = new RegexValidator(DomainNameRegex)

    override def isValidDomain(domain: String) =
      Option(DomainRegex.`match`(domain)) exists (_.nonEmpty)
  }

  def isEmptyValue(value: String): Boolean = "" == value

  def iterateBinds(topLevelBinds: List[RuntimeBind], fn: BindNode ⇒ Unit): Unit =
    for (currentBind ← topLevelBinds)
      try currentBind.applyBinds(fn)
      catch {
        case NonFatal(t) ⇒
          throw OrbeonLocationException.wrapException(
            t,
            new ExtendedLocationData(
              currentBind.staticBind.locationData,
              "evaluating XForms binds",
              currentBind.staticBind.element
            )
          )
      }

  def evaluateBooleanExpression(
    model    : XFormsModel,
    bindNode : BindNode,
    xpathMIP : StaticXPathMIP)(implicit
    reporter : Reporter
  ): Boolean =
    XPath.evaluateSingle(
      contextItems        = bindNode.parentBind.items,
      contextPosition     = bindNode.position,
      compiledExpression  = xpathMIP.compiledExpression,
      functionContext     = model.getContextStack.getFunctionContext(model.getEffectiveId, Some(bindNode)),
      variableResolver    = model.variableResolver
    ).asInstanceOf[Boolean]

  def evaluateStringExpression(
    model    : XFormsModel,
    bindNode : BindNode,
    xpathMIP : StaticXPathMIP)(implicit
    reporter : Reporter
  ): String =
    XPath.evaluateAsString(
      contextItems       = bindNode.parentBind.items,
      contextPosition    = bindNode.position,
      compiledExpression = xpathMIP.compiledExpression,
      functionContext    = model.getContextStack.getFunctionContext(model.getEffectiveId, Some(bindNode)),
      variableResolver   = model.variableResolver
    )

  def handleMIPXPathException(
    throwable : Throwable,
    bindNode  : BindNode,
    xpathMIP  : StaticXPathMIP,
    message   : String,
    collector : XFormsEvent ⇒ Unit)(implicit
    logger    : IndentedLogger
  ): Unit = {
    Exceptions.getRootThrowable(throwable) match {
      case e: TypedNodeWrapper.TypedValueException ⇒
        // Consider validation errors as ignorable. The rationale is that if the function (the XPath
        // expression) works on inputs that are not valid (hence the validation error), then the function cannot
        // produce a meaningful result. We think that it is worth handling this condition slightly differently
        // from other dynamic and static errors, so that users can just write expression without constant checks
        // with `castable as` or `instance of`.
        debug("typed value exception", List(
          "node name"     → e.nodeName,
          "expected type" → e.typeName,
          "actual value"  → e.nodeValue
        ))
      case t ⇒
        // All other errors dispatch an event and will cause the usual fatal-or-not behavior
        val ve = OrbeonLocationException.wrapException(t,
          new ExtendedLocationData(
            locationData = bindNode.locationData,
            description  = Option(message),
            params       = List("expression" → xpathMIP.compiledExpression.string),
            element      = Some(bindNode.staticBind.element)
          )
        )

        collector(new XXFormsXPathErrorEvent(bindNode.parentBind.model, ve.getMessage, ve))
    }
  }
}
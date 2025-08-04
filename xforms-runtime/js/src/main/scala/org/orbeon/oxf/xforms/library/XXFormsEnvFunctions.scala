package org.orbeon.oxf.xforms.library

import cats.syntax.option.*
import org.orbeon.dom.QName
import org.orbeon.io.CharsetNames.Iso88591
import org.orbeon.macros.XPathFunction
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{MessageFormatCache, MessageFormatter, XPathCache}
import org.orbeon.oxf.xforms.analysis.controls.LHHA
import org.orbeon.oxf.xforms.control.controls.XXFormsAttributeControl
import org.orbeon.oxf.xforms.control.{XFormsSingleNodeControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.function.XFormsFunction.*
import org.orbeon.oxf.xforms.function.xxforms.*
import org.orbeon.oxf.xforms.function.xxforms.XXFormsResourceSupport.{findResourceElementForLang, pathFromTokens, splitResourceName}
import org.orbeon.oxf.xforms.itemset.ItemsetSupport
import org.orbeon.oxf.xforms.library.XFormsEnvFunctions.findIndexForRepeatId
import org.orbeon.oxf.xforms.model.{BindNode, InstanceData, XFormsInstance, XFormsModel}
import org.orbeon.oxf.xml.{OrbeonFunctionLibrary, SaxonUtils}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.function.{CoreSupport, GetRequestHeaderSupport}
import org.orbeon.saxon.ma.map.MapItem
import org.orbeon.saxon.model.BuiltInAtomicType
import org.orbeon.saxon.om
import org.orbeon.saxon.om.{Item, SequenceTool, StandardNames}
import org.orbeon.saxon.value.{AtomicValue, QNameValue, SequenceExtent, StringValue}
import org.orbeon.xforms.XFormsId
import shapeless.syntax.typeable.*

import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag


trait XXFormsEnvFunctions extends OrbeonFunctionLibrary {

  // NOTE: This is deprecated and just points to the event() function.
//    Fun("event", classOf[Event], op = 0, min = 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("cases", classOf[XXFormsCases], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("repeat-current", classOf[XXFormsRepeatCurrent], op = 0, min = 0, Type.NODE_TYPE, EXACTLY_ONE,
//      Arg(STRING, EXACTLY_ONE)
//    )

  @XPathFunction
  def repeatPosition(repeatId: String = null)(implicit xfc: XFormsFunction.Context): Int =
    xfc.bindingContext.enclosingRepeatIterationBindingContext(Option(repeatId)).position

  @XPathFunction
  def repeatPositions()(implicit xfc: XFormsFunction.Context): Iterator[Int] =
    xfc.bindingContext.repeatPositions

//    Fun("context", classOf[XXFormsContext], op = 0, min = 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_ONE,
//      Arg(STRING, EXACTLY_ONE)
//    )

  @XPathFunction
  def repeatItems(contextIdOpt: Option[String] = None)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Iterable[om.Item] = {

    val repeatId =
      contextIdOpt match {
        case Some(contextId) => contextId
        case None => xfc.bindingContext.enclosingRepeatId
      }

    xfc.bindingContext.repeatItems(repeatId).asScala
  }

  //
//    // Backward compatibility, use repeat-items() instead
//    Fun("repeat-nodeset", classOf[XXFormsRepeatItems], op = 0, min = 0, Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("evaluate-bind-property", classOf[XXFormsEvaluateBindProperty], op = 0, min = 2, ANY_ATOMIC, ALLOWS_ZERO_OR_ONE,
//      Arg(STRING, EXACTLY_ONE),
//      Arg(ANY_ATOMIC, EXACTLY_ONE) // QName or String
//    )
//
//    Fun("valid", classOf[XXFormsValid], op = 0, min = 0, BOOLEAN, EXACTLY_ONE,
//      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
//      Arg(BOOLEAN, EXACTLY_ONE),
//      Arg(BOOLEAN, EXACTLY_ONE)
//    )

  @XPathFunction
  def `type`(item: Option[om.Item] = None)(implicit xpc: XPathContext): Option[om.Item] = // TODO: should be `QNameValue`
    item getOrElse xpc.getContextItem match {
      case atomicValue: AtomicValue =>
        atomicValue.getItemType match {
          case atomicType: BuiltInAtomicType =>
            val fingerprint = atomicType.getFingerprint
            new QNameValue(
              StandardNames.getPrefix(fingerprint),
              StandardNames.getURI(fingerprint),
              StandardNames.getLocalName(fingerprint)
            ).some
          case _ =>
            None
        }
      case node: om.NodeInfo =>
        Option(InstanceData.getType(node)) match {
          case Some(typeQName) =>
            new QNameValue(
              "",
              typeQName.namespace.uri,
              typeQName.localName
            ).some
          case _ =>
            None
        }
      case _ =>
        None
    }

  // NOTE: Custom MIPs are registered with a qualified name string. It would be better to use actual QNames
  // so that the prefix is not involved. The limitation for now is that you have to use the same prefix as
  // the one used on the binds. See also https://github.com/orbeon/orbeon-forms/issues/3721.
  @XPathFunction
  def customMip(
    binding : Iterable[om.Item],
    qName   : om.Item)(implicit
    xpc     : XPathContext,
    xfc     : XFormsFunction.Context
  ): Option[String] =
    InstanceData.findCustomMip(
      binding = binding.headOption.orNull,
      qName   = XFormsFunction.getQNameFromItem(qName)
    )

  @XPathFunction
  def invalidBinds(
    binding : Option[Iterable[om.Item]]
  )(implicit
    xpc     : XPathContext
  ): Iterable[String] =
    binding
      .getOrElse(Option(xpc.getContextItem).toList)
      .headOption
      .flatMap(_.narrowTo[om.NodeInfo])
      .map(InstanceData.getInvalidBindIds)
      .getOrElse(Nil)

  @XPathFunction
  def failedValidations(
    binding : Option[Iterable[om.Item]]
  )(implicit
    xpc     : XPathContext
  ): Iterable[String] =
    binding
      .getOrElse(Option(xpc.getContextItem).toList)
      .headOption
      .flatMap(_.narrowTo[om.NodeInfo])
      .flatMap(BindNode.failedValidationsForHighestLevelPrioritizeRequired)
      .map(_._2)
      .getOrElse(Nil)
      .map(_.id)

//    Fun("if", classOf[If], op = 0, min = 3, STRING, EXACTLY_ONE,
//      Arg(BOOLEAN, EXACTLY_ONE),
//      Arg(STRING, EXACTLY_ONE),
//      Arg(STRING, EXACTLY_ONE)
//    )

  @XPathFunction
  def binding(staticOrAbsoluteId: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Iterable[om.Item] =
    findControlsByStaticOrAbsoluteId(staticOrAbsoluteId, followIndexes = true)
      .headOption.toList.flatMap(_.bindingEvenIfNonRelevant)

  @XPathFunction
  def bindingContext(staticOrAbsoluteId: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Option[om.Item] =
    relevantControl(staticOrAbsoluteId) flatMap (_.contextForBinding)

  @XPathFunction
  def isControlRelevant(staticOrAbsoluteId: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Boolean =
    relevantControl(staticOrAbsoluteId).nonEmpty

  @XPathFunction
  def isControlReadonly(staticOrAbsoluteId: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Boolean =
    relevantWithPredicate[XFormsSingleNodeControl](staticOrAbsoluteId, _.isReadonly)

  @XPathFunction
  def isControlRequired(staticOrAbsoluteId: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Boolean =
    relevantWithPredicate[XFormsSingleNodeControl](staticOrAbsoluteId, _.isRequired)

  @XPathFunction
  def isControlValid(staticOrAbsoluteId: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Boolean =
    relevantWithPredicate[XFormsSingleNodeControl](staticOrAbsoluteId, _.isValid)

  @XPathFunction
  def isControlStaticReadonly(staticOrAbsoluteId: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Boolean =
    relevantWithPredicate[XFormsSingleNodeControl](staticOrAbsoluteId, _.isStaticReadonly)

  private def relevantWithPredicate[T <: XFormsSingleNodeControl : ClassTag](
    staticOrAbsoluteId: String,
    predicate         : T => Boolean
  )(implicit
    xpc               : XPathContext,
    xfc               : XFormsFunction.Context
  ): Boolean =
    relevantControl(staticOrAbsoluteId)
      .flatMap(_.narrowTo[T])
      .exists(predicate)

  @XPathFunction
  def value(staticOrAbsoluteId: String, followIndexes: Boolean = true)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Iterable[String] =
    XFormsFunction.findRelevantControls(staticOrAbsoluteId, followIndexes) flatMap
      (_.narrowTo[XFormsValueControl]) flatMap (_.valueOpt(EventCollector.Throw))

  @XPathFunction
  def formattedValue(staticOrAbsoluteId: String, followIndexes: Boolean = true)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Iterable[String] =
    XFormsFunction.findRelevantControls(staticOrAbsoluteId, followIndexes) flatMap
      (_.narrowTo[XFormsValueControl]) flatMap (_.getFormattedValue(EventCollector.Throw))

  @XPathFunction
  def avtValue(forId: String, attName: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Iterable[String] =
    for {
      forPrefixedId      <- sourceScope.prefixedIdForStaticIdOpt(forId)
      attControlAnalysis <- Option(xfc.container.partAnalysis.getAttributeControl(forPrefixedId, attName))
      control            <- findRelevantControls(attControlAnalysis.staticId, followIndexes = true).headOption
      attControl         <- control.narrowTo[XXFormsAttributeControl]
      value              <- attControl.valueOpt(EventCollector.Throw)
    } yield
      value

  @XPathFunction
  def componentContext()(implicit xfc: XFormsFunction.Context): Iterable[om.Item] =
    for {
      componentControl <- xfc.container.associatedControlOpt.toList
      bindingContext   <- Option(componentControl.bindingContext.parent).toList
      item             <- bindingContext.nodeset.asScala
    } yield
      item

  @XPathFunction
  def componentParamValue(paramNameString: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Option[AtomicValue] =
    ComponentParamSupport.componentParamValue(
      paramName = QName(paramNameString),
      property  = CoreSupport.property
    )

  @XPathFunction
  def instance(
    instanceId : String)(implicit
    xpc        : XPathContext,
    xfc        : XFormsFunction.Context
  ): Option[om.NodeInfo] =
    instanceId.trimAllToOpt flatMap (XFormsInstance.findInAncestorScopes(xfc.container, _))

  @XPathFunction
  def index(repeatStaticId: Option[String] = None)(implicit xfc: XFormsFunction.Context): Int =
    repeatStaticId match {
      case Some(repeatId: String) => findIndexForRepeatId(repeatId)
      case None                   => findIndexForRepeatId(xfc.bindingContext.enclosingRepeatId)
    }

  @XPathFunction
  def listModels()(implicit xfc: XFormsFunction.Context): Iterator[String] =
    for {
      model       <- xfc.containingDocument.allModels
      effectiveId = model.effectiveId
      absoluteId  = XFormsId.effectiveIdToAbsoluteId(effectiveId)
    } yield
      absoluteId

  @XPathFunction
  def listInstances(modelId: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Iterator[String] = {

    val modelOpt =
      resolveOrFindByStaticOrAbsoluteId(modelId) collect { case model: XFormsModel => model }

    for {
      model       <- modelOpt.iterator
      instance    <- model.instancesIterator
      effectiveId = instance.effectiveId
      absoluteId  = XFormsId.effectiveIdToAbsoluteId(effectiveId)
    } yield
      absoluteId
  }

  //
//    Fun("list-variables", classOf[XXFormsListVariables], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE)
//    )

  @XPathFunction
  def getVariable(modelEffectiveId: String, variableName: String)(implicit xfc: XFormsFunction.Context): Iterable[om.Item] = {
    xfc.containingDocument.getObjectByEffectiveId(modelEffectiveId) match {
      case model: XFormsModel => model.getTopLevelVariables(variableName).asIterable().asScala
      case _                  => Nil
    }
  }

  @XPathFunction
  def itemset(controlId: String, format: String, selected: Boolean = false)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Option[om.Item] =
    for {
      control        <- relevantControl(controlId)
      valueControl   <- control.narrowTo[XFormsValueControl]
      select1Control <- ItemsetSupport.findSelectionControl(valueControl)
      itemset        = select1Control.getItemset(EventCollector.Throw)
    } yield {

      val controlValueForSelection =
        if (selected)
          select1Control
            .boundItemOpt
            .map(select1Control.getCurrentItemValueFromData(_, EventCollector.Throw))
            .map { v => (v, SaxonUtils.attCompare(select1Control.boundNodeOpt, _)) }
        else
          None

      if (format == "json") {
        // Return a string
        StringValue.makeStringValue(
          ItemsetSupport.asJSON(
            itemset                    = itemset,
            controlValue               = controlValueForSelection,
            encode                     = select1Control.mustEncodeValues,
            excludeWhitespaceTextNodes = select1Control.staticControl.excludeWhitespaceTextNodesForCopy,
            locationData               = control.getLocationData
          )
        )
      } else {
        // Return an XML document
        ItemsetSupport.asXML(
          itemset                    = itemset,
          controlValue               = controlValueForSelection,
          excludeWhitespaceTextNodes = select1Control.staticControl.excludeWhitespaceTextNodesForCopy
        )
      }
    }

  @XPathFunction
  def formatMessage(template: String, args: Iterable[om.Item])(implicit xpc: XPathContext, xfc: XFormsFunction.Context): String =
    MessageFormatter.format(MessageFormatCache(template), args map SequenceTool.convertToJava toVector)

  @XPathFunction
  def lang()(implicit xfc: XFormsFunction.Context): Option[String] =
    elementAnalysisForSource flatMap (XXFormsLangSupport.resolveXMLangHandleAVTs(xfc.containingDocument, _))

  @XPathFunction
  def r(
    resourceKey      : String,
    instanceOpt      : Option[String]  = None,
    templateParamsOpt: Option[om.Item] = None // TODO: must be a `MapItem` (`map(*)`)
  )(implicit
    xpc                : XPathContext,
    xfc                : XFormsFunction.Context
  ): Option[String] = {

    def javaNamedParamsOpt: Option[List[(String, Any)]] =
      templateParamsOpt.collect {
        case params: MapItem =>

          val javaNamedParamsIt = params.keyValuePairs.iterator.asScala.map {
            case org.orbeon.saxon.ma.map.KeyValuePair(key, value) =>
              val javaParamOpt = (value.asIterable().iterator.asScala map SequenceTool.convertToJava).nextOption()
              key.getStringValue -> javaParamOpt.orNull
          }

          javaNamedParamsIt.toList
      }

    XXFormsLangSupport.r(
      resourceKey        = resourceKey,
      instanceOpt        = instanceOpt,
      javaNamedParamsOpt = javaNamedParamsOpt
    )
  }

  @XPathFunction
  def resourceElements(
    resourceKeyArgument : String,
    instanceArgument    : String = "fr-form-resources")(implicit
    xpc                 : XPathContext,
    xfc                 : XFormsFunction.Context
  ): Iterator[om.NodeInfo] = {

    def findResourcesElement =
      resolveOrFindByStaticOrAbsoluteId(instanceArgument) collect
        { case instance: XFormsInstance => instance.rootElement }

    for {
      elementAnalysis <- elementAnalysisForSource.iterator
      resources       <- findResourcesElement.iterator
      requestedLang   <- XXFormsLangSupport.resolveXMLangHandleAVTs(xfc.containingDocument, elementAnalysis).iterator
      resourceRoot    <- findResourceElementForLang(resources, requestedLang).iterator
      leaf            <- pathFromTokens(resourceRoot, splitResourceName(resourceKeyArgument)).iterator
    } yield
      leaf
  }

  @XPathFunction
  def pendingUploads(): Int =
    XFormsFunction.context.containingDocument.countPendingUploads

  @XPathFunction
  def openDialogs(): Int =
    XFormsFunction.context.containingDocument.controls.openDialogs

  @XPathFunction
  def documentId()(implicit xfc: XFormsFunction.Context): String =
    xfc.containingDocument.uuid

  @XPathFunction
  def setDocumentAttribute(ns: String, key: String, value: Iterable[om.Item])(implicit xfc: XFormsFunction.Context): Unit =
    xfc.containingDocument.setAttribute(ns, key, SequenceExtent.makeSequenceExtent(value.toSeq.asJava): om.GroundedValue)

  @XPathFunction
  def getDocumentAttribute(ns: String, key: String)(implicit xfc: XFormsFunction.Context): Iterable[om.Item] =
    xfc.containingDocument.getAttribute(ns, key) match {
      case Some(value: om.GroundedValue) => value.asIterable().asScala
      case _                             => Nil
    }

  @XPathFunction
  def removeDocumentAttribute(ns: String, key: String)(implicit xfc: XFormsFunction.Context): Unit =
    xfc.containingDocument.removeAttribute(ns, key)

  @XPathFunction
  def removeDocumentAttributes(ns: String)(implicit xfc: XFormsFunction.Context): Unit =
    xfc.containingDocument.removeAttributes(ns)

  @XPathFunction
  def label(controlId: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Option[String] =
    LHHAFunctionSupport.directOrByLocalLhhaValue(controlId, LHHA.Label)

  @XPathFunction
  def help(controlId: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Option[String] =
    LHHAFunctionSupport.directOrByLocalLhhaValue(controlId, LHHA.Help)

  @XPathFunction
  def hint(controlId: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Option[String] =
    LHHAFunctionSupport.directOrByLocalLhhaValue(controlId, LHHA.Hint)

  @XPathFunction
  def alert(controlId: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Option[String] =
    LHHAFunctionSupport.directOrByLocalLhhaValue(controlId, LHHA.Alert)

  @XPathFunction
  def labelAppearance(controlId: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Option[String] =
    LHHAFunctionSupport.labelHintAppearance(controlId, LHHA.Label)

  @XPathFunction
  def hintAppearance(controlId: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Option[String] =
    LHHAFunctionSupport.labelHintAppearance(controlId, LHHA.Hint)

  @XPathFunction
  def visited(controlId: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Option[Boolean] =
    relevantControl(controlId) map (_.visited)

  @XPathFunction
  def focusable(controlId: String, toggle: Boolean = true)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Boolean = {

    val it =
      if (toggle)
        relevantControl(controlId).iterator flatMap (_.directlyFocusableControlsMaybeWithToggle)
      else
        relevantControl(controlId).iterator flatMap (_.directlyFocusableControls)

    it.nonEmpty
  }

  @XPathFunction
  def absoluteId(staticOrAbsoluteId: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Option[String] =
    resolveOrFindByStaticOrAbsoluteId(staticOrAbsoluteId) map (_.effectiveId) map XFormsId.effectiveIdToAbsoluteId

  @XPathFunction
  def clientId(staticOrAbsoluteId: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Option[String] =
    resolveOrFindByStaticOrAbsoluteId(staticOrAbsoluteId) map (_.effectiveId)

  @XPathFunction
  def controlElement(controlId: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Option[om.NodeInfo] =
    relevantControl(controlId) flatMap
      (control => control.container.partAnalysis.controlElement(control.prefixedId))

//    Fun("extract-document", classOf[XXFormsExtractDocument], op = 0, min = 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_ONE,
//      Arg(Type.NODE_TYPE, EXACTLY_ONE),
//      Arg(STRING, EXACTLY_ONE),
//      Arg(BOOLEAN, EXACTLY_ONE)
//    )
//
//    // RFE: Support XSLT 2.0-features such as multiple sort keys
//    Fun("sort", classOf[XXFormsSort], op = 0, min = 2, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
//      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
//      Arg(Type.ITEM_TYPE, EXACTLY_ONE),
//      Arg(STRING, ALLOWS_ZERO_OR_ONE),
//      Arg(STRING, ALLOWS_ZERO_OR_ONE),
//      Arg(STRING, ALLOWS_ZERO_OR_ONE)
//    )

  // NOTES:
  //
  // - Also in exforms.
  // - This can take more than one item, but only the first one is considered.
  // - This is different from the XForms 2.0 functions.
  //
  def exformsMipFunction(items: Iterable[om.Item], operation: Int)(implicit xpc: XPathContext): Boolean = {

    val resolvedItems =
      items match {
        // NOTE: Use `null` to differentiate between the argument being statically present or not. Not great?
        case null  => Option(xpc.getContextItem).toList
        case i     => i
      }

    // "If the argument is omitted, it defaults to a node-set with the context node as its only member."
    val itemOption = resolvedItems.headOption

    def getOrDefault(item: om.Item, getFromNode: om.NodeInfo => Boolean, default: Boolean) = item match {
      case nodeInfo: om.NodeInfo => getFromNode(nodeInfo)
      case _                     => default
    }

    def get(item: om.Item) = operation match {
      case 0 => getOrDefault(item, InstanceData.getInheritedRelevant, default = true)
      case 1 => getOrDefault(item, InstanceData.getInheritedReadonly, default = true)
      case 2 => getOrDefault(item, InstanceData.getRequired,          default = false)
    }

    // "If the node-set is empty then the function returns false."
    itemOption exists (item => get(item))
  }

  @XPathFunction
  def relevant(items: Iterable[om.Item] = null)(implicit xpc: XPathContext): Boolean =
    exformsMipFunction(items, 0)

  @XPathFunction
  def readonly(items: Iterable[om.Item] = null)(implicit xpc: XPathContext): Boolean =
    exformsMipFunction(items, 1)

  @XPathFunction
  def required(items: Iterable[om.Item] = null)(implicit xpc: XPathContext): Boolean =
    exformsMipFunction(items, 2)

  // Now available in XForms 2.0
  @XPathFunction
  def bind(bindId: String, searchAncestors: Boolean = false)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Iterable[om.Item] =
    XFormsFunctionLibrary.bindImpl(bindId, searchAncestors)

  // Validation functions

  private def evaluateAndSetConstraint[T](
    propertyName  : String,
    constraintOpt : Option[T],
    evaluate      : String => Boolean)(implicit
    xpc           : XPathContext
  ): Boolean = {

    // XXX check why "true"?
    // For example `nonNegative` will trigger `true`
    XFormsFunction.setProperty(propertyName, constraintOpt map (_.toString) orElse Some("true"))

    Option(xpc.getContextItem) map (_.getStringValue) match {
      case Some(itemValue) => evaluate(itemValue)
      case None            => true
    }
  }

  @XPathFunction
  def maxLength(constraintOpt: Option[Long])(implicit xpc: XPathContext): Boolean = {

    def evaluate(itemValue: String) =
      constraintOpt match {
        case Some(constraint) => org.orbeon.saxon.value.StringValue.getStringLength(itemValue) <= constraint
        case None             => true
      }

    evaluateAndSetConstraint("max-length", constraintOpt, evaluate)
  }

  @XPathFunction
  def minLength(constraintOpt: Option[Long])(implicit xpc: XPathContext): Boolean = {

    def evaluate(itemValue: String) =
      constraintOpt match {
        case Some(constraint) => org.orbeon.saxon.value.StringValue.getStringLength(itemValue) >= constraint
        case None             => true
      }

    evaluateAndSetConstraint("min-length", constraintOpt, evaluate)
  }

  @XPathFunction
  def nonNegative()(implicit xpc: XPathContext): Boolean = {

    def evaluate(value: String) =
      NumericValidation.trySignum(value) exists (_ != -1)

    evaluateAndSetConstraint("non-negative", None, evaluate)
  }

  @XPathFunction
  def negative()(implicit xpc: XPathContext): Boolean = {

    def evaluate(value: String) =
      NumericValidation.trySignum(value) contains -1

    evaluateAndSetConstraint("negative", None, evaluate)
  }

  @XPathFunction
  def nonPositive()(implicit xpc: XPathContext): Boolean = {

    def evaluate(value: String) =
      NumericValidation.trySignum(value) exists (_ != 1)

    evaluateAndSetConstraint("non-positive", None, evaluate)
  }

  @XPathFunction
  def positive()(implicit xpc: XPathContext): Boolean = {

    def evaluate(value: String) =
      NumericValidation.trySignum(value) contains 1

    evaluateAndSetConstraint("positive", None, evaluate)
  }

  @XPathFunction
  def fractionDigits(constraintOpt: Option[Long])(implicit xpc: XPathContext): Boolean = {

    // Operate at the lexical level
    def countDigitsAfterDecimalSeparator(value: String) = {

      var beforeDecimalSeparator      = true
      var digitsAfterDecimalSeparator = 0
      var trailingZeros               = 0

      for (c <- value) {
        if (beforeDecimalSeparator) {
          if (c == '.')
            beforeDecimalSeparator = false
        } else {
          if (c == '0')
            trailingZeros += 1
          else
            trailingZeros = 0

          if ('0' <= c && c <= '9')
            digitsAfterDecimalSeparator += 1
        }
      }

      digitsAfterDecimalSeparator - trailingZeros
    }

    def evaluate(value: String) = constraintOpt match {
      case Some(constraint) => countDigitsAfterDecimalSeparator(value) <= constraint
      case None             => true
    }

    evaluateAndSetConstraint("fraction-digits", constraintOpt, evaluate)
  }

  // Backward compatibility
  @XPathFunction
  def uploadMaxSize(constraintOpt: Option[Long])(implicit xpc: XPathContext): Boolean = {
    // For now, don't actually validate, see #2956
    evaluateAndSetConstraint(ValidationFunctionNames.UploadMaxSize, constraintOpt, _ => true)
  }

  @XPathFunction
  def uploadMaxSizePerFile(constraintOpt: Option[Long])(implicit xpc: XPathContext): Boolean = {
    // For now, don't actually validate, see #2956
    evaluateAndSetConstraint(ValidationFunctionNames.UploadMaxSizePerFile, constraintOpt, _ => true)
  }

  @XPathFunction
  def uploadMaxFilesPerControl(constraintOpt: Option[Long])(implicit xpc: XPathContext): Boolean = {
    // For now, don't actually validate
    evaluateAndSetConstraint(ValidationFunctionNames.UploadMaxSizePerFile, constraintOpt, _ => true)
  }

  @XPathFunction
  def uploadMaxSizeAggregatePerControl(constraintOpt: Option[Long])(implicit xpc: XPathContext): Boolean = {
    // For now, don't actually validate, see #6064
    evaluateAndSetConstraint(ValidationFunctionNames.UploadMaxSizeAggregatePerControl, constraintOpt, _ => true)
  }

  @XPathFunction
  def uploadMediatypes(constraintOpt: Option[String])(implicit xpc: XPathContext): Boolean = {
    // For now, don't actually validate, see #2956
    evaluateAndSetConstraint(ValidationFunctionNames.UploadMediatypes, constraintOpt, _ => true)
  }

//    Fun(ExcludedDatesValidation.PropertyName, classOf[ExcludedDatesValidation], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
//      Arg(DATE, ALLOWS_ZERO_OR_MORE)
//    )

  def evaluateImpl(expr: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Iterable[om.Item] = {

    val xfcd = xfc.containingDocument
    val elem = xfcd.staticOps.findControlAnalysis(XFormsId.getPrefixedId(xfc.sourceEffectiveId)) getOrElse
      (throw new IllegalStateException(xfc.sourceEffectiveId))

    XPathCache.evaluateKeepItems(
      contextItems       = List(xpc.getContextItem).asJava,
      contextPosition    = 1,
      xpathString        = expr,
      namespaceMapping   = elem.namespaceMapping,
      variableToValueMap = xfc.bindingContext.getInScopeVariables,
      functionLibrary    = xfcd.functionLibrary,
      functionContext    = xfc,
      baseURI            = null,
      locationData       = elem.locationData,
      reporter           = null
    )
  }

  @XPathFunction
  def evaluate(expr: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Iterable[om.Item] =
    evaluateImpl(expr)

  @XPathFunction
  def evaluateAvt(avt: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): String = {

    val xfcd = xfc.containingDocument
    val elem = xfcd.staticOps.findControlAnalysis(XFormsId.getPrefixedId(xfc.sourceEffectiveId)) getOrElse
      (throw new IllegalStateException(xfc.sourceEffectiveId))

    XPathCache.evaluateAsAvt(
      contextItem        = xpc.getContextItem,
      xpathString        = avt,
      namespaceMapping   = elem.namespaceMapping,
      variableToValueMap = xfc.bindingContext.getInScopeVariables,
      functionLibrary    = xfcd.functionLibrary,
      functionContext    = xfc,
      baseURI            = null,
      locationData       = elem.locationData,
      reporter           = null
    )
  }

//    Fun("form-urlencode", classOf[XXFormsFormURLEncode], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_ONE,
//      Arg(NODE_TYPE, EXACTLY_ONE)
//    )

  @XPathFunction
  def getRequestMethod()(implicit xfc: XFormsFunction.Context): String =
    xfc.containingDocument.getRequestMethod.entryName

  @XPathFunction
  def getRequestContextPath()(implicit xfc: XFormsFunction.Context): String =
    xfc.containingDocument.getRequestContextPath

  @XPathFunction
  def getRequestPath()(implicit xfc: XFormsFunction.Context): String =
    xfc.containingDocument.getRequestPath

  @XPathFunction
  def getRequestHeader(name: String, encoding: String = Iso88591)(implicit xfc: XFormsFunction.Context): Iterable[String] =
    GetRequestHeaderSupport.getAndDecodeHeader(
      name     = name,
      encoding = encoding.some,
      getter   = xfc.containingDocument.getRequestHeaders.get
    ).toList.flatten

  @XPathFunction
  def getRequestParameter(name: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Iterable[String] =
    xfc.containingDocument.getRequestParameters.getOrElse(name, Nil)

  @XPathFunction
  def evaluateInContext(
    expr       : String,
    effectiveId: String
  )(implicit
    xpc        : XPathContext,
    xfc        : XFormsFunction.Context
  ): List[Item] =
    EvaluateSupport.evaluateInContextFromXPathString(expr, effectiveId)
}

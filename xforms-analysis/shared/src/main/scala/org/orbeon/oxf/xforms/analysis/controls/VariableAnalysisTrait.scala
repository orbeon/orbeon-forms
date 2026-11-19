package org.orbeon.oxf.xforms.analysis.controls

import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.xforms.analysis.*
import org.orbeon.oxf.xforms.analysis.controls.VariableAnalysis.isValueOrSequenceElement
import org.orbeon.xforms.XFormsId
import org.orbeon.xforms.XFormsNames.*


trait VariableTrait {
  def name: String
  def variableAnalysis: Option[XPathAnalysis]
}

trait WithExpressionOrConstantTrait extends ElementAnalysis {
  val expressionOrConstant: Either[String, String]
}

/**
 * Trait representing a variable element, whether in the model or in the view.
 */
trait VariableAnalysisTrait
  extends ElementAnalysis
     with WithChildrenTrait
     with VariableTrait {

  variableSelf =>

  val name: String
  val expressionOrConstant: Either[String, String]

  // Used below and by `ElementAnalysisTreeXPathAnalyzer`
  def nestedValueAnalysis: Option[ElementAnalysis] = children.find(e => VariableAnalysis.ValueOrSequenceQNames(e.qName))

  def valueAnalysisElement: ElementAnalysis = nestedValueAnalysis.getOrElse(variableSelf)

  override protected def synthesizedUnprefixedAttributeValue(local: String): String =
    if (local == "name") name else super.synthesizedUnprefixedAttributeValue(local)

  override protected def extraSynthesizedAttributes: List[(QName, String)] =
    super.extraSynthesizedAttributes ::: (if (name ne null) List(NAME_QNAME -> name) else Nil)

  override protected def isPrunedAttribute(attQName: QName): Boolean =
    super.isPrunedAttribute(attQName) || attQName == NAME_QNAME || attQName == SELECT_QNAME

  // `lazy` because the children are evaluated after the container
  // All used by `Variable`
  lazy val (hasNestedValue, valueScope, valueNamespaceMapping, valueStaticId, valueIsFunctionsRestricted) =
    nestedValueAnalysis match {
      case Some(valueElem) =>
        (true, valueElem.scope, valueElem.namespaceMapping, valueElem.staticId, valueElem.isFunctionsRestricted)
      case None =>
        (false, variableSelf.scope, variableSelf.namespaceMapping, variableSelf.staticId, variableSelf.isFunctionsRestricted)
    }

  def variableAnalysis: Option[XPathAnalysis] = valueAnalysis
}

object VariableAnalysis {

  val VariableQNames =
    Set(
      XXFORMS_VARIABLE_QNAME,
      XXFORMS_VAR_QNAME,
      XFORMS_VARIABLE_QNAME,
      XFORMS_VAR_QNAME,
      EXFORMS_VARIABLE_QNAME
    )

  val ValueOrSequenceQNames =
    Set(
      XXFORMS_VALUE_QNAME,
      XXFORMS_SEQUENCE_QNAME
    )

  def valueOrSelectAttribute(element: Element): Option[String] =
    element.attributeValueOpt(VALUE_QNAME) orElse element.attributeValueOpt(SELECT_QNAME)

  def valueOrSelectAttribute(analysis: ElementAnalysis): Option[String] =
    analysis.value orElse analysis.attributeValueOpt(SELECT_QNAME)

  def valueOrSequenceElement(element: Element): Option[Element] =
    element.elementOpt(XXFORMS_VALUE_QNAME) orElse element.elementOpt(XXFORMS_SEQUENCE_QNAME)

  def isValueOrSequenceElement(element: Element): Boolean =
    element.getQName == XXFORMS_VALUE_QNAME || element.getQName == XXFORMS_SEQUENCE_QNAME

  // See https://github.com/orbeon/orbeon-forms/issues/1104 and https://github.com/orbeon/orbeon-forms/issues/1132
  def variableScopesModelVariables(v: VariableAnalysisTrait): Boolean =
    v.isInstanceOf[ViewTrait] || v.model != (v.parent flatMap (_.model))
}

trait VariableValueTrait extends ElementAnalysis {

  thisVariableValue =>

  private def parentUnsafe: ElementAnalysis = parent.getOrElse(throw new IllegalStateException)

  // If in same scope as `xf:var`, in-scope variables are the same as `xf:var` because we don't
  // want the variable defined by `xf:var` to be in-scope for `xxf:value`. Otherwise, use
  // default algorithm.
  override lazy val inScopeVariables: Map[String, VariableTrait] =
    if (parentUnsafe.scope == thisVariableValue.scope)
      parentUnsafe.inScopeVariables
    else
      getRootVariables ++ thisVariableValue.treeInScopeVariables

  override protected def getRootVariables: Map[String, VariableAnalysisTrait] = parentUnsafe match {
    case _: ViewTrait =>
      // In the view, in-scope model variables are always first in scope
      // NOTE: This is duplicated in `ViewTrait`.
      thisVariableValue.model match {
        case Some(model) => model.variablesMap
        case None        => Map.empty
      }
    case _ => Map.empty
  }
}
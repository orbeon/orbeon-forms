package org.orbeon.oxf.xforms.analysis.controls

import org.orbeon.dom.Element
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.analysis.controls.VariableAnalysis.isValueOrSequenceElement
import org.orbeon.xforms.XFormsId
import org.orbeon.xforms.XFormsNames._


trait VariableTrait {
  def name: String
  def variableAnalysis: Option[XPathAnalysis]
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
  val expressionStringOpt: Option[String]

  // Used below and by `ElementAnalysisTreeXPathAnalyzer`
  def nestedValueAnalysis: Option[ElementAnalysis] = children.find(e => isValueOrSequenceElement(e.element))

  // `lazy` because the children are evaluated after the container
  // All used by `Variable`
  lazy val (valueElement, hasNestedValue, valueScope, valueNamespaceMapping, valueStaticId) =
    nestedValueAnalysis match {
      case Some(valueElem) =>
        (valueElem.element, true, valueElem.scope, valueElem.namespaceMapping, valueElem.staticId)
      case None =>
        (variableSelf.element, false, variableSelf.scope, variableSelf.namespaceMapping, variableSelf.staticId)
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
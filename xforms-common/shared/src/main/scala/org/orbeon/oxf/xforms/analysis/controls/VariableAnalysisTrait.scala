package org.orbeon.oxf.xforms.analysis.controls

import org.orbeon.dom.Element
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.xforms.analysis._
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

  // Variable name and value
  val name: String =
    variableSelf.element.attributeValueOpt(NAME_QNAME) getOrElse
      (
        throw new ValidationException(
          s"`${element.getQualifiedName}` element must have a `name` attribute",
          ElementAnalysis.createLocationData(element)
        )
      )

  val valueElement: Element = VariableAnalysis.valueOrSequenceElement(variableSelf.element) getOrElse variableSelf.element
  val expressionStringOpt: Option[String] = VariableAnalysis.valueOrSelectAttribute(valueElement)

  def nestedValueAnalysis: Option[ElementAnalysis] = children.find(_.localName == "value")

  // `lazy` because the children are evaluated after the container
  lazy val (hasNestedValue, valueScope, valueNamespaceMapping, valueStaticId) =
    nestedValueAnalysis match {
      case Some(valueElem) =>
        (true, valueElem.scope, valueElem.namespaceMapping, valueElem.staticId)
      case None =>
        (false, variableSelf.scope, variableSelf.namespaceMapping, variableSelf.staticId)
    }

  def variableAnalysis: Option[XPathAnalysis] = valueAnalysis
}

object VariableAnalysis {

  val ValueOrSequenceQNames = Set(XXFORMS_VALUE_QNAME, XXFORMS_SEQUENCE_QNAME)

  def valueOrSelectAttribute(element: Element): Option[String] =
    element.attributeValueOpt(VALUE_QNAME) orElse element.attributeValueOpt(SELECT_QNAME)

  def valueOrSequenceElement(element: Element): Option[Element] =
    element.elementOpt(XXFORMS_VALUE_QNAME) orElse element.elementOpt(XXFORMS_SEQUENCE_QNAME)

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
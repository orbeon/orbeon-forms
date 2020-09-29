/**
 *  Copyright (C) 2010 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.analysis

import org.orbeon.dom.Element
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.xforms.analysis.controls.ViewTrait
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.xforms.XFormsId
import org.orbeon.xforms.XFormsNames._

/**
 * Trait representing a variable element, whether in the model or in the view.
 */
trait VariableAnalysisTrait extends SimpleElementAnalysis with VariableTrait {

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

  val valueElement         : Element          = VariableAnalysis.valueOrSequenceElement(variableSelf.element) getOrElse variableSelf.element
  val expressionStringOpt  : Option[String]   = VariableAnalysis.valueOrSelectAttribute(valueElement)

  val (hasNestedValue, valueScope, valueNamespaceMapping, valueStaticId) =
    VariableAnalysis.valueOrSequenceElement(variableSelf.element) match {
      case Some(valueElem) =>
        val valueElemStaticId   = valueElem.idOrNull
        val valueElemPrefixedId = XFormsId.getRelatedEffectiveId(variableSelf.prefixedId, valueElemStaticId)
        val valueElemNamespaces = part.metadata.getNamespaceMapping(valueElemPrefixedId).orNull
        val valueElemScope      = part.scopeForPrefixedId(valueElemPrefixedId) // xxx require that `mapScopeIds` has taken place

        (true, valueElemScope, valueElemNamespaces, valueElemStaticId)
      case None =>
        (false, variableSelf.scope, variableSelf.namespaceMapping, variableSelf.staticId)
    }

  def variableAnalysis: Option[XPathAnalysis] = getValueAnalysis
}

object VariableAnalysis {

  def valueOrSelectAttribute(element: Element): Option[String] =
    element.attributeValueOpt(VALUE_QNAME) orElse element.attributeValueOpt(SELECT_QNAME)

  def valueOrSequenceElement(element: Element): Option[Element] =
    element.elementOpt(XXFORMS_VALUE_QNAME) orElse element.elementOpt(XXFORMS_SEQUENCE_QNAME)

  // See https://github.com/orbeon/orbeon-forms/issues/1104 and https://github.com/orbeon/orbeon-forms/issues/1132
  def variableScopesModelVariables(v: VariableAnalysisTrait): Boolean =
    v.isInstanceOf[ViewTrait] || v.model != (v.parent flatMap (_.model))
}
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
package org.orbeon.oxf.xforms.analysis.controls

import org.orbeon.dom.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.analysis.*
import org.orbeon.oxf.xforms.event.XFormsEvents.{XFORMS_FOCUS, XXFORMS_BLUR}
import org.orbeon.xforms.XFormsNames.*
import org.orbeon.xforms.analysis.model.ValidationLevel
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping


class LHHAAnalysis(
  index                         : Int,
  element                       : Element,
  parent                        : Option[ElementAnalysis],
  preceding                     : Option[ElementAnalysis],
  staticId                      : String,
  prefixedId                    : String,
  namespaceMapping              : NamespaceMapping,
  scope                         : Scope,
  containerScope                : Scope,
  val expressionOrConstant      : Either[String, String],
  val isPlaceholder             : Boolean,
  val containsHTML              : Boolean,
  val hasLocalMinimalAppearance : Boolean,
  val hasLocalFullAppearance    : Boolean,
  val hasLocalLeftAppearance    : Boolean
) extends ElementAnalysis(index, element, parent, preceding, staticId,  prefixedId,  namespaceMapping,  scope,  containerScope)
   with OptionalSingleNode
   with ViewTrait
   with AppearanceTrait
   with WithExpressionOrConstantTrait {

  self =>

  import LHHAAnalysis._

  require(parent.isDefined)

  def getParent: ElementAnalysis = parent.get // TODO: rename `parent` to `parentOpt`, and this `def` to `parent`

  def lhhaType: LHHA = LHHA.withNameOption(localName) getOrElse LHHA.Label // FIXME: Because `SelectionControlTrait` calls this for `value`!

  val defaultToHTML: Boolean = LHHAAnalysis.isHTML(element) // IIUC: starting point for nested `<xf:output>`.

  // Updated in `attachToControl()`
  var lhhaPlacementType: LhhaPlacementType = _

  def isLocal: Boolean = lhhaPlacementType match {
    case LhhaPlacementType.Local(_, _) => true
    case _ => false
  }

  def isForRepeat: Boolean = lhhaPlacementType match {
    case LhhaPlacementType.External(_, _, Some(_)) => true
    case _ => false
  }

  // Allow the client telling us that an external LHHA has the focus, for instance in the case of an `<xf:help>`
  // rendered as a `<button>` in the headings of a repeated grid.
  override protected def externalEventsDef: Set[String] = super.externalEventsDef ++ Set(XFORMS_FOCUS, XXFORMS_BLUR)

  // What we support for alert level/validation:
  //
  // - <xf:alert>                                  -> alert applies to all alert levels
  // - <xf:alert level="foo">                      -> same, unknown level is ignored [SHOULD WARN]
  // - <xf:alert level="warning info">             -> alert only applies to warning and info levels
  // - <xf:alert level="warning" validation="">    -> same, blank attribute is same as missing attribute [SHOULD WARN]
  // - <xf:alert validation="c1 c2">               -> alert only applies if either validation c1 or c2 fails
  // - <xf:alert level="" validation="c1 c2">      -> same, blank attribute is same as missing attribute [SHOULD WARN]
  // - <xf:alert level="error" validation="c1 c2"> -> same, level is ignored when a validation is present [SHOULD WARN]

  val forValidations: Set[String] =
    if (localName == "alert")
      gatherAlertValidations(element.attributeValueOpt(VALIDATION_QNAME))
    else
      Set.empty

  val forLevels: Set[ValidationLevel] =
    if (localName == "alert")
      gatherAlertLevels(element.attributeValueOpt(LEVEL_QNAME))
    else
      Set.empty

  def debugOut(): Unit =
    expressionOrConstant match {
      case Left(expr)      => println(s"expression value for control `$prefixedId` => `$expr`")
      case Right(constant) => println(s"static value for control `$prefixedId` => `$constant`")
    }
}

object LHHAAnalysis {

  def isHTML     (e: Element): Boolean = e.attributeValue(MEDIATYPE_QNAME) == "text/html"
  def isPlainText(e: Element): Boolean = e.attributeValue(MEDIATYPE_QNAME) == "text/plain"

  def gatherAlertValidations(validationAtt: Option[String]): Set[String] =
    stringOptionToSet(validationAtt)

  def gatherAlertLevels(levelAtt: Option[String]): Set[ValidationLevel] =
    stringOptionToSet(levelAtt) collect ValidationLevel.LevelByName
}
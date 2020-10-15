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

import org.orbeon.dom._
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{XPath, XPathCache}
import org.orbeon.oxf.xforms.XFormsProperties._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.analysis.model.ValidationLevel
import org.orbeon.xforms.xbl.Scope

import scala.annotation.tailrec

class LHHAAnalysis(
  part      : PartAnalysisImpl,
  index     : Int,
  element   : Element,
  parent    : Option[ElementAnalysis],
  preceding : Option[ElementAnalysis],
  scope     : Scope
) extends ElementAnalysis(part, index, element, parent, preceding, scope)
   with OptionalSingleNode
   with AppearanceTrait {

  self =>

  import LHHAAnalysis._

  require(parent.isDefined)

  def getParent: ElementAnalysis = parent.get // TODO: rename `parent` to `parentOpt`, and this `def` to `parent`

  def lhhaType: LHHA = LHHA.withNameOption(localName) getOrElse LHHA.Label // FIXME: Because `SelectionControlTrait` calls this for `value`!

  val forStaticIdOpt: Option[String] = element.attributeValueOpt(FOR_QNAME)
  val isLocal       : Boolean        = forStaticIdOpt.isEmpty
  val defaultToHTML : Boolean        = LHHAAnalysis.isHTML(element) // IIUC: starting point for nested `<xf:output>`.
  val containsHTML  : Boolean        = LHHAAnalysis.containsHTML(element)

  // Updated in `attachToControl()`
  private var _isForRepeat                          : Boolean                                 = false
  private var _forRepeatNesting                     : Int                                     = 0
  private var _directTargetControlOpt               : Option[StaticLHHASupport]               = None
  private var _effectiveTargetControlOrPrefixedIdOpt: Option[StaticLHHASupport Either String] = None

  def isForRepeat                          : Boolean           = _isForRepeat
  def forRepeatNesting                     : Int               = _forRepeatNesting
  def directTargetControl                  : StaticLHHASupport = _directTargetControlOpt getOrElse (throw new IllegalStateException)
  def effectiveTargetControlOrPrefixedIdOpt: Option[Either[StaticLHHASupport, String]] = _effectiveTargetControlOrPrefixedIdOpt

  val hasLocalMinimalAppearance: Boolean = appearances(XFORMS_MINIMAL_APPEARANCE_QNAME) || appearances(XXFORMS_PLACEHOLDER_APPEARANCE_QNAME)
  val hasLocalFullAppearance   : Boolean = appearances(XFORMS_FULL_APPEARANCE_QNAME)
  val hasLocalLeftAppearance   : Boolean = appearances(XXFORMS_LEFT_APPEARANCE_QNAME)

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

  // Placeholder is only supported for label or hint. This in fact only makes sense for a limited set
  // of controls, namely text fields or text areas at this point.
  val isPlaceholder: Boolean =
    lhhaType match {
      case LHHA.Label | LHHA.Hint =>
        hasLocalMinimalAppearance || (
          ! hasLocalFullAppearance &&
            part.staticState.staticStringProperty(
              if (lhhaType == LHHA.Hint) HintAppearanceProperty else LabelAppearanceProperty
            )
          .tokenizeToSet.contains(XFORMS_MINIMAL_APPEARANCE_QNAME.localName)
        )
      case _ => false
    }

  // Attach this LHHA to its target control if any
  def attachToControl(): Unit = {

    val (targetControl, effectiveTargetControlOrPrefixedIdOpt) = {

      def searchLHHAControlInScope(scope: Scope, forStaticId: String): Option[StaticLHHASupport] =
        part.findControlAnalysis(scope.prefixedIdForStaticId(forStaticId)) collect { case e: StaticLHHASupport => e}

      @tailrec
      def searchXblLabelFor(e: StaticLHHASupport): Option[StaticLHHASupport Either String] =
        e match {
          case xbl: ComponentControl =>
            xbl.commonBinding.labelFor match {
              case Some(nestedLabelForStaticId) =>
                searchLHHAControlInScope(xbl.bindingOrThrow.innerScope, nestedLabelForStaticId) match {
                  case Some(nestedLabelForTarget) => searchXblLabelFor(nestedLabelForTarget) // recurse
                  case None                       => Some(Right(xbl.bindingOrThrow.innerScope.fullPrefix + nestedLabelForStaticId)) // assuming id of an HTML element
                }
              case None =>
                Some(Left(xbl))
            }
          case _ =>
            Some(Left(e))
        }

      def initialElemFromForOpt =
        forStaticIdOpt map  { forStaticId =>
          searchLHHAControlInScope(scope, forStaticId) getOrElse (
            throw new ValidationException(
              s"`for` attribute with value `$forStaticId` doesn't point to a control supporting label, help, hint or alert.",
              ElementAnalysis.createLocationData(element)
            )
          )
        }

      val initialElem = initialElemFromForOpt getOrElse {
        getParent match {
          case e: StaticLHHASupport => e
          case _ =>
            throw new ValidationException(
              s"parent control must support label, help, hint or alert.",
              ElementAnalysis.createLocationData(element)
            )
        }
      }

      (initialElem, searchXblLabelFor(initialElem))
    }

    // NOTE: We don't support a reference to an effective control within an XBL which is in a repeat nested within the XBL!
    val repeatNesting = targetControl.ancestorRepeats.size - self.ancestorRepeats.size

    self._isForRepeat                           = ! self.isLocal && repeatNesting > 0
    self._forRepeatNesting                      = if (self._isForRepeat && repeatNesting > 0) repeatNesting else 0
    self._directTargetControlOpt                = Some(targetControl)
    self._effectiveTargetControlOrPrefixedIdOpt = effectiveTargetControlOrPrefixedIdOpt

    // We attach the LHHA to one, and possibly two target controls
    targetControl.attachLHHA(self)
    effectiveTargetControlOrPrefixedIdOpt foreach {
      _.left.toOption filter (_ ne targetControl) foreach (_.attachLHHABy(self))
    }
  }

  // TODO: make use of static value
  //
  // - output static value in HTML markup
  // - if has static value, don't attempt to compare values upon diff, and never send new related information to client
  // - 2017-10-17: Now using this in `XFormsLHHAControl`.
  //
  // TODO: figure out whether to allow HTML or not (could default to true?)
  //
  val staticValue: Option[String] =
    LHHAAnalysis.hasStaticValue(element) option
      XFormsElementValue.getStaticChildElementValue(containerScope.fullPrefix, element, acceptHTML = true, null)

  def debugOut(): Unit =
    if (staticValue.isDefined)
      println("static value for control " + prefixedId + " => " + staticValue.get)
}

object LHHAAnalysis {

  def isHTML     (e: Element): Boolean = e.attributeValue(MEDIATYPE_QNAME) == "text/html"
  def isPlainText(e: Element): Boolean = e.attributeValue(MEDIATYPE_QNAME) == "text/plain"

  private def containsHTML(lhhaElement: Element) = {

    val lhhaElem =
      new DocumentWrapper(
          lhhaElement.getDocument,
          null,
          XPath.GlobalConfiguration
        ).wrap(lhhaElement)

    val XFOutput = URIQualifiedName(XFORMS_NAMESPACE_URI, "output")

    val descendantOtherElems = lhhaElem descendant * filter (_.uriQualifiedName != XFOutput)
    val descendantOutputs    = lhhaElem descendant XFOutput

    isHTML(lhhaElement) || descendantOtherElems.nonEmpty || (descendantOutputs exists {
      _.attValueOpt("mediatype") contains "text/html"
    })
  }

  // Try to figure out if we have a dynamic LHHA element, including nested xf:output and AVTs.
  def hasStaticValue(lhhaElement: Element): Boolean = {

    val SearchExpression =
      """
        not(
          exists(
            descendant-or-self::*[@ref or @nodeset or @bind or @value] |
            descendant::*[@*[contains(., '{')]]
          )
        )
      """

    XPathCache.evaluateSingle(
      contextItem        = new DocumentWrapper(
        lhhaElement.getDocument,
        null,
        XPath.GlobalConfiguration
      ).wrap(lhhaElement),
      xpathString        = SearchExpression,
      namespaceMapping   = XFormsStaticStateImpl.BASIC_NAMESPACE_MAPPING,
      variableToValueMap = null,
      functionLibrary    = null,
      functionContext    = null,
      baseURI            = null,
      locationData       = ElementAnalysis.createLocationData(lhhaElement),
      reporter           = null
    ).asInstanceOf[Boolean]
  }

  def gatherAlertValidations(validationAtt: Option[String]): Set[String] =
    stringOptionToSet(validationAtt)

  def gatherAlertLevels(levelAtt: Option[String]): Set[ValidationLevel] =
    stringOptionToSet(levelAtt) collect ValidationLevel.LevelByName
}
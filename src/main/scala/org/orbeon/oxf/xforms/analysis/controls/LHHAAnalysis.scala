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
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{XPath, XPathCache}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsProperties._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.analysis.model.ValidationLevels._
import org.orbeon.oxf.xforms.xbl.Scope
import org.orbeon.oxf.xml.Dom4j
import org.orbeon.oxf.xml.dom4j.Dom4jUtils

class LHHAAnalysis(
  staticStateContext : StaticStateContext,
  element            : Element,
  parent             : Option[ElementAnalysis],
  preceding          : Option[ElementAnalysis],
  scope              : Scope
) extends SimpleElementAnalysis(staticStateContext, element, parent, preceding, scope)
   with AppearanceTrait {

  self ⇒

  import LHHAAnalysis._

  require(parent.isDefined)

  val forStaticIdOption = Option(element.attributeValue(FOR_QNAME))
  val isLocal           = forStaticIdOption.isEmpty
  val defaultToHTML     = LHHAAnalysis.isHTML(element)

  val hasLocalMinimalAppearance = appearances(XFORMS_MINIMAL_APPEARANCE_QNAME) || appearances(XXFORMS_PLACEHOLDER_APPEARANCE_QNAME)
  val hasLocalFullAppearance    = appearances(XFORMS_FULL_APPEARANCE_QNAME)

  // What we support for alert level/validation:
  //
  // - <xf:alert>                                  → alert applies to all alert levels
  // - <xf:alert level="foo">                      → same, unknown level is ignored [SHOULD WARN]
  // - <xf:alert level="warning info">             → alert only applies to warning and info levels
  // - <xf:alert level="warning" validation="">    → same, blank attribute is same as missing attribute [SHOULD WARN]
  // - <xf:alert validation="c1 c2">               → alert only applies if either validation c1 or c2 fails
  // - <xf:alert level="" validation="c1 c2">      → same, blank attribute is same as missing attribute [SHOULD WARN]
  // - <xf:alert level="error" validation="c1 c2"> → same, level is ignored when a validation is present [SHOULD WARN]

  val forValidations =
    if (localName == "alert")
      gatherAlertValidations(Option(element.attributeValue(VALIDATION_QNAME)))
    else
      Set.empty[String]

  val forLevels =
    if (localName == "alert")
      gatherAlertLevels(Option(element.attributeValue(LEVEL_QNAME)))
    else
      Set.empty[ValidationLevel]

  // Find the target control if any
  def targetControl = (
    forStaticIdOption
    map (forStaticId ⇒ part.getControlAnalysis(scope.prefixedIdForStaticId(forStaticId)))
    orElse parent
    collect { case lhhaControl: StaticLHHASupport ⇒ lhhaControl }
  )

  // Attach this LHHA to its target control if any
  def attachToControl() = targetControl match {
    case Some(lhhaControl) ⇒
      lhhaControl.attachLHHA(self)
    case None if ! isLocal ⇒
      part.getIndentedLogger.logWarning("", "cannot attach external LHHA to control",
        Array("type", localName, "element", Dom4jUtils.elementToDebugString(element)): _*)
    case None ⇒
  }

  // TODO: make use of static value
  //
  // - output static value in HTML markup and repeat templates
  // - if has static value, don't attempt to compare values upon diff, and never send new related information to client
  val (staticValue, containsHTML) =
    if (LHHAAnalysis.hasStaticValue(staticStateContext, element)) {
      // TODO: figure out whether to allow HTML or not (could default to true?)
      val containsHTML = Array(false)
      (Option(XFormsUtils.getStaticChildElementValue(containerScope.fullPrefix, element, true, containsHTML)), containsHTML(0))
    } else
      (None, false)

  def debugOut(): Unit =
    if (staticValue.isDefined)
      println("static value for control " + prefixedId + " ⇒ " + staticValue.get)

  // Consider that LHHA don't have context/binding as we delegate implementation in computeValueAnalysis
  override protected def computeContextAnalysis = None
  override protected def computeBindingAnalysis = None

  override protected def computeValueAnalysis = {
    if (staticValue.isEmpty) {
      // Value is likely not static

      // Delegate to concrete implementation
      val delegateAnalysis =
        new SimpleElementAnalysis(staticStateContext, element, parent, preceding, scope)
          with ValueTrait with OptionalSingleNode with ViewTrait

      delegateAnalysis.analyzeXPath()

      if (ref.isDefined || value.isDefined) {
        // 1. E.g. <xf:label model="…" context="…" value|ref="…"/>
        assert(element.elements.isEmpty) // no children elements allowed in this case

        // Use value provided by the delegate
        delegateAnalysis.getValueAnalysis
      } else {
        // 2. E.g. <xf:label>…<xf:output value|ref=""…/>…<span class="{…}">…</span></xf:label>

        // NOTE: We do allow @context and/or @model on LHHA element, which can change the context

        // The subtree can only contain HTML elements interspersed with xf:output. HTML elements may have AVTs.
        var combinedAnalysis: XPathAnalysis = StringAnalysis()

        Dom4jUtils.visitSubtree(element, new Dom4jUtils.VisitorListener {
          val hostLanguageAVTs = XFormsProperties.isHostLanguageAVTs
          def startElement(element: Element): Unit = {
            if (element.getQName == XFORMS_OUTPUT_QNAME) {
              // Add dependencies
              val outputAnalysis =
                new SimpleElementAnalysis(
                  staticStateContext = staticStateContext,
                  element            = element,
                  parent             = Some(delegateAnalysis),
                  preceding          = None,
                  scope              = delegateAnalysis.getChildElementScope(element)
                ) with ValueTrait with OptionalSingleNode with ViewTrait
              outputAnalysis.analyzeXPath()
              if (outputAnalysis.getValueAnalysis.isDefined)
                combinedAnalysis = combinedAnalysis combine outputAnalysis.getValueAnalysis.get
            } else if (hostLanguageAVTs) {
              for {
                attribute ← Dom4j.attributes(element)
                attributeValue = attribute.getValue
                if XFormsUtils.maybeAVT(attributeValue)
              } combinedAnalysis = NegativeAnalysis(attributeValue) // not supported just yet
            }
          }

          def endElement(element: Element) = ()
          def text(text: Text) = ()
        })

        // Result of all combined analyses
        Some(combinedAnalysis)
      }
    } else
      // Value of LHHA is 100% static and analysis is constant
      Some(StringAnalysis())
  }
}

object LHHAAnalysis {

  def isHTML(e: Element)      = e.attributeValue(MEDIATYPE_QNAME) == "text/html"
  def isPlainText(e: Element) = e.attributeValue(MEDIATYPE_QNAME) == "text/plain"

  // Try to figure out if we have a dynamic LHHA element, including nested xf:output and AVTs.
  private def hasStaticValue(staticStateContext: StaticStateContext, lhhaElement: Element): Boolean = {

    val SearchExpression =
      """
        not(
        exists(
          descendant-or-self::xf:*[@ref or @nodeset or @bind or @value] |
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

  // Whether the control has a placeholder for the given LHHA type
  def hasLHHAPlaceholder(elementAnalysis: ElementAnalysis, lhhaType: String) =
    elementAnalysis match {
      case lhhaTrait: StaticLHHASupport ⇒
        val labelOrHintOpt = lhhaTrait.lhh(lhhaType)
        (labelOrHintOpt exists (_.hasLocalMinimalAppearance)) || (
          ! (labelOrHintOpt exists (_.hasLocalFullAppearance)) &&
          stringToSet(
            elementAnalysis.part.staticState.staticStringProperty(
              if (lhhaType == "hint") HINT_APPEARANCE_PROPERTY else LABEL_APPEARANCE_PROPERTY
            )
          )(XFORMS_MINIMAL_APPEARANCE_QNAME.getName)
        )
      case _ ⇒
        false
    }

  def gatherAlertValidations(validationAtt: Option[String]) =
    stringOptionToSet(validationAtt)

  def gatherAlertLevels(levelAtt: Option[String]) =
    stringOptionToSet(levelAtt) collect LevelByName
}
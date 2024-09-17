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
package org.orbeon.oxf.xforms.control.controls

import org.orbeon.dom.Element
import org.orbeon.oxf.externalcontext.UrlRewriteMode
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.analysis.controls.AttributeControl
import org.orbeon.oxf.xforms.control.*
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.XMLReceiverHelper
import org.orbeon.oxf.xml.XMLReceiverHelper.*
import org.orbeon.xforms.Constants.DUMMY_IMAGE_URI
import org.orbeon.xforms.XFormsNames.*
import org.orbeon.xforms.{XFormsCrossPlatformSupport, XFormsId}
import org.xml.sax.helpers.AttributesImpl


class XXFormsAttributeControl(
  container   : XBLContainer,
  parent      : XFormsControl,
  element     : Element,
  _effectiveId: String
) extends XFormsSingleNodeControl(
  container,
  parent,
  element,
  _effectiveId
) with XFormsValueControl {

  import XXFormsAttributeControl._

  //override type Control <: AttributeControl

  private val attributeControl = staticControl.asInstanceOf[AttributeControl]
  private var attributeName    = if (attributeControl ne null) attributeControl.attributeName  else null
  private var attributeValue   = if (attributeControl ne null) attributeControl.attributeValue else null
  private var forName          = if (attributeControl ne null) attributeControl.forName        else null

  /**
   * Special constructor used for label, etc. content AVT handling.
   *
   * @param container             container
   * @param element               control element (should not be used here)
   * @param attributeName         name of the attribute
   * @param avtExpression         attribute template expression
   * @param forName               name of the element the attribute is on
   */
  def this(container: XBLContainer, element: Element, attributeName: String, avtExpression: String, forName: String) = {
    this(container, null, element, null)
    this.attributeName  = attributeName
    this.attributeValue = avtExpression
    this.forName        = forName
  }

  // Value comes from the AVT value attribute
  override def computeValue(collector: ErrorEventCollector): String =
    Option(evaluateAvt(attributeValue, collector)) getOrElse ""

  protected override def getRelevantEscapedExternalValue(collector: ErrorEventCollector): String = {
    // Rewrite URI attribute if needed
    val externalValue = getExternalValue(collector)
    attributeName match {
      case "src" =>
        XFormsCrossPlatformSupport.resolveResourceURL(containingDocument, element, externalValue, UrlRewriteMode.AbsolutePathOrRelative)
      case "href" if ! externalValue.startsWith("#") =>
        // NOTE: Keep value unchanged if it's just a fragment (see also XFormsLoadAction)
        attributeControl.urlType match {
          case "action"   => XFormsCrossPlatformSupport.resolveActionURL  (containingDocument, element, externalValue)
          case "resource" => XFormsCrossPlatformSupport.resolveResourceURL(containingDocument, element, externalValue, UrlRewriteMode.AbsolutePathOrRelative)
          case _          => XFormsCrossPlatformSupport.resolveRenderURL  (containingDocument, element, externalValue, skipRewrite = false) // default is "render"
        }
      case _ => externalValue
    }

  }

  override def evaluateExternalValue(collector: ErrorEventCollector): Unit =
    setExternalValue(getExternalValueHandleSrc(getValue(collector), attributeName, forName))

  def getAttributeName: String = attributeName

  def getEffectiveForAttribute: String =
    XFormsId.getRelatedEffectiveId(effectiveId, attributeControl.forStaticId)

  override def getNonRelevantEscapedExternalValue: String =
    attributeName match {
      case "src" if forName == "img" =>
        // Return rewritten URL of dummy image URL
        XFormsCrossPlatformSupport.resolveResourceURL(containingDocument, element, DUMMY_IMAGE_URI, UrlRewriteMode.AbsolutePath)
      case "src" if forName == "script" =>
        DUMMY_SCRIPT_URI
      case _ =>
        super.getNonRelevantEscapedExternalValue
    }


  final override def outputAjaxDiffUseClientValue(
    previousValue   : Option[String],
    previousControl : Option[XFormsValueControl],
    content         : Option[XMLReceiverHelper => Unit],
    collector       : ErrorEventCollector
  )(implicit
    ch              : XMLReceiverHelper
  ): Unit = {

    // If we get here, it means that `super.compareExternalUseExternalValue()` returned `false`, which means that either
    // `previousControl.isEmpty == true` or that there is a difference in value (or other aspects which don't matter here).

    import ControlAjaxSupport._

    val atts                  = new AttributesImpl
    val attributeControl2     = this

    if (attributeName != "class") {

      // The client does not store an HTML representation of the xxf:attribute control, so we
      // have to output these attributes.

      // HTML element id
      addOrAppendToAttributeIfNeeded(
        attributesImpl       = atts,
        name                 = "for",
        value                = containingDocument.namespaceId(attributeControl2.getEffectiveForAttribute),
        isNewRepeatIteration = false, // doesn't matter because `isDefaultValue == false`
        isDefaultValue       = false
      )

      // Attribute name
      // NOTE: This won't change for a given concrete control instance.
      addOrAppendToAttributeIfNeeded(
        attributesImpl       = atts,
        name                 = "name",
        value                = attributeControl2.getAttributeName,
        isNewRepeatIteration = false, // doesn't matter because `isDefaultValue == false`
        isDefaultValue       = false
      )

      outputValueElement(
        attributesImpl = atts,
        elementName    = "attribute",
        value          = getEscapedExternalValue(collector)
      )
    } else {
      // Handle class separately

      // Just output the class diffs if any
      // See https://github.com/orbeon/orbeon-forms/issues/889
      //val doOutputElement = AjaxSupport.addAjaxClasses(attributesImpl, newlyVisibleSubtree, other, attributeControl2)

      val isNewlyVisibleSubtree = previousControl.isEmpty

      // The classes are stored as the control's value
      val classes1 = previousControl flatMap (control => Option(control.getEscapedExternalValue(collector))) getOrElse ""
      val classes2 = Option(attributeControl2.getEscapedExternalValue(collector)) getOrElse ""

      if (isNewlyVisibleSubtree || classes1 != classes2) {
        val attributeValue = diffClasses(classes1, classes2)
        val doOutputElement = addOrAppendToAttributeIfNeeded(atts, "class", attributeValue, isNewlyVisibleSubtree, attributeValue == "")

        if (doOutputElement) {
          // Pass the HTML element id, not the control id, because that's what the client will expect
          val effectiveFor2 = attributeControl2.getEffectiveForAttribute
          atts.addAttribute("", "id", "id", CDATA, containingDocument.namespaceId(effectiveFor2))

          ch.element("xxf", XXFORMS_NAMESPACE_URI, "control", atts)
        }
      }
    }
  }

  override def supportFullAjaxUpdates = false
}

object XXFormsAttributeControl {

  private def getExternalValueHandleSrc(controlValue: String, attributeName: String, forName: String): String =
    if (controlValue.isAllBlank) {
      attributeName match {
        case "src" if forName == "img"    => DUMMY_IMAGE_URI
        case "src" if forName == "script" => DUMMY_SCRIPT_URI // IE8 ignores it; IE9+ loads JS properly
        case _                            => ""
      }
    } else {
      controlValue
    }

  def getExternalValueHandleSrc(
    concreteControl : XXFormsAttributeControl,
    attributeControl: AttributeControl,
    collector       : ErrorEventCollector
  ): String =
    getExternalValueHandleSrc(
      Option(concreteControl).flatMap(_.valueOpt(collector)).orNull,
      attributeControl.attributeName,
      attributeControl.forName
    )
}
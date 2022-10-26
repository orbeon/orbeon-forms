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
package org.orbeon.oxf.xforms.processor.handlers.xhtml

import cats.syntax.option._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis
import org.orbeon.oxf.xforms.control.controls.{PlaceHolderInfo, XFormsInputControl}
import org.orbeon.oxf.xforms.itemset.{Item, Itemset, LHHAValue}
import org.orbeon.oxf.xforms.processor.handlers.HandlerContext
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler._
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XFormsBaseHandlerXHTML._
import org.orbeon.oxf.xml.SaxSupport._
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.oxf.xml.XMLReceiverSupport._
import org.orbeon.oxf.xml.{XMLReceiver, XMLUtils}
import org.orbeon.xforms.Constants.ComponentSeparator
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.{XFormsId, XFormsNames}
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl


/**
 * Handle xf:input.
 *
 * TODO: Subclasses per appearance.
 */
class XFormsInputHandler(
  uri             : String,
  localname       : String,
  qName           : String,
  localAtts       : Attributes,
  elementAnalysis : ElementAnalysis,
  handlerContext  : HandlerContext
) extends
  XFormsControlLifecycleHandler(
    uri,
    localname,
    qName,
    localAtts,
    elementAnalysis,
    handlerContext,
    repeating  = false,
    forwarding = false
  ) {

  private lazy val placeHolderInfo: Option[PlaceHolderInfo] =
    PlaceHolderInfo.placeHolderValueOpt(elementAnalysis, currentControl)

  private def controlHas(predicate: XFormsInputControl => Boolean) =
    predicate(currentControl.asInstanceOf[XFormsInputControl])

  private def isBoolean     = controlHas(c => c.getBuiltinTypeName == "boolean")

  override protected def handleControlStart(): Unit = {

    implicit val xmlReceiver: XMLReceiver = handlerContext.controller.output

    val inputControl = currentControl.asInstanceOf[XFormsInputControl]

    val isRelevantControl = ! isNonRelevant(inputControl)

    if (isBoolean) {
      // Produce a boolean output

      val isMultiple = true
      val itemset = new Itemset(isMultiple, hasCopy = false)
      // NOTE: We have decided that it did not make much sense to encode the value for boolean. This also poses
      // a problem since the server does not send an itemset for new booleans, therefore the client cannot know
      // the encrypted value of "true". So we do not encrypt values.
      // NOTE: Put null label so that it is not output at all
      // encode = false,
      itemset.addChildItem(
        Item.ValueNode(
          label      = LHHAValue.Empty,
          help       = None,
          hint       = None,
          value      = Left("true"),
          attributes = Nil
        )(
          position   = 0
        )
      )

      // TODO: Do not delegate but just share `outputContent` implementation.
      new XFormsSelect1Handler(uri, localname, qName, attributes, elementAnalysis, handlerContext)
        .outputContent(
          attributes           = attributes,
          effectiveId          = getEffectiveId,
          control              = inputControl,
          itemsetOpt           = itemset.some,
          isMultiple           = isMultiple,
          isFull               = true,
          isBooleanInput       = true,
          xformsHandlerContext = handlerContext
        )
    } else {

      val xhtmlPrefix = handlerContext.findXHTMLPrefix

      // Create xh:input
      if (! isStaticReadonly(inputControl)) {
        // Regular read-write mode

        val inputQName = XMLUtils.buildQName(xhtmlPrefix, "input")

        // Main input field
        locally {
          val inputIdName = getFirstInputEffectiveIdWithNs(getEffectiveId).getOrElse(throw new IllegalStateException)
          val atts = new AttributesImpl
          atts.addOrReplace(XFormsNames.ID_QNAME, inputIdName)
          atts.addOrReplace("type", "text")
          // Use effective id for name of first field
          atts.addOrReplace("name", inputIdName)
          val inputClasses = new java.lang.StringBuilder("xforms-input-input")
          if (isRelevantControl) {
            // Output value only for concrete control
            val formattedValue = inputControl.getFirstValueUseFormat
            // Regular case, value goes to input control
            atts.addOrReplace("value", Option(formattedValue) getOrElse "")
            val firstType = inputControl.getFirstValueType
            if (firstType ne null) {
              inputClasses.append(" xforms-type-")
              inputClasses.append(firstType)
            }
            // Q: Not sure why we duplicate the appearances here. As of 2011-10-27, removing this
            // makes the minimal date picker fail on the client. We should be able to remove this.
//            elementAnalysis match {
//              case a: AppearanceTrait => a.encodeAndAppendAppearances(inputClasses)
//              case _ =>
//            }
          } else {
            atts.addOrReplace("value", "")
          }

          // Output xxf:* extension attributes
          inputControl.addExtensionAttributesExceptClassAndAcceptForHandler(atts, XXFORMS_NAMESPACE_URI)

          // Add attribute even if the control is not concrete
          placeHolderInfo foreach { placeHolderInfo =>
            if (placeHolderInfo.value ne null) // unclear whether this can ever be null
              atts.addOrReplace("placeholder", placeHolderInfo.value)
          }

          atts.addOrReplace("class", inputClasses.toString)

          forwardAccessibilityAttributes(attributes, atts)
          handleAriaByAtts(atts, XFormsLHHAHandler.coreControlLhhaByCondition)

          if (isXFormsReadonlyButNotStaticReadonly(inputControl))
            outputReadonlyAttribute(atts)
          handleAriaAttributes(inputControl.isRequired, inputControl.isValid, inputControl.visited, atts)

          xmlReceiver.startElement(XHTML_NAMESPACE_URI, "input", inputQName, atts)
          xmlReceiver.endElement(XHTML_NAMESPACE_URI, "input", inputQName)
        }
      } else {
        // Output static read-only value
        if (isRelevantControl) {
          val atts = List("class" -> "xforms-field")
          withElement("span", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = atts) {
            inputControl.getFormattedValue foreach { value =>
              xmlReceiver.characters(value.toCharArray, 0, value.length)
            }
          }
        }
      }
    }
  }

  private def getFirstInputEffectiveIdWithNs(effectiveId: String): Option[String] =
    ! isBoolean option XFormsInputHandler.firstInputEffectiveIdWithNs(effectiveId)(containingDocument)

  // xxx XFormsSelect1Handler.getItemId won't be namespaced!
  override def getForEffectiveIdWithNs(lhhaAnalysis: LHHAAnalysis): Option[String] =
    isBoolean option XFormsSelect1Handler.getItemId(getEffectiveId, 0) orElse getFirstInputEffectiveIdWithNs(getEffectiveId)

  protected override def handleLabel(lhhaAnalysis: LHHAAnalysis): Unit =
    if (! (placeHolderInfo exists (_.isLabelPlaceholder)))
      super.handleLabel(lhhaAnalysis)

  protected override def handleHint(lhhaAnalysis: LHHAAnalysis): Unit =
    if (placeHolderInfo forall (_.isLabelPlaceholder))
      super.handleHint(lhhaAnalysis)
}

object XFormsInputHandler {
  def firstInputEffectiveIdWithNs(effectiveId: String)(containingDocument: XFormsContainingDocument): String =
    containingDocument.namespaceId(XFormsId.appendToEffectiveId(effectiveId, ComponentSeparator + "xforms-input-1"))
}
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
package org.orbeon.oxf.xforms.control


import org.orbeon.xforms.XFormsNames._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.{LHHA, LHHAAnalysis, StaticLHHASupport}
import org.orbeon.oxf.xforms.control.ControlAjaxSupport._
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XFormsBaseHandlerXHTML
import org.orbeon.oxf.xml.XMLReceiverHelper.CDATA
import org.orbeon.oxf.xml.{SAXUtils, XMLReceiverHelper}
import org.orbeon.xforms.XFormsId
import org.xml.sax.helpers.AttributesImpl
import shapeless.syntax.typeable._

import scala.collection.mutable

trait ControlAjaxSupport {

  self: XFormsControl =>

  // Whether the control support Ajax updates
  def supportAjaxUpdates = true

  // Whether this control got structural changes during the current request
  final def hasStructuralChange = containingDocument.getControlsStructuralChanges.contains(prefixedId)

  // Whether the control support full Ajax updates
  def supportFullAjaxUpdates = true

  def outputAjaxDiff(
    previousControlOpt : Option[XFormsControl],
    content            : Option[XMLReceiverHelper => Unit])(implicit
    ch                 : XMLReceiverHelper
  ): Unit = ()

  def addAjaxAttributes(attributesImpl: AttributesImpl, previousControlOpt: Option[XFormsControl]) = {
    var added = false

    // Control id
    attributesImpl.addAttribute("", "id", "id", CDATA, containingDocument.namespaceId(getEffectiveId))

    // This is handled specially because it's a list of tokens, some of which can be added and removed
    added |= addAjaxClasses(attributesImpl, previousControlOpt, this)
    added |= addAjaxLHHA(attributesImpl, previousControlOpt)

    // Visited
    if ((previousControlOpt exists (_.visited)) != visited) {
      SAXUtils.addOrAppendToAttribute(attributesImpl, "visited", visited.toString)
      added |= true
    }

    // Output control-specific attributes
    added |= addAjaxExtensionAttributes(attributesImpl, previousControlOpt)

    added
  }

  // Label, help, hint, alert
  final def addAjaxLHHA(attributesImpl: AttributesImpl, previousControlOpt: Option[XFormsControl]): Boolean = {

    var added = false

    for {
      staticLhhaSupport <- staticControl.cast[StaticLHHASupport].toList // NOTE: `narrowTo` fails
      lhha              <- ajaxLhhaSupport
      // https://github.com/orbeon/orbeon-forms/issues/3836
      // Q: Could we just check `isLocal` instead of `isForRepeat`?
      if staticLhhaSupport.hasLHHANotForRepeat(lhha) || staticLhhaSupport.hasLHHAPlaceholder(lhha)
      value1            = previousControlOpt.map(_.lhhaProperty(lhha).value()).orNull
      lhha2             = self.lhhaProperty(lhha)
      value2            = lhha2.value()
      if value1 != value2
      attributeValue    = Option(lhha2.escapedValue()) getOrElse ""
    } yield
      added |= addOrAppendToAttributeIfNeeded(attributesImpl, lhha.entryName, attributeValue, previousControlOpt.isEmpty, attributeValue == "")

    added
  }

  /**
   * Add attributes differences for custom attributes.
 *
   * @return                      true if any attribute was added, false otherwise
   */
  // NOTE: overridden by XFormsOutputControl and XFormsUploadControl for FileMetadata. Could everything go through a
  // unified system of properties?
  def addAjaxExtensionAttributes(attributesImpl: AttributesImpl, previousControlOpt: Option[XFormsControl]) = {

    var added = false

    for {
      avtAttributeQName <- staticControl.extensionAttributes.keys
      if avtAttributeQName.namespace.uri == XXFORMS_NAMESPACE_URI || avtAttributeQName == ACCEPT_QNAME // only keep xxf:* attributes which are defined statically
      value1 = previousControlOpt flatMap (_.extensionAttributeValue(avtAttributeQName))
      value2 = self.extensionAttributeValue(avtAttributeQName)
      if value1 != value2
      attributeValue = value2 getOrElse ""
    } yield
      // NOTE: For now we use the local name; may want to use a full name?
      added |= addAttributeIfNeeded(attributesImpl, avtAttributeQName.localName, attributeValue, previousControlOpt.isEmpty, attributeValue == "")

    added
  }

  def writeMIPs(write: (String, String) => Unit): Unit =
    write("relevant", isRelevant.toString)

  final def writeMIPsAsAttributes(newAttributes: AttributesImpl): Unit = {
    def write(name: String, value: String) =
      newAttributes.addAttribute(XXFORMS_NAMESPACE_URI, name, XXFORMS_PREFIX + ':' + name, CDATA, value)

    writeMIPs(write)
  }
}

object ControlAjaxSupport {

  import org.orbeon.oxf.util.StringUtils._

  private def tokenize(value: String) = value.splitTo[mutable.LinkedHashSet]()

  // Diff two sets of classes
  def diffClasses(class1: String, class2: String) =
    if (class1.isEmpty)
      tokenize(class2) mkString " "
    else {
      val classes1 = tokenize(class1)
      val classes2 = tokenize(class2)

      // Classes to remove and to add
      val toRemove = classes1 -- classes2 map ('-' + _)
      val toAdd    = classes2 -- classes1 map ('+' + _)

      toRemove ++ toAdd mkString " "
    }

  // NOTE: Similar to XFormsSingleNodeControl.addAjaxCustomMIPs. Should unify handling of classes.
  def addAjaxClasses(
    attributesImpl     : AttributesImpl,
    previousControlOpt : Option[XFormsControl],
    currentControl     : XFormsControl
  ): Boolean = {

    val class1 = previousControlOpt flatMap (_.extensionAttributeValue(CLASS_QNAME)) getOrElse ""
    val class2 = currentControl.extensionAttributeValue(CLASS_QNAME) getOrElse ""

    if (previousControlOpt.isEmpty || class1 != class2) {
      val attributeValue = diffClasses(class1, class2)
      addOrAppendToAttributeIfNeeded(attributesImpl, "class", attributeValue, previousControlOpt.isEmpty, attributeValue == "")
    } else {
      false
    }
  }

  def addOrAppendToAttributeIfNeeded(
    attributesImpl       : AttributesImpl,
    name                 : String,
    value                : String,
    isNewRepeatIteration : Boolean,
    isDefaultValue       : Boolean
  ): Boolean =
    if (isNewRepeatIteration && isDefaultValue) {
      false
    } else {
      SAXUtils.addOrAppendToAttribute(attributesImpl, name, value)
      true
    }

  def addAttributeIfNeeded(
    attributesImpl       : AttributesImpl,
    name                 : String,
    value                : String,
    isNewRepeatIteration : Boolean,
    isDefaultValue       : Boolean
  ): Boolean =
    if (isNewRepeatIteration && isDefaultValue) {
      false
    } else {
      addAttribute(attributesImpl, name, value)
      true
    }

  def addAttribute(
    attributesImpl       : AttributesImpl,
    name                 : String,
    value                : String
  ): Unit =
    attributesImpl.addAttribute("", name, name, CDATA, value)

  // Output an `xxf:attribute` element for the given name and value extractor if the value has changed
  def outputAttributeElement(
    previousControlOpt : Option[XFormsControl],
    currentControl     : XFormsControl,
    effectiveId        : String,
    name               : String,
    value              : XFormsControl => String)(
    ch                 : XMLReceiverHelper,
    containingDocument : XFormsContainingDocument
  ): Unit = {

    val value1 = previousControlOpt   map value
    val value2 = Some(currentControl) map value

    if (value1 != value2) {
      val attributeValue = value2 getOrElse ""
      val attributesImpl = new AttributesImpl

      addAttribute(attributesImpl, "for",  containingDocument.namespaceId(effectiveId))
      addAttribute(attributesImpl, "name", name)
      ch.startElement("xxf", XXFORMS_NAMESPACE_URI, "attribute", attributesImpl)
      ch.text(attributeValue)
      ch.endElement()
    }
  }

  val AriaLabelledby  = "aria-labelledby"
  val AriaDescribedby = "aria-describedby"
  val AriaDetails     = "aria-details"

  val LhhaWithAriaAttName = List(
    LHHA.Label -> AriaLabelledby,
    LHHA.Hint  -> AriaDescribedby,
    LHHA.Help  -> AriaDetails
  )

  def findAriaBy(
    staticControl      : ElementAnalysis,
    control            : XFormsControl,
    lhha               : LHHA,
    condition          : LHHAAnalysis => Boolean)(
    containingDocument : XFormsContainingDocument
  ): Option[String] = {

    import shapeless._
    import syntax.typeable._

    for {
      staticLhhaSupport <- staticControl.narrowTo[StaticLHHASupport]
      staticLhha        <- staticLhhaSupport.lhhBy(lhha) orElse staticLhhaSupport.lhh(lhha)
      if condition(staticLhha)
    } yield
      if (staticLhha.isLocal) {
        XFormsBaseHandler.getLHHACId(containingDocument, control.effectiveId, XFormsBaseHandlerXHTML.LHHACodes(lhha))
      } else {
        val suffix    = XFormsId.getEffectiveIdSuffixParts(control.effectiveId)
        val newSuffix = suffix.take(suffix.size - staticLhha.forRepeatNesting) map (_.toString: AnyRef)

        XFormsId.buildEffectiveId(staticLhha.prefixedId, newSuffix)
      }
  }
}
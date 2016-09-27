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


import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.control.ControlAjaxSupport._
import org.orbeon.oxf.xml.XMLReceiverHelper.CDATA
import org.orbeon.oxf.xml.{SAXUtils, XMLReceiverHelper}
import org.xml.sax.helpers.AttributesImpl

import scala.collection.mutable

trait ControlAjaxSupport {

  self: XFormsControl ⇒

  // Whether the control support Ajax updates
  def supportAjaxUpdates = true

  // Whether this control got structural changes during the current request
  final def hasStructuralChange = containingDocument.getControlsStructuralChanges.contains(prefixedId)

  // Whether the control support full Ajax updates
  def supportFullAjaxUpdates = true

  def outputAjaxDiff(
    previousControlOpt : Option[XFormsControl],
    content            : Option[XMLReceiverHelper ⇒ Unit])(implicit
    ch                 : XMLReceiverHelper
  ): Unit = ()

  def addAjaxAttributes(attributesImpl: AttributesImpl, previousControlOpt: Option[XFormsControl]) = {
    var added = false

    // Control id
    attributesImpl.addAttribute("", "id", "id", CDATA, XFormsUtils.namespaceId(containingDocument, getEffectiveId))

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
  def addAjaxLHHA(attributesImpl: AttributesImpl, previousControlOpt: Option[XFormsControl]): Boolean = {

    var added = false

    for {
      lhhaType ← LHHA.values
      value1 = previousControlOpt.map(_.lhhaProperty(lhhaType).value()).orNull
      lhha2  = self.lhhaProperty(lhhaType)
      value2 = lhha2.value()
      if value1 != value2
      attributeValue = Option(lhha2.escapedValue()) getOrElse ""
    } yield
      added |= addOrAppendToAttributeIfNeeded(attributesImpl, lhhaType.name(), attributeValue, previousControlOpt.isEmpty, attributeValue == "")

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
      avtAttributeQName ← staticControl.extensionAttributes.keys
      if avtAttributeQName.getNamespaceURI == XXFORMS_NAMESPACE_URI || avtAttributeQName == ACCEPT_QNAME // only keep xxf:* attributes which are defined statically
      value1 = previousControlOpt flatMap (_.extensionAttributeValue(avtAttributeQName))
      value2 = self.extensionAttributeValue(avtAttributeQName)
      if value1 != value2
      attributeValue = value2 getOrElse ""
    } yield
      // NOTE: For now we use the local name; may want to use a full name?
      added |= addAttributeIfNeeded(attributesImpl, avtAttributeQName.getName, attributeValue, previousControlOpt.isEmpty, attributeValue == "")

    added
  }

  // Output an xxf:attribute element for the given name and value extractor if the value has changed
  final def outputAttributeElement(
    previousControlOpt : Option[XFormsControl],
    name               : String,
    value              : XFormsControl ⇒ String)(
    ch                 : XMLReceiverHelper
  ): Unit = {

    val value1 = previousControlOpt map value
    val value2 = Some(this)         map value

    if (value1 != value2) {
      val attributeValue = value2 getOrElse ""
      val attributesImpl = new AttributesImpl

      addAttributeIfNeeded(attributesImpl, "for",  XFormsUtils.namespaceId(containingDocument, effectiveId), isNewRepeatIteration = false, isDefaultValue = false)
      addAttributeIfNeeded(attributesImpl, "name", name,                                                     isNewRepeatIteration = false, isDefaultValue = false)
      ch.startElement("xxf", XXFORMS_NAMESPACE_URI, "attribute", attributesImpl)
      ch.text(attributeValue)
      ch.endElement()
    }
  }

  def writeMIPs(write: (String, String) ⇒ Unit): Unit =
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
      attributesImpl.addAttribute("", name, name, CDATA, value)
      true
    }
}
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


import org.orbeon.dom.QName
import org.orbeon.oxf.xforms.*
import org.orbeon.oxf.xforms.analysis.controls.{LHHA, LHHAAnalysis, StaticLHHASupport}
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, LhhaPlacementType}
import org.orbeon.oxf.xforms.control.ControlAjaxSupport.*
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler
import org.orbeon.oxf.xforms.processor.handlers.xhtml.{PlaceHolderInfo, XFormsBaseHandlerXHTML}
import org.orbeon.oxf.xml.SaxSupport.*
import org.orbeon.oxf.xml.XMLReceiverHelper.CDATA
import org.orbeon.oxf.xml.XMLReceiverSupport.*
import org.orbeon.oxf.xml.{XMLReceiver, XMLReceiverHelper}
import org.orbeon.xforms.XFormsNames.*
import org.orbeon.xforms.analysis.model.ValidationLevel
import org.orbeon.xforms.{XFormsId, XFormsNames}
import org.xml.sax.helpers.AttributesImpl
import shapeless.syntax.typeable.*

import scala.collection.mutable


trait ControlAjaxSupport {

  self: XFormsControl =>

  // Whether the control support Ajax updates
  def supportAjaxUpdates = true

  // Whether this control got structural changes during the current request
  final def hasStructuralChange: Boolean =
    containingDocument.getControlsStructuralChanges.contains(prefixedId)

  // Whether the control support full Ajax updates
  def supportFullAjaxUpdates = true

  def outputAjaxDiff(
    previousControlOpt : Option[XFormsControl],
    content            : Option[XMLReceiverHelper => Unit],
    collector          : ErrorEventCollector
  )(implicit
    ch                 : XMLReceiverHelper
  ): Unit = ()

  def addAjaxAttributes(
    attributesImpl    : AttributesImpl,
    previousControlOpt: Option[XFormsControl],
    collector         : ErrorEventCollector
  ): Boolean = {
    var added = false

    // Control id
    attributesImpl.addOrReplace(XFormsNames.ID_QNAME, containingDocument.namespaceId(effectiveId))

    // This is handled specially because it's a list of tokens, some of which can be added and removed
    added |= addAjaxClasses(attributesImpl, previousControlOpt, this)
    added |= addAjaxLHHA(attributesImpl, previousControlOpt, collector)

    // Visited
    if ((previousControlOpt exists (_.visited)) != visited) {
      attributesImpl.addOrReplace("visited", visited.toString)
      added |= true
    }

    // Output control-specific attributes
    added |= addAjaxExtensionAttributes(attributesImpl, previousControlOpt, collector)

    added
  }

  // Label, help, hint, alert
  private final def addAjaxLHHA(
    attributesImpl    : AttributesImpl,
    previousControlOpt: Option[XFormsControl],
    collector         : ErrorEventCollector
  ): Boolean = {

    var added = false

    for {
      staticLhhaSupport <- staticControl.cast[StaticLHHASupport].toList // NOTE: `narrowTo` fails
      lhha              <- ajaxLhhaSupport
      // https://github.com/orbeon/orbeon-forms/issues/3836
      // Also: we can have multiple `staticLhhaSupport` for `LHHA.Alert`, but only one `lhhaProperty`! Some `LHHA`s
      // could be nested, some external, but in the end we only get one value. For now (2022-06-09) the constraint
      // remains that we should either have all local alerts, or all separate.
      if staticLhhaSupport.hasLocal(lhha) || staticLhhaSupport.hasLhhaPlaceholder(lhha)
      value1            = previousControlOpt.map(_.lhhaProperty(lhha, local = true).value(collector)).orNull
      lhha2             = self.lhhaProperty(lhha, local = true)
      value2            = lhha2.value(collector)
      if value1 != value2
      attributeValue    = Option(lhha2.escapedValue(collector)) getOrElse ""
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
  def addAjaxExtensionAttributes(
    attributesImpl    : AttributesImpl,
    previousControlOpt: Option[XFormsControl],
    collector         : ErrorEventCollector
  ): Boolean = {

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

//  def writeMIPs(write: (String, String) => Unit): Unit =
//    write("relevant", isRelevant.toString)

//  final def writeMIPsAsAttributes(newAttributes: AttributesImpl): Unit = {
//    def write(name: String, value: String) =
//      newAttributes.addAttribute(XXFORMS_NAMESPACE_URI, name, XXFORMS_PREFIX + ':' + name, CDATA, value)
//
//    writeMIPs(write)
//  }
}

object ControlAjaxSupport {

  import org.orbeon.oxf.util.StringUtils.*

  private def tokenize(value: String) = value.splitTo[mutable.LinkedHashSet]()

  // Diff two sets of classes
  def diffClasses(class1: String, class2: String): String =
    if (class1.isEmpty)
      tokenize(class2) mkString " "
    else {
      val classes1 = tokenize(class1)
      val classes2 = tokenize(class2)

      // Classes to remove and to add
      val toRemove = classes1.diff(classes2).map("-" + _)
      val toAdd    = classes2.diff(classes1).map("+" + _)

      (toRemove ++ toAdd).mkString(" ")
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
      attributesImpl.addOrReplace(name, value)
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
    previousControlOpt: Option[XFormsControl],
    currentControl    : XFormsControl,
    effectiveId       : String,
    attName           : String,
    attValue          : XFormsControl => Option[String])(
    ch                : XMLReceiverHelper,
    containingDocument: XFormsContainingDocument
  ): Unit = {

    val value1Opt = previousControlOpt flatMap attValue
    val value2    = attValue(currentControl).getOrElse("")

    if (! value1Opt.contains(value2)) {

      val attributesImpl = new AttributesImpl

      addAttribute(attributesImpl, "for",  containingDocument.namespaceId(effectiveId))
      addAttribute(attributesImpl, "name", attName)
      ch.startElement("xxf", XXFORMS_NAMESPACE_URI, "attribute", attributesImpl)
      ch.text(value2)
      ch.endElement()
    }
  }

  val AriaReadonly     = "aria-readonly"
  val AriaLabelledby   = "aria-labelledby"
  val AriaDescribedby  = "aria-describedby"
  val AriaErrorMessage = "aria-errormessage"

  val AriaReadonlyQName    = QName(AriaReadonly)
  val AriaLabelledbyQName  = QName(AriaLabelledby)
  val AriaDescribedbyQName = QName(AriaDescribedby)
//  val AriaDetails     = "aria-details"

  val AriaRequired    = "aria-required"
  val AriaInvalid     = "aria-invalid"
  val AriaHidden      = "aria-hidden"

  val AriaAttsWithLhha: List[(String, List[LHHA])] = List(
    AriaLabelledby   -> List(LHHA.Label),
    AriaDescribedby  -> List(LHHA.Hint, LHHA.Help),
    AriaErrorMessage -> List(LHHA.Alert),
  )

  def iterateAriaByAtts(
    staticControl      : ElementAnalysis,
    controlEffectiveId : String,
    condition          : LHHAAnalysis => Boolean)(
    containingDocument : XFormsContainingDocument
  ): Iterator[(String, List[String])] =
    for {
      (attName, lhhaSet) <- ControlAjaxSupport.AriaAttsWithLhha.iterator
      attValue           = lhhaSet.flatMap(lhha => findAriaByWithNs(staticControl, controlEffectiveId, lhha, condition)(containingDocument))
      if attValue.nonEmpty
    } yield
      attName -> attValue

  // We want the same id that is placed on the `<label>` or `<span>` element in the markup, so follow the logic
  // present in `XFormsLHHAHandler`.
  // TODO: We don't want this duplication of logic!
  private def findAriaByWithNs(
    staticControl      : ElementAnalysis,
    controlEffectiveId : String,
    lhha               : LHHA, // 2024-04-29: AriaLabelledby / AriaDescribedby / AriaErrorMessage
    condition          : LHHAAnalysis => Boolean)(
    containingDocument : XFormsContainingDocument
  ): Option[String] =
    for {
      staticLhhaSupport <- staticControl.narrowTo[StaticLHHASupport]
      staticLhha        <- staticLhhaSupport.firstByOrDirectLhhaOpt(lhha)
      if condition(staticLhha)
    } yield
      staticLhha.lhhaPlacementType match {
        case LhhaPlacementType.Local(directTargetControl, _) =>
          val lhhaRepeatIndices = XFormsId.getEffectiveIdSuffixParts(controlEffectiveId)
          XFormsBaseHandler.getLHHACIdWithNs(
            containingDocument,
            XFormsId.buildEffectiveId(directTargetControl.prefixedId, lhhaRepeatIndices),
            XFormsBaseHandlerXHTML.LHHACodes(lhha)
          )
        case LhhaPlacementType.External(_, _, forRepeatNestingOpt) =>

          val controlRepeatIndices = XFormsId.getEffectiveIdSuffixParts(controlEffectiveId)

          val lhhaRepeatIndices =
            forRepeatNestingOpt match {
              case Some(forRepeatNesting) => controlRepeatIndices.take(controlRepeatIndices.size - forRepeatNesting)
              case None                   => controlRepeatIndices
            }

          containingDocument.namespaceId(XFormsId.buildEffectiveId(staticLhha.prefixedId, lhhaRepeatIndices))
      }

  def outputAriaDiff(
    previousControlOpt          : Option[XFormsValueControl],
    currentControl              : XFormsValueControl,
    referencedControlEffectiveId: String
  )(implicit
    xmlReceiver: XMLReceiver
  ): Unit = {

    def outputXxfAttributeIf(name: String, fn: XFormsSingleNodeControl => Boolean): Unit = {

      val updateAtt =
        previousControlOpt match {
          case None if fn(currentControl)                           => true
          case Some(control1) if fn(control1) != fn(currentControl) => true
          case _                                                    => false
        }

      if (updateAtt)
        element(
          "attribute",
          prefix = "xxf",
          uri    = XXFORMS_NAMESPACE_URI,
          atts   = List(
            "name"  -> name,
            "for"   -> referencedControlEffectiveId,
          ),
          text   = fn(currentControl).toString
        )
    }

    outputXxfAttributeIf(
      AriaRequired,
      _.isRequired
    )

    outputXxfAttributeIf(
      AriaInvalid,
      c => c.visited && c.alertLevel.contains(ValidationLevel.ErrorLevel)
    )
  }

  def outputPlaceholderDiff(
    previousControlOpt          : Option[XFormsValueControl],
    currentControl              : XFormsValueControl,
    referencedControlEffectiveId: String
  )(implicit
    xmlReceiver: XMLReceiver
  ): Unit = {
    lazy val placeHolderValueOpt = PlaceHolderInfo.placeHolderValueOpt(currentControl.staticControl, currentControl).map(_.value)
    if (previousControlOpt.forall(pc => PlaceHolderInfo.placeHolderValueOpt(currentControl.staticControl, pc).map(_.value) != placeHolderValueOpt))
      element(
        "attribute",
        prefix = "xxf",
        uri    = XXFORMS_NAMESPACE_URI,
        atts   = List(
          "name"  -> "placeholder",
          "for"   -> referencedControlEffectiveId,
        ),
        text   = placeHolderValueOpt.getOrElse("")
      )
  }
}
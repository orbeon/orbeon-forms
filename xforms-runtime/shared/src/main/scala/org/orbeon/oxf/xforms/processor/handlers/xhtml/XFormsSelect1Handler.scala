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
import org.orbeon.oxf.xforms.analysis.controls.{LHHA, LHHAAnalysis, SelectAppearanceTrait, SelectionControlTrait}
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control
import org.orbeon.oxf.xforms.itemset._
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler.forwardAutocompleteAttribute
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XFormsBaseHandlerXHTML._
import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, XFormsBaseHandler}
import org.orbeon.oxf.xml.SaxSupport._
import org.orbeon.oxf.xml.XMLConstants.{XHTML_NAMESPACE_URI => XHTML}
import org.orbeon.oxf.xml.XMLReceiverSupport._
import org.orbeon.oxf.xml._
import org.orbeon.saxon.om
import org.orbeon.xforms.Constants.{ComponentSeparator, ComponentSeparatorString}
import org.orbeon.xforms.{XFormsId, XFormsNames}
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl
import shapeless.syntax.typeable._


/**
 * Handle xf:select and xf:select1.
 *
 * TODO: Subclasses per appearance.
 */
object XFormsSelect1Handler {

  def getItemId(effectiveId: String, itemIndex: Int): String =
    XFormsId.appendToEffectiveId(
      effectiveId = effectiveId,
      ending      = ComponentSeparatorString + ComponentSeparator + "e" + itemIndex.toString
    )

  // Support `XFormsValueControl` only for the legacy boolean `xf:input`
  def dataValueFromControl(control: XFormsValueControl): Option[(Item.Value[om.NodeInfo], Boolean)] =
    control match {
      case c: XFormsSelect1Control => c.boundItemOpt map (i => c.getCurrentItemValueFromData(i) -> c.staticControl.excludeWhitespaceTextNodesForCopy)
      case c: XFormsValueControl   => c.valueOpt     map (v => Left(v)                          -> false)
      case null                    => None
    }

  // Support `XFormsValueControl` only for the legacy boolean `xf:input`
  def isItemSelected(control: XFormsValueControl, itemNode: ItemNode, isMultiple: Boolean): Boolean =
    itemNode match {
      case item: Item.ValueNode =>
        dataValueFromControl(control) exists { case (dataValue, excludeWhitespaceTextNodes) =>
          StaticItemsetSupport.isSelected(
            isMultiple                 = isMultiple,
            dataValue                  = dataValue,
            itemValue                  = item.value,
            compareAtt                 = SaxonUtils.attCompare(control.boundNodeOpt, _),
            excludeWhitespaceTextNodes = excludeWhitespaceTextNodes
          )
        }
      case _: Item.ChoiceNode =>
        false
    }

  def outputItemFullTemplate(
    baseHandler        : XFormsBaseHandlerXHTML,
    xhtmlPrefix        : String,
    containingDocument : XFormsContainingDocument,
    attributes         : Attributes,
    templateId         : String,
    itemName           : String,
    isMultiple         : Boolean,
    fullItemType       : String)(implicit
    xmlReceiver        : XMLReceiver
  ): Unit =
    withElement(
      localName = "span",
      prefix    = xhtmlPrefix,
      uri       = XHTML,
      atts      = List("id" -> templateId, "class" -> "xforms-template") // The client queries template by id without namespace,
    ) {                                                                  // so output that even though it's not ideal (FIXME)
      handleItemFull(
        baseHandler        = baseHandler,
        attributes         = attributes,
        xhtmlPrefix        = xhtmlPrefix,
        containingDocument = containingDocument,
        control            = null,
        itemName           = itemName,
        itemEffectiveId    = "$xforms-item-id-select" + (if (isMultiple) "" else "1") + "$", // create separate id for `select`/`select1`
        isMultiple         = isMultiple,
        fullItemType       = fullItemType,
        item               = Item.ValueNode(
          LHHAValue(
            "$xforms-template-label$",
            isHTML = false
          ),
          LHHAValue(
            "$xforms-template-help$",
            isHTML = false
          ).some,
          LHHAValue(
            "$xforms-template-hint$",
            isHTML = false
          ).some,
          Left("$xforms-template-value$"),
          Nil
        )( // make sure the value "$xforms-template-value$" is not encrypted
          0
        ),
        isFirst          = true,
        isBooleanInput   = false,
        isStaticReadonly = false,
        encode           = false
      )
    }

  private def handleItemFull(
    baseHandler        : XFormsBaseHandlerXHTML,
    attributes         : Attributes,
    xhtmlPrefix        : String,
    containingDocument : XFormsContainingDocument,
    control            : XFormsValueControl,
    itemName           : String,
    itemEffectiveId    : String,
    isMultiple         : Boolean,
    fullItemType       : String,
    item               : Item.ValueNode,
    isFirst            : Boolean,
    isBooleanInput     : Boolean,
    isStaticReadonly   : Boolean,
    encode             : Boolean)(implicit
    xmlReceiver        : XMLReceiver,
  ): Unit = {

    val xformsHandlerContextForItem = baseHandler.handlerContext

    val isSelected = isItemSelected(control, item, isMultiple)

    // `xh:span` enclosing input and label
    val itemClasses = getItemClasses(item, if (isSelected) "xforms-selected" else "xforms-deselected")

    val spanAttributes =
      XFormsBaseHandler.getIdClassXHTMLAttributes(
        containingDocument,
        SaxSupport.EmptyAttributes,
        itemClasses,
        None
      )

    // Add item attributes to span
    addItemAttributes(item, spanAttributes)

    withElement(localName = "span", prefix = xhtmlPrefix, uri = XHTML, atts = spanAttributes) {

      val itemNamespacedId = xformsHandlerContextForItem.containingDocument.namespaceId(itemEffectiveId)
      val labelName = if (! isStaticReadonly) "label" else "span"

      if (! isBooleanInput) {
        val atts = new AttributesImpl
        // Add Bootstrap classes
        atts.addOrReplace(XFormsNames.CLASS_QNAME, if (isMultiple) "checkbox" else "radio")
        // No need for @for as the input, if any, is nested
        outputLabelForStart(
          handlerContext        = xformsHandlerContextForItem,
          attributes            = atts,
          controlEffectiveIdOpt = None,
          forEffectiveIdWithNs  = None,
          lhha                  = LHHA.Label,
          elementName           = labelName,
          isExternal            = false
        )
      }

      // `xh:input`
      if (! isStaticReadonly) {
        val elementName = "input"

        val atts = new AttributesImpl
        atts.addOrReplace(XFormsNames.ID_QNAME, itemNamespacedId)
        atts.addOrReplace("type", fullItemType)

        // Get group name from selection control if possible
        val name =
          control match {
            case c: XFormsSelect1Control => c.getGroupName.getOrElse(c.getEffectiveId)
            case _                       => itemName // `$xforms-item-name$` or `effectiveId`
          }

        atts.addOrReplace("name", name)
        atts.addOrReplace("value", item.externalValue(encode))

        if (control != null) {
          if (isSelected)
            atts.addOrReplace("checked", "checked")
          if (isFirst)
            XFormsBaseHandler.forwardAccessibilityAttributes(attributes, atts)
        }

        // See:
        // - https://github.com/orbeon/orbeon-forms/issues/5595
        // - https://github.com/orbeon/orbeon-forms/issues/5427
        if (baseHandler.isXFormsReadonlyButNotStaticReadonly(control))
          outputDisabledAttribute(atts)

        forwardAutocompleteAttribute(attributes, elementName, atts)

        element(localName = elementName, prefix = xhtmlPrefix, uri = XHTML, atts = atts)
      }

      if (! isBooleanInput) {
        // Don't show item hints in static-readonly, for consistency with control hints
        val showHint = item.hint.isDefined && ! isStaticReadonly

        // `<span class="xforms-hint-region">` or plain `<span>`
        withElement(
          localName = "span",
          prefix    = xhtmlPrefix,
          uri       = XHTML,
          atts      = showHint list ("class" -> "xforms-hint-region")
        ) {
          val itemLabel = item.label
          outputLabelTextIfNotEmpty(
            itemLabel.label,
            xhtmlPrefix,
            itemLabel.isHTML,
            None
          )
        }

        // <span class="xforms-help">
        item.help foreach { help =>
          val atts = new AttributesImpl
          atts.addOrReplace(XFormsNames.CLASS_QNAME, "xforms-help")
          outputLabelFor(
            handlerContext         = xformsHandlerContextForItem,
            attributes             = atts,
            controlEffectiveIdOpt  = None,
            forEffectiveIdWithNs   = None,
            lhha                   = LHHA.Help,
            elementName            = "span",
            labelValue             = help.label,
            mustOutputHTMLFragment = help.isHTML,
            isExternal             = false
          )
        }

        // <span class="xforms-hint">
        item.hint foreach { hint =>
          val atts = new AttributesImpl
          atts.addOrReplace(XFormsNames.CLASS_QNAME, "xforms-hint")
          outputLabelFor(
            handlerContext         = xformsHandlerContextForItem,
            attributes             = atts,
            controlEffectiveIdOpt  = None,
            forEffectiveIdWithNs   = None,
            lhha                   = LHHA.Hint, elementName = "span",
            labelValue             = hint.label,
            mustOutputHTMLFragment = hint.isHTML,
            isExternal             = false
          )
        }
      }
      if (! isBooleanInput)
        outputLabelForEnd(xformsHandlerContextForItem, labelName)
    }
  }

  private def addItemAttributes(item: ItemNode, spanAttributes: AttributesImpl): Unit =
    for {
      (attQName, attValue) <- item.attributes
      if attQName != XFormsNames.CLASS_QNAME // `class` is handled separately
      attributeName = ItemsetSupport.getAttributeName(attQName)
    } locally {
      spanAttributes.addOrReplace(attributeName, attValue)
    }

  private def getItemClasses(item: ItemNode, initialClasses: String): String = {
    val classOpt = item.classAttribute
    val sb = if (initialClasses ne null) new StringBuilder(initialClasses) else new StringBuilder
    if (classOpt.isDefined) {
      if (sb.nonEmpty)
        sb.append(' ')
      sb.append(classOpt.get)
    }
    sb.toString
  }
}

class XFormsSelect1Handler(
  uri             : String,
  localname       : String,
  qName           : String,
  attributes      : Attributes,
  elementAnalysis : ElementAnalysis,
  handlerContext  : HandlerContext
) extends
  XFormsControlLifecycleHandler(
    uri            = uri,
    localname      = localname,
    qName          = qName,
    localAtts      = attributes,
    elementAnalysis        = elementAnalysis,
    handlerContext = handlerContext,
    repeating      = false,
    forwarding     = false
  ) {

  // Incremental mode is the default
  override def isDefaultIncremental = true

  private def findAppearanceTrait =
    elementAnalysis.narrowTo[SelectAppearanceTrait]

  def handleControlStart(): Unit = {

    // Get items, dynamic or static, if possible
    val xformsSelect1Control   = currentControl.asInstanceOf[XFormsSelect1Control]
    val staticSelectionControl = xformsSelect1Control.staticControl

    // Get items if:
    // 1. The itemset is static
    // 2. The control exists and is relevant
    val itemsetOpt = XFormsSelect1Control.getInitialItemset(xformsSelect1Control, staticSelectionControl)

    outputContent(
      attributes           = attributes,
      effectiveId          = getEffectiveId,
      control              = xformsSelect1Control,
      itemsetOpt           = itemsetOpt,
      isMultiple           = staticSelectionControl.isMultiple,
      isFull               = staticSelectionControl.isFull,
      isBooleanInput       = false,
      xformsHandlerContext = handlerContext
    )
  }

  def outputContent(
    attributes           : Attributes,
    effectiveId          : String,
    control              : XFormsValueControl,
    itemsetOpt           : Option[Itemset],
    isMultiple           : Boolean,
    isFull               : Boolean,
    isBooleanInput       : Boolean,
    xformsHandlerContext : HandlerContext
  ): Unit = {

    implicit val xmlReceiver: XMLReceiver = xformsHandlerContext.controller.output

    val containingDocument   = xformsHandlerContext.containingDocument
    val containerAttributes  = getEmptyNestedControlAttributesMaybeWithId(effectiveId, control, ! isFull)
    val xhtmlPrefix          = xformsHandlerContext.findXHTMLPrefix
    val isStaticReadonly     = XFormsBaseHandler.isStaticReadonly(control)

    val allowFullStaticReadonly =
      isMultiple && containingDocument.isReadonlyAppearanceStaticSelectFull ||
        ! isMultiple && containingDocument.isReadonlyAppearanceStaticSelect1Full

    val mustOutputFull = isBooleanInput || (isFull && (allowFullStaticReadonly || !isStaticReadonly))

    val encode =
      elementAnalysis match {
        case t: SelectionControlTrait => XFormsSelect1Control.mustEncodeValues(containingDocument, t)
        case _                        => false // case of boolean input
      }

    if (mustOutputFull) {
      // full appearance, also in static readonly mode
      outputFull(attributes, effectiveId, control, itemsetOpt, isMultiple, isBooleanInput, isStaticReadonly, encode)
    } else if (! isStaticReadonly) {
      // Create `xh:select`

      // This was necessary for noscript mode
      // Q: Can remove now?
      containerAttributes.addOrReplace("name", effectiveId)

      if (findAppearanceTrait.exists(_.isCompact))
        containerAttributes.addOrReplace("multiple", "multiple")

      // Handle accessibility attributes
      XFormsBaseHandler.forwardAccessibilityAttributes(attributes, containerAttributes)
      handleAriaByAtts(containerAttributes, XFormsLHHAHandler.coreControlLhhaByCondition)

      if (control ne null)
        control.addExtensionAttributesExceptClassAndAcceptForHandler(containerAttributes, XFormsNames.XXFORMS_NAMESPACE_URI)

      if (isXFormsReadonlyButNotStaticReadonly(control))
        outputDisabledAttribute(containerAttributes)

      if (control ne null)
        XFormsBaseHandler.handleAriaAttributes(control.isRequired, control.isValid, control.visited, containerAttributes)

      forwardAutocompleteAttribute(attributes, "select", containerAttributes)

      withElement(localName = "select", prefix = xhtmlPrefix, uri = XHTML, atts = containerAttributes) {
        itemsetOpt foreach { itemset =>
          itemset.visit(
            new ItemsetListener {

              var inOptgroup  = false // nesting groups is not allowed, avoid it
              var gotSelected = false

              def endLevel(): Unit =
                if (inOptgroup) {
                  // End `xh:optgroup`
                  closeElement(localName = "optgroup", prefix = xhtmlPrefix, uri = XHTML)
                  inOptgroup = false
                }

              def startItem(itemNode: ItemNode, first: Boolean): Unit = {

                // TODO: Check this, which fails with the workflow UI
    //            assert(! item.label.isHTML)

                itemNode match {
                  case item: Item.ChoiceNode =>

                    // We used to `assert` here if there are no children, but there is no real reason to do that as HTML
                    // supports `<optgroup>` without children.
                    // See https://github.com/orbeon/orbeon-forms/issues/4843

                    val itemClasses = XFormsSelect1Handler.getItemClasses(item, null)
                    val optGroupAttributes = XFormsBaseHandler.getIdClassXHTMLAttributes(containingDocument, SaxSupport.EmptyAttributes, itemClasses, None)

                    optGroupAttributes.addOrReplace("label", item.label.label)

                    // If another optgroup is open, close it - nested optgroups are not allowed. Of course this results in an
                    // incorrect structure for tree-like itemsets, there is no way around that. If the user however does
                    // the indentation himself, it will still look right.
                    if (inOptgroup)
                      closeElement(localName = "optgroup", prefix = xhtmlPrefix, uri = XHTML)

                    // Start `xh:optgroup`
                    openElement(localName = "optgroup", prefix = xhtmlPrefix, uri = XHTML, atts = optGroupAttributes)
                    inOptgroup = true
                  case item: Item.ValueNode =>
                    gotSelected |= handleItemCompact(xhtmlPrefix, control, isMultiple, item, encode, gotSelected)
                }
              }

              def startLevel(itemNode: ItemNode): Unit = ()
              def endItem(itemNode: ItemNode)   : Unit = ()
            }
          )
        }
      }
    } else {
      outputStaticReadonlyField(xhtmlPrefix) {
        itemsetOpt foreach { itemset =>
          var selectedFound = false
          val ch = new XMLReceiverHelper(xmlReceiver)
          for {
            (dataValue, excludeWhitespaceTextNodes) <- XFormsSelect1Handler.dataValueFromControl(control).iterator
            currentItem                             <- itemset.iterateSelectedItems(dataValue, SaxonUtils.attCompare(control.boundNodeOpt, _), excludeWhitespaceTextNodes)
          } locally {
            if (selectedFound)
              ch.text(" - ")
            ItemsetSupport.streamAsHTML(currentItem.label, control.getLocationData)
            selectedFound = true
          }
        }
      }
    }
  }

  private def outputFull(
    attributes       : Attributes,
    effectiveId      : String,
    control          : XFormsValueControl,
    itemsetOpt       : Option[Itemset],
    isMultiple       : Boolean,
    isBooleanInput   : Boolean,
    isStaticReadonly : Boolean,
    encode           : Boolean
  ): Unit = {

    implicit val xmlReceiver: XMLReceiver = handlerContext.controller.output

    val containerAttributes =
      getEmptyNestedControlAttributesMaybeWithId(effectiveId, control, ! findAppearanceTrait.exists(_.isFull))

    // CSS classes:
    // - `xforms-items` for styling
    // - `xforms-help-popover-control` tells the help popover the element relative to which it should positioned,
    //   needed for the case where we only have one
    containerAttributes.addOrReplace(XFormsNames.CLASS_QNAME, "xforms-items xforms-help-popover-control")

    // Keep the group/radiogroup role even if there is no label. It probably doesn't hurt, although if there is only
    // one item inside the group, those roles are not doing much.
    containerAttributes.addOrReplace(XFormsNames.ROLE_QNAME, if (isMultiple) "group" else "radiogroup")
    // When a form author explicitly uses a single checkbox, it generally doesn't make sense to have a label on the
    // control itself, as the label of the single item is sufficient. Also, it is when we have multiple checkboxes
    // that it makes sense to, at least optionally, give the user a chance to tab to the group of checkboxes, and have
    // the label and/or hint read if a screen reader is used. When there is no external label, there is no need for
    // that. So below we don't place a `tabindex` on the group when we don't have a label or hint. From an end user
    // perspective, an additional drawback of keeping the `tabindex` is that the user needs to tab twice to reach the
    // checkbox for no obvious reason.
    // Remains the case of dynamic checkboxes (with a dynamic itemset), where the form author would place a lable on
    // the control: in case the itemset has only one item, then we would have the double label and double tab issue.
    // But we leave this problem for another time, as that would require adding the tabindex dynamically depending on
    // the size of the itemset and we don't have a good mechanism to do that.
    if (findStaticLhhaOrLhhaBy(LHHA.Label).isDefined || findStaticLhhaOrLhhaBy(LHHA.Hint).isDefined)
      if (handlerContext.a11yFocusOnGroups)
        containerAttributes.addOrReplace(XFormsNames.TABINDEX_QNAME, "0")

    handleAriaByAtts(containerAttributes, _ => true)

    val xhtmlPrefix = handlerContext.findXHTMLPrefix
    val fullItemType = if (isMultiple) "checkbox" else "radio"

    // TODO: Should we always use fieldset, or make this an option?
    val containingElementName = "span"
    // Output container <span>/<fieldset> for select/select1
    val outputContainerElement = ! isBooleanInput

    if (outputContainerElement)
      openElement(localName = containingElementName, prefix = xhtmlPrefix, uri = XHTML, atts = containerAttributes)
    // {
    //     // Output <legend>
    //     final String legendName = "legend";
    //     final String legendQName = XMLUtils.buildQName(xhtmlPrefix, legendName);
    //     reusableAttributes.clear();
    //     // TODO: handle other attributes? xforms-disabled?
    //     reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, "xforms-label");
    //     xmlReceiver.startElement(XHTML, legendName, legendQName, reusableAttributes);
    //     if (control != null) {
    //         final boolean mustOutputHTMLFragment = xformsControl.isHTMLLabel();
    //         outputLabelText(xmlReceiver, xformsControl, xformsControl.getLabel(), xhtmlPrefix, mustOutputHTMLFragment);
    //     }
    //     xmlReceiver.endElement(XHTML, legendName, legendQName);
    // }
    itemsetOpt foreach { itemset =>
      for (((item, _), itemIndex) <- itemset.allItemsWithValueIterator(reverse = false).zipWithIndex) {
        XFormsSelect1Handler.handleItemFull(
          baseHandler        = this,
          attributes         = attributes,
          xhtmlPrefix        = xhtmlPrefix,
          containingDocument = containingDocument,
          control            = control,
          itemName           = effectiveId,
          itemEffectiveId    = XFormsSelect1Handler.getItemId(effectiveId, itemIndex),
          isMultiple         = isMultiple,
          fullItemType       = fullItemType,
          item               = item,
          isFirst            = itemIndex == 0,
          isBooleanInput     = isBooleanInput,
          isStaticReadonly   = isStaticReadonly,
          encode             = encode
        )
      }
    }
    if (outputContainerElement)
      closeElement(localName = containingElementName, prefix = xhtmlPrefix, uri = XHTML)

    // NOTE: Templates for full items are output globally in `XHTMLBodyHandler`
  }

  private def handleItemCompact(
    xhtmlPrefix    : String,
    xformsControl  : XFormsValueControl,
    isMultiple     : Boolean,
    item           : Item.ValueNode,
    encode         : Boolean,
    gotSelected    : Boolean)(implicit
    xmlReceiver    : XMLReceiver
  ): Boolean = {

    val itemClasses      = XFormsSelect1Handler.getItemClasses(item, null)
    val optionAttributes = XFormsBaseHandler.getIdClassXHTMLAttributes(containingDocument, SaxSupport.EmptyAttributes, itemClasses, None)

    // Add item attributes to `<option>`
    XFormsSelect1Handler.addItemAttributes(item, optionAttributes)
    optionAttributes.addOrReplace("value", item.externalValue(encode))
    item.hint.foreach { hint =>
      optionAttributes.addOrReplace("title", hint.label)
    }

    // Figure out whether what items are selected
    // Don't output more than one `selected` in the case of single-selection, see:
    // https://github.com/orbeon/orbeon-forms/issues/2901
    val mustSelect =
      (isMultiple || ! gotSelected) && XFormsSelect1Handler.isItemSelected(xformsControl, item, isMultiple)
    if (mustSelect)
      optionAttributes.addOrReplace("selected", "selected")

    // `xh:option`
    withElement(localName = "option", prefix = xhtmlPrefix, uri = XHTML, atts = optionAttributes) {
      // TODO: Check this, which fails with the workflow UI
  //    assert(! item.label.isHTML)
      text(text = item.label.label)
    }

    mustSelect
  }

  // For full appearance we don't put a `@for` attribute so that selecting the main label doesn't select the item
  override def getForEffectiveIdWithNs(lhhaAnalysis: LHHAAnalysis): Option[String] =
    if (findAppearanceTrait.exists(_.isFull))
      None
    else
      super.getForEffectiveIdWithNs(lhhaAnalysis)

  // For full appearance produce `span` with an `id`
  override def handleLabel(lhhaAnalysis: LHHAAnalysis): Unit =
    if (findAppearanceTrait.exists(_.isFull))
      handleLabelHintHelpAlert(
        lhhaAnalysis            = lhhaAnalysis,
        controlEffectiveIdOpt   = getEffectiveId.some,
        forEffectiveIdWithNsOpt = None,
        requestedElementNameOpt = "span".some, // make element name a `span`, as a label would need a `for`
        controlOrNull           = currentControl,
        isExternal              = false
      )
    else
      super.handleLabel(lhhaAnalysis)
}
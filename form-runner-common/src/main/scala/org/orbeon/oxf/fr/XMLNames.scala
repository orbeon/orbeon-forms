/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.orbeon.dom.{Namespace, QName}
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.scaxon.SimplePath.Test
import org.orbeon.xforms.{Namespaces, XFormsNames}


object XMLNames {

  val FRPrefix = "fr"
  val FR       = "http://orbeon.org/oxf/xml/form-runner"

  val XH  = XHTML_NAMESPACE_URI
  val XS  = XSD_URI

  val XF  = Namespaces.XF
  val XXF = Namespaces.XXF
  val XBL = Namespaces.XBL

  val FRNamespace              : Namespace = Namespace(FRPrefix, FR)

  val XHHeadTest               : Test      = XH -> "head"
  val XHBodyTest               : Test      = XH -> "body"

  val XBLXBLTest               : Test      = XBL -> "xbl"
  val XBLBindingTest           : Test      = XBL -> "binding"
  val XBLTemplateTest          : Test      = XBL -> "template"
  val XBLImplementationTest    : Test      = XBL -> "implementation"

  val XSSchemaTest             : Test      = XS -> "schema"

  val FRBodyTest               : Test      = FR -> "body"

  val FRGridTest               : Test      = FR -> "grid"
  val FRSectionTest            : Test      = FR -> "section"
  val FRRepeatTest             : Test      = FR -> "repeat" // legacy

  val XFModelTest              : Test      = XF -> "model"
  val XFInstanceTest           : Test      = XF -> "instance"
  val XFBindTest               : Test      = XF -> "bind"
  val XFGroupTest              : Test      = XF -> "group"
  val XFActionTest             : Test      = XF -> "action"
  val XFSendTest               : Test      = XF -> "send"

  val XXFDialogTest            : Test      = XXF -> "dialog"

  val XFItemsetTest            : Test      = XFormsNames.XFORMS_ITEMSET_QNAME
  val XFItemTest               : Test      = XFormsNames.XFORMS_ITEM_QNAME
  val XFChoicesTest            : Test      = XFormsNames.XFORMS_CHOICES_QNAME

  val XFLabelTest              : Test      = XFormsNames.LABEL_QNAME
  val XFHelpTest               : Test      = XFormsNames.HELP_QNAME
  val XFHintTest               : Test      = XFormsNames.HINT_QNAME
  val XFAlertTest              : Test      = XFormsNames.ALERT_QNAME

  val XFValueTest              : Test      = XFormsNames.XFORMS_VALUE_QNAME

  val FRMetadata               : Test      = FR -> "metadata"
  val FRItemsetId              : Test      = FR -> "itemsetid"
  val FRItemsetMap             : Test      = FR -> "itemsetmap"

  val FRItemsetMapTest         : Test      = FR -> "itemsetmap"
  val FRParamTest              : Test      = FR -> "param"
  val FRNameTest               : Test      = FR -> "name"
  val FRControlNameTest        : Test      = FR -> "controlName"
  val FRExprTest               : Test      = FR -> "expr"

  val FRListenerTest           : Test      = FR -> "listener"
  val FRActionTest             : Test      = FR -> "action"
  val FRServiceCallTest        : Test      = FR -> "service-call"

  val FRNumberTest             : Test      = FR -> "number"
  val FRCurrencyTest           : Test      = FR -> "currency"

  val ControlsTest             : Test      = QName("controls")
  val ControlTest              : Test      = QName("control")
  val RepeatTest               : Test      = QName("repeat")
  val ValueTest                : Test      = QName("value")
  val RefTest                  : Test      = QName("ref")
  val ItemsTest                : Test      = QName("items")
  val LabelTest                : Test      = QName("label")
  val HintTest                 : Test      = QName("hint")
  val ConditionTest            : Test      = QName("condition")
  val LeftTest                 : Test      = QName("left")
  val RightTest                : Test      = QName("right")
  val ResourceTest             : Test      = QName("resource")

  val XMLLangQName             : QName     = XML_LANG_QNAME//both

  val FRRelevantQName          : QName     = QName("relevant" , FRNamespace)
  val FRAutomaticQName         : QName     = QName("automatic", FRNamespace)

  val FRContainerTest          : Test      = FRSectionTest || FRGridTest

  val FRItemsetIdQName         : QName     = QName("itemsetid",           FRNamespace)
  val FRDataFormatVersionQName : QName     = QName("data-format-version", FRNamespace)
  val FRTextQName              : QName     = QName("text",                FRNamespace)
  val FRShortLabelQName        : QName     = QName("short-label",         FRNamespace)
  val FRIterationLabelQName    : QName     = QName("iteration-label",     FRNamespace)
  val FRAddIterationLabelQName : QName     = QName("add-iteration-label", FRNamespace)
  val FRSectionQName           : QName     = QName("section",             FRNamespace)

  val FRYesNoInputQName             : QName = QName("yesno-input",              FRNamespace)
  val FRCheckboxInputQName          : QName = QName("checkbox-input",           FRNamespace)
  val FRDataboundSelect1QName       : QName = QName("databound-select1",        FRNamespace)
  val FRDataboundSelect1SearchQName : QName = QName("databound-select1-search", FRNamespace)
}

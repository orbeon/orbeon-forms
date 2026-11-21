/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.fb

import org.orbeon.dom.QName
import org.orbeon.oxf.fb.FormBuilder.*
import org.orbeon.oxf.fr.FormRunner.{findBindByName, findControlByName}
import org.orbeon.oxf.fr.XMLNames.{XBLBindingTest, XBLXBLTest}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.analysis.model.Types
import org.orbeon.oxf.xforms.xbl.{BindingDescriptor, BindingIndex}
import org.orbeon.oxf.xml.XMLConstants.*
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits.*
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.Namespaces
import org.orbeon.xforms.XFormsNames.*
import org.scalatest.funspec.AnyFunSpecLike

import scala.xml.Elem


class BindingDescriptorTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormBuilderSupport {

  import BindingDescriptor.*

  val ComponentsDocument: NodeInfo =
    <components xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
          xmlns:xbl="http://www.w3.org/ns/xbl">
      <xbl:xbl>
        <xbl:binding element="xf|input, xf|input:xxf-type('xs:anyURI')">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Input Field</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="xf|textarea">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Plain Text Area</display-name>
            <conversions element="xf|input"/>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|number, xf|input:xxf-type('xs:decimal'), xf|input:xxf-type('xs:integer')">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Number</display-name>
            <conversions element="xf|input"/>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|number-stepper, xf|input:xxf-type('xs:decimal')[appearance ~= stepper], xf|input:xxf-type('xs:integer')[appearance ~= stepper]">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Numeric Stepper</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|percentage, xf|input[appearance = 'xxf:percentage']:xxf-type('xs:decimal')">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Percentage</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|date, xf|input:xxf-type('xs:date')">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Date</display-name>
            <conversions element="xf|input"/>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|dropdown-date, xf|input:xxf-type('xs:date')[appearance ~= dropdowns]">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Dropdown Date</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|fields-date, xf|input:xxf-type('xs:date')[appearance ~= fields]">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Fields Date</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|year, xf|input:xxf-type('xs:gYear')">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Year</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|dropdown-year, xf|input:xxf-type('xs:gYear')[appearance ~= dropdown]">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Dropdown Year</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|dropdown-month-year, xf|input:xxf-type('xs:gYearMonth')[appearance ~= dropdowns]">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Dropdown Month/Year</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|duration, xf|input:xxf-type('xs:dayTimeDuration')">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Duration</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="xf|select1[appearance ~= full][appearance ~= 'xxf:horizontal']">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Horizontal Radio Buttons</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="xf|select1[appearance ~= full]">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Radio Buttons</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|segmented-select1, xf|select1[appearance ~= segmented]">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Segmented Buttons</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="xf|select1[appearance ~= cards][appearance ~= 'xxf:horizontal']">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Horizontal Choice Cards</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|cards-select1, xf|select1[appearance ~= cards]">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Choice Cards</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|dropdown-select1, xf|select1[appearance ~= dropdown]">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Dropdown Menu</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|open-select1[appearance ~= minimal], xf|select1[selection = open][appearance ~= minimal]">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Dropdown with "Other"</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|open-select1[appearance ~= full][appearance ~= 'xxf:horizontal'], xf|select1[selection = open][appearance ~= full][appearance ~= 'xxf:horizontal']">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Horizontal Radio Buttons with "Other"</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|open-select1, fr|open-select1[appearance ~= full], xf|select1[selection = open], xf|select1[selection = open][appearance ~= full]">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Radio Buttons with "Other"</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="xf|select[appearance ~= full][appearance ~= 'xxf:horizontal']">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Horizontal Checkboxes</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="xf|select[appearance ~= full]">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Checkboxes</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|box-select, xf|select[appearance ~= scroll]">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Scrollable Checkboxes</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="xf|select[appearance ~= cards][appearance ~= 'xxf:horizontal']">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Horizontal Choice Cards</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|cards-select, xf|select[appearance ~= cards]">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Choice Cards</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|open-select[appearance ~= full][appearance ~= 'xxf:horizontal'], xf|select[selection = open][appearance ~= full][appearance ~= 'xxf:horizontal']">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Horizontal Checkboxes with "Other"</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|open-select, fr|open-select[appearance ~= full], xf|select[selection = open], xf|select[selection = open][appearance ~= full]">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Checkboxes with "Other"</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding
          element="
            fr|character-counter,
            xf|input[appearance ~= character-counter],
            xf|textarea[appearance ~= character-counter],
            xf|secret[appearance ~= character-counter],
            xf|textarea[appearance ~= character-counter][mediatype = 'text/html']">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">With Character Counter</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|tinymce, xf|textarea[mediatype = 'text/html']">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Formatted Text Area</display-name>
            <conversions element="xf|textarea"/>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|attachment">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Single File Attachment</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|attachment[multiple ~= true]">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Multiple File Attachments</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|us-ein">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">US Employer Identification Number (EIN)</display-name>
            <conversions element="xf|input"/>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|color">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Color</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|masked-input">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Masked Input</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|checkbox-input, xf|input:xxf-type('xs:boolean')[appearance ~= checkbox]">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Single Checkbox</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|switch-input, xf|input:xxf-type('xs:boolean')[appearance ~= switch]">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Toggle Switch</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|yesno-input, xf|input:xxf-type('xs:boolean')[appearance ~= yesno]">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Yes/No Answer</display-name>
          </metadata>
        </xbl:binding>

      </xbl:xbl>
    </components>


  val XF  = XFORMS_NAMESPACE_URI
  val XS  = XSD_URI
  val XBL = Namespaces.XBL
  val FR  = "http://orbeon.org/oxf/xml/form-runner"
  val FB  = "http://orbeon.org/oxf/xml/form-builder"

  implicit val index: BindingIndex[BindingDescriptor] = {
    val bindings = ComponentsDocument.rootElement / XBLXBLTest / XBLBindingTest
    buildIndexFromBindingDescriptors(getAllRelevantDescriptors(bindings))
  }

  describe("New element name") {

    def assertVaryTypes(
      oldControlElemName: QName,
      oldDatatype       : QName,
      oldAppearance     : Option[String],
      newDatatype       : QName,
      newAppearance     : Option[String]
    )(
      expected       : Option[(QName, Option[String])]
    ): Unit =
      it(s"must pass with $oldControlElemName/$oldDatatype/$oldAppearance/$newDatatype/$newAppearance") {
        for {
          oldT <- List(oldDatatype, Types.getVariationTypeOrKeep(oldDatatype))
          newT <- List(newDatatype, Types.getVariationTypeOrKeep(newDatatype))
        } locally {
          assert(expected == newElementName(oldControlElemName, oldT, BindingDescriptor.updateAttAppearance(Nil, oldAppearance), newT, newAppearance))
        }
      }

    assertVaryTypes(XF -> "input"         , XS -> "string" , None                     , XS -> "decimal", None                     )(Some((FR -> "number"        , None)))
    assertVaryTypes(XF -> "input"         , XS -> "string" , None                     , XS -> "decimal", Some("stepper")          )(Some((FR -> "number-stepper", None)))
    assertVaryTypes(FR -> "number"        , XS -> "decimal", None                     , XS -> "decimal", Some("stepper")          )(Some((FR -> "number-stepper", None)))
    assertVaryTypes(FR -> "number-stepper", XS -> "decimal", None                     , XS -> "decimal", None                     )(Some((FR -> "number"        , None)))
    assertVaryTypes(FR -> "number"        , XS -> "integer", None                     , XS -> "integer", Some("stepper")          )(Some((FR -> "number-stepper", None)))
    assertVaryTypes(FR -> "number-stepper", XS -> "integer", None                     , XS -> "integer", None                     )(Some((FR -> "number"        , None)))
    assertVaryTypes(FR -> "number"        , XS -> "string" , None                     , XS -> "string" , None                     )(None)
    assertVaryTypes(FR -> "number"        , XS -> "decimal", None                     , XS -> "string" , None                     )(Some((XF -> "input"         , None)))
    assertVaryTypes(FR -> "number"        , XS -> "decimal", None                     , XS -> "boolean", None                     )(Some((XF -> "input"         , None)))
    assertVaryTypes(XF -> "input"        , XS -> "string" , None                     , XS -> "boolean", None                     )(None)
    assertVaryTypes(XF -> "input"        , XS -> "boolean", None                     , XS -> "boolean", None                     )(None)

    assertVaryTypes(XF -> "input"        , XS -> "string" , None                     , XS -> "date"   , None                     )(Some(FR -> "date"         , None))
    assertVaryTypes(XF -> "input"        , XS -> "string" , None                     , XS -> "date"   , Some("dropdowns")        )(Some(FR -> "dropdown-date", None))
    assertVaryTypes(XF -> "input"        , XS -> "date"   , None                     , XS -> "date"   , Some("dropdowns")        )(Some(FR -> "dropdown-date", None))

    assertVaryTypes(FR -> "dropdown-date", XS -> "date"   , None                     , XS -> "date"   , None                     )(Some(FR -> "date"        , None))
    assertVaryTypes(FR -> "dropdown-date", XS -> "date"   , None                     , XS -> "date"   , Some("fields")           )(Some(FR -> "fields-date"  , None))
    assertVaryTypes(FR -> "dropdown-date", XS -> "date"   , None                     , XS -> "string" , Some("fields")           )(Some(XF -> "input"        , None))

    assertVaryTypes(XF -> "input"        , XS -> "string" , None                     , XS -> "gYear"  , None                     )(Some(FR -> "year"         , None))
    assertVaryTypes(XF -> "input"        , XS -> "string" , None                     , XS -> "gYear"  , Some("dropdown")         )(Some(FR -> "dropdown-year", None))
    assertVaryTypes(FR -> "year"         , XS -> "gYear"  , None                     , XS -> "gYear"  , Some("dropdown")         )(Some(FR -> "dropdown-year", None))
    assertVaryTypes(FR -> "dropdown-year", XS -> "gYear"  , None                     , XS -> "gYear"  , None                     )(Some(FR -> "year"         , None))
    assertVaryTypes(FR -> "dropdown-year", XS -> "gYear"  , None                     , XS -> "string" , None                     )(Some(XF -> "input"        , None))

    assertVaryTypes(XF -> "input"              , XS -> "string"    , None, XS -> "gYearMonth", Some("dropdowns"))(Some(FR -> "dropdown-month-year", None))
    assertVaryTypes(FR -> "dropdown-month-year", XS -> "gYearMonth", None, XS -> "string"    , None             )(Some(XF -> "input"              , None))

    assertVaryTypes(XF -> "input"   , XS -> "string"         , None, XS -> "dayTimeDuration", None)(Some(FR -> "duration", None))
    assertVaryTypes(FR -> "duration", XS -> "dayTimeDuration", None, XS -> "string"         , None)(Some(XF -> "input"   , None))

    assertVaryTypes(XF -> "select1"      , XS -> "string" , Some("full")                 , XS -> "string" , Some("dropdown")             )(Some(XF -> "select1"      , Some("dropdown")))
    assertVaryTypes(XF -> "select1"      , XS -> "string" , Some("dropdown")             , XS -> "string" , Some("full")                 )(Some(XF -> "select1"      , Some("full")))
    assertVaryTypes(XF -> "select1"      , XS -> "string" , Some("full")                 , XS -> "string" , Some("full xxf:horizontal")  )(Some(XF -> "select1"      , Some("full xxf:horizontal")))
    assertVaryTypes(XF -> "select1"      , XS -> "string" , Some("full xxf:horizontal")  , XS -> "string" , Some("full")                 )(Some(XF -> "select1"      , Some("full")))
    assertVaryTypes(XF -> "select1"      , XS -> "string" , Some("xxf:horizontal full")  , XS -> "string" , Some("full")                 )(Some(XF -> "select1"      , Some("full")))
    assertVaryTypes(XF -> "select1"      , XS -> "string" , Some("xxf:horizontal full")  , XS -> "string" , Some("full xxf:horizontal")  )(None)
    assertVaryTypes(XF -> "select1"      , XS -> "string" , Some("full")                 , XS -> "string" , Some("segmented")            )(Some(XF -> "select1"      , Some("segmented")))
    assertVaryTypes(XF -> "select1"      , XS -> "string" , Some("segmented")            , XS -> "string" , Some("cards")                )(Some(XF -> "select1"      , Some("cards")))
    assertVaryTypes(XF -> "select1"      , XS -> "string" , Some("cards")                , XS -> "string" , Some("cards xxf:horizontal") )(Some(XF -> "select1"      , Some("cards xxf:horizontal")))
    assertVaryTypes(XF -> "select1"      , XS -> "string" , Some("cards xxf:horizontal") , XS -> "string" , Some("cards")                )(Some(XF -> "select1"      , Some("cards")))
    assertVaryTypes(XF -> "select"       , XS -> "string" , Some("full")                 , XS -> "string" , Some("full xxf:horizontal")  )(Some(XF -> "select"       , Some("full xxf:horizontal")))
    assertVaryTypes(XF -> "select"       , XS -> "string" , Some("full xxf:horizontal")  , XS -> "string" , Some("full")                 )(Some(XF -> "select"       , Some("full")))
    assertVaryTypes(XF -> "select"       , XS -> "string" , Some("full")                 , XS -> "string" , Some("cards")                )(Some(XF -> "select"       , Some("cards")))
    assertVaryTypes(XF -> "select"       , XS -> "string" , Some("cards")                , XS -> "string" , Some("cards xxf:horizontal") )(Some(XF -> "select"       , Some("cards xxf:horizontal")))
    assertVaryTypes(XF -> "select"       , XS -> "string" , Some("cards xxf:horizontal") , XS -> "string" , Some("cards")                )(Some(XF -> "select"       , Some("cards")))
    assertVaryTypes(XF -> "select"       , XS -> "string" , Some("cards")                , XS -> "string" , Some("scroll")               )(Some(XF -> "select"       , Some("scroll")))
    assertVaryTypes(FR -> "open-select1" , XS -> "string" , None                         , XS -> "string" , Some("full xxf:horizontal")  )(Some(FR -> "open-select1" , Some("full xxf:horizontal")))
    assertVaryTypes(FR -> "open-select1" , XS -> "string" , Some("full xxf:horizontal")  , XS -> "string" , Some("full")                 )(Some(FR -> "open-select1" , Some("full")))
    assertVaryTypes(FR -> "open-select1" , XS -> "string" , Some("full xxf:horizontal")  , XS -> "string" , Some("minimal")              )(Some(FR -> "open-select1" , Some("minimal")))
    assertVaryTypes(FR -> "open-select"  , XS -> "string" , None                         , XS -> "string" , Some("full xxf:horizontal")  )(Some(FR -> "open-select"  , Some("full xxf:horizontal")))
    assertVaryTypes(FR -> "open-select"  , XS -> "string" , Some("full xxf:horizontal")  , XS -> "string" , Some("full")                 )(Some(FR -> "open-select"  , Some("full")))

    assertVaryTypes(XF -> "input"        , XS -> "string" , None                     , XS -> "string" , Some("character-counter"))(Some(XF -> "input"        , Some("character-counter")))
    assertVaryTypes(XF -> "textarea"     , XS -> "string" , None                     , XS -> "string" , Some("character-counter"))(Some(XF -> "textarea"     , Some("character-counter")))
    assertVaryTypes(XF -> "textarea"     , XS -> "string" , Some("character-counter"), XS -> "string" , None                     )(Some(XF -> "textarea"     , None))

    assertVaryTypes(XF -> "input"        , XS -> "string" , Some("character-counter"), XS -> "double" , Some("character-counter"))(None)

    assertVaryTypes(FR -> "attachment"   , XS -> "string" , None                     , XS -> "string" , None)(None)

    assertVaryTypes(FR -> "checkbox-input", XS -> "boolean", None                    , XS -> "boolean", Some("switch")  )(Some(FR -> "switch-input"  , None))
    assertVaryTypes(FR -> "switch-input"  , XS -> "boolean", None                    , XS -> "boolean", Some("checkbox"))(Some(FR -> "checkbox-input", None))
    assertVaryTypes(FR -> "switch-input"  , XS -> "boolean", None                    , XS -> "boolean", Some("yesno")   )(Some(FR -> "yesno-input"   , None))
  }

  describe("Possible appearances with label") {

    def assertVaryTypes(
      elemName : QName,
      dataType : QName,
      atts     : Iterable[(QName, String)] = Nil,
    )(
      expected : Seq[(Option[String], String)]
    ): Unit =
      it(s"must pass with $elemName/$dataType${if (atts.nonEmpty) "/"}${atts.mkString("=")}") {
        for {
          newT <- List(dataType, Types.getVariationTypeOrKeep(dataType))
        } locally {
          assert(expected.toSet == possibleAppearancesWithLabel(elemName, newT, atts, "en").map(t => t._1 -> t._3).toSet)
        }
      }

    assertVaryTypes(XF -> "input"     , XS -> "string" )(Seq(None         -> "Input Field"    , Some("character-counter") -> "With Character Counter"))
    assertVaryTypes(XF -> "textarea"  , XS -> "string" )(Seq(None         -> "Plain Text Area", Some("character-counter") -> "With Character Counter"))
    assertVaryTypes(XF -> "input"     , XS -> "decimal")(Seq(None         -> "Number"          , Some("xxf:percentage")    -> "Percentage", Some("stepper") -> "Numeric Stepper"))
    assertVaryTypes(XF -> "input"     , XS -> "integer")(Seq(None         -> "Number"          , Some("stepper")           -> "Numeric Stepper"))
    assertVaryTypes(XF -> "input"     , XS -> "date"   )(Seq(None         -> "Date"           , Some("dropdowns")         -> "Dropdown Date"          , Some("fields") -> "Fields Date"))
    assertVaryTypes(XF -> "input"     , XS -> "gYear"          )(Seq(None            -> "Year"               , Some("dropdown")  -> "Dropdown Year"))
    assertVaryTypes(XF -> "input"     , XS -> "gYearMonth"     )(Seq(Some("dropdowns") -> "Dropdown Month/Year"))
    assertVaryTypes(XF -> "input"     , XS -> "dayTimeDuration")(Seq(None            -> "Duration"))
    assertVaryTypes(XF -> "select1"     , XS -> "string" )(Seq(
      Some("full")                 -> "Radio Buttons",
      Some("full xxf:horizontal")  -> "Horizontal Radio Buttons",
      Some("segmented")            -> "Segmented Buttons",
      Some("cards")                -> "Choice Cards",
      Some("cards xxf:horizontal") -> "Horizontal Choice Cards",
      Some("dropdown")             -> "Dropdown Menu"
    ))
    assertVaryTypes(XF -> "select"      , XS -> "string" )(Seq(
      Some("full")                 -> "Checkboxes",
      Some("full xxf:horizontal")  -> "Horizontal Checkboxes",
      Some("scroll")               -> "Scrollable Checkboxes",
      Some("cards")                -> "Choice Cards",
      Some("cards xxf:horizontal") -> "Horizontal Choice Cards"
    ))
    assertVaryTypes(FR -> "open-select1", XS -> "string" )(Seq(
      Some("full")                 -> "Radio Buttons with \"Other\"",
      Some("full xxf:horizontal")  -> "Horizontal Radio Buttons with \"Other\"",
      Some("minimal")              -> "Dropdown with \"Other\""
    ))
    assertVaryTypes(FR -> "open-select" , XS -> "string" )(Seq(
      Some("full")                 -> "Checkboxes with \"Other\"",
      Some("full xxf:horizontal")  -> "Horizontal Checkboxes with \"Other\""
    ))
    assertVaryTypes(XF -> "input"     , XS -> "boolean")(Seq(Some("checkbox") -> "Single Checkbox", Some("switch") -> "Toggle Switch", Some("yesno") -> "Yes/No Answer"))

    assertVaryTypes(XF -> "textarea"  , XS -> "string" , List(QName("mediatype") -> "text/html"))(Seq(None-> "Formatted Text Area", Some("character-counter") -> "With Character Counter") )
    assertVaryTypes(FR -> "attachment", XS -> "string" , List(QName("mediatype") -> "text/html"))(Nil)
    assertVaryTypes(FR -> "attachment", XS -> "string" , List(QName("multiple")  -> "true")     )(Nil)
  }

  describe("Control description") {

    def assertVaryTypes(
      elemName  : QName,
      dataType  : QName,
      appearance: Option[String],
      atts      : Iterable[(QName, String)] = Nil,
    )(
      expected : String
    ): Unit =
      it(s"must pass with $elemName/$dataType/$appearance${if (atts.nonEmpty) "/"}${atts.mkString("=")}") {
        for {
          newT <- List(dataType, Types.getVariationTypeOrKeep(dataType))
        } locally {
          assert(findControlDescription(elemName, newT, BindingDescriptor.updateAttAppearance(atts, appearance), "en").contains(expected))
        }
      }

    assertVaryTypes(XF -> "input"     , XS -> "string" , None            )("Input Field")
    assertVaryTypes(XF -> "textarea"  , XS -> "string" , None            )("Plain Text Area")
    assertVaryTypes(XF -> "input"     , XS -> "decimal", None            )("Number")
    assertVaryTypes(XF -> "input"     , XS -> "decimal", Some("stepper") )("Numeric Stepper")
    assertVaryTypes(XF -> "input"     , XS -> "integer", Some("stepper") )("Numeric Stepper")
    assertVaryTypes(XF -> "input"     , XS -> "date"           , None            )("Date")
    assertVaryTypes(XF -> "input"     , XS -> "dayTimeDuration", None            )("Duration")
    assertVaryTypes(XF -> "select1"     , XS -> "string" , Some("full")                 )("Radio Buttons")
    assertVaryTypes(XF -> "select1"     , XS -> "string" , Some("full xxf:horizontal")  )("Horizontal Radio Buttons")
    assertVaryTypes(XF -> "select1"     , XS -> "string" , Some("xxf:horizontal full")  )("Horizontal Radio Buttons")
    assertVaryTypes(XF -> "select1"     , XS -> "string" , Some("segmented")            )("Segmented Buttons")
    assertVaryTypes(XF -> "select1"     , XS -> "string" , Some("cards")                )("Choice Cards")
    assertVaryTypes(XF -> "select1"     , XS -> "string" , Some("cards xxf:horizontal") )("Horizontal Choice Cards")
    assertVaryTypes(XF -> "select1"     , XS -> "string" , Some("dropdown")             )("Dropdown Menu")
    assertVaryTypes(XF -> "select"      , XS -> "string" , Some("full")                 )("Checkboxes")
    assertVaryTypes(XF -> "select"      , XS -> "string" , Some("full xxf:horizontal")  )("Horizontal Checkboxes")
    assertVaryTypes(XF -> "select"      , XS -> "string" , Some("scroll")               )("Scrollable Checkboxes")
    assertVaryTypes(XF -> "select"      , XS -> "string" , Some("cards")                )("Choice Cards")
    assertVaryTypes(XF -> "select"      , XS -> "string" , Some("cards xxf:horizontal") )("Horizontal Choice Cards")
    assertVaryTypes(FR -> "open-select1", XS -> "string" , None                         )("Radio Buttons with \"Other\"")
    assertVaryTypes(FR -> "open-select1", XS -> "string" , Some("full xxf:horizontal")  )("Horizontal Radio Buttons with \"Other\"")
    assertVaryTypes(FR -> "open-select" , XS -> "string" , None                         )("Checkboxes with \"Other\"")
    assertVaryTypes(FR -> "open-select" , XS -> "string" , Some("full xxf:horizontal")  )("Horizontal Checkboxes with \"Other\"")

    assertVaryTypes(XF -> "textarea"  , XS -> "string" , None, List(QName("mediatype") -> "text/html") )("Formatted Text Area")
    assertVaryTypes(XF -> "textarea"  , XS -> "string" , None, List(QName("mediatype") -> "text/plain"))("Plain Text Area")
    assertVaryTypes(FR -> "attachment", XS -> "string" , None, Nil                                     )("Single File Attachment")
    assertVaryTypes(FR -> "attachment", XS -> "string" , None, List(QName("multiple")  -> "false")     )("Single File Attachment")
    assertVaryTypes(FR -> "attachment", XS -> "string" , None, List(QName("multiple")  -> "true")      )("Multiple File Attachments")

    assertVaryTypes(FR -> "us-ein",     XS -> "string" , None, Nil      )("US Employer Identification Number (EIN)")
    assertVaryTypes(FR -> "us-ein",     FR -> "us-ein" , None, Nil      )("US Employer Identification Number (EIN)")
    assertVaryTypes(FR -> "color",        XS -> "string"      , None, Nil      )("Color")
    assertVaryTypes(FR -> "color",        FR -> "color"       , None, Nil      )("Color")
    assertVaryTypes(FR -> "masked-input", XS -> "string"      , None, Nil      )("Masked Input")
    assertVaryTypes(FR -> "masked-input", FR -> "masked-input", None, Nil      )("Masked Input")
  }

  describe("createGroups") {
    it("must group connected QNames into disjoint sets") {
      val a = QName("a")
      val b = QName("b")
      val c = QName("c")
      val d = QName("d")
      val e = QName("e")

      val pairs = List(
        a -> b,
        b -> c,
        d -> e
      )

      val result = BindingOps.createGroups(pairs)

      val group1 = Set(a, b, c)
      val group2 = Set(d, e)

      assert(result(a) == group1)
      assert(result(b) == group1)
      assert(result(c) == group1)

      assert(result(d) == group2)
      assert(result(e) == group2)

      assert(result(a) eq result(b))
      assert(result(a) eq result(c))

      assert(result(d) eq result(e))

      assert(result.size == 5)
    }

    it("must handle empty list") {
      assert(BindingOps.createGroups(Nil).isEmpty)
    }

    it("must handle single elements / loops") {
      val a = QName("a")
      val result = BindingOps.createGroups(List(a -> a))
      assert(result(a) == Set(a))
    }
  }

  describe("Control conversions") {

    def isSameQName(q1: QName, q2: QName): Boolean = q1 == q2

    def expectedTextConversions(
      current        : QName,
      removeMediatype: Boolean = false,
      numberDatatype : QName   = XF -> "decimal"
    ): List[ConversionResult] = {
      val removed = if (removeMediatype) Set(QName("mediatype")) else Set.empty[QName]
      List(
        ConversionResult(
          fullLabelOpt  = Some("Text Field"),
          value         = XF -> "input",
          elemName      = XF -> "input",
          appearanceOpt = None,
          datatypeOpt   = None,
          addAtts       = Set.empty,
          removeAtts    = removed,
          iconPathOpt   = None,
          iconClasses   = "fa fa-fw fa-font",
          isCurrent     = isSameQName(current, XF -> "input")
        ),
        ConversionResult(
          fullLabelOpt  = Some("Plain Text Area"),
          value         = XF -> "textarea",
          elemName      = XF -> "textarea",
          appearanceOpt = None,
          datatypeOpt   = None,
          addAtts       = Set.empty,
          removeAtts    = removed,
          iconPathOpt   = None,
          iconClasses   = "fa fa-fw fa-align-left",
          isCurrent     = isSameQName(current, XF -> "textarea")
        ),
        ConversionResult(
          fullLabelOpt  = Some("Formatted Text Area"),
          value         = FR -> "tinymce",
          elemName      = XF -> "textarea",
          appearanceOpt = None,
          datatypeOpt   = Some(XF -> "HTMLFragment"),
          addAtts       = Set(QName("mediatype") -> "text/html"),
          removeAtts    = Set.empty,
          iconPathOpt   = None,
          iconClasses   = "fa fa-fw fa-html5",
          isCurrent     = isSameQName(current, FR -> "tinymce")
        ),
        ConversionResult(
          fullLabelOpt  = Some("Markdown Text Area"),
          value         = FR -> "markdown",
          elemName      = XF -> "textarea",
          appearanceOpt = None,
          datatypeOpt   = None,
          addAtts       = Set(QName("mediatype") -> "text/markdown"),
          removeAtts    = Set.empty,
          iconPathOpt   = None,
          iconClasses   = "fa-brands fa-fw fa-markdown",
          isCurrent     = isSameQName(current, FR -> "markdown")
        ),
        ConversionResult(
          fullLabelOpt  = Some("Number"),
          value         = FR -> "number",
          elemName      = FR -> "number",
          appearanceOpt = None,
          datatypeOpt   = Some(numberDatatype),
          addAtts       = Set.empty,
          removeAtts    = removed,
          iconPathOpt   = None,
          iconClasses   = "fa fa-fw fa-superscript",
          isCurrent     = isSameQName(current, FR -> "number")
        ),
        ConversionResult(
          fullLabelOpt  = Some("Calculated Value"),
          value         = XF -> "output",
          elemName      = XF -> "output",
          appearanceOpt = None,
          datatypeOpt   = None,
          addAtts       = Set.empty,
          removeAtts    = removed,
          iconPathOpt   = None,
          iconClasses   = "fa fa-fw fa-calculator",
          isCurrent     = isSameQName(current, XF -> "output")
        ),
        ConversionResult(
          fullLabelOpt  = Some("Hidden Field"),
          value         = FR -> "hidden",
          elemName      = FR -> "hidden",
          appearanceOpt = None,
          datatypeOpt   = None,
          addAtts       = Set.empty,
          removeAtts    = removed,
          iconPathOpt   = None,
          iconClasses   = "fa fa-fw fa-eye-slash",
          isCurrent     = isSameQName(current, FR -> "hidden")
        )
      )
    }

    def expectedDateTimeConversions(
      current   : QName,
      removeAtts: Set[QName] = Set.empty
    ): List[ConversionResult] =
      List(
        ConversionResult(
          fullLabelOpt  = Some("Date"),
          value         = FR -> "date",
          elemName      = FR -> "date",
          appearanceOpt = None,
          datatypeOpt   = Some(XF -> "date"),
          addAtts       = Set.empty,
          removeAtts    = removeAtts,
          iconPathOpt   = None,
          iconClasses   = "fa fa-fw fa-calendar",
          isCurrent     = isSameQName(current, FR -> "date")
        ),
        ConversionResult(
          fullLabelOpt  = Some("Dropdown Month/Year"),
          value         = FR -> "dropdown-month-year",
          elemName      = FR -> "dropdown-month-year",
          appearanceOpt = None,
          datatypeOpt   = Some(XF -> "gYearMonth"),
          addAtts       = Set.empty,
          removeAtts    = removeAtts,
          iconPathOpt   = None,
          iconClasses   = "fa fa-fw fa-calendar",
          isCurrent     = isSameQName(current, FR -> "dropdown-month-year")
        ),
        ConversionResult(
          fullLabelOpt  = Some("Year"),
          value         = FR -> "year",
          elemName      = FR -> "year",
          appearanceOpt = None,
          datatypeOpt   = Some(XF -> "gYear"),
          addAtts       = Set.empty,
          removeAtts    = removeAtts,
          iconPathOpt   = None,
          iconClasses   = "fa fa-fw fa-calendar",
          isCurrent     = isSameQName(current, FR -> "year")
        ),
        ConversionResult(
          fullLabelOpt  = Some("Time"),
          value         = FR -> "time",
          elemName      = FR -> "time",
          appearanceOpt = None,
          datatypeOpt   = Some(XF -> "time"),
          addAtts       = Set.empty,
          removeAtts    = removeAtts,
          iconPathOpt   = None,
          iconClasses   = "fa fa-fw fa-clock-o",
          isCurrent     = isSameQName(current, FR -> "time")
        ),
        ConversionResult(
          fullLabelOpt  = Some("Date and Time"),
          value         = FR -> "datetime",
          elemName      = FR -> "datetime",
          appearanceOpt = None,
          datatypeOpt   = Some(XF -> "dateTime"),
          addAtts       = Set.empty,
          removeAtts    = removeAtts,
          iconPathOpt   = None,
          iconClasses   = "fa fa-fw fa-calendar-o",
          isCurrent     = isSameQName(current, FR -> "datetime")
        ),
        ConversionResult(
          fullLabelOpt  = Some("Partial Date"),
          value         = FR -> "partial-date",
          elemName      = FR -> "partial-date",
          appearanceOpt = None,
          datatypeOpt   = Some(FR -> "partial-date"),
          addAtts       = Set.empty,
          removeAtts    = removeAtts,
          iconPathOpt   = None,
          iconClasses   = "fa fa-fw fa-calendar",
          isCurrent     = isSameQName(current, FR -> "partial-date")
        )
      )

    def expectedAttachmentConversions(
      current   : QName,
      removeAtts: Set[QName] = Set.empty
    ): List[ConversionResult] =
      List(
        ConversionResult(
          fullLabelOpt    = Some("Single File Attachment"),
          value           = FR -> "attachment",
          elemName        = FR -> "attachment",
          appearanceOpt   = None,
          datatypeOpt     = Some(XF -> "anyURI"),
          addAtts         = Set.empty,
          removeAtts      = removeAtts,
          standardClasses = "fr-attachment",
          iconPathOpt     = None,
          iconClasses     = "fa fa-fw fa-file-o",
          isCurrent       = isSameQName(current, FR -> "attachment")
        ),
        ConversionResult(
          fullLabelOpt    = Some("Single Image Attachment"),
          value           = FR -> "image-attachment",
          elemName        = FR -> "image-attachment",
          appearanceOpt   = None,
          datatypeOpt     = Some(XF -> "anyURI"),
          addAtts         = Set.empty,
          removeAtts      = removeAtts,
          standardClasses = "fr-attachment",
          iconPathOpt     = None,
          iconClasses     = "fa fa-fw fa-file-image-o",
          isCurrent       = isSameQName(current, FR -> "image-attachment")
        ),
        ConversionResult(
          fullLabelOpt    = Some("Video Attachment"),
          value           = FR -> "video-attachment",
          elemName        = FR -> "video-attachment",
          appearanceOpt   = None,
          datatypeOpt     = Some(XF -> "anyURI"),
          addAtts         = Set.empty,
          removeAtts      = removeAtts,
          standardClasses = "fr-attachment",
          iconPathOpt     = None,
          iconClasses     = "fa fa-fw fa-file-video-o",
          isCurrent       = isSameQName(current, FR -> "video-attachment")
        ),
        ConversionResult(
          fullLabelOpt    = Some("Audio Attachment"),
          value           = FR -> "audio-attachment",
          elemName        = FR -> "audio-attachment",
          appearanceOpt   = None,
          datatypeOpt     = Some(XF -> "anyURI"),
          addAtts         = Set.empty,
          removeAtts      = removeAtts,
          standardClasses = "fr-attachment",
          iconPathOpt     = None,
          iconClasses     = "fa fa-fw fa-file-audio-o",
          isCurrent       = isSameQName(current, FR -> "audio-attachment")
        )
      )

    def expectedStaticMediaConversions(
      current: QName
    ): List[ConversionResult] =
      List(
        ConversionResult(
          fullLabelOpt    = Some("Static Image"),
          value           = FR -> "image",
          elemName        = FR -> "image",
          appearanceOpt   = None,
          datatypeOpt     = Some(XF -> "anyURI"),
          addAtts         = Set.empty,
          removeAtts      = Set.empty,
          standardClasses = "fr-static-attachment",
          iconPathOpt     = None,
          iconClasses     = "fa fa-fw fa-image",
          isCurrent       = isSameQName(current, FR -> "image")
        ),
        ConversionResult(
          fullLabelOpt    = Some("Static Video"),
          value           = FR -> "video",
          elemName        = FR -> "video",
          appearanceOpt   = None,
          datatypeOpt     = Some(XF -> "anyURI"),
          addAtts         = Set.empty,
          removeAtts      = Set.empty,
          standardClasses = "fr-static-attachment",
          iconPathOpt     = None,
          iconClasses     = "fa fa-fw fa-file-video-o",
          isCurrent       = isSameQName(current, FR -> "video")
        ),
        ConversionResult(
          fullLabelOpt    = Some("Static Audio"),
          value           = FR -> "audio",
          elemName        = FR -> "audio",
          appearanceOpt   = None,
          datatypeOpt     = Some(XF -> "anyURI"),
          addAtts         = Set.empty,
          removeAtts      = Set.empty,
          standardClasses = "fr-static-attachment",
          iconPathOpt     = None,
          iconClasses     = "fa fa-fw fa-file-audio-o",
          isCurrent       = isSameQName(current, FR -> "audio")
        )
      )

    def expectedRangeScaleRatingConversions(
      current   : QName,
      removeAtts: Set[QName] = Set.empty
    ): List[ConversionResult] =
      List(
        ConversionResult(
          fullLabelOpt  = Some("Slider"),
          value         = FR -> "range",
          elemName      = FR -> "range",
          appearanceOpt = None,
          datatypeOpt   = Some(FR -> "range"),
          addAtts       = Set.empty,
          removeAtts    = removeAtts,
          iconPathOpt   = None,
          iconClasses   = "fa fa-fw fa-sliders",
          isCurrent     = isSameQName(current, FR -> "range")
        ),
        ConversionResult(
          fullLabelOpt  = Some("Star Rating"),
          value         = FR -> "rating",
          elemName      = FR -> "rating",
          appearanceOpt = None,
          datatypeOpt   = Some(FR -> "rating"),
          addAtts       = Set.empty,
          removeAtts    = removeAtts,
          iconPathOpt   = None,
          iconClasses   = "fa fa-fw fa-star",
          isCurrent     = isSameQName(current, FR -> "rating")
        ),
        ConversionResult(
          fullLabelOpt  = Some("Linear Scale"),
          value         = FR -> "scale",
          elemName      = FR -> "scale",
          appearanceOpt = None,
          datatypeOpt   = Some(FR -> "scale"),
          addAtts       = Set.empty,
          removeAtts    = removeAtts,
          iconPathOpt   = None,
          iconClasses   = "fa fa-fw fa-ruler-horizontal",
          isCurrent     = isSameQName(current, FR -> "scale")
        )
      )

    def expectedOpenSelectionConversions(
      current: QName
    ): List[ConversionResult] =
      List(
        ConversionResult(
          fullLabelOpt  = Some("Radio Buttons with \"Other\""),
          value         = FR -> "open-select1",
          elemName      = FR -> "open-select1",
          appearanceOpt = None,
          datatypeOpt   = None,
          addAtts       = Set.empty,
          removeAtts    = Set.empty,
          iconPathOpt   = None,
          iconClasses   = "fa fa-fw fa-dot-circle-o",
          isCurrent     = isSameQName(current, FR -> "open-select1")
        ),
        ConversionResult(
          fullLabelOpt  = Some("Checkboxes with \"Other\""),
          value         = FR -> "open-select",
          elemName      = FR -> "open-select",
          appearanceOpt = None,
          datatypeOpt   = None,
          addAtts       = Set.empty,
          removeAtts    = Set.empty,
          iconPathOpt   = None,
          iconClasses   = "fa fa-fw fa-check-square-o",
          isCurrent     = isSameQName(current, FR -> "open-select")
        )
      )

    val testValues: List[(Elem, QName, Option[String], List[ConversionResult])] =
      List(
        (<xf:input xmlns:xf="http://www.w3.org/2002/xforms"/>,                        XS -> "string",       Some("minimal"),           expectedTextConversions(XF -> "input")),
        (<xf:input xmlns:xf="http://www.w3.org/2002/xforms"/>,                        XS -> "string",       Some("character-counter"), expectedTextConversions(XF -> "input")),
        (<xf:textarea xmlns:xf="http://www.w3.org/2002/xforms"/>,                     XS -> "string",       None,                      expectedTextConversions(XF -> "textarea")),
        (<xf:textarea xmlns:xf="http://www.w3.org/2002/xforms"/>,                     XS -> "string",       Some("character-counter"), expectedTextConversions(XF -> "textarea")),
        (<xf:textarea xmlns:xf="http://www.w3.org/2002/xforms" mediatype="text/html"/>, XF -> "HTMLFragment", None,                    expectedTextConversions(FR -> "tinymce", removeMediatype = true)),
        (<xf:textarea xmlns:xf="http://www.w3.org/2002/xforms" mediatype="text/html"/>, XF -> "HTMLFragment", Some("character-counter"), expectedTextConversions(FR -> "tinymce", removeMediatype = true)),
        (<fr:tinymce xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>,              XF -> "HTMLFragment", None,                      expectedTextConversions(FR -> "tinymce")),
        (<fr:number xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>,               XS -> "decimal",      None,                      expectedTextConversions(FR -> "number")),
        (<fr:number xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>,               XS -> "integer",      None,                      expectedTextConversions(FR -> "number", numberDatatype = XF -> "integer")),
        (<fr:percentage xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>,           XF -> "decimal",      None,                      expectedTextConversions(FR -> "number")),
        (<xf:input xmlns:xf="http://www.w3.org/2002/xforms"/>,                        XF -> "decimal",      Some("xxf:percentage"),    expectedTextConversions(FR -> "number")),
        (<fr:number-stepper xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>,       XF -> "integer",      None,                      expectedTextConversions(FR -> "number", numberDatatype = XF -> "integer")),
        (<xf:output xmlns:xf="http://www.w3.org/2002/xforms"/>,                       XS -> "string",       None,                      expectedTextConversions(XF -> "output")),
        (<xf:output xmlns:xf="http://www.w3.org/2002/xforms"/>,                       XS -> "string",       Some("full"),              expectedTextConversions(XF -> "output")),
        (<fr:hidden xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>,               XS -> "string",       None,                      expectedTextConversions(FR -> "hidden")),
        (<fr:date xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>,                 XS -> "date",         None,                      expectedDateTimeConversions(FR -> "date")),
        (<fr:dropdown-date xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>,        XF -> "date",         None,                      expectedDateTimeConversions(FR -> "date")),
        (<fr:dropdown-month-year xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>,  XF -> "gYearMonth",   None,                      expectedDateTimeConversions(FR -> "dropdown-month-year")),
        (<fr:dropdown-year xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>,        XF -> "gYear",        None,                      expectedDateTimeConversions(FR -> "year")),
        (<fr:time xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>,                 XF -> "time",         None,                      expectedDateTimeConversions(FR -> "time")),
        (<fr:datetime xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>,             XF -> "dateTime",     None,                      expectedDateTimeConversions(FR -> "datetime")),
        (<fr:partial-date xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>,         FR -> "partial-date", None,                      expectedDateTimeConversions(FR -> "partial-date")),
        (<fr:attachment xmlns:fr="http://orbeon.org/oxf/xml/form-runner" class="fr-attachment"/>,
          XF -> "anyURI", None, expectedAttachmentConversions(FR -> "attachment")),
        (<fr:image-attachment xmlns:fr="http://orbeon.org/oxf/xml/form-runner" class="fr-attachment"/>,
          XF -> "anyURI", None, expectedAttachmentConversions(FR -> "image-attachment")),
        (<fr:video-attachment xmlns:fr="http://orbeon.org/oxf/xml/form-runner" class="fr-attachment"/>,
          XF -> "anyURI", None, expectedAttachmentConversions(FR -> "video-attachment")),
        (<fr:audio-attachment xmlns:fr="http://orbeon.org/oxf/xml/form-runner" class="fr-attachment"/>,
          XF -> "anyURI", None, expectedAttachmentConversions(FR -> "audio-attachment")),
        (<fr:attachment xmlns:fr="http://orbeon.org/oxf/xml/form-runner" class="fr-attachment" allow-download="false"/>,
          XF -> "anyURI", None, expectedAttachmentConversions(FR -> "attachment", removeAtts = Set(QName("allow-download")))),
        (<fr:attachment xmlns:fr="http://orbeon.org/oxf/xml/form-runner" multiple="true" class="fr-attachment"/>,
          XS -> "string", None, Nil),
        (<fr:image-attachment xmlns:fr="http://orbeon.org/oxf/xml/form-runner" multiple="true" class="fr-attachment"/>,
          XS -> "string", None, Nil),
        (<fr:image xmlns:fr="http://orbeon.org/oxf/xml/form-runner" class="fr-static-attachment"/>,
          XF -> "anyURI", None, expectedStaticMediaConversions(FR -> "image")),
        (<fr:video xmlns:fr="http://orbeon.org/oxf/xml/form-runner" class="fr-static-attachment"/>,
          XF -> "anyURI", None, expectedStaticMediaConversions(FR -> "video")),
        (<fr:audio xmlns:fr="http://orbeon.org/oxf/xml/form-runner" class="fr-static-attachment"/>,
          XF -> "anyURI", None, expectedStaticMediaConversions(FR -> "audio")),
        (<fr:range xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>,                FR -> "range",        None,                      expectedRangeScaleRatingConversions(FR -> "range")),
        (<xf:range xmlns:xf="http://www.w3.org/2002/xforms"/>,                        FR -> "range",        None,                      expectedRangeScaleRatingConversions(FR -> "range")),
        (<fr:rating xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>,               FR -> "rating",       None,                      expectedRangeScaleRatingConversions(FR -> "rating")),
        (<fr:scale xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>,                FR -> "scale",        None,                      expectedRangeScaleRatingConversions(FR -> "scale")),
        (<fr:open-select1 xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>,         XS -> "string",       None,                      expectedOpenSelectionConversions(FR -> "open-select1")),
        (<fr:open-select1 xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>,         XS -> "string",       Some("minimal"),           expectedOpenSelectionConversions(FR -> "open-select1")),
        (<fr:open-select xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>,          XS -> "string",       None,                      expectedOpenSelectionConversions(FR -> "open-select")),
        (<xf:select1 xmlns:xf="http://www.w3.org/2002/xforms" appearance="full"/>,    XS -> "string",       Some("full"),              Nil),
        (<fr:date-range xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>,           FR -> "date-range",   None,                      Nil),
      )

    for ((controlElem, datatype, appearance, expected) <- testValues) {
      it(s"must find possible control conversions for $controlElem/$datatype/$appearance") {
        withActionAndFBDoc(TemplateWithSingleControlDoc) { implicit ctx =>
          val conversions =
            FormBuilder.findPossibleControlConversions2(
              originalControlElemAtts = controlElem,
              originalDatatype        = datatype,
              originalAppearanceOpt   = appearance,
              lang                    = "en"
            )

          assert(conversions == expected)
        }
      }
    }

    it("must strip incompatible custom attributes in XML conversion elements") {
      withActionAndFBDoc(TemplateWithSingleControlDoc) { implicit ctx =>
        val conversionsXml =
          FormBuilder.findPossibleControlConversions(
            controlName            = "control-1",
            controlElemAtts        = <fr:attachment xmlns:fr="http://orbeon.org/oxf/xml/form-runner" allow-download="false"/>,
            currentDatatypeOrEmpty = "anyURI",
            desiredAppearance      = "",
            lang                   = "en"
          )

        val imageConversionElem =
          conversionsXml.find(c => (c / "value").stringValue == s"Q{$FR}image-attachment").get / "element" / *

        assert(imageConversionElem.nonEmpty)
        assert(imageConversionElem.head.name == "fr:image-attachment")
        assert(imageConversionElem.head.attValueOpt("allow-download").isEmpty)
      }
    }

    it("must find target appearances for converted controls before saving") {
      withActionAndFBDoc(TemplateWithSingleControlDoc) { implicit ctx =>
        val tinyMceAppearances =
          FormBuilder.possibleAppearancesByControlNameAsXML(
            controlName        = "control-1",
            isInitialLoad      = false,
            controlElemAtts    = <xf:textarea xmlns:xf="http://www.w3.org/2002/xforms" mediatype="text/html"/>,
            newDatatypeOrEmpty = "HTMLFragment",
            desiredAppearance  = ""
          )

        assert(tinyMceAppearances.find(_.localname == "description").map(_.stringValue).contains("Formatted Text Area"))
        assert(tinyMceAppearances.filter(_.localname == "appearance").map(a => (a / "value").stringValue -> (a attValue "current")).toSet == Set(
          ""                  -> "true",
          "character-counter" -> "false"
        ))

        val percentageAppearances =
          FormBuilder.possibleAppearancesByControlNameAsXML(
            controlName        = "control-1",
            isInitialLoad      = false,
            controlElemAtts    = <fr:number xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>,
            newDatatypeOrEmpty = "decimal",
            desiredAppearance  = "xxf:percentage"
          )

        assert(percentageAppearances.exists(a => (a / "value").stringValue == "xxf:percentage" && (a attValue "current") == "true"))
      }
    }

    it("must save control element, attributes, appearance, and datatype across conversions") {
      withActionAndFBDoc(TemplateWithSingleControlDoc) { implicit ctx =>
        val Control1             = "control-1"
        val globalAlertAsXML: NodeInfo = AlertDetails(None, Nil, global = true, params = Nil).toXML

        def datatypeValidationXml(builtinType: String, schemaType: String = ""): NodeInfo =
          <validation type="datatype" id="" level="error" default-alert="true">
            <builtin-type>{builtinType}</builtin-type>
            <builtin-type-required>false</builtin-type-required>
            <schema-type>{schemaType}</schema-type>
            <alert global="true"/>
          </validation>

        // 1. `xf:input` -> Plain Text Area (`xf:textarea`, same `string` datatype)
        FormBuilder.saveControlElemAtts(
          controlName     = Control1,
          controlElemAtts = <xf:textarea xmlns:xf="http://www.w3.org/2002/xforms"/>,
          keepCurrentAtts = false
        )
        FormBuilder.writeAlertsAndValidationsAsXML(
          controlName        = Control1,
          newAppearance      = "",
          defaultAlertElem   = globalAlertAsXML,
          validationElems    = List(datatypeValidationXml("string")),
          controlElemUpdated = true
        )

        assert(findControlByName(Control1).map(_.name).contains("xf:textarea"))
        assert(findControlByName(Control1).flatMap(_.attValueOpt("appearance")).isEmpty)
        assert(findBindByName(Control1).flatMap(_.attValueOpt("type")).isEmpty)

        // 2. `xf:textarea` -> Formatted Text Area (`xf:textarea[@mediatype = 'text/html']` + `xf:HTMLFragment`)
        FormBuilder.saveControlElemAtts(
          controlName     = Control1,
          controlElemAtts = <xf:textarea xmlns:xf="http://www.w3.org/2002/xforms" mediatype="text/html"/>,
          keepCurrentAtts = false
        )
        FormBuilder.writeAlertsAndValidationsAsXML(
          controlName        = Control1,
          newAppearance      = "",
          defaultAlertElem   = globalAlertAsXML,
          validationElems    = List(datatypeValidationXml("HTMLFragment")),
          controlElemUpdated = true
        )

        assert(findControlByName(Control1).map(_.name).contains("xf:textarea"))
        assert(findControlByName(Control1).flatMap(_.attValueOpt("mediatype")).contains("text/html"))
        assert(findBindByName(Control1).flatMap(_.attValueOpt("type")).contains("xf:HTMLFragment"))

        // 3. Formatted Text Area -> Text Field (`xf:input` removes `@mediatype` and resets datatype to `string`)
        FormBuilder.saveControlElemAtts(
          controlName     = Control1,
          controlElemAtts = <xf:input xmlns:xf="http://www.w3.org/2002/xforms"/>,
          keepCurrentAtts = false
        )
        FormBuilder.writeAlertsAndValidationsAsXML(
          controlName        = Control1,
          newAppearance      = "",
          defaultAlertElem   = globalAlertAsXML,
          validationElems    = List(datatypeValidationXml("string")),
          controlElemUpdated = true
        )

        assert(findControlByName(Control1).map(_.name).contains("xf:input"))
        assert(findControlByName(Control1).flatMap(_.attValueOpt("mediatype")).isEmpty)
        assert(findBindByName(Control1).flatMap(_.attValueOpt("type")).isEmpty)

        // 4. Text Field -> Number with Percentage appearance (`fr:percentage` + `xf:decimal`)
        FormBuilder.saveControlElemAtts(
          controlName     = Control1,
          controlElemAtts = <fr:number xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>,
          keepCurrentAtts = false
        )
        FormBuilder.writeAlertsAndValidationsAsXML(
          controlName        = Control1,
          newAppearance      = "xxf:percentage",
          defaultAlertElem   = globalAlertAsXML,
          validationElems    = List(datatypeValidationXml("decimal")),
          controlElemUpdated = true
        )

        assert(findControlByName(Control1).map(_.name).contains("fr:percentage"))
        assert(findControlByName(Control1).flatMap(_.attValueOpt("appearance")).isEmpty)
        assert(findBindByName(Control1).flatMap(_.attValueOpt("type")).contains("xf:decimal"))

        // 5. Star Rating -> Slider (`fr:range` + `fr:range` schema type, keeping primary `fr:range` over `xf:range`)
        FormBuilder.saveControlElemAtts(
          controlName     = Control1,
          controlElemAtts = <fr:range xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>,
          keepCurrentAtts = false
        )
        FormBuilder.writeAlertsAndValidationsAsXML(
          controlName        = Control1,
          newAppearance      = "",
          defaultAlertElem   = globalAlertAsXML,
          validationElems    = List(datatypeValidationXml("", "fr:range")),
          controlElemUpdated = true
        )

        assert(findControlByName(Control1).map(_.name).contains("fr:range"))
        assert(findControlByName(Control1).flatMap(_.attValueOpt("appearance")).isEmpty)
        assert(findBindByName(Control1).flatMap(_.attValueOpt("type")).contains("fr:range"))

        // 6. Universal conversion: Text Field -> Searchable Combobox (`xf:select1[@appearance='search'][@selection='open']`)
        FormBuilder.saveControlElemAtts(
          controlName          = Control1,
          controlElemAtts      = <xf:input xmlns:xf="http://www.w3.org/2002/xforms"/>,
          keepCurrentAtts      = false,
          newAppearanceOrEmpty = "",
          newDatatypeOrEmpty   = "string"
        )
        FormBuilder.writeAlertsAndValidationsAsXML(
          controlName        = Control1,
          newAppearance      = "",
          defaultAlertElem   = globalAlertAsXML,
          validationElems    = List(datatypeValidationXml("string")),
          controlElemUpdated = true
        )

        FormBuilder.saveControlElemAtts(
          controlName          = Control1,
          controlElemAtts      = <xf:select1 xmlns:xf="http://www.w3.org/2002/xforms" selection="open"/>,
          keepCurrentAtts      = false,
          newAppearanceOrEmpty = "search",
          newDatatypeOrEmpty   = "string"
        )
        FormBuilder.writeAlertsAndValidationsAsXML(
          controlName        = Control1,
          newAppearance      = "search",
          defaultAlertElem   = globalAlertAsXML,
          validationElems    = List(datatypeValidationXml("string")),
          controlElemUpdated = true
        )

        val comboboxCtrl = findControlByName(Control1).get
        assert(comboboxCtrl.name == "xf:select1")
        assert(comboboxCtrl.attValueOpt("appearance").contains("search"))
        assert(comboboxCtrl.attValueOpt("selection").contains("open"))
        assert((comboboxCtrl / "*:itemset" /@ "ref").stringValue == s"$$form-resources/$Control1/item")
        assert(FormBuilder.findResourceHolders(Control1).forall(h => (h / "item").size == 3))

        // 7. Universal conversion back: Searchable Combobox -> Text Field removes `<xf:itemset>` and `<item>` resources
        FormBuilder.saveControlElemAtts(
          controlName          = Control1,
          controlElemAtts      = <xf:input xmlns:xf="http://www.w3.org/2002/xforms"/>,
          keepCurrentAtts      = false,
          newAppearanceOrEmpty = "",
          newDatatypeOrEmpty   = "string"
        )
        FormBuilder.writeAlertsAndValidationsAsXML(
          controlName        = Control1,
          newAppearance      = "",
          defaultAlertElem   = globalAlertAsXML,
          validationElems    = List(datatypeValidationXml("string")),
          controlElemUpdated = true
        )

        val inputCtrl = findControlByName(Control1).get
        assert(inputCtrl.name == "xf:input")
        assert(inputCtrl.attValueOpt("selection").isEmpty)
        assert((inputCtrl / "*:itemset").isEmpty)
        assert(FormBuilder.findResourceHolders(Control1).forall(h => (h / "item").isEmpty))
      }
    }

    it("must discover two-tier universal conversion targets while excluding non-value controls") {
      withActionAndFBDoc(TemplateWithSingleControlDoc) { implicit ctx =>

        val inputAll = FormBuilder.findPossibleControlConversions2(
          originalControlElemAtts = <xf:input xmlns:xf="http://www.w3.org/2002/xforms"/>,
          originalDatatype        = XS_STRING_QNAME,
          originalAppearanceOpt   = None,
          lang                    = "en",
          includeAllControls      = true
        )

        val compatibleValues = inputAll.filter(_.compatible).map(_.value).toSet
        val universalResults = inputAll.filterNot(_.compatible)
        val universalValues  = universalResults.map(_.value).toSet
        val universalLabels  = universalResults.flatMap(_.fullLabelOpt).toSet

        // Compatible tier unchanged
        assert(compatibleValues.contains(XFORMS_INPUT_QNAME))
        assert(compatibleValues.contains(XFORMS_TEXTAREA_QNAME))
        assert(! compatibleValues.contains(QName("open-select1", "fr", FR)))

        // Universal tier includes appearance-specific toolbox controls directly by display name
        assert(universalLabels.contains("Searchable Combobox"))
        assert(universalLabels.contains("Dropdown with \"Other\""))
        assert(universalLabels.contains("Radio Buttons"))
        assert(universalLabels.contains("Checkboxes"))
        assert(universalLabels.contains("Partial Date"))
        assert(universalValues.contains(QName("partial-date", "fr", FR)))
        assert(universalValues.contains(QName("dropdown-select1", "fr", FR)))
        assert(universalValues.contains(QName("attachment", "fr", FR)))

        // Non-value / incompatible controls excluded from universal tier
        assert(! universalValues.contains(XFORMS_TRIGGER_QNAME))
        assert(! universalValues.contains(QName("explanation", "fr", FR)))
        assert(! universalValues.contains(QName("image", "fr", FR)))
        assert(! universalValues.contains(QName("wpaint", "fr", FR)))

        // Non-value source controls do not receive universal conversion targets
        val triggerAll = FormBuilder.findPossibleControlConversions2(
          originalControlElemAtts = <xf:trigger xmlns:xf="http://www.w3.org/2002/xforms"/>,
          originalDatatype        = XS_STRING_QNAME,
          originalAppearanceOpt   = None,
          lang                    = "en",
          includeAllControls      = true
        )
        assert(triggerAll.filterNot(_.compatible).isEmpty)
      }
    }
  }
}
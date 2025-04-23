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


class BindingDescriptorTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormBuilderSupport {

  import BindingDescriptor._

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
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|number, xf|input:xxf-type('xs:decimal')">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Number</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="xf|input:xxf-type('xs:date')">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Date</display-name>
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

        <xbl:binding element="xf|select1[appearance ~= full]">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Radio Buttons</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="fr|dropdown-select1, xf|select1[appearance ~= dropdown]">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Dropdown Menu</display-name>
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

    assertVaryTypes(XF -> "input"        , XS -> "string" , None                     , XS -> "decimal", None                     )(Some((FR -> "number"      , None)))
    assertVaryTypes(FR -> "number"       , XS -> "string" , None                     , XS -> "string" , None                     )(None)
    assertVaryTypes(FR -> "number"       , XS -> "decimal", None                     , XS -> "string" , None                     )(Some((XF -> "input"       , None)))
    assertVaryTypes(FR -> "number"       , XS -> "decimal", None                     , XS -> "boolean", None                     )(Some((XF -> "input"       , None)))
    assertVaryTypes(XF -> "input"        , XS -> "string" , None                     , XS -> "boolean", None                     )(None)
    assertVaryTypes(XF -> "input"        , XS -> "boolean", None                     , XS -> "boolean", None                     )(None)

    assertVaryTypes(XF -> "input"        , XS -> "string" , None                     , XS -> "date"   , None                     )(None)
    assertVaryTypes(XF -> "input"        , XS -> "string" , None                     , XS -> "date"   , Some("dropdowns")        )(Some(FR -> "dropdown-date", None))
    assertVaryTypes(XF -> "input"        , XS -> "date"   , None                     , XS -> "date"   , Some("dropdowns")        )(Some(FR -> "dropdown-date", None))

    assertVaryTypes(FR -> "dropdown-date", XS -> "date"   , None                     , XS -> "date"   , None                     )(Some(XF -> "input"        , None))
    assertVaryTypes(FR -> "dropdown-date", XS -> "date"   , None                     , XS -> "date"   , Some("fields")           )(Some(FR -> "fields-date"  , None))
    assertVaryTypes(FR -> "dropdown-date", XS -> "date"   , None                     , XS -> "string" , Some("fields")           )(Some(XF -> "input"        , None))

    assertVaryTypes(XF -> "select1"      , XS -> "string" , Some("full")             , XS -> "string" , Some("dropdown")         )(Some(XF -> "select1"      , Some("dropdown")))
    assertVaryTypes(XF -> "select1"      , XS -> "string" , Some("dropdown")         , XS -> "string" , Some("full")             )(Some(XF -> "select1"      , Some("full")))

    assertVaryTypes(XF -> "input"        , XS -> "string" , None                     , XS -> "string" , Some("character-counter"))(Some(XF -> "input"        , Some("character-counter")))
    assertVaryTypes(XF -> "textarea"     , XS -> "string" , None                     , XS -> "string" , Some("character-counter"))(Some(XF -> "textarea"     , Some("character-counter")))
    assertVaryTypes(XF -> "textarea"     , XS -> "string" , Some("character-counter"), XS -> "string" , None                     )(Some(XF -> "textarea"     , None))

    assertVaryTypes(XF -> "input"        , XS -> "string" , Some("character-counter"), XS -> "double" , Some("character-counter"))(None)

    assertVaryTypes(FR -> "attachment"   , XS -> "string" , None                     , XS -> "string" , None)(None)
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
    assertVaryTypes(XF -> "input"     , XS -> "decimal")(Seq(None         -> "Number"))
    assertVaryTypes(XF -> "input"     , XS -> "date"   )(Seq(None         -> "Date"           , Some("dropdowns")         -> "Dropdown Date"          , Some("fields") -> "Fields Date"))
    assertVaryTypes(XF -> "select1"   , XS -> "string" )(Seq(Some("full") -> "Radio Buttons"  , Some("dropdown")          -> "Dropdown Menu"))

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
    assertVaryTypes(XF -> "input"     , XS -> "date"   , None            )("Date")
    assertVaryTypes(XF -> "select1"   , XS -> "string" , Some("full")    )("Radio Buttons")
    assertVaryTypes(XF -> "select1"   , XS -> "string" , Some("dropdown"))("Dropdown Menu")

    assertVaryTypes(XF -> "textarea"  , XS -> "string" , None, List(QName("mediatype") -> "text/html") )("Formatted Text Area")
    assertVaryTypes(XF -> "textarea"  , XS -> "string" , None, List(QName("mediatype") -> "text/plain"))("Plain Text Area")
    assertVaryTypes(FR -> "attachment", XS -> "string" , None, Nil                                     )("Single File Attachment")
    assertVaryTypes(FR -> "attachment", XS -> "string" , None, List(QName("multiple")  -> "false")     )("Single File Attachment")
    assertVaryTypes(FR -> "attachment", XS -> "string" , None, List(QName("multiple")  -> "true")      )("Multiple File Attachments")

    assertVaryTypes(FR -> "us-ein",     XS -> "string" , None, Nil      )("US Employer Identification Number (EIN)")
    assertVaryTypes(FR -> "us-ein",     FR -> "us-ein" , None, Nil      )("US Employer Identification Number (EIN)")
  }
}
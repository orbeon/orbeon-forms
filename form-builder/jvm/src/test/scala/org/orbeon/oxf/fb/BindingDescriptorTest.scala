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
import org.orbeon.oxf.fb.FormBuilder._
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.xforms.XFormsNames._
import org.orbeon.oxf.xforms.analysis.model.ModelDefs
import org.orbeon.oxf.xforms.xbl.BindingDescriptor
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._
import org.scalatest.funspec.AnyFunSpecLike
import scala.collection.compat._

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
        <xbl:binding element="xf|input">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Input Field</display-name>
          </metadata>
        </xbl:binding>

        <xbl:binding element="xf|textarea">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">Text Area</display-name>
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
            fr|tinymce[appearance ~= character-counter]">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <display-name lang="en">With Character Counter</display-name>
          </metadata>
        </xbl:binding>
      </xbl:xbl>
    </components>


  val XF  = XFORMS_NAMESPACE_URI
  val XS  = XSD_URI
  val XBL = XBL_NAMESPACE_URI
  val FR  = "http://orbeon.org/oxf/xml/form-runner"
  val FB  = "http://orbeon.org/oxf/xml/form-builder"

  val Bindings = ComponentsDocument.rootElement child (XBL -> "xbl") child (XBL -> "binding")

  describe("New element name") {

    def assertVaryTypes(
      oldControlName : QName,
      oldDatatype    : QName,
      oldAppearance  : Option[String],
      newDatatype    : QName,
      newAppearance  : Option[String])(
      expected       : Option[(QName, Option[String])]
    ): Unit =
      it(s"must pass with $oldControlName/$oldDatatype/$oldAppearance/$newDatatype/$newAppearance") {
        for {
          oldT <- List(oldDatatype, ModelDefs.getVariationTypeOrKeep(oldDatatype))
          newT <- List(newDatatype, ModelDefs.getVariationTypeOrKeep(newDatatype))
        } locally {
          assert(expected === newElementName(oldControlName, oldT, oldAppearance.to(Set), newT, newAppearance, Bindings))
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
  }

  describe("Possible appearances with label") {

    def assertVaryTypes(
      elemName : QName,
      dataType : QName)(
      expected : Seq[(Option[String], String)]
    ): Unit =
      it(s"must pass with $elemName/$dataType") {
        for {
          newT <- List(dataType, ModelDefs.getVariationTypeOrKeep(dataType))
        } locally {
          assert(expected === (possibleAppearancesWithLabel(elemName, newT, "en", Bindings) map (t => t._1 -> t._2)))
        }
      }

    assertVaryTypes(XF -> "input"   , XS -> "string" )(Seq(None         -> "Input Field"  , Some("character-counter") -> "With Character Counter"))
    assertVaryTypes(XF -> "textarea", XS -> "string" )(Seq(None         -> "Text Area"    , Some("character-counter") -> "With Character Counter"))
    assertVaryTypes(XF -> "input"   , XS -> "decimal")(Seq(None         -> "Number"))
    assertVaryTypes(XF -> "input"   , XS -> "date"   )(Seq(None         -> "Date"         , Some("dropdowns")         -> "Dropdown Date"          , Some("fields") -> "Fields Date"))
    assertVaryTypes(XF -> "select1" , XS -> "string" )(Seq(Some("full") -> "Radio Buttons", Some("dropdown")          -> "Dropdown Menu"))
  }
}

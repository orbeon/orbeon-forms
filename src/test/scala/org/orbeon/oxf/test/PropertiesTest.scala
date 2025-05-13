/**
 * Copyright (C) 2008 Orbeon, Inc.
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
package org.orbeon.oxf.test

import org.orbeon.oxf.properties.PropertySet.PropertyParams
import org.orbeon.oxf.properties.{PropertySet, PropertyStore}
import org.orbeon.oxf.xml.XMLConstants.XS_STRING_QNAME
import org.orbeon.oxf.xml.dom.IOSupport
import org.scalatest.funspec.AnyFunSpecLike


object PropertiesTest {

  private val PropertiesXmlString =
    """<properties xmlns:xs="http://www.w3.org/2001/XMLSchema">
      |    <property as="xs:string"  name="test.orbeon.builder.form"  value="value0"/>
      |    <property as="xs:string"  name="test.*.builder.form"       value="value1"/>
      |    <property as="xs:string"  name="test.orbeon.*.form"        value="value2"/>
      |    <property as="xs:string"  name="test.*.*.form"             value="value3"/>
      |    <property as="xs:string"  name="test.orbeon.builder.*"     value="value4"/>
      |    <property as="xs:string"  name="test.*.builder.*"          value="value5"/>
      |    <property as="xs:string"  name="test.orbeon.*.*"           value="value6"/>
      |    <property as="xs:string"  name="test.*.*.*"                value="value7"/>
      |</properties>""".stripMargin

  def newProperties: PropertyStore =
    PropertyStore.parse(IOSupport.readOrbeonDom(PropertiesXmlString), 0)
}

class PropertiesTest extends AnyFunSpecLike with ResourceManagerSupport {

  describe("Wildcard matching") {

    val expected =
      List(
        "test.orbeon.builder.form" -> "value0",
        "test.foo.builder.form"    -> "value1",
        "test.orbeon.foo.form"     -> "value2",
        "test.foo.bar.form"        -> "value3",
        "test.orbeon.builder.foo"  -> "value4",
        "test.foo.builder.bar"     -> "value5",
        "test.orbeon.foo.bar"      -> "value6",
        "test.foo.bar.bat"         -> "value7",
      )

    val propertySet = PropertiesTest.newProperties.getGlobalPropertySet

    for ((name, value) <- expected)
      it(s"must match property `$name` with value `$value`") {
        assert(propertySet.getNonBlankString(name).contains(value))
      }
  }

  describe("Exact matching") {

    val expected =
      List(
        "test.orbeon.builder.form" -> "value0",
        "test.*.builder.form"      -> "value1",
        "test.orbeon.*.form"       -> "value2",
        "test.*.*.form"            -> "value3",
        "test.orbeon.builder.*"    -> "value4",
        "test.*.builder.*"         -> "value5",
        "test.orbeon.*.*"          -> "value6",
        "test.*.*.*"               -> "value7",
      )

    val propertySet = PropertiesTest.newProperties.getGlobalPropertySet

    for ((name, value) <- expected)
      it(s"must match property `$name` with value `$value`") {
        assert(propertySet.getNonBlankString(name).contains(value))
      }
  }

  describe("Properties starting with a prefix") {

    val expected =
      List(
        ("test.orbeon",   true)  -> Set("test.orbeon.builder.form", "test.orbeon.builder.*", "test.orbeon.*.form", "test.orbeon.*.*", "test.*.builder.form", "test.*.builder.*", "test.*.*.form", "test.*.*.*"),
        ("test.orbeon",   false) -> Set("test.orbeon.builder.form", "test.orbeon.builder.*", "test.orbeon.*.form", "test.orbeon.*.*"),
        ("test.orbeon.*", true)  -> Set("test.orbeon.builder.form", "test.orbeon.builder.*", "test.orbeon.*.form", "test.orbeon.*.*", "test.*.builder.form", "test.*.builder.*", "test.*.*.form", "test.*.*.*"),
        ("test.orbeon.*", false) -> Set("test.orbeon.builder.form", "test.orbeon.builder.*", "test.orbeon.*.form", "test.orbeon.*.*"),
      )

    val propertySet = PropertiesTest.newProperties.getGlobalPropertySet

    for (((name, wildcards), value) <- expected)
      it(s"must find properties starting with `$name` with wildcards = `$wildcards`") {
        assert(propertySet.propertiesStartsWith(name, matchWildcards = wildcards).toSet == value)
      }
  }

  describe("Properties matching") {

    val propertySetWithXx =
      PropertySet.forTests(
        List(
          PropertyParams(Map.empty, "x.x", XS_STRING_QNAME,  "00"),
          PropertyParams(Map.empty, "x.*", XS_STRING_QNAME,  "01"),
          PropertyParams(Map.empty, "*.x", XS_STRING_QNAME,  "10"),
          PropertyParams(Map.empty, "*.*", XS_STRING_QNAME,  "11"),
        )
      )

    val propertySetWithAb =
      PropertySet.forTests(
        List(
          PropertyParams(Map.empty, "a.b.form", XS_STRING_QNAME,  "v1"),
          PropertyParams(Map.empty, "d.*.form", XS_STRING_QNAME,  "v2"),
          PropertyParams(Map.empty, "*.*.*",    XS_STRING_QNAME,  "v3"),
        )
      )

    val expected =
      List(
        (propertySetWithXx, "x.x",      Set("00")),
        (propertySetWithXx, "x.*",      Set("00", "01")),
        (propertySetWithXx, "*.x",      Set("00", "01", "10")),
        (propertySetWithXx, "*.*",      Set("00", "01", "10", "11")),

        (propertySetWithXx, "y.y",      Set("11")),
        (propertySetWithXx, "y.*",      Set("10", "11")),
        (propertySetWithXx, "*.y",      Set("01", "11")),
        (propertySetWithXx, "x.y",      Set("01")),
        (propertySetWithXx, "y.x",      Set("10")),

        (propertySetWithAb, "a.b.form", Set("v1")),
        (propertySetWithAb, "a.b.data", Set("v3")),
        (propertySetWithAb, "a.*.form", Set("v1", "v3")),
        (propertySetWithAb, "d.*.form", Set("v2")),
        (propertySetWithAb, "c.*.form", Set("v3")),
      )

    for ((propertySet, name, value) <- expected)
      it(s"must find properties matching with `$name`") {
        assert(propertySet.propertiesMatching(name).map(_.stringValue).toSet == value)
      }
  }
}

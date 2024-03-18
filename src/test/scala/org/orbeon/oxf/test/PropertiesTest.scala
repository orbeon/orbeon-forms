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

import org.orbeon.oxf.properties.PropertyStore
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
    PropertyStore.parse(IOSupport.readOrbeonDom(PropertiesXmlString))
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
        assert(propertySet.getString(name) == value)
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
        assert(propertySet.getString(name) == value)
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
}

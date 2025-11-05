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

import cats.data.NonEmptyList
import org.orbeon.oxf.externalcontext.{Credentials, ExternalContext, RequestAdapter, UserAndGroup}
import org.orbeon.oxf.properties.PropertySet.PropertyParams
import org.orbeon.oxf.properties.{CombinedPropertyStore, PropertyLoader, PropertySet, PropertyStore}
import org.orbeon.oxf.xml.XMLConstants.XS_STRING_QNAME
import org.orbeon.oxf.xml.dom.IOSupport
import org.orbeon.properties.api
import org.orbeon.properties.api.*
import org.scalatest.funspec.AnyFunSpecLike

import java.{util, util as ju}
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.util.chaining.scalaUtilChainingOps


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
    PropertyStore.parse(IOSupport.readOrbeonDom(PropertiesXmlString), "")
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

    val propertySet = PropertiesTest.newProperties.globalPropertySet

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

    val propertySet = PropertiesTest.newProperties.globalPropertySet

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

    val propertySet = PropertiesTest.newProperties.globalPropertySet

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

  describe("Combined properties") {

    val PropertiesXmlString1 =
      """<properties xmlns:xs="http://www.w3.org/2001/XMLSchema">
        |    <property as="xs:string"  name="a.b.c"  value="a.b.c.1"/>
        |    <property as="xs:string"  name="a.b.d"  value="a.b.d.1"/>
        |    <property as="xs:string"  name="a.b.*"  value="a.b.*.1"/>
        |</properties>""".stripMargin

    val PropertiesXmlString2 =
      """<properties xmlns:xs="http://www.w3.org/2001/XMLSchema">
        |    <property as="xs:string"  name="a.b.c"   value="a.b.c.2"/>
        |    <property as="xs:string"  name="a.b.*"   value="a.b.*.2"/>
        |    <property as="xs:string"  name="a.b.f"   value="a.b.f.2"/>
        |    <property as="xs:string"  name="a.b.f.g" value="a.b.f.g.2"/>
        |</properties>""".stripMargin

    def newProperties(propertiesXmlString: String): PropertyStore =
      PropertyStore.parse(IOSupport.readOrbeonDom(propertiesXmlString), "")

    val resultingPropertySet: PropertySet =
      CombinedPropertyStore.combine(NonEmptyList.fromListUnsafe(
        List(
          Some(newProperties(PropertiesXmlString1)),
          Some(newProperties(PropertiesXmlString2)),
        )
      )).get.globalPropertySet

    it("must combine properties from multiple sources") {
      assert(resultingPropertySet.getNonBlankString("a.b.c").contains("a.b.c.2"))
      assert(resultingPropertySet.getNonBlankString("a.b.d").contains("a.b.d.1"))
      assert(resultingPropertySet.getNonBlankString("a.b.e").contains("a.b.*.2"))

      assert(resultingPropertySet.propertiesStartsWith("a.b", matchWildcards = false) == List(
        "a.b.c",
        "a.b.d",
        "a.b.*",
//        "a.b.f", // apparently we make assumptions about whether we return the shorter prefix here!
        "a.b.f.g",
      ))
    }
  }

  describe("Property providers") {

    it("must load properties preferentially from the custom provider, then from the default provider") {

      PropertyLoader.initialize()
      val ps = PropertyLoader.getPropertyStore(requestOpt = None)

      assert(ps.globalPropertySet.getNonBlankString("a.b.c").contains("a.b.c.2"))
      assert(ps.globalPropertySet.getNonBlankString("a.b.d").contains("a.b.d.1"))
      assert(ps.globalPropertySet.getNonBlankString("a.b.f").contains("a.b.f.2"))
      assert(ps.globalPropertySet.getNonBlankString("a.b.f.g").contains("a.b.f.g.2"))
    }

    it("must load properties from the custom provider based on the user credentials") {

      PropertyLoader.initialize()

      def newRequest(username: String, propertySuffix: String): ExternalContext.Request =
        new RequestAdapter {
          override def credentials: Option[Credentials] = Some(Credentials(UserAndGroup(username, None), Nil, Nil))
          override def getAttributesMap: ju.Map[ETag, AnyRef] = mutable.HashMap.empty.asJava
          override def getHeaderValuesMap: util.Map[ETag, Array[ETag]] = Map("Test-Property-Suffix" -> Array(propertySuffix)).asJava
        }

      def assertForUsername(username: String, propertySuffix: String): Unit = {
        val ps = PropertyLoader.getPropertyStore(requestOpt = Some(newRequest(username, propertySuffix)))
        assert(ps.globalPropertySet.getNonBlankString("a.b.c").contains(s"a.b.c.$propertySuffix"))
        assert(ps.globalPropertySet.getNonBlankString("a.b.d").contains("a.b.d.1"))
        assert(ps.globalPropertySet.getNonBlankString("a.b.f").contains(s"a.b.f.$propertySuffix"))
        assert(ps.globalPropertySet.getNonBlankString("a.b.f.g").contains(s"a.b.f.g.$propertySuffix"))
      }

      // The property suffix is passed as a header, and when new properties are returned they include it. Subsequent
      // calls return cached properties until the ETag expires.
      for (_ <- 1 to 2) {
        assertForUsername("alice", "alice")
        assertForUsername("bob", "bob")
      }

      // Wait for ETag to expire
      Thread.sleep(1100L)

      // We now pass a new suffix and properties should be fetched again, creating new values.
      for (_ <- 1 to 2) {
        assertForUsername("alice", "alice:new")
        assertForUsername("bob", "bob:new")
      }
    }
  }
}

class TestPropertyProvider extends api.PropertyProvider {

  case class ConcretePropertyDefinition(
    getName      : String,
    getValue     : String,
    getType      : String,
    getNamespaces: ju.Map[String, String],
    getCategory  : ju.Optional[String],
  ) extends api.PropertyDefinition

  private val KeyPrefix  = "test-provider-key"
  private val KeyRegex   = s"""$KeyPrefix:(.+)""".r

  private val ETagPrefix = "test-etag"
  private val ETagRegex  = s"""$ETagPrefix:(.+):(.+)""".r

  private val DelayMs    = 1000L

  override def getCacheKey(
    request    : ju.Optional[api.Request],
    credentials: ju.Optional[api.Credentials],
    session    : ju.Optional[api.Session],
    extension  : ju.Map[String, Any],
  ): ju.Optional[String] =
    credentials.toScala match {
      case Some(creds) => ju.Optional.of(s"$KeyPrefix:${creds.getUsername}")
      case _           => ju.Optional.of(KeyPrefix)
    }

  def getPropertiesIfNeeded(
    cacheKey   : ju.Optional[api.CacheKey],
    eTag       : ju.Optional[api.ETag],
    request    : ju.Optional[api.Request],
    credentials: ju.Optional[api.Credentials],
    session    : ju.Optional[api.Session],
    extension  : ju.Map[String, Any],
  ): ju.Optional[api.PropertyDefinitionsWithETag] =
    cacheKey.toScala match {
      case Some(KeyPrefix) =>
        if (eTag.toScala.contains(ETagPrefix))
          ju.Optional.empty()
        else
          ju.Optional.of(
            new PropertyDefinitionsWithETag {
              val getProperties: PropertyDefinitions = propertyDefinitions("2").asJavaCollection
              val getETag: ETag = ETagPrefix
            }
          )
      case Some(KeyRegex(username)) =>
        val currentTime = System.currentTimeMillis()
        eTag.toScala match {
          case Some(ETagRegex(eTagUsername, eTagLastModified)) if eTagUsername == username && eTagLastModified.toLong + DelayMs >= currentTime =>
            ju.Optional.empty()
          case _ =>
            ju.Optional.of(
              new PropertyDefinitionsWithETag {
                val getProperties: PropertyDefinitions =
                  propertyDefinitions(
                    request
                      .toScala
                      .flatMap(
                        _.getHeaders
                          .asScala
                          .get("Test-Property-Suffix")
                          .flatMap(_.asScala.headOption)
                      )
                      .getOrElse("")
                  ).asJavaCollection
                val getETag: ETag =
                  s"$ETagPrefix:$username:$currentTime"
              }
            )
        }
      case _ =>
        ju.Optional.empty()
    }

  def propertyDefinitions(suffix: String): Iterable[PropertyDefinition] =
    List(
      ConcretePropertyDefinition(
        getName       = "a.b.c",
        getValue      = s"a.b.c.$suffix",
        getType       = "string",
        getNamespaces = Map.empty.asJava,
        getCategory   = ju.Optional.empty(),
      ),
      ConcretePropertyDefinition(
        getName       = "a.b.*",
        getValue      = s"a.b.*.$suffix",
        getType       = "string",
        getNamespaces = Map.empty.asJava,
        getCategory   = ju.Optional.empty(),
      ),
      ConcretePropertyDefinition(
        getName       = "a.b.f",
        getValue      = s"a.b.f.$suffix",
        getType       = "string",
        getNamespaces = Map.empty.asJava,
        getCategory   = ju.Optional.empty(),
      ),
      ConcretePropertyDefinition(
        getName       = "a.b.f.g",
        getValue      = s"a.b.f.g.$suffix",
        getType       = "string",
        getNamespaces = Map.empty.asJava,
        getCategory   = ju.Optional.empty(),
      ),
    )
}
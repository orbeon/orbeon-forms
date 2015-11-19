/**
 * Copyright (C) 2015 Orbeon, Inc.
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
package org.orbeon.oxf.json

import org.junit.Test
import org.orbeon.oxf.test.XMLSupport
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xml.{SAXStore, TransformerUtils}
import org.scalatest.junit.AssertionsForJUnit
import spray.json._

import scala.xml.Elem

class JSONTest extends AssertionsForJUnit with XMLSupport {

  import org.orbeon.scaxon.XML.{Test ⇒ _, _}

  // Examples from the XForms 2.0 spec
  val XFormsJsonToXml = List[(String, Elem)](
    """{"given": "Mark", "family": "Smith"}""" →
      <json object="true">
          <given type="string">Mark</given>
          <family type="string">Smith</family>
      </json>,
    """{"name": "Mark", "age": 21}""" →
      <json object="true"><name type="string">Mark</name><age type="number">21</age></json>,
    """{"selected": true}""" →
      <json object="true"><selected type="boolean">true</selected></json>,
    """{"cities": ["Amsterdam", "Paris", "London"]}""" →
      <json object="true">
        <cities array="true" type="string">Amsterdam</cities>
        <cities array="true" type="string">Paris</cities>
        <cities array="true" type="string">London</cities>
      </json>,
    """{"load": [0.31, 0.33, 0.32]}""" →
      <json object="true">
        <load array="true" type="number">0.31</load>
        <load array="true" type="number">0.33</load>
        <load array="true" type="number">0.32</load>
      </json>,
    """{"father": {"given": "Mark", "family": "Smith"}, "mother": {"given": "Mary", "family": "Smith"}}""" →
      <json object="true">
        <father object="true"><given type="string">Mark</given><family type="string">Smith</family></father>
        <mother object="true"><given type="string">Mary</given><family type="string">Smith</family></mother>
      </json>,
    """{"p": null}""" →
      <json object="true"><p type="null"/></json>,
    """{"p": ""}""" →
      <json object="true"><p type="string"/></json>,
    """{"p": []}""" →
      <json object="true"><p array="true"/></json>,
    """{"$v": 0}""" →
      <json object="true"><_v name="$v" type="number">0</_v></json>,
    """{"1": "one"}""" →
      <json object="true"><_1 name="1" type="string">one</_1></json>,
    """3""" →
      <json type="number">3</json>,
    """"Disconnected"""" →
      <json type="string">Disconnected</json>,
    """["red", "green", "blue"]""" →
      <json>
        <_ name="" array="true" type="string">red</_>
        <_ name="" array="true" type="string">green</_>
        <_ name="" array="true" type="string">blue</_>
      </json>,
    """{"g": [["a", "b", "c"], ["d", "e"]]}""" →
      <json object="true">
        <g array="true">
          <_ name="" array="true" type="string">a</_>
          <_ name="" array="true" type="string">b</_>
          <_ name="" array="true" type="string">c</_>
        </g>
        <g array="true">
          <_ name="" array="true" type="string">d</_>
          <_ name="" array="true" type="string">e</_>
        </g>
      </json>,
    """{}""" →
      <json object="true"/>,
    """[]""" →
      <json>
        <_ name="" array="true"/>
      </json>,
    """""""" →
      <json type="string"/>
  )

  val AdditionalJsonToXml = List[(String, Elem)](
    """{"p": [{}]}""" →
      <json object="true">
        <p array="true" object="true"/>
      </json>,
    """{"p": [[]]}""" →
      <json object="true">
        <p array="true">
          <_ name="" array="true"/>
        </p>
      </json>,
    """{"p": [[[]]]}""" →
      <json object="true">
        <p array="true">
          <_ name="" array="true">
            <_ name="" array="true"/>
          </_>
        </p>
      </json>,
    """ ["red", 42, true, { "foo": "bar"}, []] """ →
      <json>
        <_ name="" array="true" type="string">red</_>
        <_ name="" array="true" type="number">42</_>
        <_ name="" array="true" type="boolean">true</_>
        <_ name="" array="true" object="true">
          <foo type="string">bar</foo>
        </_>
        <_ name="" array="true">
          <_ name="" array="true"/>
        </_>
      </json>
  )

  val ExpectedJsonToXml = XFormsJsonToXml ++ AdditionalJsonToXml

  @Test def testJsonToXml(): Unit = {
    for ((json, xml) ← ExpectedJsonToXml) {
      val store = new SAXStore
      JSON.jsonStringToXML(json, store)
      val resultXML = TransformerUtils.saxStoreToDom4jDocument(store)
      println(Dom4jUtils.domToPrettyString(resultXML))
      assertXMLDocumentsIgnoreNamespacesInScope(elemToDom4j(xml), resultXML)
    }
  }

  @Test def testXmlToJson(): Unit = {
    for ((json, xml) ← ExpectedJsonToXml) {

      val expectedJson = json.parseJson

      println(s"processing ${expectedJson.toString}")
      val actualJson = JSON.xmlToJson(xml, strict = true)

      assert(expectedJson === actualJson)
    }
  }
}
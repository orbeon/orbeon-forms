/**
 * Copyright (c) 2015 Orbeon, Inc. http://orbeon.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.orbeon.oxf.json

import org.junit.Test
import org.orbeon.oxf.test.XMLSupport
import org.orbeon.oxf.xml.{SAXStore, TransformerUtils}
import org.scalatest.junit.AssertionsForJUnit
import spray.json._

import scala.xml.Elem

class ConverterTest extends AssertionsForJUnit with XMLSupport {

  import org.orbeon.scaxon.XML.{Test ⇒ _, _}

  // Examples from the XForms 2.0 spec
  val XFormsJsonToXml = List[(String, Elem)](
    """ {"given": "Mark", "family": "Smith"} """ →
      <json object="true">
          <given type="string">Mark</given>
          <family type="string">Smith</family>
      </json>,
    """ {"name": "Mark", "age": 21} """ →
      <json object="true"><name type="string">Mark</name><age type="number">21</age></json>,
    """ {"selected": true} """ →
      <json object="true"><selected type="boolean">true</selected></json>,
    """ {"cities": ["Amsterdam", "Paris", "London"]} """ →
      <json object="true">
        <cities array="true" type="string">Amsterdam</cities>
        <cities array="true" type="string">Paris</cities>
        <cities array="true" type="string">London</cities>
      </json>,
    """ {"load": [0.31, 0.33, 0.32]} """ →
      <json object="true">
        <load array="true" type="number">0.31</load>
        <load array="true" type="number">0.33</load>
        <load array="true" type="number">0.32</load>
      </json>,
    """ {"father": {"given": "Mark", "family": "Smith"}, "mother": {"given": "Mary", "family": "Smith"}} """ →
      <json object="true">
        <father object="true"><given type="string">Mark</given><family type="string">Smith</family></father>
        <mother object="true"><given type="string">Mary</given><family type="string">Smith</family></mother>
      </json>,
    """ {"p": null} """ →
      <json object="true"><p type="null"/></json>,
    """ {"p": ""} """ →
      <json object="true"><p type="string"/></json>,
    """ {"p": []} """ →
      <json object="true"><p array="true"/></json>,
    """ {"$v": 0} """ →
      <json object="true"><_v name="$v" type="number">0</_v></json>,
    """ {"1": "one"} """ →
      <json object="true"><_1 name="1" type="string">one</_1></json>,
    """ 3 """ →
      <json type="number">3</json>,
    """ "Disconnected" """ →
      <json type="string">Disconnected</json>,
    """ ["red", "green", "blue"] """ →
      <json>
        <_ name="" array="true" type="string">red</_>
        <_ name="" array="true" type="string">green</_>
        <_ name="" array="true" type="string">blue</_>
      </json>,
    """ {"g": [["a", "b", "c"], ["d", "e"]]} """ →
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
    """ {} """ →
      <json object="true"/>,
    """ [] """ →
      <json>
        <_ name="" array="true"/>
      </json>,
    """ "" """ →
      <json type="string"/>
  )

  val AdditionalJsonToXml = List[(String, Elem)](
    """ {"p": [{}]} """ →
      <json object="true">
        <p array="true" object="true"/>
      </json>,
    """ {"p": [[]]} """ →
      <json object="true">
        <p array="true">
          <_ name="" array="true"/>
        </p>
      </json>,
    """ {"p": [[[]]]} """ →
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
      </json>,
    """ { "< <": [[ "1 2 3" ], { "_" : 6 } ]} """ →
      <json object="true">
        <___ array="true" name="&lt; &lt;">
          <_ name="" array="true" type="string">1 2 3</_>
        </___>
        <___ array="true" name="&lt; &lt;" object="true">
          <_ type="number">6</_>
        </___>
      </json>
  )

  val ExpectedJsonToXml = XFormsJsonToXml ++ AdditionalJsonToXml

  @Test def testJsonToXml(): Unit = {
    for ((json, xml) ← ExpectedJsonToXml) {
      val store = new SAXStore
      Converter.jsonStringToXML(json, store)
      val resultXML = TransformerUtils.saxStoreToDom4jDocument(store)

      assertXMLDocumentsIgnoreNamespacesInScope(elemToDom4j(xml), resultXML)
    }
  }

  @Test def testXmlToJson(): Unit = {
    for ((json, xml) ← ExpectedJsonToXml) {
      val expectedJson = json.parseJson
      val actualJson   = Converter.xmlToJson(xml, strict = true)

      assert(expectedJson === actualJson)
    }
  }
}
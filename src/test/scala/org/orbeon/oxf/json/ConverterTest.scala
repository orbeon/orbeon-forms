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
import org.orbeon.builder.Utils
import org.orbeon.oxf.test.XMLSupport
import org.orbeon.oxf.xml.{SAXStore, TransformerUtils}
import org.orbeon.scaxon.XML
import org.scalatest.junit.AssertionsForJUnit
import spray.json._

import scala.language.postfixOps
import scala.xml.Elem

class ConverterTest extends AssertionsForJUnit with XMLSupport {

  import org.orbeon.scaxon.XML.{Test ⇒ _, _}

  // Examples from the XForms 2.0 spec
  val XFormsJsonToXml = List[(String, Elem)](
    """ {"given": "Mark", "family": "Smith"} """ →
      <json type="object">
          <given type="string">Mark</given>
          <family type="string">Smith</family>
      </json>,
    """ {"name": "Mark", "age": 21} """ →
      <json type="object"><name type="string">Mark</name><age type="number">21</age></json>,
    """ {"selected": true} """ →
      <json type="object"><selected type="boolean">true</selected></json>,
    """ {"cities": ["Amsterdam", "Paris", "London"]} """ →
      <json type="object">
        <cities type="array">
          <_ type="string">Amsterdam</_>
          <_ type="string">Paris</_>
          <_ type="string">London</_>
        </cities>
      </json>,
    """ {"load": [0.31, 0.33, 0.32]} """ →
      <json type="object">
        <load type="array">
          <_ type="number">0.31</_>
          <_ type="number">0.33</_>
          <_ type="number">0.32</_>
        </load>
      </json>,
    """ {"father": {"given": "Mark", "family": "Smith"}, "mother": {"given": "Mary", "family": "Smith"}} """ →
      <json type="object">
        <father type="object"><given type="string">Mark</given><family type="string">Smith</family></father>
        <mother type="object"><given type="string">Mary</given><family type="string">Smith</family></mother>
      </json>,
    """ {"p": null} """ →
      <json type="object"><p type="null"/></json>,
    """ {"p": ""} """ →
      <json type="object"><p type="string"/></json>,
    """ {"p": []} """ →
      <json type="object"><p type="array"/></json>,
    """ {"$v": 0} """ →
      <json type="object"><_v name="$v" type="number">0</_v></json>,
    """ {"1": "one"} """ →
      <json type="object"><_1 name="1" type="string">one</_1></json>,
    """ 3 """ →
      <json type="number">3</json>,
    """ "Disconnected" """ →
      <json type="string">Disconnected</json>,
    """ ["red", "green", "blue"] """ →
      <json type="array">
        <_ type="string">red</_>
        <_ type="string">green</_>
        <_ type="string">blue</_>
      </json>,
    """ {"g": [["a", "b", "c"], ["d", "e"]]} """ →
      <json type="object">
        <g type="array">
          <_ type="array">
            <_ type="string">a</_>
            <_ type="string">b</_>
            <_ type="string">c</_>
          </_>
          <_ type="array">
            <_ type="string">d</_>
            <_ type="string">e</_>
          </_>
        </g>
      </json>,
    """ {} """ →
      <json type="object"/>,
    """ [] """ →
      <json type="array"/>,
    """ "" """ →
      <json type="string"/>
  )

  val AdditionalJsonToXml = List[(String, Elem)](
    """ {"p": [{}]} """ →
      <json type="object">
        <p type="array">
            <_ type="object"/>
        </p>
      </json>,
    """ {"p": [[]]} """ →
      <json type="object">
        <p type="array">
          <_ type="array"/>
        </p>
      </json>,
    """ {"p": [[[]]]} """ →
      <json type="object">
        <p type="array">
          <_ type="array">
            <_ type="array"/>
          </_>
        </p>
      </json>,
    """ ["red", 42, true, { "foo": "bar"}, []] """ →
      <json type="array">
        <_ type="string">red</_>
        <_ type="number">42</_>
        <_ type="boolean">true</_>
        <_ type="object">
          <foo type="string">bar</foo>
        </_>
        <_ type="array"/>
      </json>,
    """ { "< <": [[ "1 2 3" ], { "_" : 6 } ]} """ →
      <json type="object">
        <___ name="&lt; &lt;" type="array">
          <_ type="array">
            <_ type="string">1 2 3</_>
          </_>
          <_ type="object">
            <_ type="number">6</_>
          </_>
        </___>
      </json>
  )

  val ExpectedJsonToXml = XFormsJsonToXml ++ AdditionalJsonToXml

  @Test def testJsonToXml(): Unit = {
    for ((json, xml) ← ExpectedJsonToXml) {
      val store = new SAXStore
      Converter.jsonStringToXml(json, store)
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

  @Test def testEscaping(): Unit = {

    import XML._

    val codePointsToEscape           = (0 to 0x1F) :+ 0x7F toArray
    val stringWithCodePointsToEscape = new String(codePointsToEscape, 0, codePointsToEscape.length)
    val codePointsTranslated         = codePointsToEscape map (_ + 0xE000)
    val unicodeEscapedString         = codePointsToEscape map (c ⇒ s"\\u00${Utils.toHexString(Array(c.toByte))}") mkString
    val translatedString             = new String(codePointsTranslated, 0, codePointsTranslated.length)

    // String containing both a property name and a string value with characters to escape
    val jsonString = """ { "foo""" + unicodeEscapedString + """": "bar""" + unicodeEscapedString + """" } """
    val json       = jsonString.parseJson

    val xml           = Converter.jsonToXml(json)
    val firstElem     = xml.rootElement / * head
    val firstElemName = firstElem attValueOpt "name" getOrElse firstElem.localname
    val firstValue    = firstElem.stringValue

    assert("foo" + translatedString === firstElemName)
    assert("bar" + translatedString === firstValue)

    val backToJson = Converter.xmlToJson(xml, strict = true)

    assert(json === backToJson)

    // We'll get `MatchError` during deconstruction if types don't match and the test will fail
    val JsObject(fields) = backToJson
    val (name, JsString(value)) = fields.head

    assert("bar" + stringWithCodePointsToEscape === value)
    assert("foo" + stringWithCodePointsToEscape === name)
  }
}
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

import io.circe.{Json, parser}
import org.orbeon.oxf.test.XMLSupport
import org.orbeon.oxf.util.NumberUtils._
import org.orbeon.oxf.xml.TransformerUtils._
import org.orbeon.oxf.xml.dom.Comparator
import org.orbeon.oxf.xml.{SAXStore, TransformerUtils}
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.saxon.value.Whitespace
import org.orbeon.scaxon.NodeConversions._
import org.scalatest.funspec.AnyFunSpecLike

import scala.language.postfixOps
import scala.xml.Elem


object ConverterTest {

  // Examples from the XForms 2.0 spec
  val XFormsJsonToXml = List[(String, Elem)](
    """ {"given": "Mark", "family": "Smith"} """ ->
      <json type="object">
          <given>Mark</given>
          <family>Smith</family>
      </json>,
    """ {"name": "Mark", "age": 21} """ ->
      <json type="object"><name>Mark</name><age type="number">21</age></json>,
    """ {"selected": true} """ ->
      <json type="object"><selected type="boolean">true</selected></json>,
    """ {"cities": ["Amsterdam", "Paris", "London"]} """ ->
      <json type="object">
        <cities type="array">
          <_>Amsterdam</_>
          <_>Paris</_>
          <_>London</_>
        </cities>
      </json>,
    """ {"load": [0.31, 0.33, 0.32]} """ ->
      <json type="object">
        <load type="array">
          <_ type="number">0.31</_>
          <_ type="number">0.33</_>
          <_ type="number">0.32</_>
        </load>
      </json>,
    """ {"father": {"given": "Mark", "family": "Smith"}, "mother": {"given": "Mary", "family": "Smith"}} """ ->
      <json type="object">
        <father type="object"><given>Mark</given><family>Smith</family></father>
        <mother type="object"><given>Mary</given><family>Smith</family></mother>
      </json>,
    """ {"p": null} """ ->
      <json type="object"><p type="null"/></json>,
    """ {"p": ""} """ ->
      <json type="object"><p/></json>,
    """ {"p": []} """ ->
      <json type="object"><p type="array"/></json>,
    """ {"p": {}} """ ->
      <json type="object"><p type="object"/></json>,
    """ {"$v": 0} """ ->
      <json type="object"><_v name="$v" type="number">0</_v></json>,
    """ {"1": "one"} """ ->
      <json type="object"><_1 name="1">one</_1></json>,
    """ 3 """ ->
      <json type="number">3</json>,
    """ "Disconnected" """ ->
      <json>Disconnected</json>,
    """ ["red", "green", "blue"] """ ->
      <json type="array">
        <_>red</_>
        <_>green</_>
        <_>blue</_>
      </json>,
    """ {"g": [["a", "b", "c"], ["d", "e"]]} """ ->
      <json type="object">
        <g type="array">
          <_ type="array">
            <_>a</_>
            <_>b</_>
            <_>c</_>
          </_>
          <_ type="array">
            <_>d</_>
            <_>e</_>
          </_>
        </g>
      </json>,
    """ {} """ ->
      <json type="object"/>,
    """ [] """ ->
      <json type="array"/>,
    """ "" """ ->
      <json/>
  )

  val AdditionalJsonToXml = List[(String, Elem)](
    """ {"p": [{}]} """ ->
      <json type="object">
        <p type="array">
            <_ type="object"/>
        </p>
      </json>,
    """ {"p": [[]]} """ ->
      <json type="object">
        <p type="array">
          <_ type="array"/>
        </p>
      </json>,
    """ {"p": [[[]]]} """ ->
      <json type="object">
        <p type="array">
          <_ type="array">
            <_ type="array"/>
          </_>
        </p>
      </json>,
    """ ["red", 42, true, { "foo": "bar"}, []] """ ->
      <json type="array">
        <_>red</_>
        <_ type="number">42</_>
        <_ type="boolean">true</_>
        <_ type="object">
          <foo>bar</foo>
        </_>
        <_ type="array"/>
      </json>,
    """ { "< <": [[ "1 2 3" ], { "_" : 6 } ]} """ ->
      <json type="object">
        <___ name="&lt; &lt;" type="array">
          <_ type="array">
            <_>1 2 3</_>
          </_>
          <_ type="object">
            <_ type="number">6</_>
          </_>
        </___>
      </json>,
    """ { "address": "1000 Main Street\nNew York, NY" } """ ->
      <json type="object">
        <address>1000 Main Street&#x0a;New York, NY</address>
      </json>,
    """ { "values": "foo\t42" } """ ->
      <json type="object">
        <values>foo&#x09;42</values>
      </json>
    ,
    """ { "escapes": "\\\"" } """ ->
      <json type="object">
        <escapes>\"</escapes>
      </json>
  )

  val ExpectedJsonToXml = XFormsJsonToXml ++ AdditionalJsonToXml

  /*@XPathFunction*/ def expectedJsonStrings: List[String] = ExpectedJsonToXml map (_._1)
  /*@XPathFunction*/ def expectedXmlStrings: List[DocumentInfo] = ExpectedJsonToXml map (_._2) map (elemToDocumentInfo(_, readonly = true))

  /*@XPathFunction*/ def compareXMLDocumentsIgnoreNamespacesInScope(left: DocumentInfo, right: DocumentInfo): Boolean =
    Comparator.compareDocumentsIgnoreNamespacesInScope(tinyTreeToOrbeonDom(left), tinyTreeToOrbeonDom(right))
}

class ConverterTest extends AnyFunSpecLike with XMLSupport {

  import ConverterTest._
  import org.orbeon.scaxon.SimplePath.{Test => _, _}

  describe("Converting from JSON to XML") {
    it ("must match expectations") {
      for ((json, xml) <- ExpectedJsonToXml) {
        val store = new SAXStore
        Converter.jsonStringToXmlStream(json, store)
        val resultXML = TransformerUtils.saxStoreToOrbeonDomDocument(store)

        assertXMLDocumentsIgnoreNamespacesInScope(elemToOrbeonDom(xml), resultXML)
      }
    }
  }

  describe("Converting from XML to JSON") {
    it ("must match expectations") {
      for {
        strict      <- List(true, false)
        (json, xml) <- ExpectedJsonToXml
      } locally {
        val expectedJson = parser.parse(json)
        val actualJson   = Converter.xmlToJson(xml, strict = strict)

        assert(expectedJson.contains(actualJson))
      }
    }
  }

  describe("Escaping of special characters") {

    val codePointsToEscape           = ((0 to 0x1F).toSet + 0x7F -- List(0x09, 0x0a, 0x0d)).toArray.sorted
    val stringWithCodePointsToEscape = new String(codePointsToEscape, 0, codePointsToEscape.length)
    val codePointsTranslated         = codePointsToEscape map (_ + 0xE000)
    val unicodeEscapedString         = codePointsToEscape map (c => s"\\u00${toHexString(Array(c.toByte))}") mkString
    val translatedString             = new String(codePointsTranslated, 0, codePointsTranslated.length)

    // String containing both a property name and a string value with characters to escape
    val jsonString = """ { "foo""" + unicodeEscapedString + """": "bar""" + unicodeEscapedString + """" } """
    val json       = parser.parse(jsonString)

    val xml           = Converter.jsonToXmlDoc(json.toTry.get)
    val firstElem     = xml.rootElement / * head
    val firstElemName = firstElem attValueOpt "name" getOrElse firstElem.localname
    val firstValue    = firstElem.stringValue

    it ("must escape specified character ranges when going from JSON to XML") {
      assert("foo" + translatedString == firstElemName)
      assert("bar" + translatedString == firstValue)
    }

    val backToJson = Converter.xmlToJson(xml, strict = true)

    it ("must roundtrip") {
      assert(json.contains(backToJson))
    }

    // We'll get `MatchError` during deconstruction if types don't match and the test will fail
    val Some((name, value)) = backToJson.asObject.flatMap(_.toIterable.headOption)

    it ("must contain correct code points when parsing JSON") {
      assert(value.asString.contains("bar" + stringWithCodePointsToEscape))
      assert("foo" + stringWithCodePointsToEscape == name)
    }
  }

  describe("The `strict` parameter") {

    val xmlInputs = List(
      <json type="array">
        <_>red</_>
        <_ type="number">foo</_>
        <_ type="boolean">true</_>
        <_ type="object">
          <foo>bar</foo>
        </_>
      </json>,
      <json type="array">
        <_>red</_>
        <_ type="number">42</_>
        <_ type="boolean">foo</_>
        <_ type="object">
          <foo>bar</foo>
        </_>
      </json>,
      <json type="array">
        <_>red</_>
        <_ type="number">42</_>
        <_ type="boolean">true</_>
        <_ type="foo">
          <foo>bar</foo>
        </_>
      </json>
    )

    it ("must fail with invalid XML input when set to `true` and pass when set to `false`") {
      for (xml <- xmlInputs) {
        intercept[IllegalArgumentException] {
          Converter.xmlToJson(xml, strict = true)
        }

        Converter.xmlToJson(xml, strict = false)
      }
    }
  }

  describe("Support for blank values in XML") {

    val xmlInputs = List(
      """ ["red",null,true,{"foo":"bar"}] """ ->
        <json type="array">
          <_>red</_>
          <_ type="number"></_>
          <_ type="boolean">true</_>
          <_ type="object">
            <foo>bar</foo>
          </_>
        </json>,
      """ ["red",42,null,{"foo":"bar"}] """ ->
        <json type="array">
          <_>red</_>
          <_ type="number">42</_>
          <_ type="boolean"></_>
          <_ type="object">
            <foo>bar</foo>
          </_>
        </json>
    )

    it ("must create JSON with `null`") {
      for {
        strict      <- List(true, false)
        (json, xml) <- xmlInputs
      } locally {

        val expectedJson = parser.parse(json)
        val actualJson   = Converter.xmlToJson(xml, strict = strict)

        assert(expectedJson.contains(actualJson))
      }
    }
  }

  describe("Non-compliant XML input") {

    val xmlInputs = List(
      """ "504967 Quattordici lettere inedite di Pietro Mascagni NRMI, iv (1970) 1970 Italian 493-513" """ ->
        <book>
          <book-id>504967</book-id>
          <title>Quattordici lettere inedite di Pietro Mascagni</title>
          <publication>NRMI, iv (1970)</publication>
          <publication-date>1970</publication-date>
          <language>Italian</language>
          <page-range>493-513</page-range>
        </book>
    )

    it ("must succeed and default to a concatenation of string values") {

      for {
        strict      <- List(true, false)
        (json, xml) <- xmlInputs
      } locally {

        def normalize(v: Json): Json =
          v.asString.map(s => Json.fromString(Whitespace.collapseWhitespace(s).toString)).getOrElse(v)

        val expectedJson = parser.parse(json).map(normalize)
        val actualJson   = normalize(Converter.xmlToJson(xml, strict = strict))

        assert(expectedJson.contains(actualJson))
      }
    }
  }
}
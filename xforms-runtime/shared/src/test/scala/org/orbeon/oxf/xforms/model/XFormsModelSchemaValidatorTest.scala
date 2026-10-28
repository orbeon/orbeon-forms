package org.orbeon.oxf.xforms.model

import org.orbeon.apache.xerces.parsers.{NonValidatingConfiguration, SAXParser}
import org.orbeon.apache.xerces.util.SymbolTable
import org.orbeon.apache.xerces.xni.parser.XMLInputSource
import org.orbeon.dom
import org.orbeon.dom.io.{SAXContentHandler, SAXReader}
import org.orbeon.oxf.xml.{XMLConstants, XMLReceiver}
import org.scalatest.funspec.AnyFunSpecLike

import java.io.StringReader


class XFormsModelSchemaValidatorTest extends AnyFunSpecLike {

  private def readOrbeonDom(xmlString: String): dom.Document = {
    val receiver =
      new SAXContentHandler(
        systemIdOpt         = None,
        mergeAdjacentText   = SAXReader.MergeAdjacentText,
        stripWhitespaceText = SAXReader.StripWhitespaceText,
        ignoreComments      = SAXReader.IgnoreComments
      ) with XMLReceiver

    val config = new NonValidatingConfiguration(new SymbolTable)
    val parser = new SAXParser(config)
    parser.setContentHandler(receiver)
    parser.parse(new XMLInputSource(null, null, null, new StringReader(xmlString), null))
    receiver.getDocument
  }

  private val ModelWithInlineSchemaXml =
    """<xf:model xmlns:xf="http://www.w3.org/2002/xforms"
      |          xmlns:xs="http://www.w3.org/2001/XMLSchema"
      |          xmlns:ex="http://example.org/schema">
      |    <xs:schema targetNamespace="http://example.org/schema"
      |               xmlns="http://example.org/schema"
      |               elementFormDefault="qualified">
      |
      |        <xs:simpleType name="zip-code">
      |            <xs:restriction base="xs:string">
      |                <xs:pattern value="[0-9]{5}"/>
      |            </xs:restriction>
      |        </xs:simpleType>
      |
      |        <xs:element name="order">
      |            <xs:complexType>
      |                <xs:sequence>
      |                    <xs:element name="item" maxOccurs="unbounded">
      |                        <xs:complexType>
      |                            <xs:sequence>
      |                                <xs:element name="code" type="xs:string"/>
      |                                <xs:element name="quantity" type="xs:positiveInteger"/>
      |                            </xs:sequence>
      |                            <xs:attribute name="id" type="xs:string" use="required"/>
      |                        </xs:complexType>
      |                    </xs:element>
      |                    <xs:element name="selected-item-ref" minOccurs="0" maxOccurs="unbounded">
      |                        <xs:complexType>
      |                            <xs:attribute name="ref" type="xs:string" use="required"/>
      |                        </xs:complexType>
      |                    </xs:element>
      |                </xs:sequence>
      |            </xs:complexType>
      |            <xs:key name="item-key">
      |                <xs:selector xpath="ex:item"/>
      |                <xs:field xpath="@id"/>
      |            </xs:key>
      |            <xs:keyref name="item-keyref" refer="ex:item-key">
      |                <xs:selector xpath="ex:selected-item-ref"/>
      |                <xs:field xpath="@ref"/>
      |            </xs:keyref>
      |        </xs:element>
      |    </xs:schema>
      |</xf:model>""".stripMargin

  private def newInlineSchemaValidator(): XFormsModelSchemaValidator = {
    val modelDoc  = readOrbeonDom(ModelWithInlineSchemaXml)
    val validator = new XFormsModelSchemaValidator(modelDoc.getRootElement, null)
    validator.loadSchemas(null)
    validator
  }

  describe("XFormsModelSchemaValidator datatype validation") {

    it("must validate custom simpleType and built-in XSD types") {
      val validator = newInlineSchemaValidator()
      assert(validator.hasSchema)

      assert(
        validator.validateDatatype(
          value            = "94105",
          typeNamespaceURI = "http://example.org/schema",
          typeLocalname    = "zip-code",
          typeQName        = "ex:zip-code",
          locationData     = null
        ) eq null
      )

      assert(
        validator.validateDatatype(
          value            = "ABCDE",
          typeNamespaceURI = "http://example.org/schema",
          typeLocalname    = "zip-code",
          typeQName        = "ex:zip-code",
          locationData     = null
        ) ne null
      )

      assert(
        validator.validateDatatype(
          value            = "42",
          typeNamespaceURI = XMLConstants.XSD_URI,
          typeLocalname    = "integer",
          typeQName        = "xs:integer",
          locationData     = null
        ) eq null
      )

      assert(
        validator.validateDatatype(
          value            = "not-an-int",
          typeNamespaceURI = XMLConstants.XSD_URI,
          typeLocalname    = "integer",
          typeQName        = "xs:integer",
          locationData     = null
        ) ne null
      )
    }
  }

  describe("XFormsModelSchemaValidator document and identity constraint validation") {

    it("must validate a conforming document with strict validation") {
      val validator = newInlineSchemaValidator()
      val doc = readOrbeonDom(
        """<order xmlns="http://example.org/schema">
          |    <item id="i1">
          |        <code>A100</code>
          |        <quantity>3</quantity>
          |    </item>
          |    <item id="i2">
          |        <code>B200</code>
          |        <quantity>1</quantity>
          |    </item>
          |    <selected-item-ref ref="i1"/>
          |</order>""".stripMargin
      )

      assert(validator.validateDocument(doc.getRootElement, isLax = false, isStrict = true))
      assert(InstanceData.getValid(doc.getRootElement))
    }

    it("must reject invalid element content and mark offending element invalid") {
      val validator = newInlineSchemaValidator()
      val doc = readOrbeonDom(
        """<order xmlns="http://example.org/schema">
          |    <item id="i1">
          |        <code>A100</code>
          |        <quantity>-5</quantity>
          |    </item>
          |</order>""".stripMargin
      )

      assert(! validator.validateDocument(doc.getRootElement, isLax = false, isStrict = true))
      val quantityElem = doc.getRootElement.element("item").element("quantity")
      assert(! InstanceData.getValid(quantityElem))
    }

    it("must detect duplicate keys via IDConstraintChecker") {
      val validator = newInlineSchemaValidator()
      val doc = readOrbeonDom(
        """<order xmlns="http://example.org/schema">
          |    <item id="dup">
          |        <code>A100</code>
          |        <quantity>1</quantity>
          |    </item>
          |    <item id="dup">
          |        <code>B200</code>
          |        <quantity>2</quantity>
          |    </item>
          |</order>""".stripMargin
      )

      assert(! validator.validateDocument(doc.getRootElement, isLax = false, isStrict = true))
      val items = doc.getRootElement.elements("item")
      assert(! InstanceData.getValid(items(1)))
    }

    it("must detect unresolved keyref via IDConstraintChecker") {
      val validator = newInlineSchemaValidator()
      val doc = readOrbeonDom(
        """<order xmlns="http://example.org/schema">
          |    <item id="i1">
          |        <code>A100</code>
          |        <quantity>1</quantity>
          |    </item>
          |    <selected-item-ref ref="non-existent"/>
          |</order>""".stripMargin
      )

      assert(! validator.validateDocument(doc.getRootElement, isLax = false, isStrict = true))
      val refElem = doc.getRootElement.element("selected-item-ref")
      assert(! InstanceData.getValid(refElem))
    }
  }
}

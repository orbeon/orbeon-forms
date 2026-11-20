/**
 * Copyright (C) 2025 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.proxy

import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.fr.datamigration.PathElem
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.TransformerUtils
import org.scalatest.funspec.AnyFunSpecLike

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.Base64 as JBase64


class FieldEncryptionTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  private def withProperties[T](url: String)(body: => T): T = {
    Properties.initialize(url)
    try body
    finally {
      Properties.initialize("oxf:/ops/unit-tests/properties.xml")
    }
  }

  // Real pre-change XML form data at rest encrypted with Google Tink 1.20.0 `AesGcmJce`
  // using password `ForTestsOnly-1!T42mMw*pgH9.EfTZx!4r` (from `properties-encryption-1.xml`)
  private val PreChangeEncryptedFormDataXml =
    """<form xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
      |  <section>
      |    <clear>42</clear>
      |    <encrypted-ascii fr:value-encrypted="true">4gcE0cY39H6A28IYyjzcaHwT4nCtlfVOISljZ6qD</encrypted-ascii>
      |    <encrypted-empty fr:value-encrypted="true">LZWoG8U5rfXUY2Yqe/bI1HHCiF2r5KKO8mR2Ow==</encrypted-empty>
      |    <encrypted-utf8 fr:value-encrypted="true">BRh7wFxXvHbI99rOI+Orafi4MLQ/aOG/zwudmpOQ953pugSTcwjOdF/magRW+GtWeMp1P1EwlNzyxiRWEG+FLMA=</encrypted-utf8>
      |    <encrypted-iv01 fr:value-encrypted="true">AUHFTmyiuv251TtpAfW6wDemP1+AQvuQKvL+XNxZ8/ZdcJgjxS87nyXWGlfNusCThfi90aiTNVEgdt0VPIXveM1jzD8mlpU=</encrypted-iv01>
      |    <encrypted-legacy-att encrypted="true">4gcE0cY39H6A28IYyjzcaHwT4nCtlfVOISljZ6qD</encrypted-legacy-att>
      |  </section>
      |</form>""".stripMargin

  private val ExpectedBinaryAttachmentBytes: Array[Byte] =
    Array(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0xFF, 0x01, 0x02, 0x03, 0x04, 0x7F, 0x80).map(_.toByte)

  // Real pre-change binary attachments encrypted with Google Tink 1.20.0 `AesGcmJce`
  private val PreChangeEncryptedAttachments: List[(String, Array[Byte])] = List(
    "normal IV"     -> JBase64.getDecoder.decode("euwQ8wJrqB/sJj8Q8gLr55h7mTo8Q6glUe2RzLNrMs+EhwwoD+yqplKb8jg="),
    "0x01-start IV" -> JBase64.getDecoder.decode("AYcgoEKSimdvRQYTFufZ9zJOSdwxlLDgJtQjh+BPlfoi4e0DwS/x48u6Ab4=")
  )

  private def decryptXml(xml: String): org.orbeon.dom.Document = {
    val out = new ByteArrayOutputStream
    FieldEncryption.decryptDataXmlTransform(
      new ByteArrayInputStream(xml.getBytes(CharsetNames.Utf8)),
      out
    )
    TransformerUtils.readOrbeonDom(new ByteArrayInputStream(out.toByteArray), null, false, false)
  }

  private def decryptAttachment(bytes: Array[Byte]): Array[Byte] = {
    val out = new ByteArrayOutputStream
    FieldEncryption.decryptAttachmentTransform(new ByteArrayInputStream(bytes), out)
    out.toByteArray
  }

  describe("Pre-change encrypted data at rest backward compatibility") {

    val propertyConfigs = List(
      "non-FIPS primary password"  -> "oxf:/ops/unit-tests/properties-encryption-1.xml",
      "non-FIPS fallback password" -> "oxf:/ops/unit-tests/properties-encryption-2.xml",
      "FIPS primary password"      -> "oxf:/ops/unit-tests/properties-encryption-fips-1.xml",
      "FIPS fallback password"     -> "oxf:/ops/unit-tests/properties-encryption-fips-2.xml"
    )

    for ((label, propertiesUrl) <- propertyConfigs) {
      it(s"must decrypt pre-change Tink XML form data and binary attachments ($label)") {
        withProperties(propertiesUrl) {
          val decryptedDoc = decryptXml(PreChangeEncryptedFormDataXml)
          val section      = decryptedDoc.getRootElement.element("section")

          assert(section.element("clear").getStringValue           == "42")
          assert(section.element("encrypted-ascii").getStringValue == "43")
          assert(section.element("encrypted-empty").getStringValue == "")
          assert(section.element("encrypted-utf8").getStringValue  == """<tag attr="a&b">Café 日本語</tag>""")
          assert(section.element("encrypted-iv01").getStringValue  == "Value whose legacy Tink IV starts with 0x01")
          assert(section.element("encrypted-legacy-att").getStringValue == "43")

          // Verify encryption marker attributes were stripped on decrypt
          assert(section.element("encrypted-ascii").attributeValueOpt("value-encrypted").isEmpty)
          assert(section.element("encrypted-legacy-att").attributeValueOpt("encrypted").isEmpty)

          // Verify binary attachments decrypt identically
          for ((_, encryptedBytes) <- PreChangeEncryptedAttachments)
            assert(decryptAttachment(encryptedBytes) sameElements ExpectedBinaryAttachmentBytes)
        }
      }
    }

    it("must re-encrypt form data as Version 1 in FIPS mode and remain readable in both FIPS and non-FIPS modes") {
      val encryptedXmlInFips =
        withProperties("oxf:/ops/unit-tests/properties-encryption-fips-1.xml") {
          val dom = TransformerUtils.readOrbeonDom(
            new ByteArrayInputStream(
              """<form><section><clear>42</clear><encrypted>Sensitive FIPS data</encrypted></section></form>""".getBytes(CharsetNames.Utf8)
            ),
            null,
            false,
            false
          )
          val docInfo = new DocumentWrapper(dom, null, XPath.GlobalConfiguration)
          FieldEncryption.encryptMutableFormData(
            List(List(PathElem("section"), PathElem("encrypted"))),
            docInfo
          )

          val encryptedText = dom.getRootElement.element("section").element("encrypted").getStringValue
          val rawBytes      = JBase64.getDecoder.decode(encryptedText)
          assert(rawBytes(0) == 0x01.toByte)
          assert(rawBytes.length == 1 + 16 + 12 + "Sensitive FIPS data".getBytes(CharsetNames.Utf8).length + 16)

          dom.getRootElement.serializeToString()
        }

      for (propertiesUrl <- List(
        "oxf:/ops/unit-tests/properties-encryption-fips-1.xml",
        "oxf:/ops/unit-tests/properties-encryption-fips-2.xml",
        "oxf:/ops/unit-tests/properties-encryption-1.xml",
        "oxf:/ops/unit-tests/properties-encryption-2.xml"
      )) {
        withProperties(propertiesUrl) {
          val decrypted = decryptXml(encryptedXmlInFips)
          assert(decrypted.getRootElement.element("section").element("clear").getStringValue     == "42")
          assert(decrypted.getRootElement.element("section").element("encrypted").getStringValue == "Sensitive FIPS data")
        }
      }
    }
  }
}

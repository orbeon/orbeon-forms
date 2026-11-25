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
          assert(rawBytes.take(4) sameElements Array[Byte](0x4F, 0x46, 0x00, 0x01))
          assert(rawBytes.length == 8 + 16 + 12 + "Sensitive FIPS data".getBytes(CharsetNames.Utf8).length + 16)

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

    it("must encrypt each repeated section/grid iteration with a distinct IV and skip XML attachment holder URIs") {
      withProperties("oxf:/ops/unit-tests/properties-encryption-fips-low-iter.xml") {
        val inputXml =
          """<form>
            |  <rep-section>
            |    <rep-section-iteration><secret>Identical repeated secret</secret></rep-section-iteration>
            |    <rep-section-iteration><secret>Identical repeated secret</secret></rep-section-iteration>
            |    <rep-section-iteration><secret>Third repeated secret</secret></rep-section-iteration>
            |  </rep-section>
            |  <main-section>
            |    <rep-grid>
            |      <rep-grid-iteration><grid-secret>Grid row 1</grid-secret></rep-grid-iteration>
            |      <rep-grid-iteration><grid-secret>Grid row 2</grid-secret></rep-grid-iteration>
            |    </rep-grid>
            |    <file-attachment filename="doc.pdf" mediatype="application/pdf" size="1024">/fr/service/persistence/crud/a/b/data/1/f.bin</file-attachment>
            |    <empty-attachment filename="" mediatype="" size=""/>
            |    <attributed-text custom="preserve-me">Confidential note</attributed-text>
            |  </main-section>
            |</form>""".stripMargin

        val dom = TransformerUtils.readOrbeonDom(
          new ByteArrayInputStream(inputXml.getBytes(CharsetNames.Utf8)),
          null,
          false,
          false
        )
        val docInfo = new DocumentWrapper(dom, null, XPath.GlobalConfiguration)

        FieldEncryption.encryptMutableFormData(
          List(
            List(PathElem("rep-section"), PathElem("rep-section-iteration"), PathElem("secret")),
            List(PathElem("main-section"), PathElem("rep-grid"), PathElem("rep-grid-iteration"), PathElem("grid-secret")),
            List(PathElem("main-section"), PathElem("file-attachment")),
            List(PathElem("main-section"), PathElem("empty-attachment")),
            List(PathElem("main-section"), PathElem("attributed-text"))
          ),
          docInfo
        )

        val repSecrets = dom.getRootElement.element("rep-section").elements("rep-section-iteration").map(_.element("secret"))
        assert(repSecrets.size == 3)
        assert(repSecrets.forall(_.attributeValueOpt("value-encrypted").contains("true")))
        // Identical cleartext values must produce distinct ciphertexts (fresh random 12-byte GCM IV per field)
        assert(repSecrets(0).getStringValue != repSecrets(1).getStringValue)

        val mainSec = dom.getRootElement.element("main-section")
        // Attachment holders must keep their URI/empty text unencrypted in XML
        assert(mainSec.element("file-attachment").attributeValueOpt("value-encrypted").isEmpty)
        assert(mainSec.element("file-attachment").getStringValue == "/fr/service/persistence/crud/a/b/data/1/f.bin")
        assert(mainSec.element("empty-attachment").attributeValueOpt("value-encrypted").isEmpty)

        // Normal text control with custom attribute must be encrypted while keeping its custom attribute
        assert(mainSec.element("attributed-text").attributeValueOpt("value-encrypted").contains("true"))
        assert(mainSec.element("attributed-text").attributeValueOpt("custom").contains("preserve-me"))

        val decryptedDoc = decryptXml(dom.getRootElement.serializeToString())
        val decRepValues = decryptedDoc.getRootElement.element("rep-section").elements("rep-section-iteration").map(_.element("secret").getStringValue)
        assert(decRepValues == List("Identical repeated secret", "Identical repeated secret", "Third repeated secret"))

        val decGridValues = decryptedDoc.getRootElement.element("main-section").element("rep-grid").elements("rep-grid-iteration").map(_.element("grid-secret").getStringValue)
        assert(decGridValues == List("Grid row 1", "Grid row 2"))

        val decAttrText = decryptedDoc.getRootElement.element("main-section").element("attributed-text")
        assert(decAttrText.getStringValue == "Confidential note")
        assert(decAttrText.attributeValueOpt("custom").contains("preserve-me"))
        assert(decAttrText.attributeValueOpt("value-encrypted").isEmpty)
      }
    }

    it("must decrypt Version 1 XML and binary attachments (including 0-byte and multi-KB) after oxf.crypto.kdf.iterations changes") {
      val multiKbBytes = Array.tabulate[Byte](8192)(i => (i * 31 + 7).toByte)

      val (encryptedXml10k, encryptedEmptyAttach10k, encryptedLargeAttach10k) =
        withProperties("oxf:/ops/unit-tests/properties-encryption-fips-low-iter.xml") {
          assert(org.orbeon.oxf.util.SecureUtils.getKdfIterations == 10000)

          val dom = TransformerUtils.readOrbeonDom(
            new ByteArrayInputStream("""<form><section><encrypted>Encrypted at 10k iterations</encrypted></section></form>""".getBytes(CharsetNames.Utf8)),
            null,
            false,
            false
          )
          FieldEncryption.encryptMutableFormData(
            List(List(PathElem("section"), PathElem("encrypted"))),
            new DocumentWrapper(dom, null, XPath.GlobalConfiguration)
          )

          (
            dom.getRootElement.serializeToString(),
            org.orbeon.oxf.util.SecureUtils.AesGcm.encrypt(Array.emptyByteArray),
            org.orbeon.oxf.util.SecureUtils.AesGcm.encrypt(multiKbBytes)
          )
        }

      // Decrypt under default 310,000 iterations with both primary password and rotated fallback password
      for (propertiesUrl <- List(
        "oxf:/ops/unit-tests/properties-encryption-fips-1.xml",
        "oxf:/ops/unit-tests/properties-encryption-fips-2.xml"
      )) {
        withProperties(propertiesUrl) {
          assert(org.orbeon.oxf.util.SecureUtils.getKdfIterations == 310000)

          val decDoc = decryptXml(encryptedXml10k)
          assert(decDoc.getRootElement.element("section").element("encrypted").getStringValue == "Encrypted at 10k iterations")
          assert(decryptAttachment(encryptedEmptyAttach10k).isEmpty)
          assert(decryptAttachment(encryptedLargeAttach10k) sameElements multiKbBytes)
        }
      }
    }

    it("must fail closed when field-encryption password is missing or ciphertext is corrupted") {
      val encryptedXml =
        withProperties("oxf:/ops/unit-tests/properties-encryption-fips-low-iter.xml") {
          val dom = TransformerUtils.readOrbeonDom(
            new ByteArrayInputStream("""<form><section><encrypted>Must fail closed</encrypted></section></form>""".getBytes(CharsetNames.Utf8)),
            null,
            false,
            false
          )
          FieldEncryption.encryptMutableFormData(
            List(List(PathElem("section"), PathElem("encrypted"))),
            new DocumentWrapper(dom, null, XPath.GlobalConfiguration)
          )
          dom.getRootElement.serializeToString()
        }

      val encryptedAttach =
        withProperties("oxf:/ops/unit-tests/properties-encryption-fips-low-iter.xml") {
          org.orbeon.oxf.util.SecureUtils.AesGcm.encrypt(ExpectedBinaryAttachmentBytes)
        }

      // Missing `oxf.fr.field-encryption.password` in default unit-test properties must throw ConfigurationException
      intercept[org.orbeon.oxf.common.ConfigurationException] {
        decryptXml(encryptedXml)
      }
      intercept[org.orbeon.oxf.common.ConfigurationException] {
        decryptAttachment(encryptedAttach)
      }

      // Corrupted XML ciphertext and corrupted attachment ciphertext must throw GeneralSecurityException
      withProperties("oxf:/ops/unit-tests/properties-encryption-fips-low-iter.xml") {
        val corruptedAttach = encryptedAttach.clone()
        corruptedAttach(corruptedAttach.length - 1) = (corruptedAttach(corruptedAttach.length - 1) ^ 0xff).toByte
        intercept[java.security.GeneralSecurityException] {
          decryptAttachment(corruptedAttach)
        }

        val corruptedXml =
          """<form xmlns:fr="http://orbeon.org/oxf/xml/form-runner"><section><encrypted fr:value-encrypted="true">AAAA</encrypted></section></form>"""
        intercept[java.security.GeneralSecurityException] {
          decryptXml(corruptedXml)
        }
      }
    }
  }
}

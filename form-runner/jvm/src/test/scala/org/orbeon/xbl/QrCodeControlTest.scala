/**
  * Copyright (C) 2026 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xbl

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.control.{XFormsValueComponentControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike

import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO


class QrCodeControlTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  describe("QrCodeSupport QR matrix and PNG/SVG encoder") {

    it("must generate valid ISO/IEC 18004 QR matrices across error correction levels and payload lengths") {
      val levels = List("L", "M", "Q", "H")
      levels.foreach { ec =>
        val matrixOpt = QrCodeSupport.encodeMatrix("https://www.orbeon.com/", ec)
        assert(matrixOpt.isDefined, s"Expected matrix for EC level $ec")
        val matrix = matrixOpt.get
        val size   = matrix.length
        assert(size >= 21 && (size - 17) % 4 == 0)

        // Top-left, top-right, and bottom-left finder pattern outer corners and centers must be dark
        assert(matrix(0)(0) && matrix(3)(3) && matrix(6)(6))
        assert(matrix(0)(size - 1) && matrix(3)(size - 4))
        assert(matrix(size - 1)(0) && matrix(size - 4)(3))
        // Dark module at (x=8, y=size-8)
        assert(matrix(size - 8)(8))
      }

      // Multi-block UTF-8 payload (Version 6 for M, Version 7+ for Q)
      val longText = "https://www.orbeon.com/fr/acme/order/view/12345678-90ab-cdef-1234-567890abcdef?lang=fr&token=éàü"
      val longMatrixMOpt = QrCodeSupport.encodeMatrix(longText, "M")
      assert(longMatrixMOpt.isDefined)
      assert(longMatrixMOpt.get.length >= 41)

      val longMatrixQOpt = QrCodeSupport.encodeMatrix(longText, "Q")
      assert(longMatrixQOpt.isDefined)
      assert(longMatrixQOpt.get.length >= 45)

      // Version 11..40 payloads (e.g. 500-byte payload and maximum Version 40 L payload of 2,953 bytes)
      val payload500 = "A" * 500
      val matrix500Opt = QrCodeSupport.encodeMatrix(payload500, "M")
      assert(matrix500Opt.isDefined)
      assert(matrix500Opt.get.length == 17 * 4 + 17) // Version 17 (85x85) for 500 bytes at level M

      val maxV40Payload = "B" * 2953
      val matrixV40Opt = QrCodeSupport.encodeMatrix(maxV40Payload, "L")
      assert(matrixV40Opt.isDefined)
      assert(matrixV40Opt.get.length == 40 * 4 + 17) // 177x177 modules at Version 40

      // Exceeding Version 40 capacity must return None
      assert(QrCodeSupport.encodeMatrix("B" * 2954, "L").isEmpty)
      assert(QrCodeSupport.encodeMatrix("").isEmpty)
    }

    it("must encode a valid lossless 1-bit monochrome PNG data URI readable by ImageIO") {
      val uri = QrCodeSupport.toPngDataUri("https://www.orbeon.com/", "M", "160")
      assert(uri.startsWith("data:image/png;base64,"))

      val base64Part = uri.stripPrefix("data:image/png;base64,")
      val pngBytes   = Base64.getDecoder.decode(base64Part)
      val image      = ImageIO.read(new ByteArrayInputStream(pngBytes))

      assert(image ne null)
      assert(image.getWidth == image.getHeight)
      assert(image.getWidth >= 140 && image.getWidth <= 180)

      // Quiet zone top-left corner pixel (0, 0) must be white (0xFFFFFFFF)
      assert(image.getRGB(0, 0) == 0xFFFFFFFF)

      val svg = QrCodeSupport.toSvg("https://www.orbeon.com/", "M", "160")
      assert(svg.startsWith("<svg ") && svg.endsWith("</svg>"))
    }
  }

  describe("fr:qr-code control") {

    it("must render QR code from bound value or content AVT and update dynamically") {

      val xhtml =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="fr-form-instance">
                <form>
                  <ticket-id>ORD-2026-0042</ticket-id>
                  <qr-bound>https://www.orbeon.com/ticket/ORD-2026-0042</qr-bound>
                  <qr-avt/>
                </form>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:qr-code id="bound-qr" ref="qr-bound" show-value="true">
              <xf:label>Bound QR</xf:label>
            </fr:qr-code>
            <fr:qr-code id="avt-qr" ref="qr-avt" content="https://example.com/verify/{/form/ticket-id}" size="200" error-correction="Q" show-value="true">
              <xf:label>AVT QR</xf:label>
            </fr:qr-code>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { doc =>
          withActionAndDoc(doc) {
            val boundComponent = doc.getControlByEffectiveId("bound-qr").asInstanceOf[XFormsValueComponentControl]
            val boundCaption   = doc.getControlByEffectiveId("bound-qr≡qr-caption").asInstanceOf[XFormsValueControl]
            assert(boundComponent.getValue(EventCollector.Throw) == "https://www.orbeon.com/ticket/ORD-2026-0042")
            assert(boundCaption.getValue(EventCollector.Throw)   == "https://www.orbeon.com/ticket/ORD-2026-0042")

            val avtCaption = doc.getControlByEffectiveId("avt-qr≡qr-caption").asInstanceOf[XFormsValueControl]
            assert(avtCaption.getValue(EventCollector.Throw) == "https://example.com/verify/ORD-2026-0042")

            // Update ticket-id -> AVT QR caption updates automatically
            withAction {
              XFormsAPI.setvalue(doc.findInstance("fr-form-instance").get.rootElement / "ticket-id", "ORD-2026-9999")
            }
            assert(avtCaption.getValue(EventCollector.Throw) == "https://example.com/verify/ORD-2026-9999")
          }
        }
      }
    }
  }
}

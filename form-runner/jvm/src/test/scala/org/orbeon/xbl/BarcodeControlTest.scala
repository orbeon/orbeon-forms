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


class BarcodeControlTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  describe("BarcodeSupport 1D encoder and PNG/SVG output") {

    it("must encode CODE128 (Set B and Set C compression), CODE39, EAN13, EAN8, UPCA, ITF, and CODABAR") {
      // CODE128 alphanumeric (Set B) and numeric-heavy (Set C compression)
      val c128Alpha = BarcodeSupport.encodeModules("ORBEON-2026", "CODE128")
      assert(c128Alpha.isDefined)
      assert(c128Alpha.get.head && c128Alpha.get.last)

      // Pure 10-digit string in CODE128 should use Start C (1 star + 5 pairs + 1 check + 1 stop = 8 symbols -> 7*11 + 13 = 90 modules)
      val c128Digits = BarcodeSupport.encodeModules("1234567890", "CODE128")
      assert(c128Digits.isDefined)
      assert(c128Digits.get.length == 7 * 11 + 13)

      // CODE39
      val c39Opt = BarcodeSupport.encodeWithDisplayValue("ord-42", "CODE39")
      assert(c39Opt.isDefined)
      assert(c39Opt.get._2 == "ORD-42")
      assert(BarcodeSupport.encodeModules("invalid@char", "CODE39").isEmpty)

      // EAN13: 12 digits -> auto-appends check digit '7' for 590123412345; 13 digits -> validates check digit
      val ean13Auto = BarcodeSupport.encodeWithDisplayValue("590123412345", "EAN13")
      assert(ean13Auto.isDefined)
      assert(ean13Auto.get._1.length == 95)
      assert(ean13Auto.get._2 == "5901234123457")
      assert(BarcodeSupport.encodeModules("5901234123457", "EAN13").isDefined)
      assert(BarcodeSupport.encodeModules("5901234123450", "EAN13").isEmpty) // wrong check digit

      // EAN8: 7 digits -> auto-appends check digit '4' for 9638507
      val ean8Auto = BarcodeSupport.encodeWithDisplayValue("9638507", "EAN8")
      assert(ean8Auto.isDefined)
      assert(ean8Auto.get._1.length == 67)
      assert(ean8Auto.get._2 == "96385074")

      // UPCA: 11 digits -> auto-appends check digit '2' for 03600029145
      val upcAuto = BarcodeSupport.encodeWithDisplayValue("03600029145", "UPCA")
      assert(upcAuto.isDefined)
      assert(upcAuto.get._1.length == 95)
      assert(upcAuto.get._2 == "036000291452")

      // ITF: odd number of digits gets leading '0'
      val itfOpt = BarcodeSupport.encodeWithDisplayValue("12345", "ITF")
      assert(itfOpt.isDefined)
      assert(itfOpt.get._2 == "012345")

      // CODABAR
      val codabarOpt = BarcodeSupport.encodeWithDisplayValue("40156", "CODABAR")
      assert(codabarOpt.isDefined)
      assert(codabarOpt.get._2 == "40156")

      assert(BarcodeSupport.encodeModules("", "CODE128").isEmpty)
    }

    it("must encode a valid lossless 1-bit monochrome PNG data URI and SVG") {
      val uri = BarcodeSupport.toPngDataUri("ORBEON-12345", "CODE128", "60", "2")
      assert(uri.startsWith("data:image/png;base64,"))

      val base64Part = uri.stripPrefix("data:image/png;base64,")
      val pngBytes   = Base64.getDecoder.decode(base64Part)
      val image      = ImageIO.read(new ByteArrayInputStream(pngBytes))

      assert(image ne null)
      assert(image.getHeight == 60)
      assert(image.getWidth == BarcodeSupport.imageWidthPx("ORBEON-12345", "CODE128", "2"))
      // Quiet zone left pixel (0, 30) must be white (0xFFFFFFFF)
      assert(image.getRGB(0, 30) == 0xFFFFFFFF)

      val svg = BarcodeSupport.toSvg("ORBEON-12345", "CODE128", "60", "2")
      assert(svg.startsWith("<svg ") && svg.endsWith("</svg>"))
    }
  }

  describe("fr:barcode control") {

    it("must render barcode from bound value or content AVT and update dynamically") {

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
                  <order-num>20260042</order-num>
                  <barcode-bound>590123412345</barcode-bound>
                  <barcode-avt/>
                </form>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:barcode id="bound-bc" ref="barcode-bound" format="EAN13">
              <xf:label>EAN-13 Barcode</xf:label>
            </fr:barcode>
            <fr:barcode id="avt-bc" ref="barcode-avt" content="ORD-{/form/order-num}" format="CODE39" height="80" module-width="2">
              <xf:label>Order Barcode</xf:label>
            </fr:barcode>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { doc =>
          withActionAndDoc(doc) {
            val boundComponent = doc.getControlByEffectiveId("bound-bc").asInstanceOf[XFormsValueComponentControl]
            val boundCaption   = doc.getControlByEffectiveId("bound-bc≡barcode-caption").asInstanceOf[XFormsValueControl]
            assert(boundComponent.getValue(EventCollector.Throw) == "590123412345")
            // EAN-13 caption includes the computed modulo-10 check digit '7'
            assert(boundCaption.getValue(EventCollector.Throw)   == "5901234123457")

            val avtCaption = doc.getControlByEffectiveId("avt-bc≡barcode-caption").asInstanceOf[XFormsValueControl]
            assert(avtCaption.getValue(EventCollector.Throw) == "ORD-20260042")

            // Update order-num -> AVT barcode caption updates automatically
            withAction {
              XFormsAPI.setvalue(doc.findInstance("fr-form-instance").get.rootElement / "order-num", "20269999")
            }
            assert(avtCaption.getValue(EventCollector.Throw) == "ORD-20269999")
          }
        }
      }
    }
  }
}

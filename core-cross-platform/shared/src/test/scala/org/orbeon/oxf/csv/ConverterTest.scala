/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.csv

import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike

class ConverterTest extends AnyFunSpecLike {

  describe("CSV Parser") {

    it("must parse empty input as empty list") {
      assert(CsvParser.parse("").isEmpty)
    }

    it("must parse simple comma-separated values") {
      val result = CsvParser.parse("a,b,c")
      assert(result == List(List("a", "b", "c")))
    }

    it("must trim unquoted fields around delimiters") {
      val result = CsvParser.parse(" a , b , c ")
      assert(result == List(List("a", "b", "c")))
    }

    it("must preserve whitespace inside quotes") {
      val result = CsvParser.parse(" \" a \" , \" b \" ")
      assert(result == List(List(" a ", " b ")))
    }

    it("must handle escaped double quotes") {
      val result = CsvParser.parse(" \"he said \"\"hello\"\"\" , second ")
      assert(result == List(List("he said \"hello\"", "second")))
    }

    it("must handle commas inside quoted fields") {
      val result = CsvParser.parse(" 2011, \"Jan, Mar\" ")
      assert(result == List(List("2011", "Jan, Mar")))
    }

    it("must handle newlines inside quoted fields") {
      val result = CsvParser.parse("\"line 1\r\nline 2\",other")
      assert(result == List(List("line 1\r\nline 2", "other")))
    }

    it("must handle multiple lines with CRLF and LF") {
      val crlf = "a,b\r\nc,d\r\n"
      assert(CsvParser.parse(crlf) == List(List("a", "b"), List("c", "d")))

      val lf = "a,b\nc,d\n"
      assert(CsvParser.parse(lf) == List(List("a", "b"), List("c", "d")))
    }

    it("must handle empty fields") {
      val result = CsvParser.parse("a,,c,")
      assert(result == List(List("a", "", "c", "")))
    }
  }

  describe("Header element naming") {

    it("must keep valid XML names") {
      assert(Converter.headerToElementName("Year") == "Year")
      assert(Converter.headerToElementName("first_name") == "first_name")
      assert(Converter.headerToElementName("col-1.a") == "col-1.a")
    }

    it("must replace spaces and non-XML characters with underscore") {
      assert(Converter.headerToElementName("Hex value") == "Hex_value")
      assert(Converter.headerToElementName("Total ($)") == "Total____")
    }

    it("must prepend underscore when first character cannot start XML name") {
      assert(Converter.headerToElementName("123") == "_123")
      assert(Converter.headerToElementName("-abc") == "_-abc")
    }

    it("must handle empty or blank headers") {
      assert(Converter.headerToElementName("") == "_")
      assert(Converter.headerToElementName("   ") == "_")
    }
  }

  describe("Converting CSV to XML") {

    it("must convert XForms 2.0 spec Example 3 (with headers)") {
      val csv =
        "Name, Hex value\r\n" +
        "Red, #f00\r\n" +
        "Green, #0f0\r\n" +
        "Blue, #00f\r\n"

      val doc = Converter.csvStringToXmlDoc(csv, header = true)
      val root = doc.rootElement

      assert(root.localname == "csv")

      val h = (root / "h").head
      val hChildren = (h / *).toList
      assert(hChildren.map(_.localname) == List("Name", "Hex_value"))
      assert(hChildren.map(_.stringValue) == List("Name", "Hex value"))

      val rows = (root / "r").toList
      assert(rows.length == 3)

      assert((rows(0) / *).map(_.localname) == List("Name", "Hex_value"))
      assert((rows(0) / *).map(_.stringValue) == List("Red", "#f00"))

      assert((rows(1) / *).map(_.localname) == List("Name", "Hex_value"))
      assert((rows(1) / *).map(_.stringValue) == List("Green", "#0f0"))

      assert((rows(2) / *).map(_.localname) == List("Name", "Hex_value"))
      assert((rows(2) / *).map(_.stringValue) == List("Blue", "#00f"))
    }

    it("must convert XForms 2.0 spec Example 2 (without headers)") {
      val csv =
        "2011, \"Jan, Mar\"\r\n" +
        "2012, \"Feb, Apr\"\r\n"

      val doc = Converter.csvStringToXmlDoc(csv, header = false)
      val root = doc.rootElement

      assert(root.localname == "csv")
      assert((root / "h").isEmpty)

      val rows = (root / "r").toList
      assert(rows.length == 2)

      assert((rows(0) / *).map(_.localname) == List("v", "v"))
      assert((rows(0) / *).map(_.stringValue) == List("2011", "Jan, Mar"))

      assert((rows(1) / *).map(_.localname) == List("v", "v"))
      assert((rows(1) / *).map(_.stringValue) == List("2012", "Feb, Apr"))
    }

    it("must handle empty CSV input") {
      val doc = Converter.csvStringToXmlDoc("", header = true)
      val root = doc.rootElement
      assert(root.localname == "csv")
      assert((root / *).isEmpty)
    }
  }

  describe("Converting XML to CSV and round-tripping") {

    it("must convert XML with <h> to CSV with header row") {
      val csvInput =
        "Name,Hex value\r\nRed,#f00\r\nGreen,#0f0\r\nBlue,#00f"

      val doc = Converter.csvStringToXmlDoc(csvInput, header = true)
      val csvOutput = Converter.xmlToCsvString(doc, header = true)

      assert(csvOutput == csvInput)
    }

    it("must convert XML without <h> to CSV without header row") {
      val csvInput =
        "2011,\"Jan, Mar\"\r\n2012,\"Feb, Apr\""

      val doc = Converter.csvStringToXmlDoc(csvInput, header = false)
      val csvOutput = Converter.xmlToCsvString(doc, header = false)

      assert(csvOutput == csvInput)
    }

    it("must omit header in xmlToCsv when header=false even if <h> is present") {
      val csvInput =
        "Name,Hex value\r\nRed,#f00\r\nGreen,#0f0\r\nBlue,#00f"

      val doc = Converter.csvStringToXmlDoc(csvInput, header = true)
      val csvOutput = Converter.xmlToCsvString(doc, header = false)

      assert(csvOutput == "Red,#f00\r\nGreen,#0f0\r\nBlue,#00f")
    }

    it("must roundtrip fields containing special characters and quotes") {
      val csvInput =
        "\"Header, 1\",\"Header \"\"2\"\"\"\r\n\"Val, 1\",\"Val \"\"2\"\"\""

      val doc = Converter.csvStringToXmlDoc(csvInput, header = true)
      val csvOutput = Converter.xmlToCsvString(doc, header = true)

      assert(csvOutput == csvInput)
    }
  }
}

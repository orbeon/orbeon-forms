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

import org.orbeon.oxf.util.StaticXPath
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xml.*
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath.*
import org.xml.sax.helpers.AttributesImpl

object Converter {

  type XmlElem = om.NodeInfo

  def csvStringToXmlDoc(source: String, header: Boolean = true): StaticXPath.DocumentNodeInfoType = {
    val (receiver, result) = StaticXPath.newTinyTreeReceiver
    csvStringToXmlStream(source, receiver, header)
    result()
  }

  def csvStringToXmlStream(source: String, receiver: XMLReceiver, header: Boolean = true): Unit = {
    receiver.startDocument()
    val rows = CsvParser.parse(source)
    csvToXmlStream(rows, receiver, header)
    receiver.endDocument()
  }

  def csvToXmlStream(rows: List[List[String]], receiver: XMLReceiver, header: Boolean = true): Unit = {
    val emptyAtts = new AttributesImpl
    receiver.startElement("", Symbols.CSV, Symbols.CSV, emptyAtts)

    def emitElem(name: String, text: String): Unit = {
      receiver.startElement("", name, name, emptyAtts)
      if (text.nonEmpty) {
        val chars = text.toCharArray
        receiver.characters(chars, 0, chars.length)
      }
      receiver.endElement("", name, name)
    }

    if (rows.nonEmpty) {
      if (header) {
        val headerRow   = rows.head
        val headerNames = headerRow.map(headerToElementName)

        receiver.startElement("", Symbols.Header, Symbols.Header, emptyAtts)
        for ((name, orig) <- headerNames.zip(headerRow)) {
          emitElem(name, orig)
        }
        receiver.endElement("", Symbols.Header, Symbols.Header)

        for (row <- rows.tail) {
          receiver.startElement("", Symbols.Row, Symbols.Row, emptyAtts)
          for (idx <- row.indices) {
            val elemName = if (idx < headerNames.length) headerNames(idx) else Symbols.Value
            emitElem(elemName, row(idx))
          }
          receiver.endElement("", Symbols.Row, Symbols.Row)
        }
      } else {
        for (row <- rows) {
          receiver.startElement("", Symbols.Row, Symbols.Row, emptyAtts)
          for (v <- row) {
            emitElem(Symbols.Value, v)
          }
          receiver.endElement("", Symbols.Row, Symbols.Row)
        }
      }
    }

    receiver.endElement("", Symbols.CSV, Symbols.CSV)
  }

  def headerToElementName(header: String): String =
    header.trimAllToOpt match {
      case Some(nonBlank) => SaxonUtils.makeNCName(nonBlank, keepFirstIfPossible = true)
      case None           => "_"
    }

  def xmlToCsvString(root: XmlElem, header: Boolean = true): String = {
    val elem =
      if (root.isDocument)
        root.rootElement
      else if (root.isElement)
        root
      else
        throw new IllegalArgumentException("node must be an element or document")

    val hElemOpt = (elem / Symbols.Header).headOption
    val rElems   =
      if (elem.localname == Symbols.Row)
        List(elem)
      else
        (elem / Symbols.Row).toList

    val headerRowOpt: Option[List[String]] =
      if (header) {
        hElemOpt.map { h =>
          (h / *).map(c => c.stringValue.trimAllToOpt.getOrElse(c.localname)).toList
        }.orElse {
          rElems.headOption.flatMap { firstR =>
            val children = (firstR / *).toList
            if (children.exists(_.localname != Symbols.Value))
              Some(children.map(_.localname))
            else
              None
          }
        }
      } else {
        None
      }

    val dataRows: List[List[String]] =
      rElems.map { r =>
        (r / *).map(_.stringValue).toList
      }

    val allRows = headerRowOpt.toList ++ dataRows
    if (allRows.isEmpty)
      ""
    else
      allRows.map(row => row.map(escapeCsvField).mkString(",")).mkString("\r\n")
  }

  def escapeCsvField(field: String): String =
    if (field.contains(',') || field.contains('"') || field.contains('\r') || field.contains('\n') ||
        field.startsWith(" ") || field.endsWith(" "))
      "\"" + field.replace("\"", "\"\"") + "\""
    else
      field
}

/**
 * Copyright (C) 2020 Orbeon, Inc.
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
package org.orbeon.oxf.util

import cats.syntax.option._
import org.orbeon.datatypes.Mediatype
import org.orbeon.oxf.test.ResourceManagerSupport
import org.scalatest.funspec.AnyFunSpecLike


class MediatypesTest
  extends ResourceManagerSupport
     with AnyFunSpecLike {

  describe("Getting mediatype from headers or file extension") {

    val PdfType     = "application/pdf"
    val XslxType    = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    val DocxType    = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    val ExcelType   = "application/vnd.ms-excel"
    val WordType    = "application/msword"
    val OutlookType = "application/vnd.ms-outlook"
    val PngType     = "image/png"

    val Expected = List(
      (PdfType.some,      PdfType.some,                             None),
      (PdfType.some,      None,                                     "file.pdf".some),
      (PdfType.some,      ContentTypes.OctetStreamContentType.some, "file.pdf".some),
      (None,              None,                                     None),
      (XslxType.some,     None,                                     "file.xlsx".some),
      (DocxType.some,     None,                                     "file.docx".some),
      (ExcelType.some,    None,                                     "file.xls".some),
      (WordType.some,     None,                                     "file.doc".some),
      (OutlookType.some,  None,                                     "file.msg".some),
      (PngType.some,      None,                                     "file.png".some),
    )

    for ((expectedOpt, headerOpt, filenameOpt) <- Expected)
      it(s"must pass for `$headerOpt` / `$filenameOpt`") {
        assert(
          (expectedOpt flatMap Mediatype.unapply) ==
            Mediatypes.fromHeadersOrFilename(_ => headerOpt, filenameOpt)
        )
      }
  }
}

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
package org.orbeon.oxf.util

import org.scalatest.funspec.AnyFunSpec


class FileUtilsTest extends AnyFunSpec {

  describe("uniqueFilenames") {

    val Tests = List(
      ("no duplicates",                 List("a.pdf", "b.pdf", "c.xml"),                   List("a.pdf", "b.pdf", "c.xml")),
      ("duplicates with extension",     List("f.pdf", "f.pdf", "f.pdf"),                   List("f-1.pdf", "f-2.pdf", "f-3.pdf")),
      ("duplicates without extension",  List("f", "f"),                                    List("f-1", "f-2")),
      ("several dots",                  List("a.b.pdf", "a.b.pdf"),                        List("a.b-1.pdf", "a.b-2.pdf")),
      ("suffixed name already present", List("f.pdf", "f-1.pdf", "f.pdf"),                 List("f-2.pdf", "f-1.pdf", "f-3.pdf")),
      ("suffixed name present later",   List("f.pdf", "f.pdf", "f-1.pdf"),                 List("f-2.pdf", "f-3.pdf", "f-1.pdf")),
      ("mixed",                         List("x.pdf", "y.xml", "x.pdf", "y.xml", "x.pdf"), List("x-1.pdf", "y-1.xml", "x-2.pdf", "y-2.xml", "x-3.pdf")),
    )

    for ((description, filenames, expected) <- Tests)
      it(s"must pass with $description") {
        assert(expected == FileUtils.uniqueFilenames(filenames))
      }
  }
}

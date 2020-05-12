/**
 * Copyright (C) 2017 Orbeon, Inc.
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

import org.orbeon.oxf.util.ContentTypes._
import org.scalatest.funspec.AnyFunSpec

class ContentTypesTest extends AnyFunSpec {

  describe("The isXMLContentType method") {
    it("must handle spaces and parameters") {
      assert(true  === isXMLContentType(" text/xml ; charset=utf8"))
    }

    it("must support suffixes") {
      assert(true  === isXMLContentType(" application/atom+xml ; charset=utf8"))
    }
  }

  describe("The isJSONContentType method") {
    it("must handle spaces and parameters") {
      assert(true  === isJSONContentType(" application/json ; charset=utf8"))
    }

    it("must support suffixes") {
      assert(true  === isJSONContentType(" application/calendar+json ; charset=utf8"))
    }
  }

  describe("The getContentTypeParameters method") {
    it("must parse parameters and ignore spaces") {
      assert(Map("charset" -> "utf8", "foo" -> "bar")  === getContentTypeParameters(" text/html ; charset=utf8; foo =  bar "))
    }

    it("must ignore blank names") {
      assert(Map("charset" -> "utf8")  === getContentTypeParameters(" text/html ; charset=utf8; =  bar "))
    }

    it("must return blank values") {
      assert(Map("charset" -> "utf8", "foo" -> "")  === getContentTypeParameters(" text/html ; charset=utf8; foo =  "))
    }
  }

  describe("The isTextContentType method") {
    it("must handle spaces") {
      assert(true  === isTextContentType(" text/plain ; charset=utf8"))
    }
  }
}

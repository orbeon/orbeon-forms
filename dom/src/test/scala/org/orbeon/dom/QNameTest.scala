package org.orbeon.dom

import org.scalatest.funspec.AnyFunSpecLike


class QNameTest extends AnyFunSpecLike {

  describe("QName.fromClarkName") {
    it("should parse a fully qualified Clark name") {
      val qName = QName.fromMaybeWellFormedClarkName("{http://example.org}element")
      assert(qName.isDefined)
      assert(qName.get.localName == "element")
      assert(qName.get.namespace.uri == "http://example.org")
      assert(qName.get.namespace.prefix == "")
    }

    it("should parse a Clark name with empty namespace URI") {
      val qName = QName.fromMaybeWellFormedClarkName("{}element")
      assert(qName.isDefined)
      assert(qName.get.localName == "element")
      assert(qName.get.namespace.uri == "")
      assert(qName.get.namespace.prefix == "")
    }

    it("should parse a non-prefixed local name") {
      val qName = QName.fromMaybeWellFormedClarkName("element")
      assert(qName.isDefined)
      assert(qName.get.localName == "element")
      assert(qName.get.namespace.uri == "")
      assert(qName.get.namespace.prefix == "")
    }
  }
}

/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.xml

import org.scalatest.funspec.AnyFunSpec


class NamespaceContextTest extends AnyFunSpec {

  describe("Basic nesting") {

    implicit val ns = new NamespaceContext

    it("must only contain the `xml` prefix") {
      assert(ns.current.prefixes === Seq("xml"))
    }

    it("must handle basic nesting") {

      withPrefix("p1", "u1") {
        withPrefix("p2", "u2") {

        // p1, p2 not yet available
          assertAllEmpty(Seq("p1", "p2"))

          withElement {

            assert(ns.current.uriForPrefix("p1") === Some("u1"))
            assert(ns.current.uriForPrefix("p2") === Some("u2"))

            assert(ns.current.prefixes.toSet === Set("xml", "p1", "p2"))

            withPrefix("p3", "u3") {
            // p3 not yet available
              assertAllEmpty(Seq("p3"))

              withElement {
                assert(ns.current.uriForPrefix("p1") === Some("u1"))
                assert(ns.current.uriForPrefix("p2") === Some("u2"))
                assert(ns.current.uriForPrefix("p3") === Some("u3"))

                assert(ns.current.prefixes.toSet === Set("xml", "p1", "p2", "p3"))
              }
            }
          }

        // Once the element ends, prefixes go away
          assertAllEmpty(Seq("p1", "p2", "p3"))
        }
      }

      withPrefix("p4", "u4") {
        withElement {
          assert(ns.current.uriForPrefix("p4") === Some("u4"))
          assertAllEmpty(Seq("p1", "p2", "p3"))
          assert(ns.current.prefixes.toSet === Set("xml", "p4"))
        }

        assertAllEmpty(Seq("p1", "p2", "p3", "p4"))
      }

      // Element with no mappings in scope
      withElement {
        assertAllEmpty(Seq("p1", "p2", "p3", "p4"))
        assert(ns.current.prefixes === Seq("xml"))
      }
    }
  }

  describe("Nested with same mapping") {
    implicit val ns = new NamespaceContext

    it("must handle nesting with redeclaration of the same prefix") {
      // 2 nested elements declaring the same mapping
      withPrefix("p5", "u5") {
        withElement {
          assert(ns.current.uriForPrefix("p5") === Some("u5"))
          assert(ns.current.prefixes.toSet === Set("xml", "p5"))

          // Redefine prefix
          withPrefix("p5", "u5") {
            withElement {
              // Still there
              assert(ns.current.uriForPrefix("p5") === Some("u5"))
              assert(ns.current.prefixes.toSet === Set("xml", "p5"))
            }
          }

          // Still there
          assert(ns.current.uriForPrefix("p5") === Some("u5"))
        }
      }
    }
  }

  describe("Different prefixes") {
    implicit val ns = new NamespaceContext

    it("must handle different prefixes for the same namespace URI") {
      withPrefix("p61", "u6") {
        withPrefix("p62", "u6") {
          withElement {
            assert(ns.current.uriForPrefix("p61") === Some("u6"))
            assert(ns.current.uriForPrefix("p62") === Some("u6"))
            assert(ns.current.prefixes.toSet === Set("xml", "p61", "p62"))
            assert(ns.current.prefixesForURI("u6").toSet === Set("p61", "p62"))
          }
        }
      }
    }
  }

  describe("Undeclaration") {
    implicit val ns = new NamespaceContext

    it("must handle undeclarations") {
      withPrefix("p7", "u7") {
        withElement {
          assert(ns.current.uriForPrefix("p7") === Some("u7"))
          assert(ns.current.prefixes.toSet === Set("xml", "p7"))

          // Undeclare
          withPrefix("p7", "") {
            withElement {
              assertAllEmpty(Seq("p7"))
              assert(ns.current.prefixes === Seq("xml"))
            }
          }

          // Still there
          assert(ns.current.uriForPrefix("p7") === Some("u7"))
          assert(ns.current.prefixes.toSet === Set("xml", "p7"))
        }
      }
    }
  }

  describe("Default namespace") {
    implicit val ns = new NamespaceContext

    it("must handle the default namespace") {
      withPrefix("", "u8") {
        withElement {
          assert(ns.current.uriForPrefix("") === Some("u8"))
          assert(ns.current.prefixesForURI("u8").isEmpty)
          assert(ns.current.prefixes.toSet === Set("xml"))

          // New default prefix
          withPrefix("", "u9") {
            withElement {
              assert(ns.current.uriForPrefix("") === Some("u9"))
              assert(ns.current.prefixesForURI("u9").isEmpty)
              assert(ns.current.prefixes.toSet === Set("xml"))
            }
          }

          // Undeclare default
          withPrefix("", "") {
            withElement {
              assert(ns.current.uriForPrefix("").isEmpty)
              assert(ns.current.prefixesForURI("").isEmpty)
              assert(ns.current.prefixes === Seq("xml"))
            }
          }

          // Still there
          assert(ns.current.uriForPrefix("") === Some("u8"))
          assert(ns.current.prefixesForURI("u8").isEmpty)
          assert(ns.current.prefixes.toSet === Set("xml"))
        }
      }
    }
  }

  def assertAllEmpty(prefixes: Seq[String])(implicit ns: NamespaceContext): Unit =
    prefixes foreach (p => assert(ns.current.uriForPrefix(p).isEmpty))

  def withElement[T](body: => T)(implicit ns: NamespaceContext): T = {
    ns.startElement()
    val result = body
    ns.endElement()
    result
  }

  def withPrefix[T](prefix: String, uri: String)(body: => T)(implicit ns: NamespaceContext): T = {
    ns.startPrefixMapping(prefix, uri)
    val result = body
    result
  }
}

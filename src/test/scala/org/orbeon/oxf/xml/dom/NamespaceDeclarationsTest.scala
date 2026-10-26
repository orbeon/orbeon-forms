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
package org.orbeon.oxf.xml.dom

import org.orbeon.dom.Element
import org.orbeon.oxf.test.ResourceManagerSupport
import org.orbeon.oxf.xml.dom.Extensions.*
import org.orbeon.scaxon.NodeConversions.elemToOrbeonDomElem
import org.scalatest.funspec.AnyFunSpec


class NamespaceDeclarationsTest extends AnyFunSpec with ResourceManagerSupport {

  val XF  = "http://www.w3.org/2002/xforms"
  val XXF = "http://orbeon.org/oxf/xml/xforms"
  val FR  = "http://orbeon.org/oxf/xml/form-runner"
  val XS  = "http://www.w3.org/2001/XMLSchema"
  val FRF = "java:org.orbeon.oxf.fr.FormRunner"

  def declared(e: Element): Set[(String, String)] =
    e.declaredNamespacesIterator.map(ns => ns.prefix -> ns.uri).toSet

  describe("`removeUnneededNamespaceDeclarations`") {

    it("must keep declarations used by names, QNames and XPath expressions in attributes or text, and remove the others") {

      val elem =
        elemToOrbeonDomElem(
          <xf:input
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns="http://orbeon.org/oxf/xml/form-builder"
            xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
            xmlns:xbl="http://www.w3.org/ns/xbl"
            xmlns:p="http://www.orbeon.com/oxf/pipeline"
            xmlns:frf="java:org.orbeon.oxf.fr.FormRunner"
            xmlns:xs="http://www.w3.org/2001/XMLSchema"
            xmlns:acme="urn:acme"
            xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
            xmlns:bar="urn:bar"
            xxf:format="frf:foo()">
            <xf:label xmlns:xf="http://www.w3.org/2002/xforms" xmlns:xh="http://www.w3.org/1999/xhtml"/>
            <xf:help ref="http://example.org/"/>
            <fr:type value="xs:string"/>
            <fr:expression>bar:foo()</fr:expression>
          </xf:input>
        )

      // `xf` is already in scope at the destination
      elem.removeUnneededNamespaceDeclarations(Map("xf" -> XF))

      // `p` is not used: `http:` doesn't count; `bar` is used in a text node
      assert(declared(elem) == Set("xxf" -> XXF, "frf" -> FRF, "xs" -> XS, "fr" -> FR, "bar" -> "urn:bar"))
      assert(elem.elements.forall(e => declared(e).isEmpty))
    }

    it("must keep the default namespace declaration when it is used") {

      val elem = elemToOrbeonDomElem(<foo xmlns="urn:foo" xmlns:bar="urn:bar"><baz/></foo>)

      elem.removeUnneededNamespaceDeclarations(Map.empty)
      assert(declared(elem) == Set("" -> "urn:foo"))

      elem.removeUnneededNamespaceDeclarations(Map("" -> "urn:foo"))
      assert(declared(elem).isEmpty)
    }

    it("must keep a declaration which binds a prefix in scope to another URI") {

      val elem = elemToOrbeonDomElem(<foo:a xmlns:foo="urn:other"><foo:b/></foo:a>)

      elem.removeUnneededNamespaceDeclarations(Map("foo" -> "urn:foo"))
      assert(declared(elem) == Set("foo" -> "urn:other"))
    }

    it("must keep an `xmlns=\"\"` which undeclares the default namespace") {

      val elem = IOSupport.readOrbeonDom("""<a xmlns="urn:foo"><b xmlns=""><c/></b></a>""").getRootElement

      elem.removeUnneededNamespaceDeclarations(Map.empty)
      assert(declared(elem) == Set("" -> "urn:foo"))
      assert(declared(elem.elements.head) == Set("" -> ""))
    }
  }
}

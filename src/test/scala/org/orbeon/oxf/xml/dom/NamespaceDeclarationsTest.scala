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

  def declared(e: Element): Set[(String, String)] =
    e.declaredNamespacesIterator.map(ns => ns.prefix -> ns.uri).toSet

  describe("`removeRedundantNamespaceDeclarations`") {

    it("must remove the declarations already in scope with the same URI and keep the others") {

      val elem =
        elemToOrbeonDomElem(
          <xf:input
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
            xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
            <xf:label xmlns:xf="http://www.w3.org/2002/xforms" xmlns:fr="urn:other"/>
          </xf:input>
        )

      elem.removeRedundantNamespaceDeclarations(Map("xf" -> XF, "fr" -> FR))

      // `xxf` is not in scope, and the nested `fr` binds the prefix to another URI
      assert(declared(elem) == Set("xxf" -> XXF))
      assert(declared(elem.elements.head) == Set("fr" -> "urn:other"))
    }

    it("must treat the absence of a default namespace as `xmlns=\"\"` being in scope") {

      // Can't use XML literals for `xmlns=""`
      val elem1 = IOSupport.readOrbeonDom("""<a xmlns=""><b xmlns=""/></a>""").getRootElement
      elem1.removeRedundantNamespaceDeclarations(Map("" -> ""))
      assert(declared(elem1).isEmpty && declared(elem1.elements.head).isEmpty)

      val elem2 = IOSupport.readOrbeonDom("""<a xmlns="urn:foo"><b xmlns=""/></a>""").getRootElement
      elem2.removeRedundantNamespaceDeclarations(Map("" -> ""))
      assert(declared(elem2) == Set("" -> "urn:foo"))
      assert(declared(elem2.elements.head) == Set("" -> ""))
    }
  }
}

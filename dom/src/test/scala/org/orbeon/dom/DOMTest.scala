/**
  * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.dom

import org.scalatest.funspec.AnyFunSpec

class DOMTest extends AnyFunSpec {

  val XS = Namespace("xs", "http://www.w3.org/2001/XMLSchema")
  val XF = Namespace("xf", "http://www.w3.org/2002/xforms")

  private def newRootElem = {
    val doc = Document("root")

    val rootElem = doc.getRootElement

    rootElem.add(XS)
    rootElem.add(XF)

    rootElem
  }

  describe("The `clearContent()` method on an `Element`") {

    it("must clear text content but not remove namespaces on that element") {

      val rootElem = newRootElem
      rootElem.addText("Deneb")

      assert("Deneb" == rootElem.getText)
      assert(Set(XS, XF) == rootElem.declaredNamespacesIterator.toSet)

      rootElem.clearContent()

      assert("" == rootElem.getText)
      assert(Set(XS, XF) == rootElem.declaredNamespacesIterator.toSet)
    }

    it("must clear text content also when no namespaces are present (following another code path)") {

      val rootElem = newRootElem
      val nestedElem = rootElem.addElement("nested")

      nestedElem.addText("Deneb")

      assert("Deneb" == nestedElem.getText)
      assert(Set(XS, XF)      == rootElem.declaredNamespacesIterator.toSet)
      assert(Set[Namespace]() == nestedElem.declaredNamespacesIterator.toSet)

      nestedElem.clearContent()

      assert("" == nestedElem.getText)
      assert(Set(XS, XF)      == rootElem.declaredNamespacesIterator.toSet)
      assert(Set[Namespace]() == nestedElem.declaredNamespacesIterator.toSet)
    }
  }

}

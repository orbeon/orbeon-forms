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

import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

import scala.collection.JavaConverters._

class DOMTest extends AssertionsForJUnit {

  @Test def clearContentWithNamespaces(): Unit = {

    val doc = DocumentFactory.createDocument("root")

    val rootElem = doc.getRootElement

    val XS = Namespace("xs", "http://www.w3.org/2001/XMLSchema")
    val XF = Namespace("xf", "http://www.w3.org/2002/xforms")

    rootElem.add(XS)
    rootElem.add(XF)
    rootElem.addText("Deneb")

    assert("Deneb" === rootElem.getText)
    assert(Set(XS, XF) === rootElem.declaredNamespaces.asScala.toSet)

    rootElem.clearContent()

    assert("" === rootElem.getText)
    assert(Set(XS, XF) === rootElem.declaredNamespaces.asScala.toSet)
  }

  @Test def clearContentWithoutNamespaces(): Unit = {

    val doc = DocumentFactory.createDocument("root")

    val rootElem = doc.getRootElement

    val XS = Namespace("xs", "http://www.w3.org/2001/XMLSchema")
    val XF = Namespace("xf", "http://www.w3.org/2002/xforms")

    rootElem.add(XS)
    rootElem.add(XF)

    val nestedElem = rootElem.addElement("nested")

    nestedElem.addText("Deneb")

    assert("Deneb" === nestedElem.getText)
    assert(Set(XS, XF)      === rootElem.declaredNamespaces.asScala.toSet)
    assert(Set[Namespace]() === nestedElem.declaredNamespaces.asScala.toSet)

    nestedElem.clearContent()

    assert("" === nestedElem.getText)
    assert(Set(XS, XF)      === rootElem.declaredNamespaces.asScala.toSet)
    assert(Set[Namespace]() === nestedElem.declaredNamespaces.asScala.toSet)
  }

}

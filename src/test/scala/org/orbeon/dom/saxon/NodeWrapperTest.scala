/**
  * Copyright (C) 2018 Orbeon, Inc.
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
package org.orbeon.dom.saxon

import org.orbeon.dom
import org.orbeon.dom.Namespace
import org.orbeon.oxf.util.XPath
import org.orbeon.saxon.om.SiblingCountingNode
import org.scalatest.funspec.AnyFunSpec

class NodeWrapperTest extends AnyFunSpec {

  val XS = Namespace("xs", "http://www.w3.org/2001/XMLSchema")
  val XF = Namespace("xf", "http://www.w3.org/2002/xforms")

  describe("Sibling position") {

    val doc = dom.Document("root")

    val rootElem = doc.getRootElement

    rootElem.add(XS)
    rootElem.add(XF)

    import org.orbeon.oxf.util.CoreUtils._

    val text1  = dom.Text("before")          |!> rootElem.add
    val child1 = dom.Element("elem1")        |!> rootElem.add
    val text2  = dom.Text("between")         |!> rootElem.add
    val child2 = dom.Element("elem2")        |!> rootElem.add
    val text3  = dom.Text("after")           |!> rootElem.add

    val att1   = dom.Attribute("att1", "v1") |!> child1.add
    val att2   = dom.Attribute("att2", "v2") |!> child1.add

    val att3   = dom.Attribute("att3", "v3") |!> child2.add
    val att4   = dom.Attribute("att4", "v4") |!> child2.add

    val expected = List(
      (doc     , 0),
      (rootElem, 0),

      (text1   , 2),
      (child1  , 3), // 2 namespace nodes before (BAD!)
      (text2   , 4),
      (child2  , 5),
      (text3   , 6),

      (att1    , 0),
      (att2    , 1),

      (att3    , 0),
      (att4    , 1)
    )

    val docWrapper = new DocumentWrapper(doc, null, XPath.GlobalConfiguration)

    for (((node, expected), index) <- expected.zipWithIndex)
      it(s"for index $index") {
        assert(docWrapper.wrap(node).asInstanceOf[SiblingCountingNode].getSiblingPosition === expected)}
  }
}

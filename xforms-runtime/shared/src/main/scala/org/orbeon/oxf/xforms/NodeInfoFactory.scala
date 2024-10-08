/**
  * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.oxf.xforms

import org.orbeon.dom
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.saxon.om


object NodeInfoFactory {

  // This should ideally not be global. Tried 2013-11-14 to use DocumentWrapper.makeWrapper instead, see
  // 2263a3f7b9565fa2102a7cc56ecb007a5c881312 and 0d7bc1fda0a121b2c107adc15a92cba67a09984f, but this is not good
  // enough as NodeWrapper does need a Configuration to operate properly. So for now we keep this wrapper.
  private val Wrapper = new DocumentWrapper(dom.Document(), null, XPath.GlobalConfiguration)

  def elementInfo(elem: dom.Element): om.NodeInfo =
    Wrapper.wrap(elem)

  def elementInfo(
    qName                             : dom.QName,
    content                           : collection.Seq[om.Item] = Nil,
    removeInstanceDataFromClonedNodes : Boolean                 = true
  ): om.NodeInfo = {
    val newElement = Wrapper.wrap(dom.Element(qName))
    if (content.nonEmpty)
      XFormsAPI.insert(
        into                              = List(newElement),
        origin                            = content,
        doDispatch                        = false,
        searchForInstance                 = false,
        removeInstanceDataFromClonedNodes = removeInstanceDataFromClonedNodes
      )
    newElement
  }

  def attributeInfo(name: dom.QName, value: String = ""): om.NodeInfo =
    Wrapper.wrap(dom.Attribute(name, value))

  def namespaceInfo(prefix: String, uri: String): om.NodeInfo =
    Wrapper.wrap(dom.Namespace(prefix, uri))
}

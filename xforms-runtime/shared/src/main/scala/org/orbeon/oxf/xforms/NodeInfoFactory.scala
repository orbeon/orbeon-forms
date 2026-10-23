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
import org.orbeon.oxf.util.{StaticXPath, XPath}
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xml.dom.Extensions.*
import org.orbeon.saxon.om
import org.orbeon.scaxon.NodeInfoConversions
import org.orbeon.scaxon.SimplePath.*


object NodeInfoFactory {

  // This should ideally not be global. Tried 2013-11-14 to use DocumentWrapper.makeWrapper instead, see
  // 2263a3f7b9565fa2102a7cc56ecb007a5c881312 and 0d7bc1fda0a121b2c107adc15a92cba67a09984f, but this is not good
  // enough as NodeWrapper does need a Configuration to operate properly. So for now we keep this wrapper.
  private val Wrapper = new DocumentWrapper(dom.Document(), null, XPath.GlobalConfiguration)

  def elementInfo(elem: dom.Element): om.NodeInfo =
    Wrapper.wrap(elem)

  // Copy an element to be inserted under `destination`, without the namespace declarations that `insert` would
  // otherwise copy from the origin's ancestors, and without those already in scope at the destination. Used to
  // insert templates from XBL components into form definitions and form data without extra namespaces (#7619).
  def copyElementInfoWithoutExtraNamespaces(elem: om.NodeInfo, destination: Option[om.NodeInfo]): om.NodeInfo = {

    def inScopeNamespaces(nodeOpt: Option[om.NodeInfo]): Map[String, String] =
      Map("" -> "") ++                            // the absence of a default namespace counts as xmlns="" being in scope
      nodeOpt.toList.flatMap(_.namespaceMappings)

    // We'll work on a mutable copy of the element to insert; in both cases, the copy includes all the namespaces in
    // scope at the origin
    val copy =
      NodeInfoConversions.unwrapElement(elem) match {
        case Some(domElem) => domElem.copyAndCopyParentNamespaces
        case None          => StaticXPath.tinyTreeToOrbeonDom(elem).getRootElement
      }

    // Namespace prefixes used by element and attribute *names* of the element subtree. We don't inspect attribute
    // values here (XPath expressions, etc.), as the assumption is that templates from XBL components only use prefixes
    // they declare themselves or prefixes that are implicitly present at the destination, such as xf:, xxf:, and fr:.
    val subtree      = copy.descendantElementIterator(includeSelf = true).toList
    val usedPrefixes =
      subtree                      .map(_.getNamespace.prefix).toSet ++
      subtree.flatMap(_.attributes).map(_.getNamespace.prefix).filter(_.nonEmpty) // attributes: no prefix = no namespace

    // Remove all namespace declarations inherited from the source's ancestors, *except* those actually used in names
    copy.removeRedundantNamespaceDeclarations(inScopeNamespaces(elem.parentOption) -- usedPrefixes)

    // Remove all namespace declarations the destination already has
    copy.removeRedundantNamespaceDeclarations(inScopeNamespaces(destination))

    elementInfo(copy)
  }

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

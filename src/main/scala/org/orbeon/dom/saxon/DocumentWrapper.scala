package org.orbeon.dom.saxon

import java.util
import java.util.Collections

import org.orbeon.dom.{Document, Element, Node}
import org.orbeon.saxon.Configuration
import org.orbeon.saxon.om.{DocumentInfo, NamePool, NodeInfo}

/**
 * The root node of an XPath tree. (Or equivalently, the tree itself).
 * This class should have been named Root; it is used not only for the root of a document,
 * but also for the root of a result tree fragment, which is not constrained to contain a
 * single top-level element.
 *
 * @author Michael H. Kay
 */
object DocumentWrapper {

  /**
   * Wrap a node without a document. The node must not have a document.
   */
  def makeWrapper(node: Node): NodeWrapper = {
    require(node.getDocument eq null)
    NodeWrapper.makeWrapperImpl(node, null, null)
  }
}

class DocumentWrapper(
  val doc     : Document,
  var baseURI : String,
  var config  : Configuration
) extends NodeWrapper(doc, null)
    with DocumentInfo {

  this.docWrapper = this

  private val documentNumber = config.getDocumentNumberAllocator.allocateDocumentNumber
  private var idGetter: String => Element = null

  def wrap(node: Node): NodeInfo =
    if (node eq this.node)
      this
    else
      makeWrapper(node, this)

  override def getDocumentNumber: Int = documentNumber

  def setIdGetter(idGetter: String => Element): Unit =
    this.idGetter = idGetter

  def selectID(id: String): NodeInfo =
    if (idGetter eq null)
      null
    else {
      val element = idGetter.apply(id)
      if (element != null)
        wrap(element)
      else
        null
    }

  def getUnparsedEntityNames: util.Iterator[_] = Collections.EMPTY_LIST.iterator
  def getUnparsedEntity(name: String): Array[String] = null

  override def getConfiguration: Configuration = config
  override def getNamePool: NamePool = config.getNamePool
}

// The contents of this file are subject to the Mozilla Public License Version 1.0 (the "License");
// you may not use this file except in compliance with the License. You may obtain a copy of the
// License at http://www.mozilla.org/MPL/
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations under the License.
// The Original Code is: all this file.
// The Initial Developer of the Original Code is
// Michael Kay (michael.h.kay@ntlworld.com).
// Portions created by (your name) are Copyright (C) (your legal entity). All Rights Reserved.
// Contributor(s): none.
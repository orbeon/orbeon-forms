package org.orbeon.dom.saxon

import org.orbeon.dom
import org.orbeon.dom.Node
import org.orbeon.saxon.om
import org.orbeon.saxon.utils.Configuration


// TODO: Don't derive from `om.GenericTreeInfo` as it's causing more trouble than anything.
class DocumentWrapper(
  val doc     : dom.Document,
  var baseURI : String,
  var config  : Configuration
) extends om.GenericTreeInfo(config)
     with NodeWrapper {

  // Define abstract members
  val node       : dom.Node        = doc
  val docWrapper : DocumentWrapper = this
  var parent     : NodeWrapper     = null

  this.setRootNode(docWrapper)
  treeInfo = docWrapper

  private var docSystemId: String = null

  override def getConfiguration: Configuration = config

  // These overrides are necessary as these are in two of the super traits and the compiler complains otherwise
  override def getSystemId              : String        = docSystemId
  override def setSystemId(uri: String) : Unit          = docSystemId = uri
  override def getPublicId              : String        = null
  override def isStreamed               : Boolean       = false

  // NOTE: Also used externally
  def wrap(node: Node): om.NodeInfo =
    if (node eq this.node)
      this
    else
      ConcreteNodeWrapper.makeWrapper(node, this, null)

  private var idGetter: String => dom.Element = null

  def setIdGetter(idGetter: String => dom.Element): Unit =
    this.idGetter = idGetter

  // 2020-11-09: NOTE: Saxon's DOM wrapper supports `xml:id` directly.
  // See our `XFormsInstanceIndex` for comments.
  override def selectID(id: String, getParent: Boolean): om.NodeInfo =
    if (idGetter eq null)
      null
    else {
      val element = idGetter(id)
      if (element != null)
        wrap(element)
      else
        null
    }
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
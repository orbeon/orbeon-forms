/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.dom.Element
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.analysis.model.Instance
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo, VirtualNode}

/**
 * xxf:extract-document() takes an element as parameter and extracts a document.
 */
class XXFormsExtractDocument extends XFormsFunction {

  override def evaluateItem(xpathContext: XPathContext): DocumentInfo = {

    implicit val ctx = xpathContext

    val item                  = argument(0).evaluateItem(xpathContext)
    val excludeResultPrefixes = stringArgumentOpt(1)
    val readonly              = booleanArgument(2, default = false)

    val rootElement =
      item match {
        case virtualNode: VirtualNode => virtualNode.getUnderlyingNode.asInstanceOf[Element]
        case nodeInfo: NodeInfo       => TransformerUtils.tinyTreeToDom4j(nodeInfo).getRootElement
        case _                        => return null
      }

    Instance.extractDocument(
      rootElement,
      stringOptionToSet(excludeResultPrefixes),
      readonly,
      exposeXPathTypes   = false,
      removeInstanceData = true
    )
  }
}
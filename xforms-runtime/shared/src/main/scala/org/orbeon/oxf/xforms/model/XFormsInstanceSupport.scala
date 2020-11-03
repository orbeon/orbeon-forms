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
package org.orbeon.oxf.xforms.model

import org.orbeon.dom.saxon.{DocumentWrapper, TypedDocumentWrapper}
import org.orbeon.dom.{Document, Element}
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xforms.analysis.model.Instance.{extractDocHandlePrefixes, extractReadonlyDocument}
import org.orbeon.saxon.om


object XFormsInstanceSupport {

  // Extract the document starting at the given root element
  // This always creates a copy of the original sub-tree
  //
  // @readonly         if true, the document returned is a compact TinyTree, otherwise a DocumentWrapper
  // @exposeXPathTypes if true, use a TypedDocumentWrapper
  def extractDocument(
    element               : Element,
    excludeResultPrefixes : Set[String],
    readonly              : Boolean,
    exposeXPathTypes      : Boolean,
    removeInstanceData    : Boolean
  ): DocumentNodeInfoType = {

    require(! (readonly && exposeXPathTypes)) // we can't expose types on readonly instances at the moment

    if (readonly)
      extractReadonlyDocument(element, excludeResultPrefixes)
    else
      wrapDocument(
        if (removeInstanceData)
          InstanceDataOps.removeRecursively(extractDocHandlePrefixes(element, excludeResultPrefixes))
        else
          extractDocHandlePrefixes(element, excludeResultPrefixes),
        exposeXPathTypes
      )
  }

  def wrapDocument(document: Document, exposeXPathTypes: Boolean): DocumentWrapper =
    if (exposeXPathTypes)
      new TypedDocumentWrapper(
        document.normalizeTextNodes,
        null,
        XPath.GlobalConfiguration
      )
    else
      new DocumentWrapper(
        document.normalizeTextNodes,
        null,
        XPath.GlobalConfiguration
      )
}
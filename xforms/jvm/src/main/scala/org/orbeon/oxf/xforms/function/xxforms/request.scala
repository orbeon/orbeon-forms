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
package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xml.RuntimeDependentFunction
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.{EmptyIterator, SequenceIterator}
import org.orbeon.saxon.value.StringValue
import org.orbeon.scaxon.Implicits._

class GetRequestParameterTryXFormsDocument extends RequestFunction {

  def fromDocument(containingDocument: XFormsContainingDocument, name: String) =
    containingDocument.getRequestParameters.get(name)

//  def fromRequest(request: Request, name: String) =
//    Option(request.getParameterMap.get(name)) map StringConversions.objectArrayToStringArray map (_.toList)
}

class GetRequestHeaderTryXFormsDocument extends RequestFunction {

  def fromDocument(containingDocument: XFormsContainingDocument, name: String) =
    containingDocument.getRequestHeaders.get(name.toLowerCase)

//  def fromRequest(request: Request, name: String) =
//    Option(NetUtils.getExternalContext.getRequest.getHeaderValuesMap.get(name.toLowerCase)) map (_.toList)
}

trait RequestFunction extends XFormsFunction with RuntimeDependentFunction {

  def fromDocument(containingDocument: XFormsContainingDocument, name: String): Option[List[String]]
//  def fromRequest(request: Request, name: String): Option[List[String]]

  override def iterate(xpathContext: XPathContext): SequenceIterator = {

    implicit val ctx = xpathContext

    fromDocument(getContainingDocument, stringArgument(0)) map
      stringSeqToSequenceIterator                          getOrElse
      EmptyIterator.getInstance
  }
}

class GetRequestPathTryXFormsDocument extends XFormsFunction with RuntimeDependentFunction {
  override def evaluateItem(xpathContext: XPathContext): StringValue =
    getContainingDocument(xpathContext).getRequestPath
}
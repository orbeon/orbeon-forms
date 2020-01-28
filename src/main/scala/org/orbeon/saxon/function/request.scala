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
package org.orbeon.saxon.function


import org.orbeon.io.CharsetNames
import org.orbeon.oxf.externalcontext.ExternalContext.Request
import org.orbeon.oxf.util.{NetUtils, StringConversions}
import org.orbeon.oxf.xml.{DefaultFunctionSupport, FunctionSupport, RuntimeDependentFunction}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.{EmptyIterator, SequenceIterator}
import org.orbeon.saxon.value.{BooleanValue, StringValue}
import org.orbeon.scaxon.Implicits._
import scala.collection.compat._

class GetRequestMethod extends DefaultFunctionSupport with RuntimeDependentFunction {
  override def evaluateItem(xpathContext: XPathContext): StringValue =
    NetUtils.getExternalContext.getRequest.getMethod.entryName.toUpperCase
}

class GetPortletMode extends DefaultFunctionSupport with RuntimeDependentFunction {
  override def evaluateItem(xpathContext: XPathContext): StringValue =
    NetUtils.getExternalContext.getRequest.getPortletMode
}

class GetWindowState extends DefaultFunctionSupport with RuntimeDependentFunction {
  override def evaluateItem(xpathContext: XPathContext): StringValue =
    NetUtils.getExternalContext.getRequest.getWindowState
}

class GetRequestParameter extends RequestFunction {
  def fromRequest(request: Request, name: String)(implicit ctx: XPathContext): Option[List[String]] =
    Option(request.getParameterMap.get(name)) map StringConversions.objectArrayToStringArray map (_.toList)
}

class GetRequestHeader extends RequestFunction {
  def fromRequest(request: Request, name: String)(implicit ctx: XPathContext): Option[List[String]] =
    GetRequestHeader.getAndDecodeHeader(
      name     = name,
      encoding = stringArgumentOpt(1),
      getter   = s => Option(NetUtils.getExternalContext.getRequest.getHeaderValuesMap.get(s)) map (_.toList)
    )
}

object GetRequestHeader {

  def getAndDecodeHeader(
    name     : String,
    encoding : Option[String],
    getter   : String => Option[List[String]]
  ): Option[List[String]] = {

    import CharsetNames._

    val decode: String => String =
      encoding map (_.toUpperCase) match {
        case None | Some(Iso88591) => identity
        case Some(Utf8)            => (s: String) => new String(s.getBytes(Iso88591), Utf8)
        case Some(other)           => throw new IllegalArgumentException(s"invalid `$$encoding` argument `$other`")
      }

    getter(name.toLowerCase) map (_ map decode)
  }
}

trait RequestFunction extends DefaultFunctionSupport with RuntimeDependentFunction {

  def fromRequest(request: Request, name: String)(implicit ctx: XPathContext): Option[List[String]]

  override def iterate(xpathContext: XPathContext): SequenceIterator = {

    val name =
      stringArgument(0)(xpathContext)

    val values =
      fromRequest(NetUtils.getExternalContext.getRequest, name)(xpathContext)

    values map stringSeqToSequenceIterator getOrElse EmptyIterator.getInstance
  }
}

class Username extends DefaultFunctionSupport with RuntimeDependentFunction {
  override def evaluateItem(xpathContext: XPathContext): StringValue =
    NetUtils.getExternalContext.getRequest.credentials map (_.username)
}

class UserGroup extends DefaultFunctionSupport with RuntimeDependentFunction {
  override def evaluateItem(xpathContext: XPathContext): StringValue =
    NetUtils.getExternalContext.getRequest.credentials flatMap (_.group)
}

class UserRoles extends DefaultFunctionSupport with RuntimeDependentFunction {
  override def iterate(xpathContext: XPathContext): SequenceIterator =
    stringSeqToSequenceIterator(NetUtils.getExternalContext.getRequest.credentials.to(List) flatMap (_.roles map (_.roleName)))
}

class UserOrganizations extends DefaultFunctionSupport with RuntimeDependentFunction {
  override def iterate(xpathContext: XPathContext): SequenceIterator =
    stringSeqToSequenceIterator(
      for {
        credentials <- NetUtils.getExternalContext.getRequest.credentials.toList
        org         <- credentials.organizations
        leafOrg     <- org.levels.lastOption.toList
      } yield
        leafOrg
    )
}

class AncestorOrganizations extends DefaultFunctionSupport with RuntimeDependentFunction {
  override def iterate(xpathContext: XPathContext): SequenceIterator = {

    val leafOrgParam =
      stringArgument(0)(xpathContext)

    // There should be only one match if the organizations are well-formed
    val foundOrgs =
      for {
          credentials <- NetUtils.getExternalContext.getRequest.credentials.toList
          org         <- credentials.organizations
          if org.levels.lastOption contains leafOrgParam
        } yield
          org

    stringSeqToSequenceIterator(
      foundOrgs.headOption match {
        case Some(foundOrg) => foundOrg.levels.init.reverse
        case None           => Nil

      }
    )
  }
}

class IsUserInRole extends DefaultFunctionSupport with RuntimeDependentFunction {
  override def evaluateItem(xpathContext: XPathContext): BooleanValue =
    NetUtils.getExternalContext.getRequest.isUserInRole(stringArgument(0)(xpathContext))
}

class GetRequestPath extends FunctionSupport with RuntimeDependentFunction {
  override def evaluateItem(xpathContext: XPathContext): StringValue =
    NetUtils.getExternalContext.getRequest.getRequestPath
}

class GetSessionAttribute extends DefaultFunctionSupport with RuntimeDependentFunction {

  override def iterate(xpathContext: XPathContext): SequenceIterator = {

    implicit val ctx = xpathContext

    NetUtils.getExternalContext.getRequest.sessionOpt match {
      case Some(session) =>

        val attributeName = stringArgument(0)

        ScopeFunctionSupport.convertAttributeValue(
          session.getAttribute(attributeName),
          stringArgumentOpt(1),
          attributeName
        )
      case None =>
        EmptyIterator.getInstance
    }
  }
}

class SetSessionAttribute extends DefaultFunctionSupport with RuntimeDependentFunction {

  override def iterate(xpathContext: XPathContext): SequenceIterator = {

    implicit val ctx = xpathContext

    val attributeName = stringArgument(0)
    val item          = argument(1).evaluateItem(xpathContext)

    val session = NetUtils.getExternalContext.getRequest.getSession(true) // DO force session creation

    ScopeFunctionSupport.storeAttribute(session.setAttribute(_, _), attributeName, item)

    EmptyIterator.getInstance
  }
}

class SetRequestAttribute extends DefaultFunctionSupport with RuntimeDependentFunction {

  override def iterate(xpathContext: XPathContext): SequenceIterator = {

    implicit val ctx = xpathContext

    val attributeName = stringArgument(0)
    val item          = itemArgument(1)

    val request = NetUtils.getExternalContext.getRequest

    // See https://github.com/orbeon/orbeon-forms/issues/4116
    if (item eq null)
      request.getAttributesMap.remove(attributeName)
    else
      request.getAttributesMap.put(attributeName, item)

    EmptyIterator.getInstance
  }
}

class GetRequestAttribute extends DefaultFunctionSupport with RuntimeDependentFunction {

  override def iterate(xpathContext: XPathContext): SequenceIterator = {

    implicit val ctx = xpathContext

    val attributeName = stringArgument(0)

    ScopeFunctionSupport.convertAttributeValue(
      Option(NetUtils.getExternalContext.getRequest.getAttributesMap.get(attributeName)),
      stringArgumentOpt(1),
      attributeName
    )
  }
}


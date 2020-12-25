package org.orbeon.oxf.xforms.library

import org.orbeon.macros.XPathFunction
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.FunctionSupport._
import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath._


trait IndependentFunctions extends OrbeonFunctionLibrary {

//  Fun("digest", classOf[Digest], op = 0, min = 2, STRING, EXACTLY_ONE,
//      Arg(STRING, EXACTLY_ONE),
//      Arg(STRING, EXACTLY_ONE),
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("hmac", classOf[Hmac], op = 0, min = 3, STRING, EXACTLY_ONE,
//      Arg(STRING, EXACTLY_ONE),
//      Arg(STRING, EXACTLY_ONE),
//      Arg(STRING, EXACTLY_ONE),
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("random", classOf[Random], op = 0, min = 0, NUMERIC, EXACTLY_ONE,
//      Arg(BOOLEAN, ALLOWS_ZERO_OR_ONE)
//    )
//
//    // TODO: Split this out into separate trait
//    Fun("get-portlet-mode", classOf[GetPortletMode], op = 0, min = 0, STRING, ALLOWS_ONE)
//
//    // TODO: Split this out into separate trait
//    Fun("get-window-state", classOf[GetWindowState], op = 0, min = 0, STRING, ALLOWS_ONE)
//
//    // TODO: Split this out into separate trait
//    Fun("get-session-attribute", classOf[GetSessionAttribute], op = 0, min = 1, ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE),
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    // TODO: Split this out into separate trait
//    Fun("set-session-attribute", classOf[SetSessionAttribute], op = 0, min = 2, ITEM_TYPE, ALLOWS_ZERO,
//      Arg(STRING, EXACTLY_ONE),
//      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
//    )
//
//    // TODO: Split this out into separate trait

  // TODO: For now, always return `()`
  @XPathFunction
  def getRequestAttribute(attributeName: String, contentTypeOpt: Option[String] = None): Iterable[om.Item] = Nil

//
//    // TODO: Split this out into separate trait
//    Fun("set-request-attribute", classOf[SetRequestAttribute], op = 0, min = 2, ITEM_TYPE, ALLOWS_ZERO,
//      Arg(STRING, EXACTLY_ONE),
//      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
//    )
//
  // TODO: Split this out into separate trait

  @XPathFunction
  def username: Option[String] =
    CoreCrossPlatformSupport.externalContext.getRequest.credentials map (_.username)

  @XPathFunction
  def getRemoteUser: Option[String] = // `= username`
    CoreCrossPlatformSupport.externalContext.getRequest.credentials map (_.username)

  @XPathFunction
  def userGroup: Option[String] =
    CoreCrossPlatformSupport.externalContext.getRequest.credentials flatMap (_.group)

  @XPathFunction
  def userRoles: Iterable[String] =
    CoreCrossPlatformSupport.externalContext.getRequest.credentials.toList flatMap (_.roles map (_.roleName))

  @XPathFunction
  def userOrganizations: Iterable[String] =
    for {
      credentials <- CoreCrossPlatformSupport.externalContext.getRequest.credentials.toList
      org         <- credentials.organizations
      leafOrg     <- org.levels.lastOption.toList
    } yield
      leafOrg

//    // TODO: Split this out into separate trait
//    Fun("user-ancestor-organizations", classOf[AncestorOrganizations], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    // TODO: Split this out into separate trait
//    Fun("is-user-in-role", classOf[IsUserInRole], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("call-xpl", classOf[CallXPL], op = 0, min = 4, NODE_TYPE, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE),
//      Arg(STRING, ALLOWS_ZERO_OR_MORE),
//      Arg(NODE_TYPE, ALLOWS_ZERO_OR_MORE),
//      Arg(STRING, ALLOWS_ZERO_OR_MORE)
//    )
//
//    Fun("evaluate", classOf[saxon.functions.Evaluate], op = saxon.functions.Evaluate.EVALUATE, min = 1, max = 10, ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("serialize", classOf[saxon.functions.Serialize], op = 0, min = 2, STRING, EXACTLY_ONE,
//      Arg(NODE_TYPE, ALLOWS_ZERO_OR_ONE),
//      Arg(ITEM_TYPE, EXACTLY_ONE)
//    )
//
//    Fun("property", classOf[Property], op = 0, min = 1, ANY_ATOMIC, ALLOWS_ZERO_OR_ONE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("properties-start-with", classOf[PropertiesStartsWith], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("decode-iso9075-14", classOf[DecodeISO9075], op = 0, min = 1, STRING, ALLOWS_ONE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("encode-iso9075-14", classOf[EncodeISO9075], op = 0, min = 1, STRING, ALLOWS_ONE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("doc-base64", classOf[DocBase64], op = DocBase64.DOC_BASE64, min = 1, STRING, ALLOWS_ZERO_OR_ONE,
//      Arg(STRING, ALLOWS_ZERO_OR_ONE)
//    )
//
//    Fun("doc-base64-available", classOf[DocBase64], op = DocBase64.DOC_BASE64_AVAILABLE, min = 1, BOOLEAN, EXACTLY_ONE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("rewrite-resource-uri", classOf[RewriteResourceURI], op = 0, min = 1, STRING, EXACTLY_ONE,
//      Arg(STRING, EXACTLY_ONE),
//      Arg(BOOLEAN, EXACTLY_ONE)
//    )
//
//    Fun("rewrite-service-uri", classOf[RewriteServiceURI], op = 0, min = 1, STRING, EXACTLY_ONE,
//      Arg(STRING, EXACTLY_ONE),
//      Arg(BOOLEAN, EXACTLY_ONE)
//    )

  @XPathFunction
  def hasClass(className: String, elem: Option[om.NodeInfo] = None)(implicit xpc: XPathContext): Boolean =
    classesFromAttribute(elem).contains(className)

  @XPathFunction
  def classes(elem: Option[om.NodeInfo] = None)(implicit xpc: XPathContext): Iterable[String] =
    classesFromAttribute(elem)

  @XPathFunction
  def split(s: Option[String] = None, separator: Option[String] = None)(implicit xpc: XPathContext): Iterable[String] =
    stringArgumentOrContextOpt(s).toList flatMap (_.splitTo[List](separator.orNull))

  @XPathFunction
  def trim(s: Option[String] = None)(implicit xpc: XPathContext): Option[String] =
    stringArgumentOrContextOpt(s) map (_.trimAllToEmpty)

  @XPathFunction
  def isBlank(s: Option[String] = None)(implicit xpc: XPathContext): Boolean =
    ! (stringArgumentOrContextOpt(s) exists (_.trimAllToEmpty.nonEmpty))

  @XPathFunction
  def nonBlank(s: Option[String] = None)(implicit xpc: XPathContext): Boolean =
    stringArgumentOrContextOpt(s) exists (_.trimAllToEmpty.nonEmpty)

  private def classesFromAttribute(i: Option[om.Item])(implicit xpc: XPathContext): Set[String] =
    itemArgumentOrContextOpt(i) match {
      case Some(node: om.NodeInfo) if node.isElement =>
        node.getAttributeValue("", "class").tokenizeToSet
      case _ =>
        Set.empty
    }
}

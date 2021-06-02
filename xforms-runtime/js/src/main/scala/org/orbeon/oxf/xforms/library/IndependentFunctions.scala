package org.orbeon.oxf.xforms.library

import org.orbeon.macros.XPathFunction
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.ContentTypes.XmlContentType
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, StaticXPath}
import org.orbeon.oxf.xml.FunctionSupport._
import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.oxf.xml.SaxonUtils.StringValueWithEquals
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om
import org.orbeon.saxon.value.{AtomicValue, StringValue}
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
//    // TODO: Split this out into separate trait
//    Fun("get-portlet-mode", classOf[GetPortletMode], op = 0, min = 0, STRING, ALLOWS_ONE)
//
//    // TODO: Split this out into separate trait
//    Fun("get-window-state", classOf[GetWindowState], op = 0, min = 0, STRING, ALLOWS_ONE)
//

  @XPathFunction
  def getSessionAttribute(attributeName: String, contentType: String = XmlContentType): Iterable[om.Item] = {

    // TODO: Handle XML tree
    println(s"xxx getSessionAttribute `$attributeName`")

    CoreCrossPlatformSupport.externalContext.getRequest.sessionOpt.toList flatMap { session =>
      session.getAttribute(attributeName).toList collect {
        case v: om.Item => List(v)
      }
    } flatten
  }

  @XPathFunction
  def setSessionAttribute(attributeName: String, items: Iterable[om.Item]): Unit = {

    // NOTE: We take only the first item, even though the parameter is declared as supporting
    // multiple items. This is incorrect but it's what we've done in the past. We could fix this.
    val value =
      items.headOption collect {
        case v: StringValue => new StringValueWithEquals(v.getStringValueCS)
        case v: AtomicValue => v
//        case v: NodeInfo    => TransformerUtils.tinyTreeToSAXStore(v) // TODO: copy XML tree
        case _ => throw new OXFException(s"xxf:set-*-attribute() does not support storing objects of type: ${items.getClass.getName}")
      }

    CoreCrossPlatformSupport.externalContext.getSession(true).setAttribute(attributeName, value)
  }

  @XPathFunction
  def getRequestAttribute(attributeName: String, contentType: String = XmlContentType): Iterable[om.Item] = {
    // TODO: Handle XML tree
    Option(CoreCrossPlatformSupport.externalContext.getRequest.getAttributesMap.get(attributeName)).toList collect {
        case v: om.Item => List(v)
    } flatten
  }

  @XPathFunction
  def setRequestAttribute(attributeName: String, items: Iterable[om.Item]): Unit = {

    println(s"xxx setRequestAttribute `$attributeName` with type `${items map (_.getClass.getName) mkString ", "}`")

    val request = CoreCrossPlatformSupport.externalContext.getRequest

    // NOTE: We take only the first item, even though the parameter is declared as supporting
    // multiple items. This is incorrect but it's what we've done in the past. We could fix this.
    // See https://github.com/orbeon/orbeon-forms/issues/4116
    items.toList match {
      case Nil       => request.getAttributesMap.remove(attributeName)
      case head :: _ => request.getAttributesMap.put(attributeName, head)
    }
  }

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

  // TODO: Handle `params`.
  @XPathFunction
  def serialize(node: Option[om.NodeInfo], params: om.Item): String =
    node map StaticXPath.tinyTreeToString getOrElse ""

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
  def split(s: Option[String] = null, separator: Option[String] = None)(implicit xpc: XPathContext): Iterable[String] =
    stringArgumentOrContextOpt(s).toList flatMap (_.splitTo[List](separator.orNull))

  @XPathFunction
  def trim(s: Option[String] = null)(implicit xpc: XPathContext): Option[String] =
    stringArgumentOrContextOpt(s) map (_.trimAllToEmpty)

  @XPathFunction
  def isBlank(s: Option[String] = null)(implicit xpc: XPathContext): Boolean =
    ! (stringArgumentOrContextOpt(s) exists (_.trimAllToEmpty.nonEmpty))

  @XPathFunction
  def nonBlank(s: Option[String] = null)(implicit xpc: XPathContext): Boolean =
    stringArgumentOrContextOpt(s) exists (_.trimAllToEmpty.nonEmpty)

  @XPathFunction
  def escapeXmlMinimal(s: Option[String] = null)(implicit xpc: XPathContext): Option[String] =
    stringArgumentOrContextOpt(s) map (_.escapeXmlMinimal)

  private def classesFromAttribute(i: Option[om.Item])(implicit xpc: XPathContext): Set[String] =
    itemArgumentOrContextOpt(i) match {
      case Some(node: om.NodeInfo) if node.isElement =>
        node.getAttributeValue("", "class").tokenizeToSet
      case _ =>
        Set.empty
    }
}

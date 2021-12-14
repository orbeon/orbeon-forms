/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.properties

import cats.syntax.option._
import org.orbeon.dom.QName
import org.orbeon.oxf.common.{OXFException, ValidationException}
import org.orbeon.oxf.properties.PropertySet._
import org.orbeon.oxf.util.CollectionUtils
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.oxf.xml.dom.Extensions
import org.orbeon.xml.NamespaceMapping

import java.net.{URI, URISyntaxException}
import java.{lang => jl, util => ju}
import scala.collection.mutable
import scala.jdk.CollectionConverters._


case class Property(typ: QName, value: AnyRef, namespaces: Map[String, String]) {

  private var _associatedValue: Option[Any] = None

  def associatedValue[U](evaluate: Property => U): U = {
    if (_associatedValue.isEmpty)
      _associatedValue = Option(evaluate(this))
    _associatedValue.get.asInstanceOf[U]
  }

  def namespaceMapping: NamespaceMapping = NamespaceMapping(namespaces)
}

/**
 * Represent a set of properties.
 *
 * A property name can be exact, e.g. foo.bar.gaga, or it can contain wildcards, like ".*.bar.gaga", "foo.*.gaga", or
 * "foo.bar.*", or "*.bar.*", etc.
 */
object PropertySet {

  import Private._

  val PasswordPlaceholder = "xxxxxxxx"

  private class PropertyNode {
    var property: Option[Property] = None
    var children: mutable.Map[String, PropertyNode] = mutable.LinkedHashMap[String, PropertyNode]()
  }

  def empty: PropertySet = new PropertySet(Map.empty, new PropertyNode)

  case class PropertyParams(namespaces: Map[String, String], name: String, typeQName: QName, stringValue: String)

  def apply(globalProperties: Iterable[PropertyParams]): PropertySet = {

    var exactProperties    = Map[String, Property]()
    val wildcardProperties = new PropertyNode

    def setProperty(namespaces: Map[String, String], name: String, typeQName: QName, value: AnyRef): Unit = {

      val property = Property(typeQName, value, if (namespaces eq null) Map.empty else namespaces) // shouldn't be null!

      // Store exact property name anyway
      exactProperties += name -> property

      // Also store in tree (in all cases, not only when contains wildcard, so we get find all the properties
      // that start with some token)
      var currentNode = wildcardProperties
      for (currentToken <- name.splitTo[List](".")) {
        currentNode = currentNode.children.getOrElse(currentToken, {
          val newPropertyNode = new PropertyNode
          currentNode.children += currentToken -> newPropertyNode
          newPropertyNode
        })
      }

      // Store value
      currentNode.property = property.some
    }

    globalProperties foreach { case PropertyParams(namespaces, name, typ, stringValue) =>
      setProperty(namespaces, name, typ, getObjectFromStringValue(stringValue, typ, namespaces))
    }

    new PropertySet(exactProperties, wildcardProperties)
  }

  def isSupportedType(typeQName: QName): Boolean =
    SupportedTypes.contains(typeQName)

  private object Private {

    val SupportedTypes =
      Map[QName, (String, Map[String, String]) => AnyRef](
        XS_STRING_QNAME             -> convertString,
        XS_INTEGER_QNAME            -> convertInteger,
        XS_BOOLEAN_QNAME            -> convertBoolean,
//        XS_DATE_QNAME               -> convertDate,
//        XS_DATETIME_QNAME           -> convertDate,
        XS_QNAME_QNAME              -> convertQName,
        XS_ANYURI_QNAME             -> convertURI,
        XS_NCNAME_QNAME             -> convertNCName,
        XS_NMTOKEN_QNAME            -> convertNMTOKEN,
        XS_NMTOKENS_QNAME           -> convertNMTOKENS,
//        XS_NONNEGATIVEINTEGER_QNAME -> convertNonNegativeInteger
      )

    def getObjectFromStringValue(stringValue: String, typ: QName, namespaces: Map[String, String]): AnyRef =
      SupportedTypes.get(typ) map (_(stringValue, namespaces)) orNull

    def convertString (value: String, namespaces: Map[String, String]) = value
    def convertInteger(value: String, namespaces: Map[String, String]) = jl.Integer.valueOf(value)
    def convertBoolean(value: String, namespaces: Map[String, String]) = jl.Boolean.valueOf(value)
//    def convertDate   (value: String, namespaces: Map[String, String]) = new ju.Date(DateUtilsUsingSaxon.parseISODateOrDateTime(value))

    def convertQName(value: String, namespaces: Map[String, String]): QName =
      Extensions.resolveQName(namespaces.get, value, unprefixedIsNoNamespace = true) getOrElse
        (throw new ValidationException("QName value not found ", null))

    def convertURI(value: String, namespaces: Map[String, String]): URI =
      try {
        new URI(value)
      } catch {
        case e: URISyntaxException =>
          throw new ValidationException(e, null)
      }

    def convertNCName(value: String, namespaces: Map[String, String]): String = {
      if (! SaxonUtils.isValidNCName(value))
        throw new ValidationException("Not an NCName: " + value, null)
      value
    }

    def convertNMTOKEN(value: String, namespaces: Map[String, String]): String = {
      if (! SaxonUtils.isValidNmtoken(value))
        throw new ValidationException("Not an NMTOKEN: " + value, null)
      value
    }

    def convertNMTOKENS(value: String, namespaces: Map[String, String]): ju.Set[String] = {
      val tokens = value.splitTo[Set]()
      for (token <- tokens)
        if (! SaxonUtils.isValidNmtoken(token))
          throw new ValidationException(s"Not an NMTOKENS: $value" , null)
      tokens.asJava
    }

//    def convertNonNegativeInteger(value: String, namespaces: Map[String, String]): jl.Integer = {
//      val ret = convertInteger(value, element)
//      if (ret < 0)
//        throw new ValidationException(s"Not a non-negative integer: $value", null)
//      ret
//    }
  }
}

class PropertySet private (
  exactProperties    : Map[String, Property],
  wildcardProperties : PropertyNode // this contains mutable nodes, but they don't mutate after construction
) {

  def keySet: Set[String] = exactProperties.keySet
  def size: Int = exactProperties.size

  def propertyParams: Iterable[PropertyParams] =
    exactProperties map { case (name, prop) =>

      // Custom serialization to String, not ideal
      val stringValue = {
        prop.value match {
          case v: ju.Set[_] => v.asScala map (_.toString) mkString " "
          case v            => v.toString
        }
      }

      PropertyParams(prop.namespaces, name, prop.typ, stringValue)
    }

  def allPropertiesAsJson: String = {

    val jsonProperties =
      for {
        (name, prop) <- exactProperties.toList.sortBy(_._1)
        propType     = prop.typ.toString
        propValue    = if (name.toLowerCase.contains("password")) PasswordPlaceholder else prop.value.toString
      } yield
          s"""|  "$name": {
              |    "type": "${propType.escapeJavaScript}",
              |    "value": "${propValue.escapeJavaScript}"
              |  }""".stripMargin

    jsonProperties mkString ("{\n", ",\n", "\n}")
  }

  // Return an unmodifiable `Map[String, Boolean]` of all exact Boolean properties
  def getBooleanProperties: ju.Map[String, jl.Boolean] = {
    val tuples =
      for {
        key          <- exactProperties.keys
        value        <- getObjectOpt(key)
        booleanValue <- CollectionUtils.collectByErasedType[java.lang.Boolean](value)
      } yield
        key -> booleanValue

    tuples.toMap.asJava
  }

  // Return all the properties starting with the given name
  def propertiesStartsWith(name: String, matchWildcards: Boolean = true): List[String] = {

    val result = mutable.Buffer[String]()

    def processNode(propertyNode: PropertyNode, consumed: String, tokens: List[String], currentTokenPosition: Int): Unit = {

      def appendToConsumed(s: String) = if (consumed.isEmpty) s else consumed + "." + s

      tokens.lift(currentTokenPosition) match {
        case x @ (Some("*") | None) =>

          if (propertyNode.children.isEmpty && x.isEmpty)
            result += consumed
          else if (propertyNode.children.nonEmpty) {
            for ((key, value) <- propertyNode.children) {
              val newConsumed = appendToConsumed(key)
              processNode(value, newConsumed, tokens, currentTokenPosition + 1)
            }
          }

        case Some(token) =>
          // Regular token

          // Find 1. property node with exact name 2. property node with *
          val newPropertyNodes = propertyNode.children.get(token) :: (matchWildcards list propertyNode.children.get("*"))
          for {
            (newPropertyNodeOpt, index) <- newPropertyNodes.zipWithIndex
            newPropertyNode             <- newPropertyNodeOpt
            actualToken                 = if (index == 0) token else "*"
            newConsumed                 = appendToConsumed(actualToken)
          } locally {
            processNode(newPropertyNode, newConsumed, tokens, currentTokenPosition + 1)
          }
      }
    }

    processNode(wildcardProperties, "", name.splitTo[List]("."), 0)

    result.toList
  }

  private def getPropertyOptThrowIfTypeMismatch(name: String, typ: QName): Option[Property] = {

    def wildcardSearch(
      propertyNodeOpt : Option[PropertyNode],
      tokens          : List[String]
    ): Option[Property] =
      propertyNodeOpt match {
        case None               => None
        case Some(propertyNode) =>
          tokens match {
            case Nil =>
              propertyNode.property
            case head :: tail =>
              propertyNode.children match {
                case c if c.isEmpty => None
                case c              => wildcardSearch(c.get(head), tail) orElse wildcardSearch(c.get("*"), tail)
              }
          }
      }

    def getExact = exactProperties.get(name)

    def getWildcard = wildcardSearch(Some(wildcardProperties), name.splitTo[List]("."))

    def checkType(p: Property) =
      if ((typ ne null) && typ != p.typ)
        throw new OXFException(
          s"Invalid attribute type requested for property `$name`: expected `${typ.qualifiedName}`, found `${p.typ.qualifiedName}`"
        )
      else
        p

    getExact orElse getWildcard map checkType
  }

  /* All getters */

  private def getPropertyValueOpt(name: String, typ: QName): Option[AnyRef] =
    getPropertyOptThrowIfTypeMismatch(name, typ) map (_.value)

  private def getPropertyValueOrNull(name: String, typ: QName): AnyRef =
    getPropertyValueOpt(name, typ).orNull

  def getPropertyOrThrow(name: String): Property =
    getPropertyOpt(name) getOrElse (throw new OXFException(s"property `$name` not found"))

  def getPropertyOpt(name: String): Option[Property] =
    getPropertyOptThrowIfTypeMismatch(name, null)

  def getObjectOpt(name: String): Option[AnyRef] =
    getPropertyValueOpt(name, null)

  def getObject(name: String, default: Any): Any =
    getObjectOpt(name) getOrElse default

  def getStringOrURIAsString(name: String, allowEmpty: Boolean = false): String =
    getObjectOpt(name) match {
      case Some(p: String) => if (allowEmpty) p.trimAllToEmpty else p.trimAllToNull
      case Some(p: URI)    => if (allowEmpty) p.toString.trimAllToEmpty else p.toString.trimAllToNull
      case None            => null
      case _               => throw new OXFException(s"Invalid attribute type requested for property `$name`: expected `${XMLConstants.XS_STRING_QNAME.qualifiedName}` or `${XMLConstants.XS_ANYURI_QNAME.qualifiedName}`")
    }

  def getStringOrURIAsStringOpt(name: String, allowEmpty: Boolean = false): Option[String] =
    getObjectOpt(name) flatMap {
      case p: String if allowEmpty => Some(p.trimAllToEmpty)
      case p: String               => p.trimAllToOpt
      case p: URI    if allowEmpty => Some(p.toString.trimAllToEmpty)
      case p: URI                  => p.toString.trimAllToOpt
      case _                       => throw new OXFException(s"Invalid attribute type requested for property `$name`: expected `${XMLConstants.XS_STRING_QNAME.qualifiedName}` or `${XMLConstants.XS_ANYURI_QNAME.qualifiedName}`")
    }

  def getStringOrURIAsString(name: String, default: String, allowEmpty: Boolean): String =
    getStringOrURIAsStringOpt(name, allowEmpty) getOrElse default

  def getNonBlankString(name: String): Option[String] =
    getPropertyValueOrNull(name, XMLConstants.XS_STRING_QNAME).asInstanceOf[String].trimAllToOpt

  def getString(name: String): String =
    getPropertyValueOrNull(name, XMLConstants.XS_STRING_QNAME).asInstanceOf[String].trimAllToNull

  def getString(name: String, default: String): String =
    getNonBlankString(name) getOrElse default

  // 2019-05-03: 2 calls in `XFormsProperties`
  def getNmtokens(name: String): ju.Set[String] =
    getPropertyValueOrNull(name, XMLConstants.XS_NMTOKENS_QNAME).asInstanceOf[ju.Set[String]]

  def getInteger(name: String): jl.Integer =
    getPropertyValueOrNull(name, XMLConstants.XS_INTEGER_QNAME).asInstanceOf[jl.Integer]

  def getInteger(name: String, default: Int): Int =
    Option(getInteger(name)) map (_.intValue) getOrElse default

  def getBoolean(name: String): jl.Boolean =
    getPropertyValueOrNull(name, XMLConstants.XS_BOOLEAN_QNAME).asInstanceOf[jl.Boolean]

  def getBoolean(name: String, default: Boolean): Boolean =
    Option(getBoolean(name)) map (_.booleanValue) getOrElse default

  def getDate(name: String): ju.Date =
    getPropertyValueOrNull(name, XMLConstants.XS_DATE_QNAME).asInstanceOf[ju.Date]

  def getDateTime(name: String): ju.Date =
    getPropertyValueOrNull(name, XMLConstants.XS_DATETIME_QNAME).asInstanceOf[ju.Date]

  def getQName(name: String): QName =
    getPropertyValueOrNull(name, XMLConstants.XS_QNAME_QNAME).asInstanceOf[QName]

  def getQName(name: String, default: QName): QName =
    Option(getQName(name)) getOrElse default

  // 2019-05-03: 1 Java caller
  def getURI(name: String): URI =
    getPropertyValueOrNull(name, XMLConstants.XS_ANYURI_QNAME).asInstanceOf[URI]

  def getIntOpt     (name: String): Option[Int]     = Option(getInteger(name)) map (_.intValue)
  def getBooleanOpt (name: String): Option[Boolean] = Option(getBoolean(name)) map (_.booleanValue)
  def getDateOpt    (name: String): Option[ju.Date] = Option(getDate(name))
  def getDateTimeOpt(name: String): Option[ju.Date] = Option(getDateTime(name))
  def getQNameOpt   (name: String): Option[QName]   = Option(getQName(name))
}

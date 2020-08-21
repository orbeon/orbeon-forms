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

import java.net.URI
import java.{lang => jl, util => ju}

import cats.syntax.option._
import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.CollectionUtils
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.xml.NamespaceMapping

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.compat._

case class Property(typ: QName, value: AnyRef, namespaces: Map[String, String]) {

  private var _associatedValue: Option[Any] = None

  def associatedValue[U](evaluate: Property => U): U = {
    if (_associatedValue.isEmpty)
      _associatedValue = Option(evaluate(this))
    _associatedValue.get.asInstanceOf[U]
  }

  def namespaceMapping = NamespaceMapping(namespaces)
}

private class PropertyNode {
  var property: Option[Property] = None
  var children = mutable.LinkedHashMap[String, PropertyNode]()
}

/**
 * Represent a set of properties.
 *
 * A property name can be exact, e.g. foo.bar.gaga, or it can contain wildcards, like ".*.bar.gaga", "foo.*.gaga", or
 * "foo.bar.*", or "*.bar.*", etc.
 *
 * TODO: Make this effectively immutable and remove `setProperty`.
 */
class PropertySet {

  private var exactProperties    = Map[String, Property]()
  private val wildcardProperties = new PropertyNode

  /**
   * Set a property. Used by PropertyStore.
   *
   * @param element         Element on which the property is defined. Used for QName resolution if needed.
   * @param name            property name
   * @param typ             property type, or null
   * @param stringValue     property string value
   */
  def setProperty(element: Element, name: String, typ: QName, stringValue: String): Unit = {

    val value    = PropertyStore.getObjectFromStringValue(stringValue, typ, element)
    val property = Property(typ, value, Dom4jUtils.getNamespaceContext(element).asScala.toMap)

    // Store exact property name anyway
    exactProperties += name -> property

    // Also store in tree (in all cases, not only when contains wildcard, so we get find all the properties that start with some token)
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

  def keySet: ju.Set[String] = exactProperties.keySet.asJava
  def size = exactProperties.size

  def allPropertiesAsJson: String = {

    def escapeJavaScript(value: String): String =
      value
        .replaceAllLiterally("\\", "\\\\")
        .replaceAllLiterally("\"", "\\\"")
        .replaceAllLiterally("\n", "\\n")

    val jsonProperties =
      for ((name, prop) <- exactProperties.to(List).sortBy(_._1))
        yield
          s"""|  "$name": {
              |    "type": "${escapeJavaScript(prop.typ.toString)}",
              |    "value": "${escapeJavaScript(prop.value.toString)}"
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

  def getObject(name: String, default: AnyRef): AnyRef =
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

  // 2019-05-03: 2 Java callers
  def getNmtokens(name: String): ju.Set[String] =
    getPropertyValueOrNull(name, XMLConstants.XS_NMTOKENS_QNAME).asInstanceOf[ju.Set[String]]

  def getInteger(name: String): jl.Integer =
    getPropertyValueOrNull(name, XMLConstants.XS_INTEGER_QNAME).asInstanceOf[jl.Integer]

  def getInteger(name: String, default: Int): jl.Integer =
    Option(getInteger(name)) getOrElse new jl.Integer(default)

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

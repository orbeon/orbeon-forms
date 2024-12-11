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

import cats.syntax.option.*
import org.orbeon.dom.QName
import org.orbeon.oxf.common.{OXFException, ValidationException}
import org.orbeon.oxf.properties.PropertySet.*
import org.orbeon.oxf.util.CollectionUtils
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.MarkupUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xml.XMLConstants.*
import org.orbeon.oxf.xml.dom.Extensions
import org.orbeon.oxf.xml.{SaxonUtils, XMLConstants}
import org.orbeon.xml.NamespaceMapping

import java.net.URI
import java.{lang as jl, util as ju}
import scala.collection.mutable
import scala.jdk.CollectionConverters.*


case class Property(typ: QName, value: AnyRef, namespaces: Map[String, String], name: String) {

  private var _associatedValue: Option[Any] = None

  def associatedValue[U](evaluate: Property => U): U = {
    if (_associatedValue.isEmpty)
      _associatedValue = Option(evaluate(this))
    _associatedValue.get.asInstanceOf[U]
  }

  def associateValue[U](associatedValue: U): Unit =
    _associatedValue = Option(associatedValue)

  def namespaceMapping: NamespaceMapping = NamespaceMapping(namespaces)

  def stringValue        : String         = value.toString
  def nonBlankStringValue: Option[String] = stringValue.trimAllToOpt
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

  val StarToken = "*"

  private case class PropertyNode(
    var property: Option[Property] = None,
    children    : mutable.Map[String, PropertyNode] = mutable.LinkedHashMap[String, PropertyNode]()
  )


  private val SomeXsStringQname   = XMLConstants.XS_STRING_QNAME.some
  private val SomeXsIntegerQname  = XMLConstants.XS_INTEGER_QNAME.some
  private val SomeXsBooleanQname  = XMLConstants.XS_BOOLEAN_QNAME.some
  private val SomeXsNmtokensQname = XMLConstants.XS_NMTOKENS_QNAME.some
  private val SomeXsDateQname     = XMLConstants.XS_DATE_QNAME.some
  private val SomeXsDatetimeQname = XMLConstants.XS_DATETIME_QNAME.some
  private val SomeXsQnameQname    = XMLConstants.XS_QNAME_QNAME.some

  def empty: PropertySet = new PropertySet(Map.empty, new PropertyNode)

  case class PropertyParams(namespaces: Map[String, String], name: String, typeQName: QName, stringValue: String)

  def apply(globalProperties: Iterable[PropertyParams]): PropertySet = {

    var propertiesByName = Map[String, Property]()
    val propertiesTree   = new PropertyNode

    def setProperty(namespaces: Map[String, String], name: String, typeQName: QName, value: AnyRef): Unit = {

      val property = Property(typeQName, value, if (namespaces eq null) Map.empty else namespaces, name) // shouldn't be null!

      // Store exact property name anyway
      propertiesByName += name -> property

      // Also store in tree (in all cases, not only when contains wildcard, so we get find all the properties
      // that start with some token)
      var currentNode = propertiesTree
      for (currentToken <- name.splitTo[List]("."))
        currentNode = currentNode.children.getOrElseUpdate(currentToken, new PropertyNode)

      // Store value
      currentNode.property = property.some
    }

    globalProperties foreach { case PropertyParams(namespaces, name, typ, stringValue) =>
      setProperty(namespaces, name, typ, getObjectFromStringValue(stringValue, typ, namespaces))
    }

    new PropertySet(propertiesByName, propertiesTree)
  }

  def isSupportedType(typeQName: QName): Boolean =
    SupportedTypes.contains(typeQName)

  private object Private {

    val SupportedTypes =
      Map[QName, (String, Map[String, String]) => AnyRef](
        XS_STRING_QNAME             -> convertString,
        XS_INTEGER_QNAME            -> convertInteger,
        XS_BOOLEAN_QNAME            -> convertBoolean,
        XS_DECIMAL_QNAME            -> convertDecimal,
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
    def convertDecimal(value: String, namespaces: Map[String, String]) = jl.Double.valueOf(value)
//    def convertDate   (value: String, namespaces: Map[String, String]) = new ju.Date(DateUtilsUsingSaxon.parseISODateOrDateTime(value))

    def convertQName(value: String, namespaces: Map[String, String]): QName =
      Extensions.resolveQName(namespaces.get, value, unprefixedIsNoNamespace = true) getOrElse
        (throw new ValidationException("QName value not found ", null))

    def convertURI(value: String, namespaces: Map[String, String]): URI =
      try {
        URI.create(value)
      } catch {
        case e: IllegalArgumentException =>
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
  }
}

class PropertySet private (
  propertiesByName: Map[String, Property],
  propertiesTree  : PropertyNode // this contains mutable nodes, but they don't mutate after construction
) {

  def keySet: Set[String] = propertiesByName.keySet
  def size: Int = propertiesByName.size

  // For form compilation
  def propertyParams: Iterable[PropertyParams] =
    propertiesByName.collect { case (name, prop) if ! name.toLowerCase.contains("password") =>

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
        (name, prop) <- propertiesByName.toList.sortBy(_._1)
        propType     = prop.typ.toString
        propValue    = if (name.toLowerCase.contains("password")) PasswordPlaceholder else prop.stringValue
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
        key          <- propertiesByName.keys
        value        <- getObjectOpt(key)
        booleanValue <- CollectionUtils.collectByErasedType[java.lang.Boolean](value)
      } yield
        key -> booleanValue

    tuples.toMap.asJava
  }

  // Return all the properties starting with the given name
  def propertiesStartsWith(incomingPropertyName: String, matchWildcards: Boolean = true): List[String] = {

    val result = mutable.Buffer[String]()

    def processNode(propertyNode: PropertyNode, foundTokens: List[String], incomingTokens: List[String]): Unit =
      incomingTokens.headOption match {
        case None if propertyNode.children.isEmpty =>

          result += foundTokens.reverse.mkString(".")

        case Some(StarToken) | None if propertyNode.children.nonEmpty =>

          for ((key, value) <- propertyNode.children)
            processNode(value, key :: foundTokens, incomingTokens.drop(1))

        case Some(currentToken) =>

          def findChild(t: String): List[(String, PropertyNode)] =
            propertyNode.children.get(t).map(t -> _).toList

          for ((key, value) <- findChild(currentToken) ::: (matchWildcards flatList findChild(StarToken)))
            processNode(value, key :: foundTokens, incomingTokens.drop(1))
      }

    processNode(propertiesTree, Nil, incomingPropertyName.splitTo[List]("."))

    result.toList
  }

  def propertiesMatching(incomingPropertyName: String): List[Property] = {

    val allIncomingTokens = incomingPropertyName.splitTo[List](".")

    def processNode(
      children      : collection.Map[String, PropertyNode],
      property      : Option[Property],
      incomingTokens: List[String]
    ): LazyList[(Property, Boolean)] =
      (incomingTokens.headOption, property) match {
        case (None, Some(property)) =>
          // Found

          val incomingAlwaysMatches =
            property.name.splitTo[List](".").zip(allIncomingTokens)
              .forall { case (t1, incoming) =>
                t1 == incoming || t1 == StarToken
              }

          LazyList(property -> incomingAlwaysMatches)
        case (Some(_), _) if children.isEmpty =>
          // Not found because requested property is longer
          LazyList.empty
        case (None, _) if children.nonEmpty =>
          // Not found because requested property is shorter
          LazyList.empty
        case (Some(StarToken), _) =>
          // Return all branches, but first the ones that are not wildcard if any, so that the resulting list is sorted
          (children.view.filter(_._1 != StarToken) ++ children.view.filter(_._1 == StarToken))
            .map(_._2)
            .flatMap(value => processNode(value.children, value.property, incomingTokens.drop(1)))
            .to(LazyList)
        case (Some(currentToken), _) =>
          // Return an exact branch if any and a wildcard branch if any
          (children.get(currentToken) #:: children.get(StarToken) #:: LazyList.empty)
            .flatten
            .flatMap(value => processNode(value.children, value.property, incomingTokens.drop(1)))
      }

    val allMatchingProperties =
      processNode(propertiesTree.children, propertiesTree.property, allIncomingTokens)

    // Take the shortest prefix ending with a property that swallows the input
    (allMatchingProperties.takeWhile(! _._2) ++ allMatchingProperties.dropWhile(! _._2).take(1))
      .map(_._1)
      .toList
  }

  private def getPropertyOptThrowIfTypeMismatch(name: String, typeToCheck: Option[QName]): Option[Property] = {

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
                case c              => wildcardSearch(c.get(head), tail) orElse wildcardSearch(c.get(StarToken), tail)
              }
          }
      }

    def getExact: Option[Property] =
      propertiesByName.get(name)

    def getWildcard: Option[Property] =
      wildcardSearch(Some(propertiesTree), name.splitTo[List]("."))

    def checkType(p: Property): Property =
      typeToCheck match {
        case Some(t) if t != p.typ =>
            throw new OXFException(
              s"Invalid attribute type requested for property `$name`: expected `${t.qualifiedName}`, found `${p.typ.qualifiedName}`"
            )
        case _ =>
          p
      }

     getExact orElse getWildcard map checkType
  }

  /* All getters */

  def getPropertyOpt    (name: String): Option[Property] = getPropertyOptThrowIfTypeMismatch(name, None)
  def getPropertyOrThrow(name: String): Property         = getPropertyOpt(name).getOrElse(throw new OXFException(s"property `$name` not found"))

  def getStringOrURIAsStringOpt(name: String, allowEmpty: Boolean = false): Option[String] =
    getObjectOpt(name).flatMap {
      case p: String if allowEmpty => Some(p.trimAllToEmpty)
      case p: String               => p.trimAllToOpt
      case p: URI    if allowEmpty => Some(p.toString.trimAllToEmpty)
      case p: URI                  => p.toString.trimAllToOpt
      case _                       => throw new OXFException(s"Invalid attribute type requested for property `$name`: expected `${XMLConstants.XS_STRING_QNAME.qualifiedName}` or `${XMLConstants.XS_ANYURI_QNAME.qualifiedName}`")
    }

  // Should be `getNonBlankString`
  def getString             (name: String, default: String)                     : String  = getNonBlankString(name)                    .getOrElse(default)
  def getStringOrURIAsString(name: String, default: String, allowEmpty: Boolean): String  = getStringOrURIAsStringOpt(name, allowEmpty).getOrElse(default)
  def getInteger            (name: String, default: Int)                        : Int     = getIntOpt(name).map(_.intValue)            .getOrElse(default)
  def getBoolean            (name: String, default: Boolean)                    : Boolean = getBooleanOpt(name)                        .getOrElse(default)
  def getQName              (name: String, default: QName)                      : QName   = getQNameOpt(name)                          .getOrElse(default)
  def getObject             (name: String, default: Any)                        : Any     = getObjectOpt(name)                         .getOrElse(default)

  // Should be `getNonBlankStringOpt`
  def getNonBlankString(name: String): Option[String]         = getPropertyValueOpt(name, SomeXsStringQname)  .flatMap(_.asInstanceOf[String].trimAllToOpt)
  def getIntOpt        (name: String): Option[Int]            = getPropertyValueOpt(name, SomeXsIntegerQname) .map(_.asInstanceOf[jl.Integer].intValue)
  def getBooleanOpt    (name: String): Option[Boolean]        = getPropertyValueOpt(name, SomeXsBooleanQname) .map(_.asInstanceOf[jl.Boolean].booleanValue)
  def getNmtokensOpt   (name: String): Option[ju.Set[String]] = getPropertyValueOpt(name, SomeXsNmtokensQname).map(_.asInstanceOf[ju.Set[String]])
  def getDateOpt       (name: String): Option[ju.Date]        = getPropertyValueOpt(name, SomeXsDateQname)    .map(_.asInstanceOf[ju.Date])
  def getDateTimeOpt   (name: String): Option[ju.Date]        = getPropertyValueOpt(name, SomeXsDatetimeQname).map(_.asInstanceOf[ju.Date])
  def getQNameOpt      (name: String): Option[QName]          = getPropertyValueOpt(name, SomeXsQnameQname)   .map(_.asInstanceOf[QName])
  def getObjectOpt     (name: String): Option[AnyRef]         = getPropertyValueOpt(name, null)

  private def getPropertyValueOpt(name: String, typeToCheck: Option[QName]): Option[AnyRef] =
    getPropertyOptThrowIfTypeMismatch(name, typeToCheck).map(_.value)

  // 2024-03-18: 14 legacy Java callers
  def getString(name: String): String =
    getPropertyValueOrNull(name, SomeXsStringQname).asInstanceOf[String].trimAllToNull

  // 2024-03-18: 1 legacy Java caller
  def getInteger(name: String): jl.Integer =
    getPropertyValueOrNull(name, SomeXsIntegerQname).asInstanceOf[jl.Integer]

  // 2024-03-18: 1 legacy Java caller
  def getBoolean(name: String): jl.Boolean =
    getPropertyValueOrNull(name, SomeXsBooleanQname).asInstanceOf[jl.Boolean]

  private def getPropertyValueOrNull(name: String, typeToCheck: Option[QName]): AnyRef =
    getPropertyValueOpt(name, typeToCheck).orNull
}

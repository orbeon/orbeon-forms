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
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.properties.PropertySet.*
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.MarkupUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xml.XMLConstants.*
import org.orbeon.oxf.xml.dom.Extensions
import org.orbeon.oxf.xml.{SaxonUtils, XMLConstants}
import org.orbeon.properties.api
import org.orbeon.xml.NamespaceMapping
import shapeless.syntax.typeable.*

import java.net.URI
import java.util.regex.Pattern
import java.{lang as jl, util as ju}
import scala.collection.mutable
import scala.util.control.NonFatal
import scala.jdk.CollectionConverters.*


case class Property(
  typ       : QName,
  value     : AnyRef,
  namespaces: Map[String, String],
  name      : String,
  profiles  : List[String] = Nil
) {

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

  import Private.*

  val StarToken = "*"

  private val SensitiveWords = Set("password", "credential")

  def isSensitivePropertyName(propertyName: String): Boolean =
    SensitiveWords.exists(word => propertyName.toLowerCase.contains(word))

  trait PropertyNodeT {
    def defaultProperty: Option[Property]
    def profileProperty(profile: String): Option[Property]
    def property: Option[Property] = defaultProperty
    def property(profileOpt: Option[String]): Option[Property] =
      profileOpt match {
        case Some(profile) => profileProperty(profile).orElse(defaultProperty)
        case None          => defaultProperty
      }
    def hasChildren: Boolean
    def get(k: String): Option[PropertyNodeT]
    def iterable: Iterable[(String, PropertyNodeT)] // xxx or iterator? iterate?; for propertiesMatching/propertiesStartsWith
  }

  case class MutablePropertyNode(
    var defaultProperty  : Option[Property] = None,
    val profileProperties: mutable.Map[String, Property] = mutable.LinkedHashMap[String, Property](),
    children             : mutable.Map[String, MutablePropertyNode] = mutable.LinkedHashMap[String, MutablePropertyNode]()
  ) extends PropertyNodeT {
      def hasChildren: Boolean = children.nonEmpty
      def get(k: String): Option[PropertyNodeT] = children.get(k)
      def iterable: Iterable[(String, PropertyNodeT)] = children.view

      def profileProperty(profile: String): Option[Property] = profileProperties.get(profile)
    }

  private[properties] val SomeXsStringQname   = XMLConstants.XS_STRING_QNAME.some
  private[properties] val SomeXsIntegerQname  = XMLConstants.XS_INTEGER_QNAME.some
  private[properties] val SomeXsBooleanQname  = XMLConstants.XS_BOOLEAN_QNAME.some
  private[properties] val SomeXsNmtokensQname = XMLConstants.XS_NMTOKENS_QNAME.some
  private[properties] val SomeXsDateQname     = XMLConstants.XS_DATE_QNAME.some
  private[properties] val SomeXsDatetimeQname = XMLConstants.XS_DATETIME_QNAME.some
  private[properties] val SomeXsQnameQname    = XMLConstants.XS_QNAME_QNAME.some

  val empty: PropertySet = apply(Nil, "")

  case class PropertyParams(
    namespaces : Map[String, String],
    name       : String,
    typeQName  : QName,
    stringValue: String,
    profiles   : List[String] = Nil // default param because there are many calls in tests
  )

  def forTests(globalProperties: Iterable[PropertyParams]): PropertySet =
    apply(globalProperties, eTag = "") // prefer to keep a non-default parameter and use a special method for tests

  def apply(globalProperties: Iterable[PropertyParams], eTag: api.ETag): PropertySet = {

    val allPropertiesBuffer = mutable.ListBuffer[Property]()
    var propertiesByName    = Map[String, Property]()
    val propertiesTree      = MutablePropertyNode()

    def setProperty(namespaces: Map[String, String], name: String, typeQName: QName, value: AnyRef, profiles: List[String]): Unit = {

      val property = Property(typeQName, value, if (namespaces eq null) Map.empty else namespaces, name, profiles)

      allPropertiesBuffer += property

      // Store exact property name anyway
      if (profiles.isEmpty || ! propertiesByName.contains(name))
        propertiesByName += name -> property

      // Also store in tree (in all cases, not only when contains wildcard, so we get find all the properties
      // that start with some token)
      var currentNode = propertiesTree
      for (currentToken <- name.splitTo[List]("."))
        currentNode = currentNode.children.getOrElseUpdate(currentToken, MutablePropertyNode())

      // Store value
      if (profiles.isEmpty)
        currentNode.defaultProperty = property.some
      else
        for (profile <- profiles)
          currentNode.profileProperties += (profile -> property)
    }

    globalProperties.foreach { case PropertyParams(namespaces, name, typ, stringValue, profiles) =>
      setProperty(namespaces, name, typ, getObjectFromStringValue(name, stringValue, typ, namespaces), profiles)
    }

    val allProperties = allPropertiesBuffer.toList
    val allProfiles   = allProperties.flatMap(_.profiles).toSet

    new PropertySetImpl(allProperties, propertiesByName, propertiesTree, allProfiles, eTag)
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

    def getObjectFromStringValue(propertyName: String, stringValue: String, typ: QName, namespaces: Map[String, String]): AnyRef =
      try {
        SupportedTypes.get(typ).map(_(stringValue, namespaces)).orNull
      } catch {
        case NonFatal(e) =>
          throw new OXFException(
            s"Error converting value for property `$propertyName` " +
            s"of type `${typ.qualifiedName}` (${e.toString})"
          )
      }

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

class PropertySetImpl private[properties] (
  val allProperties                     : Iterable[Property],
  protected[orbeon] val propertiesByName: collection.Map[String, Property],
  protected[orbeon] val propertiesTree  : PropertyNodeT,  // this contains mutable nodes, but they don't mutate after construction
  val allProfiles                       : Set[String],
                    val eTag            : api.ETag
) extends
  PropertySet

trait PropertySet extends PropertySetFunctions {
  val eTag         : api.ETag
  def allProperties: Iterable[Property]
  def allProfiles  : Set[String]
}

trait PropertySetFunctions extends PropertySetGetters {

  def allProperties: Iterable[Property]
  def allProfiles  : Set[String]
  protected[orbeon] def propertiesByName: collection.Map[String, Property]
  protected[orbeon] def propertiesTree  : PropertyNodeT

  def keySet: collection.Set[String] = propertiesByName.keySet
  def size: Int = propertiesByName.size
  def isEmpty: Boolean = size == 0

  // For form compilation
  def propertyParams: Iterable[PropertyParams] =
    allProperties.collect { case prop if ! PropertySet.isSensitivePropertyName(prop.name) =>

      // Custom serialization to String, not ideal
      val stringValue = {
        prop.value match {
          case v: ju.Set[_] => v.asScala map (_.toString) mkString " "
          case v            => v.toString
        }
      }

      PropertyParams(prop.namespaces, prop.name, prop.typ, stringValue, prop.profiles)
    }

  def allPropertiesAsJson: String = {

    val jsonProperties =
      for {
        (name, prop) <- propertiesByName.toList.sortBy(_._1)
        propType     = prop.typ.toString
        propValue    = if (PropertySet.isSensitivePropertyName(name)) Headers.PasswordPlaceholder else prop.stringValue
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
        booleanValue <- value.cast[java.lang.Boolean]
      } yield
        key -> booleanValue

    tuples.toMap.asJava
  }

  // Return all the properties starting with the given name
  def propertiesStartsWith(incomingPropertyName: String, matchWildcards: Boolean = true): List[String] = {

    if (incomingPropertyName.contains(StarToken))
      println(s"propertiesStartsWith: incomingPropertyName=$incomingPropertyName, matchWildcards=$matchWildcards")

    val result = mutable.Buffer[String]()

    def processNode(propertyNode: PropertyNodeT, foundTokens: List[String], incomingTokens: List[String]): Unit =
      (incomingTokens.headOption, propertyNode.hasChildren) match {
        case (None, false) =>
          result += foundTokens.reverse.mkString(".")
        case (None, true) | (Some(StarToken), _) =>
          for ((key, value) <- propertyNode.iterable)
            processNode(value, key :: foundTokens, incomingTokens.drop(1))
        case (Some(currentToken), _) =>
          def findChild(t: String): List[(String, PropertyNodeT)] =
            propertyNode.get(t).map(t -> _).toList
          for ((key, value) <- findChild(currentToken) ::: (matchWildcards flatList findChild(StarToken)))
            processNode(value, key :: foundTokens, incomingTokens.drop(1))
      }
    processNode(propertiesTree, Nil, incomingPropertyName.splitTo[List]("."))

    result.toList
  }

  def propertiesMatching(incomingPropertyName: String): List[Property] =
    propertiesMatching(incomingPropertyName, None)

  def propertiesMatching(incomingPropertyName: String, profileOpt: Option[String]): List[Property] = {

    val allIncomingTokens = incomingPropertyName.splitTo[List](".")

    def processNode(
      incomingTokens: List[String],
      currentNode   : PropertyNodeT,
    ): LazyList[(Property, Boolean)] =
      (incomingTokens.headOption, currentNode.property(profileOpt), currentNode.hasChildren) match {
        case (None, Some(property), _) =>
          // Found

          val incomingAlwaysMatches =
            property.name.splitTo[List](".").zip(allIncomingTokens)
              .forall { case (t1, incoming) =>
                t1 == incoming || t1 == StarToken
              }

          LazyList(property -> incomingAlwaysMatches)
        case (Some(_), _, false) =>
          // Not found because requested property is longer
          LazyList.empty
        case (None, _, true) =>
          // Not found because requested property is shorter
          LazyList.empty
        case (Some(StarToken), _, _) =>
          // Return all branches, but first the ones that are not wildcard if any, so that the resulting list is sorted
          (currentNode.iterable.filter(_._1 != StarToken) ++ currentNode.iterable.filter(_._1 == StarToken))
            .map(_._2)
            .flatMap(newNode => processNode(incomingTokens.drop(1), newNode))
            .to(LazyList)
        case (Some(currentToken), _, _) =>
          // Return an exact branch if any and a wildcard branch if any
          (currentNode.get(currentToken) #:: currentNode.get(StarToken) #:: LazyList.empty)
            .flatten
            .flatMap(newNode => processNode(incomingTokens.drop(1), newNode))
        case (None, None, false) =>
          // Can this happen?
          LazyList.empty
      }

    val allMatchingProperties =
      processNode(allIncomingTokens, propertiesTree)

    // Take the shortest prefix ending with a property that swallows the input
    (allMatchingProperties.takeWhile(! _._2) ++ allMatchingProperties.dropWhile(! _._2).take(1))
      .map(_._1)
      .toList
  }

  protected def getPropertyOptThrowIfTypeMismatch(
    name       : String,
    typeToCheck: Option[QName],
    profileOpt : Option[String]
  ): Option[Property] = {

    def wildcardSearch(
      propertyNodeOpt : Option[PropertyNodeT],
      tokens          : List[String]
    ): Option[Property] =
      propertyNodeOpt match {
        case None               => None
        case Some(propertyNode) =>
          tokens match {
            case Nil =>
              propertyNode.property(profileOpt)
            case head :: tail if propertyNode.hasChildren =>
              wildcardSearch(propertyNode.get(head), tail) orElse wildcardSearch(propertyNode.get(StarToken), tail)
            case _ =>
              None
          }
      }

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

    getWildcard map checkType
  }
}

trait PropertySetGetters {

  protected def getPropertyOptThrowIfTypeMismatch(
    name       : String,
    typeToCheck: Option[QName],
    profileOpt : Option[String]
  ): Option[Property]

  def getPropertyOpt(name: String, profileOpt: Option[String] = None): Option[Property] =
    getPropertyOptThrowIfTypeMismatch(name, None, profileOpt)

  def getPropertyOrThrow(name: String, profileOpt: Option[String] = None): Property =
    getPropertyOpt(name, profileOpt).getOrElse(throw new OXFException(s"property `$name` not found"))

  def getStringOrURIAsStringOpt(name: String, allowEmpty: Boolean = false, profileOpt: Option[String] = None): Option[String] =
    getObjectOpt(name, profileOpt).flatMap {
      case p: String if allowEmpty => Some(p.trimAllToEmpty)
      case p: String               => p.trimAllToOpt
      case p: URI    if allowEmpty => Some(p.toString.trimAllToEmpty)
      case p: URI                  => p.toString.trimAllToOpt
      case _                       => throw new OXFException(s"Invalid attribute type requested for property `$name`: expected `${XMLConstants.XS_STRING_QNAME.qualifiedName}` or `${XMLConstants.XS_ANYURI_QNAME.qualifiedName}`")
    }

  // Should be `getNonBlankString`
  def getString             (name: String, default: String,                              profileOpt: Option[String] = None): String  = getNonBlankString(name, profileOpt).getOrElse(default)
  def getStringOrURIAsString(name: String, default: String, allowEmpty: Boolean = false, profileOpt: Option[String] = None): String  = getStringOrURIAsStringOpt(name, allowEmpty, profileOpt).getOrElse(default)
  def getInteger            (name: String, default: Int,                                 profileOpt: Option[String] = None): Int     = getIntOpt(name, profileOpt).map(_.intValue).getOrElse(default)
  def getBoolean            (name: String, default: Boolean,                             profileOpt: Option[String] = None): Boolean = getBooleanOpt(name, profileOpt).getOrElse(default)
  def getQName              (name: String, default: QName,                               profileOpt: Option[String] = None): QName   = getQNameOpt(name, profileOpt).getOrElse(default)
  def getObject             (name: String, default: Any,                                 profileOpt: Option[String] = None): Any     = getObjectOpt(name, profileOpt).getOrElse(default)

  // Should be `getNonBlankStringOpt`
  def getNonBlankString(name: String, profileOpt: Option[String] = None): Option[String]         = getPropertyValueOpt(name, SomeXsStringQname, profileOpt).flatMap(_.asInstanceOf[String].trimAllToOpt)
  def getIntOpt        (name: String, profileOpt: Option[String] = None): Option[Int]            = getPropertyValueOpt(name, SomeXsIntegerQname, profileOpt).map(_.asInstanceOf[jl.Integer].intValue)
  def getBooleanOpt    (name: String, profileOpt: Option[String] = None): Option[Boolean]        = getPropertyValueOpt(name, SomeXsBooleanQname, profileOpt).map(_.asInstanceOf[jl.Boolean].booleanValue)
  def getNmtokensOpt   (name: String, profileOpt: Option[String] = None): Option[ju.Set[String]] = getPropertyValueOpt(name, SomeXsNmtokensQname, profileOpt).map(_.asInstanceOf[ju.Set[String]])
  def getDateOpt       (name: String, profileOpt: Option[String] = None): Option[ju.Date]        = getPropertyValueOpt(name, SomeXsDateQname, profileOpt).map(_.asInstanceOf[ju.Date])
  def getDateTimeOpt   (name: String, profileOpt: Option[String] = None): Option[ju.Date]        = getPropertyValueOpt(name, SomeXsDatetimeQname, profileOpt).map(_.asInstanceOf[ju.Date])
  def getQNameOpt      (name: String, profileOpt: Option[String] = None): Option[QName]          = getPropertyValueOpt(name, SomeXsQnameQname, profileOpt).map(_.asInstanceOf[QName])
  def getObjectOpt     (name: String, profileOpt: Option[String] = None): Option[AnyRef]         = getPropertyValueOpt(name, None, profileOpt)

  def getPattern(propertyName: String, default: String, profileOpt: Option[String] = None): Pattern =
    getPatternOpt(propertyName, profileOpt)
    .getOrElse(Pattern.compile(default))

  def getPatternOpt(propertyName: String, profileOpt: Option[String] = None): Option[Pattern] =
    getPropertyOpt(propertyName, profileOpt)
    .flatMap(_.associatedValue(_.nonBlankStringValue.map(Pattern.compile)))

  private def getPropertyValueOpt(name: String, typeToCheck: Option[QName], profileOpt: Option[String]): Option[AnyRef] =
    getPropertyOptThrowIfTypeMismatch(name, typeToCheck, profileOpt).map(_.value)

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
    getPropertyValueOpt(name, typeToCheck, profileOpt = None).orNull
}
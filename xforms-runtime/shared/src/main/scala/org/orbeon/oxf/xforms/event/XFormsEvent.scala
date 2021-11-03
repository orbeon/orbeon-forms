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
package org.orbeon.oxf.xforms.event

import org.orbeon.datatypes.LocationData
import org.orbeon.oxf.xforms.analysis.EventHandler
import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xml.{SaxonUtils, SaxonUtilsDependsOnXPath}
import org.orbeon.oxf.xml.XMLUtils.buildExplodedQName
import org.orbeon.saxon.om._
import org.orbeon.xforms.XFormsId
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.analysis.Phase

import scala.jdk.CollectionConverters._

/**
 * Base class for all DOM events.
 *
 * - an event has a number of properties
 * - a property can be defined or not
 * - if a property is defined, it may have a value or not
 * - properties are obtained from 3 sources
 *   - `XFormsEvent` base properties
 *   - overriding lazyProperties
 *   - passing to the constructor
 * - the reason we use `lazyProperties` is that we had trouble passing a `PropertyGetter` with a reference to this to the
 *   constructor!
 * - all property values are:
 *   - Java values (including `NodeInfo`)
 *   - `Seq` of Java values
 *   - `Either` of the above (2020-02-27)
 * - for Java/Scala consumers, use `property[T]``
 * - for XPath consumers, use `getAttribute``
 * - `PropertyGetter` is used instead of a plain `Map` because:
 *   - it allows for values to be computed dynamically, so we don't compute all property values unnecessarily
 *   - it is a `PartialFunction` so `PropertyGetter` can be easily composed with `orElse`
 *   - a `Map` is automatically a `PropertyGetter`
 * - whenever possible, all aspects of an event are represented as properties
 * - in some cases, events store special values natively (e.g. `Throwable`, `ConnectionResult`, etc.)
 *
 * Possible improvement: we might be able to use reflection for standard properties. This would simply the
 * implementation of `XFormsEvent`, `XFormsUIEvent`, and derived events which have more complex properties.
 *
 * 2019-05-29: Reflection is generally not desirable. Also, we might want to revise more in depth how we represent
 * events and properties. It is a little messy.
 */
abstract class XFormsEvent(
  val name         : String,
  val targetObject : XFormsEventTarget,
  properties       : PropertyGetter,
  val bubbles      : Boolean,
  val cancelable   : Boolean
) {

  require(targetObject ne null)
  require(containingDocument ne null)

  final def containingDocument = targetObject.containingDocument

  // Priority is given to the base properties, then the lazyProperties, then the properties passed at construction
  private lazy val allProperties = getters(this, XFormsEvent.Getters) orElse lazyProperties orElse properties
  def lazyProperties: PropertyGetter = EmptyGetter

  // Mutable phase and observer as the event is being dispatched
  private var _currentPhase: Phase = _
  private var _currentObserver: XFormsEventTarget = _

  final def currentPhase: Phase = _currentPhase
  final def currentPhase_=(currentPhase: Phase): Unit = _currentPhase = currentPhase

  final def currentObserver: XFormsEventTarget = _currentObserver
  final def currentObserver_=(currentObserver: XFormsEventTarget): Unit = _currentObserver = currentObserver

  // Return a property of the given type
  // WARNING: Remember that type erasure takes place! Property[T[U1]] will work even if the underlying type was T[U2]!
  //
  // Return None if:
  // - the property is not supported
  // - it is supported but no value is available for it
  final def property[T](name: String): Option[T] =
    allProperties.applyOrElse(name, { name: String => warnUnsupportedIfNeeded(name); None }) map (_.asInstanceOf[T])

  // Return a property of the given type or the default value
  // WARNING: Remember that type erasure takes place! Property[T[U1]] will work even if the underlying type was T[U2]!
  //
  // Return the default value if:
  // - the property is not supported
  // - it is supported but no value is available for it
  final def propertyOrDefault[T](name: String, default: T): T =
    allProperties.applyOrElse(name, (_: String) => None) map (_.asInstanceOf[T]) getOrElse default

  // Get an attribute as an XPath SequenceIterator
  final def getAttribute(name: String): SequenceIterator = {

    warnDeprecatedIfNeeded(name)

    // "If the event context information does not contain the property indicated by the string argument, then an
    // empty node-set is returned."

    def handleOneLevel(any: Any): SequenceIterator = any match {
      case s: Seq[_]       => listIterator(s map SaxonUtilsDependsOnXPath.anyToItemIfNeeded)
      case e: Either[_, _] => e.fold(handleOneLevel, handleOneLevel)
      case other           => itemIterator(SaxonUtilsDependsOnXPath.anyToItemIfNeeded(other))
    }

    allProperties.applyOrElse(name, { name: String => warnUnsupportedIfNeeded(name); None }) map handleOneLevel getOrElse emptyIterator
  }

  private def warnDeprecatedIfNeeded(name: String) =
    newPropertyName(name) foreach { newName =>
      indentedLogger.logWarning("", "event('" + name + "') is deprecated. Use event('" + newName + "') instead.")
    }

  private def warnUnsupportedIfNeeded(name: String) =
    if (warnIfMissingProperty)
      indentedLogger.logDebug("", "Unsupported event context information for event('" + name + "').")

  // These methods can be overridden by subclasses
  implicit def indentedLogger = containingDocument.getIndentedLogger(XFormsEvents.LOGGING_CATEGORY)
  def warnIfMissingProperty = true
  def newPropertyName(name: String) = Deprecated.get(name)
  def locationData: LocationData = null

  // Whether this event matches filters placed on the given event handler.
  // This is used for checking modifiers and text on keypress events.
  def matches(handler: EventHandler) = true
}

object XFormsEvent {

  // PropertyGetter is more general than `Map`, but a `Map` is automatically a `PropertyGetter`
  type PropertyGetter = PartialFunction[String, Option[Any]]
  val EmptyGetter: PropertyGetter = Map()

  // Can we benefit from Scala 2.10's applyOrElse here?
  def getters[E <: XFormsEvent](e: E, m: Map[String, E => Option[Any]]): PropertyGetter = new PropertyGetter {
    def isDefinedAt(name: String) = m.isDefinedAt(name)
    def apply(name: String)       = m(name)(e)
  }

  private def emptyIterator = SaxonUtils.emptyIterator
  private def itemIterator   (i: Item)      = if (i ne null)  SaxonUtils.itemIterator(i) else emptyIterator
  private def listIterator   (s: Seq[Item]) = if (s.nonEmpty) SaxonUtils.listIterator(s) else emptyIterator

  def xxfName(name: String) = buildExplodedQName(XXFORMS_NAMESPACE_URI, name)

  private val Deprecated = Map(
    "target"         -> "xxf:targetid",
    "event"          -> "xxf:type",
    "repeat-indexes" -> "xxf:repeat-indexes"
  )

  private val Getters = Map[String, XFormsEvent => Option[Any]](
    "target"                       -> (e => Option(e.targetObject.getId)),
    xxfName("target")              -> (e => Option(e.targetObject.getId)),
    xxfName("targetid")            -> (e => Option(e.targetObject.getId)),
    xxfName("absolute-targetid")   -> (e => Option(XFormsId.effectiveIdToAbsoluteId(e.targetObject.getEffectiveId))),
    "event"                        -> (e => Option(e.name)),
    xxfName("type")                -> (e => Option(e.name)),
    xxfName("bubbles")             -> (e => Option(e.bubbles)),
    xxfName("cancelable")          -> (e => Option(e.cancelable)),
    xxfName("phase")               -> (e => Option(e.currentPhase.name)),
    xxfName("observerid")          -> (e => Option(e.currentObserver.getId)),
    xxfName("absolute-observerid") -> (e => Option(XFormsId.effectiveIdToAbsoluteId(e.currentObserver.getEffectiveId))),
    "repeat-indexes"               -> repeatIndexes,
    xxfName("repeat-indexes")      -> repeatIndexes,
    xxfName("repeat-ancestors")    -> repeatAncestors,
    xxfName("target-prefixes")     -> targetPrefixes
  )

  // NOTE: should ideally be Option[Seq[Int]]. At this time XForms callers assume Option[Seq[String]].
  private def repeatIndexes(e: XFormsEvent): Option[Seq[String]] = {
    val result = Some(XFormsId.getEffectiveIdSuffixParts(e.targetObject.getEffectiveId).toList map (_.toString))
    result
  }

  private def repeatAncestors(e: XFormsEvent): Option[Seq[String]] =
    if (XFormsId.hasEffectiveIdSuffix(e.targetObject.getEffectiveId)) {
      // There is a suffix so compute
      val ancestorRepeats =
        e.containingDocument.staticOps.getAncestorRepeatIds(XFormsId.getPrefixedId(e.targetObject.getEffectiveId))

      Some(ancestorRepeats map XFormsId.getStaticIdFromId)
    } else
      // No suffix
      None

  private def targetPrefixes(e: XFormsEvent): Option[Seq[String]] =
    Some(XFormsId.getEffectiveIdPrefixParts(e.targetObject.getEffectiveId).toList)
}
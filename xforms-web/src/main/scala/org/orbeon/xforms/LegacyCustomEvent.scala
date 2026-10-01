/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.xforms

import org.log4s.Logger
import org.orbeon.oxf.util.LoggerFactory

import scala.collection.mutable.ListBuffer
import scala.scalajs.js
import scala.scalajs.js.Dynamic.global as g
import scala.util.control.NonFatal


object LegacyCustomEvent {

  val List = 0
  val Flat = 1

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xforms.LegacyCustomEvent")

  private case class Subscriber(
    fn             : js.Function,
    obj            : js.UndefOr[js.Any],
    overrideContext: js.UndefOr[js.Any]
  ) {
    def matches(candidateFn: js.Function, candidateObj: js.UndefOr[js.Any]): Boolean =
      (fn eq candidateFn) && (candidateObj.isEmpty || candidateObj == obj)
  }
}

/**
 * Lightweight native replacement for `YAHOO.util.CustomEvent`.
 *
 * Supports both LIST (default) and FLAT signatures, as well as `fireOnce`.
 */
class LegacyCustomEvent(
  val typeName    : String,
  val defaultScope: js.UndefOr[js.Any] = js.undefined,
  val signature   : Int                = LegacyCustomEvent.List,
  val fireOnce    : Boolean            = false
) extends js.Object {

  import LegacyCustomEvent.*

  def `type`: String = typeName

  private var _fired    : Boolean       = false
  private var _firedWith: Seq[js.Any]   = Nil
  private val subscribers               = ListBuffer[Subscriber]()

  def fired: Boolean = _fired

  def subscribe(
    fn             : js.Function,
    obj            : js.UndefOr[js.Any] = js.undefined,
    overrideContext: js.UndefOr[js.Any] = js.undefined
  ): Unit = {
    if (fn == null || js.isUndefined(fn)) {
      logger.warn(s"Invalid callback for subscriber to '$typeName'")
    } else if (fireOnce && _fired) {
      notifySubscriber(Subscriber(fn, obj, overrideContext), _firedWith)
    } else {
      subscribers += Subscriber(fn, obj, overrideContext)
    }
  }

  def unsubscribe(fn: js.Function, obj: js.UndefOr[js.Any] = js.undefined): Boolean = {
    val beforeSize = subscribers.size
    subscribers.filterInPlace(s => ! s.matches(fn, obj))
    subscribers.size < beforeSize
  }

  def unsubscribeAll(): Unit =
    subscribers.clear()

  def fire(args: js.Any*): Unit =
    if (fireOnce && _fired) {
      ()
    } else {
      _fired     = true
      _firedWith = args
      val currentSubscribers = subscribers.toList
      currentSubscribers.foreach { subscriber =>
        notifySubscriber(subscriber, args)
      }
    }

  private def globalScope: js.Any =
    if (js.typeOf(g.window) != "undefined") g.window
    else g.globalThis

  private def notifySubscriber(subscriber: Subscriber, args: Seq[js.Any]): Unit = {
    val scope: js.Any =
      subscriber.overrideContext.toOption match {
        case Some(ctx) if js.typeOf(ctx) == "boolean" =>
          if (ctx.asInstanceOf[Boolean])
            subscriber.obj.getOrElse(defaultScope.getOrElse(globalScope))
          else
            defaultScope.getOrElse(globalScope)
        case Some(ctx) =>
          ctx
        case None =>
          defaultScope.getOrElse(globalScope)
      }

    try {
      val customObj: js.Any = subscriber.obj.getOrElse(js.undefined.asInstanceOf[js.Any])
      if (signature == Flat) {
        val param: js.Any = args.headOption.getOrElse(js.undefined.asInstanceOf[js.Any])
        subscriber.fn.asInstanceOf[js.Dynamic].call(
          scope,
          param,
          customObj
        )
      } else {
        val argsArray = js.Array[js.Any](args: _*)
        subscriber.fn.asInstanceOf[js.Dynamic].call(
          scope,
          if (`type` != null) `type` else "",
          argsArray,
          customObj
        )
      }
    } catch {
      case NonFatal(t) =>
        logger.error(t)(s"Error executing subscriber for event '${`type`}'")
    }
  }
}

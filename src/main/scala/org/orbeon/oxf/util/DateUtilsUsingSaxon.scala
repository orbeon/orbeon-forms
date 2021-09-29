/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.util

import java.util
import java.util.Properties

import javax.xml.transform.Result
import org.orbeon.saxon.`type`.ValidationFailure
import org.orbeon.saxon.expr.{StackFrame, XPathContext, XPathContextMajor, XPathContextMinor}
import org.orbeon.saxon.instruct.ParameterSet
import org.orbeon.saxon.om.{Item, NamePool}
import org.orbeon.saxon.regex.RegexIterator
import org.orbeon.saxon.sort.{GroupIterator, StringCollator}
import org.orbeon.saxon.trans.{Mode, Rule}
import org.orbeon.saxon.value.{CalendarValue, DateTimeValue, DateValue}
import org.orbeon.saxon.{Configuration, Controller}


// TODO: Ideally use all java.time APIs?
object DateUtilsUsingSaxon {

  // Epoch dateTime/date
  private val EpochDateTime = new DateTimeValue(1970, 1, 1, 0, 0, 0, 0, 0)
  private val EpochDate     = new DateValue(1970, 1, 1, 0) // CalendarValue.NO_TIMEZONE

  def isISODateOrDateTime(value: String): Boolean =
    tryParseISODateOrDateTime(value, TimeZone.Default).isDefined

  // Parse a date in XML Schema-compatible ISO format:
  //
  // - Format for a dateTime: [-]yyyy-mm-ddThh:mm:ss[.fff*][([+|-]hh:mm | Z)]
  // - Format for a date:     [-]yyyy-mm-dd[([+|-]hh:mm | Z)]
  //
  // Throws IllegalArgumentException if the date format is incorrect.
  //
  // If the date or dateTime doesn't have a timezone, then xxx.
  //
  def parseISODateOrDateTime(date: String): Long =
    tryParseISODateOrDateTime(date, TimeZone.Default) getOrElse (throw new IllegalArgumentException)

  def tryParseISODateOrDateTime(date: String, defaultTimeZone: TimeZone): Option[Long] = {

    // FIXME: what if the value has an optional `-` sign in front?
    val valueOrFailure =
      if (date.length >= 11 && date.charAt(10) == 'T')
        DateTimeValue.makeDateTimeValue(date)
      else
        DateValue.makeDateValue(date)

    valueOrFailure match {
      case value: CalendarValue =>
        // FIXME: Could we not just use: `value.getCalendar.getTimeInMillis`
        Some(value.subtract(if (value.isInstanceOf[DateTimeValue]) EpochDateTime else EpochDate, defaultTimeZone).getLengthInMilliseconds)
      case _: ValidationFailure => None
    }
  }

  // Parse an ISO date and return the number of milliseconds from the epoch.
  def tryParseISODate(date: String, defaultTimeZone: TimeZone): Option[Long] =
    DateValue.makeDateValue(date) match {
      case value: DateValue     =>
        value.getCalendar.getTimeInMillis
        Some(value.subtract(EpochDate, defaultTimeZone).getLengthInMilliseconds)
      case _: ValidationFailure => None
    }

  object TimeZone {

    object Default extends TimeZone {
      def getImplicitTimezone: Int = DateUtils.DefaultOffsetMinutes
    }

    object UTC extends TimeZone {
      def getImplicitTimezone: Int = 0
    }
  }

  sealed trait TimeZone extends XPathContext {

    import org.orbeon.saxon.`type`.SchemaType
    import org.orbeon.saxon.event.SequenceReceiver
    import org.orbeon.saxon.expr.XPathContext
    import org.orbeon.saxon.instruct.LocalParam
    import org.orbeon.saxon.om.{SequenceIterator, StructuredQName, ValueRepresentation}
    import org.orbeon.saxon.trace.InstructionInfo

    def getImplicitTimezone: Int

    // None of these methods are called by Saxon upon `subtract()`
    def newContext                                 : XPathContextMajor   = illegal
    def newCleanContext                            : XPathContextMajor   = illegal
    def newMinorContext                            : XPathContextMinor   = illegal
    def getLocalParameters                         : ParameterSet        = illegal
    def getTunnelParameters                        : ParameterSet        = illegal
    def setOrigin(expr: InstructionInfo)           : Unit                = illegal
    def setOriginatingConstructType(loc: Int)      : Unit                = illegal
    def getOrigin                                  : InstructionInfo     = illegal
    def getOriginatingConstructType                : Int                 = illegal
    def getController                              : Controller          = illegal
    def getConfiguration                           : Configuration       = illegal
    def getNamePool                                : NamePool            = illegal
    def setCaller(caller: XPathContext)            : Unit                = illegal
    def getCaller                                  : XPathContext        = illegal
    def setCurrentIterator(iter: SequenceIterator) : Unit                = illegal
    def getCurrentIterator                         : SequenceIterator    = illegal
    def getContextPosition                         : Int                 = illegal
    def getContextItem                             : Item                = illegal
    def getLast                                    : Int                 = illegal
    def isAtLast                                   : Boolean             = illegal
    def getCollation(name: String)                 : StringCollator      = illegal
    def getDefaultCollation                        : StringCollator      = illegal
    def useLocalParameter(
      qName    : StructuredQName,
      binding  : LocalParam,
      isTunnel : Boolean
    ): Boolean = illegal
    def getStackFrame                              : StackFrame          = illegal
    def evaluateLocalVariable(slotnumber: Int)     : ValueRepresentation = illegal
    def setLocalVariable(
      slotnumber : Int,
      value      : ValueRepresentation
    ): Unit = illegal
    def changeOutputDestination(
      props        : Properties,
      result       : Result,
      isFinal      : Boolean,
      hostLanguage : Int,
      validation   : Int,
      schemaType   : SchemaType
    ) : Unit = illegal
    def setTemporaryReceiver(out: SequenceReceiver): Unit                = illegal
    def setReceiver(receiver: SequenceReceiver)    : Unit                = illegal
    def getReceiver                                : SequenceReceiver    = illegal
    def getCurrentMode                             : Mode                = illegal
    def getCurrentTemplateRule                     : Rule                = illegal
    def getCurrentGroupIterator                    : GroupIterator       = illegal
    def getCurrentRegexIterator                    : RegexIterator       = illegal
    def getCurrentDateTime                         : DateTimeValue       = illegal
    def iterateStackFrames                         : util.Iterator[_]    = illegal

    private def illegal = throw new IllegalStateException
  }
}

package org.orbeon.oxf.fr.excel

import org.orbeon.oxf.util.StringUtils.OrbeonStringOps

import java.util.regex.Pattern
import scala.util.control.Breaks.*


object ExcelNumberFormat {

  private val NonQuotedSeparators = Set(":", "/", "-")

  def convertFromXPathFormat(format: String): String = {

    val sb = new java.lang.StringBuilder

    var i = 0
    breakable {
      while (true) {
        // Search next component
        val sb2 = new java.lang.StringBuilder
        while (i < format.length && format.charAt(i) != '[') {
          sb2.append(format.charAt(i))
          if (format.charAt(i) == ']') {
            i += 1
            if (i == format.length || format.charAt(i) != ']')
              throw new IllegalArgumentException("Closing `]` in date picture must be written as `]]`")
          }
          i += 1
        }
        // Quote text part if needed
        if (sb2.length > 0) {
          val s = sb2.toString
          if (s.isAllBlank || NonQuotedSeparators(s)) {
            sb.append(s)
          } else {
            sb.append('"')
            sb.append(s)
            sb.append('"')
          }
        }
        if (i == format.length)
          break()
        i += 1
        if (i < format.length && format.charAt(i) == '[') {
          // Literal '['
          sb.append('[')
          i += 1
        } else {
          // Format the given component
          val close = if (i < format.length) format.indexOf("]", i) else -1
          if (close == -1)
            throw new IllegalArgumentException("Date format contains a `[' with no matching `]`")
          val componentFormat = format.substring(i, close)
          sb.append(
            formatComponent(
              componentFormat // ORBEON: Whitespace.removeAllWhitespace(componentFormat)
            )
          )
          i = close + 1
        }
      }
    }
    sb.toString
  }

  private val componentPattern: Pattern =
    Pattern.compile("([YMDdWwFHhmsfZzPCE])\\s*(.*)")

  private def formatComponent(
    specifier: CharSequence,
  ): CharSequence = {

    val matcher    = componentPattern.matcher(specifier)

    if (! matcher.matches())
      throw new IllegalArgumentException(s"Unrecognized date/time component `[$specifier]`")

    val component = matcher.group(1)
    var format = matcher.group(2)
    if (format == null)
      format = ""
    var defaultFormat = false
    if ("" == format || format.startsWith(",")) {
      defaultFormat = true
      component.head match {
        case 'F'       => format = s"Nn$format"
        case 'P'       => format = s"n$format"
        case 'C' | 'E' => format = s"N$format"
        case 'm' | 's' => format = s"01$format"
        case 'z' | 'Z' =>
        case _         => format = s"1$format"
      }
    }
    component.head match {
      case 'Y' =>
        getNumberModifiers(
          component,
          format,
        ) match {
          case (_, 2, "01", _) => "yy"
          case _               => "yyyy"
        }
      case 'M' =>
          getNumberModifiers(
            component,
            format,
          ) match {
            case (_, 3, "n" | "N"  |"Nn", _) => "mmm"
            case (_, _, "n" | "N"  |"Nn", _) => "mmmm"
            case (_, _, "01", _)             => "mm"
            case _                           => "m"
          }
      case 'D' =>
        getNumberModifiers(
            component,
            format,
          ) match {
            case (_, _, "01", _) => "dd"
            case _               => "d"
          }
      case 'H' | 'h' =>
        // "AM/PM" marker will cause the hour to be displayed in 12-hour format!
        getNumberModifiers(
          component,
          format,
        ) match {
          case (_, _, "01", _) => "hh"
          case _               => "h"
        }
      case 'm' =>
        // Microsoft: "Important: If you use the "m" or "mm" code immediately after the "h" or "hh" code (for hours) or
        // immediately before the "ss" code (for seconds), Excel displays minutes instead of the month"
        // https://support.microsoft.com/en-us/office/number-format-codes-5026bbd6-04bc-48cd-bf33-80f18b4eae68
        getNumberModifiers(
          component,
          format,
        ) match {
          case (_, _, "01", _) => "mm"
          case _               => "m"
        }
      case 's' =>
        getNumberModifiers(
          component,
          format,
        ) match {
          case (_, _, "01", _) => "ss"
          case _               => "s"
        }
      case 'F' =>
        getNumberModifiers(
            component,
            format,
          ) match {
            case (_, 3, "n" | "N"  |"Nn", _) => "ddd"
            case _                           => "dddd"
          }
      case 'P' =>
        "AM/PM"
      case 'd' | 'W' | 'w' | 'f' | 'z' | 'Z' | 'C' | 'E' =>
        "[UNSUPPORTED]"
      case _ =>
        throw new IllegalArgumentException(s"Unknown format-date/time component specifier `${component.charAt(0)}`")
    }
  }

  private val widthPattern : Pattern = Pattern.compile(",(\\*|[0-9]+)(-(\\*|[0-9]+))?")
  private val digitsPattern: Pattern = Pattern.compile("\\p{Nd}+")

 private def getNumberModifiers(
    component    : String,
    format       : String,
  ): (Int, Int, String, Boolean) = {

   val comma = format.lastIndexOf(',')

   val (_format, widths) =
     if (comma >= 0)
       (format.substring(0, comma), format.substring(comma))
     else
       (format, "")

    var (primary, traditional) =
      if (_format.endsWith("t"))
        (_format.substring(0, _format.length - 1), true)
      else if (_format.endsWith("o"))
        (_format.substring(0, _format.length - 1), false)
      else
        (_format, true)

    var min = 1
    var max = java.lang.Integer.MAX_VALUE
    if (digitsPattern.matcher(primary).matches()) {
      val len = getStringLength(primary)
      if (len > 1) {
        min = len
        max = len
      }
    }
    if ("Y" == component) {
      max = 0
      min = max
      if (widths.nonEmpty) {
        max = getWidths(widths)._2
      } else if (digitsPattern.matcher(primary).find()) {
        val uPrimary = primary // ORBEON: UnicodeString.makeUnicodeString(primary)
        for (i <- 0 until uPrimary.length) {
          val c = uPrimary.charAt(i)
          if (c == '#') {
            max += 1
          } else if (c >= '0' && c <= '9') { // ORBEON: || Categories.ESCAPE_d.test(c)
            min += 1
            max += 1
          }
        }
      }
      if (max <= 1)
        max = java.lang.Integer.MAX_VALUE
    }

   if (primary == "I" || primary == "i") {
      min = getWidths(widths)._1
    } else if (widths.nonEmpty) {
      val range = getWidths(widths)
      min = Math.max(min, range._1)
      max =
        if (max == java.lang.Integer.MAX_VALUE)
          range._2
        else
          Math.max(max, range._2)
    }

   if ("P" == component) {
      if (! ("N" == primary || "n" == primary || "Nn" == primary))
        primary = "n"
      if (max == java.lang.Integer.MAX_VALUE)
        max = 4
    }

    (min, max, primary, traditional)
  }

  private def getStringLength(s: CharSequence): Int = {
    var n = 0
    for (i <- 0 until s.length) {
      val c = s.charAt(i).toInt
      if (c < 55296 || c > 56319) // don't count high surrogates, i.e. D800 to DBFF
        n += 1
    }
    n
  }

  private def getWidths(widths: String): (Int, Int) =
    try {
      var min = -1
      var max = -1
      if ("" != widths) {
        val widthMatcher = widthPattern.matcher(widths)
        if (widthMatcher.matches()) {
          val smin = widthMatcher.group(1)
          min =
            if (smin == null || "" == smin || "*" == smin)
              1
            else
              smin.toInt
          val smax = widthMatcher.group(3)

          max =
            if (smax == null || "" == smax || "*" == smax)
              java.lang.Integer.MAX_VALUE
            else
              smax.toInt
          if (min < 1)
            throw new IllegalArgumentException(s"Invalid min value in format picture `$widths`")
          if (max < 1 || max < min)
            throw new IllegalArgumentException(s"Invalid max value in format picture `$widths`")
        } else {
          throw new IllegalArgumentException(s"Unrecognized width specifier in format picture `$widths`")
        }
      }
      if (min > max)
        throw new IllegalArgumentException("Minimum width in date/time picture exceeds maximum width")
      (min, max)
    } catch {
      case _: NumberFormatException =>
        throw new IllegalArgumentException("Invalid integer used as width in date/time picture")
    }
}
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
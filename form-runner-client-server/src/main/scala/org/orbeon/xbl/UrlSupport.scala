/**
  * Copyright (C) 2026 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xbl

import org.orbeon.oxf.util.StringUtils.*


object UrlSupport {

  def isValidWebUrl(value: String): Boolean =
    value.trimAllToOpt.forall(s => normalizeValidWebUrl(s).isDefined)

  def normalizeValidWebUrl(value: String): Option[String] =
    value.trimAllToOpt.filter(isWellFormedWebUrl)

  def isSafeHyperlinkUrl(value: String): Boolean =
    normalizeSafeHyperlinkUrl(value).isDefined

  def normalizeSafeHyperlinkUrl(value: String): Option[String] =
    value.trimAllToOpt.filter(isSafeHyperlinkCandidate)

  private def hasControlChar(s: String): Boolean =
    s.exists(c => c < 0x20 || c == 0x7F)

  private def hasWhitespaceOrControlChar(s: String): Boolean =
    s.exists(c => c <= 0x20 || c == 0x7F || Character.isWhitespace(c))

  private def isWellFormedWebUrl(s: String): Boolean =
    ! hasWhitespaceOrControlChar(s) && {
      val lower = s.toLowerCase
      val afterSchemeOpt =
        if (lower.startsWith("https://"))      Some(s.substring(8))
        else if (lower.startsWith("http://"))  Some(s.substring(7))
        else                                   None

      afterSchemeOpt.exists { afterScheme =>
        val authorityEnd = firstDelimiterIndex(afterScheme)
        val authority    = if (authorityEnd >= 0) afterScheme.substring(0, authorityEnd) else afterScheme

        authority.nonEmpty          &&
        ! authority.contains('@')   &&
        ! authority.contains('\\')  &&
        isValidAuthority(authority)
      }
    }

  private def firstDelimiterIndex(s: String): Int = {
    var i = 0
    val len = s.length
    while (i < len) {
      val c = s.charAt(i)
      if (c == '/' || c == '?' || c == '#')
        return i
      i += 1
    }
    -1
  }

  private def isValidAuthority(authority: String): Boolean =
    if (authority.startsWith("[")) {
      val closeIdx = authority.indexOf(']')
      if (closeIdx < 3) {
        false
      } else {
        val ipv6         = authority.substring(1, closeIdx)
        val afterBracket = authority.substring(closeIdx + 1)
        val portValid    =
          afterBracket.isEmpty || (afterBracket.startsWith(":") && isValidPort(afterBracket.substring(1)))

        isValidIpv6(ipv6) && portValid
      }
    } else if (authority.contains('[') || authority.contains(']')) {
      false
    } else {
      val colonIdx = authority.indexOf(':')
      if (colonIdx == -1) {
        isValidHost(authority)
      } else if (authority.indexOf(':', colonIdx + 1) != -1) {
        false
      } else {
        val host = authority.substring(0, colonIdx)
        val port = authority.substring(colonIdx + 1)
        isValidHost(host) && isValidPort(port)
      }
    }

  private def isValidPort(port: String): Boolean =
    port.nonEmpty &&
    port.length <= 5 &&
    port.forall(c => c >= '0' && c <= '9') && {
      val n = port.toInt
      n >= 1 && n <= 65535
    }

  private def isValidIpv6(ipv6: String): Boolean =
    ipv6.length >= 2         &&
    ipv6.contains(':')       &&
    ! ipv6.contains(":::")   &&
    ! ipv6.startsWith(".")   &&
    ! ipv6.endsWith(".")     &&
    ipv6.forall { c =>
      (c >= '0' && c <= '9') ||
      (c >= 'a' && c <= 'f') ||
      (c >= 'A' && c <= 'F') ||
      c == ':'               ||
      c == '.'
    }

  private def isValidHost(host: String): Boolean =
    host.nonEmpty          &&
    host.length <= 253     &&
    ! host.startsWith(".") &&
    ! host.endsWith(".")   &&
    ! host.contains("..")  && {
      if (host.equalsIgnoreCase("localhost")) {
        true
      } else {
        val labels = host.splitTo[List](".")
        if (host.forall(c => (c >= '0' && c <= '9') || c == '.'))
          labels.length == 4 && labels.forall(isValidIpv4Octet)
        else
          labels.length >= 2 && labels.forall(isValidDomainLabel) && isValidTld(labels.last)
      }
    }

  private def isValidIpv4Octet(s: String): Boolean =
    s.nonEmpty &&
    s.length <= 3 &&
    (s == "0" || ! s.startsWith("0")) &&
    s.forall(c => c >= '0' && c <= '9') &&
    s.toInt <= 255

  private def isValidDomainLabel(label: String): Boolean =
    label.nonEmpty          &&
    label.length <= 63      &&
    ! label.startsWith("-") &&
    ! label.endsWith("-")   &&
    label.forall(isValidDomainLabelChar)

  private def isValidDomainLabelChar(c: Char): Boolean =
    c == '-' || Character.isLetterOrDigit(c) || isCombiningMark(c)

  private def isCombiningMark(c: Char): Boolean = {
    val t = Character.getType(c)
    t == Character.NON_SPACING_MARK ||
    t == Character.COMBINING_SPACING_MARK ||
    t == Character.ENCLOSING_MARK
  }

  private def isValidTld(tld: String): Boolean =
    tld.length >= 2 &&
    ! tld.forall(c => c >= '0' && c <= '9') &&
    (tld.exists(_ > 127) || isAsciiAlpha(tld.charAt(0)))

  private def isAsciiAlpha(c: Char): Boolean =
    (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')

  private def isSafeHyperlinkCandidate(s: String): Boolean =
    ! hasControlChar(s) && {
      val lower = s.toLowerCase
      if (lower.startsWith("https://") || lower.startsWith("http://")) {
        val afterScheme  = s.substring(if (lower.startsWith("https://")) 8 else 7)
        val authorityEnd = firstDelimiterIndex(afterScheme)
        val authority    = if (authorityEnd >= 0) afterScheme.substring(0, authorityEnd) else afterScheme

        ! hasWhitespaceOrControlChar(s) &&
        authority.nonEmpty              &&
        ! authority.contains('@')       &&
        ! authority.contains('\\')
      } else if (lower.startsWith("mailto:")) {
        s.substring(7).trimAllToOpt.isDefined
      } else if (lower.startsWith("tel:")) {
        s.substring(4).trimAllToOpt.isDefined
      } else {
        s == "/" ||
        (s.startsWith("/") && ! s.startsWith("//") && ! s.startsWith("/\\")) ||
        s.startsWith("./")  ||
        s.startsWith("../") ||
        s.startsWith("#")   ||
        s.startsWith("?")
      }
    }
}

object UrlSupportJava {

  //@XPathFunction
  def isValidWebUrl(value: String): Boolean =
    Option(value).forall(UrlSupport.isValidWebUrl)

  //@XPathFunction
  def normalizeValidWebUrl(value: String): String =
    Option(value).flatMap(UrlSupport.normalizeValidWebUrl).getOrElse("")

  //@XPathFunction
  def sanitizeHyperlinkUrl(value: String): String =
    Option(value).flatMap(UrlSupport.normalizeSafeHyperlinkUrl).getOrElse("")
}

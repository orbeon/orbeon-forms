package org.orbeon.oxf.util

import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.orbeon.oxf.xml.*


object HtmlParsing extends HtmlParsingPlatform {

  private val safeElementsElemFilter: String => ElemFilter =
    n => if (MarkupUtils.SafeElements(n)) ElemFilter.Keep else ElemFilter.RemoveWithContent

  def sanitizeHtmlString(
    value          : String,
    extraElemFilter: String => ElemFilter = _ => ElemFilter.Keep
  ): String = {
    val sb = new java.lang.StringBuilder
    sanitizeHtmlStringToReceiver(
      value,
      "",
      extraElemFilter
    )(
      new SimpleHtmlSerializer(sb)
    )
    sb.toString
  }

  def sanitizeHtmlStringToReceiver(
    value          : String,
    xhtmlPrefix    : String,
    extraElemFilter: String => ElemFilter = _ => ElemFilter.Keep
  )(
    receiver       : XMLReceiver
  ): Unit =
    parseHtmlString(
      value,
      new HTMLBodyXMLReceiver(
        new SimpleHtmlFilter(
          n => safeElementsElemFilter(n).combine(extraElemFilter(n)),
          sanitizeAttribute _
        )(
          receiver
        ),
        xhtmlPrefix
      )
    )

  private val ClassAttrRegex     = """^[a-zA-Z0-9_\- ]+$""".r
  private val RoleAttrRegex      = """^[a-z]+$""".r
  private val LanguageTagRegex   = """^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{1,8})*$""".r
  private val DigitsPercentRegex = """^[0-9]+%?$""".r
  private val PositiveIntRegex   = """^[1-9][0-9]*$""".r
  private val SignedIntRegex     = """^-?[0-9]+$""".r
  private val DimensionRegex     = """^[0-9]+(\.[0-9]+)?(px|pt|em|rem|%|vw|vh|ex|ch|cm|mm|in)?$""".r
  private val FontFamilyRegex    = """^[a-zA-Z0-9_\-\s,'"]+$""".r
  private val Base64DataRegex    = """^[A-Za-z0-9+/=]+$""".r

  def sanitizeAttribute(elemName: String, attName: String, attValue: String): Option[String] = {
    val lowerAttName  = attName.toLowerCase
    val lowerElemName = elemName.toLowerCase

    if (lowerAttName.startsWith("on")) {
      None
    } else {
      // 1. Global attributes (allowed on all SafeElements)
      lowerAttName match {
        case "title" =>
          Some(attValue)
        case "dir" =>
          val v = attValue.trimAllToEmpty.toLowerCase
          if (Set("ltr", "rtl", "auto")(v)) Some(v) else None
        case "lang" | "xml:lang" =>
          val v = attValue.trimAllToEmpty
          if (LanguageTagRegex.matches(v)) Some(v) else None
        case "class" =>
          val v = attValue.trimAllToEmpty
          if (ClassAttrRegex.matches(v)) Some(v) else None
        case "style" =>
          sanitizeStyle(attValue)
        case "role" =>
          val v = attValue.trimAllToEmpty.toLowerCase
          if (RoleAttrRegex.matches(v)) Some(v) else None
        case name if name.startsWith("aria-") =>
          val ariaSuffix = name.substring(5)
          if (ariaSuffix.nonEmpty && ariaSuffix.forall(c => (c >= 'a' && c <= 'z') || c == '-')) {
            val v = attValue.trimAllToEmpty
            if (v.forall(c => c >= ' ' && c != 127)) Some(v) else None
          } else {
            None
          }
        case "data-orbeon-keyboard-shortcut" | "data-orbeon-keyboard-shortcut-condition" =>
          val v = attValue.trimAllToEmpty
          if (v.forall(c => c >= ' ' && c != 127)) Some(v) else None
        case _ =>
          // 2. Element-specific attributes
          sanitizeElementAttribute(lowerElemName, lowerAttName, attValue)
      }
    }
  }

  private def sanitizeElementAttribute(elemName: String, attName: String, attValue: String): Option[String] =
    (elemName, attName) match {
      case ("a", "href") =>
        sanitizeUrl(attValue, allowDataImages = false)
      case ("a", "target") =>
        val v = attValue.trimAllToEmpty.toLowerCase
        if (Set("_blank", "_self", "_parent", "_top")(v)) Some(v) else None
      case ("a", "rel") =>
        val v = attValue.trimAllToEmpty
        if (ClassAttrRegex.matches(v)) Some(v) else None
      case ("img", "src") =>
        sanitizeUrl(attValue, allowDataImages = true)
      case ("img", "alt") =>
        Some(attValue)
      case ("img", "width" | "height") =>
        val v = attValue.trimAllToEmpty
        if (DigitsPercentRegex.matches(v)) Some(v) else None
      case ("table", "border" | "cellpadding" | "cellspacing" | "width") =>
        val v = attValue.trimAllToEmpty
        if (DigitsPercentRegex.matches(v)) Some(v) else None
      case ("table", "summary") =>
        Some(attValue)
      case ("td" | "th", "colspan" | "rowspan") =>
        val v = attValue.trimAllToEmpty
        if (PositiveIntRegex.matches(v)) Some(v) else None
      case ("td" | "th", "scope") =>
        val v = attValue.trimAllToEmpty.toLowerCase
        if (Set("row", "col", "rowgroup", "colgroup")(v)) Some(v) else None
      case ("td" | "th", "headers") =>
        val v = attValue.trimAllToEmpty
        if (ClassAttrRegex.matches(v)) Some(v) else None
      case ("td" | "th" | "tr" | "tbody" | "thead" | "tfoot", "align") =>
        val v = attValue.trimAllToEmpty.toLowerCase
        if (Set("left", "center", "right", "justify", "char")(v)) Some(v) else None
      case ("td" | "th" | "tr" | "tbody" | "thead" | "tfoot", "valign") =>
        val v = attValue.trimAllToEmpty.toLowerCase
        if (Set("top", "middle", "bottom", "baseline")(v)) Some(v) else None
      case ("td" | "th", "width" | "height") =>
        val v = attValue.trimAllToEmpty
        if (DigitsPercentRegex.matches(v)) Some(v) else None
      case ("ol", "start") =>
        val v = attValue.trimAllToEmpty
        if (SignedIntRegex.matches(v)) Some(v) else None
      case ("ol", "type") =>
        val v = attValue.trimAllToEmpty
        if (Set("1", "a", "A", "i", "I")(v)) Some(v) else None
      case ("ol", "reversed") =>
        val v = attValue.trimAllToEmpty.toLowerCase
        if (v.isEmpty || v == "reversed") Some("reversed") else None
      case ("li", "value") =>
        val v = attValue.trimAllToEmpty
        if (SignedIntRegex.matches(v)) Some(v) else None
      case ("blockquote", "cite") =>
        sanitizeUrl(attValue, allowDataImages = false)
      case ("font", "color") =>
        val v = attValue.trimAllToEmpty
        if (isSafeColor(v)) Some(v) else None
      case ("font", "face") =>
        val v = attValue.trimAllToEmpty
        if (FontFamilyRegex.matches(v)) Some(v) else None
      case ("font", "size") =>
        val v = attValue.trimAllToEmpty
        if (v.matches("""^[+-]?[0-9]+$""")) Some(v) else None
      case ("p" | "div" | "h1" | "h2" | "h3" | "h4" | "h5" | "h6", "align") =>
        val v = attValue.trimAllToEmpty.toLowerCase
        if (Set("left", "center", "right", "justify")(v)) Some(v) else None
      case _ =>
        None
    }

  def sanitizeUrl(url: String, allowDataImages: Boolean = false): Option[String] = {
    val cleaned = url.filter(c => c > 32 && c != 127)
    if (cleaned.isEmpty) {
      None
    } else {
      val colonIdx    = cleaned.indexOf(':')
      val slashIdx    = cleaned.indexOf('/')
      val questionIdx = cleaned.indexOf('?')
      val hashIdx     = cleaned.indexOf('#')

      val firstSep =
        List(slashIdx, questionIdx, hashIdx)
          .filter(_ >= 0) match {
            case Nil => -1
            case l   => l.min
          }

      val hasScheme = colonIdx > 0 && (firstSep == -1 || colonIdx < firstSep)

      if (hasScheme) {
        val scheme = cleaned.substring(0, colonIdx).toLowerCase
        if (scheme == "http" || scheme == "https" || scheme == "mailto" || scheme == "tel") {
          Some(url.trimAllToEmpty)
        } else if (allowDataImages && scheme == "data") {
          val lowerCleaned = cleaned.toLowerCase
          if (
            lowerCleaned.startsWith("data:image/png;base64,")  ||
            lowerCleaned.startsWith("data:image/jpeg;base64,") ||
            lowerCleaned.startsWith("data:image/jpg;base64,")  ||
            lowerCleaned.startsWith("data:image/gif;base64,")  ||
            lowerCleaned.startsWith("data:image/webp;base64,")
          ) {
            val commaIdx = cleaned.indexOf(',')
            val dataPart = cleaned.substring(commaIdx + 1)
            if (dataPart.nonEmpty && Base64DataRegex.matches(dataPart))
              Some(url.trimAllToEmpty)
            else
              None
          } else {
            None
          }
        } else {
          None
        }
      } else {
        Some(url.trimAllToEmpty)
      }
    }
  }

  def isSafeColor(value: String): Boolean = {
    val v = value.trimAllToEmpty
    if (v.isEmpty || v.contains("/*") || v.contains("\\")) {
      false
    } else if (v.startsWith("#")) {
      val hex = v.substring(1)
      (hex.length == 3 || hex.length == 4 || hex.length == 6 || hex.length == 8) &&
        hex.forall(c => (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))
    } else if (v.startsWith("rgb(") || v.startsWith("rgba(") || v.startsWith("hsl(") || v.startsWith("hsla(")) {
      v.endsWith(")") && {
        val inner = v.substring(v.indexOf('(') + 1, v.length - 1)
        inner.nonEmpty && inner.forall(c =>
          (c >= '0' && c <= '9') || c == '.' || c == ',' || c == '%' || c == '/' || c <= ' '
        )
      }
    } else {
      v.nonEmpty && v.forall(c => (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))
    }
  }

  def isSafeDimension(v: String): Boolean = {
    val trimmed = v.trimAllToEmpty.toLowerCase
    if (trimmed.isEmpty || trimmed.contains("/*") || trimmed.contains("\\"))
      false
    else
      DimensionRegex.matches(trimmed)
  }

  def isSafeFontFamily(v: String): Boolean = {
    val trimmed = v.trimAllToEmpty
    trimmed.nonEmpty &&
      !trimmed.contains("/*") &&
      !trimmed.contains("\\") &&
      FontFamilyRegex.matches(trimmed)
  }

  def isSafeBorder(v: String): Boolean = {
    val trimmed = v.trimAllToEmpty.toLowerCase
    if (trimmed == "none" || trimmed == "0" || trimmed == "inherit") {
      true
    } else {
      val parts = trimmed.splitTo[List](" ").filter(_.nonEmpty)
      val borderStyles = Set("solid", "dashed", "dotted", "double", "groove", "ridge", "inset", "outset", "none", "hidden")
      parts.nonEmpty && parts.forall { part =>
        borderStyles(part) || isSafeDimension(part) || isSafeColor(part)
      }
    }
  }

  def sanitizeStyle(style: String): Option[String] = {
    val declarations = style.splitTo[List](";").map(_.trimAllToEmpty).filter(_.nonEmpty)
    val safeDeclarations = declarations.flatMap { decl =>
      val colonIdx = decl.indexOf(':')
      if (colonIdx > 0) {
        val prop  = decl.substring(0, colonIdx).trimAllToEmpty.toLowerCase
        val value = decl.substring(colonIdx + 1).trimAllToEmpty
        val isSafe = prop match {
          case "color" | "background-color" => isSafeColor(value)
          case "background"                 => isSafeColor(value)
          case "text-align"                 => Set("left", "right", "center", "justify", "start", "end", "inherit")(value.toLowerCase)
          case "text-decoration"            =>
            val tokens = value.toLowerCase.splitTo[List](" ").filter(_.nonEmpty)
            tokens.nonEmpty && tokens.forall(Set("none", "underline", "line-through", "overline", "inherit"))
          case "font-weight"                => Set("normal", "bold", "bolder", "lighter", "100", "200", "300", "400", "500", "600", "700", "800", "900", "inherit")(value.toLowerCase)
          case "font-style"                 => Set("normal", "italic", "oblique", "inherit")(value.toLowerCase)
          case "vertical-align"             => Set("baseline", "sub", "super", "top", "text-top", "middle", "bottom", "text-bottom", "inherit")(value.toLowerCase)
          case "font-size"                  =>
            Set("xx-small", "x-small", "small", "medium", "large", "x-large", "xx-large", "smaller", "larger", "inherit")(value.toLowerCase) || isSafeDimension(value)
          case "line-height"                => value.equalsIgnoreCase("normal") || isSafeDimension(value)
          case "width" | "height"           => Set("auto", "inherit", "initial", "unset")(value.toLowerCase) || isSafeDimension(value)
          case "font-family"                => isSafeFontFamily(value)
          case "border-collapse"            => Set("collapse", "separate", "inherit")(value.toLowerCase)
          case "border"                     => isSafeBorder(value)
          case "margin" | "margin-left" | "margin-right" | "margin-top" | "margin-bottom" |
               "padding" | "padding-left" | "padding-right" | "padding-top" | "padding-bottom" =>
            val parts = value.toLowerCase.splitTo[List](" ").filter(_.nonEmpty)
            parts.nonEmpty && parts.forall(p => p == "auto" || isSafeDimension(p))
          case _                            => false
        }
        if (isSafe) Some(s"$prop: $value") else None
      } else {
        None
      }
    }
    if (safeDeclarations.nonEmpty)
      Some(safeDeclarations.mkString("; "))
    else
      None
  }
}

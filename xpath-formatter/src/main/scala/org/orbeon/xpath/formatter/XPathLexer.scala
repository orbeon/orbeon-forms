package org.orbeon.xpath.formatter

import scala.collection.mutable.ListBuffer

class XPathLexer(val input: String) {

  private var pos = 0
  private val len = input.length

  private val keywords = Set(
    "for", "in", "let", "where", "order", "by", "return",
    "some", "every", "satisfies",
    "if", "then", "else",
    "to", "and", "or",
    "eq", "ne", "lt", "le", "gt", "ge", "is",
    "union", "intersect", "except",
    "instance", "of", "cast", "castable", "treat", "as",
    "div", "idiv", "mod",
    "function", "map", "array",
    "ascending", "descending", "empty", "greatest", "least", "collation"
  )

  def tokenize(includeWhitespaceAndComments: Boolean = false): List[XPathToken] = {
    val tokens = new ListBuffer[XPathToken]
    while (pos < len) {
      val ch = input.charAt(pos)
      if (isWhitespace(ch)) {
        val start = pos
        while (pos < len && isWhitespace(input.charAt(pos))) {
          pos += 1
        }
        if (includeWhitespaceAndComments) {
          tokens += XPathToken.Whitespace(input.substring(start, pos))
        }
      } else if (ch == '(' && pos + 1 < len && input.charAt(pos + 1) == ':') {
        val comment = readComment()
        if (includeWhitespaceAndComments) {
          tokens += XPathToken.Comment(comment)
        }
      } else if (ch == '"' || ch == '\'') {
        tokens += readStringLiteral(ch)
      } else if (ch == '$') {
        pos += 1 // skip $
        skipWhitespaceAndComments()
        val name = readQName()
        tokens += XPathToken.VarRef(name)
      } else if (ch >= '0' && ch <= '9') {
        tokens += readNumericLiteral()
      } else if (ch == '.' && pos + 1 < len && (input.charAt(pos + 1) >= '0' && input.charAt(pos + 1) <= '9')) {
        tokens += readNumericLiteral()
      } else if (ch == ':') {
        if (pos + 1 < len && input.charAt(pos + 1) == ':') {
          pos += 2
          tokens += XPathToken.ColonColon
        } else if (pos + 1 < len && input.charAt(pos + 1) == '=') {
          pos += 2
          tokens += XPathToken.Assign
        } else {
          pos += 1
          tokens += XPathToken.Colon
        }
      } else if (ch == '/') {
        if (pos + 1 < len && input.charAt(pos + 1) == '/') {
          pos += 2
          tokens += XPathToken.SlashSlash
        } else {
          pos += 1
          tokens += XPathToken.Slash
        }
      } else if (ch == '=') {
        if (pos + 1 < len && input.charAt(pos + 1) == '>') {
          pos += 2
          tokens += XPathToken.Arrow
        } else {
          pos += 1
          tokens += XPathToken.Eq
        }
      } else if (ch == '!') {
        if (pos + 1 < len && input.charAt(pos + 1) == '=') {
          pos += 2
          tokens += XPathToken.Ne
        } else {
          pos += 1
          tokens += XPathToken.Bang
        }
      } else if (ch == '<') {
        if (pos + 1 < len && input.charAt(pos + 1) == '=') {
          pos += 2
          tokens += XPathToken.Le
        } else if (pos + 1 < len && input.charAt(pos + 1) == '<') {
          pos += 2
          tokens += XPathToken.Precedes
        } else {
          pos += 1
          tokens += XPathToken.Lt
        }
      } else if (ch == '>') {
        if (pos + 1 < len && input.charAt(pos + 1) == '=') {
          pos += 2
          tokens += XPathToken.Ge
        } else if (pos + 1 < len && input.charAt(pos + 1) == '>') {
          pos += 2
          tokens += XPathToken.Follows
        } else {
          pos += 1
          tokens += XPathToken.Gt
        }
      } else if (ch == '|') {
        if (pos + 1 < len && input.charAt(pos + 1) == '|') {
          pos += 2
          tokens += XPathToken.PipePipe
        } else {
          pos += 1
          tokens += XPathToken.Pipe
        }
      } else if (ch == '.') {
        if (pos + 1 < len && input.charAt(pos + 1) == '.') {
          pos += 2
          tokens += XPathToken.DotDot
        } else {
          pos += 1
          tokens += XPathToken.Dot
        }
      } else if (ch == '(') {
        pos += 1
        tokens += XPathToken.LParen
      } else if (ch == ')') {
        pos += 1
        tokens += XPathToken.RParen
      } else if (ch == '[') {
        pos += 1
        tokens += XPathToken.LBracket
      } else if (ch == ']') {
        pos += 1
        tokens += XPathToken.RBracket
      } else if (ch == '{') {
        pos += 1
        tokens += XPathToken.LBrace
      } else if (ch == '}') {
        pos += 1
        tokens += XPathToken.RBrace
      } else if (ch == ',') {
        pos += 1
        tokens += XPathToken.Comma
      } else if (ch == '@') {
        pos += 1
        tokens += XPathToken.At
      } else if (ch == '?') {
        pos += 1
        tokens += XPathToken.Question
      } else if (ch == '+') {
        pos += 1
        tokens += XPathToken.Plus
      } else if (ch == '-') {
        pos += 1
        tokens += XPathToken.Minus
      } else if (ch == '*') {
        pos += 1
        tokens += XPathToken.Star
      } else if (isNameStartChar(ch) || ch == 'Q') {
        val name = readQName()
        if (keywords.contains(name)) {
          tokens += XPathToken.Keyword(name)
        } else {
          tokens += XPathToken.Name(name)
        }
      } else {
        // Unknown or unexpected character - treat as single character name/symbol
        pos += 1
        tokens += XPathToken.Name(ch.toString)
      }
    }
    tokens += XPathToken.EOF
    tokens.toList
  }

  private def isWhitespace(ch: Char): Boolean =
    ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n'

  private def skipWhitespaceAndComments(): Unit = {
    var continue = true
    while (continue && pos < len) {
      val ch = input.charAt(pos)
      if (isWhitespace(ch)) {
        pos += 1
      } else if (ch == '(' && pos + 1 < len && input.charAt(pos + 1) == ':') {
        readComment()
      } else {
        continue = false
      }
    }
  }

  private def readComment(): String = {
    val start = pos
    pos += 2 // skip (:
    var depth = 1
    while (pos < len && depth > 0) {
      if (input.charAt(pos) == '(' && pos + 1 < len && input.charAt(pos + 1) == ':') {
        depth += 1
        pos += 2
      } else if (input.charAt(pos) == ':' && pos + 1 < len && input.charAt(pos + 1) == ')') {
        depth -= 1
        pos += 2
      } else {
        pos += 1
      }
    }
    input.substring(start, pos)
  }

  private def readStringLiteral(delimiter: Char): XPathToken.StringLiteral = {
    val start = pos
    pos += 1 // skip open quote
    val valueBuf = new java.lang.StringBuilder()
    var closed = false
    while (pos < len && !closed) {
      val ch = input.charAt(pos)
      if (ch == delimiter) {
        if (pos + 1 < len && input.charAt(pos + 1) == delimiter) {
          // escaped quote
          valueBuf.append(delimiter)
          pos += 2
        } else {
          pos += 1
          closed = true
        }
      } else {
        valueBuf.append(ch)
        pos += 1
      }
    }
    val rawText = input.substring(start, pos)
    XPathToken.StringLiteral(rawText, valueBuf.toString, delimiter)
  }

  private def readNumericLiteral(): XPathToken = {
    val start = pos
    var isDecimal = false
    var isDouble = false

    if (pos < len && input.charAt(pos) == '.') {
      isDecimal = true
      pos += 1
      while (pos < len && input.charAt(pos) >= '0' && input.charAt(pos) <= '9') {
        pos += 1
      }
    } else {
      while (pos < len && input.charAt(pos) >= '0' && input.charAt(pos) <= '9') {
        pos += 1
      }
      if (pos < len && input.charAt(pos) == '.') {
        isDecimal = true
        pos += 1
        while (pos < len && input.charAt(pos) >= '0' && input.charAt(pos) <= '9') {
          pos += 1
        }
      }
    }

    if (pos < len && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
      isDouble = true
      pos += 1
      if (pos < len && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
        pos += 1
      }
      while (pos < len && input.charAt(pos) >= '0' && input.charAt(pos) <= '9') {
        pos += 1
      }
    }

    val text = input.substring(start, pos)
    if (isDouble) XPathToken.DoubleLiteral(text)
    else if (isDecimal) XPathToken.DecimalLiteral(text)
    else XPathToken.IntegerLiteral(text)
  }

  private def readQName(): String = {
    val start = pos
    if (pos < len && input.charAt(pos) == 'Q' && pos + 1 < len && input.charAt(pos + 1) == '{') {
      // Braced URI Qualified Name: Q{uri}local
      pos += 2
      while (pos < len && input.charAt(pos) != '}') {
        pos += 1
      }
      if (pos < len) pos += 1 // skip }
      // read NCName or wildcard *
      if (pos < len && input.charAt(pos) == '*') {
        pos += 1
      } else {
        while (pos < len && isNameChar(input.charAt(pos))) {
          pos += 1
        }
      }
      return input.substring(start, pos)
    }

    // Regular NCName or prefix:local or prefix:* or *:local
    if (pos < len && input.charAt(pos) == '*') {
      pos += 1
      if (pos < len && input.charAt(pos) == ':') {
        pos += 1
        while (pos < len && isNameChar(input.charAt(pos))) {
          pos += 1
        }
      }
      return input.substring(start, pos)
    }

    while (pos < len && isNameChar(input.charAt(pos))) {
      pos += 1
    }

    if (pos < len && input.charAt(pos) == ':' && pos + 1 < len && input.charAt(pos + 1) != ':') {
      // Could be prefix:local or prefix:*
      val colonPos = pos
      pos += 1
      if (pos < len && input.charAt(pos) == '*') {
        pos += 1
      } else if (pos < len && isNameStartChar(input.charAt(pos))) {
        while (pos < len && isNameChar(input.charAt(pos))) {
          pos += 1
        }
      } else {
        // Not a QName, revert
        pos = colonPos
      }
    }

    input.substring(start, pos)
  }

  private def isNameStartChar(ch: Char): Boolean =
    Character.isLetter(ch) || ch == '_'

  private def isNameChar(ch: Char): Boolean =
    Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '.'
}

object XPathLexer {
  def tokenize(input: String): List[XPathToken] =
    new XPathLexer(input).tokenize()
}

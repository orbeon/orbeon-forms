package org.orbeon.xpath.formatter

/**
 * Style for formatting conditional expressions (`if (...) then ... else ...`).
 */
sealed trait IfStyle

object IfStyle {
  /**
   * Multi-line if expression:
   * {{{
   * if ($condition) then
   *   $thenExpr
   * else
   *   $elseExpr
   * }}}
   */
  case object MultiLine extends IfStyle

  /**
   * Clause-per-line if expression:
   * {{{
   * if ($condition)
   * then $thenExpr
   * else $elseExpr
   * }}}
   */
  case object ClausePerLine extends IfStyle

  /**
   * Always format on a single line if possible:
   * {{{
   * if ($condition) then $thenExpr else $elseExpr
   * }}}
   */
  case object Inline extends IfStyle
}

/**
 * Preference for string literal quotes.
 */
sealed trait QuotePreference

object QuotePreference {
  /** Preserve the quote style originally used in the expression (' or "). */
  case object Preserve extends QuotePreference
  /** Prefer double quotes ("string"). */
  case object PreferDouble extends QuotePreference
  /** Prefer single quotes ('string'). */
  case object PreferSingle extends QuotePreference
}

/**
 * Options controlling the formatting of XPath 3.1 expressions.
 *
 * @param indentSize                     Number of spaces per indentation level (default: 2).
 * @param useTabs                        Whether to indent with tabs instead of spaces (default: false).
 * @param newline                        Line separator string to use (default: "\n").
 * @param maxLineLength                  Target maximum line length before wrapping (default: 80).
 *
 * @param flworClauseOnNewline           Put each FLWOR clause (`for`, `let`, `where`, `order by`, `return`)
 *                                       on a new line (default: true).
 * @param flworVariablePerLine           In multi-variable `for` or `let` clauses (e.g. `let $a := 1, $b := 2`),
 *                                       put each variable binding on its own line (default: true).
 * @param flworIndentReturn              Whether the `return` clause should be indented relative to the `for`/`let`
 *                                       clauses (default: false).
 * @param flworInlineThreshold           Maximum length for a short FLWOR expression to be kept inline on a single line (default: 40).
 *
 * @param ifStyle                        Formatting style for `if (...) then ... else ...` (default: [[IfStyle.MultiLine]]).
 * @param ifInlineThreshold              Maximum length for a short `if (...) then ... else ...` to be kept inline (default: 40).
 *
 * @param stepPerLine                    Whether to place each path step (`/step` or `//step`) on a new indented line
 *                                       when the path is multi-line (default: false).
 * @param arrowOnNewline                 Put each arrow step (`=> func()`) on a new indented line (default: true).
 * @param mapOperatorOnNewline           Put simple map operator (`! step`) on a new indented line when wrapping (default: false).
 *
 * @param functionArgsOnNewlineThreshold Maximum number of function arguments before formatting each argument on its
 *                                       own line, or -1 to only wrap when exceeding `maxLineLength` (default: 3).
 * @param spaceAfterComma                Whether to include a space after commas in argument lists, sequences,
 *                                       and variable lists (default: true).
 * @param spaceAroundOperators           Whether to put spaces around binary operators (e.g. `+`, `-`, `*`, `div`,
 *                                       `eq`, `=`, `and`, `or`, `to`, `||`) (default: true).
 * @param breakBeforeBinaryOperator      When wrapping binary expressions across lines, place the operator at the start
 *                                       of the new line rather than at the end of the previous line (default: true).
 *
 * @param mapEntriesOnNewline            In `map { ... }` constructors, format each key-value entry on its own line
 *                                       if there are multiple entries or when exceeding line length (default: true).
 * @param arrayEntriesOnNewline          In array constructors `[ ... ]`, format each item on its own line when multi-line
 *                                       (default: false).
 * @param spaceAroundMapColon            Whether to include a space before the colon in map entries (e.g. `'key' : 'val'`
 *                                       vs `'key': 'val'`) (default: false).
 *
 * @param quantifiedVariablePerLine      In `some`/`every` expressions, put each variable binding on its own line (default: true).
 *
 * @param quotePreference                Quote preference for string literals (default: [[QuotePreference.Preserve]]).
 * @param preserveComments               Whether to preserve XPath comments `(: ... :)` (default: true).
 */
case class XPathFormatterOptions(
  indentSize: Int                     = 2,
  useTabs: Boolean                    = false,
  newline: String                     = "\n",
  maxLineLength: Int                  = 80,

  flworClauseOnNewline: Boolean       = true,
  flworVariablePerLine: Boolean       = true,
  flworIndentReturn: Boolean          = false,
  flworInlineThreshold: Int           = 40,

  ifStyle: IfStyle                    = IfStyle.MultiLine,
  ifInlineThreshold: Int              = 40,

  stepPerLine: Boolean                = false,
  arrowOnNewline: Boolean             = true,
  mapOperatorOnNewline: Boolean       = false,

  functionArgsOnNewlineThreshold: Int = 3,
  spaceAfterComma: Boolean            = true,
  spaceAroundOperators: Boolean       = true,
  breakBeforeBinaryOperator: Boolean  = true,

  mapEntriesOnNewline: Boolean        = true,
  arrayEntriesOnNewline: Boolean      = false,
  spaceAroundMapColon: Boolean        = false,

  quantifiedVariablePerLine: Boolean  = true,

  quotePreference: QuotePreference    = QuotePreference.Preserve,
  preserveComments: Boolean           = true
) {
  def indentString(level: Int): String = {
    if (useTabs) "\t" * level
    else " " * (level * indentSize)
  }
}

object XPathFormatterOptions {
  val default: XPathFormatterOptions = XPathFormatterOptions()
}

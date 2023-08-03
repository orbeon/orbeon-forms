package org.orbeon.oxf.util

import org.parboiled2._

import java.util.concurrent.ConcurrentHashMap
import scala.util.{Failure, Success}


object MessageFormatCache {

  import MessageFormatter._

  private val cache = new ConcurrentHashMap[String, Message]

  def apply(format: String): Message =
    cache.computeIfAbsent(
      format,
      MessageFormatter.parse
    )
}

// TODO: move when done
// TODO: also what about syntax with variable names, like `$iteration`? Support natively?
class MessageFormatter(val input: ParserInput) extends Parser {

  import MessageFormatter._

  def MessageRule: Rule1[Message] = rule {
    zeroOrMore(FormatRule) ~> Message ~ EOI
  }

  def FormatRule: Rule1[Format] = rule {
    LiteralRule | StringFormatRule | NumberFormatRule | ChoiceFormatRule
  }

  def LiteralRule: Rule1[LiteralFormat] = rule {
    oneOrMore(EscapedQuoteRule | QuotedStringRule | capture(noneOf("{"))) ~> (stringsToLiteralFormat _)
  }

  def QuotedStringRule: Rule1[String] = rule {
    "'" ~ capture(noneOf("'")) ~ zeroOrMore(EscapedQuoteRule | capture(noneOf("'"))) ~> ((s1: String, s2: Seq[String]) => s1 + s2.mkString) ~ "'"
  }

  def EscapedQuoteRule: Rule1[String] = rule {
    '\'' ~ capture('\'')
  }

  def StringFormatRule: Rule1[StringFormat] = rule {
    "{" ~ OptWhiteSpace ~ capture(oneOrMore(CharPredicate.Digit)) ~ OptWhiteSpace ~ "}" ~> ((s: String) => StringFormat(s.toInt))
  }

  def NumberFormatRule: Rule1[NumberFormat] = rule {
    "{" ~ OptWhiteSpace ~ capture(oneOrMore(CharPredicate.Digit)) ~ OptWhiteSpace ~ "," ~ OptWhiteSpace ~
      "number" ~ OptWhiteSpace ~ optional("," ~ OptWhiteSpace ~ "integer" ~ OptWhiteSpace) ~ "}" ~> ((s: String) => NumberFormat(s.toInt))
  }

  def ChoiceFormatRule: Rule1[ChoiceFormat] = rule {
    "{" ~ OptWhiteSpace ~ capture(oneOrMore(CharPredicate.Digit)) ~ OptWhiteSpace ~ "," ~ OptWhiteSpace ~
      "choice" ~ OptWhiteSpace ~ "," ~ OptWhiteSpace ~ zeroOrMore(ChoiceRule).separatedBy("|" ~ OptWhiteSpace) ~ "}" ~>
        ((pos: String,  choices: Seq[(Choice, Message)]) => ChoiceFormat(pos.toInt, choices))
  }

  def ChoiceRule: Rule1[(Choice, Message)] = rule {
    capture(oneOrMore(CharPredicate.Digit)) ~ capture("#" | "<") ~ NestedMessageRule ~> (
      (pos: String, comp: String, message: Message) => {
        val value = pos.toInt
        val choice =
          if (comp == "#")
            ExactChoice(value)
          else
            ComparisonChoice(value)
        (choice, message)
      }
    )
  }

  def NestedMessageRule: Rule1[Message] = rule {
    zeroOrMore(NestedFormatRule) ~> Message
  }

  def NestedFormatRule: Rule1[Format] = rule {
    QuotedNestedChoiceFormatRule | NestedLiteralRule | StringFormatRule | NumberFormatRule
  }

  def NestedLiteralRule: Rule1[LiteralFormat] = rule {
    oneOrMore(EscapedQuoteRule | (! QuotedNestedChoiceFormatRule ~ QuotedStringRule) | capture(noneOf("{}|'"))) ~> (stringsToLiteralFormat _)
  }

  def QuotedNestedChoiceFormatRule: Rule1[ChoiceFormat] = rule {
    "'" ~ OptWhiteSpace ~ ChoiceFormatRule ~ "'"
  }

  def OptWhiteSpace: Rule0 = rule { zeroOrMore(anyOf(" \n\r\t\f")) }

  def stringsToLiteralFormat(s: Seq[String]): LiteralFormat = LiteralFormat(s.mkString)
}

object MessageFormatter {

  sealed trait Choice
  case   class ExactChoice(value: Int)      extends Choice
  case   class ComparisonChoice(value: Int) extends Choice

  sealed trait Format
  case   class LiteralFormat(v: String)                                  extends Format
  case   class StringFormat(index: Int)                                  extends Format
  case   class NumberFormat(index: Int)                                  extends Format // only support integer
  case   class ChoiceFormat(index: Int, choices: Seq[(Choice, Message)]) extends Format

  case class Message(formats: Seq[Format])

  def parse(input: String): Message = {
    val parser = new MessageFormatter(input)
    val parsingResult = parser.MessageRule.run()
    parsingResult match {
      case Success(astRoot)        => astRoot
      case Failure(pe: ParseError) =>
        println(parser.formatError(pe))
        throw pe
      case Failure(t) =>
        throw t
    }
  }

  // Supported value types for now: `Int`, `Long`, `String`
  def format(m: Message, values: IndexedSeq[Any]): String = {

    val bits =
      m.formats map {
        case LiteralFormat(v: String)                                  => v
        case StringFormat(index: Int)                                  => values(index).toString
        case NumberFormat(index: Int)                                  => values(index).toString
        case ChoiceFormat(index: Int, choices: Seq[(Choice, Message)]) =>

          val choiceValue = values(index) match {
            case v: Long => v.toInt
            case v: Int  => v
            case v       => throw new IllegalArgumentException(v.toString)
          }

          def fromExact =
            choices collectFirst {
              case (ExactChoice(`choiceValue`), message) => format(message, values)
            }

          def fromComparison =
            choices collectFirst {
              case (ComparisonChoice(value), message) if value < choiceValue => format(message, values)
            }

          fromExact orElse fromComparison getOrElse (throw new IllegalArgumentException(choiceValue.toString)) // xxx message
      }

    bits.mkString
  }
}

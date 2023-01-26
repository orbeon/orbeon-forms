package org.orbeon.saxon.function

import org.orbeon.oxf.util.{MessageFormatCache, MessageFormatter}
import java.util.regex.Matcher

object ProcessTemplateSupport {

  // Ideally should be the same as a non-qualified XPath variable name
  private val MatchTemplateKey = """\{\s*\$([A-Za-z0-9\-_]+)\s*""".r

  // Template processing
  //
  // - See https://github.com/orbeon/orbeon-forms/issues/3078
  // - For now, we only support template values like `{$foo}`.
  // - Whitespace is allowed between the brackets: `{ $foo }`.
  //
  // In the future, we would like to extend the syntax with full nested XPath expressions, which would mean
  // compiling the template to an XPath value template.

  def processTemplateWithNames(
    templateWithNames : String,
    javaNamedParams   : List[(String, Any)]
  ): String = {

    // TODO
    def formatValue(v: Any) = v match {
      case null       => ""
      case v: Byte    => v
      case v: Short   => v
      case v: Int     => v
      case v: Long    => v
      case v: Float   => v
      case v: Double  => v
      case v: Boolean => v
      case other      => other.toString
    }

    val nameToPos = javaNamedParams.iterator.map(_._1).zipWithIndex.toMap

    val templateWithPositions =
      MatchTemplateKey.replaceAllIn(templateWithNames, m => {
        Matcher.quoteReplacement("{" + nameToPos(m.group(1)).toString)
      }).replace("'", "''")

    MessageFormatter.format(
      MessageFormatCache(templateWithPositions),
      javaNamedParams.map(v => formatValue(v._2)).toVector
    )
  }

  def renameParamInTemplate(
    templateWithNames : String,
    originalName      : String,
    newName           : String
  ): String =
    MatchTemplateKey.replaceAllIn(templateWithNames, m => {
      val matchedName       = m.group(1)
      val foundOriginalName = matchedName == originalName
      val replacement       = if (foundOriginalName) "{$" + newName else m.group(0)
      Matcher.quoteReplacement(replacement)
    })

}
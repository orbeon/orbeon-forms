package org.orbeon.saxon.function

import java.util.regex.Matcher

object ProcessTemplateSupport {

  // Ideally should be the same as an unqualified XPath variable name
  private val MatchTemplateKey = """\{\s*\$([A-Za-z0-9\-_]+)\s*}""".r

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

    // Format a value based on its type
    def formatValue(v: Any): String = v match {
      case null       => ""
      case v: Byte    => v.toString
      case v: Short   => v.toString
      case v: Int     => v.toString
      case v: Long    => v.toString
      case v: Float   => v.toString
      case v: Double  => v.toString
      case v: Boolean => v.toString
      case other      => other.toString
    }

    // Extract all template variables and their positions
    val templateVars = MatchTemplateKey.findAllMatchIn(templateWithNames).map { m =>
      (m.start, m.end, m.group(1))
    }.toList.sortBy(_._1)

    // If no template variables found, return the original string
    if (templateVars.isEmpty) return templateWithNames

    // Build a new string with template variables replaced by their values
    val sb = new StringBuilder
    var lastPos = 0

    for ((start, end, varName) <- templateVars) {
      // Add text before this variable
      if (start > lastPos) {
        sb.append(templateWithNames.substring(lastPos, start))
      }

      // Replace the variable with its value if found in params
      val valueOpt = javaNamedParams.find(_._1 == varName).map(_._2)
      valueOpt match {
        case Some(value) => sb.append(formatValue(value))
        case None        => sb.append(templateWithNames.substring(start, end)) // Keep original if not found
      }

      lastPos = end
    }

    // Add any remaining text
    if (lastPos < templateWithNames.length) {
      sb.append(templateWithNames.substring(lastPos))
    }

    sb.toString
  }

  def renameParamInTemplate(
    templateWithNames : String,
    originalName      : String,
    newName           : String
  ): String =
    MatchTemplateKey.replaceAllIn(templateWithNames, m => {
      val matchedName       = m.group(1)
      val foundOriginalName = matchedName == originalName
      val replacement       = if (foundOriginalName) "{$" + newName + "}" else m.group(0)
      Matcher.quoteReplacement(replacement)
    })

}
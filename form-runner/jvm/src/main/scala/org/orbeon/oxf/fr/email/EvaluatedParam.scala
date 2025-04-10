/**
 *  Copyright (C) 2025 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr.email

import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.saxon.function.ProcessTemplateSupport


sealed trait EvaluatedParam {
  def value(html: Boolean): String
}

object EvaluatedParam {

  // "All Control Values" parameters can generate HTML or not
  case class AllControls(controlsToExclude: List[String]) extends EvaluatedParam {

    override def value(html: Boolean): String =
      FormRunnerMetadata.findAllControlsWithValuesExcludingNamedControls(html, controlsToExclude)
  }

  // Once evaluated, the parameter value is static (i.e. it doesn't depend on whether we're generating HTML or not)
  case class Static(staticValue: String) extends EvaluatedParam {

    override def value(html: Boolean): String = staticValue
  }

  def apply(staticValue: String): EvaluatedParam = Static(staticValue)

  def apply(
    template: EmailMetadata.Template,
    param   : EmailMetadata.Param)(implicit
    ctx     : InDocFormRunnerDocContext
  ): EvaluatedParam =
    param match {
      case EmailMetadata.Param.ExpressionParam(_, expression) =>
        EvaluatedParam(FormRunner.evaluatedExpressionAsStrings(expression).headOption.getOrElse(""))

      case EmailMetadata.Param.ControlValueParam(_, controlName) =>
        // Value is not formatted at all. Would need to be formatted properly like we should do with #3627.
        // TODO: add way to configure values separator
        // TODO: add support for section templates in Email Settings UI (Control Value dropdown)
        EvaluatedParam(FormRunner.controlValueAsStrings(controlName, sectionOpt = None).mkString(", "))

      case EmailMetadata.Param.AllControlValuesParam(_) =>
        // Control values from section templates will be included
        // TODO: add support for section templates in Email Settings UI (Exclude from All Control Values dropdown)
        AllControls(controlsToExclude = template.controlsToExcludeFromAllControlValues.map(_.controlName))

      case param: EmailMetadata.TokenLinkParam =>
        EvaluatedParam(buildLinkBackToFormRunner(linkType = param.entryName, includeToken = param.token))

      case param: EmailMetadata.LinkParam =>
        EvaluatedParam(buildLinkBackToFormRunner(linkType = param.entryName, includeToken = false))
    }
}

case class EvaluatedParams(parameters: List[(String, EvaluatedParam)]) {
  def processedTemplate(html: Boolean, contentTemplate: String): String =
    ProcessTemplateSupport.processTemplateWithNames(
      contentTemplate,
      parameters.map { case (name, value) => name -> value.value(html)}
    )
}

object EvaluatedParams {
  def fromEmailMetadata(
    template  : EmailMetadata.Template,
    parameters: List[EmailMetadata.Param])(implicit
    ctx       : InDocFormRunnerDocContext
): EvaluatedParams =
    EvaluatedParams(
      parameters = parameters.map(param => (param.name, EvaluatedParam(template, param)))
    )
}

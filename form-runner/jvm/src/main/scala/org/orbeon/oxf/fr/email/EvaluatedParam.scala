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
import org.orbeon.oxf.fr.email.EmailMetadata.TemplateValue
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.function.ProcessTemplateSupport
import org.orbeon.scaxon.SimplePath.NodeInfoOps
import org.orbeon.xforms.{Constants, XFormsId}
import org.orbeon.xml.NamespaceMapping


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
    param                                : EmailMetadata.Param,
    controlsToExcludeFromAllControlValues: List[TemplateValue.Control]
  )(implicit
    ctx                                  : FormRunnerDocContext,
    indentedLogger                       : IndentedLogger
  ): EvaluatedParam =
    param match {
      case EmailMetadata.Param.ExpressionParam(_, expression) =>
        implicit val namespaceMapping: NamespaceMapping = NamespaceMapping(ctx.modelElem.namespaceMappings.toMap)
        EvaluatedParam(FormRunner.evaluatedExpressionAsStrings(expression).headOption.getOrElse(""))

      case EmailMetadata.Param.ControlValueParam(_, controlName) =>
        // Value is not formatted at all. Would need to be formatted properly like we should do with #3627.
        // TODO: add way to configure values separator
        // TODO: add support for section templates in Email Settings UI (Control Value dropdown)
        EvaluatedParam(FormRunner.controlValueAsStrings(controlName, sectionOpt = None).mkString(", "))

      case EmailMetadata.Param.AllControlValuesParam(_) =>
        // Control values from section templates will be included
        // TODO: add support for section templates in Email Settings UI (Exclude from All Control Values dropdown)
        AllControls(controlsToExclude = controlsToExcludeFromAllControlValues.map(_.controlName))

      case param: EmailMetadata.TokenLinkParam =>
        EvaluatedParam(buildLinkBackToFormRunner(linkType = param.entryName, includeToken = param.token))

      case param: EmailMetadata.LinkParam =>
        EvaluatedParam(buildLinkBackToFormRunner(linkType = param.entryName, includeToken = false))
    }

  def usingContainingDocument(
    param                                : EmailMetadata.Param,
    controlsToExcludeFromAllControlValues: List[TemplateValue.Control]
  )(implicit
    xfcd                                 : XFormsContainingDocument,
    xfc                                  : XFormsFunction.Context,
    indentedLogger                       : IndentedLogger
  ): EvaluatedParam =
    param match {
      case EmailMetadata.Param.ExpressionParam(_, expression) =>

        implicit val namespaceMapping: NamespaceMapping =
          xfcd
            .searchContainedModels(Names.FormModel, contextItemOpt = None)
            .flatMap(_.modelOpt)
            .map(_.staticModel.namespaceMapping)
            .getOrElse(throw new IllegalStateException)
        EvaluatedParam(FormRunner.evaluatedExpressionAsStrings(expression).headOption.getOrElse(""))

      case EmailMetadata.Param.ControlValueParam(_, controlName) =>
        // Value is not formatted at all. Would need to be formatted properly like we should do with #3627.
        // TODO: add way to configure values separator
        // TODO: add support for section templates in Email Settings UI (Control Value dropdown)

        EvaluatedParam(
          FormRunner.resolveTargetRelativeToActionSourceOpt(
            actionSourceAbsoluteId  = XFormsId.effectiveIdToAbsoluteId(Constants.DocumentId),
            targetControlName       = controlName,
            followIndexes           = false,
            libraryOrSectionNameOpt = None
          )
          .map(_.map(_.getStringValue).mkString(", "))
          .getOrElse("")
        )

      case EmailMetadata.Param.AllControlValuesParam(_) =>
        // Control values from section templates will be included
        // TODO: add support for section templates in Email Settings UI (Exclude from All Control Values dropdown)
        AllControls(controlsToExclude = controlsToExcludeFromAllControlValues.map(_.controlName))

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
    parameters                           : List[EmailMetadata.Param],
    controlsToExcludeFromAllControlValues: List[TemplateValue.Control],
  )(implicit
    ctx                                  : FormRunnerDocContext,
    indentedLogger                       : IndentedLogger
): EvaluatedParams =
    EvaluatedParams(
      parameters = parameters.map(param => (param.name, EvaluatedParam(param, controlsToExcludeFromAllControlValues)))
    )

  def fromEmailMetadataUsingContainingDocument(
    parameters                           : List[EmailMetadata.Param],
    controlsToExcludeFromAllControlValues: List[TemplateValue.Control],
  )(implicit
    xfcd                                 : XFormsContainingDocument,
    xfc                                  : XFormsFunction.Context,
    indentedLogger                       : IndentedLogger
): EvaluatedParams =
    EvaluatedParams(
      parameters = parameters.map(param => (param.name, EvaluatedParam.usingContainingDocument(param, controlsToExcludeFromAllControlValues)))
    )
}

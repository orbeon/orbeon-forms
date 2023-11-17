package org.orbeon.oxf.fr.email

import org.orbeon.oxf.fr.email.EmailMetadata.Legacy.{FormField, FormFieldRole}
import org.orbeon.oxf.fr.email.EmailMetadata.{HeaderName, Template, TemplateValue}
import org.orbeon.oxf.util.CoreUtils.{BooleanOps, PipeOps}
import org.orbeon.saxon.function.ProcessTemplateSupport

import scala.collection.compat.immutable.LazyList

/**
 * Converting case classes for the legacy formats into the current format
 */
object EmailMetadataConversion {

  def convertLegacy2021Metadata(
    metadata : EmailMetadata.Legacy.Metadata2021
  ): EmailMetadata.Metadata =
    convertFormatFromLegacy2021(renameParams(metadata, paramsToRename(metadata)))

  def convertLegacy2022Metadata(
    metadata: EmailMetadata.Legacy.Metadata2022
  ): EmailMetadata.Metadata =
    convertFormatFromLegacy2022(metadata)

  private case class ParamToRename(originalName: String, newSubjectName: String, newBodyName: String)
  private def paramsToRename(metadata: EmailMetadata.Legacy.Metadata2021): List[ParamToRename] = {
    var allParamNames: List[String] = (
      metadata.subject.toList.flatMap(_.params) ++
      metadata.body.toList.flatMap(_.params)
    ).map(_.name)
    val conflictingNames = allParamNames.filter(name => allParamNames.count(_ == name) > 1).distinct
    conflictingNames.map { originalName =>
      def newName() =
        LazyList.from(1).map(originalName + "-" + _).collectFirst {
          case candidateName if !allParamNames.contains(candidateName) => candidateName
        }.get.kestrel(newName => allParamNames = newName :: allParamNames)
      ParamToRename(originalName, newName(), newName())
    }
  }

  private def renameParams(
    metadata       : EmailMetadata.Legacy.Metadata2021,
    paramsToRename : List[ParamToRename]
  ): EmailMetadata.Legacy.Metadata2021 =
    paramsToRename.foldLeft(metadata) { (metadata, paramToRename) =>
      def renamePart(
        part         : EmailMetadata.Legacy.Part2021,
        originalName : String,
        newName      : String
      ): EmailMetadata.Legacy.Part2021 = {
        EmailMetadata.Legacy.Part2021(
          templates = part.templates.map { template =>
            template.copy(text = {
              ProcessTemplateSupport.renameParamInTemplate(template.text, originalName, newName)
            })
          },
          params    = part.params.map { param =>
            if (param.name == originalName) {
              param match {
                case p @ EmailMetadata.Param.ControlValueParam     (_, _) => p.copy(name = newName)
                case p @ EmailMetadata.Param.ExpressionParam       (_, _) => p.copy(name = newName)
                case p @ EmailMetadata.Param.AllControlValuesParam (_)    => p.copy(name = newName)
                case p @ EmailMetadata.Param.LinkToEditPageParam   (_)    => p.copy(name = newName)
                case p @ EmailMetadata.Param.LinkToViewPageParam   (_)    => p.copy(name = newName)
                case p @ EmailMetadata.Param.LinkToNewPageParam    (_)    => p.copy(name = newName)
                case p @ EmailMetadata.Param.LinkToSummaryPageParam(_)    => p.copy(name = newName)
                case p @ EmailMetadata.Param.LinkToHomePageParam   (_)    => p.copy(name = newName)
                case p @ EmailMetadata.Param.LinkToFormsPageParam  (_)    => p.copy(name = newName)
                case p @ EmailMetadata.Param.LinkToAdminPageParam  (_)    => p.copy(name = newName)
                case p @ EmailMetadata.Param.LinkToPdfParam        (_)    => p.copy(name = newName)
              }
            } else
              param
          }
        )
      }
      metadata.copy(
        subject = metadata.subject.map(renamePart(_, paramToRename.originalName, paramToRename.newSubjectName)),
        body    = metadata.body   .map(renamePart(_, paramToRename.originalName, paramToRename.newBodyName))
      )
    }

  private def headersFromFormFields(formFields: List[FormField]): List[(HeaderName, TemplateValue)] =
    formFields.flatMap { formField =>
      val headerNameOpt = formField.role match {
        case FormFieldRole.Recipient            => Some(HeaderName.To)
        case FormFieldRole.CC                   => Some(HeaderName.CC)
        case FormFieldRole.BCC                  => Some(HeaderName.BCC)
        case FormFieldRole.Sender               => Some(HeaderName.From)
        case FormFieldRole.ReplyTo              => Some(HeaderName.ReplyTo)
        case FormFieldRole.Attachment           => None
        case FormFieldRole.ExcludeFromAllFields => None
      }

      headerNameOpt.map(_ -> TemplateValue.Control(formField.controlName, formField.sectionOpt))
    }

  private def controlsWithFormFieldRole(
    formFields   : List[FormField],
    formFieldRole: FormFieldRole
  ): List[TemplateValue.Control] =
    formFields.collect {
      case FormField(`formFieldRole`, sectionOpt, controlName) =>
        TemplateValue.Control(controlName, sectionOpt)
    }

  private def convertFormatFromLegacy2021(
    metadata : EmailMetadata.Legacy.Metadata2021,
  ): EmailMetadata.Metadata = {
    val langs = metadata.subject match {
      case Some(part) => part.templates.map(_.lang).map(Some(_))
      case None       => List(None)
    }

    val attachControls = controlsWithFormFieldRole(metadata.formFields, FormFieldRole.Attachment)

    EmailMetadata.Metadata(
      templates = langs.map { langOpt =>
        val subject = langOpt.flatMap { lang =>
          val legacySubjectOpt = metadata.subject.toList.flatMap(_.templates).find(_.lang == lang)
          legacySubjectOpt.map(legacySubject => EmailMetadata.Part(isHTML = legacySubject.isHTML, text = legacySubject.text))
        }

        val body = langOpt.flatMap { lang =>
          val legacyBodyOpt = metadata.body.toList.flatMap(_.templates).find(_.lang == lang)
          legacyBodyOpt.map(legacyBody => EmailMetadata.Part(isHTML = legacyBody.isHTML, text = legacyBody.text))
        }

        EmailMetadata.Template(
          name                        = "default",
          lang                        = langOpt,
          headers                     = headersFromFormFields(metadata.formFields),
          subject                     = subject,
          body                        = body,
          attachPdf                   = false,
          attachFiles                 = None,
          attachControls              = attachControls,
          excludeFromAllControlValues = controlsWithFormFieldRole(metadata.formFields, FormFieldRole.ExcludeFromAllFields)
        )
      },
      params =
        metadata.subject.toList.flatMap(_.params) ++
        metadata.body   .toList.flatMap(_.params)
    )
  }

  private def convertFormatFromLegacy2022(
    metadata : EmailMetadata.Legacy.Metadata2022,
  ): EmailMetadata.Metadata =
    EmailMetadata.Metadata(
      templates = metadata.templates.map { template2022 =>
        val attachControls = controlsWithFormFieldRole(template2022.formFields, FormFieldRole.Attachment)

        Template(
          name                        = template2022.name,
          lang                        = template2022.lang,
          headers                     = headersFromFormFields(template2022.formFields),
          subject                     = template2022.subject,
          body                        = template2022.body,
          attachPdf                   = template2022.attachPdf,
          attachFiles                 = template2022.attachFiles,
          attachControls              = attachControls,
          excludeFromAllControlValues = controlsWithFormFieldRole(template2022.formFields, FormFieldRole.ExcludeFromAllFields),
        )
      },
      params = metadata.params
    )
}

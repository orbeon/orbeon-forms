package org.orbeon.oxf.fr.email

import org.orbeon.oxf.util.CoreUtils.PipeOps
import org.orbeon.saxon.function.ProcessTemplateSupport

import scala.collection.compat.immutable.LazyList

/**
 * Converting case classes for the 2021 legacy format into the current format
 */
object EmailMetadataConversion {

  def convertLegacy2021Metadata(
    metadata : EmailMetadata.Legacy2021.Metadata
  ): EmailMetadata.Metadata =
    convertFormat(renameParams(metadata, paramsToRename(metadata)))

  private case class ParamToRename(originalName: String, newSubjectName: String, newBodyName: String)
  private def paramsToRename(metadata: EmailMetadata.Legacy2021.Metadata): List[ParamToRename] = {
    var allParamNames: List[String] = (metadata.subject.params ++ metadata.body.params).map(_.name)
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
    metadata       : EmailMetadata.Legacy2021.Metadata,
    paramsToRename : List[ParamToRename]
  ): EmailMetadata.Legacy2021.Metadata =
    paramsToRename.foldLeft(metadata) { (metadata, paramToRename) =>
      def renamePart(
        part         : EmailMetadata.Legacy2021.Part,
        originalName : String,
        newName      : String
      ): EmailMetadata.Legacy2021.Part = {
        EmailMetadata.Legacy2021.Part(
          templates = part.templates.map { template =>
            template.copy(text = {
              val replacement = List(originalName -> ("{$" + newName + "}"))
              ProcessTemplateSupport.processTemplateWithNames(template.text, replacement)
            })
          },
          params    = part.params.map { param =>
            if (param.name == originalName)
              param match {
                case p @ EmailMetadata.ControlValueParam(_, _) => p.copy(name = newName)
                case p @ EmailMetadata.ExpressionParam  (_, _) => p.copy(name = newName)
              }
            else
              param
          }
        )
      }
      metadata.copy(
        subject = renamePart(metadata.subject, paramToRename.originalName, paramToRename.newSubjectName),
        body    = renamePart(metadata.body   , paramToRename.originalName, paramToRename.newBodyName)
      )
    }

  private def convertFormat(
    metadata : EmailMetadata.Legacy2021.Metadata,
  ): EmailMetadata.Metadata = {
    val langs = metadata.subject.templates.map(_.lang)
    EmailMetadata.Metadata(
      templates = langs.map { lang =>
        EmailMetadata.Template(
          name    = "default",
          lang    = Some(lang),
          subject = {
            val legacySubject = metadata.subject.templates.filter(_.lang == lang).head
            EmailMetadata.Part(isHTML = legacySubject.isHTML, text = legacySubject.text)
          },
          body = {
            val legacyBody = metadata.body.templates.filter(_.lang == lang).head
            EmailMetadata.Part(isHTML = legacyBody.isHTML, text = legacyBody.text)
          },
          formFields = metadata.formFields
        )
      },
      params = metadata.subject.params ++ metadata.body.params
    )
  }
}

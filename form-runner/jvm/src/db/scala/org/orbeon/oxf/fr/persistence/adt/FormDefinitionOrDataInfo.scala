package org.orbeon.oxf.fr.persistence.adt

import org.orbeon.oxf.fr.FormRunnerPersistence.DataXml
import org.orbeon.oxf.fr.{AppForm, FormOrData}

import java.time.Instant

object FormDefinitionOrDataInfo {
  sealed trait FormDefinitionOrDataInfo {
    def appForm        : AppForm
    def version        : Int
    def formOrData     : FormOrData
    def draft          : Boolean
    def documentIdOpt  : Option[String]
    def lastModifiedOpt: Option[Instant]
  }

  case class FormDefinitionInfo(
    appForm: AppForm,
    version: Int
  ) extends FormDefinitionOrDataInfo {
    override val formOrData     : FormOrData      = FormOrData.Form
    override val draft          : Boolean         = false
    override val documentIdOpt  : Option[String]  = None
    override val lastModifiedOpt: Option[Instant] = None
  }

  case class FormDataInfo(
    appForm        : AppForm,
    version        : Int,
    draft          : Boolean,
    documentId     : String,
    lastModifiedOpt: Option[Instant],
    filenameOpt    : Option[String]
  ) extends FormDefinitionOrDataInfo {
    override val formOrData   : FormOrData     = FormOrData.Data
    override val documentIdOpt: Option[String] = Some(documentId)
    val attachment            : Boolean        = filenameOpt.isDefined
  }

  def apply(path: String): FormDefinitionOrDataInfo = path match {
    case FormPath(app, form, version) =>
      FormDefinitionInfo(
        appForm = AppForm(app, form),
        version = version.toInt
      )

    case DataPath(app, form, version, dataOrDraft, documentId, lastModified, filename) =>
      FormDataInfo(
        appForm         = AppForm(app, form),
        version         = version.toInt,
        draft           = dataOrDraft == "draft",
        documentId      = documentId,
        lastModifiedOpt = Some(lastModified).filter(_ != "latest").map(Instant.parse(_)),
        filenameOpt     = Some(filename).filter(_ != DataXml)
      )
  }

  private val FormPath = """([^/]+)/([^/]+)/([^/]+)/form/latest/form.xhtml""".r
  private val DataPath = """([^/]+)/([^/]+)/([^/]+)/((?:data|draft))/([^/]+)/([^/]+)/([^/]+)""".r
}

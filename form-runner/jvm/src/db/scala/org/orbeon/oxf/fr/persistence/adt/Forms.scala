package org.orbeon.oxf.fr.persistence.adt

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fr.AppForm
import org.orbeon.oxf.fr.FormRunnerParams.AppFormVersion
import org.orbeon.oxf.fr.FormRunnerPersistence.DataXml
import org.orbeon.oxf.fr.persistence.adt.FormDefinitionOrDataInfo.{FormDataInfo, FormDefinitionInfo}
import org.orbeon.oxf.fr.persistence.api.PersistenceApi.headerFromRFC1123OrIso
import org.orbeon.oxf.fr.persistence.http.HttpCall
import org.orbeon.oxf.fr.persistence.relational.Version
import org.orbeon.oxf.fr.persistence.relational.Version.Specific
import org.orbeon.oxf.fr.persistence.test.TestForm
import org.orbeon.oxf.http.{Headers, StatusCode}
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, LoggerFactory}

import java.time.Instant

case class Forms(forms: Seq[FormWithData]) {
  // Check that form titles are unique, so that we can identify form definitions by title
  assert(allFormTitles.distinct.size == allFormTitles.size)

  // Check that form values are unique, so that we can identify form data by value
  assert(allFormValues.distinct.size == allFormValues.size)

  def save(): Forms = {
    val updatedForms = forms.map(_.save())

    // Return forms with last modified times
    copy(forms = updatedForms)
  }

  def allFormTitles: Seq[String] =
    forms.map(_.title)

  def allFormValues: Seq[String] =
    for {
      form        <- forms
      dataHistory <- form.dataHistoryEntries
      data        <- dataHistory.dataEntries
    } yield data.value

  def formDefinitionInfos(
    formTitlesOpt: Option[Set[String]] = None,
    persistedOpt : Option[Boolean]     = None
   ): Seq[FormDefinitionInfo] =
    for {
      form <- forms
      if formTitlesOpt.forall(_.contains(form.title))
      if persistedOpt. forall(_ == form.formDefinitionPersisted())
    } yield FormDefinitionInfo(
      appForm = form.appForm,
      version = form.appFormVersion._2
    )

  def formDataInfos(
    formValuesOpt  : Option[Set[String]] = None,
    persistedOpt   : Option[Boolean]     = None)(implicit
    externalContext: ExternalContext
  ): Seq[FormDataInfo] =
    for {
      form        <- forms
      dataHistory <- form.dataHistoryEntries
      data        <- dataHistory.dataEntries
      if formValuesOpt.forall(_.contains(data.value))
      if persistedOpt. forall(_ == data.formDataPersisted(form, dataHistory))
    } yield FormDataInfo(
      appForm         = form.appForm,
      version         = form.appFormVersion._2,
      draft           = data.draft,
      documentId      = dataHistory.documentId,
      lastModifiedOpt = data.lastModified,
      filenameOpt     = data.filenameOpt
    )
}
object Forms {
  implicit val logger                   = new IndentedLogger(LoggerFactory.createLogger(classOf[FormWithData]), true)
  implicit val coreCrossPlatformSupport = CoreCrossPlatformSupport

  def urlPersisted(
    url            : String,
    version        : Version)(implicit
    externalContext: ExternalContext
  ): Boolean =
    HttpCall.get(url, version)._1 ==  StatusCode.Ok
}

case class FormWithData(
  appFormVersion    : AppFormVersion,
  title             : String,
  dataHistoryEntries: Seq[DataHistory])(implicit
  externalContext   : ExternalContext
) {
  import Forms._

  val appForm : AppForm  = appFormVersion._1
  val version : Version  = Specific(appFormVersion._2)
  val testForm: TestForm = TestForm(appForm, title, controls = Seq(TestForm.Control("control label")))

  def save(): FormWithData = {
    testForm.putFormDefinition(version)

    // Return entries with last modified times
    copy(dataHistoryEntries = dataHistoryEntries.map(_.save(this)))
  }

  def formDefinitionPersisted(): Boolean =
    Forms.urlPersisted(testForm.formDefinitionURL, version)

  def formDataInfoForValue(value: String): Option[FormDataInfo] =
    (for {
      dataHistory <- dataHistoryEntries
      data        <- dataHistory.dataEntries
      if data.value == value
    } yield FormDataInfo(
      appForm         = appForm,
      version         = appFormVersion._2,
      draft           = data.draft,
      documentId      = dataHistory.documentId,
      lastModifiedOpt = data.lastModified,
      filenameOpt     = data.filenameOpt
    )).headOption
}

case class DataHistory(
  documentId     : String,
  dataEntries    : Seq[Data])(implicit
  externalContext: ExternalContext
) {
  assert(dataEntries.nonEmpty)

  import Forms._

  def save(formWithData: FormWithData): DataHistory = {
    val updatedDataEntries = dataEntries.zipWithIndex map { case (data, index) =>
      val expectedCode = if (index == 0) StatusCode.Created else StatusCode.NoContent

      val httpResponse = HttpCall.put(
        url     = data.url(formWithData, this),
        version = formWithData.version,
        stage   = None,
        body    = HttpCall.XML(formWithData.testForm.formData(Seq(data.value)))
      )
      assert(httpResponse.statusCode == expectedCode)

      // Retrieve last modified time from headers
      val lastModified = headerFromRFC1123OrIso(httpResponse.headers, Headers.OrbeonLastModified, Headers.LastModified)
      data.copy(lastModified = lastModified)
    }

    // Return data entries with last modified times
    copy(dataEntries = updatedDataEntries)
  }
}

case class Data(
  value       : String,
  draft       : Boolean         = false,
  lastModified: Option[Instant] = None,
  filenameOpt : Option[String]  = None
) {
  def formDataPersisted(
    formWithData   : FormWithData,
    dataHistory    : DataHistory)(implicit
    externalContext: ExternalContext
  ): Boolean =
    Forms.urlPersisted(url(formWithData, dataHistory), formWithData.version)

  def url(formWithData: FormWithData, dataHistory: DataHistory): String =
    s"crud/${formWithData.appForm.app}/${formWithData.appForm.form}/data/${dataHistory.documentId}/$DataXml" +
      lastModified.map(lmt => s"?last-modified-time=$lmt").getOrElse("")
}

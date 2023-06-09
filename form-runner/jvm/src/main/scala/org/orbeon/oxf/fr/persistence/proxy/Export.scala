package org.orbeon.oxf.fr.persistence.proxy

import cats.implicits.catsSyntaxOptionId
import org.orbeon.io.IOUtils
import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Response}
import org.orbeon.oxf.fr.FormRunnerParams.AppFormVersion
import org.orbeon.oxf.fr._
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.persistence.api.PersistenceApi._
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils
import org.orbeon.oxf.http.{Headers, HttpMethod, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.util.Logging.{debug, warn}
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{ConnectionResult, CoreCrossPlatformSupport, CoreCrossPlatformSupportTrait, DateUtils, IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.scaxon.SimplePath._

import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.zip.{ZipEntry, ZipOutputStream}
import javax.xml.transform.stream.StreamResult
import scala.collection.mutable
import scala.util.{Failure, Success, Try}


object Export {

  private val Logger = LoggerFactory.createLogger("org.orbeon.fr.export")
  private implicit val indentedLogger: IndentedLogger = new IndentedLogger(Logger)

  val FormVersionParam = "form-version"
  val ContentParam     = "content"
  val DateRangeGeParam = "last-modified-time-ge"
  val DateRangeLeParam = "last-modified-time-le"

  val AllowedContentMap: Map[String, FormOrData] = Map("form-definition" -> FormOrData.Form, "form-data" -> FormOrData.Data) // "attachments"?

  val AllowedContentTokens = AllowedContentMap.keySet

  sealed trait VersionParam
  object VersionParam {
    case object Latest                extends VersionParam
    case object All                   extends VersionParam
    case class Specific(version: Int) extends VersionParam

    def fromStringOptOrThrow(versionOpt: Option[String]): Option[VersionParam] = versionOpt match {
      case None           => None
      case Some("latest") => Latest.some
      case Some("all")    => All.some
      case someSpecific   => Specific(RelationalUtils.parsePositiveIntParamOrThrow(someSpecific, 1)).some
    }
  }

  def processExport(
    request   : Request,
    response  : Response,
    app       : Option[String],
    form      : Option[String],
    documentId: Option[String],
  ): Unit = {

    implicit val ec                       = CoreCrossPlatformSupport.externalContext
    implicit val coreCrossPlatformSupport = CoreCrossPlatformSupport

    val versionParamOpt =
      VersionParam.fromStringOptOrThrow(request.getFirstParamAsString(FormVersionParam))

    val formOrDataSet: Set[FormOrData] =
      request.getFirstParamAsString(ContentParam) map { content =>
        val contentTokens = content.splitTo[Set](",")
        if (! contentTokens.forall(AllowedContentTokens.contains))
          throw HttpStatusCodeException(StatusCode.BadRequest)
        contentTokens.map(AllowedContentMap)
      } getOrElse
        FormOrData.valuesSet

    (app, form, documentId) match {
      case (someApp, None, None) =>
        // Export everything or everything under the app, with control over the versions

        val versionParam = versionParamOpt.getOrElse(VersionParam.Latest)

        if (! (versionParam == VersionParam.All || versionParam == VersionParam.Latest))
          throw HttpStatusCodeException(StatusCode.BadRequest)

        exportMultipleForms(
          request       = request,
          response      = response,
          appOpt        = someApp,
          formOpt       = None,
          versionParam  = versionParam,
          formOrDataSet = formOrDataSet
        )

      case (someApp, someForm, None) =>
        // Export a specific app/form, with control over the versions

        exportMultipleForms(
          request       = request,
          response      = response,
          appOpt        = someApp,
          formOpt       = someForm,
          versionParam  = versionParamOpt.getOrElse(VersionParam.Latest),
          formOrDataSet = formOrDataSet
        )

      case (Some(app), Some(form), Some(documentId)) =>
        // Export a specific app/form/documentId, with control over the versions

        val formVersion = {

          lazy val formVersionFromPersistence =
            PersistenceApi.readDocumentFormVersion(
              app,
              form,
              documentId,
              isDraft = false
            )

          versionParamOpt match {
            case Some(VersionParam.Latest | VersionParam.All) =>
              throw HttpStatusCodeException(StatusCode.BadRequest)
            case Some(VersionParam.Specific(version)) if ! formVersionFromPersistence.contains(version) =>
              throw HttpStatusCodeException(StatusCode.BadRequest)
            case Some(VersionParam.Specific(version)) =>
              version
            case None =>
              formVersionFromPersistence.getOrElse(throw HttpStatusCodeException(StatusCode.NotFound))
          }
        }

        IOUtils.useAndClose(new ZipOutputStream(response.getOutputStream)) { zos =>
          exportUsingDocumentId(
            zos,
            (AppForm(app, form), formVersion),
            documentId,
            formOrDataSet,
          )
        }

      case _ =>
        throw HttpStatusCodeException(StatusCode.BadRequest)
    }
  }

  def readPersistenceContentToZip(
    zos                     : ZipOutputStream,
    formVersionOpt          : Option[Int],
    fromPath                : String,
    zipPath                 : String,
    debugAction             : String)(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit = {
    debug(s"storing `$fromPath` as `$zipPath`")
    ConnectionResult.tryWithSuccessConnection(
      PersistenceApi.connectPersistence(
        method         = HttpMethod.GET,
        path           = fromPath,
        formVersionOpt = formVersionOpt
      ),
      closeOnSuccess = true
    ) { is =>
      val entry = new ZipEntry(zipPath)
      zos.putNextEntry(entry)
      IOUtils.copyStreamAndClose(is, zos, doCloseOut = false)
    } match {
      case Success(_) => debug(s"success retrieving attachment when $debugAction form `${fromPath}`")
      case Failure(_) => warn (s"failure retrieving attachment when $debugAction form `${fromPath}`")
    }
  }

  private def exportMultipleForms(
    request      : Request,
    response     : Response,
    appOpt       : Option[String],
    formOpt      : Option[String],
    versionParam : VersionParam,
    formOrDataSet: Set[FormOrData]
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit =
    IOUtils.useAndClose(new ZipOutputStream(response.getOutputStream)) { zos =>
      getFormMetadata(
        appOpt,
        formOpt,
        request.getHeaderValuesMap,
        versionParam == VersionParam.All
      ) foreach { case MetadataDetails(appName, formName, formVersion, lastModifiedTime) =>

        versionParam match {
          case VersionParam.Specific(v) if v != formVersion =>
            throw HttpStatusCodeException(StatusCode.BadRequest)
          case _ =>
        }

        val appFormVersion = (AppForm(appName, formName), formVersion)

        if (formOrDataSet(FormOrData.Form))
          processFormDefinition(zos, appFormVersion).get // can throw

        if (formOrDataSet(FormOrData.Data))
          search(appName, formName, formVersion) foreach {
            case DataDetails(_, _, documentId, false) =>
              processFormData(
                zos,
                appFormVersion,
                documentId
              ).get // can throw
            case _ =>
          }
      }
    }

  private def exportUsingDocumentId(
    zos           : ZipOutputStream,
    appFormVersion: AppFormVersion,
    documentId    : String,
    formOrDataSet : Set[FormOrData]
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit = {

    if (formOrDataSet(FormOrData.Form))
      processFormDefinition(zos, appFormVersion).get // can throw

    if (formOrDataSet(FormOrData.Data))
      processFormData(zos, appFormVersion, documentId).get // can throw
  }

  private def processFormDefinition(
    zos           : ZipOutputStream,
    appFormVersion: AppFormVersion
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Try[Unit] = {

    debug(s"exporting form definition `$appFormVersion`")

    readPublishedFormDefinition(appFormVersion._1.app, appFormVersion._1.form, FormDefinitionVersion.Specific(appFormVersion._2)) map {
      case (headers, formDefinition) =>

        writeXmlDocumentToZip(
          zos                     = zos,
          path                    = makeZipPath(appFormVersion, None, "form.xhtml"),
          documentNode            = formDefinition,
          creationTimeOpt         = Headers.firstItemIgnoreCase(headers, Headers.Created).map(DateUtils.parseRFC1123).map(Instant.ofEpochMilli),
          lastModificationTimeOpt = Headers.firstItemIgnoreCase(headers, Headers.LastModified).map(DateUtils.parseRFC1123).map(Instant.ofEpochMilli)
        )

        processAttachments(
          zos,
          formDefinition,
          appFormVersion,
          None
        )
    }
  }

  private def processFormData(
    zos           : ZipOutputStream,
    appFormVersion: AppFormVersion,
    documentId    : String
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Try[Unit] =
    readFormData(appFormVersion, documentId) map {
      case (headers, formData) =>

        // 4. Read and write form data and attachments
        writeXmlDocumentToZip(
          zos                     = zos,
          path                    = makeZipPath(appFormVersion, documentId.some, "data.xml"),
          documentNode            = formData,
          creationTimeOpt         = Headers.firstItemIgnoreCase(headers, Headers.Created).map(DateUtils.parseRFC1123).map(Instant.ofEpochMilli),
          lastModificationTimeOpt = Headers.firstItemIgnoreCase(headers, Headers.LastModified).map(DateUtils.parseRFC1123).map(Instant.ofEpochMilli)
        )

        processAttachments(
          zos,
          formData,
          appFormVersion,
          documentId.some
        )
    }

  private def processAttachments(
    zos                     : ZipOutputStream,
    xmlData                 : DocumentNodeInfoType,
    appFormVersion          : AppFormVersion,
    documentIdOpt           : Option[String]
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit = {

    val attachmentPaths = mutable.Set[String]()

    FormRunner.collectAttachments(
      data             = xmlData,
      attachmentMatch  = FormRunner.AttachmentMatch.BasePaths(includes = List(makeFromPath(appFormVersion, documentIdOpt)), excludes = Nil)
    ) foreach { case FormRunner.AttachmentWithHolder(fromPath, holder) =>

      if (! attachmentPaths(fromPath)) { // don't attempt to put a path twice (some forms have this)

        attachmentPaths += fromPath

        // NOTE: The returned attachments will match the `fromBasePaths` we are passing.

        val mediatype =
          holder.attValueOpt("mediatype").flatMap(_.trimAllToOpt).getOrElse("application/octet-stream")

//              val attachmentFilenameMetadata =
//                holder.attValueOpt("filename").flatMap(_.trimAllToOpt)

        val attachmentFilename =
          fromPath.splitTo[List]("/").lastOption.getOrElse(throw new IllegalStateException(fromPath))

        val attachmentZipPath =
          makeZipPath(appFormVersion, documentIdOpt, attachmentFilename)

        readPersistenceContentToZip(
          zos            = zos,
          formVersionOpt = appFormVersion._2.some,
          fromPath       = fromPath,
          zipPath        = attachmentZipPath,
          debugAction    = "exporting"
        )
      }
    }
  }

  private def writeXmlDocumentToZip(
    zos                    : ZipOutputStream,
    path                   : String,
    documentNode           : DocumentNodeInfoType,
    creationTimeOpt        : Option[Instant],
    lastModificationTimeOpt: Option[Instant]
  ): Unit = {
    debug(s"storing XML ${documentNode.rootElement.getSystemId} as `$path`")
    val entry = new ZipEntry(path)
    creationTimeOpt.foreach(creationTime => entry.setCreationTime(FileTime.from(creationTime)))
    lastModificationTimeOpt.foreach(lastModificationTime => entry.setLastModifiedTime(FileTime.from(lastModificationTime)))
    zos.putNextEntry(entry)
    TransformerUtils.getXMLIdentityTransformer.transform(documentNode, new StreamResult(zos))
  }

  private def makeFromPath(
    appFormVersion: AppFormVersion,
    documentIdOpt : Option[String],
  ): String =
    documentIdOpt match {
      case Some(documentId) =>
        FormRunner.createFormDataBasePath(appFormVersion._1.app, appFormVersion._1.form, isDraft = false, documentId)
      case None =>
        FormRunner.createFormDefinitionBasePath(appFormVersion._1.app, appFormVersion._1.form)
    }

  private def makeZipPath(
    appFormVersion: AppFormVersion,
    documentIdOpt : Option[String],
    filename      : String
  ): String = {
    documentIdOpt match {
      case Some(_) =>
        FormRunner.createFormDataBasePathNoPrefix(appFormVersion._1, appFormVersion._2.some, isDraft = false, documentIdOpt)
      case None =>
        FormRunner.createFormDefinitionBasePathNoPrefix(appFormVersion._1, appFormVersion._2.some)
    }
  } :: filename :: Nil mkString "/"
}
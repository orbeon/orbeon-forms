package org.orbeon.oxf.fr.persistence.proxy

import cats.data.NonEmptyList
import cats.implicits.catsSyntaxOptionId
import org.orbeon.io.IOUtils
import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Response}
import org.orbeon.oxf.fr.FormRunnerParams.AppFormVersion
import org.orbeon.oxf.fr._
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.persistence.api.PersistenceApi._
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils
import org.orbeon.oxf.http._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{ConnectionResult, ContentTypes, CoreCrossPlatformSupport, CoreCrossPlatformSupportTrait, IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.scaxon.SimplePath._

import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.zip.{ZipEntry, ZipOutputStream}
import java.{util => ju}
import javax.xml.transform.stream.StreamResult
import scala.collection.mutable
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


object Export {

  private val Logger = LoggerFactory.createLogger("org.orbeon.fr.export")
  private implicit val indentedLogger: IndentedLogger = new IndentedLogger(Logger)

  val FormVersionParam    = "form-version"
  val ContentParam        = "content"
  val MatchParam          = "match"
  val DataHistoryParam    = "include-data-revision-history"
  val DateRangeGeParam    = "last-modified-time-ge"
  val DateRangeLeParam    = "last-modified-time-le"

  val ContentParamSeparator         = ","
  val AppFormVersionParamSeparator  = "/"

  val AllowedContentMap: Map[String, FormOrData] = Map("form-definition" -> FormOrData.Form, "form-data" -> FormOrData.Data) // "attachments"?

  val AllowedContentTokens = AllowedContentMap.keySet

  sealed trait VersionParam

  private object VersionParam {
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

  case class WithOptional[T, U](_1: T, _2: Option[U])

  case class MatchSpec(
    appFormDoc     : String WithOptional (String WithOptional String),
    versionParamOpt: Option[VersionParam]
  )

  private object MatchSpec {

    // ?include=acme/order/1&include=orbeon/contact/2&include=orbeon/order/3&include=orbeon/order//123456
    def fromParams(params: collection.Map[String, Array[AnyRef]]): Option[NonEmptyList[MatchSpec]] = {
      NonEmptyList.fromList(params.get(MatchParam).toList.flatMap(_.toList.collect {
        case s: String =>
          s.split(AppFormVersionParamSeparator) match { // use Java `split()` as we want blank parts to be kept
            case Array(NonAllBlank(app)) =>
              MatchSpec(WithOptional(app, None), None)
            case Array(NonAllBlank(app), NonAllBlank(form)) =>
              MatchSpec(WithOptional(app, WithOptional(form, None).some), None)
            case Array(NonAllBlank(app), NonAllBlank(form), version) =>
              MatchSpec(WithOptional(app, WithOptional(form, None).some), VersionParam.fromStringOptOrThrow(version.trimAllToOpt))
            case Array(NonAllBlank(app), NonAllBlank(form), version, NonAllBlank(documentId)) =>
              MatchSpec(WithOptional(app, WithOptional(form, documentId.some).some), VersionParam.fromStringOptOrThrow(version.trimAllToOpt))
            case _ =>
              throw HttpStatusCodeException(StatusCode.BadRequest)
          }
      }))
    }
  }

  // Entry point for the export
  def processExport(
    request      : Request,
    response     : Response,
    appOpt       : Option[String],
    formOpt      : Option[String],
    documentIdOpt: Option[String],
  ): Unit = {

    implicit val coreCrossPlatformSupport: CoreCrossPlatformSupportTrait = CoreCrossPlatformSupport

    val appFormVersionMatchOpt = {

      val versionParamOpt =
        VersionParam.fromStringOptOrThrow(request.getFirstParamAsString(FormVersionParam))

      val mainMatchSpecOpt =
        appOpt.map { app =>
          MatchSpec(
            appFormDoc      = WithOptional(app, formOpt.map(form => WithOptional(form, documentIdOpt))),
            versionParamOpt = versionParamOpt
          )
        }

      val secondaryMatchSpec =
        MatchSpec.fromParams(request.parameters)

      secondaryMatchSpec.map(_.concat(mainMatchSpecOpt.toList)).orElse(mainMatchSpecOpt.map(NonEmptyList.one))
    }

    val formOrDataSet =
      request.getFirstParamAsString(ContentParam) map { content =>
        val contentTokens = content.splitTo[Set](sep = ContentParamSeparator)
        if (! contentTokens.forall(AllowedContentTokens.contains))
          throw HttpStatusCodeException(StatusCode.BadRequest)
        contentTokens.map(AllowedContentMap)
      } getOrElse
        FormOrData.valuesSet

    processExportImpl(
      request.getHeaderValuesMap,
      response,
      appFormVersionMatchOpt,
      formOrDataSet,
      includeDataHistory = request.getFirstParamAsString(DataHistoryParam).contains(true.toString)
    )
  }

  def readPersistenceContentToZip(
    zos                     : ZipOutputStream,
    formVersionOpt          : Option[Int],
    fromPath                : String,
    zipPath                 : String,
    debugAction             : String
  )(implicit
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
      case Success(_) => debug(s"success retrieving attachment when $debugAction form `$fromPath`")
      case Failure(_) => error(s"failure retrieving attachment when $debugAction form `$fromPath`")
    }
  }

  private def processExportImpl(
    incomingHeaders   : ju.Map[String, Array[String]],
    response          : Response,
    matchesOpt        : Option[NonEmptyList[MatchSpec]],
    formOrDataSet     : Set[FormOrData],
    includeDataHistory: Boolean
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit =
    IOUtils.useAndClose(new ZipOutputStream(response.getOutputStream)) { zos =>
      response.setContentType(ContentTypes.ZipContentType)
      matchesOpt match {
        case None =>
          exportWithMatch(zos, incomingHeaders, None, formOrDataSet, includeDataHistory)
        case Some(filters) =>
          filters.iterator.foreach { filter =>
            exportWithMatch(zos, incomingHeaders, filter.some, formOrDataSet, includeDataHistory)
          }
      }
    }

  private def exportWithMatch(
    zos               : ZipOutputStream,
    incomingHeaders   : ju.Map[String, Array[String]],
    matchOpt          : Option[MatchSpec],
    formOrDataSet     : Set[FormOrData],
    includeDataHistory: Boolean
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit =
    matchOpt match {
      case None | Some(MatchSpec(WithOptional(_, None), _)) | Some(MatchSpec(WithOptional(_, Some(WithOptional(_, None))), _)) =>
        // Export a specific app/form

        val appOpt          = matchOpt.map(_.appFormDoc._1)
        val formOpt         = matchOpt.flatMap(_.appFormDoc._2.map(_._1))
        val versionParamOpt = matchOpt.flatMap(_.versionParamOpt)

        val versionParam = versionParamOpt.getOrElse(VersionParam.Latest)

        formOpt match {
          case None if ! (versionParam == VersionParam.All || versionParam == VersionParam.Latest) =>
            throw HttpStatusCodeException(StatusCode.BadRequest)
          case _ =>
        }

        exportMultipleForms(
          zos                = zos,
          incomingHeaders    = incomingHeaders,
          appOpt             = appOpt,
          formOpt            = formOpt,
          versionParam       = versionParam,
          formOrDataSet      = formOrDataSet,
          includeDataHistory = includeDataHistory
        )

      case Some(MatchSpec(WithOptional(app, Some(WithOptional(form, Some(documentId)))), versionParamOpt)) =>
        // Export a specific app/form/documentId

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

        exportUsingDocumentId(
          zos                = zos,
          appFormVersion     = (AppForm(app, form), formVersion),
          documentId         = documentId,
          formOrDataSet      = formOrDataSet,
          includeDataHistory = includeDataHistory
        )
    }

  private def exportMultipleForms(
    zos               : ZipOutputStream,
    incomingHeaders   : ju.Map[String, Array[String]],
    appOpt            : Option[String],
    formOpt           : Option[String],
    versionParam      : VersionParam,
    formOrDataSet     : Set[FormOrData],
    includeDataHistory: Boolean
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit =
    getFormMetadata(
      appOpt,
      formOpt,
      incomingHeaders,
      versionParam == VersionParam.All
    ) foreach { case MetadataDetails(appName, formName, formVersion, _) =>

      versionParam match {
        case VersionParam.Specific(v) if v != formVersion =>
          throw HttpStatusCodeException(StatusCode.BadRequest)
        case _ =>
      }

      val appFormVersion = (AppForm(appName, formName), formVersion)

      if (formOrDataSet(FormOrData.Form))
        processFormDefinition(zos, appFormVersion).get // can throw

      if (formOrDataSet(FormOrData.Data))
        search(appFormVersion) foreach {
          case DataDetails(_, _, documentId, false) =>
            processFormDataMaybeWithHistory(
              zos,
              appFormVersion,
              documentId,
              includeDataHistory
            )
          case _ =>
        }
    }

  private def exportUsingDocumentId(
    zos               : ZipOutputStream,
    appFormVersion    : AppFormVersion,
    documentId        : String,
    formOrDataSet     : Set[FormOrData],
    includeDataHistory: Boolean
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit = {

    if (formOrDataSet(FormOrData.Form))
      processFormDefinition(zos, appFormVersion).get // can throw

    if (formOrDataSet(FormOrData.Data))
      processFormDataMaybeWithHistory(zos, appFormVersion, documentId, includeDataHistory)
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
          zipPath                    = makeZipPath(appFormVersion, None, None, "form.xhtml"),
          documentNode            = formDefinition,
          createdTimeOpt         = DateHeaders.firstDateHeaderIgnoreCase(headers, Headers.Created).map(Instant.ofEpochMilli),
          modifiedTimeOpt = DateHeaders.firstDateHeaderIgnoreCase(headers, Headers.LastModified).map(Instant.ofEpochMilli)
        )

        processAttachments(
          zos,
          formDefinition,
          appFormVersion,
          None,
          None
        )
    }
  }

  private def processFormDataMaybeWithHistory(
    zos               : ZipOutputStream,
    appFormVersion    : AppFormVersion,
    documentId        : String,
    includeDataHistory: Boolean
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit =
    if (includeDataHistory)
      PersistenceApi.dataHistory(appFormVersion._1, documentId).foreach { dataHistoryDetails =>
        processFormData(
          zos,
          appFormVersion,
          documentId,
          dataHistoryDetails.modifiedTime.some
        ).get // can throw
      }
    else
      processFormData(
        zos,
        appFormVersion,
        documentId,
        None
      ).get // can throw

  private def processFormData(
    zos            : ZipOutputStream,
    appFormVersion : AppFormVersion,
    documentId     : String,
    modifiedTimeOpt: Option[Instant]
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Try[Unit] =
    readFormData(appFormVersion, documentId, modifiedTimeOpt) map {
      case (headers, formData) =>

        writeXmlDocumentToZip(
          zos                     = zos,
          zipPath                    = makeZipPath(appFormVersion, documentId.some, modifiedTimeOpt, "data.xml"),
          documentNode            = formData,
          createdTimeOpt         = DateHeaders.firstDateHeaderIgnoreCase(headers, Headers.Created).map(Instant.ofEpochMilli),
          modifiedTimeOpt = DateHeaders.firstDateHeaderIgnoreCase(headers, Headers.LastModified).map(Instant.ofEpochMilli)
        )

        processAttachments(
          zos,
          formData,
          appFormVersion,
          documentId.some,
          modifiedTimeOpt
        )
    }

  private def processAttachments(
    zos                : ZipOutputStream,
    xmlData            : DocumentNodeInfoType,
    appFormVersion     : AppFormVersion,
    documentIdOpt      : Option[String],
    modifiedTimeOpt: Option[Instant]
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit = {

    val attachmentPaths = mutable.Set[String]()

    FormRunner.collectAttachments(
      data            = xmlData,
      attachmentMatch = FormRunner.AttachmentMatch.BasePaths(includes = List(makeFromPath(appFormVersion._1, documentIdOpt)), excludes = Nil)
    ) foreach { case FormRunner.AttachmentWithHolder(fromPath, holder) =>

      if (! attachmentPaths(fromPath)) { // don't attempt to put a path twice (some forms have this)

        attachmentPaths += fromPath

        // NOTE: The returned attachments will match the `fromBasePaths` we are passing.

//        val mediatype =
//          holder.attValueOpt("mediatype").flatMap(_.trimAllToOpt).getOrElse("application/octet-stream")
//        val attachmentFilenameMetadata =
//          holder.attValueOpt("filename").flatMap(_.trimAllToOpt)

        val attachmentFilename =
          fromPath.splitTo[List]("/").lastOption.getOrElse(throw new IllegalStateException(fromPath))

        val attachmentZipPath =
          makeZipPath(appFormVersion, documentIdOpt, modifiedTimeOpt, attachmentFilename)

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
    zos            : ZipOutputStream,
    zipPath        : String,
    documentNode   : DocumentNodeInfoType,
    createdTimeOpt : Option[Instant],
    modifiedTimeOpt: Option[Instant]
  ): Unit = {
    debug(s"storing XML ${documentNode.rootElement.getSystemId} as `$zipPath`")
    val entry = new ZipEntry(zipPath)
    createdTimeOpt.foreach(creationTime => entry.setCreationTime(FileTime.from(creationTime)))
    modifiedTimeOpt.foreach(modifiedTime => entry.setLastModifiedTime(FileTime.from(modifiedTime)))
    try {
      zos.putNextEntry(entry)
      TransformerUtils.getXMLIdentityTransformer.transform(documentNode, new StreamResult(zos))
    } catch {
      case NonFatal(t) if t.getMessage.contains("duplicate entry") =>
        error(s"failed to write XML document to zip file: `$zipPath`")
        // TODO: count errors? flag to say whether we should fail?
    }
  }

  private def makeFromPath(
    appForm      : AppForm,
    documentIdOpt: Option[String],
  ): String =
    documentIdOpt match {
      case Some(documentId) =>
        FormRunner.createFormDataBasePath(appForm.app, appForm.form, isDraft = false, documentId)
      case None =>
        FormRunner.createFormDefinitionBasePath(appForm.app, appForm.form)
    }

  private def makeZipPath(
    appFormVersion: AppFormVersion,
    documentIdOpt : Option[String],
    modifiedTime  : Option[Instant],
    filename      : String
  ): String = {

    val latestPath =
      modifiedTime.map(_.toString).getOrElse("latest")

    val basePath =
      documentIdOpt match {
        case Some(_) =>
          FormRunner.createFormDataBasePathNoPrefix(appFormVersion._1, appFormVersion._2.some, isDraft = false, documentIdOpt)
        case None =>
          FormRunner.createFormDefinitionBasePathNoPrefix(appFormVersion._1, appFormVersion._2.some)
      }

    basePath :: latestPath :: filename :: Nil mkString "/"
  }
}
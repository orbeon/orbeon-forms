package org.orbeon.oxf.fr.persistence.proxy

import cats.data.NonEmptyList
import cats.syntax.option._
import org.orbeon.io.IOUtils
import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Response}
import org.orbeon.oxf.fr.FormRunnerParams.AppFormVersion
import org.orbeon.oxf.fr._
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.http._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{ConnectionResult, ContentTypes, CoreCrossPlatformSupport, CoreCrossPlatformSupportTrait}
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.scaxon.SimplePath._

import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.zip.{ZipEntry, ZipOutputStream}
import java.{util => ju}
import javax.xml.transform.stream.StreamResult
import scala.collection.mutable
import scala.util.control.NonFatal
import scala.util.{Failure, Success}


object Export extends ExportOrPurge {

  type Context = ZipOutputStream

  // Entry point for the export
  def processExport(
    request      : Request,
    response     : Response,
    appOpt       : Option[String],
    formOpt      : Option[String],
    documentIdOpt: Option[String],
  ): Unit = {

    implicit val coreCrossPlatformSupport: CoreCrossPlatformSupportTrait = CoreCrossPlatformSupport

    val appFormVersionMatchOpt = findAppFormVersionMatch(
      request       = request,
      appOpt        = appOpt,
      formOpt       = formOpt,
      documentIdOpt = documentIdOpt
    )

    val formOrDataSet =
      request.getFirstParamAsString(ContentParam) map { content =>
        val contentTokens = content.splitTo[Set](sep = ContentParamSeparator)
        if (! contentTokens.forall(AllowedContentTokens.contains))
          throw HttpStatusCodeException(StatusCode.BadRequest)
        contentTokens.map(AllowedContentMap)
      } getOrElse
        FormOrData.valuesSet

    val dataRevisionHistory =
      DataRevisionHistoryAdt.fromStringOptOrThrow(request.getFirstParamAsString(DataRevisionHistoryParam))
        .getOrElse(DataRevisionHistoryAdt.Exclude)

    val dateRangeGtOpt =
      request.getFirstParamAsString(DateRangeGtParam).map { dateRangeGe =>
        Instant.parse(dateRangeGe)
      }

    val dateRangeLtOpt =
      request.getFirstParamAsString(DateRangeLtParam).map { dateRangeLe =>
        Instant.parse(dateRangeLe)
      }

    processExportImpl(
      request.getHeaderValuesMap,
      response,
      appFormVersionMatchOpt,
      formOrDataSet,
      dataRevisionHistory,
      dateRangeGtOpt,
      dateRangeLtOpt
    )
  }


  val processOtherAttachments: Boolean = false

  def processAttachment(
    ctx           : Context,
    formVersionOpt: Option[Int],
    fromPath      : String,
    toPath        : String,
    debugAction   : String
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit = {
    debug(s"storing `$fromPath` as `$toPath`")
    ConnectionResult.tryWithSuccessConnection(
      PersistenceApi.connectPersistence(
        method         = HttpMethod.GET,
        path           = fromPath,
        formVersionOpt = formVersionOpt
      ),
      closeOnSuccess = true
    ) { is =>
      val entry = new ZipEntry(toPath)
      ctx.putNextEntry(entry)
      IOUtils.copyStreamAndClose(is, ctx, doCloseOut = false)
    } match {
      case Success(_) => debug(s"success retrieving attachment when $debugAction form `$fromPath`")
      case Failure(_) => error(s"failure retrieving attachment when $debugAction form `$fromPath`")
    }
  }

  def completeAttachments(
    ctx                 : Context,
    appFormVersion      : AppFormVersion,
    documentId          : String,
    attachmentPaths     : mutable.Set[String], // for data that has been deleted
    otherAttachmentPaths: mutable.Set[String]  // for data that hasn't been deleted
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit = ()

  private def processExportImpl(
    incomingHeaders    : ju.Map[String, Array[String]],
    response           : Response,
    matchesOpt         : Option[NonEmptyList[MatchSpec]],
    formOrDataSet      : Set[FormOrData],
    dataRevisionHistory: DataRevisionHistoryAdt,
    dateRangeGtOpt     : Option[Instant],
    dateRangeLtOpt     : Option[Instant]
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit =
    IOUtils.useAndClose(new ZipOutputStream(response.getOutputStream)) { zos =>
      response.setContentType(ContentTypes.ZipContentType)
      matchesOpt match {
        case None =>
          exportWithMatch(zos, incomingHeaders, None, formOrDataSet, dataRevisionHistory, dateRangeGtOpt, dateRangeLtOpt)
        case Some(matches) =>
          matches.iterator.foreach { filter =>
            exportWithMatch(zos, incomingHeaders, filter.some, formOrDataSet, dataRevisionHistory, dateRangeGtOpt, dateRangeLtOpt)
          }
      }
    }

  def processXmlDocument(
    ctx            : Context,
    fromPath       : String,
    toPath         : String,
    documentNode   : DocumentNodeInfoType,
    createdTimeOpt : Option[Instant],
    modifiedTimeOpt: Option[Instant],
    forCurrentData : Boolean
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit = {
    debug(s"storing XML ${documentNode.rootElement.getSystemId} as `$toPath`")
    val entry = new ZipEntry(toPath)
    createdTimeOpt.foreach(creationTime => entry.setCreationTime(FileTime.from(creationTime)))
    modifiedTimeOpt.foreach(modifiedTime => entry.setLastModifiedTime(FileTime.from(modifiedTime)))
    try {
      ctx.putNextEntry(entry)
      TransformerUtils.getXMLIdentityTransformer.transform(documentNode, new StreamResult(ctx))
    } catch {
      case NonFatal(t) if t.getMessage.contains("duplicate entry") =>
        error(s"failed to write XML document to zip file: `$toPath`")
        // TODO: count errors? flag to say whether we should fail?
    }
  }

  def makeToPath(
    appFormVersion : AppFormVersion,
    documentIdOpt  : Option[String],
    modifiedTimeOpt: Option[Instant],
    filename       : String
  ): String = {

    val latestPath =
      modifiedTimeOpt.map(_.toString).getOrElse(LatestTimestampTag)

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
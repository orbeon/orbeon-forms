package org.orbeon.oxf.fr.persistence.proxy

import cats.data.NonEmptyList
import cats.syntax.option._
import org.orbeon.connection.ConnectionResult
import org.orbeon.dom.io.XMLWriter
import org.orbeon.io.{CharsetNames, IOUtils}
import org.orbeon.oxf.externalcontext.ExternalContext.Response
import org.orbeon.oxf.fr.FormRunnerParams.AppFormVersion
import org.orbeon.oxf.fr._
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.http._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.StaticXPath.{DocumentNodeInfoType, tinyTreeToOrbeonDom}
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{ContentTypes, CoreCrossPlatformSupportTrait}
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

  def processImpl(
    getFirstParamAsString: String => Option[String],
    incomingHeaders      : ju.Map[String, Array[String]],
    response             : Response,
    matchesOpt           : Option[NonEmptyList[MatchSpec]],
    dataRevisionHistory  : DataRevisionHistoryAdt,
    dateRangeGtOpt       : Option[Instant],
    dateRangeLtOpt       : Option[Instant]
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit = {

    val formOrDataSet =
      getFirstParamAsString(ContentParam) map { content =>
        val contentTokens = content.splitTo[Set](sep = ContentParamSeparator)
        if (! contentTokens.forall(AllowedContentTokens.contains))
          throw HttpStatusCodeException(StatusCode.BadRequest)
        contentTokens.map(AllowedContentMap)
      } getOrElse
        FormOrData.valuesSet

    IOUtils.useAndClose(new ZipOutputStream(response.getOutputStream)) { zos =>
      response.setContentType(ContentTypes.ZipContentType)
      matchesOpt match {
        case None =>
          processWithMatch(zos, incomingHeaders, None, formOrDataSet, dataRevisionHistory, dateRangeGtOpt, dateRangeLtOpt)
        case Some(matches) =>
          matches.iterator.foreach { filter =>
            processWithMatch(zos, incomingHeaders, filter.some, formOrDataSet, dataRevisionHistory, dateRangeGtOpt, dateRangeLtOpt)
          }
      }
    }
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

    val connectionResult = PersistenceApi.connectPersistence(
      method         = HttpMethod.GET,
      path           = fromPath,
      formVersionOpt = formVersionOpt
    )

    ConnectionResult.tryWithSuccessConnection(
      connectionResult,
      closeOnSuccess = true
    ) { is =>
      val entry = new ZipEntry(toPath)
      ctx.putNextEntry(entry)
      IOUtils.copyStreamAndClose(is, ctx, doCloseOut = false)

      Metadata.fromHeaders(connectionResult.headers).foreach(addMetadata(ctx, toPath, _))
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

  def processXmlDocument(
    ctx            : Context,
    fromPath       : String,
    toPath         : String,
    documentNode   : DocumentNodeInfoType,
    createdTimeOpt : Option[Instant],
    modifiedTimeOpt: Option[Instant],
    forCurrentData : Boolean,
    metadataOpt    : Option[Metadata]
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

    metadataOpt.foreach(addMetadata(ctx, toPath, _))
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

  private def addMetadata(ctx: Context, toPath: String, metadata: Metadata): Unit = {
    val entry = new ZipEntry(metadataPath(toPath))
    ctx.putNextEntry(entry)

    val metadataAsString = tinyTreeToOrbeonDom(metadata.toXML).serializeToString(XMLWriter.PrettyFormat)
    ctx.write(metadataAsString.getBytes(CharsetNames.Utf8))
  }

  private def metadataPath(toPath: String): String =
    toPath + ".metadata.xml"
}
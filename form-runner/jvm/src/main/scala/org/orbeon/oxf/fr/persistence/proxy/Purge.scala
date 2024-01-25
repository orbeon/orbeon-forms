package org.orbeon.oxf.fr.persistence.proxy

import cats.data.NonEmptyList
import cats.syntax.option._
import org.orbeon.oxf.externalcontext.ExternalContext.Response
import org.orbeon.oxf.fr.FormRunnerParams.AppFormVersion
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.{AppForm, FormOrData}
import org.orbeon.oxf.http.{HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.{CoreCrossPlatformSupportTrait, IndentedLogger}
import org.orbeon.saxon.om.DocumentInfo

import java.time.Instant
import java.{util => ju}
import scala.collection.mutable


object Purge extends ExportOrPurge {

  case class PurgeContext()

  type Context = PurgeContext

  def processImpl(
    getFirstParamAsString: String => Option[String],
    incomingHeaders      : ju.Map[String, Array[String]],
    response             : Response,
    matchesOpt           : Option[NonEmptyList[MatchSpec]],
    dataRevisionHistory  : DataRevisionHistoryAdt,
    dateRangeGtOpt       : Option[Instant],
    dateRangeLtOpt       : Option[Instant]
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait,
    indentedLogger          : IndentedLogger
  ): Unit = {
    val ctx = PurgeContext()
    matchesOpt match {
      case None =>
        processWithMatch(ctx, incomingHeaders, None, Set(FormOrData.Data), dataRevisionHistory, dateRangeGtOpt, dateRangeLtOpt)
      case Some(matches) =>
        matches.iterator.foreach { filter =>
          processWithMatch(ctx, incomingHeaders, filter.some, Set(FormOrData.Data), dataRevisionHistory, dateRangeGtOpt, dateRangeLtOpt)
        }
    }
  }

  val processOtherAttachments: Boolean = true

  def processXmlDocument(
    ctx            : Context,
    fromPath       : String,
    toPath         : String,
    documentNode   : DocumentInfo,
    createdTimeOpt : Option[Instant], // from history API or from reading the current data
    modifiedTimeOpt: Option[Instant], // from history API or from reading the current data
    forCurrentData : Boolean,
    metadataOpt    : Option[Metadata]
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait,
    indentedLogger          : IndentedLogger
  ): Unit =
    (modifiedTimeOpt, forCurrentData) match {
      case (Some(modifiedTime), true) =>
        // Multi-step `DELETE` for current data
        PersistenceApi.doDelete(fromPath, None, forceDelete = false) foreach { deletedModifiedTime => // normal `DELETE` for current data
          PersistenceApi.doDelete(fromPath, modifiedTime.some,        forceDelete = true)             // force `DELETE` for what was current data
          PersistenceApi.doDelete(fromPath, deletedModifiedTime.some, forceDelete = true)             // force `DELETE` for newly-created historical row
        }
      case (Some(modifiedTime), false) =>
        // Force `DELETE` for historical data
        PersistenceApi.doDelete(fromPath, modifiedTime.some, forceDelete = true)
      case (None, _) =>
        // Q: Should the caller check for that in all cases so we could just receive a `modifiedTime`?
        error(s"no last-modified-time found for document `$fromPath`")
        throw HttpStatusCodeException(StatusCode.InternalServerError)
    }

  def processAttachment(
    ctx           : Context,
    formVersionOpt: Option[Int],
    fromPath      : String,
    toPath        : String,
    debugAction   : String
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait,
    indentedLogger          : IndentedLogger
  ): Unit = ()

  def completeAttachments(
    ctx                 : Context,
    appFormVersion      : AppFormVersion,
    documentId          : String,
    attachmentPaths     : mutable.Set[String], // for data that has been deleted
    otherAttachmentPaths: mutable.Set[String]  // for data that hasn't been deleted
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait,
    indentedLogger          : IndentedLogger
  ): Unit =
    (attachmentPaths -- otherAttachmentPaths) foreach { fromPath =>
      // The CRUD implementation supports deleting all data matching the document id and filename, regardless of the
      // last modified time
      PersistenceApi.doDelete(fromPath, modifiedTimeOpt = None, forceDelete = true)
    }

  def makeToPath(
    appFormVersion : (AppForm, Int),
    documentIdOpt  : Option[String],
    modifiedTimeOpt: Option[Instant],
    filename       : String
  ): String =
    "" // unused
}

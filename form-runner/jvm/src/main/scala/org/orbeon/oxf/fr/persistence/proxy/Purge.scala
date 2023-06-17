package org.orbeon.oxf.fr.persistence.proxy

import cats.data.NonEmptyList
import cats.syntax.option._
import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Response}
import org.orbeon.oxf.fr.FormRunnerParams.AppFormVersion
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.{AppForm, FormOrData}
import org.orbeon.oxf.http.{Headers, HttpMethod, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.{ConnectionResult, CoreCrossPlatformSupport, CoreCrossPlatformSupportTrait, PathUtils}
import org.orbeon.saxon.om.DocumentInfo

import java.time.Instant
import java.{util => ju}
import scala.collection.mutable


object Purge extends ExportOrPurge {

  case class PurgeContext()

  type Context = PurgeContext

  // Entry point for the purge
  def processPurge(
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

    processPurgeImpl(
      request.getHeaderValuesMap,
      response,
      appFormVersionMatchOpt,
      dataRevisionHistory,
      dateRangeGtOpt,
      dateRangeLtOpt
    )
  }

   // Entry point for the export
  def processPurgeImpl(
    incomingHeaders    : ju.Map[String, Array[String]],
    response           : Response,
    matchesOpt         : Option[NonEmptyList[MatchSpec]],
    dataRevisionHistory: DataRevisionHistoryAdt,
    dateRangeGtOpt     : Option[Instant],
    dateRangeLtOpt     : Option[Instant]
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit = {

    val ctx = PurgeContext()
    matchesOpt match {
      case None =>
        exportWithMatch(ctx, incomingHeaders, None, Set(FormOrData.Data), dataRevisionHistory, dateRangeGtOpt, dateRangeLtOpt)
      case Some(matches) =>
        matches.iterator.foreach { filter =>
          exportWithMatch(ctx, incomingHeaders, filter.some, Set(FormOrData.Data), dataRevisionHistory, dateRangeGtOpt, dateRangeLtOpt)
        }
    }
  }

  private def doDelete(
    path           : String,
    modifiedTimeOpt: Option[Instant],
    forceDelete    : Boolean
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Option[Instant] = { // `None` indicates that the document was not found
    // xxx TODO: see what proxy will do wrt version and checking existence of previous doc

    val pathWithParams =
      PathUtils.recombineQuery(
        path,
        (forceDelete list ("force-delete" -> true.toString)) :::
        modifiedTimeOpt.toList.map(modifiedTime => "last-modified-time" -> modifiedTime.toString)
      )

    val cxr =
      PersistenceApi.connectPersistence(
        method         = HttpMethod.DELETE,
        path           = pathWithParams,
        formVersionOpt = None // xxx TODO
      )

    if (cxr.statusCode == StatusCode.NotFound) {
      cxr.close()
      None
    } else {
      ConnectionResult.tryWithSuccessConnection(cxr, closeOnSuccess = true) { _ =>
        headerFromRFC1123OrIso(cxr.headers, Headers.OrbeonLastModified, Headers.LastModified)
          .getOrElse(throw HttpStatusCodeException(StatusCode.InternalServerError)).some // require implementation to return modified date
      } .get // can throw
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
    forCurrentData : Boolean
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit =
    (modifiedTimeOpt, forCurrentData) match {
      case (Some(modifiedTime), true) =>
        // Multi-step `DELETE` for current data
        doDelete(fromPath, None, forceDelete = false) foreach { deletedModifiedTime => // normal `DELETE` for current data
          doDelete(fromPath, modifiedTime.some,        forceDelete = true)             // force `DELETE` for what was current data
          doDelete(fromPath, deletedModifiedTime.some, forceDelete = true)             // force `DELETE` for newly-created historical row
        }
      case (Some(modifiedTime), false) =>
        // Force `DELETE` for historical data
        doDelete(fromPath, modifiedTime.some, forceDelete = true)
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
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit = ()

  def completeAttachments(
    ctx                 : Context,
    appFormVersion      : AppFormVersion,
    documentId          : String,
    attachmentPaths     : mutable.Set[String], // for data that has been deleted
    otherAttachmentPaths: mutable.Set[String]  // for data that hasn't been deleted
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit =
    (attachmentPaths -- otherAttachmentPaths) foreach { fromPath =>
      // The CRUD implementation supports deleting all data matching the document id and filename, regardless of the
      // last modified time
      doDelete(fromPath, modifiedTimeOpt = None, forceDelete = true)
    }

  def makeToPath(
    appFormVersion : (AppForm, Int),
    documentIdOpt  : Option[String],
    modifiedTimeOpt: Option[Instant],
    filename       : String
  ): String =
    "" // unused
}

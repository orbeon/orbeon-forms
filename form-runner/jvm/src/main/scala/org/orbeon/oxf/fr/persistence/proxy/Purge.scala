package org.orbeon.oxf.fr.persistence.proxy

import cats.data.NonEmptyList
import cats.syntax.option._
import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Response}
import org.orbeon.oxf.fr.{AppForm, FormOrData, FormRunner}
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, CoreCrossPlatformSupportTrait}
import org.orbeon.saxon.om.DocumentInfo

import java.{util => ju}
import java.time.Instant


object Purge extends ExportOrPurge {

  case class PurgeContext()

  type Context = PurgeContext

//  sealed trait PurgeType
//  object PurgeType {
//    case object All                 extends PurgeType
//    case object RevisionHistoryOnly extends PurgeType
//
//    def fromString(s: String): Option[PurgeType] = s match {
//      case "all"                   => Some(All)
//      case "revision-history-only" => Some(RevisionHistoryOnly)
//      case _                       => None
//    }
//  }

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

    // xxx TODO: base trait must support providing attachments GC information

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

  private def doDelete(path: String, modifiedTimeOpt: Option[Instant]): Unit = {
    // TODO: must call API but passing flag to tell truly DELETE
    ???
  }

  def processXmlDocument(
    ctx            : Context,
    fromPath       : String,
    toPath         : String,
    documentNode   : DocumentInfo,
    createdTimeOpt : Option[Instant],
    modifiedTimeOpt: Option[Instant]
  ): Unit =
    doDelete(fromPath, modifiedTimeOpt)

  def readPersistenceContentAndProcess(
    ctx           : Context,
    formVersionOpt: Option[Int],
    fromPath      : String,
    toPath        : String,
    debugAction   : String
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit =
    doDelete(fromPath, None) // xxx maybe not immediately (see GC)

  def makeToPath(
    appFormVersion : (AppForm, Int),
    documentIdOpt  : Option[String],
    modifiedTimeOpt: Option[Instant],
    filename       : String
  ): String =
    "" // unused
}

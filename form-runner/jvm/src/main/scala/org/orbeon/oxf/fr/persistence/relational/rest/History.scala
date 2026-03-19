package org.orbeon.oxf.fr.persistence.relational.rest

import cats.implicits.catsSyntaxOptionId
import org.orbeon.oxf.controller.XmlNativeRoute
import org.orbeon.oxf.externalcontext.{ExternalContext, Organization, UserAndGroup}
import org.orbeon.oxf.fr.AppForm
import org.orbeon.oxf.fr.persistence.PersistenceMetadataSupport
import org.orbeon.oxf.fr.persistence.api.{Diffs, HistoryDiff, PersistenceApi}
import org.orbeon.oxf.fr.persistence.relational.Statement.*
import org.orbeon.oxf.fr.persistence.relational.{Provider, RelationalUtils}
import org.orbeon.oxf.http.{HttpMethod, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, CoreCrossPlatformSupportTrait, DateUtils, IndentedLogger}
import org.orbeon.oxf.xml.XMLReceiverSupport.*
import org.orbeon.oxf.xml.{DeferredXMLReceiver, XMLReceiver}

import java.sql.{Connection, ResultSet}
import java.time.Instant
import scala.collection.mutable
import scala.util.matching.Regex


object HistoryRoute extends XmlNativeRoute {

  import History.*

  def process()(implicit pc: PipelineContext, ec: ExternalContext): Unit = {

    val httpRequest  = ec.getRequest
    val httpResponse = ec.getResponse

    implicit val indentedLogger: IndentedLogger = RelationalUtils.newIndentedLogger

    try {
      require(httpRequest.getMethod == HttpMethod.GET)

      httpRequest.getRequestPath match {
        case ServicePathRe(provider, app, form, documentId, filenameOrNull) =>
          implicit val receiver: DeferredXMLReceiver = getResponseXmlReceiverSetContentType
          returnHistory(
            Request(httpRequest.getFirstParamAsString, provider),
            AppForm(app, form),
            documentId,
            Option(filenameOrNull),
            PersistenceMetadataSupport.isInternalAdminUser(httpRequest.getFirstParamAsString)
          )
        case _ =>
          httpResponse.setStatus(StatusCode.NotFound)
      }
    } catch {
      case e: HttpStatusCodeException =>
        httpResponse.setStatus(e.code)
    }
  }
}

private object History {

  case class Request(
    pageSize         : Int,
    pageNumber       : Int,
    includeDiffs     : Boolean,
    langOpt          : Option[String],
    truncationSizeOpt: Option[Int],
    provider         : Provider
  )

  case object Request {
    def apply(getParam: String => Option[String], providerString: String): Request =
      Request(
        pageSize          = RelationalUtils.parsePositiveIntParamOrThrow(getParam(PersistenceApi.PageSizeParam),  10),
        pageNumber        = RelationalUtils.parsePositiveIntParamOrThrow(getParam(PersistenceApi.PageNumberParam), 1),
        includeDiffs      = getParam(HistoryDiff.IncludeDiffsParam).getOrElse("false").toBoolean,
        langOpt           = getParam(HistoryDiff.LanguageParam),
        truncationSizeOpt = RelationalUtils.parsePositiveIntOptParamOrThrow(getParam(HistoryDiff.TruncationSizeParam)),
        provider          = Provider.withName(providerString)
      )
  }

  val ServicePathRe: Regex = "/fr/service/([^/]+)/history/([^/]+)/([^/]+)/([^/^.]+)(?:/([^/]+))?".r

  def returnHistory(
    request            : Request,
    appForm            : AppForm,
    documentId         : String,
    filenameOpt        : Option[String],
    isInternalAdminUser: Boolean // 2024-07-18: Unused, see https://github.com/orbeon/orbeon-forms/issues/6416
  )(implicit
    receiver           : DeferredXMLReceiver,
    externalContext    : ExternalContext,
    indentedLogger     : IndentedLogger
  ): Unit = {

    val historyWithoutDiffs =
      RelationalUtils.withConnection { connection =>
        historyWithoutDiffsFromDatabase(connection, request, appForm, documentId, filenameOpt)
      }

    // #7561: call this outside of the RelationalUtils.withConnection above to avoid nested connections
    emitDocumentsWithDiffsIfRequested(request, appForm, documentId, historyWithoutDiffs)
  }

  private case class HistoryWithoutDiffs(
    searchTotal           : Int,
    minLastModifiedTimeOpt: Option[Instant],
    maxLastModifiedTimeOpt: Option[Instant],
    documentsMetadata     : List[DocumentMetadata],
    commonMetadataOpt     : Option[CommonMetadata]
  )

  private case class DocumentMetadata(
    modifiedTime        : Instant,
    modifiedUsername    : String,
    ownerUserAndGroupOpt: Option[UserAndGroup],
    organizationOpt     : Option[Organization],
    deleted             : Boolean,
    stageOpt            : Option[String]
  )

  private case class CommonMetadata(
    formVersion    : Int,
    createdTime    : Instant
    //createdUsername: String // See #5734
  )

  private def historyWithoutDiffsFromDatabase(
    connection    : Connection,
    request       : Request,
    appForm       : AppForm,
    documentId    : String,
    filenameOpt   : Option[String]
  )(implicit
    indentedLogger: IndentedLogger
  ): HistoryWithoutDiffs = {

    val startOffsetZeroBased = (request.pageNumber - 1) * request.pageSize
    val rowNumSQL            = Provider.rowNumSQL(
      provider   = request.provider,
      connection = connection,
      orderBy    = "d.last_modified_time DESC"
    )
    val rowNumCol            = rowNumSQL.col
    val rowNumOrderBy        = rowNumSQL.orderBy
    val rowNumTable          = rowNumSQL.table match {
      case Some(table) => table + ","
      case None        => ""
    }

    val tableName =
      filenameOpt match {
        case None                 => "orbeon_form_data"
        case Some(NonAllBlank(_)) => "orbeon_form_data_attach" // TODO: use constants for table names
        case Some(_)              => throw HttpStatusCodeException(StatusCode.BadRequest)
      }

    val hasStage = filenameOpt.isDefined

    val innerSQL =
      s"""|SELECT  t.last_modified_time, t.last_modified_by, t.created
          |        , t.username, t.groupname, t.organization_id
          |        ${if (hasStage) ", t.stage" else ""}
          |        , t.form_version, t.deleted
          |FROM    $tableName t
          |WHERE   t.app  = ?
          |        and t.form = ?
          |        and t.document_id = ?
          |        and t.draft = ?
          |        ${if (filenameOpt.isDefined) "and t.file_name = ?" else ""}
          |""".stripMargin

    // Boilerplate for cross-database paging, see also `SearchLogic`
    val sql =
      s"""SELECT
         |    c.*
         |FROM
         |    (
         |        SELECT
         |            d.*,
         |            $rowNumCol
         |        FROM
         |             $rowNumTable
         |             (
         |                 $innerSQL
         |             ) d
         |        $rowNumOrderBy
         |    ) c
         | WHERE
         |    row_num
         |        BETWEEN ${startOffsetZeroBased + 1}
         |        AND     ${startOffsetZeroBased + request.pageSize + (if (request.includeDiffs) 1 else 0)}
         |""".stripMargin

    val setters =
      List[Setter](
        (ps, i) => ps.setString(i, appForm.app),
        (ps, i) => ps.setString(i, appForm.form),
        (ps, i) => ps.setString(i, documentId),
        (ps, i) => ps.setString(i, "N"),
      ) :::
      filenameOpt.toList.map { filename =>
        ((ps, i) => ps.setString(i, filename)): Setter
      }

    val (searchTotal, minLastModifiedTimeOpt, maxLastModifiedTimeOpt) = {

      val sql =
        s"""SELECT count(*) total,
           |       min(a.last_modified_time) min_last_modified_time,
           |       max(a.last_modified_time) max_last_modified_time
           |  FROM (
           |       $innerSQL
           |       ) a
         """.stripMargin

      debug(s"search total query:\n$sql")
      executeQuery(connection, sql, List(StatementPart("", setters))) { rs =>
        rs.next()

        (
          rs.getInt("total"),
          Option(rs.getTimestamp("min_last_modified_time")).map(_.toInstant),
          Option(rs.getTimestamp("max_last_modified_time")).map(_.toInstant)
        )
      }
    }

    // Read all documents from the database, as we might need to compute the revision history diffs based on the
    // modification time of the documents
    val (documentsMetadata, commonMetadataOpt) =
      executeQuery(connection, sql, List(StatementPart("", setters))) { rs =>
        documentsMetadataFromResultSet(connection, rs, hasStage = hasStage)
      }

    HistoryWithoutDiffs(
      searchTotal            = searchTotal,
      minLastModifiedTimeOpt = minLastModifiedTimeOpt,
      maxLastModifiedTimeOpt = maxLastModifiedTimeOpt,
      documentsMetadata      = documentsMetadata,
      commonMetadataOpt      = commonMetadataOpt
    )
  }

  private def documentsMetadataFromResultSet(
    connection: Connection,
    rs        : ResultSet,
    hasStage  : Boolean
  ): (List[DocumentMetadata], Option[CommonMetadata]) = {

    val documents = mutable.ListBuffer[DocumentMetadata]()
    var position  = 0

    var commonMetadataOpt: Option[CommonMetadata] = None

    while (rs.next()) {

      if (position == 0) {
        commonMetadataOpt = CommonMetadata(
          formVersion     = rs.getInt("form_version"),
          createdTime     = rs.getTimestamp("created").toInstant
          //createdUsername = rs.getString("created_by") // See #5734
        ).some
      }

      documents += DocumentMetadata(
        modifiedTime         = rs.getTimestamp("last_modified_time").toInstant,
        modifiedUsername     = rs.getString("last_modified_by").trimAllToEmpty,
        ownerUserAndGroupOpt = UserAndGroup.fromStrings(rs.getString("username"), rs.getString("groupname")),
        organizationOpt      = OrganizationSupport.readFromResultSet(connection, rs).map(_._2),
        deleted              = rs.getString("deleted") == "Y",
        stageOpt             = hasStage.option(rs.getString("stage").trimAllToEmpty)
      )

      position += 1
    }

    (documents.toList, commonMetadataOpt)
  }

  private def emitDocumentsWithDiffsIfRequested(
    request            : Request,
    appForm            : AppForm,
    documentId         : String,
    historyWithoutDiffs: HistoryWithoutDiffs
  )(implicit
    receiver           : DeferredXMLReceiver,
    indentedLogger     : IndentedLogger
  ): Unit =
    withDocument {
      withElement(
        "documents",
        atts =
          List(
            "application-name"         -> appForm.app,
            "form-name"                -> appForm.form,
            "document-id"              -> documentId,
            "total"                    -> historyWithoutDiffs.searchTotal.toString
          ) :::
          (historyWithoutDiffs.searchTotal > 0).flatList(
            List(
              "min-last-modified-time" -> historyWithoutDiffs.minLastModifiedTimeOpt.map(DateUtils.formatIsoDateTimeUtc).getOrElse(throw new IllegalArgumentException),
              "max-last-modified-time" -> historyWithoutDiffs.maxLastModifiedTimeOpt.map(DateUtils.formatIsoDateTimeUtc).getOrElse(throw new IllegalArgumentException)
            )
          ) :::
          List(
            "page-size"                -> request.pageSize.toString,
            "page-number"              -> request.pageNumber.toString,
          ) :::
          historyWithoutDiffs.commonMetadataOpt.toList.flatMap { commonMetadata =>
            List(
              "form-version"     -> commonMetadata.formVersion.toString,
              "created-time"     -> DateUtils.formatIsoDateTimeUtc(commonMetadata.createdTime)
              //"created-username" -> commonMetadata.createdUsername // TODO: #5734 Add created_by column
            )
          }
      ) {
        emitDocumentsWithDiffsIfRequested(
          request        = request,
          documents      = historyWithoutDiffs.documentsMetadata,
          appForm        = appForm,
          formVersionOpt = historyWithoutDiffs.commonMetadataOpt.map(_.formVersion),
          documentId     = documentId
        )
      }
    }

  private def emitDocumentsWithDiffsIfRequested(
    request       : Request,
    documents     : List[DocumentMetadata],
    appForm       : AppForm,
    formVersionOpt: Option[Int],
    documentId    : String
  )(implicit
    receiver      : XMLReceiver,
    indentedLogger: IndentedLogger
  ): Unit = {

    implicit val coreCrossPlatformSupport: CoreCrossPlatformSupportTrait = CoreCrossPlatformSupport

    // This will be computed only if diffs are requested
    lazy val formDefinitionAndDiffsTry = locally {

      val appFormVersion = (
        appForm,
        // If we retrieve the diffs, this means the form version has been retrieved as well (non-empty list of documents)
        formVersionOpt.getOrElse(throw HttpStatusCodeException(StatusCode.InternalServerError))
      )

      for {
        formDefinition <- HistoryDiff.formDefinition(
          appFormVersion   = appFormVersion,
          requestedLangOpt = request.langOpt
        )
        formDiffs      <- HistoryDiff.formDiffs(
          appFormVersion = appFormVersion,
          documentId     = documentId,
          modifiedTimes  = documents.map(_.modifiedTime),
          formDefinition = formDefinition
        )
      } yield (formDefinition, formDiffs)
    }

    for {
      // If diffs are requested, we've retrieved pageSize + 1 documents from the database to get the modification
      // date of the previous document for the last document in the page; always return pageSize documents at the most
      (document, previousLastModifiedOpt) <- documents.zip(documents.tail.map(_.modifiedTime.some) :+ None).take(request.pageSize)
    } {
      val atts =
        document.stageOpt.map("stage" -> _).toList :::
        List(
          "modified-time"     -> DateUtils.formatIsoDateTimeUtc(document.modifiedTime),
          "modified-username" -> document.modifiedUsername,
          "owner-username"    -> document.ownerUserAndGroupOpt.map(_.username).getOrElse(""),
          "owner-group"       -> document.ownerUserAndGroupOpt.flatMap(_.groupname).getOrElse(""),
          "deleted"           -> document.deleted.toString,
        )

      withElement("document", atts = atts) {
        previousLastModifiedOpt match {
          case Some(olderModifiedTime) if request.includeDiffs =>

            val newerModifiedTime                          = document.modifiedTime
            val (formDefinition, formDiffsByModifiedTimes) = formDefinitionAndDiffsTry.get
            val diffsOpt                                   = formDiffsByModifiedTimes((olderModifiedTime, newerModifiedTime))

            Diffs.serializeToSAX(
              diffsOpt             = diffsOpt,
              olderModifiedTime    = olderModifiedTime,
              newerModifiedTimeOpt = None, // Already included in document element
              formDefinition       = formDefinition,
              truncationSizeOpt    = request.truncationSizeOpt
            )

          case _ =>
        }
      }
    }
  }
}
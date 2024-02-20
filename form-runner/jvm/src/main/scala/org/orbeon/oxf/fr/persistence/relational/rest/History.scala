package org.orbeon.oxf.fr.persistence.relational.rest

import org.orbeon.oxf.externalcontext.UserAndGroup
import org.orbeon.oxf.fr.persistence.PersistenceMetadataSupport
import org.orbeon.oxf.fr.persistence.relational.Statement._
import org.orbeon.oxf.fr.persistence.relational.{Provider, RelationalUtils}
import org.orbeon.oxf.http.{HttpMethod, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{ContentTypes, DateUtils, IndentedLogger, NetUtils, XPath}
import org.orbeon.oxf.xml.{DeferredXMLReceiver, DeferredXMLReceiverImpl, TransformerUtils}

import java.io.OutputStream
import javax.xml.transform.stream.StreamResult
import scala.util.matching.Regex


class History extends ProcessorImpl {

  import History._

  override def start(pipelineContext: PipelineContext): Unit = {

    val httpRequest  = NetUtils.getExternalContext.getRequest
    val httpResponse = NetUtils.getExternalContext.getResponse

    implicit val indentedLogger: IndentedLogger = RelationalUtils.newIndentedLogger

    try {
      require(httpRequest.getMethod == HttpMethod.GET)

      httpRequest.getRequestPath match {
        case ServicePathRe(provider, app, form, documentId, filenameOrNull) =>
          httpResponse.setContentType(ContentTypes.XmlContentType)
          returnHistory(
            Request(httpRequest.getFirstParamAsString, provider),
            app,
            form,
            documentId,
            Option(filenameOrNull),
            PersistenceMetadataSupport.isInternalAdminUser(httpRequest.getFirstParamAsString),
            httpResponse.getOutputStream
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

  private val xmlIndentation = 2

  case class Request(
    pageSize  : Int,
    pageNumber: Int,
    provider  : Provider
  )

  case object Request {
    def apply(getParam: String => Option[String], providerString: String): Request =
      Request(
        pageSize   = RelationalUtils.parsePositiveIntParamOrThrow(getParam("page-size"),  10),
        pageNumber = RelationalUtils.parsePositiveIntParamOrThrow(getParam("page-number"), 1),
        provider   = Provider.withName(providerString)
      )
  }

  val ServicePathRe: Regex = "/fr/service/([^/]+)/history/([^/]+)/([^/]+)/([^/^.]+)(?:/([^/]+))?".r

  def returnHistory(
    request            : Request,
    app                : String,
    form               : String,
    documentId         : String,
    filenameOpt        : Option[String],
    isInternalAdminUser: Boolean, // xxx unused for now, we don't check permissions!
    outputStream       : OutputStream
  )(implicit
    indentedLogger     : IndentedLogger
  ): Unit = {
    RelationalUtils.withConnection { connection =>

      val startOffsetZeroBased = (request.pageNumber - 1) * request.pageSize
      val rowNumSQL            = Provider.rowNumSQL(
        provider       = request.provider,
        connection     = connection,
        orderBy        = "d.last_modified_time DESC"
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
           |        AND     ${startOffsetZeroBased + request.pageSize}
           |""".stripMargin

      val setters =
        List[Setter](
          (ps, i) => ps.setString(i, app),
          (ps, i) => ps.setString(i, form),
          (ps, i) => ps.setString(i, documentId),
          (ps, i) => ps.setString(i, "N"),
        ) :::
        filenameOpt.toList.map { filename =>
          ((ps, i) => ps.setString(i, filename)): Setter
        }

      val searchCount = {

        val sql =
          s"""SELECT count(*)
             |  FROM (
             |       $innerSQL
             |       ) a
           """.stripMargin

        debug(s"search total query:\n$sql")
        executeQuery(connection, sql, List(StatementPart("", setters))) { rs =>
          rs.next()
          rs.getInt(1)
        }
      }

      // xxx TODO: remove duplication with export, and put this somewhere common
      implicit val receiver: DeferredXMLReceiver =
        new DeferredXMLReceiverImpl(
          TransformerUtils.getIdentityTransformerHandler(XPath.GlobalConfiguration) |!>
            (t => TransformerUtils.applyOutputProperties(
              t.getTransformer,
              "xml",
              null,
              null,
              null,
              null,
              true,
              null,
              true,
              xmlIndentation
            )) |!>
            (_.setResult(new StreamResult(outputStream)))
        )

      executeQuery(connection, sql, List(StatementPart("", setters))) { rs =>

        import org.orbeon.oxf.xml.XMLReceiverSupport._

        var position = 0

        withDocument {
          withElement(
            "documents",
            atts = List(
              "application-name" -> app,
              "form-name"        -> form,
              "document-id"      -> documentId,
              "total"            -> searchCount.toString,
              "page-size"        -> request.pageSize.toString,
              "page-number"      -> request.pageNumber.toString,
            )
          ) {
            while(rs.next()) {

              if (position == 0) {
                receiver.addAttribute("", "form-version", "form-version", rs.getString("form_version"))
                receiver.addAttribute("", "created-time", "created-time", DateUtils.formatIsoDateTimeUtc(rs.getTimestamp("created").getTime))
                receiver.addAttribute("", "created-username", "created-username", "TODO")
              }

              val userAndGroup = UserAndGroup.fromStrings(rs.getString("username"), rs.getString("groupname"))
              val organization = OrganizationSupport.readFromResultSet(connection, rs).map(_._2)

              element(
                "document",
                atts =
                  (hasStage list ("stage" -> rs.getString("stage").trimAllToEmpty)) :::
                  List(
                    "modified-time"     -> DateUtils.formatIsoDateTimeUtc(rs.getTimestamp("last_modified_time").getTime),
                    "modified-username" -> rs.getString("last_modified_by").trimAllToEmpty,
                    "owner-username"    -> userAndGroup.map(_.username).getOrElse(""),
                    "owner-group"       -> userAndGroup.flatMap(_.groupname).getOrElse(""),
                    "deleted"           -> (rs.getString("deleted") == "Y").toString,
                  )
              )

              position += 1
            }
          }
        }
      }
    }
  }
}
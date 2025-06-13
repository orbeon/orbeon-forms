package org.orbeon.oxf.fr.persistence.proxy

import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Response}
import org.orbeon.oxf.fr.FormRunnerPersistence.DataXml
import org.orbeon.oxf.fr.permission.{AnyOperation, Operation, Operations, SpecificOperations}
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.persistence.api.PersistenceApi.SearchPageSize
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils.PersistenceBase
import org.orbeon.oxf.fr.{AppForm, FormRunner, SearchVersion, Version}
import org.orbeon.oxf.http.{HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.util.PathUtils.PathOps
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, CoreCrossPlatformSupportTrait, IndentedLogger, JvmUrlEncoderDecoder, Logging, PathUtils}
import org.orbeon.oxf.util.Logging.*
import org.orbeon.scaxon.NodeConversions.elemToDocumentInfo


object BatchDelete {

  private implicit val coreCrossPlatformSupport: CoreCrossPlatformSupportTrait = CoreCrossPlatformSupport

  def process(
    request       : Request,
    response      : Response,
    appForm       : AppForm,
  )(implicit
    indentedLogger: IndentedLogger
  ): Unit = {

    val incomingVersion =
      request
        .getFirstHeaderIgnoreCase(Version.OrbeonFormDefinitionVersion)
        .map(_.toInt)
        .getOrElse(throw HttpStatusCodeException(StatusCode.BadRequest))

    val query =
      elemToDocumentInfo(
        <search>
          <query/>
          <query metadata="last-modified" match="gte" sort="asc"/>
          <page-size/>
          <page-number/>
          <operations any-of="delete"/>
        </search>
      )

    // Process a single batch with the same size, at most, as a search page size
    def processBatch(): Int = {

      PersistenceApi.search(
        appForm             = appForm,
        searchVersion       = SearchVersion.Specific(incomingVersion),
        isInternalAdminUser = false,
        searchQueryOpt      = Some(query),
        returnDetails       = false,
      )
      .take(SearchPageSize)
      .map { searchResult =>

        val dataPathQuery =
          PathUtils.recombineQuery(
            PersistenceBase.dropTrailingSlash ::
            "crud"                            ::
            FormRunner.createFormDataBasePathNoPrefix(
              appForm,
              version       = None,
              isDraft       = false,
              documentIdOpt = Some(searchResult.documentId)
            )                                 ::
            DataXml                           ::
            Nil mkString "/",
            Nil
          )

        PersistenceApi.doDelete(
          path                = dataPathQuery,
          modifiedTimeOpt     = None,
          forceDelete         = false,
          isInternalAdminUser = false,
        )
      }
      .size
    }

    // Continue processing batches until no more results are returned
    val totalDeleted: Int =
      Iterator
        .iterate(processBatch())(_ => processBatch())
        .takeWhile(_ > 0)
        .sum

    debug(s"Total batch deleted: $totalDeleted", List("app" -> appForm.app, "form" -> appForm.form, "version" -> incomingVersion.toString))

    response.setStatus(StatusCode.Ok)
  }

  private def canDelete(operations: Operations): Boolean =
    operations match {
      case AnyOperation            => true
      case SpecificOperations(ops) => ops(Operation.Delete)
    }
}

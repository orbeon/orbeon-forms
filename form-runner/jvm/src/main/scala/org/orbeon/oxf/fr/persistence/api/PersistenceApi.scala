package org.orbeon.oxf.fr.persistence.api

import org.orbeon.oxf.fr.persistence.proxy.PersistenceProxyProcessor
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging.*
import org.orbeon.scaxon.SimplePath.*

import java.time.Instant
import java.util as ju


object PersistenceApi extends PersistenceApiTrait {

  case class MetadataDetails(
    appName         : String,
    formName        : String,
    formVersion     : Int,
    lastModifiedTime: Instant
  )

  // Unlike other functions in `PersistenceApiTrait`, this calls `PersistenceProxyProcessor` directly.
  def getFormMetadata(
    app                   : Option[String],
    form                  : Option[String],
    incomingHeaders       : ju.Map[String, Array[String]],
    allVersions           : Boolean,
    allForms              : Boolean,
    ignoreAdminPermissions: Boolean
  )(implicit
    logger         : IndentedLogger
  ): Iterator[MetadataDetails] = {

    debug(s"calling form metadata API")

    val formMetadataDocElem =
      PersistenceProxyProcessor.callPublishedFormsMetadata(
        makeOutgoingRequest(HttpMethod.GET, incomingHeaders, List(
          "all-forms"                -> allForms.toString,
          "all-versions"             -> allVersions.toString,
          "ignore-admin-permissions" -> ignoreAdminPermissions.toString,
        )),
        app        = app,
        form       = form
      )

    (formMetadataDocElem / "form").iterator map { formElem =>
      MetadataDetails(
        appName          = formElem.elemValue("application-name"),
        formName         = formElem.elemValue("form-name"),
        formVersion      = RelationalUtils.parsePositiveIntParamOrThrow(formElem.elemValueOpt("form-version"), 1),
        lastModifiedTime = Instant.parse(formElem.elemValue("last-modified-time"))
      )
    }
  }
}

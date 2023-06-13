package org.orbeon.oxf.fr.persistence.api

import org.orbeon.oxf.fr.persistence.proxy.PersistenceProxyProcessor
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging._
import org.orbeon.scaxon.SimplePath._

import java.time.Instant
import java.{util => ju}


object PersistenceApi extends PersistenceApiTrait {

  case class MetadataDetails(
    appName         : String,
    formName        : String,
    formVersion     : Int,
    lastModifiedTime: Instant
  )

  def getFormMetadata(
    app            : Option[String],
    form           : Option[String],
    incomingHeaders: ju.Map[String, Array[String]],
    allVersions    : Boolean)(implicit
    logger     : IndentedLogger
  ): Iterator[MetadataDetails] = {

    debug(s"calling form metadata API")

    val formMetadataDocElem =
      PersistenceProxyProcessor.callPublishedFormsMetadata(
        makeOutgoingRequest(HttpMethod.GET, incomingHeaders, List(
          "all-forms"    -> "false",
          "all-versions" -> allVersions.toString,
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

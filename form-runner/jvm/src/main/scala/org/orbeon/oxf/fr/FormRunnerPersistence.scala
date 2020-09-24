/**
 * Copyright (C) 2013 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr

import java.io.File
import java.net.URI
import java.{util => ju}

import enumeratum.EnumEntry.Lowercase
import enumeratum._
import org.orbeon.dom.QName
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.fr.FormRunner.properties
import org.orbeon.oxf.fr.persistence.relational.Version.OrbeonFormDefinitionVersion
import org.orbeon.oxf.http.Headers._
import org.orbeon.oxf.http.HttpMethod.GET
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.xforms.analysis.model.ValidationLevel
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

sealed trait FormOrData extends EnumEntry with Lowercase

object FormOrData extends Enum[FormOrData] {

  val values = findValues

  case object Form extends FormOrData
  case object Data extends FormOrData
}

object FormRunnerPersistenceJava {
  //@XPathFunction
  def providerDataFormatVersion(app: String, form: String): String =
    FormRunnerPersistence.providerDataFormatVersionOrThrow(app, form).entryName

  //@XPathFunction
  def findProvider(app: String, form: String, formOrData: String): Option[String] =
    FormRunnerPersistence.findProvider(app, form, FormOrData.withName(formOrData))
}

sealed abstract class DataFormatVersion(override val entryName: String) extends EnumEntry

object DataFormatVersion extends Enum[DataFormatVersion] {

  sealed trait MigrationVersion extends DataFormatVersion

  val values = findValues

  case object V400   extends DataFormatVersion("4.0.0")
  case object V480   extends DataFormatVersion("4.8.0")    with MigrationVersion
  case object V20191 extends DataFormatVersion("2019.1.0") with MigrationVersion

  val Edge: DataFormatVersion = values.last

  def isLatest(dataFormatVersion: DataFormatVersion): Boolean =
    dataFormatVersion == Edge

  def withNameIncludeEdge(s: String): DataFormatVersion =
    if (s == "edge")
      Edge
    else
      withName(s)
}

object FormRunnerPersistence {

  val DataFormatVersionName               = "data-format-version"
  val PruneMetadataName                   = "prune-metadata"
  val ShowProgressName                    = "show-progress"
  val FormTargetName                      = "formtarget"
  val NonRelevantName                     = "nonrelevant"

  val PersistenceDefaultDataFormatVersion = DataFormatVersion.V400

  val CRUDBasePath                        = "/fr/service/persistence/crud"
  val FormMetadataBasePath                = "/fr/service/persistence/form"
  val PersistencePropertyPrefix           = "oxf.fr.persistence"
  val PersistenceProviderPropertyPrefix   = PersistencePropertyPrefix + ".provider"

  val NewFromServiceUriPropertyPrefix     = "oxf.fr.detail.new.service"
  val NewFromServiceUriProperty           = NewFromServiceUriPropertyPrefix + ".uri"
  val NewFromServiceParamsProperty        = NewFromServiceUriPropertyPrefix + ".passing-request-parameters"

  val StandardProviderProperties          = Set("uri", "autosave", "active", "permissions")
  val AttachmentAttributeNames            = List("filename", "mediatype", "size")

  def findProvider(app: String, form: String, formOrData: FormOrData): Option[String] = {
    val providerProperty = PersistenceProviderPropertyPrefix :: app :: form :: formOrData.entryName :: Nil mkString "."
    properties.getNonBlankString(providerProperty)
  }

  def providerPropertyAsURL(provider: String, property: String): String =
    properties.getStringOrURIAsString(PersistencePropertyPrefix :: provider :: property :: Nil mkString ".")

  def getPersistenceURLHeaders(app: String, form: String, formOrData: FormOrData): (String, Map[String, String]) = {

    require(augmentString(app).nonEmpty) // Q: why `augmentString`?
    require(augmentString(form).nonEmpty)

    getPersistenceURLHeadersFromProvider(findProvider(app, form, formOrData).get)
  }

  def getPersistenceURLHeadersFromProvider(provider: String): (String, Map[String, String]) = {

    val propertyPrefix = PersistencePropertyPrefix :: provider :: Nil mkString "."
    val propertyPrefixTokenCount = propertyPrefix.splitTo[List](".").size

    // Build headers map
    val headers = (
      for {
        propertyName   <- properties.propertiesStartsWith(propertyPrefix, matchWildcards = false)
        lowerSuffix    <- propertyName.splitTo[List](".").drop(propertyPrefixTokenCount).headOption
        if ! StandardProviderProperties(lowerSuffix)
        headerName     = "Orbeon-" + capitalizeSplitHeader(lowerSuffix)
        headerValue    <- properties.getObjectOpt(propertyName)
      } yield
        headerName -> headerValue.toString) toMap

    val uri = Option(providerPropertyAsURL(provider, "uri")) getOrElse
      (throw new OXFException(s"no base URL specified for requested persistence provider `$provider` (check properties)"))

    (uri, headers)
  }

  def getPersistenceHeadersAsXML(app: String, form: String, formOrData: FormOrData): DocumentInfo = {

    val (_, headers) = getPersistenceURLHeaders(app, form, formOrData)

    // Build headers document
    val headersXML =
      <headers>{
        for {
          (name, value) <- headers
        } yield
          <header><name>{name.escapeXmlMinimal}</name><value>{value.escapeXmlMinimal}</value></header>
      }</headers>.toString

    // Convert to TinyTree
    TransformerUtils.stringToTinyTree(XPath.GlobalConfiguration, headersXML, false, false)
  }

  def providerDataFormatVersionOrThrow(app: String, form: String): DataFormatVersion = {

    val provider =
      findProvider(app, form, FormOrData.Data) getOrElse
        (throw new IllegalArgumentException(s"no provider property configuration found for `$app/$form`"))

    val dataFormatVersionString =
      providerPropertyAsString(provider, DataFormatVersionName, PersistenceDefaultDataFormatVersion.entryName)

    DataFormatVersion.withNameOption(dataFormatVersionString) match {
      case Some(dataFormatVersion) =>
        dataFormatVersion
      case None =>
        throw new IllegalArgumentException(
          s"`${fullProviderPropertyName(provider, DataFormatVersionName)}` property is set to `$dataFormatVersionString` but must be one of ${DataFormatVersion.values map (_.entryName) mkString ("`", "`, `", "`")}"
        )
    }
  }

  private def fullProviderPropertyName(provider: String, property: String) =
    PersistencePropertyPrefix :: provider :: property :: Nil mkString "."

  private def providerPropertyAsString(provider: String, property: String, default: String) =
    properties.getString(fullProviderPropertyName(provider, property), default)

  // NOTE: We generate .bin, but sample data can contain other extensions
  private val RecognizedAttachmentExtensions = Set("bin", "jpg", "jpeg", "gif", "png", "pdf")
}

trait FormRunnerPersistence {

  import FormRunnerPersistence._
  import org.orbeon.oxf.fr.FormRunner._

  // Check whether a value correspond to an uploaded file
  //
  // For this to be true
  // - the protocol must be file:
  // - the URL must have a valid signature
  //
  // This guarantees that the local file was in fact placed there by the upload control, and not tampered with.
  def isUploadedFileURL(value: String): Boolean =
    value.startsWith("file:/") && XFormsUploadControl.verifyMAC(value)

  //@XPathFunction
  def createFormDataBasePath(app: String, form: String, isDraft: Boolean, document: String): String =
    CRUDBasePath :: app :: form :: (if (isDraft) "draft" else "data") :: document :: "" :: Nil mkString "/"

  //@XPathFunction
  def createFormDefinitionBasePath(app: String, form: String): String =
    CRUDBasePath :: app :: form :: FormOrData.Form.entryName :: "" :: Nil mkString "/"

  //@XPathFunction
  def createFormMetadataPathAndQuery(app: String, form: String, allVersions: Boolean, allForms: Boolean): String =
    PathUtils.recombineQuery(
      FormMetadataBasePath :: app :: form :: Nil mkString "/",
      List(
        "all-versions" -> allVersions.toString,
        "all-forms"    -> allForms.toString
      )
    )

  //@XPathFunction
  def createNewFromServiceUrlOrEmpty(app: String, form: String): String =
    properties.getStringOrURIAsStringOpt(NewFromServiceUriProperty :: app :: form :: Nil mkString ".") match {
      case Some(serviceUrl) =>

        val requestedParams =
          properties.getString(NewFromServiceParamsProperty :: app :: form :: Nil mkString ".", "").splitTo[List]()

        val docParams = inScopeContainingDocument.getRequestParameters

        val nameValueIt =
          for {
            name   <- requestedParams.iterator
            values <- docParams.get(name).iterator
            value  <- values.iterator
          } yield
            name -> value

        PathUtils.recombineQuery(
          pathQuery = serviceUrl,
          params    = nameValueIt
        )

      case None => null
    }

  // Whether the given path is an attachment path (ignoring an optional query string)
  def isAttachmentURLFor(basePath: String, url: String): Boolean =
    url.startsWith(basePath) && (splitQuery(url)._1.splitTo[List](".").lastOption exists RecognizedAttachmentExtensions)

  // For a given attachment path, return the filename
  def getAttachmentPathFilenameRemoveQuery(pathQuery: String): String =
    splitQuery(pathQuery)._1.split('/').last

  def providerPropertyAsBoolean(provider: String, property: String, default: Boolean): Boolean =
    properties.getBoolean(PersistencePropertyPrefix :: provider :: property :: Nil mkString ".", default)

  //@XPathFunction
  def isAutosaveSupported(app: String, form: String): Boolean =
    providerPropertyAsBoolean(findProvider(app, form, FormOrData.Data).get, "autosave", default = false)

  //@XPathFunction
  def isOwnerGroupPermissionsSupported(app: String, form: String): Boolean =
    providerPropertyAsBoolean(findProvider(app, form, FormOrData.Data).get, "permissions", default = false)

  //@XPathFunction
  def isFormDefinitionVersioningSupported(app: String, form: String): Boolean =
    providerPropertyAsBoolean(findProvider(app, form, FormOrData.Form).get, "versioning", default = false)

  //@XPathFunction
  def isLeaseSupported(app: String, form: String): Boolean =
    providerPropertyAsBoolean(findProvider(app, form, FormOrData.Data).get, "lease", default = false)

  def isActiveProvider(provider: String): Boolean =
    providerPropertyAsBoolean(provider, "active", default = true)

  // Reads a document forwarding headers. The URL is rewritten, and is expected to be like "/fr/…"
  def readDocument(urlString: String)(implicit logger: IndentedLogger): Option[DocumentInfo] = {

    implicit val externalContext          = NetUtils.getExternalContext
    implicit val coreCrossPlatformSupport = CoreCrossPlatformSupport

    val request = externalContext.getRequest

    val rewrittenURLString =
      URLRewriterUtils.rewriteServiceURL(
        request,
        urlString,
        URLRewriter.REWRITE_MODE_ABSOLUTE
      )

    val url = new URI(rewrittenURLString)

    val headers = Connection.buildConnectionHeadersCapitalizedIfNeeded(
      url              = url,
      hasCredentials   = false,
      customHeaders    = Map(),
      headersToForward = Connection.headersToForwardFromProperty,
      cookiesToForward = Connection.cookiesToForwardFromProperty,
      Connection.getHeaderFromRequest(request)
    )

    val cxr = Connection.connectNow(
      method      = GET,
      url         = url,
      credentials = None,
      content     = None,
      headers     = headers,
      loadState   = true,
      saveState   = true,
      logBody     = false
    )

    // Libraries are typically not present. In that case, the persistence layer should return a 404 (thus the test
    // on status code),  but the MySQL persistence layer returns a [200 with an empty body][1] (thus a body is
    // required).
    //   [1]: https://github.com/orbeon/orbeon-forms/issues/771
    ConnectionResult.tryWithSuccessConnection(cxr, closeOnSuccess = true) { is =>
      // do process XInclude, so FB's model gets included
      TransformerUtils.readTinyTree(XPath.GlobalConfiguration, is, rewrittenURLString, true, false)
    } toOption
  }

  // Retrieves a form definition from the persistence layer
  def readPublishedForm(appName: String, formName: String)(implicit logger: IndentedLogger): Option[DocumentInfo] =
    readDocument(createFormDefinitionBasePath(appName, formName) + "form.xhtml")

  // Retrieves the metadata for a form from the persistence layer
  def readFormMetadata(appName: String, formName: String)(implicit logger: IndentedLogger): Option[DocumentInfo] =
    readDocument(
      createFormMetadataPathAndQuery(
        app         = appName,
        form        = formName,
        allVersions = false,
        allForms    = true
      )
    )

  // Whether the form data is valid as per the error summary
  // We use instance('fr-error-summary-instance')/valid and not valid() because the instance validity may not be
  // reflected with the use of XBL components.
  def dataValid: Boolean =
    errorSummaryInstance.rootElement / "valid" === "true"

  // Return the number of failed validations captured by the error summary for the given level
  def countValidationsByLevel(level: ValidationLevel): Int =
    (errorSummaryInstance.rootElement / "counts" /@ level.entryName stringValue).toInt

  // Return whether the data is saved
  def isFormDataSaved: Boolean =
    persistenceInstance.rootElement / "data-status" === "clean"

  // Return all nodes which refer to data attachments
  //@XPathFunction
  def collectDataAttachmentNodesJava(data: NodeInfo, fromBasePath: String): ju.List[NodeInfo] =
    collectAttachments(data.getDocumentRoot, fromBasePath, fromBasePath, forceAttachments = true)._1.asJava

  //@XPathFunction
  def clearMissingUnsavedDataAttachmentReturnFilenamesJava(data: NodeInfo): ju.List[String] = {

    val FormRunnerParams(app, form, _, documentIdOpt, mode) = FormRunnerParams()

    val unsavedAttachmentHolders =
      documentIdOpt match {
        case Some(documentId) if isNewOrEditMode(mode) =>
          // NOTE: `basePath` is not relevant in our use of `collectAttachments` here, but
          // we don't just want to pass a magic string in. So we still compute `basePath`.
          val basePath = createFormDataBasePath(app, form, isDraft = false, documentId)
          collectAttachments(data.getDocumentRoot, basePath, basePath, forceAttachments = false)._1
        case _ =>
          Nil
      }

    val filenames =
      for {
        holder   <- unsavedAttachmentHolders
        filename = holder attValue "filename"
        if ! new File(URLFactory.createURL(splitQuery(holder.stringValue)._1).getFile).exists()
      } yield {

        setvalue(holder, "")

        AttachmentAttributeNames foreach { attName =>
          setvalue(holder /@ attName, "")
        }

        filename
      }

    filenames flatMap (_.trimAllToOpt) asJava
  }

  def collectAttachments(
    data             : DocumentInfo,
    fromBasePath     : String,
    toBasePath       : String,
    forceAttachments : Boolean
  ): (Seq[NodeInfo], Seq[String], Seq[String]) = (
    for {
      holder        <- data descendant Node
      if holder.isAttribute || holder.isElement && ! holder.hasChildElement
      beforeURL     = holder.stringValue.trimAllToEmpty
      isUploaded    = isUploadedFileURL(beforeURL)
      if isUploaded ||
        isAttachmentURLFor(fromBasePath, beforeURL) && ! isAttachmentURLFor(toBasePath, beforeURL) ||
        isAttachmentURLFor(toBasePath, beforeURL) && forceAttachments
    } yield {
      // Here we could decide to use a nicer extension for the file. But since initially the filename comes from
      // the client, it cannot be trusted, nor can its mediatype. A first step would be to do content-sniffing to
      // determine a more trusted mediatype. A second step would be to put in an API for virus scanning. For now,
      // we just use .bin as an extension.
      val filename =
        if (isUploaded)
          SecureUtils.randomHexId + ".bin"
        else
          getAttachmentPathFilenameRemoveQuery(beforeURL)

      val afterURL =
        toBasePath + filename

      (holder, beforeURL, afterURL)
    }
  ).unzip3

  def putWithAttachments(
    data              : DocumentInfo,
    migrate           : DocumentInfo => DocumentInfo,
    toBaseURI         : String,
    fromBasePath      : String,
    toBasePath        : String,
    filename          : String,
    commonQueryString : String,
    forceAttachments  : Boolean,
    username          : Option[String] = None,
    password          : Option[String] = None,
    formVersion       : Option[String] = None,
    workflowStage     : Option[String] = None
  ): (Seq[String], Seq[String], Int) = {

    val migratedData = migrate(data)

    // Find all instance nodes containing file URLs we need to upload
    val (uploadHolders, beforeURLs, afterURLs) =
      collectAttachments(migratedData, fromBasePath, toBasePath, forceAttachments)

    def saveAllAttachments(): Unit = {
      val holdersAfterURLs = uploadHolders.zip(afterURLs)
      holdersAfterURLs foreach { case (holder, resource) =>
        // Copy holder, so we're not blocked in case there are other background uploads
        val holderCopy = TransformerUtils.extractAsMutableDocument(holder).rootElement
        sendThrowOnError("fr-create-update-attachment-submission", Map(
          "holder" -> Some(holderCopy),
          "resource" -> Some(PathUtils.appendQueryString(toBaseURI + resource, commonQueryString)),
          "username" -> username,
          "password" -> password,
          "form-version" -> formVersion
        )
        )
      }
    }

    def updateAttachmentPaths() =
      uploadHolders zip afterURLs foreach { case (holder, resource) =>
        setvalue(holder, resource)
      }

    def rollbackAttachmentPaths() =
      uploadHolders zip beforeURLs foreach { case (holder, resource) =>
        setvalue(holder, resource)
      }

    def saveXmlData(migratedData: DocumentInfo) =
      sendThrowOnError("fr-create-update-submission", Map(
        "holder"         -> Some(migratedData.rootElement),
        "resource"       -> Some(PathUtils.appendQueryString(toBaseURI + toBasePath + filename, commonQueryString)),
        "username"       -> username,
        "password"       -> password,
        "form-version"   -> formVersion,
        "workflow-stage" -> workflowStage
      ))

    // First process attachments
    saveAllAttachments()

    val versionOpt =
      try {

        // Before saving data, update attachment paths
        updateAttachmentPaths()

        // Save and try to retrieve returned version
        for {
          done     <- saveXmlData(migratedData) // https://github.com/orbeon/orbeon-forms/issues/3629
          headers  <- done.headers
          versions <- headers collectFirst { case (name, values) if name equalsIgnoreCase OrbeonFormDefinitionVersion => values }
          version  <- versions.headOption
        } yield
          version

      } catch {
        case NonFatal(e) =>
          // In our persistence implementation, we do not remove attachments if saving the data fails.
          // However, some custom persistence implementations do. So we don't think we can assume that
          // attachments have been saved. So we rollback attachment paths in the data in this case.
          // This will cause attachments to be saved again even if they actually have already been saved.
          // It is not ideal, but will not lead to data loss. See also:
          //
          // - https://github.com/orbeon/orbeon-forms/issues/606
          // - https://github.com/orbeon/orbeon-forms/issues/3084
          // - https://github.com/orbeon/orbeon-forms/issues/3301
          rollbackAttachmentPaths()
          throw e
      }

    (beforeURLs, afterURLs, versionOpt map (_.toInt) getOrElse 1)
  }

  def userOwnsLeaseOrNoneRequired: Boolean =
    persistenceInstance.rootElement / "lease-owned-by-current-user" === "true"
}

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


import cats.syntax.option._

import enumeratum.EnumEntry.Lowercase
import enumeratum._
import org.orbeon.scaxon
import org.orbeon.dom.QName
import org.orbeon.oxf.common
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.fr.persistence.relational.Version
import org.orbeon.oxf.externalcontext.{ExternalContext, URLRewriter}
import org.orbeon.oxf.fr.persistence.relational.Version.OrbeonFormDefinitionVersion
import org.orbeon.oxf.http.{BasicCredentials, HttpMethod, StreamedContent}
import org.orbeon.oxf.http.Headers._
import org.orbeon.oxf.http.HttpMethod.{GET, PUT}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.util.CoreCrossPlatformSupport.properties
import org.orbeon.oxf.xforms.NodeInfoFactory
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.xforms.analysis.model.ValidationLevel
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.saxon.value.StringValue
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.NodeInfoConversions
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.{BasicNamespaceMapping, XFormsCrossPlatformSupport}

import java.net.URI
import java.{util => ju}
import java.io.InputStream
import scala.jdk.CollectionConverters._
import scala.util.Try


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

  val FormPath                            = """/fr/service/persistence(/crud/([^/]+)/([^/]+)/form/([^/]+))""".r
  val DataPath                            = """/fr/service/persistence(/crud/([^/]+)/([^/]+)/(data|draft)/([^/]+)/([^/]+))""".r
  val DataCollectionPath                  = """/fr/service/persistence(/crud/([^/]+)/([^/]+)/data/)""".r
  val SearchPath                          = """/fr/service/persistence(/search/([^/]+)/([^/]+))""".r
  val ReEncryptAppFormPath                = """/fr/service/persistence(/reencrypt/([^/]+)/([^/]+))""".r
  val PublishedFormsMetadataPath          = """/fr/service/persistence/form(/([^/]+)(?:/([^/]+))?)?""".r
  val ReindexPath                         =   "/fr/service/persistence/reindex"
  val ReEncryptStatusPath                 =   "/fr/service/persistence/reencrypt"

  val DataFormatVersionName               = "data-format-version"
  val FormDefinitionFormatVersionName     = "form-definition-format-version"
  val PruneMetadataName                   = "prune-metadata"
  val ShowProgressName                    = "show-progress"
  val FormTargetName                      = "formtarget"
  val NonRelevantName                     = "nonrelevant"

  val PersistenceDefaultDataFormatVersion = DataFormatVersion.V400

  val OrbeonPathToHolder                  = "Orbeon-Path-To-Holder"
  val OrbeonPathToHolderLower             = OrbeonPathToHolder.toLowerCase
  val OrbeonDidEncryptHeader              = "Orbeon-Did-Encrypt"
  val OrbeonDidEncryptHeaderLower         = OrbeonDidEncryptHeader.toLowerCase
  val OrbeonDecryptHeader                 = "Orbeon-Decrypt"
  val OrbeonDecryptHeaderLower            = OrbeonDecryptHeader.toLowerCase
  val AttachmentEncryptedAttributeName    = QName("attachment-encrypted", XMLNames.FRNamespace)
  val ValueEncryptedAttributeName         = QName("value-encrypted"     , XMLNames.FRNamespace)
  val LagacyValueEncryptedAttributeName   = QName("encrypted")
  val AttachmentEncryptedAttribute        = NodeInfoFactory.attributeInfo(AttachmentEncryptedAttributeName, true.toString)
  val ValueEncryptedAttribute             = NodeInfoFactory.attributeInfo(ValueEncryptedAttributeName     , true.toString)

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

  def getPersistenceHeadersAsXML(app: String, form: String, formOrData: FormOrData): DocumentNodeInfoType = {

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
    XFormsCrossPlatformSupport.stringToTinyTree(
      XPath.GlobalConfiguration,
      headersXML,
      handleXInclude = false,
      handleLexical  = false
    )
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

  // Parse a list of product versions as found in a form definition and find the closest data format associated
  // with the form definition.
  // See `SimpleDataMigrationTest` for example for versions. They must start with two integers separated by `.`.
  def findFormDefinitionFormatFromStringVersions(versions: collection.Seq[String]): Option[DataFormatVersion] = {

    def parseVersions: Seq[(Int, Int)] =
      for {
        version <- versions
        trimmed <- version.trimAllToOpt // DEBATE: We shouldn't have to trim or ignore blank lines.
        mm      <- common.VersionSupport.majorMinor(trimmed)
      } yield
        mm

    val allPossibleDataFormatsPairs =
      for {
        value <- DataFormatVersion.values
        mm    <- common.VersionSupport.majorMinor(value.entryName)
      } yield
        value -> mm

    val allDistinctVersionsPassed = parseVersions.distinct

    val maxVersionPassedOpt =
      allDistinctVersionsPassed.nonEmpty option allDistinctVersionsPassed.max

    import scala.math.Ordering.Implicits._

    for {
      maxVersionPassed <- maxVersionPassedOpt
      (closest, _)     <- allPossibleDataFormatsPairs.filter(_._2 <= maxVersionPassed).lastOption
    } yield
      closest
  }

  private def fullProviderPropertyName(provider: String, property: String) =
    PersistencePropertyPrefix :: provider :: property :: Nil mkString "."

  private def providerPropertyAsString(provider: String, property: String, default: String) =
    properties.getString(fullProviderPropertyName(provider, property), default)

  // NOTE: We generate .bin, but sample data can contain other extensions
  private val RecognizedAttachmentExtensions = Set("bin", "jpg", "jpeg", "gif", "png", "pdf")
}

trait FormRunnerPersistence {

  private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(FormRunnerPersistence.getClass))

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

  // `documentOrEmpty` can be empty and if so won't be included. Ideally should be `Option[String]`.
  //@XPathFunction
  def createFormDataBasePath(app: String, form: String, isDraft: Boolean, documentOrEmpty: String): String =
    CRUDBasePath :: app :: form :: (if (isDraft) "draft" else "data") :: documentOrEmpty.trimAllToOpt.toList ::: "" :: Nil mkString "/"

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

  // 2020-12-23: If the provider is not configured, return `false` (for offline FIXME).
  //@XPathFunction
  def isAutosaveSupported(app: String, form: String): Boolean =
    findProvider(app, form, FormOrData.Data) exists (providerPropertyAsBoolean(_, "autosave", default = false))

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

  private def readConnectionResult(
    method          : HttpMethod,
    urlString       : String,
    customHeaders   : Map[String, List[String]]
  ): ConnectionResult = {

    implicit val externalContext         : ExternalContext = CoreCrossPlatformSupport.externalContext
    implicit val coreCrossPlatformSupport: CoreCrossPlatformSupport.type = CoreCrossPlatformSupport

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
      customHeaders    = customHeaders,
      headersToForward = Connection.headersToForwardFromProperty,
      cookiesToForward = Connection.cookiesToForwardFromProperty,
      Connection.getHeaderFromRequest(request)
    )

    Connection.connectNow(
      method      = method,
      url         = url,
      credentials = None,
      content     = None,
      headers     = headers,
      loadState   = true,
      saveState   = true,
      logBody     = false
    )
  }

  // Reads a document forwarding headers. The URL is rewritten, and is expected to be like "/fr/…"
  def readDocument(
    urlString       : String,
    customHeaders   : Map[String, List[String]]
  ): Option[DocumentNodeInfoType] = {

    val cxr = readConnectionResult(HttpMethod.GET, urlString, customHeaders)

    // Libraries are typically not present. In that case, the persistence layer should return a 404 (thus the test
    // on status code),  but the MySQL persistence layer returns a [200 with an empty body][1] (thus a body is
    // required).
    //   [1]: https://github.com/orbeon/orbeon-forms/issues/771
    ConnectionResult.tryWithSuccessConnection(cxr, closeOnSuccess = true) { is =>
      XFormsCrossPlatformSupport.readTinyTree(
        XPath.GlobalConfiguration,
        is,
        urlString,
        handleXInclude = true, // do process XInclude, so FB's model gets included
        handleLexical  = false
      )
    } toOption
  }

  def readHeaders(
    urlString       : String,
    customHeaders   : Map[String, List[String]])(
    implicit logger : IndentedLogger
  ): Map[String, List[String]] = {
    val cxr = readConnectionResult(HttpMethod.HEAD, urlString, customHeaders)
    cxr.headers
  }

  // Retrieves a form definition from the persistence layer
  def readPublishedForm(
    appName         : String,
    formName        : String,
    version         : FormDefinitionVersion)(
    implicit logger : IndentedLogger
  ): Option[DocumentNodeInfoType] = {
    val path = createFormDefinitionBasePath(appName, formName) + "form.xhtml"
    val customHeaders = version match {
      case FormDefinitionVersion.Latest            => Map.empty[String, List[String]]
      case FormDefinitionVersion.Specific(version) => Map(OrbeonFormDefinitionVersion -> List(version.toString))
    }
    readDocument(path, customHeaders)
  }

  // Retrieves from the persistence layer the metadata for a form, return an `Option[<form>]`
  def readFormMetadataOpt(
    appName  : String,
    formName : String,
    version  : FormDefinitionVersion)(implicit
    logger   : IndentedLogger
  ): Option[NodeInfo] = {
    val formsDoc = readDocument(
      createFormMetadataPathAndQuery(
        app         = appName,
        form        = formName,
        allVersions = version != FormDefinitionVersion.Latest,
        allForms    = true
      ),
      Map.empty
    )

    val formElements = formsDoc.get / "forms" / "form"
    val formByVersion = version match {
      case FormDefinitionVersion.Specific(v) =>
        formElements.find(_.child("form-version").stringValue == v.toString)
      case FormDefinitionVersion.Latest =>
        None
    }

    formByVersion.orElse(formElements.headOption)
  }

  def readDocumentFormVersion(
    appName         : String,
    formName        : String,
    documentId      : String
  ): Option[Int] = {
    val path = createFormDataBasePath(appName, formName, isDraft = false, documentId) + "data.xml"
    val headers = readHeaders(path, Map.empty)
    headers.get(Version.OrbeonFormDefinitionVersion).map(_.head).map(_.toInt)
  }

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
    collectAttachments(data.getRoot, List(fromBasePath), fromBasePath, forceAttachments = true).map(_.holder).asJava

  //@XPathFunction
  def clearMissingUnsavedDataAttachmentReturnFilenamesJava(data: NodeInfo): ju.List[String] = {

    val FormRunnerParams(app, form, _, documentIdOpt, mode) = FormRunnerParams()

    val unsavedAttachmentHolders =
      documentIdOpt match {
        case Some(documentId) if isNewOrEditMode(mode) =>
          // NOTE: `basePath` is not relevant in our use of `collectAttachments` here, but
          // we don't just want to pass a magic string in. So we still compute `basePath`.
          val basePath = createFormDataBasePath(app, form, isDraft = false, documentId)
          collectAttachments(data.getRoot, List(basePath), basePath, forceAttachments = false).map(_.holder)
        case _ =>
          Nil
      }

    val filenames =
      for {
        holder   <- unsavedAttachmentHolders
        filename = holder attValue "filename"
        if ! XFormsCrossPlatformSupport.attachmentFileExists(holder.stringValue)
      } yield {

        setvalue(holder, "")

        AttachmentAttributeNames foreach { attName =>
          setvalue(holder /@ attName, "")
        }

        filename
      }

    filenames flatMap (_.trimAllToOpt) asJava
  }

  case class AttachmentWithHolder(
    fromPath          : String,
    toPath            : String,
    holder            : NodeInfo
  )

  case class AttachmentWithEncryptedAtRest(
    fromPath          : String,
    toPath            : String,
    isEncryptedAtRest : Boolean
  )

  def collectAttachments(
    data             : NodeInfo,
    fromBasePaths    : Iterable[String],
    toBasePath       : String,
    forceAttachments : Boolean // `true` when pushing to/pulling from remote system or when using duplicate
  ): List[AttachmentWithHolder] = {
    for {
      holder        <- data.descendant(Node).toList
      if holder.isAttribute || holder.isElement && ! holder.hasChildElement
      beforeURL     = holder.stringValue.trimAllToEmpty
      isUploaded    = isUploadedFileURL(beforeURL)
      if isUploaded ||
        fromBasePaths.exists(isAttachmentURLFor(_, beforeURL)) && ! isAttachmentURLFor(toBasePath, beforeURL) ||
        isAttachmentURLFor(toBasePath, beforeURL) && forceAttachments
    } yield {
      // Here we could decide to use a nicer extension for the file. But since initially the filename comes from
      // the client, it cannot be trusted, nor can its mediatype. A first step would be to do content-sniffing to
      // determine a more trusted mediatype. A second step would be to put in an API for virus scanning. For now,
      // we just use .bin as an extension.
      val filename =
        if (isUploaded)
          CoreCrossPlatformSupport.randomHexId + ".bin"
        else
          getAttachmentPathFilenameRemoveQuery(beforeURL)

      val afterURL =
        toBasePath + filename

      AttachmentWithHolder(beforeURL, afterURL, holder)
    }
  }

  def putWithAttachments(
    liveData          : DocumentNodeInfoType,
    migrate           : Option[DocumentNodeInfoType => DocumentNodeInfoType],
    toBaseURI         : String,
    fromBasePaths     : Iterable[(String, Int)],
    toBasePath        : String,
    filename          : String,
    commonQueryString : String,
    forceAttachments  : Boolean,
    username          : Option[String] = None,
    password          : Option[String] = None,
    formVersion       : Option[String] = None,
    workflowStage     : Option[String] = None
  ): (Seq[String], Seq[String], Int) = {

    implicit val externalContext: ExternalContext = CoreCrossPlatformSupport.externalContext

    val savedData = migrate.map(_(liveData)).getOrElse(liveData)

    // Find all instance nodes containing file URLs we need to upload
    val attachmentsWithHolder =
      collectAttachments(savedData, fromBasePaths.map(_._1), toBasePath, forceAttachments)

    def updateHolder(holder: NodeInfo, afterURL: String, isEncryptedAtRest: Boolean): Unit = {
      setvalue(holder, afterURL)
      XFormsAPI.delete(holder /@ AttachmentEncryptedAttributeName)
      if (isEncryptedAtRest)
        XFormsAPI.insert(
          into   = holder,
          origin = AttachmentEncryptedAttribute
        )
    }

    def trySaveAllAttachments(): Try[List[AttachmentWithEncryptedAtRest]] =
      TryUtils.sequenceLazily(attachmentsWithHolder) { case AttachmentWithHolder(beforeUrl, afterUrl, migratedHolder) =>

        def rewriteServiceUrl(url: String) =
          URLRewriterUtils.rewriteServiceURL(
            externalContext.getRequest,
            url,
            URLRewriter.REWRITE_MODE_ABSOLUTE
          )

        val pathToHolder = migratedHolder.ancestorOrSelf(*).map(_.localname).reverse.drop(1).mkString("/")

        val attachmentVersionOpt =
          fromBasePaths collectFirst { case (path, version) if beforeUrl.startsWith(path) => version }

        // We used to use an XForms submission here which handled reading as well as writing. But that
        // didn't allow for passing HTTP headers when reading, and we need this for versioning headers.
        // So we now do all the work "natively", which is better anyway.
        // https://github.com/orbeon/orbeon-forms/issues/4919

        def connectGet: ConnectionResult =
          Connection.connectNow(
            method          = GET,
            url             = new URI(rewriteServiceUrl(beforeUrl)),
            credentials     = None,
            content         = None,
            headers         = Map(attachmentVersionOpt.toList map (v => OrbeonFormDefinitionVersion -> List(v.toString)): _*),
            loadState       = true,
            saveState       = true,
            logBody         = false
          )

        def connectPut(is: InputStream): ConnectionResult = {

          val headers =
            (formVersion.toList map (v => OrbeonFormDefinitionVersion -> List(v))) ::: // write all using the form definition version
            (OrbeonPathToHolder -> List(pathToHolder))                             ::
            Nil

          Connection.connectNow(
            method          = PUT,
            url             = new URI(rewriteServiceUrl(PathUtils.appendQueryString(toBaseURI + afterUrl, commonQueryString))),
            credentials     = username map (BasicCredentials(_, password, preemptiveAuth = false, domain = None)),
            content         = StreamedContent(is, ContentTypes.OctetStreamContentType.some, contentLength = None, title = None).some,
            headers         = headers.toMap,
            loadState       = true,
            saveState       = true,
            logBody         = false
          )
        }

        def getOrbeonDidEncryptHeader(cxr: ConnectionResult): Boolean = {
          val isEncryptedAtRest = cxr.headers.get(OrbeonDidEncryptHeader).exists(_.contains("true"))
          updateHolder(migratedHolder, afterUrl, isEncryptedAtRest)
          isEncryptedAtRest
        }

        for {
          successGetCxr     <- ConnectionResult.trySuccessConnection(connectGet)
          putCxr            <- ConnectionResult.tryBody(successGetCxr, closeOnSuccess = true)(connectPut)
          successPutCxr     <- ConnectionResult.trySuccessConnection(putCxr)
          isEncryptedAtRest <- ConnectionResult.tryBody(successPutCxr, closeOnSuccess = true)(_ => getOrbeonDidEncryptHeader(successPutCxr))
        } yield
          AttachmentWithEncryptedAtRest(beforeUrl, afterUrl, isEncryptedAtRest)
      }

    def saveXmlData(migratedData: DocumentNodeInfoType) =
      sendThrowOnError("fr-create-update-submission", Map(
        "holder"         -> Some(migratedData.rootElement),
        "resource"       -> Some(PathUtils.appendQueryString(toBaseURI + toBasePath + filename, commonQueryString)),
        "username"       -> username,
        "password"       -> password,
        "form-version"   -> formVersion,
        "workflow-stage" -> workflowStage
      ))

    // First upload the attachments
    val attachmentsWithEncryptedAtRest = trySaveAllAttachments().get

    val versionOpt =
      // Save and try to retrieve returned version
      for {
        done     <- saveXmlData(savedData) // https://github.com/orbeon/orbeon-forms/issues/3629
        headers  <- done.headers
        versions <- headers collectFirst { case (name, values) if name equalsIgnoreCase OrbeonFormDefinitionVersion => values }
        version  <- versions.headOption
      } yield
        version

    // In our persistence implementation, we do not remove attachments if saving the data fails.
    // However, some custom persistence implementations do. So we don't think we can assume that
    // attachments have been saved. So we only update the attachment paths in the data after all
    // the attachments have been successfully uploaded. This will cause attachments to be saved again
    // even if they actually have already been saved. It is not ideal, but will not lead to data loss.
    //
    // See also:
    // - https://github.com/orbeon/orbeon-forms/issues/606
    // - https://github.com/orbeon/orbeon-forms/issues/3084
    // - https://github.com/orbeon/orbeon-forms/issues/3301

    // If data isn't migrated, then it has already been updated (and doing it again would fail)
    // FIXME: if no migration happens *and* sending an attachment failed, then the URL will still updated in the data
    if (migrate.isDefined)
      attachmentsWithEncryptedAtRest.foreach {
        case AttachmentWithEncryptedAtRest(beforeURL, afterURL, isEncryptedAtRest) =>
          val holder = {
            scaxon.XPath.evalOne(
              item       = liveData,
              expr       = "//*[not(*)][xxf:trim() = $beforeURL]",
              namespaces = BasicNamespaceMapping.Mapping,
              variables  = Map("beforeURL" -> new StringValue(beforeURL))
            )(XFormsFunctionLibrary).asInstanceOf[NodeInfo]
          }
          updateHolder(holder, afterURL, isEncryptedAtRest)
      }

    (
      attachmentsWithHolder.map(_.fromPath),
      attachmentsWithHolder.map(_.toPath),
      versionOpt map (_.toInt) getOrElse 1
    )
  }

  def userOwnsLeaseOrNoneRequired: Boolean =
    persistenceInstance.rootElement / "lease-owned-by-current-user" === "true"
}

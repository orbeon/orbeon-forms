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

import cats.effect.IO
import cats.syntax.option._
import enumeratum.EnumEntry.Lowercase
import enumeratum._
import org.orbeon.connection.{AsyncConnectionResult, ConnectionContextSupport, ConnectionResult, StreamedContent}
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.dom.{Document, QName}
import org.orbeon.oxf.common
import org.orbeon.oxf.common.{Defaults, OXFException}
import org.orbeon.oxf.externalcontext._
import org.orbeon.oxf.fr.FormRunnerCommon._
import org.orbeon.oxf.fr.Names.FormModel
import org.orbeon.oxf.fr.datamigration.MigrationSupport
import org.orbeon.oxf.fr.persistence.relational.Version.OrbeonFormDefinitionVersion
import org.orbeon.oxf.http.Headers._
import org.orbeon.oxf.http.{BasicCredentials, Headers, HttpMethod}
import org.orbeon.oxf.properties.{Property, PropertySet}
import org.orbeon.oxf.util.ContentTypes.isTextOrXMLOrJSONContentType
import org.orbeon.oxf.util.CoreCrossPlatformSupport.properties
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, _}
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl
import org.orbeon.oxf.xforms.submission.{SubmissionUtils, XFormsModelSubmissionSupport}
import org.orbeon.oxf.xforms.{NodeInfoFactory, XFormsContainingDocument}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.analysis.model.ValidationLevel
import org.orbeon.xforms.{RelevanceHandling, XFormsCrossPlatformSupport}

import java.net.URI
import java.{util => ju}
import scala.jdk.CollectionConverters._


sealed trait FormOrData extends EnumEntry with Lowercase

object FormOrData extends Enum[FormOrData] {

  val values = findValues
  val valuesSet: Set[FormOrData] = values.toSet

  case object Form extends FormOrData
  case object Data extends FormOrData
}

object FormRunnerPersistenceJava {
  //@XPathFunction
  def providerDataFormatVersion(app: String, form: String): String =
    FormRunnerPersistence.providerDataFormatVersionOrThrow(AppForm(app, form)).entryName

  //@XPathFunction
  def findProvider(app: String, form: String, formOrData: String): Option[String] =
    FormRunnerPersistence.findProvider(AppForm(app, form), FormOrData.withName(formOrData))
}

sealed abstract class DataFormatVersion(override val entryName: String) extends EnumEntry

object DataFormatVersion extends Enum[DataFormatVersion] {

  sealed trait MigrationVersion extends DataFormatVersion

  val values = findValues

  case object V400   extends DataFormatVersion("4.0.0")
  case object V480   extends DataFormatVersion("4.8.0")    with MigrationVersion
  case object V20191 extends DataFormatVersion("2019.1.0") with MigrationVersion

  val Edge: DataFormatVersion = values.last

//  def isLatest(dataFormatVersion: DataFormatVersion): Boolean =
//    dataFormatVersion == Edge

  def withNameIncludeEdge(s: String): DataFormatVersion =
    if (s == "edge")
      Edge
    else
      withName(s)
}

case class AttachmentWithHolder(
  fromPath : String,
  holder   : NodeInfo
)

case class AttachmentWithHolders(
  fromPath : String,
  holder   : List[NodeInfo]
)

case class AttachmentWithEncryptedAtRest(
  fromPath          : String,
  toPath            : String,
  isEncryptedAtRest : Boolean
)

sealed trait AttachmentMatch
object AttachmentMatch {
  case object UploadedOnly                                                      extends AttachmentMatch
  case class  BasePaths(includes: Iterable[String], excludes: Iterable[String]) extends AttachmentMatch
}

object FormRunnerPersistence {

  val FormPath                            = """/fr/service/persistence(/crud/([^/]+)/([^/]+)/form/([^/]+))""".r
  val DataPath                            = """/fr/service/persistence(/crud/([^/]+)/([^/]+)/(data|draft)/([^/]+)/([^/]+))""".r
  val DataCollectionPath                  = """/fr/service/persistence(/crud/([^/]+)/([^/]+)/data/)""".r
  val SearchPath                          = """/fr/service/persistence(/search/([^/]+)/([^/]+))""".r
  val ReEncryptAppFormPath                = """/fr/service/persistence(/reencrypt/([^/]+)/([^/]+))""".r // `POST` only
  val PublishedFormsMetadataPath          = """/fr/service/persistence/form(/([^/]+)(?:/([^/]+))?)?""".r
  val HistoryPath                         = """/fr/service/persistence(/history/([^/]+)/([^/]+)/([^/]+)(?:/([^/]+))?)""".r
  val ExportPath                          = """/fr/service/persistence/export(?:/([^/]+))?(?:/([^/]+))?(?:/([^/]+))?""".r
  val PurgePath                           = """/fr/service/persistence/purge(?:/([^/]+))?(?:/([^/]+))?(?:/([^/]+))?""".r
  val DistinctValuesPath                  = """/fr/service/persistence(/distinct-values/([^/]+)/([^/]+))""".r
  val ReindexPath                         =   "/fr/service/persistence/reindex"
  val ReEncryptStatusPath                 =   "/fr/service/persistence/reencrypt" // `GET` only

  val DataXml                             = "data.xml"
  val FormXhtml                           = "form.xhtml"
  val DataFormatVersionName               = "data-format-version"
  val DataMigrationBehaviorName           = "data-migration-behavior"
  val FormDefinitionFormatVersionName     = "form-definition-format-version"
  val PruneMetadataName                   = "prune-metadata"
  val ShowProgressName                    = "show-progress"
  val FormTargetName                      = "formtarget"
  val NonRelevantName                     = "nonrelevant"

  val PersistenceDefaultDataFormatVersion = DataFormatVersion.V400

  val OrbeonOperations                    = "Orbeon-Operations"
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
  val AttachmentsSuffix                   = "attachments"

  val NewFromServiceUriPropertyPrefix     = "oxf.fr.detail.new.service"
  val NewFromServiceUriProperty           = NewFromServiceUriPropertyPrefix + ".uri"
  val NewFromServiceParamsProperty        = NewFromServiceUriPropertyPrefix + ".passing-request-parameters"

  val StandardProviderProperties          = Set("uri", "autosave", "active", "permissions")
  val AttachmentAttributeNames            = List("filename", "mediatype", "size")

  def findProvider(appForm: AppForm, formOrData: FormOrData, properties: PropertySet = CoreCrossPlatformSupport.properties): Option[String] =
    properties.getNonBlankString(
      PersistenceProviderPropertyPrefix :: appForm.app :: appForm.form :: formOrData.entryName :: Nil mkString "."
    )

  def findAttachmentsProvider(appForm: AppForm, formOrData: FormOrData): Option[String] =
    properties.getNonBlankString(
      PersistenceProviderPropertyPrefix :: appForm.app :: appForm.form :: formOrData.entryName :: AttachmentsSuffix :: Nil mkString "."
    )
  // Get all providers that can be used either for form data or for form definitions
  // 2024-02-28: Called with `None`/`None`/`Data`, or appOpt/formOpt/`Form`
  def getProviders(
    appOpt    : Option[String],
    formOpt   : Option[String],
    formOrData: FormOrData,
    properties: PropertySet = CoreCrossPlatformSupport.properties
  ): List[String] =
    getProvidersWithProperties(
      appOpt,
      formOpt,
      formOrData.some,
      properties
    ).keys.toList

  def getProvidersWithProperties(
    appOpt    : Option[String],
    formOpt   : Option[String],
    formOrData: Option[FormOrData],
    properties: PropertySet = CoreCrossPlatformSupport.properties
  ): Map[String, List[Property]] = {

    val propertyName =
      PersistenceProviderPropertyPrefix                            ::
      appOpt.getOrElse(PropertySet.StarToken)                      ::
      formOpt.getOrElse(PropertySet.StarToken)                     ::
      formOrData.map(_.entryName).getOrElse(PropertySet.StarToken) ::
      Nil mkString "."

    properties.propertiesMatching(propertyName)
      .flatMap(p => p.nonBlankStringValue.map(_ -> p))
      .groupBy(_._1)
      .mapValues(_.map(_._2))
      .filter(kv => FormRunner.isActiveProvider(kv._1, properties))
  }

  def providerPropertyName(provider: String, property: String): String =
    PersistencePropertyPrefix :: provider :: property :: Nil mkString "."

  def providerPropertyOpt(provider: String, property: String, properties: PropertySet): Option[Property] =
    properties.getPropertyOpt(providerPropertyName(provider, property))

  def providerPropertyAsUrlOpt(provider: String, property: String): Option[String] =
    properties.getStringOrURIAsStringOpt(providerPropertyName(provider, property))

  // https://github.com/orbeon/orbeon-forms/issues/6300
  def isInternalProvider(provider: String, properties: PropertySet): Boolean =
    providerPropertyOpt(provider, "uri", properties)
      .flatMap(_.nonBlankStringValue)
      .exists(_.startsWith("/"))

  def getPersistenceURLHeaders(appForm: AppForm, formOrData: FormOrData): (String, Map[String, String]) = {

    require(augmentString(appForm.app).nonEmpty) // Q: why `augmentString`?
    require(augmentString(appForm.form).nonEmpty)

    getPersistenceURLHeadersFromProvider(findProvider(appForm, formOrData).get)
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

    val uri = providerPropertyAsUrlOpt(provider, "uri") getOrElse
      (throw new OXFException(s"no base URL specified for requested persistence provider `$provider` (check properties)"))

    (uri, headers)
  }

  def getPersistenceHeadersAsXML(appForm: AppForm, formOrData: FormOrData): DocumentNodeInfoType = {

    val (_, headers) = getPersistenceURLHeaders(appForm, formOrData)

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

  def providerDataFormatVersionOrThrow(appForm: AppForm): DataFormatVersion = {

    val provider =
      findProvider(appForm, FormOrData.Data) getOrElse
        (throw new IllegalArgumentException(s"no provider property configuration found for `${appForm.app}/${appForm.form}`"))

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

  // We don't store the data format version associated with a form definition explicitly. So we have to get it from
  // other information in the form definition. We know the version is *at least* that of the highest migration version
  // information, if any. But there is no guarantee that migration information is present: for example you could have
  // a form definition with a section containing a single repeated grid, so no 2019.1.0 migrations are present as that
  // only regards non-repeated grids. So in addition, we look at the `updated-with-version` and `created-with-version`,
  // which *should* be present unless someone removes them by mistake from the form definition.
  // If we don't find a version in the form definition, it means it was last updated with a version older than 2018.2.
  // TODO: We should discriminate between 4.8.0 and 4.0.0 ideally. Currently we don't have a user use case but it would
  //   be good for correctness.
  def getOrGuessFormDataFormatVersion(metadataRootElemOpt: Option[NodeInfo]): DataFormatVersion =
    metadataRootElemOpt flatMap { metadataRootElem =>
      findFormDefinitionFormatFromStringVersions(
        (metadataRootElem / "updated-with-version" ++ metadataRootElem / "created-with-version").map(_.stringValue) ++:
        MigrationSupport.findAllMigrations(metadataRootElem).map(_._1.entryName)
      )
    } getOrElse
      DataFormatVersion.V480

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

  import FormRunnerPersistence._

  // Check whether a value correspond to an uploaded file
  //
  // For this to be true
  // - the protocol must be file:
  // - the URL must have a valid signature
  //
  // This guarantees that the local file was in fact placed there by the upload control, and not tampered with.
  def isUploadedFileURL(value: String): Boolean =
    (value.startsWith("file:/") || value.startsWith("upload:")) && XFormsUploadControl.verifyMAC(value)

  // Create a path starting and ending with `/`
  // `documentIdOrEmpty` can be empty and if so won't be included. Ideally should be `Option[String]`.
  //@XPathFunction
  def createFormDataBasePath(app: String, form: String, isDraft: Boolean, documentIdOrEmpty: String): String =
    CRUDBasePath ::
    createFormDataBasePathNoPrefix(
      AppForm(app, form),
      None,
      isDraft,
      documentIdOrEmpty.trimAllToOpt
    ) ::
    "" ::
    Nil mkString "/"

  // Path neither starts nor ends with with `/`
  def createFormDataBasePathNoPrefix(
    appForm      : AppForm,
    version      : Option[Int],
    isDraft      : Boolean,
    documentIdOpt: Option[String]
  ): String =
    appForm.app                                           ::
    appForm.form                                          ::
    version.map(_.toString).toList                        :::
    (if (isDraft) "draft" else FormOrData.Data.entryName) ::
    documentIdOpt.toList                                  :::
    Nil mkString "/"

  //@XPathFunction
  def createFormDefinitionBasePath(app: String, form: String): String =
    CRUDBasePath :: createFormDefinitionBasePathNoPrefix(AppForm(app, form), None) :: "" :: Nil mkString "/"

  def createFormDefinitionBasePathNoPrefix(appForm: AppForm, version: Option[Int]): String =
    appForm.app :: appForm.form :: version.map(_.toString).toList ::: FormOrData.Form.entryName :: Nil mkString "/"

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

  def providerPropertyAsBoolean(
    provider  : String,
    property  : String,
    default   : Boolean,
    properties: PropertySet = CoreCrossPlatformSupport.properties
  ): Boolean =
    properties.getBoolean(PersistencePropertyPrefix :: provider :: property :: Nil mkString ".", default)

  def providerPropertyAsInteger(provider: String, property: String, default: Int, properties: PropertySet = CoreCrossPlatformSupport.properties): Int =
    properties.getInteger(PersistencePropertyPrefix :: provider :: property :: Nil mkString ".", default)

  // 2020-12-23: If the provider is not configured, return `false` (for offline FIXME).
  //@XPathFunction
  def isAutosaveSupported(app: String, form: String): Boolean =
    findProvider(AppForm(app, form), FormOrData.Data) exists (providerPropertyAsBoolean(_, "autosave", default = false))

  //@XPathFunction
  def isOwnerGroupPermissionsSupported(app: String, form: String): Boolean =
    providerPropertyAsBoolean(findProvider(AppForm(app, form), FormOrData.Data).get, "permissions", default = false)

  //@XPathFunction
  def isFormDefinitionVersioningSupported(app: String, form: String): Boolean =
    providerPropertyAsBoolean(findProvider(AppForm(app, form), FormOrData.Form).get, "versioning", default = false)

  //@XPathFunction
  def isLeaseSupported(app: String, form: String): Boolean =
    providerPropertyAsBoolean(findProvider(AppForm(app, form), FormOrData.Data).get, "lease", default = false)

  //@XPathFunction
  def isSortSupported(app: String, form: String): Boolean =
    providerPropertyAsBoolean(findProvider(AppForm(app, form), FormOrData.Data).get, "sort", default = false)

  def isActiveProvider(provider: String, properties: PropertySet = CoreCrossPlatformSupport.properties): Boolean =
    providerPropertyAsBoolean(provider, "active", default = true, properties)

  // Whether the form data is valid as per the error summary
  // We use instance('fr-error-summary-instance')/valid and not valid() because the instance validity may not be
  // reflected with the use of XBL components.
  def dataValid: Boolean =
    frc.errorSummaryInstance.rootElement / "valid" === "true"

  // Return the number of failed validations captured by the error summary for the given level
  def countValidationsByLevel(level: ValidationLevel): Int =
    (frc.errorSummaryInstance.rootElement / "counts" /@ level.entryName stringValue).toInt

  // Return whether the data is saved
  def isFormDataSaved: Boolean =
    frc.persistenceInstance.rootElement / "data-status" === "clean"

  // Return all nodes which refer to data attachments
  // Used by attachments service
  //@XPathFunction
  def collectDataAttachmentNodesJava(data: NodeInfo, fromBasePath: String): ju.List[NodeInfo] =
    collectUnsavedAttachments(data.getRoot, AttachmentMatch.BasePaths(includes = List(fromBasePath), excludes = Nil)).map(_.holder).asJava

  // Called upon `xxforms-state-restored`
  //@XPathFunction
  def clearMissingUnsavedDataAttachmentReturnFilenamesJava(data: NodeInfo): ju.List[String] = {

    val FormRunnerParams(_, _, _, documentIdOpt, _, mode) = FormRunnerParams()

    val unsavedAttachmentHolders =
      documentIdOpt match {
        case Some(_) if frc.isNewOrEditMode(mode) =>
          collectUnsavedAttachments(data.getRoot, AttachmentMatch.UploadedOnly).map(_.holder)
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

//  def collectUniqueUnsavedAttachments(
//    data           : NodeInfo,
//    attachmentMatch: AttachmentMatch
//  ): List[AttachmentWithHolders] =
//    collectUnsavedAttachments(data, attachmentMatch)
//      .groupBy(_.fromPath)
//      .mapValues(_.map(_.holder))
//      .toList
//      .map((AttachmentWithHolders.apply _).tupled)

  def collectUnsavedAttachments(
    data           : NodeInfo,
    attachmentMatch: AttachmentMatch
  ): List[AttachmentWithHolder] =
    for {
      holder        <- data.descendantOrSelf(Node).toList  // TODO: not efficient!
      if holder.isAttribute || holder.isElement && ! holder.hasChildElement
      beforeURL     = holder.stringValue.trimAllToEmpty
      if isUploadedFileURL(beforeURL) || (
        attachmentMatch match {
          case AttachmentMatch.UploadedOnly =>
            false
          case AttachmentMatch.BasePaths(includes, excludes) =>
            includes.exists(isAttachmentURLFor(_, beforeURL)) && ! excludes.exists(isAttachmentURLFor(_, beforeURL))
        }
      )
    } yield
      AttachmentWithHolder(beforeURL, holder)

  // TODO: Usually, an unsaved attachment will have a unique path in a form. However, using for example a calculated
  //   value, it is possible to have the same path for multiple attachments. So far, we have saved the attachment
  //   twice in the database, which is not great. But there is more. When a control requires encryption at rest,
  //   the persistence proxy checks the attachment path, and if it is for a control that requires encryption, it will
  //   encrypt the attachment, and return that the encryption has been done via a header. But then, when we update the
  //   attachment paths in the data, we have to match on the `fromPath`/`beforeURL`, and here the behavior becomes
  //   unpredictable: the second attachment saved will write over the other ones. Also, the attachment might be saved
  //   both encrypted and unencrypted under different paths.
  //
  //   One idea would be to do this:
  //
  //   - group all attachments with the same unsaved path
  //   - save the attachment only once, passing all associated paths in the header
  //   - in the persistence proxy, if *any* of the associated controls requires encryption, encrypt it
  //   - Q: What to do whe reading the attachment? Do we know for sure we need to decrypt it?
  //
  def updateAttachments(
    data                          : DocumentNodeInfoType,
    attachmentsWithEncryptedAtRest: List[AttachmentWithEncryptedAtRest],
  ): Unit = {

    val urlsToAttachments =
      attachmentsWithEncryptedAtRest.groupBy(_.fromPath)

    for {
      holder          <- data.descendantOrSelf(*).iterator // TODO: not efficient because we build a `Stream`/`LazyList` in the background!
      if ! holder.hasChildElement
      beforeURL       <- holder.stringValue.trimAllToOpt
      attachment :: _ <- urlsToAttachments.get(beforeURL) // ignore rest, see comment about duplicate `fromPath` above
    } locally {
      XFormsCrossPlatformSupport.mapSavedUri(attachment.fromPath, attachment.toPath)
      updateHolder(holder, attachment)
    }
  }

  def setCreateUpdateResponse(value: String): Unit =
    topLevelInstance(FormModel, "fr-create-update-submission-response").foreach { instance =>
      setvalue(List(instance.rootElement), value)
    }

  // Here we could decide to use a nicer extension for the file. But since initially the filename comes from
  // the client, it cannot be trusted, nor can its mediatype. A first step would be to do content-sniffing to
  // determine a more trusted mediatype. A second step would be to put in an API for virus scanning. For now,
  // we just use .bin as an extension.
  def createAttachmentFilename(url: String, basePath: String): String = {
    val filename =
      if (isUploadedFileURL(url))
        CoreCrossPlatformSupport.randomHexId + ".bin"
      else
        getAttachmentPathFilenameRemoveQuery(url)

    basePath.appendSlash + filename
  }

  private def getAttachmentUriAndHeaders(
    fromBasePaths           : Iterable[(String, Int)],
    beforeUrl               : String
  )(implicit
    externalContext         : ExternalContext,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait,
    xfcd                    : XFormsContainingDocument,
    indentedLogger          : IndentedLogger
  ): (URI, Map[String, List[String]]) = {

    def rewriteServiceUrl(url: String) =
      URLRewriterUtils.rewriteServiceURL(
        externalContext.getRequest,
        url,
        UrlRewriteMode.Absolute
      )

    val attachmentVersionOpt =
      fromBasePaths collectFirst { case (path, version) if beforeUrl.startsWith(path) => version }

    // We used to use an XForms submission here which handled reading as well as writing. But that
    // didn't allow for passing HTTP headers when reading, and we need this for versioning headers.
    // So we now do all the work "natively", which is better anyway.
    // https://github.com/orbeon/orbeon-forms/issues/4919

    val customGetHeaders =
      Map(attachmentVersionOpt.toList map (v => OrbeonFormDefinitionVersion -> List(v.toString)): _*)

    val resolvedGetUri: URI =
      URI.create(rewriteServiceUrl(beforeUrl))

    val allGetHeaders: Map[String, List[String]] =
      Connection.buildConnectionHeadersCapitalizedIfNeeded(
        url              = resolvedGetUri,
        hasCredentials   = false,
        customHeaders    = customGetHeaders,
        headersToForward = Connection.headersToForwardFromProperty,
        cookiesToForward = Connection.cookiesToForwardFromProperty,
        getHeader        = xfcd.headersGetter
      )

    (resolvedGetUri, allGetHeaders)
  }

  private def saveXmlDataIo(
    xmlData       : Document,
    resolvedPutUri: URI,
    formVersion   : Option[String],
    credentials   : Option[BasicCredentials],
    workflowStage : Option[String]
  )(implicit
    externalContext         : ExternalContext,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait,
    connectionContext       : Option[ConnectionContextSupport.ConnectionContext],
    xfcd                    : XFormsContainingDocument,
    indentedLogger          : IndentedLogger
  ): IO[AsyncConnectionResult] = {

    val customPutHeaders =
      (formVersion.toList                         map (v => OrbeonFormDefinitionVersion -> List(v))) :::
      (workflowStage.filter(_.nonAllBlank).toList map (v => "Orbeon-Workflow-Stage" -> List(v)))     :::
      Nil

    val allPutHeaders =
      Connection.buildConnectionHeadersCapitalizedIfNeeded(
        url              = resolvedPutUri,
        hasCredentials   = false, // xxx ?
        customHeaders    = customPutHeaders.toMap,
        headersToForward = Connection.headersToForwardFromProperty,
        cookiesToForward = Connection.cookiesToForwardFromProperty,
        getHeader        = xfcd.headersGetter
      )

    val bytes =
      XFormsCrossPlatformSupport.serializeToByteArray(
        document           = xmlData,
        method             = "xml",
        encoding           = Defaults.DefaultEncodingForModernUse,
        versionOpt         = None,
        indent             = false,
        omitXmlDeclaration = false,
        standaloneOpt      = None
      )

    Connection.connectAsync(
      method      = HttpMethod.PUT,
      url         = resolvedPutUri,
      credentials = credentials,
      content     = StreamedContent.asyncFromBytes(bytes, ContentTypes.XmlContentType.some).some,
      headers     = allPutHeaders,
      loadState   = true,
      logBody     = false
    )
  }

  private def readAttachmentIo(
    fromBasePaths           : Iterable[(String, Int)],
    beforeUrl               : String
  )(implicit
    externalContext         : ExternalContext,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait,
    connectionContext       : Option[ConnectionContextSupport.ConnectionContext],
    xfcd                    : XFormsContainingDocument,
    indentedLogger          : IndentedLogger
  ): IO[AsyncConnectionResult] = {

    val (resolvedGetUri, allGetHeaders) = getAttachmentUriAndHeaders(fromBasePaths, beforeUrl)

    Connection.connectAsync(
      method      = HttpMethod.GET,
      url         = resolvedGetUri,
      credentials = None,
      content     = None,
      headers     = allGetHeaders,
      loadState   = true,
      logBody     = false
    )
  }

  private def saveAttachmentIo(
    stream         : fs2.Stream[IO, Byte],
    pathToHolder   : String,
    resolvedPutUri : URI,
    formVersion    : Option[String],
    credentials    : Option[BasicCredentials]
  )(implicit
    externalContext         : ExternalContext,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait,
    connectionContext       : Option[ConnectionContextSupport.ConnectionContext],
    xfcd                    : XFormsContainingDocument,
    indentedLogger          : IndentedLogger
  ): IO[AsyncConnectionResult] = {

    val customPutHeaders =
      (formVersion.toList map (v => OrbeonFormDefinitionVersion -> List(v))) ::: // write all using the form definition version
      (OrbeonPathToHolder -> List(pathToHolder))                             ::
      Nil

    val allPutHeaders =
      Connection.buildConnectionHeadersCapitalizedIfNeeded(
        url              = resolvedPutUri,
        hasCredentials   = false,
        customHeaders    = customPutHeaders.toMap,
        headersToForward = Connection.headersToForwardFromProperty,
        cookiesToForward = Connection.cookiesToForwardFromProperty,
        getHeader        = xfcd.headersGetter
      )

    Connection.connectAsync(
      method      = HttpMethod.PUT,
      url         = resolvedPutUri,
      credentials = credentials,
      content     = StreamedContent(stream, ContentTypes.OctetStreamContentType.some, contentLength = None).some,
      headers     = allPutHeaders,
      loadState   = true,
      logBody     = false
    )
  }

  def readAttachmentSync(
    fromBasePaths           : Iterable[(String, Int)],
    beforeUrl               : String
  )(implicit
    externalContext         : ExternalContext,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait,
    xfcd                    : XFormsContainingDocument,
    indentedLogger          : IndentedLogger
  ): ConnectionResult = {

    val (resolvedGetUri, allGetHeaders) = getAttachmentUriAndHeaders(fromBasePaths, beforeUrl)

    Connection.connectNow(
      method      = HttpMethod.GET,
      url         = resolvedGetUri,
      credentials = None,
      content     = None,
      headers     = allGetHeaders,
      loadState   = true,
      saveState   = true,
      logBody     = false
    )
  }

  def putWithAttachments(
    liveData          : DocumentNodeInfoType,
    migrate           : Option[DocumentNodeInfoType => DocumentNodeInfoType], // 2021-11-22: only from `trySaveAttachmentsAndData`
    toBaseURI         : String, // can be blank
    fromBasePaths     : Iterable[(String, Int)],
    toBasePath        : String, // not blank, starts with `CRUDBasePath`
    filename          : String,
    commonQueryString : String,
    forceAttachments  : Boolean,
    username          : Option[String] = None,
    password          : Option[String] = None,
    formVersion       : Option[String] = None,
    workflowStage     : Option[String] = None
  )(implicit
    externalContext         : ExternalContext,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait,
    connectionContext       : Option[ConnectionContextSupport.ConnectionContext],
    xfcd                    : XFormsContainingDocument,
    indentedLogger          : IndentedLogger
  ): IO[(List[AttachmentWithEncryptedAtRest], Option[Int], Option[String])] = {

    val credentials =
      username map (BasicCredentials(_, password, preemptiveAuth = true, domain = None))

    // Clear the response instance
    setCreateUpdateResponse("")

    // Prepare data for submission
    val formModel = XFormsAPI.topLevelModel(FormModel).getOrElse(throw new IllegalStateException)

    formModel.doRecalculateRevalidateIfNeeded()

    // This will be a copy of the data
    val preparedData =
      XFormsModelSubmissionSupport.prepareXML(
        xfcd              = xfcd,
        ref               = migrate.map(_(liveData)).getOrElse(liveData), // avoid extra copy if no migration
        relevanceHandling = RelevanceHandling.Keep,
        namespaceContext  = formModel.staticModel.namespaceMapping.mapping,
        annotateWith      = Set("relevant=fr:relevant"),
        relevantAttOpt    = Some(XMLNames.FRRelevantQName)
      )

    val preparedDataDocumentInfo =
      new DocumentWrapper(preparedData, null, XPath.GlobalConfiguration)

    // Find all instance nodes containing file URLs we need to upload
    val attachmentsWithHolder =
      collectUnsavedAttachments(
        preparedDataDocumentInfo,
        if (forceAttachments)
          AttachmentMatch.BasePaths(includes = toBasePath :: fromBasePaths.map(_._1).toList, excludes = Nil)
        else
          AttachmentMatch.BasePaths(includes = fromBasePaths.map(_._1), excludes = List(toBasePath))
      )

    def rewriteServiceUrl(url: String) =
      URLRewriterUtils.rewriteServiceURL(
        externalContext.getRequest,
        url,
        UrlRewriteMode.Absolute
      )

    // xxx xxx close resources
    def saveAllAttachmentsStream(implicit
      xfcd             : XFormsContainingDocument,
      connectionContext: Option[ConnectionContextSupport.ConnectionContext],
    ): fs2.Stream[IO, AttachmentWithEncryptedAtRest] =
      for {
        AttachmentWithHolder(beforeUrl, migratedHolder)
                          <- fs2.Stream.emits(attachmentsWithHolder)
        getCr             <- fs2.Stream.eval(readAttachmentIo(fromBasePaths, beforeUrl))
        getCxr            <- fs2.Stream.eval(IO.fromTry(ConnectionResult.trySuccessConnection(getCr)))
        pathToHolder      = migratedHolder.ancestorOrSelf(*).map(_.localname).reverse.drop(1).mkString("/")
        afterUrl          = createAttachmentFilename(beforeUrl, toBasePath)
        resolvedPutUri    = URI.create(rewriteServiceUrl(PathUtils.appendQueryString(toBaseURI + afterUrl, commonQueryString)))
        putCr             <- fs2.Stream.eval(saveAttachmentIo(getCxr.content.stream, pathToHolder, resolvedPutUri, formVersion, credentials))
        putCxr            <- fs2.Stream.eval(IO.fromTry(ConnectionResult.trySuccessConnection(putCr)))
        isEncryptedAtRest = putCxr.headers.get(OrbeonDidEncryptHeader).exists(_.contains("true"))
      } yield
        AttachmentWithEncryptedAtRest(beforeUrl, afterUrl, isEncryptedAtRest)

    val putUrl =
      URI.create(rewriteServiceUrl(PathUtils.appendQueryString(toBaseURI + toBasePath + filename, commonQueryString)))

    for {
      savedAttachments <- saveAllAttachmentsStream.compile.toList
      _                = updateAttachments(preparedDataDocumentInfo, savedAttachments)
      cr               <- saveXmlDataIo(preparedData, putUrl, formVersion, credentials, workflowStage)
      cxr              <- IO.fromTry(ConnectionResult.trySuccessConnection(cr))
      versionOpt       = Headers.firstItemIgnoreCase(cxr.headers, OrbeonFormDefinitionVersion).map(_.toInt) // will throw if the version is not an integer
      bytesOpt         <- if (cxr.content.contentType.exists(isTextOrXMLOrJSONContentType)) cxr.content.stream.compile.to(Array).map(Some.apply) else IO.pure(None)
      stringOpt        = bytesOpt.flatMap(b => SubmissionUtils.readTextContent(StreamedContent.fromBytes(b, cxr.content.contentType)))
    } yield
      (
        savedAttachments,
        versionOpt,
        stringOpt
      )

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
  }

  private def updateHolder(holder: NodeInfo, attachment: AttachmentWithEncryptedAtRest): Unit = {
    setvalue(holder, attachment.toPath)
    XFormsAPI.delete(holder /@ AttachmentEncryptedAttributeName)
    if (attachment.isEncryptedAtRest)
      XFormsAPI.insert(
        into   = holder,
        origin = AttachmentEncryptedAttribute
      )
  }

  def userOwnsLeaseOrNoneRequired: Boolean =
    frc.persistenceInstance.rootElement / "lease-owned-by-current-user" === "true"
}

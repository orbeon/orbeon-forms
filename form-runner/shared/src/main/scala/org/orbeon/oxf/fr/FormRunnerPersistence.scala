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
import cats.syntax.option.*
import enumeratum.*
import enumeratum.EnumEntry.Lowercase
import org.orbeon.connection.ConnectionContextSupport.ConnectionContexts
import org.orbeon.connection.{AsyncConnectionResult, AsyncStreamedContent, ConnectionResult, StreamedContent}
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.dom.{Document, QName}
import org.orbeon.oxf.common
import org.orbeon.oxf.common.{Defaults, OXFException}
import org.orbeon.oxf.externalcontext.*
import org.orbeon.oxf.fr.FormRunner.formRunnerPropertyWithNs
import org.orbeon.oxf.fr.FormRunnerCommon.*
import org.orbeon.oxf.fr.Names.FormModel
import org.orbeon.oxf.fr.Version.OrbeonFormDefinitionVersion
import org.orbeon.oxf.fr.datamigration.{MigrationSupport, PathElem}
import org.orbeon.oxf.fr.process.SimpleProcess.xpathFunctionContext
import org.orbeon.oxf.http.Headers.*
import org.orbeon.oxf.http.{BasicCredentials, Headers, HttpMethod}
import org.orbeon.oxf.properties.{Property, PropertyLoader, PropertySet}
import org.orbeon.oxf.util.*
import org.orbeon.oxf.util.ContentTypes.isTextOrXMLOrJSONContentType
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.MarkupUtils.*
import org.orbeon.oxf.util.PathUtils.*
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.action.XFormsAPI.*
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.submission.{SubmissionUtils, XFormsModelSubmissionSupport}
import org.orbeon.oxf.xforms.{NodeInfoFactory, XFormsContainingDocument}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.saxon.value.StringValue
import org.orbeon.scaxon.Implicits.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.analysis.model.ValidationLevel
import org.orbeon.xforms.{RelevanceHandling, XFormsCrossPlatformSupport, XFormsNames}
import org.orbeon.xml.NamespaceMapping

import java.net.URI
import java.util as ju
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}


sealed trait FormOrData extends EnumEntry with Lowercase

object FormOrData extends Enum[FormOrData] {

  val values = findValues
  val valuesSet: Set[FormOrData] = values.toSet

  case object Form extends FormOrData
  case object Data extends FormOrData
}

object FormRunnerPersistenceJava {
  //@XPathFunction
  def providerDataFormatVersion(app: String, form: String): String = {
    implicit val propertySet: PropertySet = CoreCrossPlatformSupport.properties
    FormRunnerPersistence.providerDataFormatVersionOrThrow(AppForm(app, form)).entryName
  }

  //@XPathFunction
  def findProvider(app: String, form: String, formOrData: String): Option[String] = {
    implicit val propertySet: PropertySet = CoreCrossPlatformSupport.properties
    FormRunnerPersistence.findProvider(AppForm(app, form), FormOrData.withName(formOrData))
  }
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

  def withNameNoneIfEdge(s: String): Option[DataFormatVersion] =
    if (s == "edge")
      None
    else
      withName(s).some
}

case class AttachmentWithHolder(
  fromPath : String,
  holder   : NodeInfo
)

case class AttachmentWithHolderAndFilename(
  fromPath            : String,
  holder              : NodeInfo,
  persistenceFilename : String
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

case class PutWithAttachmentsResult(
 savedAttachments: List[AttachmentWithEncryptedAtRest],
 versionOpt      : Option[Int],
 stringOpt       : Option[String],
 statusCode      : Int,
 headers         : Map[String, List[String]]
)

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
  val ReindexPath                         = """/fr/service/persistence/reindex(?:/([^/]+)/([^/]+))?""".r
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
  val OrbeonHashAlgorithm                 = "Orbeon-Hash-Algorithm"
  val OrbeonHashValue                     = "Orbeon-Hash-Value"
  val OrbeonDidEncryptHeader              = "Orbeon-Did-Encrypt"
  val OrbeonDidEncryptHeaderLower         = OrbeonDidEncryptHeader.toLowerCase
  val OrbeonDecryptHeader                 = "Orbeon-Decrypt"
  val OrbeonDecryptHeaderLower            = OrbeonDecryptHeader.toLowerCase
  val AttachmentEncryptedAttributeName    = QName("attachment-encrypted", XMLNames.FRNamespace)
  val TmpFileAttributeName                = QName("tmp-file", XMLNames.FRNamespace)
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

  val StandardProviderProperties          = Set("uri", "autosave", "active", "permissions", "flat-view")
  val AttachmentAttributeNames            = List("filename", "mediatype", "size")
  val JsEnvProviderName                   = "javascript"

  private def pathToHolderForEncryption(holder: NodeInfo): String = {
    // For multiple attachments, point to the container (bound element), as it is the one marked for encryption.
    val boundNode = if (holder.localname == "_") holder.parentUnsafe else holder // TODO: what about single attachment named `_`?
    boundNode.ancestorOrSelf(*).map(_.localname).reverse.drop(1).mkString("/")
  }

  def findProvider(appForm: AppForm, formOrData: FormOrData)(implicit propertySet: PropertySet): Option[String] =
    if (CoreCrossPlatformSupport.isJsEnv)
      JsEnvProviderName.some
    else
      propertySet.getNonBlankString(
        PersistenceProviderPropertyPrefix :: appForm.app :: appForm.form :: formOrData.entryName :: Nil mkString "."
      )

  def findAttachmentsProvider(appForm: AppForm, formOrData: FormOrData)(implicit propertySet: PropertySet): Option[String] =
    propertySet.getNonBlankString(
      PersistenceProviderPropertyPrefix :: appForm.app :: appForm.form :: formOrData.entryName :: AttachmentsSuffix :: Nil mkString "."
    )

  // Get all providers that can be used either for form data or for form definitions
  // 2024-02-28: Called with `None`/`None`/`Data`, or appOpt/formOpt/`Form`
  def getProviders(
    appOpt     : Option[String],
    formOpt    : Option[String],
    formOrData : FormOrData
  )(implicit
    propertySet: PropertySet
  ): List[String] =
    getProvidersWithProperties(
      appOpt,
      formOpt,
      formOrData.some
    ).keys.toList

  def getProvidersWithProperties(
    appOpt     : Option[String],
    formOpt    : Option[String],
    formOrData : Option[FormOrData]
  )(implicit
    propertySet: PropertySet
  ): Map[String, List[Property]] = {

    val propertyName =
      PersistenceProviderPropertyPrefix                            ::
      appOpt.getOrElse(PropertySet.StarToken)                      ::
      formOpt.getOrElse(PropertySet.StarToken)                     ::
      formOrData.map(_.entryName).getOrElse(PropertySet.StarToken) ::
      Nil mkString "."

    propertySet.propertiesMatching(propertyName)
      .flatMap(p => p.nonBlankStringValue.map(_ -> p))
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2))
      .filter(kv => FormRunner.isActiveProvider(kv._1))
      .toMap
  }

  def providerPropertyName(provider: String, property: String): String =
    PersistencePropertyPrefix :: provider :: property :: Nil mkString "."

  def providerPropertyOpt(provider: String, property: String)(implicit propertySet: PropertySet): Option[Property] =
    propertySet.getPropertyOpt(providerPropertyName(provider, property))

  private def providerPropertyAsUrlOpt(provider: String, property: String)(implicit propertySet: PropertySet): Option[String] =
    propertySet.getStringOrURIAsStringOpt(providerPropertyName(provider, property))

  def providerPropertyWithNs(
    provider   : String,
    property   : String
  )(implicit
    propertySet: PropertySet
  ): Option[(String, NamespaceMapping)] =
    providerPropertyOpt(provider, property).map { p =>
      (p.stringValue, p.namespaceMapping)
    }

  // https://github.com/orbeon/orbeon-forms/issues/6300
  def isInternalProvider(provider: String)(implicit propertySet: PropertySet): Boolean =
    providerPropertyOpt(provider, "uri")
      .flatMap(_.nonBlankStringValue)
      .exists(_.startsWith("/"))

  def getPersistenceURLHeaders(
    appForm    : AppForm,
    formOrData : FormOrData
  )(implicit
    propertySet: PropertySet
  ): (String, Map[String, String]) = {

    require(augmentString(appForm.app).nonEmpty) // Q: why `augmentString`?
    require(augmentString(appForm.form).nonEmpty)

    getPersistenceURLHeadersFromProvider({
      val r = findProvider(appForm, formOrData)
      if (r.isEmpty) {
        // xxx check again
        val req = CoreCrossPlatformSupport.requestOpt
        println(s"xxx no provider found for ${appForm.app}/${appForm.form} ${formOrData.entryName} (request: ${req.map(_.getRequestURI).getOrElse("no request")})")
        val ps = PropertyLoader.getPropertyStore(req)
        println(s"xxx getPropertyStore class: ${ps.getClass.getName}")
        println(s"xxx properties: ${ps.globalPropertySet.allPropertiesAsJson}")
      }
      r
    }.get)
  }

  def getPersistenceURLHeadersFromProvider(provider: String)(implicit propertySet: PropertySet): (String, Map[String, String]) = {

    val propertyPrefix = PersistencePropertyPrefix :: provider :: Nil mkString "."
    val propertyPrefixTokenCount = propertyPrefix.splitTo[List](".").size

    // Build headers map
    val headers = (
      for {
        propertyName   <- propertySet.propertiesStartsWith(propertyPrefix, matchWildcards = false)
        lowerSuffix    <- propertyName.splitTo[List](".").drop(propertyPrefixTokenCount).headOption
        if ! StandardProviderProperties(lowerSuffix)
        headerName     = "Orbeon-" + capitalizeSplitHeader(lowerSuffix)
        headerValue    <- propertySet.getObjectOpt(propertyName)
      } yield
        headerName -> headerValue.toString
    ).toMap

    val uri = providerPropertyAsUrlOpt(provider, "uri") getOrElse
      (throw new OXFException(s"no base URL specified for requested persistence provider `$provider` (check properties)"))

    (uri, headers)
  }

  def getPersistenceHeadersAsXML(
    appForm    : AppForm,
    formOrData : FormOrData
  )(implicit
    propertySet: PropertySet
  ): DocumentNodeInfoType = {

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

  def providerDataFormatVersionOrThrow(appForm: AppForm)(implicit propertySet: PropertySet): DataFormatVersion = {

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

    def parseVersions: collection.Seq[(Int, Int)] =
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

    import scala.math.Ordering.Implicits.*

    for {
      maxVersionPassed <- maxVersionPassedOpt
      (closest, _)     <- allPossibleDataFormatsPairs.filter(_._2 <= maxVersionPassed).lastOption
    } yield
      closest
  }

  private def fullProviderPropertyName(provider: String, property: String) =
    PersistencePropertyPrefix :: provider :: property :: Nil mkString "."

  private def providerPropertyAsString(provider: String, property: String, default: String)(implicit propertySet: PropertySet) =
    propertySet.getString(fullProviderPropertyName(provider, property), default)
}

trait FormRunnerPersistence {

  import FormRunnerPersistence.*

  // Check whether a value corresponds to an uploaded file
  //
  // For this to be true
  // - the protocol must be `file:`
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

  // Path neither starts nor ends with `/`
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

  // Create a path starting and ending with `/`
  //@XPathFunction
  def createFormDefinitionBasePath(app: String, form: String): String =
    CRUDBasePath :: createFormDefinitionBasePathNoPrefix(AppForm(app, form), None) :: "" :: Nil mkString "/"

  // Path neither starts nor ends with with `/`
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
  def createNewFromServiceUrlOrEmpty(app: String, form: String): String = {
    val propertySet = CoreCrossPlatformSupport.properties
    propertySet.getStringOrURIAsStringOpt(NewFromServiceUriProperty :: app :: form :: Nil mkString ".") match {
      case Some(serviceUrl) =>

        val requestedParams =
          propertySet.getString(NewFromServiceParamsProperty :: app :: form :: Nil mkString ".", "").splitTo[List]()

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
  }

  // For a given attachment path, return the filename
  private def getAttachmentPathFilenameRemoveQuery(pathQuery: String): String =
    splitQuery(pathQuery)._1.split('/').last

  def providerPropertyAsBoolean(
    provider  : String,
    property  : String,
    default   : Boolean
  )(implicit
    propertySet: PropertySet
  ): Boolean =
    propertySet.getBoolean(PersistencePropertyPrefix :: provider :: property :: Nil mkString ".", default)

  def providerPropertyAsInteger(provider: String, property: String, default: Int)(implicit propertySet: PropertySet): Int =
    propertySet.getInteger(PersistencePropertyPrefix :: provider :: property :: Nil mkString ".", default)

  // 2020-12-23: If the provider is not configured, return `false` (for offline FIXME).
  //@XPathFunction
  def isAutosaveSupported(app: String, form: String): Boolean = {
    implicit val properties: PropertySet = CoreCrossPlatformSupport.properties
    findProvider(AppForm(app, form), FormOrData.Data) exists (providerPropertyAsBoolean(_, "autosave", default = false))
  }

  // 2024-08-07: This is currently unused. Conceivably, Form Runner could have different behavior if the persistence
  // provider does not support permissions. But this would be significant work to do properly.
  //@XPathFunction
//  def isOwnerGroupPermissionsSupported(app: String, form: String): Boolean =
//    providerPropertyAsBoolean(findProvider(AppForm(app, form), FormOrData.Data).get, "permissions", default = false)

  //@XPathFunction
  def isFormDefinitionVersioningSupported(app: String, form: String): Boolean = {
    implicit val propertySet: PropertySet = CoreCrossPlatformSupport.properties
    findProvider(AppForm(app, form), FormOrData.Form) match {
      case Some(provider) => providerPropertyAsBoolean(provider, "versioning", default = false)
      case None           => false // Needed for case with Form Builder when lease is not acquired
    }
  }

  //@XPathFunction
  def isLeaseSupported(app: String, form: String): Boolean = {
    implicit val propertySet: PropertySet = CoreCrossPlatformSupport.properties
    findProvider(AppForm(app, form), FormOrData.Data) match {
      case Some(provider) => providerPropertyAsBoolean(provider, "lease", default = false)
      case None           => false // Needed for case with Form Builder when lease is not acquired
    }
  }

  //@XPathFunction
  def isSortSupported(app: String, form: String): Boolean = {
    implicit val propertySet: PropertySet = CoreCrossPlatformSupport.properties
    providerPropertyAsBoolean(findProvider(AppForm(app, form), FormOrData.Data).get, "sort", default = false)
  }

  def isActiveProvider(provider: String)(implicit propertySet: PropertySet): Boolean =
    providerPropertyAsBoolean(provider, "active", default = true)

  // Whether the form data is valid as per the error summary
  // We use instance('fr-error-summary-instance')/valid and not valid() because the instance validity may not be
  // reflected with the use of XBL components.
  def dataValid: Boolean =
    frc.errorSummaryInstance.rootElement / "valid" === "true"

  // Return the number of failed validations captured by the error summary for the given level
  def countValidationsByLevel(level: ValidationLevel): Int =
    (frc.errorSummaryInstance.rootElement / "counts" /@ level.entryName).stringValue.toInt

  // Return whether the data is saved
  def isFormDataSaved: Boolean =
    frc.persistenceInstance.rootElement / "data-status" === "clean"

  // Return all nodes which refer to data attachments
  // Used by attachments service
  //@XPathFunction
  def collectDataAttachmentNodesJava(
    app           : String,
    form          : String,
    formDefinition: NodeInfo,
    data          : NodeInfo,
    fromBasePath  : String
  ): ju.List[NodeInfo] = {

    // We have to
    // https://github.com/orbeon/orbeon-forms/issues/6530
    implicit val ctx        : FormRunnerDocContext = new InDocFormRunnerDocContext(formDefinition)
    implicit val propertySet: PropertySet          = CoreCrossPlatformSupport.properties

    val possiblePathsLeafToRoot =
      frc.searchControlsInFormByControlPredicate(
        controlPredicate  = _ => true, // don't exclude any controls as don't have a list of attachment control types
        dataFormatVersion = FormRunnerPersistence.providerDataFormatVersionOrThrow(AppForm(app, form))
      ).map(_.path.reverse).toSet

    collectUnsavedAttachments(data.getRoot, AttachmentMatch.BasePaths(includes = List(fromBasePath), excludes = Nil))
      .map(_.holder)
      .flatMap { holder =>
        val path = holder.ancestorOrSelf(*).init.map(n => PathElem(n.name)).toList
        if (possiblePathsLeafToRoot.contains(path))
          Option(holder -> path.head.value)
        else if (possiblePathsLeafToRoot.contains(path.tail))
          Option(holder -> path.tail.head.value)
        else
          None
      }
      .map { case (holder, controlName) =>

        // Consider we are non-relevant if any ancestor-or-self `fr:relevant` attribute is set to `false`. This matches
        // the XForms logic. We are not supposed to have any nested `fr:relevant` attributes, or any set to `true`, but
        // if that was the case we would be correct with this logic.
        val nonRelevantAttOpt: Option[NodeInfo] =
          holder
            .ancestorOrSelf(*)
            .find(_.attValueOpt(XMLNames.FRRelevantQName).contains(false.toString))
            .flatMap(_.att(XMLNames.FRRelevantQName).headOption)

        NodeInfoFactory.elementInfo(
          qName   = QName("attachment"),
          content =
            nonRelevantAttOpt ++:
            holder /@ @*.except(XMLNames.FRRelevantQName) ++: (
              NodeInfoFactory.attributeInfo("name", controlName) ::
              StringValue.makeStringValue(holder.stringValue)    ::
              Nil
            )
        )
      }
      .asJava
  }

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

    filenames.flatMap(_.trimAllToOpt).asJava
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
  ): List[AttachmentWithHolder] = {

    // A more correct implementation wouldn't test all holders, but only attachment holders, so it would need access to
    // the form definition. At the moment, if you include a correctly-formed "file:/...?mac=..." or "/fr/..." URL in a
    // text field, for example, it will be collected as an unsaved attachment. The "file:/..." URL would need to have
    // a correct HMAC, so this is very unlikely to happen. Including the base path for the document/data in a text field
    // is an easier attack, but it is still very unlikely to happen by chance.

    for {
      holder        <- data.descendantOrSelf(Node).toList  // TODO: not efficient!
      // 2025-08-27: previous test was holder.isAttribute || (holder.isElement && ! holder.hasChildElement)
      if holder.isElement && ! holder.hasChildElement
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
  }

  // Whether the given path is an attachment path (ignoring an optional query string)
  private def isAttachmentURLFor(basePath: String, url: String): Boolean =
    url.startsWith(basePath) && splitQuery(url)._1.splitTo[List]("/").lastOption.exists { filename =>

      // 2025-08-22:
      //  - isAttachmentURLFor is called from collectUnsavedAttachments only
      //  - it is not called for "file:/..." URLs
      //  - the previous version of this method was only testing the filename extension
      //  - initially, only the .bin extension was tested
      //  - for #1250, additional extensions were added (.jpg, .jpeg, etc.), to support the duplication of sample data,
      //    as some of the sample attachments don't have a .bin extension but native extensions
      //  - since #6565 (custom filenames), testing the file extension (.bin or other) is not correct anymore
      //  - for #6565, the test has been made less restrictive: any filename different from data.xml and form.xhtml is
      //    detected as an attachment
      //  - if a file called data.xml or form.xhtml is attached to a form, its filename will always include the
      //    40-character hexadecimal attachment ID, so this is not a problematic case

      filename != DataXml && filename != FormXhtml
    }

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
    data            : DocumentNodeInfoType,
    savedAttachments: List[AttachmentWithEncryptedAtRest],
    setTmpFileAtt   : Boolean
  ): Unit = {

    val urlsToAttachments =
      savedAttachments.groupBy(_.fromPath)

    for {
      holder          <- data.descendantOrSelf(*).iterator // TODO: not efficient because we build a `Stream`/`LazyList` in the background!
      if ! holder.hasChildElement
      beforeURL       <- holder.stringValue.trimAllToOpt
      attachment :: _ <- urlsToAttachments.get(beforeURL) // ignore rest, see comment about duplicate `fromPath` above
    } locally {
      XFormsCrossPlatformSupport.mapSavedUri(attachment.fromPath, attachment.toPath)
      updateHolder(holder, attachment, setTmpFileAtt)
    }
  }

  def setCreateUpdateResponse(value: String): Unit =
    topLevelInstance(FormModel, "fr-create-update-submission-response").foreach { instance =>
      setvalue(List(instance.rootElement), value)
    }

  private def uploadedAttachmentFilename(
    attachmentHolder: NodeInfo)(implicit
    formRunnerParams: FormRunnerParams,
    indentedLogger  : IndentedLogger,
  ): String = {

    val attachmentId = CoreCrossPlatformSupport.randomHexId
    val PropertyName = "oxf.fr.persistence.attachments.filename"

    val filenameExpressionAndMappingsOpt = for {
      (expression, ns)  <- formRunnerPropertyWithNs(PropertyName)
      trimmedExpression <- expression.trimAllToOpt
    } yield (trimmedExpression, ns)

    filenameExpressionAndMappingsOpt match {
      case None =>
        // No property, use default filename
        s"$attachmentId.bin"

      case Some((expression, ns)) =>
        // Include the attachment ID in the function context
        val functionContext = xpathFunctionContext match {
          case context: XFormsFunction.Context => context.copy(attachmentIdOpt = attachmentId.some)
          case functionContext                 => functionContext
        }

        // Evaluate the expression from the property
        Try(process.SimpleProcess.evaluateString(expression, attachmentHolder, ns, functionContext)) match {
          case Success(filename) =>
            // Make sure the resulting string can be used as a filename
            val sanitizedFilename = FileUtils.sanitizedFilename(filename)

            // Check that the attachment ID is included in the attachment filename
            if (! sanitizedFilename.contains(attachmentId)) {
              val errorMessage = s"Expression '$expression' from property '$PropertyName' doesn't include attachment ID"
              indentedLogger.logError("", errorMessage)
              throw new OXFException(errorMessage)
            }

            sanitizedFilename

          case Failure(throwable) =>
            indentedLogger.logError(
              "",
              s"Error while evaluating expression '$expression' from property '$PropertyName': ${throwable.getMessage}",
              throwable
            )

            throw throwable
        }
    }
  }

  private def createAttachmentFilename(
    url             : String,
    basePath        : String,
    attachmentHolder: NodeInfo)(implicit
    formRunnerParams: FormRunnerParams,
    indentedLogger  : IndentedLogger
  ): String = {
    val filename =
      if (isUploadedFileURL(url))
        uploadedAttachmentFilename(attachmentHolder)
      else
        getAttachmentPathFilenameRemoveQuery(url)

    basePath.appendSlash + filename
  }

  private def getAttachmentUriAndHeaders(
    fromBasePaths : Iterable[(String, Int)],
    beforeUrl     : String
  )(implicit
    safeRequestCtx: SafeRequestContext,
    xfcd          : XFormsContainingDocument,
    indentedLogger: IndentedLogger
  ): (URI, Map[String, List[String]]) = {

    def rewriteServiceUrl(url: String) =
      URLRewriterUtils.rewriteServiceURLPlain(
        UrlRewriterContext(safeRequestCtx),
        url,
        UrlRewriteMode.Absolute,
      )

    val attachmentVersionOpt =
      fromBasePaths collectFirst { case (path, version) if beforeUrl.startsWith(path) => version }

    // We used to use an XForms submission here which handled reading as well as writing. But that
    // didn't allow for passing HTTP headers when reading, and we need this for versioning headers.
    // So we now do all the work "natively", which is better anyway.
    // https://github.com/orbeon/orbeon-forms/issues/4919

    val customGetHeaders =
      Map(attachmentVersionOpt.toList.map(v => OrbeonFormDefinitionVersion -> List(v.toString))*)

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
    workflowStage : Option[String],
    ifMatch       : Option[String]
  )(implicit
    safeRequestCtx: SafeRequestContext,
    connectionCtx : ConnectionContexts,
    xfcd          : XFormsContainingDocument,
    indentedLogger: IndentedLogger
  ): IO[AsyncConnectionResult] = {

    val putHeaders =
      (formVersion.toList                         map (v => OrbeonFormDefinitionVersion -> List(v))) :::
      (workflowStage.filter(_.nonAllBlank).toList map (v => "Orbeon-Workflow-Stage" -> List(v)))     :::
      (ifMatch.toList                             map (v => Headers.IfMatch -> List(v)))             :::
      Nil

    val allPutHeaders =
      Connection.buildConnectionHeadersCapitalizedIfNeeded(
        url              = resolvedPutUri,
        hasCredentials   = false, // xxx ?
        customHeaders    = putHeaders.toMap,
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

    // Unneeded for `PUT`
    implicit val resourceResolver: Option[ResourceResolver] = None

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
    fromBasePaths : Iterable[(String, Int)],
    beforeUrl     : String
  )(implicit
    safeRequestCtx: SafeRequestContext,
    connectionCtx : ConnectionContexts,
    xfcd          : XFormsContainingDocument,
    indentedLogger: IndentedLogger
  ): IO[AsyncConnectionResult] = {

    implicit val resourceResolver: Option[ResourceResolver] = xfcd.staticState.resourceResolverOpt

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
    streamedContent  : AsyncStreamedContent,
    pathToHolder     : String,
    resolvedPutUri   : URI,
    formVersion      : Option[String],
    hashAlgorithmOpt : Option[String],
    hashValueOpt     : Option[String],
    credentials      : Option[BasicCredentials]
  )(implicit
    safeRequestCtx   : SafeRequestContext,
    connectionCtx    : ConnectionContexts,
    xfcd             : XFormsContainingDocument,
    indentedLogger   : IndentedLogger
  ): IO[AsyncConnectionResult] = {

    implicit val resourceResolver: Option[ResourceResolver] = xfcd.staticState.resourceResolverOpt

    val customPutHeaders =
      formVersion     .toList.map(OrbeonFormDefinitionVersion -> List(_)) :::
      hashAlgorithmOpt.toList.map(OrbeonHashAlgorithm         -> List(_)) :::
      hashValueOpt    .toList.map(OrbeonHashValue             -> List(_)) :::
      (OrbeonPathToHolder -> List(pathToHolder))                           ::
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
      content     = streamedContent.some,
      headers     = allPutHeaders,
      loadState   = true,
      logBody     = false
    )
  }

  // 1 use: `buildMultipartEntity()`
  def readAttachmentSync(
    fromBasePaths  : Iterable[(String, Int)],
    beforeUrl      : String
  )(implicit
    externalContext: ExternalContext,
    xfcd           : XFormsContainingDocument,
    indentedLogger : IndentedLogger
  ): ConnectionResult = {

    implicit val resourceResolver: Option[ResourceResolver] = xfcd.staticState.resourceResolverOpt
    implicit val safeRequestCtx: SafeRequestContext = SafeRequestContext(externalContext)

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
    liveData         : DocumentNodeInfoType,
    migrate          : Option[DocumentNodeInfoType => DocumentNodeInfoType], // 2021-11-22: only from `trySaveAttachmentsAndData`
    toBaseURI        : String, // can be blank
    fromBasePaths    : Iterable[(String, Int)],
    toBasePath       : String, // not blank, starts with `CRUDBasePath`
    filename         : String,
    commonQueryString: String,
    forceAttachments : Boolean,
    ifMatch          : Option[String] = None,
    username         : Option[String] = None,
    password         : Option[String] = None,
    formVersion      : Option[String] = None,
    workflowStage    : Option[String] = None
  )(implicit
    safeRequestCtx   : SafeRequestContext,
    connectionCtx    : ConnectionContexts,
    xfcd             : XFormsContainingDocument,
    indentedLogger   : IndentedLogger
  ): IO[PutWithAttachmentsResult] = {

    implicit val formRunnerParams: FormRunnerParams = FormRunnerParams()

    val credentials =
      username map (BasicCredentials(_, password, preemptiveAuth = true, domain = None))

    // Clear the response instance
    setCreateUpdateResponse("")

    // Prepare data for submission
    val formModel = XFormsAPI.topLevelModel(FormModel).getOrElse(throw new IllegalStateException)

    formModel.doRebuildRecalculateRevalidateIfNeeded()

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

    // Find all instance nodes containing file URLs we need to upload, and compute the attachment filenames
    val attachmentsWithHolderAndFilename =
      collectUnsavedAttachments(
        preparedDataDocumentInfo,
        if (forceAttachments)
          AttachmentMatch.BasePaths(includes = toBasePath :: fromBasePaths.map(_._1).toList, excludes = Nil)
        else
          AttachmentMatch.BasePaths(includes = fromBasePaths.map(_._1), excludes = List(toBasePath))
      ).map { case AttachmentWithHolder(beforeUrl, migratedHolder) =>
        // Compute the attachment filenames here to have access to all the necessary context (in-scope containing
        // document, XPath function context, etc.)
        AttachmentWithHolderAndFilename(
          fromPath            = beforeUrl,
          holder              = migratedHolder,
          persistenceFilename = createAttachmentFilename(beforeUrl, toBasePath, migratedHolder)
        )
      }

    def rewriteServiceUrl(url: String) =
      URLRewriterUtils.rewriteServiceURLPlain(
        UrlRewriterContext(safeRequestCtx),
        url,
        UrlRewriteMode.Absolute
      )

    // xxx xxx close resources
    def saveAllAttachmentsStream(implicit
      xfcd         : XFormsContainingDocument,
      connectionCtx: ConnectionContexts
    ): fs2.Stream[IO, AttachmentWithEncryptedAtRest] =
      for {
        case AttachmentWithHolderAndFilename(beforeUrl, migratedHolder, afterUrl)
                          <- fs2.Stream.emits(attachmentsWithHolderAndFilename)
        getCr             <- fs2.Stream.eval(readAttachmentIo(fromBasePaths, beforeUrl))
        getCxr            <- fs2.Stream.eval(IO.fromTry(ConnectionResult.trySuccessConnection(getCr)))
        pathToHolder      = pathToHolderForEncryption(migratedHolder)
        hashAlgorithmOpt  = migratedHolder.attValueOpt(XFormsNames.XXFORMS_HASH_ALGORITHM_QNAME.localName)
        hashValueOpt      = migratedHolder.attValueOpt(XFormsNames.XXFORMS_HASH_VALUE_QNAME.localName)
        resolvedPutUri    = URI.create(rewriteServiceUrl(PathUtils.appendQueryString(toBaseURI + afterUrl, commonQueryString)))
        putCr             <- fs2.Stream.eval(saveAttachmentIo(getCxr.contentWithTypeAndLengthFromHeadersIfMissing, pathToHolder, resolvedPutUri, formVersion, hashAlgorithmOpt, hashValueOpt, credentials))
        putCxr            <- fs2.Stream.eval(IO.fromTry(ConnectionResult.trySuccessConnection(putCr)))
        isEncryptedAtRest = putCxr.headers.get(OrbeonDidEncryptHeader).exists(_.contains("true"))
      } yield
        AttachmentWithEncryptedAtRest(beforeUrl, afterUrl, isEncryptedAtRest)

    val putUrl =
      URI.create(rewriteServiceUrl(PathUtils.appendQueryString(toBaseURI + toBasePath + filename, commonQueryString)))

    for {
      savedAttachments <- saveAllAttachmentsStream.compile.toList
      _                = updateAttachments(preparedDataDocumentInfo, savedAttachments, setTmpFileAtt = false) // works on a copy of the data
      cr               <- saveXmlDataIo(preparedData, putUrl, formVersion, credentials, workflowStage = workflowStage, ifMatch = ifMatch)
      cxr              <- IO.fromTry(ConnectionResult.trySuccessConnection(cr))
      versionOpt       = Headers.firstItemIgnoreCase(cxr.headers, OrbeonFormDefinitionVersion).map(_.toInt) // will throw if the version is not an integer
      bytesOpt         <- if (cxr.content.contentType.exists(isTextOrXMLOrJSONContentType)) cxr.content.stream.compile.to(Array).map(Some.apply) else IO.pure(None)
      stringOpt        = bytesOpt.flatMap(b => SubmissionUtils.readTextContent(StreamedContent.fromBytes(b, cxr.content.contentType)))
    } yield
      PutWithAttachmentsResult(
        savedAttachments = savedAttachments,
        versionOpt       = versionOpt,
        stringOpt        = stringOpt,
        statusCode       = cr.statusCode,
        headers          = cr.headers
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

  private def updateHolder(holder: NodeInfo, attachment: AttachmentWithEncryptedAtRest, setTmpFileAtt: Boolean): Unit = {
    setvalue(holder, attachment.toPath)
    XFormsAPI.delete(holder /@ AttachmentEncryptedAttributeName)
    if (attachment.isEncryptedAtRest)
      XFormsAPI.insert(
        into   = holder,
        origin = AttachmentEncryptedAttribute
      )
    if (setTmpFileAtt) {
      // Only store a temporary file URI in `fr:tmp-file`, not other URIs pointing to the persistence layer, which
      // might be outdated (e.g. draft), or that `email` might not be able to access.
      val fromPathURIOpt = Try(URI.create(attachment.fromPath)).toOption
      val isTemporaryURI = fromPathURIOpt.exists(org.orbeon.io.FileUtils.isTemporaryFileUri)
      if (isTemporaryURI)
        XFormsAPI.insert(
          into   = holder,
          origin = NodeInfoFactory.attributeInfo(TmpFileAttributeName, attachment.fromPath)
        )
    }
  }

  def userOwnsLeaseOrNoneRequired: Boolean =
    frc.persistenceInstance.rootElement / "lease-owned-by-current-user" === "true"
}

package org.orbeon.oxf.fr.persistence.proxy


import cats.data.NonEmptyList
import cats.syntax.option._
import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Response}
import org.orbeon.oxf.externalcontext.UserAndGroup
import org.orbeon.oxf.fr.AttachmentMatch.BasePaths
import org.orbeon.oxf.fr.FormRunnerParams.AppFormVersion
import org.orbeon.oxf.fr.FormRunnerPersistence.{DataXml, FormXhtml}
import org.orbeon.oxf.fr._
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.persistence.api.PersistenceApi._
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils
import org.orbeon.oxf.http.{Headers, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.util.Logging.debug
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils.{NonAllBlank, _}
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, CoreCrossPlatformSupportTrait, IndentedLogger, LoggerFactory}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions._

import java.time.Instant
import java.{util => ju}
import scala.collection.mutable
import scala.util.{Success, Try}


trait ExportOrPurge {

  type Context

  val Logger = LoggerFactory.createLogger("org.orbeon.fr.export")
  implicit val indentedLogger: IndentedLogger = new IndentedLogger(Logger)

  val FormVersionParam         = "form-version"
  val ContentParam             = "content"
  val MatchParam               = "match"
  val DataRevisionHistoryParam = "data-revision-history"
  val DateRangeGtParam         = "data-last-modified-time-gt"
  val DateRangeLtParam         = "data-last-modified-time-lt"

  val ContentParamSeparator         = ","
  val AppFormVersionParamSeparator  = "/"

  val AllowedContentMap: Map[String, FormOrData] =
    Map("form-definition" -> FormOrData.Form, "form-data" -> FormOrData.Data) // "attachments"?

  val AllowedContentTokens = AllowedContentMap.keySet

  val LatestTimestampTag = "latest"

  sealed trait DataRevisionHistoryAdt

  object DataRevisionHistoryAdt {
    case object Exclude extends DataRevisionHistoryAdt
    case object Include extends DataRevisionHistoryAdt
    case object Only    extends DataRevisionHistoryAdt

    def fromStringOptOrThrow(paramOpt: Option[String]): Option[DataRevisionHistoryAdt] = paramOpt match {
      case None           => None
      case Some("exclude") => Exclude.some
      case Some("include") => Include.some
      case Some("only")    => Only.some
      case _               => throw HttpStatusCodeException(StatusCode.BadRequest)
        //throw new IllegalArgumentException(s"Invalid value for $DataRevisionHistoryParam: $paramOpt")
    }
  }

  sealed trait FormVersionAdt

  object FormVersionAdt {
    case object Latest                extends FormVersionAdt
    case object All                   extends FormVersionAdt
    case class Specific(version: Int) extends FormVersionAdt

    def fromStringOptOrThrow(paramOpt: Option[String]): Option[FormVersionAdt] = paramOpt match {
      case None           => None
      case Some("latest") => Latest.some
      case Some("all")    => All.some
      case someSpecific   => Specific(RelationalUtils.parsePositiveIntParamOrThrow(someSpecific, 1)).some
    }
  }

  case class MatchSpec(
    app         : String,
    formDoc     : Option[(String, Option[String])],
    versionParamOpt: Option[FormVersionAdt]
  )

  object MatchSpec {

    // ?include=acme/order/1&include=orbeon/contact/2&include=orbeon/order/3&include=orbeon/order//123456
    def fromParams(params: collection.Map[String, Array[AnyRef]]): Option[NonEmptyList[MatchSpec]] = {
      NonEmptyList.fromList(params.get(MatchParam).toList.flatMap(_.toList.collect {
        case s: String =>
          s.split(AppFormVersionParamSeparator) match { // use Java `split()` as we want blank parts to be kept
            case Array(NonAllBlank(app)) =>
              MatchSpec(app, None, None)
            case Array(NonAllBlank(app), NonAllBlank(form)) =>
              MatchSpec(app, (form, None).some, None)
            case Array(NonAllBlank(app), NonAllBlank(form), version) =>
              MatchSpec(app, (form, None).some, FormVersionAdt.fromStringOptOrThrow(version.trimAllToOpt))
            case Array(NonAllBlank(app), NonAllBlank(form), version, NonAllBlank(documentId)) =>
              MatchSpec(app, (form, documentId.some).some, FormVersionAdt.fromStringOptOrThrow(version.trimAllToOpt))
            case _ =>
              throw HttpStatusCodeException(StatusCode.BadRequest)
          }
      }))
    }
  }

  case class Metadata(
    createdByOpt         : Option[UserAndGroup],
    lastModifiedByOpt    : Option[UserAndGroup],
    workflowStageOpt     : Option[String],
    organizationLevelsOpt: Option[List[String]]
  ) {
    def isEmpty: Boolean =
      createdByOpt.isEmpty          &&
      lastModifiedByOpt.isEmpty     &&
      workflowStageOpt.isEmpty      &&
      organizationLevelsOpt.isEmpty

    def toXML: NodeInfo =
      <metadata>{
        Seq(
          createdByOpt.map { case UserAndGroup(username, groupname) =>
            <created-by username={username} groupname={groupname.orNull}/>
          },
          lastModifiedByOpt.map { case UserAndGroup(username, groupname) =>
            <last-modified-by username={username} groupname={groupname.orNull}/>
          },
          workflowStageOpt.map { workflowStage =>
            <workflow-stage>{workflowStage}</workflow-stage>
          },
          organizationLevelsOpt.map { organizationLevels =>
            <organization-levels>{
              organizationLevels.map(level => <organization-level>{level}</organization-level>)
            }</organization-levels>
          }
        ).flatten
      }</metadata>
  }

  object Metadata {
    def fromHeaders(headers: Map[String, List[String]]): Option[Metadata] =
      Metadata(
        createdByOpt          = Headers.firstItemIgnoreCase(headers, Headers.OrbeonUsername).map { username =>
          UserAndGroup(
            username  = username,
            groupname = Headers.firstItemIgnoreCase(headers, Headers.OrbeonGroup)
          )
        },
        lastModifiedByOpt     = Headers.firstItemIgnoreCase(headers, Headers.OrbeonLastModifiedByUsername).map { username =>
          UserAndGroup(
            username  = username,
            groupname = None
          )
        },
        workflowStageOpt      = Headers.firstItemIgnoreCase(headers, Headers.OrbeonWorkflowStage),
        organizationLevelsOpt = Some(Headers.allItemsIgnoreCase(headers, Headers.OrbeonOrganization).toList).filter(_.nonEmpty)
      ).some.filterNot(_.isEmpty)
  }

  def process(
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

    processImpl(
      request.getFirstParamAsString,
      request.getHeaderValuesMap,
      response,
      appFormVersionMatchOpt,
      dataRevisionHistory,
      dateRangeGtOpt,
      dateRangeLtOpt
    )
  }

  def processImpl(
    getFirstParamAsString: String => Option[String],
    incomingHeaders      : ju.Map[String, Array[String]],
    response             : Response,
    matchesOpt           : Option[NonEmptyList[MatchSpec]],
    dataRevisionHistory  : DataRevisionHistoryAdt,
    dateRangeGtOpt       : Option[Instant],
    dateRangeLtOpt       : Option[Instant]
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit

  private def findAppFormVersionMatch(
    request      : Request,
    appOpt       : Option[String],
    formOpt      : Option[String],
    documentIdOpt: Option[String],
  ): Option[NonEmptyList[MatchSpec]] = {

    val versionParamOpt =
      FormVersionAdt.fromStringOptOrThrow(request.getFirstParamAsString(FormVersionParam))

    val mainMatchSpecOpt =
      appOpt.map { app =>
        MatchSpec(
          app             = app,
          formDoc         = formOpt.map((_, documentIdOpt)),
          versionParamOpt = versionParamOpt
        )
      }

    val secondaryMatchSpec =
      MatchSpec.fromParams(request.parameters)

    secondaryMatchSpec.map(_.concat(mainMatchSpecOpt.toList)).orElse(mainMatchSpecOpt.map(NonEmptyList.one))
  }

  // TODO: Should be trait parameter with Scala 3
  val processOtherAttachments: Boolean

  def processXmlDocument(
    ctx            : Context,
    fromPath       : String,
    toPath         : String,
    documentNode   : DocumentNodeInfoType,
    createdTimeOpt : Option[Instant],
    modifiedTimeOpt: Option[Instant],
    forCurrentData : Boolean,
    metadataOpt    : Option[Metadata]
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit

  def processAttachment(
    ctx           : Context,
    formVersionOpt: Option[Int],
    fromPath      : String,
    toPath        : String,
    debugAction   : String
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit

  def completeAttachments(
    ctx                 : Context,
    appFormVersion      : AppFormVersion,
    documentId          : String,
    attachmentPaths     : mutable.Set[String],
    otherAttachmentPaths: mutable.Set[String]
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit

  def makeToPath(
    appFormVersion : AppFormVersion,
    documentIdOpt  : Option[String],
    modifiedTimeOpt: Option[Instant],
    filename       : String
  ): String

  private def processAttachments(
    ctx            : Context,
    xmlData        : DocumentNodeInfoType,
    appFormVersion : AppFormVersion,
    documentIdOpt  : Option[String],
    modifiedTimeOpt: Option[Instant],
    attachmentPaths: mutable.Set[String]
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit =
    FormRunner.collectUnsavedAttachments(
      data             = xmlData,
      attachmentMatch  = BasePaths(includes = List(makeFromPath(appFormVersion._1, documentIdOpt)), excludes = Nil)
    ) foreach { case AttachmentWithHolder(fromPath, _) =>

      if (! attachmentPaths(fromPath)) { // don't attempt to put a path twice (some forms have this)

        attachmentPaths += fromPath

        // NOTE: The returned attachments will match the `fromBasePaths` we are passing.

//        val mediatype =
//          holder.attValueOpt("mediatype").flatMap(_.trimAllToOpt).getOrElse("application/octet-stream")
//        val attachmentFilenameMetadata =
//          holder.attValueOpt("filename").flatMap(_.trimAllToOpt)

        val attachmentToPath =
          makeToPath(appFormVersion, documentIdOpt, modifiedTimeOpt, extractAttachmentFilename(fromPath))

        processAttachment(
          ctx            = ctx,
          formVersionOpt = appFormVersion._2.some,
          fromPath       = fromPath,
          toPath         = attachmentToPath,
          debugAction    = "exporting"
        )
      }
    }

  def processWithMatch(
    ctx                : Context,
    incomingHeaders    : ju.Map[String, Array[String]],
    matchOpt           : Option[MatchSpec],
    formOrDataSet      : Set[FormOrData],
    dataRevisionHistory: DataRevisionHistoryAdt,
    dateRangeGtOpt     : Option[Instant],
    dateRangeLtOpt     : Option[Instant]
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit =
    matchOpt match {
      case None | Some(MatchSpec(_, None, _)) | Some(MatchSpec(_, Some((_, None)), _)) =>
        // Export a specific app/form

        val appOpt          = matchOpt.map(_.app)
        val formOpt         = matchOpt.flatMap(_.formDoc.map(_._1))
        val versionParamOpt = matchOpt.flatMap(_.versionParamOpt)

        val versionParam = versionParamOpt.getOrElse(FormVersionAdt.Latest)

        formOpt match {
          case None if ! (versionParam == FormVersionAdt.All || versionParam == FormVersionAdt.Latest) =>
            throw HttpStatusCodeException(StatusCode.BadRequest)
          case _ =>
        }

        exportMultipleForms(
          ctx                = ctx,
          incomingHeaders    = incomingHeaders,
          appOpt             = appOpt,
          formOpt            = formOpt,
          versionParam       = versionParam,
          formOrDataSet      = formOrDataSet,
          dataRevisionHistory = dataRevisionHistory,
          dateRangeGtOpt     = dateRangeGtOpt,
          dateRangeLtOpt     = dateRangeLtOpt
        )

      case Some(MatchSpec(app, Some((form, Some(documentId))), versionParamOpt)) =>
        // Export a specific app/form/documentId

        val formVersion = {

          lazy val formVersionFromPersistence =
            PersistenceApi.readDocumentFormVersion(
              app,
              form,
              documentId,
              isDraft = false
            )

          versionParamOpt match {
            case Some(FormVersionAdt.Latest | FormVersionAdt.All) =>
              throw HttpStatusCodeException(StatusCode.BadRequest)
            case Some(FormVersionAdt.Specific(version)) if ! formVersionFromPersistence.contains(version) =>
              throw HttpStatusCodeException(StatusCode.BadRequest)
            case Some(FormVersionAdt.Specific(version)) =>
              version
            case None =>
              formVersionFromPersistence.getOrElse(throw HttpStatusCodeException(StatusCode.NotFound))
          }
        }

        exportUsingDocumentId(
          ctx                = ctx,
          appFormVersion     = (AppForm(app, form), formVersion),
          documentId         = documentId,
          formOrDataSet      = formOrDataSet,
          dataRevisionHistory = dataRevisionHistory,
          dateRangeGtOpt     = dateRangeGtOpt,
          dateRangeLtOpt     = dateRangeLtOpt
        )
    }

  private def exportMultipleForms(
    ctx                : Context,
    incomingHeaders    : ju.Map[String, Array[String]],
    appOpt             : Option[String],
    formOpt            : Option[String],
    versionParam       : FormVersionAdt,
    formOrDataSet      : Set[FormOrData],
    dataRevisionHistory: DataRevisionHistoryAdt,
    dateRangeGtOpt     : Option[Instant],
    dateRangeLtOpt     : Option[Instant]
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit =
    getFormMetadata(
      appOpt,
      formOpt,
      incomingHeaders,
      versionParam == FormVersionAdt.All
    ) foreach { case MetadataDetails(appName, formName, formVersion, _) =>

      versionParam match {
        case FormVersionAdt.Specific(v) if v != formVersion =>
          throw HttpStatusCodeException(StatusCode.BadRequest)
        case _ =>
      }

      val appFormVersion = (AppForm(appName, formName), formVersion)

      if (formOrDataSet(FormOrData.Form))
        processFormDefinition(ctx, appFormVersion).get // can throw

      if (formOrDataSet(FormOrData.Data))
        search(appFormVersion) foreach {
          case DataDetails(_, _, documentId, false) =>
            processFormDataMaybeWithHistory(
              ctx,
              appFormVersion,
              documentId,
              dataRevisionHistory,
              dateRangeGtOpt,
              dateRangeLtOpt
            )
          case _ =>
        }
    }

  private def exportUsingDocumentId(
    ctx                : Context,
    appFormVersion     : AppFormVersion,
    documentId         : String,
    formOrDataSet      : Set[FormOrData],
    dataRevisionHistory: DataRevisionHistoryAdt,
    dateRangeGtOpt     : Option[Instant],
    dateRangeLtOpt     : Option[Instant]
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit = {

    if (formOrDataSet(FormOrData.Form))
      processFormDefinition(ctx, appFormVersion).get // can throw

    if (formOrDataSet(FormOrData.Data))
      processFormDataMaybeWithHistory(ctx, appFormVersion, documentId, dataRevisionHistory, dateRangeGtOpt, dateRangeLtOpt)
  }

  private def processFormDefinition(
    ctx           : Context,
    appFormVersion: AppFormVersion
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Try[Unit] = {

    debug(s"exporting form definition `$appFormVersion`")

    readPublishedFormDefinition(appFormVersion._1.app, appFormVersion._1.form, FormDefinitionVersion.Specific(appFormVersion._2)) map {
      case ((headers, formDefinition), path) =>

        processXmlDocument(
          ctx             = ctx,
          fromPath        = path,
          toPath          = makeToPath(appFormVersion, None, None, FormXhtml),
          documentNode    = formDefinition,
          createdTimeOpt  = headerFromRFC1123OrIso(headers, Headers.OrbeonCreated, Headers.Created),
          modifiedTimeOpt = headerFromRFC1123OrIso(headers, Headers.OrbeonLastModified, Headers.LastModified),
          forCurrentData  = true,
          metadataOpt     = Metadata.fromHeaders(headers)
        )

        processAttachments(
          ctx,
          formDefinition,
          appFormVersion,
          None,
          None,
          mutable.Set[String]()
        )
    }
  }

  private def processFormDataMaybeWithHistory(
    ctx                : Context,
    appFormVersion     : AppFormVersion,
    documentId         : String,
    dataRevisionHistory: DataRevisionHistoryAdt,
    dateRangeGtOpt     : Option[Instant],
    dateRangeLtOpt     : Option[Instant]
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Unit = {

    val attachmentPaths      = mutable.Set[String]()
    val otherAttachmentPaths = mutable.Set[String]()

    if (dataRevisionHistory == DataRevisionHistoryAdt.Include || dataRevisionHistory == DataRevisionHistoryAdt.Only)
      PersistenceApi.dataHistory(appFormVersion._1, documentId).zipWithIndex.foreach { case (dataHistoryDetails, index) =>

        val isFirst = index == 0


        // The first item is the current data, unless data is deleted
        // NOTE: We don't yet support deleted data in the history API.
        val dataProcessed =
          if (! (isFirst && ! dataHistoryDetails.isDeleted && dataRevisionHistory == DataRevisionHistoryAdt.Only))
            processFormData(
              ctx,
              appFormVersion,
              documentId,
              dataHistoryDetails.modifiedTime.some,
              attachmentPaths,
              dateRangeGtOpt,
              dateRangeLtOpt,
              forCurrentData = isFirst && ! dataHistoryDetails.isDeleted
            ).get // can throw
          else
            false

        if (processOtherAttachments && ! dataProcessed)
          readFormData(appFormVersion, documentId, dataHistoryDetails.modifiedTime.some) map {
            case ((_, formData), _) =>
              processAttachments(
                ctx,
                formData,
                appFormVersion,
                documentId.some,
                None,                // only one revision of each attachment is stored
                otherAttachmentPaths // to check for duplicate attachment paths
              )
          }
      }
    else
      processFormData(
        ctx,
        appFormVersion,
        documentId,
        None,
        attachmentPaths,
        dateRangeGtOpt,
        dateRangeLtOpt,
        forCurrentData = true
      ).get // can throw

    completeAttachments(
      ctx,
      appFormVersion,
      documentId,
      attachmentPaths,
      otherAttachmentPaths
    )
  }

  private def isTimeInRange(
    modifiedTime  : Instant,
    dateRangeGtOpt: Option[Instant],
    dateRangeLtOpt: Option[Instant]
  ): Boolean =
    dateRangeGtOpt.forall(modifiedTime.isAfter) && dateRangeLtOpt.forall(modifiedTime.isBefore)

  private def extractAttachmentFilename(fromPath: String): String =
    fromPath.splitTo[List]("/").lastOption.getOrElse(throw new IllegalStateException(fromPath))

  private def processFormData(
    ctx                    : Context,
    appFormVersion         : AppFormVersion,
    documentId             : String,
    modifiedTimeOpt        : Option[Instant],
    attachmentPaths        : mutable.Set[String],
    dateRangeGtOpt         : Option[Instant],
    dateRangeLtOpt         : Option[Instant],
    forCurrentData         : Boolean
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Try[Boolean] =
    modifiedTimeOpt match {
      case Some(modifiedTime) if ! isTimeInRange(modifiedTime, dateRangeGtOpt, dateRangeLtOpt) =>
        // We are passed the modified time so we can do the check right away
        // We are outside of the range so we can ignore the document
        Success(false)
      case _ =>
        readFormData(appFormVersion, documentId, modifiedTimeOpt) map {
          case ((headers, formData), path) =>

            val effectiveModifiedTimeOpt =
              modifiedTimeOpt
                .orElse(headerFromRFC1123OrIso(headers, Headers.OrbeonLastModified, Headers.LastModified))

            // Process the data if it is within the range
            // Also process if we don't have a modified time
            if (effectiveModifiedTimeOpt.forall(isTimeInRange(_, dateRangeGtOpt, dateRangeLtOpt))) {

              processXmlDocument(
                ctx             = ctx,
                fromPath        = path,
                toPath          = makeToPath(appFormVersion, documentId.some, modifiedTimeOpt, DataXml),
                documentNode    = formData,
                createdTimeOpt  = headerFromRFC1123OrIso(headers, Headers.OrbeonCreated, Headers.Created),
                modifiedTimeOpt = effectiveModifiedTimeOpt,
                forCurrentData  = forCurrentData,
                metadataOpt     = Metadata.fromHeaders(headers)
              )

              processAttachments(
                ctx,
                formData,
                appFormVersion,
                documentId.some,
                None,           // only one revision of each attachment is stored
                attachmentPaths // to check for duplicate attachment paths
              )

              true
            } else {
              false
            }
        }
    }

  private def makeFromPath(
    appForm      : AppForm,
    documentIdOpt: Option[String],
  ): String =
    documentIdOpt match {
      case Some(documentId) =>
        FormRunner.createFormDataBasePath(appForm.app, appForm.form, isDraft = false, documentId)
      case None =>
        FormRunner.createFormDefinitionBasePath(appForm.app, appForm.form)
    }
}
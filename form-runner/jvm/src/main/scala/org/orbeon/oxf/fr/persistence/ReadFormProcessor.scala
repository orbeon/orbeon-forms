package org.orbeon.oxf.fr.persistence

import cats.syntax.option.*
import org.orbeon.connection.ConnectionResult
import org.orbeon.oxf.cache.{OutputCacheKey, SimpleOutputCacheKey}
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.persistence.api.PersistenceApi.headerFromRFC1123OrIso
import org.orbeon.oxf.fr.{FormDefinitionVersion, FormRunner, FormRunnerParams, Version}
import org.orbeon.oxf.http.{Headers, HttpMethod}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.{ProcessorImpl, ProcessorOutput, ScalaProcessorOutputImpl}
import org.orbeon.oxf.util.*
import org.orbeon.oxf.xml.*
import org.orbeon.oxf.xml.XMLReceiverSupport.*
import org.orbeon.scaxon.SimplePath.*
import org.xml.sax.InputSource

import java.nio.charset.StandardCharsets
import java.time.Instant
import scala.util.chaining.scalaUtilChainingOps


// This processor reads a form definition from the persistence layer and produces updated `params` which include the
// form definition version. It takes as input:
//
// - `params`: the `FormRunnerParams` needed to identify the form definition to read, version being optional
// - the incoming request (only for method and token)
//
// It produces as output:
//
// - `data`: the form definition XML coming from the persistence layer
// - `params`: an XML document containing the updated parameters, with the correct form definition version
//
// This processor solves two issues:
//
// - obtaining the form definition version from the persistence layer if needed
// - ensuring that downstream processors can cache content based on a correct key/validity for the form definition
//
// The form definition version is not always known at the time of the request. This is the case when requesting a
// specific document: the document uniquely identifies the form definition version, but through the persistence layer.
// In that case, a `HEAD` request is needed to obtain the form definition version from the persistence layer. This is
// done with HTTP response headers.
//
// The expectation is that operations should happen in this order:
//
// - initial call
//     - `data` output `getKeyValidity`: issues a `HEAD` which returns the form definition version (or fails)
//     - `params` output `getKeyValidity`
//     - `data` output `read`: issues a `GET` and read the data
//     - downstream can cache data associated with the form definition
// - subsequent calls
//    - `data` output `getKeyValidity`: issues a `HEAD`
//    - `params` output `getKeyValidity`
//    - downstream cache
//        - hit: no read
//        - miss: `data` output `read`
//
class ReadFormProcessor extends ProcessorImpl {

  selfProcessor =>

  import ReadFormProcessor.*

  private def readParams()(implicit pc: PipelineContext): FormRunnerParams =
    FormRunnerParams(readCacheInputAsTinyTree(pc, XPath.GlobalConfiguration, "params").rootElement)

  private def createDataOutput(outputName: String): ProcessorOutput =
    new ScalaProcessorOutputImpl(ReadFormProcessor.this, outputName) with ReadFormOutputBase {

      def getKeyValidity(state: StateType)(implicit pc: PipelineContext, ec: ExternalContext): Option[(OutputCacheKey, ValidityType)] =
        (
          new SimpleOutputCacheKey(getProcessorClass, outputName, state.key),
          state.validity.orNull
        )
        .tap(kv => debugLog(s"Computed key and validity for `$outputName` output: `$kv`"))
        .some

      def read(state: StateType)(implicit pc: PipelineContext, ec: ExternalContext, rcv: XMLReceiver): Unit = {
        val outgoingRequest = state.outgoingRequest
        ConnectionResult.withSuccessConnection(issueRequest(HttpMethod.GET, outgoingRequest), closeOnSuccess = true) { is =>
          // Here we get a form definition version from the persistence, but we should have had already a `HEAD` issued.
          // If not, we could set it, but that would make the code more complex than the `lazy val` we have now. And
          // again, it's not expected to happen.
          val reader =
            XMLParsing.newXMLReader(ParserConfiguration.Plain)// no XInclude
              .tap(_.setContentHandler(rcv))
              .tap(_.setProperty(XMLConstants.SAX_LEXICAL_HANDLER, rcv))
          val inputSource = new InputSource(is)
            .tap(_.setSystemId(outgoingRequest.pathQuery))
          reader.parse(inputSource)
        }
      }
      .tap(_ => debugLog(s"Read `$outputName` output"))
    }

  private def createParamsOutput(outputName: String): ProcessorOutput =
    new ScalaProcessorOutputImpl(ReadFormProcessor.this, outputName) with ReadFormOutputBase {

      def getKeyValidity(state: StateType)(implicit pc: PipelineContext, ec: ExternalContext): Option[(OutputCacheKey, ValidityType)] = {

        val updatedParams = state.updatedParams

        val md =
          SecureUtils.defaultMessageDigest
            .tap(_.update(updatedParams.app.getBytes(StandardCharsets.UTF_8)))
            .tap(_.update(updatedParams.form.getBytes(StandardCharsets.UTF_8)))
            .tap(_.update(updatedParams.formVersionOpt.map(_.toString).getOrElse("").getBytes(StandardCharsets.UTF_8)))
            .tap(_.update(updatedParams.document.getOrElse("").getBytes(StandardCharsets.UTF_8)))
            .tap(_.update(updatedParams.isDraft.toString.getBytes(StandardCharsets.UTF_8)))
            .tap(_.update(updatedParams.mode.getBytes(StandardCharsets.UTF_8)))

        (
          new SimpleOutputCacheKey(getProcessorClass, outputName, NumberUtils.toHexString(md.digest())),
          0L: ValidityType // TODO: do we need to use the object cache and indirection?validity
        )
      }
      .tap(kv => debugLog(s"Computed key and validity for `$outputName` output: `$kv`"))
      .some

      def read(state: StateType)(implicit pc: PipelineContext, ec: ExternalContext, rcv: XMLReceiver): Unit = {
        val updatedParams = state.updatedParams
        withDocument {
          withElement("request") {
            element("app",          text = updatedParams.app)
            element("form",         text = updatedParams.form)
            element("form-version", text = updatedParams.formVersion.toString) // version can be missing for example when retrieving the Form Builder form definition
            element("document",     text = updatedParams.document.getOrElse(""))
            element("mode",         text = updatedParams.mode)
          }
        }
      }.tap(_ => debugLog(s"Read `$outputName` output"))
    }

  override def createOutput(outputName: String): ProcessorOutput =
    addOutput(
      outputName,
      outputName match {
        case "data"   => createDataOutput(outputName)
        case "params" => createParamsOutput(outputName)
      }
    )

  private trait ReadFormOutputBase extends ScalaProcessorOutputImpl {

    final type ValidityType = ReadFormProcessor.ValidityType
    final type StateType    = ReadFormProcessor.StateType

    final protected def newState(implicit pc: PipelineContext, ec: ExternalContext): StateType =
      new ReadFormProcessorState(
        computeParams        = readParams,
        computeRequest       = computeOutgoingRequest,
        computeResponse      = issueHeadOrThrow,
        computeUpdatedParams = computeUpdatedParams,
      )
  }
}

private object ReadFormProcessor {

  private val Logger = LoggerFactory.createLogger(classOf[ReadFormProcessor])

  private implicit def coreCrossPlatformSupport: CoreCrossPlatformSupport.type = CoreCrossPlatformSupport

  type ValidityType = java.lang.Long
  type StateType    = ReadFormProcessorState

  // This contains metadata extracted from the form definition as it is read. This information is needed to compute
  // `FormRunnerConfig`. It can be cached against the form definition, while taking much less space.
  case class CacheableFormMetadata(
    tocModes                        : Set[String],
    tocMinSections                  : Int,
    tocPositionRaw                  : Option[String],
    hasPdfAttachments               : Boolean,
    sectionCount                    : Int,
    useFormulaDebugger              : Boolean,
    useWizard                       : Option[Boolean],
    readonlyDisableCalculate        : Option[Boolean],
    validateSelectionControlsChoices: Option[Boolean],
  )

  case class OutgoingRequest(
    headers  : List[(String, List[String])],
    pathQuery: String
  )

  class ReadFormProcessorState(
    computeParams       : ()                                                         => FormRunnerParams,
    computeRequest      : FormRunnerParams                                           => OutgoingRequest,
    computeResponse     : OutgoingRequest                                            => (Option[Instant], Option[FormDefinitionVersion.Specific]),
    computeUpdatedParams: (FormRunnerParams, Option[FormDefinitionVersion.Specific]) => FormRunnerParams,
  ) {
    lazy val params                  : FormRunnerParams                                = computeParams()                                                      .tap(_ => debugLog(s"Computed FormRunnerParams"))
    lazy val outgoingRequest         : OutgoingRequest                                 = computeRequest(params)                                               .tap(_ => debugLog(s"Computed OutgoingRequest"))
    lazy val (lastModifiedFromPersistenceOpt, formDefinitionVersionFromPersistenceOpt) = computeResponse(outgoingRequest)                                     .tap(_ => debugLog(s"Issued HEAD request"))
    lazy val updatedParams           : FormRunnerParams                                = computeUpdatedParams(params, formDefinitionVersionFromPersistenceOpt).tap(_ => debugLog(s"Computed updated FormRunnerParams"))

    lazy val key: String =
      (params.app :: params.form :: formDefinitionVersionFromPersistenceOpt.map(_.version.toString).getOrElse("") :: Nil)
        .mkString("|")
        .tap(key => debugLog(s"Computed key for ReadFormProcessorState: `$key`"))

    lazy val validity: Option[ValidityType] =
      lastModifiedFromPersistenceOpt.map(_.toEpochMilli: ValidityType)
  }

  private def debugLog(s: => String): Unit =
    if (Logger.isDebugEnabled)
      Logger.debug(s)

  def computeOutgoingRequest(params: FormRunnerParams)(implicit ec: ExternalContext): OutgoingRequest = {

    val documentIdOpt = {
      val requestMethod = ec.getRequest.getMethod
      params.document match {
        case some @ Some(_)
          if params.mode != "new" && (requestMethod == HttpMethod.GET || requestMethod == HttpMethod.HEAD) => some
        case _ => None
      }
    }

    val outgoingRequestHeaders =
      (documentIdOpt, params.formVersionOpt) match {
        case (Some(documentId), _) =>
          (Version.OrbeonForDocumentId -> List(documentId)) ::
          params.isDraft.map(v => Version.OrbeonForDocumentIsDraft -> List(v.toString)).toList
        case (None, Some(formVersion)) =>
          Version.OrbeonFormDefinitionVersion -> List(formVersion.toString) ::
          Nil
        case (None, None) =>
          Nil
      }

    val pathQuery =
      PathUtils.recombineQuery(
        FormRunner.createFormDefinitionBasePath(
          app  = params.app,
          form = params.form,
        ) + "form.xhtml",
        // Forward the token as the persistence proxy requires a `HEAD` on the data to obtain the form definition
        // version, and that call must pass permission checks.
        ec.getRequest.getFirstParamAsString(FormRunner.AccessTokenParam).map(FormRunner.AccessTokenParam -> _).toList
      )

    OutgoingRequest(outgoingRequestHeaders, pathQuery)
  }

  def issueHeadOrThrow(outgoingRequest: OutgoingRequest): (Option[Instant], Option[FormDefinitionVersion.Specific]) =
    ConnectionResult.trySuccessConnection(issueRequest(HttpMethod.HEAD, outgoingRequest))
      .map { cxr =>

        val lastModifiedFromResponseHeaderOpt = headerFromRFC1123OrIso(cxr.headers, Headers.OrbeonLastModified, Headers.LastModified)
        val formDefinitionVersionOpt          = Headers.firstItemIgnoreCase(cxr.headers, Version.OrbeonFormDefinitionVersion)

        cxr.close()

        (
          lastModifiedFromResponseHeaderOpt,
          formDefinitionVersionOpt.map(_.toInt).map(FormDefinitionVersion.Specific.apply)
        )
      }
      .get // throws in case of connection `Failure`

  def computeUpdatedParams(params: FormRunnerParams, formDefinitionVersionOpt: Option[FormDefinitionVersion.Specific]): FormRunnerParams =
    FormRunnerParams(
      app            = params.app,
      form           = params.form,
      formVersionOpt = formDefinitionVersionOpt.map(_.version),
      document       = params.document,
      isDraft        = params.isDraft,
      mode           = params.mode,
    )

  private def issueRequest(method: HttpMethod, outgoingRequest: OutgoingRequest): ConnectionResult = {
    implicit val indentedLogger: IndentedLogger = new IndentedLogger(Logger)
    PersistenceApi.connectPersistence(
      method         = method,
      pathQuery      = outgoingRequest.pathQuery,
      formVersionOpt = None,
      customHeaders  = outgoingRequest.headers.toMap
    )
  }
}
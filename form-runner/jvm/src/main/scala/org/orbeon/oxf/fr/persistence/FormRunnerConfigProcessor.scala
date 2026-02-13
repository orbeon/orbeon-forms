package org.orbeon.oxf.fr.persistence

import cats.Eval
import cats.syntax.option.*
import org.orbeon.dom.QName
import org.orbeon.oxf.cache.*
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fr.FormRunner.{InternalValidateSelectionControlsChoicesParam, UsePdfTemplateParam}
import org.orbeon.oxf.fr.FormRunnerMetadataSupport.*
import org.orbeon.oxf.fr.XMLNames.{FR, FRNamespace}
import org.orbeon.oxf.fr.definitions.FormRunnerDetailMode.SupportedNonDetailModes
import org.orbeon.oxf.fr.process.FormRunnerRenderedFormat
import org.orbeon.oxf.fr.{FormRunner, FormRunnerParams}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.*
import org.orbeon.oxf.processor.ProcessorImpl.INPUT_DATA
import org.orbeon.oxf.util.*
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.orbeon.oxf.xml.*
import org.orbeon.oxf.xml.XMLReceiverSupport.*
import org.orbeon.scaxon.SimplePath.*
import org.xml.sax.Attributes

import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*
import scala.util.chaining.*


// This processor reads a form definition on its `data` input, and copies it to its `data` output. In this process,
// it computes a configuration document for the Form Runner form definition transformation. It takes as input:
//
// - `data`: form definition
// - `params`: form runner parameters, including the _correct_ form definition version
//
// It produces as output:
//
// - `data`: form definition (same as input, but can be streamed out, so we avoid a `TeeProcessor`)
// - `form-runner-config`: configuration for the form definition transformation
//
// This processor solves the issue of computing all the information which, in addition to the form definition
// (app/form/version)'s last modification or ETag, can influence the result of the compiled form definition. The goal
// is to have a minimal set, so that, for example, two requests which should end up with the same compiled form
// definition have the same set of values here. The most basic example is that the document id should not influence the
// compiled form definition. Ideally, also, `new` and `edit` modes should share the same compiled form definition.
//
class FormRunnerConfigProcessor extends ProcessorImpl {

  selfProcessor =>

  import FormRunnerConfigProcessor.*

  private def readParams()(implicit pc: PipelineContext): FormRunnerParams =
    FormRunnerParams(readCacheInputAsTinyTree(pc, XPath.GlobalConfiguration, "params").rootElement)

  // Only used for tests by connecting a `request` input
  private def readRequestForTests(implicit pc: PipelineContext): Option[(String, String => Option[String])] =
    getInputNames.asScala.contains("request").option {
      readCacheInputAsObject(
        pc,
        getInputByName("request"),
        (pc: PipelineContext, input: ProcessorInput) => {
          val doc = readInputAsTinyTree(pc, input, XPath.GlobalConfiguration).rootElement
          (
            doc elemValue "request-path",
            {
              val paramsMap =
                (doc / "parameters" / "parameter")
                  .map { paramElem =>
                    val name = paramElem elemValue "name"
                    val values = paramElem / "value" map (_.stringValue)
                    (name, values.toList)
                  }
                  .toMap

              (name: String) => paramsMap.get(name).flatMap(_.headOption)
            }
          )
        }
      )
    }

  private def findMetadataInCache()(implicit pc: PipelineContext): Option[CacheableFormMetadata] =
    Option(findInputInCache(pc, getInputByName(INPUT_DATA)).asInstanceOf[CacheableFormMetadata])

  // The `data` output directly matches the `data` input, while also extracting and caching the form metadata
  private def createDataOutput(outputName: String): ProcessorOutput =
    new ScalaProcessorOutputImpl(FormRunnerConfigProcessor.this, outputName) with ReadFormOutputBase {

      type ValidityType = AnyRef

      // Make the output key and validity directly match the `data` input's
      def getKeyValidity(state: StateType)(implicit pc: PipelineContext, ec: ExternalContext): Option[(OutputCacheKey, AnyRef)] =
        Option(getInputKeyValidity(pc, INPUT_DATA))
          .map(kv => (new CompoundOutputCacheKey(selfProcessor.getClass, outputName, Array(kv.key)), kv.validity))
          .tap(kv => debugLog(s"Computed key and validity for `$outputName` output from `data` input: `$kv`"))

      // Reading forwards the `data` input to the output, while also extracting and caching the form metadata
      def read(state: StateType)(implicit pc: PipelineContext, ec: ExternalContext, rcv: XMLReceiver): Unit = {

        def readInputToReceiver(receiver: XMLReceiver): Unit =
          readInputAsSAX(pc, outputName, receiver)

        readCacheInputAsObject(pc, getInputByName(INPUT_DATA), new CacheableInputReader[CacheableFormMetadata] {

          // This is called if the metadata is not already in cache. This reads the input and extracts the metadata,
          // which will be cached against the input.
          def read(pipelineContext: PipelineContext, input: ProcessorInput): CacheableFormMetadata = {
            if (FormRunner.isFormBuilder(state.params)) {
              // Special-case Form Builder as an optimization

              readInputToReceiver(rcv)

              CacheableFormMetadata(
                tocModes                         = None,
                tocMinSections                   = None,
                tocPosition                      = None,
                hasPdfAttachments                = false,
                sectionCount                     = 0,
                useFormulaDebugger               = false,
                useWizard                        = false.some,
                readonlyDisableCalculate         = false.some,
                validateSelectionControlsChoices = false.some,
              )
            } else {

              val (metadataRcv,    metadataResult)    = StaticXPath.newTinyTreeReceiver
              val (attachmentsRcv, attachmentsResult) = StaticXPath.newTinyTreeReceiver

              val metadataFilter    = newInstanceFilter(metadataRcv,    isMetadataElement,    _ => true)
              val attachmentsFilter = newInstanceFilter(attachmentsRcv, isAttachmentsElement, _ => true)

              var sectionCount = 0
              val bodyFilter        = newBodyFilter(new XMLReceiverAdapter {
                override def startElement(namespaceURI: String, localName: String, qName: String, atts: Attributes): Unit = {
                  if (namespaceURI == FR && localName == "section")
                    sectionCount += 1
                }
              })

              readInputToReceiver(new TeeXMLReceiver(List(rcv, metadataFilter, attachmentsFilter, bodyFilter)))

              val metadataRootElemOpt    = Option(metadataResult()).flatMap(_.rootElementOpt)
              val attachmentsRootElemOpt = Option(attachmentsResult()).flatMap(_.rootElementOpt)
              val tocOpt                 = metadataRootElemOpt.flatMap(e => (e / "xbl" / (FR -> "toc")).headOption)

              CacheableFormMetadata(
                tocModes                         = tocOpt.map(_ attTokens "modes"),
                tocMinSections                   = tocOpt.flatMap(_ attValueOpt "min-sections").flatMap(_.trimAllToOpt).map(_.toInt),
                tocPosition                      = tocOpt.flatMap(_ attValueNonBlankOpt "position").flatMap(_.trimAllToOpt),
                hasPdfAttachments                = attachmentsRootElemOpt.map(FormRunnerRenderedFormat.extractPdfTemplates).exists(_.nonEmpty),
                sectionCount                     = sectionCount,
                useFormulaDebugger               = metadataRootElemOpt.flatMap(_ elemValueOpt "formula-debugger").exists(_.toBoolean),
                useWizard                        = metadataRootElemOpt.flatMap(_ elemValueOpt "wizard").map(_.toBoolean),
                readonlyDisableCalculate         = metadataRootElemOpt.flatMap(_ elemValueOpt "readonly-disable-calculate").map(_.toBoolean),
                validateSelectionControlsChoices = metadataRootElemOpt.flatMap(_ elemValueOpt "validate-selection-controls-choices").map(_.toBoolean),
              )
            }
          }
          .tap(state.setCacheableMetadata) // store immediately into the state, for the cases where caching is not possible (like the test mode)

          // If metadata was already in cache, the input will not be read by `readCacheInputAsObject()`, so we need to
          // read it now explicitly in order to forward it to the output.
          override def foundInCache(t: CacheableFormMetadata): Unit = {
            state.setCacheableMetadata(t)
            readInputToReceiver(rcv)
              .tap(_ => debugLog(s"Forwarded input to output as metadata was found in cache"))
          }

          override def storedInCache(): Unit =
            debugLog(s"Stored metadata in cache")
        })
      }
      .tap(_ => debugLog(s"Read `$outputName` output"))
    }

  private def createFormRunnerConfigOutput(outputName: String): ProcessorOutput =
    new ScalaProcessorOutputImpl(FormRunnerConfigProcessor.this, outputName) with ReadFormOutputBase {

      type ValidityType = FormRunnerConfigProcessor.ValidityType

      def getKeyValidity(state: StateType)(implicit pc: PipelineContext, ec: ExternalContext): Option[(OutputCacheKey, ValidityType)] =
        state
          .formRunnerConfigOpt
          .map(formRunnerConfig =>
            (
              new SimpleOutputCacheKey(getProcessorClass, outputName, formRunnerConfig.digest),
              0L: ValidityType
    //          state.lastModifiedFromPersistenceOpt.map(_.toEpochMilli: ValidityType).orNull
            )
          )
          .tap(kv => debugLog(s"Computed key and validity for `$outputName` output: `$kv`"))

      def read(state: StateType)(implicit pc: PipelineContext, ec: ExternalContext, rcv: XMLReceiver): Unit = {

        val formRunnerConfig =
          state.formRunnerConfigOpt
            .getOrElse(throw new IllegalStateException("Form metadata should have been found in cache at this point"))

        withDocument {
          withElement("_") {
            element("app",                 text = formRunnerConfig.app)
            element("form",                text = formRunnerConfig.form)
            element("form-version",        text = formRunnerConfig.formVersion.toString)
            element("major-mode",          text = formRunnerConfig.majorMode)

            formRunnerConfig.viewAppearanceOpt
              .foreach { v =>
                element("view-appearance",        text = v.qualifiedName)
                element("mode-namespace-uri-opt", text = v.namespace.uri)
              }

            element("is-pdf",              text = formRunnerConfig.isPdf.toString)
            element("is-readonly-mode",    text = formRunnerConfig.isReadonlyMode.toString)
            element("use-pdf-template",    text = formRunnerConfig.usePdfTemplate.toString)
            element("is-static-readonly",  text = formRunnerConfig.isStaticReadonly.toString)
            element("is-test",             text = formRunnerConfig.isTest.toString)

            formRunnerConfig.tocPositionOpt
              .foreach(v => element("toc-position", text = v))
            formRunnerConfig.validateSelectionControlsChoices
              .foreach(v => element("validate-selection-controls-choices", text = v.toString))
            formRunnerConfig.disableRelevantOpt
              .foreach(v => element("disable-relevant",  text = v.toString))
            formRunnerConfig.disableDefaultOpt
              .foreach(v => element("disable-default",   text = v.toString))
            formRunnerConfig.disableCalculateOpt
              .foreach(v => element("disable-calculate", text = v.toString))
          }
        }
      }
      .tap(_ => debugLog(s"Read `$outputName` output"))
    }

  override def createOutput(outputName: String): ProcessorOutput =
    addOutput(
      outputName,
      outputName match {
        case "data"               => createDataOutput(outputName)
        case "form-runner-config" => createFormRunnerConfigOutput(outputName)
        case other                => throw new IllegalArgumentException(s"Unknown output name: `$other`")
      }
    )

  private trait ReadFormOutputBase extends ScalaProcessorOutputImpl {

    type ValidityType
    final type StateType  = FormRunnerConfigProcessor.StateType

    final protected def newState(implicit pc: PipelineContext, ec: ExternalContext): StateType = {
      val (requestPath, requestParams) = readRequestForTests.getOrElse((ec.getRequest.getRequestPath, ec.getRequest.getFirstParamAsString _))
      new FormRunnerConfigProcessorState(
        computeParams           = readParams,
        computeMetadata         = findMetadataInCache,
        computeFormRunnerConfig = computeFormRunnerConfig(_, _, requestPath, requestParams),
      )
    }
  }
}

private object FormRunnerConfigProcessor {

  private val Logger = LoggerFactory.createLogger(classOf[FormRunnerConfigProcessor])

  type ValidityType = java.lang.Long
  type StateType    = FormRunnerConfigProcessorState

  val AllowedTocModes = Set("new", "edit", "view", "pdf", "tiff", "test", "test-pdf")
  val DefaultTocModes = Set("new", "edit", "view")

  // This contains metadata extracted from the form definition as it is read. This information is needed to compute
  // `FormRunnerConfig`. It can be cached against the form definition, while taking much less space.
  case class CacheableFormMetadata(
    tocModes                        : Option[Set[String]],
    tocMinSections                  : Option[Int],
    tocPosition                     : Option[String],
    hasPdfAttachments               : Boolean,
    sectionCount                    : Int,
    useFormulaDebugger              : Boolean,
    useWizard                       : Option[Boolean],
    readonlyDisableCalculate        : Option[Boolean],
    validateSelectionControlsChoices: Option[Boolean],
  )

  // This contains all the information which, in addition to the form definition (app/form/version)'s last modification
  // or ETag, can influence the result of the compiled form definition. The goal is to have a minimal set, so that, for
  // example, two requests which should end up with the same compiled form definition have the same set of values here.
  // The most basic example is that the document id should not influence the compiled form definition. Ideally, also,
  // `new` and `edit` modes should share the same compiled form definition.
  case class FormRunnerConfig(
    app                             : String,
    form                            : String,
    formVersion                     : Int,
    majorMode                       : String,
    viewAppearanceOpt               : Option[QName],
    isPdf                           : Boolean,
    isReadonlyMode                  : Boolean,
    usePdfTemplate                  : Boolean,
    isStaticReadonly                : Boolean,
    isTest                          : Boolean,
    tocPositionOpt                  : Option[String],
    validateSelectionControlsChoices: Option[Boolean],
    disableRelevantOpt              : Option[Boolean],
    disableDefaultOpt               : Option[Boolean],
    disableCalculateOpt             : Option[Boolean],
  ) {
    lazy val digest: String =
      SecureUtils.defaultMessageDigest
        .tap(_.update(app.getBytes(StandardCharsets.UTF_8)))
        .tap(_.update(form.getBytes(StandardCharsets.UTF_8)))
        .tap(_.update(formVersion.toString.getBytes(StandardCharsets.UTF_8)))
        .tap(_.update(majorMode.getBytes(StandardCharsets.UTF_8)))
        .tap(_.update(viewAppearanceOpt.map(_.namespace.uri).getOrElse("").getBytes(StandardCharsets.UTF_8)))
        .tap(_.update(viewAppearanceOpt.map(_.localName).getOrElse("").getBytes(StandardCharsets.UTF_8)))
        .tap(_.update(isPdf.toString.getBytes(StandardCharsets.UTF_8)))
        .tap(_.update(isReadonlyMode.toString.getBytes(StandardCharsets.UTF_8)))
        .tap(_.update(usePdfTemplate.toString.getBytes(StandardCharsets.UTF_8)))
        .tap(_.update(isStaticReadonly.toString.getBytes(StandardCharsets.UTF_8)))
        .tap(_.update(isTest.toString.getBytes(StandardCharsets.UTF_8)))
        .tap(_.update(tocPositionOpt.getOrElse("").getBytes(StandardCharsets.UTF_8)))
        .tap(_.update(validateSelectionControlsChoices.toString.getBytes(StandardCharsets.UTF_8)))
        .tap(_.update(disableRelevantOpt.toString.getBytes(StandardCharsets.UTF_8)))
        .tap(_.update(disableDefaultOpt.toString.getBytes(StandardCharsets.UTF_8)))
        .tap(_.update(disableCalculateOpt.toString.getBytes(StandardCharsets.UTF_8)))
        .pipe(md => NumberUtils.toHexString(md.digest()))
  }

  class FormRunnerConfigProcessorState(
    computeParams          : ()                                        => FormRunnerParams,
    computeMetadata        : ()                                        => Option[CacheableFormMetadata],
    computeFormRunnerConfig: (FormRunnerParams, CacheableFormMetadata) => FormRunnerConfig
  ) {
    lazy val params: FormRunnerParams = computeParams().tap(_ => debugLog(s"Computed FormRunnerParams"))

    private def newFormRunnerConfigOptEval(metadataOpt: () => Option[CacheableFormMetadata]): Eval[Option[FormRunnerConfig]] =
      Eval.later {
        metadataOpt().map(computeFormRunnerConfig(params, _))
          .tap(v => debugLog(s"Computed CacheableFormMetadata: $v"))
      }

    private var formRunnerConfigOptEval: Eval[Option[FormRunnerConfig]] =
      newFormRunnerConfigOptEval(computeMetadata)

    def setCacheableMetadata(cacheableFormMetadata: CacheableFormMetadata): Unit = {
      debugLog(s"Set CacheableFormMetadata: $cacheableFormMetadata")
      formRunnerConfigOptEval = newFormRunnerConfigOptEval(() => cacheableFormMetadata.some)
    }

    def formRunnerConfigOpt: Option[FormRunnerConfig] =
      formRunnerConfigOptEval.value
  }

  private def debugLog(s: => String): Unit =
    if (Logger.isDebugEnabled)
      Logger.debug(s)

  private val TestModes           = Set("test", "test-pdf")
  private val PdfModes            = Set("pdf", "tiff", "test-pdf")

  private val DisableCalculateParams = Set("disable-calculations", "disable-calculate", "fr-disable-calculate")
  private val DisableDefaultParams   = Set("disable-default", "fr-disable-default")
  private val DisableRelevantParams  = Set("disable-relevant", "fr-disable-relevant")

  val FormulaDebuggerQName: QName = QName("formula-debugger", FRNamespace)
  val WizardQName         : QName = QName("wizard", FRNamespace)
  val FullQName           : QName = QName("full", FRNamespace)

  private def computeFormRunnerConfig(
    updatedParams: FormRunnerParams,
    metadata     : CacheableFormMetadata,
    requestPath  : String,
    getParam     : String => Option[String]
  ): FormRunnerConfig = {

    val isFormBuilder = FormRunner.isFormBuilder(updatedParams)
    val isService     = FormRunner.isServicePath(requestPath)

    def majorMode: String =
      if (SupportedNonDetailModes.contains(updatedParams.mode)) // also includes "validate" and "import" (correct?)
        updatedParams.mode
      else
        "detail"

    val isPdf = PdfModes(updatedParams.mode)

    val usePdfTemplate =
      isPdf                      &&
      metadata.hasPdfAttachments &&
      ! (isService && getParam(UsePdfTemplateParam).contains(false.toString))

    val isStaticReadonly =
      updatedParams.mode == "view" && ! isFormBuilder ||
      isPdf                        && ! usePdfTemplate

    val viewAppearanceOpt = {

      def isImportPage =
        updatedParams.mode == "import" && ! isService

      val modeQName =
        FormRunner.modeQName(updatedParams.app, updatedParams.form, updatedParams.mode)

      val viewAppearance: QName =
        modeQName.namespace.uri.nonAllBlank.option(modeQName)               // if the mode is namespaced, use that, as it is an explicit custom mode
          .orElse(isImportPage.option(WizardQName))                         // else if we are the `import` page, use the wizard
          .orElse(metadata.useFormulaDebugger.option(FormulaDebuggerQName)) // else if formula debugger is on, use that
          .orElse(metadata.useWizard.contains(true).option(WizardQName))    // else if wizard is explicitly enabled in metadata, use that (including `import` page)
          .orElse(metadata.useWizard.contains(false).option(FullQName))     // else if wizard is explicitly disabled in metadata, use the full mode
          .orElse {                                                         // else use mode from property
            FormRunner.formRunnerQNameProperty("oxf.fr.detail.view.appearance")(updatedParams)
              .map {
                case qName if qName.namespace.prefix.isEmpty => QName(qName.localName, FRNamespace) // `wizard` or `full` without prefix
                case qName                                   => qName
              }
          }
          .getOrElse(FullQName)

      val useViewAppearance =
        modeQName.namespace.uri.nonAllBlank                             ||  // the mode is namespaced so use that, as it is an explicit custom mode
        isImportPage                                                    ||  // use wizard for import page
        (                                                                   // TODO: not sure which case this covers, review
          ! isFormBuilder             &&
          viewAppearance != FullQName &&                                    // right now, `fr:full` is not a real view appearance but the absence of a view appearance component
          Set("edit", "new", "test", "compile")(updatedParams.mode)         // intentionally no test on 'test-pdf'; TODO: check and say why; because readonly?
          // TODO: ^should be just `! isReadonlyMode || isCompile`?
        )

      useViewAppearance.option(viewAppearance)
    }

    val isReadonlyMode =
      FormRunner.isReadonlyModeFromString(updatedParams.app, updatedParams.form, updatedParams.mode)

    assert(
      ! (isPdf && ! FormRunner.isReadonlyModeFromString(updatedParams.app, updatedParams.form, updatedParams.mode)),
      "Inconsistent state: PDF mode but not readonly mode"
    )

    assert(
      ! (viewAppearanceOpt.contains(WizardQName) && isReadonlyMode),
      "Inconsistent state: wizard view appearance in readonly mode"
    )

    val disableCalculateInReadonlyModes =
      metadata.readonlyDisableCalculate.contains(true) || (
        ! metadata.readonlyDisableCalculate.contains(false) &&
        FormRunner.booleanFormRunnerProperty("oxf.fr.detail.readonly.disable-calculate")(updatedParams)
      )

    val tocModes = {

      val tocModesFromProperties = FormRunner.formRunnerProperty("oxf.fr.detail.toc.modes")(updatedParams).map(_.tokenizeToSet)

      metadata.tocModes
        .orElse(tocModesFromProperties)
        .getOrElse(DefaultTocModes)
        .filter(AllowedTocModes)
    }

    val tocMinSections = {

      // Use `-2` as internal magic value to indicate that the property is not set
      val tocMinSectionsFromProperties1 = FormRunner.formRunnerProperty("oxf.fr.detail.toc")(updatedParams).map(_.toInt).filter(_ != -2)
      val tocMinSectionsFromProperties2 = FormRunner.formRunnerProperty("oxf.fr.detail.toc.min-sections")(updatedParams).map(_.toInt).filter(_ != -2)

      metadata.tocMinSections
        .orElse(tocMinSectionsFromProperties1)
        .orElse(tocMinSectionsFromProperties2)
        .getOrElse(-1)
    }

    val tocPositionOpt = {

      val tocPositionFromProperties =
        FormRunner.formRunnerProperty("oxf.fr.detail.toc.position")(updatedParams).flatMap(_.trimAllToOpt)

      metadata.tocPosition                                    // explicitly in form definition wins
        .orElse(tocPositionFromProperties)                    // then property
        .orElse("top".some)                                   // defaults to `top`
        .filter(pos => pos == "top" || pos == "left") match { // `none` is allowed to be explicit and is the same as blank
          case Some(_) if isPdf => "top".some                 // force `top` for PDF
          case other            => other
        }
    }

    val hasToc =
      ! isFormBuilder                           && // Form Builder never has a TOC
      tocModes(updatedParams.mode)              && // mode must match
      tocMinSections >= 0                       && // negative value disables the TOC
      metadata.sectionCount >= tocMinSections   && // meet constraint on number of form sections
      tocPositionOpt.nonEmpty                   && // missing position disables the TOC
      ! viewAppearanceOpt.contains(WizardQName)    // Wizard view never has a separate TOC

    val validateSelectionControlsChoicesFromEncryptedParamOpt =
      getParam(InternalValidateSelectionControlsChoicesParam)
        .flatMap(FormRunner.decryptParameterIfNeeded(_).trimAllToOpt)
        .map(_.toBoolean)

    val validateSelectionControlsChoices =
      if (majorMode == "detail" && ! isReadonlyMode && ! isFormBuilder) {
        validateSelectionControlsChoicesFromEncryptedParamOpt
          .getOrElse(
            metadata.validateSelectionControlsChoices.contains(true) || (
              ! metadata.validateSelectionControlsChoices.contains(false) &&
                FormRunner.booleanFormRunnerProperty("oxf.fr.detail.validate-selection-controls-choices")(updatedParams)
            )
          )
      } else
        false

    def paramOpts(paramNames: Set[String]): Option[Boolean] =
      paramNames
        .iterator
        .flatMap(getParam)
        .flatMap(_.toBooleanOption)
        .nextOption()

    val disableParams: (Option[Boolean], Option[Boolean], Option[Boolean]) =
      if (isService || updatedParams.mode == "test-pdf")
        (
          paramOpts(DisableRelevantParams),
          paramOpts(DisableDefaultParams),
          paramOpts(DisableCalculateParams) // the parameter takes precedence
            .orElse((isReadonlyMode && disableCalculateInReadonlyModes).option(true))
        )
      else
        (None, None, None)

    FormRunnerConfig(
      app                              = updatedParams.app,
      form                             = updatedParams.form,
      formVersion                      = updatedParams.formVersion,
      majorMode                        = majorMode,
      viewAppearanceOpt                = viewAppearanceOpt,
      isPdf                            = isPdf,
      isReadonlyMode                   = isReadonlyMode,
      usePdfTemplate                   = usePdfTemplate,
      isStaticReadonly                 = isStaticReadonly,
      isTest                           = TestModes(updatedParams.mode),
      tocPositionOpt                   = if (hasToc) tocPositionOpt else None,
      validateSelectionControlsChoices = if (validateSelectionControlsChoices) Some(true) else None,
      disableRelevantOpt               = disableParams._1,
      disableDefaultOpt                = disableParams._2,
      disableCalculateOpt              = disableParams._3,
    )
  }
}
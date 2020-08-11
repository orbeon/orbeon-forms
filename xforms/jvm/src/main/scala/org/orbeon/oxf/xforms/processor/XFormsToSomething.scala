/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor

import java.{lang => jl}

import javax.xml.transform.stream.StreamResult
import org.orbeon.dom.Document
import org.orbeon.io.StringBuilderWriter
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.{ExternalContext, URLRewriter}
import org.orbeon.oxf.http.Credentials
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.URIProcessorOutputImpl.{URIReferences, URIReferencesState}
import org.orbeon.oxf.processor._
import org.orbeon.oxf.processor.impl.DependenciesProcessorInput
import org.orbeon.oxf.util.{IndentedLogger, NetUtils, NumberUtils, WhitespaceMatching}
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.analysis.{IdGenerator, Metadata, XFormsAnnotator, XFormsExtractor}
import org.orbeon.oxf.xforms.state.{AnnotatedTemplate, XFormsStateManager, XFormsStaticStateCache}
import org.orbeon.oxf.xml._
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult
import org.xml.sax.SAXException

import scala.util.control.NonFatal

/**
 * This processor handles XForms initialization and produces an XHTML document which is a
 * translation from the source XForms + XHTML.
 */
abstract class XFormsToSomething extends ProcessorImpl {

  selfProcessor =>

  import XFormsToSomething._

  protected def produceOutput(
    pipelineContext      : PipelineContext,
    outputName           : String,
    externalContext      : ExternalContext,
    indentedLogger       : IndentedLogger,
    stage2CacheableState : Stage2CacheableState,
    containingDocument   : XFormsContainingDocument,
    xmlReceiver          : XMLReceiver
  ): Unit

  addInputInfo(new ProcessorInputOutputInfo(InputAnnotatedDocument))
  addInputInfo(new ProcessorInputOutputInfo("namespace")) // This input ensures that we depend on a portlet namespace
  addOutputInfo(new ProcessorInputOutputInfo(OutputDocument))

  /**
   * Case where an XML response must be generated.
   */
  override def createOutput(outputName: String): ProcessorOutput = {

    val output = new URIProcessorOutputImpl(selfProcessor, outputName, InputAnnotatedDocument) {

      def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit =
        doIt(pipelineContext, xmlReceiver, this, outputName)

      override protected def supportsLocalKeyValidity = true

      // NOTE: As of 2010-03, caching of the output should never happen
      // - more work is needed to make this work properly
      // - not many use cases benefit
      override def getLocalKeyValidity(pipelineContext: PipelineContext, uriReferences: URIReferences): ProcessorImpl.KeyValidity =
        null
    }
    addOutput(outputName, output)
    output
  }

  override def createInput(inputName: String): ProcessorInput =
    if (inputName == InputAnnotatedDocument) {
      // Insert processor on the fly to handle dependencies. This is a bit tricky: we used to have an
      // XSLT/XInclude before `XFormsToXHTML`. This step handled XBL dependencies. Now that it is removed, we
      // need a mechanism to detect dependencies. So we insert a step here.
      // Return an input which handles dependencies
      // The system actually has two processors:
      // - stage1 is the processor automatically inserted below for the purpose of handling dependencies
      // - stage2 is the actual `oxf:xforms-to-xhtml` which actually does XForms processing
      val originalInput = super.createInput(inputName)
      new DependenciesProcessorInput(selfProcessor, inputName, originalInput) {
        // Return dependencies object, set by stage2 before reading its input
        protected def getURIReferences(pipelineContext: PipelineContext): URIReferences =
          selfProcessor.getState(pipelineContext).asInstanceOf[Stage2TransientState].stage1CacheableState
      }
    } else {
      super.createInput(inputName)
    }

  override def reset(context: PipelineContext): Unit =
    setState(context, new Stage2TransientState)

  private def doIt(
    pipelineContext : PipelineContext,
    xmlReceiver     : XMLReceiver,
    processorOutput : URIProcessorOutputImpl,
    outputName      : String
  ): Unit = {

    val externalContext = NetUtils.getExternalContext
    val htmlLogger      = Loggers.getIndentedLogger("html")
    val cachingLogger   = Loggers.getIndentedLogger("cache")

    val customTracer = pipelineContext.getAttribute("orbeon.cache.test.tracer").asInstanceOf[XFormsStaticStateCache.CacheTracer]
    val cacheTracer =
      if (customTracer != null)
        customTracer
      else
        new LoggingCacheTracer(cachingLogger)

    val initializeXFormsDocument =
      Option(pipelineContext.getAttribute("orbeon.cache.test.initialize-xforms-document").asInstanceOf[jl.Boolean]) forall
        (_.booleanValue)

    // Read and try to cache the complete XForms+XHTML document with annotations
    val (stage2CacheableState, containingDocumentFromReadOpt) = {

      // Side-effects of calling `readCacheInputAsObject`
      var containingDocumentOpt: Option[XFormsContainingDocument] = None
      var cachedStatus         : Boolean                          = false

      val stage2CacheableState =
        readCacheInputAsObject(
          pipelineContext,
          getInputByName(InputAnnotatedDocument),
          new CacheableInputReader[Stage2CacheableState] {
            def read(pipelineContext: PipelineContext, processorInput: ProcessorInput): Stage2CacheableState = {

              // Compute annotated XForms document + static state document
              val stage1CacheableState = new Stage1CacheableState

              // Store dependencies container in state before reading
              selfProcessor.getState(pipelineContext).asInstanceOf[Stage2TransientState].stage1CacheableState = stage1CacheableState

              // Read static state from input
              val (stage2CacheableState, staticState) = readStaticState(pipelineContext, cachingLogger, cacheTracer)

              // Create containing document and initialize XForms engine
              // NOTE: Create document here so we can do appropriate analysis of caching dependencies
              val uriResolver =
                new XFormsURIResolver(
                  selfProcessor,
                  processorOutput,
                  pipelineContext,
                  InputAnnotatedDocument,
                  XMLParsing.ParserConfiguration.PLAIN
                )

              val containingDocument =
                new XFormsContainingDocument(
                  staticState,
                  uriResolver,
                  PipelineResponse.getResponse(xmlReceiver, externalContext),
                  initializeXFormsDocument
                )

              gatherInputDependencies(containingDocument, cachingLogger) foreach {
                case (url, credentialsOpt) => stage1CacheableState.addReference(null, url, credentialsOpt.orNull)
              }

              containingDocumentOpt = Some(containingDocument)
              stage2CacheableState
            }

            override def foundInCache(): Unit =
              cachedStatus = true
          }
        )

      assert(containingDocumentOpt.isEmpty && cachedStatus || containingDocumentOpt.isDefined && ! cachedStatus)

      (stage2CacheableState, containingDocumentOpt)
    }

    try {
      // Create containing document if not done yet
      val containingDocument =
        containingDocumentFromReadOpt match {
          case None =>

            // In this case, we found the static state digest and more in the cache, but we must now create a new XFormsContainingDocument from this information
            cacheTracer.digestAndTemplateStatus(Option(stage2CacheableState.staticStateDigest))

            val staticState =
              XFormsStaticStateCache.findDocument(stage2CacheableState.staticStateDigest) match {
                case Some((cachedState, _)) if cachedState.topLevelPart.metadata.bindingsIncludesAreUpToDate =>
                  // Found static state in cache
                  cacheTracer.staticStateStatus(found = true, cachedState.digest)
                  cachedState
                case _ =>
                  // Not found static state in cache OR it is out of date, create static state from input
                  // NOTE: In out of date case, could clone static state and reprocess instead?

                  //xxxxx just logging
    //              if (cachedState != null)
    //                cachingLogger.logDebug("", "out-of-date static state by digest in cache due to: " + cachedState.topLevelPart.metadata.debugOutOfDateBindingsIncludesJava)
                  val staticStateBits = new StaticStateBits(pipelineContext, cachingLogger, stage2CacheableState.staticStateDigest)
                  val staticState =
                    XFormsStaticStateImpl.createFromStaticStateBits(
                      staticStateBits.staticStateDocument,
                      stage2CacheableState.staticStateDigest,
                      staticStateBits.metadata,
                      staticStateBits.template
                    )
                  cacheTracer.staticStateStatus(found = false, staticState.digest)
                  XFormsStaticStateCache.storeDocument(staticState)
                  staticState
              }

            val uriResolver =
              new XFormsURIResolver(
                selfProcessor,
                processorOutput,
                pipelineContext,
                InputAnnotatedDocument,
                XMLParsing.ParserConfiguration.PLAIN
              )

              new XFormsContainingDocument(
                staticState,
                uriResolver,
                PipelineResponse.getResponse(xmlReceiver, externalContext),
                initializeXFormsDocument
              )
          case Some(containingDocument) =>
            cacheTracer.digestAndTemplateStatus(None)
            containingDocument
        }


      // Output resulting document
      if (initializeXFormsDocument)
        produceOutput(pipelineContext, outputName, externalContext, htmlLogger, stage2CacheableState, containingDocument, xmlReceiver)

      // Notify state manager
      // Scope because dynamic properties can cause lazy XPath evaluations
      XFormsAPI.withContainingDocument(containingDocument) {
        XFormsStateManager.afterInitialResponse(containingDocument, disableDocumentCache = false)
      }
    } catch {
      case NonFatal(t) =>
        htmlLogger.logDebug("", "throwable caught during initialization.")
        throw new OXFException(t)
    }
  }

  private def readStaticState(
    pipelineContext : PipelineContext,
    logger          : IndentedLogger,
    cacheTracer     : XFormsStaticStateCache.CacheTracer
  ): (Stage2CacheableState, XFormsStaticState) = {

    val staticStateBits = new StaticStateBits(pipelineContext, logger, null)
    val staticState =
      XFormsStaticStateCache.findDocument(staticStateBits.staticStateDigest) match {
        case Some((cachedState, _)) if cachedState.topLevelPart.metadata.bindingsIncludesAreUpToDate =>
          cacheTracer.staticStateStatus(found = true, cachedState.digest)
          cachedState
        case _ =>
          // Not found static state in cache OR it is out of date, create and initialize static state object
          // xxxx logging
  //        if (cachedState != null)
  //          logger.logDebug("", "out-of-date static state by digest in cache due to: " + cachedState.topLevelPart.metadata.debugOutOfDateBindingsIncludesJava)
          val newStaticState = XFormsStaticStateImpl.createFromStaticStateBits(
            staticStateBits.staticStateDocument,
            staticStateBits.staticStateDigest,
            staticStateBits.metadata,
            staticStateBits.template
          )
          cacheTracer.staticStateStatus(found = false, newStaticState.digest)
          XFormsStaticStateCache.storeDocument(newStaticState)
          newStaticState
      }

    // Update input dependencies object
    (new Stage2CacheableState(staticStateBits.staticStateDigest, staticStateBits.template), staticState)
  }

  private class StaticStateBits(
    val pipelineContext           : PipelineContext,
    val logger                    : IndentedLogger,
    val existingStaticStateDigest : String
  ) {

    private val isLogStaticStateInput = XFormsProperties.getDebugLogging.contains("html-static-state")
    private val computeDigest         = isLogStaticStateInput || existingStaticStateDigest == null

    val metadata: Metadata = Metadata(new IdGenerator(1), isTopLevelPart = true)

    logger.startHandleOperation("", "reading input", "existing digest", existingStaticStateDigest)

    private val digestReceiver = if (computeDigest) new DigestContentHandler else null
    val documentResult = new LocationDocumentResult
    private val extractorOutput = {

      val documentReceiver = TransformerUtils.getIdentityTransformerHandler
      documentReceiver.setResult(documentResult)

      if (isLogStaticStateInput)
        if (computeDigest)
          new TeeXMLReceiver(documentReceiver, digestReceiver, getDebugReceiver(logger))
        else
          new TeeXMLReceiver(documentReceiver, getDebugReceiver(logger))
      else
        if (computeDigest)
          new TeeXMLReceiver(documentReceiver, digestReceiver)
        else
          documentReceiver
    }

    // Read the input through the annotator and gather namespace mappings
    //
    // Output of annotator is:
    // - annotated page template (TODO: this should not include model elements)
    // - extractor
    // Output of extractor is:
    // - static state document
    // - optionally: digest
    // - optionally: debug output
    val template: AnnotatedTemplate = AnnotatedTemplate(new SAXStore)

    readInputAsSAX(
      pipelineContext,
      InputAnnotatedDocument,
      new WhitespaceXMLReceiver(
        new XFormsAnnotator(
          this.template.saxStore,
          new XFormsExtractor(
            Option[XMLReceiver](
              new WhitespaceXMLReceiver(
                extractorOutput,
                WhitespaceMatching.defaultBasePolicy,
                WhitespaceMatching.basePolicyMatcher
              )
            ),
            metadata,
            Option[AnnotatedTemplate](template),
            ".",
            XXBLScope.Inner,
            true,
            false
          ),
          metadata,
          true
        ),
        WhitespaceMatching.defaultHTMLPolicy,
        WhitespaceMatching.htmlPolicyMatcher
      )
    )

    val staticStateDocument: Document = documentResult.getDocument

    val staticStateDigest: String =
      if (computeDigest)
        NumberUtils.toHexString(digestReceiver.getResult)
      else
        null

    assert(! isLogStaticStateInput || existingStaticStateDigest == null || this.staticStateDigest == existingStaticStateDigest)

    logger.endHandleOperation("computed digest", this.staticStateDigest)

    private def getDebugReceiver(indentedLogger: IndentedLogger): ForwardingXMLReceiver = {
      val identity = TransformerUtils.getIdentityTransformerHandler
      val writer = new StringBuilderWriter(new jl.StringBuilder)
      identity.setResult(new StreamResult(writer))
      new ForwardingXMLReceiver(identity) {
        @throws[SAXException]
        override def endDocument(): Unit = {
          super.endDocument()
          // Log out at end of document
          indentedLogger.logDebug("", "static state input", "input", writer.result)
        }
      }
    }
  }
}

object XFormsToSomething {

  private val InputAnnotatedDocument = "annotated-document"
  private val OutputDocument         = "document"

  // What can be cached by the first stage: URI dependencies
  private class Stage1CacheableState extends URIReferences

  // What can be cached by the second stage: SAXStore and static state
  class Stage2CacheableState(val staticStateDigest: String, val template: AnnotatedTemplate) extends URIReferences

  // State passed by the second stage to the first stage.
  // NOTE: This extends URIReferencesState because we use URIProcessorOutputImpl.
  // It is not clear that we absolutely need URIProcessorOutputImpl in the second stage, but right now we keep it,
  // because XFormsURIResolver requires URIProcessorOutputImpl.
  private class Stage2TransientState extends URIReferencesState {
    var stage1CacheableState: Stage1CacheableState = null
  }

  private def gatherInputDependencies(
    containingDocument : XFormsContainingDocument,
    logger             : IndentedLogger
  ): Iterator[(String, Option[Credentials])] = {

    val instanceDependencies = {
      // Add static instance source dependencies for top-level models
      // TODO: check all models/instances
      val topLevelPart = containingDocument.getStaticState.topLevelPart
      for {
        model                 <- topLevelPart.getModelsForScope(topLevelPart.startScope).iterator
        (_, instance)         <- model.instances
        if instance.dependencyURL.isDefined && ! instance.cache
        resolvedDependencyURL =
          XFormsUtils.resolveServiceURL(
            containingDocument,
            instance.element,
            instance.dependencyURL.get,
            URLRewriter.REWRITE_MODE_ABSOLUTE
          )
      } yield {
        if (logger.isDebugEnabled)
            logger.logDebug("", "adding document cache dependency for non-cacheable instance", "instance URI", resolvedDependencyURL)

        (resolvedDependencyURL, instance.credentials)
      }
    }

    // Set caching dependencies if the input was actually read
    // Q: should use static dependency information instead? what about schema imports and instance replacements?
    // TODO: We should also use dependencies computed in XFormsModelSchemaValidator.SchemaInfo.
    val xmlSchemaDependencies =
      for {
        currentModel     <- containingDocument.models.iterator
        schemaURIs       <- currentModel.getSchemaURIs.toList
        currentSchemaURI <- schemaURIs
      } yield {
        if (logger.isDebugEnabled)
          logger.logDebug("", "adding document cache dependency for schema", "schema URI", currentSchemaURI)
        (currentSchemaURI, None)
      }

    val xblBindingDependencies =
      for (include <- containingDocument.getStaticState.topLevelPart.metadata.bindingIncludes)
        yield ("oxf:" + include, None)

    instanceDependencies ++ xmlSchemaDependencies ++ xblBindingDependencies
  }
}

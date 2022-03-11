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

import cats.syntax.option._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.{ExternalContext, UrlRewriteMode}
import org.orbeon.oxf.http.{BasicCredentials, URIReferences}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.URIProcessorOutputImpl.URIReferencesState
import org.orbeon.oxf.processor._
import org.orbeon.oxf.processor.impl.DependenciesProcessorInput
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.analysis.PartAnalysisBuilder
import org.orbeon.oxf.xforms.state.{AnnotatedTemplate, XFormsStateManager, XFormsStaticStateCache}
import org.orbeon.oxf.xml._
import org.orbeon.xforms.XFormsCrossPlatformSupport

import java.{lang => jl}
import scala.util.control.NonFatal


/**
 * This processor handles XForms initialization and produces an XHTML document which is a
 * translation from the source XForms + XHTML.
 */
abstract class XFormsProcessorBase extends ProcessorImpl {

  selfProcessor =>

  import XFormsProcessorBase._

  protected def produceOutput(
    pipelineContext    : PipelineContext,
    outputName         : String,
    externalContext    : ExternalContext,
    indentedLogger     : IndentedLogger,
    template           : AnnotatedTemplate,
    containingDocument : XFormsContainingDocument,
    xmlReceiver        : XMLReceiver
  ): Unit

  addInputInfo(new ProcessorInputOutputInfo(InputAnnotatedDocument))
  addInputInfo(new ProcessorInputOutputInfo("namespace")) // This input ensures that we depend on a portlet namespace
  addOutputInfo(new ProcessorInputOutputInfo(OutputDocument))

  /**
   * Case where an XML response must be generated.
   */
  override def createOutput(outputName: String): ProcessorOutput =
    addOutput(
      outputName,
      new URIProcessorOutputImpl(selfProcessor, outputName, InputAnnotatedDocument) {

        def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit =
          doIt(pipelineContext, xmlReceiver, this, outputName)

        override protected def supportsLocalKeyValidity = true

        // NOTE: As of 2010-03, caching of the output should never happen
        // - more work is needed to make this work properly
        // - not many use cases benefit
        override def getLocalKeyValidity(pipelineContext: PipelineContext, uriReferences: URIReferences): ProcessorImpl.KeyValidity =
          null
      }
    )

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

    val externalContext = XFormsCrossPlatformSupport.externalContext
    val htmlLogger      = Loggers.getIndentedLogger("html")
    val cachingLogger   = Loggers.getIndentedLogger("cache")

    val cacheTracer =
      Option(pipelineContext.getAttribute("orbeon.cache.test.tracer").asInstanceOf[XFormsStaticStateCache.CacheTracer]) getOrElse
        new LoggingCacheTracer(cachingLogger)

    val initializeXFormsDocument =
      Option(pipelineContext.getAttribute("orbeon.cache.test.initialize-xforms-document").asInstanceOf[jl.Boolean]) forall
        (_.booleanValue)

    val uriResolver =
      new XFormsURIResolver(
        selfProcessor,
        processorOutput,
        pipelineContext,
        InputAnnotatedDocument,
        ParserConfiguration.Plain
      )

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
              val (stage2CacheableState, staticState) =
                readStaticState(readInputAsSAX(pipelineContext, InputAnnotatedDocument, _), cachingLogger, cacheTracer)

              // Create containing document and initialize XForms engine
              // NOTE: Create document here so we can do appropriate analysis of caching dependencies
              val containingDocument =
                XFormsContainingDocumentBuilder(
                  staticState    = staticState,
                  uriResolver    = uriResolver.some,
                  response       = Option(PipelineResponse.getResponse(xmlReceiver, externalContext)),
                  mustInitialize = initializeXFormsDocument
                )

              collectDependencies(containingDocument, cachingLogger) foreach {
                case (url, credentialsOpt) => stage1CacheableState.addReference(null, url, credentialsOpt.orNull)
              }

              containingDocumentOpt = containingDocument.some
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

            // In this case, we found the static state digest and more in the cache, but we must now create a new
            // `XFormsContainingDocument` from this information
            cacheTracer.digestAndTemplateStatus(Option(stage2CacheableState.staticStateDigest))

            val staticState =
              XFormsStaticStateCache.findDocument(stage2CacheableState.staticStateDigest) match {
                case Some((cachedState, _)) if cachedState.topLevelPart.bindingsIncludesAreUpToDate =>
                  // Found static state in cache
                  cacheTracer.staticStateStatus(found = true, cachedState.digest)
                  cachedState
                case other =>
                  // Not found static state in cache OR it is out of date, create static state from input
                  // NOTE: In out of date case, could clone static state and reprocess instead?

                  other foreach { case (cachedState, _) =>
                    cachingLogger.logDebug("", s"out-of-date static state by digest in cache due to: ${cachedState.topLevelPart.debugOutOfDateBindingsIncludes}")
                  }

                  val staticState =
                    PartAnalysisBuilder.createFromStaticStateBits(
                      StaticStateBits.fromXmlReceiver(
                        stage2CacheableState.staticStateDigest.some,
                        readInputAsSAX(pipelineContext, InputAnnotatedDocument, _))(
                        cachingLogger
                      )
                    )
                  cacheTracer.staticStateStatus(found = false, staticState.digest)
                  XFormsStaticStateCache.storeDocument(staticState)
                  staticState
              }

              XFormsContainingDocumentBuilder(
                staticState    = staticState,
                uriResolver    = uriResolver.some,
                response       = Option(PipelineResponse.getResponse(xmlReceiver, externalContext)),
                mustInitialize = initializeXFormsDocument
              )
          case Some(containingDocument) =>
            cacheTracer.digestAndTemplateStatus(None)
            containingDocument
        }


      // Output resulting document
      if (initializeXFormsDocument)
        produceOutput(pipelineContext, outputName, externalContext, htmlLogger, stage2CacheableState.template, containingDocument, xmlReceiver)

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
}

private object XFormsProcessorBase {

  val InputAnnotatedDocument = "annotated-document"
  val OutputDocument         = "document"

  // What can be cached by the first stage: URI dependencies
  class Stage1CacheableState extends URIReferences

  // What can be cached by the second stage: SAXStore and static state
  class Stage2CacheableState(val staticStateDigest: String, val template: AnnotatedTemplate) extends URIReferences

  // State passed by the second stage to the first stage.
  // NOTE: This extends URIReferencesState because we use URIProcessorOutputImpl.
  // It is not clear that we absolutely need URIProcessorOutputImpl in the second stage, but right now we keep it,
  // because XFormsURIResolver requires URIProcessorOutputImpl.
  class Stage2TransientState extends URIReferencesState {
    var stage1CacheableState: Stage1CacheableState = null
  }

  def collectDependencies(
    containingDocument : XFormsContainingDocument,
    logger             : IndentedLogger
  ): Iterator[(String, Option[BasicCredentials])] = {

    val instanceDependencies = {
      // Add static instance source dependencies for top-level models
      // TODO: check all models/instances
      val topLevelPart = containingDocument.staticState.topLevelPart
      for {
        model                 <- topLevelPart.getModelsForScope(topLevelPart.startScope).iterator
        (_, instance)         <- model.instances
        if instance.dependencyURL.isDefined && ! instance.cache
        resolvedDependencyURL =
          XFormsCrossPlatformSupport.resolveServiceURL(
            containingDocument,
            instance.element,
            instance.dependencyURL.get,
            UrlRewriteMode.Absolute
          )
      } yield {
        if (logger.debugEnabled)
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
        if (logger.debugEnabled)
          logger.logDebug("", "adding document cache dependency for schema", "schema URI", currentSchemaURI)
        (currentSchemaURI, None)
      }

    val xblBindingDependencies =
      for (include <- containingDocument.staticState.topLevelPart.bindingIncludes)
        yield ("oxf:" + include, None)

    instanceDependencies ++ xmlSchemaDependencies ++ xblBindingDependencies
  }

  def readStaticState(
    read          : XMLReceiver => Unit,
    cachingLogger : IndentedLogger,
    cacheTracer   : XFormsStaticStateCache.CacheTracer
  ): (Stage2CacheableState, XFormsStaticState) = {

    val staticStateBits =
      StaticStateBits.fromXmlReceiver(None, read)(cachingLogger)

    val staticState =
      XFormsStaticStateCache.findDocument(staticStateBits.staticStateDigest) match {
        case Some((cachedState, _)) if cachedState.topLevelPart.bindingsIncludesAreUpToDate =>
          cacheTracer.staticStateStatus(found = true, cachedState.digest)
          cachedState
        case other =>
          // Not found static state in cache OR it is out of date, create and initialize static state object

          other foreach { case (cachedState, _) =>
            cachingLogger.logDebug("", s"out-of-date static state by digest in cache due to: ${cachedState.topLevelPart.debugOutOfDateBindingsIncludes}")
          }

          val newStaticState = PartAnalysisBuilder.createFromStaticStateBits(staticStateBits)
          cacheTracer.staticStateStatus(found = false, newStaticState.digest)
          XFormsStaticStateCache.storeDocument(newStaticState)
          newStaticState
      }

    // Update input dependencies object
    (new Stage2CacheableState(staticStateBits.staticStateDigest, staticStateBits.template), staticState)
  }
}

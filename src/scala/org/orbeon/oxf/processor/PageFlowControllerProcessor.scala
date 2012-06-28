/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.processor

import PageFlowControllerProcessor._
import PageFlowControllerBuilder._
import collection.JavaConverters._
import java.util.regex.Pattern
import java.util.{List ⇒ JList, Map ⇒ JMap}
import org.dom4j.{QName, Document, Element}
import org.orbeon.errorified.Exceptions._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.pipeline.api.ExternalContext.Request
import org.orbeon.oxf.pipeline.api.{XMLReceiver, PipelineContext}
import org.orbeon.oxf.processor.PageFlowControllerProcessor.FileRoute
import org.orbeon.oxf.processor.PageFlowControllerProcessor.PageFlow
import org.orbeon.oxf.processor.PageFlowControllerProcessor.PageRoute
import org.orbeon.oxf.processor.RegexpMatcher.MatchResult
import org.orbeon.oxf.processor.impl.{DigestState, DigestTransformerOutputImpl}
import org.orbeon.oxf.processor.pipeline.ast._
import org.orbeon.oxf.processor.pipeline.{PipelineConfig, PipelineProcessor}
import org.orbeon.oxf.resources.ResourceNotFoundException
import org.orbeon.oxf.util.DebugLogger._
import org.orbeon.oxf.util.URLRewriterUtils._
import org.orbeon.oxf.util.{LoggerFactory, IndentedLogger, PipelineUtils, NetUtils}
import org.orbeon.oxf.webapp.HttpStatusCodeException
import org.orbeon.oxf.xml.Dom4j
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.oxf.xml.XMLUtils.DigestContentHandler
import org.orbeon.oxf.xml.dom4j.Dom4jUtils._
import org.orbeon.oxf.xml.dom4j.LocationData

// Orbeon Forms application controller
class PageFlowControllerProcessor extends ProcessorImpl {

    addInputInfo(new ProcessorInputOutputInfo(ControllerInput, ControllerNamespaceURI))

    override def start(pipelineContext: PipelineContext) {

        implicit val logger = new IndentedLogger(Logger, "")

        // Get or compile page flow
        val pageFlow = readCacheInputAsObject(pipelineContext, getInputByName(ControllerInput), new CacheableInputReader[PageFlow] {
            def read(context: PipelineContext, input: ProcessorInput) = {
                val configRoot = readInputAsDOM4J(pipelineContext, ControllerInput).getRootElement
                val controllerValidity  = ProcessorImpl.getInputValidity(pipelineContext, getInputByName(ControllerInput))
                compile(configRoot, controllerValidity)
            }
        })

        // Run it
        val externalContext = NetUtils.getExternalContext
        val request = externalContext.getRequest
        val path = request.getRequestPath
        val method = request.getMethod

        lazy val logParams = Seq("controller" → pageFlow.file.orNull, "path" → path, "method" → method)

        info("processing request", logParams)

        // If required, store information about resources to rewrite in the pipeline context for downstream use, e.g. by
        // oxf:xhtml-rewrite. This allows consumers who would like to rewrite resources into versioned resources to
        // actually know what a "resource" is.
        if (pageFlow.pathMatchers.nonEmpty) {
            Option(pipelineContext.getAttribute(PathMatchers).asInstanceOf[JList[PathMatcher]]) match {
                case Some(existingPathMatchers) ⇒
                    // Add if we come after others (in case of nested page flows)
                    val allMatchers = existingPathMatchers.asScala ++ pageFlow.pathMatchers
                    pipelineContext.setAttribute(PathMatchers, allMatchers.asJava)
                case None ⇒
                    // Set if we are the first
                    pipelineContext.setAttribute(PathMatchers, pageFlow.pathMatchers.asJava)
            }
        }

        def runErrorRoute(t: Throwable) = pageFlow.errorRoute match {
            case Some(errorRoute) ⇒
                // Run the error route
                error("error caught", logParams)
                externalContext.getResponse.setStatus(500)
                errorRoute.process(pipelineContext, request, MatchResult(matches = false))
            case None ⇒
                // We don't have an error route so throw instead
                throw t
        }

        def runNotFoundRoute(t: Option[Throwable]) = pageFlow.notFoundRoute match {
            case Some(notFoundRoute) ⇒
                // Run the not found route
                info("page not found", logParams)
                externalContext.getResponse.setStatus(404)
                try notFoundRoute.process(pipelineContext, request, MatchResult(matches = false))
                catch { case t ⇒ runErrorRoute(t) }
            case None ⇒
                // We don't have a not found route so throw instead
                runErrorRoute(t getOrElse new HttpStatusCodeException(404))
        }

        // Run the first matching entry if any
        var matchResult: MatchResult = null
        pageFlow.routes find { route ⇒ matchResult = MatchResult(route.routeElement.pattern, path); matchResult.matches } match {
            case Some(route: FileRoute) ⇒
                // Run the given route and let the caller handle errors
                route.process(pipelineContext, request, matchResult)
            case Some(route: PageRoute) ⇒
                // Run the given route and handle "not found" and error conditions
                try route.process(pipelineContext, request, matchResult)
                catch { case t ⇒
                    getRootThrowable(t) match {
                        case e: HttpStatusCodeException if e.code == 404 ⇒ runNotFoundRoute(Some(t))
                        case e: ResourceNotFoundException                ⇒ runNotFoundRoute(Some(t))
                        case e                                           ⇒ runErrorRoute(t)
                    }
                }
            case None ⇒
                // Handle "not found"
                runNotFoundRoute(None)
        }
    }

    // Compile a controller file
    def compile(configRoot: Element, controllerValidity: AnyRef)(implicit logger: IndentedLogger) = {
        // Controller format:
        //
        // - config: files*, page*, epilogue?, not-found-handler?, error-handler?
        // - files:  @id?, @path-info, @matcher?, @mime-type?, @versioned?
        // - page:   @id?, @path-info, @matcher?, @default-submission?, @model?, @view?

        val stepProcessorContext = new StepProcessorContext(controllerValidity)
        val locationData = configRoot.getData.asInstanceOf[LocationData]
        val urlBase = Option(locationData) map (_.getSystemID) orNull

        val globalInstancePassing =
            att(configRoot, InstancePassingPropertyName) getOrElse getPropertySet.getString(InstancePassingPropertyName, DEFAULT_INSTANCE_PASSING)

        // NOTE: We use a global property, not an oxf:page-flow scoped one
        val defaultVersioned =
            att(configRoot, "versioned") map (_.toBoolean) getOrElse isResourcesVersioned

        val epilogueElement = configRoot.element("epilogue")
        val epilogueURL     = Option(epilogueElement) flatMap (att(_, "url")) getOrElse getPropertySet.getString(EpiloguePropertyName)

        val topLevelElements = Dom4j.elements(configRoot)

         // Prepend a page for submissions
        val submissionPage = {
            val submissionPath  = getPropertySet.getString(SubmissionPathPropertyName, SubmissionPathDefault)
            val submissionModel = getPropertySet.getStringOrURIAsString(SubmissionModelPropertyName)
            if ((submissionPath eq null) && (submissionModel ne null) || (submissionPath ne null) && (submissionModel eq null))
                throw new OXFException("Only one of properties " + SubmissionPathPropertyName + " and " + SubmissionModelPropertyName + " is set.")

            PageElement(None, submissionPath, Pattern.compile(submissionPath), None, Some(submissionModel), None, configRoot)
        }

        val routeElements: Seq[RouteElement] =
            submissionPage +: (
                for (e ← topLevelElements filter (e ⇒ Set("files", "page")(e.getName)))
                yield e.getName match {
                    case "files" ⇒ FileElement(e, defaultVersioned)
                    case "page"  ⇒ PageElement(e)
                }
            )

        val pagesElementsWithIds = routeElements collect { case page: PageElement if page.id.isDefined ⇒ page }
        val pathIdToPath              = pagesElementsWithIds map (p ⇒ p.id.get → p.path) toMap
        val pageIdToSetvaluesDocument = pagesElementsWithIds map (p ⇒ p.id.get → getSetValuesDocument(p.element)) filter (_._2 ne null) toMap

        val pathMatchers =
            routeElements collect
            { case files: FileElement if files.versioned ⇒ files } map
            (f ⇒ new PathMatcher(f.path, f.mimeType.orNull, f.versioned))

        // Compile the pipeline for the given page element
        def compile(page: PageElement) = {
            val ast = createPipelineAST(
                page.element,
                controllerValidity,
                stepProcessorContext,
                urlBase,
                globalInstancePassing,
                epilogueURL,
                epilogueElement,
                pathIdToPath.asJava,
                pageIdToSetvaluesDocument.asJava)

            // For debugging
            if (logger.isDebugEnabled) {
                val astDocumentHandler = new ASTDocumentHandler
                ast.walk(astDocumentHandler)
                debug("Created PFC pipeline", Seq("path" → page.path, "pipeline" → ('\n' + domToString(astDocumentHandler.getDocument))))
            }

            PipelineProcessor.createConfigFromAST(ast)
        }

        // All routes
        val routes: Seq[Route] =
            for (e ← routeElements)
            yield e match {
                case files: FileElement ⇒ FileRoute(files)
                case page: PageElement  ⇒ PageRoute(page, compile)
            }

        // Find a handler route
        def handler(elementNames: Set[String]) =
            topLevelElements find (e ⇒ elementNames(e.getName)) flatMap (att(_, "page")) flatMap
                { pageId ⇒ routes collectFirst { case page: PageRoute if page.routeElement.id == Some(pageId) ⇒ page } }

        PageFlow(routes, handler(Set("not-found-handler")), handler(Set("error-handler")), pathMatchers, Option(urlBase))
    }

    def createPipelineAST(
            element: Element,
            controllerValidity: AnyRef,
            stepProcessorContext: StepProcessorContext,
            urlBase: String,
            globalInstancePassing: String,
            epilogueURL: String,
            epilogueElement: Element,
            pageIdToPathInfo: JMap[String, String],
            pageIdToSetvaluesDocument: JMap[String, Document]) =
        new ASTPipeline {

            setValidity(controllerValidity)

            // The pipeline has an input with matcher results
            val matcherParam = addParam(new ASTParam(ASTParam.INPUT, "matches"))

            val epilogueData = new ASTOutput(null, "html")
            val epilogueModelData = new ASTOutput(null, "epilogue-model-data")
            val epilogueInstance = new ASTOutput(null, "epilogue-instance")

            // Page
            handlePage(stepProcessorContext, urlBase, getStatements, element,
                    matcherParam.getName, epilogueData, epilogueModelData,
                    epilogueInstance, pageIdToPathInfo, pageIdToSetvaluesDocument,
                    globalInstancePassing)

            // Epilogue
            addStatement(new ASTChoose(new ASTHrefId(epilogueData)) {
                addWhen(new ASTWhen("not(/*/@xsi:nil = 'true')") {
                    setNamespaces(PageFlowControllerBuilder.NAMESPACES_WITH_XSI_AND_XSLT)
                    handleEpilogue(urlBase, getStatements, epilogueURL, epilogueElement,
                            epilogueData, epilogueModelData, epilogueInstance)
                })
                addWhen(new ASTWhen() {
                    // Make sure we execute the model if there is a model but no view
                    addStatement(new ASTProcessorCall(NULL_SERIALIZER_PROCESSOR_QNAME) {
                        addInput(new ASTInput("data", new ASTHrefId(epilogueModelData)))
                    })
                })
            })
        }
}

object PageFlowControllerProcessor {

    val Logger = LoggerFactory.createLogger(classOf[PageFlowControllerProcessor])

    val ControllerInput = "controller"
    val ControllerNamespaceURI = "http://www.orbeon.com/oxf/controller"

    // Properties
    val InstancePassingPropertyName = "instance-passing"
    val EpiloguePropertyName        = "epilogue"
    val PathMatchers                = "path-matchers"

    val SubmissionModelPropertyName = "xforms-submission-model"
    val SubmissionPathPropertyName  = "xforms-submission-path"
    val SubmissionPathDefault       = "/xforms-server-submit"

    // Route elements
    sealed trait RouteElement { def path: String; def pattern: Pattern; def id: Option[String] }
    case class FileElement(id: Option[String], path: String, pattern: Pattern, mimeType: Option[String], versioned: Boolean) extends RouteElement
    case class PageElement(id: Option[String], path: String, pattern: Pattern, defaultSubmission: Option[String], model: Option[String], view: Option[String], element: Element) extends RouteElement

    object FileElement {
        // id?, path-info?, matcher?, mime-type?, versioned?
        def apply(e: Element, defaultVersioned: Boolean): FileElement = {
            val path = getPath(e)
            FileElement(idAtt(e), path, compilePattern(e, path), att(e, "mime-type"), att(e, "versioned") map (_ == "true") getOrElse defaultVersioned)
        }
    }

    object PageElement {
        // id?, path-info, matcher?, default-submission?, model?, view?
        def apply(e: Element): PageElement = {
            val path = getPath(e)
            PageElement(idAtt(e), path, compilePattern(e, path), att(e, "default-submission"), att(e, "model"), att(e, "view"), e)
        }
    }

    // Only read mime types config once (used for serving files)
    lazy val MimeTypes = ResourceServer.readMimeTypeConfig

    // Routes
    sealed trait Route { def routeElement: RouteElement; def process(pipelineContext: PipelineContext, request: Request, matchResult: MatchResult) }

    case class FileRoute(routeElement: FileElement) extends Route {
        // Serve a file by path
        def process(pipelineContext: PipelineContext, request: Request, matchResult: MatchResult) =
            if (request.getMethod == "GET")
                ResourceServer.serveResource(MimeTypes, request.getRequestPath)
            else
                throw new HttpStatusCodeException(403)
    }

    case class PageRoute(routeElement: PageElement, compile: PageElement ⇒ PipelineConfig) extends Route {

        lazy val pipelineConfig = compile(routeElement)

        // Run a page
        def process(pipelineContext: PipelineContext, request: Request, matchResult: MatchResult) = {

            // PipelineConfig is reusable, but PipelineProcessor is not
            val pipeline = new PipelineProcessor(pipelineConfig)

            // Provide matches input using a digest, because that's equivalent to how the PFC was working when the
            // matches were depending on oxf:request. If we don't do this, then
            val matchesProcessor = new DigestedProcessor(RegexpMatcher.writeXML(_, matchResult))

            // Connect matches input and start pipeline
            PipelineUtils.connect(matchesProcessor, "data", pipeline, "matches")

            matchesProcessor.reset(pipelineContext)
            pipeline.reset(pipelineContext)
            pipeline.start(pipelineContext)
        }
    }

    case class PageFlow(routes: Seq[Route], notFoundRoute: Option[PageRoute], errorRoute: Option[PageRoute], pathMatchers: Seq[PathMatcher], file: Option[String])

    def att(e: Element, name: String) = Option(e.attributeValue(name))
    def idAtt(e: Element) = att(e, "id")
    def getPath(e: Element) = att(e, "path-info") orElse att(e, "path") ensuring (_.isDefined) get

    // Support "regexp" and "oxf:perl5-matcher" for backward compatibility
    val RegexpQNames = Set(new QName("regexp"), new QName("perl5-matcher", OXF_PROCESSORS_NAMESPACE))

    // Compile and convert glob expression if needed
    def compilePattern(e: Element, path: String) =
        RegexpMatcher.compilePattern(path, ! RegexpQNames(extractAttributeValueQName(e, "matcher")))
}

// This processor provides digest-based caching based on any content
class DigestedProcessor(content: XMLReceiver ⇒ Unit) extends ProcessorImpl {

    override def createOutput(name: String) =
        new DigestTransformerOutputImpl(DigestedProcessor.this, name) {

            def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver) = content(xmlReceiver)

            def fillOutState(pipelineContext: PipelineContext, digestState: DigestState) = true

            def computeDigest(pipelineContext: PipelineContext, digestState: DigestState) = {
                val digester = new DigestContentHandler("MD5")
                content(digester)
                digester.getResult
            }
        }

    override def reset(context: PipelineContext): Unit =
        setState(context, new DigestState)
}

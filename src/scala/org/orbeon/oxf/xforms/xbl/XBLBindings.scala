/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.xbl

import org.orbeon.oxf.xml.dom4j.{Dom4jUtils, LocationData}
import org.orbeon.oxf.xforms.event.XFormsEventHandlerImpl
import org.orbeon.saxon.dom4j.DocumentWrapper
import org.dom4j.{Document, QName, Element}
import org.orbeon.oxf.util.{XPathCache, IndentedLogger}
import org.orbeon.oxf.xforms.xbl.XBLBindingsBase.{Scope, AbstractBinding}
import org.orbeon.oxf.xforms._
import analysis.{PartAnalysisImpl, Metadata}
import processor.handlers.XHTMLHeadHandler
import org.orbeon.oxf.properties.PropertySet
import org.apache.commons.lang.StringUtils
import collection.mutable.LinkedHashSet
import java.util.{Collections => JCollections}
import collection.JavaConversions._
import collection.mutable.LinkedHashMap
import java.lang.IllegalStateException

class XBLBindings(indentedLogger: IndentedLogger, partAnalysis: PartAnalysisImpl, metadata: Metadata, xblDocuments: java.util.List[Document])
    extends XBLBindingsBase(indentedLogger, partAnalysis, metadata, xblDocuments) {

    val allGlobals = new LinkedHashMap[QName, XBLBindingsBase.Global]

    lazy val baselineResources = {

        // xxx TODO
        val metadata = partAnalysis.metadata

        // Register baseline includes
        XFormsProperties.getResourcesBaseline match {
            case baselineProperty: PropertySet.Property =>
                val tokens = LinkedHashSet(StringUtils.split(baselineProperty.value.toString): _*) toIterable

                val (scripts, styles) =
                    (for {
                        token <- tokens
                        qName = Dom4jUtils.extractTextValueQName(baselineProperty.namespaces, token, true)
                    } yield
                        if (metadata.isXBLBinding(qName.getNamespaceURI, qName.getName)) {
                            val binding = getComponentBindings.get(qName)
                            (binding.scripts, binding.styles)
                        } else {
                            // Load XBL document
                            val xblDocument = readXBLResource(metadata.getAutomaticXBLMappingPath(qName.getNamespaceURI, qName.getName))

                            // Extract xbl:xbl/xbl:script
                            val scripts = Dom4jUtils.elements(xblDocument.getRootElement, XFormsConstants.XBL_SCRIPT_QNAME)

                            // Try to find binding
                            (Dom4jUtils.elements(xblDocument.getRootElement, XFormsConstants.XBL_BINDING_QNAME) map
                                (e => new XBLBindingsBase.AbstractBinding(e, partAnalysis.getNamespaceMapping("", e), scripts, null)) find
                                    (_.qNameMatch == qName)) match {
                                case Some(binding) => (binding.scripts, binding.styles)
                                case None => (JCollections.emptyList[Element], JCollections.emptyList[Element])
                            }
                        }) unzip

                // Return tuple with two sets
                (LinkedHashSet(XHTMLHeadHandler.xblResourcesToSeq(scripts.flatten): _*),
                    LinkedHashSet(XHTMLHeadHandler.xblResourcesToSeq(styles.flatten): _*))
            case _ => (collection.Set.empty[String], collection.Set.empty[String])
        }
    }

    def processElementIfNeeded(indentedLogger: IndentedLogger, controlElement: Element, controlPrefixedId: String, locationData: LocationData,
                               controlsDocumentInfo: DocumentWrapper, containerScope: XBLBindingsBase.Scope): XBLBindingsBase.ConcreteBinding = {

        // Create concrete binding if there is an abstract binding
        Option(abstractBindings) flatMap
            (b => Option(b.get(controlElement.getQName))) map
                (binding => createConcreteBinding(indentedLogger, controlElement, controlPrefixedId, locationData, controlsDocumentInfo, containerScope, binding)) orNull
    }

    def createConcreteBinding(indentedLogger: IndentedLogger, controlElement: Element, boundControlPrefixedId: String, locationData: LocationData,
               controlsDocumentInfo: DocumentWrapper, containerScope: XBLBindingsBase.Scope, abstractBinding: AbstractBinding): XBLBindingsBase.ConcreteBinding = {

        // New prefix corresponds to bound element prefixed id
        val newPrefix = boundControlPrefixedId + XFormsConstants.COMPONENT_SEPARATOR

        // Generate the shadow content for this particular binding
        val fullShadowTreeDocument = withProcessAutomaticXBL {
            generateShadowTree(indentedLogger, controlsDocumentInfo, controlElement, abstractBinding.bindingElement, newPrefix)
        }

        if (fullShadowTreeDocument != null) // null if there is no template
            createConcreteBinding(indentedLogger, controlElement, boundControlPrefixedId, locationData, containerScope, abstractBinding, fullShadowTreeDocument, newPrefix)
        else
            null
    }

    def withProcessAutomaticXBL[T](body: => T) = {
        // Check how many automatic XBL includes we have so far
        val initialIncludesCount = metadata.bindingIncludes.size

        // Run body
        val result = body

        // Process newly added automatic XBL includes if any
        val finalIncludesCount = metadata.bindingIncludes.size
        if (finalIncludesCount > initialIncludesCount) {
            indentedLogger.startHandleOperation("", "adding XBL bindings")
            val xblBindingCount =
                (metadata.bindingIncludes.view(initialIncludesCount, finalIncludesCount) map
                    (r => extractXBLBindings(readXBLResource(r), partAnalysis))).foldLeft(0)(_ + _)

            indentedLogger.endHandleOperation(
                "xbl:xbl count", finalIncludesCount - initialIncludesCount toString,
                "xbl:binding count", xblBindingCount.toString,
                "total xbl:binding count", abstractBindings.size.toString)
        }

        result
    }

    def createConcreteBinding(indentedLogger: IndentedLogger, controlElement: Element, boundControlPrefixedId: String, locationData: LocationData,
               containerScope: XBLBindingsBase.Scope, abstractBinding: AbstractBinding, fullShadowTreeDocument: Document, newPrefix: String) = {

        val newInnerScope = partAnalysis.newScope(containerScope, boundControlPrefixedId)
        // NOTE: Outer scope is not necessarily the container scope!
        val outerScope = partAnalysis.getResolutionScopeByPrefixedId(boundControlPrefixedId)

        val compactShadowTreeDocument = filterShadowTree(indentedLogger, fullShadowTreeDocument, controlElement, newPrefix, newInnerScope, outerScope)

        // Register models placed under xbl:implementation
        val implementationModels =
            if (abstractBinding.implementations.size > 0) {
                // Say we DO annotate because these models are outside the template
                addModelDocuments(abstractBinding.implementations, newPrefix, true, newInnerScope, outerScope, XFormsConstants.XXBLScope.inner)
            } else
                Seq.empty

        if (implementationModels.size > 0 && indentedLogger.isDebugEnabled)
            indentedLogger.logDebug("", "registered XBL implementation model documents", "count", implementationModels.size.toString)

        // Extract and register models from within the template
        val templateModels = {
            val compactShadowTreeWrapper = new DocumentWrapper(compactShadowTreeDocument, null, XPathCache.getGlobalConfiguration)
            val templateModelDocuments = PartAnalysisBase.extractNestedModels(compactShadowTreeWrapper, true, locationData)

            if (templateModelDocuments.size > 0)
                // Say we don't annotate documents because already annotated as part as template processing
                addModelDocuments(templateModelDocuments, newPrefix, false, newInnerScope, null, null)
            else
                Seq.empty
        }

        if (templateModels.size > 0 && indentedLogger.isDebugEnabled)
            indentedLogger.logDebug("", "created and registered XBL template model documents", "count", templateModels.size.toString)

        // Analyze the models first
        partAnalysis.analyzeModelsXPathForScope(newInnerScope)

        // Remember concrete binding information
        val newConcreteBinding = new XBLBindingsBase.ConcreteBinding(abstractBinding, newInnerScope, fullShadowTreeDocument, compactShadowTreeDocument, implementationModels ++ templateModels)
        concreteBindings.put(boundControlPrefixedId, newConcreteBinding)

        // Process globals here as the component is in use
        processGlobals(abstractBinding, indentedLogger, locationData)

        // Extract xbl:xbl/xbl:script and xbl:binding/xbl:resources/xbl:style
        // TODO: should do this here, in order to include only the scripts and resources actually used

        // Gather xbl:handlers/xbl:handler attached to bound node
        processHandlers(abstractBinding.handlers, newInnerScope, outerScope, containerScope, boundControlPrefixedId, newPrefix)

        newConcreteBinding
    }

    def processGlobals(abstractBinding: XBLBindingsBase.AbstractBinding, indentedLogger: IndentedLogger, locationData: LocationData) {
        if (abstractBinding.global != null && !allGlobals.contains(abstractBinding.qNameMatch)) {

            val pseudoBoundElement = Dom4jUtils.NULL_DOCUMENT.getRootElement

            val topLevelScopeForGlobals = partAnalysis.startScope

            val globalFullShadowTreeDocument = generateGlobalShadowTree(indentedLogger, abstractBinding.bindingElement, abstractBinding.global)
            val globalCompactShadowTreeDocument = filterShadowTree(indentedLogger, globalFullShadowTreeDocument, pseudoBoundElement,  topLevelScopeForGlobals.getFullPrefix, topLevelScopeForGlobals, topLevelScopeForGlobals)

            // Extract and register models from within the template

            val compactShadowTreeWrapper = new DocumentWrapper(globalCompactShadowTreeDocument, null, XPathCache.getGlobalConfiguration)
            val templateModelDocuments = PartAnalysisBase.extractNestedModels(compactShadowTreeWrapper, true, locationData)
            if (templateModelDocuments.size > 0) {
                // Say we don't annotate documents because already annotated as part as template processing
                val models = addModelDocuments(templateModelDocuments, topLevelScopeForGlobals.getFullPrefix, false, topLevelScopeForGlobals, null, null)

                if (models.size > 0 && indentedLogger.isDebugEnabled)
                    indentedLogger.logDebug("", "created and registered XBL global model documents", "count", models.size.toString)
            }

            allGlobals += (abstractBinding.qNameMatch -> new XBLBindingsBase.Global(globalFullShadowTreeDocument, globalCompactShadowTreeDocument))
        }
    }

    def processHandlers(handlerElements: Seq[Element], newInnerScope: Scope, outerScope: Scope,
                        containerScope: XBLBindingsBase.Scope, controlPrefixedId: String, newPrefix: String) {
        for (handlerElement <- handlerElements) {
            // Register xbl:handler as an action handler

            // Annotate handler and gather scope information
            val currentHandlerAnnotatedElement = annotateHandler(handlerElement, newPrefix, newInnerScope, outerScope, XFormsConstants.XXBLScope.inner).getRootElement

            // NOTE: <xbl:handler> has similar attributes as XForms actions, in particular @event, @phase, etc.
            val controlStaticId = XFormsUtils.getStaticIdFromId(controlPrefixedId)
            val prefix = containerScope.getFullPrefix

            val eventHandler = new XFormsEventHandlerImpl(prefix,
                currentHandlerAnnotatedElement,
                null,
                controlStaticId,
                prefix,
                true,
                controlStaticId,
                currentHandlerAnnotatedElement.attributeValue(XFormsConstants.XBL_HANDLER_EVENT_ATTRIBUTE_QNAME),
                null, // no target attribute allowed in XBL
                currentHandlerAnnotatedElement.attributeValue(XFormsConstants.XBL_HANDLER_PHASE_ATTRIBUTE_QNAME),
                currentHandlerAnnotatedElement.attributeValue(XFormsConstants.XBL_HANDLER_PROPAGATE_ATTRIBUTE_QNAME),
                currentHandlerAnnotatedElement.attributeValue(XFormsConstants.XBL_HANDLER_DEFAULT_ACTION_ATTRIBUTE_QNAME),
                null,
                null)

            partAnalysis.registerActionHandler(eventHandler)

            // Extract scripts in the handler
            val handlerWrapper = new DocumentWrapper(currentHandlerAnnotatedElement.getDocument, null, XPathCache.getGlobalConfiguration)
            partAnalysis.extractXFormsScripts(handlerWrapper, newPrefix)
        }
    }

    /**
     * Generate shadow content for the given control id and XBL binding.
     *
     * @param indentedLogger    logger
     * @param documentWrapper   wrapper around controls document
     * @param boundElement      element to which the binding applies
     * @param binding           corresponding <xbl:binding>
     * @param prefix            prefix of the ids within the new shadow tree, e.g. component1$component2$
     * @return shadow tree document
     */
    protected def generateShadowTree(indentedLogger: IndentedLogger, documentWrapper: DocumentWrapper, boundElement: Element, binding: Element, prefix: String): Document = {
        binding.element(XFormsConstants.XBL_TEMPLATE_QNAME) match {
            case templateElement: Element =>
                if (indentedLogger.isDebugEnabled) {
                    indentedLogger.startHandleOperation("", "generating XBL shadow content",
                        "bound element", Dom4jUtils.elementToDebugString(boundElement),
                        "binding id", XFormsUtils.getElementStaticId(binding))
                }

                // TODO: in script mode, XHTML elements in template should only be kept during page generation

                // Here we create a completely separate document

                // 1. Apply optional preprocessing step (usually XSLT)
                // Copy as the template element may be used many times
                val shadowTreeDocument = applyPipelineTransform(templateElement, boundElement)

                // 2. Apply xbl:attr, xbl:content, xxbl:attr and index xxbl:scope
                XBLTransformer.transform(documentWrapper, shadowTreeDocument, boundElement)

                // 3: Annotate tree
                val hasUpdateFull = hasFullUpdate(shadowTreeDocument)
                val annotatedShadowTreeDocument = annotateShadowTree(shadowTreeDocument, prefix, hasUpdateFull)
                if (indentedLogger.isDebugEnabled) {
                    indentedLogger.endHandleOperation("document", if (logShadowTrees) Dom4jUtils.domToString(annotatedShadowTreeDocument) else null)
                }
                annotatedShadowTreeDocument
            case _ => null
        }
    }

    protected def addModelDocuments(modelDocuments: Seq[Document], prefix: String, annotate: Boolean, newInnerScope: XBLBindingsBase.Scope,
                                    outerScope: XBLBindingsBase.Scope, startScope: XFormsConstants.XXBLScope) = {
        for {
            modelDocument <- modelDocuments
            annotated =
                if (annotate) {
                    val md = annotateShadowTree(modelDocument, prefix, false)
                    gatherScopeMappingsAndTransform(md, prefix,
                        newInnerScope,
                        outerScope, startScope, null, false, "/")
                    md
                } else {
                    modelDocument
                }
        } yield
            partAnalysis.addModel(newInnerScope, annotated)
    }

    // This function is not called as of 2011-06-28 but if/when we support removing scopes, check these notes:
    // - deindex prefixed ids => Scope
    // - remove models associated with scope
    // - remove control analysis
    // - deindex scope id => Scope
    def removeScope(scope: XBLBindingsBase.Scope) {
        throw new IllegalStateException("NIY")
    }
}
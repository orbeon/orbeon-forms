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
package org.orbeon.oxf.processor.xinclude


import XIncludeProcessor._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.pipeline.api.{FunctionLibrary, PipelineContext, XMLReceiver}
import org.orbeon.oxf.processor._
import org.orbeon.oxf.processor.transformer.TransformerURIResolver
import org.orbeon.oxf.processor.transformer.XPathProcessor
import org.orbeon.oxf.processor.transformer.xslt.XSLTTransformer
import org.orbeon.oxf.properties.PropertyStore
import org.orbeon.oxf.util.{LoggerFactory, XPathCache}
import org.orbeon.oxf.xml._
import org.orbeon.oxf.xml.XMLUtils.{ParserConfiguration, addOrReplaceAttribute}
import org.orbeon.oxf.xml.dom4j.LocationData
import org.orbeon.saxon.om.ValueRepresentation
import org.xml.sax._
import ProcessorImpl._
import URIProcessorOutputImpl.URIReferences
import collection.JavaConverters._
import XMLConstants._

/**
 * XInclude processor.
 *
 * This processor reads a document on its "config" input that may contain XInclude directives. It
 * produces on its output a resulting document with the XInclude directives processed.
 *
 * For now, this processor only supports <xi:include href="..." parse="xml"/> with no xpointer,
 * encoding, accept, or accept-language attribute. <xi:fallback> is not supported.
 *
 * TODO: Merge caching with URL generator, possibly XSLT transformer. See also XFormsToXHTML processor.
 */
class XIncludeProcessor extends ProcessorImpl {

    self ⇒

    addInputInfo(new ProcessorInputOutputInfo(AttributesInput, XSLTTransformer.XSLT_PREFERENCES_CONFIG_NAMESPACE_URI))
    addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG))
    addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA))

    override def createOutput(name: String) =
        addOutput(name, new URIProcessorOutputImpl(self, name, INPUT_CONFIG) {
            def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver) {
                // Read attributes input only if connected (just in case, for backward compatibility, although it shouldn't happen)
                val configurationAttributes =
                    if (getConnectedInputs.get(AttributesInput) ne null) {
                        readCacheInputAsObject(pipelineContext, getInputByName(AttributesInput), new CacheableInputReader[Map[String, Boolean]] {
                            def read(pipelineContext: PipelineContext, input: ProcessorInput) = {
                                val preferencesDocument = readInputAsDOM4J(pipelineContext, input)
                                val propertyStore = new PropertyStore(preferencesDocument)
                                val propertySet = propertyStore.getGlobalPropertySet

                                propertySet.getBooleanProperties.asScala map { case (k, v) ⇒ k → v.booleanValue } toMap
                            }
                        })
                    } else
                        Map.empty[String, Boolean]

                // URL resolver is initialized with a parser configuration which can be configured to support external entities or not.
                val parserConfiguration = new ParserConfiguration(false, false, ! (configurationAttributes.get("external-entities") exists (_ == false)))
                val uriResolver = new TransformerURIResolver(self, pipelineContext, INPUT_CONFIG, parserConfiguration)

                /**
                 * The code below reads the input in a SAX store, before replaying the SAX store to the
                 * XIncludeContentHandler.
                 *
                 * This may seem inefficient, but it is necessary (unfortunately) so the URI resolver which will be
                 * called by the XInclude content handler has the right parents in the stack. When we do
                 * readInputAsSAX() here, this might run a processor PR which might be outside of the current
                 * pipeline PI that executed the XInclude processor. When PR run, PI might not be in the processor
                 * stack anymore, because it is possible for PR to be outside of PI. When PR calls a SAX method of
                 * the XInclude handler, there is no executeChildren() that runs, so when the XInclude handler
                 * method is called the parent stack might not include PI, and so reading an input of PI will fail.
                 *
                 * The general rule is that when you receive SAX events, you might not be in the right context.
                 * While the readInput...() method is running you can't rely on having the right context, so you
                 * shouldn't call other readInput...() methods or do anything that relies on the context.
                 *
                 * We don't have this problem with Saxon, because Saxon will first read the input stylesheet and
                 * then processes it. So when the processing happens, the readInput...() methods that reads the
                 * stylesheet has returned.
                 */

                // Try to cache URI references
                // NOTE: Always be careful not to cache refs to TransformerURIResolver. We seem to be fine here.
                var wasRead = false
                readCacheInputAsObject(pipelineContext, getInputByName(INPUT_CONFIG), new CacheableInputReader[URIReferences] {
                    def read(context: PipelineContext, input: ProcessorInput) = {
                        val uriReferences = new URIReferences
                        val saxStore = new SAXStore
                        readInputAsSAX(pipelineContext, INPUT_CONFIG, saxStore)
                        saxStore.replay(new XIncludeXMLReceiver(pipelineContext, xmlReceiver, uriReferences, uriResolver))
                        wasRead = true
                        uriReferences
                    }
                })

                // Read if not already read
                if (! wasRead) {
                    val saxStore = new SAXStore
                    readInputAsSAX(pipelineContext, INPUT_CONFIG, saxStore)
                    saxStore.replay(new XIncludeXMLReceiver(pipelineContext, xmlReceiver, null, uriResolver))
                }
            }
        })
}

object XIncludeProcessor {

    val Logger = LoggerFactory.createLogger(classOf[XIncludeProcessor])
    val AttributesInput = "attributes"
    val XPointerPattern = """xpath\((.*)\)""".r

    class XIncludeXMLReceiver(
            pipelineContext: PipelineContext,
            val parent: Option[XIncludeXMLReceiver],
            xmlReceiver: XMLReceiver,
            uriReferences: URIReferences,
            uriResolver: TransformerURIResolver,
            xmlBase: String,
            generateXMLBase: Boolean,
            outputLocator: OutputLocator)
        extends ForwardingXMLReceiver(xmlReceiver) {

        self ⇒

        def this(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver, uriReferences: URIReferences, uriResolver: TransformerURIResolver) =
            this(pipelineContext, None, xmlReceiver, uriReferences, uriResolver, null, true, new OutputLocator)

        private val topLevel = parent.isEmpty
        private val namespaceContext = new NamespaceContext

        // This part is a bit tricky because we would like to deal with namespaces properly. The XInclude spec is a bit
        // flexible here, but what it is striving for is to do as if the XML infosets have been merged. In our case,
        // this means that we don't want namespaces from the including document to be visible to the included document.
        // Also, we don't want redundant namespace events to be dispatched, or interleaved declarations/undeclarations
        // for the same prefixes. So we nicely merge namespace events so that the output is as clean as possible from
        // the point of view of namespace events.
        //
        // We keep a stack of context information (ElementContext), with, for each element:
        //
        // 1. the pending mappings between the closest relevant ancestor element
        // 2. its in-scope namespace mappings
        // 3. whether the context is "relevant", that is useful for finding pending mappings
        //
        // For the root element of an included document only, we use this information to search the closest relevant
        // ancestor ElementContext, and use it to compute an exact list of undeclarations/declarations to send when
        // needed.
        //
        // For non-root elements, the pending mappings are just the mappings between the element and its parent,
        // obtained from NamespaceContext.
        //
        // We don't send out namespace events when reaching xi:include itself, as those would be unneeded events.
        //
        def findPending(pending: Map[String, String]) =
            if (! topLevel && level == 0) {

                def ancestors = Iterator.iterate(self)(_.parent.orNull) takeWhile (_ ne null)
                def relevantContexts = ancestors flatMap (_.contexts) filter (_.relevant)

                val closestAncestorMappings =
                    relevantContexts.next().context.mappingsWithDefault filterNot
                    { case (prefix, _) ⇒ prefix == "xml" }

                val toUndeclare = closestAncestorMappings.toSet -- pending
                val toDeclare   = pending.toSet -- closestAncestorMappings

                (toUndeclare map { case (prefix, _) ⇒ prefix → "" }) ++ toDeclare toMap
            } else
                pending

        private var currentLocator: Locator = null
        private var level = 0

        case class ElementContext(pending: Map[String, String], context: NamespaceContext#Context, relevant: Boolean)

        private var _contexts: List[ElementContext] = ElementContext(Map(), namespaceContext.current, relevant = topLevel) :: Nil
        def contexts = _contexts

        override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {

            val isXIURI = Set(XINCLUDE_URI, OLD_XINCLUDE_URI)(uri)
            val isInclude = isXIURI && localname == "include"

            // Do this before startElement(), which will modify the pending mappings
            val pending = findPending(namespaceContext.pending)

            namespaceContext.startElement()
            _contexts ::= ElementContext(pending, namespaceContext.current, ! isInclude)

            val newAttributes =
                if (! topLevel && level == 0 && generateXMLBase)
                    addOrReplaceAttribute(attributes, XML_URI, "xml", "base", xmlBase)
                else
                    attributes

            if (isInclude) {
                // Entering xi:include element

                if (uri == OLD_XINCLUDE_URI)
                    Logger.warn("Using incorrect XInclude namespace URI: '" + uri + "'; should use '" + XINCLUDE_URI + "' at " + new LocationData(outputLocator).toString)

                val href     = attributes.getValue("href")
                val parse    = Option(attributes.getValue("parse"))
                val xpointer = Option(attributes.getValue("xpointer"))

                // Whether to create/update xml:base attribute or not
                val generateXMLBase = {
                    val disableXMLBase = attributes.getValue(XXINCLUDE_OMIT_XML_BASE.getNamespaceURI, XXINCLUDE_OMIT_XML_BASE.getName)
                    val fixupXMLBase = attributes.getValue(XINCLUDE_FIXUP_XML_BASE.getNamespaceURI, XINCLUDE_FIXUP_XML_BASE.getName)
                    ! (disableXMLBase == "true" || fixupXMLBase == "false")
                }

                if (parse exists (_ != "xml"))
                    throw new ValidationException("Invalid 'parse' attribute value: " + parse.get, new LocationData(outputLocator))

                // Get SAXSource
                val base = Option(outputLocator) map (_.getSystemId) orNull
                val source = uriResolver.resolve(href, base)
                val systemId = source.getSystemId
                
                // Keep URI reference for caching
                if (uriReferences ne null)
                    uriReferences.addReference(base, href, null, null)

                def createChildReceiver =
                    new XIncludeXMLReceiver(pipelineContext, Some(self), getXMLReceiver, uriReferences, uriResolver, systemId, generateXMLBase, outputLocator)
                
                try {
                    xpointer match {
                        case Some(XPointerPattern(xpath)) ⇒
                            // xpath() scheme

                            // Document is read entirely in memory for XPath processing
                            val document = TransformerUtils.readTinyTree(XPathCache.getGlobalConfiguration, source, false)

                            val result =
                                XPathCache.evaluate(
                                    document,
                                    xpath,
                                    new NamespaceMapping(namespaceContext.current.mappingsWithDefault.toMap.asJava),
                                    Map.empty[String, ValueRepresentation].asJava,
                                    FunctionLibrary.instance,
                                    null,
                                    systemId,
                                    null)

                            // Each resulting object is output through the next level of processing
                            for (item ← result.asScala)
                                XPathProcessor.streamResult(pipelineContext, createChildReceiver, item, new LocationData(outputLocator))

                        case Some(xpointer) ⇒
                            // Other XPointer schemes are not supported
                            throw new ValidationException("Invalid 'xpointer' attribute value: " + xpointer, new LocationData(outputLocator))
                        case None ⇒
                            // No xpointer attribute specified, just stream the child document
                            val xmlReader = source.getXMLReader
                            val xmlReceiver = createChildReceiver

                            xmlReader.setContentHandler(xmlReceiver)
                            xmlReader.setProperty(SAX_LEXICAL_HANDLER, xmlReceiver)

                            xmlReader.parse(new InputSource(systemId)) // Yeah, the SAX API doesn't make much sense
                    }
                } catch {
                    case e: Exception ⇒
                        // Resource error, must go to fallback if possible
                        if (systemId != null)
                            throw new OXFException("Error while handling: " + systemId, e)
                        else
                            throw new OXFException(e)
                }
            } else if (isXIURI) {
                // NOTE: Should support xi:fallback
                throw new ValidationException("Invalid XInclude element: " + localname, new LocationData(outputLocator))
            } else {
                // Start a regular element
                playStartPrefixMappings(_contexts.head.pending)
                super.startElement(uri, localname, qName, newAttributes)
            }

            level += 1
        }

        override def endElement(uri: String, localname: String, qName: String) {
            level -= 1

            if (Set(XINCLUDE_URI, OLD_XINCLUDE_URI)(uri) && localname == "include") {
                // Nothing to do when existing xi:include element
            } else {
                super.endElement(uri, localname, qName)
                playEndPrefixMappings(_contexts.head.pending)
            }

            namespaceContext.endElement()
            _contexts = _contexts.tail
        }

        // Collect mappings but don't forward right away
        override def startPrefixMapping(prefix: String, uri: String): Unit =
            namespaceContext.startPrefixMapping(prefix, uri)

        // Don't do anything, we take care of regenerating these (unneeded!) events
        override def endPrefixMapping(s: String) = ()

        override def setDocumentLocator(locator: Locator): Unit = {
            // Keep track of current locator
            this.currentLocator = locator

            // Set output locator to be our own locator if we are at the top-level
            if (topLevel)
                super.setDocumentLocator(outputLocator)
        }

        override def startDocument(): Unit = {
            outputLocator.push(currentLocator)

            // Make sure only once startDocument() is produced
            if (topLevel)
                super.startDocument()
        }

        override def endDocument(): Unit = {
            // Make sure only once endDocument() is produced
            if (topLevel)
                super.endDocument()

            outputLocator.pop()
        }

        def playStartPrefixMappings(mappings: Map[String, String]): Unit =
            for ((prefix, uri) ← mappings)
                super.startPrefixMapping(prefix, uri)

        def playEndPrefixMappings(mappings: Map[String, String]): Unit =
            for ((prefix, _) ← mappings)
                super.endPrefixMapping(prefix)
    }

    // This is the Locator object passed to the output. It supports a stack of input Locator objects in order to
    // correctly report location information of the included documents.
    class OutputLocator extends Locator {

        private var locators: List[Locator] = Nil
        private def currentLocator = locators.headOption flatMap Option.apply

        // locator can be null
        def push(locator: Locator): Unit = locators ::= locator
        def pop(): Unit                  = locators = locators.tail

        def getPublicId     = currentLocator map (_.getPublicId)     orNull
        def getSystemId     = currentLocator map (_.getSystemId)     orNull
        def getLineNumber   = currentLocator map (_.getLineNumber)   getOrElse -1
        def getColumnNumber = currentLocator map (_.getColumnNumber) getOrElse -1

        def size = locators.size
    }
}

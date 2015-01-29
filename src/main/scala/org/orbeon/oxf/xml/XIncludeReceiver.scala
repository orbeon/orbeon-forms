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
package org.orbeon.oxf.xml

import XMLConstants._
import collection.JavaConverters._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.pipeline.api.{FunctionLibrary, PipelineContext}
import org.orbeon.oxf.processor.transformer.TransformerURIResolver
import org.orbeon.oxf.processor.transformer.XPathProcessor
import org.orbeon.oxf.util.{XPath, LoggerFactory, XPathCache}
import org.orbeon.oxf.xml.dom4j.LocationData
import org.orbeon.saxon.om.ValueRepresentation
import org.xml.sax._
import org.orbeon.oxf.processor.URIProcessorOutputImpl.URIReferences
import XIncludeReceiver._
import scala.util.control.NonFatal

// Streaming XInclude processing
// NOTE: Does not support: <xi:fallback>, encoding, accept, or accept-language attributes.
class XIncludeReceiver(
        pipelineContext: PipelineContext,
        val parent     : Option[XIncludeReceiver],
        xmlReceiver    : XMLReceiver,
        uriReferences  : URIReferences,
        uriResolver    : TransformerURIResolver,
        xmlBase        : String,
        generateXMLBase: Boolean,
        outputLocator  : OutputLocator)
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
                SAXUtils.addOrReplaceAttribute(attributes, XML_URI, "xml", "base", xmlBase)
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
                uriReferences.addReference(base, href, null)

            def createChildReceiver =
                new XIncludeReceiver(pipelineContext, Some(self), getXMLReceiver, uriReferences, uriResolver, systemId, generateXMLBase, outputLocator)

            try {
                xpointer match {
                    case Some(XPointerPattern(xpath)) ⇒
                        // xpath() scheme

                        // Document is read entirely in memory for XPath processing
                        val document = TransformerUtils.readTinyTree(XPath.GlobalConfiguration, source, false)

                        val result =
                            XPathCache.evaluate(
                                document,
                                xpath,
                                new NamespaceMapping(namespaceContext.current.mappingsWithDefault.toMap.asJava),
                                Map.empty[String, ValueRepresentation].asJava,
                                FunctionLibrary.instance,
                                null,
                                systemId,
                                null,
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
                case NonFatal(t) ⇒
                    // Resource error, must go to fallback if possible
                    if (systemId != null)
                        throw new OXFException("Error while handling: " + systemId, t)
                    else
                        throw new OXFException(t)
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

object XIncludeReceiver {
    val Logger = LoggerFactory.createLogger(classOf[XIncludeReceiver])
    val XPointerPattern = """xpath\((.*)\)""".r
}

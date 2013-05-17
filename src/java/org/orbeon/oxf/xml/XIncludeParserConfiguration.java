/*
 * The Apache Software License, Version 1.1
 *
 *
 * Copyright (c) 2001-2003 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Xerces" and "Apache Software Foundation" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    nor may "Apache" appear in their name, without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation and was
 * originally based on software copyright (c) 2003, International
 * Business Machines, Inc., http://www.apache.org.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */
package org.orbeon.oxf.xml;

import orbeon.apache.xerces.impl.Constants;
import orbeon.apache.xerces.parsers.XML11Configuration;
import orbeon.apache.xerces.util.SymbolTable;
import orbeon.apache.xerces.xinclude.XIncludeNamespaceSupport;
import orbeon.apache.xerces.xni.XMLDocumentHandler;
import orbeon.apache.xerces.xni.grammars.XMLGrammarPool;
import orbeon.apache.xerces.xni.parser.XMLComponentManager;
import orbeon.apache.xerces.xni.parser.XMLConfigurationException;
import orbeon.apache.xerces.xni.parser.XMLDocumentSource;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * This parser configuration includes an <code>XIncludeHandler</code> in the pipeline
 * before the schema validator, or as the last component in the pipeline if there is
 * no schema validator.  Using this pipeline will enable processing according to the
 * XML Inclusions specification, to the conformance level described in
 * <code>XIncludeHandler</code>.
 *
 * @author Peter McCracken, IBM
 * @see orbeon.apache.xerces.xinclude.XIncludeHandler
 * @see org.orbeon.oxf.xml.XIncludeHandler
 */
public class XIncludeParserConfiguration extends XML11Configuration {

    private boolean handleXInclude;
    private XIncludeHandler fXIncludeHandler;

    /**
     * Feature identifier: allow notation and unparsed entity events to be sent out of order.
     */
    protected static final String ALLOW_UE_AND_NOTATION_EVENTS =
            Constants.SAX_FEATURE_PREFIX + Constants.ALLOW_DTD_EVENTS_AFTER_ENDDTD_FEATURE;

    /**
     * Property identifier: error reporter.
     */
    protected static final String XINCLUDE_HANDLER =
            Constants.XERCES_PROPERTY_PREFIX + Constants.XINCLUDE_HANDLER_PROPERTY;

    /**
     * Property identifier: error reporter.
     */
    protected static final String NAMESPACE_CONTEXT =
            Constants.XERCES_PROPERTY_PREFIX + Constants.NAMESPACE_CONTEXT_PROPERTY;

    /**
     * Default constructor.
     */
    public XIncludeParserConfiguration() {
        this(null, null, null, true);
    } // <init>()

    public XIncludeParserConfiguration(boolean handleXInclude) {
        this(null, null, null, handleXInclude);
    } // <init>()

    /**
     * Constructs a parser configuration using the specified symbol table,
     * grammar pool, and parent settings.
     * <p/>
     *
     * @param symbolTable    The symbol table to use.
     * @param grammarPool    The grammar pool to use.
     * @param parentSettings The parent settings.
     */
    public XIncludeParserConfiguration(
            SymbolTable symbolTable,
            XMLGrammarPool grammarPool,
            XMLComponentManager parentSettings,
            boolean handleXInclude) {
        super(symbolTable, grammarPool, parentSettings);
        this.handleXInclude = handleXInclude;

        if (handleXInclude) {
            fXIncludeHandler = new XIncludeHandler(this);
            addCommonComponent(fXIncludeHandler);

            final String[] recognizedFeatures = {
                    ALLOW_UE_AND_NOTATION_EVENTS
            };
            addRecognizedFeatures(recognizedFeatures);

            // add default recognized properties
            final String[] recognizedProperties =
                    {XINCLUDE_HANDLER, NAMESPACE_CONTEXT};
            addRecognizedProperties(recognizedProperties);

            setFeature(ALLOW_UE_AND_NOTATION_EVENTS, true);

            setProperty(XINCLUDE_HANDLER, fXIncludeHandler);
            setProperty(NAMESPACE_CONTEXT, new XIncludeNamespaceSupport());
        }
    } // <init>(SymbolTable,XMLGrammarPool)}


    /**
     * Configures the pipeline.
     */
    protected void configurePipeline() {
        super.configurePipeline();

        if(handleXInclude) {
            //configure DTD pipeline
            fDTDScanner.setDTDHandler(fDTDProcessor);
            fDTDProcessor.setDTDSource(fDTDScanner);
            fDTDProcessor.setDTDHandler(fXIncludeHandler);
            fXIncludeHandler.setDTDSource(fDTDProcessor);
            fXIncludeHandler.setDTDHandler(fDTDHandler);
            if (fDTDHandler != null) {
                fDTDHandler.setDTDSource(fXIncludeHandler);
            }

            // configure XML document pipeline: insert after DTDValidator and
            // before XML Schema validator
            XMLDocumentSource prev = null;
            if (fFeatures.get(XMLSCHEMA_VALIDATION) == Boolean.TRUE) {
                // we don't have to worry about fSchemaValidator being null, since
                // super.configurePipeline() instantiated it if the feature was set
                prev = fSchemaValidator.getDocumentSource();
            }
            // Otherwise, insert after the last component in the pipeline
            else {
                prev = fLastComponent;
                fLastComponent = fXIncludeHandler;
            }

            XMLDocumentHandler next = prev.getDocumentHandler();
            prev.setDocumentHandler(fXIncludeHandler);
            fXIncludeHandler.setDocumentSource(prev);
            if (next != null) {
                fXIncludeHandler.setDocumentHandler(next);
                next.setDocumentSource(fXIncludeHandler);
            }
        }

    } // configurePipeline()

    protected void configureXML11Pipeline() {
        super.configureXML11Pipeline();

        if (handleXInclude) {
            // configure XML 1.1. DTD pipeline
            fXML11DTDScanner.setDTDHandler(fXML11DTDProcessor);
            fXML11DTDProcessor.setDTDSource(fXML11DTDScanner);
            fXML11DTDProcessor.setDTDHandler(fXIncludeHandler);
            fXIncludeHandler.setDTDSource(fXML11DTDProcessor);
            fXIncludeHandler.setDTDHandler(fDTDHandler);
            if (fDTDHandler != null) {
                fDTDHandler.setDTDSource(fXIncludeHandler);
            }

            // configure XML document pipeline: insert after DTDValidator and
            // before XML Schema validator
            XMLDocumentSource prev = null;
            if (fFeatures.get(XMLSCHEMA_VALIDATION) == Boolean.TRUE) {
                // we don't have to worry about fSchemaValidator being null, since
                // super.configurePipeline() instantiated it if the feature was set
                prev = fSchemaValidator.getDocumentSource();
            }
            // Otherwise, insert after the last component in the pipeline
            else {
                prev = fLastComponent;
                fLastComponent = fXIncludeHandler;
            }

            XMLDocumentHandler next = prev.getDocumentHandler();
            prev.setDocumentHandler(fXIncludeHandler);
            fXIncludeHandler.setDocumentSource(prev);
            if (next != null) {
                fXIncludeHandler.setDocumentHandler(next);
                next.setDocumentSource(fXIncludeHandler);
            }
        }

    } // configureXML11Pipeline()

    public void setProperty(String propertyId, Object value)
            throws XMLConfigurationException {

        if (propertyId.equals(XINCLUDE_HANDLER)) {
        }

        super.setProperty(propertyId, value);
    } // setProperty(String,Object)

    public Collection getRecognizedFeatures() {
        final TreeSet ret = new TreeSet();
        ret.addAll(fRecognizedFeatures);
        // Xerces uses PARSER_SETTINGS internally and makes sure that nothing
        // from outside Xerces passes it in.  But we are exposing features collection
        // here so need to remove PARSER_SETTING in case the feature set is passed
        // back in to Xerces.
        ret.remove(PARSER_SETTINGS);
        // 2/16/2004 d : Xerces special cases  http://xml.org/sax/features/namespaces and 
        //               http://xml.org/sax/features/namespace-prefixes and consequently they
        //               won't be available unless we add them manually.
        ret.add(FEATURE_NS_PREFIXES);
        ret.add("http://xml.org/sax/features/namespaces");
        return ret;
    }

    public Map getFeatures() {
        // Xerces uses PARSER_SETTINGS internally and makes sure that nothing
        // from outside Xerces passes it in.  But we are exposing features collection
        // here so need to remove PARSER_SETTING in case the feature set is passed
        // back in to Xerces.
        final TreeMap ret = new TreeMap(fFeatures);
        ret.remove(PARSER_SETTINGS);
        return ret;
    }

    private static final String FEATURE_NS_PREFIXES
            = "http://xml.org/sax/features/namespace-prefixes";

    private static final String FEATURE_NS = "http://xml.org/sax/features/namespace-prefixes";

    /**
     * 4/9/2005 d : A more efficient way to find out if this cfg recognizes a resource.  ( More
     * efficient than creating a map anyway. )
     */
    public boolean isRecognizedFeature(final String id) {
        // Think this order of checks is one of the more optimal ones.  That is we see ids other 
        // than FEATURE_NS and FEATURE_NS_PREFIXES more often.
        boolean ret = !PARSER_SETTINGS.equals(id) && fRecognizedFeatures.contains(id);
        if (!ret) {
            ret = FEATURE_NS.equals(id) || FEATURE_NS_PREFIXES.equals(id);
        }
        return ret;
    }
}
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
package org.orbeon.oxf.processor.generator;

import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.cache.SimpleOutputCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.NonLazyUserDataDocument;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.NodeInfo;
import org.xml.sax.ContentHandler;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;

/**
 * DOM Document to sax event processor.
 *
 * A note wrt caching.  Each instance of DOMGenerator creates a unique key for caching.  The
 * validity, however, is provided by whoever instantiates the DOMGenerator.  The intent is that
 * a.) we don't get cache hits for two different DOM objects that happen to have equivalent content.
 * ( source of performance problem in past ), and
 * b.) two doc fragments are considered equivalent input iff they are the same fragment.
 *
 * Unfortunately at the moment (b) happens more by coincidence than by design.  That is just so
 * happens that that with current impl and usage of DOMGenerator we get this result.  It would
 * be better if there was code that made this happen explicitly.
 */
public final class DOMGenerator extends ProcessorImpl {

    /**
     * Abstraction that lets use either DOM4J or W3C document as source.
     */
    private static abstract class SourceFactory {
        private final String systemID;

        protected SourceFactory(final String sid) {
            systemID = sid;
        }

        abstract Source makeDOMSource();

        final Source makeSource() {
            final Source ret = makeDOMSource();
            ret.setSystemId(systemID);
            return ret;
        }
    }

    private static class DOM4JSourceFactory extends SourceFactory {

        private final org.dom4j.Document doc;

        DOM4JSourceFactory(final org.dom4j.Document d, final String sid, boolean clone) {
            super(sid);
            doc = clone ? (org.dom4j.Document) d.clone() : d;
        }

        Source makeDOMSource() {
            return Dom4jUtils.getDocumentSource(doc);
        }
    }

    private static class W3CSourceFactory extends SourceFactory {
        private final org.w3c.dom.Document doc;

        W3CSourceFactory(final org.w3c.dom.Document d, final String sid) {
            super(sid);
            if (d == null) throw new OXFException("Document d == null");
            doc = (org.w3c.dom.Document) d.cloneNode(true);
        }

        Source makeDOMSource() {
            return new DOMSource(doc);
        }
    }

    private static class TinyTreeSourceFactory extends SourceFactory {
        private final DocumentInfo documentInfo;

        TinyTreeSourceFactory(final DocumentInfo documentInfo, final String systemId) {
            super(systemId);
            if (documentInfo == null) throw new OXFException("Document documentInfo == null");
            this.documentInfo = documentInfo;
        }

        Source makeDOMSource() {
            return documentInfo;
        }
    }

    private static class DocKey extends SimpleOutputCacheKey {

        public DocKey(final String id) {
            super(DOMGenerator.class, OUTPUT_DATA, id);
        }

        public String toString() {
            return "DocKey [ " + super.toString() + " ]";
        }

        public boolean equals(final Object rhsObj) {
            return rhsObj == this;
        }
    }

    public final static Long ZeroValidity = (long) 0;
    public final static String DefaultContext = "oxf:/";

    private static org.dom4j.Document makeCopyDoc(final org.dom4j.Element e) {
        final org.dom4j.Element cpy = e.createCopy();
        final NonLazyUserDataDocument ret = new NonLazyUserDataDocument();
        ret.setRootElement(cpy);
        return ret;
    }

    private static DocumentInfo makeCopyDoc(final NodeInfo nodeInfo) {
        if (nodeInfo instanceof DocumentInfo)
            return (DocumentInfo) nodeInfo;
        else
            return TransformerUtils.readTinyTree(nodeInfo, false);
    }

    private final SourceFactory sourceFactory;
    private final DocKey key;
    private final Object validity;

    private DOMGenerator(final String id, final Object v, final SourceFactory srcFctry) {
        key = new DocKey(id);
        validity = v;
        sourceFactory = srcFctry;
        final ProcessorInputOutputInfo pInOutInf = new ProcessorInputOutputInfo(OUTPUT_DATA);
        addOutputInfo(pInOutInf);
    }

    /**
     * @param id  Is really just for debugging purposes.  Should give some clue as to who is
     *            instantiating this DOMGenerator.
     * @param sid Base url used to resolve any relative URLs that may be contained within the
     *            document.
     */
    public DOMGenerator(final org.w3c.dom.Document d, final String id, Object v, final String sid) {
        this(id, v, new W3CSourceFactory(d, sid));
    }

    /**
     * @param id  Is really just for debugging purposes.  Should give some clue as to who is
     *            instantiating this DOMGenerator.
     * @param sid Base url used to resolve any relative URLs that may be contained within the
     *            document.
     */

    public DOMGenerator(final org.dom4j.Element e, final String id, Object v, final String sid) {
        this(id, v, new DOM4JSourceFactory(makeCopyDoc(e), sid, false));
    }

    /**
     * @param id  Is really just for debugging purposes.  Should give some clue as to who is
     *            instantiating this DOMGenerator.
     * @param sid Base url used to resolve any relative URLs that may be contained within the
     *            document.
     */
    public DOMGenerator(final org.dom4j.Document d, final String id, Object v, final String sid) {
        this(id, v, new DOM4JSourceFactory(d, sid, true));
    }

    public DOMGenerator(final NodeInfo nodeInfo, final String id, Object v, final String sid) {
        this(id, v, new TinyTreeSourceFactory(makeCopyDoc(nodeInfo), sid));
    }

    public ProcessorOutput createOutput(final String nm) {

        final Class clazz = getClass();
        final ProcessorOutput ret = new ProcessorImpl.CacheableTransformerOutputImpl(clazz, nm) {
            public void readImpl(final PipelineContext pipelineContext, final ContentHandler contentHandler) {
                try {
                    final Transformer identityTransformer = TransformerUtils.getIdentityTransformer();
                    // NOTE: source cannot be an instance var.  Reason is that the XMLReader it
                    // will create is stateful.  ( Meaning that if it used by multiple threads
                    // confusion will ensue.
                    final Source source = sourceFactory.makeSource();
                    final SAXResult result = new SAXResult(contentHandler);
                    identityTransformer.transform(source, result);
                } catch (final TransformerException e) {
                    throw new OXFException(e);
                }
            }

            public OutputCacheKey getKeyImpl(final PipelineContext pipelineContext) {
                return key;
            }

            public Object getValidityImpl(final PipelineContext pipelineContext) {
                return validity;
            }
        };

        addOutput(nm, ret);
        return ret;
    }
}

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

import org.orbeon.dom.Document;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.cache.SimpleOutputCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.processor.ProcessorSupport;
import org.orbeon.oxf.processor.impl.CacheableTransformerOutputImpl;
import org.orbeon.oxf.util.XPath;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLReceiver;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.NodeInfo;

import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;

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
     * Abstraction that lets use either Orbeon DOM or W3C document as source.
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

    private static class OrbeonDomSourceFactory extends SourceFactory {

        private final org.orbeon.dom.Document doc;

        OrbeonDomSourceFactory(final org.orbeon.dom.Document d, final String sid, boolean clone) {
            super(sid);
            doc = clone ? (org.orbeon.dom.Document) d.deepCopy() : d;
        }

        Source makeDOMSource() {
            return ProcessorSupport.getDocumentSource(doc);
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

        @Override
        public String toString() {
            return "DocKey [ " + super.toString() + " ]";
        }

        @Override
        public boolean equals(final Object other) {
            // ???
            return other == this;
        }
    }

    public final static Long ZeroValidity = (long) 0;
    public final static String DefaultContext = "oxf:/";

    private static org.orbeon.dom.Document makeCopyDoc(final org.orbeon.dom.Element e) {
        return Document.apply(e.createCopy());
    }

    private static DocumentInfo makeCopyDoc(final NodeInfo nodeInfo) {
        if (nodeInfo instanceof DocumentInfo)
            return (DocumentInfo) nodeInfo;
        else
            return TransformerUtils.readTinyTree(XPath.GlobalConfiguration(), nodeInfo, false);
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

    public DOMGenerator(final org.orbeon.dom.Element e, final String id, Object v, final String sid) {
        this(id, v, new OrbeonDomSourceFactory(makeCopyDoc(e), sid, false));
    }

    /**
     * @param id  Is really just for debugging purposes.  Should give some clue as to who is
     *            instantiating this DOMGenerator.
     * @param sid Base url used to resolve any relative URLs that may be contained within the
     *            document.
     */
    public DOMGenerator(final org.orbeon.dom.Document d, final String id, Object v, final String sid) {
        this(id, v, new OrbeonDomSourceFactory(d, sid, true));
    }

    public DOMGenerator(final NodeInfo nodeInfo, final String id, Object v, final String sid) {
        this(id, v, new TinyTreeSourceFactory(makeCopyDoc(nodeInfo), sid));
    }

    @Override
    public ProcessorOutput createOutput(final String nm) {

        final ProcessorOutput ret = new CacheableTransformerOutputImpl(DOMGenerator.this, nm) {
            public void readImpl(final PipelineContext pipelineContext, final XMLReceiver xmlReceiver) {
                // NOTE: source cannot be an instance var.  Reason is that the XMLReader it
                // will create is stateful.  ( Meaning that if it used by multiple threads
                // confusion will ensue.
                TransformerUtils.sourceToSAX(sourceFactory.makeSource(), xmlReceiver);
            }

            @Override
            public OutputCacheKey getKeyImpl(PipelineContext pipelineContext) {
                return key;
            }

            @Override
            public Object getValidityImpl(final PipelineContext pipelineContext) {
                return validity;
            }
        };

        addOutput(nm, ret);
        return ret;
    }
}

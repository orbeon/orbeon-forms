/**
 *  Copyright (C) 2004 - 2005 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor.generator;

import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import org.dom4j.DocumentHelper;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.cache.SimpleOutputCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.xml.sax.ContentHandler;

/**
 * DOM Document to sax event processor.
 * 
 * A note wrt caching.  Each instance of DOMGenerator creates a unique key for caching.  The 
 * validity, however, is provided by whoever instantiates the DOMGenerator.  The intent is that
 * a.) we don't get cache hits for two different DOM objs that happen to have equivalent content.
 *     ( source of performance problem in past ), and 
 * b.) two doc fragments are considered equivalent input iff they are the same fragment.
 * 
 * Unfortunately at the moment (b) happens more by coincedence than by design.  That is just so
 * happens that that with current impl and usage of DOMGenerator we get this result.  It would
 * be better if there was code that made this happen explicitly.  
 */
public final class DOMGenerator extends ProcessorImpl {
    
    /**
     * Abstraction that lets use either DOM4J or W3C document as source.  
     */
    private static abstract class SourceFactory {
        private final String systemID;

        protected SourceFactory( final String sid ) {
            systemID = sid;
        }
        
        abstract Source makeDOMSource();

        final Source makeSource() {
            final Source ret = makeDOMSource();
            ret.setSystemId( systemID );
            return ret;
        }
    };
    
    private static class DOM4JSourceFactory extends SourceFactory {
        
        private final org.dom4j.Document doc;
        
        DOM4JSourceFactory( final org.dom4j.Document d, final String sid, boolean clone ) {
            super( sid );
            doc = clone ? ( org.dom4j.Document )d.clone() : d;
        }
        
        Source makeDOMSource() {
            final Source ret = Dom4jUtils.getDocumentSource( doc );
            return ret;
        }
    }
    
    private static class W3CSourceFactory extends SourceFactory {
        private final org.w3c.dom.Document doc;

        W3CSourceFactory( final org.w3c.dom.Document d, final String sid ) {
            super( sid );
            if ( d == null ) throw new OXFException( "Document d == null" );
            doc = ( org.w3c.dom.Document )d.cloneNode( true );
        }
        Source makeDOMSource() {
            final Source ret = new DOMSource( doc );
            return ret;
        }
    }
    
    private static class DocKey extends SimpleOutputCacheKey {
        
        public DocKey( final String id ) {
            super( DOMGenerator.class, OUTPUT_DATA, id );
        }

        public String toString() {
            return "DocKey [ " +  super.toString() + " ]"; 
        }
        public boolean equals( final Object rhsObj ) {
            return rhsObj == this;
        }
    }
    
    public final static Long ZeroValidity = new Long( 0 );
    public final static String DefaultContext = "oxf:/";

    private static org.dom4j.Document makeCopyDoc( final org.dom4j.Element e ) {
        final org.dom4j.Element cpy = ( org.dom4j.Element )e.createCopy();
        final org.dom4j.Document ret = DocumentHelper.createDocument( cpy );
        return ret;
    }
    

    private final SourceFactory sourceFactory;
    private final DocKey key;
    private final Object validity;

    private DOMGenerator( final String id, final Object v, final SourceFactory srcFctry ) {
            key = new DocKey( id ); 
            validity = v;
            sourceFactory = srcFctry;
            final ProcessorInputOutputInfo pInOutInf = new ProcessorInputOutputInfo( OUTPUT_DATA );
            addOutputInfo( pInOutInf );
    }

    /**
     * @param id Is really just for debugging purposes.  Should give some clue as to who is 
     *         instantiating this DOMGenerator.
     * @param sid Base url used to resolve any relative urls that may be contained within the 
     *        document.
     */
    public DOMGenerator
    ( final org.w3c.dom.Document d, final String id, Object v, final String sid ) {
        this( id, v, new W3CSourceFactory( d, sid )  );
    }

    /**
     * @param id Is really just for debugging purposes.  Should give some clue as to who is 
     *         instantiating this DOMGenerator.
     * @param sid Base url used to resolve any relative urls that may be contained within the 
     *        document.
     */

    public DOMGenerator( final org.dom4j.Element e, final String id, Object v, final String sid ) {
        this( id, v, new DOM4JSourceFactory( makeCopyDoc( e ), sid, false )  );
    }

    /**
     * @param id Is really just for debugging purposes.  Should give some clue as to who is 
     *         instantiating this DOMGenerator.
     * @param sid Base url used to resolve any relative urls that may be contained within the 
     *        document.
     */
    public DOMGenerator
    ( final org.dom4j.Document d, final String id, Object v, final String sid ) {
        this( id, v, new DOM4JSourceFactory( d, sid, true )  );
    }

    public ProcessorOutput createOutput( final String nm ) {

        final Class cls = getClass();
        final ProcessorOutput ret = new ProcessorImpl.CacheableTransformerOutputImpl( cls, nm ) {
            public void readImpl( final PipelineContext ctxt, final ContentHandler cntntHndlr ) {
                try {
                    final Transformer idnt = TransformerUtils.getIdentityTransformer();
                    // NOTE: source cannot be an instance var.  Reason is that the XMLReader it
                    // will create is stateful.  ( Meaning that if it used by multiple threads 
                    // confusion will ensue.
                    final Source source = sourceFactory.makeSource();
                    final SAXResult sr = new SAXResult( cntntHndlr  );
                    idnt.transform( source, sr );
                } catch ( final TransformerException e ) {
                    throw new OXFException(e);
                } 
            }

            public OutputCacheKey getKeyImpl( final PipelineContext ctxt ) {
                return key;
            }

            public Object getValidityImpl( final PipelineContext ctxt ) {
                return validity;
            }
        };

        addOutput( nm, ret );
        return ret;
    }
}

/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms;

import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.PoolableObjectFactory;
import org.apache.commons.pool.impl.SoftReferenceObjectPool;
import org.orbeon.oxf.cache.Cache;
import org.orbeon.oxf.cache.InternalCacheKey;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.xforms.event.events.XXFormsInitializeStateEvent;
import org.orbeon.oxf.xforms.processor.XFormsServer;

/**
 * This cache stores mappings XFormsState -> XFormsContainingDocument into a global cache.
 */
public class XFormsServerDocumentCache {

    private static final String CONTAINING_DOCUMENT_KEY_TYPE = "oxf.xforms.cache.context.containing-document";

    private static XFormsServerDocumentCache instance = null;

    public static XFormsServerDocumentCache instance() {
        if (instance == null) {
            synchronized (XFormsServerDocumentCache.class){
                if (instance == null) {
                    instance = new XFormsServerDocumentCache();
                }
            }
        }
        return instance;
    }

    public synchronized void add(PipelineContext pipelineContext, XFormsServer.XFormsState xformsState, XFormsContainingDocument containingDocument) {

        final Long validity = new Long(0);
        final Cache cache = ObjectCache.instance();
        final String cacheKeyString = xformsState.toString();
        //logger.info("xxx KEY used when returning: " + cacheKeyString);

        final InternalCacheKey cacheKey = new InternalCacheKey(CONTAINING_DOCUMENT_KEY_TYPE, cacheKeyString);
        ObjectPool destinationPool = (ObjectPool) cache.findValid(pipelineContext, cacheKey, validity);
        if (destinationPool == null) {
            // The pool is not in cache
            destinationPool = createXFormsContainingDocumentPool(xformsState);
            cache.add(pipelineContext, cacheKey, validity, destinationPool);
            XFormsServer.logger.debug("XForms - containing document cache (cacheContainingDocument): did not find document pool in cache; creating new pool and returning document to it.");
        } else {
            // Pool is already in cache
            XFormsServer.logger.debug("XForms - containing document cache (cacheContainingDocument): found containing document pool in cache. Returning document to it.");
        }

        // Return object to destination pool
        try {
            destinationPool.returnObject(containingDocument);
        } catch (Exception e) {
            throw new OXFException(e);
        }

        // Remove object from source pool
        final ObjectPool sourceObjectPool = containingDocument.getSourceObjectPool();
        if (sourceObjectPool != null && sourceObjectPool != destinationPool) {
            try {
                XFormsServer.logger.debug("XForms - containing document cache: discarding document from source pool.");
                sourceObjectPool.invalidateObject(containingDocument);
            } catch (Exception e) {
                throw new OXFException(e);
            }
        }
        containingDocument.setSourceObjectPool(null);
    }

    /**
     * Find an XFormsContainingDocument from the cache. If not found, create it.
     *
     * @param pipelineContext       current PipelineContext
     * @param xformsState           state used to search cache
     * @return                      XFormsContainingDocument
     */
    public synchronized XFormsContainingDocument find(PipelineContext pipelineContext, XFormsServer.XFormsState xformsState) {

        final Long validity = new Long(0);
        final Cache cache = ObjectCache.instance();
        final String cacheKeyString = xformsState.toString();
        //logger.info("xxx KEY used when returning: " + cacheKeyString);

        // Try to find pool in cache, create it if not found
        final InternalCacheKey cacheKey = new InternalCacheKey(CONTAINING_DOCUMENT_KEY_TYPE, cacheKeyString);

        final XFormsContainingDocument containingDocument;
        final ObjectPool pool = (ObjectPool) cache.findValid(pipelineContext, cacheKey, validity);
        if (pool == null) {
            // We don't add the pool to the cache here
            XFormsServer.logger.debug("XForms - containing document cache (getContainingDocument): did not find document pool in cache.");
            containingDocument = XFormsServer.createXFormsContainingDocument(pipelineContext, xformsState, null);
        } else {
            // Get object from pool
            XFormsServer.logger.debug("XForms - containing document cache (getContainingDocument): found containing document pool in cache; getting document from pool.");
            try {
                containingDocument = (XFormsContainingDocument) pool.borrowObject();
            } catch (Exception e) {
                throw new OXFException(e);
            }
            // Initialize state
//            containingDocument.dispatchExternalEvent(pipelineContext, new XXFormsInitializeStateEvent(containingDocument, null, null, true));
        }
        containingDocument.setSourceObjectPool(pool);

        // Return document
        return containingDocument;
    }

    private static ObjectPool createXFormsContainingDocumentPool(XFormsServer.XFormsState xformsState) {
        try {
            final SoftReferenceObjectPool pool = new SoftReferenceObjectPool();
            pool.setFactory(new CachedPoolableObjetFactory(pool, xformsState));

            return pool;
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private static class CachedPoolableObjetFactory implements PoolableObjectFactory {
        private final ObjectPool pool;
        private XFormsServer.XFormsState xformsState;

        public CachedPoolableObjetFactory(ObjectPool pool, XFormsServer.XFormsState xformsState) {
            this.pool = pool;
            this.xformsState = xformsState;
        }

        public void activateObject(Object o) throws Exception {
        }

        public void destroyObject(Object o) throws Exception {
            if (o instanceof XFormsContainingDocument) {
                // Don't actually "damage" the object, as it may be put into another pool when
                // invalidated from the current pool
            } else
                throw new OXFException(o.toString() + " is not an XFormsContainingDocument");
        }

        public Object makeObject() throws Exception {
            // NOTE: We have trouble passing the PipelineContext from borrowObject() up to here, so we try to get it
            // from the StaticExternalContext. We need an ExternalContext in it in case the XForms initialization
            // requires things like resolving URLs to load schemas, etc.

            final PipelineContext pipelineContext = StaticExternalContext.getStaticContext().getPipelineContext();
            final XFormsContainingDocument result = XFormsServer.createXFormsContainingDocument(pipelineContext, xformsState, null);
            result.setSourceObjectPool(pool);
            return result;
        }

        public void passivateObject(Object o) throws Exception {
        }

        public boolean validateObject(Object o) {
            // We don't need the below anymore, as we are returning the document to the right pool
//            final XFormsContainingDocument containingDocument = (XFormsContainingDocument) o;
//            final boolean valid = dynamicStateString.equals(containingDocument.getDynamicStateString());
//            if (logger.isDebugEnabled())
//                logger.debug("XForms - containing document cache (validateObject): " + valid);
//            return valid;
            return true;
        }
    }
}

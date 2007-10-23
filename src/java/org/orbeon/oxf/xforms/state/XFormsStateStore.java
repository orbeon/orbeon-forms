/**
 *  Copyright (C) 2005-2007 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.state;

import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.Datasource;
import org.orbeon.oxf.processor.xmldb.XMLDBProcessor;
import org.orbeon.oxf.xforms.XFormsModelSubmission;
import org.orbeon.oxf.xforms.XFormsSubmissionUtils;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.orbeon.saxon.om.FastStringBuffer;
import org.xml.sax.ContentHandler;

import javax.xml.transform.sax.TransformerHandler;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

/**
 * Base class for XFormsState stores.
 */
public abstract class XFormsStateStore {

    private static final boolean TEMP_PERF_TEST = false;
    private static final int TEMP_PERF_ITERATIONS = 100;
    private static final boolean TEMP_USE_XMLDB = true;

    // For now the driver is not configurable, but everything else (URI, username, password, collection) is configurable in properties
    private static final String EXIST_XMLDB_DRIVER = "org.exist.xmldb.DatabaseImpl";

    private boolean isPersistent;
    private int currentStoreSize = 0;

    private Map keyToEntryMap = new HashMap();
    private LinkedList linkedList = new LinkedList();

    protected XFormsStateStore(boolean isPersistent) {
        this.isPersistent = isPersistent;
    }

    protected abstract int getMaxSize();

    protected abstract String getStoreDebugName();

    public synchronized void add(String pageGenerationId, String oldRequestId, String requestId, XFormsState xformsState) {
        // Add static state and move it to the front
        addOne(pageGenerationId, xformsState.getStaticState(), false);

        // Remove old dynamic state if present as we keep only one entry per page generation
        // NOTE: We try to keep the initial dynamic state entry in the store however, because the client is still likely to request it
        if (oldRequestId != null) {
            final StoreEntry oldStoredEntry = (StoreEntry) keyToEntryMap.get(oldRequestId);
            if (oldStoredEntry != null && !oldStoredEntry.isInitialEntry)
                removeStoreEntry(oldStoredEntry);
        }

        // Add new dynamic state
        final boolean isInitialEntry = oldRequestId == null; // tell whether this is an initial dynamic state entry which has preferential treatment
        addOne(requestId, xformsState.getDynamicState(), isInitialEntry);

        if (XFormsServer.logger.isDebugEnabled()) {
            debug("store size after adding: " + currentStoreSize + " bytes.");
            debugDumpKeys();
        }
    }

    private void addOne(String key, String value, boolean isInitialEntry) {

        // Remove existing entry if present
        {
            final StoreEntry existingStoreEntry = (StoreEntry) keyToEntryMap.get(key);
            if (existingStoreEntry != null)
                removeStoreEntry(existingStoreEntry);
        }

        // Make room if needed
        final int size = value.length() * 2;
        final int storeSizeBeforeExpire = currentStoreSize;
        int expiredCount = 0;
        while (currentStoreSize != 0 && (currentStoreSize + size) > getMaxSize()) {
            expireOne();
            expiredCount++;
        }

        if (storeSizeBeforeExpire != currentStoreSize && XFormsServer.logger.isDebugEnabled())
           debug("expired " + expiredCount + " entries (" + (storeSizeBeforeExpire - currentStoreSize) + " bytes).");

        // Add new element to store
        final StoreEntry newStoreEntry = new StoreEntry(key, value, isInitialEntry);

        linkedList.addFirst(newStoreEntry);
        keyToEntryMap.put(key, newStoreEntry);

        if (XFormsServer.logger.isDebugEnabled())
            debug("added entry of " + size + " bytes.");

        // Update store size
        currentStoreSize += size;
    }

    public synchronized XFormsState find(String pageGenerationId, String requestId) {
        if (XFormsServer.logger.isDebugEnabled()) {
            debug("store size before finding: " + currentStoreSize + " bytes.");
            debugDumpKeys();
        }

        final String staticState = findOne(pageGenerationId);
        if (staticState == null)
            return null;
        final String dynamicState = findOne(requestId);
        if (dynamicState == null)
            return null;
        return new XFormsState(staticState, dynamicState);
    }

    private String findOne(String key) {
        final StoreEntry existingStoreEntry = (StoreEntry) keyToEntryMap.get(key);
        if (existingStoreEntry != null) {
            // Move to the front (is this useful in our use case?)
            if (linkedList.getFirst() != existingStoreEntry) {
                linkedList.remove(existingStoreEntry);
                linkedList.addFirst(existingStoreEntry);
            }
            debug("found and refreshed entry for key: " + key);
            return existingStoreEntry.value;
        } else if (isPersistent) {
            // Try the persistent cache

            final StoreEntry persistedStoreEntry = findPersistedEntry(key);
            if (persistedStoreEntry != null) {
                // Add the key to the list in memory
                addOne(persistedStoreEntry.key, persistedStoreEntry.value, persistedStoreEntry.isInitialEntry);
                debug("migrated persisted entry for key: " + key);
                return persistedStoreEntry.value;
            }
        }

        // Not found
        debug("did not find entry for key: " + key);
        return null;
    }

    private void removeStoreEntry(StoreEntry existingStoreEntry) {

        final int stateSize = existingStoreEntry.value.length() * 2;

        linkedList.remove(existingStoreEntry);
        keyToEntryMap.remove(existingStoreEntry.key);

        // Update store size
        currentStoreSize -= stateSize;

        if (XFormsServer.logger.isDebugEnabled())
            debug("removed entry of " + stateSize + " bytes.");
    }

    private void expireOne() {
        if (linkedList.size() > 0) {
            final StoreEntry lastStoreEntry = (StoreEntry) linkedList.getLast();
            removeStoreEntry(lastStoreEntry);

            if (isPersistent) {
                // Persist state
                persistEntry(lastStoreEntry);
            }
        }
    }

    private void persistEntry(StoreEntry storeEntry) {

        if (XFormsServer.logger.isDebugEnabled()) {
            debug("persisting entry for key: " + storeEntry.key + " (" + (storeEntry.value.length() * 2) + " bytes).");
        }

        if (TEMP_PERF_TEST) {

            // Do the operation TEMP_PERF_ITERATIONS times to test performance
            final long startTime = System.currentTimeMillis();
            for (int i = 0; i < TEMP_PERF_ITERATIONS; i ++) {
                if (TEMP_USE_XMLDB) {
                    persistEntryExistXMLDB(storeEntry);
                } else {
                    persistEntryExistHTTP(storeEntry);
                }
            }
            debug("average write persistence time: " + ((System.currentTimeMillis() - startTime) / TEMP_PERF_ITERATIONS) + " ms." );

        } else {
            if (TEMP_USE_XMLDB) {
                persistEntryExistXMLDB(storeEntry);
            } else {
                persistEntryExistHTTP(storeEntry);
            }
        }
    }

    private void persistEntryExistXMLDB(StoreEntry storeEntry) {

        final PipelineContext pipelineContext;
        {
            final StaticExternalContext.StaticContext staticContext = StaticExternalContext.getStaticContext();
            pipelineContext = staticContext.getPipelineContext();
        }

        final String messageBody = encodeMessageBody(pipelineContext, storeEntry);
        try {
            new XMLDBAccessor().storeResource(pipelineContext, new Datasource(EXIST_XMLDB_DRIVER,
                    XFormsProperties.getStoreURI(), XFormsProperties.getStoreUsername(), XFormsProperties.getStorePassword()), XFormsProperties.getStoreCollection(),
                    true, storeEntry.key, messageBody);
        } catch (Exception e) {
            throw new OXFException("Unable to store entry in persistent state store for key: " + storeEntry.key, e);
        }
    }

    private void persistEntryExistHTTP(StoreEntry storeEntry) {

        final ExternalContext externalContext;
        final PipelineContext pipelineContext;
        {
            final StaticExternalContext.StaticContext staticContext = StaticExternalContext.getStaticContext();
            externalContext = staticContext.getExternalContext();
            pipelineContext = staticContext.getPipelineContext();
        }

        final String url = "/exist/rest" + XFormsProperties.getStoreCollection() + storeEntry.key;
        final String resolvedURL = externalContext.getResponse().rewriteResourceURL(url, true);

        final byte[] messageBody;
        try {
            messageBody = encodeMessageBody(pipelineContext, storeEntry).getBytes("utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new OXFException(e);// won't happen
        }

        // Put document into external storage
        XFormsModelSubmission.ConnectionResult result = XFormsSubmissionUtils.doRegular(externalContext, "put", resolvedURL, null, null, "application/xml", messageBody, null);
        if (result.resultCode < 200 || result.resultCode >= 300)
            throw new OXFException("Got non-successful return code from store persistence layer: " + result.resultCode);
    }

    private String encodeMessageBody(PipelineContext pipelineContext, StoreEntry storeEntry) {

        final FastStringBuffer sb = new FastStringBuffer("<entry><key>");
        sb.append(storeEntry.key);
        sb.append("</key><value>");

        // Make sure value is encrypted as it will be externalized
        final String encryptedValue;
        if (storeEntry.value.startsWith("X3") || storeEntry.value.startsWith("X4")) {
            // Data is currently not encrypted, so encrypt it
            final byte[] decodedValue = XFormsUtils.decodeBytes(pipelineContext, storeEntry.value, XFormsProperties.getXFormsPassword());
            encryptedValue = XFormsUtils.encodeBytes(pipelineContext, decodedValue, XFormsProperties.getXFormsPassword());
        } else {
            // Data is already encrypted
            encryptedValue = storeEntry.value;
        }

        sb.append(encryptedValue);
        sb.append("</value><is-initial-entry>");
        sb.append(Boolean.toString(storeEntry.isInitialEntry));
        sb.append("</is-initial-entry></entry>");

        return sb.toString();
    }

    private StoreEntry findPersistedEntry(String key) {

        if (XFormsServer.logger.isDebugEnabled()) {
            debug("finding persisting entry for key: " + key + ".");
        }

        StoreEntry result = null;
        if (TEMP_PERF_TEST) {

            // Do the operation TEMP_PERF_ITERATIONS times to test performance
            final long startTime = System.currentTimeMillis();
            for (int i = 0; i < TEMP_PERF_ITERATIONS; i ++) {
                if (TEMP_USE_XMLDB) {
                    result = findPersistedEntryExistXMLDB(key);
                } else {
                    result = findPersistedEntryExistHTTP(key);
                }
                if (result == null)
                    return null;
            }
            debug("average read persistence time: " + ((System.currentTimeMillis() - startTime) / TEMP_PERF_ITERATIONS) + " ms." );

        } else {
            if (TEMP_USE_XMLDB) {
                result = findPersistedEntryExistXMLDB(key);
            } else {
                result = findPersistedEntryExistHTTP(key);
            }
        }

        return result;
    }

    private StoreEntry findPersistedEntryExistXMLDB(String key) {

        final PipelineContext pipelineContext;
        {
            final StaticExternalContext.StaticContext staticContext = StaticExternalContext.getStaticContext();
            pipelineContext = staticContext.getPipelineContext();
        }

        final LocationDocumentResult documentResult = new LocationDocumentResult();
        final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
        identity.setResult(documentResult);

        try {
            new XMLDBAccessor().getResource(pipelineContext, new Datasource(EXIST_XMLDB_DRIVER,
                    XFormsProperties.getStoreURI(), XFormsProperties.getStoreUsername(), XFormsProperties.getStorePassword()),
                    XFormsProperties.getStoreCollection(), true, key, identity);
        } catch (Exception e) {
            throw new OXFException("Unable to find entry in persistent state store for key: " + key, e);
        }

        final Document document = documentResult.getDocument();
        return getStoreEntryFromDocument(key, document);
    }

    private StoreEntry findPersistedEntryExistHTTP(String key) {

        final ExternalContext externalContext;
        {
            final StaticExternalContext.StaticContext staticContext = StaticExternalContext.getStaticContext();
            externalContext = staticContext.getExternalContext();
        }

        final String url = "/exist/rest" + XFormsProperties.getStoreCollection() + key;
        final String resolvedURL = externalContext.getResponse().rewriteResourceURL(url, true);

        XFormsModelSubmission.ConnectionResult result = XFormsSubmissionUtils.doRegular(externalContext, "get", resolvedURL, null, null, null, null, null);

        if (result.resultCode == 404)
            return null;

        if (result.resultCode < 200 || result.resultCode >= 300)
            throw new OXFException("Got non-successful return code from store persistence layer: " + result.resultCode);

        final Document document = TransformerUtils.readDom4j(result.getResultInputStream(), result.resourceURI);
        return getStoreEntryFromDocument(key, document);
    }

    private StoreEntry getStoreEntryFromDocument(String key, Document document) {
        final String value = document.getRootElement().element("value").getStringValue();
        final boolean isInitialEntry = new Boolean(document.getRootElement().element("is-initial-entry").getStringValue()).booleanValue();

        return new StoreEntry(key, value, isInitialEntry);
    }

    protected void debug(String message) {
        XFormsServer.logger.debug("XForms - " + getStoreDebugName() + " store: " + message);
    }

    protected void debugDumpKeys() {
        int index = 1;
        for (final Iterator i = linkedList.iterator(); i.hasNext(); index++) {
            final StoreEntry currentEntry = (StoreEntry) i.next();
            debug("key in store: " + index + ": " + currentEntry.key);
        }
    }

    private static class StoreEntry {
        public String key;
        public String value;
        public boolean isInitialEntry;

        public StoreEntry(String key, String value, boolean isInitialEntry) {
            this.key = key;
            this.value = value;
            this.isInitialEntry = isInitialEntry;
        }
    }

    private static class XMLDBAccessor extends XMLDBProcessor {
//        public void update(PipelineContext pipelineContext, Datasource datasource, String collectionName, boolean createCollection, String resourceId, String query) {
//            super.update(pipelineContext, datasource, collectionName, createCollection, resourceId, query);
//        }

//        public void query(PipelineContext pipelineContext, Datasource datasource, String collectionName, boolean createCollection, String resourceId, String query, Map namespaceContext, ContentHandler contentHandler) {
//            super.query(pipelineContext, datasource, collectionName, createCollection, resourceId, query, namespaceContext, contentHandler);
//        }

        protected void getResource(PipelineContext pipelineContext, Datasource datasource, String collectionName, boolean createCollection, String resourceName, ContentHandler contentHandler) {
            super.getResource(pipelineContext, datasource, collectionName, createCollection, resourceName, contentHandler);
        }

        protected void storeResource(PipelineContext pipelineContext, Datasource datasource, String collectionName, boolean createCollection, String resourceName, String document) {
            super.storeResource(pipelineContext, datasource, collectionName, createCollection, resourceName, document);
        }
    }
}

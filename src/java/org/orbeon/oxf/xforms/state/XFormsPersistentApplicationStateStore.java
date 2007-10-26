/**
 *  Copyright (C) 2007 Orbeon, Inc.
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
import org.dom4j.io.DocumentResult;
import org.orbeon.oxf.cache.CacheLinkedList;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.Datasource;
import org.orbeon.oxf.processor.xmldb.XMLDBProcessor;
import org.orbeon.oxf.xforms.XFormsModelSubmission;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsSubmissionUtils;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.orbeon.saxon.om.FastStringBuffer;
import org.xml.sax.ContentHandler;

import javax.xml.transform.sax.TransformerHandler;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * This store keeps XFormsState instances into an application store and persists data going over a given size.
 *
 * This store leverages the underlying memory store.
 *
 * o When an entry from the memory store is expiring, it is migrated to the persistent store.
 * o When an entry is not found in the memory store, it is searched for in the persistent store.
 * o A session id is added when available.
 * o When a session expires, both memory and persistent entries are expired.
 * o Upon first use, all persistent entries with session information are expired.
 */
public class XFormsPersistentApplicationStateStore extends XFormsStateStore {

    private static final boolean TEMP_PERF_TEST = false;
    private static final int TEMP_PERF_ITERATIONS = 100;
    private static final boolean TEMP_USE_XMLDB = true;

    // Ideally we wouldn't want to force session creation, but it's hard to implement the more elaborate expiration
    // strategy without session. See https://wiki.objectweb.org/ops/Wiki.jsp?page=XFormsStateStoreImprovements
    private static final boolean FORCE_SESSION_CREATION = true;

    private static final String PERSISTENT_STATE_STORE_APPLICATION_KEY = "oxf.xforms.state.store.persistent-application-key";
    private static final String XFORMS_STATE_STORE_LISTENER_STATE_KEY = "oxf.xforms.state.store.has-session-listeners-key";

    // For now the driver is not configurable, but everything else (URI, username, password, collection) is configurable in properties
    private static final String EXIST_XMLDB_DRIVER = "org.exist.xmldb.DatabaseImpl";

    // Access to the XML:DB API
    private static final XMLDBAccessor xmlDBAccessor = new XMLDBAccessor();

    // Map session ids -> Map of keys
    private final Map sessionToKeysMap = new HashMap();

    /**
     * Create an instance of this state store.
     *
     * @param externalContext   external context
     * @return                  state store
     */
    public synchronized static XFormsStateStore instance(ExternalContext externalContext) {
        // Try to find existing store
        {
            final XFormsStateStore existingStateStore
                    = (XFormsStateStore) externalContext.getAttributesMap().get(PERSISTENT_STATE_STORE_APPLICATION_KEY);

            if (existingStateStore != null)
                return existingStateStore;
        }
        // Create new store
        {
            final XFormsPersistentApplicationStateStore newStateStore = new XFormsPersistentApplicationStateStore();

            // Expire remaining persistent entries with session information
            newStateStore.expireAllPersistentWithSession();
//            newStateStore.expireAllPersistent();

            // Keep new store in application scope
            externalContext.getAttributesMap().put(PERSISTENT_STATE_STORE_APPLICATION_KEY, newStateStore);
            return newStateStore;
        }
    }

    protected int getMaxSize() {
        return XFormsProperties.getApplicationStateStoreSize();
    }

    protected String getStoreDebugName() {
        return "global application";
    }

    // NOTE: The super() method doesn't do anything
    protected void persistEntry(StoreEntry storeEntry) {

        if (XFormsServer.logger.isDebugEnabled()) {
            debug("persisting entry for key: " + storeEntry.key + " (" + (storeEntry.value.length() * 2) + " bytes).");
        }

        final PipelineContext pipelineContext = getPipelineContext();
        final ExternalContext externalContext = getExternalContext();

        if (TEMP_PERF_TEST) {

            // Do the operation TEMP_PERF_ITERATIONS times to test performance
            final long startTime = System.currentTimeMillis();
            for (int i = 0; i < TEMP_PERF_ITERATIONS; i ++) {
                if (TEMP_USE_XMLDB) {
                    persistEntryExistXMLDB(pipelineContext, externalContext, storeEntry);
                } else {
                    persistEntryExistHTTP(pipelineContext, externalContext, storeEntry);
                }
            }
            debug("average write persistence time: " + ((System.currentTimeMillis() - startTime) / TEMP_PERF_ITERATIONS) + " ms." );

        } else {
            if (TEMP_USE_XMLDB) {
                persistEntryExistXMLDB(pipelineContext, externalContext, storeEntry);
            } else {
                persistEntryExistHTTP(pipelineContext, externalContext, storeEntry);
            }
        }
    }

    // NOTE: This calls super() and handles session information
    protected void addOne(String key, String value, boolean isInitialEntry) {

        // Actually add
        super.addOne(key, value, isInitialEntry);

        final ExternalContext.Session session = getExternalContext().getSession(FORCE_SESSION_CREATION);
        if (session != null) {
            // Remember that this key is associated with a session
            final String sessionId = session.getId();
            Map sessionMap = (Map) sessionToKeysMap.get(sessionId);
            if (sessionMap == null) {
                sessionMap = new HashMap();
                sessionToKeysMap.put(sessionId, sessionMap);
            }
            sessionMap.put(key, "");
        }
    }

    // This calls super() (without the session id) and handles session information
    // NOTE: This version is used when called from the session listener
    private void removeStoreEntry(String sessionId, CacheLinkedList.ListEntry existingListEntry) {

        // Actually remove
        super.removeStoreEntry(existingListEntry);

        // Remove the mapping of the session to this key, if any
        final Map sessionMap = (Map) sessionToKeysMap.get(sessionId);
        if (sessionMap != null) {
            sessionMap.remove(((StoreEntry) existingListEntry.element).key);
        }
    }

    // This calls super() and handles session information
    protected void removeStoreEntry(CacheLinkedList.ListEntry existingListEntry) {

        // Actually remove
        super.removeStoreEntry(existingListEntry);

        final ExternalContext.Session session = getExternalContext().getSession(false);
        if (session != null) {
            // Remove the mapping of the session to this key, if any
            final String sessionId = session.getId();
            final Map sessionMap = (Map) sessionToKeysMap.get(sessionId);
            if (sessionMap != null) {
                sessionMap.remove(((StoreEntry) existingListEntry.element).key);
            }
        }
    }

    // This calls super() and if that fails, tries the persistent store
    protected String findOne(String key) {

        final String memoryValue = super.findOne(key);
        if (memoryValue != null) {
            // Try memory first
            return memoryValue;
        } else {
            // Try the persistent cache
            final StoreEntry persistedStoreEntry = findPersistedEntry(key);
            if (persistedStoreEntry != null) {
                // Add the key to the list in memory
                addOne(persistedStoreEntry.key, persistedStoreEntry.value, persistedStoreEntry.isInitialEntry);
                debug("migrated persisted entry for key: " + key);
                return persistedStoreEntry.value;
            } else {
                // Not found
                debug("did not find entry in persistent cache for key: " + key);
                return null;
            }
        }
    }

    private void persistEntryExistXMLDB(PipelineContext pipelineContext, ExternalContext externalContext, StoreEntry storeEntry) {
        final String messageBody = encodeMessageBody(pipelineContext, externalContext, storeEntry);
        try {
            xmlDBAccessor.storeResource(pipelineContext, new Datasource(EXIST_XMLDB_DRIVER,
                    XFormsProperties.getStoreURI(), XFormsProperties.getStoreUsername(), XFormsProperties.getStorePassword()), XFormsProperties.getStoreCollection(),
                    true, storeEntry.key, messageBody);
        } catch (Exception e) {
            throw new OXFException("Unable to store entry in persistent state store for key: " + storeEntry.key, e);
        }
    }

    private void persistEntryExistHTTP(PipelineContext pipelineContext, ExternalContext externalContext, StoreEntry storeEntry) {
        final String url = "/exist/rest" + XFormsProperties.getStoreCollection() + storeEntry.key;
        final String resolvedURL = externalContext.getResponse().rewriteResourceURL(url, true);

        final byte[] messageBody;
        try {
            messageBody = encodeMessageBody(pipelineContext, externalContext, storeEntry).getBytes("utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new OXFException(e);// won't happen
        }

        // Put document into external storage
        XFormsModelSubmission.ConnectionResult result = XFormsSubmissionUtils.doRegular(externalContext, "put", resolvedURL, null, null, "application/xml", messageBody, null);
        if (result.resultCode < 200 || result.resultCode >= 300)
            throw new OXFException("Got non-successful return code from store persistence layer: " + result.resultCode);
    }

    /**
     * Remove all memory entries which have the given session id.
     *
     * @param sessionId     Servlet session id
     */
    private void expireMemoryBySession(String sessionId) {

        final Map sessionMap = (Map) sessionToKeysMap.get(sessionId);
        if (sessionMap != null) {
            final int storeSizeBeforeExpire = getCurrentStoreSize();
            int expiredCount = 0;
            for (Iterator i = sessionMap.keySet().iterator(); i.hasNext();) {
                final String currentKey = (String) i.next();
                final CacheLinkedList.ListEntry currenEntry = findEntry(currentKey);
                removeStoreEntry(sessionId, currenEntry);
                expiredCount++;
            }
            sessionToKeysMap.remove(sessionId);

            if (expiredCount > 0 && XFormsServer.logger.isDebugEnabled())
                debug("expired " + expiredCount + " entries for session " + sessionId + " (" + (storeSizeBeforeExpire - getCurrentStoreSize()) + " bytes).");
        }
    }

    /**
     * Remove all persisted entries which have the given session id.
     *
     * @param sessionId     Servlet session id
     */
    private void expirePersistentBySession(String sessionId) {

        final String query = "xquery version \"1.0\";" +
            "                 declare namespace xmldb=\"http://exist-db.org/xquery/xmldb\";" +
            "                 declare namespace util=\"http://exist-db.org/xquery/util\";" +
            "                 <result>" +
            "                   {" +
            "                     count(for $entry in /entry[session-id = '" + sessionId + "']" +
            "                           return (xmldb:remove(util:collection-name($entry), util:document-name($entry)), ''))" +
            "                   }" +
            "                 </result>";

        final Document result = executeQuery(query);
        final int count = Integer.parseInt(result.getDocument().getRootElement().getStringValue());
        debug("expired " + count + " persistent entries for session (" + sessionId + ").");
    }

    private void expireAllPersistentWithSession() {

        final String query = "xquery version \"1.0\";" +
            "                 declare namespace xmldb=\"http://exist-db.org/xquery/xmldb\";" +
            "                 declare namespace util=\"http://exist-db.org/xquery/util\";" +
            "                 <result>" +
            "                   {" +
            "                     count(for $entry in /entry[session-id]" +
            "                           return (xmldb:remove(util:collection-name($entry), util:document-name($entry)), ''))" +
            "                   }" +
            "                 </result>";

        final Document result = executeQuery(query);
        final int count = Integer.parseInt(result.getRootElement().getStringValue());
        debug("expired " + count + " persistent entries with session information.");
    }

    public void expireAllPersistent() {

        final String query = "xquery version \"1.0\";" +
            "                 declare namespace xmldb=\"http://exist-db.org/xquery/xmldb\";" +
            "                 declare namespace util=\"http://exist-db.org/xquery/util\";" +
            "                 <result>" +
            "                   {" +
            "                     count(for $entry in /entry" +
            "                           return (xmldb:remove(util:collection-name($entry), util:document-name($entry)), ''))" +
            "                   }" +
            "                 </result>";

        final Document result = executeQuery(query);
        final int count = Integer.parseInt(result.getRootElement().getStringValue());
        debug("expired " + count + " persistent entries.");
    }

    private Document executeQuery(String query) {

        final DocumentResult result = new DocumentResult();
        final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
        identity.setResult(result);

        xmlDBAccessor.query(getPipelineContext(), new Datasource(EXIST_XMLDB_DRIVER,
                XFormsProperties.getStoreURI(), XFormsProperties.getStoreUsername(), XFormsProperties.getStorePassword()), XFormsProperties.getStoreCollection(),
                true, null, query, null, identity);

        return result.getDocument();
    }

    private PipelineContext getPipelineContext() {
        // NOTE: We may not have a StaticContext when we are called from a session listener, but that should be ok
        // (PipelineContext is used further down the line to ensure that the db drive is registered, but it should
        // be.)
        final StaticExternalContext.StaticContext staticContext = StaticExternalContext.getStaticContext();
        return (staticContext != null) ? staticContext.getPipelineContext() : null;
    }

    private ExternalContext getExternalContext() {
            final StaticExternalContext.StaticContext staticContext = StaticExternalContext.getStaticContext();
            return (staticContext != null) ? staticContext.getExternalContext() : null;
    }

    private String encodeMessageBody(PipelineContext pipelineContext, ExternalContext externalContext, StoreEntry storeEntry) {

        final FastStringBuffer sb = new FastStringBuffer("<entry><key>");
        sb.append(storeEntry.key);
        sb.append("</key><value>");

        // Store the value and make sure it is encrypted as it will be externalized
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
        sb.append("</value>");

        // Store the session id if any
        final ExternalContext.Session session = externalContext.getSession(FORCE_SESSION_CREATION);
        if (session != null) {
            sb.append("<session-id>");
            sb.append(session.getId());
            sb.append("</session-id>");

            // Add session listener if needed (we want to register only one expiration listener per session)
            final Map sessionAttributes = session.getAttributesMap(ExternalContext.Session.APPLICATION_SCOPE);
            if (sessionAttributes.get(XFORMS_STATE_STORE_LISTENER_STATE_KEY) == null) {
                session.addListener(new ExternalContext.Session.SessionListener() {
                    public void sessionDestroyed() {
                        // Expire both memory and persistent entries
                        expireMemoryBySession(session.getId());
                        expirePersistentBySession(session.getId());
                    }
                });
                sessionAttributes.put(XFORMS_STATE_STORE_LISTENER_STATE_KEY, "");
            }
        }

        // Store the initial entry flag
        sb.append("<is-initial-entry>");
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

        final PipelineContext pipelineContext = getPipelineContext();

        final LocationDocumentResult documentResult = new LocationDocumentResult();
        final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
        identity.setResult(documentResult);

        try {
            xmlDBAccessor.getResource(pipelineContext, new Datasource(EXIST_XMLDB_DRIVER,
                    XFormsProperties.getStoreURI(), XFormsProperties.getStoreUsername(), XFormsProperties.getStorePassword()),
                    XFormsProperties.getStoreCollection(), true, key, identity);
        } catch (Exception e) {
            throw new OXFException("Unable to find entry in persistent state store for key: " + key, e);
        }

        final Document document = documentResult.getDocument();
        return getStoreEntryFromDocument(key, document);
    }

    private StoreEntry findPersistedEntryExistHTTP(String key) {

        final ExternalContext externalContext = getExternalContext();

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

    private static class XMLDBAccessor extends XMLDBProcessor {

        public void query(PipelineContext pipelineContext, Datasource datasource, String collectionName, boolean createCollection, String resourceId, String query, Map namespaceContext, ContentHandler contentHandler) {
            super.query(pipelineContext, datasource, collectionName, createCollection, resourceId, query, namespaceContext, contentHandler);
        }

        protected void getResource(PipelineContext pipelineContext, Datasource datasource, String collectionName, boolean createCollection, String resourceName, ContentHandler contentHandler) {
            super.getResource(pipelineContext, datasource, collectionName, createCollection, resourceName, contentHandler);
        }

        protected void storeResource(PipelineContext pipelineContext, Datasource datasource, String collectionName, boolean createCollection, String resourceName, String document) {
            super.storeResource(pipelineContext, datasource, collectionName, createCollection, resourceName, document);
        }
    }
}

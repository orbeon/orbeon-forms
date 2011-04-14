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
package org.orbeon.oxf.xforms.state;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.DocumentResult;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.Datasource;
import org.orbeon.oxf.processor.xmldb.XMLDBProcessor;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This store:
 *
 * o keeps key/value pairs into an memory store if possible and moves them to external storage when needed
 * o is shared among all users and sessions
 *
 * Here is how things work:
 *
 * o When an entry from the memory store is expiring, it is migrated to the persistent store.
 * o When an entry is not found in the memory store, it is searched for in the persistent store.
 * o One or more session ids are added to all key/value pairs.
 * o When a session expires, both memory and persistent entries are expired.
 * o Upon first use, all persistent entries with session information are expired.
 *
 * NOTE about session ids: a single entry can have multiple session ids in the case of static states. This means that
 * we must be careful:
 *
 * o Merge session information upon persisting an entry
 * o Remove entry only once last session id is gone in memory
 * o Remove entry only once last session id is gone in the persistent store
 *
 * The store is basically a smarter HashMap.
 *
 * They keys are Strings (they look like UUIDs, so they are small), and the values are Strings as well (they are
 * usually base-64 encoded content, and they can be very large).
 *
 * When you add keys and values, they are first added to memory into a LRU list (using the base class). When an item is
 * pushed out at the end of the list (because the size of the items in the list becomes larger than the allocated size),
 * it is migrated to external storage.
 *
 * When items are read with find(), they are searched first in memory, then in external storage, and then they are
 * migrated to the beginning of the LRU list.
 *
 * This is all and well, but if you don't do anything more, then the store would grow forever. So we implemented a
 * session-based expiration strategy as a first expiration strategy.
 *
 * The store is used as a consequence of a user interacting with an XForms page. The user is associated with a session.
 * So when calling add(), we pass the current session id.
 *
 * Static state keys can be reused among multiple users and sessions (dynamic state keys probably not at this time).
 * Either way, the entries in the cache are tagged with zero, one or more session ids.
 *
 * This means that is user USER1 in session SESSION1 is adding a key KEY1, and user USER1 in session SESSION2 is
 * adding KEY1 as well, KEY1 will be tagged with SESSION1 and SESSION2.
 *
 * Now when SESSION1 expires, what we want to do is remove all the mappings tagged with SESSION1, except those that
 * are still tagged with other sessions. For those, we just remove the SESSION1 tag. This would be a good thing to
 * test.
 */
public class EXistStateStore extends EXistStateStoreBase {

    private static final boolean TEMP_PERF_TEST = false;
    private static final int TEMP_PERF_ITERATIONS = 100;

    private static final String PERSISTENT_STATE_STORE_APPLICATION_KEY = "oxf.xforms.state.store.persistent-application-key";

    // The driver is not configurable, but everything else (URI, username, password, collection) is configurable in properties
    private static final String EXIST_XMLDB_DRIVER = "org.exist.xmldb.DatabaseImpl";

    // Access to the XML:DB API
    private static final XMLDBAccessor XMLDB_ACCESSOR = new XMLDBAccessor();

    /**
     * Create an instance of this state store.
     *
     * @param externalContext   external context
     * @return                  state store
     */
    public synchronized static EXistStateStoreBase instance(ExternalContext externalContext) {
        // Try to find existing store
        final EXistStateStoreBase existingStateStore
                = (EXistStateStoreBase) externalContext.getAttributesMap().get(PERSISTENT_STATE_STORE_APPLICATION_KEY);

        if (existingStateStore != null) {
            return existingStateStore;
        } else {
            // Create new store
            final EXistStateStore newStateStore = new EXistStateStore();

            // Expire persistent entries
            // NOTE: Not sure why we used to remove only those with session information. For now we remove everything as
            // a session is expected, but mainly this is because removing an entire collection is faster than removing
            // individual resources.
            newStateStore.expireAllPersistentUseCollection();

            // Keep new store in application scope
            externalContext.getAttributesMap().put(PERSISTENT_STATE_STORE_APPLICATION_KEY, newStateStore);
            return newStateStore;
        }
    }

    @Override
    public void clear() {
        super.clear();
        expireAllPersistentUseCollection();
    }

    private void expireAllPersistentUseCollection() {

        final String query = "xquery version \"1.0\";" +
            "                 declare namespace xmldb=\"http://exist-db.org/xquery/xmldb\";" +
            "                 declare namespace util=\"http://exist-db.org/xquery/util\";" +
            "                 <result>" +
            "                   {" +
            "                     xmldb:remove('" + XFormsProperties.getStoreCollection() + "')" +
            "                   }" +
            "                 </result>";

        executeQuery(query);
        debug("expired all persistent entries.");
    }

    /**
     * Remove all entries which have the given session id.
     *
     * @param sessionId     session id
     */
    @Override
    public void expireBySessionId(String sessionId) {

        // 1. Expire in memory
        super.expireBySessionId(sessionId);

        // 2. Expire in external storage

        // 2.1. Remove documents having only one session-id element left equal to this session id
        // 2.2. Remove all session-id elements equal to this session id
        final String query = "xquery version \"1.0\";" +
            "                 declare namespace xmldb=\"http://exist-db.org/xquery/xmldb\";" +
            "                 declare namespace util=\"http://exist-db.org/xquery/util\";" +
            "                 <result>" +
            "                   {" +
            "                     (count(for $entry in /entry[session-id = '" + sessionId + "' and count(session-id) = 1]" +
            "                           return (xmldb:remove(util:collection-name($entry), util:document-name($entry)), ''))," +
            "                     for $session-id in /entry/session-id[. = '" + sessionId + "'] return update delete $session-id)" +
            "                   }" +
            "                 </result>";

        final Document result = executeQuery(query);
        final int count = Integer.parseInt(result.getDocument().getRootElement().getStringValue());
        debug("expired " + count + " persistent entries for session (" + sessionId + ").");
    }

    protected void persistEntry(StoreEntry storeEntry) {

        if (isDebugEnabled()) {
            debug("persisting entry for key: " + storeEntry.key + " (" + (storeEntry.value.length() * 2) + " bytes).");
        }

        if (TEMP_PERF_TEST) {

            // Do the operation TEMP_PERF_ITERATIONS times to test performance
            final long startTime = System.currentTimeMillis();
            for (int i = 0; i < TEMP_PERF_ITERATIONS; i ++) {
                persistEntryExistXMLDB(storeEntry);
            }
            debug("average write persistence time: " + ((System.currentTimeMillis() - startTime) / TEMP_PERF_ITERATIONS) + " ms." );

        } else {
            persistEntryExistXMLDB(storeEntry);
        }
    }

    private void persistEntryExistXMLDB(StoreEntry storeEntry) {
        final String messageBody = encodeMessageBody(storeEntry);
        try {
            final StoreEntry existingStoreEntry = findPersistedEntryExistXMLDB(storeEntry.key);
            if (existingStoreEntry != null) {
                // Merge existing session ids
                final int currentSessionIdCount = storeEntry.sessionIds.size();
                storeEntry.sessionIds.addAll(existingStoreEntry.sessionIds);
                debug("merged session ids for key: " + storeEntry.key + " (" + (storeEntry.sessionIds.size() - currentSessionIdCount) + " ids).");
            }

            XMLDB_ACCESSOR.storeResource(new Datasource(EXIST_XMLDB_DRIVER,
                    XFormsProperties.getStoreURI(), XFormsProperties.getStoreUsername(), XFormsProperties.getStorePassword()), XFormsProperties.getStoreCollection(),
                    true, storeEntry.key, messageBody);
        } catch (Exception e) {
            throw new OXFException("Unable to store entry in persistent state store for key: " + storeEntry.key, e);
        }
    }

    private Document executeQuery(String query) {

        final DocumentResult result = new DocumentResult();
        final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();
        identity.setResult(result);

        XMLDB_ACCESSOR.query(new Datasource(EXIST_XMLDB_DRIVER,
                XFormsProperties.getStoreURI(), XFormsProperties.getStoreUsername(), XFormsProperties.getStorePassword()), XFormsProperties.getStoreCollection(),
                true, null, query, null, identity);

        return result.getDocument();
    }

    private String encodeMessageBody(StoreEntry storeEntry) {

        final StringBuilder sb = new StringBuilder("<entry><key>");
        sb.append(storeEntry.key);
        sb.append("</key><value>");

        // Store the value and make sure it is encrypted as it will be externalized
        final String encryptedValue = XFormsUtils.ensureEncrypted(storeEntry.value);

        sb.append(encryptedValue);
        sb.append("</value>");

        // Store the session ids if any
        final Set<String> sessionIds = storeEntry.sessionIds;
        if (sessionIds != null && sessionIds.size() > 0) {
            for (final String currentSessionId: sessionIds) {
                sb.append("<session-id>");
                sb.append(currentSessionId);
                sb.append("</session-id>");
            }
        }

        sb.append("</entry>");

        return sb.toString();
    }

    protected StoreEntry findPersistedEntry(String key) {

        if (isDebugEnabled()) {
            debug("finding persisting entry for key: " + key + ".");
        }

        // Call persistent store
        if (TEMP_PERF_TEST) {

            // Do the operation TEMP_PERF_ITERATIONS times to test performance
            StoreEntry tempResult = null;
            final long startTime = System.currentTimeMillis();
            for (int i = 0; i < TEMP_PERF_ITERATIONS; i ++) {
                tempResult = findPersistedEntryExistXMLDB(key);
                if (tempResult == null)
                    break;
            }
            if (tempResult != null)
                debug("average read persistence time: " + ((System.currentTimeMillis() - startTime) / TEMP_PERF_ITERATIONS) + " ms." );

            return tempResult;

        } else {
            return findPersistedEntryExistXMLDB(key);
        }
    }

    private StoreEntry findPersistedEntryExistXMLDB(String key) {
        final Document document;
        try {
            document = XMLDB_ACCESSOR.getResource(new Datasource(EXIST_XMLDB_DRIVER,
                    XFormsProperties.getStoreURI(), XFormsProperties.getStoreUsername(), XFormsProperties.getStorePassword()),
                    XFormsProperties.getStoreCollection(), true, key);
        } catch (Exception e) {
            throw new OXFException("Unable to find entry in persistent state store for key: " + key, e);
        }

        return (document != null) ? getStoreEntryFromDocument(key, document) : null;
    }

    private StoreEntry getStoreEntryFromDocument(String key, Document document) {
        final Element rootElement = document.getRootElement();

        final String value = rootElement.element("value").getStringValue();
        final Set<String> sessionIds = new HashSet<String>();
        {
            final List<Element> sessionIdsList = Dom4jUtils.elements(rootElement, "session-id");
            for (Element currentElement: sessionIdsList) {
                final String currentSessionId = currentElement.getStringValue();
                sessionIds.add(currentSessionId);
            }
        }

        return new StoreEntry(key, value, sessionIds);
    }

    private static class XMLDBAccessor extends XMLDBProcessor {

        @Override
        public void query(Datasource datasource, String collectionName, boolean createCollection, String resourceId, String query, Map namespaceContext, XMLReceiver xmlReceiver) {
            super.query(datasource, collectionName, createCollection, resourceId, query, namespaceContext, xmlReceiver);
        }

        protected Document getResource(Datasource datasource, String collectionName, boolean createCollection, String resourceName) {

            ensureDriverRegistered(datasource);
            try {
                Collection collection = getCollection(datasource, collectionName);
                if (collection == null) {
                    if (!createCollection)
                        throw new OXFException("Cannot find collection '" + collectionName + "'.");
                    else
                        collection = createCollection(datasource, collectionName);
                }
                final Resource resource = collection.getResource(resourceName);
                if (resource == null) {
                    return null;
                } else if (resource instanceof XMLResource) {

                    final LocationDocumentResult documentResult = new LocationDocumentResult();
                    final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();
                    identity.setResult(documentResult);

                    ((XMLResource) resource).getContentAsSAX(new DatabaseReadXMLReceiver(identity));

                    return documentResult.getDocument();
                } else {
                    throw new OXFException("Unsupported resource type: " + resource.getClass());
                }
            } catch (XMLDBException e) {
                throw new OXFException(e);
            }
        }

        @Override
        protected void storeResource(Datasource datasource, String collectionName, boolean createCollection, String resourceName, String document) {
            super.storeResource(datasource, collectionName, createCollection, resourceName, document);
        }
    }
}

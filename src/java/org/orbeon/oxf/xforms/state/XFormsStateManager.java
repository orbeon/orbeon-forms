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

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.processor.XFormsServer;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * XForms state manager.
 */
public class XFormsStateManager implements XFormsStateLifecycle {

    private static final String LOG_TYPE = "state manager";
    private static final String LOGGING_CATEGORY = "state";
    private static final Logger logger = LoggerFactory.createLogger(XFormsStateManager.class);
    private static final IndentedLogger indentedLogger = XFormsContainingDocument.getIndentedLogger(logger, XFormsServer.getLogger(), LOGGING_CATEGORY);

    private static final String XFORMS_STATE_MANAGER_UUID_KEY_PREFIX = "oxf.xforms.state.manager.uuid-key.";
    private static final String XFORMS_STATE_MANAGER_LISTENER_STATE_KEY_PREFIX = "oxf.xforms.state.manager.session-listeners-key.";

    // Ideally we wouldn't want to force session creation, but it's hard to implement the more elaborate expiration
    // strategy without session.
    public static final boolean FORCE_SESSION_CREATION = true;

    public static IndentedLogger getIndentedLogger() {
        return indentedLogger;
    }

    private static XFormsStateManager instance = null;

    public synchronized static XFormsStateLifecycle instance() {
        if (instance == null) {
            instance = new XFormsStateManager();
        }
        return instance;
    }

    private XFormsStateManager() {}

    /**
     * Called after the initial response is sent without error.
     *
     * Implementation: cache the document and/or store its initial state.
     *
     * @param containingDocument    containing document
     */
    public void afterInitialResponse(XFormsContainingDocument containingDocument) {

        // Remember this UUID in the session
        addDocumentToSession(containingDocument.getUUID());

        cacheOrStore(containingDocument, true);
    }

    /**
     * Information about a document tied to the session.
     */
    private static class SessionDocument implements java.io.Serializable {
        public final Lock lock = new ReentrantLock();
        public final String uuid;

        private SessionDocument(String uuid) {
            this.uuid = uuid;
        }
    }

    // Keep public and static for unit tests and submission processor (called from XSLT)
    public static void addDocumentToSession(String uuid) {
        final ExternalContext.Session session = NetUtils.getSession(XFormsStateManager.FORCE_SESSION_CREATION);

        final Map<String, Object> sessionAttributes = session.getAttributesMap(ExternalContext.Session.APPLICATION_SCOPE);
        sessionAttributes.put(getUUIDSessionKey(uuid), new SessionDocument(uuid));
    }

    private static SessionDocument getSessionDocument(String uuid) {
        final ExternalContext.Session session = NetUtils.getSession(false);
        if (session != null) {
            final Map<String, Object> sessionAttributes = session.getAttributesMap(ExternalContext.Session.APPLICATION_SCOPE);
            return (SessionDocument) sessionAttributes.get(getUUIDSessionKey(uuid));
        } else {
            return null;
        }
    }

    // Keep public and static for unit tests and submission processor (called from XSLT)
    public static void removeSessionDocument(String uuid) {
        final ExternalContext.Session session = NetUtils.getSession(false);
        if (session != null) {
            final Map<String, Object> sessionAttributes = session.getAttributesMap(ExternalContext.Session.APPLICATION_SCOPE);
            sessionAttributes.remove(getUUIDSessionKey(uuid));
        }
    }

    /**
     * Called when the document is added to the cache.
     *
     * Implementation: set listener to remove the document from the cache when the session expires.
     *
     * @param uuid
     */
    public void onAddedToCache(String uuid) {
        addCacheSessionListener(uuid);
    }

    private void addCacheSessionListener(final String uuid) {

        final ExternalContext.Session session = NetUtils.getSession(XFormsStateManager.FORCE_SESSION_CREATION);

        assert session != null;

        final Map<String, Object> sessionAttributes = session.getAttributesMap(ExternalContext.Session.APPLICATION_SCOPE);
        final String listenerSessionKey = getListenerSessionKey(uuid);
        if (sessionAttributes.get(listenerSessionKey) == null) {

            // Remove from cache when session expires
            final ExternalContext.Session.SessionListener listener = new ExternalContext.Session.SessionListener() {
                public void sessionDestroyed() {
                    indentedLogger.logDebug(LOG_TYPE, "Removing document from cache following session expiration.");
                    // NOTE: This will call onRemoved() on the document, and onRemovedFromCache() on XFormsStateManager
                    XFormsDocumentCache.instance().removeDocument(uuid);
                }
            };

            // Add listener
            session.addListener(listener);
            // Remember, in session, mapping (UUID -> session listener)
            sessionAttributes.put(listenerSessionKey, listener);
        }
    }

    /**
     * Called when the document is removed from the cache.
     *
     * Implementation: remove session listener.
     *
     * This is called indirectly when:
     *
     * o the session expires, which calls the session listener above to remove the document from cache
     * o upon takeValid()
     * o nobody else is supposed to call remove() or removeAll() on the cache
     *
     * @param uuid
     */
    public void onRemovedFromCache(String uuid) {
        // WARNING: This can be called while another threads owns this document lock
        removeCacheSessionListener(uuid);
    }

    private void removeCacheSessionListener(String uuid) {
        // Tricky: if onRemove() is called upon session expiration, there might not be an ExternalContext. But it's fine,
        // because the session goes away -> all of its attributes go away so we don't have to remove them below.
        final ExternalContext.Session session = NetUtils.getSession(XFormsStateManager.FORCE_SESSION_CREATION);
        if (session != null) {
            final Map<String, Object> sessionAttributes = session.getAttributesMap(ExternalContext.Session.APPLICATION_SCOPE);
            final String listenerSessionKey = getListenerSessionKey(uuid);

            final ExternalContext.Session.SessionListener listener = (ExternalContext.Session.SessionListener) sessionAttributes.get(listenerSessionKey);
            if (listener != null) {
                // Remove listener
                session.removeListener(listener);
                // Forget, in session, mapping (UUID -> session listener)
                sessionAttributes.remove(listenerSessionKey);
            }
        }
    }

    // Public for unit tests
    public static String getListenerSessionKey(String uuid) {
        return XFORMS_STATE_MANAGER_LISTENER_STATE_KEY_PREFIX + uuid;
    }

    private static String getUUIDSessionKey(String uuid) {
        return XFORMS_STATE_MANAGER_UUID_KEY_PREFIX + uuid;
    }

    /**
     * Called when the document is evicted from cache.
     *
     * Implementation: remove session listener; if server state, store the document state.
     *
     * @param containingDocument    containing document
     */
    public void onEvictedFromCache(XFormsContainingDocument containingDocument) {

        // WARNING: This could have been called while another threads owns this document lock, but the cache now obtains
        // the lock on the document first and will not evict us if we have the lock. This means that this will be called
        // only if no thread is dealing with this document.

        // Remove session listener for cache
        removeCacheSessionListener(containingDocument.getUUID());

        // Store document state
        if (containingDocument.getStaticState().isServerStateHandling())
            storeDocumentState(containingDocument, false);
    }

    private void cacheOrStore(XFormsContainingDocument containingDocument, boolean isInitialState) {
        if (XFormsDocumentCache.instance().isEnabled(containingDocument.getStaticState())) {
            // Cache the document
            indentedLogger.logDebug(LOG_TYPE, "Document cache enabled. Storing document in cache.");
            XFormsDocumentCache.instance().storeDocument(containingDocument);

            if (isInitialState && containingDocument.getStaticState().isServerStateHandling()) {
                // Also store document state (used by browser back and <xforms:reset>)
                indentedLogger.logDebug(LOG_TYPE, "Storing initial document state.");
                storeDocumentState(containingDocument, isInitialState);
            }

        } else if (containingDocument.getStaticState().isServerStateHandling()) {
            // Directly store the document state
            indentedLogger.logDebug(LOG_TYPE, "Document cache disabled. Storing initial document state.");
            storeDocumentState(containingDocument, isInitialState);
        }
    }

    /**
     * Store the document state.
     *
     * @param containingDocument    containing document
     * @param isInitialState        true iif this is the document's initial state
     */
    private void storeDocumentState(XFormsContainingDocument containingDocument, boolean isInitialState) {

        assert containingDocument.getStaticState().isServerStateHandling();

        final ExternalContext externalContext = NetUtils.getExternalContext();
        final XFormsStateStore stateStore = XFormsStateStoreFactory.instance(externalContext);

        final ExternalContext.Session session = externalContext.getSession(XFormsStateManager.FORCE_SESSION_CREATION);

        stateStore.storeDocumentState(containingDocument, session, isInitialState);
    }

    /**
     * Get the document UUID from an incoming request.
     *
     * @param request   incoming
     * @return          document UUID
     */
    public static String getRequestUUID(Document request) {
        final Element uuidElement = request.getRootElement().element(XFormsConstants.XXFORMS_UUID_QNAME);
        assert uuidElement != null;
        return StringUtils.trimToNull(uuidElement.getTextTrim());
    }

    /**
     * Get the request sequence number from an incoming request.
     *
     * @param request   incoming
     * @return          request sequence number, or -1 if missing
     */
    public static long getRequestSequence(Document request) {
        final Element sequenceElement = request.getRootElement().element(XFormsConstants.XXFORMS_SEQUENCE_QNAME);
        assert sequenceElement != null;
        final String text = StringUtils.trimToNull(sequenceElement.getTextTrim());
        return (text != null) ? Long.parseLong(text) : -1; // allow for empty value for non-Ajax cases
    }

    /**
     * Called before an incoming update.
     *
     * If found in cache, document is removed from cache.
     *
     * @param parameters    incoming Ajax request
     * @return              document, either from cache or from state information
     */
    public XFormsContainingDocument beforeUpdate(RequestParameters parameters) {

        assert parameters.getUUID() != null;

        // Check that the session is associated with the requested UUID. This enforces the rule that an incoming request
        // for a given UUID must belong to the same session that created the document. If the session expires, the
        // key goes away as well, and the key won't be present. If we don't do this check, the XForms server might
        // handle requests for a given UUID within a separate session, therefore providing access to other sessions,
        // which is not desirable. Further, we now have a lock stored in the session.
        final Lock lock = getDocumentLock(parameters.getUUID());
        if (lock == null)
            throw new OXFException("Session has expired. Unable to process incoming request.");

        // Lock document
        lock.lock();

        // We got the lock, return the document

        return findOrRestoreDocument(parameters, false);
    }

    public static Lock getDocumentLock(String uuid) {
        final SessionDocument sessionDocument = getSessionDocument(uuid);
        return (sessionDocument != null) ? sessionDocument.lock : null;
    }

    /**
     * Called after an update.
     *
     * @param containingDocument    document
     * @param keepDocument          whether to keep the document around
     */
    public void afterUpdate(XFormsContainingDocument containingDocument, boolean keepDocument) {
        final String uuid = containingDocument.getUUID();
        final Lock lock = getDocumentLock(uuid);
        if (lock == null) {
            // Possible situation is that session expired in the middle of a request? Seems unlikely. In this case we
            // don't expect to update the session information as somebody clearly wanted to get rid of the session, so
            // just return.
            return;
        }

        if (keepDocument) {
            // Re-add document to the cache
            indentedLogger.logDebug(LOG_TYPE, "Keeping document in cache.");
            cacheOrStore(containingDocument, false);
        } else {
            // Don't re-add document to the cache
            indentedLogger.logDebug(LOG_TYPE, "Not keeping document in cache following error.");

            // Remove all information about this document from the session
            removeCacheSessionListener(uuid);
            removeSessionDocument(uuid);
        }

        // Unlock document
        lock.unlock();
    }

    private static class RequestParametersImpl implements RequestParameters, java.io.Serializable {
        private final String uuid;
        private final String encodedClientStaticState;
        private final String encodedClientDynamicState;

        private RequestParametersImpl(String uuid, String encodedClientStaticState, String encodedClientDynamicState) {
            this.uuid = uuid;
            this.encodedClientStaticState = encodedClientStaticState;
            this.encodedClientDynamicState = encodedClientDynamicState;
        }

        public String getUUID() {
            return uuid;
        }

        public String getEncodedClientStaticState() {
            return encodedClientStaticState;
        }

        public String getEncodedClientDynamicState() {
            return encodedClientDynamicState;
        }
    }

    public RequestParameters extractParameters(Document request, boolean isInitialState) {

        // Get UUID
        final String uuid = getRequestUUID(request);
        assert uuid != null;

        // Get static state if any
        final String encodedStaticState;
        {
            final Element staticStateElement = request.getRootElement().element(XFormsConstants.XXFORMS_STATIC_STATE_QNAME);
            encodedStaticState = (staticStateElement != null) ? StringUtils.trimToNull(staticStateElement.getTextTrim()) : null;
        }

        // Get dynamic state if any
        final String encodedDynamicState;
        {
            final QName qName = isInitialState ? XFormsConstants.XXFORMS_INITIAL_DYNAMIC_STATE_QNAME : XFormsConstants.XXFORMS_DYNAMIC_STATE_QNAME;
            final Element dynamicStateElement = request.getRootElement().element(qName);
            encodedDynamicState = (dynamicStateElement != null) ? StringUtils.trimToNull(dynamicStateElement.getTextTrim()) : null;
        }

        assert (encodedStaticState != null && encodedDynamicState != null) || (encodedStaticState == null && encodedDynamicState == null);

        // Session must be present if state is not coming with the request
        if (NetUtils.getSession(false) == null && encodedStaticState == null) {
            throw new OXFException("Session has expired. Unable to process incoming request.");
        }

        return new RequestParametersImpl(uuid, encodedStaticState, encodedDynamicState);
    }

    /**
     * Find or restore a document based on an incoming request.
     *
     * Implementation: try cache first, then restore from store if not found.
     *
     * If found in cache, document is removed from cache.
     *
     * @param parameters        update parameters
     * @param isInitialState    whether to return the initial state, otherwise return the current state
     * @return                  document, either from cache or from state information
     */
    public XFormsContainingDocument findOrRestoreDocument(RequestParameters parameters, boolean isInitialState) {

        // Try cache first unless the initial state is requested
        if (!isInitialState) {
            if (XFormsProperties.isCacheDocument()) {
                // Try to find the document in cache using the UUID
                // NOTE: If the document has cache.document="false", then it simply won't be found in the cache, but
                // we can't know that the property is set to false before trying.
                final XFormsContainingDocument cachedDocument = XFormsDocumentCache.instance().takeDocument(parameters.getUUID());
                if (cachedDocument != null) {
                    // Found in cache
                    indentedLogger.logDebug(LOG_TYPE, "Document cache enabled. Returning document from cache.");
                    return cachedDocument;
                }
                indentedLogger.logDebug(LOG_TYPE, "Document cache enabled. Document not found in cache. Retrieving state from store.");
            } else {
                indentedLogger.logDebug(LOG_TYPE, "Document cache disabled. Retrieving state from store.");
            }
        } else {
            indentedLogger.logDebug(LOG_TYPE, "Initial document state requested. Retrieving state from store.");
        }

        // Must recreate from store
        return createDocumentFromStore(parameters, isInitialState);
    }

    private XFormsContainingDocument createDocumentFromStore(RequestParameters parameters, boolean isInitialState) {

        final boolean isServerState = parameters.getEncodedClientStaticState() == null;

        final XFormsState xformsState;
        if (isServerState) {
            // State must be found by UUID in the store
            final ExternalContext externalContext = NetUtils.getExternalContext();
            final XFormsStateStore stateStore = XFormsStateStoreFactory.instance(externalContext);

            if (indentedLogger.isDebugEnabled())
                indentedLogger.logDebug(LOG_TYPE, "Getting document state from store.",
                        "current cache size", Integer.toString(XFormsDocumentCache.instance().getCurrentSize()),
                        "max cache size", Integer.toString(XFormsDocumentCache.instance().getMaxSize()),
                        "current store size", Integer.toString(stateStore.getCurrentSize()),
                        "max store size", Integer.toString(stateStore.getMaxSize())
                );

            final ExternalContext.Session session = externalContext.getSession(XFormsStateManager.FORCE_SESSION_CREATION);

            xformsState = stateStore.findState(session, parameters.getUUID(), isInitialState);

            if (xformsState == null) {
                // Oops, we couldn't find the state in the store

                final String UNABLE_TO_RETRIEVE_XFORMS_STATE_MESSAGE = "Unable to retrieve XForms engine state.";
                final String PLEASE_RELOAD_PAGE_MESSAGE = "Please reload the current page. Note that you will lose any unsaved changes.";
                final String UUIDS_MESSAGE = "UUID: " + parameters.getUUID();

                // Produce exception
                final ExternalContext.Session currentSession =  externalContext.getSession(false);
                if (currentSession == null || currentSession.isNew()) {
                    // This means that no session is currently existing, or a session exists but it is newly created
                    final String message = "Your session has expired. " + PLEASE_RELOAD_PAGE_MESSAGE;
                    indentedLogger.logError("", message);
                    throw new OXFException(message + " " + UUIDS_MESSAGE);
                } else {
                    // There is a session and it is still known by the client
                    final String message = UNABLE_TO_RETRIEVE_XFORMS_STATE_MESSAGE + " " + PLEASE_RELOAD_PAGE_MESSAGE;
                    indentedLogger.logError("", message);
                    throw new OXFException(message + " " + UUIDS_MESSAGE);
                }
            }
        } else {
            // State comes directly with request
            xformsState = new XFormsState(parameters.getEncodedClientStaticState(), parameters.getEncodedClientDynamicState());
        }

        // Create document
        final XFormsContainingDocument document = new XFormsContainingDocument(xformsState);
        assert isServerState ? document.getStaticState().isServerStateHandling() : document.getStaticState().isClientStateHandling();
        return document;
    }

    /**
     * Return the static state string to send to the client in the HTML page.
     *
     *
     * @param containingDocument    document
     * @return                      encoded state
     */
    public String getClientEncodedStaticState(XFormsContainingDocument containingDocument) {
        final String staticStateString;
        {
            if (containingDocument.getStaticState().isServerStateHandling()) {
                // No state to return
                staticStateString = null;
            } else {
                // Return full encoded state
                staticStateString = containingDocument.getStaticState().encodedState();
            }
        }
        return staticStateString;
    }

    /**
     * Return the dynamic state string to send to the client in the HTML page.
     *
     *
     * @param containingDocument    containing document
     * @return                      encoded state
     */
    public String getClientEncodedDynamicState(XFormsContainingDocument containingDocument) {
        final String dynamicStateString;
        {
            if (containingDocument.getStaticState().isServerStateHandling()) {
                // Return UUID
                dynamicStateString = null;
            } else {
                // Return full encoded state
                dynamicStateString = containingDocument.createEncodedDynamicState(XFormsProperties.isGZIPState(), true);
            }
        }
        return dynamicStateString;
    }

    /**
     * Called before sending an update response.
     *
     * Implementation: update the document's change sequence.
     *
     * @param containingDocument    containing document
     * @param ignoreSequence        whether to ignore the sequence number
     */
    public void beforeUpdateResponse(XFormsContainingDocument containingDocument, boolean ignoreSequence) {
        if (containingDocument.isDirtySinceLastRequest()) {
            // The document is dirty
            indentedLogger.logDebug(LOG_TYPE, "Document is dirty. Generating new dynamic state.");
        } else {
            // The document is not dirty: no real encoding takes place here
            indentedLogger.logDebug(LOG_TYPE, "Document is not dirty. Keep existing dynamic state.");
        }

        // Tell the document to update its state
        if (!ignoreSequence)
            containingDocument.updateChangeSequence();
    }

    /**
     * Called after sending a successful update response.
     *
     * Implementation: cache the document and/or store its current state.
     *
     * @param containingDocument    containing document
     */
    public void afterUpdateResponse(XFormsContainingDocument containingDocument) {
        // Notify document that we are done sending the response
        containingDocument.afterUpdateResponse();
    }

    /**
     * Return the delay for the session heartbeat event.
     *
     * @param containingDocument    containing document
     * @param externalContext       external context (for access to session and application scopes)
     * @return                      delay in ms, or -1 is not applicable
     */
    public static long getHeartbeatDelay(XFormsContainingDocument containingDocument, ExternalContext externalContext) {
        if (containingDocument.getStaticState().isClientStateHandling()) {
            // No heartbeat for client state. Is that reasonable?
            return -1;
        } else {
            final boolean isSessionHeartbeat = XFormsProperties.isSessionHeartbeat(containingDocument);
            final ExternalContext.Session session = externalContext.getSession(FORCE_SESSION_CREATION);

            final long heartbeatDelay;
            if (isSessionHeartbeat)
                heartbeatDelay = session.getMaxInactiveInterval() * 800; // 80% of session expiration time, in ms
            else
                heartbeatDelay = -1;

            return heartbeatDelay;
        }
    }
}

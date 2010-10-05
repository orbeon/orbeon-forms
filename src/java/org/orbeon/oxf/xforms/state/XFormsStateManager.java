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
import org.dom4j.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.processor.XFormsServer;

import java.util.Map;

/**
 * XForms state manager.
 */
public class XFormsStateManager implements XFormsStateLifecycle {

    private static final String LOG_TYPE = "state manager";
    private static final String LOGGING_CATEGORY = "state";
    private static final Logger logger = LoggerFactory.createLogger(XFormsStateManager.class);
    private static final IndentedLogger indentedLogger = XFormsContainingDocument.getIndentedLogger(logger, XFormsServer.getLogger(), LOGGING_CATEGORY);

    private static final String XFORMS_STATE_MANAGER_LISTENER_STATE_KEY_PREFIX = "oxf.xforms.state.manager.session-listeners-key.";

    // Ideally we wouldn't want to force session creation, but it's hard to implement the more elaborate expiration
    // strategy without session.
    public static final boolean FORCE_SESSION_CREATION = true;

    public static IndentedLogger getIndentedLogger() {
        return indentedLogger;
    }

    private static XFormsStateManager instance = null;

    public synchronized static XFormsStateManager instance() {
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
     * @param propertyContext       current context
     * @param containingDocument    containing document
     */
    public void afterInitialResponse(PropertyContext propertyContext, XFormsContainingDocument containingDocument) {
        cacheOrStore(propertyContext, containingDocument, true);
    }

    /**
     * Called when the document is added to the cache.
     *
     * Implementation: set listener to remove the document from the cache when the session expires.
     *
     * @param propertyContext       current context
     * @param containingDocument    containing document
     */
    public void onAdd(PropertyContext propertyContext, final XFormsContainingDocument containingDocument) {

        final ExternalContext externalContext = XFormsUtils.getExternalContext(propertyContext);
        final ExternalContext.Session session = externalContext.getSession(XFormsStateManager.FORCE_SESSION_CREATION);

        final Map<String, Object> sessionAttributes = session.getAttributesMap(ExternalContext.Session.APPLICATION_SCOPE);
        final String listenerSessionKey = getListenerSessionKey(containingDocument);
        final String uuid = containingDocument.getUUID();
        if (sessionAttributes.get(listenerSessionKey) == null) {

            // Remove from cache when session expires
            final ExternalContext.Session.SessionListener listener = new ExternalContext.Session.SessionListener() {
                public void sessionDestroyed() {
                    // NOTE: Don't use PropertyContext provided above here, it must be let go.
                    indentedLogger.logDebug(LOG_TYPE, "Removing document from cache following session expiration.");
                    final PipelineContext pipelineContext = new PipelineContext();
                    final XFormsContainingDocument containingDocument = XFormsDocumentCache.instance().getDocument(pipelineContext, uuid);
                    if (containingDocument != null)
                        XFormsDocumentCache.instance().removeDocument(pipelineContext, containingDocument);
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
     * @param propertyContext       current context
     * @param containingDocument    containing document
     */
    public void onRemove(PropertyContext propertyContext, XFormsContainingDocument containingDocument) {

        final ExternalContext externalContext = XFormsUtils.getExternalContext(propertyContext);
        // Tricky: if onRemove() is called upon session expiration, there might not be an ExternalContext. But it's fine,
        // because the session goes away -> all of its attributes go away so we don't have to remove them below.
        if (externalContext != null) {
            final ExternalContext.Session session = externalContext.getSession(XFormsStateManager.FORCE_SESSION_CREATION);

            final Map<String, Object> sessionAttributes = session.getAttributesMap(ExternalContext.Session.APPLICATION_SCOPE);
            final String listenerSessionKey = getListenerSessionKey(containingDocument);
            if (sessionAttributes.get(listenerSessionKey) != null) {
                final ExternalContext.Session.SessionListener listener
                        = (ExternalContext.Session.SessionListener) sessionAttributes.get(listenerSessionKey);
                if (listener != null) {
                    // Remove listener
                    session.removeListener(listener);
                    // Forget, in session, mapping (UUID -> session listener)
                    sessionAttributes.remove(listenerSessionKey);
                }
            }
        }
    }

    // Public for unit tests
    public static String getListenerSessionKey(XFormsContainingDocument containingDocument) {
        return XFORMS_STATE_MANAGER_LISTENER_STATE_KEY_PREFIX + containingDocument.getUUID();
    }

    /**
     * Called when the document is evicted from cache.
     *
     * Implementation: remove session listener; if server state, store the document state.
     *
     * @param propertyContext       current context
     * @param containingDocument    containing document
     */
    public void onEvict(PropertyContext propertyContext, XFormsContainingDocument containingDocument) {

        // Do the same as on remove()
        onRemove(propertyContext, containingDocument);

        // Store state to cache if needed
        if (containingDocument.getStaticState().isServerStateHandling())
            storeDocumentState(propertyContext, containingDocument, false);
    }

    private void cacheOrStore(PropertyContext propertyContext, XFormsContainingDocument containingDocument, boolean isInitialState) {
        if (XFormsDocumentCache.instance().isEnabled(containingDocument.getStaticState())) {
            // Cache the document
            indentedLogger.logDebug(LOG_TYPE, "Document cache enabled. Storing document in cache.");
            XFormsDocumentCache.instance().storeDocument(propertyContext, containingDocument);

            if (isInitialState && containingDocument.getStaticState().isServerStateHandling()) {
                // Also store document state (used by browser back and <xforms:reset>)
                indentedLogger.logDebug(LOG_TYPE, "Storing initial document state.");
                storeDocumentState(propertyContext, containingDocument, isInitialState);
            }

        } else if (containingDocument.getStaticState().isServerStateHandling()) {
            // Directly store the document state
            indentedLogger.logDebug(LOG_TYPE, "Document cache disabled. Storing initial document state.");
            storeDocumentState(propertyContext, containingDocument, isInitialState);
        }
    }

    /**
     * Store the document state.
     *
     * @param propertyContext       current context
     * @param containingDocument    containing document
     * @param isInitialState        true iif this is the document's initial state
     */
    private void storeDocumentState(PropertyContext propertyContext, XFormsContainingDocument containingDocument, boolean isInitialState) {

        assert containingDocument.getStaticState().isServerStateHandling();

        final ExternalContext externalContext = XFormsUtils.getExternalContext(propertyContext);
        final XFormsStateStore stateStore = XFormsPersistentApplicationStateStore.instance(externalContext);

        final ExternalContext.Session session = externalContext.getSession(XFormsStateManager.FORCE_SESSION_CREATION);
        final String sessionId = session.getId();

        stateStore.storeDocumentState(propertyContext, containingDocument, sessionId, isInitialState);
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
     * Find or restore a document based on an incoming request.
     *
     * @param pipelineContext   current context
     * @param request           incoming Ajax request document
     * @param session           session
     * @param isInitialState    whether to return the initial state, otherwise return the current state
     * @return                  document, either from cache or from state information
     */
    public XFormsContainingDocument findOrRestoreDocument(PipelineContext pipelineContext, Document request, ExternalContext.Session session, boolean isInitialState) {

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
        if (session == null && encodedStaticState == null) {
            throw new OXFException("Session has expired. Unable to process incoming request.");
        }

        return findOrRestoreDocument(pipelineContext, uuid, encodedStaticState, encodedDynamicState, isInitialState);
    }

    /**
     * Find or restore a document based on an incoming request.
     *
     * Implementation: try cache first, then restore from store if not found.
     *
     * @param pipelineContext           current context
     * @param uuid                      document UUID
     * @param encodedClientStaticState  incoming static state or null
     * @param encodedClientDynamicState incoming dynamic state or null
     * @param isInitialState            whether to return the initial state, otherwise return the current state
     * @return                          document, either from cache or from state information
     */
    public XFormsContainingDocument findOrRestoreDocument(PipelineContext pipelineContext, String uuid, String encodedClientStaticState,
                                                          String encodedClientDynamicState, boolean isInitialState) {

        // Try cache first unless the initial state is requested
        if (!isInitialState) {
            if (XFormsProperties.isCacheDocument()) {
                // Try to find the document in cache using the UUID
                // NOTE: If the document has cache.document="false", then it simply won't be found in the cache, but
                // we can't know that the property is set to false before trying.
                final XFormsContainingDocument cachedDocument = XFormsDocumentCache.instance().getDocument(pipelineContext, uuid);
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
        return createDocumentFromStore(pipelineContext, uuid, encodedClientStaticState, encodedClientDynamicState, isInitialState);
    }

    private XFormsContainingDocument createDocumentFromStore(PipelineContext pipelineContext, String uuid, String encodedClientStaticState,
                                                             String encodedClientDynamicState, boolean isInitialState) {

        final boolean isServerState = encodedClientStaticState == null;

        final XFormsState xformsState;
        if (isServerState) {
            // State must be found by UUID in the store
            final ExternalContext externalContext = XFormsUtils.getExternalContext(pipelineContext);
            final XFormsStateStore stateStore = XFormsPersistentApplicationStateStore.instance(externalContext);

            if (indentedLogger.isDebugEnabled())
                indentedLogger.logDebug(LOG_TYPE, "Getting document state from store.",
                        "current cache size", Integer.toString(XFormsDocumentCache.instance().getCurrentSize()),
                        "max cache size", Integer.toString(XFormsDocumentCache.instance().getMaxSize()),
                        "current store size", Integer.toString(stateStore.getCurrentSize()),
                        "max store size", Integer.toString(stateStore.getMaxSize())
                );
            xformsState = stateStore.findState(uuid, isInitialState);

            if (xformsState == null) {
                // Oops, we couldn't find the state in the store

                final String UNABLE_TO_RETRIEVE_XFORMS_STATE_MESSAGE = "Unable to retrieve XForms engine state.";
                final String PLEASE_RELOAD_PAGE_MESSAGE = "Please reload the current page. Note that you will lose any unsaved changes.";
                final String UUIDS_MESSAGE = "UUID: " + uuid;

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
            xformsState = new XFormsState(encodedClientStaticState, encodedClientDynamicState);
        }

        // Create document
        final XFormsContainingDocument document = new XFormsContainingDocument(pipelineContext, xformsState);
        assert isServerState ? document.getStaticState().isServerStateHandling() : document.getStaticState().isClientStateHandling();
        return document;
    }

    /**
     * Return the static state string to send to the client in the HTML page.
     *
     * @param propertyContext       current context
     * @param containingDocument    document
     * @return                      encoded state
     */
    public String getClientEncodedStaticState(PropertyContext propertyContext, XFormsContainingDocument containingDocument) {
        final String staticStateString;
        {
            if (containingDocument.getStaticState().isServerStateHandling()) {
                // No state to return
                staticStateString = null;
            } else {
                // Return full encoded state
                staticStateString = containingDocument.getStaticState().getEncodedStaticState(propertyContext);
            }
        }
        return staticStateString;
    }

    /**
     * Return the dynamic state string to send to the client in the HTML page.
     *
     * @param propertyContext       current context
     * @param containingDocument    containing document
     * @return                      encoded state
     */
    public String getClientEncodedDynamicState(PropertyContext propertyContext, XFormsContainingDocument containingDocument) {
        final String dynamicStateString;
        {
            if (containingDocument.getStaticState().isServerStateHandling()) {
                // Return UUID
                dynamicStateString = null;
            } else {
                // Return full encoded state
                dynamicStateString = containingDocument.createEncodedDynamicState(propertyContext, true);
            }
        }
        return dynamicStateString;
    }

    /**
     * Called before sending an update response.
     *
     * Implementation: update the document's change sequence.
     *
     * @param propertyContext       current context
     * @param containingDocument    containing document
     * @param ignoreSequence        whether to ignore the sequence number
     */
    public void beforeUpdateResponse(PropertyContext propertyContext, XFormsContainingDocument containingDocument, boolean ignoreSequence) {
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
     * @param propertyContext       current context
     * @param containingDocument    containing document
     */
    public void afterUpdateResponse(PropertyContext propertyContext, XFormsContainingDocument containingDocument) {

        // Notify document that we are done sending the response
        containingDocument.afterUpdateResponse();

        cacheOrStore(propertyContext, containingDocument, false);
    }

    /**
     * Called if there is an error during an update request.
     *
     * Implementation: remove the document from the cache.
     *
     * @param propertyContext       current context
     * @param containingDocument    containing document
     */
    public void onUpdateError(PropertyContext propertyContext, XFormsContainingDocument containingDocument) {
        if (XFormsDocumentCache.instance().isEnabled(containingDocument.getStaticState())) {
            // Remove document from the cache
            indentedLogger.logDebug(LOG_TYPE, "Document cache enabled. Removing document from cache following error.");
            XFormsDocumentCache.instance().removeDocument(propertyContext, containingDocument);
        }
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

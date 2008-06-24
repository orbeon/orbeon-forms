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

import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.UUIDUtils;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsUtils;

/**
 * Centralize XForms state management.
 *
 * TODO: Get rid of the old "session" store code as it is replaced by the new persistent store.
 */
public class XFormsStateManager {

    public static Logger logger = LoggerFactory.createLogger(XFormsStateManager.class);

    // All these must have the same length
    public static final String SESSION_STATE_PREFIX = "sess:";
    public static final String APPLICATION_STATE_PREFIX = "appl:";
    public static final String PERSISTENT_STATE_PREFIX = "pers:";

    private static final int PREFIX_COLON_POSITION = SESSION_STATE_PREFIX.length() - 1;

    // Ideally we wouldn't want to force session creation, but it's hard to implement the more elaborate expiration
    // strategy without session. See https://wiki.objectweb.org/ops/Wiki.jsp?page=XFormsStateStoreImprovements
    public static final boolean FORCE_SESSION_CREATION = true;

    /**
     * Get the initial encoded XForms state as it must be sent to the client within the (X)HTML.
     *
     * @param containingDocument    containing document
     * @param externalContext       external context (for access to session and application scopes)
     * @param xformsState           post-initialization XFormsState
     * @param staticStateUUID       static state UUID (if static state was cached against input document)
     * @param dynamicStateUUID      dynamic state UUID (if dynamic state was cached against output document)
     * @return                      XFormsState containing the encoded static and dynamic states
     */
    public static XFormsState getInitialEncodedClientState(XFormsContainingDocument containingDocument, ExternalContext externalContext, XFormsState xformsState, String staticStateUUID, String dynamicStateUUID) {

        final String currentPageGenerationId;
        final String staticStateString;
        {
            if (!XFormsProperties.isClientStateHandling(containingDocument)) {
                if (XFormsProperties.isLegacySessionStateHandling(containingDocument)) {
                    // Legacy session server handling

                    // Produce UUID
                    if (staticStateUUID == null) {
                        currentPageGenerationId = UUIDUtils.createPseudoUUID();
                        staticStateString = SESSION_STATE_PREFIX + currentPageGenerationId;
                    } else {
                        // In this case, we first store in the application scope, so that multiple requests can use the
                        // same cached state.
                        currentPageGenerationId = staticStateUUID;
                        staticStateString = APPLICATION_STATE_PREFIX + currentPageGenerationId;
                    }
                } else {
                    // New server state handling with persistent store

                    if (staticStateUUID == null) {
                        currentPageGenerationId = UUIDUtils.createPseudoUUID();
                    } else {
                        currentPageGenerationId = staticStateUUID;
                    }
                    staticStateString = PERSISTENT_STATE_PREFIX + currentPageGenerationId;
                }
            } else {
                // Produce encoded static state
                staticStateString = xformsState.getStaticState();
                currentPageGenerationId = null;
            }
        }

        final String dynamicStateString;
        {
            if (!XFormsProperties.isClientStateHandling(containingDocument)) {
                if (XFormsProperties.isLegacySessionStateHandling(containingDocument)) {
                    // Legacy session server handling

                    if (dynamicStateUUID == null) {
                        // In this case, we use session scope since not much sharing will occur, if at all.

                        // Produce dynamic state key
                        final String newRequestId = UUIDUtils.createPseudoUUID();
                        final XFormsStateStore stateStore = XFormsSessionStateStore.instance(externalContext, true);
                        stateStore.add(currentPageGenerationId, null, newRequestId, xformsState, null, true);
                        dynamicStateString = SESSION_STATE_PREFIX + newRequestId;
                    } else {
                        // In this case, we first store in the application scope, so that multiple requests can use the
                        // same cached state.
                        dynamicStateString = APPLICATION_STATE_PREFIX + dynamicStateUUID;
                        final XFormsStateStore applicationStateStore = XFormsApplicationStateStore.instance(externalContext);
                        applicationStateStore.add(currentPageGenerationId, null, dynamicStateUUID, xformsState, null, true);
                    }
                } else {
                    // New server state handling with persistent store

                    // Get session id if needed
                    final ExternalContext.Session session = externalContext.getSession(FORCE_SESSION_CREATION);
                    final String sessionId = (session != null) ? session.getId() : null;

                    final XFormsStateStore stateStore = XFormsPersistentApplicationStateStore.instance(externalContext);
                    if (dynamicStateUUID == null) {
                        // Produce dynamic state key
                        final String newRequestId = UUIDUtils.createPseudoUUID();
                        stateStore.add(currentPageGenerationId, null, newRequestId, xformsState, sessionId, true);
                        dynamicStateString = PERSISTENT_STATE_PREFIX + newRequestId;
                    } else {
                        dynamicStateString = PERSISTENT_STATE_PREFIX + dynamicStateUUID;
                        stateStore.add(currentPageGenerationId, null, dynamicStateUUID, xformsState, sessionId, true);
                    }
                }
            } else {
                // Send state to the client
                dynamicStateString = xformsState.getDynamicState();
            }
        }

        return new XFormsState(staticStateString, dynamicStateString);
    }

    /**
     * Return the delay for the session heartbeat event.
     *
     * @param containingDocument    containing document
     * @param externalContext       external context (for access to session and application scopes)
     * @return                      delay in ms, or -1 is not applicable
     */
    public static long getHeartbeatDelay(XFormsContainingDocument containingDocument, ExternalContext externalContext) {
        if (XFormsProperties.isClientStateHandling(containingDocument)) {
            return -1;
        } else {
            final long heartbeatDelay;
            final boolean isSessionHeartbeat = XFormsProperties.isSessionHeartbeat(containingDocument);
            final ExternalContext.Session session = externalContext.getSession(FORCE_SESSION_CREATION);
            if (isSessionHeartbeat && session != null)
                heartbeatDelay = session.getMaxInactiveInterval() * 800; // 80% of session expiration time, in ms
            else
                heartbeatDelay = -1;
            return heartbeatDelay;
        }
    }

    /**
     * Decode static and dynamic state strings coming from the client.
     *
     * @param pipelineContext       pipeline context
     * @param staticStateString     static state string as sent by client
     * @param dynamicStateString    dynamic state string as sent by client
     * @return                      decoded state
     */
    public static XFormsDecodedClientState decodeClientState(PipelineContext pipelineContext, String staticStateString, String dynamicStateString) {

        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

        final XFormsDecodedClientState xformsDecodedClientState;
        if (staticStateString.length() > PREFIX_COLON_POSITION && staticStateString.charAt(PREFIX_COLON_POSITION) == ':') {
            // State doesn't come directly with request

            // Separate prefixes from UUIDs
            final String staticStatePrefix = staticStateString.substring(0, PREFIX_COLON_POSITION + 1);
            final String staticStateUUID = staticStateString.substring(PREFIX_COLON_POSITION + 1);

            final String dynamicStatePrefix = dynamicStateString.substring(0, PREFIX_COLON_POSITION + 1);
            final String dynamicStateUUID = dynamicStateString.substring(PREFIX_COLON_POSITION + 1);

            // Both prefixes must be the same
            if (!staticStatePrefix.equals(dynamicStatePrefix)) {
                final String message = "Inconsistent XForms state prefixes: " + staticStatePrefix + ", " + dynamicStatePrefix;
                logger.debug(message);
                throw new OXFException(message);
            }

            // Get relevant store
            final XFormsStateStore stateStore;
            if (staticStatePrefix.equals(PERSISTENT_STATE_PREFIX)) {
                stateStore = XFormsPersistentApplicationStateStore.instance(externalContext);
            } else if (staticStatePrefix.equals(SESSION_STATE_PREFIX)) {
                // NOTE: Don't create session at this time if it is not found
                stateStore = XFormsSessionStateStore.instance(externalContext, false);
            } else if (staticStatePrefix.equals(APPLICATION_STATE_PREFIX))  {
                stateStore = XFormsApplicationStateStore.instance(externalContext);
            } else {
                // Invalid prefix
                final String message = "Invalid state prefix: " + staticStatePrefix;
                logger.debug(message);
                throw new OXFException(message);
            }

            // Get state from store
            final XFormsState xformsState = (stateStore == null) ? null : stateStore.find(staticStateUUID, dynamicStateUUID);

            if (xformsState == null) {
                // Oops, we couldn't find the state in the store

                final String UNABLE_TO_RETRIEVE_XFORMS_STATE_MESSAGE = "Unable to retrieve XForms engine state.";
                final String PLEASE_RELOAD_PAGE_MESSAGE = "Please reload the current page. Note that you will lose any unsaved changes.";
                final String UUIDS_MESSAGE = "Static state key: " + staticStateUUID + ", dynamic state key: " + dynamicStateUUID;

                if (staticStatePrefix.equals(PERSISTENT_STATE_PREFIX)) {
                    final ExternalContext.Session currentSession =  externalContext.getSession(false);
                    if (currentSession == null || currentSession.isNew()) {
                        // This means that no session is currently existing, or a session exists but it is newly created
                        final String message = "Your session has expired. " + PLEASE_RELOAD_PAGE_MESSAGE;
                        logger.error(message);
                        throw new OXFException(message + " " + UUIDS_MESSAGE);
                    } else {
                        // There is a session and it is still known by the client
                        final String message = UNABLE_TO_RETRIEVE_XFORMS_STATE_MESSAGE + " " + PLEASE_RELOAD_PAGE_MESSAGE;
                        logger.error(message);
                        throw new OXFException(message + " " + UUIDS_MESSAGE);
                    }

                } else {
                    final String message = UNABLE_TO_RETRIEVE_XFORMS_STATE_MESSAGE + " " + PLEASE_RELOAD_PAGE_MESSAGE;
                    logger.error(message);
                    throw new OXFException(message + " " + UUIDS_MESSAGE);
                }
            }

            xformsDecodedClientState = new XFormsDecodedClientState(xformsState, staticStateUUID, dynamicStateUUID);

        } else {
            // State comes directly with request
            xformsDecodedClientState = new XFormsDecodedClientState(new XFormsState(staticStateString, dynamicStateString), null, null);
        }

        return xformsDecodedClientState;
    }

    /**
     * Get serialized and encrypted XForms state. This does not use the document's state handling preferences, and
     * doesn't attempt to cache the containing document.
     *
     * @param containingDocument        containing document
     * @param pipelineContext           pipeline context
     * @param xformsDecodedClientState  decoded state as received in Ajax request
     * @return                          XFormsState serialized and encrypted
     */
    public static XFormsState getEncryptedSerializedClientState(XFormsContainingDocument containingDocument, PipelineContext pipelineContext, XFormsDecodedClientState xformsDecodedClientState) {

        // Create encoded static state and make sure encryption is used
        final String newEncodedStaticState;
        {
            final long startTime = logger.isDebugEnabled() ? System.currentTimeMillis() : 0;

            final String encodedStaticState = xformsDecodedClientState.getXFormsState().getStaticState();
            newEncodedStaticState = XFormsUtils.ensureEncrypted(pipelineContext, encodedStaticState);

            if (logger.isDebugEnabled()) {
                final long elapsedTime = System.currentTimeMillis() - startTime;
                logger.debug("Time to encode static state: " + elapsedTime);
            }
        }

        // Create encoded dynamic state and make sure encryption is used
        final String newEncodedDynamicState;
        {
            final long startTime = logger.isDebugEnabled() ? System.currentTimeMillis() : 0;
            newEncodedDynamicState = containingDocument.createEncodedDynamicState(pipelineContext, true);
            if (logger.isDebugEnabled()) {
                final long elapsedTime = System.currentTimeMillis() - startTime;
                logger.debug("Time to encode dynamic state: " + elapsedTime);
            }
        }

        return new XFormsState(newEncodedStaticState, newEncodedDynamicState);
    }


    /**
     * Get the encoded XForms state as it must be sent to the client within an Ajax response.
     *
     * @param containingDocument        containing document
     * @param pipelineContext           pipeline context
     * @param xformsDecodedClientState  decoded state as received in Ajax request
     * @param isAllEvents               whether this is a special "all events" request
     * @return                          XFormsState containing the encoded static and dynamic states
     */
    public static XFormsState getEncodedClientStateDoCache(XFormsContainingDocument containingDocument, PipelineContext pipelineContext,
                                                    XFormsDecodedClientState xformsDecodedClientState, boolean isAllEvents) {

        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

        // Compute static state and dynamic state
        final String staticStateString;
        final String dynamicStateString;
        {
            // Whether the incoming state handling mode is different from the outgoing state handling mode
            final boolean isMustChangeStateHandling
                    = xformsDecodedClientState.isClientStateHandling() != XFormsProperties.isClientStateHandling(containingDocument);

            final XFormsState newXFormsState;
            if (containingDocument.isDirtySinceLastRequest() || isMustChangeStateHandling) {
                if (containingDocument.isDirtySinceLastRequest()) {
                    // The document is dirty
                    logger.debug("Document is dirty: generate new dynamic state.");
                } else {
                    // Changing modes
                    logger.debug("Changing state handling mode: generate new dynamic state.");
                }

                // Produce page generation id if needed
                final String currentPageGenerationId = (xformsDecodedClientState.getStaticStateUUID() != null) ? xformsDecodedClientState.getStaticStateUUID() : UUIDUtils.createPseudoUUID();

                // Get encoded static state
                staticStateString = xformsDecodedClientState.getIncomingStaticStateEncoded(containingDocument, currentPageGenerationId);

                // Create and encode dynamic state (encoded static state is reused)
                final String newEncodedDynamicState = containingDocument.createEncodedDynamicState(pipelineContext, false);
                newXFormsState = new XFormsState(xformsDecodedClientState.getXFormsState().getStaticState(), newEncodedDynamicState);

                if (!XFormsProperties.isClientStateHandling(containingDocument)) {
                    final String requestId = xformsDecodedClientState.getDynamicStateUUID(); // may be null when switching modes

                    // Get session id if needed
                    final ExternalContext.Session session = externalContext.getSession(FORCE_SESSION_CREATION);
                    final String sessionId = (session != null) ? session.getId() : null;

                    // Produce dynamic state key (keep the same when allEvents!)
                    final String newRequestId = isAllEvents ? requestId : UUIDUtils.createPseudoUUID();

                    if (XFormsProperties.isLegacySessionStateHandling(containingDocument)) {
                        // Legacy session server handling
                        final XFormsStateStore stateStore = XFormsSessionStateStore.instance(externalContext, true);
                        stateStore.add(currentPageGenerationId, requestId, newRequestId, newXFormsState, sessionId, false);
                        dynamicStateString = SESSION_STATE_PREFIX + newRequestId;
                    } else {
                        // New server state handling with persistent store
                        final XFormsStateStore stateStore = XFormsPersistentApplicationStateStore.instance(externalContext);
                        stateStore.add(currentPageGenerationId, requestId, newRequestId, newXFormsState, sessionId, false);
                        dynamicStateString = PERSISTENT_STATE_PREFIX + newRequestId;
                    }
                } else {
                    // Send state directly to the client
                    dynamicStateString = newEncodedDynamicState;
                }
            } else {
                // The document is not dirty AND we are not changing mode: no real encoding takes place here
                logger.debug("Document is not dirty: keep existing dynamic state.");
                newXFormsState = xformsDecodedClientState.getXFormsState();

                staticStateString = xformsDecodedClientState.getIncomingStaticStateEncoded(containingDocument);
                dynamicStateString = xformsDecodedClientState.getIncomingDynamicStateEncoded(containingDocument);

                if (logger.isDebugEnabled() && !XFormsProperties.isClientStateHandling(containingDocument)) {
                    // Check that dynamic state is the same
                    // TODO: Need XML-aware comparison here
//                    final String newEncodedDynamicState = containingDocument.createEncodedDynamicState(pipelineContext);
//                    if (!newEncodedDynamicState.equals(xformsDecodedClientState.getXFormsState().getDynamicState())) {
//
//                        final Document oldDocument = XFormsUtils.decodeXML(pipelineContext, xformsDecodedClientState.getXFormsState().getDynamicState());
//                        final Document newDocument = XFormsUtils.decodeXML(pipelineContext, newEncodedDynamicState);
//
//                        logger.debug("Old document:\n" + Dom4jUtils.domToString(oldDocument));
//                        logger.debug("New document:\n" + Dom4jUtils.domToString(newDocument));
//
//                        throw new OXFException("Document is not dirty but dynamic state turns out to be different from original.");
//                    }
                }
            }

            // Cache document if requested and possible
            if (XFormsProperties.isCacheDocument()) {
                XFormsDocumentCache.instance().add(pipelineContext, newXFormsState, containingDocument);
            }
        }

        return new XFormsState(staticStateString, dynamicStateString);
    }

    /**
     * Represent a decoded client state, i.e. a decoded XFormsState with some information extracted from the Ajax
     * request.
     */
    public static class XFormsDecodedClientState {

        private XFormsState xformsState;
        private String staticStateUUID;
        private String dynamicStateUUID;

        public XFormsDecodedClientState(XFormsState xformsState, String staticStateUUID, String dynamicStateUUID) {
            this.xformsState = xformsState;
            this.staticStateUUID = staticStateUUID;
            this.dynamicStateUUID = dynamicStateUUID;
        }

        public XFormsState getXFormsState() {
            return xformsState;
        }

        public String getStaticStateUUID() {
            return staticStateUUID;
        }

        public String getDynamicStateUUID() {
            return dynamicStateUUID;
        }

        public boolean isClientStateHandling() {
            // Request was in client state handling if we don't have an incoming UUID for the dynamic state
            return dynamicStateUUID == null;
        }

        public String getIncomingStaticStateEncoded(XFormsContainingDocument containingDocument) {
            return getIncomingStaticStateEncoded(containingDocument, getStaticStateUUID());
        }

        public String getIncomingStaticStateEncoded(XFormsContainingDocument containingDocument, String newStaticStateUUID) {
            if (!XFormsProperties.isClientStateHandling(containingDocument)) {
                if (newStaticStateUUID == null) {
                    final String message = "Null value for newStaticStateUUID";
                    logger.debug(message);
                    throw new OXFException(message);
                }
                if (XFormsProperties.isLegacySessionStateHandling(containingDocument)) {
                    return SESSION_STATE_PREFIX + newStaticStateUUID;
                } else {
                    return PERSISTENT_STATE_PREFIX + newStaticStateUUID;
                }
            } else {
                return getXFormsState().getStaticState();
            }
        }

        public String getIncomingDynamicStateEncoded(XFormsContainingDocument containingDocument) {
            return getIncomingDynamicStateEncoded(containingDocument, getDynamicStateUUID());
        }

        public String getIncomingDynamicStateEncoded(XFormsContainingDocument containingDocument, String newDynamicStateUUID) {
            if (!XFormsProperties.isClientStateHandling(containingDocument)) {
                if (newDynamicStateUUID == null) {
                    final String message = "Null value for newDynamicStateUUID";
                    logger.debug(message);
                    throw new OXFException(message);
                }
                if (XFormsProperties.isLegacySessionStateHandling(containingDocument)) {
                    return SESSION_STATE_PREFIX + newDynamicStateUUID;
                } else {
                    return PERSISTENT_STATE_PREFIX + newDynamicStateUUID;
                }
            } else {
                return getXFormsState().getDynamicState();
            }

        }
    }
}

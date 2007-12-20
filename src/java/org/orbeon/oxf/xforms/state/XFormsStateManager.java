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
import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.UUIDUtils;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

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
                        stateStore.add(currentPageGenerationId, null, newRequestId, xformsState, null, false);
                        dynamicStateString = SESSION_STATE_PREFIX + newRequestId;
                    } else {
                        // In this case, we first store in the application scope, so that multiple requests can use the
                        // same cached state.
                        dynamicStateString = APPLICATION_STATE_PREFIX + dynamicStateUUID;
                        final XFormsStateStore applicationStateStore = XFormsApplicationStateStore.instance(externalContext);
                        applicationStateStore.add(currentPageGenerationId, null, dynamicStateUUID, xformsState, null, false);
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
                        stateStore.add(currentPageGenerationId, null, newRequestId, xformsState, sessionId, false);
                        dynamicStateString = PERSISTENT_STATE_PREFIX + newRequestId;
                    } else {
                        dynamicStateString = PERSISTENT_STATE_PREFIX + dynamicStateUUID;
                        stateStore.add(currentPageGenerationId, null, dynamicStateUUID, xformsState, sessionId, false);
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
     * Get the encoded XForms state as it must be sent to the client within an Ajax response.
     *
     * @param containingDocument        containing document
     * @param pipelineContext           pipeline context
     * @param xformsDecodedClientState  decoded state as received in Ajax request
     * @param isAllEvents               whether this is a special "all events" request
     * @param pinNewDynamicState        whether to "pin" the new dynamic state
     * @return                          XFormsState containing the encoded static and dynamic states
     */
    public static XFormsState getEncodedClientStateDoCache(XFormsContainingDocument containingDocument, PipelineContext pipelineContext,
                                                    XFormsDecodedClientState xformsDecodedClientState, boolean isAllEvents, boolean pinNewDynamicState) {

        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

        // Output static state (FOR TESTING ONLY)
        final String staticStateString = xformsDecodedClientState.getXFormsState().getStaticState();

        // Output dynamic state
        final String dynamicStateString;
        {
            final XFormsState newXFormsState;
            if (containingDocument.isDirtySinceLastRequest()) {
                // The document is dirty
                logger.debug("Document is dirty: generate new dynamic state.");

                // Produce page generation id if needed
                final String currentPageGenerationId = (xformsDecodedClientState.getStaticStateUUID() != null) ? xformsDecodedClientState.getStaticStateUUID() : UUIDUtils.createPseudoUUID();

                // Create and encode dynamic state
                final String newEncodedDynamicState = containingDocument.createEncodedDynamicState(pipelineContext);
                newXFormsState = new XFormsState(staticStateString, newEncodedDynamicState);

                if (!XFormsProperties.isClientStateHandling(containingDocument)) {
                    final String requestId = xformsDecodedClientState.getDynamicStateUUID();

                    // Get session id if needed
                    final ExternalContext.Session session = externalContext.getSession(FORCE_SESSION_CREATION);
                    final String sessionId = (session != null) ? session.getId() : null;

                    // Produce dynamic state key (keep the same when allEvents!)
                    final String newRequestId = isAllEvents ? requestId : UUIDUtils.createPseudoUUID();

                    if (XFormsProperties.isLegacySessionStateHandling(containingDocument)) {
                        // Legacy session server handling
                        final XFormsStateStore stateStore = XFormsSessionStateStore.instance(externalContext, true);
                        stateStore.add(currentPageGenerationId, requestId, newRequestId, newXFormsState, sessionId, pinNewDynamicState);
                        dynamicStateString = SESSION_STATE_PREFIX + newRequestId;
                    } else {
                        // New server state handling with persistent store
                        final XFormsStateStore stateStore = XFormsPersistentApplicationStateStore.instance(externalContext);
                        stateStore.add(currentPageGenerationId, requestId, newRequestId, newXFormsState, sessionId, pinNewDynamicState);
                        dynamicStateString = PERSISTENT_STATE_PREFIX + newRequestId;
                    }
                } else {
                    // Send state directly to the client
                    dynamicStateString = newEncodedDynamicState;
                }
            } else {
                // The document is not dirty
                logger.debug("Document is not dirty: keep existing dynamic state.");
                newXFormsState = xformsDecodedClientState.getXFormsState();

                if (!XFormsProperties.isClientStateHandling(containingDocument)) {
                    if (XFormsProperties.isLegacySessionStateHandling(containingDocument)) {
                        dynamicStateString = SESSION_STATE_PREFIX + xformsDecodedClientState.getDynamicStateUUID();
                    } else {
                        dynamicStateString = PERSISTENT_STATE_PREFIX + xformsDecodedClientState.getDynamicStateUUID();
                    }
                } else {
                    dynamicStateString = xformsDecodedClientState.getXFormsState().getDynamicState();
                }

                if (logger.isDebugEnabled() && !XFormsProperties.isClientStateHandling(containingDocument)) {
                    // Check that dynamic state is the same
                    final String newEncodedDynamicState = containingDocument.createEncodedDynamicState(pipelineContext);
                    if (!newEncodedDynamicState.equals(xformsDecodedClientState.getXFormsState().getDynamicState())) {

                        final Document oldDocument = XFormsUtils.decodeXML(pipelineContext, xformsDecodedClientState.getXFormsState().getDynamicState());
                        final Document newDocument = XFormsUtils.decodeXML(pipelineContext, newEncodedDynamicState);

                        logger.debug("Old document:\n" + Dom4jUtils.domToString(oldDocument));
                        logger.debug("New document:\n" + Dom4jUtils.domToString(newDocument));

                        throw new OXFException("Document is not dirty but dynamic state turns out to be different from original.");
                    }
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
    }
}

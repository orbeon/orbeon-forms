/**
 *  Copyright (C) 20067 Orbeon, Inc.
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

import org.orbeon.oxf.util.UUIDUtils;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.common.OXFException;

/**
 * Centralize XForms state management.
 */
public class XFormsStateManager {

    // All these must have the same length
    public static final String SESSION_STATE_PREFIX = "sess:";
    public static final String APPLICATION_STATE_PREFIX = "appl:";
    public static final String PERSISTENT_STATE_PREFIX = "pers:";

    private static final int PREFIX_COLON_POSITION = SESSION_STATE_PREFIX.length() - 1;

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
            if (containingDocument.isServerStateHandling()) {
                if (containingDocument.isLegacyServerStateHandling()) {
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
            if (containingDocument.isServerStateHandling()) {
                if (containingDocument.isLegacyServerStateHandling()) {
                    // Legacy session server handling

                    if (dynamicStateUUID == null) {
                        // In this case, we use session scope since not much sharing will occur, if at all.

                        // Produce dynamic state key
                        final String newRequestId = UUIDUtils.createPseudoUUID();
                        final XFormsStateStore stateStore = XFormsSessionStateStore.instance(externalContext, true);
                        stateStore.add(currentPageGenerationId, null, newRequestId, xformsState);
                        dynamicStateString = SESSION_STATE_PREFIX + newRequestId;
                    } else {
                        // In this case, we first store in the application scope, so that multiple requests can use the
                        // same cached state.
                        dynamicStateString = APPLICATION_STATE_PREFIX + dynamicStateUUID;
                        final XFormsStateStore applicationStateStore = XFormsApplicationStateStore.instance(externalContext);
                        applicationStateStore.add(currentPageGenerationId, null, dynamicStateUUID, xformsState);
                    }
                } else {
                    // New server state handling with persistent store

                    final XFormsStateStore stateStore = XFormsPersistentApplicationStateStore.instance(externalContext);
                    if (dynamicStateUUID == null) {
                        // In this case, we use session scope since not much sharing will occur, if at all.

                        // Produce dynamic state key
                        final String newRequestId = UUIDUtils.createPseudoUUID();
                        stateStore.add(currentPageGenerationId, null, newRequestId, xformsState);
                        dynamicStateString = PERSISTENT_STATE_PREFIX + newRequestId;
                    } else {
                        // In this case, we first store in the application scope, so that multiple requests can use the
                        // same cached state.
                        dynamicStateString = PERSISTENT_STATE_PREFIX + dynamicStateUUID;
                        stateStore.add(currentPageGenerationId, null, dynamicStateUUID, xformsState);
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
                throw new OXFException("Inconsistent XForms state prefixes: " + staticStatePrefix + ", " + dynamicStatePrefix);
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
                throw new OXFException("Invalid state prefix: " + staticStatePrefix);
            }

            // Get state from store
            final XFormsState xformsState = (stateStore == null) ? null : stateStore.find(staticStateUUID, dynamicStateUUID);

            // This is not going to be good when it happens, and we must create a caching heuristic that minimizes this
            if (xformsState == null)
                throw new OXFException("Unable to retrieve XForms engine state.");

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
     * @return                          XFormsState containing the encoded static and dynamic states
     */
    public static XFormsState getEncodedClientStateDoCache(XFormsContainingDocument containingDocument, PipelineContext pipelineContext,
                                                    XFormsDecodedClientState xformsDecodedClientState, boolean isAllEvents) {

        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

        // Output static state (FOR TESTING ONLY)
        final String staticStateString = xformsDecodedClientState.getXFormsState().getStaticState();

        // Output dynamic state
        final String dynamicStateString;
        {
            // Produce page generation id if needed
            final String currentPageGenerationId = (xformsDecodedClientState.getStaticStateUUID() != null) ? xformsDecodedClientState.getStaticStateUUID() : UUIDUtils.createPseudoUUID();

            // Create and encode dynamic state
            final String newEncodedDynamicState = containingDocument.createEncodedDynamicState(pipelineContext);
            final XFormsState newXFormsState = new XFormsState(staticStateString, newEncodedDynamicState);

            if (containingDocument.isServerStateHandling()) {
                final String requestId = xformsDecodedClientState.getDynamicStateUUID();
                // Produce dynamic state key (keep the same when allEvents!)
                final String newRequestId = isAllEvents ? requestId : UUIDUtils.createPseudoUUID();
                if (containingDocument.isLegacyServerStateHandling()) {
                    // Legacy session server handling
                    final XFormsStateStore stateStore = XFormsSessionStateStore.instance(externalContext, true);
                    stateStore.add(currentPageGenerationId, requestId, newRequestId, newXFormsState);
                    dynamicStateString = SESSION_STATE_PREFIX + newRequestId;
                } else {
                    // New server state handling with persistent store
                    final XFormsStateStore stateStore = XFormsPersistentApplicationStateStore.instance(externalContext);
                    stateStore.add(currentPageGenerationId, requestId, newRequestId, newXFormsState);
                    dynamicStateString = PERSISTENT_STATE_PREFIX + newRequestId;
                }
            } else {
                // Send state directly to the client
                dynamicStateString = newEncodedDynamicState;
            }

            // Cache document if requested and possible
            if (XFormsUtils.isCacheDocument()) {
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

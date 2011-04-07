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
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.junit.Test;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.processor.test.TestExternalContext;
import org.orbeon.oxf.test.ResourceManagerTestBase;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.UUIDUtils;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.analysis.XFormsStaticStateTest;
import org.orbeon.oxf.xforms.event.ClientEvents;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.events.XXFormsValueChangeWithFocusChangeEvent;

import java.util.Collections;
import java.util.List;

import static junit.framework.Assert.*;

public class XFormsStateManagerTest extends ResourceManagerTestBase {

    private XFormsStateLifecycle stateManager = XFormsStateManager.instance();

    @Test
    public void testClientNoCache() {
        testClient(false, "client-nocache.xhtml");
    }

    @Test
    public void testClientDoCache() {
        testClient(true, "client-cache.xhtml");
    }

    @Test
    public void testServerNoCache() {
        testServer(false, "server-nocache.xhtml");
    }

    @Test
    public void testServerDoCache() {
        testServer(true, "server-cache.xhtml");
    }

    @Test
    public void testDocumentCacheSessionListener() {

        // Create document
        final ExternalContext.Session session = NetUtils.getSession(true);
        final XFormsStaticState staticState = XFormsStaticStateTest.getStaticState("oxf:/org/orbeon/oxf/xforms/state/server-cache.xhtml");
        final XFormsContainingDocument document = new XFormsContainingDocument(staticState, null, null, null);

        stateManager.afterInitialResponse(document);

        // Check there is a state manager session listener for this document
        assertNotNull(session.getAttributesMap().get(XFormsStateManager.getListenerSessionKey(document.getUUID())));

        // Test that the document is in cache
        assertSame(document, XFormsDocumentCache.instance().takeDocument(document.getUUID()));

        // Expire session
        ((TestExternalContext.TestSession) session).expireSession();

        // Test that the document is no longer in cache
        assertNull(XFormsDocumentCache.instance().takeDocument(document.getUUID()));
    }

    @Test(expected=OXFException.class)
    public void testClientStaticStateEncrypted() {
        final XFormsState state = createDocumentGetState();

        // This should throw an exception
        XFormsUtils.decodeXML(state.getStaticState(), "WrongPassword");
    }

    @Test(expected=OXFException.class)
    public void testClientDynamicStateEncrypted() {
        final XFormsState state = createDocumentGetState();

        // This should throw an exception
        XFormsUtils.decodeXML(state.getDynamicState(), "WrongPassword");
    }

    private XFormsState createDocumentGetState() {
        final XFormsStaticState staticState = XFormsStaticStateTest.getStaticState("oxf:/org/orbeon/oxf/xforms/state/client-nocache.xhtml");
        final XFormsContainingDocument document = new XFormsContainingDocument(staticState, null, null, null);

        final String staticStateString = stateManager.getClientEncodedDynamicState(document);
        final String dynamicStateString = stateManager.getClientEncodedDynamicState(document);

        // X2 is the prefix saying it's encrypted
        assertTrue(staticStateString.startsWith("X2"));
        assertTrue(dynamicStateString.startsWith("X2"));


        return new XFormsState(staticStateString, dynamicStateString);
    }

    private static class State {
        public XFormsContainingDocument document;
        public String uuid;
        public String staticStateString;
        public String dynamicStateString;
    }

    private interface EventCallback {
        List<XFormsEvent> createEvents(XFormsContainingDocument document);
    }

    private void testClient(boolean isCache, String formFile) {

        final XFormsStaticState staticState = XFormsStaticStateTest.getStaticState("oxf:/org/orbeon/oxf/xforms/state/" + formFile);

        // Initialize document and get initial document state
        final State state1 = new State();
        {
            NetUtils.getSession(true); // make sure a session is in place as it is used by the state manager
            state1.document = new XFormsContainingDocument(staticState, null, null, null);

            state1.uuid = state1.document.getUUID();
            state1.staticStateString = stateManager.getClientEncodedStaticState(state1.document);
            state1.dynamicStateString = stateManager.getClientEncodedDynamicState(state1.document);

            assertEquals(state1.uuid.length(), UUIDUtils.UUID_LENGTH);
            assertNotNull(StringUtils.trimToNull(state1.staticStateString));
            assertNotNull(StringUtils.trimToNull(state1.dynamicStateString));

            // Initial response sent
            state1.document.afterInitialResponse();
            stateManager.afterInitialResponse(state1.document);
        }

        assertEquals(1, getSequenceNumber(state1.dynamicStateString));

        // Run update
        final State state2 = doUpdate(isCache, state1, new EventCallback() {
            public List<XFormsEvent> createEvents(XFormsContainingDocument document) {
                final XFormsEventTarget input = (XFormsEventTarget) document.getObjectByEffectiveId("my-input");
                return Collections.<XFormsEvent>singletonList(new XXFormsValueChangeWithFocusChangeEvent(document, input, null, "gaga"));
            }
        });

        // UUID and static state can't change
        assertEquals(state1.uuid, state2.uuid);
        assertEquals(state1.staticStateString, state2.staticStateString);
        // Sequence must be updated
        assertEquals(2, getSequenceNumber(state2.dynamicStateString));
        // Dynamic state must have changed
        assertFalse(stripSequenceNumber(state1.dynamicStateString).equals(stripSequenceNumber(state2.dynamicStateString)));

        // Run update
        final State state3 = doUpdate(isCache, state2, null);

        // UUID and static state can't change
        assertEquals(state1.uuid, state3.uuid);
        assertEquals(state1.staticStateString, state3.staticStateString);
        // Sequence must be updated
        assertEquals(3, getSequenceNumber(state3.dynamicStateString));
        // Dynamic state must NOT have changed because no event was dispatched
        assertEquals(stripSequenceNumber(state2.dynamicStateString), stripSequenceNumber(state3.dynamicStateString));

        // Get back to initial state
        final State state4 = getInitialState(state1);

        // UUID and static state can't change
        assertEquals(state1.uuid, state4.uuid);
        assertEquals(state1.staticStateString, state4.staticStateString);
        // Sequence must be updated
        assertEquals(1, getSequenceNumber(state4.dynamicStateString));
        // Make sure we found the initial dynamic state
        assertEquals(stripSequenceNumber(state1.dynamicStateString), stripSequenceNumber(state4.dynamicStateString));
    }

    private void testServer(boolean isCache, String formFile) {

        final XFormsStaticState staticState = XFormsStaticStateTest.getStaticState("oxf:/org/orbeon/oxf/xforms/state/" + formFile);

        // Initialize document and get initial document state
        final ExternalContext.Session session;
        final String initialDynamicStateString;
        final State state1 = new State();
        {
            session = NetUtils.getSession(true);
            state1.document = new XFormsContainingDocument(staticState, null, null, null);

            state1.uuid = state1.document.getUUID();
            state1.staticStateString = stateManager.getClientEncodedStaticState(state1.document);
            state1.dynamicStateString = stateManager.getClientEncodedDynamicState(state1.document);

            assertEquals(state1.uuid.length(), UUIDUtils.UUID_LENGTH);
            assertNull(StringUtils.trimToNull(state1.staticStateString));
            assertNull(StringUtils.trimToNull(state1.dynamicStateString));

            // Initial response sent
            state1.document.afterInitialResponse();
            stateManager.afterInitialResponse(state1.document);

            initialDynamicStateString = state1.document.createEncodedDynamicState(XFormsProperties.isGZIPState(), false);
        }

        assertEquals(1, state1.document.getSequence());

        // Run update
        final State state2 = doUpdate(isCache, state1, new EventCallback() {
            public List<XFormsEvent> createEvents(XFormsContainingDocument document) {
                final XFormsEventTarget input = (XFormsEventTarget) document.getObjectByEffectiveId("my-input");
                return Collections.<XFormsEvent>singletonList(new XXFormsValueChangeWithFocusChangeEvent(document, input, null, "gaga"));
            }
        });

        // UUID can't change
        assertEquals(state1.uuid, state2.uuid);
        // Sequence must be updated
        assertEquals(2, state2.document.getSequence());
        // State strings must be null
        assertNull(StringUtils.trimToNull(state2.staticStateString));
        assertNull(StringUtils.trimToNull(state2.dynamicStateString));

        // Run update
        final State state3 = doUpdate(isCache, state2, null);

        // UUID can't change
        assertEquals(state1.uuid, state3.uuid);
        // Sequence must be updated
        assertEquals(3, state3.document.getSequence());
        // State strings must be null
        assertNull(StringUtils.trimToNull(state3.staticStateString));
        assertNull(StringUtils.trimToNull(state3.dynamicStateString));

        // Get back to initial state
        final State state4 = getInitialState(state1);

        // UUID can't change
        assertEquals(state1.uuid, state4.uuid);
        // Sequence must be updated
        assertEquals(1, state4.document.getSequence());
        // State strings must be null
        assertNull(StringUtils.trimToNull(state4.staticStateString));
        assertNull(StringUtils.trimToNull(state4.dynamicStateString));
        // Make sure we found the initial dynamic state
        {
            assertEquals(stripSequenceNumber(initialDynamicStateString), stripSequenceNumber(state4.document.createEncodedDynamicState(XFormsProperties.isGZIPState(), false)));
        }
    }

    private State doUpdate(boolean isCache, final State state1, EventCallback callback) {

        final XFormsStateLifecycle.RequestParameters parameters = new XFormsStateLifecycle.RequestParameters() {
            public String getUUID() {
                return state1.uuid;
            }

            public String getEncodedClientStaticState() {
                return state1.staticStateString;
            }

            public String getEncodedClientDynamicState() {
                return state1.dynamicStateString;
            }
        };

        // New state
        final State state2 = new State();

        state2.document = stateManager.beforeUpdate(parameters);

        if (isCache)
            assertSame(state1.document, state2.document);// must be the same because cache is enabled and cache has room
        else
            assertNotSame(state1.document, state2.document);// can't be the same because cache is disabled

        // Run events if any
        state2.document.beforeExternalEvents(null);
        if (callback != null) {
            for (final XFormsEvent event: callback.createEvents(state2.document)) {
                ClientEvents.processEvent(state2.document, event);
            }
        }
        state2.document.afterExternalEvents();

        stateManager.beforeUpdateResponse(state2.document, false);

        // New state
        state2.uuid = state2.document.getUUID();
        state2.staticStateString = stateManager.getClientEncodedStaticState(state2.document);
        state2.dynamicStateString = stateManager.getClientEncodedDynamicState(state2.document);

        stateManager.afterUpdateResponse(state2.document);

        stateManager.afterUpdate(state2.document, true);

        return state2;
    }

    private State getInitialState(final State state1) {

        final XFormsStateLifecycle.RequestParameters parameters = new XFormsStateLifecycle.RequestParameters() {
            public String getUUID() {
                return state1.uuid;
            }

            public String getEncodedClientStaticState() {
                return state1.staticStateString;
            }

            public String getEncodedClientDynamicState() {
                return state1.dynamicStateString;
            }
        };

        // New state
        final State state2 = new State();

        // Find document
        state2.document = XFormsStateManager.instance().findOrRestoreDocument(parameters, true);

        assertNotSame(state1.document, state2.document);// can't be the same because either cache is disabled OR we create a duplicate document (could be same if state1 is initial state)

        // New state
        state2.uuid = state2.document.getUUID();
        state2.staticStateString = stateManager.getClientEncodedStaticState(state2.document);
        state2.dynamicStateString = stateManager.getClientEncodedDynamicState(state2.document);

        return state2;
    }

    private String stripSequenceNumber(String serializedState) {
        final Document state = XFormsUtils.decodeXML(serializedState);
        final Attribute sequenceNumber = state.getRootElement().attribute("sequence");
        if (sequenceNumber != null)
            state.getRootElement().remove(sequenceNumber);
        return XFormsUtils.encodeXML(state, false);
    }

    private long getSequenceNumber(String serializedState) {
        final Document state = XFormsUtils.decodeXML(serializedState);
        final Attribute sequenceNumber = state.getRootElement().attribute("sequence");

        return Long.parseLong(sequenceNumber.getStringValue());
    }
}

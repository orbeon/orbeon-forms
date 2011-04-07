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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.pipeline.api.ExternalContext.Session;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.test.TestExternalContext;
import org.orbeon.oxf.test.ResourceManagerTestBase;
import org.orbeon.oxf.util.Base64;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.XFormsProperties;

import java.util.*;

import static junit.framework.Assert.*;

public class EXistStateStoreTest extends ResourceManagerTestBase {

    private PipelineContext pipelineContext;
	private ExtendedTestExternalContext externalContext;

    private EXistStateStoreBase stateStore;

	private static Map<String, TestExternalContext.TestSession> sessionMap = new HashMap<String, TestExternalContext.TestSession>();

    @Before
    public void beforeTest() {

        externalContext = (ExtendedTestExternalContext) NetUtils.getExternalContext();

        stateStore = EXistStateStore.instance(externalContext);

        stateStore.clear();
        sessionMap.clear();
    }

    @After
    public void afterTest() {
        stateStore = null;
        externalContext = null;
        pipelineContext = null;
        for (final TestExternalContext.TestSession session: sessionMap.values()) {
            session.expireSession();
		}
		sessionMap.clear();
    }

    @Test
	public void testSimpleAddAndFind() {

        externalContext.setSession(findOrCreateSession("session-id-1"));

		final XFormsState xformsState = new XFormsState("staticState", "dynamicState");
		stateStore.addStateCombined("ss-uuid", "ds-uuid-1", xformsState, "session-id-1");
		final XFormsState retreivedState = stateStore.findStateCombined("ss-uuid", "ds-uuid-1");
		assertNotNull("State not found", retreivedState);
		assertEquals("Static state incorrect", xformsState.getStaticState(), retreivedState.getStaticState());
		assertEquals("Dynamic state incorrect", xformsState.getDynamicState(), retreivedState.getDynamicState());
	}

    @Test
	public void testFindNoneExistent() {
		assertNull("Returned state for invalid request id", stateStore.findStateCombined("doesnot-exist", "doesnot-exist"));
	}

    @Test
	public void testValueReplacement() {

        externalContext.setSession(findOrCreateSession("session-id-1"));

		final XFormsState originalState = new XFormsState("staticState", "dynamicState");
		stateStore.addStateCombined("ss-uuid", "ds-uuid", originalState, "session-id-1");
		final XFormsState modifiedState = new XFormsState("staticState" + "Modified", "dynamicState" + "Modified");
		stateStore.addStateCombined("ss-uuid", "ds-uuid", modifiedState, "session-id-1");

		final XFormsState retreivedState = stateStore.findStateCombined("ss-uuid", "ds-uuid");
		assertNotNull("State not found", retreivedState);
		assertFalse("Static state incorrect", originalState.getStaticState().equals(retreivedState.getStaticState()));
		assertFalse("Dynamic state incorrect", originalState.getDynamicState().equals(retreivedState .getDynamicState()));
	}

    @Test
	public void testForLargeSizedState() {
		final XFormsState xformsState = new XFormsState(
				generateRandomString(100 * 1024),
				generateRandomString(100 * 1024));

        externalContext.setSession(findOrCreateSession("session-id-1"));
		stateStore.addStateCombined("ss-uuid", "ds-uuid-1", xformsState, "session-id-1");

		final XFormsState retrievedState = stateStore.findStateCombined("ss-uuid", "ds-uuid-1");
		assertNotNull("State not found", retrievedState);
		assertEquals("Static state incorrect", xformsState.getStaticState(), retrievedState.getStaticState());
		assertEquals("Dynamic state incorrect", xformsState.getDynamicState(), retrievedState.getDynamicState());
	}

    @Test
	public void testForLargeNumberOfEntries() {
		final int NUMBER_OF_PAGES = 10;
		final int NUMBER_OF_REQUESTS = 25;

        externalContext.setSession(findOrCreateSession("session-id-1"));

		XFormsState xformStates[] = new XFormsState[NUMBER_OF_PAGES];
		for (int i = 0; i < NUMBER_OF_PAGES; i++) {
			xformStates[i] = new XFormsState("staticState" + i, "dynamicState-0-" + i);
			stateStore.addStateCombined("pgid" + i, "requestid0", xformStates[i], "session-id-1");
			for (int j = 1; j <= NUMBER_OF_REQUESTS; j++) {
				xformStates[i] = new XFormsState(xformStates[i].getStaticState(), "dynamicState-" + j + "-" + i);
				stateStore.addStateCombined("pgid" + i, "requestid" + i
						+ "-" + j, xformStates[i], "session-id-1");

			}
		}
		for (int i = 0; i < NUMBER_OF_PAGES; i++) {
			verifyStateExists("pgid" + i, "requestid" + i + "-"
					+ NUMBER_OF_REQUESTS, xformStates[i]);
		}
	}

    
    // TODO: temp commented out as this fails on the build machine
//  @Test
//	public void testLargeNumberOfSessions() {
//		final int NUMBER_OF_SESSIONS = 10;
//		final String pageIdPrefix = generateRandomString(25);
//		final String requestIdPrefix = generateRandomString(25);
//		final String sessionIdPrefix = generateRandomString(25);
//
//		final XFormsState xformStates[] = new XFormsState[NUMBER_OF_SESSIONS];
//		for (int i = 0; i < NUMBER_OF_SESSIONS; i++) {
//			xformStates[i] = new XFormsState(generateRandomString(256), generateRandomString(128));
//			externalContext.setSession(findOrCreateSession(sessionIdPrefix + i));
//			fixture.add(pageIdPrefix + i, null, requestIdPrefix + i,
//					xformStates[i], sessionIdPrefix + i, false);
//		}
//		for (int i = 0; i < NUMBER_OF_SESSIONS; i++) {
//			verifyStateExists(pageIdPrefix + i, requestIdPrefix + i,
//					xformStates[i]);
//		}
//		expireAllSessions();
//		for (int i = 0; i < NUMBER_OF_SESSIONS; i++) {
//			assertNull(fixture.find(pageIdPrefix + i, sessionIdPrefix + i));
//		}
//	}

	private void verifyStateExists(String pageGenerationId, String requestId, XFormsState expectedState) {
		final XFormsState retreivedState = stateStore.findStateCombined(pageGenerationId, requestId);
		assertNotNull("State not found", retreivedState);
		assertEquals("Static state incorrect", expectedState.getStaticState(), retreivedState.getStaticState());
		assertEquals("Dynamic state incorrect", expectedState.getDynamicState(), retreivedState.getDynamicState());
	}

    @Test
	public void testForLargeSizedStatePersistedToDB() {

        externalContext.setSession(findOrCreateSession("session-id-1"));

		final XFormsState xformsState = new XFormsState(
				generateRandomString(100 * 1024),
				generateRandomString(100 * 1024));
		stateStore.addStateCombined("ss-uuid", "ds-uuid-1", xformsState, "session-id-1");
		final XFormsState retreivedState = stateStore.findStateCombined("ss-uuid", "ds-uuid-1");
		fillUpInMemoryCache();
		assertNotNull("State not found", retreivedState);
		assertEquals("Static state incorrect", xformsState.getStaticState(), retreivedState.getStaticState());
		assertEquals("Dynamic state incorrect", xformsState.getDynamicState(), retreivedState.getDynamicState());
	}

    @Test
	public void testAllEntriesFound() {

        externalContext.setSession(findOrCreateSession("session-id-1"));

        // NOTE: This test used to test that the "previous previous" dynamic state was cleared. The store now no longer
        // clears state, so instead we just test that all the entries are kept in the store.
		XFormsState xformsState = new XFormsState("staticState", "dynamicState");
        stateStore.addStateCombined("ss-uuid", "ds-uuid-0", xformsState, "session-id-1");
        stateStore.addStateCombined("ss-uuid", "ds-uuid-1", xformsState, "session-id-1");
		stateStore.addStateCombined("ss-uuid", "ds-uuid-2", xformsState, "session-id-1");
        stateStore.addStateCombined("ss-uuid", "ds-uuid-3", xformsState, "session-id-1");

        assertNotNull("Initial request data removed", stateStore.findStateCombined("ss-uuid", "ds-uuid-0"));
        assertNotNull("Previous previous request data removed", stateStore.findStateCombined("ss-uuid", "ds-uuid-1"));
        assertNotNull("Previous request data removed", stateStore.findStateCombined("ss-uuid", "ds-uuid-2"));

        stateStore.addStateCombined("ss-uuid", "ds-uuid-4", xformsState, "session-id-1");

        assertNotNull("Initial request data removed", stateStore.findStateCombined("ss-uuid", "ds-uuid-0"));
        assertNotNull("Previous previous request data not removed", stateStore.findStateCombined("ss-uuid", "ds-uuid-2"));
        assertNotNull("Previous request data removed", stateStore.findStateCombined("ss-uuid", "ds-uuid-3"));

    }

    @Test
	public void testInitialEntryFound() {

        externalContext.setSession(findOrCreateSession("session-id-1"));

		final XFormsState xformsState = new XFormsState("staticState", "dynamicState");
		stateStore.addStateCombined("ss-uuid", "ds-uuid-1", xformsState, "session-id-1");
		stateStore.addStateCombined("ss-uuid", "ds-uuid-2", xformsState, "session-id-1");
		assertNotNull("Initial entry should not be cleared", stateStore.findStateCombined("ss-uuid", "ds-uuid-1"));
	}

    @Test
	public void testFindAfterStatePersistedToDB() {

        externalContext.setSession(findOrCreateSession("sessionid"));

		final XFormsState xformsState = new XFormsState("staticState", "dynamicState");
		stateStore.addStateCombined("pgid", "requestid", xformsState, "sessionid");

		fillUpInMemoryCache();

		final XFormsState retrievedState = stateStore.findStateCombined("pgid", "requestid");
		assertNotNull("State not found", retrievedState);
		assertEquals("Static state incorrect", xformsState.getStaticState(), retrievedState.getStaticState());
		assertEquals("Dynamic state incorrect", xformsState.getDynamicState(), retrievedState.getDynamicState());
	}

	private void fillUpInMemoryCache() {
		final int CHUNK_SIZE = 1024;
		for (int i = 0; i < XFormsProperties.getApplicationStateStoreSize() / (CHUNK_SIZE * 2); i++) {


            final String sessionId = "session-id-" + i;
            externalContext.setSession(findOrCreateSession(sessionId));

			stateStore.addStateCombined("ss-uuid-" + i, "ds-uuid-" + i, new XFormsState(
					generateRandomString(CHUNK_SIZE),
					generateRandomString(CHUNK_SIZE)), sessionId);
		}
	}

    @Test
	public void testFindAfterSessionExpiry() {
		final XFormsState xformsState = new XFormsState("staticState", "dynamicState");
		externalContext.setSession(findOrCreateSession("session-id-1"));
		stateStore.addStateCombined("ss-uuid", "ds-uuid-0", xformsState, "session-id-1");
		stateStore.addStateCombined("ss-uuid", "ds-uuid-1", xformsState, "session-id-1");
		expireSession("session-id-1");
		assertNull("Initial State not removed even after session expiry", stateStore.findStateCombined("ss-uuid", "ds-uuid-0"));
		assertNull("State not removed even after session expiry", stateStore.findStateCombined("ss-uuid", "ds-uuid-1"));
	}

    @Test
	public void testSessionExpiryOfPersistedState() {
        
        externalContext.setSession(findOrCreateSession("session-id-1"));

		final XFormsState xformsState = new XFormsState("staticState", "dynamicState");
		externalContext.setSession(findOrCreateSession("session-id-1"));
		stateStore.addStateCombined("ss-uuid", "ds-uuid-0", xformsState, "session-id-1");
		stateStore.addStateCombined("ss-uuid", "ds-uuid-1", xformsState, "session-id-1");
		externalContext.setSession(null);
		// This will cause the previous entries to be pushed to db
		fillUpInMemoryCache();
		expireSession("session-id-1");
		assertNull("Initial State not removed even after session expiry", stateStore.findStateCombined("ss-uuid", "ds-uuid-0"));
		assertNull("State not removed even after session expiry", stateStore.findStateCombined( "ss-uuid", "ds-uuid-1"));
	}

    @Test
	public void testStateSharedAcrossMultipleSessions() {
		final XFormsState xformsState = new XFormsState("staticState", "dynamicState");
		externalContext.setSession(findOrCreateSession("session-id-1"));
		stateStore.addStateCombined("ss-uuid", "ds-uuid-1", xformsState, "session-id-1");
		externalContext.setSession(findOrCreateSession("session-id-2"));
		stateStore.addStateCombined("ss-uuid", "ds-uuid-1", xformsState, "session-id-2");
		expireSession("session-id-1");
		assertNotNull("State removed even though it is refered to by a session",
				stateStore.findStateCombined("ss-uuid", "ds-uuid-1"));
	}

    @Test
	public void testClearingOfStateSharedAcrossMultipleSessions() {
		final XFormsState xformsState = new XFormsState("staticState", "dynamicState");
		externalContext.setSession(findOrCreateSession("session-id-1"));
		stateStore.addStateCombined("ss-uuid", "ds-uuid-1", xformsState, "session-id-1");
		externalContext.setSession(findOrCreateSession("session-id-2"));
		stateStore.addStateCombined("ss-uuid", "ds-uuid-1", xformsState, "session-id-2");
		expireSession("session-id-1");
		expireSession("session-id-2");
		assertNull("State not removed even after expiration of all associated sessions",
				stateStore.findStateCombined("ss-uuid", "ds-uuid-1"));
	}

    @Test
	public void testClearingOfInitialRequestOnSessionExpiration() {
		final XFormsState xformsState = new XFormsState("staticState", "dynamicState");
		externalContext.setSession(findOrCreateSession("session-id-1"));
		stateStore.addStateCombined("ss-uuid", "ds-uuid-1", xformsState, "session-id-1");
		expireSession("session-id-1");
		assertNull("Initial entry state removed after expiration of session",
				stateStore.findStateCombined("ss-uuid", "ds-uuid-1"));
	}

    @Test
	public void testMultiThreadedUsage() throws Throwable {
		final List errors = Collections.synchronizedList(new ArrayList());
		final int NO_OF_THREADS = 5;
		final Thread threads[] = new Thread[NO_OF_THREADS];
		for (int i = 0; i < NO_OF_THREADS; i++) {
			threads[i] = new Thread() {
				public void run() {
					StaticExternalContext
							.setStaticContext(new StaticExternalContext.StaticContext(
									externalContext, pipelineContext));
					try {
						testForLargeNumberOfEntries();
					} catch (Throwable e) {
						errors.add(e);
					}
				}
			};
			threads[i].start();
		}
		for (int i = 0; i < NO_OF_THREADS; i++) {
			try {
				threads[i].join();
			} catch (InterruptedException e) {
			}
		}
		if (errors.size() > 0) {
			throw (Throwable) errors.get(0);
		}
	}

	private void expireSession(String sessionId) {
		final TestExternalContext.TestSession session = sessionMap.get(sessionId);
		if (session != null) {
			session.expireSession();
			sessionMap.remove(sessionId);
		}
	}

	private String generateRandomString(int size) {
		// The first two characters are used to determine if the string is encrypted, this is to ensure that it will never
		// get interpreted as being encrypted.
		final StringBuffer randomString = new StringBuffer("AA");
		final Random randomGenerator = new Random();
		final byte bytes[] = new byte[size / 2];
		randomGenerator.nextBytes(bytes);
		randomString.append(Base64.encode(bytes));
		return randomString.toString();
	}

	private Session findOrCreateSession(String sessionId) {
		TestExternalContext.TestSession session = sessionMap.get(sessionId);
		if (session == null) {
			session = new TestExternalContext.TestSession(sessionId);
			sessionMap.put(sessionId, session);
		}
		return session;
	}
}

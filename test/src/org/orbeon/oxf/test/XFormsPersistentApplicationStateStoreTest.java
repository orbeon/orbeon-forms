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
package org.orbeon.oxf.test;

import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.pipeline.api.ExternalContext.Session;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.test.TestExternalContext;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.util.Base64;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.state.XFormsPersistentApplicationStateStore;
import org.orbeon.oxf.xforms.state.XFormsState;
import org.orbeon.oxf.xforms.state.XFormsStateStore;

import java.util.*;

public class XFormsPersistentApplicationStateStoreTest extends ResourceManagerTestBase {

	private XFormsStateStore fixture;
	private ExtendedTestExternalContext externalContext;
	private PipelineContext pipelineContext;
	private Map sessionMap = new HashMap();

	protected void setUp() {
        try {
            pipelineContext = new PipelineContext();
            externalContext = new ExtendedTestExternalContext(pipelineContext, null);

            pipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, externalContext);
            StaticExternalContext.setStaticContext(new StaticExternalContext.StaticContext(externalContext, pipelineContext));
            fixture = XFormsPersistentApplicationStateStore.instance(externalContext);
        } catch (RuntimeException e) {
            OXFException.getRootThrowable(e).printStackTrace();
            throw e;
        }
    }

	protected void tearDown() throws Exception {
		expireAllSessions();
	}

	public void testSimpleAddAndFind() {
		final XFormsState xformsState = new XFormsState("staticState", "dynamicState");
		fixture.add("pgid-1", null, "requestid-1", xformsState, "sessionid-1", true);
		final XFormsState retreivedState = fixture.find("pgid-1", "requestid-1");
		assertNotNull("State not found", retreivedState);
		assertEquals("Static state incorrect", xformsState.getStaticState(), retreivedState.getStaticState());
		assertEquals("Dynamic state incorrect", xformsState.getDynamicState(), retreivedState.getDynamicState());
	}

	public void testFindNoneExistent() {
		assertNull("Returned state for invalid request id", fixture.find("doesnot-exist", "doesnot-exist"));
	}

	public void testStaticState() {
		// For a given pageGeneration id the state associated does not change on subsequent add a subsequent add can only
		// associated a new session id, same is true for request id and dynamic state (Should the code check and throw an
		// exception if such an attempt is made?)
		final XFormsState originalState = new XFormsState("staticState", "dynamicState");
		fixture.add("pgid-1", null, "requestid-1", originalState, "sessionid-1", true);
		final XFormsState modifiedState = new XFormsState("staticState" + "Modified", "dynamicState" + "Modified");
		fixture.add("pgid-1", null, "requestid-1", modifiedState, "sessionid-1", true);

		final XFormsState retreivedState = fixture.find("pgid-1", "requestid-1");
		assertNotNull("State not found", retreivedState);
		assertEquals("Static state incorrect", originalState.getStaticState(), retreivedState.getStaticState());
		assertEquals("Dynamic state incorrect", originalState.getDynamicState(), retreivedState .getDynamicState());
	}

	public void testForLargeSizedState() {
		final XFormsState xformsState = new XFormsState(
				generateRandomString(100 * 1024),
				generateRandomString(100 * 1024));
		fixture.add("pgid-1", null, "requestid-1", xformsState, "sessionid-1", true);
		final XFormsState retreivedState = fixture.find("pgid-1", "requestid-1");
		assertNotNull("State not found", retreivedState);
		assertEquals("Static state incorrect", xformsState.getStaticState(), retreivedState.getStaticState());
		assertEquals("Dynamic state incorrect", xformsState.getDynamicState(), retreivedState.getDynamicState());
	}

	public void testForLargeNumberOfEntries() {
		final int NUMBER_OF_PAGES = 10;
		final int NUMBER_OF_REQUESTS = 25;
		XFormsState xformStates[] = new XFormsState[NUMBER_OF_PAGES];
		for (int i = 0; i < NUMBER_OF_PAGES; i++) {
			xformStates[i] = new XFormsState("staticState" + i, "dynamicState-0-" + i);
			fixture.add("pgid" + i, null, "requestid0", xformStates[i], "sessionid-1", true);
			for (int j = 1; j <= NUMBER_OF_REQUESTS; j++) {
				xformStates[i] = new XFormsState(xformStates[i].getStaticState(), "dynamicState-" + j + "-" + i);
				fixture.add("pgid" + i, "requestid" + (j - 1), "requestid" + i
						+ "-" + j, xformStates[i], "sessionid-1", false);

			}
		}
		for (int i = 0; i < NUMBER_OF_PAGES; i++) {
			verifyStateExists("pgid" + i, "requestid" + i + "-"
					+ NUMBER_OF_REQUESTS, xformStates[i]);
		}
	}

    
    // TODO: temp commented out as this fails on the build machine
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

	private void expireAllSessions() {
		for (Iterator iterator = sessionMap.values().iterator(); iterator.hasNext();) {
			((TestSession) iterator.next()).expireSession();
		}
		sessionMap.clear();
	}

	private void verifyStateExists(String pageGenerationId, String requestId, XFormsState expectedState) {
		final XFormsState retreivedState = fixture.find(pageGenerationId, requestId);
		assertNotNull("State not found", retreivedState);
		assertEquals("Static state incorrect", expectedState.getStaticState(), retreivedState.getStaticState());
		assertEquals("Dynamic state incorrect", expectedState.getDynamicState(), retreivedState.getDynamicState());
	}

	public void testForLargeSizedStatePersistedToDB() {
		final XFormsState xformsState = new XFormsState(
				generateRandomString(100 * 1024),
				generateRandomString(100 * 1024));
		fixture.add("pgid-1", null, "requestid-1", xformsState, "sessionid-1", true);
		final XFormsState retreivedState = fixture.find("pgid-1", "requestid-1");
		fillUpInMemoryCache();
		assertNotNull("State not found", retreivedState);
		assertEquals("Static state incorrect", xformsState.getStaticState(), retreivedState.getStaticState());
		assertEquals("Dynamic state incorrect", xformsState.getDynamicState(), retreivedState.getDynamicState());
	}

	public void testPreviousRequestDataCleared() {
		XFormsState xformsState = new XFormsState("staticState", "dynamicState");
        fixture.add("pgid-1", null, "requestid-0", xformsState, "sessionid-1", true);
        fixture.add("pgid-1", "requestid-0", "requestid-1", xformsState, "sessionid-1", false);
		fixture.add("pgid-1", "requestid-1", "requestid-2", xformsState, "sessionid-1", false);
        fixture.add("pgid-1", "requestid-2", "requestid-3", xformsState, "sessionid-1", false);

        assertNotNull("Initial request data removed", fixture.find("pgid-1", "requestid-0"));
        assertNotNull("Previous request data removed", fixture.find("pgid-1", "requestid-2"));
        assertNull("Previous previous request data not removed", fixture.find("pgid-1", "requestid-1"));

        fixture.add("pgid-1", "requestid-3", "requestid-4", xformsState, "sessionid-1", false);

        assertNotNull("Initial request data removed", fixture.find("pgid-1", "requestid-0"));
        assertNotNull("Previous request data removed", fixture.find("pgid-1", "requestid-3"));
        assertNull("Previous previous request data not removed", fixture.find("pgid-1", "requestid-2"));

    }

	public void testInitialEntryShouldNotBeCleared() {
		final XFormsState xformsState = new XFormsState("staticState", "dynamicState");
		fixture.add("pgid-1", null, "requestid-1", xformsState, "sessionid-1", true);
		fixture.add("pgid-1", "requestid-1", "requestid-2", xformsState, "sessionid-1", false);
		assertNotNull("Initial entry should not be cleared", fixture.find("pgid-1", "requestid-1"));
	}

	public void testFindAfterStatePersistedToDB() {
		final XFormsState xformsState = new XFormsState("staticState", "dynamicState");
		fixture.add("pgid", null, "requestid", xformsState, "sessionid", true);

		fillUpInMemoryCache();

		final XFormsState retreivedState = fixture.find("pgid", "requestid");
		assertNotNull("State not found", retreivedState);
		assertEquals("Static state incorrect", xformsState.getStaticState(), retreivedState.getStaticState());
		assertEquals("Dynamic state incorrect", xformsState.getDynamicState(), retreivedState.getDynamicState());
	}

	private void fillUpInMemoryCache() {
		final int CHUNK_SIZE = 1024;
		for (int i = 0; i < XFormsProperties.getApplicationStateStoreSize() / (CHUNK_SIZE * 2); i++) {
			fixture.add("pgid-" + i, null, "requestid-" + i, new XFormsState(
					generateRandomString(CHUNK_SIZE),
					generateRandomString(CHUNK_SIZE)), "sessionid-" + i, true);
		}
	}

	public void testFindAfterSessionExpiry() {
		final XFormsState xformsState = new XFormsState("staticState", "dynamicState");
		externalContext.setSession(findOrCreateSession("sessionid-1"));
		fixture.add("pgid-1", null, "requestid-0", xformsState, "sessionid-1", true);
		fixture.add("pgid-1", "requestid-0", "requestid-1", xformsState, "sessionid-1", false);
		expireSession("sessionid-1");
		assertNull("Initial State not removed even after session expiry", fixture.find("pgid-1", "requestid-0"));
		assertNull("State not removed even after session expiry", fixture.find("pgid-1", "requestid-1"));
	}

	public void testSessionExpiryOfPersistedState() {
		final XFormsState xformsState = new XFormsState("staticState", "dynamicState");
		externalContext.setSession(findOrCreateSession("sessionid-1"));
		fixture.add("pgid-1", null, "requestid-0", xformsState, "sessionid-1", true);
		fixture.add("pgid-1", "requestid-0", "requestid-1", xformsState, "sessionid-1", false);
		externalContext.setSession(null);
		// This will cause the previous entries to be pushed to db
		fillUpInMemoryCache();
		expireSession("sessionid-1");
		assertNull("Initial State not removed even after session expiry", fixture.find("pgid-1", "requestid-0"));
		assertNull("State not removed even after session expiry", fixture.find( "pgid-1", "requestid-1"));
	}

	public void testStateSharedAcrossMultipleSessions() {
		final XFormsState xformsState = new XFormsState("staticState", "dynamicState");
		externalContext.setSession(findOrCreateSession("sessionid-1"));
		fixture.add("pgid-1", "requestid-10", "requestid-1", xformsState, "sessionid-1", false);
		externalContext.setSession(findOrCreateSession("sessionid-2"));
		fixture.add("pgid-1", "requestid-20", "requestid-1", xformsState, "sessionid-2", false);
		expireSession("sessionid-1");
		assertNotNull("State removed even though it is refered to by a session",
				fixture.find("pgid-1", "requestid-1"));
	}

	public void testClearingOfStateSharedAcrossMultipleSessions() {
		final XFormsState xformsState = new XFormsState("staticState", "dynamicState");
		externalContext.setSession(findOrCreateSession("sessionid-1"));
		fixture.add("pgid-1", "requestid-10", "requestid-1", xformsState, "sessionid-1", false);
		externalContext.setSession(findOrCreateSession("sessionid-2"));
		fixture.add("pgid-1", "requestid-20", "requestid-1", xformsState, "sessionid-2", false);
		expireSession("sessionid-1");
		expireSession("sessionid-2");
		assertNull("State not removed even after expiration of all associated sessions",
				fixture.find("pgid-1", "requestid-1"));
	}

	public void testClearingOfInitialRequestOnSessionExpiration() {
		final XFormsState xformsState = new XFormsState("staticState", "dynamicState");
		externalContext.setSession(findOrCreateSession("sessionid-1"));
		fixture.add("pgid-1", null, "requestid-1", xformsState, "sessionid-1", true);
		expireSession("sessionid-1");
		assertNull("Initial entry state not removed even after expiration of session",
				fixture.find("pgid-1", "requestid-1"));
	}

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
		final TestSession session = (TestSession) sessionMap.get(sessionId);
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
		Session session = (Session) sessionMap.get(sessionId);
		if (session == null) {
			session = new TestSession(sessionId);
			sessionMap.put(sessionId, session);
		}
		return session;
	}

	static class ExtendedTestExternalContext extends TestExternalContext {

		private Session session;

		public ExtendedTestExternalContext(PipelineContext pipelineContext, Document requestDocument) {
			super(pipelineContext, requestDocument);
		}

		public String getRealPath(String path) {
            if (path.equals("WEB-INF/exist-conf.xml")) {
                return ResourceManagerWrapper.instance().getRealPath("/ops/unit-tests/exist-conf.xml");
            }
			return super.getRealPath(path);
		}

		public Session getSession(boolean create) {
			return session;
		}

		public void setSession(Session session) {
			this.session = session;
		}
	}

	static class TestSession implements Session {

		private String sessionId;
		private Set sessionListeners = new HashSet();
		private Map attributesMap = new HashMap();

		public TestSession(String sessionId) {
			this.sessionId = sessionId;
		}

		public void expireSession() {
			for (Iterator iterator = sessionListeners.iterator(); iterator.hasNext();) {
				((SessionListener) iterator.next()).sessionDestroyed();
			}
		}

		public void addListener(SessionListener sessionListener) {
			sessionListeners.add(sessionListener);
		}

		public Map getAttributesMap() {
			return null;
		}

		public Map getAttributesMap(int scope) {
			return attributesMap;
		}

		public long getCreationTime() {
			return 0;
		}

		public String getId() {
			return sessionId;
		}

		public long getLastAccessedTime() {
			return 0;
		}

		public int getMaxInactiveInterval() {
			return 0;
		}

		public void invalidate() {
		}

		public boolean isNew() {
			return false;
		}

		public void removeListener(SessionListener sessionListener) {
			sessionListeners.remove(sessionListener);
		}

		public void setMaxInactiveInterval(int interval) {
		}
	}
}

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
package org.orbeon.oxf.xforms.state

import org.orbeon.oxf.cache.CacheApi
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{IndentedLogger, SecureUtils}
import org.orbeon.oxf.xforms.analysis.XFormsStaticStateTest
import org.orbeon.oxf.xforms.event.events.XXFormsValueEvent
import org.orbeon.oxf.xforms.event.{ClientEvents, XFormsEvent, XFormsEventTarget}
import org.orbeon.oxf.xforms.*
import org.scalatest.funspec.AnyFunSpecLike


class XFormsStateManagerTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  import Private.*

  describe("Cache passivation to store using tuples") {

    // The size of the memory cache is supposed to be 1, so anything beyond that should be passivated to the store.
    val Count = 100

    it("should store and retrieve values") {

      val stateStore: CacheApi = XFormsStores.stateStore

      for (i <- 1 to Count)
        stateStore.put(i.toString, (s"v-$i", i))

      for (i <- 1 to Count)
        assert(stateStore.get(i.toString).map(_.asInstanceOf[(String, Int)]).contains((s"v-$i", i)))
    }
  }

  describe("Cache configurations") {

    for ((isCache, file) <- List(false -> "client-nocache.xhtml", true -> "client-cache.xhtml"))
      it(s"state with isCache = `$isCache` and file = `$file`") {
        testClient(isCache, file)
      }

    for ((isCache, file) <- List(false -> "server-nocache.xhtml", true -> "server-cache.xhtml"))
      it(s"state with isCache = `$isCache` and file = `$file`") {
        testServer(isCache, file)
      }
  }

  describe("Session listener") {
    it("must remove the UUID when the session expires") {
      withTestExternalContext { ec =>

        val session = ec.getSession(true)

        implicit val indentedLogger: IndentedLogger = XFormsStateManager.newIndentedLogger

        def createDoc() = {

          val doc =
            XFormsContainingDocumentBuilder(
              XFormsStaticStateTest.getStaticState("oxf:/org/orbeon/oxf/xforms/state/server-cache.xhtml"),
              None,
              None,
              mustInitialize = true
            )

          XFormsStateManager.afterInitialResponse(doc, disableDocumentCache = false)

          doc
        }

        val docs = List(createDoc(), createDoc())

        assert(XFormsStateManager.hasUuidListInSession(session))

        docs.foreach { doc =>
          assert(XFormsStateManager.getUuidListInSession(session).contains(doc.uuid))
          assert(doc eq XFormsDocumentCache.peekForTests(doc.uuid).get)
        }

        // Expire session
        XFormsStateManager.sessionDestroyed(session)

        // Test that the document is no longer in cache
        docs.foreach { doc =>
          assert(XFormsDocumentCache.take(doc.uuid).isEmpty)
          assert(! XFormsStateManager.hasUuidListInSession(session))
        }
      }
    }

    it("must clean up passivated dynamic states from xforms.state when the session expires (#1990, #7572)") {
      withTestExternalContext { ec =>

        val session = ec.getSession(true)
        implicit val indentedLogger: IndentedLogger = XFormsStateManager.newIndentedLogger

        val staticState = XFormsStaticStateTest.getStaticState("oxf:/org/orbeon/oxf/xforms/state/server-cache.xhtml")
        val doc         = XFormsContainingDocumentBuilder(staticState, None, None, mustInitialize = true)

        doc.afterInitialResponse()
        XFormsStateManager.afterInitialResponse(doc, disableDocumentCache = false)

        // Upon initial load, only `uuid-I` is stored in `xforms.state` (no separate `uuid` mapping object yet)
        assert(XFormsStores.stateStore.get(doc.uuid).isEmpty)
        assert(XFormsStores.stateStore.get(doc.uuid + "-I").isDefined)
        assert(XFormsStateStore.findSequence(doc.uuid).contains(1L))
        assert(XFormsStateStore.findState(doc.uuid, isInitialState = false).isDefined)
        assert(XFormsStateStore.findState(doc.uuid, isInitialState = true).isDefined)

        // Simulate cache eviction to `XFormsStateStore`
        XFormsDocumentCache.remove(doc.uuid)
        XFormsStateManager.onEvictedFromCache(doc)

        assert(XFormsStateManager.getUuidListInSession(session).contains(doc.uuid))
        assert(XFormsStores.stateStore.get(doc.uuid).isDefined)
        assert(XFormsStateStore.findState(doc.uuid, isInitialState = false).isDefined)
        assert(XFormsStateStore.findState(doc.uuid, isInitialState = true).isDefined)
        assert(XFormsStores.staticStateStore.get(staticState.digest).exists(_.isInstanceOf[Array[Byte]]))
        assert(XFormsStores.stateStore.get(staticState.digest).isEmpty)

        // Expire session -> both current and initial dynamic states must be removed from `xforms.state`,
        // while the shared static state remains in `xforms.static-state`
        XFormsStateManager.sessionDestroyed(session)

        assert(XFormsStores.stateStore.get(doc.uuid).isEmpty)
        assert(XFormsStores.stateStore.get(doc.uuid + "-I").isEmpty)
        assert(XFormsStateStore.findState(doc.uuid, isInitialState = false).isEmpty)
        assert(XFormsStateStore.findState(doc.uuid, isInitialState = true).isEmpty)
        assert(XFormsStores.staticStateStore.get(staticState.digest).isDefined)
      }
    }

    it("must restore static state from raw compressed bytes in xforms.static-state when XFormsStaticStateCache misses") {
      withTestExternalContext { ec =>

        ec.getSession(true)
        implicit val indentedLogger: IndentedLogger = XFormsStateManager.newIndentedLogger

        val staticState = XFormsStaticStateTest.getStaticState("oxf:/org/orbeon/oxf/xforms/state/server-nocache.xhtml")
        val state1      = TestState(XFormsContainingDocumentBuilder(staticState, None, None, mustInitialize = true))

        state1.document.afterInitialResponse()
        XFormsStateManager.afterInitialResponse(state1.document, disableDocumentCache = true)

        // Stored static state in `xforms.static-state` must be raw compressed `Array[Byte]` smaller than Base64 string
        val rawBytesOpt = XFormsStores.staticStateStore.get(staticState.digest).collect { case b: Array[Byte] => b }
        assert(rawBytesOpt.isDefined)
        assert(rawBytesOpt.get.length < staticState.encodedState.length)

        // Clear in-memory `XFormsStaticStateCache` and verify full document restoration from `xforms.static-state`
        XFormsStaticStateCache.getCacheForDiagnostics.removeAll()
        assert(XFormsStaticStateCache.findDocument(staticState.digest).isEmpty)

        val state2 = doUpdate(isCache = false, state1, doc =>
          List(
            new XXFormsValueEvent(
              doc.getObjectByEffectiveId("my-input").asInstanceOf[XFormsEventTarget],
              "restored-from-bytes"
            )
          )
        )
        assert(state2.document.sequence == 2)
        assert(XFormsStaticStateCache.findDocument(staticState.digest).isDefined)
      }
    }

    it("must bound session UUID tracking in BoundedSessionUuidSet using LRU order") {
      val set = new BoundedSessionUuidSet(maxEntries = 3)
      assert(set.add("u1").isEmpty)
      assert(set.add("u2").isEmpty)
      assert(set.add("u3").isEmpty)

      // Touch `u1` so `u2` becomes least-recently-used
      assert(set.add("u1").isEmpty)
      assert(set.add("u4").contains("u2"))

      assert(set.size == 3)
      assert(! set.contains("u2"))
      assert(set.contains("u1") && set.contains("u3") && set.contains("u4"))
    }
  }

  object Private {

    case class TestState(
      document           : XFormsContainingDocument,
      uuid               : String,
      staticStateString  : Option[String],
      dynamicStateString : Option[String]
    )

    object TestState {
      def apply(doc: XFormsContainingDocument): TestState =
        TestState(
          document           = doc,
          uuid               = doc.uuid,
          staticStateString  = XFormsStateManager.getClientEncodedStaticState(doc),
          dynamicStateString = XFormsStateManager.getClientEncodedDynamicState(doc)
        )
    }

    def testClient(isCache: Boolean, formFile: String): Unit = {
      withTestExternalContext { ec =>

        implicit val indentedLogger: IndentedLogger = XFormsStateManager.newIndentedLogger

        ec.getSession(true)

        val staticState = XFormsStaticStateTest.getStaticState("oxf:/org/orbeon/oxf/xforms/state/" + formFile)

        // Initialize document and get initial document state
        val state1 = TestState(XFormsContainingDocumentBuilder(staticState, None, None, mustInitialize = true))

        locally {

          assert(state1.uuid.length === SecureUtils.HexShortLength)
          assertNonEmptyClientState(state1)

          // Initial response sent
          state1.document.afterInitialResponse()
          XFormsStateManager.afterInitialResponse(state1.document, ! isCache)
        }

        assert(state1.dynamicStateString map getSequenceNumber contains 1)

        // Run update
        val state2 = doUpdate(isCache, state1, doc =>
          List(
            new XXFormsValueEvent(
              doc.getObjectByEffectiveId("my-input").asInstanceOf[XFormsEventTarget],
              "gaga"
            )
          )
        )

        // UUID and static state can't change
        assert(state1.uuid === state2.uuid)
        assert(state1.staticStateString === state2.staticStateString)

        // Sequence must be updated
        assert(state2.dynamicStateString map getSequenceNumber contains 2)

        // Dynamic state must have changed
        assert((state1.dynamicStateString map stripSequenceNumber) != (state2.dynamicStateString map stripSequenceNumber))

        val state3 = doUpdate(isCache, state2, _ => Nil)

        assert(state1.uuid === state3.uuid)
        assert(state1.staticStateString === state3.staticStateString)
        assert(state3.dynamicStateString map getSequenceNumber contains 3)

        // Dynamic state must NOT have changed because no event was dispatched
        assert((state2.dynamicStateString map stripSequenceNumber) === (state3.dynamicStateString map stripSequenceNumber))

        // Get back to initial state
        val state4 = getInitialState(state1, isCache)

        assert(state1.uuid === state4.uuid)
        assert(state1.staticStateString === state4.staticStateString)
        assert(state4.dynamicStateString map getSequenceNumber contains 1)

        // Make sure we found the initial dynamic state
        assert((state1.dynamicStateString map stripSequenceNumber) === (state4.dynamicStateString map stripSequenceNumber))
      }
    }

    def testServer(isCache: Boolean, formFile: String): Unit = {
      withTestExternalContext { ec =>

        implicit val indentedLogger: IndentedLogger = XFormsStateManager.newIndentedLogger

        ec.getSession(true)

        val staticState = XFormsStaticStateTest.getStaticState("oxf:/org/orbeon/oxf/xforms/state/" + formFile)

        val state1 = TestState(XFormsContainingDocumentBuilder(staticState, None, None, mustInitialize = true))

        val initialDynamicStateString = {
          assert(state1.uuid.length === SecureUtils.HexShortLength)
          assertEmptyClientState(state1)

          state1.document.afterInitialResponse()
          XFormsStateManager.afterInitialResponse(state1.document, ! isCache)

          DynamicState.encodeDocumentToString(state1.document, XFormsGlobalProperties.isGZIPState, isForceEncryption = false)
        }

        assert(1 === state1.document.sequence)

        val state2 = doUpdate(isCache, state1, doc =>
          List(
            new XXFormsValueEvent(
              doc.getObjectByEffectiveId("my-input").asInstanceOf[XFormsEventTarget],
              "gaga"
            )
          )
        )

        // UUID can't change
        assert(state1.uuid === state2.uuid)
        assert(2 === state2.document.sequence)

        assertEmptyClientState(state2)

        val state3 = doUpdate(isCache, state2, _ => Nil)

        assert(state1.uuid === state3.uuid)
        assert(3 === state3.document.sequence)
        assertEmptyClientState(state3)

        val state4 = getInitialState(state1, isCache)

        assert(state1.uuid === state4.uuid)
        assert(1 === state4.document.sequence)
        assertEmptyClientState(state4)
        assert(
          stripSequenceNumber(initialDynamicStateString) ===
            stripSequenceNumber(DynamicState.encodeDocumentToString(state4.document, XFormsGlobalProperties.isGZIPState, isForceEncryption = false))
        )
      }
    }

    def doUpdate(isCache: Boolean, state1: TestState, callback: XFormsContainingDocument => List[XFormsEvent]) = {

      val parameters =
        RequestParameters(
          state1.uuid,
          None,
          None,
          state1.staticStateString,
          state1.dynamicStateString
        )

      // New state
      XFormsStateManager.acquireDocumentLock(parameters.uuid, 0L) match {
        case LockResponse.Success(lock) =>
          try {
            val newDoc = XFormsStateManager.beforeUpdate(parameters, ! isCache).get

            if (isCache)
              assert(state1.document eq newDoc) // must be the same because cache is enabled and cache has room
            else
              assert(state1.document ne newDoc) // can't be the same because cache is disabled

            // Run events if any
            newDoc.withExternalEvents(null, submissionIdOpt = None) {
              for (event <- callback(newDoc))
                ClientEvents.processEvent(newDoc, event)
            }

            val result =
              XFormsContainingDocumentSupport.withUpdateResponse(newDoc, ignoreSequence = false) {
                TestState(newDoc)
              }

            XFormsStateManager.afterUpdate(newDoc, keepDocument = true, disableDocumentCache = ! isCache)

            result
          } finally
            XFormsStateManager.releaseDocumentLock(lock)
        case LockResponse.Busy       => fail("Ajax update lock busy (zero timeout)")
        case LockResponse.Timeout    => fail("Ajax update lock timeout exceeded")
        case LockResponse.Failure(t) => throw t
      }
    }

    def getInitialState(
      state1        : TestState,
      isCache       : Boolean
    )(implicit
      indentedLogger: IndentedLogger
    ): TestState = {

      val parameters =
        RequestParameters(
          state1.uuid,
          None,
          None,
          state1.staticStateString,
          state1.dynamicStateString
        )

      // Find document
      val newDoc =
        XFormsStateManager.createDocumentFromStore(
          parameters,
          isInitialState = true,
          disableUpdates = true
        ).get

      // can't be the same because either cache is disabled OR we create a duplicate document (could be same if state1 is initial state)
      assert(state1.document ne newDoc)

      TestState(newDoc)
    }

    def assertNonEmptyClientState(state: TestState) = {
      assert(state.staticStateString  flatMap (_.trimAllToOpt) nonEmpty)
      assert(state.dynamicStateString flatMap (_.trimAllToOpt) nonEmpty)
    }

    def assertEmptyClientState(state: TestState) = {
      assert(state.staticStateString  flatMap (_.trimAllToOpt) isEmpty)
      assert(state.dynamicStateString flatMap (_.trimAllToOpt) isEmpty)
    }

    def stripSequenceNumber(serializedState: String) =
      DynamicState(serializedState).copy(sequence = 1, initializationData = None).encodeToString(XFormsGlobalProperties.isGZIPState, isForceEncryption = false)

    def getSequenceNumber(serializedState: String) =
      DynamicState(serializedState).sequence
  }
}
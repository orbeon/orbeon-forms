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

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.SecureUtils
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.analysis.XFormsStaticStateTest
import org.orbeon.oxf.xforms.event.events.XXFormsValueEvent
import org.orbeon.oxf.xforms.event.{ClientEvents, XFormsEvent, XFormsEventTarget}
import org.orbeon.oxf.xforms.{XFormsContainingDocument, XFormsProperties}
import org.scalatest.funspec.AnyFunSpecLike

class XFormsStateManagerTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  import Private._

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

        def createDoc() = {

          val doc =
            XFormsContainingDocument(
              XFormsStaticStateTest.getStaticState("oxf:/org/orbeon/oxf/xforms/state/server-cache.xhtml"),
              None,
              None,
              mustInitialize = true
            )

          XFormsStateManager.afterInitialResponse(doc, disableDocumentCache = false)

          doc
        }

        val docs = List(createDoc(), createDoc())

        docs foreach { doc =>
          assert(XFormsStateManager.getUuidListInSession(session).contains(doc.uuid))
          assert(doc eq XFormsDocumentCache.peekForTests(doc.uuid).get)
        }

        // Expire session
        XFormsStateManager.sessionDestroyed(session)

        // Test that the document is no longer in cache
        docs foreach { doc =>
          assert(XFormsDocumentCache.take(doc.uuid).isEmpty)
        }
      }
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

        ec.getSession(true)

        val staticState = XFormsStaticStateTest.getStaticState("oxf:/org/orbeon/oxf/xforms/state/" + formFile)

        // Initialize document and get initial document state
        val state1 = TestState(XFormsContainingDocument(staticState, None, None, mustInitialize = true))

        locally {

          assert(state1.uuid.length === SecureUtils.HexIdLength)
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

        ec.getSession(true)

        val staticState = XFormsStaticStateTest.getStaticState("oxf:/org/orbeon/oxf/xforms/state/" + formFile)

        val state1 = TestState(XFormsContainingDocument(staticState, None, None, mustInitialize = true))

        val initialDynamicStateString = {
          assert(state1.uuid.length === SecureUtils.HexIdLength)
          assertEmptyClientState(state1)

          state1.document.afterInitialResponse()
          XFormsStateManager.afterInitialResponse(state1.document, ! isCache)

          DynamicState.encodeDocumentToString(state1.document, XFormsProperties.isGZIPState, isForceEncryption = false)
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
            stripSequenceNumber(DynamicState.encodeDocumentToString(state4.document, XFormsProperties.isGZIPState, isForceEncryption = false))
        )
      }
    }

    def doUpdate(isCache: Boolean, state1: TestState, callback: XFormsContainingDocument => List[XFormsEvent]) = {

      val parameters =
        RequestParameters(
          state1.uuid,
          None,
          state1.staticStateString,
          state1.dynamicStateString
        )

      // New state
      val lock = XFormsStateManager.acquireDocumentLock(parameters.uuid, 0L)

      if (lock.isEmpty)
        fail("Ajax update lock timeout exceeded")

      try {
        val newDoc = XFormsStateManager.beforeUpdate(parameters, ! isCache)

        if (isCache)
          assert(state1.document eq newDoc) // must be the same because cache is enabled and cache has room
        else
          assert(state1.document ne newDoc) // can't be the same because cache is disabled

        // Run events if any
        newDoc.beforeExternalEvents(null, true)

        for (event <- callback(newDoc))
          ClientEvents.processEvent(newDoc, event)

        newDoc.afterExternalEvents(true)

        XFormsStateManager.beforeUpdateResponse(newDoc, ignoreSequence = false)

        val result = TestState(newDoc)

        XFormsStateManager.afterUpdateResponse(newDoc)
        XFormsStateManager.afterUpdate(newDoc, keepDocument = true, disableDocumentCache = ! isCache)

        result
      } finally
        XFormsStateManager.releaseDocumentLock(lock.get)
    }

    def getInitialState(state1: TestState, isCache: Boolean) = {

      val parameters =
        RequestParameters(
          state1.uuid,
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
        )

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
      DynamicState(serializedState).copy(sequence = 1, initialClientScript = None).encodeToString(XFormsProperties.isGZIPState, isForceEncryption = false)

    def getSequenceNumber(serializedState: String) =
      DynamicState(serializedState).sequence
  }
}
/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.xforms

import org.scalatest.funspec.AnyFunSpecLike

import scala.scalajs.js


class LegacyCustomEventTest extends AnyFunSpecLike {

  describe("LegacyCustomEvent with LIST signature (default)") {

    it("must notify subscriber with event type, args array, and custom obj") {
      val event = new LegacyCustomEvent("testEvent")
      var calledType: String           = null
      var calledArgs: js.Array[js.Any] = null
      var calledObj: js.Any            = null

      val fn: js.Function = (t: String, a: js.Array[js.Any], o: js.Any) => {
        calledType = t
        calledArgs = a
        calledObj  = o
      }

      event.subscribe(fn, obj = "customData": js.Any)
      event.fire("arg1", 42)

      assert(calledType === "testEvent")
      assert(calledArgs.length === 2)
      assert(calledArgs(0) === "arg1")
      assert(calledArgs(1) === 42)
      assert(calledObj === "customData")
    }

    it("must support 0-argument firing and no-argument subscribers") {
      val event = new LegacyCustomEvent("orbeonLoaded", fireOnce = true)
      var invoked = false

      val fn: js.Function = () => {
        invoked = true
      }

      event.subscribe(fn)
      event.fire()

      assert(invoked === true)
    }

    it("must immediately notify late subscribers when fireOnce is true") {
      val event = new LegacyCustomEvent("orbeonLoaded", fireOnce = true)
      event.fire("loadedPayload")

      assert(event.fired === true)

      var lateInvoked = false
      var lateArg: js.Any = null

      val fn: js.Function = (_: String, a: js.Array[js.Any], _: js.Any) => {
        lateInvoked = true
        lateArg     = a(0)
      }

      event.subscribe(fn)

      assert(lateInvoked === true)
      assert(lateArg === "loadedPayload")
    }

    it("must not re-fire when fireOnce is true") {
      val event = new LegacyCustomEvent("singleEvent", fireOnce = true)
      var callCount = 0

      val fn: js.Function = () => {
        callCount += 1
      }

      event.subscribe(fn)
      event.fire()
      event.fire()

      assert(callCount === 1)
    }
  }

  describe("LegacyCustomEvent with FLAT signature") {

    it("must notify subscriber with first arg and custom obj directly") {
      val event = new LegacyCustomEvent("flatEvent", signature = LegacyCustomEvent.Flat)
      var calledArg: js.Any = null
      var calledObj: js.Any = null

      val fn: js.Function = (arg: js.Any, obj: js.Any) => {
        calledArg = arg
        calledObj = obj
      }

      event.subscribe(fn, obj = "myContext": js.Any)
      val payload = js.Dynamic.literal(foo = "bar")
      event.fire(payload)

      assert(calledArg === payload)
      assert(calledObj === "myContext")
    }
  }

  describe("LegacyCustomEvent subscription management") {

    it("must unsubscribe matching function") {
      val event = new LegacyCustomEvent("testEvent")
      var callCount = 0

      val fn: js.Function = () => {
        callCount += 1
      }

      event.subscribe(fn)
      event.fire()
      assert(callCount === 1)

      val removed = event.unsubscribe(fn)
      assert(removed === true)

      event.fire()
      assert(callCount === 1)
    }

    it("must unsubscribe all listeners with unsubscribeAll") {
      val event = new LegacyCustomEvent("testEvent")
      var count1 = 0
      var count2 = 0

      val fn1: js.Function = () => { count1 += 1 }
      val fn2: js.Function = () => { count2 += 1 }

      event.subscribe(fn1)
      event.subscribe(fn2)
      event.fire()

      assert(count1 === 1)
      assert(count2 === 1)

      event.unsubscribeAll()
      event.fire()

      assert(count1 === 1)
      assert(count2 === 1)
    }

    it("must continue notifying subsequent subscribers if one subscriber throws") {
      val event = new LegacyCustomEvent("testEvent")
      var sub2Called = false

      val throwingFn: js.Function = () => {
        throw new RuntimeException("Test error in callback")
      }
      val normalFn: js.Function = () => {
        sub2Called = true
      }

      event.subscribe(throwingFn)
      event.subscribe(normalFn)
      event.fire()

      assert(sub2Called === true)
    }

    it("must bind this to obj when overrideContext is true") {
      val customCtx = js.Dynamic.literal(name = "ctx")
      val event = new LegacyCustomEvent("testEvent")
      var boundThis: js.Any = null

      val fn: js.Function = js.ThisFunction.fromFunction1 { (thiz: js.Any) =>
        boundThis = thiz
      }

      event.subscribe(fn, obj = customCtx, overrideContext = true: js.Any)
      event.fire()

      assert(boundThis === customCtx)
    }

    it("must bind this to overrideContext when overrideContext is an object") {
      val customCtx = js.Dynamic.literal(name = "ctxObj")
      val event = new LegacyCustomEvent("testEvent")
      var boundThis: js.Any = null

      val fn: js.Function = js.ThisFunction.fromFunction1 { (thiz: js.Any) =>
        boundThis = thiz
      }

      event.subscribe(fn, obj = "data": js.Any, overrideContext = customCtx: js.Any)
      event.fire()

      assert(boundThis === customCtx)
    }
  }
}

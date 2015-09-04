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
package org.orbeon.scaxon

import collection.mutable.HashMap

// Simple FSM in good part inspired by Akka's akka.actor.FSM, but without the reliance on actors.
object FSM {
  // State containing the actual state with data
  case class State[S, D](state: S, data: D) {
    // Call `using` to update state data e.g. `stay() using "foo"`
    def using(nextData: D): State[S, D] =
      copy(data = nextData)
  }
}

trait FSM[S, E, D] {
  
  import FSM._

  // Each state function is passed an Event containing an event and data
  case class Event[D](ev: E, data: D)

  type State = FSM.State[S, D]
  type StateFunction = PartialFunction[Event[D], State]

  private var currentState: State = _
  private val stateFunctions = HashMap[S, StateFunction]()

  // Associate a state with a function
  protected final def when(s: S)(f: StateFunction) =
    if (stateFunctions contains s)
      stateFunctions(s) = stateFunctions(s) orElse f
    else
      stateFunctions(s) = f

  // Define the initial state
  protected final def startWith(s: S, d: D) =
    currentState = State(s, d);

  protected final def initialize() = () // NOP for now

  // Change state
  protected final def goto(nextState: S): State =
    State(nextState, currentState.data)

  // Stay at current state
  protected final def stay(): State =
    goto(currentState.state)

  protected final def processEvent(ev: E) =
    currentState = stateFunctions(currentState.state)(Event(ev, currentState.data))
}
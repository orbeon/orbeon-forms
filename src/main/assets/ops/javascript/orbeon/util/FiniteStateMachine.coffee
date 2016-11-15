# Copyright (C) 2011 Orbeon, Inc.
#
# This program is free software; you can redistribute it and/or modify it under the terms of the
# GNU Lesser General Public License as published by the Free Software Foundation; either version
# 2.1 of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
# without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
# See the GNU Lesser General Public License for more details.
#
# The full text of the license is available at http://www.gnu.org/copyleft/lesser.html

$ = ORBEON.jQuery
elementStateMatches = (states, element) ->                                                                              # Case where state is per element
    state = f$.data 'state', element
    ((_.isUndefined state) and (_.contains states, 'initial')) or (_.contains states, state)
globalState = 'initial'                                                                                                 # Case where we have just one global state

ORBEON.util.FiniteStateMachine =
    create: ({ events, elements, conditions, actions, transitions }) ->
        _.each transitions, (transition) ->
            _.each transition.events, (eventName) ->
                events[eventName] (event) ->
                    if transition.elements?                                                                             # State if per-element
                        elts = elements[transition.elements] event                                                      # Get elements from event (e.g. editable inside cell for mouseover)
                        if transition.from?                                                                             # Filter elements that are in the 'from' state
                            elts = _.filter elts, (e) -> elementStateMatches transition.from, $ e
                        if transition.conditions                                                                        # Filter elements that match all the conditions
                            elts = _.filter elts, (e) ->
                                _.all transition.conditions, (c) -> conditions[c] $ e
                        _.each elts, (element) ->
                            f$.data 'state', transition.to, $ element                                                   # Change state before running action, so if action trigger an event, that event runs against the new state
                            _.each transition.actions, (action) -> actions[action] $ element                            # Run all the actions on the elements
                    else                                                                                                # State is global (just one state)
                        if not transition.from? or -1 != _.indexOf transition.from, globalState
                            conditionsMet = true
                            if transition.conditions
                                _.each transition.conditions, (condition) ->
                                    conditionsMet = false if not conditions[c](event)
                            if conditionsMet == true
                                globalState = transition.to if transition.to?
                                _.each transition.actions, (action) ->
                                    actions[action] event

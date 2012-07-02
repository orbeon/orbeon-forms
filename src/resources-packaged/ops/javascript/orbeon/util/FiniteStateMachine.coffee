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

matchState = (states, element) ->
    state = f$.data 'state', element
    ((_.isUndefined state) and (_.contains states, 'initial')) or (_.contains states, state)

ORBEON.util.FiniteStateMachine =
    create: (events, elements, conditions, actions, transitions) ->
        _.each transitions, (transition) ->
            _.each transition.events, (event) ->
                events[event] (event) ->
                    elts = elements[transition.elements] event                                                          # Get elements from event (e.g. editable inside cell for mouseover)
                    elts = _.filter elts, (e) -> matchState transition.from, $ e                                        # Filter elements that are in the 'from' state
                    if transition.conditions                                                                            # Filter elements that match all the conditions
                        elts = _.filter elts, (e) ->
                            _.all transition.conditions, (c) -> conditions[c] $ e
                    _.each elts, (element) ->
                        f$.data 'state', transition.to, $ element                                                       # Change state before running action, so if action trigger an event, that event runs against the new state
                        _.each transition.actions, (action) -> actions[action] $ element                                # Run all the actions on the elements

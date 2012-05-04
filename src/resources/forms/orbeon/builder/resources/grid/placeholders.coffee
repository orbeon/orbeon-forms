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

YD = YAHOO.util.Dom
OD = ORBEON.util.Dom
Builder = ORBEON.Builder
Event = YAHOO.util.Event
Events = ORBEON.xforms.Events
Controls = ORBEON.xforms.Controls

# For the generic label, generic hint, button's label, and link's label:
#   Show/hide placeholders
#   Handle editing of the label/hint

$ ->
    # Functional jQuery, which should be moved to a common location
    f$ = do ->
        jQueryObject = $ '<div>'
        result = {}
        for m in _.methods jQueryObject
            do (m) ->
                result[m] = (params...) ->
                    o = params.pop()
                    jQueryObject[m].apply o, params
        result
    f$.findOrIs = (selector, element) ->                                                                                # Like find, but includes the current element if matching
        result = f$.find selector, element
        result = f$.add element, result if f$.is selector, element
        result

    # Low-level operations on editables
    editables = [
        {
            selector: '.xforms-label'
            editInputSelector: '#fb-edit-label'
            placeholderOutputSelector: '#fb-placeholder-label'
            placeholderContainerSelector: '.xforms-label'
            initialValueSelector: '.xforms-label'
        }
        {
            selector: '.xforms-hint'
            editInputSelector: '#fb-edit-hint'
            placeholderOutputSelector: '#fb-placeholder-hint'
            placeholderContainerSelector: '.xforms-hint'
            initialValueSelector: '.xforms-hint'
        }
        {
            selector: '.xforms-trigger-appearance-full'
            editInputSelector: '#fb-edit-label'
            placeholderOutputSelector: '#fb-placeholder-label'
            placeholderContainerSelector: '.xforms-mock-button span'
            initialValueSelector: 'button'
            createMock: (element) ->
                button = f$.find 'button', element                                                                      # Hide actual button
                f$.hide button
                mockDiv = f$.appendTo element, ($ '<div class="xforms-mock-button">')                                   # Create mock button
                f$.append (mockLabel = $ '<span>'), mockDiv
                mockLabel.text f$.text button
            removeMock: (element) ->
                f$.show f$.find 'button', element
                f$.remove f$.find '.xforms-mock-button', element
        }
        {
            selector: '.fr-grid-content .xforms-trigger-appearance-minimal'
            editInputSelector: '#fb-edit-label'
            placeholderOutputSelector: '#fb-placeholder-label'
            placeholderContainerSelector: '.fb-mock-link'
            initialValueSelector: 'a'
            createMock: (element) ->
                anchor = f$.find 'a', element                                                                           # Hide link
                f$.hide anchor
                f$.appendTo element, (mockLink = $ '<div class="fb-mock-link">')                                        # Create mock link
                mockLink.text f$.text anchor
            removeMock: (element) ->
                f$.show f$.find 'a', element
                f$.remove f$.find '.fb-mock-link', element
        }
    ]

    # Function dealing with editables
    anyEditableSelector = (_.pluck editables, 'selector').join ', '                                                     # A CSS selector matching any editable
    editable = (element) -> _.first _.filter editables, (e) -> f$.is '*', f$.findOrIs e.selector, element               # element: editable -> editable info
    editableEditInput = (element) -> $ (editable element).editInputSelector                                             # Input field used for editing
    editablePlaceholderOutput = (element) -> $ (editable element).placeholderOutputSelector                             # Element in which we put the placeholder
    editableInside = (selectorType, element) -> f$.findOrIs (editable element)[selectorType], element                   # Find an element inside a container element for a certain type of selector
    editablePlaceholderContainer = (element) -> editableInside 'placeholderContainerSelector', element
    editableInitialValue = (element) -> editableInside 'initialValueSelector', element
    editableDo = (actionName) -> (element) ->
        action = (editable element)[actionName]
        action element if action
    matchState = (states, element) ->
        state = f$.data 'state', element
        ((_.isUndefined state) and (_.contains states, 'initial')) or (_.contains states, state)

    # Events
    mouseEntersGridTd = (f) -> Builder.mouseEntersGridTdEvent.subscribe ({gridTd}) -> f $ gridTd
    mouseExistsGridTd = (f) -> Builder.mouseExitsGridTdEvent .subscribe ({gridTd}) -> f $ gridTd
    ajaxResponse = (f) -> Events.ajaxResponseProcessedEvent.subscribe f
    click = (f) -> ($ document).click ({target}) -> f $ target
    enterKey = (f) -> ($ document).keypress ({target, which}) -> f $ target if which == 13
    lostFocus = (f) -> ($ document).focusout ({target}) -> f $ target
    editDoneCallbacks = $.Callbacks()
    editDone = (f) -> editDoneCallbacks.add f

    # Return elements, maybe relative to a node
    elementsInContainer = (container) -> f$.find anyEditableSelector, container
    elementClosest = (element) -> f$.closest anyEditableSelector, element
    elementsAll = -> $ anyEditableSelector

    # Conditions
    isEmpty = (element) -> (f$.text editableInitialValue element) == ''
    isNonEmpty = (element) -> not isEmpty element
    pointerInsideCell = do ->
        currentCell = null
        Builder.mouseEntersGridTdEvent.subscribe ({gridTd}) -> currentCell = gridTd
        Builder.mouseExitsGridTdEvent.subscribe () -> currentCell = null
        (element) -> currentCell? and f$.is '*', f$.closest currentCell, element
    pointerOutsideCell = (element) -> not pointerInsideCell element

    # Actions
    removeFor = (element) -> f$.removeAttr 'for', element                                                               # So on click on the label, the focus isn't set on the input on click
    showPlaceholder = (element) ->
        f$.addClass 'fb-label-hint-placeholder', element
        placeholderText = Controls.getCurrentValue _.first editablePlaceholderOutput element
        f$.text placeholderText, editablePlaceholderContainer element
    hidePlaceholder = (element) ->
        f$.removeClass 'fb-label-hint-placeholder', element
        f$.text '', editablePlaceholderContainer element
    createMock = editableDo 'createMock'
    removeMock = editableDo 'removeMock'
    startEdit = (element) ->
        f$.removeClass 'fb-label-hint-placeholder', element
        f$.removeClass 'xforms-disabled', element                                                                       # Remove disabled which we have on hint when their value is empty
        input = editableEditInput element
        f$.append input, f$.empty editablePlaceholderContainer element
        f$.show input
        f$.focus f$.find 'input', input
    endEdit = (element) ->
        input = editableEditInput element
        f$.append input, $ '.fb-cell-editor'                                                                            # Move editor out of grid, so it doesn't get removed by HTML replacements
        newValue = Controls.getCurrentValue input[0]
        f$.text newValue, editableInitialValue element                                                                  # Restore text under label/hint
    fireEditDone = -> editDoneCallbacks.fire()

    # Finite state machine description
    # Diagram: https://docs.google.com/a/orbeon.com/drawings/d/1cJ0B3Tl7QRTMkVUbtlA55C0TUvRiOt5hzR8-dc-aBrk/edit
    transitions = [
        { from: [ 'initial' ],                  events: [ mouseEntersGridTd ],      elements: elementsInContainer,  conditions: [ isEmpty ],                        to: 'placeholder',              actions: [ removeFor, createMock, showPlaceholder ]  }
        { from: [ 'initial' ],                  events: [ mouseEntersGridTd ],      elements: elementsInContainer,  conditions: [ isNonEmpty ],                     to: 'mock',                     actions: [ removeFor, createMock ]                   }
        { from: [ 'mock' ],                     events: [ mouseExistsGridTd ],      elements: elementsInContainer,                                                  to: 'initial',                  actions: [ removeMock ]                              }
        { from: [ 'placeholder' ],              events: [ mouseExistsGridTd ],      elements: elementsInContainer,                                                  to: 'initial',                  actions: [ hidePlaceholder, removeMock  ]            }
        { from: [ 'placeholder', 'mock' ],      events: [ click ],                  elements: elementClosest,                                                       to: 'wait-xhr-to-edit'                                                               }
        { from: [ 'wait-xhr-to-edit' ],         events: [ ajaxResponse ],           elements: elementsAll,                                                          to: 'edit',                     actions: [ startEdit ]                               }
        { from: [ 'edit' ],                     events: [ enterKey, lostFocus ],    elements: elementClosest,                                                       to: 'edit-done',                actions: [ endEdit, removeMock, fireEditDone ]       }
        { from: [ 'edit-done' ],                events: [ editDone ],               elements: elementsAll,          conditions: [ pointerOutsideCell ],             to: 'initial'                                                                        }
        { from: [ 'edit-done' ],                events: [ editDone ],               elements: elementsAll,          conditions: [ pointerInsideCell, isEmpty ],     to: 'placeholder-after-edit',   actions: [ createMock, showPlaceholder ]             }
        { from: [ 'edit-done' ],                events: [ editDone ],               elements: elementsAll,          conditions: [ pointerInsideCell, isNonEmpty ],  to: 'mock',                     actions: [ createMock ]                              }
        { from: [ 'placeholder-after-edit' ],   events: [ ajaxResponse ],           elements: elementsAll,                                                          to: 'placeholder',              actions: [ showPlaceholder ]                         }
    ]

    # Finite state machine runner
    _.each transitions, (transition) ->
        _.each transition.events, (event) ->
            event (event) ->
                elements = transition.elements event                                                                    # Get elements from event (e.g. editable inside cell for mouseover)
                if transition.conditions                                                                                # Filter elements that match all the conditions
                    elements = _.filter elements, (e) ->
                        _.all transition.conditions, (c) -> c $ e
                elements = _.filter elements, (e) -> matchState transition.from, $ e                                    # Filter elements that are in the 'from' state
                _.each elements, (element) ->
                    f$.data 'state', transition.to, $ element                                                           # Change state before running action, so if action trigger an event, that event runs against the new state
                    _.each transition.actions, (action) -> action $ element                                             # Run all the actions on the elements

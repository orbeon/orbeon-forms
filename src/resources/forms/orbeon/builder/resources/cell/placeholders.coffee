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

Builder = ORBEON.Builder
Controls = ORBEON.xforms.Controls
Document = ORBEON.xforms.Document
Event = YAHOO.util.Event
Events = ORBEON.xforms.Events
FSM = ORBEON.util.FiniteStateMachine
OD = ORBEON.util.Dom
YD = YAHOO.util.Dom

# For the generic label, generic hint, button's label, and link's label:
#   Show/hide placeholders
#   Handle editing of the label/hint

$ ->
    # Low-level operations on editables
    editables =
        hint:
            selector: '.xforms-hint'
            editInputSelector: '#fb-edit-hint'
            placeholderOutputSelector: '#fb-placeholder-hint'
            placeholderContainerSelector: '.xforms-hint'
            initialValueSelector: '.xforms-hint'
        button:
            selector: '.fr-grid-content > .xforms-trigger-appearance-full'
            editInputSelector: '#fb-edit-label'
            placeholderOutputSelector: '#fb-placeholder-label'
            placeholderContainerSelector: '.btn'
            initialValueSelector: 'button'
            createMock: (element) ->
                button = f$.find 'button', element                                                                      # Hide actual button
                f$.hide button
                mockButton = f$.appendTo element, ($ '<span class="btn">')                                              # Create mock button
                mockButton.text f$.text button
            removeMock: (element) ->
                f$.show f$.find 'button', element
                f$.remove f$.find 'span.btn', element
        link:
            selector: '.fr-grid-content > .xforms-trigger-appearance-minimal'
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

    # Function dealing with editables
    anyEditableSelector = _.pluck (_.values editables), 'selector'                                                      # CSS selectors for all the editables
    editable = (element) -> _.first _.filter (_.values editables), (e) -> f$.is '*', f$.findOrIs e.selector, element    # element: editable -> editable info
    editableEditInput = do ->                                                                                           # Input field used for editing
        selectorToInput = _.memoize (selector) -> $ selector                                                            # Avoid "loosing" editors when HTML is replaced
        (element) -> selectorToInput (editable element).editInputSelector
    editablePlaceholderOutput = (element) -> $ (editable element).placeholderOutputSelector                             # Element in which we put the placeholder
    editableInside = (selectorType, element) -> f$.findOrIs (editable element)[selectorType], element                   # Find an element inside a container element for a certain type of selector
    editablePlaceholderContainer = (element) -> editableInside 'placeholderContainerSelector', element
    editableInitialValue = (element) -> editableInside 'initialValueSelector', element
    editableDo = (actionName) -> (element) ->
        action = (editable element)[actionName]
        action element if action

    # Utility
    currentSeqNo = (element) ->
        form = f$.closest 'form', element
        parseInt Document.getFromClientState (f$.attr 'id', form), "sequence"
    # A way of setting the focus that works for IE
    # See: http://wiki.orbeon.com/forms/doc/contributor-guide/browser?pli=1#TOC-Setting-the-focus-on-IE
    setFocus = (element) ->
        deferred = $.Deferred()
        do setFocusWorker = ->
            f$.focus element
            focusSet = document.activeElement == element[0]
            if focusSet then deferred.resolve() else _.defer setFocusWorker
        deferred
    editDoneCallbacks = $.Callbacks()

    # Finite state machine description
    # Diagram: https://docs.google.com/a/orbeon.com/drawings/d/1cJ0B3Tl7QRTMkVUbtlA55C0TUvRiOt5hzR8-dc-aBrk/edit
    FSM.create

        transitions: [
            { events: [ 'mouseEntersGridTd' ],      elements: 'elementsInContainer',  from: [ 'initial' ],                          conditions: [ 'isEmpty' ],                          to: 'placeholder',              actions: [ 'removeFor', 'createMock', 'showPlaceholder' ]   }
            { events: [ 'mouseEntersGridTd' ],      elements: 'elementsInContainer',  from: [ 'initial' ],                          conditions: [ 'isNonEmpty' ],                       to: 'mock',                     actions: [ 'removeFor', 'createMock' ]                      }
            { events: [ 'mouseExistsGridTd' ],      elements: 'elementsInContainer',  from: [ 'mock' ],                                                                                 to: 'initial',                  actions: [ 'removeMock' ]                                   }
            { events: [ 'mouseExistsGridTd' ],      elements: 'elementsInContainer',  from: [ 'placeholder' ],                                                                          to: 'initial',                  actions: [ 'hidePlaceholder', 'removeMock'  ]               }
            { events: [ 'click' ],                  elements: 'elementClosest',       from: [ 'placeholder', 'mock' ],                                                                  to: 'wait-xhr-to-edit',         actions: [ 'storeSeqNo' ]                                   }
            # This transition is for the case where in one server response we get two controls added. In the case
            # we want to make sure we end the edit on the first control, before we add the second one. If we don't
            # when moving the editor for the second control, the blur event from the first editor runs and gives us
            # a DOM Exception 8 on Chrome. See bugs #247 and #839, as well as
            # http://wiki.orbeon.com/forms/doc/contributor-guide/browser#TOC-Chrome-s-DOM-Exception-8
            { events: [ 'controlAdded' ],           elements: 'elementsAll',          from: [ 'edit' ],                                                                                 to: 'edit-done',                actions: [ 'endEdit', 'removeMock', 'fireEditDone' ]        }
            { events: [ 'controlAdded' ],           elements: 'labelsInContainer',    from: [ 'initial' ],                                                                              to: 'edit',                     actions: [ 'removeFor', 'createMock', 'startEdit' ]         }
            { events: [ 'ajaxResponse' ],           elements: 'elementsAll',          from: [ 'wait-xhr-to-edit' ],                 conditions: [ 'isNextSeqNo' ],                      to: 'edit',                     actions: [ 'startEdit' ]                                    }
            { events: [ 'enterKey', 'lostFocus' ],  elements: 'elementClosest',       from: [ 'edit' ],                                                                                 to: 'edit-done',                actions: [ 'endEdit', 'removeMock', 'fireEditDone' ]        }
            { events: [ 'editDone' ],               elements: 'elementsAll',          from: [ 'edit-done' ],                        conditions: [ 'pointerOutsideCell' ],               to: 'initial'                                                                               }
            { events: [ 'editDone' ],               elements: 'elementsAll',          from: [ 'edit-done' ],                        conditions: [ 'pointerInsideCell', 'isEmpty' ],     to: 'placeholder-after-edit',   actions: [ 'storeSeqNo', 'createMock', 'showPlaceholder' ]  }
            { events: [ 'editDone' ],               elements: 'elementsAll',          from: [ 'edit-done' ],                        conditions: [ 'pointerInsideCell', 'isNonEmpty' ],  to: 'mock',                     actions: [ 'createMock' ]                                   }
            { events: [ 'ajaxResponse' ],           elements: 'elementsAll',          from: [ 'placeholder-after-edit' ],           conditions: [ 'isNextSeqNo' ],                      to: 'placeholder',              actions: [ 'showPlaceholder' ]                              }
        ]

        events:
            mouseEntersGridTd: (f) -> Builder.mouseEntersGridTdEvent.subscribe ({gridTd}) -> f $ gridTd
            mouseExistsGridTd: (f) -> Builder.mouseExitsGridTdEvent .subscribe ({gridTd}) -> f $ gridTd
            controlAdded: (f) -> Builder.controlAdded.add (containerId) -> f $ document.getElementById containerId
            ajaxResponse: (f) -> Events.ajaxResponseProcessedEvent.subscribe f
            click: (f) -> ($ document).click ({target}) -> f $ target
            enterKey: (f) -> ($ document).keypress ({target, which}) -> f $ target if which == 13
            lostFocus: (f) -> ($ document).focusout ({target}) ->
                f $ target if f$.is '.fb-edit-label, .fb-edit-hint', f$.parent $ target                                 # Make sure an editor got the focus. http://goo.gl/rkEAB
            editDone: (f) -> editDoneCallbacks.add f

        elements: do ->
            adjustContainerForRepeat = (container) ->
                grid =  f$.closest '.fr-grid', container
                if f$.is '.fr-repeat-single-row', grid
                    position = 1 + f$.length f$.prevAll container
                    thContainer = f$.nth position, f$.children f$.find 'tr.fr-dt-master-row', grid
                    f$.add thContainer, container
                else container
            elementsInContainerWithSelector = (container, selectors) -> f$.find (selectors.join ', '), adjustContainerForRepeat container
            elementsInContainer: (container) -> elementsInContainerWithSelector container, anyEditableSelector
            labelsInContainer: (container) ->
                labelLikeEditables = _.pick editables, ['label', 'button', 'link']
                elementsInContainerWithSelector container, _.pluck labelLikeEditables, 'selector'
            elementClosest: (element) -> f$.closest (anyEditableSelector.join ', '), element
            elementsAll: ->
                selectors = _.map anyEditableSelector, (s) -> '.fr-editable ' + s
                selectors = selectors.join ', '
                $ selectors

        conditions:
            isEmpty: isEmpty = (element) -> (f$.text editableInitialValue element) == ''
            isNonEmpty: (element) -> not isEmpty element
            pointerInsideCell: pointerInsideCell = do ->
                currentCell = null
                Builder.mouseEntersGridTdEvent.subscribe ({gridTd}) -> currentCell = gridTd
                Builder.mouseExitsGridTdEvent.subscribe () -> currentCell = null
                (element) -> currentCell? and f$.is '*', f$.closest currentCell, element
            pointerOutsideCell: (element) -> not pointerInsideCell element
            isNextSeqNo: (element) ->
                storeSeqNo = parseInt (f$.data 'seqNo', element)
                (currentSeqNo element) == storeSeqNo + 1

        actions:
            removeFor: (element) -> f$.removeAttr 'for', element                                                        # So on click on the label, the focus isn't set on the input on click
            showPlaceholder: (element) ->
                f$.addClass 'fb-label-hint-placeholder', element
                placeholderText = Controls.getCurrentValue _.first editablePlaceholderOutput element
                f$.text placeholderText, editablePlaceholderContainer element
            hidePlaceholder: (element) ->
                f$.removeClass 'fb-label-hint-placeholder', element
                f$.text '', editablePlaceholderContainer element
            createMock: editableDo 'createMock'
            removeMock: editableDo 'removeMock'
            storeSeqNo: (element) -> f$.data 'seqNo', (currentSeqNo element), element
            startEdit: (element) ->
                Builder.beforeAddingEditorCallbacks.fire element
                f$.removeClass 'fb-label-hint-placeholder', element
                f$.removeClass 'xforms-disabled', element                                                               # Remove disabled which we have on hint when their value is empty
                input = editableEditInput element
                f$.append input, f$.empty editablePlaceholderContainer element
                f$.show input
                htmlInput = f$.find 'input', input
                saveButton = $ '#fr-save-button button'                                                                 # HACK: Focus on something else (we know we have a Save button in Form Builder)
                (setFocus saveButton).then -> setFocus htmlInput
            endEdit: (element) ->
                input = editableEditInput element
                f$.append input, $ '.fb-cell-editor'                                                                    # Move editor out of grid, so it doesn't get removed by HTML replacements
                newValue = Controls.getCurrentValue input[0]
                f$.text newValue, editableInitialValue element                                                          # Restore text under label/hint
            fireEditDone: -> editDoneCallbacks.fire()


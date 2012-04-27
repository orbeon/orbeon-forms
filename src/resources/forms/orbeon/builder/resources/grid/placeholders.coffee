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

# Show/hide placeholders for label and hint

$ ->
    f$ = do ->
        jQueryObject = $ '<div>'
        result = {}
        for m in _.methods ($ '<div>')
            do (m) ->
                result[m] = (params...) ->
                    o = params.pop()
                    jQueryObject[m].apply o, params
        result

    class Editable
        constructor: (@element) ->
        instances: (container) ->
            elements = container.find @selector
            new @constructor $ e for e in elements
        showPlaceholder: ->
            if not _.first @valueContainer().contents()
                @element.addClass 'fb-label-hint-placeholder'
                placeholderContainer = @initPlaceholderContainer()
                placeholderText = Controls.getCurrentValue _.first $ @placeholderOutput
                placeholderContainer.text placeholderText
        hidePlaceholder: ->
            if @element.hasClass 'fb-label-hint-placeholder'
                @element.removeClass 'fb-label-hint-placeholder'
                @cleanPlaceholderContainer()

    class LabelHint extends Editable
        valueContainer: -> @element
        initPlaceholderContainer: -> @element
        cleanPlaceholderContainer: -> @element.contents().filter(-> @nodeType == @TEXT_NODE).detach()

    class Label extends LabelHint
        selector: '.xforms-label'
        placeholderOutput: '#fb-enter-label'

    class Hint extends LabelHint
        selector: '.xforms-hint'
        placeholderOutput: '#fb-enter-hint'

    class Button extends Editable
        selector: '.xforms-trigger-appearance-full'
        placeholderOutput: '#fb-enter-label'
        valueContainer: -> @element.children 'button'
        initPlaceholderContainer: ->
            # Hide actual button
            f$.hide f$.find 'button', @element
            # Create mock button
            f$.append (mockLabel = $ '<span>'), f$.appendTo @element, ($ '<div class="xforms-mock-button">')
            mockLabel
        cleanPlaceholderContainer: ->
            f$.show f$.find 'button', @element
            f$.remove f$.find '.xforms-mock-button', @element

    Editables = [Label, Hint, Button]
    doOnEditables = (method, container) ->
        instances = (E::instances container  for E in Editables)
        instances = [].concat instances... # flatten
        method.call i for i in instances

    gridTdWithMouse = null

    # Show placeholder on mouse entering, and remove them on mouse exiting
    Builder.mouseEntersGridTdEvent.subscribe ({gridTd}) -> doOnEditables Editable::showPlaceholder, $ gridTd
    Builder.mouseExitsGridTdEvent .subscribe ({gridTd}) -> doOnEditables Editable::hidePlaceholder, $ gridTd

    # Remove placeholder class when start editing and show it when done editing
    Builder.startLabelHintEditEvent.subscribe ({labelHint}) -> hidePlaceholder labelHint
    Builder.endLabelHintEditEvent.subscribe ({labelHint}) ->
        Events.runOnNext Events.ajaxResponseProcessedEvent, ->
            isLabelHintInCurrentGridTd =
                if gridTdWithMouse? false
                else
                    parentGridTd = YD.getAncestorByClassName labelHint, 'fr-grid-td'
                    parentGridTd? and parentGridTd == gridTdWithMouse
            showPlaceholder labelHint if isLabelHintInCurrentGridTd


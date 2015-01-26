###
Copyright (C) 2014 Orbeon, Inc.

This program is free software; you can redistribute it and/or modify it under the terms of the
GNU Lesser General Public License as published by the Free Software Foundation; either version
2.1 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
See the GNU Lesser General Public License for more details.

The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
###

$ = ORBEON.jQuery
Builder = ORBEON.Builder

LabelHintSelector = '.fr-editable .xforms-label, .fr-editable .xforms-hint, .fr-editable .xforms-text'
ControlSelector = '.xforms-control, .xbl-component'

# Heuristic to close the editor based on click and focus events
clickOrFocus = ({target}) ->
    target = $(target)
    eventOnEditor = target.closest('.fb-label-editor').is('*')
    eventOnControlLabel =
        # Click on label or element inside label
        (target.is(LabelHintSelector) || target.parents(LabelHintSelector).is('*')) &&
        # Only interested in labels in the "editor" portion of FB
        target.parents('.fb-main').is('*')
    Builder.resourceEditorEndEdit() unless eventOnEditor or eventOnControlLabel

$ ->
    $(document).on('click', clickOrFocus)
    $(document).on('focusin', clickOrFocus)

    # Click on label/hint
    $('.fb-main').on 'click', LabelHintSelector, ({currentTarget}) ->
        # Close current editor, if there is one open
        Builder.resourceEditorEndEdit() if ! _.isNull(Builder.resourceEditorCurrentControl)
        Builder.resourceEditorCurrentLabelHint = $(currentTarget)
        # Find control for this label
        th = Builder.resourceEditorCurrentLabelHint.parents('th')
        Builder.resourceEditorCurrentControl =
            if th.is('*')
                # Case of a repeat
                trWithControls = th.parents('table').find('tbody tr.fb-grid-tr').first()
                tdWithControl = trWithControls.children(':nth-child(' + (th.index() + 1) + ')')
                tdWithControl.find(ControlSelector)
            else
                Builder.resourceEditorCurrentLabelHint.parents(ControlSelector).first()
        Builder.resourceEditorStartEdit()

    # New control added
    Builder.controlAdded.add (containerId) ->
        container = $(document.getElementById(containerId))
        Builder.resourceEditorCurrentControl = container.find(ControlSelector)
        repeat = container.parents('.fr-repeat').first()
        Builder.resourceEditorCurrentLabelHint =
            if   repeat.is('*') \
            then repeat.find('thead tr th:nth-child(' + (container.index() + 1) + ') .xforms-label')
            else container.find('.xforms-label').first()
        if (Builder.resourceEditorCurrentLabelHint.is('*'))
            Builder.resourceEditorStartEdit()

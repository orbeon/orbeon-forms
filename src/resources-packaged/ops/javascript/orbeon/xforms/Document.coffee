# Copyright (C) 2012 Orbeon, Inc.
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
AjaxServer = ORBEON.xforms.server.AjaxServer
Controls = ORBEON.xforms.Controls

# Functions designed to be called from JavaScript code embedded in forms
ORBEON.xforms.Document = _.tap {}, (Document) -> _.extend Document,

    # Reference: http://www.w3.org/TR/xforms/slice10.html#action-dispatch
    # Use the first XForms form on the page when no form is provided
    dispatchEvent: (args) ->

        # Backward compatibility: support arguments passed as a series of parameters
        if arguments.length > 1
            oldParams = ['targetId', 'eventName', 'form', 'bubbles', 'cancelable', 'incremental', 'ignoreErrors']
            newArgs = {}
            newArgs[name] = arguments[i] for name, i in oldParams
            args = newArgs

        # Use first XForms form, if none is specified
        args.form = _.head($(document.forms).filter('.xforms-form')) unless args.form?
        event = new AjaxServer.Event args
        AjaxServer.fireEvents [event], incremental? and incremental

    _findControl: (controlIdOrElem, formElem) ->

        [controlId, resolvedControl] =
            if _.isString(controlIdOrElem)
                givenControlId = controlIdOrElem

                formId =
                    if _.isElement(formElem)
                        formElem.id
                    else
                        $(document.forms).filter('.xforms-form')[0].id

                ns = ORBEON.xforms.Globals.ns[formId]

                # For backward compatibility, handle the case where the id is already prefixed.
                # This is not great as we don't know for sure whether the control starts with a namespace, e.g. `o0`,
                # `o1`, etc. It might be safer to disable the short namespaces feature because of this.
                namespacedControlId =
                    if (givenControlId.indexOf(ns) == 0)
                        givenControlId
                    else
                        ns + givenControlId

                [givenControlId, document.getElementById(namespacedControlId)]
            else
                [controlIdOrElem.id, controlIdOrElem]

        if not resolvedControl?
            throw "Cannot find control id #{controlId}"

        if Controls.isInRepeatTemplate(resolvedControl)
            throw 'Cannot set the value of a repeat template'

        resolvedControl

    # Returns the value of an XForms control.
    # @param {String | HTMLElement} control
    # @param {HTMLElement} form
    getValue: (controlIdOrElem, form) ->
        Controls.getCurrentValue(Document._findControl(controlIdOrElem, form))

    # Set the value of an XForms control.
    # @param {String | HTMLElement} control
    # @param {String} newValue
    # @param {HTMLElement} form
    setValue: (controlIdOrElem, newValue, form) ->

        # Cast to String if caller passes a non-string value "by mistake" (or not, like a Number)
        newValue = newValue.toString()

        control = Document._findControl(controlIdOrElem, form)

        if $(control).is('.xforms-output, .xforms-upload')
            throw 'Cannot set the value of an output or upload control'

        # Directly change the value in the UI without waiting for an Ajax response
        Controls.setCurrentValue(control, newValue)

        # And also fire server event
        event = new AjaxServer.Event null, control.id, newValue, "xxforms-value"
        AjaxServer.fireEvents([event], false)

    # Returns whether the document is being reloaded.
    isReloading: -> ORBEON.xforms.Globals.isReloading

    # Exposes to JavaScript the current index of all the repeats. This is the JavaScript equivalent to the
    # XForms XPath function index(repeatId).
    getRepeatIndex: (repeatId) -> ORBEON.xforms.Globals.repeatIndexes[repeatId]

    _clientStateToMap: (formId) ->
        clientState = ORBEON.xforms.Globals.formClientState[formId].value
        if clientState == "" then {} else
            keyValues = clientState.split '&'
            [keys, values] = _.map [0, 1], (start) -> _.values _.pick keyValues, (_.range start, keyValues.length, 2)
            _.tap {}, (result) -> _.each (_.zip keys, values), ([key, value]) -> result[key] = decodeURIComponent value

    _mapToClientState: (formId, map) ->
        clientState = ORBEON.xforms.Globals.formClientState[formId]
        stateArray = _.flatten _.map (_.keys map), (key) -> [key, encodeURIComponent map[key]]
        clientState.value = stateArray.join '&'

    # Gets a value stored in the hidden client-state input field.
    getFromClientState: (formId, key) ->
        map = Document._clientStateToMap formId
        map[key]

    # Returns a value stored in the hidden client-state input field.
    storeInClientState: (formId, key, value) ->
        map = Document._clientStateToMap formId
        map[key] = value
        Document._mapToClientState formId, map

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

AjaxServer.eventCreated = $.Callbacks()

AjaxServer.Event = (args) ->

    supportedArgs = ->
        form:               object ->
                                      if   _.isString args.targetId
                                      then Controls.getForm document.getElementById args.targetId
                                      else null
        targetId:           string -> null
        value:              string -> null
        eventName:          string -> null
        bubbles:            bool   -> null
        cancelable:         bool   -> null
        ignoreErrors:       bool   -> null
        showProgress:       bool   -> true
        progressMessage:    string -> null
        additionalAttribs:  object -> null
        properties:         object -> {}

    # Backward compatibility: support arguments passed as a series of parameters
    if arguments.length > 1
        oldParams = ['form', 'targetId', 'value', 'eventName', 'bubbles', 'cancelable',
                     'ignoreErrors', 'showProgress', 'progressMessage', 'additionalAttribs']
        newArgs = {}
        newArgs[name] = arguments[i] for name, i in oldParams
        args = newArgs

    # Set event properties based on `supportedArgs` defined above
    type = (isType) => (alternative) => (name) =>
        @[name] = if isType args[name] then args[name] else alternative()
    object = type _.isObject
    string = type _.isString
    bool   = type _.isBoolean
    setDefault name for name, setDefault of supportedArgs()

    # Notify listeners, given them a chance to, say, add properties to the event
    AjaxServer.eventCreated.fire @
    # Make sure we don't return anything, since this is a constructor
    return

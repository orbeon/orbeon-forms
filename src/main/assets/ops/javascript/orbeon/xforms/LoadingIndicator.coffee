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
Globals = ORBEON.xforms.Globals
YD = YAHOO.util.Dom
OD = ORBEON.util.Dom
Utils = ORBEON.util.Utils
Overlay = YAHOO.widget.Overlay
Properties = ORBEON.util.Properties
Connect = YAHOO.util.Connect
Events = ORBEON.xforms.Events

class LoadingIndicator

    constructor: (@form) ->
        @shownCounter = 0

        # Don't show NProgress spinner
        NProgress.configure({ showSpinner: false })

        # Extract whether this is an upload from the object passed to the callback
        # This, because we only want the loading indicator to show for Ajax request, not uploads,
        # for which we have a different way of indicating the upload is in progress.
        isUpload = (argument) -> ! (_.isUndefined(argument)) and _.isBoolean(argument.isUpload) and argument.isUpload

        # When an Ajax call starts, we might want to show the indicator
        Connect.startEvent.subscribe (type, args) =>
            isAjax = not isUpload(args[1])
            if isAjax and @nextConnectShow
                if @shownCounter == 0
                    # Show the indicator after a delay
                    afterDelay = =>
                        @shownCounter++
                        @show() if @shownCounter == 1
                    _.delay afterDelay, Properties.delayBeforeDisplayLoading.get()
                else
                    # Indicator already shown, just increment counter
                    @shownCounter++

        # When an Ajax call ends, we might want to hide the indicator
        requestEnded = (argument) =>
            isAjax = not isUpload(argument)
            if isAjax and @nextConnectShow
                # Defer hiding the indicator to give a chance to next request to start, so we don't flash the indicator
                _.defer =>
                    @shownCounter--
                    @hide() if @shownCounter == 0
                # Reset show and message
                @nextConnectShow = true

        Events.ajaxResponseProcessedEvent.subscribe (type, args) ->
            requestEnded(args[0])
        Connect.failureEvent.subscribe (type, args) ->
            # Only called for Ajax requests; YUI Connect doesn't call `failure` function for uploads
            requestEnded(args[0])

    setNextConnectProgressShown: (shown) ->
        @nextConnectShow = shown

    runShowing: (f) ->
        @shownCounter++
        @show()
        _.defer =>
            f()
            @shownCounter--
            @hide() if @shownCounter == 0

    # Actually shows the loading indicator (no delay or counter)
    show: ->
        NProgress.start()

    # Actually hides the loading indicator (no counter)
    hide: ->
        if not Globals.loadingOtherPage
            NProgress.done()


ORBEON.xforms.LoadingIndicator = LoadingIndicator

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

        # For now hide the loading indicator (use jQuery to fix #403)
        @loadingSpan = (f$.children '.xforms-loading-loading', $ @form)[0]
        @loadingSpan.style.display = "none"

        # Differ creation of the overlay to the first time we need it
        @loadingOverlay = null
        # On scroll or resize, move the overlay so it stays visible
        Overlay.windowScrollEvent.subscribe => @_updateLoadingPosition()
        Overlay.windowResizeEvent.subscribe => @_updateLoadingPosition()

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
                        @show(@nextConnectMessage) if @shownCounter == 1
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
                @nextConnectMessage = DEFAULT_LOADING_TEXT

        Events.ajaxResponseProcessedEvent.subscribe (type, args) ->
            requestEnded(args[0])
        Connect.failureEvent.subscribe (type, args) ->
            # Only called for Ajax requests; YUI Connect doesn't call `failure` function for uploads
            requestEnded(args[0])

    setNextConnectProgressShown: (shown) ->
        @nextConnectShow = shown
    setNextConnectProgressMessage: (message) -> @nextConnectMessage = message

    runShowing: (f) ->
        @shownCounter++
        @show()
        _.defer =>
            f()
            @shownCounter--
            @hide() if @shownCounter == 0

    # Actually shows the loading indicator (no delay or counter)
    show: (message) ->
        @_initLoadingOverlay()
        message ?= DEFAULT_LOADING_TEXT
        OD.setStringValue @loadingOverlay.element, message
        @loadingOverlay.show()
        # Get loading indicator to show above other dialogs or elements
        $(@loadingOverlay.element).css('z-index', Globals.lastDialogZIndex + 1)
        @_updateLoadingPosition()

    # Actually hides the loading indicator (no counter)
    hide: ->
        if not Globals.loadingOtherPage
            @loadingOverlay.cfg.setProperty "visible", false

    _initLoadingOverlay: () ->
        # Initialize @loadingOverlay if not done already
        if not @loadingOverlay?
            # On scroll, we might need to move the overlay
            @loadingSpan.style.display = "block"
            @initialRight = YD.getViewportWidth() - YD.getX @loadingSpan
            @initialTop = YD.getY @loadingSpan
            # Create a YUI overlay after showing the span
            @loadingOverlay = new YAHOO.widget.Overlay @loadingSpan, { visible: false, monitorresize: true }
            Utils.overlayUseDisplayHidden @loadingOverlay
            @loadingSpan.style.right = "auto"

    _updateLoadingPosition: () ->
        @_initLoadingOverlay()
        @loadingOverlay.cfg.setProperty "x", do =>
            # Keep the distance to the right border of the viewport the same
            scrollX = YD.getDocumentScrollLeft()
            scrollX + YD.getViewportWidth() - @initialRight
        @loadingOverlay.cfg.setProperty "y", do =>
            scrollY = YD.getDocumentScrollTop()
            if scrollY + Properties.loadingMinTopPadding.get() > @initialTop
            # Place indicator at a few pixels from the top of the viewport
            then scrollY + Properties.loadingMinTopPadding.get()
            # Loading is visible at its initial position, so leave it there
            else @initialTop

ORBEON.xforms.LoadingIndicator = LoadingIndicator

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

Globals = ORBEON.xforms.Globals
YD = YAHOO.util.Dom
OD = ORBEON.util.Dom
Utils = ORBEON.util.Utils
Overlay = YAHOO.widget.Overlay
Properties = ORBEON.util.Properties
Connect = YAHOO.util.Connect
Events = ORBEON.xforms.Events

class LoadingIndicator

    constructor: (form) ->
        @shownCounter = 0
        # Span for loading indicator is a child of the form element
        loadingSpan = _.detect form.childNodes, (node) -> YD.hasClass node, "xforms-loading-loading"
        # On scroll, we might need to move the overlay
        loadingSpan.style.display = "block"
        @initialRight = YD.getViewportWidth() - YD.getX loadingSpan
        @initialTop = YD.getY loadingSpan
        Overlay.windowScrollEvent.subscribe => @_updateLoadingPosition()
        # Create a YUI overlay after showing the span
        @loadingOverlay = new YAHOO.widget.Overlay loadingSpan, { visible: false, monitorresize: true }
        Utils.overlayUseDisplayHidden @loadingOverlay
        loadingSpan.style.right = "auto"

        # When an Ajax call starts, we might want to show the indicator
        Connect.startEvent.subscribe =>
            if @nextConnectShow
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
        Events.ajaxResponseProcessedEvent.subscribe =>
            if @nextConnectShow
                # Defer hiding the indicator to give a chance to next request to start, so we don't flash the indicator
                _.defer =>
                    @shownCounter--
                    @hide() if @shownCounter == 0
            # Reset show and message
            @nextConnectShow = true
            @nextConnectMessage = DEFAULT_LOADING_TEXT

    setNextConnectProgressShown: (shown) -> @nextConnectShow = shown
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
        message ?= DEFAULT_LOADING_TEXT
        OD.setStringValue @loadingOverlay.element, message
        @loadingOverlay.show()
        @_updateLoadingPosition()

    # Actually hides the loading indicator (no counter)
    hide: ->
        if not Globals.loadingOtherPage
            @loadingOverlay.cfg.setProperty "visible", false

    _updateLoadingPosition: () ->
        @loadingOverlay.cfg.setProperty "x", do =>
            # Keep the distance to the right border of the viewport the same
            scrollX = document.documentElement.scrollLeft ? document.body.scrollLeft
            scrollX + YD.getViewportWidth() - @initialRight
        @loadingOverlay.cfg.setProperty "y", do =>
            scrollY = document.documentElement.scrollTop ? document.body.scrollTop
            if scrollY + Properties.loadingMinTopPadding.get() > @initialTop
            # Place indicator at a few pixels from the top of the viewport
            then scrollY + Properties.loadingMinTopPadding.get()
            # Loading is visible at its initial position, so leave it there
            else @initialTop

ORBEON.xforms.LoadingIndicator = LoadingIndicator
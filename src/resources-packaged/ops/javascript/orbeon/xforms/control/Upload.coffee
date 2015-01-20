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

OD = ORBEON.util.Dom
ExecutionQueue = ORBEON.util.ExecutionQueue
Properties = ORBEON.util.Properties
UploadServer = ORBEON.xforms.server.UploadServer
Control = ORBEON.xforms.control.Control
Page = ORBEON.xforms.Page
Event = YAHOO.util.Event
YD = YAHOO.util.Dom
ProgressBar = YAHOO.widget.ProgressBar

class Upload extends Control

    yuiProgressBar: null

    # Creates markup for loading progress indicator element, if necessary
    init: (container) ->
        super container
        if not this.getElementByClassName "xforms-upload-progress"
            # Add markup to the DOM
            uploadProgressSpan = document.createElement "span"
            uploadProgressSpan.innerHTML = '<span class="xforms-upload-progress-bar"></span>
                <a href="#" class="xforms-upload-cancel">Cancel</a>'
            OD.setAttribute uploadProgressSpan, "class", "xforms-upload-progress"
            inputSelect = this.getElementByClassName "xforms-upload-select"
            YD.insertAfter uploadProgressSpan, inputSelect

            # Register listener on the cancel link
            cancelAnchor = this.getElementByClassName "xforms-upload-cancel"
            Event.addListener cancelAnchor, "click", this.cancel, this, true

    # The change event corresponds to a file being selected. This will queue an event to submit this file in the
    # background  as soon as possible (pseudo-Ajax request).
    change: ->
        # Start at 10, so the progress bar doesn't appear to be stuck at the beginning
        UploadServer.uploadEventQueue.add {form: this.getForm(), upload: this},
            Properties.delayBeforeIncrementalRequest.get(), ExecutionQueue.MIN_WAIT

    # This method is called when the server sends us a progress update for this upload control. If the upload was
    # interrupted we resume it and otherwise update the progress indicator to reflect the new value we got from the
    # server.
    #
    # @param {number} received     Number of bytes the server received so far
    # @param {number} expected     Total number of bytes the server expects
    progress: (state, received, expected) ->
        switch state
            when "interrupted" then UploadServer.cancel(true, 'xxforms-upload-error')
            else this.yuiProgressBar.set "value", 10 + 100 * received / expected if this.yuiProgressBar && received && expected

    # Called by UploadServer when the upload for this control is finished.
    uploadDone: () ->
        ajaxResponseProcessed = () =>
            ORBEON.xforms.Events.ajaxResponseProcessedEvent.unsubscribe ajaxResponseProcessed
            # However, if we don't (thus the progress indicator is still shown), this means some XForms reset the file name
            if YD.hasClass this.container, "xforms-upload-state-progress"
                # So switch back to the file selector, as we won't get a file name anymore
                @setState "empty"
        # After the file is uploaded, in general at the next Ajax response, we get the file name
        ORBEON.xforms.Events.ajaxResponseProcessedEvent.subscribe ajaxResponseProcessed

    # When users press on the cancel link, we cancel the upload, delegating this to the UploadServer.
    cancel: (event) ->
        Event.preventDefault event
        UploadServer.cancel(true,  'xxforms-upload-cancel')

    # Sets the state of the control to either "empty" (no file selected, or upload hasn't started yet), "progress"
    # (file is being uploaded), or "file" (a file has been uploaded).
    #
    # @param {String} state
    setState: (state) ->
        STATES = ["empty", "progress", "file"]
        # Check the state we got is one of the recognized states
        throw "Invalid state " + state if not _.contains STATES, state
        # Switch class
        YD.removeClass this.container, "xforms-upload-state-" + s for s in STATES
        YD.addClass this.container, "xforms-upload-state-" + state

        if state == "progress"
            # Create or recreate progress bar
            progressBarSpan = this.getElementByClassName "xforms-upload-progress-bar"
            progressBarSpan.innerHTML = ""
            this.yuiProgressBar = new ProgressBar { width: 100, height: 10, value: 0, minValue: 0, maxValue: 110, anim: true }
            this.yuiProgressBar.get("anim").duration = Properties.delayBeforeUploadProgressRefresh.get() / 1000 * 1.5
            this.yuiProgressBar.render this.getElementByClassName "xforms-upload-progress-bar"
            this.yuiProgressBar.set "value", 10

    # Clears the upload field by recreating it.
    clear: ->
        inputElement = this.getElementByClassName "xforms-upload-select", null, this.container
        parentElement = inputElement.parentNode;
        newInputElement = document.createElement "input"
        YAHOO.util.Dom.addClass newInputElement, inputElement.className
        newInputElement.id = ORBEON.util.Utils.appendToEffectiveId this.container.id, XF_COMPONENT_SEPARATOR + "xforms-input"
        newInputElement.setAttribute "type", inputElement.type
        newInputElement.setAttribute "name", inputElement.name
        newInputElement.setAttribute "size", inputElement.size
        newInputElement.setAttribute "unselectable", "on" # The server sets this, so we have to set it again
        newInputElement.setAttribute "accept", inputElement.accept
        parentElement.replaceChild newInputElement, inputElement

Page.registerControlConstructor Upload, (container) -> YD.hasClass container, "xforms-upload"
ORBEON.xforms.control.Upload = Upload

/**
 * Copyright (C) 2011 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
(function() {


    /**
     * Corresponds to <xforms:input> bound to node of type xs:anyURI or xs:base64Binary.
     */
    ORBEON.xforms.control.Upload = function() {};

    var OD = ORBEON.util.Dom;
    var ExecutionQueue = ORBEON.util.ExecutionQueue;
    var Properties = ORBEON.util.Properties;
    var UploadServer = ORBEON.xforms.server.UploadServer;
    var Control = ORBEON.xforms.control.Control;
    var Upload = ORBEON.xforms.control.Upload;
    var Page = ORBEON.xforms.Page;
    var Event = YAHOO.util.Event;
    var YD = YAHOO.util.Dom;

    Upload.prototype = new Control();

    Upload.prototype.init = function(container) {
        // Call super-class init
        Control.prototype.init.call(this, container);

        // Create loading progress indicator element, if we don't already have one
        if (! this.getElementByClassName("xforms-upload-progress")) {

            // Add markup to the DOM
            var uploadProgressSpan = document.createElement("span");
            uploadProgressSpan.innerHTML = '<span class="xforms-upload-spinner"></span>'
                + '<a href="#" class="xforms-upload-cancel">Cancel</a>';
            OD.setAttribute(uploadProgressSpan, "class", "xforms-upload-progress");
            var inputSelect = this.getElementByClassName("xforms-upload-select");
            YD.insertAfter(uploadProgressSpan, inputSelect);

            // Register listener on the cancel link
            var cancelAnchor = this.getElementByClassName("xforms-upload-cancel");
            Event.addListener(cancelAnchor, "click", this.cancel, this, true);
        }
    };

    /**
     * The change event corresponds to a file being selected. This will queue an event to submit this file in the
     * background  as soon as possible (pseudo-Ajax request).
     */
    Upload.prototype.change = function() {
        UploadServer.uploadEventQueue.add({form: this.getForm(), upload: this},
                Properties.delayBeforeIncrementalRequest.get(), ExecutionQueue.MIN_WAIT);
    };

    /**
     * This method is called when we the server sends us a progress update for this upload control. Here we update
     * the progress indicator to reflect the new value we got from the server.
     *
     * @param {number} received     Number of bytes the server received so far
     * @param {number} expected     Total number of bytes the server expects
     */
    Upload.prototype.progress = function(received, expected) {
        // TODO
    };

    /**
     * When users press on the cancel link, we cancel the upload, delegating this to the UploadServer.
     */
    Upload.prototype.cancel = function(event) {
        Event.preventDefault(event);
        UploadServer.cancel();
    };

    /**
     * Sets the state of the control to either "empty" (no file selected, or upload hasn't started yet), "progress"
     * (file is being uploaded), or "file" (a file has been uploaded).
     *
     * @param {String} state
     */
    Upload.prototype.setState = function(state) {
        var STATES = ["empty", "progress", "file"];
        // Check the state we got is one of the recognized states
        if (! _.contains(STATES, state)) throw "Invalid state " + state;
        // Remove any existing state class
        _.each(STATES, _.bind(function(state) { YD.removeClass(this.container, "xforms-upload-state-" + state); }, this));
        // Add the relevant state class
        YD.addClass(this.container, "xforms-upload-state-" + state);
    };

    /**
     * Clears the upload field by recreating it.
     */
    Upload.prototype.clear = function() {
        var inputElement = YD.getElementsByClassName("xforms-upload-select", null, this.container)[0];
        var parentElement = inputElement.parentNode;
        var newInputElement = document.createElement("input");
        YAHOO.util.Dom.addClass(newInputElement, inputElement.className);
        newInputElement.setAttribute("type", inputElement.type);
        newInputElement.setAttribute("name", inputElement.name);
        newInputElement.setAttribute("size", inputElement.size);
        newInputElement.setAttribute("unselectable", "on");// the server sets this, so we have to set it again
        parentElement.replaceChild(newInputElement, inputElement);
    };

    Page.registerControlConstructor(Upload,  function(container) {
        return YD.hasClass(container, "xforms-upload");
    });

})();
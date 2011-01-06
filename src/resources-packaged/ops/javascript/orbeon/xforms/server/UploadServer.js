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
     * Upload server is a singleton.
     */
    ORBEON.xforms.server.UploadServer = {};

    var ExecutionQueue = ORBEON.util.ExecutionQueue;
    var UploadServer = ORBEON.xforms.server.UploadServer;
    var AjaxServer = ORBEON.xforms.server.AjaxServer;
    var Connect = YAHOO.util.Connect;

    // While an upload is in progress, the YUI object for that connection
    UploadServer.yuiConnection = null;
    // While an upload is in progress, the events for the fields being uploaded
    UploadServer.processingEvents = [];
    // While an upload is in progress, the events for the fields that are left to be uploaded
    UploadServer.remainingEvents = [];
    // While an upload is in progress, call-back function when we are done processing all the events
    UploadServer.executionQueueDone = null;

    /**
     * Method called by YUI when the upload ends successfully.
     *
     * @private
     */
    UploadServer.uploadSuccess = function(o) {
        // Clear server events that were sent to the server (why? is this still needed?)
        ORBEON.xforms.Globals.formServerEvents[form.id].value = "";
        // Clear upload fields we just uploaded, otherwise subsequent uploads will upload the same data again
        _.each(this.processingEvents, function(event) { event.upload.clear(); });
        // The Ajax response typically contains information about each file (name, size, etc)
        AjaxServer.handleResponseAjax(o);
        // Are we done, or do we still need to handle events for other forms?
        this.continueWithRemainingEvents();
    };

    /**
     * Once we are done processing the events (either because the uploads have been completed or canceled), handle the
     * remaining events (which were for other forms).
     */
    UploadServer.continueWithRemainingEvents = function() {
        if (this.remainingEvents.length == 0) this.executionQueueDone();
        else UploadServer.asyncUploadRequest(this.remainingEvents, this.executionQueueDone);
    };

    /**
     * Run background form post do to the upload. This method is called by the ExecutionQueue when it determines that
     * the upload can be done.
     *
     * @private
     */
    UploadServer.asyncUploadRequest = function(events, done) {
        this.executionQueueDone = done;
        // Upload is done by form, so we pick the form of the first event as the one for which we handle the events
        var form = events[0].form;
        this.processingEvents = _.filter(events, function(event) { return event.form = form; });
        this.remainingEvents = _.filter(events, function(event) { return event.form != form; });
        // Switch the upload to progress state, so users can't change the file and know the upload is in progress
        _.each(this.processingEvents, function(event) { event.upload.setState("progress"); });
        // Tell server we're starting uploads
        var uploadStartEvents = _.map(this.processingEvents, function(event) {
            return new AjaxServer.Event(null, event.upload.container.id, null, null, "xxforms-upload-start"); });
        AjaxServer.fireEvents(uploadStartEvents, false);
        // Trigger actual upload through a form POST
        Connect.setForm(form, true, true);
        this.yuiConnection = Connect.asyncRequest("POST", ORBEON.xforms.Globals.xformsServerURL[form.id], {
            upload: _.bind(this.uploadSuccess, this),
            failure: _.bind(AjaxServer.retryRequestAfterDelay, AjaxServer, _.bind(UploadServer.asyncUploadRequest, UploadServer, events, done))
        });
    };

    /**
     * Cancels the uploads currently in process. This is called by the control, which delegates canceling to the
     * UploadServer as it can't know about other controls being "uploaded" at the same time. Indeed, we can have
     * uploads for multiple files at the same time, and for each one of the them, we want to clear the upload field,
     * and switch back to the empty state so users can again select a file to upload.
     */
    UploadServer.cancel = function() {
        Connect.abort(UploadServer.yuiConnection);
        var uploadCancelEvents =_.map(this.processingEvents, function(event) {
            event.upload.clear();
            event.upload.setState("empty");
            return new AjaxServer.Event(null, event.upload.container.id, null, null, "xxforms-upload-cancel");
        });
        AjaxServer.fireEvents(uploadCancelEvents, false);
        this.continueWithRemainingEvents();
    };

    /**
     * Queue for upload events.
     * @private
     */
    UploadServer.uploadEventQueue = new ExecutionQueue(_.bind(UploadServer.asyncUploadRequest, UploadServer));
})();
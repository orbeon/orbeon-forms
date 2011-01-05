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

    /**
     * Object
     * @private
     */
    UploadServer.yuiConnection = null;

    /**
     * Run background form post (upload)
     */
    UploadServer.asyncUploadRequest = function(events, done) {

        // Upload is done by form, so we pick the form of the first event as the one for which we handle the events
        var form = events[0].form;
        var thisFormEvents = _.filter(events, function(event) { return event.form = form; });
        var otherFormsEvents = _.filter(events, function(event) { return event.form != form; });

        // Switch the upload to progress state, so users can't change the file and know the upload is in progress
        _.each(thisFormEvents, function(event) { event.upload.setState("progress"); });
        // Tell server we're starting uploads
        var uploadStartEvents = _.map(thisFormEvents, function(event) {
            return new AjaxServer.Event(null, event.upload.container.id, null, null, "xxforms-upload-start"); });
        AjaxServer.fireEvents(uploadStartEvents, false);

        // Trigger actual upload through a form POST
        Connect.setForm(form, true, true);
        this.yuiConnection = Connect.asyncRequest("POST", ORBEON.xforms.Globals.xformsServerURL[form.id], {
            upload: function(o) {
                // Clear server events that were sent to the server (why? is this still needed?)
                ORBEON.xforms.Globals.formServerEvents[form.id].value = "";
                // Clear upload fields we just uploaded, otherwise subsequent uploads will upload the same data again
                _.each(thisFormEvents, function(event) { event.upload.clear(); });
                AjaxServer.handleResponseAjax(o);
                // Are we done, or do we still need to handle events for other forms?
                if (otherFormsEvents.length == 0) done();
                else UploadServer.asyncUploadRequest(otherFormsEvents, done);
            },
            failure: function() {
                console.log("Upload failure");
                AjaxServer.retryRequestAfterDelay(_.bind(UploadServer.asyncUploadRequest, UploadServer, events, done));
            }
        });
    };

    /**
     * Queue for upload events.
     * @private
     */
    UploadServer.uploadEventQueue = new ExecutionQueue(_.bind(UploadServer.asyncUploadRequest, UploadServer));
})();
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

    /**
     * Run background form post (upload)
     */
    UploadServer.asyncUploadRequest = function(events, done) {

        // Potentially, if multiple forms are on the same page, and users simultaneously select a file in both forms,
        // then we could have a problem here, as we'll only upload the first form. But we'll be optimistic and
        // pretend this doesn't happen.
        var form = events[0].form;

        // Switch the upload to progress state, so users can't change the file and know the upload is in progress
        _.each(events, function(event) { event.upload.setState("progress"); });
        // Tell server we're starting uploads
        var uploadStartEvents = _.map(events, function(event) { return new ORBEON.xforms.server.AjaxServer.Event(null, event.upload.container.id, null, null, "xxforms-upload-start"); });
        ORBEON.xforms.server.AjaxServer.fireEvents(uploadStartEvents, false);

        // Trigger actual upload through a form POST
        YAHOO.util.Connect.setForm(form, true, true);
        YAHOO.util.Connect.asyncRequest("POST", ORBEON.xforms.Globals.xformsServerURL[form.id], {
            upload: function(o) {
                // Clear server events that were sent to the server (why? is this still needed?)
                ORBEON.xforms.Globals.formServerEvents[form.id].value = "";
                // Clear upload fields we just uploaded, otherwise subsequent uploads will upload the same data again
                _.each(events, function(event) { event.upload.clear(); });
                AjaxServer.handleResponseAjax(o);
                // Call back to execution queue, which might have more files upload
                done();
            },
            failure: function() {
                console.log("Upload failure");
                AjaxServer.retryRequestAfterDelay(_.bind(UploadServer.asyncUploadRequest, UploadServer, events, done));
            }
        });
    };

    UploadServer.uploadEventQueue = new ExecutionQueue(UploadServer.asyncUploadRequest);

})();
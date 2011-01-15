OD = ORBEON.util.Dom;
ExecutionQueue = ORBEON.util.ExecutionQueue;
Properties = ORBEON.util.Properties;
UploadServer = ORBEON.xforms.server.UploadServer;
Control = ORBEON.xforms.control.Control;
Upload = ORBEON.xforms.control.Upload;
Page = ORBEON.xforms.Page;
Event = YAHOO.util.Event;
YD = YAHOO.util.Dom;
ProgressBar = YAHOO.widget.ProgressBar;

class Upload extends Control

    yuiProgressBar: null

    init: (container) ->
        super container

        # Create loading progress indicator element, if we don't already have one
        `(function() {
            if (! this.getElementByClassName("xforms-upload-progress")) {
                // Add markup to the DOM
                var uploadProgressSpan = document.createElement("span");
                uploadProgressSpan.innerHTML = '<span class="xforms-upload-progress-bar"></span>'
                    + '<a href="#" class="xforms-upload-cancel">Cancel</a>';
                OD.setAttribute(uploadProgressSpan, "class", "xforms-upload-progress");
                var inputSelect = this.getElementByClassName("xforms-upload-select");
                YD.insertAfter(uploadProgressSpan, inputSelect);

                // Register listener on the cancel link
                var cancelAnchor = this.getElementByClassName("xforms-upload-cancel");
                Event.addListener(cancelAnchor, "click", this.cancel, this, true);
            }
        }).call(this)`

    # The change event corresponds to a file being selected. This will queue an event to submit this file in the
    # background  as soon as possible (pseudo-Ajax request).
    change: ->
        # // Start at 10, so the progress bar doesn't appear to be stuck at the beginning
        `UploadServer.uploadEventQueue.add({form: this.getForm(), upload: this},
                Properties.delayBeforeIncrementalRequest.get(), ExecutionQueue.MIN_WAIT);`

    # This method is called when we the server sends us a progress update for this upload control. Here we update
    # the progress indicator to reflect the new value we got from the server.
    #
    # @param {number} received     Number of bytes the server received so far
    # @param {number} expected     Total number of bytes the server expects
    progress: (received, expected) ->
        `(function() {
            if (this.yuiProgressBar) {
                this.yuiProgressBar.set("value", 10 + 100 * received / expected);
                UploadServer.askForProgressUpdate();
            }
        }).call(this)`

    # When users press on the cancel link, we cancel the upload, delegating this to the UploadServer.
    cancel: ->
        `Event.preventDefault(event);
        UploadServer.cancel();`


    # Sets the state of the control to either "empty" (no file selected, or upload hasn't started yet), "progress"
    # (file is being uploaded), or "file" (a file has been uploaded).
    #
    # @param {String} state
    setState: (state) ->
        `(function() {
            var STATES = ["empty", "progress", "file"];
            // Check the state we got is one of the recognized states
            if (! _.contains(STATES, state)) throw "Invalid state " + state;
            // Remove any existing state class
            _.each(STATES, _.bind(function(state) { YD.removeClass(this.container, "xforms-upload-state-" + state); }, this));
            if (state == "progress") {
                // Create or recreate progress bar
                this.getElementByClassName("xforms-upload-progress-bar").innerHTML = "";
                this.yuiProgressBar = new ProgressBar({ width: 100, height: 10, value: 0, minValue: 0, maxValue: 110, anim: true });
                this.yuiProgressBar.get("anim").duration = Properties.delayBeforeUploadProgressRefresh.get() / 1000 * 1.5;
                this.yuiProgressBar.render(this.getElementByClassName("xforms-upload-progress-bar"));
                this.yuiProgressBar.set("value", 10);
            }
            // Add the relevant state class
            YD.addClass(this.container, "xforms-upload-state-" + state);
        }).call(this)`

    # Clears the upload field by recreating it.
    clear: ->
        `(function() {
            var inputElement = YD.getElementsByClassName("xforms-upload-select", null, this.container)[0];
            var parentElement = inputElement.parentNode;
            var newInputElement = document.createElement("input");
            YAHOO.util.Dom.addClass(newInputElement, inputElement.className);
            newInputElement.setAttribute("type", inputElement.type);
            newInputElement.setAttribute("name", inputElement.name);
            newInputElement.setAttribute("size", inputElement.size);
            newInputElement.setAttribute("unselectable", "on");// the server sets this, so we have to set it again
            parentElement.replaceChild(newInputElement, inputElement);
        }).call(this)`

    `Page.registerControlConstructor(Upload,  function(container) {
        return YD.hasClass(container, "xforms-upload");
    });`


ORBEON.xforms.control.Upload = Upload
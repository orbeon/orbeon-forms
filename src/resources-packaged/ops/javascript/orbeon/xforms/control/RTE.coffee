`ORBEON.widgets.RTE = function() {

    // === PRIVATE ===

    rteEditors = {};            // Maps control ID to YUI RTE object
    isIncremental = {};         // Maps control ID to boolean telling us if this control is in incremental model
    editorWithFocus = null;     // The control ID of the RTE editor that has the focus, null if none

    /**
     * Maps control ID to either:
     *      undefined:      if the control is not rendered yet, and nobody is listening on render event.
     *      true:           if the control is rendered and nobody listened to a render event before it was rendered.
     *      a custom event: if someone listened to the render event before the control was rendered.
     */
    renderedCustomEvents = {};

    /**
     * Event handler called by the RTE every time there is an event which can could potentially change the content
     * of the editor.
     */
    function changeEvent(controlID) {
        // Simulate keyup
        var currentFocusControlId = ORBEON.xforms.Globals.currentFocusControlId;
        ORBEON.xforms.Events.keydown({ target: ORBEON.util.Dom.get(currentFocusControlId) });
        ORBEON.xforms.Events.keyup({ target: ORBEON.util.Dom.get(currentFocusControlId) });
    }

    // === PUBLIC ===

    var PUBLIC = {

        extending: ORBEON.widgets.Base,
        rteEditors: rteEditors,

        /**
         * Initializes the RTE editor for a particular control.
         */
        init: function(control) {
            // Create RTE config
            var rteConfig;
            if (typeof YUI_RTE_CUSTOM_CONFIG != "undefined")
                rteConfig = YUI_RTE_CUSTOM_CONFIG;
            else
                rteConfig = ORBEON.xforms.control.RTEConfig;

            // Create RTE object
            var textarea = ORBEON.util.Utils.isNewXHTMLLayout() ? control.getElementsByTagName("textarea")[0] : control;
            // Make sure that textarea is not disabled unless readonly, otherwise RTE renders it in read-only mode
            textarea.disabled = YAHOO.util.Dom.hasClass(control, "xforms-readonly");
            var yuiRTE = new YAHOO.widget.Editor(textarea, rteConfig);

            // Register event listener for user interacting with the control
            // RTE fires afterNodeChange right at the end of initialisation, which mistakenly results
            // in changeEvent being called onload, which has a side-effect of making Orbeon think the RTE
            // has focus. Avoid this by only registering the changeEvent listener when the first afterNodeChange
            // event is received.
            var controlId = control.id;
            var registerChangeEvent = function() {
                yuiRTE.on("editorKeyUp", function() { changeEvent(controlId); });
                yuiRTE.on("afterNodeChange", function() { changeEvent(controlId); });
                yuiRTE.on("editorWindowFocus", function() { ORBEON.xforms.Events.focus({ target: control }); });
                yuiRTE.on("editorWindowBlur", function() { ORBEON.xforms.Events.blur({ target: control }); });
                yuiRTE.removeListener("afterNodeChange", registerChangeEvent);
            };
            yuiRTE.on("afterNodeChange", registerChangeEvent);

            // Store information about this RTE
            rteEditors[control.id] = yuiRTE;
            isIncremental[control.id] = YAHOO.util.Dom.hasClass(control, "xforms-incremental");
            // Transform text area into RTE on the page
            yuiRTE.on("windowRender", function() {
                // Store initial server value
                // If we don't and user's JS code calls ORBEON.xforms.Document.setValue(), the value of the RTE is changed, our RFE changeEvent() is called,
                // it sets the focus on the RTE, which calls focus(), which stores the current value (newly set) as the server value if no server value is defined.
                // Then in executeNextRequest() we ignore the value change because it is the same the server value.
                var controlCurrentValue = ORBEON.xforms.Controls.getCurrentValue(control);
                ORBEON.xforms.ServerValueStore.set(control.id, controlCurrentValue);
                // Fire event we have a custom event listener from this RTE
                if (YAHOO.lang.isObject(renderedCustomEvents[control.id]))
                    renderedCustomEvents[control.id].fire();
                // Set to true, so future listeners are called back right away
                renderedCustomEvents[control.id] = true;

                if (! ORBEON.util.Utils.isNewXHTMLLayout()) {
                    // Move classes on the container created by YUI
                    var rteContainer = control.parentNode;
                    rteContainer.className += " " + control.className;
                    control.className = "";
                    // If classes are later changed, they need to be changed on the container, so move the id on the container
                    control.id = controlId + "-textarea";
                    rteContainer.id = controlId;
                }
            });
            yuiRTE.render();
        },

        // TODO: destroy()

        /**
         * Called on any focus event of other form controls on the page
         */
        focusOnAnyFormControl: function(control) {
            var currentFocusControlId = ORBEON.xforms.Globals.currentFocusControlId;
            // If the focus went to another control (not RTE) and the current is a an RTE
            if (rteEditors[control.id] == null && rteEditors[currentFocusControlId] != null) {
                // Send blur to that RTE
                ORBEON.xforms.Events.change({ target: ORBEON.util.Dom.get(currentFocusControlId) });
                ORBEON.xforms.Events.blur({ target: ORBEON.util.Dom.get(currentFocusControlId) });
            }
        },

        /**
         * Called to set the value of the RTE
         */
        setValue: function(control, newValue) {
            // Don't update the textarea with HTML from the server while the user is typing, otherwise the user
            // loses their cursor position. This lets us have a certain level of support for incremental rich text areas,
            // however, ignoring server values means that the visual state of the RTE can become out of sync
            // with the server value (for example, the result of a calculation wouldn't be visible until focus moved
            // out of the field).
            if (! YAHOO.util.Dom.hasClass(control, "xforms-incremental") || ORBEON.xforms.Globals.currentFocusControlId != control.id) {
                var yuiRTE = rteEditors[control.id];
                yuiRTE.setEditorHTML(newValue);
            }
        },

        getValue: function(control) {
            var yuiRTE = rteEditors[control.id];
            var value = yuiRTE.getEditorHTML();
            // HACK: with Firefox, it seems that sometimes, when setting the value of the editor to "" you get"<br>" back
            // The purpose of this hack is to work around that problem. It has drawbacks:
            // o This means setting "<br>" will also result in ""
            // o This doesn't fix the root of the problem so there may be other cases not caught by this
            if (value == "<br>")
                value = "";
            return value;
        },

        setFocus: function(control) {
            var yuiRTE = rteEditors[control.id];
            yuiRTE.focus();
        },

        onRendered: function(control, callback) {
            if (renderedCustomEvents[control.id] === true) {
                // Already rendered.
                callback();
            } else {
                // Create custom event if necessary
                if (renderedCustomEvents[control.id] === undefined)
                    renderedCustomEvents[control.id] = new YAHOO.util.CustomEvent("rteRendered");
                // Custom event was already created
                renderedCustomEvents[control.id].subscribe(callback);
            }
        },

        /**
         * XForms readonly == RTE disabled configuration attribute.
         */
        setReadonly: function(control, isReadonly) {
            var yuiRTE = rteEditors[control.id];
            yuiRTE.set("disabled", isReadonly);
        }
    };

    return PUBLIC;
}();`
/**
 * Copyright (C) 2012 Orbeon, Inc.
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
    var YD = YAHOO.util.Dom;

    YAHOO.namespace("xbl.fr");
    YAHOO.xbl.fr.Tinymce = function() {};
    ORBEON.xforms.XBL.declareClass(YAHOO.xbl.fr.Tinymce, "xbl-fr-tinymce");
    YAHOO.xbl.fr.Tinymce.prototype = {

        groupElement: null,
        visibleInputElement: null,
        myEditor: null,
        textareaId: null,
        tinymceInitialized: false,

        init: function() {
            this.textareaId = YAHOO.util.Dom.getElementsByClassName('xbl-fr-tinymce-xforms-textarea', null, this.container)[0].id;

            // Tell TinyMCE about base URL, which it can't guess in combined resources
            var baseURLa = YAHOO.util.Dom.getElementsByClassName('tinymce-base-url', null, this.container)[0];
            // Remove the '.js' at the end of the URL, added so the server-side code includes the version number in the URL
            var baseURL = baseURLa.href.substr(0, baseURLa.href.length - 3);
            tinymce.baseURL = baseURL;

            // Create TinyMCE editor instance
            var tinyMceConfig = typeof TINYMCE_CUSTOM_CONFIG !== "undefined" ? TINYMCE_CUSTOM_CONFIG : YAHOO.xbl.fr.Tinymce.DefaultConfig;
            var tinyMceDivId = YAHOO.util.Dom.getElementsByClassName('xbl-fr-tinymce-div', null, this.container)[0].id;
            this.myEditor = new tinymce.Editor(tinyMceDivId, tinyMceConfig);
            var xformsValue = ORBEON.xforms.Document.getValue(this.textareaId);
            this.onInit(_.bind(function() {
                this.myEditor.setContent(xformsValue);
                // On click inside the iframe, propagate the click outside, so code listening on click on an ancestor gets called
                $(this.container).find('iframe').contents().on('click', _.bind(function() { this.container.click(); }, this));
                this.tinymceInitialized = true;
            }, this));
            this.myEditor.onChange.add(_.bind(this.clientToServer, this));

            // Render the component
            this.myEditor.render();
        },

        // Send value in MCE to server
        clientToServer: function() {
            ORBEON.xforms.Document.setValue(this.textareaId, this.myEditor.getContent());
        },

        // Update MCE with server value
        serverToClient: function() {
            if (! this.tinymceInitialized) return;                                                                  // Don't update value until TinyMCE is fully initialized
            var mceContainer = YAHOO.util.Dom.getAncestorBy(document.activeElement, _.bind(function(e) {            // Look for ancestor of active element which is part of the MCE
                return e == this.container || YD.hasClass(e, 'mceListBoxMenu');                                     // TinyMCE creates a div.mceListBoxMenu under the body for menus
            }, this));
            if (mceContainer == null) {                                                                             // Heuristic: if TinyMCE has focus, users might still be editing so don't update
                var newServerValue = ORBEON.xforms.Document.getValue(this.textareaId);
                this.myEditor.setContent(newServerValue);
            }
        },

        // Runs a function when the TinyMCE is initialized
        onInit: function(f) {
            var bound = _.bind(f, this);
            if (this.tinymceInitialized) bound();
            else this.myEditor.onInit.add(bound);
        },

        readonly:   function() { this.onInit(function() { this.myEditor.getBody().contentEditable = false; })},
        readwrite:  function() { this.onInit(function() { this.myEditor.getBody().contentEditable = true; })}
    };

})();
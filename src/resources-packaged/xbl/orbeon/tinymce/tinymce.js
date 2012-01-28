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

        xformsInputElement: null,
        groupElement: null,
        visibleInputElement: null,
        myDivClass: 'xbl-fr-tinymce-div',
        myInputClass: 'xbl-fr-tinymce-xforms-input',
        myDivId: null,
        myEditor: null,

        init: function(){
            // get to id attribute of our xbl's div element
            this.visibleInputElement = YAHOO.util.Dom.getElementsByClassName(this.myDivClass, null, this.container)[0];
            this.myDivId = this.visibleInputElement.id;

            // Tell TinyMCE about base URL, which it can't guess in combined resources
            var baseURLa = YAHOO.util.Dom.getElementsByClassName('tinymce-base-url', null, this.container)[0];
            // Remove the '.js' at the end of the URL, added so the server-side code includes the version number in the URL
            var baseURL = baseURLa.href.substr(0, baseURLa.href.length - 3);
            tinymce.baseURL = baseURL;

            // Create TinyMCE editor instance
            var tinyMceConfig = typeof TINYMCE_CUSTOM_CONFIG !== "undefined" ? TINYMCE_CUSTOM_CONFIG : YAHOO.xbl.fr.Tinymce.DefaultConfig;
            this.myEditor = new tinymce.Editor(this.myDivId, tinyMceConfig);

            // set initial value (add a listener to the init event. because myEditor is may not fully initialized yet!)
            // can't rely on init_instance_callback or oninit because custom config may lacking those options
            this.xformsInputElement = YAHOO.util.Dom.getElementsByClassName(this.myInputClass, null, this.container)[0];
            var xformsValue = ORBEON.xforms.Document.getValue(this.xformsInputElement.id);
            this.myEditor.onInit.add(function(ed) { ed.setContent(xformsValue); });
            this.myEditor.onChange.add(_.bind(this.clientToServer, this));

            // render the component
            this.myEditor.render();
        },

        // Send value in MCE to server
        clientToServer: function() {
            ORBEON.xforms.Document.setValue(this.xformsInputElement.id, this.myEditor.getContent());
        },

        // Update MCE with server value
        serverToClient: function() {
            var mceContainer = YAHOO.util.Dom.getAncestorBy(document.activeElement, _.bind(function(e) {            // Look for ancestor of active element which is part of the MCE
                return e == this.container || YD.hasClass(e, 'mceListBoxMenu');                                     // TinyMCE creates a div.mceListBoxMenu under the body for menus
            }, this));
            if (mceContainer == null) {                                                                             // Heuristic: if TinyMCE has focus, users might still be editing so don't update
                var xformsInputElement = YAHOO.util.Dom.getElementsByClassName(this.myInputClass, null, this.container)[0];
                var newServerValue = ORBEON.xforms.Document.getValue(xformsInputElement.id);
                this.myEditor.setContent(newServerValue);
                this.visibleInputElement.disabled = YAHOO.util.Dom.hasClass(this.xformsInputElement, "xforms-readonly");
            }
        },

        readonly: function() {
            this.visibleInputElement.disabled = true;
        },

        readwrite: function() {
            this.visibleInputElement.disabled = false;
        }
    };

})();


/**
 * Copyright (C) 2010 Orbeon, Inc.
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
    var OD = ORBEON.util.Dom;
    var YD = YAHOO.util.Dom;
    var Document = ORBEON.xforms.Document;

    YAHOO.namespace("xbl.fr");
    YAHOO.xbl.fr.CodeMirror = function() {};
    ORBEON.xforms.XBL.declareClass(YAHOO.xbl.fr.CodeMirror, "xbl-fr-code-mirror");
    YAHOO.xbl.fr.CodeMirror.prototype = {

        init: function() {
            var form = ORBEON.xforms.Controls.getForm(this.container);
            var resourcesBaseURL = ORBEON.xforms.Globals.resourcesBaseURL[form.id];
            var resourcesPrefix = resourcesBaseURL + "/xbl/orbeon/code-mirror/CodeMirror-0.94/";
            this.textarea = YD.getElementsByClassName("xforms-textarea", null, this.container)[0];
            var editorContainer = YD.getElementsByClassName("xbl-fr-code-mirror-editor-container", null, this.container)[0];
            this.editor = new CodeMirror(editorContainer, {
                path: resourcesPrefix + "js/",
                parserfile: "parsexml.js",
                stylesheet: resourcesPrefix + "css/xmlcolors.css",
                lineNumbers: true,
                indentUnit: 4,
                textWrapping: false,
                useHTMLKludges: false,
                onChange: _.bind(this.codeMirrorChange, this),
                content: Document.getValue(this.textarea),
                height: "100%",
                onLoad: _.bind(this.codeMirrorOnLoad, this)
            });
        },

        codeMirrorOnLoad: function() {
            YAHOO.util.Event.addListener(this.editor.win.document, "focus", _.bind(this.codeMirrorFocus, this));
            YAHOO.util.Event.addListener(this.editor.win.document, "blur", _.bind(this.codeMirrorBlur, this));
        },

        codeMirrorFocus: function() { this.hasFocus = true; },
        codeMirrorBlur: function() { this.hasFocus = false; },
        codeMirrorChange: function() { Document.setValue(this.textarea, this.editor.getCode()); },

        valueChanged: function() {
            // As a shortcut, don't update the control if the user is typing in it
            if (! this.hasFocus)
                this.editor.setCode(Document.getValue(this.textarea));
        }
    };
})();
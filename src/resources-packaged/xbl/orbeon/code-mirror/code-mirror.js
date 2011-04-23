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
            this.textarea = YD.getElementsByClassName("xforms-textarea", null, this.container)[0];
            var editorContainer = YD.getElementsByClassName("xbl-fr-code-mirror-editor-container", null, this.container)[0];
            this.editor = CodeMirror(editorContainer, {
                mode: "xml",
                lineNumbers: true,
                indentUnit: 4,
                value: Document.getValue(this.textarea),
                onChange: _.bind(this.codeMirrorChange, this),
                onFocus: _.bind(this.codeMirrorFocus, this),
                onBlur: _.bind(this.codeMirrorBlur, this)
            });
        },

        codeMirrorFocus: function() { this.hasFocus = true; },
        codeMirrorBlur: function() { this.hasFocus = false; },
        codeMirrorChange: function() {
            if (this.editor)
                Document.setValue(this.textarea, this.editor.getValue());
        },

        valueChanged: function() {
            // As a shortcut, don't update the control if the user is typing in it
            if (! this.hasFocus)
                this.editor.setValue(Document.getValue(this.textarea));
        }
    };
})();
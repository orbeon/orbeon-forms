/**
 * Copyright (C) 2016 Orbeon, Inc.
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

        hasFocus: false,                 // Heuristic: if has focus, don't update with value from server
        userChangedSinceLastBlur: false, // Use CodeMirror's own change event to track whether the value changed

        init: function() {
            var form = ORBEON.xforms.Controls.getForm(this.container);
            var inner = YD.getElementsByClassName("xbl-fr-code-mirror-editor-inner", null, this.container)[0];
            this.editor = CodeMirror(inner, {
                mode: "xml",
                lineNumbers: true,
                indentUnit: 4
            });
            this.editor.on('change', _.bind(this.codeMirrorChange, this));
            this.editor.on('focus' , _.bind(this.codeMirrorFocus , this));
            this.editor.on('blur'  , _.bind(this.codeMirrorBlur  , this));
        },

        enabled: function() {
            this.editor.setOption("readOnly", YD.hasClass(this.container, "xforms-readonly") ? 'nocursor' : false);
        },

        setFocus : function() { this.editor.focus();  },
        codeMirrorFocus: function() { this.hasFocus = true; },
        codeMirrorBlur: function() {
            this.hasFocus = false;
            if (this.userChangedSinceLastBlur) {
                YD.addClass(this.container, "xforms-visited");
                ORBEON.xforms.Document.setValue(
                    this.container.id,
                    this.getCurrentValue()
                );
                this.userChangedSinceLastBlur = false;
            }
        },
        codeMirrorChange: function(codemirror, event) {
            if (event.origin != 'setValue') {
                this.userChangedSinceLastBlur = true;
            }
        },

        // Use 'true' instead of 'nocursor' so that copy/paste works:
        // https://github.com/orbeon/orbeon-forms/issues/1841
        xformsReadonly: function() { this.editor.setOption("readOnly", 'true'); },
        xformsReadwrite: function() { this.editor.setOption("readOnly", false); },
        updateWithServerValue: function(uriEncodedSource) {
            var newSource = decodeURIComponent(uriEncodedSource);
            var doUpdate =
                // As a shortcut, don't update the control if the user is typing in it
                ! this.hasFocus &&
                // Don't update if the new value is the same as the current one, as doing so resets the editor position
                newSource != this.editor.getValue();
            if (doUpdate) {
                this.editor.setValue(newSource);
            }
        },
        getCurrentValue: function() {
            return this.editor.getValue();
        }
    };
})();

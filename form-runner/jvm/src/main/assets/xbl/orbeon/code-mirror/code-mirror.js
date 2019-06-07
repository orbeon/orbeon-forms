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
(function () {

  var $ = ORBEON.jQuery;

  ORBEON.xforms.XBL.declareCompanion('fr|code-mirror', {

    editor: null,
    handlers: {},

    hasFocus: false, // Heuristic: if has focus, don't update with value from server
    userChangedSinceLastBlur: false, // Use CodeMirror's own change event to track whether the value changed

    init: function () {

      var outer = $(this.container).find('.xbl-fr-code-mirror-editor-outer')[0];
      $('<xh:span class="xbl-fr-code-mirror-editor-inner"/>').appendTo(outer);

      var inner = $(this.container).find('.xbl-fr-code-mirror-editor-inner')[0];

      this.hasFocus = false;
      this.userChangedSinceLastBlur = false;
      
      this.editor = CodeMirror(
        inner,
        {
          mode: 'xml',
          lineNumbers: true,
          lineWrapping: true,
          indentUnit: 4,
          foldGutter: true,
          extraKeys: {"Ctrl-Q": function(cm){ cm.foldCode(cm.getCursor()); }},
          gutters: ["CodeMirror-linenumbers", "CodeMirror-foldgutter"]
        }
      );

      this.handlers = {
        'change': _.bind(this.codeMirrorChange, this), // TODO: .bind() is deprecated
        'focus': _.bind(this.codeMirrorFocus, this),
        'blur': _.bind(this.codeMirrorBlur, this)
      };

      var editor = this.editor;
      _.each(this.handlers, function (value, key) {
        editor.on(key, value);
      });

      this.xformsUpdateReadonly($(this.container).is('.xforms-readonly'));
    },
    destroy: function () {
      // Try to clean-up as much as we can
      var editor = this.editor;
      _.each(this.handlers, function (value, key) {
        editor.off(key, value);
      });
      this.handlers = {};
      this.editor = null;
      $(this.container).find('.xbl-fr-code-mirror-editor-outer').empty();
    },
    xformsFocus: function () {
      this.editor.focus();
    },
    codeMirrorFocus: function () {
      this.hasFocus = true;
    },
    codeMirrorBlur: function () {
      this.hasFocus = false;
      if (this.userChangedSinceLastBlur) {
        $(this.container).addClass('xforms-visited');
        ORBEON.xforms.Document.setValue(
          this.container.id,
          this.xformsGetValue()
          );
        this.userChangedSinceLastBlur = false;
      }
    },
    codeMirrorChange: function (codemirror, event) {
      if (event.origin != 'setValue') {
        this.userChangedSinceLastBlur = true;
      }
    },
    xformsUpdateReadonly: function (readonly) {
      if (readonly) {
        // Use 'true' instead of 'nocursor' so that copy/paste works:
        // https://github.com/orbeon/orbeon-forms/issues/1841
        this.editor.setOption('readOnly', 'true');
      } else {
        this.editor.setOption('readOnly', false);
      }
    },
    xformsUpdateValue: function (newValue) {
      var doUpdate =
        // As a shortcut, don't update the control if the user is typing in it
        !this.hasFocus &&
        // Don't update if the new value is the same as the current one, as doing so resets the editor position
        newValue != this.editor.getValue();
      if (doUpdate) {
        // It seems that we need a delay refresh otherwise sometimes the content doesn't appear when in a dialog
        // which is just shown. However, this doesn't work 100% of the time it seems.
        var editor = this.editor;

        var deferred = $.Deferred();

        setTimeout(function () {
          editor.setValue(newValue);
          deferred.resolve();
        }, 0);

        return deferred.promise();
      }
    },
    xformsGetValue: function () {
      return this.editor.getValue();
    }
  });
})();

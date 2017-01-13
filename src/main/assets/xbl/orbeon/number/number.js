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

    var $ = ORBEON.jQuery;
    var AS = ORBEON.xforms.server.AjaxServer;
    var Document = ORBEON.xforms.Document;

    YAHOO.namespace('xbl.fr');
    YAHOO.xbl.fr.Number = function() {};
    ORBEON.xforms.XBL.declareClass(YAHOO.xbl.fr.Number, 'xbl-fr-number');
    YAHOO.xbl.fr.Number.prototype = {

        xformsInputElement: null,
        xformsOutputElement: null,
        visibleInputElement: null,

        decimalSeparatorElement: null,
        decimalSeparator: null,

        init: function() {

            this.xformsInputElement  = $(this.container).find('.xbl-fr-number-xforms-input')[0];
            this.xformsOutputElement = $(this.container).find('.xbl-fr-number-xforms-output')[0];
            this.visibleInputElement = $(this.container).find('.xbl-fr-number-visible-input')[0];

            // Find prefix based on class/control name, as this JS can be used with fr:number and fr:currency and properties use the control name
            var controlClassPrefix = null;
            var containerClasses = this.container.className.split(' ');
            for (var classIndex = 0; classIndex < containerClasses.length; classIndex++) {
                var currentClass = containerClasses[classIndex];
                if (currentClass.indexOf('xbl-fr-') == 0) {
                    controlClassPrefix = currentClass;
                    break;
                }
            }

            this.decimalSeparatorElement  = $(this.container).find('.' + controlClassPrefix + '-decimal-separator')[0];
            this.decimalSeparator         = Document.getValue(this.decimalSeparatorElement);

            // Register listeners

            // Switch the input type after cleaning up the value for edition
            $(this.visibleInputElement).on('touchstart focus', _.bind(function(e) {

                // Don't set value if not needed, so not to unnecessarily disturb the cursor position
                if (this.visibleInputElement.value != this.getEditString())
                    this.visibleInputElement.value = this.getEditString();

                // See https://github.com/orbeon/orbeon-forms/issues/2545
                function hasNativeDecimalSeparator(separator) {
                    return ! _.isUndefined(Number.toLocaleString) &&
                        1.1.toLocaleString().substring(1, 2) == separator;
                }

                if ($('body').is('.xforms-mobile') && hasNativeDecimalSeparator(this.decimalSeparator)) {
                    // With Firefox, changing the type synchronously interferes with the focus
                    window.setTimeout(_.bind(function() {
                        $(this.visibleInputElement).attr('type', 'number');
                    }, this), 0);
                }
            }, this));

            // Restore input type, send the value to the server, and updates value after server response
            $(this.visibleInputElement).on('focusout', _.bind(function(e) {

                // With Firefox, changing the type synchronously interferes with the focus
                window.setTimeout(_.bind(function() {
                    $(this.visibleInputElement).attr('type', 'text');
                }, this), 0);

                this.sendValueToServer();
                var formId = $(this.container).parents('form').attr('id');

                // Always update visible value with XForms value
                // - relying just value change event from server is not enough
                // - value change not dispatched if server value hasn't changed
                // - if visible changed, but XForms hasn't, we still need to show XForms value
                // - see: https://github.com/orbeon/orbeon-forms/issues/1026
                AS.nextAjaxResponse(formId).then(_.bind(this.updateWithServerValue, this));
            }, this));

            $(this.visibleInputElement).on('keypress', _.bind(function(e) {
                if (e.which == 13)
                    this.sendValueToServer();
            }, this));
        },

        xformsFocus: function() {
            this.visibleInputElement.focus();
         },

        sendValueToServer: function() {
            var newValue = this.visibleInputElement.value;
            var xformsValue = Document.getValue(this.xformsInputElement);
            // Document.setValue() doesn't automatically avoid sending the value if it hasn't changed
            if (newValue != xformsValue)
                Document.setValue(this.xformsInputElement, newValue);
        },

        getEditString: function() {
            return Document.getValue(this.xformsOutputElement);
        },

        updateWithServerValue: function() {

            var hasFocus = this.visibleInputElement == document.activeElement;

            this.visibleInputElement.value =
                hasFocus ?
                this.getEditString() :
                Document.getValue(this.xformsInputElement);

            // Also update disabled because this might be called upon an iteration being moved, in which case all the control properties must be updated
            this.visibleInputElement.disabled = $(this.xformsInputElement).hasClass('xforms-readonly');
        },

        readonly: function() {
            this.visibleInputElement.disabled = true;
        },

        readwrite: function() {
            this.visibleInputElement.disabled = false;
        },

        parameterDecimalSeparatorChanged: function() {
            this.decimalSeparator = Document.getValue(this.decimalSeparatorElement);
            this.updateWithServerValue();
        }
    };
})();

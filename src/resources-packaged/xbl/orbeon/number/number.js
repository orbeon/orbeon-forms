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

    YAHOO.namespace("xbl.fr");
    YAHOO.xbl.fr.Number = function() {};
    ORBEON.xforms.XBL.declareClass(YAHOO.xbl.fr.Number, "xbl-fr-number");
    YAHOO.xbl.fr.Number.prototype = {

        xformsInputElement: null,
        visibleInputElement: null,

        prefixElement: null,
        prefix: null,

        decimalSeparatorElement: null,
        decimalSeparator: null,

        groupingSeparatorElement: null,
        groupingSeparator: null,

        hasFocus: false,

        init: function() {
            // Get information from the DOM

            this.xformsInputElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-number-xforms-input", null, this.container)[0];
            this.visibleInputElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-number-visible-input", null, this.container)[0];
            this.hasFocus = false;

            // Properties
            // Find prefix based on class/control name, as this JS can be used with fr:number and fr:currency and properties use the control name
            var controlClassPrefix = null;
            var containerClasses = this.container.className.split(" ");
            for (var classIndex = 0; classIndex < containerClasses.length; classIndex++) {
                var currentClass = containerClasses[classIndex];
                if (currentClass.indexOf("xbl-fr-") == 0) {
                    controlClassPrefix = currentClass;
                    break;
                }
            }

            this.prefixElement = YAHOO.util.Dom.getElementsByClassName(controlClassPrefix + "-prefix", null, this.container)[0];
            this.prefix = Document.getValue(this.prefixElement.id);
            this.decimalSeparatorElement = YAHOO.util.Dom.getElementsByClassName(controlClassPrefix + "-decimal-separator", null, this.container)[0];
            this.decimalSeparator = Document.getValue(this.decimalSeparatorElement.id);
            this.groupingSeparatorElement = YAHOO.util.Dom.getElementsByClassName(controlClassPrefix + "-grouping-separator", null, this.container)[0];
            this.groupingSeparator = Document.getValue(this.groupingSeparatorElement.id);

            // Register listener
            YAHOO.util.Event.addFocusListener(this.visibleInputElement, this.focus, this, true);
            YAHOO.util.Event.addBlurListener(this.visibleInputElement, this.blur, this, true);
            $(this.visibleInputElement).on('keypress', _.bind(function(e) {
                if (e.which == 13) this.sendValueToServer();
            }, this));
        },

        focus: function() {
            this.hasFocus = true;
            this.visibleInputElement.value = this.numberToEditString(this.visibleInputElement.value);
            ORBEON.xforms.Globals.currentFocusControlId = this.container.id;
            Document.dispatchEvent(this.xformsInputElement.id, 'xforms-focus');
        },

        sendValueToServer: function() {
            var newValue = this.visibleInputElement.value;
            Document.setValue(this.xformsInputElement.id, newValue);
        },

        blur: function() {
            this.hasFocus = false;
            this.sendValueToServer();
            var formId = $(this.container).parents('form').attr('id');

            // Always update visible value with XForms value
            // - relying just value change event from server is not enough
            // - value change not dispatched if server value hasn't changed
            // - if visible changed, but XForms hasn't, we still need to show XForms value
            // - see: https://github.com/orbeon/orbeon-forms/issues/1026
            AS.nextAjaxResponse(formId).then(_.bind(this.updateWithServerValue, this));
        },

        numberToEditString: function(number) {
            var cleaned = number;

            // Remove spaces and grouping separators
            cleaned = cleaned.replace(new RegExp("[\\s" + this.groupingSeparator + "]", "g"), "");

            // Remove prefix if present
            if (cleaned.indexOf(this.prefix) == 0)
                cleaned = cleaned.substring(this.prefix.length);

            var cleanedAsNumberString = cleaned.replace(new RegExp("[" + this.decimalSeparator + "]", "g"), ".");

            return isNaN(Number(cleanedAsNumberString)) ? number : cleaned;
        },

        updateWithServerValue: function() {
            // Get value as formatted by server
            var numberFormattedValue = Document.getValue(this.xformsInputElement.id);
            // If there is an update in the value, and the field already has the focus, just populate with the
            // XForms value without number formatting
            this.visibleInputElement.value = this.hasFocus ? this.numberToEditString(numberFormattedValue) : numberFormattedValue;
            // Also update disabled because this might be called upon an iteration being moved, in which case all the control properties must be updated
            this.visibleInputElement.disabled = YAHOO.util.Dom.hasClass(this.xformsInputElement, "xforms-readonly");
        },

        readonly: function() {
            this.visibleInputElement.disabled = true;
        },

        readwrite: function() {
            this.visibleInputElement.disabled = false;
        },

        parameterPrefixChanged: function() {
            this.prefix = Document.getValue(this.prefixElement.id);
            this.updateWithServerValue();
        },

        parameterDecimalSeparatorChanged: function() {
            this.decimalSeparator = Document.getValue(this.decimalSeparatorElement.id);
            this.updateWithServerValue();
        },

        parameterGroupingSeparatorChanged: function() {
            this.groupingSeparator = Document.getValue(this.groupingSeparatorElement.id);
            this.updateWithServerValue();
        },

        parameterDigitsAfterDecimalChanged: function() {}
    };
})();

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
YAHOO.namespace("xbl.fr");
YAHOO.xbl.fr.Currency = function() {};
ORBEON.xforms.XBL.declareClass(YAHOO.xbl.fr.Currency, "xbl-fr-currency");
YAHOO.xbl.fr.Currency.prototype = {

    xformsInputElement: null,
    groupElement: null,
    visibleInputElement: null,
    prefixElement: null,
    prefix: null,
    digitsAfterDecimalElement: null,
    digitsAfterDecimal: null,
    hasFocus: false,

    init: function() {
        // Get information from the DOM
        this.xformsInputElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-currency-xforms-input", null, this.container)[0];
        this.groupElement = YAHOO.util.Dom.getElementsByClassName("xforms-group", null, this.container)[0];
        this.visibleInputElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-currency-visible-input", null, this.container)[0];
        this.prefixElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-currency-prefix", null, this.container)[0];
        this.prefix = ORBEON.xforms.Document.getValue(this.prefixElement.id);
        this.digitsAfterDecimalElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-currency-digits-after-decimal", null, this.container)[0];
        this.digitsAfterDecimal = ORBEON.xforms.Document.getValue(this.digitsAfterDecimalElement.id);
        this.hasFocus = false;

        // Initialize value of visible input
        this.xformsToVisible();

        // Register listener
        YAHOO.util.Event.addFocusListener(this.visibleInputElement, this.focus, this, true);
        YAHOO.util.Event.addBlurListener(this.visibleInputElement, this.blur, this, true);
    },

    focus: function() {
        this.hasFocus = true;
        this.visibleInputElement.value = this.currencyToNumber(this.visibleInputElement.value);
    },

    blur: function() {
        this.hasFocus = false;
        var cleanString = this.visibleInputElement.value.replace(new RegExp("[\\s,]", "g"), "");
        // Remove prefix if at the beginning of the string
        if (cleanString.indexOf(this.prefix) == 0)
            cleanString = cleanString.substring(this.prefix.length);
        ORBEON.xforms.Document.setValue(this.xformsInputElement.id, cleanString);
        this.visibleInputElement.value = this.numberToCurrency(this.visibleInputElement.value);
        // Update xforms-required-empty/xforms-required-filled and xforms-visited
        ORBEON.xforms.Controls.updateRequiredEmpty(this.groupElement, cleanString);
        if (! YAHOO.util.Dom.hasClass(this.groupElement, "xforms-visited"))
            ORBEON.xforms.Controls.updateInvalidVisitedOnNextAjaxResponse(this.groupElement);
    },

    setfocus: function() {
        this.visibleInputElement.focus();
    },

    currencyToNumber: function(currency) {
        if (currency.indexOf(this.prefix) == 0) {
            var cleaned = currency.substring(this.prefix.length);
            cleaned = cleaned.replace(new RegExp("[\\s,]", "g"), "");
            return isNaN(new Number(cleaned)) ? currency : cleaned;
        } else {
            return currency;
        }
    },

    numberToCurrency: function(number) {
        // Cleaning number (might be entered by the user with all kind of formatting)
        var cleaned = function() {
            var result = number;
            // Remove currency prefix, if present anywhere in the string
            var indexOfPrefix = result.indexOf(this.prefix);
            if (indexOfPrefix != -1) result = result.split(this.prefix).join("");
            // Remove spaces and comas
            result = result.replace(new RegExp("[\\s,]", "g"), "");
            return result;
        }.call(this);
        var result;
        if (cleaned == "") {
            result = number;
        } else {
            var numberObject = new Number(cleaned);
            if (isNaN(numberObject)) {
                result = number;
            } else {
                // Extract integer and fractional parts
                var parts = cleaned.split(".");
                parts.push("");
                var integerPart = parts[0];
                var fractionalPart = parts[1];

                // Add thousand separator for integer
                var regExp = /(\d+)(\d{3})/;
                while (regExp.test(integerPart)) integerPart = integerPart.replace(regExp, "$1" + "," + "$2");

                // Get fractional part to be of the demanded length
                if (fractionalPart.length > this.digitsAfterDecimal) fractionalPart = fractionalPart.substring(0, this.digitsAfterDecimal);
                while (fractionalPart.length < this.digitsAfterDecimal) fractionalPart += "0";

                // Build result
                result = this.prefix == "" ? "" : this.prefix + " ";
                result += integerPart;
                if (fractionalPart.length > 1) result += "." + fractionalPart;
            }
        }
        return result;
    },

    xformsToVisible: function() {
        var xformsValue = ORBEON.xforms.Document.getValue(this.xformsInputElement.id);
        // If there is an update in the value, and the field already has the focus, just populate with the
        // XForms value without currency formatting
        var currencyFormattedValue = this.numberToCurrency(xformsValue);
        this.visibleInputElement.value = this.hasFocus ? this.currencyToNumber(currencyFormattedValue) : currencyFormattedValue;
        // Also update disabled because this might be called upon an iteration being moved, in which case all the control properties must be updated
        this.visibleInputElement.disabled = YAHOO.util.Dom.hasClass(this.xformsInputElement, "xforms-readonly");
    },

    update: function() {
        this.xformsToVisible();
    },

    readonly: function() {
        this.visibleInputElement.disabled = true;
    },

    readwrite: function() {
        this.visibleInputElement.disabled = false;
    },

    parameterPrefixChanged: function() {
        this.prefix = ORBEON.xforms.Document.getValue(this.prefixElement.id);
        this.xformsToVisible();
    },

    parameterDigitsAfterDecimalChanged: function() {
        this.digitsAfterDecimal = ORBEON.xforms.Document.getValue(this.digitsAfterDecimalElement.id);
        this.xformsToVisible();
    }
};

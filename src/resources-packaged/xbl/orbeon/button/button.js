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

    YAHOO.namespace("xbl.fr");
    YAHOO.xbl.fr.Button = function() {};
    ORBEON.xforms.XBL.declareClass(YAHOO.xbl.fr.Button, "xbl-fr-button");
    YAHOO.xbl.fr.Button.prototype = {

        /** @type {Button} */ yuiButton: null,
        /** @type {HTMLButtonElement} */ yuiButtonSpan: null,

        /**
         * Create the YUI Button object
         */
        init: function() {
            this.yuiButtonSpan = YD.getElementsByClassName("yui-button", null, this.container)[0];
            this.yuiButton = new YAHOO.widget.Button(this.yuiButtonSpan);
            var tabIndexSpan = YD.getElementsByClassName("fr-button-tabindex", null, this.container);
            if (tabIndexSpan.length == 1) this.yuiButton.set("tabindex", parseInt(OD.getStringValue(tabIndexSpan[0])));
        },

        /**
         * When the button becomes enabled, update its readonly state, as that state might have changed while the button
         * was disabled, without an xforms-readonly or xforms-readwrite event being dispatched.
         */
        enabled: function() {
            this.yuiButton.set("disabled", YAHOO.util.Dom.hasClass(this.yuiButtonSpan, "xforms-readonly"));
        },

        readonly:  function() { this.yuiButton.set("disabled", true); },
        readwrite: function() { this.yuiButton.set("disabled", false); }
    };
})();
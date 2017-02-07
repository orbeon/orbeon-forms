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
YAHOO.xbl.fr.Select1Button = function() {};
ORBEON.xforms.XBL.declareClass(YAHOO.xbl.fr.Select1Button, "xbl-fr-select1-button");
YAHOO.xbl.fr.Select1Button.prototype = {

    htmlButton: null,
    select1ControlId: null,
    yuiButton: null,

    /**
     * Constructor
     */
    init: function() {

        // Get references to HTML elements
        this.htmlButton = YAHOO.util.Dom.getElementsByClassName("fr-select1-button-button", null, this.container)[0];
        this.select1ControlId = YAHOO.util.Dom.getElementsByClassName("fr-select1-button-select1", null, this.container)[0].id;
        this.xformsSelectTrigger = YAHOO.util.Dom.getElementsByClassName("fr-select1-button-xforms-select", null, this.container)[0];

        // Initialize YUI button
        this.yuiButton = new YAHOO.widget.Button(this.htmlButton.id, {
            type: "menu",
            menu: [],
            lazyloadmenu: false
        });
        this.yuiButton.getMenu().cfg.setProperty("scrollincrement", 10);

        // Populate list and set initial value
        this.itemsetChanged();
        this.valueChanged();
    },

    /**
     * When the button becomes enabled, update its readonly state, as that state might have changed while the button
     * was disabled, without an xforms-readonly or xforms-readwrite event being dispatched.
     */
    enabled: function() {
        this.yuiButton.set("disabled", YAHOO.util.Dom.hasClass(this.select1ControlId, "xforms-readonly"));
    },

    /**
     * We can't store a reference to the HTML select as an attribute of this object, as the HTML select gets entirely
     * recreated on IE when the itemset changes.
     */
    getHTMLSelect: function() {
        var select1Control = YAHOO.util.Dom.get(this.select1ControlId);
        return ORBEON.util.Dom.getElementByTagName(select1Control , "select");
    },

    getMenuItems: function() {
        var newMenuItems = [];
        var htmlSelect = this.getHTMLSelect();
        var options = htmlSelect.options;
        for (var optionIndex = 0; optionIndex < options.length; optionIndex++) {
            var option = { label: options[optionIndex].text, value: options[optionIndex].value };
            newMenuItems.push({ text: option.label, value: option.value, onclick: { fn: this.selectionMade, obj: option, scope: this }});
        }
        return newMenuItems;
    },

    /**
     * When users select a value from the menu, set that value as the label of the button.
     */
    selectionMade: function(name, event, option) {
        this.yuiButton.set("label", option.label);
        ORBEON.xforms.Document.setValue(this.select1ControlId, option.value);
        ORBEON.util.Dom.getElementByTagName(this.xformsSelectTrigger, "button").click();
    },

    /**
     * When the server tells us that the value changed, lookup the label for the new value, and set it on the button.
     */
    valueChanged: function() {
        var htmlSelect = this.getHTMLSelect();
        var currentValue = ORBEON.xforms.Document.getValue(this.select1ControlId);
        for (var optionIndex = 0; optionIndex < htmlSelect.options.length; optionIndex++) {
            var option = htmlSelect.options[optionIndex];
            if (option.value  == currentValue) {
                this.yuiButton.set("label", option.text);
                break;
            }
        }
    },

    itemsetChanged: function() {
        var yuiMenu = this.yuiButton.getMenu();
        yuiMenu.clearContent();
        yuiMenu.addItems(this.getMenuItems());
        yuiMenu.render();
    },

    readonly:  function() { this.yuiButton.set("disabled", true); },
    readwrite: function() { this.yuiButton.set("disabled", false); }
};

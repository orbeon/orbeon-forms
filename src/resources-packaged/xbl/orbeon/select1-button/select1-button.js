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
    htmlSelect: null,
    select1Control: null,
    yuiButton: null,

    /**
     * Constructor
     */
    init: function() {

        // Get references to HTML elements
        this.htmlButton = YAHOO.util.Dom.getElementsByClassName("fr-select1-button-button", null, this.container)[0];
        this.select1Control = YAHOO.util.Dom.getElementsByClassName("fr-select1-button-select1", null, this.container)[0];
        this.htmlSelect = ORBEON.util.Dom.getElementByTagName(this.select1Control , "select");

        // Initialize YUI button
        this.yuiButton = new YAHOO.widget.Button(this.htmlButton.id, {
            type: "menu",
            menu: [],
            lazyloadmenu: false
        });
        this.yuiButton.getMenu().cfg.setProperty("scrollincrement", 10);

        // Populate list and set initial value
        this.itemsetChanged();
        var initialValue = ORBEON.xforms.Document.getValue(this.select1Control.id);
        for (var optionIndex = 0; optionIndex < this.htmlSelect.options.length; optionIndex++) {
            var option = this.htmlSelect.options[optionIndex];
            if (option.value  == initialValue) {
                this.yuiButton.set("label", option.text);
                break;
            }
        }
    },

    getMenuItems: function() {
        var newMenuItems = [];
        var options = this.htmlSelect.options;
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
        ORBEON.xforms.Document.setValue(this.select1Control.id, option.value);
    },

    /**
     * When the server tells us that the value changed, lookup the label for the new value, and set it on the button.
     */
    valueChanged: function() {
        var newValue = ORBEON.xforms.Document.getValue(this.select1Control.id);
        var menu = this.yuiButton.getMenu();
        var yuiItems = menu.getItems();
        for (var yuiItemIndex = 0; yuiItemIndex < yuiItems.length; yuiItemIndex++) {
            var yuiItem = yuiItems[yuiItemIndex];
            if (yuiItem.value == newValue) {
                var label = yuiItem.cfg.getProperty("text");
                this.yuiButton.set("label", label);
            }
        }
    },

    itemsetChanged: function() {
        var yuiMenu = this.yuiButton.getMenu();
        yuiMenu.clearContent();
        yuiMenu.addItems(this.getMenuItems());
        yuiMenu.render();
    }
};

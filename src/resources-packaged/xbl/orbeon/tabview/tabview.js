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
YAHOO.xbl.fr.TabView = function() {};
ORBEON.xforms.XBL.declareClass(YAHOO.xbl.fr.TabView, "xbl-fr-tabview");
YAHOO.xbl.fr.TabView.prototype = {

    yuiTabView: null,

    /**
     * Constructor
     */
    init: function() {
        this.yuiTabView = new YAHOO.widget.TabView(YAHOO.util.Dom.getElementsByClassName("yui-navset", null, this.container)[0]);
        this.yuiTabView.addListener("activeTabChange", this.activeTabChange, this, true);
    },

    activeTabChange: function(event) {
        function getTrigger(selectDeselect, index) {
            var trigger = YAHOO.util.Dom.getElementsByClassName
                ("fr-tabview-" + selectDeselect + "-" + (index + 1), null, this.container)[0];
            return ORBEON.util.Dom.getElementByTagName(trigger, "button");
        }

        // Deselect other tabs
        getTrigger("deselect", this.yuiTabView.getTabIndex(event.prevValue)).click();
        // Select new tab
        getTrigger("select", this.yuiTabView.getTabIndex(event.newValue)).click();
    },

    /**
     * Find position of this elements amongts its sibling elements
     */
    getElementIndex: function(element) {
        var children = YAHOO.util.Dom.getChildren(element.parentNode);
        for (var childIndex = 0; childIndex < children.length; childIndex++)
            if (children[childIndex] == element) return childIndex;
        return -1;
    },

    /**
     * Respond to fr-toggle event.
     */
    toggle: function(groupElement) {
        var tabIndex = this.getElementIndex(groupElement.parentNode);
        this.yuiTabView.set("activeTab", this.yuiTabView.getTab(tabIndex), false);
    },

    readonly: function(groupElement) {
        var tabIndex = this.getElementIndex(groupElement.parentNode);
        this.yuiTabView.getTab(tabIndex).set("disabled", true);
    },

    readwrite: function(groupElement) {
        var tabIndex = this.getElementIndex(groupElement.parentNode);
        this.yuiTabView.getTab(tabIndex).set("disabled", false);
    }
};

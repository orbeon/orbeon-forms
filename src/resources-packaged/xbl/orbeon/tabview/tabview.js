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

    /**
     * Listener on a change to the active tab, either because users have clicked on another tab or because
     * a tab received the fr-toggle event.
     */
    activeTabChange: function(event) {
        var tabView = this;

        var OrderEnum = { PREV: 0, NEXT: 1 };
        function changeTab(order, index) {
            // Click button to inform XForms about the tab change
            var trigger = $(tabView.container).find('.fr-tabview-' + (order == OrderEnum.PREV ? 'deselect' : 'select'))[index];
            $(trigger).find('button')[0].click();
        }

        changeTab(OrderEnum.PREV, tabView.yuiTabView.getTabIndex(event.prevValue));
        changeTab(OrderEnum.NEXT, tabView.yuiTabView.getTabIndex(event.newValue));
    },

    /**
     * Find position of this elements amongst its sibling elements
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

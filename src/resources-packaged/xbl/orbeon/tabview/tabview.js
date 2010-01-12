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
            return YAHOO.util.Dom.getElementsByClassName("fr-tabview-" + selectDeselect + "-" + (index + 1), 
                null, this.container)[0];
        }

        // Deselect other tabs
        getTrigger("deselect", this.yuiTabView.getTabIndex(event.prevValue)).click();
        // Select new tab
        getTrigger("select", this.yuiTabView.getTabIndex(event.newValue)).click();
    }
};

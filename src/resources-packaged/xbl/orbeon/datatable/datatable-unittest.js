/**
 * Copyright (C) 2009 Orbeon, Inc.
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
YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

    name: "datatable",

    isOpenAccordionCase: function(targetId) {
        var dd = YAHOO.util.Dom.get('my-accordion$d-' + targetId);
        return YAHOO.util.Dom.hasClass(dd, 'a-m-d-expand');
    },

    toggleAccordionCase: function (targetId) {
        var dt = YAHOO.util.Dom.get('my-accordion$t-' + targetId);
        YAHOO.util.UserAction.click(dt, {clientX: 1});
     },

    openAccordionCase: function (targetId) {
        if (!this.isOpenAccordionCase(targetId)) {
            this.toggleAccordionCase(targetId);
            this.wait(function(){this.openAccordionCase(targetId);}, 500);
        }
    },

    closeAccordionCase: function (targetId) {
        if (this.isOpenAccordionCase(targetId)) {
            this.toggleAccordionCase(targetId);
            this.wait(function(){this.closeAccordionCase(targetId);}, 500);
        }
    },

    test314211: function() {
        this.openAccordionCase('_314211');
        var table = YAHOO.util.Dom.get('my-accordion$table-314211$table-314211-table');
        YAHOO.util.Assert.isTrue(table.clientWidth < 300, 'The table width (' + table.clientWidth + "px) should be small, let's say < 300px...");
        this.closeAccordionCase('_314211');
    },

    test314174: function() {
        this.openAccordionCase('_314174');
        //TODO: test something here!
        this.closeAccordionCase('_314174');
    },
    
    test314210: function() {
        this.openAccordionCase('_314210');
        var headerTable = YAHOO.util.Dom.get('my-accordion$table-314210$table-314210-table');
        YAHOO.util.Assert.isTrue(headerTable.clientWidth > headerTable.parentNode.clientWidth, 'The table header width (' + headerTable.clientWidth + 'px) should be larger than its container width (' + headerTable.parentNode.clientWidth + 'px).');
        this.closeAccordionCase('_314210');
    },

    EOS: ""
}));

ORBEON.xforms.Events.orbeonLoadedEvent.subscribe(function() {
    if (parent && parent.TestManager) {
        parent.TestManager.load();
    } else {
        new YAHOO.tool.TestLogger();
        YAHOO.tool.TestRunner.run();
    }
});

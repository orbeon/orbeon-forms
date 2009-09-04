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
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            YAHOO.util.UserAction.click(dt, {clientX: 1});
        }, function() {
            return;
        });
     },

    openAccordionCase: function (targetId) {
        if (!this.isOpenAccordionCase(targetId)) {
            this.toggleAccordionCase(targetId);
        }
    },

    closeAccordionCase: function (targetId) {
        if (this.isOpenAccordionCase(targetId)) {
            this.toggleAccordionCase(targetId);
        }
    },

    testSimplistic: function() {
        this.openAccordionCase('simplistic');
        var table = YAHOO.util.Dom.get('my-accordion$table-simplistic$table-simplistic-table');
        YAHOO.util.Assert.isTrue(table.clientWidth < 300, 'The table width (' + table.clientWidth + "px) should be small, let's say < 300px...");
        this.closeAccordionCase('simplistic');
    },

    test314210: function() {
        this.openAccordionCase('_314210');
        var headerTable = YAHOO.util.Dom.get('my-accordion$table-314210$table-314210-table');
        YAHOO.util.Assert.isTrue(headerTable.clientWidth > headerTable.parentNode.clientWidth, 'The table header width (' + headerTable.clientWidth + 'px) should be larger than its container width (' + headerTable.parentNode.clientWidth + 'px).');
        this.closeAccordionCase('_314210');
    }

}));

ORBEON.xforms.Events.orbeonLoadedEvent.subscribe(function() {
    if (parent && parent.TestManager) {
        parent.TestManager.load();
    } else {
        new YAHOO.tool.TestLogger();
        YAHOO.tool.TestRunner.run();
    }
});

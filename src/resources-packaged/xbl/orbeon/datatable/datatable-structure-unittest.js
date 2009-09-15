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

var accordionAccessMethod = "api";

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

    openAccordionCase: function (targetId, callback) {
        if (accordionAccessMethod == 'css') {

            var dt = YAHOO.util.Dom.get('my-accordion$t-' + targetId);
            var dd = YAHOO.util.Dom.get('my-accordion$d-' + targetId);
            YAHOO.util.Dom.addClass(dt, 'a-m-d-expand');
            YAHOO.util.Dom.addClass(dd, 'a-m-d-expand');

        } else if (accordionAccessMethod == 'api') {

            var dl = YAHOO.util.Dom.get('my-accordion$dl');
            var dt = YAHOO.util.Dom.get('my-accordion$t-' + targetId);
            var dd = YAHOO.util.Dom.get('my-accordion$d-' + targetId);
            AccordionMenu.expandCase(dl, dt,dd);

        } else /* click */ {

            if (!this.isOpenAccordionCase(targetId)) {
                this.toggleAccordionCase(targetId);
            }
        }

        // Check if the action has been done and call back
        if (this.isOpenAccordionCase(targetId)) {
            if (callback) {
                callback.call();
            }
         } else {
            this.wait(function(){this.openAccordionCase(targetId, callback);}, 10);
         }
    },

    closeAccordionCase: function (targetId, callback) {
        if (accordionAccessMethod == 'css') {

            var dt = YAHOO.util.Dom.get('my-accordion$t-' + targetId);
            var dd = YAHOO.util.Dom.get('my-accordion$d-' + targetId);
            YAHOO.util.Dom.removeClass(dt, 'a-m-d-expand');
            YAHOO.util.Dom.removeClass(dd, 'a-m-d-expand');

        } else if (accordionAccessMethod == 'api') {

            var dl = YAHOO.util.Dom.get('my-accordion$dl');
            var dt = YAHOO.util.Dom.get('my-accordion$t-' + targetId);
            var dd = YAHOO.util.Dom.get('my-accordion$d-' + targetId);
            AccordionMenu.collapseCase(dl, dt,dd);

        } else /* click */ {
            if (this.isOpenAccordionCase(targetId)) {
                this.toggleAccordionCase(targetId);
            }
        }

        // Check if the action has been done and call back
         if (!this.isOpenAccordionCase(targetId)) {
            if (callback) {
                callback.call();
            }
         } else {
            this.wait(function(){this.closeAccordionCase(targetId, callback);}, 10);
         }
    },

    checkTableStructure: function(table, nbcols) {
        YAHOO.util.Assert.isObject(table.tHead, 'The table header is missing');
        YAHOO.util.Assert.areEqual(1, table.tHead.rows.length, 'There should be exactly one header row (not ' + table.tHead.rows.length + ')');
        YAHOO.util.Assert.areEqual(nbcols, table.tHead.rows[0].cells.length, table.tHead.rows[0].cells.length + ' header columns found instead of ' + nbcols);
        YAHOO.util.Assert.areEqual(1, table.tBodies.length, 'There should be exactly one body (not ' + table.tBodies.length + ')');
        YAHOO.util.Assert.areEqual(nbcols, table.tBodies[0].rows[2].cells.length, table.tBodies[0].rows[0].cells.length + ' columns found on the first body row instead of ' + nbcols);
    },

    testComplete: function() {
        var thiss = this;
        this.openAccordionCase('complete', function(){
            var table = YAHOO.util.Dom.get('my-accordion$table-complete$table-complete-table');
            thiss.checkTableStructure(table, 2);
            thiss.closeAccordionCase('complete');
        });
    },

    testTrRepeatNodeset: function() {
        var thiss = this;
        this.openAccordionCase('tr-repeat-nodeset', function(){
            var table = YAHOO.util.Dom.get('my-accordion$table-tr-repeat-nodeset$table-tr-repeat-nodeset-table');
            thiss.checkTableStructure(table, 2);
            thiss.closeAccordionCase('tr-repeat-nodeset');
        });
    },
    
    testNoHeader: function() {
        var thiss = this;
        this.openAccordionCase('no-header', function(){
            var table = YAHOO.util.Dom.get('my-accordion$table-no-header$table-no-header-table');
            thiss.checkTableStructure(table, 2);
            thiss.closeAccordionCase('no-header');
        });
    },

    testNoHeaderTrRepeatNodeset: function() {
        var thiss = this;
        this.openAccordionCase('no-header-tr-repeat-nodeset', function(){
            var table = YAHOO.util.Dom.get('my-accordion$table-no-header-tr-repeat-nodeset$table-no-header-tr-repeat-nodeset-table');
            thiss.checkTableStructure(table, 2);
            thiss.closeAccordionCase('no-header-tr-repeat-nodeset');
        });
    },

    EOS: ""
}));

ORBEON.xforms.Events.orbeonLoadedEvent.subscribe(function() {
    AccordionMenu.setting('my-accordion$dl', {animation: true, seconds: 0.001, openedIds: [], dependent: false, easeOut: false});
    if (parent && parent.TestManager) {
        parent.TestManager.load();
    } else {
        new YAHOO.tool.TestLogger();
        YAHOO.tool.TestRunner.run();
    }
});

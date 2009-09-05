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

    openAccordionCase: function (targetId, callback) {
//        var dt = YAHOO.util.Dom.get('my-accordion$t-' + targetId);
//        var dd = YAHOO.util.Dom.get('my-accordion$d-' + targetId);
//        YAHOO.util.Dom.addClass(dt, 'a-m-d-expand');
//        YAHOO.util.Dom.addClass(dd, 'a-m-d-expand');

//        var dl = YAHOO.util.Dom.get('my-accordion$dl');
//        var dt = YAHOO.util.Dom.get('my-accordion$t-' + targetId);
//        var dd = YAHOO.util.Dom.get('my-accordion$d-' + targetId);
//        AccordionMenu.expandCase(dl, dt,dd);

        if (!this.isOpenAccordionCase(targetId)) {
            this.toggleAccordionCase(targetId);
         }
         if (this.isOpenAccordionCase(targetId)) {
            if (callback) {
                callback.call();
            }
         } else {
            this.wait(function(){this.openAccordionCase(targetId, callback);}, 10);
         }
    },

    closeAccordionCase: function (targetId, callback) {
//        var dt = YAHOO.util.Dom.get('my-accordion$t-' + targetId);
//        var dd = YAHOO.util.Dom.get('my-accordion$d-' + targetId);
//        YAHOO.util.Dom.removeClass(dt, 'a-m-d-expand');
//        YAHOO.util.Dom.removeClass(dd, 'a-m-d-expand');

//        var dl = YAHOO.util.Dom.get('my-accordion$dl');
//        var dt = YAHOO.util.Dom.get('my-accordion$t-' + targetId);
//        var dd = YAHOO.util.Dom.get('my-accordion$d-' + targetId);
//        AccordionMenu.collapseCase(dl, dt,dd);

        if (this.isOpenAccordionCase(targetId)) {
            this.toggleAccordionCase(targetId);
        }
         if (!this.isOpenAccordionCase(targetId)) {
            if (callback) {
                callback.call();
            }
         } else {
            this.wait(function(){this.closeAccordionCase(targetId, callback);}, 10);
         }
    },

    test314209: function() {
        var thiss = this;
        this.closeAccordionCase('_314209', function(){
            thiss.openAccordionCase('_314209', function() {
                var table = YAHOO.util.Dom.get('my-accordion$table-314209$table-314209-table');
                var visibility = YAHOO.util.Dom.getStyle(table, 'visibility');
                YAHOO.util.Assert.isTrue(visibility == 'visible' || visibility == 'inherit', 'Visibility should be visible or inherit, not ' + visibility );
                // unfortunately, I haven't found any way to check that the table is actually visible!
                //thiss.closeAccordionCase('_314209');
            });

        });
    },
    
    test314211: function() {
        var thiss = this;
        this.openAccordionCase('_314211', function() {
            var table = YAHOO.util.Dom.get('my-accordion$table-314211$table-314211-table');
            YAHOO.util.Assert.isTrue(table.clientWidth < 300, 'The table width (' + table.clientWidth + "px) should be small, let's say < 300px...");
            thiss.closeAccordionCase('_314211');

        });
    },

    test314174: function() {
        this.openAccordionCase('_314174');
        //TODO: test something here!
        this.closeAccordionCase('_314174');
    },
    
    test314210: function() {
        var thiss = this;
        this.openAccordionCase('_314210', function(){
            var headerTable = YAHOO.util.Dom.get('my-accordion$table-314210$table-314210-table');
            YAHOO.util.Assert.isTrue(headerTable.clientWidth > headerTable.parentNode.clientWidth, 'The table header width (' + headerTable.clientWidth + 'px) should be larger than its container width (' + headerTable.parentNode.clientWidth + 'px).');
            thiss.closeAccordionCase('_314210');
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

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

    testComplete: function() {
        var thiss = this;
        ORBEON.widgets.datatable.unittests_lib.openAccordionCase(this, 'complete', function(){
            var table = YAHOO.util.Dom.get('my-accordion$table-complete$table-complete-table');
            ORBEON.widgets.datatable.unittests_lib.checkTableStructure(table, 2);
            ORBEON.widgets.datatable.unittests_lib.closeAccordionCase(thiss, 'complete');
        });
    },

    testTrRepeatNodeset: function() {
        var thiss = this;
        ORBEON.widgets.datatable.unittests_lib.openAccordionCase(thiss, 'tr-repeat-nodeset', function(){
            var table = YAHOO.util.Dom.get('my-accordion$table-tr-repeat-nodeset$table-tr-repeat-nodeset-table');
            ORBEON.widgets.datatable.unittests_lib.checkTableStructure(table, 2);
            ORBEON.widgets.datatable.unittests_lib.closeAccordionCase(thiss, 'tr-repeat-nodeset');
        });
    },
    
    testNoHeader: function() {
        var thiss = this;
        ORBEON.widgets.datatable.unittests_lib.openAccordionCase(thiss, 'no-header', function(){
            var table = YAHOO.util.Dom.get('my-accordion$table-no-header$table-no-header-table');
            ORBEON.widgets.datatable.unittests_lib.checkTableStructure(table, 2);
            ORBEON.widgets.datatable.unittests_lib.closeAccordionCase(thiss, 'no-header');
        });
    },

    testNoHeaderTrRepeatNodeset: function() {
        var thiss = this;
        ORBEON.widgets.datatable.unittests_lib.openAccordionCase(thiss, 'no-header-tr-repeat-nodeset', function(){
            var table = YAHOO.util.Dom.get('my-accordion$table-no-header-tr-repeat-nodeset$table-no-header-tr-repeat-nodeset-table');
            ORBEON.widgets.datatable.unittests_lib.checkTableStructure(table, 2);
            ORBEON.widgets.datatable.unittests_lib.closeAccordionCase(thiss, 'no-header-tr-repeat-nodeset');
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

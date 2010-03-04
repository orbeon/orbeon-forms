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


var testCase = {
 
    name: "datatable",

    testComplete: function() {
        var thiss = this;
        thiss.openAccordionCase(this, 'complete', function(){
            var table = YAHOO.util.Dom.get('my-accordion$table-complete$table-complete-table');
            thiss.checkTableStructure(table, 2);
            thiss.closeAccordionCase(thiss, 'complete');
        });
    },

    testTrRepeatNodeset: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, 'tr-repeat-nodeset', function(){
            var table = YAHOO.util.Dom.get('my-accordion$table-tr-repeat-nodeset$table-tr-repeat-nodeset-table');
            thiss.checkTableStructure(table, 2);
            thiss.closeAccordionCase(thiss, 'tr-repeat-nodeset');
        });
    },
    
    testNoHeader: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, 'no-header', function(){
            var table = YAHOO.util.Dom.get('my-accordion$table-no-header$table-no-header-table');
            thiss.checkTableStructure(table, 2);
            thiss.closeAccordionCase(thiss, 'no-header');
        });
    },

    testNoHeaderTrRepeatNodeset: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, 'no-header-tr-repeat-nodeset', function(){
            var table = YAHOO.util.Dom.get('my-accordion$table-no-header-tr-repeat-nodeset$table-no-header-tr-repeat-nodeset-table');
            thiss.checkTableStructure(table, 2);
            thiss.closeAccordionCase(thiss, 'no-header-tr-repeat-nodeset');
        });
    },

    testVariables: function() {
        var thiss = this;
        thiss.openAccordionCase(this, 'variables', function(){
            var table = YAHOO.util.Dom.get('my-accordion$table-variables$table-variables-table');
            thiss.checkTableStructure(table, 2);
            thiss.closeAccordionCase(thiss, 'variables');
        });
    },
    
    EOS: ""
};

ORBEON.xforms.Events.orbeonLoadedEvent.subscribe(function() {
    for (var property in YAHOO.xbl.fr.Datatable.unittests_lib) {
        testCase[property] = YAHOO.xbl.fr.Datatable.unittests_lib[property];
    }
    YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase(testCase));
    AccordionMenu.setting('my-accordion$dl', {animation: true, seconds: 0.001, openedIds: [], dependent: false, easeOut: false});
    if (parent && parent.TestManager) {
        parent.TestManager.load();
    } else {
        new YAHOO.tool.TestLogger();
        YAHOO.tool.TestRunner.run();
    }
});

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

    lib: ORBEON.widgets.datatable.unittests_lib,

    testHelloWorld: function() {
        var thiss = this;
        ORBEON.widgets.datatable.unittests_lib.openAccordionCase(thiss, 'hello_world', function() {
            // Test significant values from the column set
            var colset = YAHOO.util.Dom.get('my-accordion$hello_world-table$debug-column·1');
            ORBEON.widgets.datatable.unittests_lib.checkColTypeValue(colset, 'columnSet');
            ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(colset, 'index', 1);
            ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(colset, 'nbColumns', 4);
            ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(colset, 'nodeset', '*');
            // Test significant values from the first column
            var column = YAHOO.util.Dom.get('my-accordion$hello_world-table$debug-column·2');
            ORBEON.widgets.datatable.unittests_lib.checkColTypeValue(column, 'column');
            ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(column, 'index', 1);
            ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(column, 'position', 1);
            // Test significant values from the second column
            column = YAHOO.util.Dom.get('my-accordion$hello_world-table$debug-column·3');
            ORBEON.widgets.datatable.unittests_lib.checkColTypeValue(column, 'column');
            ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(column, 'index', 2);
            ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(column, 'position', 2);
            // Test significant values from the third column
            column = YAHOO.util.Dom.get('my-accordion$hello_world-table$debug-column·4');
            ORBEON.widgets.datatable.unittests_lib.checkColTypeValue(column, 'column');
            ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(column, 'index', 3);
            ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(column, 'position', 3);
            // Test significant values from the fourth column
            column = YAHOO.util.Dom.get('my-accordion$hello_world-table$debug-column·5');
            ORBEON.widgets.datatable.unittests_lib.checkColTypeValue(column, 'column');
            ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(column, 'index', 4);
            ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(column, 'position', 4);
            // Check the table structure
            var table = YAHOO.util.Dom.get('my-accordion$hello_world-table$hello_world-table-table');
            ORBEON.widgets.datatable.unittests_lib.checkTableStructure(table, 4);

            ORBEON.widgets.datatable.unittests_lib.closeAccordionCase(thiss, 'hello_world');
        });
    },

    testMixed: function() {
         var thiss = this;
         ORBEON.widgets.datatable.unittests_lib.openAccordionCase(thiss, 'mixed', function() {
             // Test significant values the first column
             var div = YAHOO.util.Dom.get('my-accordion$mixed-table$debug-column·1');
             ORBEON.widgets.datatable.unittests_lib.checkColTypeValue(div, 'column');
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'index', 1);
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'position', undefined);
             // Test significant values the column set
             div = YAHOO.util.Dom.get('my-accordion$mixed-table$debug-column·2');
             ORBEON.widgets.datatable.unittests_lib.checkColTypeValue(div, 'columnSet');
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'index', 2);
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'nbColumns', 4);
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'nodeset', '*');
             // Test significant values from the second column
             div = YAHOO.util.Dom.get('my-accordion$mixed-table$debug-column·3');
             ORBEON.widgets.datatable.unittests_lib.checkColTypeValue(div, 'column');
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'index', 2);
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'position', 1);
             // Test significant values from the third column
             div = YAHOO.util.Dom.get('my-accordion$mixed-table$debug-column·4');
             ORBEON.widgets.datatable.unittests_lib.checkColTypeValue(div, 'column');
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'index', 3);
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'position', 2);
             // Test significant values from the fourth column
             div = YAHOO.util.Dom.get('my-accordion$mixed-table$debug-column·5');
             ORBEON.widgets.datatable.unittests_lib.checkColTypeValue(div, 'column');
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'index', 4);
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'position', 3);
             // Test significant values from the fifth column
             div = YAHOO.util.Dom.get('my-accordion$mixed-table$debug-column·6');
             ORBEON.widgets.datatable.unittests_lib.checkColTypeValue(div, 'column');
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'index', 5);
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'position', 4);
             // Test significant values from the sixth column
             div = YAHOO.util.Dom.get('my-accordion$mixed-table$debug-column·7');
             ORBEON.widgets.datatable.unittests_lib.checkColTypeValue(div, 'column');
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'index', 6);
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'position', undefined);

             // Check the table structure
             var table = YAHOO.util.Dom.get('my-accordion$mixed-table$mixed-table-table');
             ORBEON.widgets.datatable.unittests_lib.checkTableStructure(table, 6);

             ORBEON.widgets.datatable.unittests_lib.closeAccordionCase(thiss, 'mixed');
         });
     },

    testMixedTwoColumsets: function() {
         var thiss = this;
         ORBEON.widgets.datatable.unittests_lib.openAccordionCase(thiss, 'mixed-two-columnsets', function() {
             // Test significant values the first column set
             var div = YAHOO.util.Dom.get('my-accordion$mixed-two-columnsets-table$debug-column·1');
             ORBEON.widgets.datatable.unittests_lib.checkColTypeValue(div, 'columnSet');
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'index', 1);
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'nbColumns', 2);
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'nodeset', '*[position() &lt; 3]');
             // First column (dynamic)
             div = YAHOO.util.Dom.get('my-accordion$mixed-two-columnsets-table$debug-column·2');
             ORBEON.widgets.datatable.unittests_lib.checkColTypeValue(div, 'column');
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'index', 1);
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'position', 1);
             // Second column (dynamic)
             div = YAHOO.util.Dom.get('my-accordion$mixed-two-columnsets-table$debug-column·3');
             ORBEON.widgets.datatable.unittests_lib.checkColTypeValue(div, 'column');
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'index', 2);
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'position', 2);
             // Third column (static)
             div = YAHOO.util.Dom.get('my-accordion$mixed-two-columnsets-table$debug-column·4');
             ORBEON.widgets.datatable.unittests_lib.checkColTypeValue(div, 'column');
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'index', 3);
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'position', undefined);
             // Second columnset
             div = YAHOO.util.Dom.get('my-accordion$mixed-two-columnsets-table$debug-column·5');
             ORBEON.widgets.datatable.unittests_lib.checkColTypeValue(div, 'columnSet');
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'index', 4);
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'nbColumns', 2);
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'nodeset', '*[position() &gt;= 3]');
             // Fourth column (dynamic)
             div = YAHOO.util.Dom.get('my-accordion$mixed-two-columnsets-table$debug-column·6');
             ORBEON.widgets.datatable.unittests_lib.checkColTypeValue(div, 'column');
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'index', 4);
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'position', 1);
             // Fifth column (dynamic)
             div = YAHOO.util.Dom.get('my-accordion$mixed-two-columnsets-table$debug-column·7');
             ORBEON.widgets.datatable.unittests_lib.checkColTypeValue(div, 'column');
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'index', 5);
             ORBEON.widgets.datatable.unittests_lib.checkColDebugValue(div, 'position', 2);


             // Check the table structure
             var table = YAHOO.util.Dom.get('my-accordion$mixed-two-columnsets-table$mixed-two-columnsets-table-table');
             ORBEON.widgets.datatable.unittests_lib.checkTableStructure(table, 5);

             ORBEON.widgets.datatable.unittests_lib.closeAccordionCase(thiss, 'mixed-two-columnsets');
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

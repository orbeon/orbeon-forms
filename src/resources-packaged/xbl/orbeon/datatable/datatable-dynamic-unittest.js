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

    name: "datatable: dynamic columns features",

    testHelloWorld: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, 'hello_world', function() {
            // Test significant values from the column set
            var colset = YAHOO.util.Dom.get('my-accordion$hello_world-table$debug-column·1');
            thiss.checkColTypeValue(colset, 'columnSet');
            thiss.checkColDebugValue(colset, 'index', 1);
            thiss.checkColDebugValue(colset, 'nbColumns', 4);
            thiss.checkColDebugValue(colset, 'nodeset', '*');
            // Test significant values from the first column
            var column = YAHOO.util.Dom.get('my-accordion$hello_world-table$debug-column·2');
            thiss.checkColTypeValue(column, 'column');
            thiss.checkColDebugValue(column, 'index', 1);
            thiss.checkColDebugValue(column, 'position', 1);
            // Test significant values from the second column
            column = YAHOO.util.Dom.get('my-accordion$hello_world-table$debug-column·3');
            thiss.checkColTypeValue(column, 'column');
            thiss.checkColDebugValue(column, 'index', 2);
            thiss.checkColDebugValue(column, 'position', 2);
            // Test significant values from the third column
            column = YAHOO.util.Dom.get('my-accordion$hello_world-table$debug-column·4');
            thiss.checkColTypeValue(column, 'column');
            thiss.checkColDebugValue(column, 'index', 3);
            thiss.checkColDebugValue(column, 'position', 3);
            // Test significant values from the fourth column
            column = YAHOO.util.Dom.get('my-accordion$hello_world-table$debug-column·5');
            thiss.checkColTypeValue(column, 'column');
            thiss.checkColDebugValue(column, 'index', 4);
            thiss.checkColDebugValue(column, 'position', 4);
            // Check the table structure
            var table = YAHOO.util.Dom.get('my-accordion$hello_world-table$hello_world-table-table');
            thiss.checkTableStructure(table, 4);

            thiss.closeAccordionCase(thiss, 'hello_world');
        });
    },

    testMixed: function() {
         var thiss = this;
         thiss.openAccordionCase(thiss, 'mixed', function() {
             // Test significant values the first column
             var div = YAHOO.util.Dom.get('my-accordion$mixed-table$debug-column·1');
             thiss.checkColTypeValue(div, 'column');
             thiss.checkColDebugValue(div, 'index', 1);
             thiss.checkColDebugValue(div, 'position', undefined);
             // Test significant values the column set
             div = YAHOO.util.Dom.get('my-accordion$mixed-table$debug-column·2');
             thiss.checkColTypeValue(div, 'columnSet');
             thiss.checkColDebugValue(div, 'index', 2);
             thiss.checkColDebugValue(div, 'nbColumns', 4);
             thiss.checkColDebugValue(div, 'nodeset', '*');
             // Test significant values from the second column
             div = YAHOO.util.Dom.get('my-accordion$mixed-table$debug-column·3');
             thiss.checkColTypeValue(div, 'column');
             thiss.checkColDebugValue(div, 'index', 2);
             thiss.checkColDebugValue(div, 'position', 1);
             // Test significant values from the third column
             div = YAHOO.util.Dom.get('my-accordion$mixed-table$debug-column·4');
             thiss.checkColTypeValue(div, 'column');
             thiss.checkColDebugValue(div, 'index', 3);
             thiss.checkColDebugValue(div, 'position', 2);
             // Test significant values from the fourth column
             div = YAHOO.util.Dom.get('my-accordion$mixed-table$debug-column·5');
             thiss.checkColTypeValue(div, 'column');
             thiss.checkColDebugValue(div, 'index', 4);
             thiss.checkColDebugValue(div, 'position', 3);
             // Test significant values from the fifth column
             div = YAHOO.util.Dom.get('my-accordion$mixed-table$debug-column·6');
             thiss.checkColTypeValue(div, 'column');
             thiss.checkColDebugValue(div, 'index', 5);
             thiss.checkColDebugValue(div, 'position', 4);
             // Test significant values from the sixth column
             div = YAHOO.util.Dom.get('my-accordion$mixed-table$debug-column·7');
             thiss.checkColTypeValue(div, 'column');
             thiss.checkColDebugValue(div, 'index', 6);
             thiss.checkColDebugValue(div, 'position', undefined);

             // Check the table structure
             var table = YAHOO.util.Dom.get('my-accordion$mixed-table$mixed-table-table');
             thiss.checkTableStructure(table, 6);

             thiss.closeAccordionCase(thiss, 'mixed');
         });
     },

    testMixedVariables: function() {
         var thiss = this;
         thiss.openAccordionCase(thiss, 'mixed-variables', function() {
             // Test significant values the first column
             var div = YAHOO.util.Dom.get('my-accordion$mixed-table-variables$debug-column·1');
             thiss.checkColTypeValue(div, 'column');
             thiss.checkColDebugValue(div, 'index', 1);
             thiss.checkColDebugValue(div, 'position', undefined);
             // Test significant values the column set
             div = YAHOO.util.Dom.get('my-accordion$mixed-table-variables$debug-column·2');
             thiss.checkColTypeValue(div, 'columnSet');
             thiss.checkColDebugValue(div, 'index', 2);
             thiss.checkColDebugValue(div, 'nbColumns', 4);
             thiss.checkColDebugValue(div, 'nodeset', '*');
             // Test significant values from the second column
             div = YAHOO.util.Dom.get('my-accordion$mixed-table-variables$debug-column·3');
             thiss.checkColTypeValue(div, 'column');
             thiss.checkColDebugValue(div, 'index', 2);
             thiss.checkColDebugValue(div, 'position', 1);
             // Test significant values from the third column
             div = YAHOO.util.Dom.get('my-accordion$mixed-table-variables$debug-column·4');
             thiss.checkColTypeValue(div, 'column');
             thiss.checkColDebugValue(div, 'index', 3);
             thiss.checkColDebugValue(div, 'position', 2);
             // Test significant values from the fourth column
             div = YAHOO.util.Dom.get('my-accordion$mixed-table-variables$debug-column·5');
             thiss.checkColTypeValue(div, 'column');
             thiss.checkColDebugValue(div, 'index', 4);
             thiss.checkColDebugValue(div, 'position', 3);
             // Test significant values from the fifth column
             div = YAHOO.util.Dom.get('my-accordion$mixed-table-variables$debug-column·6');
             thiss.checkColTypeValue(div, 'column');
             thiss.checkColDebugValue(div, 'index', 5);
             thiss.checkColDebugValue(div, 'position', 4);
             // Test significant values from the sixth column
             div = YAHOO.util.Dom.get('my-accordion$mixed-table-variables$debug-column·7');
             thiss.checkColTypeValue(div, 'column');
             thiss.checkColDebugValue(div, 'index', 6);
             thiss.checkColDebugValue(div, 'position', undefined);

             // Check the table structure
             var table = YAHOO.util.Dom.get('my-accordion$mixed-table$mixed-table-table');
             thiss.checkTableStructure(table, 6);

             thiss.closeAccordionCase(thiss, 'mixed-variables');
         });
     },

     testMixedTwoColumsets: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, 'mixed-two-columnsets', function() {
            // Test significant values the first column set
            var div = YAHOO.util.Dom.get('my-accordion$mixed-two-columnsets-table$debug-column·1');
            thiss.checkColTypeValue(div, 'columnSet');
            thiss.checkColDebugValue(div, 'index', 1);
            thiss.checkColDebugValue(div, 'nbColumns', 2);
            thiss.checkColDebugValue(div, 'nodeset', '*[position() &lt; 3]');
            // First column (dynamic)
            div = YAHOO.util.Dom.get('my-accordion$mixed-two-columnsets-table$debug-column·2');
            thiss.checkColTypeValue(div, 'column');
            thiss.checkColDebugValue(div, 'index', 1);
            thiss.checkColDebugValue(div, 'position', 1);
            // Second column (dynamic)
            div = YAHOO.util.Dom.get('my-accordion$mixed-two-columnsets-table$debug-column·3');
            thiss.checkColTypeValue(div, 'column');
            thiss.checkColDebugValue(div, 'index', 2);
            thiss.checkColDebugValue(div, 'position', 2);
            // Third column (static)
            div = YAHOO.util.Dom.get('my-accordion$mixed-two-columnsets-table$debug-column·4');
            thiss.checkColTypeValue(div, 'column');
            thiss.checkColDebugValue(div, 'index', 3);
            thiss.checkColDebugValue(div, 'position', undefined);
            // Second columnset
            div = YAHOO.util.Dom.get('my-accordion$mixed-two-columnsets-table$debug-column·5');
            thiss.checkColTypeValue(div, 'columnSet');
            thiss.checkColDebugValue(div, 'index', 4);
            thiss.checkColDebugValue(div, 'nbColumns', 2);
            thiss.checkColDebugValue(div, 'nodeset', '*[position() &gt;= 3]');
            // Fourth column (dynamic)
            div = YAHOO.util.Dom.get('my-accordion$mixed-two-columnsets-table$debug-column·6');
            thiss.checkColTypeValue(div, 'column');
            thiss.checkColDebugValue(div, 'index', 4);
            thiss.checkColDebugValue(div, 'position', 1);
            // Fifth column (dynamic)
            div = YAHOO.util.Dom.get('my-accordion$mixed-two-columnsets-table$debug-column·7');
            thiss.checkColTypeValue(div, 'column');
            thiss.checkColDebugValue(div, 'index', 5);
            thiss.checkColDebugValue(div, 'position', 2);


            // Check the table structure
            var table = YAHOO.util.Dom.get('my-accordion$mixed-two-columnsets-table$mixed-two-columnsets-table-table');
            thiss.checkTableStructure(table, 5);

            thiss.closeAccordionCase(thiss, 'mixed-two-columnsets');
        });
    },

    testStatic: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, 'static', function() {
            // Test significant values the first column set
            var div;
            // First column (static)
            div = YAHOO.util.Dom.get('my-accordion$static-table$debug-column·1');
            thiss.checkColTypeValue(div, 'column');
            thiss.checkColDebugValue(div, 'index', 1);
            thiss.checkColDebugValue(div, 'position', undefined);
            // Second column (static)
            div = YAHOO.util.Dom.get('my-accordion$static-table$debug-column·2');
            thiss.checkColTypeValue(div, 'column');
            thiss.checkColDebugValue(div, 'index', 2);
            thiss.checkColDebugValue(div, 'position', undefined);

            // Check the table structure
            var table = YAHOO.util.Dom.get('my-accordion$static-table$static-table-table');
            thiss.checkTableStructure(table, 2);

            thiss.closeAccordionCase(thiss, 'static');
        });
    },

    testResize: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, 'resize', function() {
            // Check the table structure
            var table = YAHOO.util.Dom.get('my-accordion$resize-table$resize-table-table');
            thiss.checkTableStructure(table, 6);
            thiss.checkTableAndContainerWidths(table);
            thiss.checkRowWidth(table.tHead.rows[0]);

            var th1 = table.tHead.rows[0].cells[0]; // Static column
            var th2 = table.tHead.rows[0].cells[3]; // Second dynamic column
            var width1 = th1.clientWidth;
            var width2 = th2.clientWidth;

            thiss.resizeColumn(th1, 100, 10);
            thiss.checkTableAndContainerWidths(table);
            thiss.checkRowWidth(table.tHead.rows[0]);
            YAHOO.util.Assert.areEqual(width2, th2.clientWidth, "The width of the second column shouldn't change (before: " + width2 + ", after: " + th2.clientWidth + ").");
            YAHOO.util.Assert.areEqual(width1 + 100, th1.clientWidth, "The width of the first column should be " + (width1 + 100) + ", not " + th1.clientWidth);

            thiss.resizeColumn(th2, 100, 10);
            thiss.checkTableAndContainerWidths(table);
            thiss.checkRowWidth(table.tHead.rows[0]);
            YAHOO.util.Assert.areEqual(width1 + 100, th1.clientWidth, "The width of the first column shouldn't change (before: " + width1 + 100 + ", after: " + th1.clientWidth + ").");
            YAHOO.util.Assert.areEqual(width2 + 100, th2.clientWidth, "The width of the second column should be " + (width2 + 100) + ", not " + th2.clientWidth);

            thiss.closeAccordionCase(thiss, 'resize');
        });
    },

    testSortable: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, 'sortable', function() {
            // Check the table structure
            var table = YAHOO.util.Dom.get('my-accordion$sortable-table$sortable-table-table');
            thiss.checkTableStructure(table, 6);
            thiss.checkTableAndContainerWidths(table);
            thiss.checkRowWidth(table.tHead.rows[0]);

            // Check significant values in the datatable local instance before any sort action
            var columnsDiv = YAHOO.util.Dom.get('my-accordion$sortable-table$debug-columns');
            thiss.checkColTypeValue(columnsDiv, 'columns');
            thiss.checkColDebugValue(columnsDiv, 'currentSortColumn', -1);

            var col1Div = YAHOO.util.Dom.get('my-accordion$sortable-table$debug-column·1');
            thiss.checkColTypeValue(col1Div, 'column');
            thiss.checkColDebugValue(col1Div, 'currentSortOrder', 'none');
            thiss.checkColDebugValue(col1Div, 'nextSortOrder', 'ascending');
            //thiss.checkColDebugValue(col1Div, 'type', 'number');
            thiss.checkColDebugValue(col1Div, 'pathToFirstNode', 'xxforms:component-context()/(line[length &lt; 20])[1]/(position())');
            thiss.checkColDebugValue(col1Div, 'sortKey', 'position()');
            thiss.checkColDebugValue(col1Div, 'fr:sortable', 'true');

            var col2Div = YAHOO.util.Dom.get('my-accordion$sortable-table$debug-column·3');
            thiss.checkColTypeValue(col2Div, 'column');
            thiss.checkColDebugValue(col2Div, 'currentSortOrder', 'none');
            thiss.checkColDebugValue(col2Div, 'nextSortOrder', 'ascending');
            //thiss.checkColDebugValue(col2Div, 'type', 'number');
            thiss.checkColDebugValue(col2Div, 'pathToFirstNode', 'xxforms:component-context()/(line[length &lt; 20])[1]/((*)[1]/.)');
            thiss.checkColDebugValue(col2Div, 'sortKey', '(*)[1]/.');
            thiss.checkColDebugValue(col2Div, 'fr:sortable', 'true');

            var col3Div = YAHOO.util.Dom.get('my-accordion$sortable-table$debug-column·4');
            thiss.checkColTypeValue(col3Div, 'column');
            thiss.checkColDebugValue(col3Div, 'currentSortOrder', 'none');
            thiss.checkColDebugValue(col3Div, 'nextSortOrder', 'ascending');
            //thiss.checkColDebugValue(col3Div, 'type', 'text');
            thiss.checkColDebugValue(col3Div, 'pathToFirstNode', 'xxforms:component-context()/(line[length &lt; 20])[1]/((*)[2]/.)');
            thiss.checkColDebugValue(col3Div, 'sortKey', '(*)[2]/.');
            thiss.checkColDebugValue(col3Div, 'fr:sortable', 'true');

            var col4Div = YAHOO.util.Dom.get('my-accordion$sortable-table$debug-column·5');
            thiss.checkColTypeValue(col4Div, 'column');
            thiss.checkColDebugValue(col4Div, 'currentSortOrder', 'none');
            thiss.checkColDebugValue(col4Div, 'nextSortOrder', 'ascending');
            //thiss.checkColDebugValue(col4Div, 'type', 'number');
            thiss.checkColDebugValue(col4Div, 'pathToFirstNode', 'xxforms:component-context()/(line[length &lt; 20])[1]/((*)[3]/.)');
            thiss.checkColDebugValue(col4Div, 'sortKey', '(*)[3]/.');
            thiss.checkColDebugValue(col4Div, 'fr:sortable', 'true');

            var col5Div = YAHOO.util.Dom.get('my-accordion$sortable-table$debug-column·6');
            thiss.checkColTypeValue(col5Div, 'column');
            thiss.checkColDebugValue(col5Div, 'currentSortOrder', 'none');
            thiss.checkColDebugValue(col5Div, 'nextSortOrder', 'ascending');
            //thiss.checkColDebugValue(col5Div, 'type', 'number');
            thiss.checkColDebugValue(col5Div, 'pathToFirstNode', 'xxforms:component-context()/(line[length &lt; 20])[1]/((*)[4]/.)');
            thiss.checkColDebugValue(col5Div, 'sortKey', '(*)[4]/.');
            thiss.checkColDebugValue(col5Div, 'fr:sortable', 'true');

            var col6Div = YAHOO.util.Dom.get('my-accordion$sortable-table$debug-column·7');
            thiss.checkColTypeValue(col6Div, 'column');
            thiss.checkColDebugValue(col6Div, 'currentSortOrder', 'none');
            thiss.checkColDebugValue(col6Div, 'nextSortOrder', 'ascending');
            //thiss.checkColDebugValue(col6Div, 'type', 'number');
            thiss.checkColDebugValue(col6Div, 'pathToFirstNode', 'xxforms:component-context()/(line[length &lt; 20])[1]/(round(length div nb-words))');
            thiss.checkColDebugValue(col6Div, 'sortKey', 'round(length div nb-words)');
            thiss.checkColDebugValue(col6Div, 'fr:sortable', 'true');

            // Click to sort the 3rd column
            thiss.clickAndCheckSortOrder(table, 3, 'ascending', 'text', function() {
                var col3th = YAHOO.util.Dom.get('my-accordion$sortable-table$sortable-th-dyn·2');
                thiss.checkColDebugValue(columnsDiv, 'currentSortColumn', 3);
                thiss.checkColDebugValue(col3Div, 'currentSortOrder', 'ascending');
                thiss.checkColDebugValue(col3Div, 'nextSortOrder', 'descending');

                thiss.clickAndCheckSortOrder(table, 6, 'ascending', 'number', function() {
                    thiss.checkColDebugValue(columnsDiv, 'currentSortColumn', 6);
                    thiss.checkColDebugValue(col6Div, 'currentSortOrder', 'ascending');
                    thiss.checkColDebugValue(col6Div, 'nextSortOrder', 'descending');
                    thiss.checkColDebugValue(col3Div, 'currentSortOrder', 'none');
                    thiss.checkColDebugValue(col3Div, 'nextSortOrder', 'ascending');

                    thiss.clickAndCheckSortOrder(table, 6, 'descending', 'number', function() {
                        thiss.checkColDebugValue(columnsDiv, 'currentSortColumn', 6);
                        thiss.checkColDebugValue(col6Div, 'currentSortOrder', 'descending');
                        thiss.checkColDebugValue(col6Div, 'nextSortOrder', 'ascending');

                        thiss.closeAccordionCase(thiss, 'sortable');
                    });

                });

            });
        });

    },

    testSorted: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, 'sorted', function() {
            // Check the table structure
            var table = YAHOO.util.Dom.get('my-accordion$sorted-table$sorted-table-table');
            thiss.checkTableStructure(table, 4);
            thiss.checkTableAndContainerWidths(table);
            thiss.checkRowWidth(table.tHead.rows[0]);

            // Check significant values in the datatable local instance before any sort action
            var columnsDiv = YAHOO.util.Dom.get('my-accordion$sorted-table$debug-columns');
            thiss.checkColTypeValue(columnsDiv, 'columns');
            thiss.checkColDebugValue(columnsDiv, 'currentSortColumn', -1); // not sugnificant at that point
            thiss.checkColDebugValue(columnsDiv, 'default', 'true');
            thiss.checkActualSortOrder(table, 4, 'ascending', 'number')

            var col1Div = YAHOO.util.Dom.get('my-accordion$sorted-table$debug-column·2');
            thiss.checkColTypeValue(col1Div, 'column');
            thiss.checkColDebugValue(col1Div, 'currentSortOrder', 'none');
            thiss.checkColDebugValue(col1Div, 'nextSortOrder', 'ascending');

            var col2Div = YAHOO.util.Dom.get('my-accordion$sorted-table$debug-column·3');
            thiss.checkColTypeValue(col2Div, 'column');
            thiss.checkColDebugValue(col2Div, 'currentSortOrder', 'none');
            thiss.checkColDebugValue(col2Div, 'nextSortOrder', 'ascending');

            var col3Div = YAHOO.util.Dom.get('my-accordion$sorted-table$debug-column·4');
            thiss.checkColTypeValue(col3Div, 'column');
            thiss.checkColDebugValue(col3Div, 'currentSortOrder', 'none');
            thiss.checkColDebugValue(col3Div, 'nextSortOrder', 'ascending');

            var col4Div = YAHOO.util.Dom.get('my-accordion$sorted-table$debug-column·5');
            thiss.checkColTypeValue(col4Div, 'column');
            thiss.checkColDebugValue(col4Div, 'currentSortOrder', 'ascending');
            thiss.checkColDebugValue(col4Div, 'fr:sorted', 'ascending');
            thiss.checkColDebugValue(col4Div, 'nextSortOrder', 'descending');

            // Click to sort the 4th column
            thiss.clickAndCheckSortOrder(table, 4, 'descending', 'number', function() {
                thiss.checkColDebugValue(columnsDiv, 'default', 'false');
                thiss.checkColDebugValue(columnsDiv, 'currentSortColumn', 4);
                thiss.checkColDebugValue(col4Div, 'currentSortOrder', 'descending');
                thiss.checkColDebugValue(col4Div, 'nextSortOrder', 'ascending');

                thiss.closeAccordionCase(thiss, 'sorted');

            });
        });

    },

    testSortableExternal: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, 'sortableExternal', function() {

            ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                ORBEON.xforms.Document.setValue("sortedColumn", "-1");
                ORBEON.xforms.Document.setValue("sortOrder", "none");
            }, function() {

                // Check the table structure
                var table = YAHOO.util.Dom.get('my-accordion$sortableExternal-table$sortableExternal-table-table');
                thiss.checkTableStructure(table, 6);
                thiss.checkTableAndContainerWidths(table);
                thiss.checkRowWidth(table.tHead.rows[0]);

                thiss.checkHint(table, 6, 'Hey you, click to sort ascending');

                // Click to sort the 6th column
                thiss.clickAndCheckSortOrder(table, 6, 'ascending', 'number', function() {
                    thiss.closeAccordionCase(thiss, 'sortableExternal');
                }, 'Hey you, click to sort ascending', 'Hey you, click to sort descending');

            });
        });

    },

    testScrollH: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, 'scrollH', function() {
            // Check the table structure
            var table = YAHOO.util.Dom.get('my-accordion$scrollH-table$scrollH-table-table');
            thiss.checkIsSplit(table, true);
            thiss.checkTableStructure(table, 6, true);
            thiss.checkCellClasses(table, true);
            thiss.checkCellStyles(table, true);

            thiss.closeAccordionCase(thiss, 'scrollH');

        });

    },

    testUpdate: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, 'update', function() {
            // Check the table structure
            var table = YAHOO.util.Dom.get('my-accordion$update-table$update-table-table');
            thiss.checkIsSplit(table, false);
            thiss.checkTableStructure(table, 6, false);
            thiss.checkNumberRows(table, 6, false);
            thiss.checkCellClasses(table, false);
            thiss.checkCellStyles(table, false);
            ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                ORBEON.xforms.Document.setValue("maxLength", "15");
            }, function() {
                // Test after reducing the number of rows
                thiss.checkNumberRows(table, 2, false);
                thiss.checkCellClasses(table, false);
                thiss.checkCellStyles(table, false);

                ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                    ORBEON.xforms.Document.setValue("maxLength", "40");
                }, function() {
                    // Test after increasing the numebr of rows
                    thiss.checkNumberRows(table, 16, false);
                    thiss.checkCellClasses(table, false);
                    thiss.checkCellStyles(table, false);

                    thiss.closeAccordionCase(thiss, 'update');

                });

            });
        });
    },

    testPaginate: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, 'paginate', function() {
            // Check the table structure
            var table = YAHOO.util.Dom.get('my-accordion$paginate-table$paginate-table-table');
            var container = YAHOO.util.Dom.get('my-accordion$paginate-table$paginate-table-container');
            thiss.checkColumnValues(table, 2, false, [0, 1, 2, 3, 4]);

            var linkNext = thiss.getPaginateLink(container, 'next >');
            ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                YAHOO.util.UserAction.click(linkNext, {clientX: 1});
            }, function() {
                // Test the status after clicking on "next"
                thiss.checkColumnValues(table, 2, false, [5, 6, 7, 8, 9]);

                var linkLast = thiss.getPaginateLink(container, 'last >>');
                ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                    YAHOO.util.UserAction.click(linkLast, {clientX: 1});
                }, function() {
                    // Test the status after clicking on "last"
                    thiss.checkColumnValues(table, 2, false, [35, 36, 37]);

                    thiss.closeAccordionCase(thiss, 'paginate');
                });
            });


        });
    },

    testPaginateMaxPage: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, 'paginateMaxPage', function() {
            // Check the table structure
            var table = YAHOO.util.Dom.get('my-accordion$paginateMaxPage-table$paginateMaxPage-table-table');
            thiss.checkColumnValues(table, 2, false, [0, 1, 2, 3]);
            var container = YAHOO.util.Dom.get('my-accordion$paginateMaxPage-table$paginateMaxPage-table-container');
            thiss.checkPaginationLinks(container, ['-<< first', '-< prev', '-1', '+2', '+3', '+4', '+5', '-...', '+10', '+next >', '+last >>']);

            var linkNext = thiss.getPaginateLink(container, 'next >');
            ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                YAHOO.util.UserAction.click(linkNext, {clientX: 1});
            }, function() {
                // Test the status after clicking on "next"
                thiss.checkColumnValues(table, 2, false, [4, 5, 6, 7]);
                thiss.checkPaginationLinks(container, ['+<< first', '+< prev', '+1', '-2', '+3', '+4', '+5', '-...', '+10', '+next >', '+last >>']);

                var linkLast = thiss.getPaginateLink(container, 'last >>');
                ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                    YAHOO.util.UserAction.click(linkLast, {clientX: 1});
                }, function() {
                    // Test the status after clicking on "last"
                    thiss.checkColumnValues(table, 2, false, [36, 37]);
                    thiss.checkPaginationLinks(container, ['+<< first', '+< prev', '+1', '-...', '+6', '+7', '+8', '+9', '-10', '-next >', '-last >>']);

                    thiss.closeAccordionCase(thiss, 'paginateMaxPage');
                });
            });


        });
    },


    testPaginateExternal: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, 'paginateExternal', function() {
            ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                ORBEON.xforms.Document.setValue("rowsPerPage", "3");
                ORBEON.xforms.Document.setValue("page", "7");
            }, function() {
                // Check the table structure
                var table = YAHOO.util.Dom.get('my-accordion$paginateExternal-table$paginateExternal-table-table');
                thiss.checkColumnValues(table, 2, false, [18, 19, 20]);
                var container = YAHOO.util.Dom.get('my-accordion$paginateExternal-table$paginateExternal-table-container');
                thiss.checkPaginationLinks(container, ['+<< first', '+< prev', '+1', '-...', '+6', '-7', '+8', '-...', '+13', '+next >', '+last >>']);

                var linkNext = thiss.getPaginateLink(container, '< prev');
                ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                    YAHOO.util.UserAction.click(linkNext, {clientX: 1});
                }, function() {
                    // Test the status after clicking on "prev"
                    thiss.checkColumnValues(table, 2, false, [15, 16, 17]);
                    thiss.checkPaginationLinks(container, ['+<< first', '+< prev', '+1', '-...', '+5', '-6', '+7', '-...', '+13', '+next >', '+last >>']);


                    thiss.closeAccordionCase(thiss, 'paginateExternal');
                });
            });

        });
    },

    // Commented out as this test is known not to work, and we have a bug open for it.
    Xtest314379: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, '_314379', function() {
            ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                ORBEON.xforms.Document.setValue("loading", "false");
            }, function() {
                var table = YAHOO.util.Dom.get('my-accordion$table-314379').getElementsByTagName("table")[0];
                var th = thiss.getSignificantElementByIndex(table.tHead.rows[0].cells, 1);
                var width;
                width = th.clientWidth;
                // resize the first column
                thiss.resizeColumn(th, 100, 10);
                // check the result
                YAHOO.util.Assert.areEqual(width + 100, th.clientWidth, "The width of the first column should be " + (width + 100) + ", not " + th.clientWidth);
                width = th.clientWidth;

                // Hide the table using the loading indicator
                ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                    ORBEON.xforms.Document.setValue("loading", "true");
                }, function() {

                    // Test that the loading indicator is visible
                    var loading = thiss.getLoadingIndicator(table);
                    thiss.checkVisibility(loading, true);

                    // Display the table again
                    ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                        ORBEON.xforms.Document.setValue("loading", "false");
                    }, function() {
                        // Test that the column of the first column has been preserved
                        YAHOO.util.Assert.areEqual(width, th.clientWidth, "The width of the first column should be " + width + ", not " + th.clientWidth);
                        thiss.checkCellClasses(table, true);
                        thiss.checkCellStyles(table, true);

                        thiss.closeAccordionCase(thiss, '_314379');
                    });
                });
            });
        });
    },

    EOS:""
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

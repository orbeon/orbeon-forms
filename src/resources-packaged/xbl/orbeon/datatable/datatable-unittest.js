/**
 * Copyright (C) 2009 Orbeon, Inc.
 *
 * lib program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * lib program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */


var testCase = {

    name: "datatable",

    test314679: function() {
       var thiss = this;
       thiss.openAccordionCase(thiss, '_314679', function() {

           var table = YAHOO.util.Dom.get('my-accordion$table-314679$table-314679-table');
           thiss.clickAndCheckSortOrder(table, 1, 'descending', ['three', 'two', 'one'], function() {

                thiss.closeAccordionCase(thiss, '_314679');

            });

       });
   },
    

    testOptionalScrollhV: function() {
         var thiss = this;
         thiss.openAccordionCase(thiss, 'optional-scrollh-v', function() {
             var tbody = YAHOO.util.Dom.get('my-accordion$optional-scrollh-v-table$optional-scrollh-v-table-tbody');
             var bodyContainer = tbody.parentNode.parentNode;
             thiss.checkHorizontalScrollbar(bodyContainer, false);
             thiss.closeAccordionCase(thiss, 'optional-scrollh-v');
         });
     }
     ,

    testOptionalScrollh: function() {
         var thiss = this;
         thiss.openAccordionCase(thiss, 'optional-scrollh', function() {
             var tbody = YAHOO.util.Dom.get('my-accordion$optional-scrollh-table$optional-scrollh-table-tbody');
             var bodyContainer = tbody.parentNode.parentNode;
             thiss.checkHorizontalScrollbar(bodyContainer, false);
             thiss.closeAccordionCase(thiss, 'optional-scrollh');
         });
     }
     ,

     test314466: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, '_314466', function() {

            var table = YAHOO.util.Dom.get('my-accordion$_314466-table$_314466-table-table');
            YAHOO.util.Assert.isFalse(YAHOO.util.Dom.hasClass(table, 'fr-dt-initialized'), "The datatable sshouldn't be initialized at that point");
            ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                YAHOO.util.UserAction.click(YAHOO.util.Dom.get('my-accordion$show-314466'), {clientX: 1});
            }, function() {
                thiss.wait(function() {
                    table = YAHOO.util.Dom.get('my-accordion$_314466-table$_314466-table-table');
                    YAHOO.util.Assert.isTrue(YAHOO.util.Dom.hasClass(table, 'fr-dt-initialized'),  "The datatable sshould be initialized at that point");

                    YAHOO.util.UserAction.click(YAHOO.util.Dom.get('my-accordion$hide-314466'), {clientX: 1});
                    thiss.closeAccordionCase(thiss, '_314466')
                }, 200);

            });

        });
    },


    // Simple hide/show cycle
    test314459: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, '_314459', function() {

            ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                ORBEON.xforms.Document.setValue("maxLength", "30");
                ORBEON.xforms.Document.setValue("show", "true");
            }, function() {
                // Check the table structure
                var table = YAHOO.util.Dom.get('my-accordion$_314459-table$_314459-table-table');
                thiss.checkTableStructure(table, 1, false);
                var container = YAHOO.util.Dom.get('my-accordion$_314459-table$_314459-table-container');
                thiss.checkPaginationLinks(container, ['-<< first', '-< prev', '-1', '+2', '+next >', '+last >>']);

                ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                    ORBEON.xforms.Document.setValue("show", "false");
                }, function() {
                    table = YAHOO.util.Dom.get('my-accordion$_314459-table$_314459-table-table');
                    thiss.checkTableStructure(table, 1, false);
                    container = YAHOO.util.Dom.get('my-accordion$_314459-table$_314459-table-container');
                    thiss.checkPaginationLinks(container, []);
                    ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                        ORBEON.xforms.Document.setValue("show", "true");
                    }, function() {
                        // Check the table structure
                        table = YAHOO.util.Dom.get('my-accordion$_314459-table$_314459-table-table');
                        thiss.checkTableStructure(table, 1, false);
                        container = YAHOO.util.Dom.get('my-accordion$_314459-table$_314459-table-container');
                        thiss.checkPaginationLinks(container, ['-<< first', '-< prev', '-1', '+2', '+next >', '+last >>']);

                        thiss.closeAccordionCase(thiss, '_314459');
                    });
                });

            });

        });
    },

    test314415: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, '_314415', function() {

            ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                ORBEON.xforms.Document.setValue("show", "true");
            }, function() {
                // Check the table structure
                var table = YAHOO.util.Dom.get('my-accordion$_314415-table$_314415-table-table·1');
                thiss.checkTableStructure(table, 1, true);

                ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                    ORBEON.xforms.Document.setValue("show", "false");
                }, function() {
                    table = YAHOO.util.Dom.get('my-accordion$_314415-table$_314415-table-table·1');
                    YAHOO.util.Assert.isNull(table, "The table should have been deleted");
                    ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                        ORBEON.xforms.Document.setValue("show", "true");
                    }, function() {
                        // Check the table structure
                        table = YAHOO.util.Dom.get('my-accordion$_314415-table$_314415-table-table·1');
                        thiss.checkTableStructure(table, 1, true);

                        thiss.closeAccordionCase(thiss, '_314415');
                    });
                });

            });

        }
                )
                ;
    },


    test314422: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, '_314422', function() {

            ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                ORBEON.xforms.Document.setValue("maxLength", "1000");
            }, function() {
                // Check the table structure
                var table = YAHOO.util.Dom.get('my-accordion$_314422-table$_314422-table-table');
                thiss.checkColumnValues(table, 1, false, [0, 1, 2, 3, 4]);
                var container = YAHOO.util.Dom.get('my-accordion$_314422-table$_314422-table-container');
                thiss.checkPaginationLinks(container, ['-<< first', '-< prev', '-1', '+2', '+3', '+4', '+5', '+6', '+7', '+8', '+next >', '+last >>']);

                var link6 = thiss.getPaginateLink(container, 'last >>');
                ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                    YAHOO.util.UserAction.click(link6, {clientX: 1});
                }, function() {
                    thiss.checkColumnValues(table, 1, false, [35, 36, 37]);
                    thiss.checkPaginationLinks(container, ['+<< first', '+< prev', '+1', '+2', '+3', '+4', '+5', '+6', '+7', '-8', '-next >', '-last >>']);
                    ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                        ORBEON.xforms.Document.setValue("maxLength", "30");
                    }, function() {
                        // Test the status after clicking on "last"
                        thiss.checkColumnValues(table, 1, false, [35, 37]);
                        thiss.checkPaginationLinks(container, ['+<< first', '+< prev', '+1', '-2', '-next >', '-last >>']);

                        thiss.closeAccordionCase(thiss, '_314422');
                    });
                });
            });

        });
    }
    ,


    test314359: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, '_314359', function() {
            // Check the table structure
            var table = YAHOO.util.Dom.get('my-accordion$table-314359$table-314359-table');
            thiss.checkIsSplit(table, true);
            thiss.checkTableStructure(table, 2, true);
            thiss.checkCellClasses(table, true);
            thiss.checkCellStyles(table, true);

            thiss.closeAccordionCase(thiss, '_314359');

        });

    }
    ,

    test314217: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, '_314217', function() {
            var tbody = YAHOO.util.Dom.get('my-accordion$table-314217$table-314217-tbody');
            var bodyContainer = tbody.parentNode.parentNode;
            thiss.checkHorizontalScrollbar(bodyContainer);
            thiss.closeAccordionCase(thiss, '_314217');
        });
    }
    ,

    testWidths: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, 'widths', function() {
            var table = YAHOO.util.Dom.get('my-accordion$table-widths$table-widths-table');
            thiss.checkRowWidth(table.tHead.rows[0]);
            thiss.checkTableAndContainerWidths(YAHOO.util.Dom.get('my-accordion$table-widths$table-widths-table'));
            thiss.closeAccordionCase(thiss, 'widths');
        });
    }
    ,

    testWidthsResizeable: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, 'widths-resizeable', function() {
            var table = YAHOO.util.Dom.get('my-accordion$table-widths-resizeable$table-widths-resizeable-table');
            thiss.checkRowWidth(table.tHead.rows[0]);
            thiss.checkTableAndContainerWidths(table);
            thiss.closeAccordionCase(thiss, 'widths-resizeable');
        });
    }
    ,

    testWidthsResizeable100pxRight: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, 'widths-resizeable', function() {
            var table = YAHOO.util.Dom.get('my-accordion$table-widths-resizeable$table-widths-resizeable-table');
            var th1 = table.tHead.rows[0].cells[0];
            var th2 = table.tHead.rows[0].cells[1];
            var width1 = th1.clientWidth;
            var width2 = th2.clientWidth;
            thiss.resizeColumn(th2, 100, 10);
            thiss.checkTableAndContainerWidths(table);
            YAHOO.util.Assert.areEqual(width1, th1.clientWidth, "The width of the first column shouldn't change (before: " + width1 + ", after: " + width2 + ").");
            YAHOO.util.Assert.areEqual(width2 + 100, th2.clientWidth, "The width of the second column should be " + (width2 + 100) + ", not " + th2.clientWidth);
            thiss.checkRowWidth(table.tHead.rows[0]);
            thiss.closeAccordionCase(thiss, 'widths-resizeable');
        });
    }
    ,

    testWidthsResizeable100pxLeft: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, 'widths-resizeable', function() {
            var table = YAHOO.util.Dom.get('my-accordion$table-widths-resizeable$table-widths-resizeable-table');
            var th1 = table.tHead.rows[0].cells[0];
            var th2 = table.tHead.rows[0].cells[1];
            var width1 = th1.clientWidth;
            var width2 = th2.clientWidth;
            thiss.resizeColumn(th2, -100, 10);
            thiss.checkTableAndContainerWidths(table);
            YAHOO.util.Assert.areEqual(width1, th1.clientWidth, "The wdith of the first column shouldn't change (before: " + width1 + ", after: " + width2 + ").");
            YAHOO.util.Assert.areEqual(width2 - 100, th2.clientWidth, "The width of the second column should be " + (width2 - 100) + ", not " + th2.clientWidth);
            thiss.checkRowWidth(table.tHead.rows[0]);
            thiss.closeAccordionCase(thiss, 'widths-resizeable');
        });
    }
    ,

    testWidthsResizeable10MorePxLeft: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, 'widths-resizeable', function() {
            var table = YAHOO.util.Dom.get('my-accordion$table-widths-resizeable$table-widths-resizeable-table');
            var th1 = table.tHead.rows[0].cells[0];
            var th2 = table.tHead.rows[0].cells[1];
            var width1 = th1.clientWidth;
            var width2 = th2.clientWidth;
            thiss.resizeColumn(th2, -10);
            thiss.checkTableAndContainerWidths(table);
            thiss.checkRowWidth(table.tHead.rows[0]);
            YAHOO.util.Assert.areEqual(width1, th1.clientWidth, "The wdith of the first column shouldn't change (before: " + width1 + ", after: " + width2 + ").");
            YAHOO.util.Assert.areEqual(width2 - 10, th2.clientWidth, "The width of the second column should be " + (width2 - 10) + ", not " + th2.clientWidth);
            thiss.closeAccordionCase(thiss, 'widths-resizeable');
        });
    }
    ,

    test314216: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, '_314216', function() {
            var th = YAHOO.util.Dom.get('my-accordion$table-314216$th-314216-2');
            var resizerliner = ORBEON.widgets.datatable.utils.getFirstChildByTagAndClassName(th, 'div', 'yui-dt-resizerliner');
            var liner = ORBEON.widgets.datatable.utils.getFirstChildByTagAndClassName(resizerliner, 'div', 'yui-dt-liner');
            var resizer = ORBEON.widgets.datatable.utils.getFirstChildByTagAndClassName(resizerliner, 'div', 'yui-dt-resizer');
            thiss.resizeColumn(th, -100, 5);
            YAHOO.util.Assert.isTrue(th.clientWidth > 0, 'The column width should be greater than 0, not ' + th.clientWidth);
            thiss.checkTableAndContainerWidths(YAHOO.util.Dom.get('my-accordion$table-314216$table-314216-table'));
            thiss.checkCellWidth(th);
            thiss.closeAccordionCase(thiss, '_314216');
        });
    }
    ,

    test314209: function() {
        var thiss = this;
        thiss.closeAccordionCase(thiss, '_314209', function() {
            thiss.openAccordionCase(thiss, '_314209', function() {
                var table = YAHOO.util.Dom.get('my-accordion$table-314209$table-314209-table');
                var visibility = YAHOO.util.Dom.getStyle(table, 'visibility');
                YAHOO.util.Assert.isTrue(visibility == 'visible' || visibility == 'inherit', 'Visibility should be visible or inherit, not ' + visibility);
                // unfortunately, I haven't found any way to check that the table is actually visible!
                thiss.closeAccordionCase(thiss, '_314209');
            });

        });
    }
    ,

    test314211: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, '_314211', function() {
            var table = YAHOO.util.Dom.get('my-accordion$table-314211$table-314211-table');
            YAHOO.util.Assert.isTrue(table.clientWidth < 300, 'The table width (' + table.clientWidth + "px) should be small, let's say < 300px...");
            thiss.closeAccordionCase(thiss, '_314211');

        });
    }
    ,

    test314174: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, '_314174');
        //TODO: test something here!
        thiss.closeAccordionCase(thiss, '_314174');
    }
    ,

    test314210: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, '_314210', function() {
            var headerTable = YAHOO.util.Dom.get('my-accordion$table-314210$table-314210-table');
            YAHOO.util.Assert.isTrue(headerTable.clientWidth > headerTable.parentNode.clientWidth, 'The table header width (' + headerTable.clientWidth + 'px) should be larger than its container width (' + headerTable.parentNode.clientWidth + 'px).');
            thiss.closeAccordionCase(thiss, '_314210');
        });
    }
    ,

    test314292: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, '_314292', function() {
            window.resizeBy(-50, 0);
            thiss.wait(function() {
                var table = YAHOO.util.Dom.get('my-accordion$table-314292$table-314292-table');
                // The following test seems to detect the root cause of this bug
                thiss.checkEmbeddedWidthAndHeight(table.parentNode.parentNode);
                thiss.checkTableAndContainerWidths(table);
                var tableX = YAHOO.util.Dom.getX(table);
                var containerX = YAHOO.util.Dom.getX(table.parentNode.parentNode);
                // The next one actually checks that the table does not overlap the border of the main container
                // but is isn't 100% reliable
                // YAHOO.util.Assert.areEqual(containerX, tableX - 1, 'The table left (' + tableX + ") should be 1 px right to the container left (" + containerX + ')');
                window.resizeBy(50, 0);
                thiss.closeAccordionCase(thiss, '_314292');
            }, 500);

        });
    }
    ,

    EOS: ""
}


ORBEON.xforms.Events.orbeonLoadedEvent.subscribe(function() {
    for (var property in ORBEON.widgets.datatable.unittests_lib) {
        testCase[property] = ORBEON.widgets.datatable.unittests_lib[property];
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

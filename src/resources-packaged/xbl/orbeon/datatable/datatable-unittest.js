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


YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

    name: "datatable",

    lib: ORBEON.widgets.datatable.unittests_lib,

    test314217: function() {
        var thiss = this;
        ORBEON.widgets.datatable.unittests_lib.openAccordionCase(thiss, '_314217', function() {
            var tbody = YAHOO.util.Dom.get('my-accordion$table-314217$table-314217-tbody');
            var bodyContainer = tbody.parentNode.parentNode;
            ORBEON.widgets.datatable.unittests_lib.checkHorizontalScrollbar(bodyContainer);
            ORBEON.widgets.datatable.unittests_lib.closeAccordionCase(thiss, '_314217');
        });
    },

    testWidths: function() {
        var thiss = this;
        ORBEON.widgets.datatable.unittests_lib.openAccordionCase(thiss, 'widths', function() {
            var table = YAHOO.util.Dom.get('my-accordion$table-widths$table-widths-table');
            ORBEON.widgets.datatable.unittests_lib.checkRowWidth(table.tHead.rows[0]);
            ORBEON.widgets.datatable.unittests_lib.checkTableAndContainerWidths(YAHOO.util.Dom.get('my-accordion$table-widths$table-widths-table'));
            ORBEON.widgets.datatable.unittests_lib.closeAccordionCase(thiss, 'widths');
        });
    },

    testWidthsResizeable: function() {
        var thiss = this;
        ORBEON.widgets.datatable.unittests_lib.openAccordionCase(thiss, 'widths-resizeable', function() {
            var table = YAHOO.util.Dom.get('my-accordion$table-widths-resizeable$table-widths-resizeable-table');
            ORBEON.widgets.datatable.unittests_lib.checkRowWidth(table.tHead.rows[0]);
            ORBEON.widgets.datatable.unittests_lib.checkTableAndContainerWidths(table);
            ORBEON.widgets.datatable.unittests_lib.closeAccordionCase(thiss, 'widths-resizeable');
        });
    },

    testWidthsResizeable100pxRight: function() {
        var thiss = this;
        ORBEON.widgets.datatable.unittests_lib.openAccordionCase(thiss, 'widths-resizeable', function() {
            var table = YAHOO.util.Dom.get('my-accordion$table-widths-resizeable$table-widths-resizeable-table');
            var th1 = table.tHead.rows[0].cells[0];
            var th2 = table.tHead.rows[0].cells[1];
            var width1 = th1.clientWidth;
            var width2 = th2.clientWidth;
            ORBEON.widgets.datatable.unittests_lib.resizeColumn(th2, 100, 10);
            ORBEON.widgets.datatable.unittests_lib.checkTableAndContainerWidths(table);
            YAHOO.util.Assert.areEqual(width1, th1.clientWidth, "The width of the first column shouldn't change (before: " + width1 + ", after: " + width2 + ").");
            YAHOO.util.Assert.areEqual(width2 + 100, th2.clientWidth, "The width of the second column should be " + (width2 + 100) + ", not " + th2.clientWidth);
            ORBEON.widgets.datatable.unittests_lib.checkRowWidth(table.tHead.rows[0]);
            ORBEON.widgets.datatable.unittests_lib.closeAccordionCase(thiss, 'widths-resizeable');
        });
    },

    testWidthsResizeable100pxLeft: function() {
        var thiss = this;
        ORBEON.widgets.datatable.unittests_lib.openAccordionCase(thiss, 'widths-resizeable', function() {
            var table = YAHOO.util.Dom.get('my-accordion$table-widths-resizeable$table-widths-resizeable-table');
            var th1 = table.tHead.rows[0].cells[0];
            var th2 = table.tHead.rows[0].cells[1];
            var width1 = th1.clientWidth;
            var width2 = th2.clientWidth;
            ORBEON.widgets.datatable.unittests_lib.resizeColumn(th2, -100, 10);
            ORBEON.widgets.datatable.unittests_lib.checkTableAndContainerWidths(table);
            YAHOO.util.Assert.areEqual(width1, th1.clientWidth, "The wdith of the first column shouldn't change (before: " + width1 + ", after: " + width2 + ").");
            YAHOO.util.Assert.areEqual(width2 - 100, th2.clientWidth, "The width of the second column should be " + (width2 - 100) + ", not " + th2.clientWidth);
            ORBEON.widgets.datatable.unittests_lib.checkRowWidth(table.tHead.rows[0]);
            ORBEON.widgets.datatable.unittests_lib.closeAccordionCase(thiss, 'widths-resizeable');
        });
    },

    testWidthsResizeable10MorePxLeft: function() {
        var thiss = this;
        ORBEON.widgets.datatable.unittests_lib.openAccordionCase(thiss, 'widths-resizeable', function() {
            var table = YAHOO.util.Dom.get('my-accordion$table-widths-resizeable$table-widths-resizeable-table');
            var th1 = table.tHead.rows[0].cells[0];
            var th2 = table.tHead.rows[0].cells[1];
            var width1 = th1.clientWidth;
            var width2 = th2.clientWidth;
            ORBEON.widgets.datatable.unittests_lib.resizeColumn(th2, -10);
            ORBEON.widgets.datatable.unittests_lib.checkTableAndContainerWidths(table);
            ORBEON.widgets.datatable.unittests_lib.checkRowWidth(table.tHead.rows[0]);
            YAHOO.util.Assert.areEqual(width1, th1.clientWidth, "The wdith of the first column shouldn't change (before: " + width1 + ", after: " + width2 + ").");
            YAHOO.util.Assert.areEqual(width2 - 10, th2.clientWidth, "The width of the second column should be " + (width2 - 10) + ", not " + th2.clientWidth);
            ORBEON.widgets.datatable.unittests_lib.closeAccordionCase(thiss, 'widths-resizeable');
        });
    },

    test314216: function() {
        var thiss = this;
        ORBEON.widgets.datatable.unittests_lib.openAccordionCase(thiss, '_314216', function() {
            var th = YAHOO.util.Dom.get('my-accordion$table-314216$th-314216-2');
            var resizerliner = ORBEON.widgets.datatable.utils.getFirstChildByTagAndClassName(th, 'div', 'yui-dt-resizerliner');
            var liner = ORBEON.widgets.datatable.utils.getFirstChildByTagAndClassName(resizerliner, 'div', 'yui-dt-liner');
            var resizer = ORBEON.widgets.datatable.utils.getFirstChildByTagAndClassName(resizerliner, 'div', 'yui-dt-resizer');
            ORBEON.widgets.datatable.unittests_lib.resizeColumn(th, -100, 5);
            YAHOO.util.Assert.isTrue(th.clientWidth > 0, 'The column width should be greater than 0, not ' + th.clientWidth);
            ORBEON.widgets.datatable.unittests_lib.checkTableAndContainerWidths(YAHOO.util.Dom.get('my-accordion$table-314216$table-314216-table'));
            ORBEON.widgets.datatable.unittests_lib.checkCellWidth(th);
            ORBEON.widgets.datatable.unittests_lib.closeAccordionCase(thiss, '_314216');
        });
    },

    test314209: function() {
        var thiss = this;
        ORBEON.widgets.datatable.unittests_lib.closeAccordionCase(thiss, '_314209', function() {
            ORBEON.widgets.datatable.unittests_lib.openAccordionCase(thiss, '_314209', function() {
                var table = YAHOO.util.Dom.get('my-accordion$table-314209$table-314209-table');
                var visibility = YAHOO.util.Dom.getStyle(table, 'visibility');
                YAHOO.util.Assert.isTrue(visibility == 'visible' || visibility == 'inherit', 'Visibility should be visible or inherit, not ' + visibility);
                // unfortunately, I haven't found any way to check that the table is actually visible!
                ORBEON.widgets.datatable.unittests_lib.closeAccordionCase(thiss, '_314209');
            });

        });
    },

    test314211: function() {
        var thiss = this;
        ORBEON.widgets.datatable.unittests_lib.openAccordionCase(thiss, '_314211', function() {
            var table = YAHOO.util.Dom.get('my-accordion$table-314211$table-314211-table');
            YAHOO.util.Assert.isTrue(table.clientWidth < 300, 'The table width (' + table.clientWidth + "px) should be small, let's say < 300px...");
            ORBEON.widgets.datatable.unittests_lib.closeAccordionCase(thiss, '_314211');

        });
    },

    test314174: function() {
        var thiss = this;
        ORBEON.widgets.datatable.unittests_lib.openAccordionCase(thiss, '_314174');
        //TODO: test something here!
        ORBEON.widgets.datatable.unittests_lib.closeAccordionCase(thiss, '_314174');
    },

    test314210: function() {
        var thiss = this;
        ORBEON.widgets.datatable.unittests_lib.openAccordionCase(thiss, '_314210', function() {
            var headerTable = YAHOO.util.Dom.get('my-accordion$table-314210$table-314210-table');
            YAHOO.util.Assert.isTrue(headerTable.clientWidth > headerTable.parentNode.clientWidth, 'The table header width (' + headerTable.clientWidth + 'px) should be larger than its container width (' + headerTable.parentNode.clientWidth + 'px).');
            ORBEON.widgets.datatable.unittests_lib.closeAccordionCase(thiss, '_314210');
        });
    },

    test314292: function() {
        var thiss = this;
        ORBEON.widgets.datatable.unittests_lib.openAccordionCase(thiss, '_314292', function() {
            window.resizeBy(-50, 0);
            thiss.wait(function() {
                var table = YAHOO.util.Dom.get('my-accordion$table-314292$table-314292-table');
                // The following test seems to detect the root cause of this bug
                ORBEON.widgets.datatable.unittests_lib.checkEmbeddedWidthAndHeight(table.parentNode.parentNode);
                ORBEON.widgets.datatable.unittests_lib.checkTableAndContainerWidths(table);
                var tableX = YAHOO.util.Dom.getX(table);
                var containerX = YAHOO.util.Dom.getX(table.parentNode.parentNode);
                // The next one actually checks that the table does not overlap the border of the main container
                // but is isn't 100% reliable
                // YAHOO.util.Assert.areEqual(containerX, tableX - 1, 'The table left (' + tableX + ") should be 1 px right to the container left (" + containerX + ')');
                window.resizeBy(50, 0);
                ORBEON.widgets.datatable.unittests_lib.closeAccordionCase(thiss, '_314292');
            }, 500);

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

/**
 *  Copyright (C) 2009-2010 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
/**
 * JavaScript implementation for the datatable component.
 *
 * During a typical initialization, the following steps run:
 *
 *      1.  updateColumns() and updateRows() - don't do anything at this point as the datatable hasn't been initialized yet.
 *      2.  draw() is called (xforms-enabled).
 *      3.      initProperties()
 *      4.          getAndSetColumns()
 *      5.      finish()
 *      6.          setSizes()
 *      7.          initColumns()
 */

YAHOO.namespace("xbl.fr");
YAHOO.xbl.fr.Datatable = function() {};
ORBEON.xforms.XBL.declareClass(YAHOO.xbl.fr.Datatable, "xbl-fr-datatable");

/**
 * Used to manage the datatables that are located in a page
 */
YAHOO.xbl.fr.DatatableManager = {

    waitingToResize: false,                                      //  Are we waiting that the page width is stabilized to resize the datatables?
    previousBodyWidth: YAHOO.util.Dom.getViewportWidth(),        //  Previous value of the page width
    bodyWidthWhenResized: YAHOO.util.Dom.getViewportWidth(),     //  Value of the page width when the datatables have been last resized

    /**
     * Event handler for window resizing events
     */
    resize: function () {
        if (YAHOO.xbl.fr.DatatableManager.waitingToResize) {
            return;
        }
        YAHOO.xbl.fr.DatatableManager.waitingToResize = true;
        setTimeout("YAHOO.xbl.fr.DatatableManager.resizeWhenStabilized()", 200);
    },

    /**
     * Test if the windows size is stabilized (needed for IE)
     * and resize all the datatables that are in the page
     */
    resizeWhenStabilized: function () {
        var width = YAHOO.util.Dom.getViewportWidth();

        if (YAHOO.xbl.fr.DatatableManager.previousBodyWidth != width) {
            // The window width is still changing, wait till it get stabilized
            YAHOO.xbl.fr.DatatableManager.previousBodyWidth = width;
            setTimeout("YAHOO.xbl.fr.DatatableManager.resizeWhenStabilized()", 200);
            return;
        }

        // The window size seems to be stable, resize the datatables

        YAHOO.xbl.fr.DatatableManager.waitingToResize = false;

        if (YAHOO.xbl.fr.DatatableManager.bodyWidthWhenResized == width) {
            // The datatables have already been resized for that window width, nothing to do...
            return;
        }

        // To restore the original look and feel, we need to undo the changes done to all the datatables first
        // and then to reinit them.

        YAHOO.xbl.fr.DatatableManager.bodyWidthWhenResized = width;

        // Reset
        for (var id in YAHOO.xbl.fr.Datatable._instances) {
            var datatable = YAHOO.xbl.fr.Datatable._instances[id];
            if (datatable.isDisplayed())
                datatable.reset();
        }
        // And draw again
        for (var id in YAHOO.xbl.fr.Datatable._instances) {
            var datatable = YAHOO.xbl.fr.Datatable._instances[id];
            if (datatable.isDisplayed()) {
                datatable.isInitialized = false;
                datatable.draw();
            }
        }
    },

    EOO: ''                                                     // End Of Object!
};

// Set the event listener for window resize events
YAHOO.util.Event.addListener(window, "resize", YAHOO.xbl.fr.DatatableManager.resize);

/*
 * Datatable class
 */
YAHOO.xbl.fr.Datatable.prototype = {

    container: null,                                            // Set by xforms.js
    isInitialized: false,                                       // Has the datatable been initialized?
    lastRequestUUID: 'init',                                    // UUID of the dynamic state store for future use
    columnsUpdateUUID: null,                                    // UUID of the dynamic state when the columns have been updated
    rowsUpdateUUID: null,                                       // UUID of the dynamic state when the rows have been updated
    divContainer: null,                                         // Main div container around the datatable
    innerTableWidth: null,                                      // Inner table width, parameter from the XSL/XBL layer
    scrollH:null,                                               // Is the table horizontally scrollable?
    scrollV:null,                                               // Is the table vertically scrollable?
    scroll:null,                                                // Is the table scrollable?
    headBodySplit: null,                                        // Is the table split?
    headerTable: null,                                          // Table used for the header
    table: null,                                                // Table (table used by the body if split)
    thead: null,                                                // Table head
    tbody: null,                                                // Table body
    id:null,                                                    // Table's id
    originalWidth: null,                                        // Original width style value
    originalHeight: null,                                       // Original height style value
    height: null,                                               // Height
    hasFixedWidthTable: null,                                   // Has the table a fixed width?
    hasFixedWidthContainer: null,                               // Has the container a fixed width?
    adjustHeightForIE:null,                                     // Do we need to adjust the height for IE
    headerContainer: null,                                      // Table header div container
    headerScrollContainer: null,                                // Table header scroll container
    bodyContainer: null,                                        // Body container
    significantNodes: [],                                       // List of significant nodes which style needs to be saved
    width: null,
    tableWidth: null,
    tableHeight: null,
    headerHeight: null,
    colResizers: [],
    colSorters: [],
    masterRow: null,                                            // Header "master" row
    headerColumns: [],
    delayedDraw: null,                                          // A version of draw with all the arguments bound

    init: function() {
        /** @type {HTMLElement} */  var innerTableWidthSpan = YAHOO.util.Dom.getElementsByClassName("fr-dt-inner-table-width", null, this.container)[0];
        /** @type {String} */       var innerTableWidthText = ORBEON.util.Dom.getStringValue(innerTableWidthSpan);
        this.innerTableWidth = innerTableWidthText == "" ? null : parseInt(innerTableWidthText);
        this.draw();
    },

    /**
     * Initialize a datatable.
     * Runs on xforms-enabled.
     *
     * @param innerTableWidth
     */
    draw: function() {

        // We don't care about datatable that are disabled (we'll receive a new event when they'll be enabled again)
        // TODO: check if this test is still needed
        if (this.isXformsEnabled()) {
            if (!this.isInitialized) {
                this.whenDisplayed(_.bind(function() {
                    this.initProperties();
                    this.finish();
                    this.isInitialized = true;
                    this.container.fr_dt_initialized = true;
                    this.columnsUpdateUUID = this.getRequestUUID();
                }, this));
            } else {
                this.updateColumns();
            }
        }
        // if not xforms-enabled, we'll receive a new call when we'll become xforms-enabled
    },

    /**
     *  Get the request sequence number
     */
    getRequestUUID: function() {
        var form = ORBEON.xforms.Controls.getForm(this.container);
        return ORBEON.xforms.Document.getFromClientState(form.id, "sequence");
    },

    /**
     * Is the component XForms enabled
     */
    isXformsEnabled: function() {
        var xformsGroup = YAHOO.xbl.fr.Datatable.utils.getFirstChildByTagName(this.container, 'span');
        return ! (YAHOO.util.Dom.hasClass(xformsGroup, 'xforms-disabled') || YAHOO.util.Dom.hasClass(xformsGroup, 'xforms-disabled-subsequent'));
    },

    /**
     * Initialize a bunch of datatable's properties
     */
    initProperties: function () {
        this.divContainer = YAHOO.util.Dom.getElementsByClassName('yui-dt', 'div', this.container)[0];

        YAHOO.util.Dom.addClass(this.divContainer, 'fr-dt-initialized');

        this.scrollV = YAHOO.util.Dom.hasClass(this.divContainer, 'fr-scrollV');
        this.scrollH = YAHOO.util.Dom.hasClass(this.divContainer, 'fr-scrollH');
        this.scroll = this.scrollV || this.scrollH;
        this.headBodySplit = this.scroll;

        var tables = this.divContainer.getElementsByTagName("table");
        this.headerTable = tables[0];
        this.headerTable.style.height = "";

        if (this.headBodySplit) {
            this.table = tables[1];
        } else {
            this.table = tables[0];
        }

        this.thead = ORBEON.util.Dom.getElementByTagName(this.headerTable, 'thead');
        this.tbody = ORBEON.util.Dom.getElementByTagName(this.table, 'tbody');
        this.getAndSetColumns();
        this.id = this.table.getAttribute('id');
        this.originalWidth = YAHOO.xbl.fr.Datatable.utils.getStyle(this.table.parentNode, 'width', 'auto');
        this.originalHeight = YAHOO.xbl.fr.Datatable.utils.getStyle(this.table, 'height', 'auto');
        this.height = YAHOO.xbl.fr.Datatable.utils.getStyle(this.table, 'height', 'auto');
        this.hasFixedWidthContainer = this.originalWidth != 'auto';
        this.hasFixedWidthTable = this.hasFixedWidthContainer && ! this.scrollH;
        this.adjustHeightForIE = false;
        if (this.scrollV) {
            this.headerScrollContainer = this.headerTable.parentNode;
            this.headerContainer = this.headerScrollContainer.parentNode;
        } else {
            this.headerContainer = this.headerTable.parentNode;
        }
        this.bodyContainer = this.table.parentNode;

        // Save original styles

        this.significantNodes = [this.divContainer, this.bodyContainer, this.table, this.thead, this.tbody, this.table.tHead.rows[0]];
        if (this.headBodySplit) {
            this.significantNodes.push(this.headerTable);
            this.significantNodes.push(this.headerContainer);
            if (this.scrollV) {
                this.significantNodes.push(this.headerScrollContainer);
            }
            for (var iRow = 0; iRow < this.table.tHead.rows.length; iRow++) {
                this.significantNodes.push(this.table.tHead.rows[iRow]);
            }
        }

        for (var i = 0; i < this.significantNodes.length; i++) {
            var node = this.significantNodes[i];
            node.savedWidth = node.style.width;
            node.savedHeight = node.style.height;
            node.savedClassName = node.className;
        }
    },

    /**
     * Finish the datatable initialization
     */
    finish: function () {
        this.setSizes();

        if (this.scrollH) {
            YAHOO.util.Event.addListener(this.bodyContainer, 'scroll', YAHOO.xbl.fr.Datatable.prototype.scrollHandler, this, true);
            this.width = this.divContainer.clientWidth;
            if (this.tableWidth > this.width) {
                this.bodyContainer.style.overflowX = "scroll";
            }
        } else {
            this.width = this.tableWidth;
        }

        this.initColumns();

        // Set size of body with remaining space
        // We do this after initColumns(), as changing the size of the columns can change the amount of space taken by
        // the header, and hence the space remaining for the body.
        this.headerHeight = this.headerContainer.clientHeight;
        if (this.height != 'auto') {
            var newBodyHeight = this.divContainer.clientHeight - this.headerHeight;
            YAHOO.util.Dom.setStyle(this.bodyContainer, 'height', newBodyHeight + 'px');
        }

        // Now that the table has been properly sized, reconsider its "resizability"

        if (this.hasFixedWidthContainer && this.hasFixedWidthTable) {
            // These are fixed width tables without horizontal scroll bars
            // and as we don't know how to resize their columns properly,
            // we'd better consider that as variable width
            this.hasFixedWidthContainer = false;
            this.hasFixedWidthTable = false;
        }

        // Sometimes, in IE / quirks mode, the height or width is miscalculated and that forces an horizontal scroll bar...

        if (YAHOO.env.ua.ie > 0 && (YAHOO.env.ua.ie < 8 || document.compatMode == "BackCompat")) {
            var limit;

            // Make sure we don't have a vertical bar if not required
            if (this.scrollH && ! this.scrollV) {
                limit = 1000;
                while (limit > 0 && this.table.parentNode.clientWidth < this.pxWidth - 2) {
                    this.tableHeight += 1;
                    this.height = this.tableHeight + "px";
                    this.bodyContainer.style.height = this.height;
                    limit -= 1;
                }
            }

            // Make sure we don't have an horizontal bar if not required
            if (this.scrollV && ! this.scrollH) {
                limit = 50;
                while (limit > 0 && this.table.clientWidth > this.table.parentNode.clientWidth) {
                    this.tableWidth -= 1;
                    var w = this.tableWidth + "px";
                    this.table.style.width = w;
                    this.headerTable.style.width = w;
                    limit -= 1;
                }
            }
        }
    },

    /**
     * Set the datable various sizes
     */
    setSizes: function () {

        var width = this.originalWidth;
        var pxWidth = this.getActualOriginalTableWidth();
        var containerWidth = this.divContainer.clientWidth;


        if (this.scrollH && width.indexOf('%') != - 1) {
            // This is needed to measure the container width when expressed as %
            // since in some cases browsers choose to increase the page width instead
            // of scrolling
            YAHOO.util.Dom.addClass(this.table, 'xforms-disabled');
            var dummy = document.createElement('div');
            dummy.innerHTML = "foo";
            this.bodyContainer.appendChild(dummy);

            containerWidth = this.divContainer.clientWidth;
            width = containerWidth + 'px';

            this.bodyContainer.removeChild(dummy);
            YAHOO.util.Dom.removeClass(this.table, 'xforms-disabled');
        } else {
            width = pxWidth + 'px';
        }


        this.tableWidth = this.table.clientWidth;

        this.tableHeight = this.table.clientHeight;

        // Do some magic adjustments
        if (this.scrollH) {
            if (pxWidth > this.tableWidth) {
                // Can be the case if table width was expressed as %
                this.tableWidth = pxWidth;
            }
            if (this.innerTableWidth != null) {
                this.tableWidth = this.table.clientWidth;
            } else {
                var minWidth;
                if (this.scrollV) {
                    minWidth = this.tableWidth - 19;
                } else {
                    if (YAHOO.env.ua.ie > 0 && YAHOO.env.ua.ie < 8) {
                        minWidth = this.tableWidth - 1;
                    } else {
                        minWidth = this.tableWidth;
                    }
                }
                this.tableWidth = this.optimizeWidth(minWidth);
            }
        } else if (this.scrollV) {
            if (this.hasFixedWidthTable && (this.originalWidth.indexOf('%') == - 1 || (YAHOO.env.ua.ie > 0 && YAHOO.env.ua.ie < 8) )) {
                width = this.tableWidth + 'px';
                this.tableWidth = this.tableWidth - 19;
            } else {
                width = (this.tableWidth + 19) + 'px';
            }
        } else {
            width = (this.tableWidth + 2) + 'px';
        }

        // At last, we know how the table will be sized, it's time to set these sizes

        YAHOO.util.Dom.setStyle(this.table, 'width', this.tableWidth + 'px');
        YAHOO.util.Dom.setStyle(this.headerTable, 'width', this.tableWidth + 'px');

        // In IE7, tHead elements have a clientWidth set to 0.
        var region = YAHOO.util.Dom.getRegion(this.table.tHead);
        // Hack needed for IE7 that doesn't want to measure the header height after the table is reset!
        if (region.bottom - region.top < 5) {
            if (this.headerHeight == null) {
                this.headerHeight = 10;
            }
        } else {
            this.headerHeight = region.bottom - region.top;
        }


        this.adjustHeightForIE = this.adjustHeightForIE || (this.scrollH && ! this.scrollV && this.height == 'auto' && YAHOO.env.ua.ie > 0 && YAHOO.env.ua.ie < 7);
        if (this.adjustHeightForIE) {
            this.height = (this.tableHeight + 22) + 'px';
            this.adjustHeightForIE = true;
        }

        // Resize the containers if not already done

        if (this.originalWidth == 'auto') {
            this.divContainer.style.width = width;
        } else  if (this.originalWidth.indexOf('%') != - 1) {
            this.divContainer.style.width = containerWidth + 'px';
        }

        this.columnWidths = [];
        var j = 0;

        // Here we can't use the master row  that is in the header table since its widths are not significant
        var masterRowCopyInBody = YAHOO.util.Dom.getElementsByClassName('fr-dt-master-row', 'tr', this.table)[0];
        for (var i = 0; i < masterRowCopyInBody.cells.length; i++) {
            var cell = masterRowCopyInBody.cells[i];
            if (YAHOO.xbl.fr.Datatable.utils.isSignificant(cell)) {
                this.columnWidths[j] = cell.clientWidth;
                j += 1;
            }
        }

        // Reset the yui-dt-bd class if we've removed it earlier on
        if (this.headBodySplit) {

            // Final set of size settings

            if (width.indexOf('%') != - 1 && YAHOO.env.ua.ie > 0 && (YAHOO.env.ua.ie < 8 || document.compatMode == "BackCompat")) {
                // Old versions of IE and quirks mode do not like at all widths expressed as % here

                this.headerContainer.style.width = (containerWidth - 2) + 'px';
                this.bodyContainer.style.width = (containerWidth - 2) + 'px';

            } else {
                YAHOO.util.Dom.setStyle(this.headerContainer, 'width', width);
                YAHOO.util.Dom.setStyle(this.bodyContainer, 'width', width);
            }

            if (this.scrollV) {
                this.headerScrollWidth = this.tableWidth + 20;
                this.headerScrollContainer.style.width = this.headerScrollWidth + 'px';
                this.bodyContainer.style.overflow = "auto";
                this.bodyContainer.style.overflowY = "scroll";
                this.headerTable.style.width = this.tableWidth + 'px';
                this.table.style.height = "auto";
            }

            for (var iRow = 0; iRow < this.table.tHead.rows.length; iRow++) {
                 YAHOO.util.Dom.addClass(this.table.tHead.rows[iRow], 'fr-datatable-hidden');
             }
             YAHOO.util.Dom.removeClass(this.headerContainer, 'fr-datatable-hidden');
        }

        this.pxWidth = pxWidth;

    },

    /**
     * Sets the layout of the table columns
     */
    initColumns: function () {

        this.colResizers = [];
        this.colSorters = [];
        for (var j = 0; j < this.headerColumns.length; j++) {
            var headerColumn = this.headerColumns[j];
            var childDiv = YAHOO.xbl.fr.Datatable.utils.getFirstChildByTagName(headerColumn, 'div');
            var liner = null;
            var colResizer = null;
            if (YAHOO.util.Dom.hasClass(this.headerColumns[j], 'yui-dt-resizeable')) {
                liner = YAHOO.xbl.fr.Datatable.utils.getFirstChildByTagName(childDiv, 'div');
                colResizer = new YAHOO.xbl.fr.Datatable.colResizer(j, this.headerColumns[j], this);
                this.colResizers[j] = colResizer;
            } else {
                liner = childDiv;
            }

            var width = (this.columnWidths[j] - 20) + 'px';
            var rule;
            // See _setColumnWidth in YUI datatable.js...
            if (YAHOO.env.ua.ie == 0) {
                var className = 'dt-' + this.id + '-col-' + (j + 1);
                className = className.replace(/\$/g, "-");
                YAHOO.util.Dom.addClass(liner, className);
                for (var k = 0; k < this.bodyColumns[j].length; k++) {
                    var cell = this.bodyColumns[j][k];
                    liner = ORBEON.util.Dom.getElementByTagName(cell, 'div');
                    YAHOO.util.Dom.addClass(liner, className);

                }
                if (! this.styleElt) {
                    this.styleElt = document.createElement('style');
                    this.styleElt.type = 'text/css';
                    document.getElementsByTagName('head').item(0).appendChild(this.styleElt);
                }
                if (this.styleElt) {
                    if (this.styleElt.styleSheet && this.styleElt.styleSheet.addRule) {
                        this.styleElt.styleSheet.addRule('.' + className, 'width:' + width);
                        rule = this.styleElt.styleSheet.rules[ this.styleElt.styleSheet.rules.length - 1];
                    } else if (this.styleElt.sheet && this.styleElt.sheet.insertRule) {
                        this.styleElt.sheet.insertRule('.' + className + ' {width:' + width + ';}', this.styleElt.sheet.cssRules.length);
                        rule = this.styleElt.sheet.cssRules[ this.styleElt.sheet.cssRules.length - 1];
                    }
                }
                if (rule && YAHOO.util.Dom.hasClass(this.headerColumns[j], 'yui-dt-resizeable')) {
                    colResizer.setRule(rule);
                }
            }
            if (! rule) {
                var style = liner.style;
                style.width = width;
                var styles = [style];
                for (var k = 0; k < this.bodyColumns[j].length; k++) {
                    var cell = this.bodyColumns[j][k];
                    var div = YAHOO.util.Selector.query('div', cell, true);
                    if (div != undefined) {
                        style = div.style;
                        style.width = width;
                        styles[styles.length] = style;
                    }
                }
                if (YAHOO.util.Dom.hasClass(this.headerColumns[j], 'yui-dt-resizeable')) {
                    colResizer.setStyleArray(styles);
                }
            }

            if (YAHOO.util.Dom.hasClass(this.headerColumns[j], 'yui-dt-sortable')) {
                this.colSorters[ this.colSorters.length] = new YAHOO.xbl.fr.Datatable.colSorter(this.headerColumns[j]);
            }

        }

        // The "real" init of the column resizers is done after the header dimensioning so that the dimensions are reliable
        for (var iResizer = 0; iResizer < this.colResizers.length; iResizer++) {
            var colResizer = this.colResizers[iResizer];
            if (! YAHOO.lang.isUndefined(colResizer)) colResizer.initResizer();
        }
    },

    /**
     * Get the actual table width
     */
    getActualOriginalTableWidth: function () {

        var pxWidth;

        if (this.originalWidth.indexOf('%') != - 1) {
            if (this.scrollV) {
                pxWidth = this.divContainer.clientWidth - 21;
            } else {
                pxWidth = this.divContainer.clientWidth;
            }
        } else if (this.originalWidth == 'auto') {
            pxWidth = this.table.clientWidth;
        } else {
            pxWidth = this.divContainer.clientWidth - 2;
        }
        return pxWidth;

    },

    /**
     * Reset the datatable as it was when we got it (undo all the changes we've done)
     */
    reset: function () {

        // Restore styles rules to the table

        for (var i = 0; i < this.significantNodes.length; i++) {
            var node = this.significantNodes[i];
            node.style.width = node.savedWidth;
            node.style.height = node.savedHeight;
            node.className = node.savedClassName;
        }

        // Restore column header widths
        for (var icol = 0; icol < this.headerColumns.length; icol++) {
            var th = this.headerColumns[icol];
            var resizerliner = YAHOO.xbl.fr.Datatable.utils.getFirstChildByTagAndClassName(th, 'div', 'yui-dt-resizerliner');
            if (resizerliner != null) {
                var liner = YAHOO.xbl.fr.Datatable.utils.getFirstChildByTagName(resizerliner, 'div');
                liner.style.width = "";
                liner.className = "yui-dt-liner";
            }
        }
        // Remove column width
        for (var icol = 0; icol < this.bodyColumns.length; icol++) {
            var col = this.bodyColumns[icol];
            for (var irow = 0; irow < col.length; irow++) {
                var cell = col[irow];
                var liner = YAHOO.xbl.fr.Datatable.utils.getFirstChildByTagAndClassName(cell, 'div', 'yui-dt-liner');
                if (liner != null) {
                    liner.style.width = "";
                    liner.className = "yui-dt-liner";
                }
            }
        }

        // remove the dynamic style sheet if it exists
        if (this.styleElt != undefined) {
            this.styleElt.parentNode.removeChild(this.styleElt);
            this.styleElt = undefined;
        }
    },

    /**
     * Get the table height for a given width
     * @param width
     */
    getTableHeightForWidth: function (width) {
        this.table.style.width = width + 'px';
        return this.table.clientHeight;
    },

    /**
     * Find the optimal width for the inner table
     * @param minWidth
     */
    optimizeWidth:function (minWidth) {
        this.bodyContainer.style.position = "absolute";
        this.bodyContainer.style.width = "2500px";
        var savedWidth = this.table.style.width;
        this.table.style.width = "auto";
        var width = this.table.clientWidth;
        this.tableHeight = this.table.clientHeight;
        this.bodyContainer.style.position = "";
        this.bodyContainer.style.width = "";
        this.table.style.width = savedWidth;
        if (minWidth > width) {
            return minWidth;
        }
        return width;
    },

    /**
     * Adjust the table width after a column has been resized
     * @param deltaX
     * @param index
     */
    adjustWidth: function (deltaX, index) {
        //alert('Before-> this.width: ' + this.width +', this.tableWidth: ' + this.tableWidth);
        if (! this.hasFixedWidthContainer) {
            this.width += deltaX;
            if (this.headBodySplit) {
                YAHOO.util.Dom.setStyle(this.divContainer, 'width', this.width + 'px');
                YAHOO.util.Dom.setStyle(this.headerContainer, 'width', this.width + 'px');
                YAHOO.util.Dom.setStyle(this.bodyContainer, 'width', this.width + 'px');
            } else {
                YAHOO.util.Dom.setStyle(this.divContainer, 'width', (this.width + 2) + 'px');
                //YAHOO.util.Dom.setStyle(this.headerContainer, 'width', this.width + 'px');
                YAHOO.util.Dom.setStyle(this.bodyContainer, 'width', this.width + 'px');

            }
        }
        if (! this.hasFixedWidthTable) {
            this.tableWidth += deltaX;
            YAHOO.util.Dom.setStyle(this.table, 'width', this.tableWidth + 'px');
            this.headerScrollWidth += deltaX;
            if (this.headBodySplit) {
                YAHOO.util.Dom.setStyle(this.headerScrollContainer, 'width', this.headerScrollWidth + 'px');
                YAHOO.util.Dom.setStyle(this.headerTable, 'width', this.tableWidth + 'px');
            }
        }
        //alert('After-> this.width: ' + this.width +', this.tableWidth: ' + this.tableWidth);
    },


    /**
     * Get the column lists
     */
    getAndSetColumns: function () {

        this.headerColumns = [];
        this.bodyColumns = [];
        this.masterRow = YAHOO.util.Dom.getElementsByClassName('fr-dt-master-row', 'tr', this.thead)[0];
        var headerCells = this.masterRow.cells;
        for (var icol = 0; icol < headerCells.length; icol++) {
            var cell = headerCells[icol];
            if (YAHOO.xbl.fr.Datatable.utils.isSignificant(cell)) {
                this.headerColumns.push(cell);
                this.bodyColumns.push([]);
            }
        }
        var bodyRows = YAHOO.util.Dom.getChildren(this.tbody);  //    bodyRows.length blows up IE 6/7 in some cases here :(
        for (var irow = 0; irow < bodyRows.length; irow++) {
            var row = bodyRows[irow];
            if (YAHOO.xbl.fr.Datatable.utils.isSignificant(row)) {
                var iActualCol = 0;
                var cells = YAHOO.util.Dom.getChildren(row);
                for (var icol = 0; icol < cells.length; icol++) {
                    var cell = cells[icol];
                    if (YAHOO.xbl.fr.Datatable.utils.isSignificant(cell)) {
                        this.bodyColumns[iActualCol].push(cell);
                        iActualCol += 1;
                    }
                }
            }
        }
        this.nbColumns = this.headerColumns.length;
        if (this.bodyColumns.length > 0) {
            this.nbRows = this.bodyColumns[0].length;
        } else {
            this.nbRows = 0;
        }
    },

    /**
     *  The datatable needs to be redrawn when the column list has been updated
     */
    needsRedraw: function() {
        return  this.headerColumns[0].parentNode === null;
    },

    /**
     * Update the datatable when its columns sets have changed.
     * Runs on xxforms-nodeset-changed xforms-enabled xforms-disabled.
     */
    updateColumns: function () {
        if (! (this.isInitialized && this.isXformsEnabled())) {
            return; // we'll receive a new call when/if needed
        }
        this.whenDisplayed(_.bind(function() {
            if (this.columnsUpdateUUID != this.getRequestUUID()) {
                this.reset();
                this.isInitialized = false;
                thiss = this;
                this.draw();
            }
            // Otherwise, we've already done an update for this dynamic state!
        }, this));
    },

    /**
     * Update the datatable when its rows have changed.
     * Runs on xxforms-nodeset-changed xforms-enabled xforms-disabled.
     */
    updateRows: function () {
        if (! (this.isInitialized && this.isXformsEnabled())) {
            return; // we'll receive a new call when/if needed
        }

        if (this.rowsUpdateUUID == this.getRequestUUID()) {
            return; // we've already done an update for this dynamic state!
        }

        // This method is called when the xforms:repeat nodeset has been changed
        // this.totalNbRows is the number of rows memorized during the last run of this method...
        if (this.totalNbRows == undefined) {
            this.totalNbRows = -1;
        }
        var totalNbRows = YAHOO.util.Dom.getChildren(this.tbody).length; // this.tbody.rows.length blows up IE 6/7 in some cases here!
        if (totalNbRows != this.totalNbRows) {
            // If the number of rows has changed, we need to reset our cell arrays
            this.getAndSetColumns();
        }
        if (totalNbRows > this.totalNbRows) {
            // If we have new rows, we need to (re)write their cells width
            for (var icol = 0; icol < this.headerColumns.length; icol++) {
                var headerColumn = this.headerColumns[icol];
                var divs = YAHOO.util.Dom.getElementsByClassName('yui-dt-liner', 'div', headerColumn);
                if (divs.length > 0) {
                    var div = divs[0];
                    if (div != undefined) {
                        if (div.style.width != "") {
                            // Resizing is supported through width style properties
                            var width = div.style.width;
                            var styles = [div.style];
                            for (var irow = 0; irow < this.bodyColumns[icol].length; irow++) {
                                var cell = this.bodyColumns[icol][irow];
                                var cellDivs = YAHOO.util.Dom.getElementsByClassName('yui-dt-liner', 'div', cell);
                                if (cellDivs.length > 0) {
                                    var cellDiv = cellDivs[0];
                                    if (cellDiv != undefined) {
                                        cellDiv.style.width = width;
                                        styles[styles.length] = cellDiv.style;
                                    }
                                }
                            }
                            var colResizer = this.colResizers[icol];
                            if (colResizer != undefined) {
                                colResizer.setStyleArray(styles);
                            }
                        } else {
                            // Resizing is supported through dynamic styles
                            var className = 'dt-' + this.id + '-col-' + (icol + 1);
                            className = className.replace(/\$/g, '-');
                            for (var irow = 0; irow < this.bodyColumns[icol].length; irow++) {
                                var cell = this.bodyColumns[icol][irow];
                                var cellDivs = YAHOO.util.Dom.getElementsByClassName('yui-dt-liner', 'div', cell);
                                if (cellDivs.length > 0) {
                                    var cellDiv = cellDivs[0];
                                    if (cellDiv != undefined) {
                                        YAHOO.util.Dom.addClass(cellDiv, className);
                                    }
                                }
                            }

                        }
                    }
                }
            }
        }


        this.totalNbRows = totalNbRows;
        this.rowsUpdateUUID = this.getRequestUUID();

        if (this.adjustHeightForIE) {
            // We need to update the container height for old broken versions of IE :( ...
            this.tableHeight = this.table.clientHeight;
            var bodyHeight = this.tableHeight + 22;
            this.height = bodyHeight + 'px';
            this.bodyContainer.style.height = this.height;
            var height = bodyHeight + this.headerContainer.clientHeight;
            this.divContainer.style.height = height + 'px';
        }
    },

    /**
     * Scroll handler (for split tables)
     * @param e
     */
    scrollHandler: function (e) {
        // alert('scrolling');
        this.headerContainer.scrollLeft = this.bodyContainer.scrollLeft;
    },

    /**
     * Initialize the loading indicator
     * This method is special because it works on datatables
     * that may not have been initialized yet!
     *
     * @param target
     * @param scrollV
     * @param scrollH
     */
    initLoadingIndicator: function(target, scrollV, scrollH) {
        var div = YAHOO.util.Dom.getFirstChild(target);
        var subDiv = YAHOO.util.Dom.getFirstChild(div);
        var table = YAHOO.util.Dom.getFirstChild(subDiv);
        var region = YAHOO.util.Dom.getRegion(table);
        var curTableWidth = region.right - region.left;
        if (curTableWidth < 50) {
            YAHOO.util.Dom.setStyle(table, 'width', '50px');
        }
        if (scrollV) {
            if (YAHOO.env.ua.ie == 6 && target.hasBeenAdjusted == undefined) {
                // This is a hack to adjust the indicator height in IE6 :(
                region = YAHOO.util.Dom.getRegion(div);
                var curHeight = region.bottom - region.top;
                var heightProp = (curHeight - 4) + 'px'    ;
                YAHOO.util.Dom.setStyle(div, 'height', heightProp);
                YAHOO.util.Dom.setStyle(subDiv, 'height', heightProp);
                target.hasBeenAdjusted = true;
            }
            var cell = table.tBodies[0].rows[0].cells[0];
            var cellDiv = YAHOO.xbl.fr.Datatable.utils.getFirstChildByTagName(cell, 'div');
            if (scrollH) {
                YAHOO.util.Dom.setStyle(cellDiv, 'height', (div.clientHeight - 41) + 'px');
            } else {
                YAHOO.util.Dom.setStyle(cellDiv, 'height', (div.clientHeight - 21) + 'px');
                YAHOO.util.Dom.setStyle(div, 'width', (table.clientWidth + 22) + 'px');
            }
        }
    },


    EOO: ''                                                     // End Of Object!
};

/**
 *
 * Utilities
 */
YAHOO.xbl.fr.Datatable.utils = {

    getFirstChildByTagName: function (root, tagName) {
        tagName = tagName.toLowerCase();
        return YAHOO.util.Dom.getFirstChildBy(root, function(element) {
            return element.tagName.toLowerCase() == tagName;
        });
    },

    getFirstChildByTagAndClassName: function (root, tagName, className) {
        tagName = tagName.toLowerCase();
        return YAHOO.util.Dom.getFirstChildBy(root, function(element) {
            return element.tagName.toLowerCase() == tagName && YAHOO.util.Dom.hasClass(element, className);
        });
    },

    getFirstChildByClassName: function (root, className) {
        return YAHOO.util.Dom.getFirstChildBy(root, function(element) {
            return YAHOO.util.Dom.hasClass(element, className)
        });
    },

    getStyle: function (elt, property, defolt) {
        if (elt.style[property] == '') {
            return defolt;
        }
        return elt.style[property];
    },

    freezeWidth: function (elt) {
        YAHOO.util.Dom.setStyle(elt, 'width', elt.clientWidth + 'px');
    },

    isSignificant: function (element) {
        return !YAHOO.util.Dom.hasClass(element, 'xforms-repeat-begin-end')
                && !YAHOO.util.Dom.hasClass(element, 'xforms-repeat-delimiter')
                && !YAHOO.util.Dom.hasClass(element, 'xforms-repeat-template');
    },

    EOO: ''                                                     // End Of Object!

};

YAHOO.xbl.fr.Datatable.colSorter = function (th) {
    var liner = ORBEON.util.Dom.getElementByTagName(th, "div");
    YAHOO.util.Event.addListener(liner, "click", function (ev) {
        var triggerControl = YAHOO.util.Selector.query('.xforms-trigger:not(.xforms-disabled)', liner, true);
        var a = ORBEON.util.Dom.getElementByTagName(triggerControl, "a");
        if (a != undefined && YAHOO.util.Event.getTarget(ev) != a) {
            ORBEON.xforms.Document.dispatchEvent(a.id, "DOMActivate");
        }
    });
};

/**
 * Implementation of datatable.colResizer constructor. Creates the YAHOO.util.DD object.
 *
 * @method YAHOO.xbl.fr.Datatable.colResizer
 * @param col {DOM Element} The th DOM element.
 */
YAHOO.xbl.fr.Datatable.colResizer = function (index, th, datatable) {
    this.index = index;
    this.th = th;
    this.datatable = datatable;
    this.rule = null;
    this.styles = null;

    this.resizerliner = YAHOO.xbl.fr.Datatable.utils.getFirstChildByTagName(th, 'div')

    var childrenDivs = this.resizerliner.getElementsByTagName('div');

    this.liner = childrenDivs[0];

    this.resizer = childrenDivs[1];

};


YAHOO.extend(YAHOO.xbl.fr.Datatable.colResizer, YAHOO.util.DDProxy, {
    /////////////////////////////////////////////////////////////////////////////
    //
    // Public methods
    //
    // ///////////////////////////////////////////////////////////////////////////

    initResizer: function() {
        this.resizer.style.height = this.datatable.headerHeight + 'px';
        YAHOO.util.Dom.setY(this.resizer, YAHOO.util.Dom.getY(this.datatable.headerTable));
        //this.resizer.style.top = (YAHOO.util.Dom.getY(this.datatable.headerTable) - YAHOO.util.Dom.getY(this.th)) + 'px';

        this.init(this.resizer, this.resizer, {
            dragOnly: true, dragElId: this.resizer.id
        });

        this.setYConstraint(0, 0);
        this.initFrame();
        this.delta = 7;

    },

    setStyleArray: function (styles) {
        this.styles = styles;
    },

    setRule: function (rule) {
        this.rule = rule;
    },

    /////////////////////////////////////////////////////////////////////////////
    //
    // Public DOM event handlers
    //
    // ///////////////////////////////////////////////////////////////////////////


    /**
     * Handles mousedown events on the Column resizer.
     *
     * @method onMouseDown
     * @param e
     *            {string} The mousedown event
     */
    onMouseDown: function (ev) {
        this.resetConstraints();
        this.width = this.liner.clientWidth;
        // this.resizerX = YAHOO.util.Dom.getX(this.resizer);
        this.resizerX = YAHOO.util.Event.getXY(ev)[0];
    },

    /**
     * Handles mouseup events on the Column resizer.
     * Reset style left property so that the resizer finds its place
     * if it had lost it!
     *
     * @method onMouseUp
     * @param ev {string} The mousedown event
     */
    onMouseUp: function (ev) {
        this.resizer.style.left = "auto";
    },
    /**
     * Handles drag events on the Column resizer.
     *
     * @method onDrag
     * @param e {string} The drag event
     */
    onDrag: function (ev) {
        var newX = YAHOO.util.Event.getXY(ev)[0];
        this.datatable.table.style.display = 'none';
        var deltaX = newX - this.resizerX;
        this.resizerX = newX;
        var width = this.width + deltaX;
        var widthStyle = (width - 20) + 'px'; // TODO : determine 20 from
        // padding
        // If different and non null, try to set it
        if (width > 20 && width != this.width) {
            this.datatable.adjustWidth(deltaX, this.index);
            if (this.rule) {
                this.rule.style.width = widthStyle;
            } else {
                for (var i = 0; i < this.styles.length; i++) {
                    this.styles[i].width = widthStyle;
                }
            }
            this.width = width;
        }
        this.datatable.table.style.display = '';
    }
});

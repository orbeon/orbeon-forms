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
/*
 *  JavaScript implementation for the datatable component (new generation)
 *
 */

/**
 * Implementation of datatable constructor. Creates column resizers.
 *
 * @method ORBEON.widgets.datatable
 * @param element
 *            {DOM Element} The DOM element that contains the table.
 * @param index
 *            {integer} Index (position) of the table in the document. Currently
 *            not used, but might be useful to generate IDs.
 */
ORBEON.widgets.datatable = function (element, index, innerTableWidth) {

    YAHOO.log("Creating datatable index " + index, "info");
    // Store useful stuff as properties
    this.initProperties(element, index, innerTableWidth);

    this.finish();
}

ORBEON.widgets.datatable.prototype.initProperties = function (element, index, innerTableWidth) {
    this.table = element;
    YAHOO.util.Dom.addClass(this.table, 'fr-dt-initialized');
    this.index = index;
    this.innerTableWidth = innerTableWidth;
    this.header = this.table;
    this.thead = YAHOO.util.Selector.query('thead', this.table, true);
    this.tbody = YAHOO.util.Selector.query('tbody', this.table, true);
    this.getAndSetColumns();
    var plainId = this.table.getAttribute('id');
    //the following doesn't work in all the cases!
    //this.id = plainId.substring(0, plainId.length - '-table'.length);
    //Let's take something safer at least in the short term...
    this.id = plainId;
    this.originalWidth = ORBEON.widgets.datatable.utils.getStyle(this.table, 'width', 'auto');
    this.originalHeight = ORBEON.widgets.datatable.utils.getStyle(this.table, 'height', 'auto');
    this.height = ORBEON.widgets.datatable.utils.getStyle(this.table, 'height', 'auto');
    this.scrollV = YAHOO.util.Dom.hasClass(this.table, 'fr-scrollV');
    this.scrollH = YAHOO.util.Dom.hasClass(this.table, 'fr-scrollH');
    this.scroll = this.scrollV || this.scrollH;
    this.headBodySplit = this.scroll;
    this.hasFixedWidthContainer = this.originalWidth != 'auto';
    this.hasFixedWidthTable = this.hasFixedWidthContainer && ! this.scrollH;
    this.adjustHeightForIE = false;
    this.headerContainer = this.table.parentNode;
    this.container = this.headerContainer.parentNode
}

ORBEON.widgets.datatable.prototype.finish = function () {

    var width = this.originalWidth;
    var pxWidth;

    if (width.indexOf('%') != - 1) {
        // the following block is required to calculate the width in a way that works for IE 6.0 :(
        this.headerContainer.style.overflow = "hidden";
        this.headerContainer.style.width = this.originalWidth;
        var pxWidth = this.headerContainer.clientWidth;
        // Convert % into px...
        width = pxWidth + 'px';
        this.headerContainer.style.overflow = "";
        this.headerContainer.style.width = "";
    } else {
        pxWidth = this.table.clientWidth;
    }

    // See how big the table would be without its size restriction
    if (this.scrollH) {
        if (this.innerTableWidth != null) {
            YAHOO.util.Dom.setStyle(this.table, 'width', this.innerTableWidth);
        } else {
            YAHOO.util.Dom.setStyle(this.table, 'width', 'auto');
        }
    }
    if (this.scrollV) {
        YAHOO.util.Dom.setStyle(this.table, 'height', 'auto');
    }

    this.tableWidth = this.table.clientWidth;
    if (this.tableWidth < pxWidth) {
        this.tableWidth = pxWidth;
    }
    this.tableHeight = this.table.clientHeight;
    this.headerHeight = this.thead.rows[0].clientHeight;
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
                if (YAHOO.env.ua.ie > 0 && YAHOO.env.ua.ie < 8)       {
                    minWidth = this.tableWidth -1;
                } else {
                    minWidth = this.tableWidth; 
                }

            }
            this.tableWidth = this.optimizeWidth(minWidth);
        }
    } else if (this.scrollV) {
        if (this.hasFixedWidthTable) {
            width = this.tableWidth + 'px';
            this.tableWidth = this.tableWidth - 19;
        } else {
            width = (this.tableWidth + 19) + 'px';
        }
    } else {
        width = (this.tableWidth + 2) + 'px';
    }
    YAHOO.util.Dom.setStyle(this.table, 'width', this.tableWidth + 'px');

    this.adjustHeightForIE = this.adjustHeightForIE || (this.scrollH && ! this.scrollV && this.height == 'auto' && YAHOO.env.ua.ie > 0 && YAHOO.env.ua.ie < 8);
    if (this.adjustHeightForIE) {
        this.height = (this.tableHeight + 22) + 'px';
        this.adjustHeightForIE = true;
    }

    // Resize the container
    YAHOO.util.Dom.setStyle(this.container, 'width', width);
    if (this.height != 'auto') {
        YAHOO.util.Dom.setStyle(this.container, 'height', this.height);
    }

    // Resize the header container
    //YAHOO.util.Dom.setStyle(this.headerContainer, 'width', width);
    if (this.height != 'auto' && this.headBodySplit) {
        YAHOO.util.Dom.setStyle(this.headerContainer, 'height', this.headerHeight + 'px');
    } else if (! this.headBodySplit && this.height == 'auto') {
        //	YAHOO.util.Dom.setStyle(this.headerContainer, 'border', '1px solid #7F7F7F')
    }

    // Store the column widths before any split

    var columnWidths = [];
    for (var j = 0; j < this.headerColumns.length; j++) {
        columnWidths[j] = this.headerColumns[j].clientWidth;
    }

    // Split when needed

    this.headerScrollWidth = this.tableWidth;
    if (this.headBodySplit) {
        YAHOO.util.Dom.setStyle(this.headerContainer, 'width', width);
        // Create a container for the body
        this.bodyContainer = document.createElement('div');
        YAHOO.util.Dom.setStyle(this.bodyContainer, 'width', width);
        YAHOO.util.Dom.addClass(this.bodyContainer, 'yui-dt-bd');

        // Duplicate the table to populate the body

        this.table = this.header.cloneNode(true);
        ORBEON.widgets.datatable.removeIdAttributes(this.table);

        // Move the tbody elements to keep event handler bindings in the visible
        // tbody
        var tBody = YAHOO.util.Selector.query('tbody', this.header, true);
        this.header.removeChild(tBody);
        this.table.replaceChild(tBody, YAHOO.util.Selector.query('tbody', this.table, true));
        this.table.removeChild(this.table.tHead);

        // Add an intermediary div to the header to compensate the scroll bar width when needed
        // also force the scrollbars when scrolling in both directions.

        if (this.scrollV) {
            if (this.headerScrollContainer == undefined) {
                this.headerScrollContainer = document.createElement('div');
                this.headerContainer.appendChild(this.headerScrollContainer);
                this.headerContainer.removeChild(this.header);
                this.headerScrollContainer.appendChild(this.header);
            }
            this.headerScrollWidth = this.tableWidth + 20;
            this.headerScrollContainer.style.width = this.headerScrollWidth + 'px';
            this.bodyContainer.style.overflow = "auto";
            this.bodyContainer.style.overflowY = "scroll";
        }


        // Do more resizing
        if (this.height != 'auto') {
            YAHOO.util.Dom.setStyle(this.bodyContainer, 'height', (this.container.clientHeight - this.headerHeight - 5) + 'px');
        }

        // And more assembly
        this.container.appendChild(this.bodyContainer);
        this.bodyContainer.appendChild(this.table);

    }


    if (this.scrollH) {
        YAHOO.util.Event.addListener(this.bodyContainer, 'scroll', ORBEON.widgets.datatable.scrollHandler, this, true);
        this.width = this.container.clientWidth;
        if (this.tableWidth > this.width) {
            this.bodyContainer.style.overflowX = "scroll";
        }   
    } else {
        this.width = this.tableWidth;
    }

    this.colResizers = [];
    this.colSorters = [];
    for (var j = 0; j < this.headerColumns.length; j++) {
        var headerColumn = this.headerColumns[j];
        var childDiv = YAHOO.util.Selector.query('div', headerColumn, true);
        var colResizer = null;
        if (YAHOO.util.Dom.hasClass(this.headerColumns[j], 'yui-dt-resizeable')) {
            colResizer = new ORBEON.widgets.datatable.colResizer(j, this.headerColumns[j], this)
            this.colResizers[j] = colResizer;
        }

        var width = (columnWidths[j] - 20) + 'px';
        var rule;
        // See _setColumnWidth in YUI datatable.js...
        if (YAHOO.env.ua.ie == 0) {
            var className = 'dt-' + this.id + '-col-' + (j + 1);
            className = className.replace('\$', '-', 'g');
            YAHOO.util.Dom.addClass(childDiv, className);
            for (var k = 0; k < this.bodyColumns[j].length; k++) {
                var cell = this.bodyColumns[j][k];
                var liner = YAHOO.util.Selector.query('div', cell, true);
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
            var style = childDiv.style;
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
            this.colSorters[ this.colSorters.length] = new ORBEON.widgets.datatable.colSorter(this.headerColumns[j]);
        }

    }

    // Now that the table has been properly sized, reconsider its
    // "resizeability"

    if (this.hasFixedWidthContainer && this.hasFixedWidthTable) {
        // These are fixed width tables without horizontal scroll bars
        // and as we don't know how to resize their columns properly,
        // we'd better consider that as variable width
        this.hasFixedWidthContainer = false;
        this.hasFixedWidthTable = false;
    }
    YAHOO.log("Datatable index " + this.index + 'created with width: ' + this.width + ', table width: ' + this.tableWidth, "info")

}

ORBEON.widgets.datatable.prototype.reset = function () {
    // undo the split so that the size can be computed accurately
    if (this.headBodySplit) {
        var body = this.table.tBodies[0];
        this.container.removeChild(this.bodyContainer);
        this.header.appendChild(body);
        this.table = this.header;
    }
    if (!YAHOO.util.Dom.hasClass(this.table.parentNode, 'yui-dt-hd')) {
        var div1 = this.table.parentNode;
        var div2 = div1.parentNode;
        div1.removeChild(this.table);
        div2.removeChild(div1);
        div2.appendChild(this.table);
    }
    // Restore styles rules to the table
    this.table.style.width = this.originalWidth;
    this.table.style.height = this.originalHeight;
    // Restore column headers
    for (var icol = 0; icol < this.headerColumns.length; icol++) {
        var th = this.headerColumns[icol];
        var resizerliner = ORBEON.widgets.datatable.utils.getFirstChildByTagAndClassName(th, 'div', 'yui-dt-resizerliner');
        if (resizerliner != null) {
            var resizer = ORBEON.widgets.datatable.utils.getFirstChildByTagAndClassName(resizerliner, 'div', 'yui-dt-resizer');
            if (resizer != null) {
                resizerliner.removeChild(resizer);
            }
            var liner = ORBEON.widgets.datatable.utils.getFirstChildByTagName(resizerliner, 'div');
            liner.style.width = "";
            th.removeChild(resizerliner);
            th.appendChild(liner);
        }
    }
    // Remove column widths
    for (var icol = 0; icol < this.bodyColumns.length; icol++) {
        var col = this.bodyColumns[icol];
        for (var irow = 0; irow < col.length; irow++) {
            var cell = col[irow];
            var liner = ORBEON.widgets.datatable.utils.getFirstChildByTagAndClassName(cell, 'div', 'yui-dt-liner');
            if (liner != null) {
                liner.style.width = "";
            }
        }
    }
    // Remove the containers widths and heights
    this.container.style.height='';
    this.headerContainer.style.height='';
    this.container.style.width='';
    this.headerContainer.style.width='';

    // remove the dynamic style sheet if it exists
    if (this.styleElt != undefined) {
        this.styleElt.parentNode.removeChild(this.styleElt);
        this.styleElt = undefined;
    }
}

ORBEON.widgets.datatable.prototype.getTableHeightForWidth = function (width) {
    this.table.style.width = width + 'px';
    return this.table.clientHeight;
}

ORBEON.widgets.datatable.prototype.optimizeWidth = function (minWidth) {
    this.headerContainer.style.position = "absolute";
    this.headerContainer.style.width = "2500px";
    var savedWidth = this.table.style.width;
    this.table.style.width = "auto";
    var width = this.table.clientWidth;
    this.tableHeight = this.table.clientHeight;
    this.headerContainer.style.position = "";
    this.headerContainer.style.width = "";
    this.table.style.width = savedWidth;
    if (minWidth > width) {
        return minWidth;
    }
    return width;
}

ORBEON.widgets.datatable.prototype.adjustWidth = function (deltaX, index) {
    //alert('Before-> this.width: ' + this.width +', this.tableWidth: ' + this.tableWidth);
    if (! this.hasFixedWidthContainer) {
        this.width += deltaX;
        if (this.headBodySplit) {
            YAHOO.util.Dom.setStyle(this.container, 'width', this.width + 'px');
            YAHOO.util.Dom.setStyle(this.headerContainer, 'width', this.width + 'px');
            YAHOO.util.Dom.setStyle(this.bodyContainer, 'width', this.width + 'px');
        } else {
            YAHOO.util.Dom.setStyle(this.container, 'width', (this.width + 2) + 'px');
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
            YAHOO.util.Dom.setStyle(this.header, 'width', this.tableWidth + 'px');
        }
    }
    //alert('After-> this.width: ' + this.width +', this.tableWidth: ' + this.tableWidth);
}

ORBEON.widgets.datatable.prototype.getAndSetColumns = function () {

    this.headerColumns = [];
    this.bodyColumns = [];
    var headerCells = this.thead.rows[0].cells;
    for (var icol = 0; icol < headerCells.length; icol++) {
        var cell = headerCells[icol];
        if (ORBEON.widgets.datatable.isSignificant(cell)) {
            this.headerColumns.push(cell);
            this.bodyColumns.push([]);
        }
    }
    var bodyRows = YAHOO.util.Dom.getChildren(this.tbody);  //    bodyRows.length blows up IE 6/7 in some cases here :(
    for (var irow = 0; irow < bodyRows.length; irow++) {
        var row = bodyRows[irow];
        if (ORBEON.widgets.datatable.isSignificant(row)) {
            var iActualCol = 0;
            var cells = YAHOO.util.Dom.getChildren(row);
            for (var icol = 0; icol < cells.length; icol++) {
                var cell = cells[icol];
                if (ORBEON.widgets.datatable.isSignificant(cell)) {
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
}


ORBEON.widgets.datatable.prototype.update = function () {
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
                        className = className.replace('\$', '-', 'g');
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

    if (this.adjustHeightForIE) {
        // We need to update the container height for old broken versions of IE :( ...
        this.tableHeight = this.table.clientHeight;
        var bodyHeight = this.tableHeight + 22;
        this.height = bodyHeight + 'px';
        this.bodyContainer.style.height = this.height;
        var height = bodyHeight + this.headerContainer.clientHeight;
        this.container.style.height = height + 'px';
    }
}

ORBEON.widgets.datatable.scrollHandler = function (e) {
    //alert('scrolling');
    this.headerContainer.scrollLeft = this.bodyContainer.scrollLeft;
}

ORBEON.widgets.datatable.utils = [];


ORBEON.widgets.datatable.utils.getFirstChildByTagName = function (root, tagName) {
    tagName = tagName.toLowerCase();
    return YAHOO.util.Dom.getFirstChildBy(root, function(element) {
        return element.tagName.toLowerCase() == tagName;
    });
}

ORBEON.widgets.datatable.utils.getFirstChildByTagAndClassName = function (root, tagName, className) {
    tagName = tagName.toLowerCase();
    return YAHOO.util.Dom.getFirstChildBy(root, function(element) {
        return element.tagName.toLowerCase() == tagName && YAHOO.util.Dom.hasClass(element, className);
    });
}

ORBEON.widgets.datatable.utils.getFirstChildByClassName = function (root, className) {
    return YAHOO.util.Dom.getFirstChildBy(root, function(element) {
        return YAHOO.util.Dom.hasClass(element, className)
    });
}

ORBEON.widgets.datatable.utils.getStyle = function (elt, property, defolt) {
    if (elt.style[property] == '') {
        return defolt;
    }
    return elt.style[property];
}

ORBEON.widgets.datatable.utils.freezeWidth = function (elt) {
    YAHOO.util.Dom.setStyle(elt, 'width', elt.clientWidth + 'px');
}

ORBEON.widgets.datatable.colSorter = function (th) {
    var liner = YAHOO.util.Selector.query('div.yui-dt-liner', th, true);
    YAHOO.util.Event.addListener(liner, "click", function (ev) {
        var a = YAHOO.util.Selector.query('a.xforms-trigger:not(.xforms-disabled)', liner, true);
        if (YAHOO.util.Event.getTarget(ev) != a) {
            ORBEON.xforms.Document.dispatchEvent(a.id, "DOMActivate");
        }
    });
}

/**
 * Implementation of datatable.colResizer constructor. Creates the YAHOO.util.DD object.
 *
 * @method ORBEON.widgets.datatable.colResizer
 * @param col {DOM Element} The th DOM element.
 */
ORBEON.widgets.datatable.colResizer = function (index, th, datatable) {
    this.index = index;
    this.th = th;
    this.datatable = datatable;
    this.rule = null;
    this.styles = null;

    this.resizerliner = document.createElement('div');
    YAHOO.util.Dom.addClass(this.resizerliner, 'yui-dt-resizerliner');

    this.liner = ORBEON.widgets.datatable.utils.getFirstChildByTagName(th, 'div');

    this.th.replaceChild(this.resizerliner, this.liner);

    this.resizerliner.appendChild(this.liner);

    this.resizer = document.createElement('div');
    YAHOO.util.Dom.addClass(this.resizer, 'yui-dt-resizer');
    this.resizerliner.appendChild(this.resizer);
    this.resizer.style.left = 'auto';
    this.resizer.style.right = '0pt';
    this.resizer.style.top = 'auto';
    this.resizer.style.bottom = '0pt';
    this.resizer.style.height = datatable.headerHeight + 'px';


    this.init(this.resizer, this.resizer, {
        dragOnly: true, dragElId: this.resizer.id
    });

    this.setYConstraint(0, 0);
    this.initFrame();
    this.delta = 7;
}


YAHOO.extend(ORBEON.widgets.datatable.colResizer, YAHOO.util.DDProxy, {
    /////////////////////////////////////////////////////////////////////////////
    //
    // Public methods
    //
    // ///////////////////////////////////////////////////////////////////////////

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
     * @param e
     *            {string} The mousedown event
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

ORBEON.widgets.datatable.isSignificant = function (element) {
    return !YAHOO.util.Dom.hasClass(element, 'xforms-repeat-begin-end')
            && !YAHOO.util.Dom.hasClass(element, 'xforms-repeat-delimiter')
            && !YAHOO.util.Dom.hasClass(element, 'xforms-repeat-template');
}

ORBEON.widgets.datatable.removeIdAttributes = function (element, skipSelf) {
    if (! skipSelf) {
        element.removeAttribute('id');
    }
    for (var i = 0; i < element.childNodes.length; i++) {
        var node = element.childNodes[i];
        if (node.nodeType == 1) {
            ORBEON.widgets.datatable.removeIdAttributes(node);
        }
    }
}


ORBEON.widgets.datatable.initLoadingIndicator = function(target, scrollV, scrollH) {
    var div = YAHOO.util.Dom.getFirstChild(target);
    var subDiv = YAHOO.util.Dom.getFirstChild(div);
    var table = YAHOO.util.Dom.getFirstChild(subDiv);
    var region =    YAHOO.util.Dom.getRegion(table);
    var curTableWidth = region.right - region.left;
    if (curTableWidth < 50) {
        YAHOO.util.Dom.setStyle(table, 'width', '50px');    
    }
    if (scrollV) {
        if (YAHOO.env.ua.ie == 6 && target.hasBeenAdjusted == undefined ) {
            // This is a hack to adjust the indicator height in IE6 :(
            region =    YAHOO.util.Dom.getRegion(div);
            var curHeight = region.bottom - region.top;
            var heightProp =   (curHeight - 4) + 'px'    ;
            YAHOO.util.Dom.setStyle(div, 'height', heightProp);
            YAHOO.util.Dom.setStyle(subDiv, 'height', heightProp);
            target.hasBeenAdjusted = true;
        }
        var cell = table.tBodies[0].rows[0].cells[0];
        var cellDiv = ORBEON.widgets.datatable.utils.getFirstChildByTagName(cell, 'div');
        if (scrollH) {
            YAHOO.util.Dom.setStyle(cellDiv, 'height', (div.clientHeight - 41) + 'px');
        } else {
            YAHOO.util.Dom.setStyle(cellDiv, 'height', (div.clientHeight - 21) + 'px');
            YAHOO.util.Dom.setStyle(div, 'width', (table.clientWidth + 22) + 'px');
        }
    }
}

ORBEON.widgets.datatable.init = function (target, innerTableWidth) {
    // Initializes a datatable (called by xforms-enabled events)
    var container = YAHOO.util.Dom.getAncestorByClassName(target, 'xbl-fr-datatable');
    var id = container.id;
    if (! YAHOO.util.Dom.hasClass(target, 'xforms-disabled')) {
        if (ORBEON.widgets.datatable.datatables[id] == undefined || container.fr_dt_initialized == undefined) {
            var table = YAHOO.util.Selector.query('table', target.parentNode, false)[0];
            var region = YAHOO.util.Region.getRegion(table);
            if ((region.left >= 0 && region.top >= 0)
                && (region.left < region.right) 
                && (region.top < region.bottom) ) {
                ORBEON.widgets.datatable.datatables[id] = new ORBEON.widgets.datatable(table, id, innerTableWidth);
                container.fr_dt_initialized = true;
            } else {
                // Hack!!! We are here if the datatable is hidden unselected in an xforms:switch/xforms:case...
                setTimeout(function() {
                    ORBEON.widgets.datatable.init(target, innerTableWidth);
                }, 100);
            }
        } else {
            ORBEON.widgets.datatable.datatables[id].update();
        }
    }

}

ORBEON.widgets.datatable.update = function (target) {
    // Updates a datatable when the xforms:repeat nodeset has been changed
    var container = YAHOO.util.Dom.getAncestorByClassName(target, 'xbl-fr-datatable');
    var id = container.id;
    YAHOO.log("Updating datatable " + id, "info");
    if (! YAHOO.util.Dom.hasClass(target.parentNode, 'xforms-disabled')) {
        if (ORBEON.widgets.datatable.datatables[id] != undefined) {
            ORBEON.widgets.datatable.datatables[id].update();
        }
    }

}

ORBEON.widgets.datatable.resize = function () {
    if (ORBEON.widgets.datatable.waitingToResize) {
        return;
    }
    ORBEON.widgets.datatable.waitingToResize = true;
    setTimeout("ORBEON.widgets.datatable.resizeWhenStabilized()", 200);
}

ORBEON.widgets.datatable.resizeWhenStabilized = function () {


    var width = YAHOO.util.Dom.getViewportWidth();

    if (ORBEON.widgets.datatable.previousBodyWidth != width) {
        // The window width is still changing, wait till it get stabilized
        ORBEON.widgets.datatable.previousBodyWidth = width;
        setTimeout("ORBEON.widgets.datatable.resizeWhenStabilized()", 200);
        return;
    }

    ORBEON.widgets.datatable.waitingToResize = false;
    if (ORBEON.widgets.datatable.bodyWidthWhenResized == width) {
        // The datatables have already been resized for that window width, nothing to do...
        return;
    }

    var oldDatatables = [];
    ORBEON.widgets.datatable.bodyWidthWhenResized = width;
    for (var tableId in ORBEON.widgets.datatable.datatables) {
        var datatable = ORBEON.widgets.datatable.datatables[tableId];
        oldDatatables.push(datatable);
        datatable.reset();
    }
    ORBEON.widgets.datatable.datatables = {};
    for (var itable = 0; itable < oldDatatables.length; itable ++) {
        var datatable = oldDatatables[itable];
        ORBEON.widgets.datatable.init(datatable.table.parentNode, datatable.innerTableWidth);
    }
}


YAHOO.util.Event.addListener(window, "resize", ORBEON.widgets.datatable.resize);

// Comment/uncomment in normal/debug mode...
//var myLogReader = new YAHOO.widget.LogReader();

ORBEON.widgets.datatable.datatables = {
};

ORBEON.widgets.datatable.waitingToResize = false;
ORBEON.widgets.datatable.previousBodyWidth = YAHOO.util.Dom.getViewportWidth();
ORBEON.widgets.datatable.bodyWidthWhenResized = ORBEON.widgets.datatable.previousBodyWidth;


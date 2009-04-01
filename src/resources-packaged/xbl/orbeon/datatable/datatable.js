/**
 *  Copyright (C) 2009 Orbeon, Inc.
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
 * Implementation of table_ng constructor. Creates column resizers.
 *
 * @method ORBEON.widgets.table_ng
 * @param element {DOM Element} The DOM element that contains the table.
 * @param index {integer} Index (position) of the table in the document.
 *        Currently not used, but might be useful to generate IDs.
 */
ORBEON.widgets.table_ng = function (element, index) {
    this.el = element;
    this.index = index;
    this.colResizers = [];
    var resizers = YAHOO.util.Dom.getElementsByClassName('yui-dt-resizer', 'div', this.el);
    for (var i=0; i < resizers.length; i++) {
        this.colResizers[this.colResizers.length] = new ORBEON.widgets.table_ng.colResizer(resizers[i]);
    }
}

/**
 * Implementation of table_ng.colResizer constructor. Creates the YAHOO.util.DD object.
 *
 * @method ORBEON.widgets.table_ng.colResizer
 * @param element {DOM Element} The DOM element that contains the resizer handle.
 */
ORBEON.widgets.table_ng.colResizer = function (element) {
    this.el = element;
    this.col = this.el.parentNode.parentNode;
    this.dd = new YAHOO.util.DD(this.el);
    this.dd.setYConstraint(0, 0);
    var colRegion = YAHOO.util.Dom.getRegion(this.col);
    var X = YAHOO.util.Dom.getX(this.el);
    this.delta = colRegion.right - X;
    this.dd.on('mouseDownEvent', ORBEON.widgets.table_ng.colResizer.mouseDown, this, true);
    this.dd.on('dragEvent', ORBEON.widgets.table_ng.colResizer.drag, this, true);
}

/**
 * Implementation of table_ng.colResizer.mouseDown event handler. Stores the positions as a D&D action might start.
 *
 * @method ORBEON.widgets.table_ng.colResizer.mouseDown
 * @param ev {Event}.
 */
ORBEON.widgets.table_ng.colResizer.mouseDown = function (ev) {
    this.colRegion = YAHOO.util.Dom.getRegion(this.col);
    this.colWidth = this.colRegion.right - this.colRegion.left;
    this.elX = YAHOO.util.Dom.getX(this.el);
}

/**
 * Implementation of table_ng.colResizer.drag event handler. Adjusts the column size.
 *
 * @method ORBEON.widgets.table_ng.colResizer.drag
 * @param ev {Event}.
 */
ORBEON.widgets.table_ng.colResizer.drag = function (ev) {
    // Calculate the new length
    var newX = YAHOO.util.Dom.getX(this.el);
    var width = this.colWidth + newX -this.elX;
    // If different and non null, try to set it
    if (width > 0 && width != this.colWidth) {
        YAHOO.util.Dom.setStyle(this.col, 'width', width + 'px');
    }
    // If the width adjustement has failed, the column is probably
    // too small, move the handle back to its normal position
    var colRegion = YAHOO.util.Dom.getRegion(this.col);
    if (width != colRegion.right - colRegion.left) {
        YAHOO.util.Dom.setX(this.el, colRegion.right - this.delta);
    }
}

/**
 * Creates table_ng objects.
 *
 * @method ORBEON.widgets.table_ng.init
 */
ORBEON.widgets.table_ng.init = function() {
    // Transforms all the datatables in a document
    var tables = YAHOO.util.Dom.getElementsByClassName('datatable', 'div');
    for (var i=0; i < tables.length; i++) {
        new ORBEON.widgets.table_ng(tables[i], i);
    }
}

YAHOO.util.Event.onDOMReady(ORBEON.widgets.table_ng.init);
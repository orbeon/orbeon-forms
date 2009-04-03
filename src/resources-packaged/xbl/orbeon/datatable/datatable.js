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
 * Implementation of datatable constructor. Creates column resizers.
 *
 * @method ORBEON.widgets.datatable
 * @param element {DOM Element} The DOM element that contains the table.
 * @param index {integer} Index (position) of the table in the document.
 *        Currently not used, but might be useful to generate IDs.
 */
ORBEON.widgets.datatable = function (element, index) {
    this.container = element;
    this.index = index;
    var plainId = this.container.getAttribute('id');
    this.id = plainId.substring(0, plainId.length - '-container'.length);
    this.innerContainer = document.getElementById(this.id + '-inner-container');
    this.table = document.getElementById(this.id + '-table');
    this.thead = document.getElementById(this.id + '-thead');
    this.theadTr = document.getElementById(this.id + '-thead-tr');
    this.tbody = document.getElementById(this.id + '-tbody');
    this.scrollV = YAHOO.util.Dom.hasClass(this.container, 'fr-scrollV');
    this.scrollH = YAHOO.util.Dom.hasClass(this.container, 'fr-scrollH');


    if (this.scrollH) {
         var cWidth = YAHOO.util.Dom.getStyle(this.innerContainer, 'width');
         YAHOO.util.Dom.setStyle(this.innerContainer, 'width', 'auto');
         var tRegion = YAHOO.util.Dom.getRegion(this.table);
         YAHOO.util.Dom.setStyle(this.innerContainer, 'width', cWidth);
         YAHOO.util.Dom.setStyle(this.table, 'width', (tRegion.right - tRegion.left + 15) + 'px');
    } 

    if (this.scrollV) {
         var cRegion = YAHOO.util.Dom.getRegion(this.innerContainer);
         var thRegion = YAHOO.util.Dom.getRegion(this.thead);
         YAHOO.util.Dom.setStyle(this.tbody, 'height', ((cRegion.bottom - cRegion.top) - (thRegion.bottom - thRegion.top) - 4) + 'px');
    }

    this.colResizers = [];
    var resizers = YAHOO.util.Dom.getElementsByClassName('yui-dt-resizer', 'div', this.container);
    for (var i=0; i < resizers.length; i++) {
        this.colResizers[this.colResizers.length] = new ORBEON.widgets.datatable.colResizer(resizers[i]);
    }
}

ORBEON.widgets.datatable.scrollHandler = function (e) {
    //alert('scrolling');
    YAHOO.util.Dom.setY(this.thead, this.theadY);
}

/**
 * Implementation of datatable.colResizer constructor. Creates the YAHOO.util.DD object.
 *
 * @method ORBEON.widgets.datatable.colResizer
 * @param element {DOM Element} The DOM element that contains the resizer handle.
 */
ORBEON.widgets.datatable.colResizer = function (element) {
    this.el = element;
    this.col = this.el.parentNode.parentNode;
    this.dd = new YAHOO.util.DD(this.el);
    this.dd.setYConstraint(0, 0);
    var colRegion = YAHOO.util.Dom.getRegion(this.col);
    var X = YAHOO.util.Dom.getX(this.el);
    this.delta = colRegion.right - X;
    this.dd.on('mouseDownEvent', ORBEON.widgets.datatable.colResizer.mouseDown, this, true);
    this.dd.on('dragEvent', ORBEON.widgets.datatable.colResizer.drag, this, true);
}

/**
 * Implementation of datatable.colResizer.mouseDown event handler. Stores the positions as a D&D action might start.
 *
 * @method ORBEON.widgets.datatable.colResizer.mouseDown
 * @param ev {Event}.
 */
ORBEON.widgets.datatable.colResizer.mouseDown = function (ev) {
    this.colRegion = YAHOO.util.Dom.getRegion(this.col);
    this.colWidth = this.colRegion.right - this.colRegion.left;
    this.elX = YAHOO.util.Dom.getX(this.el);
}

/**
 * Implementation of datatable.colResizer.drag event handler. Adjusts the column size.
 *
 * @method ORBEON.widgets.datatable.colResizer.drag
 * @param ev {Event}.
 */
ORBEON.widgets.datatable.colResizer.drag = function (ev) {
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
 * Creates datatable objects.
 *
 * @method ORBEON.widgets.datatable.init
 */
ORBEON.widgets.datatable.init = function() {
    // Transforms all the datatables in a document
    var tables = YAHOO.util.Dom.getElementsByClassName('datatable', 'div');
    for (var i=0; i < tables.length; i++) {
        new ORBEON.widgets.datatable(tables[i], i);
    }
}

YAHOO.util.Event.onDOMReady(ORBEON.widgets.datatable.init);
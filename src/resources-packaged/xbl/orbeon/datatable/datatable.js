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

    // Store useful stuff as properties
    this.table = element;
    this.header = this.table;
    var plainId = this.table.getAttribute('id');
    this.id = plainId.substring(0, plainId.length - '-table'.length);
    this.width = ORBEON.widgets.datatable.utils.getStyle(this.table, 'width', 'auto');
    this.height = ORBEON.widgets.datatable.utils.getStyle(this.table, 'height', 'auto');
    this.scrollV = YAHOO.util.Dom.hasClass(this.table, 'fr-scrollV');
    this.scrollH = YAHOO.util.Dom.hasClass(this.table, 'fr-scrollH');
    this.scroll = this.scrollV || this.scrollH;
    this.headBodySplit = this.scroll;

    // Create a global container
    this.container = document.createElement('div');
    YAHOO.util.Dom.addClass(this.container, 'yui-dt');
    YAHOO.util.Dom.addClass(this.container, 'yui-dt-scrollable');

    // Create a container for the header (or the whole table if there is no scrolling)
    this.headerContainer = document.createElement('div');
    YAHOO.util.Dom.addClass(this.headerContainer, 'yui-dt-hd');

    // Assemble all that stuff
    this.table.parentNode.replaceChild(this.container, this.table);
    this.container.appendChild(this.headerContainer);
    this.headerContainer.appendChild(this.header);
 
    // See how big the table would be without its size restriction
    YAHOO.util.Dom.setStyle(this.table, 'width', 'auto');
    YAHOO.util.Dom.setStyle(this.table, 'height', 'auto');
    //YAHOO.util.Dom.setStyle(this.table, 'position', 'absolute');
    this.region = YAHOO.util.Dom.getRegion(this.table);
    this.tableWidth = (this.region.right - this.region.left);
    this.tableHeight = (this.region.bottom - this.region.top);
    if (this.scrollH) {
        for (var i = this.tableWidth; i < 2000; i+=50){
            YAHOO.util.Dom.setStyle(this.table, 'width', i + 'px');
            var region = YAHOO.util.Dom.getRegion(this.table);
            var tableWidth = (region.right - region.left);
            var tableHeight = (region.bottom - region.top);
            if (tableHeight < this.tableHeight) {
                this.region = region;
                this.tableWidth = tableWidth;
                this.tableHeight = tableHeight;
            }
        }
        
    } else if (this.scrollV) {
        this.width = (this.tableWidth + 17) + 'px';
    } else {
        this.width = this.tableWidth + 'px';
    }
    YAHOO.util.Dom.setStyle(this.table, 'width', this.tableWidth + 'px');

    if (this.scrollH && ! this.scrollV && this.height == 'auto' && YAHOO.env.ua.ie > 0) {
        this.height = (this.tableHeight + 22)  + 'px';
    }

this.headerRegion = YAHOO.util.Dom.getRegion(YAHOO.util.Selector.query('thead', this.table, true));
    this.bodyRegion = YAHOO.util.Dom.getRegion(YAHOO.util.Selector.query('tbody', this.table, true));

    // Resize the container
    YAHOO.util.Dom.setStyle(this.container, 'width', this.width);
    if (this.height != 'auto') {
        YAHOO.util.Dom.setStyle(this.container, 'height', this.height);
    }

    // Resize the header container
    YAHOO.util.Dom.setStyle(this.headerContainer, 'width', this.width);
    if (this.height != 'auto' && this.headBodySplit) {
        YAHOO.util.Dom.setStyle(this.headerContainer, 'height', (this.headerRegion.bottom - this.headerRegion.top) + 'px');
    } else if (!this.headBodySplit && this.height == 'auto') {
         YAHOO.util.Dom.setStyle(this.headerContainer, 'border', '1px solid #7F7F7F')
    }

    if (this.headBodySplit) {

        // Create a container for the body
        this.bodyContainer = document.createElement('div');
        YAHOO.util.Dom.setStyle(this.bodyContainer, 'width', this.width);
        YAHOO.util.Dom.addClass(this.bodyContainer, 'yui-dt-bd');

        // Duplicate the table to populate the body

        this.table = this.header.cloneNode(true);
        ORBEON.widgets.datatable.removeIdAttributes(this.table);

        // Move the tbody elements to keep event handler bindings in the visible tbody
        var tBody = YAHOO.util.Selector.query('tbody', this.header, true);
        this.header.removeChild(tBody);
        this.table.replaceChild(tBody, YAHOO.util.Selector.query('tbody', this.table, true));



        // Do more resizing
        this.containerRegion = YAHOO.util.Dom.getRegion(this.container);
        if (this.height != 'auto') {
            YAHOO.util.Dom.setStyle(this.bodyContainer, 'height', ((this.containerRegion.bottom - this.containerRegion.top) - (this.headerRegion.bottom - this.headerRegion.top) -5) + 'px');
        }

        // And more assembly
        this.container.appendChild(this.bodyContainer);
        this.bodyContainer.appendChild(this.table);

        var headerColumns = this.header.getElementsByTagName('thead')[0].getElementsByTagName('tr')[0].getElementsByTagName('th');
        var bodyColumns = this.table.getElementsByTagName('tbody')[0].getElementsByTagName('tr')[2].getElementsByTagName('td');

        for (var i=0; i< bodyColumns.length; i++) {
            var r = YAHOO.util.Dom.getRegion(bodyColumns[i]);
            YAHOO.util.Dom.setStyle(headerColumns[i], 'width', (r.right - r.left - 1) + 'px');
        }
    }

    if (this.scrollH) {
        YAHOO.util.Event.addListener(this.bodyContainer, 'scroll', ORBEON.widgets.datatable.scrollHandler, this, true);
    }

//    this.container = element;
//    this.index = index;
//    var plainId = this.container.getAttribute('id');
//    this.id = plainId.substring(0, plainId.length - '-container'.length);
//    this.innerContainer = document.getElementById(this.id + '-inner-container');
//    this.table = document.getElementById(this.id + '-table');
//    this.thead = document.getElementById(this.id + '-thead');
//    this.theadTr = document.getElementById(this.id + '-thead-tr');
//    this.tbody = document.getElementById(this.id + '-tbody');
//    this.scrollV = YAHOO.util.Dom.hasClass(this.container, 'fr-scrollV');
//    this.scrollH = YAHOO.util.Dom.hasClass(this.container, 'fr-scrollH');
//
//
//    if (this.scrollH) {
//         var cWidth = YAHOO.util.Dom.getStyle(this.innerContainer, 'width');
//         YAHOO.util.Dom.setStyle(this.innerContainer, 'width', 'auto');
//         var tRegion = YAHOO.util.Dom.getRegion(this.table);
//         YAHOO.util.Dom.setStyle(this.innerContainer, 'width', cWidth);
//         YAHOO.util.Dom.setStyle(this.table, 'width', (tRegion.right - tRegion.left + 15) + 'px');
//    }
//
//    if (this.scrollV) {
//         var cRegion = YAHOO.util.Dom.getRegion(this.innerContainer);
//         var thRegion = YAHOO.util.Dom.getRegion(this.thead);
//         YAHOO.util.Dom.setStyle(this.tbody, 'height', ((cRegion.bottom - cRegion.top) - (thRegion.bottom - thRegion.top) - 4) + 'px');
//    }
//
//    this.colResizers = [];
//    var resizers = YAHOO.util.Dom.getElementsByClassName('yui-dt-resizer', 'div', this.container);
//    for (var i=0; i < resizers.length; i++) {
//        this.colResizers[this.colResizers.length] = new ORBEON.widgets.datatable.colResizer(resizers[i]);
//    }
}


ORBEON.widgets.datatable.scrollHandler = function (e) {
    //alert('scrolling');
    this.headerContainer.scrollLeft = this.bodyContainer.scrollLeft;
}

ORBEON.widgets.datatable.utils = [] ;

ORBEON.widgets.datatable.utils.getStyle = function (elt, property, defolt) {
    if (elt.style[property] == '') {
        return defolt;
    }
    return elt.style[property];
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

ORBEON.widgets.datatable.removeIdAttributes = function(element, skipSelf) {
    if (!skipSelf) {
        element.removeAttribute('id');
     }
    for (var i =0; i< element.childNodes.length; i++) {
        var node = element.childNodes[i];
        if (node.nodeType == 1) {
            ORBEON.widgets.datatable.removeIdAttributes(node);
        }
    }
}

/**
 * Creates datatable objects.
 *
 * @method ORBEON.widgets.datatable.init
 */
ORBEON.widgets.datatable.init = function() {
    // Transforms all the datatables in a document
    var tables = YAHOO.util.Dom.getElementsByClassName('datatable', 'table');
    for (var i=0; i < tables.length; i++) {
        new ORBEON.widgets.datatable(tables[i], i);
    }
}

YAHOO.util.Event.onDOMReady(ORBEON.widgets.datatable.init);
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
    this.headerColumns = this.header.getElementsByTagName('thead')[0].getElementsByTagName('tr')[0].getElementsByTagName('th');
    this.bodyRows = this.table.getElementsByTagName('tbody')[0].getElementsByTagName('tr');
    this.bodyColumns = this.bodyRows[2].getElementsByTagName('td');
    var plainId = this.table.getAttribute('id');
    this.id = plainId.substring(0, plainId.length - '-table'.length);
    var width = ORBEON.widgets.datatable.utils.getStyle(this.table, 'width', 'auto');
    this.height = ORBEON.widgets.datatable.utils.getStyle(this.table, 'height', 'auto');
    this.scrollV = YAHOO.util.Dom.hasClass(this.table, 'fr-scrollV');
    this.scrollH = YAHOO.util.Dom.hasClass(this.table, 'fr-scrollH');
    this.scroll = this.scrollV || this.scrollH;
    this.headBodySplit = this.scroll;
    this.hasFixedWidthContainer = width != 'auto';
    this.hasFixedWidthTable = this.hasFixedWidthContainer && !this.scrollH;

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

    var region = YAHOO.util.Dom.getRegion(this.table);
    this.tableWidth = (region.right - region.left);
    this.tableHeight = (region.bottom - region.top);
    if (this.scrollH) {
        for (var i = this.tableWidth; i < 2000; i+=50){
            YAHOO.util.Dom.setStyle(this.table, 'width', i + 'px');
            region = YAHOO.util.Dom.getRegion(this.table);
            var tableWidth = (region.right - region.left);
            var tableHeight = (region.bottom - region.top);
            if (tableHeight < this.tableHeight) {
                this.tableWidth = tableWidth;
                this.tableHeight = tableHeight;
            }
        }
        
    } else if (this.scrollV) {
        width = (this.tableWidth + 17) + 'px';
    } else {
        width = this.tableWidth + 'px';
    }
    YAHOO.util.Dom.setStyle(this.table, 'width', this.tableWidth + 'px');

    if (this.scrollH && ! this.scrollV && this.height == 'auto' && YAHOO.env.ua.ie > 0) {
        this.height = (this.tableHeight + 22)  + 'px';
    }

    this.headerRegion = YAHOO.util.Dom.getRegion(YAHOO.util.Selector.query('thead', this.table, true));
    this.bodyRegion = YAHOO.util.Dom.getRegion(YAHOO.util.Selector.query('tbody', this.table, true));

    // Resize the container
    YAHOO.util.Dom.setStyle(this.container, 'width', width);
    if (this.height != 'auto') {
        YAHOO.util.Dom.setStyle(this.container, 'height', this.height);
    }

    // Resize the header container
    YAHOO.util.Dom.setStyle(this.headerContainer, 'width', width);
    if (this.height != 'auto' && this.headBodySplit) {
        YAHOO.util.Dom.setStyle(this.headerContainer, 'height', (this.headerRegion.bottom - this.headerRegion.top) + 'px');
    } else if (!this.headBodySplit && this.height == 'auto') {
         YAHOO.util.Dom.setStyle(this.headerContainer, 'border', '1px solid #7F7F7F')
    }

    if (this.headBodySplit) {

        // Create a container for the body
        this.bodyContainer = document.createElement('div');
        YAHOO.util.Dom.setStyle(this.bodyContainer, 'width', width);
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

 
        for (var i=0; i< this.bodyColumns.length; i++) {
            var r = YAHOO.util.Dom.getRegion(this.bodyColumns[i]);
            YAHOO.util.Dom.setStyle(this.headerColumns[i], 'width', (r.right - r.left - 1) + 'px');
        }
    }

    region = YAHOO.util.Dom.getRegion(this.container);
    this.width = region.right - region.left;

    if (this.scrollH) {
        YAHOO.util.Event.addListener(this.bodyContainer, 'scroll', ORBEON.widgets.datatable.scrollHandler, this, true);
    }

    this.colResizers = [];
    for (var j=0; j < this.headerColumns.length; j++) {
        var childDiv = YAHOO.util.Selector.query('div', this.headerColumns[j], true);
        var region = YAHOO.util.Dom.getRegion(this.headerColumns[j]);
        var width = (region.right - region.left - 19) + 'px';
        var style = childDiv.style;
        style.width = width;
        var styles = [style];
        for (var k=0; k < this.bodyRows.length; k++) {
            row=this.bodyRows[k];
            if (row.cells.length > j && !YAHOO.util.Dom.hasClass(row, 'xforms-repeat-template')) {
                style = YAHOO.util.Selector.query('div', row.cells[j], true).style;
                style.width = width;
                styles[styles.length] = style;
            }
        }

        if (YAHOO.util.Dom.hasClass(this.headerColumns[j], 'yui-dt-resizeable')) {
            this.colResizers[this.colResizers.length] = new ORBEON.widgets.datatable.colResizer(j, this.headerColumns[j], styles, this);
        }
    }
}


ORBEON.widgets.datatable.prototype.adjustWidth = function (deltaX, index) {
    if (! this.hasFixedWidthContainer) {
        this.width += deltaX;
        YAHOO.util.Dom.setStyle(this.container, 'width', this.width + 'px');
        YAHOO.util.Dom.setStyle(this.headerContainer, 'width', this.width + 'px');
        if (this.headBodySplit) {
            YAHOO.util.Dom.setStyle(this.bodyContainer, 'width', this.width + 'px');
        }
    }
    if (! this.hasFixedWidthTable) {
        this.tableWidth += deltaX;
        YAHOO.util.Dom.setStyle(this.header, 'width', this.tableWidth + 'px');
        if (this.headBodySplit) {
            YAHOO.util.Dom.setStyle(this.table, 'width', this.tableWidth + 'px');
        }
    }
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

ORBEON.widgets.datatable.utils.freezeWidth = function (elt) {
    region = YAHOO.util.Dom.getRegion(elt);
    YAHOO.util.Dom.setStyle(elt, 'width', (region.right - region.left) + 'px');
}

/**
 * Implementation of datatable.colResizer constructor. Creates the YAHOO.util.DD object.
 *
 * @method ORBEON.widgets.datatable.colResizer
 * @param col {DOM Element} The th DOM element.
 */
ORBEON.widgets.datatable.colResizer = function (index, th, styles, datatable) {
    this.index = index;
    this.th = th;
    this.styles = styles;

    this.datatable = datatable;

    this.resizerliner = document.createElement('div');
    YAHOO.util.Dom.addClass(this.resizerliner, 'yui-dt-resizerliner');

    this.liner = YAHOO.util.Selector.query('div', this.th, true);

    this.th.replaceChild(this.resizerliner, this.liner);

    this.resizerliner.appendChild(this.liner);

    this.resizer = document.createElement('div');
    YAHOO.util.Dom.addClass(this.resizer, 'yui-dt-resizer');
    this.resizer.style.left='auto';
    this.resizer.style.right='0pt';
    this.resizer.style.top='auto';
    this.resizer.style.bottom='0pt';
    this.resizer.style.height='25px';
    this.resizerliner.appendChild(this.resizer);

  
    this.dd = new YAHOO.util.DD(this.resizer);
    this.dd.setYConstraint(0, 0);
  
    var colRegion = YAHOO.util.Dom.getRegion(this.th);
    var X = YAHOO.util.Dom.getX(this.resizer);
    this.delta = colRegion.right - X;

    this.dd.on('mouseDownEvent', ORBEON.widgets.datatable.colResizer.prototype.mouseDown, this, true);
    this.dd.on('dragEvent', ORBEON.widgets.datatable.colResizer.prototype.drag, this, true);
}

/**
 * Implementation of datatable.colResizer.mouseDown event handler. Stores the positions as a D&D action might start.
 *
 * @method ORBEON.widgets.datatable.colResizer.mouseDown
 * @param ev {Event}.
 */
ORBEON.widgets.datatable.colResizer.prototype.mouseDown = function (ev) {
    var thRegion = YAHOO.util.Dom.getRegion(this.th);
    this.thWidth = thRegion.right - thRegion.left;
    this.resizerX = YAHOO.util.Dom.getX(this.resizer);
}

/**
 * Implementation of datatable.colResizer.drag event handler. Adjusts the column size.
 *
 * @method ORBEON.widgets.datatable.colResizer.drag
 * @param ev {Event}.
 */
ORBEON.widgets.datatable.colResizer.prototype.drag = function (ev) {
    // Calculate the new length
    var newX = YAHOO.util.Dom.getX(this.resizer);
    this.datatable.table.style.display = 'none';
    var deltaX = newX -this.resizerX;
    this.datatable.adjustWidth(deltaX, this.index);
    this.resizerX = newX;
    var width = this.thWidth + deltaX;
    var widthStyle = (width - 19 )+ 'px';
    // If different and non null, try to set it
    if (width > 0 && width != this.thWidth) {
        for (var i= 0; i < this.styles.length; i++) {
            this.styles[i].width = widthStyle;
        }
    }
    // If the width adjustement has failed, the column is probably
    // too small, move the handle back to its normal position
    this.datatable.table.style.display = '';
    var thRegion = YAHOO.util.Dom.getRegion(this.th);
    YAHOO.util.Dom.setX(this.resizer, thRegion.right - this.delta);
    this.thWidth = thRegion.right - thRegion.left;
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
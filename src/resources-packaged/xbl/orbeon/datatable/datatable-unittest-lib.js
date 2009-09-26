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

ORBEON.widgets.datatable.unittests_lib = {


    accordionAccessMethod: "api",

    isOpenAccordionCase: function(targetId) {
        var dd = YAHOO.util.Dom.get('my-accordion$d-' + targetId);
        return YAHOO.util.Dom.hasClass(dd, 'a-m-d-expand');
    },

    toggleAccordionCase: function (targetId) {
        var dt = YAHOO.util.Dom.get('my-accordion$t-' + targetId);
        YAHOO.util.UserAction.click(dt, {clientX: 1});
    },

    openAccordionCase: function (testCase, targetId, callback) {
        if (this.accordionAccessMethod == 'css') {

            var dt = YAHOO.util.Dom.get('my-accordion$t-' + targetId);
            var dd = YAHOO.util.Dom.get('my-accordion$d-' + targetId);
            YAHOO.util.Dom.addClass(dt, 'a-m-d-expand');
            YAHOO.util.Dom.addClass(dd, 'a-m-d-expand');

        } else if (this.accordionAccessMethod == 'api') {

            var dl = YAHOO.util.Dom.get('my-accordion$dl');
            var dt = YAHOO.util.Dom.get('my-accordion$t-' + targetId);
            var dd = YAHOO.util.Dom.get('my-accordion$d-' + targetId);
            AccordionMenu.expandCase(dl, dt, dd);

        } else /* click */ {

            if (!this.isOpenAccordionCase(targetId)) {
                this.toggleAccordionCase(targetId);
            }
        }

        // Check if the action has been done and call back
        if (this.isOpenAccordionCase(targetId)) {
            if (callback) {
                callback.call();
            }
        } else {
            var thiss = this;
            testCase.wait(function() {
                thiss.openAccordionCase(testCase, targetId, callback);
            }, 10);
        }
    },

    closeAccordionCase: function (testCase, targetId, callback) {
        if (this.accordionAccessMethod == 'css') {

            var dt = YAHOO.util.Dom.get('my-accordion$t-' + targetId);
            var dd = YAHOO.util.Dom.get('my-accordion$d-' + targetId);
            YAHOO.util.Dom.removeClass(dt, 'a-m-d-expand');
            YAHOO.util.Dom.removeClass(dd, 'a-m-d-expand');

        } else if (this.accordionAccessMethod == 'api') {

            var dl = YAHOO.util.Dom.get('my-accordion$dl');
            var dt = YAHOO.util.Dom.get('my-accordion$t-' + targetId);
            var dd = YAHOO.util.Dom.get('my-accordion$d-' + targetId);
            AccordionMenu.collapseCase(dl, dt, dd);

        } else /* click */ {
            if (this.isOpenAccordionCase(targetId)) {
                this.toggleAccordionCase(targetId);
            }
        }

        // Check if the action has been done and call back
        if (!this.isOpenAccordionCase(targetId)) {
            if (callback) {
                callback.call();
            }
        } else {
            var thiss = this;
            testCase.wait(function() {
                thiss.closeAccordionCase(testCase, targetId, callback);
            }, 10);
        }
    },

    checkCellWidth: function(cell) {
        var resizerliner = ORBEON.widgets.datatable.utils.getFirstChildByTagAndClassName(cell, 'div', 'yui-dt-resizerliner');
        var liner;
        if (resizerliner == null) {
            var liner = ORBEON.widgets.datatable.utils.getFirstChildByTagAndClassName(cell, 'div', 'yui-dt-liner');
        } else {
            var liner = ORBEON.widgets.datatable.utils.getFirstChildByTagAndClassName(resizerliner, 'div', 'yui-dt-liner');
        }
        var cssWidth = YAHOO.util.Dom.getStyle(liner, 'width');
        var actualWidth = (liner.clientWidth - 20) + "px";
        YAHOO.util.Assert.areEqual(cssWidth, actualWidth, 'CSS (' + cssWidth + ') and actual (' + liner.clientWidth + 'px) width should differ by exactly 20 px.');
    },

    checkRowWidth: function(row) {
        for (var icol = 0; icol < row.cells.length; icol++) {
            var cell = row.cells[icol];
            this.checkCellWidth(cell);
        }
    },

    checkTableAndContainerWidths: function(table) {
        var tableWidth = YAHOO.util.Dom.getStyle(table, 'width');
        var tableWidthValue = parseInt(tableWidth.substr(0, tableWidth.length - 2)) ;
        var headerContainerWidth = YAHOO.util.Dom.getStyle(table.parentNode, 'width');
        var mainContainerWidth = YAHOO.util.Dom.getStyle(table.parentNode.parentNode, 'width');
        if (headerContainerWidth == 'auto')  {
            headerContainerWidth = tableWidth;
        }
        var mainContainerWidthValue = parseInt(mainContainerWidth.substr(0, mainContainerWidth.length - 2)) ;

        YAHOO.util.Assert.areEqual(tableWidthValue + 2, mainContainerWidthValue, 'Table (' + tableWidth + ') and main container (' + mainContainerWidth + ') widths should differ by 2 pixels.');
        YAHOO.util.Assert.areEqual(headerContainerWidth, tableWidth, 'Header (' + headerContainerWidth + ') and table (' + tableWidth + ') widths should be equal.');
    },

    resizeColumn: function(th, offset, step) {
        var resizerliner = ORBEON.widgets.datatable.utils.getFirstChildByTagAndClassName(th, 'div', 'yui-dt-resizerliner');
        var resizer = ORBEON.widgets.datatable.utils.getFirstChildByTagAndClassName(resizerliner, 'div', 'yui-dt-resizer');
        var region = YAHOO.util.Region.getRegion(resizer);
        YAHOO.util.UserAction.mousedown(resizer, {clientX: region.right, clientY: region.top});
        var x;
        if (step == undefined) {
            step = offset;
        } else if (step * offset < 0) {
            step = -step;
        }
        for (x = region.right; (offset < 0 && x >= region.right + offset) || (offset > 0 && x <= region.right + offset); x = x + step) {
            YAHOO.util.UserAction.mousemove(resizer, {clientX: x, clientY: region.top});
        }
        YAHOO.util.UserAction.mouseup(resizer, {clientX: x, clientY: region.top});
        return x - region.right;
    },

    checkHorizontalScrollbar: function (elt) {
        YAHOO.util.Assert.isTrue(elt.clientHeight + 15 < elt.offsetHeight, 'Element has no horizontal scroll bar (clientHeight: ' + elt.clientHeight + ', offsetHeight: ' + elt.offsetHeight + ')');
    },

    checkEmbeddedWidthAndHeight: function (elt, parentWidth, parentHeight) {
        var region =  YAHOO.util.Region.getRegion(elt);
        var width;
        var height;
        if (region != null && region.right != null && region.left != null) {
            width = region.right - region.left;
            height = region.bottom - region.top;
        } else {
            width = parentWidth;
            height = parentHeight ;
        }
  
        if (parentWidth != undefined) {
            YAHOO.util.Assert.isTrue(parentWidth >= width, 'Node ' + elt.nodeName + '.' + elt.className + ', width (' + width + ") is larger than its parent's " + elt.parentNode.nodeName + '.' + elt.parentNode.className + " width (" + parentWidth + ')');
        }
        if (parentHeight != undefined) {
            YAHOO.util.Assert.isTrue(parentHeight >= height, 'Node ' + elt.nodeName + '.' + elt.className + ', height (' + height + ") is larger than its parent's " + elt.parentNode.nodeName + '.' + elt.parentNode.className + " height (" + parentHeight + ')');
        }
        var children = elt.childNodes;
        for (var i = 0; i < children.length; i++) {
            var child = children[i];
            if (child.nodeType == 1) {
                this.checkEmbeddedWidthAndHeight(child, width, height);
            }
        }
    },

    EOS: null
}
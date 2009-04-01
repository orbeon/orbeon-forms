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
 *  JavaScript implementation for the datatable component
 *
 */



ORBEON.widgets.datatable = function (originalContent, position) {
    
    // ORBEON.widgets.datatable extends YAHOO.widget.DataTable
    
    this.position = position;

    // Transforms a div with an embedded table into a YUI datatable.
            
    // Define a few variables to access the table
    
    this.originalContent = originalContent;
    this.widget = originalContent.parentNode.parentNode;
    this.id= this.widget.getAttribute('id');
            
    this.originalTable = originalContent.getElementsByTagName('table')[0];
    
    this.tableContent = this.originalTable.innerHTML;
    
    var originalTHead = this.originalTable.getElementsByTagName('thead')[0];
    var originalHeader = originalTHead.getElementsByTagName('tr')[0];
    var originalHeaderCells = originalHeader.getElementsByTagName('th');
    var originalTBody = this.originalTable.getElementsByTagName('tbody')[0];
    var originalFirstRow;
    var originalTrs = originalTBody.getElementsByTagName('tr');
    var originalFirstRowTds;
                    
    // find the first row with cells (rows without cells are orbeon markers)
    for (var i = 0; i < originalTrs.length; i++) {
        var originalFirstRow = originalTrs[i];
        originalFirstRowTds = originalFirstRow.getElementsByTagName('td');
        if (originalFirstRowTds.length > 0)
        break;
    }


    // We need to copy the original content since it will be totally rewritten by the YUI
    
    var table = this.simplifyTable();
    this.simplifiedContent = table.parentNode; 
    
    originalContent.parentNode.insertBefore(this.simplifiedContent, originalContent);
    originalContent.setAttribute('class', 'xforms-data')
    //YAHOO.util.Dom.setStyle(originalContent, 'display', 'none');
    
    //  Now we can create the datatable based on the simplified content
    
    this.orbeonDataSource = new YAHOO.util.DataSource(table);
    this.orbeonDataSource.responseType = YAHOO.util.DataSource.TYPE_HTMLTABLE;
    this.orbeonDataSource.responseSchema = {
        fields:[]
    };
    
    this.orbeonColumnDefs =[];

    // Now, build the structures needed for the datatable from the
    // original table
    //
    for (var i = 0; i < originalHeaderCells.length; i++) {
        var td = originalFirstRowTds[i];
        var header = originalHeaderCells[i];
        var field = {};
        var column = {};
        field['key'] = ORBEON.widgets.datatable.getDeepStringValue(header);
        column['key'] = field['key'];
        column['formatter'] = ORBEON.widgets.datatable.copyFormatter;
        if (ORBEON.widgets.datatable.isTdNumber(td)) {
            field['parser'] = 'number';
        } else if (ORBEON.widgets.datatable.isTdDate(td)) {
            field['parser'] = 'date';
        } 
        var span = td.getElementsByTagName('span')[0];
        if (ORBEON.util.Dom.hasClass(span, 'widget-sortable')) {
            column['sortable'] = true;
        }
        if (ORBEON.util.Dom.hasClass(span, 'widget-resizeable')) {
            column['resizeable'] = true;
        }
        //TODO: support more datatypes
        this.orbeonDataSource.responseSchema.fields[i] = field;
        this.orbeonColumnDefs[i] = column;
    }
        
    // Create the config
    
    this.orbeonConfig = {};
    
    if (ORBEON.xforms.Document.getValue(this.id + '$scrollable') == 'true') {
       this.orbeonConfig['scrollable'] = true;
    }
    if (ORBEON.xforms.Document.getValue(this.id + '$height')  != '' ) {
        this.orbeonConfig['height'] = ORBEON.xforms.Document.getValue(this.id + '$height');
    }
    if (ORBEON.xforms.Document.getValue(this.id + '$width')  != '' ) {
        this.orbeonConfig['width'] = ORBEON.xforms.Document.getValue(this.id + '$width');
    }
    if (ORBEON.xforms.Document.getValue(this.id + '$paginated') == 'true') {
       this.orbeonConfig['paginator'] = new YAHOO.widget.Paginator( {
        rowsPerPage    : parseInt(ORBEON.xforms.Document.getValue(this.id + '$rowsPerPage'))
       } )
    }
    if (ORBEON.xforms.Document.getValue(this.id + '$sortedByKey')  != '' ) {
        this.orbeonConfig['sortedBy'] = { key : ORBEON.xforms.Document.getValue(this.id + '$sortedByKey')} ;
        if (ORBEON.xforms.Document.getValue(this.id + '$sortedByDir')  == 'descending' ) {
          this.orbeonConfig['sortedBy']['dir'] = YAHOO.widget.DataTable.CLASS_DESC;
        } else {
          this.orbeonConfig['sortedBy']['dir'] = YAHOO.widget.DataTable.CLASS_ASC;
        }
    }


    // And call the constructor
    
    if (this.orbeonConfig['scrollable'] ) {
        YAHOO.widget.ScrollingDataTable.call(this, this.simplifiedContent, this.orbeonColumnDefs, this.orbeonDataSource, this.orbeonConfig);   
    } else {
        YAHOO.widget.DataTable.call(this, this.simplifiedContent, this.orbeonColumnDefs, this.orbeonDataSource, this.orbeonConfig);   
    }
    
    YAHOO.lang.later(1000, this, ORBEON.widgets.datatable.checkUpdates, [], true);
    
    // Enables single-mode row selection
    this.set("selectionMode","single");
    this.subscribe("rowClickEvent", function(oArgs) {
        this.unselectAllRows();
        var row = oArgs.target;
        this.selectRow(oArgs.target);
        var clickEvent = document.createEvent('MouseEvents');
        clickEvent.initEvent(
           'click'     // event type
           ,true     // can bubble?
           ,true      // cancelable?
          );
         this.originalDataRows[this.getRowId(row)].dispatchEvent(clickEvent);
      }
    );

    
}

YAHOO.lang.extend(ORBEON.widgets.datatable, YAHOO.widget.ScrollingDataTable);


// Methods

ORBEON.widgets.datatable.prototype.getRowId = function (row) {
    var id = row.getAttribute('id');
    // the row id attribute has the form 'yui-recNNN' where NNN is the record id
    return parseInt(id.substr(7)) - this.recordCountOffset;
}

ORBEON.widgets.datatable.prototype.simplifyTable = function () {

    this.simplifiedContent = this.originalContent.cloneNode(true);
    this.simplifiedContent.setAttribute('id', this.originalContent.getAttribute('id') + '_copy');
    var table = this.simplifiedContent.getElementsByTagName('table')[0];
    table.setAttribute('id', table.getAttribute('id') + '-copy');
    
    // We also need to filter out the rows added by OrbeonForms and adjust the content of the cells
    
    var tbody = table.getElementsByTagName('tbody')[0];
    var trs = tbody.getElementsByTagName('tr');
    
    for (var i = trs.length - 1; i >= 0; i--) {
        var tr = trs[i];
        var tds = tr.getElementsByTagName('td');
        if (tds.length == 0 || ORBEON.util.Dom.hasClass(tr, 'xforms-repeat-template')) {
            tr.parentNode.removeChild(tr);
        } else {
            for (var j = tds.length - 1; j >= 0; j--) {
                var td = tds[j];
                 // Remove the markup
                 ORBEON.util.Dom.setStringValue(td, ORBEON.widgets.datatable.getDeepStringValue(td));
            }
        }
    }
    
    // Save the original data rows in the datable
    
    var originalTBody = this.originalTable.getElementsByTagName('tbody')[0];
    var originalTrs = originalTBody.getElementsByTagName('tr');

    
    this.originalDataRows = [];
    for (var i = 0; i <  originalTrs.length; i++) {
        var tr = originalTrs[i];
        var tds = tr.getElementsByTagName('td');
        if (tds.length > 0 && ! ORBEON.util.Dom.hasClass(tr, 'xforms-repeat-template')) {
            this.originalDataRows[this.originalDataRows.length] = tr;
         }
    }
    
    this.recordCountOffset = undefined;
    
    return table;
}

// Class functions

ORBEON.widgets.datatable.checkUpdates = function () {

    if (this.originalTable.innerHTML != this.tableContent) {
         var state = this.getState();
         var recordCountOffset = this.recordCountOffset;
         var savScrollTop = this._elBdContainer.scrollTop;
         var savScrollLeft = this._elBdContainer.scrollLeft;
         if (state.pagination != null) {
             var page = state.pagination.paginator.getCurrentPage();
         }
         this.tableContent = this.originalTable.innerHTML;
         this.initializeTable();
         var parsed = this.getDataSource().parseHTMLTableData('', this.simplifyTable());
         this.addRows( parsed['results'], 0);
         this.render();
         if (state.sortedBy != null) {
             this.sortColumn(this.getColumn(state.sortedBy.key), state.sortedBy.dir);
         }
         var newState = this.getState();
         if (newState.pagination != null) {
              newState.pagination.paginator.setPage(page);
         }
         if (state.selectedRows.length > 0) {
             var index = parseInt(state.selectedRows[0].substr(7)) + this.recordCountOffset - recordCountOffset;
             this.selectRow('yui-rec' + index );
         }
         this._elBdContainer.scrollTop = savScrollTop;
         this._elBdContainer.scrollLeft = savScrollLeft;
    } 

}

ORBEON.widgets.datatable.getDeepStringValue = function(element) {
    // Get the string value of an element recursing
    // over all its descendants.
    var result = "";
    for (var i = 0; i < element.childNodes.length; i++) {
        var child = element.childNodes[i];
        if (child.nodeType == TEXT_TYPE) {
            result += child.nodeValue;
        } else if (child.nodeType == ELEMENT_TYPE) {
            result += ORBEON.widgets.datatable.getDeepStringValue(child);
        }
    }
    return result;
}

ORBEON.widgets.datatable.isTdNumber = function(td) {
    // Check if a cell contains a number
    var span = td.getElementsByTagName('span')[0];
    return ORBEON.util.Dom.hasClass(span, 'xforms-type-integer');
}
            
ORBEON.widgets.datatable.isTdDate = function(td) {
    // Check if a cell contains a date
    var span = td.getElementsByTagName('span')[0];
    return ORBEON.util.Dom.hasClass(span, 'xforms-type-date') || ORBEON.util.Dom.hasClass(span, 'xforms-type-dateTime');
}
            
ORBEON.widgets.datatable.copyFormatter = function(elCell, oRecord, oColumn, oData) {
    // This formatter is a hack!
    // All it does is to restore the orignal markup that was in the table.
    if (this.recordCountOffset == undefined ) {
        // This is a hack: record counts appear to be global to all the different datatable and we need to memorize 
        // the offset for each table!
        this.recordCountOffset = oRecord.getCount();
    }
    elCell.innerHTML = this.originalDataRows[oRecord.getCount() - this.recordCountOffset].getElementsByTagName('td')[oColumn.getIndex()].innerHTML; 
    ORBEON.widgets.datatable.removeIdAttributes(elCell);
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
    
ORBEON.widgets.datatable.init = function() {
    // Transforms all the datatables in a document
    var datatables = YAHOO.util.Dom.getElementsByClassName('data-table', 'div');
    for (var i=0; i < datatables.length; i++) {
        new ORBEON.widgets.datatable(datatables[i], i);
    }
}

YAHOO.util.Event.onDOMReady(ORBEON.widgets.datatable.init);
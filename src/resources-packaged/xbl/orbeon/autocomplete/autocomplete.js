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
(function() {
    var Dom = YAHOO.util.Dom;
    var Event = YAHOO.util.Event;
    var Lang = YAHOO.lang;
    var Document = ORBEON.xforms.Document;

    /**
     * Singleton with mapping control ID to control object
     */
    var controls = {};

    /**
     * AutoComplete object constructor
     */
    ORBEON.widget.AutoComplete = function(element) { this.init(element); }
    ORBEON.widget.AutoComplete.prototype = {

        /**
         * Attributes
         */
        element: null,              // Div containing this control
        dynamicItemset: null,       // Do we need to wait for the itemset to change before we update the auto-complete?
        yuiAutoComplete: null,      // YUI object for the auto-complete

        /**
         * Constructor
         */
        init: function(element) {
            var autoComplete = this;

            autoComplete.element = element;

            var inputSpan = Dom.getElementsByClassName("fr-autocomplete-input", null, element)[0];
            var inputField = Dom.getChildren(inputSpan)[0];
            var yuiDiv = Dom.getElementsByClassName("fr-autocomplete-yui-div", null, element)[0];
            YAHOO.util.Dom.generateId(yuiDiv); // Generate ID dynamically as our implementation of XBL doesn't rewrite IDs on HTML
            var valueSelectedButton = Dom.getElementsByClassName("fr-autocomplete-value-selected", null, element)[0];

            // Build data source
            var dataSource;
            if (autoComplete.isDynamicItemset()) {
                // If the itemset changes dynamically, update list when we have response to an Ajax request
                dataSource = autoComplete.buildNullDataSource();
                ORBEON.xforms.Events.ajaxResponseProcessedEvent.subscribe(function() {
                    // If this YUI auto-complete is focused (otherwise don't bother updating the list)
                    if (autoComplete.yuiAutoComplete.isFocused()) {
                        // Get new list of values
                        var query = inputField.value;
                        var newList = autoComplete.getCurrentValues(query);

                        // Call YUI code to update list
                        if (newList.length != 1 || newList[0] != query) {
                            autoComplete.yuiAutoComplete._populateList(query, { results: newList }, { query: query });
                        }
                    }
                });
            } else {
                // Simply get
                dataSource = new YAHOO.util.FunctionDataSource(function(query) {
                    return autoComplete.getCurrentValues(query);
                });
            }

            // Create YUI auto-complete object
            autoComplete.yuiAutoComplete = new YAHOO.widget.AutoComplete(inputField.id, yuiDiv.id, dataSource);
            // Use iframe method for IE6/7
            if (YAHOO.env.ua.ie != 0 && YAHOO.env.ua.ie <= 7)
                autoComplete.yuiAutoComplete.useIFrame = true;
            autoComplete.yuiAutoComplete.animVert = false;

            // Set maximum number of items displayed
            var maxResultsDisplayedOutput = Dom.getElementsByClassName("fr-max-results-displayed", null, autoComplete.element)[0];
            var maxResultsDisplayed = Document.getValue(maxResultsDisplayedOutput.id);
            if (maxResultsDisplayed == "") maxResultsDisplayed = 10;
            autoComplete.yuiAutoComplete.maxResultsDisplayed = parseInt(maxResultsDisplayed);

            // When a new value is selected, set the value of the XForms input control
            // We are doing this because when a value is set by clicking on it with the mouse, a change event is
            // dispatched to the input, but when it arrives the value of the input hasn't changed yet.
            autoComplete.yuiAutoComplete.itemSelectEvent.subscribe(function(type, args) {
                var selectedValue = args[2][0];
                Document.setValue(inputSpan.id, selectedValue);
                valueSelectedButton.click();
            });
        },

        /**
         * Creates a data source that never returns anything
         */
        buildNullDataSource: function() {
            var autoComplete = this;

            var WaitAjaxResponseDataSource = function(oLiveData, oConfigs) {
                this.dataType = YAHOO.util.DataSourceBase.TYPE_JSFUNCTION;
                oLiveData = oLiveData || function() {};
                this.constructor.superclass.constructor.call(this, oLiveData, oConfigs);
            };

            Lang.extend(WaitAjaxResponseDataSource, YAHOO.util.DataSourceBase, {
                makeConnection : function(oRequest, oCallback, oCaller) {
                    var dataSource = this;
                    var tId = YAHOO.util.DataSourceBase._nTransactionId++;
                    dataSource.fireEvent("requestEvent", {tId:tId,request:oRequest,callback:oCallback,caller:oCaller});
                    dataSource.responseType = YAHOO.util.DataSourceBase.TYPE_JSARRAY;
                    return tId;
                }
            });

            return new WaitAjaxResponseDataSource(function(query) { return autoComplete.getCurrentValues(query); });
        },

        /**
         * Returns the list of values currently in the itemset
         */
        isDynamicItemset: function() {
            var autoComplete = this;
            if (autoComplete.dynamicItemset === null) {
                var output = Dom.getElementsByClassName("fr-autocomplete-dynamic-itemset", null, autoComplete.element)[0];
                autoComplete.dynamicItemset = Document.getValue(output.id) == "true";
            }
            return autoComplete.dynamicItemset;
        },

        /**
         * Returns the content the values from the select1
         */
        getCurrentValues: function(query) {
            var autoComplete = this;
            var result = [];

            // Look again for the element, as on IE the <select> is recreated when the itemset changes, and so can't be cached
            var select1Element = autoComplete.element.getElementsByTagName("select")[0];
            var options = select1Element.options;
            if (query != "") {
                query = query.toLowerCase();
                for (var optionIndex = 0; optionIndex < options.length; optionIndex++) {
                    var option = options[optionIndex];
                    // We only do filtering for the static itemset mode
                    if (autoComplete.isDynamicItemset() || option.text.toLowerCase().indexOf(query) == 0)
                        result[result.length] = option.text;
                }
            }
            return result;
        }
    };

    ORBEON.widget.AutoComplete.containerXFormsEnabled = function() {
        var container = this;
        var controlID = container.parentNode.id;
        if (!Lang.isObject(controls[controlID])) {
            controls[controlID] = new ORBEON.widget.AutoComplete(container.parentNode);
        }
    };
})();


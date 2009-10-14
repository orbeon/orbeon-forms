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
    ORBEON.widget.AutoComplete = function(element) { this.init(element); };
    ORBEON.widget.AutoComplete.prototype = {

        /**
         * Attributes
         */
        element: null,              // Div containing this control
        dynamicItemset: null,       // Do we need to wait for the itemset to change before we update the auto-complete?
        yuiAutoComplete: null,      // YUI object for the auto-complete
        searchControl: null,        // xforms:input control in which users type
        searchField: null,          // Form field in which users type
        externalValueInput : null,  // xforms:input containing the external value
        lastSuggestionList: [],     // List is saved so we don't show the same when the Ajax response comes back
        justMadeSelection: false,   // Reset to false when an Ajax response arrives

        /**
         * Constructor
         */
        init: function(element) {

            this.element = element;
            this.searchControl = Dom.getElementsByClassName("fr-autocomplete-search", null, element)[0];
            this.searchField = Dom.getChildren(this.searchControl)[0];
            var yuiDiv = Dom.getElementsByClassName("fr-autocomplete-yui-div", null, element)[0];
            YAHOO.util.Dom.generateId(yuiDiv); // Generate ID dynamically as our implementation of XBL doesn't rewrite IDs on HTML
            this.externalValueInput = Dom.getElementsByClassName("fr-autocomplete-external-value", null, element)[0];

            // Build data source
            var dataSource;
            if (this.isDynamicItemset()) {
                // If the itemset changes dynamically, update list when we have response to an Ajax request
                dataSource = this.buildNullDataSource();
                ORBEON.xforms.Events.ajaxResponseProcessedEvent.subscribe(this.ajaxResponseProcessed, this, true);
            } else {
                // Simply get
                var autoComplete = this;
                dataSource = new YAHOO.util.FunctionDataSource(function(query) {
                    return autoComplete.getCurrentValues(query);
                });
            }

            // Create YUI auto-complete object
            this.yuiAutoComplete = new YAHOO.widget.AutoComplete(this.searchField.id, yuiDiv.id, dataSource);
            // Use iframe method for IE6/7
            if (YAHOO.env.ua.ie != 0 && YAHOO.env.ua.ie <= 7)
                this.yuiAutoComplete.useIFrame = true;
            this.yuiAutoComplete.animVert = false;

            // Set maximum number of items displayed
            var maxResultsDisplayedOutput = Dom.getElementsByClassName("fr-max-results-displayed", null, this.element)[0];
            var maxResultsDisplayed = Document.getValue(maxResultsDisplayedOutput.id);
            if (maxResultsDisplayed == "") maxResultsDisplayed = 10;
            this.yuiAutoComplete.maxResultsDisplayed = parseInt(maxResultsDisplayed, 10);

            // Listen on user selecting a value
            this.yuiAutoComplete.itemSelectEvent.subscribe(this.itemSelected, this, true);
            // Listen on user typing in search field
            YAHOO.util.Event.addListener(this.searchField, "keyup", this.searchFieldKeyUp, this, true);
        },

        /**
         * Called when the user selects a value from the suggestion list
         */
        itemSelected: function(type, args) {
            var itemLabel = args[2][0];
            var itemValue = args[2][1];
            this.justMadeSelection = true;
            Document.setValue(this.searchControl.id, itemLabel);
            Document.setValue(this.externalValueInput.id, itemValue);
        },

        /**
         * Called when users type in the search field.
         * If they end up typing something which shows in the suggestion list, set the external value to
         * to value of that item.
         */
        searchFieldKeyUp: function() {
            var searchFieldValue = this.searchField.value;
            var select1Element = this.element.getElementsByTagName("select")[0];
            var options = select1Element.options;
            var matchingOptionIndex = -1;
            for (var optionIndex = 0; optionIndex < options.length; optionIndex++) {
                var option = options[optionIndex];
                if (option.text == searchFieldValue) {
                    // Give up if this is not the first match
                    if (matchingOptionIndex != -1) return;
                    matchingOptionIndex = optionIndex;
                }
            }
            if (matchingOptionIndex != -1)
                Document.setValue(this.externalValueInput.id, options[matchingOptionIndex].value);
        },

        /**
         * Called when we received a response for an Ajax request, which means we may have to update the suggestion list
         */
        ajaxResponseProcessed: function() {
            // Get new list of values
            var query = this.searchField.value;
            var oldList = this.lastSuggestionList;
            var newList = this.getCurrentValues(query);

            var sameSuggestionAsBeforeAjax = function() {
                var result = newList.length == oldList.length;
                if (result) {
                    for (var listIndex = 0; listIndex < newList.length; listIndex++) {
                        if (newList[listIndex][0] != oldList[listIndex][0] || newList[listIndex][1] != oldList[listIndex][1]) {
                            result = false;
                            break;
                        }
                    };
                }
                return result;
            }.call(this);

            var doUdateSuggestionList =
                    // If the user just selected something and the response when the Ajax query comes back has the same
                    // itemset, don't update the suggestion list
                    ! (this.justMadeSelection && sameSuggestionAsBeforeAjax)
                    // Update the list only of the control has the focus, as updating the list will show the suggestion list
                    // and we only want to show the suggestion list if the user happens to be in that field
                    && this.yuiAutoComplete.isFocused();
            if (doUdateSuggestionList)
                this.yuiAutoComplete._populateList(query, { results: newList }, { query: query });
            this.justMadeSelection = false;
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

            // YUI autocomplete give us an escaped string
            query = unescape(query);
            // Look again for the element, as on IE the <select> is recreated when the itemset changes, and so can't be cached
            var select1Element = this.element.getElementsByTagName("select")[0];
            var options = select1Element.options;
            var foundExactMatch = false;
            if (query != "") {
                var queryLowerCase = query.toLowerCase();
                for (var optionIndex = 0; optionIndex < options.length; optionIndex++) {
                    var option = options[optionIndex];
                    // We only do filtering for the static itemset mode
                    if (this.isDynamicItemset() || option.text.toLowerCase().indexOf(queryLowerCase) == 0)
                        result[result.length] = [ option.text, option.value ];
                    if (option.text == query) foundExactMatch = true;

                }
            }

            // If the value in the search field is not in the itemset, set the external value to empty string
            if (! foundExactMatch)
                Document.setValue(this.externalValueInput.id, "");

            // If the result only contains one item, and its label is equal to the current value
            // Set the external value to the value of this option
            if (result.length == 1 && result[0][0] == query) {
                Document.setValue(this.externalValueInput.id, result[0][1]);
                // Don't return any suggestion
                result = [];
            }

            this.lastSuggestionList = result;
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


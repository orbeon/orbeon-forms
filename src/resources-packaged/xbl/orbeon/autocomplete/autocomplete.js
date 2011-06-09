/**
 *  Copyright (C) 2011 Orbeon, Inc.
 *
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
    var OD = ORBEON.util.Dom;
    var YD = YAHOO.util.Dom;
    var Document = ORBEON.xforms.Document;

    YAHOO.namespace("xbl.fr");
    YAHOO.xbl.fr.Autocomplete = function() {};
    ORBEON.xforms.XBL.declareClass(YAHOO.xbl.fr.Autocomplete, "xbl-fr-autocomplete");
    YAHOO.xbl.fr.Autocomplete.prototype = {

        dynamicItemset: null,       // Do we need to wait for the itemset to change before we update the auto-complete?
        yuiAutoComplete: null,      // YUI object for the auto-complete
        searchControl: null,        // xforms:input control in which users type
        searchField: null,          // Form field in which users type
        valueSelectedButton: null,  // Bridge to tell XForms users selected a value
        externalValueInput : null,  // xforms:input containing the external value
        justMadeSelection: false,   // Reset to false when an Ajax response arrives
        suggestionRequested: false, // Whether users requested the suggestion menu to open by clicking on the button

        /**
         * Constructor
         */
        init: function() {
            this.searchControl = YD.getElementsByClassName("fr-autocomplete-search", null, this.container)[0];
            this.searchField = YD.getChildren(this.searchControl)[0];
            this.valueSelectedButton = OD.getElementByTagName(YD.getElementsByClassName("fr-autocomplete-value-selected", null, this.container)[0], "button");
            var yuiDiv = YD.getElementsByClassName("fr-autocomplete-yui-div", null, this.container)[0];
            YD.generateId(yuiDiv); // Generate ID dynamically as our implementation of XBL doesn't rewrite IDs on HTML
            this.externalValueInput = YD.getElementsByClassName("fr-autocomplete-external-value", null, this.container)[0];

            // This is mostly useful for the dynamic and resources cases, but this code is also useful in static mode, allowing us
            // to open the suggestions in static mode when the button is pressed
            ORBEON.xforms.Events.ajaxResponseProcessedEvent.subscribe(_.bind(this.ajaxResponseProcessed, this));

            // Build data source
            var dataSource;
            if (this.isDynamicItemset()) {
                // If the itemset changes dynamically, update list when we have response to an Ajax request
                dataSource = this.buildNullDataSource();
            } else {
                // Simply filters on the values in the hidden dropdown; we could do this on Ajax response, but using a data source
                // allows us to show suggestions faster, without having to wait for the result of the Ajax request
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
            var maxResultsDisplayedOutput = YD.getElementsByClassName("xbl-fr-autocomplete-max-results-displayed", null, this.container)[0];
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
            this.justMadeSelection = true;
            Document.setValue(this.searchControl.id, itemLabel);
            this.valueSelectedButton.click();
        },

        /**
         * Called when users type in the search field.
         * If they end up typing something which shows in the suggestion list, set the external value to
         * to value of that item.
         */
        searchFieldKeyUp: function() {
            var searchFieldValue = this.searchField.value;
            var select1Element = this.container.getElementsByTagName("select")[0];
            var options = select1Element.options;
            var matchingOptionIndex = -1;
            var itemsCount = options.length / 2;
            for (var optionIndex = 0; optionIndex < itemsCount; optionIndex++) {
                var option = options[optionIndex];
                if (option.value == searchFieldValue) {
                    // Give up if this is not the first match
                    if (matchingOptionIndex != -1) return;
                    matchingOptionIndex = optionIndex;
                }
            }
        },

        /**
         * Called when we received a response for an Ajax request, which means we may have to update the suggestion list
         */
        ajaxResponseProcessed: function() {
            // Get new list of values
            var query = this.searchField.value;
            var newList = this.getCurrentValues(query);

            var doUpdateSuggestionList =
                this.suggestionRequested || (
                // If the user just selected something before which triggered an Ajax query, don't show the suggestion list (why?)
                ! this.justMadeSelection
                // Update the list only of the control has the focus, as updating the list will show the suggestion list
                // and we only want to show the suggestion list if the user happens to be in that field
                && ORBEON.xforms.Globals.currentFocusControlId == this.searchControl.id);
            if (doUpdateSuggestionList) {
                // Tell YUI this control has the focus; we set the focus on the input, but the YUI focus handler is sometimes called too late
                this.yuiAutoComplete._bFocused = true;
                this.yuiAutoComplete._populateList(query, { results: newList }, { query: query });
            }
            this.justMadeSelection = false;
            this.suggestionRequested = false;
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

            YAHOO.lang.extend(WaitAjaxResponseDataSource, YAHOO.util.DataSourceBase, {
                makeConnection : function(oRequest, oCallback, oCaller) {
                    var dataSource = this;
                    var tId = YAHOO.util.DataSourceBase._nTransactionId++;
                    dataSource.fireEvent("requestEvent", {tId:tId,request:oRequest,callback:oCallback,caller:oCaller});
                    dataSource.responseType = YAHOO.util.DataSourceBase.TYPE_JSARRAY;
                    return tId;
                }
            });

            return new WaitAjaxResponseDataSource(function(query) { return autoComplete.getCurrentValues(query); }, {});
        },

        /**
         * Returns the list of values currently in the itemset
         */
        isDynamicItemset: function() {
            var autoComplete = this;
            if (autoComplete.dynamicItemset === null) {
                var output = YD.getElementsByClassName("fr-autocomplete-dynamic-itemset", null, autoComplete.container)[0];
                autoComplete.dynamicItemset = Document.getValue(output.id) == "true";
            }
            return autoComplete.dynamicItemset;
        },

        /**
         * Returns the content the values from the select1
         */
        getCurrentValues: function(query) {
            var result = [];

            // YUI autocomplete give us an escaped string
            query = unescape(query);
            // Look again for the element, as on IE the <select> is recreated when the itemset changes, and so can't be cached
            var select1Container = YD.getElementsByClassName("fr-autocomplete-select1", null, this.container)[0];
            var select1Element = OD.getElementByTagName(select1Container, "select");
            var options = select1Element.options;

            if (this.suggestionRequested        // Always build result if asked by users by clicking on button
                    || query != "" ) {          // Don't build result if user deleted everything from the field
                var queryLowerCase = query.toLowerCase();
                var itemsCount = options.length / 2;
                for (var optionIndex = 0; optionIndex < itemsCount; optionIndex++) {
                    var option = options[optionIndex];
                    // We only do filtering for the static itemset mode
                    if (this.isDynamicItemset() || option.value.toLowerCase().indexOf(queryLowerCase) == 0 || query == "")
                        result[result.length] = [ option.value, options[itemsCount + optionIndex].value ];
                }
            }

            // If the result only contains one item, and its label is equal to the current value, don't show suggestions
            // Set the external value to the value of this option
            if (result.length == 1 && result[0][0] == query)
                result = [];

            return result;
        }
    };
})();
/**
 * Copyright (C) 2010 Orbeon, Inc.
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
(function() {

    var $ = ORBEON.jQuery;
    YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

        name: "Autocomplete",

        /**
         * Runs the function for both the static and dynamic cases.
         *
         * @param f     function(staticDynamic, continuation)
         *              @param staticDynamicResource    Either "static", "dynamic", or "resource"
         *              @param continuation             Function with no parameter, but the caller needs to make sure
         *                                              to keep the this
         */
        runForStaticDynamicResource: function(f) {
            f.call(this, "static", function() {
                f.call(this, "dynamic", function(){
                    f.call(this, "resource", function(){});
                });
            });
        },

        /**
         * Runs the function on all the active lis in the suggestion list.
         *
         * @param       function(li)
         *              @param li   Active list item
         */
        runOnLis: function(staticDynamicResource, f) {
            var autocompleteDiv = YAHOO.util.Dom.get(staticDynamicResource + "-autocomplete");
            var autocompleteSuggestions = YAHOO.util.Dom.getElementsByClassName("fr-autocomplete-yui-div", null, autocompleteDiv)[0];
            var lis = autocompleteSuggestions.getElementsByTagName("li");
            for (liIndex = 0; liIndex < lis.length; liIndex++) {
                var li = lis[liIndex];
                if (li.style.display != "none")
                    f.call(this, li);
            }
        },

        /**
         * Simulates the user typing a value in a search field.
         */
        simulateTypeInField: function(staticDynamicResource, newValue) {
            var searchInput = YAHOO.util.Dom.get(staticDynamicResource + "-autocomplete$search").getElementsByTagName("input")[0];
            searchInput.focus();
            searchInput.value = newValue;
            YAHOO.util.UserAction.keyup(searchInput);
        },

        simulateClickItem: function(staticDynamicResource, position) {
            var liIndex = 0;
            this.runOnLis(staticDynamicResource, function(li) {
                if (liIndex == position) {
                    YAHOO.util.UserAction.click(li);
                }
                liIndex++;
            });
        },

        /**
         * Users focusing out of the field will update the external value.
         */
        simulateFocusOut: function() {
            document.getElementById("focus-input").getElementsByTagName("input")[0].focus();
        },

        simulateAutocompleteClick: function(staticDynamicResource) {
            var autocomplete = YAHOO.util.Dom.get(staticDynamicResource + "-autocomplete");
            var button = autocomplete.getElementsByTagName("button")[0];
            button.focus();
            button.click();
        },

        /**
         * Checks that the external value and label is what we expect it to be.
         */
        checkExternal: function(staticDynamicResource, expectedValue, expectedLabel, message) {
            var expected = { 'value': expectedValue, 'label': expectedLabel };
            _.each(_.keys(expected), function(key) {
                var doAssert =
                    /* Value for label is optional */
                    expected[key] != null
                    /* Don't check label for the static mode, as it isn't supported (and doesn't make sense) */
                    && !(staticDynamicResource == 'static' && key == 'label');
                if (doAssert) {
                    var value = ORBEON.xforms.Document.getValue(staticDynamicResource + "-output-" + key);
                    YAHOO.util.Assert.areEqual(expected[key], value, staticDynamicResource + " " + key + " " +
                        (_.isUndefined(message) ? "" : " - " + message));
                }
            });
        },

        /**
         * Checks that the value in the search field is what we expect it to be.
         */
        checkSearchValue: function(staticDynamicResource, expectedValue, message) {
            var searchValue = ORBEON.xforms.Document.getValue(staticDynamicResource + "-autocomplete$search");
            YAHOO.util.Assert.areEqual(expectedValue, searchValue, staticDynamicResource +
                (_.isUndefined(message) ? "" : " - " + message));
        },

        /**
         * Checks that the items we get in the suggestion list are the one we expect.
         */
        checkSuggestions: function(staticDynamicResource, expectedValues) {
            this.runOnLis(staticDynamicResource, function(li) {
                YAHOO.util.Assert.areEqual(expectedValues[liIndex], li.innerHTML, staticDynamicResource + " at index " + liIndex);
            });
        },

        checkSuggestionCount: function(staticDynamicResource, expectedCount) {
            var actualCount = 0;
            this.runOnLis(staticDynamicResource, function(li) {
                actualCount++;
            });
            YAHOO.util.Assert.areEqual(actualCount, expectedCount, staticDynamicResource + " suggestions shows");
        },

        checkSuggestionOpen: function(staticDynamicResource, isOpen, message) {
            var autocomplete = document.getElementById(staticDynamicResource + "-autocomplete");
            YAHOO.util.Assert.areEqual(isOpen, $(autocomplete).find('.yui-ac-content').css('display') == 'block',
                    staticDynamicResource + ". " + message);
        },

        /**
         * This test needs to be first, as we test that setting the label to Canada on xforms-ready by dispatching
         * the fr-set-label event, we indeed get the value 'ca' in the node bound to the control.
         */
        testSetLabelOnXFormsReady: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                // Nothing is done in JS, but the fr-set-label we dispatch on xforms-ready will create some Ajax traffic
            }, function() {
                this.runForStaticDynamicResource(function(staticDynamicResource, continuation) {
                    this.checkExternal(staticDynamicResource, "ca", "Canada");
                    this.checkSearchValue(staticDynamicResource, "Canada");
                    continuation.call(this);
                });
            });
        },

        /**
         * Test that tabindex attribute is copied on the visible input field.
         */
        testHasTabIndex: function() {
            // On the static autocomplete we have tabindex="1"
            var staticVisibleInput = YAHOO.util.Dom.get("static-autocomplete").getElementsByTagName("input")[0];
            YAHOO.util.Assert.areEqual("1", ORBEON.util.Dom.getAttribute(staticVisibleInput, "tabindex"));
            // On the dynamic autocomplete we don't have a tabindex
            var dynamicVisibleInput = YAHOO.util.Dom.get("dynamic-autocomplete").getElementsByTagName("input")[0];
            var noTabindex = ORBEON.util.Dom.getAttribute(dynamicVisibleInput, "tabindex");
            // IE 6/7 returns 0, while other browsers returns null
            YAHOO.util.Assert.isTrue(noTabindex == null || noTabindex == 0);
        },

        /**
         * Test that when we type the full value "Switzerland", the value of the node becomes "sz",
         * because "Switzerland" shows in the list of possible values, so the value should be selected
         * even if it wasn't "clicked on" by the user.
         */
        testTypeFullValue: function() {
            this.runForStaticDynamicResource(function(staticDynamicResource, continuation) {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    this.simulateTypeInField(staticDynamicResource, "Switzerland");
                    this.simulateFocusOut();
                }, function() {
                    this.checkExternal(staticDynamicResource, "sz", "Switzerland");
                    continuation.call(this);
                });
            });
        },

        /**
         * Test that entering a partial match "Sw", we get the expected list of countries in the suggestion list.
         */
        testTypePartialValueSelect: function() {
            this.runForStaticDynamicResource(function(staticDynamicResource, continuation) {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    this.simulateTypeInField(staticDynamicResource, "Sw");
                }, function() {
                    this.checkSuggestions(staticDynamicResource, ["Swaziland", "Sweden", "Switzerland"]);
                    ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                        this.simulateClickItem(staticDynamicResource, 1);
                    }, function() {
                        continuation.call(this);
                    });
                });
            });
        },

        testAlertShown: function() {
            this.runForStaticDynamicResource(function(staticDynamicResource, continuation) {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    // Set field to be empty
                    this.simulateTypeInField(staticDynamicResource, "");
                    this.simulateFocusOut();
                }, function() {
                    // Check the alert is active
                    var control = YAHOO.util.Dom.get(staticDynamicResource + "-autocomplete");
                    var container = YAHOO.util.Dom.getElementsByClassName("fr-autocomplete-container", null, control)[0];
                    YAHOO.util.Assert.isTrue(YAHOO.util.Dom.hasClass(container, "xforms-invalid"));
                    var alert = YAHOO.util.Dom.getElementsByClassName("xforms-alert", null, control)[0];
                    YAHOO.util.Assert.isTrue($(alert).is(".xforms-alert.xforms-active"),
                        "initially should have xforms-alert and xforms-active for " + staticDynamicResource);
                    ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                        // Set value to something that will start with the letter "s"
                        this.simulateTypeInField(staticDynamicResource, "Switzerland");
                        this.simulateFocusOut();
                    }, function() {
                        // Check the alert in inactive
                        YAHOO.util.Assert.isFalse(YAHOO.util.Dom.hasClass(container, "xforms-invalid"),
                            "after setting value, should not have xforms-invalid class for " + staticDynamicResource);
                        YAHOO.util.Assert.isFalse($(alert).is(".xforms-alert.xforms-active"),
                            "after setting value, should not have xforms-alert and xforms-active for " + staticDynamicResource);
                        continuation.call(this);
                    });
                });
            });
        },

        testDuplicateItemLabel: function() {
            this.runForStaticDynamicResource(function(staticDynamicResource, continuation) {
                if (staticDynamicResource == "resource") continuation.call(this); else {
                    ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                        // By typing the full country name, we already check that the suggestion comes and that it is
                        // not one of the 2 possibilities that is automatically selected
                        this.simulateTypeInField(staticDynamicResource, "United States");
                    }, function() {
                        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                            // Click on the first US
                            this.simulateClickItem(staticDynamicResource, 0);
                            this.simulateFocusOut();
                        }, function() {
                            this.checkExternal(staticDynamicResource, "us", "United States");
                            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                                this.simulateTypeInField(staticDynamicResource, "United States");
                            }, function() {
                                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                                    // Click on the second US
                                    this.simulateClickItem(staticDynamicResource, 1);
                                    this.simulateFocusOut();
                                }, function() {
                                    // The value is still 'us', not 'us2', since the XForms code that looks the value up
                                    // in the itemset only knows the label, not the position of the label
                                    this.checkExternal(staticDynamicResource, "us", "United States");
                                    continuation.call(this);
                                });
                            });
                        });
                    });
                }
            });
        },

        testDoubleSpaceInLabel: function() {
            this.runForStaticDynamicResource(function(staticDynamicResource, continuation) {
                if (staticDynamicResource == "resource") continuation.call(this); else {
                    ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                        // By typing the full country name, we already check that the suggestion comes and that it is
                        // not one of the 2 possibilities that is automatically selected
                        this.simulateTypeInField(staticDynamicResource, "Virgin");
                    }, function() {
                        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                            // Click on the first Virgin Island
                            this.simulateClickItem(staticDynamicResource, 0);
                            this.simulateFocusOut();
                        }, function() {
                            this.checkExternal(staticDynamicResource, "vq", "Virgin Islands", "1st value (vq) selected when clicking on the 1st item in the list");
                            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                                this.simulateTypeInField(staticDynamicResource, "Virgin");
                            }, function() {
                                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                                    // Click on the second US
                                    this.simulateClickItem(staticDynamicResource, 1);
                                    this.simulateFocusOut();
                                }, function() {
                                    this.checkExternal(staticDynamicResource, "vq2", "Virgin Islands", "2nd value (vq2) selected when clicking on the 2nd item in the list");
                                    this.checkSearchValue(staticDynamicResource, "Virgin  Islands");
                                    continuation.call(this);
                                });
                            });
                        });
                    });
                }
            });
        },

        /**
         * The max-results-displayed is set to 4 in the markup with an attribute for the static case and an element
         * for the dynamic case.
         */
        testMaxResultsDisplayed: function() {
            this.runForStaticDynamicResource(function(staticDynamicResource, continuation) {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    // There are more than 4 countries that start with "Ba"
                    // Enter 2 letters, because the dynamic case requires 2 letters before it gives suggestions
                    this.simulateTypeInField(staticDynamicResource, "Ba");
                }, function() {
                    this.checkSuggestionCount(staticDynamicResource, 4);
                    // Select one of the items just to close the suggestion list
                    this.simulateClickItem(staticDynamicResource, 1);
                    continuation.call(this);
                });
            });
        },

        /**
         * Test that the left border of the suggestion box is aligned with the left border of the text field.
         */
        testAlignment: function() {
            this.runForStaticDynamicResource(function(staticDynamicResource, continuation) {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    // There are more than 4 countries that start with "Ba"
                    // Enter 2 letters, because the dynamic case requires 2 letters before it gives suggestions
                    this.simulateTypeInField(staticDynamicResource, "Ba");
                }, function() {
                    var container = YAHOO.util.Dom.get(staticDynamicResource + "-autocomplete");
                    var suggestions = YAHOO.util.Dom.getElementsByClassName("yui-ac-container", null, container)[0];
                    var input = YAHOO.util.Dom.getElementsByClassName("fr-autocomplete-search", null, container)[0];
                    YAHOO.util.Assert.areEqual(YAHOO.util.Dom.getX(suggestions), YAHOO.util.Dom.getX(input));
                    // Select one of the items just to close the suggestion list
                    this.simulateClickItem(staticDynamicResource, 1);
                    continuation.call(this);
                });
            });
        },

        testSetLabel: function() {
            this.runForStaticDynamicResource(function(staticDynamicResource, continuation) {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    YAHOO.util.UserAction.click(YAHOO.util.Dom.get(staticDynamicResource + "-set-to-canada"));
                }, function() {
                    this.checkExternal(staticDynamicResource, "ca", "Canada", "external value is 'ca' because Canada exists in the itemset");
                    ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                        YAHOO.util.UserAction.click(YAHOO.util.Dom.get(staticDynamicResource + "-set-to-utopia"));
                    }, function() {
                        this.checkExternal(staticDynamicResource, "", "", "external value is empty string because Utopia does not exist in the itemset");
                        continuation.call(this);
                    });
                });
            });
        },

        workerTestFocus: function(innerOrOuter) {
            var staticAutocompleteInput = YAHOO.util.Dom.getElementsByClassName("yui-ac-input", null, "static-autocomplete")[0];
            var dynamicAutocompleteInput = YAHOO.util.Dom.getElementsByClassName("yui-ac-input", null, "dynamic-autocomplete")[0];
            var staticGotFocus = false;
            var dynamicGotFocus = false;
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                YAHOO.util.Event.addFocusListener(staticAutocompleteInput, function() { staticGotFocus = true; }, this, true);
                YAHOO.util.Event.addFocusListener(dynamicAutocompleteInput, function() { dynamicGotFocus = true; }, this, true);
                YAHOO.util.UserAction.click(YAHOO.util.Dom.get("static-setfocus-" + innerOrOuter));
            }, function() {
                YAHOO.util.Assert.isTrue(staticGotFocus);
                YAHOO.util.Assert.isFalse(dynamicGotFocus);

                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    YAHOO.util.UserAction.click(YAHOO.util.Dom.get("dynamic-setfocus-" + innerOrOuter));
                }, function() {
                    YAHOO.util.Assert.isTrue(staticGotFocus);
                    YAHOO.util.Assert.isTrue(dynamicGotFocus);
                });
            });
        },

        testSetFocusOuter: function() { this.workerTestFocus("outer"); },
        testSetFocusInner: function() { this.workerTestFocus("inner"); },

        testValueForStatic: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                YAHOO.util.UserAction.click(YAHOO.util.Dom.get("static-set-to-sz"));
            }, function() {
                this.checkSearchValue("static", "Switzerland");
            });
        },

        /**
         * Test that the full itemset dropdown can be used to make a selection in the static case.
         */
        testFullItemsetDropdown: function() {
            this.runForStaticDynamicResource(function(staticDynamicResource, continuation) {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    // Start by resetting the field, so the suggestion contains the n first results
                    YAHOO.util.UserAction.click(YAHOO.util.Dom.get(staticDynamicResource + "-reset"));
                }, function() {
                    ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                        // Click on the button, which may require something to be done on the server
                        this.simulateAutocompleteClick(staticDynamicResource);
                    }, function() {
                        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                            // Click on the second item in the list; the search field is populated right away
                            this.simulateClickItem(staticDynamicResource, 2);
                            this.checkSearchValue(staticDynamicResource, "Albania", "Search field contains value we clicked on");
                            this.simulateFocusOut();
                        }, function() {
                            // And the external value should now be updated as well
                            this.checkExternal(staticDynamicResource, "al", "Albania", "External value set based on selected item");
                            continuation.call(this);
                        });
                    });
                });
            });
        },

        /**
         * On focus out, if the label users typed isn't found in the itemset, we reset the autocomplete. However, we
         * don't want to reset it if just the text field looses the focus and the button gains it, which happens
         * when users start typing and click on the button. We also test that suggestion list is closed after this
         * (see https://github.com/orbeon/orbeon-forms/issues/101).
         */
        testKeepPartialValueOnFocusOut: function() {
            this.runForStaticDynamicResource(function(staticDynamicResource, continuation) {
                ORBEON.util.Test.runMayCauseXHR(this,
                    function() { this.simulateTypeInField(staticDynamicResource, "Sw"); },
                    function() { this.simulateAutocompleteClick(staticDynamicResource); },
                    function() { this.checkSearchValue(staticDynamicResource, "Sw", "List should show with current value still 'Sw'"); },
                    function() { this.simulateFocusOut(); },
                    function() { this.checkSearchValue(staticDynamicResource, "", "On focus lost, the search field is reset since 'Sw' isn't an existing label"); },
                    function() { this.checkSuggestionOpen(staticDynamicResource, false, 'List should be closed after focus out'); },
                    function() { continuation.call(this); }
                );
            });
        },

        EOO: {}
    }));

    ORBEON.util.Test.onOrbeonLoadedRunTest();
})();

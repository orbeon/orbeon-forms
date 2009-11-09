ORBEON.xforms.Events.orbeonLoadedEvent.subscribe(function() {

    YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

        name: "Autocomplete",

        /**
         * Runs the function for both the static and dynamic cases.
         *
         * @param f     function(staticDynamic, continuation)
         *              @param staticDynamic    Either "static" or "dynamic"
         *              @param continuation     Function with no parameter, but the caller needs to make sure
         *                                      to keep the this
         */
        runForStaticDynamic: function(f) {
            f.call(this, "static", function() {
                f.call(this, "dynamic", function(){});
            });
        },

        /**
         * Runs the function on all the active lis in the suggestion list.
         *
         * @param       function(li)
         *              @param li   Active list item
         */
        runOnLis: function(staticDynamic, f) {
            var autocomplete = YAHOO.util.Dom.get(staticDynamic + "-autocomplete");
            var lis = autocomplete.getElementsByTagName("li");
            for (liIndex = 0; liIndex < lis.length; liIndex++) {
                var li = lis[liIndex];
                if (li.style.display != "none")
                    f.call(this, li);
            }
        },

        /**
         * Simulates the user typing a value in a search field.
         */
        simulateTypeInField: function(staticDynamic, newValue) {
            var searchInput = YAHOO.util.Dom.get(staticDynamic + "-autocomplete$search").getElementsByTagName("input")[0];
            searchInput.focus();
            searchInput.value = newValue;
            YAHOO.util.UserAction.keyup(searchInput);
        },

        simulateClickItem: function(staticDynamic, position) {
            var liIndex = 0;
            this.runOnLis(staticDynamic, function(li) {
                if (liIndex == position) {
                    YAHOO.util.UserAction.click(li);
                }
                liIndex++;
            });
        },

        /**
         * Checks that the external value is what we expect it to be.
         */
        checkExternalValue: function(staticDynamic, expectedValue, message) {
            var outputValue = ORBEON.xforms.Document.getValue(staticDynamic + "-output");
            YAHOO.util.Assert.areEqual(expectedValue, outputValue, staticDynamic +
                (YAHOO.lang.isUndefined(message) ? "" : " - " + message));
        },

        /**
         * Checks that the items we get in the suggestion list are the one we expect.
         */
        checkSuggestions: function(staticDynamic, expectedValues) {
            this.runOnLis(staticDynamic, function(li) {
                YAHOO.util.Assert.areEqual(expectedValues[liIndex], li.innerHTML, staticDynamic + " at index " + liIndex);
            });
        },

        checkSuggestionCount: function(staticDynamic, expectedCount) {
            var actualCount = 0;
            this.runOnLis(staticDynamic, function(li) {
                actualCount++;
            });
            YAHOO.util.Assert.areEqual(actualCount, expectedCount, staticDynamic + " suggestions shows");
        },

        /**
         * This test needs to be first, as we test that setting the label to Canada on xforms-ready by dispatching
         * the fr-set-label event, we indeed get the value 'ca' in the node bound to the control.
         */
        testSetLabelOnXFormsReady: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                // Nothing is done in JS, but the fr-set-label we dispatch on xforms-ready will create some Ajax traffic
            }, function() {
                this.runForStaticDynamic(function(staticDynamic, continuation) {
                    this.checkExternalValue(staticDynamic, "ca");
                    continuation.call(this);
                });
            });
        },
        
        /**
         * Test that when we type the full value "Switzerland", the value of the node becomes "sz",
         * because "Switzerland" shows in the list of possible values, so the value should be selected
         * even if it wasn't "clicked on" by the user.
         */
        testTypeFullValue: function() {
            this.runForStaticDynamic(function(staticDynamic, continuation) {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    this.simulateTypeInField(staticDynamic, "Switzerland");
                }, function() {
                    this.checkExternalValue(staticDynamic, "sz");
                    continuation.call(this);
                });
            });
        },
        
        /**
         * Test that entering a partial match "Sw", we get the expected list of countries in the suggestion list.
         */
        testTypePartialValueSelect: function() {
            this.runForStaticDynamic(function(staticDynamic, continuation) {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    this.simulateTypeInField(staticDynamic, "Sw");
                }, function() {
                    this.checkSuggestions(staticDynamic, ["Swaziland", "Sweden", "Switzerland"]);
                    ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                        this.simulateClickItem(staticDynamic, 1);
                    }, function() {
                        continuation.call(this);
                    });
                });
            });
        },

        testAlertShown: function() {
            this.runForStaticDynamic(function(staticDynamic, continuation) {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    // Set field to be empty
                    this.simulateTypeInField(staticDynamic, "");
                }, function() {
                    // Check the alert is active
                    var control = YAHOO.util.Dom.get(staticDynamic + "-autocomplete");
                    var container = YAHOO.util.Dom.getElementsByClassName("fr-autocomplete-container", null, control)[0];
                    YAHOO.util.Assert.isTrue(YAHOO.util.Dom.hasClass(container, "xforms-invalid"));
                    var alert = YAHOO.util.Dom.getElementsByClassName("xforms-alert", null, control)[0];
                    YAHOO.util.Assert.isTrue(YAHOO.util.Dom.hasClass(alert, "xforms-alert-active"),
                        "initially should have xforms-alert-active for " + staticDynamic);
                    ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                        // Set value to something that will start with the letter "s"
                        this.simulateTypeInField(staticDynamic, "Switzerland");
                    }, function() {
                        // Check the alert in inactive
                        YAHOO.util.Assert.isFalse(YAHOO.util.Dom.hasClass(container, "xforms-invalid"),
                            "after setting value, should not have xforms-invalid class for " + staticDynamic);
                        YAHOO.util.Assert.isFalse(YAHOO.util.Dom.hasClass(alert, "xforms-alert-active"),
                            "after setting value, should not have xforms-alert-active for " + staticDynamic);
                        continuation.call(this);
                    });
                });
            });
        },

        testDuplicateItemLabel: function() {
            this.runForStaticDynamic(function(staticDynamic, continuation) {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    // By typing the full country name, we already check that the suggestion comes and that it is
                    // not one of the 2 possibilities that is automatically selected
                    this.simulateTypeInField(staticDynamic, "United States");
                }, function() {
                    ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                        // Click on the first US
                        this.simulateClickItem(staticDynamic, 0);
                    }, function() {
                        this.checkExternalValue(staticDynamic, "us");
                        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                            this.simulateTypeInField(staticDynamic, "United States");
                        }, function() {
                            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                                // Click on the second US
                                this.simulateClickItem(staticDynamic, 1);
                            }, function() {
                                this.checkExternalValue(staticDynamic, "us2");
                                continuation.call(this);
                            });
                        });
                    });
                });
            });
        },
        
        /**
         * The max-results-displayed is set to 4 in the markup with an attribute for the static case and an element
         * for the dynamic case.
         */
        testMaxResultsDisplayed: function() {
            this.runForStaticDynamic(function(staticDynamic, continuation) {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    // There are more than 4 countries that start with "Ba"
                    // Enter 2 letters, because the dynamic case requires 2 letters before it gives suggestions
                    this.simulateTypeInField(staticDynamic, "Ba");
                }, function() {
                    this.checkSuggestionCount(staticDynamic, 4);
                    // Select one of the items just to close the suggestion list
                    this.simulateClickItem(staticDynamic, 1);
                    continuation.call(this);
                });
            });
        },
        
        /**
         * Test that the left border of the suggestion box is aligned with the left border of the text field.
         */
        testAlignment: function() {
            this.runForStaticDynamic(function(staticDynamic, continuation) {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    // There are more than 4 countries that start with "Ba"
                    // Enter 2 letters, because the dynamic case requires 2 letters before it gives suggestions
                    this.simulateTypeInField(staticDynamic, "Ba");
                }, function() {
                    var container = YAHOO.util.Dom.get(staticDynamic + "-autocomplete");
                    var suggestions = YAHOO.util.Dom.getElementsByClassName("yui-ac-container", null, container)[0];
                    var input = YAHOO.util.Dom.getElementsByClassName("fr-autocomplete-search", null, container)[0];
                    YAHOO.util.Assert.areEqual(YAHOO.util.Dom.getX(suggestions), YAHOO.util.Dom.getX(input));
                    // Select one of the items just to close the suggestion list
                    this.simulateClickItem(staticDynamic, 1);
                    continuation.call(this);
                });
            });
        },
        
        testSetLabel: function() {
            this.runForStaticDynamic(function(staticDynamic, continuation) {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    YAHOO.util.UserAction.click(YAHOO.util.Dom.get(staticDynamic + "-set-to-canada"));
                }, function() {
                    this.checkExternalValue(staticDynamic, "ca", "external value is 'ca' because Canada exists in the itemset");
                    ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                        YAHOO.util.UserAction.click(YAHOO.util.Dom.get(staticDynamic + "-set-to-utopia"));
                    }, function() {
                        this.checkExternalValue(staticDynamic, "", "external value is empty string because Utopia does not exist in the itemset");
                        continuation.call(this);
                    });
                });
            });
        }
    }));

    if (parent && parent.TestManager) {
        parent.TestManager.load();
    } else {
        new YAHOO.tool.TestLogger();
        YAHOO.tool.TestRunner.run();
    }
});

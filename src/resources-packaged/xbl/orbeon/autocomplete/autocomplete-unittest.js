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
        checkExternalValue: function(staticDynamic, expectedValue) {
            var outputValue = ORBEON.xforms.Document.getValue(staticDynamic + "-output");
            YAHOO.util.Assert.areEqual(expectedValue, outputValue, staticDynamic);
        },
        
        /**
         * Checks that the items we get in the suggestion list are the one we expect.
         */
        checkSuggestions: function(staticDynamic, expectedValues) {
            this.runOnLis(staticDynamic, function(li) {
                YAHOO.util.Assert.areEqual(expectedValues[liIndex], li.innerHTML, staticDynamic + " at index " + liIndex);
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
                    var container = YAHOO.util.Dom.get(staticDynamic + "-autocomplete$autocomplete-container");
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
                    continuation.call(this);
                    
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

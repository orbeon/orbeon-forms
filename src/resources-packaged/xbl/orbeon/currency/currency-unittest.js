ORBEON.xforms.Events.orbeonLoadedEvent.subscribe(function() {

    var emptyInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-currency-visible-input", null, "empty")[0];
    var valueInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-currency-visible-input", null, "value")[0];
    var valueOutput = YAHOO.util.Dom.get("value-output");
    var doubleInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-currency-visible-input", null, "double")[0];
    var prefixStaticInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-currency-visible-input", null, "prefix-static")[0];
    var prefixDynamicInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-currency-visible-input", null, "prefix-dynamic")[0];
    var floatStaticInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-currency-visible-input", null, "float-static")[0];
    var floatDynamicInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-currency-visible-input", null, "float-dynamic")[0];
    var floatNoDigitsInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-currency-visible-input", null, "float-no-digits")[0];
    var readonlyInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-currency-visible-input", null, "readonly")[0];

    YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

        name: "Currency",

        // Field should be empty when the page loaded if the instance is empty
        testEmptyInitial: function() {
            YAHOO.util.Assert.areEqual("", emptyInput.value);
        },
        
        // If we just enter and leave the field, the value must stay empty
        testEmptyNoChange: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                emptyInput.focus();
                emptyInput.blur();
                doubleInput.focus();
            }, function() {
                YAHOO.util.Assert.areEqual("", emptyInput.value);
            });
        },
        
        testBeforeFocus: function() {
            YAHOO.util.Assert.areEqual("$ 1,234.00", valueInput.value);
        },
        
        testAfterFocus: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                valueInput.focus();
                YAHOO.util.Assert.areEqual("1234.00", valueInput.value);
            }, function() {
            });
        },
        
        testChangeSimple: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                valueInput.value = 42;
                valueInput.blur();
            }, function() {
                YAHOO.util.Assert.areEqual("$ 42.00", valueInput.value);
                YAHOO.util.Assert.areEqual("$ 84.00", doubleInput.value);
            });
        },
        
        testChangeWhileFocus: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                valueInput.focus();
                valueInput.value = 43;
                valueInput.blur();
                doubleInput.focus();
            }, function() {
                YAHOO.util.Assert.areEqual("$ 43.00", valueInput.value);
                YAHOO.util.Assert.areEqual("86.00", doubleInput.value);
            });
        },
        
        testStaticCurrency: function() {
            YAHOO.util.Assert.areEqual("£ 4,567.00", prefixStaticInput.value);
        },
        
        testDynamicCurrency: function() {
            YAHOO.util.Assert.areEqual("£ 4,567.00", prefixDynamicInput.value);
        },
        
        testChangeCurrency: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                YAHOO.util.UserAction.click(YAHOO.util.Dom.get("change-prefix"));
            }, function() {
                YAHOO.util.Assert.areEqual("CHF 4,567.00", prefixDynamicInput.value);
            });
        },
        testStaticDigits: function() {
            YAHOO.util.Assert.areEqual("$ 123.456", floatStaticInput.value);
        },
        testDynamicDigits: function() {
            YAHOO.util.Assert.areEqual("$ 123.456", floatDynamicInput.value);
        },
        testChangeDigits: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                YAHOO.util.UserAction.click(YAHOO.util.Dom.get("change-digits"));
            }, function() {
                YAHOO.util.Assert.areEqual("$ 123.46", floatDynamicInput.value);
            });
        },
        testNoDigits: function() {
            YAHOO.util.Assert.areEqual("$ 123", floatNoDigitsInput.value);
        },
        testReadonly: function() {
            YAHOO.util.Assert.isTrue(readonlyInput.disabled);
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                YAHOO.util.UserAction.click(YAHOO.util.Dom.get("set-readwrite"));
            }, function() {
                YAHOO.util.Assert.isFalse(readonlyInput.disabled);
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    YAHOO.util.UserAction.click(YAHOO.util.Dom.get("set-readonly"));
                }, function() {
                    YAHOO.util.Assert.isTrue(readonlyInput.disabled);
                });
            });
        },
        testCleanupComa: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                valueInput.focus();
                valueInput.value = "1,1";
                doubleInput.focus();
            }, function() {
                YAHOO.util.Assert.areEqual("11", ORBEON.xforms.Controls.getCurrentValue(valueOutput));
            });
        },
        testCleanupPrefix: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                valueInput.focus();
                valueInput.value = "$ 42";
                doubleInput.focus();
            }, function() {
                YAHOO.util.Assert.areEqual("42", ORBEON.xforms.Controls.getCurrentValue(valueOutput));
            });
        },
        testNoPrefix: function() {
            var noPrefixInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-currency-visible-input", null, "no-prefix")[0];
            YAHOO.util.Assert.areEqual("42.00", noPrefixInput.value);
        },
        testInRepeat: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                YAHOO.util.UserAction.click(YAHOO.util.Dom.get("repeat-show-hide"));
            }, function() {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    YAHOO.util.UserAction.click(YAHOO.util.Dom.get("repeat-show-hide"));
                }, function() {
                    var repeatInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-currency-visible-input", null, "in-repeat" + XFORMS_SEPARATOR_1 + "1")[0];
                    YAHOO.util.Assert.areEqual("$ 42.00", repeatInput.value);
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

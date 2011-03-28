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
ORBEON.xforms.Events.orbeonLoadedEvent.subscribe(function() {

    var emptyInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-number-visible-input", null, "empty")[0];
    var valueInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-number-visible-input", null, "value")[0];
    var valueOutput = YAHOO.util.Dom.get("value-output");
    var doubleInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-number-visible-input", null, "double")[0];
    var prefixStaticInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-number-visible-input", null, "prefix-static")[0];
    var prefixDynamicInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-number-visible-input", null, "prefix-dynamic")[0];
    var floatStaticInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-number-visible-input", null, "float-static")[0];
    var floatDynamicInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-number-visible-input", null, "float-dynamic")[0];
    var floatNoDigitsInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-number-visible-input", null, "float-no-digits")[0];
    var readonlyInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-number-visible-input", null, "readonly")[0];
    var largeInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-number-visible-input", null, "large")[0];

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
                valueInput.value = "$4242";
                doubleInput.focus();
            }, function() {
                YAHOO.util.Assert.areEqual("$ 4,242.00", valueInput.value, "1st attempt, value in field");
                YAHOO.util.Assert.areEqual("4242", ORBEON.xforms.Controls.getCurrentValue(valueOutput), "1st attempt, value in instance");
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    valueInput.focus();
                    console.log(valueInput.value);
                    valueInput.value = "$4242";
                    doubleInput.focus();
                }, function() {
                    YAHOO.util.Assert.areEqual("$ 4,242.00", valueInput.value, "2nd attempt, value in field");
                    YAHOO.util.Assert.areEqual("4242", ORBEON.xforms.Controls.getCurrentValue(valueOutput), "2nd attempt, value in instance");
                });
            });
        },
        testNoPrefix: function() {
            var noPrefixInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-number-visible-input", null, "no-prefix")[0];
            YAHOO.util.Assert.areEqual("42.00", noPrefixInput.value);
        },
        testInRepeat: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                YAHOO.util.UserAction.click(YAHOO.util.Dom.get("repeat-show-hide"));
            }, function() {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    YAHOO.util.UserAction.click(YAHOO.util.Dom.get("repeat-show-hide"));
                }, function() {
                    var repeatInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-number-visible-input", null, "in-repeat" + XFORMS_SEPARATOR_1 + "1")[0];
                    YAHOO.util.Assert.areEqual("$ 42.00", repeatInput.value);
                });
            });
        },
        testClasses: function() {
            var requiredEmptyControl = YAHOO.util.Dom.get("required-empty");
            var requiredEmptyGroup = YAHOO.util.Dom.getElementsByClassName("xforms-group", null, requiredEmptyControl)[0];
            var requiredEmptyInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-number-visible-input", null, requiredEmptyGroup)[0];
            var emptyControl = YAHOO.util.Dom.get("empty");
            var emptyInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-number-visible-input", null, emptyControl)[0];

            var requiredFilledControl = YAHOO.util.Dom.get("required-filled");
            var requiredFilledGroup = YAHOO.util.Dom.getElementsByClassName("xforms-group", null, requiredFilledControl)[0];

            function checkClasses(group, invalid, required, invalidVisited, requiredFilled, situation) {
                YAHOO.util.Assert.areEqual(invalid, YAHOO.util.Dom.hasClass(group, "xforms-invalid"), situation + " for xforms-invalid");
                YAHOO.util.Assert.areEqual(required, YAHOO.util.Dom.hasClass(group, "xforms-required"), situation + " for xforms-required");
                YAHOO.util.Assert.areEqual(invalidVisited, YAHOO.util.Dom.hasClass(group, "xforms-invalid-visited"), situation + " for xforms-invalid-visited");
                YAHOO.util.Assert.areEqual(requiredFilled, YAHOO.util.Dom.hasClass(group, "xforms-required-filled"), situation + " for xforms-required-filled");
            }

            // Check classes with no change
            checkClasses(requiredEmptyGroup, true, true, false, false, "initially empty");
            checkClasses(requiredFilledGroup, false, true, false, true, "initially filled");
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                requiredEmptyInput.focus();
                requiredEmptyInput.blur();
                emptyInput.focus();
            }, function() {
                //  Check we now have the class xforms-invalid-visited
                checkClasses(requiredEmptyGroup, true, true, true, false, "after visited empty");
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    requiredEmptyInput.focus();
                    requiredEmptyInput.value = "42";
                    requiredEmptyInput.blur();
                    emptyInput.focus();
                }, function() {
                    // Check it is now marked valid and filled
                    checkClasses(requiredEmptyGroup, false, true, false, true, "after filled empty");
                });
            });
        },
        testSetfocus: function() {
            var setfocusInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-number-visible-input", null, "setfocus")[0];
            var gotFocus = false;
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                YAHOO.util.Event.addFocusListener(setfocusInput, function() { gotFocus = true; }, this, true);
                YAHOO.util.UserAction.click(YAHOO.util.Dom.get("setfocus-trigger"));
            }, function() {
                YAHOO.util.Assert.isTrue(gotFocus);
            });
        },

        // Currency field should not be susceptible to limitations of number precision imposed by the platform
        testLarge: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                YAHOO.util.Assert.areEqual("$ 12,345,678,901,234,567,890,123.00", largeInput.value);
                largeInput.focus();
                YAHOO.util.Assert.areEqual("12345678901234567890123.00", largeInput.value);
                largeInput.value = "12345678901234567890124";
                largeInput.blur();
                emptyInput.focus();
            }, function() {
                YAHOO.util.Assert.areEqual("$ 12,345,678,901,234,567,890,124.00", largeInput.value);
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

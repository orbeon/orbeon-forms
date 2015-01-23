/**
 * Copyright (C) 2012 Orbeon, Inc.
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

    function input(id) {
        return $(document.getElementById(id)).find('.xbl-fr-number-visible-input')[0];
    }

    YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

        name: "Currency",

        // Field should be empty when the page loaded if the instance is empty
        testEmptyInitial: function() {
            YAHOO.util.Assert.areEqual("", input('empty').value);
        },

        // If we just enter and leave the field, the value must stay empty
        testEmptyNoChange: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                input('empty').focus();
                input('empty').blur();
                input('double').focus();
            }, function() {
                YAHOO.util.Assert.areEqual("", input('empty').value);
            });
        },

        testBeforeFocus: function() {
            YAHOO.util.Assert.areEqual("$ 1,234.00", input('value').value);
        },

        testAfterFocus: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                input('value').focus();
                YAHOO.util.Assert.areEqual("1234.00", input('value').value);
            }, function() {
            });
        },

        testChangeSimple: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                input('value').value = 42;
                input('value').blur();
            }, function() {
                YAHOO.util.Assert.areEqual("$ 42.00", input('value').value);
                YAHOO.util.Assert.areEqual("$ 84.00", input('double').value);
            });
        },

        testChangeWhileFocus: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                input('value').focus();
                input('value').value = 43;
                input('value').blur();
                input('double').focus();
            }, function() {
                YAHOO.util.Assert.areEqual("$ 43.00", input('value').value);
                YAHOO.util.Assert.areEqual("86.00", input('double').value);
            });
        },

        testStaticCurrency: function() {
            YAHOO.util.Assert.areEqual("£ 4,567.00", input('prefix-static').value);
        },

        testDynamicCurrency: function() {
            YAHOO.util.Assert.areEqual("£ 4,567.00", input('prefix-dynamic').value);
        },

        testChangeCurrency: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                YAHOO.util.UserAction.click(YAHOO.util.Dom.get("change-prefix"));
            }, function() {
                YAHOO.util.Assert.areEqual("CHF 4,567.00", input('prefix-dynamic').value);
            });
        },
        testStaticDigits: function() {
            YAHOO.util.Assert.areEqual("$ 123.456", input('float-static').value);
        },
        testDynamicDigits: function() {
            YAHOO.util.Assert.areEqual("$ 123.456", input('float-dynamic').value);
        },
        testChangeDigits: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                YAHOO.util.UserAction.click(YAHOO.util.Dom.get("change-digits"));
            }, function() {
                YAHOO.util.Assert.areEqual("$ 123.46", input('float-dynamic').value);
            });
        },
        testNoDigits: function() {
            YAHOO.util.Assert.areEqual("$ 123", input('float-no-digits').value);
        },
        testReadonly: function() {
            YAHOO.util.Assert.isTrue(input('readonly').disabled);
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                YAHOO.util.UserAction.click(YAHOO.util.Dom.get("set-readwrite"));
            }, function() {
                YAHOO.util.Assert.isFalse(input('readonly').disabled);
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    YAHOO.util.UserAction.click(YAHOO.util.Dom.get("set-readonly"));
                }, function() {
                    YAHOO.util.Assert.isTrue(input('readonly').disabled);
                });
            });
        },
        testCleanupComa: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                input('value').focus();
                input('value').value = "1,1";
                input('double').focus();
            }, function() {
                YAHOO.util.Assert.areEqual("11", ORBEON.xforms.Controls.getCurrentValue(YAHOO.util.Dom.get("value-output")));
            });
        },
        testCleanupPrefix: function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                input('value').focus();
                input('value').value = "$4242";
                input('double').focus();
            }, function() {
                YAHOO.util.Assert.areEqual("$ 4,242.00", input('value').value, "1st attempt, value in field");
                YAHOO.util.Assert.areEqual("4242", ORBEON.xforms.Controls.getCurrentValue(YAHOO.util.Dom.get("value-output")), "1st attempt, value in instance");
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    input('value').focus();
                    input('value').value = "$4242";
                    input('double').focus();
                }, function() {
                    YAHOO.util.Assert.areEqual("$ 4,242.00", input('value').value, "2nd attempt, value in field");
                    YAHOO.util.Assert.areEqual("4242", ORBEON.xforms.Controls.getCurrentValue(YAHOO.util.Dom.get("value-output")), "2nd attempt, value in instance");
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
                    var repeatInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-number-visible-input", null, "in-repeat" + XF_REPEAT_SEPARATOR + "1")[0];
                    YAHOO.util.Assert.areEqual("$ 42.00", repeatInput.value);
                });
            });
        },
        testClasses: function() {
            var requiredEmptyControl = YAHOO.util.Dom.get("required-empty");
            var emptyControl = YAHOO.util.Dom.get("empty");

            var requiredFilledControl = YAHOO.util.Dom.get("required-filled");

            function checkClasses(group, invalid, required, visited, requiredFilled, situation) {
                YAHOO.util.Assert.areEqual(invalid, YAHOO.util.Dom.hasClass(group, "xforms-invalid"), situation + " for xforms-invalid");
                YAHOO.util.Assert.areEqual(required, YAHOO.util.Dom.hasClass(group, "xforms-required"), situation + " for xforms-required");
                YAHOO.util.Assert.areEqual(visited, YAHOO.util.Dom.hasClass(group, "xforms-visited"), situation + " for xforms-visited");
                YAHOO.util.Assert.areEqual(requiredFilled, YAHOO.util.Dom.hasClass(group, "xforms-filled"), situation + " for xforms-filled");
            }

            // Check classes with no change
            checkClasses(requiredEmptyControl, true, true, false, false, "initially empty");
            checkClasses(requiredFilledControl, false, true, false, true, "initially filled");
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                input('required-empty').focus();
                input('required-empty').blur();
                input('empty').focus();
            }, function() {
                //  Check we now have the class xforms-visited
                checkClasses(requiredEmptyControl, true, true, true, false, "after visited empty");
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    input('required-empty').focus();
                    input('required-empty').value = "42";
                    input('required-empty').blur();
                    input('empty').focus();
                }, function() {
                    // Check it is now marked valid and filled
                    checkClasses(requiredEmptyControl, false, true, false, true, "after filled empty");
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
                YAHOO.util.Assert.areEqual("$ 12,345,678,901,234,567,890,123.00", input('large').value);
                input('large').focus();
                YAHOO.util.Assert.areEqual("12345678901234567890123.00", input('large').value);
                input('large').value = "12345678901234567890124";
                input('large').blur();
                input('empty').focus();
            }, function() {
                YAHOO.util.Assert.areEqual("$ 12,345,678,901,234,567,890,124.00", input('large').value);
            });
        }
    }));

    ORBEON.util.Test.onOrbeonLoadedRunTest();
})();
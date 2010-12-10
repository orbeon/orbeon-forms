/**
 * Copyright (C) 2009 Orbeon, Inc.
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
YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

    name: "Controls in repeat (also tests setvalue/getvalue)",

    repeatRebuildWorker: function(controlId) {
        var fullId = controlId + XFORMS_SEPARATOR_1 + "1";
        YAHOO.util.Assert.areEqual("true", ORBEON.xforms.Document.getValue(fullId));
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            ORBEON.xforms.Document.setValue("repeat-shown", "false");
        }, function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                ORBEON.xforms.Document.setValue("repeat-shown", "true");
            }, function() {
                var control = YAHOO.util.Dom.get(fullId);
                var formElement = ORBEON.util.Dom.getElementByTagName(control, ["input", "textarea"]);
                YAHOO.util.Assert.isFalse(formElement.disabled, "form element must not be disabled after recreation");
                YAHOO.util.Assert.areEqual("true", ORBEON.xforms.Document.getValue(fullId));
                YAHOO.util.Assert.areEqual("Label", ORBEON.xforms.Controls.getLabelMessage(YAHOO.util.Dom.get(fullId)));
            });
        });
   },

   testInput: function() { this.repeatRebuildWorker("input"); },
   testTextarea: function() { this.repeatRebuildWorker("textarea"); },
   testSecret: function() { this.repeatRebuildWorker("secret"); },
   testInputBoolean: function() { this.repeatRebuildWorker("input-boolean"); }

}));

YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

    name: "Readonly and relevant",

    getFormControls: function() {
        var controlsContainer = YAHOO.util.Dom.get("controls");
        var formTagNames = [ "input", "textarea", "select", "button" ];
        var formElements = [];
        for (var formTagNameIndex = 0; formTagNameIndex < formTagNames.length; formTagNameIndex++) {
            var formTagName = formTagNames[formTagNameIndex];
            var thisTagElements = controlsContainer.getElementsByTagName(formTagName);
            for (var thisTagElementIndex = 0; thisTagElementIndex < thisTagElements.length; thisTagElementIndex++) {
                var thisTagElement = thisTagElements[thisTagElementIndex];
                if (YAHOO.util.Dom.getAncestorByClassName(thisTagElement, "xforms-repeat-template") == null)
                    formElements.push(thisTagElement);
            }
        }
        return formElements;
    },

    checkDisabled: function(disabled) {
        var elements = this.getFormControls();
        for (var elementIndex = 0; elementIndex < elements.length; elementIndex++) {
            var element = elements[elementIndex];
            if (element.id != "disabled-input$xforms-input-1" + XFORMS_SEPARATOR_1 + "1"
                    && element.id != "readonly-input$xforms-input-1" + XFORMS_SEPARATOR_1 + "1")
                YAHOO.util.Assert.areEqual(disabled, element.disabled, "element " + element.id + " supposed to have disabled = " + disabled);
        }
    },

    testReadonly: function() {
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            ORBEON.xforms.Document.setValue("readonly", "true");
        }, function() {
            this.checkDisabled(true);
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                ORBEON.xforms.Document.setValue("readonly", "false");
            }, function() {
                this.checkDisabled(false);
            });
        });
    },

    testReadonlyBecomingRelevant: function() {
        var inputContainer = YAHOO.util.Dom.get("readonly-input" + XFORMS_SEPARATOR_1 + "1");
        var inputField = ORBEON.util.Dom.getElementByTagName(inputContainer, "input");
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            YAHOO.util.Assert.isTrue(inputField.disabled, "input field initially disabled because bound to a readonly node");
            ORBEON.xforms.Document.setValue("relevant", "false");
        }, function() {
            // When non-relevant, we don't care whether the field is actually disabled or not as it won't accessible by users
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                ORBEON.xforms.Document.setValue("relevant", "true");
            }, function() {
                YAHOO.util.Assert.isTrue(inputField.disabled, "input field still disabled when becomes relevant because readonly");
            });
        });
    }
}));

YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

    name: "xforms:select and xforms:select1",

    getSelect: function(controlId) {
        var control = YAHOO.util.Dom.get(controlId);
        return ORBEON.util.Utils.isNewXHTMLLayout()
            ? control.getElementsByTagName("select")[0]
            : control;
    },

    testAddToItemset: function() {
        // Get initial value for flavor
        var flavorSelect1 = YAHOO.util.Dom.get("flavor-select1-full" + XFORMS_SEPARATOR_1 + "1");
        var initialFlavorValue = ORBEON.xforms.Controls.getCurrentValue(flavorSelect1);
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            // Click on text field
            var addTrigger = YAHOO.util.Dom.get("add-flavor" + XFORMS_SEPARATOR_1 + "1");
            YAHOO.util.UserAction.click(addTrigger);
        }, function() {
            // Check that the values didn't change
            YAHOO.util.Assert.areEqual(initialFlavorValue, ORBEON.xforms.Controls.getCurrentValue(flavorSelect1));
       });
    },

    testUpdateRadio: function() {
         ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
             // Click on Set to strawberry button
             var setToStrawBerryTrigger = YAHOO.util.Dom.get("set-to-strawberry" + XFORMS_SEPARATOR_1 + "1");
             YAHOO.util.UserAction.click(setToStrawBerryTrigger);
         }, function() {
             // Check all the select/select1 changed
             YAHOO.util.Assert.isTrue(YAHOO.util.Dom.get("flavor-select1-full$$e1" + XFORMS_SEPARATOR_1 + "1").checked, "radio is checked");
             YAHOO.util.Assert.isTrue(YAHOO.util.Dom.get("flavor-select-full$$e1" + XFORMS_SEPARATOR_1 + "1").checked, "checkbox is checked");
             YAHOO.util.Assert.isTrue(this.getSelect("flavor-select1-compact" + XFORMS_SEPARATOR_1 + "1").options[1].selected, "list single is selected");
             YAHOO.util.Assert.isTrue(this.getSelect("flavor-select-compact" + XFORMS_SEPARATOR_1 + "1").options[1].selected, "list multiple is selected");
        });
    },

    testUpdateList: function() {
         ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
             // Set value in list to vanilla
             var select1Id = "flavor-select1-compact" + XFORMS_SEPARATOR_1 + "1";
             var select1Control = YAHOO.util.Dom.get(select1Id);
             var select1List = this.getSelect(select1Id);
             ORBEON.xforms.Controls.setCurrentValue(select1Control, select1List.options[3].value);
             ORBEON.xforms.Events.change({target: select1Control});
         }, function() {
             // Check vanilla radio is checked
             YAHOO.util.Assert.isTrue(YAHOO.util.Dom.get("flavor-select1-full$$e3" + XFORMS_SEPARATOR_1 + "1").checked, "radio checked");
        });
    },

    testUpdateCheckbox: function() {
         ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
             // Click on key lime checkbox (in addition to already selected strawberry)
             var dhlCheckbox = YAHOO.util.Dom.get("flavor-select-full$$e2" + XFORMS_SEPARATOR_1 + "1");
             dhlCheckbox.click();
         }, function() {
             // Check strawberry and key lime are selected in the list
             YAHOO.util.Assert.isTrue(this.getSelect("flavor-select-compact" + XFORMS_SEPARATOR_1 + "1").options[1].selected, "strawberry is selected");
             YAHOO.util.Assert.isTrue(this.getSelect("flavor-select-compact" + XFORMS_SEPARATOR_1 + "1").options[2].selected, "key lime is selected");
        });
    },

    testOutOfRange: function() {
        function valueOfRadio() { return ORBEON.xforms.Document.getValue("flavor-select1-full" + XFORMS_SEPARATOR_1 + "1") }

        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
           YAHOO.util.UserAction.click("set-out-of-range" + XFORMS_SEPARATOR_1 + "1");
        }, function() {
            YAHOO.util.Assert.areEqual("", valueOfRadio(), "value is empty string after setting value to out of range");
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
               YAHOO.util.UserAction.click("set-to-strawberry" + XFORMS_SEPARATOR_1 + "1");
            }, function() {
                YAHOO.util.Assert.areEqual("s", valueOfRadio(), "apple radio is selected after putting an 'x' in the node");
            });
        });
    },

    testClasses: function() {
        function checkColors(firstColor, secondColor) {
            var radioContainer = YAHOO.util.Dom.get("flavor-select1-full" + XFORMS_SEPARATOR_1 + "1");
            if (ORBEON.util.Utils.isNewXHTMLLayout()) radioContainer = YAHOO.util.Dom.getFirstChild(radioContainer);
            var radios = YAHOO.util.Dom.getChildren(radioContainer);
            YAHOO.util.Assert.isTrue(YAHOO.util.Dom.hasClass(radios[0], firstColor), "radio has " + firstColor + " class");
            YAHOO.util.Assert.isTrue(YAHOO.util.Dom.hasClass(radios[1], secondColor), "radio has " + secondColor + " class");
            var checkboxContainer = YAHOO.util.Dom.get("flavor-select-full" + XFORMS_SEPARATOR_1 + "1");
            if (ORBEON.util.Utils.isNewXHTMLLayout()) checkboxContainer = YAHOO.util.Dom.getFirstChild(checkboxContainer);
            var checkboxes = YAHOO.util.Dom.getChildren(checkboxContainer);
            YAHOO.util.Assert.isTrue(YAHOO.util.Dom.hasClass(checkboxes[0], firstColor), "check box has " + firstColor + " class");
            YAHOO.util.Assert.isTrue(YAHOO.util.Dom.hasClass(checkboxes[1], secondColor), "check box has " + secondColor + " class");
            var select1Options = YAHOO.util.Dom.get("flavor-select1-compact" + XFORMS_SEPARATOR_1 + "1").getElementsByTagName("option");
            YAHOO.util.Assert.isTrue(YAHOO.util.Dom.hasClass(select1Options[0], firstColor), "list for select1 has " + firstColor + " class");
            YAHOO.util.Assert.isTrue(YAHOO.util.Dom.hasClass(select1Options[1], secondColor), "list for select1 has " + secondColor + " class");
        }

        checkColors("orange", "red");
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
           YAHOO.util.UserAction.click("change-colors" + XFORMS_SEPARATOR_1 + "1");
        }, function() {
            checkColors("yellow", "brown");
        });
    }
}));

YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

    name: "xforms:input type xs:date",

    dateValueControlId: "date" + XFORMS_SEPARATOR_1 + "1",
    dateValueInputId: "date$xforms-input-1" + XFORMS_SEPARATOR_1 + "1",

    testOpenHideCalendar: function() {
        // Click on text field
        YAHOO.util.UserAction.click(this.dateValueInputId);
        // Check calendar div shown
        YAHOO.util.Assert.areEqual("visible", document.getElementById("orbeon-calendar-div").style.visibility);
        // Click on body
        YAHOO.util.UserAction.click(document.body);
        // Check calendar div is hidden
        YAHOO.util.Assert.areEqual("hidden", document.getElementById("orbeon-calendar-div").style.visibility);
    },

    testCantOpenReadonly: function() {
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            ORBEON.xforms.Document.setValue("readonly", "true");
        }, function() {
            // Check input field has been disabled
            YAHOO.util.Assert.areEqual(true, document.getElementById(this.dateValueInputId).disabled);
            // Click on text field
            YAHOO.util.UserAction.click(this.dateValueInputId);
            // Check that the div is still hidden
            YAHOO.util.Assert.areEqual("hidden", document.getElementById("orbeon-calendar-div").style.visibility);
            // Restore read-only = false
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                ORBEON.xforms.Document.setValue("readonly", "false");
            }, function() {});
        });
    },

    checkDateConversion: function(twoDigits, fourDigits) {
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            // This year taken in the 21th century
            ORBEON.xforms.Document.setValue(this.dateValueControlId, "1/1/" + twoDigits);
        }, function() {
            YAHOO.util.Assert.areEqual(fourDigits + "-01-01", ORBEON.xforms.Document.getValue(this.dateValueControlId));
       });
    },

    testTwoDigitClose: function() { this.checkDateConversion ("02", "2002"); },
    testTwoDigitTwentyFirst: function() { this.checkDateConversion ("40", "2040"); },
    testTwoDigitTwentieth: function() { this.checkDateConversion ("85", "1985"); },

    testDateOverflow: function() {
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            // Enter invalid date
            ORBEON.xforms.Document.setValue(this.dateValueControlId, "1/40/10");
        }, function() {
            YAHOO.util.Assert.areEqual("1/40/10", ORBEON.xforms.Document.getValue(this.dateValueControlId), "value still what we entered");
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                // Enter valid date
                ORBEON.xforms.Document.setValue(this.dateValueControlId, "1/31/10");
            }, function() {
                YAHOO.util.Assert.areEqual("2010-01-31", ORBEON.xforms.Document.getValue(this.dateValueControlId), "value was parsed");
            });
        });
    }
}));

YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

    name: "xforms:input type xs:time",

    timeValueId: "time" + XFORMS_SEPARATOR_1 + "1",
    timeValueInputId: "time$xforms-input-1" + XFORMS_SEPARATOR_1 + "1",
    dateValueId: "date" + XFORMS_SEPARATOR_1 + "1",
    dateValueInputId: "date$xforms-input-1" + XFORMS_SEPARATOR_1 + "1",

    workerTimeParsing: function(typedValue, expectedValue) {
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            ORBEON.xforms.Document.setValue(this.timeValueId, typedValue);
        }, function() {
            YAHOO.util.Assert.areEqual(expectedValue, YAHOO.util.Dom.get(this.timeValueInputId).value);
        });
    },

    // Test for: Regression: dateTime field is always invalid when "p.m." is entered
    // http://forge.objectweb.org/tracker/?func=detail&atid=350207&aid=313427&group_id=168
    testParsing: function() {
        var parsedTime = ORBEON.util.DateTime.magicTimeToJSDate("6:00:00 p");
        YAHOO.util.Assert.isNotNull(parsedTime);
    },

    testFirstHourShort:     function() { this.workerTimeParsing("12 am",        "0:00:00 a.m."); },
    testFirstHourMedium:    function() { this.workerTimeParsing("12:30 am",     "0:30:00 a.m."); },
    testFirstHourLong:      function() { this.workerTimeParsing("12:30:40 am",  "0:30:40 a.m."); },
    testNoAmPmShort:        function() { this.workerTimeParsing("12",           "12:00:00 p.m."); },
    testNoAmPmMedium:       function() { this.workerTimeParsing("12:30",        "12:30:00 p.m."); },
    testNoAmPmLong:         function() { this.workerTimeParsing("12:30:40",     "12:30:40 p.m."); },

    testNextYear: function() {
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            ORBEON.xforms.Document.setValue(this.dateValueId, "10/20/2030");
        }, function() {
            // Click on Next Year
            ORBEON.xforms.Events.click({target: YAHOO.util.Dom.get(this.dateValueInputId), button: 0 });
            var nextYear = YAHOO.util.Dom.getElementsByClassName("calyearright", null, "orbeon-calendar-div")[0];
            YAHOO.util.UserAction.click(nextYear);
            this.wait(function() {
                var monthYear = YAHOO.util.Dom.getElementsByClassName("calnav", null, "orbeon-calendar-div")[0];
                // Check we are no in 2031
                YAHOO.util.Assert.areEqual("October 2031", monthYear.innerHTML);
                // Click previous year twice
                var previousYear = YAHOO.util.Dom.getElementsByClassName("calyearleft", null, "orbeon-calendar-div")[0];
                YAHOO.util.UserAction.click(previousYear);
                YAHOO.util.UserAction.click(previousYear);
                this.wait(function() {
                    // Check we are no in 2029
                    monthYear = YAHOO.util.Dom.getElementsByClassName("calnav", null, "orbeon-calendar-div")[0];
                    YAHOO.util.Assert.areEqual("October 2029", monthYear.innerHTML);
                    YAHOO.util.UserAction.click(document.body);
                }, ORBEON.util.Properties.internalShortDelay.get());
            }, ORBEON.util.Properties.internalShortDelay.get());
        });

    }
}));

YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

    name: "xforms:trigger appearance=\"minimal\"",

    triggerId: "trigger-minimal" + XFORMS_SEPARATOR_1 + "1",

    // Test that the control is correctly restored when the iteration is recreated
    // http://forge.objectweb.org/tracker/index.php?func=detail&aid=313369&group_id=168&atid=350207
    testRepeatCreate: function() {
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            ORBEON.xforms.Document.setValue("repeat-shown", "false");
        }, function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                ORBEON.xforms.Document.setValue("repeat-shown", "true");
            }, function() {
                var trigger = YAHOO.util.Dom.get(this.triggerId);
                var link = ORBEON.util.Utils.isNewXHTMLLayout()
                    ? YAHOO.util.Dom.getFirstChild(trigger) : trigger;
                YAHOO.util.Assert.areEqual("a", link.tagName.toLowerCase());
                YAHOO.util.Assert.areEqual("Label", link.innerHTML);
            });
        });
    }
}));

YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

    name: "xforms:output appearance=\"xxforms:download\"",

    outputFileId: "output-file-value" + XFORMS_SEPARATOR_1 + "1",

    // Test that the control is correctly restored when the iteration is recreated
    // http://forge.objectweb.org/tracker/index.php?func=detail&aid=313369&group_id=168&atid=350207
    testRepeatCreate: function() {
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            ORBEON.xforms.Document.setValue("repeat-shown", "false");
        }, function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                ORBEON.xforms.Document.setValue("repeat-shown", "true");
            }, function() {
                var control = document.getElementById(this.outputFileId);
                var children = YAHOO.util.Dom.getChildren(control);
                // Check we still have the link
                YAHOO.util.Assert.areEqual(1, children.length);
                var a = children[0];
                // The link points to a dynamic resource
                YAHOO.util.Assert.areNotEqual(-1, a.href.indexOf("/orbeon/xforms-server/dynamic/"));
                // The text for the link is still the same
                YAHOO.util.Assert.areEqual("Download file", ORBEON.util.Dom.getStringValue(a));
            });
        });
    }
}));

YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

    name: "Type change",

    testChangeToDate: function() {
        var input = YAHOO.util.Dom.get("type-change-input" + XFORMS_SEPARATOR_1 + "1");
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            YAHOO.util.UserAction.click("set-type-date" + XFORMS_SEPARATOR_1 + "1");
        }, function() {
            var inputInput = ORBEON.util.Dom.getChildElementByClass(input, "xforms-input-input");
            YAHOO.util.Assert.isTrue(YAHOO.util.Dom.hasClass(inputInput, "xforms-type-date"));
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                YAHOO.util.UserAction.click("set-type-float" + XFORMS_SEPARATOR_1 + "1");
            }, function() {
                var inputInput = ORBEON.util.Dom.getChildElementByClass(input, "xforms-input-input");
                YAHOO.util.Assert.isTrue(YAHOO.util.Dom.hasClass(inputInput, "xforms-type-string"));
            });
        });
    }
}));

ORBEON.xforms.Events.orbeonLoadedEvent.subscribe(function() {
    if (parent && parent.TestManager) {
        parent.TestManager.load();
    } else {
        new YAHOO.tool.TestLogger();
        YAHOO.tool.TestRunner.run();
    }
});

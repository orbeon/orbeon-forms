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
(function() {
    var OD = ORBEON.util.Dom;
    var OT = ORBEON.util.Test;
    var YA = YAHOO.util.Assert;
    var YD = YAHOO.util.Dom;

    YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

        name: "Full update",

        /**
         * Test a group around a group around a tr is correctly hidden and shown again when becoming disabled
         * and then back enabled.
         */
        testGroupAroundTr: function() {
            var groupBegin = document.getElementById("group-begin-group-update-full" + XFORMS_SEPARATOR_1 + "2");
            var button = OD.getElementByTagName(OD.get("toggle-two"), "button");
            OT.executeCausingAjaxRequest(this, function() {
                button.click();
            }, function() {
                var tr = YD.getNextSibling(groupBegin);
                YA.areEqual("tr", tr.tagName.toLowerCase());
                YA.isTrue(YD.hasClass(tr, "xforms-disabled"));
                OT.executeCausingAjaxRequest(this, function() {
                    button.click();
                }, function() {
                    var tr = YD.getNextSibling(groupBegin);
                    YA.areEqual("tr", tr.tagName.toLowerCase());
                    YA.isFalse(YD.hasClass(tr, "xforms-disabled"));
                });
            });
        },

        /**
         * Test full update of a case.
         */
        testCase: function() {
            var caseBegin = document.getElementById("xforms-case-begin-case-1");
            var button = OD.getElementByTagName(OD.get("increment-case-value"), "button");
            OT.executeCausingAjaxRequest(this, function() {
                button.click();
            }, function() {
                var span = YD.getNextSibling(caseBegin);
                YA.isTrue(YD.hasClass(span, "xforms-control"));
                YA.areEqual("2", ORBEON.xforms.Controls.getCurrentValue(span));
            });
        },

        /**
         * Test that after doing the innerHTML, we restore the focus to the control that previously had the focus.
         */
        testRestoreFocus: function() {
            OT.executeCausingAjaxRequest(this, function() {
                OD.getElementByTagName(document.getElementById("focus-restore"), "input").focus();
            }, function() {
                YA.areEqual(ORBEON.xforms.Globals.currentFocusControlElement,
                    document.getElementById("focus-restore"),
                    "focus is restored to first input box");
            });
        },

        /**
         * Test we don't have an error if the control that had the focus disappears.
         */
        testFocusNonRelevantNoError: function() {
            OT.executeCausingAjaxRequest(this, function() {
                OD.getElementByTagName(OD.get("focus-non-relevant-no-error"), "input").focus();
            }, function() {
                // nop
            });
        },

        /**
         * Test we don't have an error if the control that had the focus becomes readonly.
         */
        testFocusReadonlyNoError: function() {
            OT.executeCausingAjaxRequest(this, function() {
                OD.getElementByTagName(OD.get("focus-readonly-no-error"), "input").focus();
            }, function() {
                // nop
            });
        }
    }));

    OT.onOrbeonLoadedRunTest();
})();
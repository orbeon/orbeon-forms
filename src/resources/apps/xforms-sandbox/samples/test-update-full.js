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
    var OUD = ORBEON.util.Dom;
    var OUT = ORBEON.util.Test;
    var OXD = ORBEON.xforms.Document;
    var YUA = YAHOO.util.Assert;
    var YUD = YAHOO.util.Dom;

    YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

        name: "Full update",

        /**
         * Test a group around a group around a tr is correctly hidden and shown again when becoming disabled
         * and then back enabled.
         */
        testGroupAroundTr: function() {
            var groupBegin = document.getElementById("group-begin-group-update-full" + XFORMS_SEPARATOR_1 + "2");
            var button = OUD.getElementByTagName(OUD.get("toggle-two"), "button");
            OUT.executeCausingAjaxRequest(this, function() {
                button.click();
            }, function() {
                var tr = YUD.getNextSibling(groupBegin);
                YUA.areEqual("tr", tr.tagName.toLowerCase());
                YUA.isTrue(YUD.hasClass(tr, "xforms-disabled"));
                OUT.executeCausingAjaxRequest(this, function() {
                    button.click();
                }, function() {
                    var tr = YUD.getNextSibling(groupBegin);
                    YUA.areEqual("tr", tr.tagName.toLowerCase());
                    YUA.isFalse(YUD.hasClass(tr, "xforms-disabled"));
                });
            });
        },

        /**
         * Test full update of a case.
         */
        testCase: function() {
            var caseBegin = document.getElementById("xforms-case-begin-case-1");
            var button = OUD.getElementByTagName(OUD.get("increment-case-value"), "button");
            OUT.executeCausingAjaxRequest(this, function() {
                button.click();
            }, function() {
                var span = YUD.getNextSibling(caseBegin);
                YUA.isTrue(YUD.hasClass(span, "xforms-control"));
                YUA.areEqual("2", ORBEON.xforms.Controls.getCurrentValue(span));
            });
        },

        /**
         * Test that after doing the innerHTML, we restore the focus to the control that previously had the focus.
         */
        testRestoreFocus: function() {
            OUT.executeCausingAjaxRequest(this, function() {
                OUD.getElementByTagName(document.getElementById("focus-restore"), "input").focus();
            }, function() {
                YUA.areEqual(ORBEON.xforms.Globals.currentFocusControlElement,
                    document.getElementById("focus-restore"),
                    "focus is restored to first input box");
            });
        },

        /**
         * Test we don't have an error if the control that had the focus disappears.
         */
        testFocusNonRelevantNoError: function() {
            OUT.executeCausingAjaxRequest(this, function() {
                OUD.getElementByTagName(OUD.get("focus-non-relevant-no-error"), "input").focus();
            }, function() {
                // nop
            });
        },

        /**
         * Test we don't have an error if the control that had the focus becomes readonly.
         */
        testFocusReadonlyNoError: function() {
            OUT.executeCausingAjaxRequest(this, function() {
                OUD.getElementByTagName(OUD.get("focus-readonly-no-error"), "input").focus();
            }, function() {
                // nop
            });
        },

        /**
         * [ #315595 ] Full update: need to reset some server values on full update
         * http://forge.ow2.org/tracker/index.php?func=detail&aid=315595&group_id=168&atid=350207
         *
         * The client might think it known the server value for a control (A), because this is the last value it sent to
         * the server. But in fact it might be wrong if that control had a full update and its value was changed (to B)
         * during that full update. Then, if the user changes the value (back to A), the client might incorrectly
         * think it doesn't need to send the value because it hasn't changed.
         */
        testServerValueUserChangeSent: function() {
            OUT.executeSequenceCausingAjaxRequest(this, [[
                function() { OXD.setValue("server-value-input", "true"); },
                function() { YUA.areEqual("true", OXD.getValue("server-value-output"), "first set checkbox to true"); }
            ], [
                function() { OUT.click("server-value-false"); },
                function() { YUA.areEqual("false", OXD.getValue("server-value-output"), "false set with setvalue"); }
            ], [
                function() { OXD.setValue("server-value-input", "true"); },
                function() { YUA.areEqual("true", OXD.getValue("server-value-output"), "second set checkbox to true"); }
            ], [
                function() { OUT.click("server-value-false"); },
                function() { YUA.areEqual("false", OXD.getValue("server-value-output"), "reset to false to get back to initial state"); }
            ]]);
        }
    }));

    OUT.onOrbeonLoadedRunTest();
})();
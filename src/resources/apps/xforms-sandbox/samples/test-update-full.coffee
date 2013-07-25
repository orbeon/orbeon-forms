# Copyright (C) 2011 Orbeon, Inc.
#
# This program is free software; you can redistribute it and/or modify it under the terms of the
# GNU Lesser General Public License as published by the Free Software Foundation; either version
# 2.1 of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
# without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
# See the GNU Lesser General Public License for more details.
#
# The full text of the license is available at http://www.gnu.org/copyleft/lesser.html

OD = ORBEON.util.Dom
Test = ORBEON.util.Test
Document = ORBEON.xforms.Document
Assert = YAHOO.util.Assert
YD = YAHOO.util.Dom

YAHOO.tool.TestRunner.add new YAHOO.tool.TestCase

    name: "Full update"

    # Test a group around a group around a tr is correctly hidden and shown again when becoming disabled
    # and then back enabled.
    testGroupAroundTr: () ->
        groupBegin = document.getElementById "group-begin-group-update-full" + XF_REPEAT_SEPARATOR + "2"
        button = OD.getElementByTagName (OD.get "toggle-two"), "button"
        Test.executeSequenceCausingAjaxRequest this, [[
            () -> button.click()
            () ->
                tr = YD.getNextSibling groupBegin
                Assert.areEqual "tr", tr.tagName.toLowerCase()
                Assert.isTrue YD.hasClass tr, "xforms-disabled"
        ], [
            () -> button.click()
            () ->
                tr = YD.getNextSibling groupBegin
                Assert.areEqual "tr", tr.tagName.toLowerCase()
                Assert.isFalse YD.hasClass tr, "xforms-disabled"
        ]]

    # Test full update of a case.
    testCase: () ->
        caseBegin = document.getElementById "xforms-case-begin-case-1"
        button = OD.getElementByTagName (OD.get "increment-case-value"), "button"
        Test.executeSequenceCausingAjaxRequest this, [[
            () -> button.click()
            () ->
                span = YD.getNextSibling caseBegin
                Assert.isTrue YD.hasClass span, "xforms-control"
                Assert.areEqual "2", ORBEON.xforms.Controls.getCurrentValue span
        ]]

    # Test that after doing the innerHTML, we restore the focus to the control that previously had the focus.
    testRestoreFocus: () ->
        Test.executeSequenceCausingAjaxRequest this, [[
            () ->
                focusRestoreInput = OD.getElementByTagName (OD.get "focus-restore"), "input"
                focusRestoreInput.focus()
            () ->
                Assert.areEqual(ORBEON.xforms.Globals.currentFocusControlElement,
                    document.getElementById("focus-restore"),
                    "focus is restored to first input box")
        ]]

    # Test we don't have an error if the control that had the focus disappears.
    testFocusNonRelevantNoError: () ->
        Test.executeSequenceCausingAjaxRequest this, [[
            () ->
                nonRelevantInput = OD.getElementByTagName (OD.get "focus-non-relevant-no-error"), "input"
                nonRelevantInput.focus()
        ]]

    # Test we don't have an error if the control that had the focus becomes readonly.
    testFocusReadonlyNoError: () ->
        Test.executeSequenceCausingAjaxRequest this, [[
            () ->
                readonlyInput = OD.getElementByTagName (OD.get "focus-readonly-no-error"), "input"
                readonlyInput.focus()
        ]]

    # [ #315595 ] Full update: need to reset some server values on full update
    # http://forge.ow2.org/tracker/index.php?func=detail&aid=315595&group_id=168&atid=350207
    #
    # The client might think it known the server value for a control (A), because this is the last value it sent to
    # the server. But in fact it might be wrong if that control had a full update and its value was changed (to B)
    # during that full update. Then, if the user changes the value (back to A), the client might incorrectly
    # think it doesn't need to send the value because it hasn't changed.
    testServerValueUserChangeSent: () ->
        Test.executeSequenceCausingAjaxRequest this, [[
            () -> Document.setValue "server-value-input", "true"
            () -> Assert.areEqual "true", (Document.getValue "server-value-output"), "first set checkbox to true"
        ], [
            () -> Test.click "server-value-false"
            () -> Assert.areEqual "false", (Document.getValue "server-value-output"), "false set with setvalue"
        ], [
            () -> Document.setValue "server-value-input", "true"
            () -> Assert.areEqual "true", (Document.getValue "server-value-output"), "second set checkbox to true"
        ], [
            () -> Test.click "server-value-false"
            () -> Assert.areEqual "false", (Document.getValue "server-value-output"), "reset to false to get back to initial state"
        ]]

    # [ #315846 ] Dialog inside full update section not reinitialized
    testDialogInitialized: () ->
        Test.executeSequenceCausingAjaxRequest this, [[
            () -> addIteration = OD.getElementByTagName (OD.get "add-iteration"), "button"; addIteration.click()
            () -> Assert.isObject ORBEON.xforms.Globals.dialogs["dialog" + XF_REPEAT_SEPARATOR + "1"]
        ]]

Test.onOrbeonLoadedRunTest()
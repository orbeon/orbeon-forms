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

    name: "Full update",

    /**
     * Test a group around a group around a tr is correctly hidden and shown again when becoming disabled 
     * and then back enabled.
     */
    testGroupAroundTr: function() {
        var groupBegin = document.getElementById("group-begin-group-update-full" + XFORMS_SEPARATOR_1 + "2");
        var button = ORBEON.util.Dom.getElementByTagName(document.getElementById("toggle-two"), "button");
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            button.click();
        }, function() {
            var tr = YAHOO.util.Dom.getNextSibling(groupBegin);
            YAHOO.util.Assert.areEqual("tr", tr.tagName.toLowerCase());
            YAHOO.util.Assert.isTrue(YAHOO.util.Dom.hasClass(tr, "xforms-disabled"));
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                button.click();
            }, function() {
                var tr = YAHOO.util.Dom.getNextSibling(groupBegin);
                YAHOO.util.Assert.areEqual("tr", tr.tagName.toLowerCase());
                YAHOO.util.Assert.isFalse(YAHOO.util.Dom.hasClass(tr, "xforms-disabled"));
            });
        });
    },

    /**
     * Test full update of a case.
     */
    testCase: function() {
        var caseBegin = document.getElementById("xforms-case-begin-case-1");
        var button = ORBEON.util.Dom.getElementByTagName(document.getElementById("increment-case-value"), "button");
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            button.click();
        }, function() {
            var span = YAHOO.util.Dom.getNextSibling(caseBegin);
            YAHOO.util.Assert.isTrue(YAHOO.util.Dom.hasClass(span, "xforms-control"));
            YAHOO.util.Assert.areEqual("2", ORBEON.xforms.Controls.getCurrentValue(span));
        });
    },

    /**
     * Test that after doing the innerHTML, we restore the focus to the control that previously had the focus.
     */
    testRestoreFocus: function() {
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            ORBEON.util.Dom.getElementByTagName(document.getElementById("first-input"), "input").focus();
        }, function() {
            YAHOO.util.Assert.areEqual(ORBEON.xforms.Globals.currentFocusControlElement, 
                document.getElementById("first-input"), 
                "focus is restored to first input box");
        });
    },

    /**
     * Test we don't have an error if the control that had the focus disappears.
     */
    testFocusNoError: function() {
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            ORBEON.util.Dom.getElementByTagName(document.getElementById("second-input"), "input").focus();
        }, function() {
            // nop
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

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

(function() {
    var OD = ORBEON.util.Dom;
    var OT = ORBEON.util.Test;
    var YA = YAHOO.util.Assert;
    var YD = YAHOO.util.Dom;
    var YU = YAHOO.util.UserAction;

    YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

        name: "Button",

        // [ #315586 ] fr:button: if becomes non-readonly while non-relevant, when it becomes relevant, it still shows as readonly
        // http://forge.ow2.org/tracker/index.php?func=detail&aid=315586&group_id=168&atid=350207
        testNonReadonlyWhileNonRelevant: function() {
            OT.executeSequenceCausingAjaxRequest(this, [[
                function() { YU.click(OD.getElementByTagName(OD.get("toggle-readonly"), "button")); },
                function() {}
            ],[
                function() { YU.click(OD.getElementByTagName(OD.get("toggle-relevant"), "button")); },
                function() {}
            ], [
                function() { YU.click(OD.getElementByTagName(OD.get("toggle-readonly"), "button")); },
                function() {}
            ], [
                function() { YU.click(OD.getElementByTagName(OD.get("toggle-relevant"), "button")); },
                function() {
                    YA.isFalse(
                            YD.hasClass(YD.getElementsByClassName("yui-button", null, "my-button")[0], "yui-button-disabled"),
                            "button should not have the disabled class");
                    YA.isFalse(
                            OD.getElementByTagName(OD.get("my-button"), "button").disabled,
                            "button should not have the disabled attribute set");
                }
            ]]);
        }
    }));

    OT.onOrbeonLoadedRunTest();
})();
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
    var OUD = ORBEON.util.Dom;
    var OUT = ORBEON.util.Test;
    var YUA = YAHOO.util.Assert;
    var YUD = YAHOO.util.Dom;

    YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

        name: "Button",

        isButtonYuiDisabled: function(buttonId) { return YUD.hasClass(YUD.getElementsByClassName("yui-button", null, buttonId)[0], "yui-button-disabled"); },

        /**
         * Check that buttons is the expected state when it comes to them being readonly and relevant.
         *
         * @param {boolean} readonly    Should they be readonly?
         * @param {boolean} relevant    Should they be relevant?
         */
        checkButtonState: function(readonly, relevant) {
            return function() {
                _.each(["my-button-normal", "my-button-empty-label", "my-button-full-update"], function(buttonId) {
                    if (relevant) {
                        YUA.areEqual(readonly,
                            this.isButtonYuiDisabled(buttonId),
                            buttonId + ": class yui-button-disabled is not in the expected state");
                        var domButton = OUD.getElementByTagName(OUD.get(buttonId), "button");
                        YUA.areEqual(readonly, domButton.disabled,
                            buttonId + ": disabled attribute is not in the expected state");
                        YUA.isFalse(YUD.hasClass(domButton, "xforms-disabled-subsequent"),
                            buttonId + ": relevant button must not have disabled class");
                    }
                }, this);
                if (relevant)
                    YUA.areEqual(false,
                        this.isButtonYuiDisabled("my-button-in-group"),
                        "my-button-in-group: should never be disabled if relevant");
            };
        },

        toggleReadonly: _.bind(OUT.click, OUT, "toggle-readonly"),
        toggleRelevant: _.bind(OUT.click, OUT, "toggle-relevant"),
        triggerFullUpdate: _.bind(OUT.click, OUT, "trigger-full-update"),

        /**
         * [ #315586 ] fr:button: if becomes non-readonly while non-relevant, when it becomes relevant, it still shows as readonly
         * http://forge.ow2.org/tracker/index.php?func=detail&aid=315586&group_id=168&atid=350207
         */
        testNonReadonlyWhileNonRelevant: function() {
            (this.checkButtonState(false, true)).call(this);
            OUT.executeSequenceCausingAjaxRequest(this, [
                [ this.toggleReadonly, this.checkButtonState(true, true) ],
                [ this.toggleRelevant, this.checkButtonState(true, false) ],
                [ this.toggleReadonly, this.checkButtonState(false, false) ],
                [ this.toggleRelevant, this.checkButtonState(false, true) ]
            ]);
        },

        /**
         * Make sure the tabindex is correctly set on the HTML button.
         */
        testTabIndex: function() {
            function tabIndex(buttonId) { return OUD.get(buttonId).getElementsByTagName("button")[0].tabIndex; }
            YUA.areEqual(42, tabIndex("my-button-normal"));
            YUA.areEqual(0, tabIndex("my-button-full-update"));
        },

        /**
         * [ #315596 ] Full update: after full update init() is not automatically called on XBL components
         * http://forge.ow2.org/tracker/index.php?func=detail&aid=315596&group_id=168&atid=350207
         */
        testInitAfterFullUpdate: function() {
            OUT.executeSequenceCausingAjaxRequest(this, [
                [ this.toggleReadonly, this.checkButtonState(true, true) ],
                [ this.triggerFullUpdate, this.checkButtonState(true, true) ],
                [ this.toggleReadonly, this.checkButtonState(false, true) ]
            ]);
        },

        /**
         * [ #315870 ] fr:button: if a readonly group, when becomes non-relevant, and relevant again, button shows readonly
         * http://forge.ow2.org/tracker/index.php?func=detail&aid=315870&group_id=168&atid=350207
         */
        testGroupReadonly: function() {
            OUT.executeSequenceCausingAjaxRequest(this, [
                [ this.toggleReadonly, this.checkButtonState(true, true) ],
                [ this.toggleRelevant, this.checkButtonState(true, false) ],
                [ this.toggleRelevant, this.checkButtonState(true, true) ],
                [ this.toggleReadonly, this.checkButtonState(false, true) ]
            ]);
        },

        /**
         * Check the button is properly initialized when added to a repeat. We check that it is initialized by checking
         * that the class yui-button-disabled has been appropriately set.
         */
        testInitInRepeat: function() {
            function checkIsDisabled(count) {
                for (var index = 1; index < count; index++)
                    YUA.isTrue(this.isButtonYuiDisabled("init-in-repeat" + XFORMS_SEPARATOR_1 + index),
                            "button " + index + " is expected to be disabled");
            }
            checkIsDisabled.call(this, 1);
            OUT.executeSequenceCausingAjaxRequest(this, [
                [ _.bind(OUT.click, OUT, "init-add"), _.bind(checkIsDisabled, this, 2) ],
                [ _.bind(OUT.click, OUT, "init-add"), _.bind(checkIsDisabled, this, 3) ]
            ]);
        }
    }));

    OUT.onOrbeonLoadedRunTest();
})();
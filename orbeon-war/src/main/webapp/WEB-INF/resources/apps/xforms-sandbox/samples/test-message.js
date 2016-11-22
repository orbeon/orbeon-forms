(function() {

    var Assert = YAHOO.util.Assert;
    var Test = ORBEON.util.Test;
    var OD = ORBEON.util.Dom;
    var YD = YAHOO.util.Dom;

    YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

        name: "Message action",

        /**
         * Checks the specified message is display, and close the message box.
         *
         * @param {string} text
         */
        checkDisplayedMessage: function(text) {
            var dialog = OD.get("xforms-message-dialog");
            Assert.areEqual("visible", dialog.parentNode.style.visibility, "dialog is visible");
            var body = YD.getElementsByClassName("bd", null, dialog)[0];
            var span = OD.getChildElementByIndex(body, 0);
            Assert.areEqual(text, OD.getStringValue(span), "unexpected message in dialog");
            dialog.getElementsByTagName("button")[0].click();
        },

        /**
         * Check we are able to show multiple messages in a row when a page is first loaded.
         */
        testInitial: function() {
            this.checkDisplayedMessage("Message 1");
            this.checkDisplayedMessage("Message 2");
        },

        /**
         * Check a that 2 messages are shown in a row after clicking on a button.
         */
        testSubsequent: function() {
            Test.executeCausingAjaxRequest(this, function() {
                Test.click("show-messages");
            }, function() {
                this.checkDisplayedMessage("Message 3");
                this.checkDisplayedMessage("Message 4");
            });
        }
    }));

    Test.onOrbeonLoadedRunTest();
})();

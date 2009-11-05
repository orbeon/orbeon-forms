YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

    name: "Dialog",

    testShowHideMinimalDailog: function() {
        var dialogContainer = YAHOO.util.Dom.get("minimal-dialog_c");
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            YAHOO.util.UserAction.click("show-minimal-dialog");
        }, function() {
            YAHOO.util.Assert.areEqual("block", dialogContainer.style.display, "dialog shown after click on trigger");
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                YAHOO.util.UserAction.click(document.body);
            }, function() {
                YAHOO.util.Assert.areEqual("none", dialogContainer.style.display, "dialog hidden after click on body");
            });
        });

    },

    testMinimalDialogNoTitle: function() {
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            YAHOO.util.UserAction.click("show-minimal-dialog");
        }, function() {
            var header = YAHOO.util.Dom.getElementsByClassName("hd", null, "minimal-dialog")[0];
            YAHOO.util.Assert.areEqual(null, header, "minimal dialog must not have header");
            YAHOO.util.UserAction.click(document.body); // Close dialog to get back to initial state
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

YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

    name: "Repeat",

    /**
     * Hide repeat and show it again. At this point the server value is stored. Then we hide and show
     * another time. When we show it, if the server value was kept, no update will be done, and the
     * fields will show empty. We are testing here that the value is indeed updated.
     */
    testSetValueAfterRecreate: function(htmlIn, htmlOut) {
        var testCase = this;
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            ORBEON.xforms.Document.setValue("show", "false");
        }, function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                ORBEON.xforms.Document.setValue("show", "true");
            }, function() {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    ORBEON.xforms.Document.setValue("show", "false");
                }, function() {
                    ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                        ORBEON.xforms.Document.setValue("show", "true");
                    }, function() {
                        var actualValue = ORBEON.xforms.Document.getValue("name" + XFORMS_SEPARATOR_1 + "1");
                        YAHOO.util.Assert.areEqual("Wal Mart", actualValue);
                    });
                
                });
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

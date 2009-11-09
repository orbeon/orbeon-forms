YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

    name: "Repeat",

    /**
     * Hide repeat and show it again. At this point the server value is stored. Then we hide and show
     * another time. When we show it, if the server value was kept, no update will be done, and the
     * fields will show empty. We are testing here that the value is indeed updated.
     */
    xtestSetValueAfterRecreate: function(htmlIn, htmlOut) {
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
    },

    testObserverInRepeats: function() {
        var delimiters = YAHOO.util.Dom.getElementsByClassName("xforms-repeat-delimiter", null, "table");
        var firstInput = YAHOO.util.Dom.get("name" + XFORMS_SEPARATOR_1 + "1").getElementsByTagName("input")[0];
        var thirdInput = YAHOO.util.Dom.get("name" + XFORMS_SEPARATOR_1 + "3").getElementsByTagName("input")[0];
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            firstInput.focus();
        }, function() {
            YAHOO.util.Assert.areEqual(delimiters[0], window.observerElement, "delimiter before first input");
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                thirdInput.focus();
            }, function() {
                YAHOO.util.Assert.areEqual(delimiters[2], window.observerElement, "delimiter before first input");
            });
        });
    },
    
    testObserverInRepeatsObserver: function() {
        var beginEnds = YAHOO.util.Dom.getElementsByClassName("xforms-repeat-begin-end", null, "table-observer");
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            ORBEON.xforms.Document.setValue("show", "false");
        }, function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                ORBEON.xforms.Document.setValue("show", "true");
            }, function() {
                YAHOO.util.Assert.areEqual(beginEnds[0], window.observerElement, "xforms-repeat-begin-end");
            });
        });
    },
    
    testObserverInRepeatsInXbl: function() {
        var delimiters = YAHOO.util.Dom.getElementsByClassName("xforms-repeat-delimiter", null, "table-xbl");
        var firstInput = YAHOO.util.Dom.get("xbl-component$name-xbl" + XFORMS_SEPARATOR_1 + "1").getElementsByTagName("input")[0];
        var thirdInput = YAHOO.util.Dom.get("xbl-component$name-xbl" + XFORMS_SEPARATOR_1 + "3").getElementsByTagName("input")[0];
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            firstInput.focus();
        }, function() {
            YAHOO.util.Assert.areEqual(delimiters[0], window.observerElement, "delimiter before first input");
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                thirdInput.focus();
            }, function() {
                YAHOO.util.Assert.areEqual(delimiters[2], window.observerElement, "delimiter before first input");
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

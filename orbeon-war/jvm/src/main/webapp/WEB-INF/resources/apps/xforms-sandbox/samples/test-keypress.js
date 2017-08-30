YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

    name: "Key press listeners",

    /**
     * Simulate click on "Reset keypress" to blank "name" field at the beginning of a test.
     */
    resetKeyPress: function(continuation) {
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            ORBEON.util.Dom.getElementByTagName(YAHOO.util.Dom.get("reset-keypress"), "button").click();
        }, function() {
            continuation.call(this);
        });
    },

    testDivKeyPress: function() {
        this.resetKeyPress(function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                var htmlInput = ORBEON.util.Dom.getElementByTagName(YAHOO.util.Dom.get("input"), "input");
                YAHOO.util.UserAction.keydown(htmlInput, { keyCode: "U".charCodeAt(0), ctrlKey: true });
            }, function() {
                YAHOO.util.Assert.areEqual("div", ORBEON.xforms.Document.getValue("keypress"));
            });
        });
    },
        
    testEmptyKeyPress: function() {
        this.resetKeyPress(function() {
            YAHOO.util.Assert.areEqual("", ORBEON.xforms.Document.getValue("keypress"));
        });
    },
    
    testDialogKeyPress: function() {
        this.resetKeyPress(function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                ORBEON.util.Dom.getElementByTagName(YAHOO.util.Dom.get("show-dialog"), "button").click();
            }, function() {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    YAHOO.util.UserAction.keydown(document, { keyCode: "I".charCodeAt(0), ctrlKey: true });
                }, function() {
                    YAHOO.util.Assert.areEqual("dialog1", ORBEON.xforms.Document.getValue("keypress"));
                    ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                        YAHOO.util.UserAction.keydown(document, { keyCode: "J".charCodeAt(0) });
                    }, function() {
                        YAHOO.util.Assert.areEqual("dialog2", ORBEON.xforms.Document.getValue("keypress"));
                        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                            ORBEON.util.Dom.getElementByTagName(YAHOO.util.Dom.get("hide-dialog"), "button").click();
                        }, function() {
                        });
                    });
                });
            });
        });
    },
    
    testDocumentKeyPressNotListeningDiv: function() {
        this.resetKeyPress(function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                YAHOO.util.UserAction.keydown(document, { keyCode: "U".charCodeAt(0), ctrlKey: true });
            }, function() {
                YAHOO.util.Assert.areEqual("", ORBEON.xforms.Document.getValue("keypress"));
            });
        });
    },
    
    testDocumentKeyPressNotListeningDialog: function() {
        this.resetKeyPress(function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                YAHOO.util.UserAction.keydown(document, { keyCode: "I".charCodeAt(0), ctrlKey: true });
            }, function() {
                YAHOO.util.Assert.areEqual("", ORBEON.xforms.Document.getValue("keypress"));
            });
        });
    },
    
    testDocumentKeyPress: function() {
        this.resetKeyPress(function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                YAHOO.util.UserAction.keydown(document, { keyCode: "Y".charCodeAt(0), ctrlKey: true, shiftKey: true });
            }, function() {
                YAHOO.util.Assert.areEqual("document", ORBEON.xforms.Document.getValue("keypress"));
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

(function() {

    var Assert = YAHOO.util.Assert;
    var Events = ORBEON.xforms.Events;
    var Test = ORBEON.util.Test;
    var OD = ORBEON.util.Dom;
    var YD = YAHOO.util.Dom;

    YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

        name: "AJAX error",

        doTest: function(type) {
            var listenerCalled = false;
            var showErrorDialog = (type % 2) < 1;
            var registerListener = (type % 4) < 2;
            Test.executeCausingAjaxRequest(this, function() {
                // Do we want the error dialog to be shown?
                opsXFormsProperties[ORBEON.util.Properties.showErrorDialog.name] = showErrorDialog;
                // Register or un-register listener
                registerListener
                        ? Events.errorEvent.subscribe(function() { listenerCalled = true; })
                        : Events.errorEvent.unsubscribeAll();
                // Click server-side or client-side error button
                (OD.get("triggers").getElementsByTagName("button")[type < 4 ? 0 : 1]).click();
            }, function() {
                // Determine if the error panel is visible
                var errorPanel = YD.getElementsByClassName("xforms-error-panel")[0];
                var errorPanelContainer = errorPanel.parentNode;
                var visibility = YD.getStyle(errorPanelContainer.id, "visibility");
                // Hide error panel in case it was visible
                var form = ORBEON.xforms.Controls.getForm(errorPanel);
                ORBEON.xforms.Globals.formErrorPanel[form.id].hide();
                // Check visibility and listener called are what we expected
                Assert.areEqual(showErrorDialog ? "visible" : "hidden", visibility);
                Assert.areEqual(registerListener, listenerCalled, "listener called");
            });
        },

        testServerSideShowErrorDialogWithListenerRegistered:    function() { this.doTest(0); },
        testServerSideHideErrorDialogWithListenerRegistered:    function() { this.doTest(1); },
        testServerSideShowErrorDialogWithListenerNotRegistered: function() { this.doTest(2); },
        testServerSideHideErrorDialogWithListenerNotRegistered: function() { this.doTest(3); },
        testClientSideShowErrorDialogWithListenerRegistered:    function() { this.doTest(4); },
        testClientSideHideErrorDialogWithListenerRegistered:    function() { this.doTest(5); },
        testClientSideShowErrorDialogWithListenerNotRegistered: function() { this.doTest(6); },
        testClientSideHideErrorDialogWithListenerNotRegistered: function() { this.doTest(7); }
    }));

    Test.onOrbeonLoadedRunTest();
})();

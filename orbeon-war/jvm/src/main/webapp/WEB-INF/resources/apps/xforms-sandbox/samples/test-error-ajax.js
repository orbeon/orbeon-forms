(function() {

    var Assert = YAHOO.util.Assert;
    var Events = ORBEON.xforms.Events;
    var Test = ORBEON.util.Test;
    var OD = ORBEON.util.Dom;
    var YD = YAHOO.util.Dom;

    YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

        name: "AJAX error",


        testErrorDialog: function() {

            function partialTest(type) {
                var listenerCalled = false;
                var showErrorDialog = (type & (1 << 0)) != 0;
                var registerListener = (type & (1 << 1)) != 0;
                var buttonIndex = type >> 2;
                return [
                    function() {
                        // Do we want the error dialog to be shown?
                        opsXFormsProperties[ORBEON.util.Properties.showErrorDialog.name] = showErrorDialog;
                        // Register or un-register listener
                        registerListener
                                ? Events.errorEvent.subscribe(function() { listenerCalled = true; })
                                : Events.errorEvent.unsubscribeAll();
                        // Click server-side or client-side error button
                        (OD.get("triggers").getElementsByTagName("button")[buttonIndex]).click();
                    },
                    function () {
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
                    }
                ];
            }

            // Get tests for all possible types
            var tests = [];
            var maxType = (2 << 2) | 1 << 1 | 1;
            for (var type = 0; type <= maxType; type++) {
                var partial = partialTest(type);
                tests.push(partial[0]);
                tests.push(partial[1]);
            }

            // Run tests
            Test.runMayCauseXHR(this, tests);
        }
    }));

    Test.onOrbeonLoadedRunTest();
})();

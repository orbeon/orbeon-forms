YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

    name: "fr:date-picker",

    // Check initial date
    testInitialValue: function() {
        var output = YAHOO.util.Dom.getElementsByClassName("xforms-output-output", null, "date-picker")[0];
        YAHOO.util.Assert.areEqual("Friday May 15, 2009 ", output.innerHTML);
    },
    
    // Check date change by interacting with date picker
    testChangeDatePicker: function() {
        // Click on image to open YUI date picker
        var datePickerImage = YAHOO.util.Dom.getElementsByClassName("xforms-input-appearance-minimal", "img", "date-picker")[0];
        ORBEON.xforms.Events.click({target: datePickerImage });
        this.wait(function() {
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                // Click on day 20
                var day20 = YAHOO.util.Dom.getElementsByClassName("d20", null, "orbeon-calendar-div")[0];
                YAHOO.util.UserAction.click(day20);
                // Copy new value to input
                YAHOO.util.UserAction.click(YAHOO.util.Dom.get("copy-to-input"));
            }, function() {
                // Check that date picker output is updated
                var output = YAHOO.util.Dom.getElementsByClassName("xforms-output-output", null, "date-picker")[0];
                YAHOO.util.Assert.areEqual("Wednesday May 20, 2009 ", output.innerHTML);
                // Check input is updated
                var input = YAHOO.util.Dom.get("input$xforms-input-1");
                YAHOO.util.Assert.areEqual("2009-05-20", input.value);
            });
            
        }, XFORMS_INTERNAL_SHORT_DELAY_IN_MS);
    },
    
    testChangeInput: function() {
        ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
            // Set value of input and copy to date picker
            ORBEON.xforms.Document.setValue("input", "2009-06-25");
            YAHOO.util.UserAction.click(YAHOO.util.Dom.get("copy-to-date-picker"));
        }, function() {
            // Check that date picker output is updated
            var output = YAHOO.util.Dom.getElementsByClassName("xforms-output-output", null, "date-picker")[0];
            YAHOO.util.Assert.areEqual("Thursday June 25, 2009 ", output.innerHTML);
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

YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

    name: "Group delimiters",
    counter: 0,

    worker: function(count, increment) {

        function setEnabled(count) {
            this.counter++;
            ORBEON.xforms.Document.setValue("count", this.counter);
            var enabled = [];
            for (var enabledIndex = 0; enabledIndex < 4; enabledIndex++) {
                var mask = Math.pow(2, enabledIndex);
                var isEnabled = (count & mask) == mask;
                enabled[3 - enabledIndex] = isEnabled;
                ORBEON.xforms.Document.setValue("level-enabled" + XF_REPEAT_SEPARATOR + (3 - enabledIndex + 1), isEnabled ? "true" : "false");
            }
            return enabled;
        }

        if (count < 16*16) {
            var enabled = [];
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                // Set first position of enabled
                setEnabled.call(this, Math.floor(count / 16));
            }, function() {
                ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                    // Set second position of enabled
                    enabled = setEnabled.call(this, count % 16);
                }, function() {
                    // Check that the tr visibility is as expected
                    for (var position = 0; position < 2; position++) {
                        var trID = "tr-" + (position + 1);
                        var trIsEnabled = ! YAHOO.util.Dom.hasClass(trID, "xforms-disabled")
                                       && ! YAHOO.util.Dom.hasClass(trID, "xforms-disabled-subsequent");
                        var trShouldBeEnabled = enabled[0] && enabled[1] && enabled[2 + position];
                        YAHOO.util.Assert.areEqual(trShouldBeEnabled, trIsEnabled);
                    }
                    this.worker(count + 1);
                });
            });
        }
    },

    testGroupDelimiters: function() {
        this.worker(0);
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

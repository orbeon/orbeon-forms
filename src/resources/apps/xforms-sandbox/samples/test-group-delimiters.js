YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

    name: "Group delimiters",

    worker: function(count) {
        if (count <= 50) {
            var allEnabled = true;
            ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                // Set enabled status of the 3 level to a random value
                function setEnabled(level) {
                    var thisEnabled = Math.random() > 0.5;
                    ORBEON.xforms.Document.setValue("level-enabled" + XFORMS_SEPARATOR_1 + level, thisEnabled);
                    if (! thisEnabled) allEnabled = false;
                }
                ORBEON.xforms.Document.setValue("count", count);
                setEnabled(1); setEnabled(2); setEnabled(3);
            }, function() {
                var trIsDisabled = YAHOO.util.Dom.hasClass("tr", "xforms-disabled") || YAHOO.util.Dom.hasClass("tr", "xforms-disabled-subsequent");
                YAHOO.util.Assert.areEqual(allEnabled, ! trIsDisabled);
                this.worker(count + 1);
            });
        }
    },

    testGroupDelimiters: function() {
        this.worker(1);
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

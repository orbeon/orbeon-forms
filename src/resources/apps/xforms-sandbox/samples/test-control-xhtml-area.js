YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

    name: "HTML Area",

    settingValue: function(htmlIn, htmlOut) {
        var testCase = this;
        window.setTimeout(function() {
            ORBEON.widgets.RTE.onRendered(ORBEON.util.Dom.get("xhtml-editor"), function() {
                testCase.resume(function() {
                    ORBEON.util.Test.executeCausingAjaxRequest(this, function() {
                        ORBEON.xforms.Document.setValue("xhtml-editor", htmlIn);
                    }, function() {
                        var htmlActualOut = ORBEON.xforms.Document.getValue("xhtml-textarea");
                        var htmlNormalizedOut = YAHOO.lang.trim(htmlActualOut).replace(new RegExp("  +", "g"), " ");
                        YAHOO.util.Assert.areEqual(htmlOut, htmlNormalizedOut);
                    });
                });
            });
        }, ORBEON.util.Properties.internalShortDelay.get());
        testCase.wait();
    },

    testSimpleHTML: function() {
        var simpleHTML = "Some different <b>content</b>.";
        this.settingValue(simpleHTML, simpleHTML);
    },

	testWordHTML: function() {
        this.settingValue(
                "<p class=MsoNormal align=center"+
                    " style='margin-bottom:0in;margin-bottom:.0001pt;text-align:center;line-height:normal'><b"+
                    " style='mso-bidi-font-weight:normal'><u><span"+
                    " style='font-size:14.0pt;mso-bidi-font-size:11.0pt;mso-fareast-font-family:&quot;Times New"+
                    " Roman&quot;;mso-bidi-font-family:&quot;Times New Roman&quot;;color:#0070C0'>Project"+
                    " Description<o:p></o:p></span></u></b></p>",
                "<p align=\"center\" class=\"MsoNormal\" style=\"margin-bottom:0in;margin-bottom:.0001pt;text-align:center;line-height:normal\"><b" +
                    " style=\"mso-bidi-font-weight:normal\"><u><span" +
                    " style=\"font-size:14.0pt;mso-bidi-font-size:11.0pt;mso-fareast-font-family:&quot;Times New" +
                    " Roman&quot;;mso-bidi-font-family:&quot;Times New Roman&quot;;color:#0070C0\">Project" +
                    " Description</span></u></b></p>");
    },

	testJSInjection: function() {
        this.settingValue(
                "<div>Text to keep<scr" + "ipt>doSomethingBad()</scr" + "ipt></div>",
                "<div>Text to keep</div>");
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

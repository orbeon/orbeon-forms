ORBEON.testcases = {


    dateTimeTestCase: new YAHOO.tool.TestCase({

        name: "Library doing conversions between different formats of date and time",
        opsXFormsProperties: {},

        setUp: function() {
            this.opsXFormsProperties = window.opsXFormsProperties;
        },

        tearDown: function() {
            window.opsXFormsProperties = this.opsXFormsProperties;
        },

        testMagicTimeSimple: function() {
            var jsDate = ORBEON.util.DateTime.magicTimeToJSDate("11:22:33");
            YAHOO.util.Assert.areEqual(11, jsDate.getHours());
            YAHOO.util.Assert.areEqual(22, jsDate.getMinutes());
            YAHOO.util.Assert.areEqual(33, jsDate.getSeconds());
        },

        testMagicTime24h: function() {
            var jsDate = ORBEON.util.DateTime.magicTimeToJSDate("20:22:33");
            YAHOO.util.Assert.areEqual(20, jsDate.getHours());
        },

        testMagicTimeUSHour: function() {
            var jsDate = ORBEON.util.DateTime.magicTimeToJSDate("9:05 pm");
            YAHOO.util.Assert.areEqual(21, jsDate.getHours());
        },

        testMagicInvalidTime: function() {
            YAHOO.util.Assert.isNull(ORBEON.util.DateTime.magicTimeToJSDate("gaga"));
        },

        testDisplayTimeUSMorning: function() {
            window.opsXFormsProperties = {};
            window.opsXFormsProperties[FORMAT_INPUT_TIME_PROPERTY] = "[h]:[m]:[s] [P]";
            var jsDate = ORBEON.util.DateTime.magicTimeToJSDate("11:22:33");
            var displayTime = ORBEON.util.DateTime.jsDateToformatDisplayTime(jsDate);
            YAHOO.util.Assert.areEqual("11:22:33 a.m.", displayTime);
        },

        testDisplayTimeUSAfternoon: function() {
            window.opsXFormsProperties = {};
            window.opsXFormsProperties[FORMAT_INPUT_TIME_PROPERTY] = "[h]:[m]:[s] [P]";
            var jsDate = ORBEON.util.DateTime.magicTimeToJSDate("16:22:33");
            var displayTime = ORBEON.util.DateTime.jsDateToformatDisplayTime(jsDate);
            YAHOO.util.Assert.areEqual("4:22:33 p.m.", displayTime);
        },

        testToISOTime: function() {
            var jsDate = ORBEON.util.DateTime.magicTimeToJSDate("20:22:33");
            YAHOO.util.Assert.areEqual("20:22:33", ORBEON.util.DateTime.jsDateToISOTime(jsDate));
        }

    }),

    inputTestCase: new YAHOO.tool.TestCase({

        name: "XForms Input",

        testSimpleInput: function() {

            function ajaxReceived() {
                this.resume(function() {
                    ORBEON.xforms.Events.ajaxResponseProcessedEvent.unsubscribe(ajaxReceived);
                    YAHOO.util.Assert.areEqual("gaga", ORBEON.xforms.Document.getValue("input-field"));
                });
            }

            ORBEON.xforms.Events.ajaxResponseProcessedEvent.subscribe(ajaxReceived, this, true);
            ORBEON.xforms.Document.setValue("input-type", "string");
            ORBEON.xforms.Document.setValue("input-field", "gaga");
            this.wait();
        }
    })

};

ORBEON.testing = {

    run: function() {
        var testLogger = new YAHOO.tool.TestLogger();
        for (var testcaseID in ORBEON.testcases)
            YAHOO.tool.TestRunner.add(ORBEON.testcases[testcaseID]);
        YAHOO.tool.TestRunner.run();
    }
}
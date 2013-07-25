# Copyright (C) 2011 Orbeon, Inc.
#
# This program is free software; you can redistribute it and/or modify it under the terms of the
# GNU Lesser General Public License as published by the Free Software Foundation; either version
# 2.1 of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
# without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
# See the GNU Lesser General Public License for more details.
#
# The full text of the license is available at http://www.gnu.org/copyleft/lesser.html

OD = ORBEON.util.Dom
Test = ORBEON.util.Test
Document = ORBEON.xforms.Document
Assert = YAHOO.util.Assert
YD = YAHOO.util.Dom
DateTime = ORBEON.util.DateTime

YAHOO.tool.TestRunner.add new YAHOO.tool.TestCase

    name: "Date control (including on iOS)"

    isIOS: () -> YD.hasClass document.body, "xforms-ios"

    # Checks the values of the input fields is what we expect based on what the output shows
    checkValues: (iteration) ->
        controlIds = ["date", "time", "dateTime"]
        for controlId in controlIds
            [inputControl, outputControl] = (YD.get controlId + "-" + type + "⊙" + iteration for type in ["input", "output"])
            outputValue = Document.getValue outputControl
            isoDateTimes = outputValue.split "T"
            for input in inputControl.getElementsByTagName "input"
                expectedValue =
                    if YD.hasClass input, "xforms-type-date"
                        iso = isoDateTimes[0]
                        if @isIOS() then iso else DateTime.jsDateToFormatDisplayDate DateTime.magicDateToJSDate iso
                    else if YD.hasClass input, "xforms-type-time"
                        iso = isoDateTimes[isoDateTimes.length - 1]
                        if @isIOS() then iso else DateTime.jsDateToFormatDisplayTime DateTime.magicTimeToJSDate iso
                Assert.areEqual expectedValue, input.value

    # Initial value should be formatted on desktop browsers and ISO on iOS
    testInitialValue: ->
        Test.runMayCauseXHR this,
            -> @checkValues "1"
            -> Test.click "add"
            -> @checkValues "2"
            -> Test.click "remove"

    # After an increment, values are still formatted on desktop browsers and ISO on iOS
    testValueAfterIncrement: ->
        Test.runMayCauseXHR this,
            -> Test.click "increment-date-time⊙1"
            -> @checkValues "1"
            -> Test.click "add"
            -> Test.click "increment-date-time⊙2"
            -> @checkValues "2"
            -> Test.click "remove"
            -> Test.click "reset-date-time⊙1"

Test.onOrbeonLoadedRunTest()


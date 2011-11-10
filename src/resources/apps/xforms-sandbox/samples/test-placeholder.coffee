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

YAHOO.tool.TestRunner.add new YAHOO.tool.TestCase

    name: "Input placeholder"

    browserSupportsPlaceholder: do ->
        input = document.createElement "input"
        input.placeholder?

    assertPlaceholderShown: (control, placeholder) ->
        input = (control.getElementsByTagName "input")[0]
        Assert.areEqual placeholder, input.placeholder, "placeholder attribute has the correct value"
        if @browserSupportsPlaceholder
            Assert.areEqual "", input.value, "input should have no value"
        else
            Assert.isTrue (YD.hasClass control, "xforms-placeholder"), "has placeholder class"
            Assert.areEqual placeholder, input.value, "input should have placeholder value"

    assertContentShown: (control, content) ->
        input = (control.getElementsByTagName "input")[0]
        Assert.isFalse (YD.hasClass control, "xforms-placeholder"), "doesn't have placeholder class"
        Assert.areEqual content, input.value, "input must show content"

    assertBlockShows: (index, values) ->
        prefixes = ["static-label·", "static-hint·", "dynamic-label·", "dynamic-hint·"]
        for i in [0..3]
            value = values[i]
            control = YD.get (prefixes[i] + index)
            @assertPlaceholderShown control, value.placeholder if value.placeholder?
            @assertContentShown control, value.content if value.content?

    # Initially placeholders must be shown
    testPlaceholderShown: ->
        placeholders = [{placeholder: "First name"}, {placeholder: "First name"}, {placeholder: "1"}, {placeholder: "1"}]
        Test.runMayCauseXHR this, [
            => @assertBlockShows 1, placeholders
            => Test.click "add"
            => @assertBlockShows 2, placeholders
            => Test.click "remove"
        ]

    # Setting the content hides the placeholders
    testContentShown: ->
        content = [{content: "1"}, {content: "1"}, {content: "1"}, {content: "1"}]
        Test.runMayCauseXHR this, [
            => Test.click "increment-content·1"
            => @assertBlockShows 1, content
            => Test.click "reset-content·1"
            => Test.click "add"
            => Test.click "increment-content·2"
            => @assertBlockShows 2, content
            => Test.click "remove"
        ]

    # Setting the focus on a the first name hides the placeholder
    testFocusNoPlaceholder: ->
        focusOnFirst = [{content: ""}, {placeholder: "First name"}, {placeholder: "1"}, {placeholder: "1"}]
        Test.runMayCauseXHR this, [
            => (YD.get "static-label$xforms-input-1·1").focus()
            => @assertBlockShows 1, focusOnFirst
            => Test.click "add"
            => (YD.get "static-label$xforms-input-1·2").focus()
            => @assertBlockShows 2, focusOnFirst
            => Test.click "remove"
            => (YD.get "add").focus()
        ]



Test.onOrbeonLoadedRunTest()


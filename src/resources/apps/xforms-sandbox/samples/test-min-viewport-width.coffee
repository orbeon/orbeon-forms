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

    name: "Viewport minimum width"

    # Check that the markup Orbeon Forms generates doesn't imposes any constraint on the minimum width or height of the
    # page, which would create scroll bars scrolling to "nothing".
    testNoScroll: () ->
        # Add a div just under body that contain everything previously under body
        topLevelContainer = document.createElement("div")
        topLevelContainer.className = "test-top-level-div"
        topLevelContainer.appendChild topLevel for topLevel in (YD.getChildren document.body) when not YD.hasClass topLevel, "yui-log"
        document.body.appendChild topLevelContainer
        # Check there are no elements we need to scroll to
        region = YD.getRegion topLevelContainer
        Assert.isTrue topLevelContainer.clientHeight >= topLevelContainer.scrollHeight, "Scroll height should be same as client height"
        Assert.isTrue topLevelContainer.clientWidth >= topLevelContainer.scrollWidth, "Scroll width should be the same as client width"

Test.onOrbeonLoadedRunTest()


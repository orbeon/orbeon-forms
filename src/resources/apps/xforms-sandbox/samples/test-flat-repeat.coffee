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

    name: "Flat repeat"

    testHiddenTemplateShowsWhenInserted: ->
        isCatVisible = (position) ->
            cat = YD.get ("cat" + XFORMS_SEPARATOR_1 + position)
            not (_.any ["xforms-disabled", "xforms-disabled-subsequent"], (c) -> YD.hasClass cat.parentElement, c)

        Test.runMayCauseXHR this, [
            -> Test.click "show"
            -> Assert.isTrue (isCatVisible 1), "Showing the group, the first cat should be there"
            -> Test.click "add"
            -> Assert.isTrue (isCatVisible 2), "The copy of a template with xforms-disabled must be visible"
            -> Test.click "hide"
            -> Test.click "show"
            -> Test.click "add"
            -> Assert.isTrue (isCatVisible 3), "The copy of a template with xforms-disabled-subsequent must be visible"
        ]

Test.onOrbeonLoadedRunTest()
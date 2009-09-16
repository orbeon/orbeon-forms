/**
 * Copyright (C) 2009 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */

YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase({

    name: "Loading indicator",

    testPosition: function() {
        var thiss = this;
        window.resizeBy(200, 0);
        thiss.wait( function() {
            var target =  YAHOO.util.Dom.get('my-trigger');
            var region = YAHOO.util.Region.getRegion(target);
            YAHOO.util.UserAction.mouseover(target, {clientX: region.right, clientY: region.top});
            thiss.wait (function() {
                YAHOO.util.UserAction.mouseout(target, {clientX: region.right - 10 , clientY: region.top});
                thiss.wait(function() {
                    window.resizeBy(-200, 0);
                    thiss.wait(function() {
                        YAHOO.util.Assert.areEqual(document.documentElement.clientWidth, document.documentElement.scrollWidth, "There shouldn't be an horizontal scrollbar (clientWidth: " + document.documentElement.clientWidth + ', scrollWidth: ' + document.documentElement.scrollWidth + ').');
                    }, 300);
                }, 300);
            }, 300);
        }, 300);

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

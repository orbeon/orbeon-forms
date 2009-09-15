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

    test314235: function() {
        thiss = this;
        window.resizeBy(200, 0);
        xformsDisplayIndicator("loading", "test");  // set the loading indicator
        xformsDisplayIndicator("none");             // hide it
        var formId = "xforms-form";
        var overlay = ORBEON.xforms.Globals.formLoadingLoadingOverlay[formId];
        var x0 = overlay.cfg.getProperty("x");      // store its location
        var y0 = overlay.cfg.getProperty("y");
        window.resizeBy(-200, 0);                   // resize the window
        thiss.wait  (function() {
            var x1 = overlay.cfg.getProperty("x");      // get the new location
            var y1 = overlay.cfg.getProperty("y");
            YAHOO.util.Assert.areEqual(y0, y1);         // compare
            YAHOO.util.Assert.areEqual(x0, x1 + 200);
        }, 500);

    }
}));

function initTestLoadingIndicator() {
    if (parent && parent.TestManager) {
        parent.TestManager.load();
    } else {
        new YAHOO.tool.TestLogger();
        YAHOO.tool.TestRunner.run();
    }
};

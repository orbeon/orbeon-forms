/**
 * Copyright (C) 2012 Orbeon, Inc.
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
(function() {
    var OD = ORBEON.util.Dom;
    var YD = YAHOO.util.Dom;

    YAHOO.namespace("xbl.fr");
    YAHOO.xbl.fr.HrefButton = function() {};
    ORBEON.xforms.XBL.declareClass(YAHOO.xbl.fr.HrefButton, "xbl-fr-href-button");
    YAHOO.xbl.fr.HrefButton.prototype = {

        /**
         * Attach a click listener to the button. The listener opens a window based on information from the anchor.
         */
        init: function() {
            var container = this.container;
            var button = ORBEON.util.Dom.getElementsByName(container, "button")[0];
            YAHOO.util.Event.addListener(button, "click", function() {
                var a = YAHOO.util.Dom.getElementsByClassName("fr-href-button-anchor", null, container)[0];
                window.open(a.href, a.target)
            }, this);
        }
    };
})();
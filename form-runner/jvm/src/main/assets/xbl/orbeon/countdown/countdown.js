/**
 * Copyright (C) 2017 Orbeon, Inc.
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

    var $ = ORBEON.jQuery;

    YAHOO.namespace('xbl.fr');
    YAHOO.xbl.fr.Countdown = function() {};
    ORBEON.xforms.XBL.declareClass(YAHOO.xbl.fr.Countdown, 'xbl-fr-countdown');

    var lastTimestamp = Date.now();
    var outputEls = [];

    function parseDuration(text) {
        var minSec = text.split(":");
        if (minSec.length == 1) {
            return parseInt(minSec[0]);
        } else if (minSec.length == 2) {
            var min = parseInt(minSec[0]);
            var sec = parseInt(minSec[1]);
            if (_.isNaN(min) || _.isNaN(sec)) return NaN;
            return min*60 + sec;
        } else {
            return NaN;
        }

    }

    function serializeDuration(secTotal) {
        var min = Math.floor(secTotal / 60);
        var sec = secTotal % 60;
        var minPart = min > 0 ? min + ":" : "";
        var secPart = sec < 10 ? "0" + sec : sec;
        return minPart + secPart;
    }

    setInterval(function() {
        var newTimestamp = Date.now();
        var increment = Math.floor((newTimestamp - lastTimestamp)/1000);
        if (increment > 0) {
            lastTimestamp = newTimestamp;
            _.each(outputEls, function(outputEl) {
                var sec = parseDuration($(outputEl).text());
                if (! _.isNaN(sec)) {
                    var newSec = sec - increment;
                    if (newSec >= 0) {
                        var newDuration = serializeDuration(newSec);
                        $(outputEl).text(newDuration);
                    }
                }
            });
        }
    }, 100);

    YAHOO.xbl.fr.Countdown.prototype = {
        init: function() {
            var outputEl = $(this.container).find(".xforms-output-output").get(0);
            if (! _.contains(outputEls, outputEl))
                outputEls.push(outputEl);
        }
    };
})();

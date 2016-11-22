/**
 * Copyright (C) 2011 Orbeon, Inc.
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

    /**
     * Server is a singleton, with methods common for Ajax and non-Ajax interactions with the server.
     */
    ORBEON.xforms.server.Server = {};

    var Server = ORBEON.xforms.server.Server;

    Server.callUserScript = function(functionName, targetId, observerId) {

        function getElement(id) {
            var element = ORBEON.util.Dom.get(id);
            if (element == null) {
                // Try to find repeat (some events xxforms-nodeset-changed can target the repeat)
                element = ORBEON.util.Dom.get("repeat-begin-" + id);
                if (element == null) {
                    // Try getting repeat delimiter
                    var separatorPosition = Math.max(id.lastIndexOf(XF_REPEAT_SEPARATOR), id.lastIndexOf(XF_REPEAT_INDEX_SEPARATOR));
                    if (separatorPosition != -1) {
                        var repeatID = id.substring(0, separatorPosition);
                        var iteration = id.substring(separatorPosition + 1);
                        element = ORBEON.util.Utils.findRepeatDelimiter(repeatID, iteration);
                        if (element == null) {
                            // If everything else has failed, the id might be an xf:repeat id!
                            element = ORBEON.util.Dom.get("repeat-begin-" + id);
                        }
                    }
                }
            }
            return element;
        }

        var targetElement = getElement(targetId);
        var observer = getElement(observerId);
        var event = { "target" : targetElement };
        var theFunction = eval(functionName);

        // Arguments to the function:
        // - First is always `event`
        // - After that come custom arguments passed with <xxf:param> in <xf:action>
        var args = [event].concat(_.rest(arguments, 3));

        theFunction.apply(observer, args);
    };

})();
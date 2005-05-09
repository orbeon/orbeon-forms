/**
 *  Copyright (C) 2005 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */

function xformsUpdateStyle(element) {
    if (element.className) {
        var classes = element.className.split(" ");
        for (var classIndex = 0; classIndex < classes.length; classIndex++) {
            var className = classes[classIndex];
            
            if (className == "xforms-valid") {
                //alert(element.tagName);
                // Copy hidden section at top of form
            }
            
            if (className == "xforms-xhtml") {
                element.innerHTML = element.firstChild.data;
                element.style.display = "inline";
            }

            /*
            if (className == "xforms-hint") {
                if (!element.listenerRegistered) {
                    element.listenerRegistered = true;
                    var control = element.ownerDocument.getElementById(element.htmlFor);
                    var controlGetsFocus = function() {
                        alert(element.className);
                    };
                    if (control.addEventListener)
                        control.addEventListener("focus", controlGetsFocus, false);
                    else
                        control.attachEvent("onfocus", controlGetsFocus);
                }
            }
            */
        }
    }
}

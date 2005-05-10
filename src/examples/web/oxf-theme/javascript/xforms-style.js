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

            if (className == "xforms-hint") {
                // Only add listener once
                if (!element.listenerRegistered) {
                    element.listenerRegistered = true;

                    // Compute class for active state
                    var activeClass = new Array();
                    activeClass.push("xforms-hint-active");
                    for (var i = 0; i < classes.length; i++)
                        if (classes[i] != "xforms-hint") activeClass.push(classes[i]);
                        
                    // What happens when control gets/looses focus
                    var controlGetsFocus = function() { element.className = activeClass.join(" "); };
                    var controlLoosesFocus = function() { element.className = classes.join(" "); };

                    // Add listeners on control
                    var control = element.ownerDocument.getElementById(element.htmlFor);
                    if (control.addEventListener) {
                        control.addEventListener("focus", controlGetsFocus, false);
                        control.addEventListener("blur", controlLoosesFocus, false);
                    } else {
                        control.attachEvent("onfocus", controlGetsFocus);
                        control.attachEvent("onblur", controlLoosesFocus);
            	    }
                }
            }
            
            if (className == "xforms-help") {
                var showHelp = function(event) {
                    tt_Show(event, "id", false, 0, false, false, ttOffsetX, ttOffsetY, false, false, ttTemp);
                };
                var hideHelp = function() {
                    tt_Hide();
                };
                if (element.addEventListener)  {
                    element.addEventListener("mouseover", showHelp, false);
                    element.addEventListener("mouseout", hideHelp, false);
                    
                } else {
                    element.attachEvent("onmouseover", hideHelp);
                    element.attachEvent("onmouseout", hideHelp);
                }
            }
        }
    }
}

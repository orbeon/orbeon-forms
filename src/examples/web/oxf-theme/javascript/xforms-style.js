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
                if (!element.divId) {
                    element.divId = element.id + "-div";
                    var divHTML = tt_Htm(this, element.divId, element.firstChild.data);
                    var container = element.cloneNode(true);
                    container.innerHTML = divHTML;
                    document.body.appendChild(container.firstChild);
                }

                
                xformsAddEventListener(element, "mouseover", function(event) {
                    tt_Show(event, getEventTarget(event).divId, false, 0, false, false, 
                        ttOffsetX, ttOffsetY, false, false, ttTemp);
                });
                xformsAddEventListener(element, "mouseout", function() {
                    tt_Hide();
                });
            }
            
            if (className == "xforms-date") {
                if (!element.setupDone) {
                    element.setupDone = true;
                    
                    var showCalendarId = element.id + "-showcalendar";
                    element.nextSibling.id = showCalendarId;
                        
                    // Setup calendar library
                    Calendar.setup({
                        inputField     :    element.id,
                        ifFormat       :    "%Y-%m-%d",
                        showsTime      :    false,
                        button         :    showCalendarId,
                        singleClick    :    false,
                        step           :    1,
                        onUpdate       :    function() { 
                            xformsValueChanged(this.inputField, false); 
                        }
                    });
                }
            }
            
            if (element.alertElement) {
                element.alertElement.className = element.valid ? "xforms-alert-inactive" 
                    : "xforms-alert-active";
            }
        }
    }
}

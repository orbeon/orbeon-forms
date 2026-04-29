/**
 * Copyright (C) 2010 Orbeon, Inc.
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



this.ORBEON.onJavaScriptLoaded = new YAHOO.util.CustomEvent("javascript-loaded");

(function() {

    var $ = ORBEON.jQuery;
    var _ = ORBEON._;

    // Define packages
    ORBEON.xforms = ORBEON.xforms || {};


    ORBEON.xforms.XBL = {

        // Map the XBL CSS class to the JavaScript class
        _cssClassesToConstructors: {},

        // Get or create an instance of the JavaScript companion class for the given element, which must be
        // an XBL control element or a descendant of an XBL control element.
        instanceForControl: function(elem) {

            var xblControlElem = $(elem).closest('.xbl-component')[0];

            if (xblControlElem) {

                var identifyingCssClass =
                    _.find(xblControlElem.className.split(" "), function(clazz) {
                        // The "identifying class" should be the first after `xbl-component`, but filter
                        // known classes just in case
                        return clazz.indexOf("xbl-") == 0 &&
                               clazz != "xbl-component"   &&
                               clazz != "xbl-focusable"   &&
                               clazz != "xbl-javascript-lifecycle";
                    });

                if (identifyingCssClass) {
                    var factory = this._cssClassesToConstructors[identifyingCssClass];
                    if (factory) {
                        return factory(xblControlElem);
                    } else {
                        return null;
                    }
                }
                // TODO: This can return `undefined`!
            } else {
                return null;
            }
        },

        // Declare a companion JavaScript class. The class is defined by a prototype or a class.
        declareCompanion: function(bindingName, prototypeOrClass) {

            const isClass = typeof prototypeOrClass === "function";

            const parts = bindingName.split("|");
            const head = parts[0];
            const tail = bindingName.substring(head.length + 1);

            const cssClass = "xbl-" + head + "-" + tail;

            var xblClass;
            if (isClass) {
                xblClass = prototypeOrClass;
            } else {
                xblClass = function() {};
                Object.assign(xblClass.prototype, prototypeOrClass);
            }

            this.declareClass(xblClass, cssClass);
        },

        declareClass: function(xblClass, cssClass) {

            // 2023-01-09: TODO: Check cases where this is useful, and what kind of class we create below. Could we have
            //  simply an adapter class?
            var doNothingSingleton = null;

            this._cssClassesToConstructors[cssClass] = function(targetElem) {

                const subclass = ORBEON.xforms.XFormsXbl.createSubclass(xblClass);

                const containerElem =
                    targetElem == null || ! document.contains(targetElem)
                    ? null
                    : targetElem.closest("." + cssClass);

                if (containerElem == null) {
                    // If we get an event for a target which is not in the document, return a mock object
                    // that won't do anything when its methods are called
                    // Q: Under what circumstances can this happen?
                    if (doNothingSingleton == null) {
                        doNothingSingleton = {};
                        for (var methodName in xblClass.prototype) {
                            console.trace("instanceForControl: creating `doNothingSingleton` for `" + methodName + "`");
                            doNothingSingleton[methodName] = function () {};
                        }
                    }
                    console.debug("instanceForControl: returning mock `doNothingSingleton` for `" + cssClass + "`");
                    return doNothingSingleton;
                } else {
                    // Get or create instance
                    var instance = $(containerElem).data("xforms-xbl-object");
                    if (! _.isObject(instance) || instance.container != containerElem) {

                        // Q: Under what circumstances can this happen?
                        if (_.isObject(instance) && instance.container != containerElem) {
                            console.debug(
                                "instanceForControl: instance found in data but for different container: `" +
                                instance.container.id + "` and `" + containerElem.id + "`"
                            );
                            // Q: In this case, should we call `destroy()` on the class?
                        }

                        // Instantiate and initialize
                        instance = new subclass(containerElem);
                        instance.init();

                        // Keep track of the instance
                        // TODO: We remove those in `Form.destroy()`, but should we do it when the instance is destroyed
                        //  here too?
                        ORBEON.xforms.Page.getXFormsFormFromHtmlElemOrThrow(instance.container).xblInstances.push(instance);
                        $(containerElem).data('xforms-xbl-object', instance);
                    }
                    return instance;
                }
            };
        },

        componentInitialized: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT)
    };

    YAHOO.util.Event.throwErrors = true;
    ORBEON.onJavaScriptLoaded.fire();

})();

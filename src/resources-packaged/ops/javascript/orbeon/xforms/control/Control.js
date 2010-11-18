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
(function() {

    /**
     * Base class for controls.
     *
     * @constructor
     */
    ORBEON.xforms.control.Control = function() {};
    var Control = ORBEON.xforms.control.Control;

    /** @type {HTMLElement} */
    Control.prototype.container = null;

    /**
     * Creates a new instance of this control based on the container element.
     *
     * @param {HTMLElement} container
     * @void
     */
    Control.prototype.init = function(container) { this.container = container; };

    /**
     * Provides a new itemset for a control, if the control supports this.
     *
     * @param itemset
     * @void
     */
    Control.prototype.setItemset = function(itemset) {};

    /**
     * Sets the current value of the control in the UI, without sending an update to the server about the new value.
     *
     * @param value
     * @void
     */
    Control.prototype.setValue = function(value) {};

    /**
     * Returns the current value of the control.
     *
     * @return {String}
     */
    Control.prototype.getValue = function() {};

})();
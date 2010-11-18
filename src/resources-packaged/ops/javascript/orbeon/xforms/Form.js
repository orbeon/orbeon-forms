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

    ORBEON.xforms.Form = function() {};
    var Form = ORBEON.xforms.Form;

    Form.prototype.idToControl = {};

    /**
     * Creates or returns a control object corresponding to the provided container.
     *
     * @param   container
     * @return  {ORBEON.xforms.control.Control}
     */
    Form.prototype.getControl = function(container) {
        var control = this.idToControl[container.id];
        if (control == null || control.container != container) {
            control = new (ORBEON.xforms.Page.getControlConstructor(container));
            control.init(container);
            this.idToControl[container.id] = control;
        }
        return control;
    };
})();
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
     * Page is a singleton.
     */
    ORBEON.xforms.Page = {};

    var Form = ORBEON.xforms.Form;
    var Page = ORBEON.xforms.Page;
    var YD = YAHOO.util.Dom;

    /** @private @type {Object.<string, ORBEON.xforms.Form>*/
    Page.forms = {};
    /** @private */
    Page.controlConstructors = [];
    /** @private */
    Page.idToControl = {};

    /**
     * Returns the form object (not HTML element) corresponding to the specified id.
     *
     * @param   id
     * @return  {ORBEON.xforms.Form}
     */
    Page.getForm = function(id) {
        if (! this.forms[id]) this.forms[id] = new Form(YD.get(id));
        return this.forms[id];
    };

    /**
     * Creates or returns a control object corresponding to the provided container. Each control is inside a given
     * form, so getControl() could be a method of a form, but since we can given a container or control id determine
     * the control object without knowing the form, this method is defined at the Page level which makes it easier
     * to use for a caller who doesn't necessarily have a reference to the form object.
     *
     * @param   container
     * @return  {ORBEON.xforms.control.Control}
     */
    Page.getControl = function(container) {
        var control = this.idToControl[container.id];
        if (control == null || control.container != container) {
            control = new (ORBEON.xforms.Page.getControlConstructor(container));
            this.idToControl[container.id] = control;
            control.init(container);
        }
        return control;
    };

    Page.getControlConstructor = function(container) {
        var controlConstructor = _.detect(this.controlConstructors, function(controlConstructor) {
            return controlConstructor.predicate(container);
        });
        if (! controlConstructor) throw "Can't find a relevant control for container: " + container.id;
        return controlConstructor.controlConstructor;
    };

    /**
     * Register a control constructor (such as tree, input...). This is expected to be called by the control itself
     * when loaded.
     *
     * @param {function(): Control}             controlConstructor
     * @param {function(HTMLElement): boolean}  predicate
     */
    Page.registerControlConstructor = function(controlConstructor, predicate) {
        this.controlConstructors.push({controlConstructor: controlConstructor, predicate: predicate});
    };

})();
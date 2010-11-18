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

    var Form = ORBEON.xforms.Form;

    ORBEON.xforms.Page = {

        /** @private @type {Object.<string, ORBEON.xforms.Form>*/
        forms: {},
        /** @private */
        controlConstructors: [],

        /**
         * Returns the form object (not HTML element) corresponding to the specified id.
         *
         * @param   id
         * @return  {ORBEON.xforms.Form}
         */
        getForm: function(id) {
            if (! this.forms[id]) this.forms[id] = new Form();
            return this.forms[id];
        },

        getControlConstructor: function(container) {
            var controlConstructor = _.detect(this.controlConstructors, function(controlConstructor) {
                return controlConstructor.predicate(container);
            });
            if (! controlConstructor) throw "Can't find a relevant control for container" + container.id;
            return controlConstructor.controlConstructor;
        },

        /**
         * Register a control constructor (such as tree, input...). This is expected to be called by the control itself
         * when loaded.
         *
         * @param {function(): Control}             controlConstructor
         * @param {function(HTMLElement): boolean}  predicate
         */
        registerControlConstructor: function(controlConstructor, predicate) {
            this.controlConstructors.push({controlConstructor: controlConstructor, predicate: predicate});
        }
    };

})();
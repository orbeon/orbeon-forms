/**
 * Copyright (C) 2013 Orbeon, Inc.
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
(function($) {

    // Keep pointing to menu so we can move it around as needed
    var globalMenu = $('.fr-repeat-dropdown-menu')[0];

    var opNames = ['move-up', 'move-down', 'insert-above', 'insert-below', 'remove'];

    // Move the menu after the button so that positioning it will work
    function moveMenuIfNeeded(e) {
        $(e.target).closest('.dropdown').append(globalMenu);

        var row  = $(e.target).closest('.fr-repeat-iteration');
        var menu = row.find('.fr-repeat-dropdown-menu');

        _.each(opNames, function(opName) {
            menu.children('.fr-' + opName).toggleClass('disabled', ! row.is('.can-' + opName));
        });
        return true;
    }

    function gridIteration(e) {
        var rowId = $(e.target).closest('.fr-repeat-iteration').attr('id');
        var indexes = ORBEON.util.Utils.getRepeatIndexes(rowId);

        return indexes[indexes.length - 1];
    }

    function grid(e) {
        return $(e.target).closest('.xbl-fr-repeater')[0];
    }

    function gridId(e) {
        return $(grid(e)).attr('id');
    }

    function actionFunction(eventName) {
        return function(e) {
            ORBEON.xforms.Document.dispatchEvent({
                targetId: gridId(e),
                eventName: 'fr-' + eventName,
                properties: { row: gridIteration(e) }
            });
            e.preventDefault();
            return true;
        }
    }

    if (globalMenu) {
        // Bootstrap dropdown listens to document, so we listen to body so that we are called first and can move the
        // menu to the right place first
        $('body').on('click.orbeon', '.fr-repeat-menu [data-toggle=dropdown]', moveMenuIfNeeded);

        // Listeners for actions
        _.each(opNames, function(opName) {
            $(document).on('click.orbeon', '.fr-repeat-menu .fr-' + opName, actionFunction(opName));
        });
    }
})(window.jQuery); // NOTE: Bootstrap 3 uses non-global jQuery

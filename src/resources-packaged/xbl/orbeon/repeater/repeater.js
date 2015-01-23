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
ORBEON.jQuery(function() {

    var $ = ORBEON.jQuery;

    // Keep pointing to menu so we can move it around as needed
    var globalMenu = $('.fr-repeater-dropdown-menu')[0];

    var opNames = ['move-up', 'move-down', 'insert-above', 'insert-below', 'remove'];

    // Not very nice: global context for actionFunction.
    var currentRepeaterId = null;
    var currentRepeaterIteration = null;

    function moveAndShowMenu(e) {

        moveMenu(e);

        // NOTE: Don't use dropdown('toggle') as that registers a new handler further down the DOM!
        $(globalMenu).find(".dropdown-toggle").trigger("click");

        // Prevent "propagation". In fact, with jQuery, "delegated" handlers are handled first, and if a delegated
        // event calls stopPropagation(), then "directly-bound" handlers are not called. Yeah. So here, we prevent
        // propagation as Dropdown.toggle() does, which will prevent the catch-all handler for clearMenus() from
        // running.
        return false;
    }

    // Move the menu just below the button
    function moveMenu(e) {
        var dropdown = $(e.target).closest('.dropdown');

        var dropdownOffset = dropdown.offset();
        $(globalMenu).css("position", "absolute");
        $(globalMenu).offset({top: dropdownOffset.top + dropdown.height(),  left: dropdownOffset.left });

        var row = $(repeaterRow(e));
        _.each(opNames, function(opName) {
            $(globalMenu).find('.dropdown-menu').children('.fr-' + opName).toggleClass('disabled', ! row.is('.can-' + opName));
        });

        currentRepeaterId = repeaterId(e);
        currentRepeaterIteration = repeaterIteration(e);

        // Prevent "propagation". In fact, with jQuery, "delegated" handlers are handled first, and if a delegated
        // event calls stopPropagation(), then "directly-bound" handlers are not called. Yeah. So here, we prevent
        // propagation as Dropdown.toggle() does, which will prevent the catch-all handler for clearMenus() from
        // running.
        return false;
    }

    // Handle keyboard events that arrive on our button and delegate the to the Bootstrap menu button
    function delegateKeyEventToBootstrapButton(e) {
        moveMenu(e);
        $(globalMenu).find(".dropdown-toggle").trigger({ type: e.type, keyCode: e.keyCode });
    }

    function repeaterRow(e) {
        return $(e.target).closest('.fr-repeat-iteration')[0];
    }

    function repeaterIteration(e) {
        var rowId = $(repeaterRow(e)).attr('id');
        var indexes = ORBEON.util.Utils.getRepeatIndexes(rowId);

        return indexes[indexes.length - 1];
    }

    function repeater(e) {
        return $(e.target).closest('.xbl-fr-repeater')[0];
    }

    function repeaterId(e) {
        return $(repeater(e)).attr('id');
    }

    function actionFunction(eventName) {
        return function(e) {
            ORBEON.xforms.Document.dispatchEvent({
                targetId: currentRepeaterId,
                eventName: 'fr-' + eventName,
                properties: { row: currentRepeaterIteration }
            });
            e.preventDefault();
            return true;
        };
    }

    if (globalMenu) {
        // Click on our own button moves and shows the menu
        $(document).on('click.orbeon.repeater',   '.fr-repeater-dropdown-button', moveAndShowMenu);
        $(document).on('keydown.orbeon.repeater', '.fr-repeater-dropdown-button', delegateKeyEventToBootstrapButton);

        // Listeners for all menu actions
        _.each(opNames, function(opName) {
            $(document).on('click.orbeon.repeater', '.fr-repeater-dropdown-menu .fr-' + opName, actionFunction(opName));
        });
    }
});

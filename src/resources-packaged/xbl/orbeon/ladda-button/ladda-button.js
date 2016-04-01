/**
 * Copyright (C) 2015 Orbeon, Inc.
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

    var $ = ORBEON.jQuery;
    var Document = ORBEON.xforms.Document;
    var AjaxServer = ORBEON.xforms.server.AjaxServer;

    var STATE_BEGIN   = 0;
    var STATE_CLICKED = 1;
    var STATE_SENT    = 2;

    YAHOO.namespace("xbl.fr");
    YAHOO.xbl.fr.LaddaButton = function() {};
    ORBEON.xforms.XBL.declareClass(YAHOO.xbl.fr.LaddaButton, "xbl-fr-ladda-button");
    YAHOO.xbl.fr.LaddaButton.prototype = {

        button: null,
        state: STATE_BEGIN,

        init: function() {

            // Init buttons
            this.button = $(this.container).find('button');
            this.button.attr('data-style', 'slide-left');
            var isPrimary = this.button.parents('.xforms-trigger-appearance-xxforms-primary').is('*');
            this.button.attr('data-spinner-color', isPrimary ? 'white' : 'black');
            this.button.addClass('ladda-button');
            this.button.ladda();

            // Events
            this.button.on('click',             _.bind(this.click    , this));
            AjaxServer.beforeSendingEvent.add  (_.bind(this.sending  , this));
            AjaxServer.ajaxResponseReceived.add(_.bind(this.receiving, this));
        },

        destroy: function () {
            // TODO: remove event handlers, destroy component
        },

        click: function() {
            if (this.state == STATE_BEGIN) {
                // Defer changing the button, not to prevent other listeners on the click event from being called
                _.defer(_.bind(function() {
                    this.button.ladda('start');
                    this.state = STATE_CLICKED;
                }, this));
            }
        },

        sending: function() {
            if (this.state == STATE_CLICKED)
                this.state = STATE_SENT;
        },

        receiving: function() {
            if (this.state == STATE_SENT) {
                this.button.ladda('stop');
                this.state = STATE_BEGIN;
            }
        }
    };
})();

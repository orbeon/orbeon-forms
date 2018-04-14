/**
 * Copyright (C) 2009 Orbeon, Inc.
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
    var Event = YAHOO.util.Event;

    YAHOO.namespace('xbl.fr');
    YAHOO.xbl.fr.Recaptcha = function() {};
    ORBEON.xforms.XBL.declareClass(YAHOO.xbl.fr.Recaptcha, 'xbl-fr-recaptcha');
    YAHOO.xbl.fr.Recaptcha.prototype = {

        responseId          : null,
        publicKeyPropertyId : null,
        publicKeyLocalId    : null,
        widgetId            : null,

        /**
         * Constructor
         */
        init: function() {
            var recaptchaDiv = $(this.container).find('.xbl-fr-recaptcha-div').get(0);
            this.responseId  = $(this.container).find('.xbl-fr-recaptcha-response').get(0).id;

            // Public key comes from property
            var publicKeyElement = $(this.container).find('.xbl-fr-recaptcha-public-key').get(0);
            var publicKey        = ORBEON.xforms.Document.getValue(publicKeyElement.id);
            // Other configurations
            var themeElement     = $(this.container).find('.xbl-fr-recaptcha-theme').get(0);
            var theme            = ORBEON.xforms.Document.getValue(themeElement.id);
            var langElement      = $(this.container).find('.xbl-fr-recaptcha-lang').get(0);
            var lang             = ORBEON.xforms.Document.getValue(langElement.id);

            // Default theme is `clean`
            if (theme == "") theme = "clean";

            var self = this;
            var renderRecaptcha = function () {
                if (_.isUndefined(window.grecaptcha)) {
                    var shortDelay = ORBEON.util.Properties.internalShortDelay.get();
                    _.delay(renderRecaptcha, shortDelay);
                } else {
                    self.widgetId = grecaptcha.render(recaptchaDiv, {
                        sitekey  : publicKey,
                        theme    : theme,
                        callback : _.bind(self.successfulResponse, self)
                    });
                }
            };

            renderRecaptcha();
        },

        successfulResponse: function() {
            console.log("successfulResponse");
            var response = grecaptcha.getResponse(this.widgetId);
            console.log("response", response);
            ORBEON.xforms.Document.setValue(this.responseId, response);
        },

        reload: function() {
            ORBEON.xforms.Document.setValue(this.responseId, '');
            Recaptcha.reload();
        }
    };
})();

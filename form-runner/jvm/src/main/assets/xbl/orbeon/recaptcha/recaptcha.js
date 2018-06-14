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

        render: function(publicKey, theme) {
            var recaptchaDiv = $(this.container).find('.xbl-fr-recaptcha-div').get(0);
            this.responseId  = $(this.container).find('.xbl-fr-recaptcha-response').get(0).id;

            // Load reCAPTCHA script with appropriate language
            if (_.isUndefined(window.grecaptcha)) {
                var htmlLang = ORBEON.jQuery('html').attr('lang');
                var langParameter =
                    _.isUndefined(htmlLang) ? "" :
                    "?hl=" + htmlLang;
                var reCaptchaScript = $('<script src="https://www.recaptcha.net/recaptcha/api.js' + langParameter + '">');
                $(this.container).append(reCaptchaScript);
            }

            var self = this;
            var renderRecaptcha = function () {
                if (_.isUndefined(window.grecaptcha) || _.isUndefined(window.grecaptcha.render)) {
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
            var response = grecaptcha.getResponse(this.widgetId);
            ORBEON.xforms.Document.setValue(this.responseId, response);
        }
    };
})();

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
    var Document = ORBEON.xforms.Document;
    var Event = YAHOO.util.Event;

    YAHOO.namespace('xbl.fr');
    YAHOO.xbl.fr.Recaptcha = function() {};
    ORBEON.xforms.XBL.declareClass(YAHOO.xbl.fr.Recaptcha, 'xbl-fr-recaptcha');
    YAHOO.xbl.fr.Recaptcha.prototype = {

        challengeId: null,
        responseId: null,
        publicKeyPropertyId: null,
        publicKeyLocalId: null,

        /**
         * Constructor
         */
        init: function() {
            var recaptchaDiv = $(this.container).find('.xbl-fr-recaptcha-div').get(0);
            this.challengeId = $(this.container).find('.xbl-fr-recaptcha-challenge').get(0).id;
            this.responseId  = $(this.container).find('.xbl-fr-recaptcha-response').get(0).id;

            // Public key comes from property
            var publicKeyElement = $(this.container).find('.xbl-fr-recaptcha-public-key').get(0);
            var publicKey        = Document.getValue(publicKeyElement.id);
            // Other configurations
            var themeElement     = $(this.container).find('.xbl-fr-recaptcha-theme').get(0);
            var theme            = Document.getValue(themeElement.id);
            var langElement      = $(this.container).find('.xbl-fr-recaptcha-lang').get(0);
            var lang             = Document.getValue(langElement.id);

            Recaptcha.create(publicKey, recaptchaDiv.id, {
               theme: theme,
               lang: lang,
               callback: _.bind(this.recaptchaInitialized, this)
            });
        },

        /**
         * When the reCAPTCHA initialized, add a listener on the input to store the challenge/response in XForms controls,
         * so they are ready to be checked.
         */
        recaptchaInitialized: function() {
            Event.addListener(this.recaptchaInput(), 'change', _.bind(function() {
                Document.setValue(this.challengeId, Recaptcha.get_challenge());
                Document.setValue(this.responseId, Recaptcha.get_response());
            }, this));
        },

        reload: function() {
            Document.setValue(this.responseId, '');
            Recaptcha.reload();
        },

        focus: function() {
            this.recaptchaInput().focus();
        },

        // Return the recaptcha input created by Recaptcha.create(). We used to search by id but it's unclear whether
        // the id is always 'recaptcha_response_field'.
        recaptchaInput: function() {
            return $(this.container).find('.xbl-fr-recaptcha-div input[type=text]').get(0);
        }
    };
})();

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
    var OD = ORBEON.util.Dom;
    var YD = YAHOO.util.Dom;

    YAHOO.namespace("xbl.fr");
    YAHOO.xbl.fr.Recaptcha = function() {};
    ORBEON.xforms.XBL.declareClass(YAHOO.xbl.fr.Recaptcha, "xbl-fr-recaptcha");
    YAHOO.xbl.fr.Recaptcha.prototype = {

        challengeId: null,
        responseId: null,
        publicKeyPropertyId: null,
        publicKeyLocalId: null,

        /**
         * Constructor
         */
        init: function() {
            var recaptchaDiv = YAHOO.util.Dom.getElementsByClassName("xbl-fr-recaptcha-div", null, this.container)[0];
            this.challengeId = YAHOO.util.Dom.getElementsByClassName("xbl-fr-recaptcha-challenge", null, this.container)[0].id;
            this.responseId = YAHOO.util.Dom.getElementsByClassName("xbl-fr-recaptcha-response", null, this.container)[0].id;
            this.verifyButton = YAHOO.util.Dom.getElementsByClassName("xbl-fr-recaptcha-verify", null, this.container)[0];

            // Public key comes from property
            var publicKeyElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-recaptcha-public-key", null, this.container)[0];
            var publicKey = ORBEON.xforms.Document.getValue(publicKeyElement.id);
            // Other configurations
            var themeElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-recaptcha-theme", null, this.container)[0];
            var theme = ORBEON.xforms.Document.getValue(themeElement.id);
            var langElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-recaptcha-lang", null, this.container)[0];
            var lang = ORBEON.xforms.Document.getValue(langElement.id);

            Recaptcha.create(publicKey, recaptchaDiv.id, {
               theme: theme,
               lang: lang
            });
        },

        getChallengeResponse: function() {
            ORBEON.xforms.Document.setValue(this.challengeId, Recaptcha.get_challenge());
            ORBEON.xforms.Document.setValue(this.responseId, Recaptcha.get_response());
            OD.getElementByTagName(this.verifyButton, "button").click();
        },

        reload: function() {
            Recaptcha.reload();
        }
    };
})();

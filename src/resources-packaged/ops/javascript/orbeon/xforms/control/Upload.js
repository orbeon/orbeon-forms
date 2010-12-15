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
     * Corresponds to <xforms:input> bound to node of type xs:anyURI or xs:base64Binary.
     */
    ORBEON.xforms.control.Upload = function() {};

    var ExecutionQueue = ORBEON.util.ExecutionQueue;
    var Properties = ORBEON.util.Properties;
    var Server = ORBEON.xforms.Server;
    var Control = ORBEON.xforms.control.Control;
    var Upload = ORBEON.xforms.control.Upload;
    var Page = ORBEON.xforms.Page;
    var YD = YAHOO.util.Dom;

    Upload.prototype = new Control();

    Upload.prototype.change = function() {

        // Submit form in the background (pseudo-Ajax request)
        YAHOO.util.Connect.setForm(this.getForm(), true, true);
        Server.uploadEventQueue.add({}, Properties.delayBeforeIncrementalRequest.get(), ExecutionQueue.MIN_WAIT);
    };

    Page.registerControlConstructor(Upload,  function(container) {
        return YD.hasClass(container, "xforms-upload");
    });

})();
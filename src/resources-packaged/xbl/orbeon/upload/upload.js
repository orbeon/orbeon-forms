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
YAHOO.namespace("xbl.fr");
YAHOO.xbl.fr.Upload = function() {};
ORBEON.xforms.XBL.declareClass(YAHOO.xbl.fr.Upload, "xbl-fr-upload");
YAHOO.xbl.fr.Upload.prototype = {

    yuiButton: null,        // YUI object representing the button
    inputFileContainer: null,
    inputFile: null,
    visibleInput: null,     // Text field visible to the user showing the file name

    /**
     * Constructor
     */
    init: function() {
        var uploadDiv = YAHOO.util.Dom.getElementsByClassName("xbl-fr-upload-div", null, this.container)[0];
        var buttonContainer = YAHOO.util.Dom.getElementsByClassName("yui-button", null, this.container)[0];
        var fakeFileContainer = YAHOO.util.Dom.getElementsByClassName("xbl-fr-upload-fakefile", null, this.container)[0];
        this.inputFileContainer = YAHOO.util.Dom.getElementsByClassName("xforms-upload", null, this.container)[0];
        this.visibleInput = YAHOO.util.Dom.getElementsByClassName("xbl-fr-upload-visible-input", null, this.container)[0];
        
        // Init YUI button
        this.yuiButton = new YAHOO.widget.Button(buttonContainer);
        // Size the file upload field to be of the same size of the visible content
        this.inputFileContainer.style.height = fakeFileContainer.clientHeight + "px";
        this.inputFileContainer.style.width = fakeFileContainer.clientWidth + "px";
        // Register listener on mouse events
        YAHOO.util.Event.addListener(this.inputFileContainer, "mouseover", this.fileMouseOver, this, true);
        YAHOO.util.Event.addListener(this.inputFileContainer, "mouseout", this.fileMouseOut, this, true);
    },
    
    fileSelected: function() {
        // Copy file name to text field
        var inputFile = ORBEON.util.Dom.getElementByTagName(this.inputFileContainer, "input");
        this.visibleInput.value = inputFile.value;
    },
    
    fileMouseOver: function() {
        // Forward event to the button
        var event = arguments[0];
        this.yuiButton._onMouseOver(event);
        // Register 'change' event listener on mouseover as the file upload control might have been recreated by YUI
        var inputFile = ORBEON.util.Dom.getElementByTagName(this.inputFileContainer, "input");
        if (this.inputFile != inputFile) {
            YAHOO.util.Event.addListener(inputFile, "change", this.fileSelected, this, true);
            this.inputFile = inputFile;
        }
    },
    
    fileMouseOut: function() {
        // Forward event to the button
        var event = arguments[0];
        this.yuiButton._onMouseOut(event);
    }
};

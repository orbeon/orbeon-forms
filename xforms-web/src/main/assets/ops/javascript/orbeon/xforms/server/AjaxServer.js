/**
 * Copyright (C) 2011 Orbeon, Inc.
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

    // 2020-10-05: xforms.js comes first. It defines `ORBEON.xforms` already.

    ORBEON.xforms.AjaxServerResponse = {};

    var $ = ORBEON.jQuery;

    /**
     * Process events in the DOM passed as parameter.
     *
     * @param responseXML       DOM containing events to process
     */
    ORBEON.xforms.AjaxServerResponse.handleResponseDom = function(responseXML, formID, ignoreErrors) {

        try {
            var responseRoot = responseXML.documentElement;

            // Whether this response has triggered a load which will replace the current page.
            var newDynamicStateTriggersReplace = false;

            // Getting xforms namespace
            var nsAttribute = _.find(responseRoot.attributes, function(attribute) {
                return attribute.nodeValue == XXFORMS_NAMESPACE_URI;

            });

            var responseDialogIdsToShowAsynchronously = [];
            var controlsWithUpdatedItemsets = {};

            _.each(responseRoot.childNodes, function(childNode) {

                if (ORBEON.util.Utils.getLocalName(childNode) == "action") {

                    var actionElement = childNode;

                    var controlValuesElements = _.filter(actionElement.childNodes, function(childElement) {
                        return ORBEON.util.Utils.getLocalName(childElement) == "control-values";
                    });

                    function findRepeatInsertionPoint(repeatPrefixedId, parentIndexes) {
                        return document.getElementById("repeat-end-" + ORBEON.util.Utils.appendRepeatSuffix(repeatPrefixedId, parentIndexes));
                    }

                    if (ORBEON.util.Utils.isIOS() && ORBEON.util.Utils.getZoomLevel() != 1.0) {
                        var dialogsToShowArray = ORBEON.xforms.XFormsUi.findDialogsToShow(controlValuesElements);
                        if (dialogsToShowArray.length > 0) {
                            responseDialogIdsToShowAsynchronously = dialogsToShowArray;
                            ORBEON.util.Utils.resetIOSZoom();
                        }
                    }

                    // First add and remove "lines" in repeats (as itemset changed below might be in a new line)
                    ORBEON.xforms.XFormsUi.handleDeleteRepeatElements(controlValuesElements);

                    function handleControlDetails(controlValuesElements) {

                        var recreatedInputs = {};

                        function handleItemset(elem, controlId) {

                            var itemsetTree = JSON.parse(ORBEON.util.Dom.getStringValue(elem));
                            var groupName   = elem.getAttribute("group");

                            if (itemsetTree == null)
                                itemsetTree = [];

                            var documentElement = document.getElementById(controlId);

                            controlsWithUpdatedItemsets[controlId] = true;

                            if ($(documentElement).is('.xforms-select1-appearance-compact, .xforms-select-appearance-compact, .xforms-select1-appearance-minimal')) {
                                ORBEON.xforms.XFormsUi.updateSelectItemset(documentElement, itemsetTree);
                            } else {

                                // Case of checkboxes / radio buttons

                                // Actual values:
                                //
                                //  <span>
                                //    <label for="my-new-select1$$e1">
                                //      <input id="my-new-select1$$e1" type="radio" checked name="my-new-select1" value="orange"/>Orange
                                //    </label>
                                //  </span>

                                // Get template
                                var isSelect = $(documentElement).is('.xforms-select');
                                var template = isSelect
                                        ? document.getElementById("xforms-select-full-template")
                                        : document.getElementById("xforms-select1-full-template");
                                template = ORBEON.util.Dom.getChildElementByIndex(template, 0);

                                // Get the span that contains the one span per checkbox/radio
                                var spanContainer = documentElement.querySelector("span.xforms-items");

                                // Remove spans and store current checked value
                                var valueToChecked = {};
                                while (true) {
                                    var child = YAHOO.util.Dom.getFirstChild(spanContainer);
                                    if (child == null) break;
                                    var input = child.getElementsByTagName("input")[0];
                                    valueToChecked[input.value] = input.checked;
                                    spanContainer.removeChild(child);
                                }

                                // Recreate content based on template
                                _.each(itemsetTree, function(itemElement, itemIndex) {

                                    var templateClone = template.cloneNode(true);

                                    var parsedLabel = $.parseHTML(itemElement.label);
                                    ORBEON.util.Utils.replaceInDOM(templateClone, "$xforms-template-label$", parsedLabel, true);
                                    ORBEON.util.Utils.replaceInDOM(templateClone, "$xforms-template-value$", itemElement.value, false);
                                    var itemEffectiveId = ORBEON.util.Utils.appendToEffectiveId(controlId, XF_LHHAI_SEPARATOR + "e" + itemIndex);
                                    ORBEON.util.Utils.replaceInDOM(templateClone, isSelect ? "$xforms-item-id-select$" : "$xforms-item-id-select1$", itemEffectiveId, false);
                                    ORBEON.util.Utils.replaceInDOM(templateClone, "$xforms-item-name$", ! _.isNull(groupName) ? groupName : controlId, false);

                                    if (itemElement.help && itemElement.help != "") {
                                        ORBEON.util.Utils.replaceInDOM(templateClone, "$xforms-template-help$", itemElement.help, false);
                                    } else {
                                        $(templateClone).find('.xforms-help').remove();
                                    }

                                    if (itemElement.hint && itemElement.hint != "") {
                                        var parsedHint = $.parseHTML(itemElement.hint);
                                        ORBEON.util.Utils.replaceInDOM(templateClone, "$xforms-template-hint$", parsedHint, true);
                                    } else {
                                        $(templateClone).find('.xforms-hint-region').removeAttr("class");
                                        $(templateClone).find('.xforms-hint').remove();
                                    }

                                    if (! _.isUndefined(itemElement.attributes) && ! _.isUndefined(itemElement.attributes["class"])) {
                                        templateClone.className += " " + itemElement.attributes["class"];
                                    }

                                    // Set or remove `disabled` depending on whether the control is readonly.
                                    // NOTE: jQuery went back and forth on using `attr()` vs. `prop()` but this seems to work.
                                    var controlEl = document.getElementById(controlId);
                                    var isReadonly = ORBEON.xforms.Controls.isReadonly(controlEl);

                                    var inputCheckboxOrRadio = templateClone.getElementsByTagName("input")[0];

                                    // See:
                                    // - https://github.com/orbeon/orbeon-forms/issues/5595
                                    // - https://github.com/orbeon/orbeon-forms/issues/5427
                                    ORBEON.xforms.Controls.setDisabledOnFormElement(inputCheckboxOrRadio, isReadonly);

                                    // Restore checked state after copy
                                    if (valueToChecked[itemElement.value] == true) {
                                        inputCheckboxOrRadio.checked = true;
                                    }

                                    spanContainer.appendChild(templateClone);
                                });
                            }

                            // Call custom listener if any (temporary until we have a good API for custom components)
                            if (typeof xformsItemsetUpdatedListener != "undefined") {
                                xformsItemsetUpdatedListener(controlId, itemsetTree);
                            }
                        }

                        function handleSwitchCase(elem) {
                            var id      = ORBEON.util.Dom.getAttribute(elem, "id");
                            var visible = ORBEON.util.Dom.getAttribute(elem, "visibility") == "visible";
                            ORBEON.xforms.Controls.toggleCase(id, visible);
                        }

                        function handleControl(elem) {
                            var controlId        = ORBEON.util.Dom.getAttribute(elem, "id");
                            var staticReadonly   = ORBEON.util.Dom.getAttribute(elem, "static");
                            var relevant         = ORBEON.util.Dom.getAttribute(elem, "relevant");
                            var readonly         = ORBEON.util.Dom.getAttribute(elem, "readonly");
                            var required         = ORBEON.util.Dom.getAttribute(elem, "required");
                            var classes          = ORBEON.util.Dom.getAttribute(elem, "class");
                            var newLevel         = ORBEON.util.Dom.getAttribute(elem, "level");
                            var progressState    = ORBEON.util.Dom.getAttribute(elem, "progress-state");
                            var progressReceived = ORBEON.util.Dom.getAttribute(elem, "progress-received");
                            var progressExpected = ORBEON.util.Dom.getAttribute(elem, "progress-expected");
                            var newSchemaType    = ORBEON.util.Dom.getAttribute(elem, "type");
                            var newVisited       = ORBEON.util.Dom.getAttribute(elem, "visited");

                            var documentElement = document.getElementById(controlId);

                            // Done to fix #2935; can be removed when we have taken care of #2940
                            if (documentElement == null &&
                                (controlId      == "fb-static-upload-empty" ||
                                 controlId      == "fb-static-upload-non-empty"))
                                return;

                            if (documentElement == null) {
                                documentElement = document.getElementById("group-begin-" + controlId);
                                if (documentElement == null) ORBEON.util.Utils.logMessage ("Can't find element or iteration with ID '" + controlId + "'");
                            }
                            var jDocumentElement = $(documentElement);

                            var documentElementClasses = documentElement.className.split(" ");
                            var isLeafControl = $(documentElement).is('.xforms-control');

                            var recreatedInput = false;

                            // Handle migration of control from non-static to static if needed
                            var isStaticReadonly = $(documentElement).is('.xforms-static');
                            if (! isStaticReadonly && staticReadonly == "true") {
                                if (isLeafControl) {
                                    // Replace existing element with span
                                    var parentElement = documentElement.parentNode;
                                    var newDocumentElement = document.createElement("span");
                                    newDocumentElement.setAttribute("id", controlId);
                                    newDocumentElement.className = documentElementClasses.join(" ") + " xforms-static";
                                    parentElement.replaceChild(newDocumentElement, documentElement);

                                    // Remove alert
                                    var alertElement = ORBEON.xforms.Controls.getControlLHHA(newDocumentElement, "alert");
                                    if (alertElement != null)
                                        parentElement.removeChild(alertElement);
                                    // Remove hint
                                    var hintElement = ORBEON.xforms.Controls.getControlLHHA(newDocumentElement, "hint");
                                    if (hintElement != null)
                                        parentElement.removeChild(hintElement);
                                        // Update document element information
                                    documentElement = newDocumentElement;
                                } else {
                                    // Just add the new class
                                    YAHOO.util.Dom.addClass(documentElement, "xforms-static");
                                }
                                isStaticReadonly = true;
                            }

                            // We update the relevance and readonly before we update the value. If we don't, updating the value
                            // can fail on IE in some cases. (The details about this issue have been lost.)

                            // Handle becoming relevant
                            if (relevant == "true")
                                ORBEON.xforms.Controls.setRelevant(documentElement, true);

                            // Update input control type
                            // NOTE: This is not ideal: in the future, we would like a template-based mechanism instead.
                            if (newSchemaType != null) {

                                if ($(documentElement).is('.xforms-input')) {

                                    // For each supported type, declares the recognized schema types and the class used in the DOM
                                    var INPUT_TYPES = [
                                        { type: "boolean",  schemaTypes: [ "{http://www.w3.org/2001/XMLSchema}boolean", "{http://www.w3.org/2002/xforms}boolean" ], className: "xforms-type-boolean" },
                                        { type: "string",   schemaTypes: null, className: "xforms-type-string" }
                                    ];

                                    var existingType = _.detect(INPUT_TYPES, function(type) { return type.schemaTypes == null || YAHOO.util.Dom.hasClass(documentElement, type.className); });
                                    var newType =      _.detect(INPUT_TYPES, function(type) { return type.schemaTypes == null || _.include(type.schemaTypes, newSchemaType); });
                                    if (newType != existingType) {

                                        // Remember that this input has be recreated which means we need to update its value
                                        recreatedInput = true;
                                        recreatedInputs[controlId] = documentElement;
                                        // Clean-up document element by removing type classes
                                        _.each(INPUT_TYPES, function(type) { YAHOO.util.Dom.removeClass(documentElement, type.className); });
                                        // Minimal control content can be different
                                        var isMinimal = $(documentElement).is('.xforms-input-appearance-minimal');

                                        // Find the position of the last label before the control "actual content" and remove all elements that are not labels
                                        // A value of -1 means that the content came before any label
                                        var lastLabelPosition = null;
                                        _.each(YAHOO.util.Dom.getChildren(documentElement), function(childElement, childIndex) {
                                            if (! $(childElement).is('.xforms-label, .xforms-help, .xforms-hint, .xforms-alert')) {
                                                documentElement.removeChild(childElement);
                                                if (lastLabelPosition == null)
                                                    lastLabelPosition = childIndex - 1;
                                            }
                                        });

                                        function insertIntoDocument(nodes) {
                                            var childElements = YAHOO.util.Dom.getChildren(documentElement);
                                            // Insert after "last label" (we remembered the position of the label after which there is real content)
                                            if (childElements.length == 0) {
                                                _.each(nodes, function(node) {
                                                    documentElement.appendChild(node);
                                                });
                                            } else if (lastLabelPosition == -1) {
                                                // Insert before everything else
                                                var firstChild = childElements[0];
                                                _.each(nodes, function(node) {
                                                    YAHOO.util.Dom.insertBefore(node, firstChild);
                                                });
                                            } else {
                                                // Insert after a LHHA
                                                var lhha = childElements[lastLabelPosition];
                                                for (var nodeIndex = nodes.length - 1; nodeIndex >= 0; nodeIndex--)
                                                    YAHOO.util.Dom.insertAfter(nodes[nodeIndex], lhha);
                                            }
                                        }

                                        function createInput(typeClassName, inputIndex) {
                                            var newInputElement = document.createElement("input");
                                            newInputElement.setAttribute("type", "text");
                                            newInputElement.className = "xforms-input-input " + typeClassName;
                                            newInputElement.id = ORBEON.util.Utils.appendToEffectiveId(controlId, "$xforms-input-" + inputIndex);

                                            // In portlet mode, name is not prefixed
                                            newInputElement.name = ORBEON.xforms.Page.deNamespaceIdIfNeeded(formID, newInputElement.id);

                                            return newInputElement;
                                        }

                                        var inputLabelElement = ORBEON.xforms.Controls.getControlLHHA(documentElement, "label");
                                        if (newType.type == "string") {
                                            var newStringInput = createInput("xforms-type-string", 1);
                                            insertIntoDocument([newStringInput]);
                                            YAHOO.util.Dom.addClass(documentElement, "xforms-type-string");
                                            if (inputLabelElement != null) inputLabelElement.htmlFor = newStringInput.id;
                                        } else if (newType.type == "boolean") {

                                            // Make copy of the template
                                            var booleanTemplate = document.getElementById("xforms-select-full-template");
                                            booleanTemplate = ORBEON.util.Dom.getChildElementByIndex(booleanTemplate, 0);
                                            var booleanTemplateClone = booleanTemplate.cloneNode(true);

                                            // Remove the label we have in the template for each individual checkbox/radio button
                                            // Do this because the checkbox label is actually not used, instead the control label is used
                                            var templateLabelElement = booleanTemplateClone.getElementsByTagName("label")[0];
                                            var templateInputElement = booleanTemplateClone.getElementsByTagName("input")[0];
                                            // Move <input> at level of <label> and get rid of label
                                            templateLabelElement.parentNode.replaceChild(templateInputElement, templateLabelElement);

                                            // Remove the disabled attribute from the template, which is there so tab would skip over form elements in template
                                            var booleanInput = ORBEON.util.Dom.getElementByTagName(booleanTemplateClone, "input");
                                            booleanInput.removeAttribute("disabled");

                                            // Replace placeholders
                                            insertIntoDocument([booleanTemplateClone]);
                                            ORBEON.util.Utils.replaceInDOM(booleanTemplateClone, "$xforms-template-value$", "true", false);
                                            var booleanEffectiveId = ORBEON.util.Utils.appendToEffectiveId(controlId, XF_LHHAI_SEPARATOR + "e0", false);
                                            ORBEON.util.Utils.replaceInDOM(booleanTemplateClone, "$xforms-item-id-select$", booleanEffectiveId, false);
                                            ORBEON.util.Utils.replaceInDOM(booleanTemplateClone, "$xforms-item-name$", controlId, false);

                                            // Update classes
                                            YAHOO.util.Dom.addClass(documentElement, "xforms-type-boolean");
                                            YAHOO.util.Dom.addClass(documentElement, "xforms-input-appearance-minimal");
                                            YAHOO.util.Dom.addClass(documentElement, "xforms-incremental");

                                            if (inputLabelElement != null) inputLabelElement.htmlFor = booleanEffectiveId;
                                        }
                                    }
                                }

                                // Update type annotation
                                var typePrefix = "xforms-type-";

                                // Remove existing type classes
                                _.each(documentElement.className.split(" "), function(currentClass) {
                                    if (currentClass.indexOf(typePrefix) == 0) {
                                        YAHOO.util.Dom.removeClass(documentElement, currentClass);
                                    }
                                });

                                // Add new class
                                var typeResult = /{(.*)}(.*)/.exec(newSchemaType);
                                if (typeResult != null && typeResult.length == 3) {
                                    var typeNamespace = typeResult[1];
                                    var typeLocalName = typeResult[2];
                                    var isBuiltIn = typeNamespace == 'http://www.w3.org/2001/XMLSchema'
                                                 || typeNamespace == 'http://www.w3.org/2002/xforms';
                                    var newClass = typePrefix + (isBuiltIn ? '' : 'custom-') + typeLocalName;
                                    jDocumentElement.addClass(newClass);
                                }
                            }

                            // Handle required
                            if (required != null) {
                                jDocumentElement.toggleClass("xforms-required", required == "true");
                            }

                            // Handle readonly
                            if (readonly != null && ! isStaticReadonly)
                                ORBEON.xforms.Controls.setReadonly(documentElement, readonly == "true");

                            // Handle updates to custom classes
                            if (classes != null) {
                                _.each(classes.split(" "), function(currentClass) {
                                    if (currentClass.charAt(0) == '-') {
                                        YAHOO.util.Dom.removeClass(documentElement, currentClass.substring(1));
                                    } else {
                                        // '+' is optional
                                        YAHOO.util.Dom.addClass(documentElement, currentClass.charAt(0) == '+' ? currentClass.substring(1) : currentClass);
                                    }
                                });
                            }

                            // Update the required-empty/required-full even if the required has not changed or
                            // is not specified as the value may have changed
                            if (! isStaticReadonly) {
                                var emptyAttr = elem.getAttribute("empty");
                                if (! _.isNull(emptyAttr))
                                    ORBEON.xforms.Controls.updateRequiredEmpty(documentElement, emptyAttr);
                            }

                            // Custom attributes on controls
                            if (isLeafControl) {

                                // Aria attributes on some controls only
                                if (jDocumentElement.is(".xforms-input, .xforms-textarea, .xforms-secret, .xforms-select1.xforms-select1-appearance-compact, .xforms-select1.xforms-select1-appearance-minimal")) {

                                    var firstInput = jDocumentElement.find(":input");

                                    if (required != null) {
                                        if (required == "true")
                                            firstInput.attr("aria-required", "true");
                                        else
                                            firstInput.removeAttr("aria-required");
                                    }

                                    var visited = newVisited != null ? newVisited          : documentElement.classList.contains("xforms-visited");
                                    var invalid = newLevel   != null ? newLevel == "error" : documentElement.classList.contains("xforms-invalid");
                                    if (invalid && visited) firstInput.attr      ("aria-invalid", "true");
                                    else                    firstInput.removeAttr("aria-invalid");
                                }

                                if ($(documentElement).is('.xforms-upload')) {
                                    // Additional attributes for xf:upload
                                    // <xxf:control id="xforms-control-id"
                                    //    state="empty|file"
                                    //    accept=".txt"
                                    //    filename="filename.txt" mediatype="text/plain" size="23kb"/>

                                    // Get elements we want to modify from the DOM
                                    var fileNameSpan  = YAHOO.util.Dom.getElementsByClassName("xforms-upload-filename", null, documentElement)[0];
                                    var mediatypeSpan = YAHOO.util.Dom.getElementsByClassName("xforms-upload-mediatype", null, documentElement)[0];
                                    var sizeSpan      = YAHOO.util.Dom.getElementsByClassName("xforms-upload-size", null, documentElement)[0];

                                    // Set values in DOM
                                    var upload = ORBEON.xforms.Page.getUploadControl(documentElement);

                                    var state     = ORBEON.util.Dom.getAttribute(elem, "state");
                                    var fileName  = ORBEON.util.Dom.getAttribute(elem, "filename");
                                    var mediatype = ORBEON.util.Dom.getAttribute(elem, "mediatype");
                                    var size      = ORBEON.util.Dom.getAttribute(elem, "size");
                                    var accept    = ORBEON.util.Dom.getAttribute(elem, "accept");

                                    if (state)
                                        upload.setState(state);
                                    if (fileName != null)
                                        ORBEON.util.Dom.setStringValue(fileNameSpan, fileName);
                                    if (mediatype != null)
                                        ORBEON.util.Dom.setStringValue(mediatypeSpan, mediatype);
                                    if (size != null)
                                        ORBEON.util.Dom.setStringValue(sizeSpan, size);
                                    // NOTE: Server can send a space-separated value but accept expects a comma-separated value
                                    if (accept != null)
                                        jDocumentElement.find(".xforms-upload-select").attr("accept", accept.split(/\s+/).join(","));

                                } else if ($(documentElement).is('.xforms-output, .xforms-static')) {

                                    var alt = ORBEON.util.Dom.getAttribute(elem, "alt");

                                    if (alt != null && jDocumentElement.is('.xforms-mediatype-image')) {
                                        var img = jDocumentElement.children('img').first();
                                        img.attr('alt', alt);
                                    }
                                } else if ($(documentElement).is('.xforms-trigger, .xforms-submit')) {
                                    // It isn't a control that can hold a value (e.g. trigger) and there is no point in trying to update it
                                    // NOP
                                } else if ($(documentElement).is('.xforms-input, .xforms-secret')) {
                                    // Additional attributes for xf:input and xf:secret

                                    var inputSize         = ORBEON.util.Dom.getAttribute(elem, "size");
                                    var maxlength         = ORBEON.util.Dom.getAttribute(elem, "maxlength");
                                    var inputAutocomplete = ORBEON.util.Dom.getAttribute(elem, "autocomplete");

                                    // NOTE: Below, we consider an empty value as an indication to remove the attribute. May or may not be
                                    // the best thing to do.

                                    var input = documentElement.getElementsByTagName("input")[0];

                                    if (inputSize != null) {
                                        if (inputSize == "")
                                            input.removeAttribute("size");
                                        else
                                            input.size = inputSize;
                                    }
                                    if (maxlength != null) {
                                        if (maxlength == "")
                                            input.removeAttribute("maxlength");// this, or = null doesn't work w/ IE 6
                                        else
                                            input.maxLength = maxlength;// setAttribute() doesn't work with IE 6
                                    }
                                    if (inputAutocomplete != null) {
                                        if (inputAutocomplete == "")
                                            input.removeAttribute("autocomplete");
                                        else
                                            input.autocomplete = inputAutocomplete;
                                    }
                                } else if ($(documentElement).is('.xforms-textarea')) {
                                    // Additional attributes for xf:textarea

                                    var maxlength    = ORBEON.util.Dom.getAttribute(elem, "maxlength");
                                    var textareaCols = ORBEON.util.Dom.getAttribute(elem, "cols");
                                    var textareaRows = ORBEON.util.Dom.getAttribute(elem, "rows");

                                    var textarea = documentElement.getElementsByTagName("textarea")[0];

                                    // NOTE: Below, we consider an empty value as an indication to remove the attribute. May or may not be
                                    // the best thing to do.
                                    if (maxlength != null) {
                                        if (maxlength == "")
                                            textarea.removeAttribute("maxlength");// this, or = null doesn't work w/ IE 6
                                        else
                                            textarea.maxLength = maxlength;// setAttribute() doesn't work with IE 6
                                    }
                                    if (textareaCols != null) {
                                        if (textareaCols == "")
                                            textarea.removeAttribute("cols");
                                        else
                                            textarea.cols = textareaCols;
                                    }
                                    if (textareaRows != null) {
                                        if (textareaRows == "")
                                            textarea.removeAttribute("rows");
                                        else
                                            textarea.rows = textareaRows;
                                    }
                                }
                            }

                            // Store new label message in control attribute
                            var newLabel = ORBEON.util.Dom.getAttribute(elem, "label");
                            if (newLabel != null)
                                ORBEON.xforms.Controls.setLabelMessage(documentElement, newLabel);
                            // Store new hint message in control attribute
                            // See also https://github.com/orbeon/orbeon-forms/issues/3561
                            var newHint = ORBEON.util.Dom.getAttribute(elem, "hint");
                            if (newHint != null) {
                                ORBEON.xforms.Controls.setHintMessage(documentElement, newHint);
                            } else {
                                var newTitle = ORBEON.util.Dom.getAttribute(elem, "title");
                                if (newTitle != null)
                                    ORBEON.xforms.Controls.setHintMessage(documentElement, newTitle);
                            }
                            // Store new help message in control attribute
                            var newHelp = ORBEON.util.Dom.getAttribute(elem, "help");
                            if (newHelp != null)
                                ORBEON.xforms.Controls.setHelpMessage(documentElement, newHelp);
                            // Store new alert message in control attribute
                            var newAlert = ORBEON.util.Dom.getAttribute(elem, "alert");
                            if (newAlert != null)
                                ORBEON.xforms.Controls.setAlertMessage(documentElement, newAlert);
                            // Store validity, label, hint, help in element
                            if (newLevel != null)
                                ORBEON.xforms.Controls.setConstraintLevel(documentElement, newLevel);

                            // Handle progress for upload controls
                            if (progressState != null && progressState != "")
                                ORBEON.xforms.Page.getUploadControl(documentElement).progress(
                                    progressState,
                                    progressReceived != null && progressReceived != "" ? parseInt(progressReceived) : null,
                                    progressExpected != null && progressExpected != "" ? parseInt(progressExpected) : null
                                );

                            // Handle visited flag
                            if (newVisited)
                                ORBEON.xforms.Controls.updateVisited(documentElement, newVisited == 'true');

                            // Nested elements
                            _.each(elem.childNodes, function(childNode) {
                                switch (ORBEON.util.Utils.getLocalName(childNode)) {
                                    case 'itemset':
                                        handleItemset(childNode, controlId);
                                        break;
                                    case 'case':
                                        handleSwitchCase(childNode);
                                        break;
                                }
                            });

                            // Must handle `value` after `itemset`
                            ORBEON.xforms.XFormsUi.handleValues(elem, controlId, recreatedInput, controlsWithUpdatedItemsets);

                            // Handle becoming non-relevant after everything so that XBL companion class instances
                            // are nulled and can be garbage-collected
                            if (relevant == "false")
                                ORBEON.xforms.Controls.setRelevant(documentElement, false);
                        }

                        function handleInnerHtml(elem) {

                            var innerHTML    = ORBEON.util.Dom.getStringValue(ORBEON.xforms.XFormsUi.firstChildWithLocalName(elem, 'value'));
                            var initElem     = ORBEON.xforms.XFormsUi.firstChildWithLocalName(elem, 'init');
                            var initValue    = _.isUndefined(initElem) ? null : ORBEON.util.Dom.getStringValue(initElem);
                            var destroyElem  = ORBEON.xforms.XFormsUi.firstChildWithLocalName(elem, 'destroy');
                            var destroyValue = _.isUndefined(destroyElem) ? null : ORBEON.util.Dom.getStringValue(destroyElem);
                            var controlId    = ORBEON.util.Dom.getAttribute(elem, "id");

                            var prefixedId = ORBEON.util.Utils.getEffectiveIdNoSuffix(controlId);

                            function endsWith(text, suffix) {
                              var index = text.lastIndexOf(suffix);
                              return index !== -1 && index + suffix.length === text.length;
                            }

                            if (destroyValue) {
                                ORBEON.xforms.InitSupport.destroyJavaScriptControlsFromSerialized(destroyValue);
                            }

                            if (endsWith(prefixedId, "~iteration")) {
                                // The HTML is the content of a repeat iteration

                                var repeatPrefixedId = prefixedId.substring(0, prefixedId.length - "~iteration".length);

                                var parentRepeatIndexes = ORBEON.util.Utils.getRepeatIndexes(controlId);
                                parentRepeatIndexes.pop();

                                // NOTE: New iterations are added always at the end so we don't yet need the value of the current index.
                                // This will be either the separator before the template, or the end element.
                                var afterInsertionPoint = findRepeatInsertionPoint(repeatPrefixedId, parentRepeatIndexes.join("-"));

                                var tagName = afterInsertionPoint.tagName;

                                $(afterInsertionPoint).before(
                                    '<' + tagName + ' class="xforms-repeat-delimiter"></' + tagName + '>',
                                    innerHTML
                                );

                            } else {

                                var documentElement = document.getElementById(controlId);
                                if (documentElement != null) {
                                    // Found container
                                    // Detaching children to avoid nodes becoming disconnected
                                    // http://wiki.orbeon.com/forms/doc/contributor-guide/browser#TOC-In-IE-nodes-become-disconnected-when-removed-from-the-DOM-with-an-innerHTML
                                    $(documentElement).children().detach();
                                    documentElement.innerHTML = innerHTML;
                                    ORBEON.xforms.FullUpdate.onFullUpdateDone(controlId);
                                    // Special case for https://github.com/orbeon/orbeon-forms/issues/3707
                                    ORBEON.xforms.Controls.fullUpdateEvent.fire({control: documentElement});
                                } else {
                                    // Insertion between delimiters
                                    function insertBetweenDelimiters(prefix) {
                                        // Some elements don't support innerHTML on IE (table, tr...). So for those, we create a div, and
                                        // complete the table with the missing parent elements.
                                        var SPECIAL_ELEMENTS = {
                                            "table": { opening: "<table>", closing: "</table>", level: 1 },
                                            "thead": { opening: "<table><thead>", closing: "</thead></table>", level: 2 },
                                            "tbody": { opening: "<table><tbody>", closing: "</tbody></table>", level: 2 },
                                            "tr":    { opening: "<table><tr>", closing: "</tr></table>", level: 2 }
                                        };
                                        var delimiterBegin = document.getElementById(prefix + "-begin-" + controlId);
                                        if (delimiterBegin != null) {
                                            // Remove content between begin and end marker
                                            while (delimiterBegin.nextSibling.nodeType != ELEMENT_TYPE || delimiterBegin.nextSibling.id != prefix + "-end-" + controlId)
                                                delimiterBegin.parentNode.removeChild(delimiterBegin.nextSibling);
                                            // Insert content
                                            var delimiterEnd = delimiterBegin.nextSibling;
                                            var specialElementSpec = SPECIAL_ELEMENTS[delimiterBegin.parentNode.tagName.toLowerCase()];
                                            var dummyElement = document.createElement(specialElementSpec == null ? delimiterBegin.parentNode.tagName : "div");
                                            dummyElement.innerHTML = specialElementSpec == null ? innerHTML : specialElementSpec.opening + innerHTML + specialElementSpec.closing;
                                            // For special elements, the parent is nested inside the dummyElement
                                            var dummyParent = specialElementSpec == null ? dummyElement
                                                : specialElementSpec.level == 1 ? YAHOO.util.Dom.getFirstChild(dummyElement)
                                                : specialElementSpec.level == 2 ? YAHOO.util.Dom.getFirstChild(YAHOO.util.Dom.getFirstChild(dummyElement))
                                                : null;
                                            // Move nodes to the real DOM
                                            while (dummyParent.firstChild != null)
                                                YAHOO.util.Dom.insertBefore(dummyParent.firstChild, delimiterEnd);
                                            return true;
                                        } else {
                                            return false;
                                        }
                                    }
                                    // First try inserting between group delimiters, and if it doesn't work between repeat delimiters
                                    if (! insertBetweenDelimiters("group"))
                                        if (! insertBetweenDelimiters("repeat"))
                                            insertBetweenDelimiters("xforms-case");
                                }
                            }

                            if (initValue) {
                                ORBEON.xforms.InitSupport.initializeJavaScriptControlsFromSerialized(initValue);
                            }

                            // If the element that had the focus is not in the document anymore, it might have been replaced by
                            // setting the innerHTML, so set focus it again
                            if (! YAHOO.util.Dom.inDocument(ORBEON.xforms.Globals.currentFocusControlElement, document)) {
                                var focusControl = document.getElementById(ORBEON.xforms.Globals.currentFocusControlId);
                                if (focusControl != null) ORBEON.xforms.Controls.setFocus(ORBEON.xforms.Globals.currentFocusControlId);
                            }
                        }

                        _.each(controlValuesElements, function(controlValuesElement) {
                            _.each(controlValuesElement.childNodes, function(childNode) {
                                switch (ORBEON.util.Utils.getLocalName(childNode)) {
                                    case 'control':
                                        handleControl(childNode);
                                        break;
                                    case 'init':
                                        ORBEON.xforms.XFormsUi.handleInit(childNode, controlsWithUpdatedItemsets);
                                        break;
                                    case 'inner-html':
                                        handleInnerHtml(childNode);
                                        break;
                                    case 'attribute':
                                        ORBEON.xforms.XFormsUi.handleAttribute(childNode);
                                        break;
                                    case 'text':
                                        ORBEON.xforms.XFormsUi.handleText(childNode);
                                        break;
                                    case 'repeat-iteration':
                                        ORBEON.xforms.XFormsUi.handleRepeatIteration(childNode, formID);
                                        break;
                                    case 'dialog':
                                        ORBEON.xforms.XFormsUi.handleDialog(childNode, formID);
                                        break;
                                }

                            });
                        });

                        // Notification event if the type changed
                        _.each(recreatedInputs, function(documentElement, controlId) {
                            ORBEON.xforms.Controls.typeChangedEvent.fire({control: documentElement});
                        });
                    }

                    function handleOtherActions(actionElement) {

                        _.each(actionElement.childNodes, function(childNode) {
                            switch (ORBEON.util.Utils.getLocalName(childNode)) {

                                // Update repeat hierarchy
                                case "repeat-hierarchy": {
                                    ORBEON.xforms.InitSupport.processRepeatHierarchyUpdateForm(formID, ORBEON.util.Dom.getStringValue(childNode));
                                    break;
                                }

                                // Change highlighted section in repeat
                                case "repeat-indexes": {
                                    var newRepeatIndexes = {};
                                    // Extract data from server response
                                    _.each(childNode.childNodes, function(childNode) {
                                        if (ORBEON.util.Utils.getLocalName(childNode) == "repeat-index") {
                                            var repeatIndexElement = childNode;
                                            var repeatId = ORBEON.util.Dom.getAttribute(repeatIndexElement, "id");
                                            var newIndex = ORBEON.util.Dom.getAttribute(repeatIndexElement, "new-index");
                                            newRepeatIndexes[repeatId] = newIndex;
                                        }
                                    });
                                    // For each repeat id that changes, see if all the children are also included in
                                    // newRepeatIndexes. If they are not, add an entry with the index unchanged.

                                    var form = ORBEON.xforms.Page.getXFormsFormFromNamespacedIdOrThrow(formID);
                                    var repeatTreeParentToAllChildren = form.repeatTreeParentToAllChildren;
                                    var repeatIndexes                 = form.repeatIndexes;

                                    for (var repeatId in newRepeatIndexes) {
                                        if (typeof repeatId == "string") { // hack because repeatId may be trash when some libraries override Object
                                            var children = repeatTreeParentToAllChildren[repeatId];
                                            if (children != null) { // test on null is a hack because repeatId may be trash when some libraries override Object
                                                for (var childIndex in children) {
                                                    var child = children[childIndex];
                                                    if (! newRepeatIndexes[child])
                                                        newRepeatIndexes[child] = repeatIndexes[child];
                                                }
                                            }
                                        }
                                    }
                                    // Unhighlight items at old indexes
                                    for (var repeatId in newRepeatIndexes) {
                                        if (typeof repeatId == "string") { // hack because repeatId may be trash when some libraries override Object
                                            var oldIndex = repeatIndexes[repeatId];
                                            if (typeof oldIndex == "string" && oldIndex != 0) { // hack because repeatId may be trash when some libraries override Object
                                                var oldItemDelimiter = ORBEON.util.Utils.findRepeatDelimiter(formID, repeatId, oldIndex);
                                                if (oldItemDelimiter != null) { // https://github.com/orbeon/orbeon-forms/issues/3689
                                                    var cursor = oldItemDelimiter.nextSibling;
                                                    while (cursor.nodeType != ELEMENT_TYPE ||
                                                           (! $(cursor).is('.xforms-repeat-delimiter')
                                                                   && ! $(cursor).is('.xforms-repeat-begin-end'))) {
                                                        if (cursor.nodeType == ELEMENT_TYPE)
                                                            YAHOO.util.Dom.removeClass(cursor, ORBEON.util.Utils.getClassForRepeatId(formID, repeatId));
                                                        cursor = cursor.nextSibling;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    // Store new indexes
                                    for (var repeatId in newRepeatIndexes) {
                                        var newIndex = newRepeatIndexes[repeatId];
                                        repeatIndexes[repeatId] = newIndex;
                                    }
                                    // Highlight item at new index
                                    for (var repeatId in newRepeatIndexes) {
                                        if (typeof repeatId == "string") { // Hack because repeatId may be trash when some libraries override Object
                                            var newIndex = newRepeatIndexes[repeatId];
                                            if (typeof newIndex == "string" && newIndex != 0) { // Hack because repeatId may be trash when some libraries override Object
                                                var newItemDelimiter = ORBEON.util.Utils.findRepeatDelimiter(formID, repeatId, newIndex);
                                                if (newItemDelimiter != null) { // https://github.com/orbeon/orbeon-forms/issues/3689
                                                    var cursor = newItemDelimiter.nextSibling;
                                                    while (cursor.nodeType != ELEMENT_TYPE ||
                                                           (! $(cursor).is('.xforms-repeat-delimiter')
                                                                   && ! $(cursor).is('.xforms-repeat-begin-end'))) {
                                                        if (cursor.nodeType == ELEMENT_TYPE)
                                                            YAHOO.util.Dom.addClass(cursor, ORBEON.util.Utils.getClassForRepeatId(formID, repeatId));
                                                        cursor = cursor.nextSibling;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    break;
                                }

                                // 2020-07-21: Only for upload response
                                case "server-events": {
                                    var serverEventsElement = childNode;
                                    var serverEvents = ORBEON.util.Dom.getStringValue(serverEventsElement);

                                    ORBEON.xforms.AjaxClient.createDelayedServerEvent(serverEvents, 0, true, false, formID);

                                    break;
                                }

                                case "poll": {
                                    var pollElement = childNode;
                                    var delayOrNull = ORBEON.util.Dom.getAttribute(pollElement, "delay");

                                    ORBEON.xforms.AjaxClient.createDelayedPollEvent(_.isNull(delayOrNull) ? undefined : parseInt(delayOrNull), formID);

                                    break;
                                }

                                // Submit form
                                case "submission": {
                                    ORBEON.xforms.XFormsUi.handleSubmission(formID, childNode, function() {
                                        newDynamicStateTriggersReplace = true;
                                    });
                                    break;
                                }

                                // Display modal message
                                case "message": {
                                    ORBEON.xforms.Message.execute(childNode);
                                    break;
                                }

                                // Load another page
                                case "load": {
                                    var loadElement = childNode;
                                    var resource = ORBEON.util.Dom.getAttribute(loadElement, "resource");
                                    var show = ORBEON.util.Dom.getAttribute(loadElement, "show");
                                    var target = ORBEON.util.Dom.getAttribute(loadElement, "target");
                                    var showProgress = ORBEON.util.Dom.getAttribute(loadElement, "show-progress");

                                    if (resource.indexOf("javascript:") == 0) {
                                        // JavaScript URL
                                        var js = decodeURIComponent(resource.substring("javascript:".length));
                                        eval(js);
                                    } else  if (show == "replace") {
                                        if (target == null) {
                                            // Display loading indicator unless the server tells us not to display it
                                            if (resource.charAt(0) != '#' && showProgress != "false")
                                                newDynamicStateTriggersReplace = true;
                                            try {
                                                window.location.href = resource;
                                            } catch (e) {
                                                // NOP: This is to prevent the error "Unspecified error" in IE. This can
                                                // happen when navigating away is cancelled by the user pressing cancel
                                                // on a dialog displayed on unload.
                                            }
                                        } else {
                                            window.open(resource, target, "noopener");
                                        }
                                    } else {
                                        window.open(resource, "_blank", "noopener");
                                    }
                                    break;
                                }

                                // Set focus to a control
                                case "focus": {
                                    var focusElement = childNode;
                                    var controlId = ORBEON.util.Dom.getAttribute(focusElement, "control-id");
                                    ORBEON.xforms.Controls.setFocus(controlId);
                                    break;
                                }

                                // Remove focus from a control
                                case "blur": {
                                    var blurElement = childNode;
                                    var controlId = ORBEON.util.Dom.getAttribute(blurElement, "control-id");
                                    ORBEON.xforms.Controls.removeFocus(controlId);
                                    break;
                                }

                                // Run JavaScript code
                                case "script": {
                                    ORBEON.xforms.XFormsUi.handleScriptElem(formID, childNode);
                                    break;
                                }

                                // Run JavaScript code
                                case "callback": {
                                    ORBEON.xforms.XFormsUi.handleCallbackElem(formID, childNode);
                                    break;
                                }

                                // Show help message for specified control
                                case "help": {
                                    var helpElement = childNode;
                                    var controlId = ORBEON.util.Dom.getAttribute(helpElement, "control-id");
                                    var control = document.getElementById(controlId);
                                    ORBEON.xforms.Help.showHelp(control);
                                    break;
                                }
                            }
                        });
                    }

                    if (responseDialogIdsToShowAsynchronously.length > 0) {
                        var timerId = setTimeout(function() {
                            handleControlDetails(controlValuesElements);
                            handleOtherActions(actionElement);
                        }, 200);

                        var form = ORBEON.xforms.Page.getXFormsFormFromNamespacedIdOrThrow(formID)

                        _.each(responseDialogIdsToShowAsynchronously, function(dialogId) {
                            form.addDialogTimerId(dialogId, timerId);
                        });

                    } else {
                        // Process synchronously
                        handleControlDetails(controlValuesElements);
                        handleOtherActions(actionElement);
                    }

                } else if (ORBEON.util.Utils.getLocalName(childNode) == "errors") {
                    ORBEON.xforms.XFormsUi.handleErrorsElem(formID, ignoreErrors, childNode);
                }
            });

            if (newDynamicStateTriggersReplace) {
                // Display loading indicator when we go to another page.
                // Display it even if it was not displayed before as loading the page could take time.
                ORBEON.xforms.Page.loadingIndicator().showIfNotAlreadyVisible();
            }
        } catch (e) {
            // Show dialog with error to the user, as they won't be able to continue using the UI anyway
            ORBEON.xforms.AjaxClient.logAndShowError(e, formID, ignoreErrors);
            // Don't rethrow exception: we want to code that runs after the Ajax response is handled to run, so we have a chance to recover from this error
        }
    };

})();

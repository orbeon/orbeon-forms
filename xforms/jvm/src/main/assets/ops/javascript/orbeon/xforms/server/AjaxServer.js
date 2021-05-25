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

    ORBEON.xforms.server.AjaxServer = {};

    var $ = ORBEON.jQuery;
    var AjaxServer  = ORBEON.xforms.server.AjaxServer;
    var Controls    = ORBEON.xforms.Controls;
    var Properties  = ORBEON.util.Properties;
    var Globals     = ORBEON.xforms.Globals;

    function childrenWithLocalName(node, name) {
        var result = [];
        _.each(node.childNodes, function(child) {
            // Not using child.localName, as it isn't supported by IE8
            var childLocalName = _.last(child.nodeName.split(':'));
            if (childLocalName == name)
                result.push(child);
        });
        return result;
    }

    /**
     * Process events in the DOM passed as parameter.
     *
     * @param responseXML       DOM containing events to process
     */
    AjaxServer.handleResponseDom = function(responseXML, formID, ignoreErrors) {

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

                    function findDialogsToShow() {

                        var result = [];

                        _.each(controlValuesElements, function(controlValuesElement) {
                            _.each(childrenWithLocalName(controlValuesElement, 'dialog'), function(elem) {

                                var id      = ORBEON.util.Dom.getAttribute(elem, "id");
                                var visible = ORBEON.util.Dom.getAttribute(elem, "visibility") == "visible";

                                if (visible)
                                    result.push(id);
                            });
                        });

                        return result;
                    }

                    function findRepeatInsertionPoint(repeatPrefixedId, parentIndexes) {
                        return document.getElementById("repeat-end-" + ORBEON.util.Utils.appendRepeatSuffix(repeatPrefixedId, parentIndexes));
                    }

                    if (ORBEON.util.Utils.isIOS() && ORBEON.util.Utils.getZoomLevel() != 1.0) {
                        var dialogsToShowArray = findDialogsToShow();
                        if (dialogsToShowArray.length > 0) {
                            responseDialogIdsToShowAsynchronously = dialogsToShowArray;
                            ORBEON.util.Utils.resetIOSZoom();
                        }
                    }

                    // First add and remove "lines" in repeats (as itemset changed below might be in a new line)
                    _.each(controlValuesElements, function(controlValuesElement) {

                        _.each(childrenWithLocalName(controlValuesElement, 'delete-repeat-elements'), function(deleteElementElement) {

                            // Extract data from server response
                            var deleteId      = ORBEON.util.Dom.getAttribute(deleteElementElement, "id");
                            var parentIndexes = ORBEON.util.Dom.getAttribute(deleteElementElement, "parent-indexes");
                            var count         = ORBEON.util.Dom.getAttribute(deleteElementElement, "count");

                            // Find end of the repeat
                            var repeatEnd = document.getElementById("repeat-end-" + ORBEON.util.Utils.appendRepeatSuffix(deleteId, parentIndexes));

                            // Find last element to delete
                            var lastElementToDelete = repeatEnd.previousSibling;

                            // Perform delete
                            for (var countIndex = 0; countIndex < count; countIndex++) {
                                var nestedRepeatLevel = 0;
                                while (true) {
                                    var wasDelimiter = false;
                                    if (lastElementToDelete.nodeType == ELEMENT_TYPE) {
                                        if ($(lastElementToDelete).is('.xforms-repeat-begin-end') &&
                                            lastElementToDelete.id.indexOf("repeat-end-") == 0) {
                                            // Entering nested repeat
                                            nestedRepeatLevel++;
                                        } else if ($(lastElementToDelete).is('.xforms-repeat-begin-end') &&
                                                   lastElementToDelete.id.indexOf("repeat-begin-") == 0) {
                                            // Exiting nested repeat
                                            nestedRepeatLevel--;
                                        } else {
                                            wasDelimiter = nestedRepeatLevel == 0 && $(lastElementToDelete).is('.xforms-repeat-delimiter');
                                        }
                                    }
                                    var previous = lastElementToDelete.previousSibling;
                                    // Since we are removing an element that can contain controls, remove the known server value
                                    if (lastElementToDelete.nodeType == ELEMENT_TYPE) {
                                        YAHOO.util.Dom.getElementsByClassName("xforms-control", null, lastElementToDelete, function(control) {
                                            ORBEON.xforms.ServerValueStore.remove(control.id);
                                        });
                                        // We also need to check this on the "root", as the getElementsByClassName() function only returns sub-elements
                                        // of the specified root and doesn't include the root in its search.
                                        if ($(lastElementToDelete).is('.xforms-control'))
                                            ORBEON.xforms.ServerValueStore.remove(lastElementToDelete.id);
                                    }
                                    lastElementToDelete.parentNode.removeChild(lastElementToDelete);
                                    lastElementToDelete = previous;
                                    if (wasDelimiter) break;
                                }
                            }
                        });
                    });

                    function handleControlDetails(controlValuesElements) {

                        var recreatedInputs = {};

                        function handleItemset(elem, controlId) {

                            var itemsetTree = JSON.parse(ORBEON.util.Dom.getStringValue(elem));

                            if (itemsetTree == null)
                                itemsetTree = [];

                            var documentElement = document.getElementById(controlId);

                            controlsWithUpdatedItemsets[controlId] = true;

                            if ($(documentElement).is('.xforms-select1-appearance-compact, .xforms-select-appearance-compact, .xforms-select1-appearance-minimal')) {

                                // Case of list / combobox
                                var select = documentElement.getElementsByTagName("select")[0];

                                // Remember selected values
                                var selectedOptions = _.filter(select.options, function(option) {
                                    return option.selected;
                                });

                                var selectedValues = _.map(selectedOptions, function(option) {
                                    return option.value;
                                });

                                // Utility function to generate an option
                                function generateOption(label, value, clazz, selectedValues) {
                                    var selected = _.contains(selectedValues, value);
                                    return '<option value="' + ORBEON.common.MarkupUtils.escapeXmlForAttribute(value) + '"'
                                            + (selected ? ' selected="selected"' : '')
                                            + (clazz != null ? ' class="' + ORBEON.common.MarkupUtils.escapeXmlForAttribute(clazz) + '"' : '')
                                            + '>' + label + '</option>';
                                }

                                // Utility function to generate an item, including its sub-items, and make sure we do not produce nested optgroups
                                var sb = []; // avoid concatenation to the same string over and over again
                                var inOptgroup = false;
                                function generateItem(itemElement) {
                                    var clazz = null;
                                    if (! _.isUndefined(itemElement.attributes) && ! _.isUndefined(itemElement.attributes["class"])) {
                                        // We have a class property
                                        clazz = itemElement.attributes["class"];
                                    }
                                    if (_.isUndefined(itemElement.children)) { // a normal value
                                        sb[sb.length] =  generateOption(itemElement.label, itemElement.value, clazz, selectedValues);
                                    }
                                    else { // containing sub-items
                                        // the remaining elements: sub-items
                                        if (inOptgroup) // nested optgroups are not allowed, close the old one
                                            sb[sb.length] = '</optgroup>';
                                        // open optgroup
                                        sb[sb.length] = '<optgroup label="' + ORBEON.common.MarkupUtils.escapeXmlForAttribute(itemElement.label) + '"'
                                            + (clazz != null ? ' class="' + ORBEON.common.MarkupUtils.escapeXmlForAttribute(clazz) + '"' : '')
                                            + '">';
                                        inOptgroup = true;
                                        // add subitems
                                        _.each(itemElement.children, function(child) {
                                            generateItem(child);
                                        });
                                        // if necessary, close optgroup
                                        if (inOptgroup)
                                            sb[sb.length] = '</optgroup>';
                                        inOptgroup = false;
                                    }
                                }


                                // Build new content for the select element
                                _.each(itemsetTree, function(item) {
                                    generateItem(item);
                                });

                                // Set content of select element
                                select.innerHTML = sb.join("");

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
                                    ORBEON.util.Utils.replaceInDOM(templateClone, "$xforms-item-name$", controlId, false);

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
                                    if (isReadonly)
                                        $(inputCheckboxOrRadio).attr('disabled', 'disabled');
                                    else
                                        $(inputCheckboxOrRadio).removeAttr('disabled');

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

                        function handleValue(elem, controlId, recreatedInput) {
                            var newControlValue  = ORBEON.util.Dom.getStringValue(elem);
                            var documentElement  = document.getElementById(controlId);
                            var jDocumentElement = $(documentElement);

                            // Save new value sent by server (upload controls don't carry their value the same way as other controls)
                            var previousServerValue = ORBEON.xforms.ServerValueStore.get(controlId);

                            if (! jDocumentElement.is('.xforms-upload')) // Should not happen to match
                                ORBEON.xforms.ServerValueStore.set(controlId, newControlValue);

                            // Update value
                            if (jDocumentElement.is('.xforms-trigger, .xforms-submit, .xforms-upload')) {
                               // Should not happen
                                ORBEON.util.Utils.logMessage("Got value from server for element with class: " + jDocumentElement.attr('class'));
                            } else if (jDocumentElement.is('.xforms-output, .xforms-static, .xforms-label, .xforms-hint, .xforms-help')) {
                                // Output-only control, just set the value
                                ORBEON.xforms.Controls.setCurrentValue(documentElement, newControlValue);
                            } else {
                                var currentValue = ORBEON.xforms.Controls.getCurrentValue(documentElement);
                                if (currentValue != null) {
                                    previousServerValue = previousServerValue == null ? null : ORBEON.common.MarkupUtils.normalizeSerializedHtml(previousServerValue);
                                    currentValue    = ORBEON.common.MarkupUtils.normalizeSerializedHtml(currentValue);
                                    newControlValue = ORBEON.common.MarkupUtils.normalizeSerializedHtml(newControlValue);

                                    var doUpdate =
                                        // If this was an input that was recreated because of a type change, we always set its value
                                        recreatedInput ||
                                        // If this is a control for which we recreated the itemset, we want to set its value
                                        controlsWithUpdatedItemsets[controlId] ||
                                        (
                                            // Update only if the new value is different than the value already have in the HTML area
                                            currentValue != newControlValue
                                            // Update only if the value in the control is the same now as it was when we sent it to the server,
                                            // so not to override a change done by the user since the control value was last sent to the server
                                            && (
                                                (previousServerValue == null || currentValue == previousServerValue) ||
                                                // For https://github.com/orbeon/orbeon-forms/issues/3130
                                                //
                                                // We would like to test for "becomes readonly", but test below is equivalent:
                                                //
                                                // - either the control was already readonly, so `currentValue != newControlValue` was `true`
                                                //   as server wouldn't send a value otherwise
                                                // - or it was readwrite and became readonly, in which case we test for this below
                                                jDocumentElement.is('.xforms-readonly')
                                            )
                                        );

                                    if (doUpdate) {
                                        var promiseOrUndef = ORBEON.xforms.Controls.setCurrentValue(documentElement, newControlValue, true);

                                        // Store the server value as the client sees it, not as the server sees it. There can be a difference in the following cases:
                                        //
                                        // 1) For HTML editors, the HTML might change once we put it in the DOM.
                                        // 2) For select/select1, if the server sends an out-of-range value, the actual value of the field won't be the out
                                        //    of range value but the empty string.
                                        // 3) For boolean inputs, the server might tell us the new value is "" when the field becomes non-relevant, which is
                                        //    equivalent to "false".
                                        //
                                        // It is important to store in the serverValue the actual value of the field, otherwise if the server later sends a new
                                        // value for the field, since the current value is different from the server value, we will incorrectly think that the
                                        // user modified the field, and won't update the field with the value provided by the AjaxServer.

                                        // `setCurrentValue()` may return a jQuery `Promise` and if it does we update the server value only once it is resolved.
                                        // For details see https://github.com/orbeon/orbeon-forms/issues/2670.

                                        function setServerValue() {
                                            ORBEON.xforms.ServerValueStore.set(
                                                controlId,
                                                ORBEON.xforms.Controls.getCurrentValue(document.getElementById(controlId))
                                            );
                                        }

                                        if (_.isObject(promiseOrUndef) && _.isFunction(promiseOrUndef.done))
                                            promiseOrUndef.done(setServerValue);
                                        else if (_.isObject(promiseOrUndef) && _.isFunction(promiseOrUndef.then))
                                            promiseOrUndef.then(setServerValue);
                                        else
                                            setServerValue();
                                    }
                                }
                            }

                            // Call custom listener if any (temporary until we have a good API for custom components)
                            if (typeof xformsValueChangedListener != "undefined") {
                                xformsValueChangedListener(controlId, newControlValue);
                            }
                        }

                        function handleSwitchCase(elem) {
                            var id      = ORBEON.util.Dom.getAttribute(elem, "id");
                            var visible = ORBEON.util.Dom.getAttribute(elem, "visibility") == "visible";
                            ORBEON.xforms.Controls.toggleCase(id, visible);
                        }

                        function handleInit(elem) {
                            var controlId        = ORBEON.util.Dom.getAttribute(elem, "id");
                            var documentElement  = document.getElementById(controlId);
                            var relevant         = ORBEON.util.Dom.getAttribute(elem, "relevant");
                            var readonly         = ORBEON.util.Dom.getAttribute(elem, "readonly");

                            var instance = ORBEON.xforms.XBL.instanceForControl(documentElement);

                            if (_.isObject(instance)) {

                                var becomesRelevant    = relevant == "true";
                                var becomesNonRelevant = relevant == "false";

                                function callXFormsUpdateReadonlyIfNeeded() {
                                    if (readonly != null && _.isFunction(instance.xformsUpdateReadonly)) {
                                        instance.xformsUpdateReadonly(readonly == "true");
                                    }
                                }

                                if (becomesRelevant) {
                                    // NOTE: We don't need to call this right now, because  this is done via `instanceForControl`
                                    // the first time. `init()` is guaranteed to be called only once. Obviously this is a little
                                    // bit confusing.
                                    // if (_.isFunction(instance.init))
                                    //     instance.init();
                                    callXFormsUpdateReadonlyIfNeeded();
                                } else if (becomesNonRelevant) {

                                    // We ignore `readonly` when we become non-relevant

                                    if (_.isFunction(instance.destroy))
                                        instance.destroy();

                                    // The class's `destroy()` should do that anyway as we inject our own `destroy()`, but ideally
                                    // `destroy()` should only be called from there, and so the `null`ing of `xforms-xbl-object` should
                                    // take place here as well.
                                    $(documentElement).data("xforms-xbl-object", null);
                                } else {
                                    // Stays relevant or non-relevant (but we should never be here if we are non-relevant)
                                    callXFormsUpdateReadonlyIfNeeded();
                                }

                                _.each(elem.childNodes, function(childNode) {
                                    switch (ORBEON.util.Utils.getLocalName(childNode)) {
                                        case 'value':
                                            handleValue(childNode, controlId, false);
                                            break;
                                    }
                                });
                            }
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
                                        { type: "date",     schemaTypes: [ "{http://www.w3.org/2001/XMLSchema}date", "{http://www.w3.org/2002/xforms}date" ], className: "xforms-type-date" },
                                        { type: "time",     schemaTypes: [ "{http://www.w3.org/2001/XMLSchema}time", "{http://www.w3.org/2002/xforms}time" ], className: "xforms-type-time" },
                                        { type: "dateTime", schemaTypes: [ "{http://www.w3.org/2001/XMLSchema}dateTime", "{http://www.w3.org/2002/xforms}dateTime" ], className: "xforms-type-dateTime" },
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
                                        } else if (newType.type == "date" && ! isMinimal) {
                                            var newDateInput = createInput("xforms-type-date", 1);
                                            insertIntoDocument([newDateInput]);
                                            YAHOO.util.Dom.addClass(documentElement, "xforms-type-date");
                                            if (inputLabelElement != null) inputLabelElement.htmlFor = newDateInput.id;
                                        } else if (newType.type == "date" && isMinimal) {
                                            // Create image element
                                            var image = document.createElement("img");
                                            image.setAttribute("src", ORBEON.xforms.Page.getForm(formID).calendarImagePath);
                                            image.className = "xforms-input-input xforms-type-date xforms-input-appearance-minimal";
                                            insertIntoDocument([image]);
                                            YAHOO.util.Dom.addClass(documentElement, "xforms-type-date");
                                            if (inputLabelElement != null) inputLabelElement.htmlFor = documentElement.id;
                                        } else if (newType.type == "time") {
                                            var newTimeInput = createInput("xforms-type-time", 1);
                                            insertIntoDocument([newTimeInput]);
                                            YAHOO.util.Dom.addClass(documentElement, "xforms-type-time");
                                            if (inputLabelElement != null) inputLabelElement.htmlFor = newTimeInput.id;
                                        } else if (newType.type == "dateTime") {
                                            var newDateTimeInput = createInput("xforms-type-date", 1);
                                            insertIntoDocument([newDateTimeInput, createInput("xforms-type-time", 2)]);
                                            YAHOO.util.Dom.addClass(documentElement, "xforms-type-dateTime");
                                            if (inputLabelElement != null) inputLabelElement.htmlFor = newDateTimeInput.id;
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
                                    if (newLevel != null) {
                                        if (newLevel == "error")
                                            firstInput.attr("aria-invalid", "true");
                                        else
                                            firstInput.removeAttr("aria-invalid");
                                    }
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
                            var newVisited = ORBEON.util.Dom.getAttribute(elem, "visited");
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
                            _.each(elem.childNodes, function(childNode) {
                                switch (ORBEON.util.Utils.getLocalName(childNode)) {
                                    case 'value':
                                        handleValue(childNode, controlId, recreatedInput);
                                        break;
                                }
                            });

                            // Handle becoming non-relevant after everything so that XBL companion class instances
                            // are nulled and can be garbage-collected
                            if (relevant == "false")
                                ORBEON.xforms.Controls.setRelevant(documentElement, false);
                        }

                        function handleInnerHtml(elem) {

                            var innerHTML    = ORBEON.util.Dom.getStringValue(childrenWithLocalName(elem, 'value')[0]);
                            var initElem     = childrenWithLocalName(elem, 'init')[0];
                            var initValue    = _.isUndefined(initElem) ? null : ORBEON.util.Dom.getStringValue(initElem);
                            var destroyElem  = childrenWithLocalName(elem, 'destroy')[0];
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
                                    Controls.fullUpdateEvent.fire({control: documentElement});
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

                        function handleAttribute(elem) {
                            var newAttributeValue = ORBEON.util.Dom.getStringValue(elem);
                            var forAttribute = ORBEON.util.Dom.getAttribute(elem, "for");
                            var nameAttribute = ORBEON.util.Dom.getAttribute(elem, "name");
                            var htmlElement = document.getElementById(forAttribute);
                            if (htmlElement != null) {// use case: xh:html/@lang but HTML fragment produced
                                ORBEON.util.Dom.setAttribute(htmlElement, nameAttribute, newAttributeValue);
                            }
                        }

                        function handleText(elem) {
                            var newTextValue = ORBEON.util.Dom.getStringValue(elem);
                            var forAttribute = ORBEON.util.Dom.getAttribute(elem, "for");
                            var htmlElement = document.getElementById(forAttribute);

                            if (htmlElement != null && htmlElement.tagName.toLowerCase() == "title") {
                                // Set HTML title
                                document.title = newTextValue;
                            }
                        }

                        function handleRepeatIteration(elem) {
                            // Extract data from server response
                            var repeatId = ORBEON.util.Dom.getAttribute(elem, "id");
                            var iteration = ORBEON.util.Dom.getAttribute(elem, "iteration");
                            var relevant = ORBEON.util.Dom.getAttribute(elem, "relevant");
                            // Remove or add xforms-disabled on elements after this delimiter
                            if (relevant != null)
                                ORBEON.xforms.Controls.setRepeatIterationRelevance(formID, repeatId, iteration, relevant == "true" ? true : false);
                        }

                        function handleDialog(elem) {
                            var id        = ORBEON.util.Dom.getAttribute(elem, "id");
                            var visible   = ORBEON.util.Dom.getAttribute(elem, "visibility") == "visible";
                            var neighbor  = ORBEON.util.Dom.getAttribute(elem, "neighbor");
                            var yuiDialog = ORBEON.xforms.Globals.dialogs[id];

                            if (visible) {
                                ORBEON.xforms.Controls.showDialog(id, neighbor);
                            } else {
                                // If the dialog hasn't been initialized yet, there is nothing we need to do to hide it
                                if (_.isObject(yuiDialog)) {
                                    // Remove timer to show the dialog asynchronously so it doesn't show later!
                                    ORBEON.xforms.Page.getForm(formID).removeDialogTimerId(id);

                                    ORBEON.xforms.Globals.maskDialogCloseEvents = true;
                                    yuiDialog.hide();
                                    ORBEON.xforms.Globals.maskDialogCloseEvents = false;
                                    // Fixes cursor Firefox issue; more on this in dialog init code
                                    yuiDialog.element.style.display = "none";
                                }
                            }
                        }

                        _.each(controlValuesElements, function(controlValuesElement) {
                            _.each(controlValuesElement.childNodes, function(childNode) {
                                switch (ORBEON.util.Utils.getLocalName(childNode)) {
                                    case 'control':
                                        handleControl(childNode);
                                        break;
                                    case 'init':
                                        handleInit(childNode);
                                        break;
                                    case 'inner-html':
                                        handleInnerHtml(childNode);
                                        break;
                                    case 'attribute':
                                        handleAttribute(childNode);
                                        break;
                                    case 'text':
                                        handleText(childNode);
                                        break;
                                    case 'repeat-iteration':
                                        handleRepeatIteration(childNode);
                                        break;
                                    case 'dialog':
                                        handleDialog(childNode);
                                        break;
                                }

                            });
                        });

                        // Notification event if the type changed
                        _.each(recreatedInputs, function(documentElement, controlId) {
                            Controls.typeChangedEvent.fire({control: documentElement});
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

                                    var form = ORBEON.xforms.Page.getForm(formID);
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

                                    AjaxServer.createDelayedServerEvent(serverEvents, 0, true, false, formID);

                                    break;
                                }

                                case "poll": {
                                    var pollElement = childNode;
                                    var delayOrNull = ORBEON.util.Dom.getAttribute(pollElement, "delay");

                                    AjaxServer.createDelayedPollEvent(_.isNull(delayOrNull) ? undefined : parseInt(delayOrNull), formID);

                                    break;
                                }

                                // Submit form
                                case "submission": {
                                    var submissionElement = childNode;
                                    var showProgress = ORBEON.util.Dom.getAttribute(submissionElement, "show-progress");
                                    var replace      = ORBEON.util.Dom.getAttribute(submissionElement, "replace");
                                    var target       = ORBEON.util.Dom.getAttribute(submissionElement, "target");
                                    var action       = ORBEON.util.Dom.getAttribute(submissionElement, "action");

                                    // Increment and send sequence number
                                    var requestForm = document.getElementById(formID);
                                    // Go to another page
                                    if (showProgress != "false") {
                                        // Display loading indicator unless the server tells us not to display it
                                        newDynamicStateTriggersReplace = true;
                                    }

                                    /**
                                     * Set the action to the URL of the current page.
                                     *
                                     * We can't (or don't know how to) set the URL to the URL to which we did a submission
                                     * replace="all", so the best we can do it to set it to the current URL.
                                     *
                                     * We don't do it when the server generated a <form action="…"> that contains
                                     * xforms-server-submit, which can happen in cases (e.g. running in a portal) where for
                                     * some reason submitting to the URL of the page wouldn't work.
                                     *
                                     * When the target is an iframe, we add a ?t=id to work around a Chrome bug happening
                                     * when doing a POST to the same page that was just loaded, gut that the POST returns
                                     * a PDF. See:
                                     *
                                     *     https://code.google.com/p/chromium/issues/detail?id=330687
                                     *     https://github.com/orbeon/orbeon-forms/issues/1480
                                     */
                                    if (requestForm.action.indexOf("xforms-server-submit") == -1) {
                                        var isTargetAnIframe = _.isString(target) && $('#' + target).prop('tagName') == 'IFRAME';
                                        var a = $('<a>');
                                        a.prop('href', window.location.href);
                                        if (isTargetAnIframe) {
                                            var param = "t=" + _.uniqueId();
                                            var search = a.prop('search');
                                            var newSearch = (search == '' || search == '?') ? '?' + param : search + '&' + param;
                                            a.prop('search', newSearch);
                                        }
                                        requestForm.action = a.prop('href');
                                    }

                                    // Do we set a target on the form to open the page in another frame?
                                    var noTarget = (function() {
                                        if (target == null) {
                                            // Obviously, we won't try to set a target if the server didn't us one
                                            return true;
                                        } else if (! _.isUndefined(window.frames[target])) {
                                            // Pointing to a frame, so this won't open a new new window
                                            return false;
                                        } else {
                                            // See if we're able to open a new window
                                            if (target == "_blank")
                                                // Use target name that we can reuse, in case opening the window works
                                                target = Math.random().toString().substring(2);
                                            var newWindow = window.open("about:blank", target);
                                            if (newWindow && newWindow.close) {
                                                return false;
                                            } else {
                                                return true;
                                            }
                                        }
                                    })();

                                    // Set or reset `target` attribute
                                    if (noTarget)
                                        requestForm.removeAttribute("target");
                                    else
                                        requestForm.target = target;

                                    if (action == null) {
                                        // Reset as this may have been changed before by asyncAjaxRequest
                                        requestForm.removeAttribute("action");
                                    } else {
                                        // Set the requested target
                                        requestForm.action = action;
                                    }

                                    try {
                                        requestForm.submit();
                                    } catch (e) {
                                        // NOP: This is to prevent the error "Unspecified error" in IE. This can
                                        // happen when navigating away is cancelled by the user pressing cancel
                                        // on a dialog displayed on unload.
                                    }
                                    break;
                                }

                                // Display modal message
                                case "message": {
                                    var messageElement = childNode;
                                    ORBEON.xforms.action.Message.execute(messageElement);
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
                                            window.open(resource, target);
                                        }
                                    } else {
                                        window.open(resource, "_blank");
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
                                    var scriptElement = childNode;
                                    var functionName = ORBEON.util.Dom.getAttribute(scriptElement, "name");
                                    var targetId = ORBEON.util.Dom.getAttribute(scriptElement, "target-id");
                                    var observerId = ORBEON.util.Dom.getAttribute(scriptElement, "observer-id");
                                    var paramElements = childrenWithLocalName(scriptElement, "param");
                                    var paramValues = _.map(paramElements, function(paramElement) {
                                        return $(paramElement).text();
                                    });
                                    var args = [formID, functionName, targetId, observerId].concat(paramValues);
                                    ORBEON.xforms.server.Server.callUserScript.apply(ORBEON.xforms.server.Server, args);
                                    break;
                                }

                                // Show help message for specified control
                                case "help": {
                                    var helpElement = childNode;
                                    var controlId = ORBEON.util.Dom.getAttribute(helpElement, "control-id");
                                    var control = document.getElementById(controlId);
                                    ORBEON.xforms.Controls.showHelp(control);
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

                        var form = ORBEON.xforms.Page.getForm(formID)

                        _.each(responseDialogIdsToShowAsynchronously, function(dialogId) {
                            form.addDialogTimerId(dialogId, timerId);
                        });

                    } else {
                        // Process synchronously
                        handleControlDetails(controlValuesElements);
                        handleOtherActions(actionElement);
                    }

                } else if (ORBEON.util.Utils.getLocalName(childNode) == "errors") {

                    // NOTE: Similar code is in XFormsError.scala.
                    // <xxf:errors>
                    var errorsElement = childNode;
                    var details = "<ul>";

                    _.each(errorsElement.childNodes, function(errorElement) {
                        // <xxf:error exception="org.orbeon.saxon.trans.XPathException" file="gaga.xhtml" line="24" col="12">
                        //     Invalid date "foo" (Year is less than four digits)
                        // </xxf:error>
                        var exception = ORBEON.util.Dom.getAttribute(errorElement, "exception");
                        var file      = ORBEON.util.Dom.getAttribute(errorElement, "file");
                        var line      = ORBEON.util.Dom.getAttribute(errorElement, "line");
                        var col       = ORBEON.util.Dom.getAttribute(errorElement, "col");
                        var message   = ORBEON.util.Dom.getStringValue(errorElement);

                        // Create HTML with message
                        details += "<li>" + message;
                        if (file) details += " in " + ORBEON.common.MarkupUtils.escapeXmlMinimal(file);
                        if (line) details += " line " + ORBEON.common.MarkupUtils.escapeXmlMinimal(line);
                        if (col) details += " column " + ORBEON.common.MarkupUtils.escapeXmlMinimal(col);
                        if (exception) details += " (" + ORBEON.common.MarkupUtils.escapeXmlMinimal(exception) + ")";
                        details += "</li>";
                    });
                    AjaxServer.showError("Non-fatal error", details, formID, ignoreErrors);
                    details += "</ul>";
                }
            });

            if (newDynamicStateTriggersReplace) {
                // Display loading indicator when we go to another page.
                // Display it even if it was not displayed before as loading the page could take time.
                ORBEON.xforms.Page.loadingIndicator().showIfNotAlreadyVisible();
            }
        } catch (e) {
            // Show dialog with error to the user, as they won't be able to continue using the UI anyway
            AjaxServer.logAndShowError(e, formID, ignoreErrors);
            // Don't rethrow exception: we want to code that runs after the Ajax response is handled to run, so we have a chance to recover from this error
        }
    };

})();

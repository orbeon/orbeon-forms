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
    var _ = ORBEON._;

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

                    if (ORBEON.xforms.XFormsUi.isIOS() && ORBEON.xforms.XFormsUi.getZoomLevel() != 1.0) {
                        var dialogsToShowArray = ORBEON.xforms.XFormsUi.findDialogsToShow(controlValuesElements);
                        if (dialogsToShowArray.length > 0) {
                            responseDialogIdsToShowAsynchronously = dialogsToShowArray;
                            ORBEON.xforms.XFormsUi.resetIOSZoom();
                        }
                    }

                    // First add and remove "lines" in repeats (as itemset changed below might be in a new line)
                    ORBEON.xforms.XFormsUi.handleDeleteRepeatElements(controlValuesElements);

                    function handleControlDetails(controlValuesElements) {

                        var recreatedInputs = {};

                        _.each(controlValuesElements, function(controlValuesElement) {
                            _.each(controlValuesElement.childNodes, function(childNode) {
                                switch (ORBEON.util.Utils.getLocalName(childNode)) {
                                    case 'control':
                                        ORBEON.xforms.XFormsUi.handleControl(childNode, recreatedInputs, controlsWithUpdatedItemsets, formID);
                                        break;
                                    case 'init':
                                        ORBEON.xforms.XFormsUi.handleInit(childNode, controlsWithUpdatedItemsets);
                                        break;
                                    case 'inner-html':
                                        ORBEON.xforms.XFormsUi.handleInnerHtml(childNode);
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
                                    alert(childNode.textContent);
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

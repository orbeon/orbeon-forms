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

    /**
     * Server is a singleton.
     */
    ORBEON.xforms.server.AjaxServer = {};

    var AjaxServer = ORBEON.xforms.server.AjaxServer;
    var Controls = ORBEON.xforms.Controls;
    var Properties = ORBEON.util.Properties;

    AjaxServer.Event = function(form, targetId, otherId, value, eventName, bubbles, cancelable, ignoreErrors, showProgress, progressMessage, additionalAttribs) {
        // If no form is provided, infer the form based on that targetId, if one is provided
        this.form = YAHOO.lang.isObject(form) ? form
            : YAHOO.lang.isString(targetId) ? ORBEON.xforms.Controls.getForm(ORBEON.util.Dom.get(targetId)) : null;
        this.targetId = YAHOO.lang.isUndefined(targetId) ? null: targetId;
        this.otherId = YAHOO.lang.isUndefined(otherId) ? null: otherId;
        this.value = YAHOO.lang.isUndefined(value) ? null: value;
        this.eventName = YAHOO.lang.isUndefined(eventName) ? null: eventName;
        this.bubbles = YAHOO.lang.isUndefined(bubbles) ? null: bubbles;
        this.cancelable = YAHOO.lang.isUndefined(cancelable) ? null: cancelable;
        this.ignoreErrors = YAHOO.lang.isUndefined(ignoreErrors) ? null: ignoreErrors;
        this.showProgress = YAHOO.lang.isBoolean(showProgress) ? showProgress : true;
        this.progressMessage = YAHOO.lang.isUndefined(progressMessage) ? null: progressMessage;
        this.additionalAttribs = YAHOO.lang.isUndefined(additionalAttribs) ? null: additionalAttribs;
    };

    /**
     * When an exception happens while we communicate with the server, we catch it and show an error in the UI.
     * This is to prevent the UI from becoming totally unusable after an error.
     */
    AjaxServer.exceptionWhenTalkingToServer = function(e, formID) {
        ORBEON.util.Utils.logMessage("JavaScript error");
        ORBEON.util.Utils.logMessage(e);
        var details = "Exception in client-side code.";
        details += "<ul>";
        if (e.message != null) details += "<li>Message: " + e.message + "</li>";
        if (e.fileName != null) details += "<li>File: " + e.fileName + "</li>";
        if (e.lineNumber != null) details += "<li>Line number: " + e.lineNumber + "</li>";
        details += "</ul>";
        AjaxServer.showError("Exception in client-side code", details, formID);
    };

    /**
     * Display the error panel and shows the specified detailed message in the detail section of the panel.
     */
    AjaxServer.showError = function(title, details, formID) {
        ORBEON.xforms.Events.errorEvent.fire({title: title, details: details });
        if (!ORBEON.xforms.Globals.requestIgnoreErrors && ORBEON.util.Properties.showErrorDialog.get()) {
            var formErrorPanel = ORBEON.xforms.Globals.formErrorPanel[formID];
            if (formErrorPanel) {
                // Render the dialog if needed
                formErrorPanel.element.style.display = "block";
                formErrorPanel.errorTitleDiv.innerHTML = title;
                formErrorPanel.errorDetailsDiv.innerHTML = details;
                formErrorPanel.show();
                ORBEON.xforms.Globals.lastDialogZIndex += 2;
                formErrorPanel.cfg.setProperty("zIndex", ORBEON.xforms.Globals.lastDialogZIndex);
                formErrorPanel.center();
            }
        }
    };

    AjaxServer.fireEvents = function(events, incremental) {
        if (!ORBEON.xforms.Offline.isOnline) {
            // Go through all events
            var valueChangeEvents = [];
            for (var eventIndex = 0; eventIndex < events.length; eventIndex++) {
                var event = events[eventIndex];
                if (event.eventName == "xxforms-value-change-with-focus-change") {
                    valueChangeEvents.push(event);
                    // Store new value of control
                    ORBEON.xforms.Offline.controlValues[event.targetId] = event.value;
                }
            }
            // Evaluate MIPS if there was a value change event
            if (valueChangeEvents.length > 0) {
                // Store in memory the value change events
                ORBEON.xforms.Offline.memoryOfflineEvents = ORBEON.xforms.Offline.memoryOfflineEvents.concat(valueChangeEvents);
                // Insert delay before we evaluate MIPS, just to avoid repeatedly evaluating MIPS if nothing changed
                ORBEON.xforms.Globals.executeEventFunctionQueued++;
                window.setTimeout(
                    function() {
                        ORBEON.xforms.Globals.executeEventFunctionQueued--;
                        if (ORBEON.xforms.Globals.executeEventFunctionQueued == 0)
                            ORBEON.xforms.Offline.evaluateMIPs();
                    },
                    ORBEON.util.Properties.internalShortDelay.get()
                );
            }
        } else {
            // We do not filter events when the modal progress panel is shown.
            //      It is tempting to filter all the events that happen when the modal progress panel is shown.
            //      However, if we do so we would loose the delayed events that become mature when the modal
            //      progress panel is shown. So we either need to make sure that it is not possible for the
            //      browser to generate events while the modal progress panel is up, or we need to filter those
            //      event before this method is called.

            // Store the time of the first event to be sent in the queue
            var currentTime = new Date().getTime();
            if (ORBEON.xforms.Globals.eventQueue.length == 0)
                ORBEON.xforms.Globals.eventsFirstEventTime = currentTime;

            // Store events to fire
            for (var eventIndex = 0; eventIndex < events.length; eventIndex++) {
                ORBEON.xforms.Globals.eventQueue.push(events[eventIndex]);
            }

            // Fire them with a delay to give us a change to aggregate events together
            ORBEON.xforms.Globals.executeEventFunctionQueued++;
            if (incremental && !(currentTime - ORBEON.xforms.Globals.eventsFirstEventTime >
                                 ORBEON.util.Properties.delayBeforeIncrementalRequest.get())) {
                // After a delay (e.g. 500 ms), run executeNextRequest() and send queued events to server
                // if there are no other executeNextRequest() that have been added to the queue after this
                // request.
                window.setTimeout(
                    function() { AjaxServer.executeNextRequest(false); },
                    ORBEON.util.Properties.delayBeforeIncrementalRequest.get()
                );
            } else {
                // After a very short delay (e.g. 20 ms), run executeNextRequest() and force queued events
                // to be sent to the server, even if there are other executeNextRequest() queued.
                // The small delay is here so we don't send multiple requests to the server when the
                // browser gives us a sequence of events (e.g. focus out, change, focus in).
                window.setTimeout(
                    function() { AjaxServer.executeNextRequest(true); },
                    ORBEON.util.Properties.internalShortDelay.get()
                );
            }
            ORBEON.xforms.Globals.lastEventSentTime = new Date().getTime(); // Update the last event sent time
        }
    };

    /**
     * Create a timer which after the specified delay will fire a server event.
     */
    AjaxServer.createDelayedServerEvent = function(serverEvents, delay, showProgress, progressMessage, discardable, formID) {
        var timerId = window.setTimeout(function () {
            var event = new AjaxServer.Event(ORBEON.util.Dom.get(formID), null, null,
                    serverEvents, "xxforms-server-events", null, null, null, showProgress, progressMessage);
            AjaxServer.fireEvents([event]);
        }, delay);
        // Save timer id for this discardable timer
        if (discardable) {
            var discardableTimerIds = ORBEON.xforms.Globals.discardableTimerIds;
            discardableTimerIds[formID] = discardableTimerIds[formID] || [];
            discardableTimerIds[formID].push(timerId);
        }
    };

    AjaxServer._debugEventQueue = function() {
        ORBEON.util.Utils.logMessage("Event queue:");
        for (var eventIndex = 0; eventIndex < ORBEON.xforms.Globals.eventQueue.length; eventIndex++) {
            var event = ORBEON.xforms.Globals.eventQueue[eventIndex];
            ORBEON.util.Utils.logMessage(" " + eventIndex + " - name: " + event.eventName + " | targetId: " + event.targetId + " | value: " + event.value);
        }
    };

    AjaxServer.executeNextRequest = function(bypassRequestQueue) {
        bypassRequestQueue = typeof(bypassRequestQueue) == "boolean" && bypassRequestQueue == true;

        ORBEON.xforms.Globals.executeEventFunctionQueued--;
        if (!ORBEON.xforms.Globals.requestInProgress
                && ORBEON.xforms.Globals.eventQueue.length > 0
                && (bypassRequestQueue || ORBEON.xforms.Globals.executeEventFunctionQueued == 0)) {

            // Populate map for efficiency
            // TODO: could compute this once and for all
            var eventsToFilter = {};
            {
                var eventsToFilterProperty = ORBEON.util.Properties.clientEventsFilter.get().split(" ");
                for (var eventIndex = 0; eventIndex < eventsToFilterProperty.length; eventIndex++)
                    eventsToFilter[eventsToFilterProperty[eventIndex]] = true;
            }

            var foundActivatingEvent = false;
            if (ORBEON.util.Properties.clientEventMode.get() == "deferred") {

                // Element with class xxforms-events-mode-default which is the parent of a target
                var parentWithDefaultClass = null;
                // Set to true when we find a target which is not under and element with the default class
                var foundTargetWithNoParentWithDefaultClass = false;

                // Look for events that we need to send to the server when deferred mode is enabled
                eventLoop: for (var eventIndex = 0; eventIndex < ORBEON.xforms.Globals.eventQueue.length; eventIndex++) {

                    var event = ORBEON.xforms.Globals.eventQueue[eventIndex];

                    // DOMActivate is considered to be an "activating" event
                    if (event.eventName == "DOMActivate") {
                        foundActivatingEvent = true;
                        break;
                    }

                    // Check if we find a class on the target that tells us this is an activating event
                    // Do NOT consider a filtered event as an activating event
                    if (event.targetId != null && eventsToFilter[event.eventName] == null) {
                        var target = ORBEON.util.Dom.get(event.targetId);
                        if (target == null) {
                            // Target is not on the client. For most use cases, assume event should be dispatched right away.
                            foundActivatingEvent = true;
                            break;
                        } else {
                            // Target is on the client
                            if (YAHOO.util.Dom.hasClass(target, "xxforms-events-mode-default")) {
                                foundActivatingEvent = true;
                                break;
                            }

                            // Look for parent with the default class
                            var parent = target.parentNode;
                            var foundParentWithDefaultClass = false;
                            while (parent != null) {
                                // Found a parent with the default class
                                if (parent.nodeType == ELEMENT_TYPE && YAHOO.util.Dom.hasClass(parent, "xxforms-events-mode-default")) {
                                    foundParentWithDefaultClass = true;
                                    if (foundTargetWithNoParentWithDefaultClass) {
                                        // And there is another target which is outside of a parent with a default class
                                        foundActivatingEvent = true;
                                        break eventLoop;
                                    }
                                    if (parentWithDefaultClass == null) {
                                        parentWithDefaultClass = parent;
                                    } else if (parentWithDefaultClass != parent) {
                                        // And there is another target which is under another parent with a default class
                                        foundActivatingEvent = true;
                                        break eventLoop;
                                    }
                                    break;
                                }
                                parent = parent.parentNode;
                            }
                            // Record the fact
                            if (! foundParentWithDefaultClass) {
                                foundTargetWithNoParentWithDefaultClass = true;
                                if (parentWithDefaultClass != null) {
                                    foundActivatingEvent = true;
                                    break;
                                }
                            }
                        }
                    }
                }
            } else {
                // Every event is an activating event
                foundActivatingEvent = true;
            }

            if (foundActivatingEvent) {

                // Collapse value change for the same control, filter events as specified by property,
                // and remove value change events if the server already knows about that value
                {
                    var seenControlValue = {};
                    var newEvents = [];
                    var firstUploadProgressEvent = null;

                    for (var eventIndex = 0; eventIndex < ORBEON.xforms.Globals.eventQueue.length; eventIndex++) {
                        // Extract information from event array
                        var event = ORBEON.xforms.Globals.eventQueue[eventIndex];
                        // Proceed with this event only if this is not one of the event we filter
                        if (eventsToFilter[event.eventName] == null) {
                            if (event.eventName == "xxforms-value-change-with-focus-change") {
                                // Value change is handled specially as values are collapsed

                                if (seenControlValue[event.targetId] == null) {
                                    // Haven't yet seen this control in current block of events

                                    var serverValue = ORBEON.xforms.ServerValueStore.get(event.targetId);
                                    if (YAHOO.util.Dom.hasClass(ORBEON.util.Dom.get(event.targetId), "xforms-upload") ||
                                            (serverValue == null || serverValue != event.value)) {

                                        // Add event
                                        seenControlValue[event.targetId] = event;
                                        ORBEON.xforms.ServerValueStore.set(event.targetId, event.value);
                                        newEvents.push(event);
                                    }
                                } else {
                                    // Have seen this control already in current block of events

                                    // Keep latest value
                                    seenControlValue[event.targetId].value = event.value;
                                    // Update server value
                                    ORBEON.xforms.ServerValueStore.set(event.targetId, event.value);
                                }
                            } else if (event.eventName == "xxforms-upload-progress") {
                                // Collapse multiple upload progress requests only sending the one for the latest control
                                if (firstUploadProgressEvent == null) {
                                    firstUploadProgressEvent = event;
                                    newEvents.push(event);
                                } else {
                                    firstUploadProgressEvent.targetId = event.targetId;
                                }
                            } else {
                                // Any non-value change event is a boundary between event blocks
                                seenControlValue = {};

                                // Add event
                                newEvents.push(event);
                            }
                        }
                    }
                    ORBEON.xforms.Globals.eventQueue = newEvents;
                }

                // Check again that we have events to send after collapsing
                if (ORBEON.xforms.Globals.eventQueue.length > 0) {

                    // Save the form for this request
                    ORBEON.xforms.Globals.requestForm = ORBEON.xforms.Globals.eventQueue[0].form;
                    var formID = ORBEON.xforms.Globals.requestForm.id;

                    // Remove from this list of ids that changed the id of controls for
                    // which we have received the keyup corresponding to the keydown
                    for (var id  in ORBEON.xforms.Globals.changedIdsRequest) {
                        if (ORBEON.xforms.Globals.changedIdsRequest[id] == 0)
                            ORBEON.xforms.Globals.changedIdsRequest[id] = null;
                    }

                    ORBEON.xforms.Globals.requestIgnoreErrors = true;
                    var sendInitialDynamicState = false;
                    var showProgress = false;
                    var progressMessage;
                    var foundEventOtherThanHeartBeat = false;
                    for (var eventIndex = 0; eventIndex < ORBEON.xforms.Globals.eventQueue.length; eventIndex++) {
                        var event = ORBEON.xforms.Globals.eventQueue[eventIndex];
                        // Figure out if we will be ignoring error during this request or not
                        if (!event.ignoreErrors)
                            ORBEON.xforms.Globals.requestIgnoreErrors = false;
                        // Figure out whether we need to send the initial dynamic state
                        if (event.eventName == "xxforms-all-events-required" || event.eventName == "xxforms-offline")
                            sendInitialDynamicState = true;
                        // Remember if we see an event other than a session heartbeat
                        if (event.eventName != "xxforms-session-heartbeat") foundEventOtherThanHeartBeat = true;
                        // Figure out if any of the events asks for the progress to be shown (the default)
                        if (event.showProgress)
                            showProgress = true;
                        // Figure out if all the events have the same progress message
                        if (YAHOO.lang.isString(event.progressMessage)) {
                            // Only use the event's progressMessage if it is equal to the value of progressMessage we already have
                            progressMessage = eventIndex == 0 ? event.progressMessage
                                    : progressMessage == event.progressMessage ? event.progressMessage
                                    : null;
                        } else {
                            progressMessage = null;
                        }

                        // Case of going offline
                        if (event.eventName == "DOMActivate") {
                            var eventElement = ORBEON.util.Dom.get(event.targetId);
                            if (eventElement && YAHOO.util.Dom.hasClass(eventElement, "xxforms-offline"))
                                sendInitialDynamicState = true;
                        }
                    }

                    // Get events to send, filtering out those that are not for the form we chose
                    var eventsToSend = [];
                    var remainingEvents = [];
                    _.each(ORBEON.xforms.Globals.eventQueue, function(event) {
                        ((Controls.getForm(event.form) == ORBEON.xforms.Globals.requestForm) ? eventsToSend : remainingEvents)
                            .push(event);
                    });
                    ORBEON.xforms.Globals.eventQueue = remainingEvents;

                    // Mark this as loading
                    ORBEON.xforms.Globals.requestInProgress = true;

                    // Since we are sending a request, throw out all the discardable timers.
                    // But only do this if we are not just sending a heartbeat event, which is handled in a more efficient
                    // way by the server, skipping the "normal" processing which includes checking if there are
                    // any discardable events waiting to be executed.
                    if (foundEventOtherThanHeartBeat) {
                        var discardableTimerIds = ORBEON.xforms.Globals.discardableTimerIds[formID] || [];
                        for (var discardableTimerIdIndex = 0; discardableTimerIdIndex < discardableTimerIds.length; discardableTimerIdIndex++) {
                            var discardableTimerId = discardableTimerIds[discardableTimerIdIndex];
                            window.clearTimeout(discardableTimerId);
                        }
                        ORBEON.xforms.Globals.discardableTimerIds[formID] = [];
                    }

                    // Show loading indicator, unless all the events asked us not to display it
                    if (showProgress) {
                        var delayBeforeDisplayLoading = ORBEON.util.Properties.delayBeforeDisplayLoading.get();
                        if (delayBeforeDisplayLoading == 0) xformsDisplayLoading(progressMessage);
                        else window.setTimeout(function() { xformsDisplayLoading(progressMessage); }, delayBeforeDisplayLoading);
                    }

                    // Build request
                    var requestDocumentString = [];

                    // Add entity declaration for nbsp. We are adding this as this entity is generated by the FCK editor.
                    // The "unnecessary" concatenation is done to prevent IntelliJ from wrongly interpreting this
                    requestDocumentString.push('<!' + 'DOCTYPE xxforms:event-request [<!ENTITY nbsp "&#160;">]>\n');

                    var indent = "    ";
                    {
                        // Start request
                        requestDocumentString.push('<xxforms:event-request xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">\n');

                        // Add form UUID
                        requestDocumentString.push(indent);
                        requestDocumentString.push('<xxforms:uuid>');
                        requestDocumentString.push(ORBEON.xforms.Document.getFromClientState(formID, "uuid"));
                        requestDocumentString.push('</xxforms:uuid>\n');

                        // Increment and send sequence number if we have at least one event which is not a request for upload progress or session heartbeat
                        // NOTE: Still send the element name even if empty as this is what the schema and server-side code expects
                        requestDocumentString.push(indent);
                        requestDocumentString.push('<xxforms:sequence>');
                        if (_.detect(eventsToSend, function(event) { return event.eventName != "xxforms-upload-progress" && event.eventName != "xxforms-session-heartbeat"; })) {
                            var currentSequenceNumber = ORBEON.xforms.Document.getFromClientState(formID, "sequence");
                            requestDocumentString.push(currentSequenceNumber);
                            ORBEON.xforms.Document.storeInClientState(formID, "sequence", parseInt(currentSequenceNumber) + 1);
                        }
                        requestDocumentString.push('</xxforms:sequence>\n');

                        // Add static state
                        var staticState = ORBEON.xforms.Globals.formStaticState[formID].value;
                        if (staticState != null && staticState != "") {
                            requestDocumentString.push(indent);
                            requestDocumentString.push('<xxforms:static-state>');
                            requestDocumentString.push(staticState);
                            requestDocumentString.push('</xxforms:static-state>\n');
                        }

                        // Add dynamic state
                        var dynamicState = ORBEON.xforms.Globals.formDynamicState[formID].value;
                        if (dynamicState != null && dynamicState != "") {
                            requestDocumentString.push(indent);
                            requestDocumentString.push('<xxforms:dynamic-state>');
                            requestDocumentString.push(dynamicState);
                            requestDocumentString.push('</xxforms:dynamic-state>\n');
                        }

                        // Add initial dynamic state if needed
                        if (sendInitialDynamicState) {
                            requestDocumentString.push(indent);
                            requestDocumentString.push('<xxforms:initial-dynamic-state>');
                            requestDocumentString.push(ORBEON.xforms.Document.getFromClientState(formID, "initial-dynamic-state"));
                            requestDocumentString.push('</xxforms:initial-dynamic-state>\n');
                        }

                        // Keep track of the events we have handled, so we can later remove them from the queue

                        // Start action
                        requestDocumentString.push(indent);
                        requestDocumentString.push('<xxforms:action>\n');

                        // Add events
                        _.each(eventsToSend, function(event) {
                            // Create <xxforms:event> element
                            requestDocumentString.push(indent + indent);
                            requestDocumentString.push('<xxforms:event');
                            requestDocumentString.push(' name="' + event.eventName + '"');
                            if (event.targetId != null)
                                requestDocumentString.push(' source-control-id="' + event.targetId + '"');
                            if (event.otherId != null)
                                requestDocumentString.push(' other-control-id="' + event.otherId + '"');
                            if (event.additionalAttribs != null) {
                                for(var attribIndex = 0; attribIndex < event.additionalAttribs.length - 1; attribIndex+=2)
                                    requestDocumentString.push(' '+ event.additionalAttribs[attribIndex] +'="' + event.additionalAttribs[attribIndex+1] + '"');
                            }
                            requestDocumentString.push('>');
                            if (event.value != null) {
                                // When the range is used we get an int here when the page is first loaded
                                if (typeof event.value == "string") {
                                    event.value = event.value.replace(XFORMS_REGEXP_AMPERSAND, "&amp;");
                                    event.value = event.value.replace(XFORMS_REGEXP_OPEN_ANGLE, "&lt;");
                                    event.value = event.value.replace(XFORMS_REGEXP_CLOSE_ANGLE, "&gt;");
                                }
                                requestDocumentString.push(event.value);
                            }
                            requestDocumentString.push('</xxforms:event>\n');
                            remainingEvents = _.without(remainingEvents, event);
                        });

                        // End action
                        requestDocumentString.push(indent);
                        requestDocumentString.push('</xxforms:action>\n');

                        // End request
                        requestDocumentString.push('</xxforms:event-request>');

                        // New event queue now formed of the events we haven't just handled
                        ORBEON.xforms.Globals.eventQueue = remainingEvents;
                    }

                    // Send request
                    ORBEON.xforms.Globals.requestTryCount = 0;
                    ORBEON.xforms.Globals.requestDocument = requestDocumentString.join("");
                    AjaxServer.asyncAjaxRequest();
                }
            }
        }

        // Hide loading indicator if we have not started a new request (nothing more to run)
        // and there are not events in the queue. However make sure not to hide the error message
        // if the last XHR query returned an error.
        if (!ORBEON.xforms.Globals.requestInProgress && ORBEON.xforms.Globals.eventQueue.length == 0) {
            xformsDisplayIndicator("none");
        }
    };

    AjaxServer.setTimeoutOnCallback = function(callback) {
        var ajaxTimeout = ORBEON.util.Properties.delayBeforeAjaxTimeout.get();
        if (ajaxTimeout != -1)
            callback.timeout = ajaxTimeout;
    };

    AjaxServer.asyncAjaxRequest = function() {
        try {
            ORBEON.xforms.Globals.requestTryCount++;
            YAHOO.util.Connect.setDefaultPostHeader(false);
            YAHOO.util.Connect.initHeader("Content-Type", "application/xml");
            var formId = ORBEON.xforms.Globals.requestForm.id;
            var callback = {
                success: AjaxServer.handleResponseAjax,
                failure: AjaxServer.handleFailureAjax,
                argument: { formId: formId }
            };
            AjaxServer.setTimeoutOnCallback(callback);
            YAHOO.util.Connect.asyncRequest("POST", ORBEON.xforms.Globals.xformsServerURL[formId], callback, ORBEON.xforms.Globals.requestDocument);
        } catch (e) {
            ORBEON.xforms.Globals.requestInProgress = false;
            AjaxServer.exceptionWhenTalkingToServer(e, formID);
        }
    };

    /**
     * Retry after a certain delay which increases with the number of consecutive failed request, but which
     * never exceeds a maximum delay.
     */
    AjaxServer.retryRequestAfterDelay = function(requestFunction) {
        var delay = Math.min(ORBEON.util.Properties.retryDelayIncrement.get() * (ORBEON.xforms.Globals.requestTryCount - 1),
                ORBEON.util.Properties.retryMaxDelay.get());
        if (delay == 0) requestFunction();
        else window.setTimeout(requestFunction, delay);
    };

    /**
     * Unless we get a clear indication from the server that an error occurred, we retry to send the request to
     * the AjaxServer.
     *
     * Browsers behaviors:
     *
     *      On Safari, when o.status == 0, it might not be an error. Instead, it can be happen when users click on a
     *      link to download a file, and Safari stops the current Ajax request before it knows that new page is loaded
     *      (vs. a file being downloaded). With the current core, we assimilate this to a communication error, and
     *      we'll retry to send the Ajax request to the AjaxServer.
     *
     *      On Firefox, when users navigate to another page while an Ajax request is in progress,
     *      we receive an error here, which we don't want to display. We don't have a good way of knowing if we get
     *      this error because there was really a communication failure or if this is because the user is
     *      going to another page. We handle this as a communication failure, and resend the request to the server,
     *      This  doesn't hurt as the server knows that it must not execute the request more than once.
     *
     */
    AjaxServer.handleFailureAjax = function(o) {

        if (o.responseXML && o.responseXML.documentElement && o.responseXML.documentElement.tagName.indexOf("error") != -1) {
            // If we get an error document as follows, we consider this to be a permanent error, we don't retry, and
            // we show an error to users.
            //      <error>
            //          <title>...</title>
            //          <body>...</body>
            //      </error>
            ORBEON.xforms.Globals.requestInProgress = false;
            ORBEON.xforms.Globals.requestDocument = "";
            var formID = ORBEON.xforms.Globals.requestForm.id;
            var title = ORBEON.util.Dom.getStringValue(ORBEON.util.Dom.getElementsByName(o.responseXML.documentElement, "title", null)[0]);
            var detailsFromBody = ORBEON.util.Dom.getStringValue(ORBEON.util.Dom.getElementsByName(o.responseXML.documentElement, "body", null)[0]);
            AjaxServer.showError(title, detailsFromBody, formID);
        } else {
            AjaxServer.retryRequestAfterDelay(AjaxServer.asyncAjaxRequest);
        }
    };

    AjaxServer.handleResponseAjax = function(o) {

        var responseXML = o.responseXML;
        if (!YAHOO.lang.isUndefined(o.getResponseHeader) && YAHOO.lang.trim(o.getResponseHeader["Content-Type"]) == "text/html") {

            if (window.dojox && dojox.html && dojox.html.set) {
                // Parse content we receive into a new div we create just for that purpose
                var temporaryContainer = document.createElement("div");
                temporaryContainer.innerHTML = o.responseText;
                var newPortletDiv = ORBEON.util.Dom.getChildElementByIndex(temporaryContainer, 0);

                // Get existing div which is above the form that issued this request
                var existingPortletDiv = ORBEON.xforms.Globals.requestForm;
                while (existingPortletDiv != null && existingPortletDiv.className && !YAHOO.util.Dom.hasClass(existingPortletDiv, "orbeon-portlet-div"))
                    existingPortletDiv = existingPortletDiv.parentNode;

                // Remove top-level event handlers in case the user interacts with newly added elements before
                // ORBEON.xforms.Init.document() has completed
                if (ORBEON.xforms.Globals.topLevelListenerRegistered) {
                    if (ORBEON.xforms.Globals.isRenderingEngineTrident) {
                        YAHOO.util.Event.removeListener(document, "focusin", ORBEON.xforms.Events.focus);
                        YAHOO.util.Event.removeListener(document, "focusout", ORBEON.xforms.Events.blur);
                        YAHOO.util.Event.removeListener(document, "change", ORBEON.xforms.Events.change);
                    } else {
                        document.removeEventListener("focus", ORBEON.xforms.Events.focus, true);
                        document.removeEventListener("blur", ORBEON.xforms.Events.blur, true);
                        document.removeEventListener("change", ORBEON.xforms.Events.change, true);
                    }

                    YAHOO.util.Event.removeListener(document, "keypress", ORBEON.xforms.Events.keypress);
                    YAHOO.util.Event.removeListener(document, "keydown", ORBEON.xforms.Events.keydown);
                    YAHOO.util.Event.removeListener(document, "keyup", ORBEON.xforms.Events.keyup);
                    YAHOO.util.Event.removeListener(document, "mouseover", ORBEON.xforms.Events.mouseover);
                    YAHOO.util.Event.removeListener(document, "mouseout", ORBEON.xforms.Events.mouseout);
                    YAHOO.util.Event.removeListener(document, "click", ORBEON.xforms.Events.click);
                    YAHOO.util.Event.removeListener(window, "resize", ORBEON.xforms.Events.resize);
                    YAHOO.widget.Overlay.windowScrollEvent.unsubscribe(ORBEON.xforms.Events.scrollOrResize);
                    YAHOO.widget.Overlay.windowResizeEvent.unsubscribe(ORBEON.xforms.Events.scrollOrResize);

                    ORBEON.xforms.Globals.topLevelListenerRegistered = false;
                }

                // Run custom clean-up function
                // NOTE: For now, global function, so we don't undefine it after calling it
                if (typeof xformsPageUnloadedServer != "undefined") {
                    xformsPageUnloadedServer();
                }

                // Clear existing custom JavaScript initialization function if any
                if (typeof xformsPageLoadedServer != "undefined") {
                    xformsPageLoadedServer = undefined;
                }

                // Remove content from existing div
                while (existingPortletDiv.childNodes.length > 0)
                    existingPortletDiv.removeChild(existingPortletDiv.firstChild);

                // Replace the content and re-initialize XForms
                // NOTE: renderStyles: false: for now, tell Dojo not to process CSS within content, as this seems to cause JavaScript errors down the line.
                dojox.html.set(existingPortletDiv, o.responseText, { renderStyles: false, executeScripts: true, adjustPaths: true, referencePath: "/" });
                ORBEON.xforms.Init.document();
            }
        } else {
            if (o.responseText &&
                    (!responseXML || (responseXML && responseXML.documentElement && responseXML.documentElement.tagName.toLowerCase() == "html"))) {
                // The XML document does not come in o.responseXML: parse o.responseText.
                // This happens in particular when we get a response after a background upload.
                var xmlString = o.responseText.replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&amp;/g, "&");
                responseXML = ORBEON.util.Dom.stringToDom(xmlString);
            }

            if (o.responseText != "" && responseXML && responseXML.documentElement && responseXML.documentElement.tagName.indexOf("event-response") != -1) {

                // Everything is fine with the response

                // If neither of these two conditions is met, hide the modal progress panel:
                //      a) The server tells us to do a submission or load, so we don't want to remove it otherwise
                //         users could start interacting with a page which is going to be replaced shortly.
                //      b) There is another Ajax request in the queue, which could be the one that triggered the
                //         display of the modal progress panel, so we don't want to hide before that request ran.
                // We remove the modal progress panel before handling DOM response, as xxf:script may dispatch
                // events and we don't want them to be filtered. If there are server events, we don't remove the
                // panel until they have been processed, i.e. the request sending the server events returns.
                if (! (AjaxServer.keepModelProgressPanelDisplayed(responseXML)
                        || ORBEON.xforms.Globals.eventQueue.length > 0)) {
                    ORBEON.util.Utils.hideModalProgressPanel();
                }

                var formID = o.argument.formId;
                AjaxServer.handleResponseDom(responseXML, formID);
                // Reset changes, as changes are included in this bach of events
                ORBEON.xforms.Globals.changedIdsRequest = {};
                // Notify listeners that we are done processing this request
                ORBEON.xforms.Events.ajaxResponseProcessedEvent.fire();
                // Go ahead with next request, if any
                ORBEON.xforms.Globals.requestDocument = "";
                ORBEON.xforms.Globals.executeEventFunctionQueued++;
                AjaxServer.executeNextRequest(false);

            } else {
                // Consider this a failure
                AjaxServer.handleFailureAjax(o);
            }
        }
    };

    /**
     * Keep the model progress panel if the server tells us to do a submission or load which isn't opened in another
     * window and for which the user didn't specify xxforms:show-progress="false".
     *
     * The logic here corresponds to the following XPath:
     * exists((//xxf:submission, //xxf:load)[empty(@target) and empty(@show-progress)])
     */
    AjaxServer.keepModelProgressPanelDisplayed = function(responseXML) {
        if (responseXML && responseXML.documentElement
                    && responseXML.documentElement.tagName.indexOf("event-response") != -1) {
            var foundLoadOrSubmissionOrLoadWithNoTargetNoDownload = false;
            YAHOO.util.Dom.getElementsBy(function(element) {
                var localName = ORBEON.util.Utils.getLocalName(element);
                var hasTargetAttribute = ORBEON.util.Dom.getAttribute(element, "target") == null;
                if ((localName  == "submission" || localName == "load")) {
                    if (ORBEON.util.Dom.getAttribute(element, "target") == null && ORBEON.util.Dom.getAttribute(element, "show-progress") == null)
                        foundLoadOrSubmissionOrLoadWithNoTargetNoDownload = true;
                }
            }, null, responseXML.documentElement);
            return foundLoadOrSubmissionOrLoadWithNoTargetNoDownload;
        }
        return false;
    };

    /**
     * Process events in the DOM passed as parameter.
     *
     * @param responseXML       DOM containing events to process
     */
    AjaxServer.handleResponseDom = function(responseXML, formID) {

        try {
            var responseRoot = responseXML.documentElement;

            // Whether this response has triggered a load which will replace the current page.
            var newDynamicStateTriggersReplace = false;

            var xmlNamespace = null; // xforms namespace
            // Getting xforms namespace
            for (var j = 0; j < responseRoot.attributes.length; j++) {
                if (responseRoot.attributes[j].nodeValue == XXFORMS_NAMESPACE_URI) {
                    var attrName = responseRoot.attributes[j].name;
                    xmlNamespace = attrName.substr(attrName.indexOf(":") + 1);
                    break;
                }
            }

            // If the last request was taking the form offline
            if (ORBEON.xforms.Offline.lastRequestIsTakeOnline) {
                ORBEON.xforms.Offline.lastRequestIsTakeOnline = false;
                // See if we are still offline (if there is a /xxf:event-response/xxf:action/xxf:offline)
                var actionElements = ORBEON.util.Dom.getElementsByName(responseRoot, "action", xmlNamespace);
                var offlineElements = ORBEON.util.Dom.getElementsByName(actionElements[0], "offline", xmlNamespace);
                if (offlineElements.length == 1) {
                    // Server is asking us to stay offline
                    ORBEON.xforms.Offline.isOnline = false;
                } else {
                    // Remove form from store and database
                    ORBEON.xforms.Offline.gearsDatabase.execute("delete from Offline_Forms where url = ?", [ window.location.href ]).close();
                    ORBEON.xforms.Offline.formStore.remove(window.location.href);
                    // Then we'll continue processing of the request as usual
                }
            }

            for (var i = 0; i < responseRoot.childNodes.length; i++) {

                // Store new dynamic and static state as soon as we find it. This is because the server only keeps the last
                // dynamic state. So if a JavaScript error happens later on while processing the response,
                // the next request we do we still want to send the latest dynamic state known to the AjaxServer.
                if (ORBEON.util.Utils.getLocalName(responseRoot.childNodes[i]) == "dynamic-state") {
                    var newDynamicState = ORBEON.util.Dom.getStringValue(responseRoot.childNodes[i]);
                    ORBEON.xforms.Globals.formDynamicState[formID].value = newDynamicState;
                } else if (ORBEON.util.Utils.getLocalName(responseRoot.childNodes[i]) == "static-state") {
                    var newStaticState = ORBEON.util.Dom.getStringValue(responseRoot.childNodes[i]);
                    ORBEON.xforms.Globals.formStaticState[formID].value = newStaticState;
                } else if (ORBEON.util.Utils.getLocalName(responseRoot.childNodes[i]) == "action") {
                    var actionElement = responseRoot.childNodes[i];

                    // First repeat and delete "lines" in repeat (as itemset changed below might be in a new line)
                    for (var actionIndex = 0; actionIndex < actionElement.childNodes.length; actionIndex++) {
                        var actionName = ORBEON.util.Utils.getLocalName(actionElement.childNodes[actionIndex]);
                        switch (actionName) {

                            case "control-values": {
                                var controlValuesElement = actionElement.childNodes[actionIndex];
                                var copyRepeatTemplateElements = ORBEON.util.Dom.getElementsByName(controlValuesElement, "copy-repeat-template", xmlNamespace);
                                var copyRepeatTemplateElementsLength = copyRepeatTemplateElements.length;
                                for (var j = 0; j < copyRepeatTemplateElementsLength; j++) {

                                    // Copy repeat template
                                    var copyRepeatTemplateElement = copyRepeatTemplateElements[j];
                                    var repeatId = ORBEON.util.Dom.getAttribute(copyRepeatTemplateElement, "id");
                                    var parentIndexes = ORBEON.util.Dom.getAttribute(copyRepeatTemplateElement, "parent-indexes");
                                    var startSuffix = Number(ORBEON.util.Dom.getAttribute(copyRepeatTemplateElement, "start-suffix"));
                                    var endSuffix = Number(ORBEON.util.Dom.getAttribute(copyRepeatTemplateElement, "end-suffix"));
                                    // Put nodes of the template in an array
                                    var templateNodes = new Array();
                                    {
                                        // Locate end of the repeat
                                        var delimiterTagName = null;
                                        var templateRepeatEnd = ORBEON.util.Dom.get("repeat-end-" + repeatId);
                                        var templateNode = templateRepeatEnd.previousSibling;
                                        var nestedRepeatLevel = 0;
                                        while (!(nestedRepeatLevel == 0 && templateNode.nodeType == ELEMENT_TYPE
                                                && YAHOO.util.Dom.hasClass(templateNode, "xforms-repeat-delimiter"))) {
                                            var nodeCopy = templateNode.cloneNode(true);
                                            if (templateNode.nodeType == ELEMENT_TYPE) {
                                                // Save tag name to be used for delimiter
                                                delimiterTagName = templateNode.tagName;
                                                // Decrement nestedRepeatLevel when we we exit a nested repeat
                                                if (YAHOO.util.Dom.hasClass(templateNode, "xforms-repeat-begin-end") && templateNode.id.indexOf("repeat-begin-") == 0)
                                                    nestedRepeatLevel--;
                                                // Increment nestedRepeatLevel when we enter a nested repeat
                                                if (YAHOO.util.Dom.hasClass(templateNode, "xforms-repeat-begin-end") && templateNode.id.indexOf("repeat-end-") == 0)
                                                    nestedRepeatLevel++;
                                                // Remove "xforms-repeat-template" from classes on copy of element
                                                var nodeCopyClasses = nodeCopy.className.split(" ");
                                                var nodeCopyNewClasses = new Array();
                                                for (var nodeCopyClassIndex = 0; nodeCopyClassIndex < nodeCopyClasses.length; nodeCopyClassIndex++) {
                                                    var currentClass = nodeCopyClasses[nodeCopyClassIndex];
                                                    if (currentClass != "xforms-repeat-template")
                                                        nodeCopyNewClasses.push(currentClass);
                                                }
                                                nodeCopy.className = nodeCopyNewClasses.join(" ");
                                            }
                                            templateNodes.push(nodeCopy);
                                            templateNode = templateNode.previousSibling;
                                        }
                                        // Add a delimiter
                                        var newDelimiter = document.createElement(delimiterTagName);
                                        newDelimiter.className = "xforms-repeat-delimiter";
                                        templateNodes.push(newDelimiter);
                                        // Reverse nodes as they were inserted in reverse order
                                        templateNodes = templateNodes.reverse();
                                    }
                                    // Find element after insertion point
                                    var afterInsertionPoint;
                                    {
                                        if (parentIndexes == "") {
                                            // Top level repeat: contains a template
                                            var repeatEnd = ORBEON.util.Dom.get("repeat-end-" + repeatId);
                                            var cursor = repeatEnd.previousSibling;
                                            while (!(cursor.nodeType == ELEMENT_TYPE
                                                    && YAHOO.util.Dom.hasClass(cursor, "xforms-repeat-delimiter")
                                                    && !YAHOO.util.Dom.hasClass(cursor, "xforms-repeat-template"))) {
                                                cursor = cursor.previousSibling;
                                            }
                                            afterInsertionPoint = cursor;
                                        } else {
                                            // Nested repeat: does not contain a template
                                            var repeatEnd = ORBEON.util.Dom.get("repeat-end-" + ORBEON.util.Utils.appendRepeatSuffix(repeatId, parentIndexes));
                                            afterInsertionPoint = repeatEnd;
                                        }
                                    }
                                    // Insert copy of template nodes
                                    for (var suffix = startSuffix; suffix <= endSuffix; suffix++) {
                                        var nestedRepeatLevel = 0;
                                        for (var templateNodeIndex = 0; templateNodeIndex < templateNodes.length; templateNodeIndex++) {
                                            var templateNode = templateNodes[templateNodeIndex];

                                            // Add suffix to all the ids
                                            var newTemplateNode;
                                            if (startSuffix == endSuffix || suffix == endSuffix) {
                                                // Just one template to copy, or we are at the end: do the work on the initial copy
                                                newTemplateNode = templateNodes[templateNodeIndex];
                                            } else {
                                                // Clone again
                                                newTemplateNode = templateNodes[templateNodeIndex].cloneNode(true);
                                            }
                                            if (newTemplateNode.nodeType == ELEMENT_TYPE) {
                                                // Decrement nestedRepeatLevel when we we exit a nested repeat
                                                if (YAHOO.util.Dom.hasClass(newTemplateNode, "xforms-repeat-begin-end") && templateNode.id.indexOf("repeat-end-") == 0)
                                                    nestedRepeatLevel--;
                                                ORBEON.util.Utils.addSuffixToIdsAndRemoveDisabled(newTemplateNode, parentIndexes == "" ? String(suffix) : parentIndexes + XFORMS_SEPARATOR_2 + suffix, nestedRepeatLevel);
                                                // Increment nestedRepeatLevel when we enter a nested repeat
                                                if (YAHOO.util.Dom.hasClass(newTemplateNode, "xforms-repeat-begin-end") && templateNode.id.indexOf("repeat-begin-") == 0)
                                                    nestedRepeatLevel++;
                                            }
                                            afterInsertionPoint.parentNode.insertBefore(newTemplateNode, afterInsertionPoint);
                                            ORBEON.xforms.Init.insertedElement(newTemplateNode);
                                        }
                                    }
                                    ORBEON.xforms.Init.registerDraggableListenersOnRepeatElements();
                                }

                                var deleteRepeatTemplateElements = ORBEON.util.Dom.getElementsByName(controlValuesElement, "delete-repeat-elements", xmlNamespace);
                                var deleteRepeatTemplateElementsLength = deleteRepeatTemplateElements.length;
                                for (var j = 0; j < deleteRepeatTemplateElementsLength; j++) {

                                    // Extract data from server response
                                    var deleteElementElement = deleteRepeatTemplateElements[j];
                                    var deleteId = ORBEON.util.Dom.getAttribute(deleteElementElement, "id");
                                    var parentIndexes = ORBEON.util.Dom.getAttribute(deleteElementElement, "parent-indexes");
                                    var count = ORBEON.util.Dom.getAttribute(deleteElementElement, "count");
                                    // Find end of the repeat
                                    var repeatEnd = ORBEON.util.Dom.get("repeat-end-" + ORBEON.util.Utils.appendRepeatSuffix(deleteId, parentIndexes));
                                    // Find last element to delete
                                    var lastElementToDelete;
                                    {
                                        lastElementToDelete = repeatEnd.previousSibling;
                                        if (parentIndexes == "") {
                                            // Top-level repeat: need to go over template
                                            while (true) {
                                                // Look for delimiter that comes just before the template
                                                if (lastElementToDelete.nodeType == ELEMENT_TYPE
                                                        && YAHOO.util.Dom.hasClass(lastElementToDelete, "xforms-repeat-delimiter")
                                                        && !YAHOO.util.Dom.hasClass(lastElementToDelete, "xforms-repeat-template"))
                                                    break;
                                                lastElementToDelete = lastElementToDelete.previousSibling;
                                            }
                                            lastElementToDelete = lastElementToDelete.previousSibling;
                                        }
                                    }
                                    // Perform delete
                                    for (var countIndex = 0; countIndex < count; countIndex++) {
                                        var nestedRepeatLevel = 0;
                                        while (true) {
                                            var wasDelimiter = false;
                                            if (lastElementToDelete.nodeType == ELEMENT_TYPE) {
                                                if (YAHOO.util.Dom.hasClass(lastElementToDelete, "xforms-repeat-begin-end") &&
                                                    lastElementToDelete.id.indexOf("repeat-end-") == 0) {
                                                    // Entering nested repeat
                                                    nestedRepeatLevel++;
                                                } else if (YAHOO.util.Dom.hasClass(lastElementToDelete, "xforms-repeat-begin-end") &&
                                                           lastElementToDelete.id.indexOf("repeat-begin-") == 0) {
                                                    // Exiting nested repeat
                                                    nestedRepeatLevel--;
                                                } else {
                                                    wasDelimiter = nestedRepeatLevel == 0 && YAHOO.util.Dom.hasClass(lastElementToDelete, "xforms-repeat-delimiter");
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
                                                if (YAHOO.util.Dom.hasClass(lastElementToDelete, "xforms-control"))
                                                    ORBEON.xforms.ServerValueStore.remove(lastElementToDelete.id);
                                            }
                                            lastElementToDelete.parentNode.removeChild(lastElementToDelete);
                                            lastElementToDelete = previous;
                                            if (wasDelimiter) break;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Second handle the <xxforms:itemset> actions (we want to do this before we set the value of
                    // controls as the value of the select might be in the new values of the itemset).
                    var controlsWithUpdatedItemsets = {};
                    for (var actionIndex = 0; actionIndex < actionElement.childNodes.length; actionIndex++) {
                        // Change values in an itemset
                        if (ORBEON.util.Utils.getLocalName(actionElement.childNodes[actionIndex]) == "control-values") {
                            var itemsetsElement = actionElement.childNodes[actionIndex];
                            for (var j = 0; j < itemsetsElement.childNodes.length; j++) {
                                if (ORBEON.util.Utils.getLocalName(itemsetsElement.childNodes[j]) == "itemset") {
                                    var itemsetElement = itemsetsElement.childNodes[j];
                                    var itemsetTree = ORBEON.util.String.eval(ORBEON.util.Dom.getStringValue(itemsetElement));
                                    if (itemsetTree == null) itemsetTree = [];
                                    var controlId = ORBEON.util.Dom.getAttribute(itemsetElement, "id");
                                    var documentElement = ORBEON.util.Dom.get(controlId);
                                    var documentElementClasses = documentElement.className.split(" ");
                                    controlsWithUpdatedItemsets[controlId] = true;

                                    if (YAHOO.util.Dom.hasClass(documentElement, "xforms-select-appearance-xxforms-tree")
                                            || YAHOO.util.Dom.hasClass(documentElement, "xforms-select1-appearance-xxforms-tree")) {
                                        ORBEON.xforms.Page.getControl(documentElement).setItemset(itemsetTree);
                                    } else if (YAHOO.util.Dom.hasClass(documentElement, "xforms-select-appearance-xxforms-menu")
                                            || YAHOO.util.Dom.hasClass(documentElement, "xforms-select1-appearance-xxforms-menu")) {
                                        // NOP: We don't do anything for menus, as an update of the menu after the page is loaded isn't supported at this point
                                    } else if (YAHOO.util.Dom.hasClass(documentElement, "xforms-select1-appearance-compact")
                                            || YAHOO.util.Dom.hasClass(documentElement, "xforms-select-appearance-compact")
                                            || YAHOO.util.Dom.hasClass(documentElement, "xforms-select1-appearance-minimal")) {

                                        // Case of list / combobox
                                        var select = ORBEON.util.Utils.isNewXHTMLLayout()
                                            ? documentElement.getElementsByTagName("select")[0]
                                            : documentElement;
                                        var options = select.options;

                                        // Remember selected values
                                        var selectedValueCount = 0;
                                        var selectedValues = new Array();
                                        for (var k = 0; k < options.length; k++) {
                                            if (options[k].selected) {
                                                selectedValues[selectedValueCount] = options[k].value;
                                                selectedValueCount++;
                                            }
                                        }

                                        // Utility function to generate an option
                                        function generateOption(label, value, clazz, selectedValues) {
                                            var selected = xformsArrayContains(selectedValues, value);
                                            return '<option value="' + ORBEON.util.String.escapeAttribute(value) + '"'
                                                    + (selected ? ' selected="selected"' : '')
                                                    + (clazz != null ? ' class="' + ORBEON.util.String.escapeAttribute(clazz) + '"' : '')
                                                    + '>' + label + '</option>';
                                        }

                                        // Build new content for the select element
                                        var sb = new Array();
                                        for (var topIndex = 0; topIndex < itemsetTree.length; topIndex++) {
                                            var itemElement = itemsetTree[topIndex];
                                            var clazz = null;
                                            if (! YAHOO.lang.isUndefined(itemElement.attributes) && ! YAHOO.lang.isUndefined(itemElement.attributes["class"])) {
                                                // We have a class property
                                                clazz = itemElement.attributes["class"];
                                            }
                                            if (! YAHOO.lang.isUndefined(itemElement.children)) {
                                                // This is an item that contains other elements
                                                sb[sb.length] = '<optgroup label="' + ORBEON.util.String.escapeAttribute(itemElement.label) + '"'
                                                    + (clazz != null ? ' class="' + ORBEON.util.String.escapeAttribute(clazz) + '"' : '')
                                                    + '">';
                                                // Go through options in this optgroup
                                                for (var childItemIndex = 0; childItemIndex < itemElement.children.length; childItemIndex++) {
                                                    var itemElementOption = itemElement.children[childItemIndex];
                                                    var subItemClazz = ! YAHOO.lang.isUndefined(itemElementOption.attributes) && ! YAHOO.lang.isUndefined(itemElementOption.attributes["class"])
                                                        ? itemElementOption.attributes["class"] : null;
                                                    sb[sb.length] = generateOption(itemElementOption.label, itemElementOption.value, subItemClazz, selectedValues);
                                                }
                                                sb[sb.length] = '</optgroup>';
                                            } else {
                                                // This item is directly an option
                                                sb[sb.length] = generateOption(itemElement.label, itemElement.value, clazz, selectedValues);
                                            }
                                        }

                                        // Set content of select element
                                        if (ORBEON.xforms.Globals.isRenderingEngineTrident) {
                                            // IE does not support setting the content of a select with innerHTML
                                            // So we have to generate the whole select, and use outerHTML
                                            YAHOO.util.Event.removeListener(select, "change");
                                            var selectOpeningTag = select.outerHTML.substring(0, select.outerHTML.indexOf(">") + 1);
                                            select.outerHTML = selectOpeningTag + sb.join("") + "</select>";
                                            // Get again control, as it has been re-created
                                            select = ORBEON.util.Dom.get(controlId);
                                            if (ORBEON.util.Utils.isNewXHTMLLayout())
                                                select = documentElement.getElementsByTagName("select")[0];
                                        } else {
                                            // Version for compliant browsers
                                            select.innerHTML = sb.join("");
                                        }

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
                                        var template = YAHOO.util.Dom.hasClass(documentElement, "xforms-select")
                                                ? ORBEON.util.Dom.get("xforms-select-full-template")
                                                : ORBEON.util.Dom.get("xforms-select1-full-template");
                                        template = ORBEON.util.Dom.getChildElementByIndex(template, 0);

                                        // Get the span that contains the one span per checkbox/radio
                                        // This is the first span that has no class on it (we don't want to get a span for label, hint, help, alert)
                                        var spanContainer = ORBEON.util.Utils.isNewXHTMLLayout()
                                            ? _.detect(documentElement.getElementsByTagName("span"), function(span) { return span.className == ""; })
                                            : documentElement;

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
                                        var itemIndex = 0;
                                        for (var k = 0; k < itemsetTree.length; k++) {
                                            var itemElement = itemsetTree[k];

                                            var templateClone = template.cloneNode(true);
                                            spanContainer.appendChild(templateClone);
                                            templateClone.innerHTML = new String(templateClone.innerHTML).replace(new RegExp("\\$xforms-template-label\\$", "g"), itemElement.label.replace(new RegExp("\\$", "g"), "$$$$"));
                                            ORBEON.util.Utils.stringReplace(templateClone, "$xforms-template-value$", itemElement.value);
                                            var itemEffectiveId = ORBEON.util.Utils.appendToEffectiveId(controlId, "$$e" + itemIndex);
                                            ORBEON.util.Utils.stringReplace(templateClone, "$xforms-item-effective-id$", itemEffectiveId);
                                            ORBEON.util.Utils.stringReplace(templateClone, "$xforms-effective-id$", controlId);
                                            if (! YAHOO.lang.isUndefined(itemElement.attributes) && ! YAHOO.lang.isUndefined(itemElement.attributes["class"])) {
                                                templateClone.className += " " + itemElement.attributes["class"];
                                            }

                                            // Restore checked state after copy
                                            var inputCheckboxOrRadio = templateClone.getElementsByTagName("input")[0];
                                            if (valueToChecked[itemElement.value] == true) {
                                                inputCheckboxOrRadio.checked = true;
                                            }

                                            // Remove the disabled attribute from the template, which is there so tab would skip over form elements in template
                                            inputCheckboxOrRadio.removeAttribute("disabled");

                                            itemIndex++;
                                        }
                                    }

                                    // Call custom listener if any (temporary until we have a good API for custom components)
                                    if (typeof xformsItemsetUpdatedListener != "undefined") {
                                        xformsItemsetUpdatedListener(controlId, itemsetTree);
                                    }
                                }
                            }
                        }
                    }

                    // Handle other actions
                    var serverEventsIndex = -1;
                    for (var actionIndex = 0; actionIndex < actionElement.childNodes.length; actionIndex++) {

                        var actionName = ORBEON.util.Utils.getLocalName(actionElement.childNodes[actionIndex]);
                        switch (actionName) {

                            // Update controls
                            case "control-values": {
                                var controlValuesElement = actionElement.childNodes[actionIndex];
                                var controlElements = ORBEON.util.Dom.getElementsByName(controlValuesElement, "control", xmlNamespace);
                                var controlElementsLength = controlElements.length;
                                // Update control value and MIPs
                                for (var j = 0; j < controlElementsLength; j++) {
                                    var controlElement = controlElements[j];
                                    var newControlValue = ORBEON.util.Dom.getStringValue(controlElement);
                                    var controlId = ORBEON.util.Dom.getAttribute(controlElement, "id");
                                    var staticReadonly = ORBEON.util.Dom.getAttribute(controlElement, "static");
                                    var relevant = ORBEON.util.Dom.getAttribute(controlElement, "relevant");
                                    var readonly = ORBEON.util.Dom.getAttribute(controlElement, "readonly");
                                    var required = ORBEON.util.Dom.getAttribute(controlElement, "required");
                                    var classes = ORBEON.util.Dom.getAttribute(controlElement, "class");
                                    var progressState = ORBEON.util.Dom.getAttribute(controlElement, "progress-state");
                                    var progressReceived = ORBEON.util.Dom.getAttribute(controlElement, "progress-received");
                                    var progressExpected = ORBEON.util.Dom.getAttribute(controlElement, "progress-expected");

                                    var newSchemaType = ORBEON.util.Dom.getAttribute(controlElement, "type");
                                    var documentElement = ORBEON.util.Dom.get(controlId);
                                    if (documentElement == null) {
                                        documentElement = ORBEON.util.Dom.get("group-begin-" + controlId);
                                        if (documentElement == null) ORBEON.util.Utils.logMessage ("Can't find element or iteration with ID '" + controlId + "'");
                                    }
                                    var documentElementClasses = documentElement.className.split(" ");
                                    var isLeafControl = YAHOO.util.Dom.hasClass(documentElement, "xforms-control");

                                    // Save new value sent by server (upload controls don't carry their value the same way as other controls)
                                    var previousServerValue = ORBEON.xforms.ServerValueStore.get(controlId);
                                    if (! YAHOO.util.Dom.hasClass(documentElement, "xforms-upload"))
                                        ORBEON.xforms.ServerValueStore.set(controlId, newControlValue);

                                    // Handle migration of control from non-static to static if needed
                                    var isStaticReadonly = YAHOO.util.Dom.hasClass(documentElement, "xforms-static");
                                    if (!isStaticReadonly && staticReadonly == "true") {
                                        if (isLeafControl) {
                                            // Replace existing element with span
                                            var parentElement = documentElement.parentNode;
                                            var newDocumentElement = document.createElement("span");
                                            newDocumentElement.setAttribute("id", controlId);
                                            newDocumentElement.className = documentElementClasses.join(" ") + " xforms-static";
                                            parentElement.replaceChild(newDocumentElement, documentElement);

                                            // Remove alert
                                            var alertElement = ORBEON.xforms.Controls._getControlLHHA(newDocumentElement, "alert");
                                            if (alertElement != null)
                                                parentElement.removeChild(alertElement);
                                            // Remove hint
                                            var hintElement = ORBEON.xforms.Controls._getControlLHHA(newDocumentElement, "hint");
                                            if (hintElement != null)
                                                parentElement.removeChild(hintElement);
                                                // Update document element information
                                            documentElement = newDocumentElement;
                                        } else {
                                            // Just add the new class
                                            YAHOO.util.Dom.addClass(documentElement, "xforms-static");
                                        }
                                        isStaticReadonly = true;
                                        documentElementClasses = documentElement.className.split(" ");
                                    }

                                    // We update the relevance and readonly before we update the value. If we don't, updating the value
                                    // can fail on IE in some cases. (The details about this issue have been lost.)

                                    // Handle relevance
                                    if (relevant != null) {
                                        var isRelevant = relevant == "true";
                                        ORBEON.xforms.Controls.setRelevant(documentElement, isRelevant);
                                         // Autosize textarea
                                        if (YAHOO.util.Dom.hasClass(documentElement, "xforms-textarea-appearance-xxforms-autosize")) {
                                            ORBEON.xforms.Controls.autosizeTextarea(documentElement);
                                        }
                                    }

                                    // Handle required
                                    if (required != null) {
                                        var isRequired = required == "true";
                                        if (isRequired) YAHOO.util.Dom.addClass(documentElement, "xforms-required");
                                        else YAHOO.util.Dom.removeClass(documentElement, "xforms-required");
                                    }

                                    // Update input control type
                                    // NOTE: This is not ideal: in the future, we would like a template-based mechanism instead.
                                    var recreatedInput = false;
                                    if (newSchemaType != null && YAHOO.util.Dom.hasClass(documentElement, "xforms-input")) {

                                        // For each supported type, declares the recognized schema types and the class used in the DOM
                                        var INPUT_TYPES = [
                                            { type: "date",     schemaTypes: [ "{http://www.w3.org/2001/XMLSchema}date", "{http://www.w3.org/2002/xforms}date" ], className: "xforms-type-date" },
                                            { type: "time",     schemaTypes: [ "{http://www.w3.org/2001/XMLSchema}time", "{http://www.w3.org/2002/xforms}time" ], className: "xforms-type-time" },
                                            { type: "dateTime", schemaTypes: [ "{http://www.w3.org/2001/XMLSchema}dateTime", "{http://www.w3.org/2002/xforms}dateTime" ], className: "xforms-type-dateTime" },
                                            { type: "boolean",  schemaTypes: [ "{http://www.w3.org/2001/XMLSchema}boolean", "{http://www.w3.org/2002/xforms}boolean" ], className: "xforms-type-boolean" },
                                            { type: "string",   schemaTypes: null, className: null }
                                        ];

                                        /** @type {Object} */ var existingType = _.detect(INPUT_TYPES, function(type) { return type.className == null || YAHOO.util.Dom.hasClass(documentElement, type.className); });
                                        /** @type {Object} */ var newType = _.detect(INPUT_TYPES, function(type) { return type.schemaTypes == null || _.include(type.schemaTypes, newSchemaType); });
                                        if (newType != existingType) {

                                            // Remember that this input has be recreated which means we need to update its value
                                            recreatedInput = true;
                                            // Clean-up document element by removing type classes
                                            _.each(INPUT_TYPES, function(type) { YAHOO.util.Dom.removeClass(documentElement, type.className); });
                                            YAHOO.util.Dom.removeClass(documentElement, "xforms-incremental");
                                            // Minimal control content can be different
                                            var isMinimal = YAHOO.util.Dom.hasClass(documentElement, "xforms-input-appearance-minimal");

                                            // Find the position of the last label before the control "actual content"
                                            // and remove all elements that are not labels
                                            var lastLabelPosition = -1;
                                            var childElements = YAHOO.util.Dom.getChildren(documentElement);
                                            for (var childIndex = 0; childIndex < childElements.length; childIndex++) {
                                                var childElement = childElements[childIndex];
                                                if (! YAHOO.util.Dom.hasClass(childElement, "xforms-label")
                                                        && ! YAHOO.util.Dom.hasClass(childElement, "xforms-help")
                                                        && ! YAHOO.util.Dom.hasClass(childElement, "xforms-hint")
                                                        && ! YAHOO.util.Dom.hasClass(childElement, "xforms-alert")
                                                        && ! YAHOO.util.Dom.hasClass(childElement, "xforms-help-image")) {
                                                    documentElement.removeChild(childElement);
                                                    if (lastLabelPosition == -1)
                                                        lastLabelPosition = childIndex - 1;
                                                }
                                            }

                                            function insertIntoDocument(nodes) {
                                                if (ORBEON.util.Utils.isNewXHTMLLayout()) {
                                                    // New markup: insert after "last label" (we remembered the position of the label after which there is real content)
                                                    if (YAHOO.util.Dom.getChildren(documentElement).length == 0) {
                                                        for (var nodeIndex = 0; nodeIndex < nodes.length; nodeIndex++)
                                                            documentElement.appendChild(nodes[nodeIndex]);
                                                    } else if (lastLabelPosition == -1) {
                                                        var firstChild = YAHOO.util.Dom.getFirstChild(documentElement);
                                                        for (var nodeIndex = nodes.length - 1; nodeIndex >= 0; nodeIndex--)
                                                            YAHOO.util.Dom.insertBefore(nodes[nodeIndex], firstChild);
                                                    } else {
                                                        for (var nodeIndex = 0; nodeIndex < nodes.length; nodeIndex++)
                                                            YAHOO.util.Dom.insertAfter(nodes[nodeIndex], childElements[lastLabelPosition]);
                                                    }
                                                } else {
                                                    // Old markup: insert in container, which will be empty
                                                    for (var nodeIndex = 0; nodeIndex < nodes.length; nodeIndex++)
                                                        documentElement.appendChild(nodes[nodeIndex]);
                                                }
                                            }

                                            function createInput(typeClassName, inputIndex) {
                                                var newInputElement = document.createElement("input");
                                                newInputElement.setAttribute("type", "text");
                                                newInputElement.className = "xforms-input-input " + typeClassName;
                                                newInputElement.id = ORBEON.util.Utils.appendToEffectiveId(controlId, "$xforms-input-" + inputIndex);
                                                // In portlet mode, name is not prefixed
                                                if (newInputElement.id.indexOf(ORBEON.xforms.Globals.ns[formID]) == 0)
                                                    newInputElement.name = newInputElement.id.substring(ORBEON.xforms.Globals.ns[formID].length);
                                                else
                                                    newInputElement.name = newInputElement.id; // should not happen as ns should be valid or ""
                                                return newInputElement;
                                            }

                                            var inputLabelElement = ORBEON.xforms.Controls._getControlLHHA(documentElement, "label");
                                            if (newType.type == "string") {
                                                var newStringInput = createInput("xforms-type-string", 1);
                                                insertIntoDocument([newStringInput]);
                                                YAHOO.util.Dom.addClass(documentElement, "xforms-type-string");
                                                if (inputLabelElement != null) inputLabelElement.htmlFor = newStringInput.id;
                                            } else if (newType.type == "date" && !isMinimal) {
                                                var newDateInput = createInput("xforms-type-date", 1);
                                                insertIntoDocument([newDateInput]);
                                                YAHOO.util.Dom.addClass(documentElement, "xforms-type-date");
                                                if (inputLabelElement != null) inputLabelElement.htmlFor = newDateInput.id;
                                            } else if (newType.type == "date" && isMinimal) {
                                                // Create image element
                                                var image = document.createElement("img");
                                                image.setAttribute("src", ORBEON.xforms.Globals.resourcesBaseURL[formID] + "/ops/images/xforms/calendar.png");
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
                                                var booleanTemplate = ORBEON.util.Dom.get("xforms-select-full-template");
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
                                                ORBEON.util.Utils.stringReplace(booleanTemplateClone, "$xforms-template-value$", "true");
                                                var booleanEffectiveId = ORBEON.util.Utils.appendToEffectiveId(controlId, "$$e0");
                                                ORBEON.util.Utils.stringReplace(booleanTemplateClone, "$xforms-item-effective-id$", booleanEffectiveId);
                                                ORBEON.util.Utils.stringReplace(booleanTemplateClone, "$xforms-effective-id$", controlId);

                                                // Update classes
                                                YAHOO.util.Dom.addClass(documentElement, "xforms-type-boolean");
                                                YAHOO.util.Dom.addClass(documentElement, "xforms-input-appearance-minimal");
                                                YAHOO.util.Dom.addClass(documentElement, "xforms-incremental");

                                                if (inputLabelElement != null) inputLabelElement.htmlFor = booleanEffectiveId;
                                            }
                                        }
                                    } else if (newSchemaType != null) {
                                        // Type has changed for any control but xforms:input

                                        var typePrefix = "xforms-type-";

                                        // Remove existing type classes
                                        var classesArray = documentElement.className.split(" ");
                                        for (var classIndex = 0; classIndex < classesArray.length; classIndex++) {
                                            var currentClass = classesArray[classIndex];
                                            if (currentClass.indexOf(typePrefix) == 0) {
                                                YAHOO.util.Dom.removeClass(documentElement, currentClass);
                                            }
                                        }

                                        // Add new class
                                        var typeResult = /}(.*)/.exec(newSchemaType);
                                        if (typeResult != null && typeResult.length >= 2)
                                            YAHOO.util.Dom.addClass(documentElement, typePrefix + typeResult[1]);
                                    }

                                    // Handle readonly
                                    if (readonly != null && !isStaticReadonly)
                                        ORBEON.xforms.Controls.setReadonly(documentElement, readonly == "true");

                                    // Handle updates to custom classes
                                    if (classes != null) {
                                        var classesArray = classes.split(" ");
                                        for (var classIndex = 0; classIndex < classesArray.length; classIndex++) {
                                            var currentClass = classesArray[classIndex];
                                            if (currentClass.charAt(0) == '-') {
                                                YAHOO.util.Dom.removeClass(documentElement, currentClass.substring(1));
                                            } else {
                                                // '+' is optional
                                                YAHOO.util.Dom.addClass(documentElement, currentClass.charAt(0) == '+' ? currentClass.substring(1) : currentClass);
                                            }
                                        }
                                    }

                                    // Update value
                                    if (isLeafControl) {
                                        if (YAHOO.util.Dom.hasClass(documentElement, "xforms-upload")) {
                                            // Additional attributes for xforms:upload
                                            // <xxforms:control id="xforms-control-id"
                                            //    state="empty|file"
                                            //    filename="filename.txt" mediatype="text/plain" size="23kb"/>
                                            var state = ORBEON.util.Dom.getAttribute(controlElement, "state");
                                            var filename = ORBEON.util.Dom.getAttribute(controlElement, "filename");
                                            var mediatype = ORBEON.util.Dom.getAttribute(controlElement, "mediatype");
                                            var size = ORBEON.util.Dom.getAttribute(controlElement, "size");
                                            ORBEON.xforms.Controls.setCurrentValue(documentElement, newControlValue, state, filename, mediatype, size);
                                        } else if (YAHOO.util.Dom.hasClass(documentElement, "xforms-output")
                                                    || YAHOO.util.Dom.hasClass(documentElement, "xforms-static")) {
                                            // Output-only control, just set the value
                                            ORBEON.xforms.Controls.setCurrentValue(documentElement, newControlValue);
                                        } else if (YAHOO.util.Dom.hasClass(documentElement, "xforms-trigger")
                                                    || YAHOO.util.Dom.hasClass(documentElement, "xforms-submit")) {
                                            // It isn't a control that can hold a value (e.g. trigger) and there is no point in trying to update it
                                            // NOP
                                        } else {
                                            var currentValue = ORBEON.xforms.Controls.getCurrentValue(documentElement);
                                            if (currentValue != null) {
                                                previousServerValue = ORBEON.util.String.normalizeSerializedHTML(previousServerValue);
                                                currentValue = ORBEON.util.String.normalizeSerializedHTML(currentValue);
                                                newControlValue = ORBEON.util.String.normalizeSerializedHTML(newControlValue);

                                                var isInput = YAHOO.util.Dom.hasClass(documentElement, "xforms-input");
                                                var inputSize = isInput ? ORBEON.util.Dom.getAttribute(controlElement, "size") : null;
                                                var inputLength = isInput ? ORBEON.util.Dom.getAttribute(controlElement, "maxlength") : null;
                                                var inputAutocomplete = isInput ? ORBEON.util.Dom.getAttribute(controlElement, "autocomplete") : null;

                                                var isTextarea = YAHOO.util.Dom.hasClass(documentElement, "xforms-textarea");
                                                var textareaMaxlength = isTextarea ? ORBEON.util.Dom.getAttribute(controlElement, "maxlength") : null;
                                                var textareaCols = isTextarea ? ORBEON.util.Dom.getAttribute(controlElement, "cols") : null;
                                                var textareaRows = isTextarea ? ORBEON.util.Dom.getAttribute(controlElement, "rows") : null;

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
                                                            && (previousServerValue == null || currentValue == previousServerValue)
                                                        ) ||
                                                        // Special xforms:input attributes
                                                        (isInput && (inputSize != null || inputLength != null || inputAutocomplete != null)) ||
                                                        // Special xforms:textarea attributes
                                                        (isTextarea && (textareaMaxlength != null || textareaCols != null || textareaRows != null));
                                                if (doUpdate) {
                                                    if (isInput) {
                                                        // Additional attributes for xforms:input
                                                        ORBEON.xforms.Controls.setCurrentValue(documentElement, newControlValue, inputSize, inputLength, inputAutocomplete);
                                                    } else if (isTextarea && YAHOO.util.Dom.hasClass(documentElement, "xforms-mediatype-text-html")) {
                                                        ORBEON.xforms.Controls.setCurrentValue(documentElement, newControlValue);
                                                    } else if (isTextarea) {
                                                        // Additional attributes for xforms:textarea
                                                        ORBEON.xforms.Controls.setCurrentValue(documentElement, newControlValue, textareaMaxlength, textareaCols, textareaRows);
                                                    } else {
                                                        // Other control just have a new value
                                                        ORBEON.xforms.Controls.setCurrentValue(documentElement, newControlValue);
                                                    }
                                                    // Store the server value as the client sees it, not as the server sees it. There can be a different in the following cases:
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
                                                    ORBEON.xforms.ServerValueStore.set(documentElement.id, ORBEON.xforms.Controls.getCurrentValue(documentElement));
                                                }
                                            }
                                        }

                                        // Call custom listener if any (temporary until we have a good API for custom components)
                                        if (typeof xformsValueChangedListener != "undefined") {
                                            xformsValueChangedListener(controlId, newControlValue);
                                        }

                                        // Mark field field as visited when its value changes, unless the new value is given to us when the field becomes relevant
                                        // This is a heuristic that works when a section is shown for the first time, but won't work in many cases. This will be changed
                                        // by handling this on the server-side with custom MIPS.
                                        if (YAHOO.util.Dom.hasClass(documentElement, "xforms-output") && relevant == null) {
                                            YAHOO.util.Dom.addClass(documentElement, "xforms-visited");
                                            if (YAHOO.util.Dom.hasClass(documentElement, "xforms-invalid"))
                                                YAHOO.util.Dom.addClass(documentElement, "xforms-invalid-visited");
                                        }
                                    }

                                    // Update the required-empty/required-full even if the required has not changed or
                                    // is not specified as the value may have changed
                                    var isRequiredEmpty;
                                    if (!isStaticReadonly && !YAHOO.util.Dom.hasClass(documentElement, "xforms-group")) {
                                        // We don't get the value for groups, so we are not calling this method as it would otherwise
                                        // incorrectly add the class xforms-required-empty on groups.
                                        isRequiredEmpty = ORBEON.xforms.Controls.updateRequiredEmpty(documentElement, newControlValue);
                                    } else {
                                        isRequiredEmpty = false;
                                    }

                                    // Store new label message in control attribute
                                    var newLabel = ORBEON.util.Dom.getAttribute(controlElement, "label");
                                    if (newLabel != null)
                                        ORBEON.xforms.Controls.setLabelMessage(documentElement, newLabel);
                                    // Store new hint message in control attribute
                                    var newHint = ORBEON.util.Dom.getAttribute(controlElement, "hint");
                                    if (newHint != null)
                                        ORBEON.xforms.Controls.setHintMessage(documentElement, newHint);
                                    // Store new help message in control attribute
                                    var newHelp = ORBEON.util.Dom.getAttribute(controlElement, "help");
                                    if (newHelp != null)
                                        ORBEON.xforms.Controls.setHelpMessage(documentElement, newHelp);
                                    // Store new alert message in control attribute
                                    var newAlert = ORBEON.util.Dom.getAttribute(controlElement, "alert");
                                    if (newAlert != null)
                                        ORBEON.xforms.Controls.setAlertMessage(documentElement, newAlert);
                                    // Store validity, label, hint, help in element
                                    var newValid = ORBEON.util.Dom.getAttribute(controlElement, "valid");
                                    if (newValid != null)
                                        ORBEON.xforms.Controls.setValid(documentElement, newValid);

                                    // Handle progress for upload controls
                                    if (progressState != null && progressReceived != null && progressExpected != null
                                            && progressState != "" && progressReceived != "" && progressExpected != "")
                                        ORBEON.xforms.Page.getControl(documentElement).progress(progressState, parseInt(progressReceived), parseInt(progressExpected));
                                }

                                // Handle innerHTML updates
                                var innerElements = ORBEON.util.Dom.getElementsByName(controlValuesElement, "inner-html", xmlNamespace);
                                var innerElementsLength = innerElements.length;
                                for (var j = 0; j < innerElementsLength; j++) {
                                    var innerElement = innerElements[j];
                                    var innerHTML = ORBEON.util.Dom.getStringValue(innerElement);
                                    var controlId = ORBEON.util.Dom.getAttribute(innerElement, "id");
                                    var documentElement = document.getElementById(controlId);
                                    if (documentElement != null) {
                                        // Found container
                                        documentElement.innerHTML = innerHTML;
                                        ORBEON.xforms.FullUpdate.onFullUpdateDone(controlId);
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
                                    // If the element that had the focus is not in the document anymore, it might have been replaced by
                                    // setting the innerHTML, so set focus it again
                                    if (! YAHOO.util.Dom.inDocument(ORBEON.xforms.Globals.currentFocusControlElement, document)) {
                                        var focusControl = document.getElementById(ORBEON.xforms.Globals.currentFocusControlId);
                                        if (focusControl != null) ORBEON.xforms.Controls.setFocus(ORBEON.xforms.Globals.currentFocusControlId);
                                    }
                                }

                                // Handle updates to HTML attributes
                                var attributeElements = ORBEON.util.Dom.getElementsByName(controlValuesElement, "attribute", xmlNamespace);
                                var attributeElementslength = attributeElements.length;
                                for (var j = 0; j < attributeElementslength; j++) {
                                    var attributeElement = attributeElements[j];
                                    var newAttributeValue = ORBEON.util.Dom.getStringValue(attributeElement);
                                    var forAttribute = ORBEON.util.Dom.getAttribute(attributeElement, "for");
                                    var nameAttribute = ORBEON.util.Dom.getAttribute(attributeElement, "name");
                                    var htmlElement = ORBEON.util.Dom.get(forAttribute);
                                    if (htmlElement != null) {// use case: xhtml:html/@lang but HTML fragment produced
                                        ORBEON.util.Dom.setAttribute(htmlElement, nameAttribute, newAttributeValue);
                                    }
                                }

                                // Handle text updates
                                var textElements = ORBEON.util.Dom.getElementsByName(controlValuesElement, "text", xmlNamespace);
                                var textElementslength = textElements.length;
                                for (var j = 0; j < textElementslength; j++) {
                                    var textElement = textElements[j];
                                    var newTextValue = ORBEON.util.Dom.getStringValue(textElement);
                                    var forAttribute = ORBEON.util.Dom.getAttribute(textElement, "for");
                                    var htmlElement = ORBEON.util.Dom.get(forAttribute);

                                    if (htmlElement != null && htmlElement.tagName.toLowerCase() == "title") {
                                        // Set HTML title
                                        document.title = newTextValue;
                                    }
                                }

                                // Model item properties on a repeat item
                                var repeatIterationElements = ORBEON.util.Dom.getElementsByName(controlValuesElement, "repeat-iteration", xmlNamespace);
                                var repeatIterationElementslength = repeatIterationElements.length;
                                for (var j = 0; j < repeatIterationElementslength; j++) {
                                    var repeatIterationElement = repeatIterationElements[j];
                                    // Extract data from server response
                                    var repeatId = ORBEON.util.Dom.getAttribute(repeatIterationElement, "id");
                                    var iteration = ORBEON.util.Dom.getAttribute(repeatIterationElement, "iteration");
                                    var relevant = ORBEON.util.Dom.getAttribute(repeatIterationElement, "relevant");
                                    // Remove or add xforms-disabled on elements after this delimiter
                                    if (relevant != null)
                                        ORBEON.xforms.Controls.setRepeatIterationRelevance(repeatId, iteration, relevant == "true" ? true : false);
                                }

                                // "div" elements for xforms:switch and xxforms:dialog
                                var divsElements = ORBEON.util.Dom.getElementsByName(controlValuesElement, "div", xmlNamespace);
                                var divElementsLength = divsElements.length;
                                for (var j = 0; j < divElementsLength; j++) {
                                    var divElement = divsElements[j];

                                    var controlVisibilityChangeId = ORBEON.util.Dom.getAttribute(divElement, "id");
                                    var visible = ORBEON.util.Dom.getAttribute(divElement, "visibility") == "visible";
                                    var neighbor = ORBEON.util.Dom.getAttribute(divElement, "neighbor");

                                    var yuiDialog = ORBEON.xforms.Globals.dialogs[controlVisibilityChangeId];
                                    if (yuiDialog == null) {
                                        // This is a case
                                        var caseBeginId = "xforms-case-begin-" + controlVisibilityChangeId;
                                        var caseBegin = ORBEON.util.Dom.get(caseBeginId);
                                        var caseBeginParent = caseBegin.parentNode;
                                        var foundCaseBegin = false;
                                        for (var childId = 0; caseBeginParent.childNodes.length; childId++) {
                                            var cursor = caseBeginParent.childNodes[childId];
                                            if (!foundCaseBegin) {
                                                if (cursor.id == caseBegin.id) foundCaseBegin = true;
                                                else continue;
                                            }
                                            if (cursor.nodeType == ELEMENT_TYPE) {
                                                // Change visibility by switching class
                                                if (cursor.id == "xforms-case-end-" + controlVisibilityChangeId) break;
                                                var doAnimate = cursor.id != "xforms-case-begin-" + controlVisibilityChangeId && // don't animate case-begin/end
                                                        YAHOO.util.Dom.hasClass(cursor, "xxforms-animate") // only animate if class present
                                                        && !(YAHOO.env.ua.ie != 0 && YAHOO.env.ua.ie <= 7); // simply disable animation for IE 6/7 as they behave badly
                                                if (doAnimate) {
                                                    if (visible) {
                                                        // Figure out what its natural height is
                                                        cursor.style.height = "auto";
                                                        var region = YAHOO.util.Dom.getRegion(cursor);
                                                        var fullHeight = region.bottom - region.top;

                                                        // Set height back to 0 and animate back to natural height
                                                        cursor.style.height = 0;

                                                        YAHOO.util.Dom.addClass(cursor, "xforms-case-selected");
                                                        YAHOO.util.Dom.removeClass(cursor, "xforms-case-deselected");
                                                        YAHOO.util.Dom.removeClass(cursor, "xforms-case-deselected-subsequent");

                                                        var anim = new YAHOO.util.Anim(cursor, { height: {  to: fullHeight } }, .2);
                                                        anim.onComplete.subscribe(_.bind(function(cursor) {
                                                            // Set back the height to auto when the animation is finished
                                                            // This is also needed because the natural height might have changed during animation
                                                            cursor.style.height = "auto";
                                                        }, this, cursor));
                                                        anim.animate();
                                                    } else {
                                                        var anim = new YAHOO.util.Anim(cursor, { height: { to: 0 } }, 0.2);
                                                        anim.onComplete.subscribe(_.bind(function(cursor) {
                                                            // Only close case once the animation terminates
                                                            YAHOO.util.Dom.addClass(cursor, "xforms-case-deselected-subsequent");
                                                            YAHOO.util.Dom.removeClass(cursor, "xforms-case-selected");
                                                        }, this, cursor));
                                                        anim.animate();
                                                    }
                                                } else {
                                                    if (visible) {
                                                        YAHOO.util.Dom.addClass(cursor, "xforms-case-selected");
                                                        YAHOO.util.Dom.removeClass(cursor, "xforms-case-deselected");
                                                        YAHOO.util.Dom.removeClass(cursor, "xforms-case-deselected-subsequent");
                                                        ORBEON.util.Dom.nudgeAfterDelay(cursor);
                                                    } else {
                                                        YAHOO.util.Dom.addClass(cursor, "xforms-case-deselected-subsequent");
                                                        YAHOO.util.Dom.removeClass(cursor, "xforms-case-selected");
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // This is a dialog
                                        if (visible) {
                                            ORBEON.xforms.Controls.showDialog(controlVisibilityChangeId, neighbor);
                                        } else {
                                            ORBEON.xforms.Globals.maskDialogCloseEvents = true;
                                            yuiDialog.hide();
                                            ORBEON.xforms.Globals.maskDialogCloseEvents = false;
                                            // Fixes cursor Firefox issue; more on this in dialog init code
                                            yuiDialog.element.style.display = "none";
                                        }
                                    }
                                }

                                break;
                            }

                            // Change highlighted section in repeat
                            case "repeat-indexes": {
                                var repeatIndexesElement = actionElement.childNodes[actionIndex];
                                var newRepeatIndexes = new Array();
                                // Extract data from server response
                                for (var j = 0; j < repeatIndexesElement.childNodes.length; j++) {
                                    if (ORBEON.util.Utils.getLocalName(repeatIndexesElement.childNodes[j]) == "repeat-index") {
                                        var repeatIndexElement = repeatIndexesElement.childNodes[j];
                                        var repeatId = ORBEON.util.Dom.getAttribute(repeatIndexElement, "id");
                                        var newIndex = ORBEON.util.Dom.getAttribute(repeatIndexElement, "new-index");
                                        newRepeatIndexes[repeatId] = newIndex;
                                    }
                                }
                                // For each repeat id that changes, see if all the children are also included in
                                // newRepeatIndexes. If they are not, add an entry with the index unchanged.
                                for (var repeatId in newRepeatIndexes) {
                                    if (typeof repeatId == "string") { // hack because repeatId may be trash when some libraries override Object
                                        var children = ORBEON.xforms.Globals.repeatTreeParentToAllChildren[repeatId];
                                        if (children != null) { // test on null is a hack because repeatId may be trash when some libraries override Object
                                            for (var childIndex in children) {
                                                var child = children[childIndex];
                                                if (!newRepeatIndexes[child])
                                                    newRepeatIndexes[child] = ORBEON.xforms.Globals.repeatIndexes[child];
                                            }
                                        }
                                    }
                                }
                                // Unhighlight items at old indexes
                                for (var repeatId in newRepeatIndexes) {
                                    if (typeof repeatId == "string") { // hack because repeatId may be trash when some libraries override Object
                                        var oldIndex = ORBEON.xforms.Globals.repeatIndexes[repeatId];
                                        if (typeof oldIndex == "string" && oldIndex != 0) { // hack because repeatId may be trash when some libraries override Object
                                            var oldItemDelimiter = ORBEON.util.Utils.findRepeatDelimiter(repeatId, oldIndex);
                                            if (oldItemDelimiter != null) {
                                                var cursor = oldItemDelimiter.nextSibling;
                                                while (cursor.nodeType != ELEMENT_TYPE ||
                                                       (!YAHOO.util.Dom.hasClass(cursor, "xforms-repeat-delimiter")
                                                               && !YAHOO.util.Dom.hasClass(cursor, "xforms-repeat-begin-end"))) {
                                                    if (cursor.nodeType == ELEMENT_TYPE)
                                                        YAHOO.util.Dom.removeClass(cursor, ORBEON.util.Utils.getClassForRepeatId(repeatId));
                                                    cursor = cursor.nextSibling;
                                                }
                                            }
                                        }
                                    }
                                }
                                // Store new indexes
                                for (var repeatId in newRepeatIndexes) {
                                    var newIndex = newRepeatIndexes[repeatId];
                                    ORBEON.xforms.Globals.repeatIndexes[repeatId] = newIndex;
                                }
                                // Highlight item at new index
                                for (var repeatId in newRepeatIndexes) {
                                    if (typeof repeatId == "string") { // Hack because repeatId may be trash when some libraries override Object
                                        var newIndex = newRepeatIndexes[repeatId];
                                        if (typeof newIndex == "string" && newIndex != 0) { // Hack because repeatId may be trash when some libraries override Object
                                            var newItemDelimiter = ORBEON.util.Utils.findRepeatDelimiter(repeatId, newIndex);
                                            var cursor = newItemDelimiter.nextSibling;
                                            while (cursor.nodeType != ELEMENT_TYPE ||
                                                   (!YAHOO.util.Dom.hasClass(cursor, "xforms-repeat-delimiter")
                                                           && !YAHOO.util.Dom.hasClass(cursor, "xforms-repeat-begin-end"))) {
                                                if (cursor.nodeType == ELEMENT_TYPE)
                                                    YAHOO.util.Dom.addClass(cursor, ORBEON.util.Utils.getClassForRepeatId(repeatId));
                                                cursor = cursor.nextSibling;
                                            }
                                        }
                                    }
                                }
                                break;
                            }

                            // Server events
                            case "server-events": {
                                var serverEventsElement = actionElement.childNodes[actionIndex];
                                var delay = ORBEON.util.Dom.getAttribute(serverEventsElement, "delay");
                                var showProgress = ORBEON.util.Dom.getAttribute(serverEventsElement, "show-progress");
                                showProgress = YAHOO.lang.isNull(showProgress) || showProgress == "true";
                                var discardable = ORBEON.util.Dom.getAttribute(serverEventsElement, "discardable");
                                discardable = ! YAHOO.lang.isNull(discardable) & discardable == "true";
                                var progressMessage = ORBEON.util.Dom.getAttribute(serverEventsElement, "progress-message");
                                if (delay == null) {
                                    // Case of 2-phase submission: store position of this element, and later when we
                                    // process the submission element, we'll store the value of server-events in the
                                    // $server-events form field, which will be submitted to the server by POSTing
                                    // the form.
                                    serverEventsIndex = actionIndex;
                                } else {
                                    // Case where we need to send those events to the server with a regular Ajax request
                                    // after the given delay.
                                    var serverEvents = ORBEON.util.Dom.getStringValue(serverEventsElement);
                                    AjaxServer.createDelayedServerEvent(serverEvents, delay, showProgress, progressMessage, discardable, formID);
                                }
                                break;
                            }

                            // Submit form
                            case "submission": {
                                var submissionElement = actionElement.childNodes[actionIndex];
                                var showProgress = ORBEON.util.Dom.getAttribute(submissionElement, "show-progress");
                                var replace = ORBEON.util.Dom.getAttribute(submissionElement, "replace");
                                var target = ORBEON.util.Dom.getAttribute(submissionElement, "target");

                                ORBEON.xforms.Globals.formServerEvents[formID].value = serverEventsIndex != -1
                                    ? ORBEON.util.Dom.getStringValue(actionElement.childNodes[serverEventsIndex]) : "";
                                // Increment and send sequence number
                                var requestForm = ORBEON.util.Dom.get(formID);
                                // Go to another page
                                if (showProgress != "false") {
                                    // Display loading indicator unless the server tells us not to display it
                                    newDynamicStateTriggersReplace = true;
                                }
                                // Set the action to the URL of the current page, so the URL seen by the client is always the URL
                                // of a page to which we didn't do a submission replace="all"
                                if (requestForm.action.indexOf("xforms-server-submit") == -1)
                                    requestForm.action = window.location.href;
                                if (target == null) {
                                    // Reset as this may have been changed before by asyncAjaxRequest
                                    requestForm.removeAttribute("target");
                                } else {
                                    // Set the requested target
                                    requestForm.target = target;
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
                                var messageElement = actionElement.childNodes[actionIndex];
                                ORBEON.xforms.action.Message.execute(messageElement);
                                break;
                            }

                            // Load another page
                            case "load": {
                                var loadElement = actionElement.childNodes[actionIndex];
                                var resource = ORBEON.util.Dom.getAttribute(loadElement, "resource");
                                var show = ORBEON.util.Dom.getAttribute(loadElement, "show");
                                var target = ORBEON.util.Dom.getAttribute(loadElement, "target");
                                var showProcess = ORBEON.util.Dom.getAttribute(loadElement, "show-progress");
                                if (show == "replace") {
                                    if (target == null) {
                                        // Display loading indicator unless the server tells us not to display it
                                        if (resource.charAt(0) != '#' && showProcess != "false")
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
                            case "setfocus": {
                                var setfocusElement = actionElement.childNodes[actionIndex];
                                var controlId = ORBEON.util.Dom.getAttribute(setfocusElement, "control-id");
                                ORBEON.xforms.Controls.setFocus(controlId);
                                break;
                            }

                            // Run JavaScript code
                            case "script": {
                                var scriptElement = actionElement.childNodes[actionIndex];
                                var functionName = ORBEON.util.Dom.getAttribute(scriptElement, "name");
                                var targetId = ORBEON.util.Dom.getAttribute(scriptElement, "target-id");
                                var observerId = ORBEON.util.Dom.getAttribute(scriptElement, "observer-id");
                                ORBEON.xforms.server.Server.callUserScript(functionName, targetId, observerId);
                                break;
                            }

                            // Show help message for specified control
                            case "help": {
                                var helpElement = actionElement.childNodes[actionIndex];
                                var controlId = ORBEON.util.Dom.getAttribute(helpElement, "control-id");
                                var control = ORBEON.util.Dom.get(controlId);
                                ORBEON.xforms.Controls.showHelp(control);
                                break;
                            }

                            // Take form offline
                            case "offline": {
                                var offlineElement = actionElement.childNodes[actionIndex];
                                var eventsElements = ORBEON.util.Dom.getElementsByName(offlineElement, "events", xmlNamespace);
                                var mappingsElements = ORBEON.util.Dom.getElementsByName(offlineElement, "mappings", xmlNamespace);
                                if (eventsElements.length != 0 && mappingsElements.length != 0) {
                                    var replayResponse = ORBEON.util.Dom.getStringValue(eventsElements[0]);
                                    var mappings = ORBEON.util.Dom.getStringValue(mappingsElements[0]);
                                    ORBEON.xforms.Offline.takeOffline(replayResponse, formID, mappings);
                                }
                            }
                        }
                    }
                }
            }

            if (newDynamicStateTriggersReplace) {
                // Display loading indicator when we go to another page.
                // Display it even if it was not displayed before as loading the page could take time.
                xformsDisplayIndicator("loading");
                ORBEON.xforms.Globals.loadingOtherPage = true;
            }

            // We can safely set this to false here, as if there is a request executed right after this, requestInProgress is set again to true by executeNextRequest().
            ORBEON.xforms.Globals.requestInProgress = false;
        } catch (e) {
            // Show dialog with error to the user, as they won't be able to continue using the UI anyway
            AjaxServer.exceptionWhenTalkingToServer(e, formID);
            // Rethrow, so the exception isn't lost (can be shown by Firebug, or a with little icon on the bottom left of the IE window)
            throw e;
        }
    };

})();

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

    var $ = ORBEON.jQuery;
    var AjaxServer = ORBEON.xforms.server.AjaxServer;
    var Controls = ORBEON.xforms.Controls;
    var Properties = ORBEON.util.Properties;
    var StringUtils = ORBEON.util.String;
    var Globals = ORBEON.xforms.Globals;

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
                if (details != null) {
                    formErrorPanel.errorDetailsDiv.innerHTML = details;
                    // Restore arrow button in case we had called errorHideAllDetails() before
                    ORBEON.xforms.Events.errorShowHideDetails.apply(formErrorPanel.errorBodyDiv);
                } else {
                    // No details so don't show the arrow to show the details
                    ORBEON.xforms.Events.errorHideAllDetails(formErrorPanel.errorBodyDiv);
                }
                formErrorPanel.show();
                ORBEON.xforms.Globals.lastDialogZIndex += 2;
                formErrorPanel.cfg.setProperty("zIndex", ORBEON.xforms.Globals.lastDialogZIndex);
                formErrorPanel.center();
                // Focus within the dialog so that screen readers handle aria attributes
                $('.xforms-error-panel .container-close').focus();
            }
        }
    };

    AjaxServer.fireEvents = function(events, incremental) {
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
    };

    /**
     * Create a timer which after the specified delay will fire a server event.
     */
    AjaxServer.createDelayedServerEvent = function(serverEvents, delay, showProgress, progressMessage, discardable, formID) {
        var timerId = window.setTimeout(function () {
            var event = new AjaxServer.Event(ORBEON.util.Dom.get(formID), null,
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

    AjaxServer.beforeSendingEvent = $.Callbacks();
    AjaxServer.ajaxResponseReceived = $.Callbacks();

    AjaxServer.executeNextRequest = function(bypassRequestQueue) {
        bypassRequestQueue = typeof(bypassRequestQueue) == "boolean" && bypassRequestQueue == true;

        ORBEON.xforms.Globals.executeEventFunctionQueued--;
        if (!ORBEON.xforms.Globals.requestInProgress
                && ORBEON.xforms.Globals.eventQueue.length > 0
                && (bypassRequestQueue || ORBEON.xforms.Globals.executeEventFunctionQueued == 0)) {

            // Filter events (typically used for xforms-focus/xxforms-blur events)
            (function() {
                var eventsToFilter = Properties.clientEventsFilter.get().split(" ");
                Globals.eventQueue = _.filter(Globals.eventQueue, function(event) {
                    var filterThisEvent = _.contains(eventsToFilter, event.eventName);
                    return ! filterThisEvent;
                });
            })();

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
                    if (event.targetId != null) {
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
                        if (event.eventName == "xxforms-value") {
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
                    ORBEON.xforms.Globals.eventQueue = newEvents;
                }

                // Call listeners on events before being sent, giving them a sense to provide additional event properties
                _.each(ORBEON.xforms.Globals.eventQueue, function(event) {
                    AjaxServer.beforeSendingEvent.fire(event, function(properties) {
                        _.extend(event.properties, properties);
                    });
                });

                // Check again that we have events to send after collapsing
                if (ORBEON.xforms.Globals.eventQueue.length > 0) {

                    // Save the form for this request
                    ORBEON.xforms.Globals.requestForm = ORBEON.xforms.Globals.eventQueue[0].form;
                    var formID = ORBEON.xforms.Globals.requestForm.id;

                    // Remove from this list of ids that changed the id of controls for
                    // which we have received the keyup corresponding to the keydown.
                    // From ECMAScript 5.1: "Properties of the object being enumerated may be deleted during enumeration."
                    _.each(ORBEON.xforms.Globals.changedIdsRequest, function(value, key) {
                        if (value == 0)
                            $(ORBEON.xforms.Globals.changedIdsRequest).removeProp(key);
                    });

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
                        if (event.eventName == "xxforms-all-events-required")
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

                    // Tell the loading indicator whether to display itself and what the progress message on the next Ajax request
                    var loadingIndicator = ORBEON.xforms.Page.getForm(formID).getLoadingIndicator();
                    loadingIndicator.setNextConnectProgressShown(showProgress);
                    loadingIndicator.setNextConnectProgressMessage(progressMessage);

                    // Build request
                    var requestDocumentString = [];

                    // Add entity declaration for nbsp. We are adding this as this entity is generated by the FCK editor.
                    // The "unnecessary" concatenation is done to prevent IntelliJ from wrongly interpreting this
                    requestDocumentString.push('<!' + 'DOCTYPE xxf:event-request [<!ENTITY nbsp "&#160;">]>\n');

                    var indent = "    ";
                    {
                        // Start request
                        requestDocumentString.push('<xxf:event-request xmlns:xxf="http://orbeon.org/oxf/xml/xforms">\n');

                        // Add form UUID
                        requestDocumentString.push(indent);
                        requestDocumentString.push('<xxf:uuid>');
                        requestDocumentString.push(ORBEON.xforms.Document.getFromClientState(formID, "uuid"));
                        requestDocumentString.push('</xxf:uuid>\n');

                        // Increment and send sequence number if we have at least one event which is not a request for upload progress or session heartbeat
                        var requestWithSequenceNumber = ! _.isUndefined(_.find(eventsToSend, function(event) {
                            return event.eventName != "xxforms-upload-progress" && event.eventName != "xxforms-session-heartbeat"; }));
                        // Still send the element name even if empty as this is what the schema and server-side code expects
                        requestDocumentString.push(indent);
                        requestDocumentString.push('<xxf:sequence>');
                        if (requestWithSequenceNumber) {
                            var currentSequenceNumber = ORBEON.xforms.Document.getFromClientState(formID, "sequence");
                            requestDocumentString.push(currentSequenceNumber);
                            AjaxServer.ajaxResponseReceived.add(function incrementSequenceNumber() {
                                // Increment sequence number, now that we know the server processed our request
                                //      If we were to do this after the request was processed, we might fail to increment the sequence
                                //      if we were unable to process the response (i.e. JS error). Doing this here, before the
                                //      response is processed, we incur the risk of incrementing the counter while the response is
                                //      garbage and in fact maybe wasn't even sent back by the server, but by a front-end.
                                var newSeq = parseInt(currentSequenceNumber) + 1;
                                ORBEON.xforms.Document.storeInClientState(formID, "sequence", newSeq);
                                AjaxServer.ajaxResponseReceived.remove(incrementSequenceNumber);
                            })
                        }
                        requestDocumentString.push('</xxf:sequence>\n');

                        // Add static state
                        var staticState = ORBEON.xforms.Globals.formStaticState[formID].value;
                        if (staticState != null && staticState != "") {
                            requestDocumentString.push(indent);
                            requestDocumentString.push('<xxf:static-state>');
                            requestDocumentString.push(staticState);
                            requestDocumentString.push('</xxf:static-state>\n');
                        }

                        // Add dynamic state
                        var dynamicState = ORBEON.xforms.Globals.formDynamicState[formID].value;
                        if (dynamicState != null && dynamicState != "") {
                            requestDocumentString.push(indent);
                            requestDocumentString.push('<xxf:dynamic-state>');
                            requestDocumentString.push(dynamicState);
                            requestDocumentString.push('</xxf:dynamic-state>\n');
                        }

                        // Add initial dynamic state if needed
                        if (sendInitialDynamicState) {
                            requestDocumentString.push(indent);
                            requestDocumentString.push('<xxf:initial-dynamic-state>');
                            requestDocumentString.push(ORBEON.xforms.Document.getFromClientState(formID, "initial-dynamic-state"));
                            requestDocumentString.push('</xxf:initial-dynamic-state>\n');
                        }

                        // Keep track of the events we have handled, so we can later remove them from the queue

                        // Start action
                        requestDocumentString.push(indent);
                        requestDocumentString.push('<xxf:action>\n');

                        // Add events
                        _.each(eventsToSend, function(event) {
                            // Create <xxf:event> element
                            requestDocumentString.push(indent + indent);
                            requestDocumentString.push('<xxf:event');
                            requestDocumentString.push(' name="' + event.eventName + '"');
                            if (event.targetId != null)
                                requestDocumentString.push(' source-control-id="' + event.targetId.substring(ORBEON.xforms.Globals.ns[formID].length) + '"');
                            if (event.additionalAttribs != null) {
                                for(var attribIndex = 0; attribIndex < event.additionalAttribs.length - 1; attribIndex+=2) {
                                    var attribName = event.additionalAttribs[attribIndex];
                                    var escapedAttribValue = event.additionalAttribs[attribIndex+1].replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/&/g, "&amp;");
                                    requestDocumentString.push(' '+ attribName +'="' + escapedAttribValue + '"');
                                }
                            }
                            requestDocumentString.push('>');
                            if (event.value != null) {
                                // When the range is used we get an int here when the page is first loaded
                                if (typeof event.value == "string") {
                                    event.value = event.value.replace(XFORMS_REGEXP_AMPERSAND, "&amp;");
                                    event.value = event.value.replace(XFORMS_REGEXP_OPEN_ANGLE, "&lt;");
                                    event.value = event.value.replace(XFORMS_REGEXP_CLOSE_ANGLE, "&gt;");
                                    event.value = event.value.replace(XFORMS_REGEXP_INVALID_XML_CHAR, "");
                                }
                                requestDocumentString.push(event.value);
                            } else if (! _.isEmpty(event.properties)) {
                                // Only add properties when we don't have a value (in the future, the value should be
                                // sent in a sub-element, so both a value and properties can be sent for the same event)
                                requestDocumentString.push('\n');
                                _.each(_.keys(event.properties), function(name) {
                                    var value = event.properties[name];
                                    var propertyParts = [
                                        indent + indent + indent,
                                        '<xxf:property name="' + StringUtils.escapeForMarkup(name) + '">',
                                        StringUtils.escapeForMarkup(value),
                                        '</xxf:property>\n'
                                    ];
                                    _.each(propertyParts, function(part) { requestDocumentString.push(part); });
                                });
                                requestDocumentString.push(indent + indent);
                            }
                            requestDocumentString.push('</xxf:event>\n');
                            remainingEvents = _.without(remainingEvents, event);
                        });

                        // End action
                        requestDocumentString.push(indent);
                        requestDocumentString.push('</xxf:action>\n');

                        // End request
                        requestDocumentString.push('</xxf:event-request>');

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
     *      2015-01-26: Firefox (and others) return a <parsererror> document if the XML doesn't parse. So if there is an
     *      XML Content-Type header but an empty body, for example, we get <parsererror>. See:
     *
     *      https://github.com/orbeon/orbeon-forms/issues/2074
     */
    AjaxServer.handleFailureAjax = function(o) {
        var formID = ORBEON.xforms.Globals.requestForm.id;

        if (o.responseXML &&
            o.responseXML.documentElement &&
            o.responseXML.documentElement.tagName == "error" && // don't catch <parsererror>
            o.status != 503) {                                  // 503 is specifically sent to trigger a retry

            // If we get an error document as follows, we consider this to be a permanent error, we don't retry, and
            // we show an error to users.
            //
            //   <error>
            //       <title>...</title>
            //       <body>...</body>
            //   </error>

            ORBEON.xforms.Globals.requestInProgress = false;
            ORBEON.xforms.Globals.requestDocument = "";
            var title = ORBEON.util.Dom.getStringValue(ORBEON.util.Dom.getElementsByName(o.responseXML.documentElement, "title", null)[0]);
            var body = ORBEON.util.Dom.getElementsByName(o.responseXML.documentElement, "body", null)[0];
            var detailsFromBody = body != null ? ORBEON.util.Dom.getStringValue(body) : null;
            AjaxServer.showError(title, detailsFromBody, formID);
        } else {
            var loginRegexp = ORBEON.util.Properties.loginPageDetectionRegexp.get();
            if (loginRegexp != '' && new RegExp(loginRegexp).test(o.responseText)) {

                // It seems we got a login page back, so display dialog and reload form
                var dialogEl = $('#' + formID + ' .xforms-login-detected-dialog');

                // Link dialog with title for ARIA
                var title = dialogEl.find('h4');
                if (_.isUndefined(title.attr('id'))) {
                    var titleId = _.uniqueId('xf-');
                    title.attr('id', titleId);
                    dialogEl.attr('aria-labelledby', titleId);
                }

                dialogEl.find('button').one('click.xf', function() {
                    // Reloading the page will redirect us to the login page if necessary
                    window.location.href = window.location.href;
                });
                dialogEl.modal({
                  backdrop: 'static', // Click on the background doesn't hide dialog
                  keyboard: false     // Can't use esc to close the dialog
                });
            } else {
                AjaxServer.retryRequestAfterDelay(AjaxServer.asyncAjaxRequest);
            }
        }
    };

    AjaxServer.handleResponseAjax = function(o) {

        var responseXML = o.responseXML;
        if (! _.isUndefined(o.getResponseHeader) && YAHOO.lang.trim(o.getResponseHeader["Content-Type"]) == "text/html") {

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
            var responseXmlIsHTML = responseXML && responseXML.documentElement && responseXML.documentElement.tagName.toLowerCase() == "html";
            var formID = o.argument.formId;
            var isResponseToBackgroundUpload = o.responseText && (!responseXML || responseXmlIsHTML);
            if (isResponseToBackgroundUpload) {
                // Background uploads
                //      In this case, the server sends a xxf:event-response embedded in an HTML document:
                //      <!DOCTYPE HTML><html><body>&lt;xxf:event-response ... </body></html>
                //      When it happens, responseXML may be the HTML document, so we also test on the root element being 'html'
                //      But, surprisingly, responseText only contains the text inside the body, i.e. &lt;xxf:event-response ...
                //      So here we "unescape" the text inside <body>
                // HOWEVER: If, by any chance, there is some trailing stuff after the closing </html>, as in
                //      </xhtml><script/>, then, with FF and IE, we get &lt;/xxf:event-response&gt;<script/> and then
                //      XML parsing fails. Some company proxies might insert scripts this way, so we better make sure
                //      to parse only xxf:event-response.
                var fragment =
                    o.responseText.substring(
                        o.responseText.indexOf("&lt;xxf:event-response"),
                        o.responseText.indexOf("&lt;/xxf:event-response&gt;") + "&lt;/xxf:event-response&gt;".length);
                var xmlString = fragment.replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&amp;/g, "&");
                responseXML = ORBEON.util.Dom.stringToDom(xmlString);
            } else {
                // Regular Ajax response

                // On IE, don't rely on the browser's XML parsing, as it doesn't preserve white spaces
                if (o.responseText && $.browser.msie)
                    responseXML = ORBEON.util.Dom.stringToDom(o.responseText);
            }

            if (o.responseText != "" && responseXML && responseXML.documentElement && responseXML.documentElement.tagName.indexOf("event-response") != -1) {

                // Everything is fine with the response

                if (! isResponseToBackgroundUpload)
                    AjaxServer.ajaxResponseReceived.fire();

                // If neither of these two conditions is met, hide the modal progress panel:
                //      a) There is another Ajax request in the queue, which could be the one that triggered the
                //         display of the modal progress panel, so we don't want to hide before that request ran.
                //      b) The server tells us to do a submission or load, so we don't want to remove it otherwise
                //         users could start interacting with a page which is going to be replaced shortly.
                // We remove the modal progress panel before handling DOM response, as xxf:script may dispatch
                // events and we don't want them to be filtered. If there are server events, we don't remove the
                // panel until they have been processed, i.e. the request sending the server events returns.
                function doHideProgressDialog() {

                    return ! (eventQueueHasShowProgressEvent()
                              || serverSaysToKeepModelProgressPanelDisplayed());

                    function eventQueueHasShowProgressEvent() {
                        return _.some(ORBEON.xforms.Globals.eventQueue,
                                      function(event) { return event.showProgress == true; });
                    }

                    /**
                     * Keep the model progress panel if the server tells us to do a submission or load which isn't opened in another
                     * window and for which the user didn't specify xxf:show-progress="false".
                     *
                     * The logic here corresponds to the following XPath:
                     * exists((//xxf:submission, //xxf:load)[empty(@target) and empty(@show-progress)])
                     */
                    function serverSaysToKeepModelProgressPanelDisplayed() {
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
                    }
                }

                if (doHideProgressDialog()) {
                    ORBEON.util.Utils.hideModalProgressPanel();
                }

                AjaxServer.handleResponseDom(responseXML, isResponseToBackgroundUpload, formID);
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
                // As if the server returned an error code (5xx), in particular used by the loading indicator
                YAHOO.util.Connect.failureEvent.fire();
                AjaxServer.handleFailureAjax(o);
            }
        }
    };

    /**
     * Process events in the DOM passed as parameter.
     *
     * @param responseXML       DOM containing events to process
     */
    AjaxServer.handleResponseDom = function(responseXML, isResponseToBackgroundUpload, formID) {

        try {
            var responseRoot = responseXML.documentElement;

            // Whether this response has triggered a load which will replace the current page.
            var newDynamicStateTriggersReplace = false;

            var xxfPrefix = null; // xforms namespace
            // Getting xforms namespace
            for (var j = 0; j < responseRoot.attributes.length; j++) {
                if (responseRoot.attributes[j].nodeValue == XXFORMS_NAMESPACE_URI) {
                    var attrName = responseRoot.attributes[j].name;
                    xxfPrefix = attrName.substr(attrName.indexOf(":") + 1);
                    break;
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
                                var copyRepeatTemplateElements = $(controlValuesElement).children(xxfPrefix + '\\:copy-repeat-template');
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
                                                // Remove "xforms-repeat-template", "xforms-disabled" from classes on copy of element
                                                var nodeCopyClasses = nodeCopy.className.split(" ");
                                                var nodeCopyNewClasses = new Array();
                                                for (var nodeCopyClassIndex = 0; nodeCopyClassIndex < nodeCopyClasses.length; nodeCopyClassIndex++) {
                                                    var currentClass = nodeCopyClasses[nodeCopyClassIndex];
                                                    if (currentClass != "xforms-repeat-template" && currentClass != "xforms-disabled" && currentClass != "xforms-disabled-subsequent")
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
                                                ORBEON.util.Utils.addSuffixToIdsAndRemoveDisabled(newTemplateNode, parentIndexes == "" ? String(suffix) : parentIndexes + XF_REPEAT_INDEX_SEPARATOR + suffix, nestedRepeatLevel);
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

                                var deleteRepeatTemplateElements = $(controlValuesElement).children(xxfPrefix + '\\:delete-repeat-elements');
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

                    // Second handle the <xxf:itemset> actions (we want to do this before we set the value of
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

                                    if (YAHOO.util.Dom.hasClass(documentElement, "xforms-select1-appearance-compact")
                                            || YAHOO.util.Dom.hasClass(documentElement, "xforms-select-appearance-compact")
                                            || YAHOO.util.Dom.hasClass(documentElement, "xforms-select1-appearance-minimal")) {

                                        // Case of list / combobox
                                        var select = documentElement.getElementsByTagName("select")[0];
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
                                            var selected = _.contains(selectedValues, value);
                                            return '<option value="' + ORBEON.util.String.escapeForMarkup(value) + '"'
                                                    + (selected ? ' selected="selected"' : '')
                                                    + (clazz != null ? ' class="' + ORBEON.util.String.escapeForMarkup(clazz) + '"' : '')
                                                    + '>' + label + '</option>';
                                        }

                                        // Utility function to generate an item, including its sub-items, and make sure we do not produce nested optgroups
                                        var sb = new Array(); // avoid concatenation to the same string over and over again
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
                                                sb[sb.length] = '<optgroup label="' + ORBEON.util.String.escapeForMarkup(itemElement.label) + '"'
                                                    + (clazz != null ? ' class="' + ORBEON.util.String.escapeForMarkup(clazz) + '"' : '')
                                                    + '">';
                                                inOptgroup = true;
                                                // add subitems
                                                for (var childItemIndex = 0; childItemIndex < itemElement.children.length; childItemIndex++) {
                                                    generateItem(itemElement.children[childItemIndex]);
                                                }
                                                // if necessary, close optgroup
                                                if (inOptgroup)
                                                    sb[sb.length] = '</optgroup>';
                                                inOptgroup = false;
                                            }
                                        }


                                        // Build new content for the select element
                                        for (var topIndex = 0; topIndex < itemsetTree.length; topIndex++) {
                                            generateItem(itemsetTree[topIndex]);
                                        }

                                        // Set content of select element
                                        if (ORBEON.xforms.Globals.isRenderingEngineTrident) {
                                            // IE does not support setting the content of a select with innerHTML
                                            // So we have to generate the whole select, and use outerHTML
                                            YAHOO.util.Event.removeListener(select, "change");
                                            var selectOpeningTag = select.outerHTML.substring(0, select.outerHTML.indexOf(">") + 1);
                                            select.outerHTML = selectOpeningTag + sb.join("") + "</select>";
                                            // Get again control, as it has been re-created
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
                                        var isFull = YAHOO.util.Dom.hasClass(documentElement, "xforms-select");
                                        var template = isFull
                                                ? ORBEON.util.Dom.get("xforms-select-full-template")
                                                : ORBEON.util.Dom.get("xforms-select1-full-template");
                                        template = ORBEON.util.Dom.getChildElementByIndex(template, 0);

                                        // Get the span that contains the one span per checkbox/radio
                                        // This is the first span that has no class on it (we don't want to get a span for label, hint, help, alert)
                                        var spanContainer = _.detect(documentElement.getElementsByTagName("span"), function(span) { return span.className == "xforms-items"; });

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

                                            // Handle empty string explicitly as otherwise $.parseHTML("") returns null
                                            var parsedLabel = itemElement.label == "" ? [] : $.parseHTML(itemElement.label);
                                            ORBEON.util.Utils.replaceInDOM(templateClone, "$xforms-template-label$", parsedLabel, true);
                                            ORBEON.util.Utils.replaceInDOM(templateClone, "$xforms-template-value$", itemElement.value, false);
                                            var itemEffectiveId = ORBEON.util.Utils.appendToEffectiveId(controlId, XF_LHHAI_SEPARATOR + "e" + itemIndex);
                                            ORBEON.util.Utils.replaceInDOM(templateClone, isFull ? "$xforms-item-id-select$" : "$xforms-item-id-select1$", itemEffectiveId, false);
                                            ORBEON.util.Utils.replaceInDOM(templateClone, "$xforms-item-name$", controlId, false);

                                            if (itemElement.help && itemElement.help != "") {
                                                ORBEON.util.Utils.replaceInDOM(templateClone, "$xforms-template-help$", itemElement.help, false);
                                            } else {
                                                $(templateClone).find('.xforms-help').remove();
                                            }

                                            if (itemElement.hint && itemElement.hint != "") {
                                                ORBEON.util.Utils.replaceInDOM(templateClone, "$xforms-template-hint$", itemElement.hint, false);
                                            } else {
                                                $(templateClone).find('.xforms-hint-region').removeAttr("class");
                                                $(templateClone).find('.xforms-hint').remove();
                                            }

                                            if (! _.isUndefined(itemElement.attributes) && ! _.isUndefined(itemElement.attributes["class"])) {
                                                templateClone.className += " " + itemElement.attributes["class"];
                                            }

                                            spanContainer.appendChild(templateClone);

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
                                var controlElements = $(controlValuesElement).children(xxfPrefix + '\\:control');
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
                                    if (newSchemaType != null) {

                                        if (YAHOO.util.Dom.hasClass(documentElement, "xforms-input")) {

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
                                                // Clean-up document element by removing type classes
                                                _.each(INPUT_TYPES, function(type) { YAHOO.util.Dom.removeClass(documentElement, type.className); });
                                                // Minimal control content can be different
                                                var isMinimal = YAHOO.util.Dom.hasClass(documentElement, "xforms-input-appearance-minimal");

                                                // Find the position of the last label before the control "actual content" and remove all elements that are not labels
                                                // A value of -1 means that the content came before any label
                                                var lastLabelPosition = null;
                                                (function() {
                                                    var childElements = YAHOO.util.Dom.getChildren(documentElement);
                                                    for (var childIndex = 0; childIndex < childElements.length; childIndex++) {
                                                        var childElement = childElements[childIndex];
                                                        if (! YAHOO.util.Dom.hasClass(childElement, "xforms-label")
                                                                && ! YAHOO.util.Dom.hasClass(childElement, "xforms-help")
                                                                && ! YAHOO.util.Dom.hasClass(childElement, "xforms-hint")
                                                                && ! YAHOO.util.Dom.hasClass(childElement, "xforms-alert")) {
                                                            documentElement.removeChild(childElement);
                                                            if (lastLabelPosition == null)
                                                                lastLabelPosition = childIndex - 1;
                                                        }
                                                    }
                                                })();

                                                function insertIntoDocument(nodes) {
                                                    var childElements = YAHOO.util.Dom.getChildren(documentElement);
                                                    // Insert after "last label" (we remembered the position of the label after which there is real content)
                                                    if (childElements.length == 0) {
                                                        for (var nodeIndex = 0; nodeIndex < nodes.length; nodeIndex++)
                                                            documentElement.appendChild(nodes[nodeIndex]);
                                                    } else if (lastLabelPosition == -1) {
                                                        // Insert before everything else
                                                        var firstChild = childElements[0];
                                                        for (var nodeIndex = 0; nodeIndex < nodes.length; nodeIndex++)
                                                            YAHOO.util.Dom.insertBefore(nodes[nodeIndex], firstChild);
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
                                                    if (newInputElement.id.indexOf(ORBEON.xforms.Globals.ns[formID]) == 0)
                                                        newInputElement.name = newInputElement.id.substring(ORBEON.xforms.Globals.ns[formID].length);
                                                    else
                                                        newInputElement.name = newInputElement.id; // should not happen as ns should be valid or ""
                                                    return newInputElement;
                                                }

                                                var inputLabelElement = ORBEON.xforms.Controls.getControlLHHA(documentElement, "label");
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
                                                    image.setAttribute("src", ORBEON.xforms.Globals.calendarImageURL[formID]);
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
                                        var classesArray = documentElement.className.split(" ");
                                        for (var classIndex = 0; classIndex < classesArray.length; classIndex++) {
                                            var currentClass = classesArray[classIndex];
                                            if (currentClass.indexOf(typePrefix) == 0) {
                                                YAHOO.util.Dom.removeClass(documentElement, currentClass);
                                            }
                                        }

                                        // Add new class
                                        var typeResult = /{(.*)}(.*)/.exec(newSchemaType);
                                        if (typeResult != null && typeResult.length == 3) {
                                            var typeNamespace = typeResult[1];
                                            var typeLocalName = typeResult[2];
                                            var isBuiltIn = typeNamespace == 'http://www.w3.org/2001/XMLSchema'
                                                         || typeNamespace == 'http://www.w3.org/2002/xforms';
                                            var newClass = typePrefix + (isBuiltIn ? '' : 'custom-') + typeLocalName;
                                            $(documentElement).addClass(newClass);
                                        }
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
                                            // Additional attributes for xf:upload
                                            // <xxf:control id="xforms-control-id"
                                            //    state="empty|file"
                                            //    accept=".txt"
                                            //    filename="filename.txt" mediatype="text/plain" size="23kb"/>
                                            ORBEON.xforms.Controls.setCurrentValue(
                                                documentElement,
                                                newControlValue,
                                                ORBEON.util.Dom.getAttribute(controlElement, "state"),
                                                ORBEON.util.Dom.getAttribute(controlElement, "filename"),
                                                ORBEON.util.Dom.getAttribute(controlElement, "mediatype"),
                                                ORBEON.util.Dom.getAttribute(controlElement, "size"),
                                                ORBEON.util.Dom.getAttribute(controlElement, "accept"));
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
                                                previousServerValue = previousServerValue == null ? null : ORBEON.util.String.normalizeSerializedHTML(previousServerValue);
                                                currentValue = ORBEON.util.String.normalizeSerializedHTML(currentValue);
                                                newControlValue = ORBEON.util.String.normalizeSerializedHTML(newControlValue);

                                                var isInputOrSecret = YAHOO.util.Dom.hasClass(documentElement, "xforms-input")
                                                    || YAHOO.util.Dom.hasClass(documentElement, "xforms-secret");

                                                var inputSize = isInputOrSecret ? ORBEON.util.Dom.getAttribute(controlElement, "size") : null;
                                                var inputAutocomplete = isInputOrSecret ? ORBEON.util.Dom.getAttribute(controlElement, "autocomplete") : null;

                                                var isTextarea = YAHOO.util.Dom.hasClass(documentElement, "xforms-textarea");

                                                var textareaCols = isTextarea ? ORBEON.util.Dom.getAttribute(controlElement, "cols") : null;
                                                var textareaRows = isTextarea ? ORBEON.util.Dom.getAttribute(controlElement, "rows") : null;

                                                var maxlength = (isInputOrSecret || isTextarea) ? ORBEON.util.Dom.getAttribute(controlElement, "maxlength") : null;

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
                                                        // Special xf:input attributes
                                                        (isInputOrSecret && (inputSize != null || maxlength != null || inputAutocomplete != null)) ||
                                                        // Special xf:textarea attributes
                                                        (isTextarea && (maxlength != null || textareaCols != null || textareaRows != null));
                                                if (doUpdate) {
                                                    if (isInputOrSecret) {
                                                        // Additional attributes for xf:input
                                                        ORBEON.xforms.Controls.setCurrentValue(documentElement, newControlValue, inputSize, maxlength, inputAutocomplete);
                                                    } else if (isTextarea && YAHOO.util.Dom.hasClass(documentElement, "xforms-mediatype-text-html")) {
                                                        ORBEON.xforms.Controls.setCurrentValue(documentElement, newControlValue);
                                                    } else if (isTextarea) {
                                                        // Additional attributes for xf:textarea
                                                        ORBEON.xforms.Controls.setCurrentValue(documentElement, newControlValue, maxlength, textareaCols, textareaRows);
                                                    } else {
                                                        // Other control just have a new value
                                                        ORBEON.xforms.Controls.setCurrentValue(documentElement, newControlValue);
                                                    }
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
                                                    ORBEON.xforms.ServerValueStore.set(documentElement.id, ORBEON.xforms.Controls.getCurrentValue(documentElement));
                                                }
                                            }
                                        }

                                        // Call custom listener if any (temporary until we have a good API for custom components)
                                        if (typeof xformsValueChangedListener != "undefined") {
                                            xformsValueChangedListener(controlId, newControlValue);
                                        }
                                    }

                                    // Update the required-empty/required-full even if the required has not changed or
                                    // is not specified as the value may have changed
                                    if (! isStaticReadonly) {
                                        var emptyAttr = $(controlElement).attr("empty");
                                        if (!_.isUndefined(emptyAttr))
                                            ORBEON.xforms.Controls.updateRequiredEmpty(documentElement, emptyAttr);
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
                                    var newLevel = ORBEON.util.Dom.getAttribute(controlElement, "level");
                                    if (newLevel != null)
                                        ORBEON.xforms.Controls.setConstraintLevel(documentElement, newLevel);

                                    // Handle progress for upload controls
                                    if (progressState != null && progressState != "")
                                        ORBEON.xforms.Page.getControl(documentElement).progress(
                                            progressState,
                                            progressReceived != null && progressReceived != "" ? parseInt(progressReceived) : null,
                                            progressExpected != null && progressExpected != "" ? parseInt(progressExpected) : null);

                                    // Handle visited flag
                                    var newVisited = ORBEON.util.Dom.getAttribute(controlElement, "visited");
                                    if (newVisited)
                                        ORBEON.xforms.Controls.updateVisited(documentElement, newVisited == 'true');

                                    // Notification event if the type changed
                                    if (recreatedInput) Controls.typeChangedEvent.fire({control: documentElement});
                                }

                                // Handle innerHTML updates
                                var innerElements = $(controlValuesElement).children(xxfPrefix + '\\:inner-html');
                                var innerElementsLength = innerElements.length;
                                for (var j = 0; j < innerElementsLength; j++) {
                                    var innerElement = innerElements[j];
                                    var innerHTML = ORBEON.util.Dom.getStringValue(innerElement);
                                    var controlId = ORBEON.util.Dom.getAttribute(innerElement, "id");
                                    var documentElement = document.getElementById(controlId);
                                    if (documentElement != null) {
                                        // Found container
                                        // Detaching children to avoid nodes becoming disconnected
                                        // http://wiki.orbeon.com/forms/doc/contributor-guide/browser#TOC-In-IE-nodes-become-disconnected-when-removed-from-the-DOM-with-an-innerHTML
                                        $(documentElement).children().detach();
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
                                var attributeElements = $(controlValuesElement).children(xxfPrefix + '\\:attribute');
                                var attributeElementslength = attributeElements.length;
                                for (var j = 0; j < attributeElementslength; j++) {
                                    var attributeElement = attributeElements[j];
                                    var newAttributeValue = ORBEON.util.Dom.getStringValue(attributeElement);
                                    var forAttribute = ORBEON.util.Dom.getAttribute(attributeElement, "for");
                                    var nameAttribute = ORBEON.util.Dom.getAttribute(attributeElement, "name");
                                    var htmlElement = ORBEON.util.Dom.get(forAttribute);
                                    if (htmlElement != null) {// use case: xh:html/@lang but HTML fragment produced
                                        ORBEON.util.Dom.setAttribute(htmlElement, nameAttribute, newAttributeValue);
                                    }
                                }

                                // Handle text updates
                                var textElements = $(controlValuesElement).children(xxfPrefix + '\\:text');
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
                                var repeatIterationElements = $(controlValuesElement).children(xxfPrefix + '\\:repeat-iteration');
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

                                // "div" elements for xf:switch and xxf:dialog
                                var divsElements = $(controlValuesElement).children(xxfPrefix + '\\:div');
                                var divElementsLength = divsElements.length;
                                for (var j = 0; j < divElementsLength; j++) {
                                    var divElement = divsElements[j];

                                    var controlVisibilityChangeId = ORBEON.util.Dom.getAttribute(divElement, "id");
                                    var visible = ORBEON.util.Dom.getAttribute(divElement, "visibility") == "visible";
                                    var neighbor = ORBEON.util.Dom.getAttribute(divElement, "neighbor");

                                    var yuiDialog = ORBEON.xforms.Globals.dialogs[controlVisibilityChangeId];
                                    if (yuiDialog == null) {
                                        // This is a case
                                        ORBEON.xforms.Controls.toggleCase(controlVisibilityChangeId, visible);
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
                                var newRepeatIndexes = {};
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
                                var showProgress = ORBEON.util.Dom.getAttribute(loadElement, "show-progress");

                                if (resource.indexOf("javascript:") == 0) {
                                    // JavaScript URL
                                    ORBEON.util.String.eval(resource.substring("javascript:".length));
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
                                var focusElement = actionElement.childNodes[actionIndex];
                                var controlId = ORBEON.util.Dom.getAttribute(focusElement, "control-id");
                                ORBEON.xforms.Controls.setFocus(controlId);
                                break;
                            }

                            // Remove focus from a control
                            case "blur": {
                                var blurElement = actionElement.childNodes[actionIndex];
                                var controlId = ORBEON.util.Dom.getAttribute(blurElement, "control-id");
                                ORBEON.xforms.Controls.removeFocus(controlId);
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
                        }
                    }

                } else if (ORBEON.util.Utils.getLocalName(responseRoot.childNodes[i]) == "errors") {

                    // <xxf:errors>
                    var errorsElement = responseRoot.childNodes[i];
                    var details = "<ul>";
                    for (var errorIndex = 0; errorIndex < errorsElement.childNodes.length; errorIndex++) {

                        // <xxf:error exception="org.orbeon.saxon.trans.XPathException" file="gaga.xhtml" line="24" col="12">
                        //     Invalid date "foo" (Year is less than four digits)
                        // </xxf:error>
                        var errorElement = errorsElement.childNodes[errorIndex];
                        var exception = ORBEON.util.Dom.getAttribute(errorElement, "exception");
                        var file = ORBEON.util.Dom.getAttribute(errorElement, "file");
                        var line = ORBEON.util.Dom.getAttribute(errorElement, "line");
                        var col = ORBEON.util.Dom.getAttribute(errorElement, "col");
                        var message = ORBEON.util.Dom.getStringValue(errorElement);

                        // Create HTML with message
                        details += "<li>" + message;
                        if (file) details += " in " + ORBEON.util.String.escapeForMarkup(file);
                        if (line) details += " line " + ORBEON.util.String.escapeForMarkup(line);
                        if (col) details += " column " + ORBEON.util.String.escapeForMarkup(col);
                        if (exception) details += " (" + ORBEON.util.String.escapeForMarkup(exception) + ")";
                        details += "</li>";
                    }
                    details += "</ul>";
                    AjaxServer.showError("Non-fatal error", details, formID);
                }
            }

            if (newDynamicStateTriggersReplace) {
                // Display loading indicator when we go to another page.
                // Display it even if it was not displayed before as loading the page could take time.
                ORBEON.xforms.Page.getForm(formID).getLoadingIndicator().show();
                ORBEON.xforms.Globals.loadingOtherPage = true;
            }
        //} catch (e) {
        //    // Show dialog with error to the user, as they won't be able to continue using the UI anyway
        //    AjaxServer.exceptionWhenTalkingToServer(e, formID);
        //    // Don't rethrow exception: we want to code that runs after the Ajax response is handled to run, so we have a chance to recover from this error
        } finally {
            // We can safely set this to false here, as if there is a request executed right after this, requestInProgress is set again to true by executeNextRequest().
            if (! isResponseToBackgroundUpload)
                ORBEON.xforms.Globals.requestInProgress = false;
        }
    };

})();

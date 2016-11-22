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
     * Handles a queue of events to which events can be added, and where after a certain delay all the events are
     * executed at once.
     *
     * @param {function(Array.<Event>, function())}  execute    Function used to execute events. The function receives
     *                                                          the event to execute and a callback function to call
     *                                                          when it is done executing the event.
     */
    ORBEON.util.ExecutionQueue = function(execute) {
        this.execute = execute;
    };

    var ExecutionQueue = ORBEON.util.ExecutionQueue;

    /** @type {function(Array.<Event>, function())} */  ExecutionQueue.prototype.execute = null;
    /** @type {boolean} */                              ExecutionQueue.prototype.waitingCompletion = false;
    /** @type {number} */                               ExecutionQueue.prototype.delayFunctions = 0;
    /** @type {Array.<Event>} */                        ExecutionQueue.prototype.events = [];

    ExecutionQueue.MIN_WAIT = 0;
    ExecutionQueue.MAX_WAIT = 1;

    /**
     * Adds an event to the queue and executes all the events in the queue either:
     *
     * a. If waitType is MIN_WAIT, after the specified delay unless another event is added to the queue before that.
     * b. If waitType is MAX_WAIT, after the specified delay.
     *
     * @param {Event}   event       Event to be executed
     * @param {number}  waitMs      Number of ms to wait
     * @param {number}  waitType    Either MIN_WAIT or MAX_WAIT
     * @void
     */
    ExecutionQueue.prototype.add = function(event, waitMs, waitType) {
        this.events.push(event);
        if (! this.waitingCompletion) {
            this.delayFunctions++;
            _.delay(_.bind(function() {
                this.delayFunctions--;
                if ((waitType == ExecutionQueue.MAX_WAIT || this.delayFunctions == 0) && ! this.waitingCompletion)
                    this.executeEvents();
            }, this), waitMs);
        }
    };

    /**
     * Calls the execute function to run the events queued up so far.
     *
     * @private
     * @void
     */
    ExecutionQueue.prototype.executeEvents = function() {
        if (this.events.length > 0) {
            this.waitingCompletion = true;
            var events = this.events;
            this.events = [];
            this.execute(events, _.bind(function() {
                this.waitingCompletion = false;
                this.executeEvents();
            }, this));
        }
    };

})();
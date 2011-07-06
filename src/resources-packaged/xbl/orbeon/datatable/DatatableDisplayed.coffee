# Copyright (C) 2011 Orbeon, Inc.
#
# This program is free software; you can redistribute it and/or modify it under the terms of the
# GNU Lesser General Public License as published by the Free Software Foundation; either version
# 2.1 of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
# without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
# See the GNU Lesser General Public License for more details.
#
# The full text of the license is available at http://www.gnu.org/copyleft/lesser.html


# Deals with the details of  whether we consider the datatable to be "displayed" and hence should go ahead
# with its initialization.

YD = YAHOO.util.Dom
Event = YAHOO.util.Event
Events = ORBEON.xforms.Events
Property = ORBEON.util.Property
Page = ORBEON.xforms.Page
Controls = ORBEON.xforms.Controls

# Whether we wait until the datatable is in the viewport before initializing it.
initInViewPortProperty = new Property "xbl.fr.datatable.init-in-viewport", false

# An abstract condition; concrete classes follow.
class Condition
    constructor: (@container) ->

# Is the datatable below the bottom of the viewport? We could also not render the datatable if we
# have to scroll right to reach it but here we don't bother optimizing this more rare case.
class InViewportCondition extends Condition
    isMet: ->
        containerTop = YD.getY @container
        containerBottom = containerTop + @container.offsetHeight
        viewportTop = YD.getDocumentScrollTop()
        viewportBottom = viewportTop + YD.getViewportHeight()
        containerTop < viewportBottom  and viewportTop < containerBottom
    addListener: (listener) -> Event.addListener window, "scroll", listener
    removeListener: (listener) -> Event.removeListener window, "scroll", listener

# Is the datatable rendered off-screen (with top: -10000px; left: -10000px;  position: absolute).
# Rendering the datatable in that case would produce inaccurate results.
class OnScreenCondition extends Condition
    isMet: ->
        _.all ["xforms-case-deselected", "xforms-initially-hidden"], (name) =>
            firstChild = YD.getFirstChild @container
            (YD.getAncestorByClassName firstChild, name) == null
    addListener: (listener) -> Events.ajaxResponseProcessedEvent.subscribe listener
    removeListener: (listener) -> Events.ajaxResponseProcessedEvent.unsubscribe listener

# The conditions we want to be met to consider the datatable to be "displayed"
YAHOO.xbl.fr.Datatable.prototype.getConditions = ->
    _.compact [ (new OnScreenCondition @container),
        if initInViewPortProperty.get() then new InViewportCondition @container ]

# Returns true whether we consider the datatable to be "displayed" and hence should go ahead with initialization.
YAHOO.xbl.fr.Datatable.prototype.isDisplayed = ->
    _.all @getConditions(), (condition) => condition.isMet(@container)

# Calls `ready` when the datatable is considered to be displayed. This can happen right away, or at a later time on
# some event like and Ajax response or scroll.
YAHOO.xbl.fr.Datatable.prototype.whenDisplayed = (ready) ->

    # Calls a callback when a list of conditions are met. A condition has isMet, addListener, and
    # removeListener properties.
    onConditions = (conditions, conditionsMet) =>
        unmetCondition = _.detect conditions, (condition) -> not condition.isMet()
        if unmetCondition?
            listener = () -> if unmetCondition.isMet()
                unmetCondition.removeListener listener
                onConditions conditions, conditionsMet
            unmetCondition.addListener listener
        else
            # Run code doing the real rendering of the database, while showing the loading indicator
            formElement = Controls.getForm @container
            orbeonForm = Page.getForm formElement.id
            orbeonForm.getLoadingIndicator().runShowing conditionsMet

    onConditions @getConditions(), () -> ready()

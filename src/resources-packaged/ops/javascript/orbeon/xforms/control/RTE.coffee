Control = ORBEON.xforms.control.Control
RTEConfig = ORBEON.xforms.control.RTEConfig
ServerValueStore = ORBEON.xforms.ServerValueStore
Events = ORBEON.xforms.Events
Page = ORBEON.xforms.Page
OD = ORBEON.util.Dom
YD = YAHOO.util.Dom
CustomEvent = YAHOO.util.CustomEvent
Utils = ORBEON.util.Utils

class RTE extends Control

    constructor: () ->
        @yuiRTE = null
        @isRendered = false
        @renderedEvent = new CustomEvent "rteRendered"

    # Initializes the RTE editor for a particular control.
    init: (container) ->
        super container
        if Utils.isNewXHTMLLayout()
            # In span mode, get textarea under container and keep current structure
            textarea = @container.getElementsByTagName("textarea")[0]
        else
            # In nospan mode, insert newly created container
            textarea = @container
            @container = document.createElement "span"
            YD.insertAfter @container, textarea
            @container.appendChild textarea
            # Move classes on the container created by YUI
            @container.className = textarea.className
            textarea.className = ""
            # Move the id to the container, and add -textarea to the id of the textarea
            containerId = textarea.id
            textarea.id = textarea.id + "-textarea"
            @container.id = containerId
        # Make sure that textarea is not disabled unless readonly, otherwise RTE renders it in read-only mode
        textarea.disabled = YD.hasClass @container, "xforms-readonly"
        # Create RTE object
        rteConfig = if typeof YUI_RTE_CUSTOM_CONFIG != "undefined" then YUI_RTE_CUSTOM_CONFIG else RTEConfig
        @yuiRTE = new YAHOO.widget.Editor textarea, rteConfig

        # Register event listener for user interacting with the container
        # RTE fires afterNodeChange right at the end of initialisation, which mistakenly results
        # in changeEvent being called onload, which has a side-effect of making Orbeon think the RTE
        # has focus. Avoid this by only registering the changeEvent listener when the first afterNodeChange
        # event is received.
        containerId = @container.id
        registerChangeEvent = () =>
            @yuiRTE.on "editorKeyUp", () => @changeEvent()
            @yuiRTE.on "afterNodeChange", () => @changeEvent()
            @yuiRTE.on "editorWindowFocus", () => Events.focus { target: @container }
            @yuiRTE.on "editorWindowBlur", () => Events.blur { target: @container }
            @yuiRTE.removeListener "afterNodeChange", registerChangeEvent
        @yuiRTE.on "afterNodeChange", registerChangeEvent

        # Transform text area into RTE on the page
        @yuiRTE.on "windowRender", () =>
            # Store initial server value
            # If we don't and user's JS code calls ORBEON.xforms.Document.setValue(), the value of the RTE is changed, our RFE changeEvent() is called,
            # it sets the focus on the RTE, which calls focus(), which stores the current value (newly set) as the server value if no server value is defined.
            # Then in executeNextRequest() we ignore the value change because it is the same the server value.
            ServerValueStore.set @container.id, @getValue()
            # Fire render event
            @isRendered = true
            @renderedEvent.fire()

        @yuiRTE.render()

    # Event handler called by the RTE every time there is an event which can could potentially change the content
    # of the editor.
    changeEvent: (controlID) ->
        # Simulate keyup
        currentFocusControlId = ORBEON.xforms.Globals.currentFocusControlId
        Events.keydown { target: OD.get(currentFocusControlId) }
        Events.keyup { target: OD.get(currentFocusControlId) }

    # Called to set the value of the RTE
    setValue: (newValue) ->
        # Don't update the textarea with HTML from the server while the user is typing, otherwise the user
        # loses their cursor position. This lets us have a certain level of support for incremental rich text areas,
        # however, ignoring server values means that the visual state of the RTE can become out of sync
        # with the server value (for example, the result of a calculation wouldn't be visible until focus moved
        # out of the field).
        @onRendered () =>
            if (not YD.hasClass(@container, "xforms-incremental") || ORBEON.xforms.Globals.currentFocusControlId != @container.id)
                @yuiRTE.setEditorHTML(newValue)

    getValue: () ->
        value = @yuiRTE.getEditorHTML()
        # HACK: with Firefox, it seems that sometimes, when setting the value of the editor to "" you get"<br>" back
        # The purpose of this hack is to work around that problem. It has drawbacks:
        # o This means setting "<br>" will also result in ""
        # o This doesn't fix the root of the problem so there may be other cases not caught by this
        value = "" if value == "<br>"
        return value

    setFocus: () ->
        @yuiRTE.focus()

    # XForms readonly == RTE disabled configuration attribute.
    setReadonly: (isReadonly) ->
        @yuiRTE.set("disabled", isReadonly)

    onRendered: (callback) ->
        if @isRendered then callback() else @renderedEvent.subscribe callback

Page.registerControlConstructor RTE, (container) ->
    hasClass = (c) -> YD.hasClass container, c
    (hasClass "xforms-textarea") and (hasClass "xforms-mediatype-text-html")
ORBEON.xforms.control.RTE = RTE

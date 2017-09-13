$ = ORBEON.jQuery
$ ->
    AjaxServer = ORBEON.xforms.server.AjaxServer
    Builder = ORBEON.Builder
    Position = ORBEON.builder.Position
    SideEditor = ORBEON.builder.SideEditor
    Controls = ORBEON.xforms.Controls
    Events = ORBEON.xforms.Events
    FSM = ORBEON.util.FiniteStateMachine
    Properties = ORBEON.util.Properties

    pageX = 0; pageY = 0
    SectionTitleSelector = '.fr-section-title:first'
    SectionLabelSelector = '.fr-section-label:first a, .fr-section-label:first .xforms-output-output'

    FSM.create
        transitions : [
            { events: [ 'mouseMove'], actions: [ 'mouseMoved' ] }
        ]
        events:
            mouseMove: (f) -> ($ document).on 'mousemove', (e) -> f e
        actions:
            mouseMoved: (event) ->
                pageX = event.pageX
                pageY = event.pageY

    do setupLabelEditor = ->

        labelInput = null

        # On click on a trigger inside .fb-section-editor, send section id as a property along with the event
        AjaxServer.beforeSendingEvent.add (event, addProperties) ->
            target = $ document.getElementById event.targetId
            inSectionEditor = f$.is '*', f$.closest '.fb-section-editor', target
            if event.eventName == 'DOMActivate' && inSectionEditor
                addProperties 'section-id': f$.attr 'id', SideEditor.currentSectionOpt

        sendNewLabelValue = ->
            newLabelValue = f$.val labelInput
            labelInputOffset = Position.adjustedOffset(labelInput)
            section = Position.findInCache(SideEditor.gridSectionCache, labelInputOffset.top, labelInputOffset.left)
            f$.text newLabelValue, f$.find SectionLabelSelector, section.el
            sectionId = f$.attr 'id', section.el
            ORBEON.xforms.Document.dispatchEvent
                targetId: sectionId
                eventName: 'fb-update-section-label'
                properties: label: newLabelValue
            f$.hide labelInput

        showLabelEditor = (clickInterceptor) ->
            if ORBEON.xforms.Globals.eventQueue.length > 0 or ORBEON.xforms.Globals.requestInProgress
                _.delay (-> showLabelEditor clickInterceptor), Properties.internalShortDelay.get()
            else
                # Clear interceptor click hint, if any
                f$.text '', clickInterceptor
                # Create single input element, if we don't have one already
                unless labelInput?
                    labelInput = $ '<input class="fb-edit-section-label"/>'
                    f$.append labelInput, $ '.fb-main'
                    labelInput.on 'blur', -> if f$.is ':visible', labelInput then sendNewLabelValue()
                    labelInput.on 'keypress', (e) -> if e.which == 13 then sendNewLabelValue()
                    Events.ajaxResponseProcessedEvent.subscribe -> f$.hide labelInput
                interceptorOffset = Position.adjustedOffset clickInterceptor
                # From the section title, get the anchor element, which contains the title
                labelAnchor = do ->
                    section = Position.findInCache(SideEditor.gridSectionCache, interceptorOffset.top, interceptorOffset.left)
                    f$.find SectionLabelSelector, section.el
                # Set placeholder, done every time to account for a value change when changing current language
                do ->
                    placeholderOutput = f$.children '.fb-type-section-title-label', SideEditor.sectionEditor
                    placeholderValue = Controls.getCurrentValue placeholderOutput[0]
                    f$.attr 'placeholder', placeholderValue, labelInput
                # Populate and show input
                f$.val (f$.text labelAnchor), labelInput
                f$.show labelInput
                # Position and size input
                inputOffset =
                    top: interceptorOffset.top -
                        # Interceptor offset is normalized, so we need to remove the scrollTop when setting the offset
                        Position.scrollTop() +
                        # Vertically center input inside click interceptor
                        ((f$.height clickInterceptor) - (f$.outerHeight labelInput)) / 2
                    left: interceptorOffset.left
                f$.offset inputOffset, labelInput
                f$.offset inputOffset, labelInput   # Workaround for issue on Chrome, see https://github.com/orbeon/orbeon-forms/issues/572
                labelInput.width(clickInterceptor.width() - 10)
                f$.focus labelInput

        # Update highlight of section title, as a hint users can click to edit
        updateHighlight = (updateClass, clickInterceptor) ->
            offset = Position.adjustedOffset clickInterceptor
            section = Position.findInCache(SideEditor.gridSectionCache, offset.top, offset.left)
            if _.isUndefined(section)
                debugger
                Position.findInCache(SideEditor.gridSectionCache, offset.top, offset.left)
            sectionTitle = f$.find '.fr-section-title:first', section.el
            updateClass 'hover', sectionTitle

        # Show textual indication user can click on empty section title
        showClickHintIfTitleEmpty = (clickInterceptor) ->
            interceptorOffset = Position.adjustedOffset clickInterceptor
            section = Position.findInCache(SideEditor.gridSectionCache, interceptorOffset.top, interceptorOffset.left)
            labelAnchor = f$.find SectionLabelSelector, section.el
            if (f$.text labelAnchor) == ''
                outputWithHintMessage = SideEditor.sectionEditor.children('.fb-enter-section-title-label')
                hintMessage = Controls.getCurrentValue(outputWithHintMessage.get(0))
                clickInterceptor.text(hintMessage)

        # Create an position click interceptors
        do ->
            labelClickInterceptors = []
            Position.onOffsetMayHaveChanged ->
                sections = $('.xbl-fr-section:visible:not(.xforms-disabled)')
                # Create interceptor divs, so we have enough to cover all the sections
                _.each _.range(sections.length - labelClickInterceptors.length), ->
                    container = $ '<div class="fb-section-label-editor-click-interceptor">'
                    f$.append container, $ '.fb-main'
                    container.on 'click', ({target}) -> showLabelEditor $ target
                    container.on 'mouseover', ({target}) ->
                        updateHighlight f$.addClass, $ target
                        showClickHintIfTitleEmpty $ target
                    container.on 'mouseout', ({target}) ->
                        updateHighlight f$.removeClass, $ target
                        f$.text '', $ target
                    labelClickInterceptors.push container
                # Hide interceptors we don't need
                _.each _.range(sections.length, labelClickInterceptors.length), (pos) ->
                    labelClickInterceptors[pos].hide()
                # Position interceptor for each section
                _.each _.range(sections.length), (pos) ->
                    sectionTitle = $(sections[pos]).find(SectionTitleSelector)
                    sectionLabel = $(sections[pos]).find(SectionLabelSelector)
                    interceptor = labelClickInterceptors[pos]
                    # Show, as this might be an interceptor that was previously hidden, and is now reused
                    interceptor.show()
                    # Start at the label, but extend all the way to the right to the end of the title
                    interceptor.offset(sectionLabel.offset())
                    interceptor.height(sectionTitle.height())
                    interceptor.width(sectionTitle.width() - (sectionLabel.offset().left - sectionTitle.offset().left))

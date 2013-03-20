$ ->
    AjaxServer = ORBEON.xforms.server.AjaxServer
    Builder = ORBEON.Builder
    Controls = ORBEON.xforms.Controls
    Events = ORBEON.xforms.Events
    FSM = ORBEON.util.FiniteStateMachine
    OD = ORBEON.xforms.Document
    Properties = ORBEON.util.Properties

    sectionEditor = $ '.fb-section-editor'
    currentSection = null
    sectionsCache = []
    frBodyLeft = 0;
    pageX = 0; pageY = 0

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

    Builder.onOffsetMayHaveChanged ->
        frBodyLeft = (f$.offset $ '.fr-body').left
        sectionsCache.length = 0                                        # Don't recreate array, as other parts of the code keep a reference to it
        _.each ($ '.xbl-fr-section:visible'), (section) ->
            section = $ section
            sectionsCache.unshift
                el: section
                offset: Builder.adjustedOffset section
                height: f$.height section
                titleOffset: f$.offset f$.find 'a', section

    Builder.currentContainerChanged sectionsCache,
        wasCurrent: ->
            sectionEditor.hide()
            currentSection = null
        becomesCurrent: (section) ->
            currentSection = section.el
            # Position the editor
            do ->
                sectionEditor.show()
                sectionEditor.offset
                    top: section.offset.top - Builder.scrollTop()
                    left: frBodyLeft - f$.outerWidth sectionEditor      # Use `.fr-body` left rather than the section left to account for sub-sections indentation
            # Update trigger relevance
            do ->
                container = section.el.children '.fr-section-container'
                # Hide/show section move icons
                _.each (['up', 'right', 'down', 'left']), (direction) ->
                    relevant = container.hasClass ("fb-can-move-" + direction)
                    trigger = sectionEditor.children ('.fb-section-move-' + direction)
                    if relevant then trigger.show() else trigger.hide()
                # Hide/show delete icon
                deleteTrigger = f$.children '.delete-section-trigger', sectionEditor
                if f$.is '.fb-can-delete', container then f$.show deleteTrigger else f$.hide deleteTrigger

    do setupLabelEditor = ->

        labelInput = null

        # On click on a trigger inside .fb-section-editor, send section id as a property along with the event
        AjaxServer.beforeSendingEvent.add (event, addProperties) ->
            target = $ document.getElementById event.targetId
            inSectionEditor = f$.is '*', f$.closest '.fb-section-editor', target
            if event.eventName == 'DOMActivate' && inSectionEditor
                addProperties 'section-id': f$.attr 'id', currentSection

        sendNewLabelValue = ->
            newLabelValue = f$.val labelInput
            section = Builder.findInCache sectionsCache, (Builder.adjustedOffset labelInput).top
            f$.text newLabelValue, f$.find '.fr-section-label:first a', section.el
            sectionId = f$.attr 'id', section.el
            OD.dispatchEvent
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
                interceptorOffset = Builder.adjustedOffset clickInterceptor
                # From the section title, get the anchor element, which contains the title
                labelAnchor = do ->
                    section = Builder.findInCache sectionsCache, interceptorOffset.top
                    f$.find '.fr-section-label:first a', section.el
                # Set placeholder, done every time to account for a value change when changing current language
                do ->
                    placeholderOutput = f$.children '.fb-type-section-title-label', sectionEditor
                    placeholderValue = Controls.getCurrentValue placeholderOutput[0]
                    f$.attr 'placeholder', placeholderValue, labelInput
                # Populate and show input
                f$.val (f$.text labelAnchor), labelInput
                f$.show labelInput
                # Position and size input
                inputOffset =
                    top: interceptorOffset.top -
                        # Interceptor offset is normalized, so we need to remove the scrollTop when setting the offset
                        Builder.scrollTop() +
                        # Vertically center input inside click interceptor
                        ((f$.height clickInterceptor) - (f$.height labelInput)) / 2
                    left: interceptorOffset.left
                f$.offset inputOffset, labelInput
                f$.offset inputOffset, labelInput   # Workaround for issue on Chrome, see https://github.com/orbeon/orbeon-forms/issues/572
                f$.width (f$.width labelAnchor) - 10, labelInput
                f$.focus labelInput

        # Update highlight of section title, as a hint users can click to edit
        updateHightlight = (updateClass, clickInterceptor) ->
            offset = Builder.adjustedOffset clickInterceptor
            section = Builder.findInCache sectionsCache, offset.top
            sectionTitle = f$.find '.fr-section-title:first', section.el
            updateClass 'hover', sectionTitle

        # Show textual indication user can click on empty section title
        showClickHintIfTitleEmpty = (clickInterceptor) ->
            interceptorOffset = Builder.adjustedOffset clickInterceptor
            section = Builder.findInCache sectionsCache, interceptorOffset.top
            labelAnchor = f$.find '.fr-section-label:first a', section.el
            if (f$.text labelAnchor) == ''
                enterTitleOutput = f$.children '.fb-enter-section-title-label', sectionEditor
                enterTitleValue = Controls.getCurrentValue enterTitleOutput[0]
                f$.text enterTitleValue, clickInterceptor

        # Handle click
        do ->
            labelClickInterceptors = []
            Builder.onOffsetMayHaveChanged ->
                sections = $ '.xbl-fr-section'
                # Create interceptor divs, so we have enough to cover all the sections
                _.each _.range(sections.length - labelClickInterceptors.length), ->
                    container = $ '<div class="fb-section-label-editor-click-interceptor">'
                    f$.append container, $ '.fb-main'
                    container.on 'click', ({target}) -> showLabelEditor $ target
                    container.on 'mouseover', ({target}) ->
                        updateHightlight f$.addClass, $ target
                        showClickHintIfTitleEmpty $ target
                    container.on 'mouseout', ({target}) ->
                        updateHightlight f$.removeClass, $ target
                        f$.text '', $ target
                    labelClickInterceptors.push container
                # Hide interceptors we don't need
                _.each _.range(sections.length, labelClickInterceptors.length), (pos) ->
                    labelClickInterceptors[pos].hide()
                # Position interceptor for each section
                _.each _.range(sections.length), (pos) ->
                    title = f$.find '.fr-section-label a', $ sections[pos]
                    interceptor = labelClickInterceptors[pos]
                    interceptor.show()                                          # Might be an interceptor that was previously hidden, and is now reused
                    interceptor.offset title.offset()
                    interceptor.height title.height()
                    interceptor.width title.width()

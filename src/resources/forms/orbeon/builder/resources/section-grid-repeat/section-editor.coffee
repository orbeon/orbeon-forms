$ ->
    AjaxServer = ORBEON.xforms.server.AjaxServer
    Builder = ORBEON.Builder
    Events = ORBEON.xforms.Events
    FSM = ORBEON.util.FiniteStateMachine
    OD = ORBEON.xforms.Document
    Properties = ORBEON.util.Properties

    sectionEditor = $ '.fb-section-editor'
    currentSection = null
    sectionsCache = []
    pageX = 0; pageY = 0

    FSM.create
        transitions : [
            { events: [ 'mouseMove'    ], actions: [ 'mouseMoved' ] }
        ]
        events:
            mouseMove:    (f) -> ($ document).on 'mousemove', (e) -> f e
        actions:
            mouseMoved: (event) ->
                pageX = event.pageX
                pageY = event.pageY

    updateSectionsOffset = ->
        sectionsCache.length = 0
        _.each ($ '.xbl-fr-section:visible'), (section) ->
            section = $ section
            sectionsCache.unshift
                el: section
                offset: f$.offset section
                height: f$.height section
                titleOffset: f$.offset f$.find 'a', section
    ($ document).on 'mousemove', updateSectionsOffset                                                                   # On mousemove rather than load, as the offset changes on scroll
    Events.ajaxResponseProcessedEvent.subscribe updateSectionsOffset

    Builder.currentContainerChanged sectionsCache,
        wasCurrent: (section) ->
            sectionEditor.hide()
            currentSection = null
        becomesCurrent: (section) ->
            currentSection = section.el
            do positionEditor = ->
                sectionEditor.show()
                sectionEditor.offset
                    top: section.offset.top
                    left: (f$.offset $ '#fr-form-group').left - (f$.outerWidth sectionEditor)
            do updateTriggerRelevance = ->
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
            OD.setValue (f$.attr 'id', $ '.fb-section-new-label'), newLabelValue
            section = Builder.findInCache sectionsCache, (f$.offset labelInput).top
            f$.text newLabelValue, f$.find '.fr-section-label:first a', section.el
            OD.dispatchEvent (f$.attr 'id', section.el), 'fb-update-section-label'
            f$.hide labelInput

        showLabelEditor = (clickInterceptor) ->
            if ORBEON.xforms.Globals.eventQueue.length > 0 or ORBEON.xforms.Globals.requestInProgress
                _.delay (-> showLabelEditor clickInterceptor), Properties.internalShortDelay.get()
            else
                if not labelInput?
                    labelInput = $ '<input class="fb-edit-section-label"/>'
                    f$.append labelInput, $ document.body
                    labelInput.on 'blur', -> if f$.is ':visible', labelInput then sendNewLabelValue()
                    labelInput.on 'keypress', (e) -> if e.charCode == 13 then sendNewLabelValue()
                    Events.ajaxResponseProcessedEvent.subscribe -> f$.hide labelInput
                offset = f$.offset clickInterceptor
                labelAnchor = do ->
                    section = Builder.findInCache sectionsCache, offset.top
                    f$.find '.fr-section-label:first a', section.el
                do setInputContent = ->
                    labelText = f$.text labelAnchor
                    labelInput.val labelText
                f$.show labelInput
                do positionSizeInput = ->
                    offset.top += ((f$.height clickInterceptor) - (f$.height labelInput)) / 2
                    f$.width (f$.width labelAnchor) - 10, labelInput
                f$.focus labelInput
                f$.offset offset, labelInput

        updateHightlight = (updateClass, clickInterceptor) ->
            offset = f$.offset clickInterceptor
            section = Builder.findInCache sectionsCache, offset.top
            sectionTitle = f$.find '.fr-section-title:first', section.el
            updateClass 'hover', sectionTitle

        do setupLabelClickInterceptor = ->

            labelClickInterceptors = []
            positionLabelClickInterceptors = ->
                sections = $ '.xbl-fr-section'
                _.each _.range(sections.length - labelClickInterceptors.length), ->                                     # Create interceptors, so we have enough to cover all the sections
                    container = $ '<div class="fb-section-label-editor-click-interceptor">'
                    f$.append container, $ document.body
                    container.on 'click', ({target}) -> showLabelEditor $ target
                    container.on 'mouseover', ({target}) -> updateHightlight f$.addClass, $ target
                    container.on 'mouseout', ({target}) -> updateHightlight f$.removeClass, $ target
                    labelClickInterceptors.push container
                _.each _.range(sections.length, labelClickInterceptors.length), (pos) ->                                # Hide interceptors we don't need
                    labelClickInterceptors[pos].hide()
                _.each _.range(sections.length), (pos) ->                                                               # Position interceptor for each section
                    title = f$.find '.fr-section-label a', $ sections[pos]
                    interceptor = labelClickInterceptors[pos]
                    interceptor.offset title.offset()
                    interceptor.height title.height()
                    interceptor.width title.width()
            Events.orbeonLoadedEvent.subscribe positionLabelClickInterceptors
            ($ window).resize positionLabelClickInterceptors
            Events.ajaxResponseProcessedEvent.subscribe positionLabelClickInterceptors

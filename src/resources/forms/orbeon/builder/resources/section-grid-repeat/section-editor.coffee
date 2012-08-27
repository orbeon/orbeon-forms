$ ->
    Builder = ORBEON.Builder
    OD = ORBEON.xforms.Document
    FSM = ORBEON.util.FiniteStateMachine
    Events = ORBEON.xforms.Events
    Properties = ORBEON.util.Properties

    sectionEditor = $ '.fb-section-editor'
    currentSection = null
    sectionsCache = []
    pageX = 0; pageY = 0

    FSM.create
        transitions : [
            { events: [ 'mouseMove'    ], actions: [ 'mouseMoved' ] }
            { events: [ 'ajaxResponse' ], actions: [ 'domUpdated'  ] }
        ]
        events:
            mouseMove:    (f) -> ($ document).on 'mousemove', (e) -> f e
            ajaxResponse: (f) -> Events.ajaxResponseProcessedEvent.subscribe f
        actions:
            mouseMoved: (event) ->
                updateSectionsOffset() if sectionsCache.length == 0
                pageX = event.pageX
                pageY = event.pageY
            domUpdated: ->
                updateSectionsOffset()

    updateSectionsOffset = ->
        sectionsCache.length = 0
        _.each ($ '.xbl-fr-section'), (section) ->
            section = $ section
            sectionsCache.unshift
                element: section
                offset: f$.offset section
                height: f$.height section
                titleOffset: f$.offset f$.find 'a', section

    Builder.currentContainerChanged sectionsCache,
        (section) ->
            sectionEditor.hide()
            currentSection = null
        (section) ->
            currentSection = section.element
            do positionEditor = ->
                sectionEditor.show()
                sectionEditor.offset
                    top: section.offset.top - f$.scrollTop f$.closest '.yui-layout-bd', $ '#fr-view'
                    left: (f$.offset $ '#fr-form-group').left - (f$.outerWidth sectionEditor)
            do updateTriggerRelevance = ->
                container = section.element.children '.fr-section-container'
                _.each (['up', 'right', 'down', 'left']), (direction) ->
                    relevant = container.hasClass ("fb-can-move-" + direction)
                    trigger = sectionEditor.children ('.fb-section-move-' + direction)
                    if relevant then trigger.show() else trigger.hide()

    do setupLabelEditor = ->

        labelInput = null

        Events.clickEvent.subscribe setCurrentSection = ({target}) ->
            if f$.is '*', f$.closest '.fb-section-editor', $ target
                OD.dispatchEvent currentSection[0].id, 'fb-set-current-section'

        sendNewLabelValue = ->
            newLabelValue = f$.val labelInput
            OD.setValue (f$.attr 'id', $ '.fb-section-new-label'), newLabelValue
            section = Builder.findInCache sectionsCache, (f$.offset labelInput).top
            f$.text newLabelValue, f$.find '.fr-section-label:first a', section.element
            OD.dispatchEvent (f$.attr 'id', section.element), 'fb-update-section-label'
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
                    f$.find '.fr-section-label:first a', section.element
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
            sectionTitle = f$.find '.fr-section-title:first', section.element
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

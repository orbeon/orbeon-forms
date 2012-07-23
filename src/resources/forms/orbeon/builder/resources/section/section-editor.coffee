OD = ORBEON.xforms.Document
FSM = ORBEON.util.FiniteStateMachine
Events = ORBEON.xforms.Events

$ ->
    sectionEditor = $ '.fb-section-editor'
    currentSection = null
    sections = null
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
                updateSectionsOffset() if not sections?
                pageX = event.pageX
                pageY = event.pageY
                updateEditor()
            domUpdated: ->
                updateSectionsOffset()
                updateEditor()

    updateSectionsOffset = () ->
        sections = []
        _.each ($ '.xbl-fr-section'), (section) ->
            section = $ section
            sections.unshift
                element: section
                offset: f$.offset section
                height: f$.height section

    updateEditor = ->

        becomesCurrentSection = ->
            do positionEditor = ->
                sectionEditor.show()
                sectionEditor.offset
                    top: currentSection.offset().top
                    left: (f$.offset $ '#fr-form-group').left - (f$.outerWidth sectionEditor)
            do updateTriggerRelevance = ->
                container = currentSection.children '.fr-section-container'
                _.each (['up', 'right', 'down', 'left']), (direction) ->
                    relevant = container.hasClass ("fb-can-move-" + direction)
                    trigger = sectionEditor.children ('.fb-section-move-' + direction)
                    if relevant then trigger.show() else trigger.hide()

        wasCurrentSection = ->
            sectionEditor.hide()
            currentSection = null

        viewPos = do ->
            view = $ '.fr-view'
            left = (f$.offset view).left
            left: left
            right: left + (f$.width view)
        if viewPos.left <= pageX <= viewPos.right
            newSection = _.find sections, (section) ->
                sectionPos = do ->
                    top = section.offset.top
                    top: top
                    bottom: top + section.height
                sectionPos.top <= pageY <= sectionPos.bottom
        if newSection?
            if newSection.element != currentSection
                wasCurrentSection() if currentSection?
                currentSection = newSection.element
                becomesCurrentSection()
        else
            wasCurrentSection() if currentSection?


    Events.clickEvent.subscribe (event) ->
        target = $ event.target
        conditionToEvents = [[
            -> f$.is '*', f$.closest '.fb-section-editor', target
            ['fb-set-current-section']
        ], [
            -> (f$.is 'a', target) and (f$.is '.fr-section-label', f$.parent target)
            ['fb-set-current-section', 'fb-show-section-label-editor']
        ]]
        _.each conditionToEvents, ([condition, events]) ->
            if condition()
                _.each events, (e) -> OD.dispatchEvent currentSection[0].id, e

$ = ORBEON.jQuery
$ ->
    AjaxServer = ORBEON.xforms.server.AjaxServer
    Builder = ORBEON.Builder
    Position = ORBEON.builder.Position
    Controls = ORBEON.xforms.Controls
    Events = ORBEON.xforms.Events
    FSM = ORBEON.util.FiniteStateMachine

    Properties = ORBEON.util.Properties

    sectionEditor = $ '.fb-section-editor'
    currentSection = null
    sectionsCache = []
    fbMainCache = []
    frBodyLeft = 0;
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

    Position.onOffsetMayHaveChanged ->
        frBodyLeft = (f$.offset $ '.fr-body').left
        sectionsCache.length = 0                                        # Don't recreate array, as other parts of the code keep a reference to it
        _.each ($ '.xbl-fr-section:visible'), (section) ->
            section = $(section)
            mostOuterSection = section.parents(".xbl-fr-section").last()
            titleAnchor = section.find("a")
            if not mostOuterSection.is("*")
                # If we haven't found a parent section, `section` is already most outer section
                mostOuterSection = section
            sectionsCache.unshift
                el: section
                top:  Builder.adjustedOffset(section).top
                left: Builder.adjustedOffset(mostOuterSection).left
                height: titleAnchor.height()
                width : mostOuterSection.width()
                titleOffset: titleAnchor.offset()
        fbMainCache.length = 0
        fbMain = $(".fb-main-inner")
        fbMainOffset = Builder.adjustedOffset(fbMain)
        fbMainCache.unshift({
            el         : fbMain
            top        : fbMainOffset.top
            left       : fbMainOffset.left
            height     : f$.height fbMain
            width      : f$.width  fbMain
        })

    Builder.currentContainerChanged sectionsCache,
        wasCurrent: ->
            # NOP, instead we hide the section editor when the pointer leaves `.fb-main`
        becomesCurrent: (section) ->
            currentSection = section.el
            # Position the editor
            do ->
                sectionEditor.show()
                sectionEditor.offset
                    top: section.top - Builder.scrollTop()
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

    Builder.currentContainerChanged fbMainCache,
        wasCurrent: ->
            sectionEditor.hide()
            currentSection = null
        becomesCurrent: -> # NOP

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
            labelInputOffset = Builder.adjustedOffset(labelInput)
            section = Position.findInCache(sectionsCache, labelInputOffset.top, labelInputOffset.left)
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
                interceptorOffset = Builder.adjustedOffset clickInterceptor
                # From the section title, get the anchor element, which contains the title
                labelAnchor = do ->
                    section = Position.findInCache(sectionsCache, interceptorOffset.top, interceptorOffset.left)
                    f$.find SectionLabelSelector, section.el
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
                        ((f$.height clickInterceptor) - (f$.outerHeight labelInput)) / 2
                    left: interceptorOffset.left
                f$.offset inputOffset, labelInput
                f$.offset inputOffset, labelInput   # Workaround for issue on Chrome, see https://github.com/orbeon/orbeon-forms/issues/572
                labelInput.width(clickInterceptor.width() - 10)
                f$.focus labelInput

        # Update highlight of section title, as a hint users can click to edit
        updateHighlight = (updateClass, clickInterceptor) ->
            offset = Builder.adjustedOffset clickInterceptor
            section = Position.findInCache(sectionsCache, offset.top, offset.left)
            if _.isUndefined(section)
                debugger
                Position.findInCache(sectionsCache, offset.top, offset.left)
            sectionTitle = f$.find '.fr-section-title:first', section.el
            updateClass 'hover', sectionTitle

        # Show textual indication user can click on empty section title
        showClickHintIfTitleEmpty = (clickInterceptor) ->
            interceptorOffset = Builder.adjustedOffset clickInterceptor
            section = Position.findInCache(sectionsCache, interceptorOffset.top, interceptorOffset.left)
            labelAnchor = f$.find SectionLabelSelector, section.el
            if (f$.text labelAnchor) == ''
                outputWithHintMessage = sectionEditor.children('.fb-enter-section-title-label')
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

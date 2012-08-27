$ ->
    Builder = ORBEON.Builder
    Events = ORBEON.xforms.Events

    viewPos = left: 0, right: 0; do ->
        updateViewPos = ->
            view = $ '.fr-view'
            viewPos.left = (f$.offset view).left
            viewPos.right = viewPos.left + (f$.width view)
        f$.on 'load resize', updateViewPos, $ window

    mousePos = left: 0, top: 0; do ->
        ($ document).on 'mousemove', (event) ->
            scrollContainer = f$.closest '.yui-layout-bd', $ '#fr-view'
            mousePos.left = event.pageX
            mousePos.top = event.pageY + f$.scrollTop scrollContainer

    Builder.findInCache = (containerCache, top) ->
        _.find containerCache, (container) ->
            containerTop = container.offset.top
            sectionBottom = containerTop + container.height
            containerTop <= top <= sectionBottom

    Builder.currentContainerChanged = (containerCache, wasCurrentContainer, becomesCurrentContainer) ->

        currentContainer = null

        checkContainerChange = ->
            if viewPos.left <= mousePos.left <= viewPos.right
                newContainer = Builder.findInCache containerCache, mousePos.top
            if newContainer?
                if currentContainer is null or newContainer.element  != currentContainer.element
                    wasCurrentContainer currentContainer if currentContainer?
                    currentContainer = newContainer
                    becomesCurrentContainer currentContainer
            else
                wasCurrentContainer currentContainer if currentContainer?
                currentContainer = null

        ($ document).on 'mousemove', checkContainerChange
        Events.ajaxResponseProcessedEvent.subscribe checkContainerChange
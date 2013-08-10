$ ->

    # Change DOM as we expect the server to sent it to us, when implemented
    # - LHHA shown as tooltip have the xforms-appearance-tooltip class
    _.each $('.fr-body .xforms-control'), (control) ->
        control = $(control)
        selectorAppearance = _.pairs
            '.xforms-hint': 'xforms-appearance-tooltip'
            '.xforms-help': 'xforms-appearance-popover'
        _.each selectorAppearance, ([selector, appearance]) ->
            el = control.find(selector)
            el.addClass('xforms-hidden')
            el.addClass(appearance)

    # Keep track of whether a tooltip is shown and this is because a control got the focus
    # - used to diactivate hiding/showing tooltip based on mouse movements
    tooltipShownBecauseOfFocus = false

    $('.fr-body').on 'mouseenter mouseleave focusin focusout', '.xforms-control', (event) ->
        controlEl = $(event.target).closest('.xforms-control')
        lhhaEls = controlEl.find('.xforms-appearance-tooltip, .xforms-appearance-popover')
        controlHasTooltipPopover = lhhaEls.is('*')
        if controlHasTooltipPopover
            TooltipPopover.init(controlEl, lhhaEls)
            if ! tooltipShownBecauseOfFocus
                if event.type == 'mouseenter' then TooltipPopover.show(controlEl)
                if event.type == 'mouseleave' then TooltipPopover.hide(controlEl, event)
            if event.type == 'focusin'
                TooltipPopover.show(controlEl)
                tooltipShownBecauseOfFocus = true
            if event.type == 'focusout'
                TooltipPopover.hide(controlEl, event)
                tooltipShownBecauseOfFocus = false

    # Facade for Bootstrap's Tooltip component
    # - ensures we don't show an already visible tooltip, which would make it "blink"
    TooltipPopover =
        DataPrefix: 'xforms-tooltip-popover-'
        Initialized: @DataPrefix + 'initialized'
        Methods: @DataPrefix + 'methods'
        _tooltipPopoverShownForControlEl: null

        # Create the underlying Bootstrap tooltip (but don't show it just yet)
        init: (controlEl, lhhaEls) ->
            alreadyInitialized = controlEl.data(@Initialized) == 'true'
            if ! alreadyInitialized
                methods = []
                _.each lhhaEls, (lhhaEl) ->
                    lhhaEl = $(lhhaEl)
                    if lhhaEl.hasClass('xforms-appearance-tooltip')
                        controlEl.tooltip
                            placement: 'bottom',
                            trigger: 'manual',
                            title: lhhaEl.text()
                        methods.push('tooltip')
                    if lhhaEl.hasClass('xforms-appearance-popover')
                        beforeLastColumn = $(controlEl).closest('td').next().is('*')
                        controlEl.popover
                            placement: if beforeLastColumn then 'right' else 'left',
                            trigger: 'manual',
                            title: controlEl.find('.xforms-label').text()
                            content: lhhaEl.text()
                        methods.push('popover')
                controlEl.data(@Methods, methods)

        # Show tooltip for control
        # - but don't show if the tooltip is alrady shown for this control
        show: (controlEl) ->
            sameControl = controlEl.is(@_tooltipPopoverShownForControlEl)
            if ! sameControl
                @_callMethods(@_tooltipPopoverShownForControlEl, 'hide') if @_tooltipPopoverShownForControlEl?
                @_callMethods(controlEl, 'show')
                @_tooltipPopoverShownForControlEl = controlEl

        # Hide tooltip for control
        # - but don't hide if the element we're going to (if any) is the same as the current one
        hide: (controlEl, event) ->
            toTargetEl = $(event.relatedTarget || event.toElement)
            toControlEl = toTargetEl.closest('.xforms-control')
            sameControl = controlEl.is(toControlEl)
            if ! sameControl
                @_callMethods(controlEl, 'hide')
                @_tooltipPopoverShownForControlEl = null

        _callMethods: (controlEl, arg) ->
            methods = controlEl.data(@Methods)
            _.each methods, (method) -> controlEl[method].call(controlEl, arg)

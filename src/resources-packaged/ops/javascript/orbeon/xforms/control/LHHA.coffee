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

    State =

        # Keep track of whether a tooltip is shown and this is because a control got the focus
        # - used to diactivate hiding/showing tooltip based on mouse movements
        tooltipShownBecauseOfFocus: false

        # Keep track of the control for which we've last shown the tooltip/popover
        # - used not to re-show tooltip/popover, which causes blinks
        # - used to be able to hide popover on mouseleave for the popover
        tooltipPopoverShownForControlEl: null

    Events =

        onControl: (event) ->
            controlEl = $(event.target).closest('.xforms-control')
            lhhaEls = controlEl.find('.xforms-appearance-tooltip, .xforms-appearance-popover')
            controlHasTooltipPopover = lhhaEls.is('*')
            if controlHasTooltipPopover
                TooltipPopover.init(controlEl, lhhaEls)
                if ! State.tooltipShownBecauseOfFocus
                    if event.type == 'mouseenter'
                        TooltipPopover.show(controlEl)
                    if event.type == 'mouseleave'
                        # Allow mouse to be on popover, e.g. to click on link
                        mouseOnPopover = $('.popover:hover').is('*')
                        TooltipPopover.hide(controlEl, event) if ! mouseOnPopover
                if event.type == 'focusin'
                    TooltipPopover.show(controlEl)
                    State.tooltipShownBecauseOfFocus = true
                if event.type == 'focusout'
                    TooltipPopover.hide(controlEl, event)
                    State.tooltipShownBecauseOfFocus = false

        onPopover: (event) ->
            # Also test on popover being shown, as by the time we get the mouseleave on the popover,
            # it might have been hidden for some other reason
            if ! State.tooltipShownBecauseOfFocus && State.tooltipPopoverShownForControlEl?
                TooltipPopover.hide(State.tooltipPopoverShownForControlEl, event)

    $('.fr-body').on('mouseenter mouseleave focusin focusout', '.xforms-control', Events.onControl)
    $('body'    ).on('mouseleave'                            , '.popover'       , Events.onPopover)

    # Facade for Bootstrap's Tooltip component
    # - ensures we don't show an already visible tooltip, which would make it "blink"
    TooltipPopover =

        DataPrefix: 'xforms-tooltip-popover-'
        Initialized: @DataPrefix + 'initialized'
        Methods: @DataPrefix + 'methods'

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
            sameControl = controlEl.is(State.tooltipPopoverShownForControlEl)
            if ! sameControl
                @_callMethods(State.tooltipPopoverShownForControlEl, 'hide') if State.tooltipPopoverShownForControlEl?
                @_callMethods(controlEl, 'show')
                State.tooltipPopoverShownForControlEl = controlEl

        # Hide tooltip for control
        # - but don't hide if the element we're going to (if any) is the same as the current one
        hide: (controlEl, event) ->
            toTargetEl = $(event.relatedTarget || event.toElement)
            toControlEl = toTargetEl.closest('.xforms-control')
            sameControl = controlEl.is(toControlEl)
            if ! sameControl
                @_callMethods(controlEl, 'hide')
                State.tooltipPopoverShownForControlEl = null

        _callMethods: (controlEl, arg) ->
            methods = controlEl.data(@Methods)
            _.each methods, (method) -> controlEl[method].call(controlEl, arg)

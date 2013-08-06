$ ->

    # Change DOM as we expect the server to sent it to us, when implemented
    # - LHHA shown as tooltip have the xforms-appearance-tooltip class
    _.each $('.fr-body .xforms-control'), (control) ->
        control = $(control)
        hintEl = control.find('.xforms-hint')
        hintEl.addClass('xforms-hidden')
        hintEl.addClass('xforms-appearance-tooltip')

    # Keep track of whether a tooltip is shown and this is because a control got the focus
    # - used to diactivate hiding/showing tooltip based on mouse movements
    tooltipShownBecauseOfFocus = false

    $('.fr-body').on 'mouseenter mouseleave focusin focusout', '.xforms-control', (event) ->
        controlEl = $(event.target).closest('.xforms-control')
        tooltipEl = controlEl.find('.xforms-appearance-tooltip')
        controlHasTooltip = tooltipEl.is('*')
        if controlHasTooltip
            Tooltip.init(controlEl, tooltipEl)
            if ! tooltipShownBecauseOfFocus
                if event.type == 'mouseenter' then Tooltip.show(controlEl)
                if event.type == 'mouseleave' then Tooltip.hide(controlEl, event)
            if event.type == 'focusin'
                Tooltip.show(controlEl)
                tooltipShownBecauseOfFocus = true
            if event.type == 'focusout'
                Tooltip.hide(controlEl, event)
                tooltipShownBecauseOfFocus = false

    # Facade for Bootstrap's show/hide
    # - ensures we don't show an already visible tooltip, which would make it "blink"
    Tooltip =
        _tooltipShownForEl: null

        # Create the underlying Bootstrap tooltip (but don't show it just yet)
        init: (controlEl, tooltipEl) ->
            tooltipAlreadyInitialized = controlEl.data('xforms-tooltip-initialized') == 'true'
            if ! tooltipAlreadyInitialized
                controlEl.tooltip
                    placement: 'bottom',
                    trigger: 'manual',
                    title: tooltipEl.text()
                controlEl.data('xforms-tooltip-initialized', 'true')

        # Show tooltip for control
        # - but don't show if the tooltip is alrady shown for this control
        show: (controlEl) ->
            sameControl = controlEl.is(@_tooltipShownForEl)
            if ! sameControl
                @_tooltipShownForEl.tooltip('hide') if @_tooltipShownForEl?
                controlEl.tooltip('show')
                @_tooltipShownForEl = controlEl

        # Hide tooltip for control
        # - but don't hide if the element we're going to (if any) is the same as the current one
        hide: (controlEl, event) ->
            toTargetEl = $(event.relatedTarget || event.toElement)
            toControlEl = toTargetEl.closest('.xforms-control')
            sameControl = controlEl.is(toControlEl)
            if ! sameControl
                controlEl.tooltip('hide')
                @_tooltipShownForEl = null

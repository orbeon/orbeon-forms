/**
 * Copyright (C) 2013 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */

(function() {

    var $ = ORBEON.jQuery;
    var Controls = ORBEON.xforms.Controls;

    /**
     * Show, update, init, or destroy the tooltip on mouseover on a hint region
     *
     * [1] In Form Builder, the tooltip is absolutely positioned inside a div.fb-hover that gets inserted inside the
     *     the cell, and which is position: relative. Thus if we don't have a width on the tooltip, the browser tries
     *     to set its width to the tooltip doesn't "come out" of the div.fb-hover, which makes it extremely narrow
     *     since the tooltip is shown all the way to the right of the cell. To avoid this, if we detect that situation,
     *     we set the container to be the parent of the div.fb-hover (which is the td).
     */
    $(document).on('mouseover', '.xforms-form .xforms-items .xforms-hint-region', function() {

        var hintRegionEl       = $(this);
        var hintHtml           = hintRegionEl.nextAll('.xforms-hint').html();
        var tooltipData        = hintRegionEl.data('tooltip');
        var haveHint           = hintHtml != '';
        var tooltipInitialized = ! _.isUndefined(tooltipData);

        // Compute placement, and don't use 'over' since tooltips don't support it
        var placement  = (function() {
            var p = Controls.getPlacement(Controls.getPosition(hintRegionEl));
            return p == 'over' ? 'bottom' : p;
        })();

        switch (true) {
            case haveHint && tooltipInitialized:
                // If already initialized:
                // - Update the message (it might have changed, e.g. if the language changed).
                // - Update the placement (it might have changed, e.g. the optimal placement might go from 'bottom'
                //   to 'top' when the user scrolls down and the control becomes closer to the top of the viewport).
                //   Also, we need to call show(), as the Bootstrap tooltip code gets the even before us, and otherwise
                //   has it already has shown the tooltip without using the updated placement.
                tooltipData.options.title = hintHtml;
                tooltipData.options.placement = placement;
                hintRegionEl.tooltip('show');
                break;
            case haveHint && ! tooltipInitialized:
                // Avoid super-narrow tooltip in Form Builder [1]
                var containerEl = (function() {
                    var parentFbHover = hintRegionEl.closest('.fb-hover');
                    return parentFbHover.is('*') ? parentFbHover.parent() : hintRegionEl;
                })();
                // Create tooltip and show right away
                hintRegionEl.tooltip({
                    title:     hintHtml,
                    html:      true,
                    animation: false,
                    placement: placement,
                    container: containerEl
                });
                hintRegionEl.on('shown', _.partial(shiftTooltipLeft, containerEl, hintRegionEl));
                hintRegionEl.tooltip('show');
                break;
            case ! haveHint && tooltipInitialized:
                // We had a tooltip, but we don't have anything for show anymore
                hintRegionEl.tooltip('destroy');
                break;
            default:
                // NOP if not initialized and we don't have a tooltip
        }
    });

    /**
     * Fixup position of tooltip element to be to the left of the checkbox/radio. Without this fixup, the tooltip is
     * shown to the left of the hint region, so it shows over the checkbox/radio.
     */
    function shiftTooltipLeft(containerEl, hintRegionEl) {
        var tooltipEl = containerEl.children('.tooltip');
        if (tooltipEl.is('.left')) {
            var offset = tooltipEl.offset();
            // Add 5px spacing between arrow and checkbox/radio
            offset.left = hintRegionEl.parent().offset().left - tooltipEl.outerWidth() - 5;
            tooltipEl.offset(offset);
        }
    }

})();

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
        var HaveHint           = 1 << 0;
        var TooltipInitialized = 1 << 1;

        switch ((hintHtml != '' ? HaveHint : 0) |
                (! _.isUndefined(tooltipData) ? TooltipInitialized : 0)) {

            case HaveHint | TooltipInitialized:
                // If already initialized, we just need to update the message
                tooltipData.options.title = hintHtml;
                break;
            case HaveHint:
                // Avoid super-narrow tooltip in Form Builder [1]
                var parentFbHover = hintRegionEl.closest('.fb-hover');
                var container = parentFbHover.is('*') ? parentFbHover.parent() : hintRegionEl;
                // Create tooltip and show right away
                hintRegionEl.tooltip({
                    title:     hintHtml,
                    html:      true,
                    animation: false,
                    placement: 'right',
                    container: container
                });
                hintRegionEl.tooltip('show');
                break;
            case TooltipInitialized:
                // We had a tooltip, but we don't have anything for show anymore
                hintRegionEl.tooltip('destroy');
                break;
            default:
                // NOP if not initialized and we don't have a tooltip
        }
    });

})();

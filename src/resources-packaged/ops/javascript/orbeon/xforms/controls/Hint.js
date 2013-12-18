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

    // Show, update, init, or destroy the tooltip on mouseover a hint region
    $(document).on('mouseover', '.xforms-form .xforms-items .xforms-hint-region', function() {

        var hintRegionEl       = $(this);
        var hintText           = hintRegionEl.nextAll('.xforms-hint').text();
        var tooltipData        = hintRegionEl.data('tooltip');
        var HaveHint           = 1 << 0;
        var TooltipInitialized = 1 << 1;

        switch ((hintText != '' ? HaveHint : 0) |
                (! _.isUndefined(tooltipData) ? TooltipInitialized : 0)) {

            case HaveHint | TooltipInitialized:
                // If already initialized, we just need to update the message
                tooltipData.options.title = hintText;
                break;
            case HaveHint:
                // Create tooltip and show right away
                hintRegionEl.tooltip({
                    title: hintText,
                    animation: false,
                    placement: 'right'
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

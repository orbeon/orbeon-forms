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
    var RepeatClassesSelector = ':not(.xforms-repeat-delimiter):not(.xforms-repeat-begin-end)';

    ORBEON.xforms.XBL.declareCompanion('fr|tabbable', {

        init: function() {

            // 2016-10-13: We use our own logic to show/hide tabs based on position as we want to be able to
            // support dynamically repeated tabs.
            $(this.container).on('click.tabbable.data-api', '[data-toggle = "tabbable"]', function (e) {

                e.preventDefault();  // don't allow anchor navigation
                e.stopPropagation(); // prevent ancestor tab handlers from running

                var newLi       = $(this).parent(RepeatClassesSelector);
                var allLis      = newLi.parent().children(RepeatClassesSelector);
                var tabPosition = newLi.prevAll(RepeatClassesSelector).length;

                if (newLi.is('.active'))
                    return;

                var allTabPanes = newLi.closest('.nav-tabs').nextAll('.tab-content').children('.tab-pane').filter(RepeatClassesSelector);
                var newTabPane  = allTabPanes[tabPosition];

                allLis.removeClass('active');
                allTabPanes.removeClass('active');

                newLi.addClass('active');
                $(newTabPane).addClass('active');
            });
        }
    });

})();



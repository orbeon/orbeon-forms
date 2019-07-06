/**
 * Copyright (C) 2019 Orbeon, Inc.
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

    var Companion = {

        init: function() {

            var container              = $(this.container);
            var select                 = container.find("select");
            var elementWithPlaceholder = container.children("[data-placeholder]")

            // Select2 wants the first option not to have a value when using a placeholder
            select.find("option").first().removeAttr("value");

            function initOrUpdatePlaceholder() {
                select.select2({
                    placeholder: elementWithPlaceholder.attr("data-placeholder")
                });
            }
            initOrUpdatePlaceholder();
            onAttributeChange(elementWithPlaceholder, "data-placeholder", initOrUpdatePlaceholder);
        }
    };

    ORBEON.xforms.XBL.declareCompanion("fr|dropdown-select1-search", Companion);
    ORBEON.xforms.XBL.declareCompanion("fr|databound-select1-search", Companion);

    function onAttributeChange(element, attributeName, listener) {
        var observer = new MutationObserver(function(mutations) {
          listener();
        });
        observer.observe(element[0], {
          attributes: true,
          attributeFilter: [attributeName]
        });
    }

})();

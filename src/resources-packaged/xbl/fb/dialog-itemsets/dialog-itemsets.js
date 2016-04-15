/**
 * Copyright (C) 2016 Orbeon, Inc.
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
    var $        = ORBEON.jQuery;
    var Document = ORBEON.xforms.Document;

    $(function() {

        // User changed the label ⇒ automatically set a corresponding value
        var labelInputSelector = '#dialog-itemsets' + XF_COMPONENT_SEPARATOR + 'dialog .fb-itemset-label-input';
        return $(document).on('change.orbeon', labelInputSelector, function(event) {

            var labelInput = $(event.target);
            // Get corresponding value input
            var valueInput = labelInput.closest('tr').find('.fb-itemset-value-input')[0];
            if ($.trim(Document.getValue(valueInput)) === '') {
                // Populate value from label
                var newValue = $.trim(labelInput.val());
                newValue = newValue.replace(new RegExp(' ', 'g'), '-');
                newValue = newValue.toLowerCase();
                // If user pressed tab, after `change` on the label input, there is a `focus` on the value input,
                // which stores the value as a server value, so if we set the value before the `focus`, the value
                // isn't sent, hence the `defer()`.
                _.defer(function() {
                    Document.setValue(valueInput, newValue);
                });
            }
        });
    });
})();

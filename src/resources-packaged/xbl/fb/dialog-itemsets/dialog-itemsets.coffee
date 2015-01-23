# Copyright (C) 2011 Orbeon, Inc.
#
# This program is free software; you can redistribute it and/or modify it under the terms of the
# GNU Lesser General Public License as published by the Free Software Foundation; either version
# 2.1 of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
# without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
# See the GNU Lesser General Public License for more details.
#
# The full text of the license is available at http://www.gnu.org/copyleft/lesser.html

$ = ORBEON.jQuery
Document = ORBEON.xforms.Document

$ ->
    $(document).on 'change.orbeon', '#dialog-itemsets' + XF_COMPONENT_SEPARATOR + 'dialog .fb-itemset-label-input', (event) ->
        label = $(event.target)                                             # User changed the label
        value = label.closest('tr').find('.fb-itemset-value-input')[0]      # Get corresponding value
        if $.trim(Document.getValue(value)) == ''                           # If user didn't already provide a value
            newValue = $.trim(label.val())                                  # Populate value from label
            newValue = newValue.replace(new RegExp(' ', 'g'), '-')
            newValue = newValue.toLowerCase()
            Document.setValue(value, newValue)

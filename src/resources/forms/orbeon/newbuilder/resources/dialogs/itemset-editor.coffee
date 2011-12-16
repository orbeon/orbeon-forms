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

$ ->
    $('#fb-form-editor\\$dialog-itemsets\\$dialog').on 'change', '.fb-itemset-label-input', (event) ->
        # User changed the label
        label = $(event.target)
        # Get corresponding value
        value = label.closest('td').find('.fb-itemset-value-input')

        if $.trim(value.val()) == ''
            $.trim(label.val())
        console.log('change', event)

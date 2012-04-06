# Copyright (C) 2012 Orbeon, Inc.
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

Builder = ORBEON.Builder
Events = ORBEON.xforms.Events

$ ->

    # Add/remove fb-hover class on the tds in the first row to only show the delete/expand icons for the current column,
    # as we do for rows in plain CSS.
    do () ->
        getToolbarTd = (gridTd) ->
            left = $(gridTd).offset().left
            $(gridTd).closest('table').find('td.fb-grid-column-toolbar-td').filter () -> $(this).offset().left == left
        Builder.mouseEntersGridTdEvent.subscribe ({gridTd}) -> getToolbarTd(gridTd).addClass('fb-hover')
        Builder.mouseExitsGridTdEvent.subscribe  ({gridTd}) -> getToolbarTd(gridTd).removeClass('fb-hover')

    # Add a class fb-grid-repeat-bottom on the td that touch the bottom of the repeat, except those in the first two columns
    # so we can add a border with CSS which is like the border we have a runtime.
    do () ->
        decorateGridRepeat = () ->
            $('.fr-grid.fr-repeat.fr-editable').each (tableIndex, table) ->
                visibleTrs = $(table).find('tbody tr:not([class^="xforms"]):not([class^=" xforms"])')       # xforms with space can be removed once bug #25 is fixed
                visibleTrs.find('td').removeClass('fb-grid-repeat-bottom')                                  # Reset: remove bottom class
                visibleTrs.last().find('td').each (tdIndex, td) ->                                          # Add on tds of the bottom tr, except the 1st which is FB's row management
                    if tdIndex != 0 then $(td).addClass('fb-grid-repeat-bottom')
                visibleTrs.each (trIndex, tr) -> $(tr).find('td[rowspan]').each (tdIndex, td) ->            # Add on td with rowspan for delete iteration
                    if trIndex + parseInt($(td).attr('rowspan')) == visibleTrs.length
                        $(td).addClass('fb-grid-repeat-bottom')
        Events.ajaxResponseProcessedEvent.subscribe decorateGridRepeat
        decorateGridRepeat()

# Copyright (C) 2013 Orbeon, Inc.
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

OD = ORBEON.util.Dom
YD = YAHOO.util.Dom

YAHOO.namespace('xbl.fr')
YAHOO.xbl.fr.WPaint = ->
ORBEON.xforms.XBL.declareClass(YAHOO.xbl.fr.WPaint, "xbl-fr-wpaint");
YAHOO.xbl.fr.WPaint.prototype =

    init: ->
        @wpaintEl = $(@container).children('.wpaint-container')
        @wpaintEl.wPaint
            drawUp: => console.log('image', @wpaintEl.wPaint('image'))

    enabled: ->
    readonly:  ->
    readwrite: ->


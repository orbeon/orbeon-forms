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

OD = ORBEON.xforms.Document
YD = YAHOO.util.Dom

YAHOO.namespace('xbl.fr')
YAHOO.xbl.fr.WPaint = ->
ORBEON.xforms.XBL.declareClass(YAHOO.xbl.fr.WPaint, "xbl-fr-wpaint");
YAHOO.xbl.fr.WPaint.prototype =

    init: ->
        @wpaintEl     = $(@container).children('.fr-wpaint-container')
        @annotationEl = $(@container).find('.fr-wpaint-annotation img')
        @imageEl      = $(@container).find('.fr-wpaint-image img')
        @imageEl.load(=> @imageUploaded())

    enabled: ->


    imageUploaded: ->
        imageSrc = @imageEl.attr('src')
        if (isEmpty(imageSrc))
            @wpaintEl.css('display', 'none')
            @wpaintEl.wPaint('clear')
        else
            @wpaintEl.css('display', 'block')
            @wpaintEl.css('width' , @imageEl.width()  + 'px')
            @wpaintEl.css('height', @imageEl.height() + 'px')
            annotationSrc = @annotationEl.attr('src')
            @wpaintEl.wPaint
                imageBg: @imageEl.attr('src')
                image: if isEmpty(annotationSrc) then null else annotationSrc
                drawUp: => @drawUp()

    # When users draw something, send it to the server right away (incremental)
    drawUp: ->
        annotationImgData = @wpaintEl.wPaint('image')
        OD.dispatchEvent
            targetId:  @container.id
            eventName: 'fr-update-annotation'
            properties: value: annotationImgData

    readonly:  ->
    readwrite: ->

isEmpty = (src) -> src.match(/spacer.gif$/)

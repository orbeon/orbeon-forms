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
        @wpaintElA    = $(@container).find('.fr-wpaint-container-a')
        @wpaintElB    = $(@container).find('.fr-wpaint-container-b')
        @wpaintElC    = $(@container).find('.fr-wpaint-container-c')
        @annotationEl = $(@container).find('.fr-wpaint-annotation .xforms-output-output')
        @imageEl      = $(@container).find('.fr-wpaint-image img')

        # Test canvas support, see http://stackoverflow.com/a/2746983/5295
        testCanvasEl = document.createElement('canvas');
        @canvasSupported = !!(testCanvasEl.getContext && testCanvasEl.getContext('2d'))
        if @canvasSupported
            # Remove the canvas used just to show the canvas isn't supported and show the image selector
            $(@container).find('.fr-wpaint-no-canvas').detach()
            $(@container).find('.xforms-upload').css('display', 'inline')
            # Register events
            @imageEl.imagesLoaded(=> @imageLoaded())
            @wpaintElC.blur      (=> @blur())

    enabled: ->

    imageLoaded: ->
        if @canvasSupported
            imageSrc = @imageEl.attr('src')
            imageIsEmpty = imageSrc.match(/spacer.gif$/)
            if (imageIsEmpty)
                @wpaintElA.css('display', 'none')
                @wpaintElC.wPaint('clear')
            else
                @wpaintElA.css('display', 'block')
                @wpaintElA.css('width' , @imageEl.width()  + 'px')
                @wpaintElB.css('padding-top', (@imageEl.height() / @imageEl.width() * 100) + '%')
                @wpaintElC.css('width',  @imageEl.width()  + 'px')
                @wpaintElC.css('height', @imageEl.height() + 'px')
                annotation = @annotationEl.text()
                @wpaintElC.wPaint
                    imageBg  : @imageEl.attr('src')
                    drawDown : => @drawDown()
                    image    : if annotation == "" then null else annotation
            # Re-register listener, as imagesLoaded() calls listener only once
            @imageEl.one('load', => @imageLoaded())

    drawDown: ->
        @wpaintElC.focus()

    # When looses focus, send drawing to the server right away (incremental)
    blur: ->
        annotationImgData = @wpaintElC.wPaint('image')
        OD.dispatchEvent
            targetId:  @container.id
            eventName: 'fr-update-annotation'
            properties: value: annotationImgData

    readonly:  ->
    readwrite: ->

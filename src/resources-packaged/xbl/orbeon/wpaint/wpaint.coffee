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

$ = ORBEON.jQuery
OD = ORBEON.xforms.Document
YD = YAHOO.util.Dom

YAHOO.namespace('xbl.fr')
YAHOO.xbl.fr.WPaint = ->
ORBEON.xforms.XBL.declareClass(YAHOO.xbl.fr.WPaint, "xbl-fr-wpaint");
YAHOO.xbl.fr.WPaint.prototype =

    init: ->
        @annotationEl = $(@container).find('.fr-wpaint-annotation img')
        @imageEl      = $(@container).find('.fr-wpaint-image img')
        @wpaintElA    = $(@container).find('.fr-wpaint-container-a')
        @wpaintElB    = $(@container).find('.fr-wpaint-container-b')
        @wpaintElC    = null

        # Test canvas support, see http://stackoverflow.com/a/2746983/5295
        testCanvasEl = document.createElement('canvas');
        @canvasSupported = !!(testCanvasEl.getContext && testCanvasEl.getContext('2d'))
        if @canvasSupported
            # Hide the canvas used just to show the canvas isn't supported and show the image selector
            # NOTE: Don't remove it as it contains an `xf:output` which needs to be there in case the FR language changes.
            $(@container).find('.fr-wpaint-no-canvas').addClass('xforms-hidden')
            $(@container).find('.xforms-upload').removeClass('xforms-hidden')
            # Register events
            @imageEl.imagesLoaded(=> @imageLoaded())

    enabled: ->

    imageLoaded: ->
        if @canvasSupported
            imageSrc = @imageEl.attr('src')
            imageIsEmpty = ! _.isNull(imageSrc.match(/spacer.gif$/))
            if (imageIsEmpty)
                @wpaintElA.addClass('xforms-hidden')
                if not _.isNull(@wpaintElC)
                    @wpaintElC.detach()
                    @wpaintElC = null
            else
                @wpaintElA.removeClass('xforms-hidden')
                @wpaintElA.css('width' , @imageEl.width()  + 'px')
                @wpaintElB.css('padding-top', (@imageEl.height() / @imageEl.width() * 100) + '%')
                # tabindex="-1" allows div to have the focus, used to send change on blur
                @wpaintElC = $('<div class="fr-wpaint-container-c" tabindex="-1"/>')
                @wpaintElB.append(@wpaintElC)
                @wpaintElC.blur(=> @blur())
                @wpaintElC.css('width',  @imageEl.width()  + 'px')
                @wpaintElC.css('height', @imageEl.height() + 'px')
                annotation = @annotationEl.attr('src')
                @wpaintElC.wPaint
                    drawDown : => @drawDown()
                    imageBg  : @imageEl.attr('src')
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

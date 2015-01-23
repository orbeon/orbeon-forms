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

$ = ORBEON.jQuery
Events = ORBEON.xforms.Events

$ ->
    # Replace spacer image to photo image
    setPlaceholderImage = () ->
        spacer = '/ops/images/xforms/spacer.gif'
        $("#fr-form-group .fb-upload img.xforms-output-output[src $= '#{spacer}']").each (index, image) ->
            prefix = image.src.substr(0, image.src.indexOf(spacer))
            image.src = prefix + '/apps/fr/style/images/silk/photo.png'

    # Initial run when the form is first loaded
    setPlaceholderImage()
    # Run again after every Ajax request
    Events.ajaxResponseProcessedEvent.subscribe setPlaceholderImage
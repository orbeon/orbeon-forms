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

# TODO: should most likel be called StringUtils rather than String, to avoid overloading String when imported
ORBEON.util.String = _.tap {}, (OString) -> _.extend OString,

    # Based on code by Fagner Brack, under MIT license
    # http://stackoverflow.com/a/9918856/5295
    replace: (text, token, newToken) ->
        index = 0
        loop
            index = text.indexOf token, index
            break if index is -1
            text = String::concat.call '',
                text.substring 0, index
                newToken
                text.substring index + token.length
            index += newToken.length
        text

    # Evaluates JavaScript which can contain return characters we need to remove
    # TODO: Why do we need to replace newline in JavaScript before it to eval()?
    eval: (jsString) ->
        jsString = OString.replace jsString, "\n", " "
        jsString = OString.replace jsString, "\r", " "
        eval jsString

    # Escape text that appears in an HTML attribute which we use in an innerHTML
    escapeForMarkup: (text) ->
        # List of characters to replace per http://stackoverflow.com/a/1091953/5295
        characters =
            '"': '&quot;'
            "'": '&apos;'
            '<': '&lt;'
            '>': '&gt;'
            '&': '&amp;'
        for from, to of characters
            text = OString.replace text, from, to
        text

    # Checks if a string ends with another string
    endsWith: (text, suffix) ->
        index = text.lastIndexOf suffix
        index isnt -1 and index + suffix.length is text.length

    normalizeSerializedHTML: (text) ->
        text.replace XFORMS_REGEXP_CR, ""

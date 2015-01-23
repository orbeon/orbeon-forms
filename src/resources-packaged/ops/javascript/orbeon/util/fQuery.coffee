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

# Functional jQuery

$ = ORBEON.jQuery
f$ = do ->
    jQueryObject = $ '<div>'
    result = {}
    for m in _.methods jQueryObject
        do (m) ->
            result[m] = (params...) ->
                o = params.pop()
                jQueryObject[m].apply o, params
    result
f$.findOrIs = (selector, jQueryObject) ->                                                                                # Like find, but includes the current element if matching
    result = f$.find selector, jQueryObject
    result = f$.add jQueryObject, result if f$.is selector, jQueryObject
    result
f$.length = (jQueryObject) -> jQueryObject.length
f$.nth = (n, jQueryObject) -> $ jQueryObject[n - 1]

# Export f$ to the top-level
this.f$ = f$
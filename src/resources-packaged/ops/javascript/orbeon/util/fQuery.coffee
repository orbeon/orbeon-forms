# Functional jQuery
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
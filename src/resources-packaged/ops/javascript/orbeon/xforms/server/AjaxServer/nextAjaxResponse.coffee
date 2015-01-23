$        = ORBEON.jQuery
AS       = -> ORBEON.xforms.server.AjaxServer
Document = -> ORBEON.xforms.Document
Events   = -> ORBEON.xforms.Events

# Returns a deferred object that gets resolved when the next Ajax response arrives
AS().nextAjaxResponse = (formId) ->
    deferred = $.Deferred()
    seqNo = -> parseInt Document().getFromClientState formId, 'sequence'
    initialSeqNo = seqNo()
    onAjaxResponse = ->
        if seqNo() == initialSeqNo + 1
            Events().ajaxResponseProcessedEvent.unsubscribe onAjaxResponse
            deferred.resolve()
    Events().ajaxResponseProcessedEvent.subscribe onAjaxResponse
    deferred
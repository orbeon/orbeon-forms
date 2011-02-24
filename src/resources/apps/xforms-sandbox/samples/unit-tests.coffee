TestManager = YAHOO.tool.TestManager
window.TestManager = TestManager

# Test pages to run
TestManager.setPages [
#    "test-underscore"
#    "test-message"
#    "test-orbeon-dom"
#    "test-xforms-controls"
#    "test-control-xhtml-area"
#    "test-deferred-client-events"
#    "test-trigger-modal"
#    "test-upload-replace-instance"
#    "test-setbasepaths"
#    "test-output-update"
#    "test-custom-mips"
#    "test-repeat"
#    "test-error-ajax"
#    "test-bug-checkbox-update"
#    "test-do-update"
#    "test-loading-indicator"
    "test-disabled-nested"
#    "test-bug-checkbox-update"
#    "test-repeat-setvalue"
#    "xbl/orbeon/accordion/accordion-unittest"
#    "xbl/orbeon/datatable/datatable-unittest"
#    "xbl/orbeon/datatable/datatable-structure-unittest"
#    "xbl/orbeon/datatable/datatable-dynamic-unittest"
#    "xbl/orbeon/autocomplete/autocomplete-unittest"
#    "xbl/orbeon/currency/currency-unittest"
#    "xbl/orbeon/button/button-unittest"
]

ORBEON.xforms.Events.orbeonLoadedEvent.subscribe () ->

    # Test page stars:
    TestManager.subscribe TestManager.TEST_PAGE_BEGIN_EVENT, (data) ->
        ORBEON.xforms.Document.setValue "page", data

    # Test page completed: update instance with results from this page
    TestManager.subscribe TestManager.TEST_PAGE_COMPLETE_EVENT, (data) ->
        ORBEON.xforms.Document.setValue "report-text", (YAHOO.tool.TestFormat.XML data.results)
        ORBEON.xforms.Document.setValue "page", data.page
        ORBEON.xforms.Document.dispatchEvent "main-model", "page-complete"

    # Set "in progress" to false when all the test ran
    TestManager.subscribe TestManager.TEST_MANAGER_COMPLETE_EVENT, (data) ->
        ORBEON.xforms.Document.setValue "in-progress", "false"

    TestManager.start()

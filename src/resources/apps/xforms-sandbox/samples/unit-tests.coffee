TestManager = YAHOO.tool.TestManager
window.TestManager = TestManager

# Test pages to run
TestManager.setPages [
    "test-bug-checkbox-update"
    "test-bug-checkbox-update"
    "test-control-xhtml-area"
    "test-custom-mips"
    "test-deferred-client-events"
    "test-dialog"
    "test-disabled-nested"
    "test-do-update"
    "test-error-ajax"
    "test-group-delimiters"
    "test-keypress"
    "test-loading-indicator"
    "test-message"
    "test-orbeon-dom"
    "test-output-update"
    "test-repeat"
    "test-repeat-setvalue"
    "test-setbasepaths"
    "test-trigger-modal"
    "test-underscore"
    "test-update-full"
    "test-upload-replace-instance"
    "test-xforms-controls"
    "xbl/orbeon/accordion/accordion-unittest"
    "xbl/orbeon/autocomplete/autocomplete-unittest"
    "xbl/orbeon/button/button-unittest"
    "xbl/orbeon/currency/currency-unittest"
    #"xbl/orbeon/datatable/datatable-dynamic-unittest"
    #"xbl/orbeon/datatable/datatable-structure-unittest"
    #"xbl/orbeon/datatable/datatable-unittest"
    "xbl/orbeon/date-picker/date-picker-unittest"
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

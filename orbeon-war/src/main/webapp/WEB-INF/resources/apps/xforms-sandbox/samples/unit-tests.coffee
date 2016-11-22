TestManager = YAHOO.tool.TestManager
window.TestManager = TestManager

# Test pages to run
pages = [
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
    "test-min-viewport-width"
    "test-orbeon-dom"
    "test-output-update"
    "test-repeat"
    "test-repeat-setvalue"
    "test-trigger-modal"
    "test-underscore"
    "test-update-full"
    "test-upload-replace-instance"
    "test-xforms-controls"
    "xbl/orbeon/accordion/accordion-unittest"
    "xbl/orbeon/autocomplete/autocomplete-unittest"
    "xbl/orbeon/button/button-unittest"
    "xbl/orbeon/currency/currency-unittest"
    "xbl/orbeon/date-picker/date-picker-unittest"
]

# The following unit tests have been taken out as they still need some work
    #"xbl/orbeon/datatable/datatable-dynamic-unittest"
    #"xbl/orbeon/datatable/datatable-structure-unittest"
    #"xbl/orbeon/datatable/datatable-unittest"

# Load pages with plain theme (faster, and required by some tests that rely on width)
pages = (page + "?orbeon-theme=plain" for page in pages)
TestManager.setPages pages

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

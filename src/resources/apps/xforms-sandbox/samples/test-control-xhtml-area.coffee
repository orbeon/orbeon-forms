OD = ORBEON.util.Dom
Document = ORBEON.xforms.Document
Assert = YAHOO.util.Assert
Page = ORBEON.xforms.Page
Test = ORBEON.util.Test

YAHOO.tool.TestRunner.add new YAHOO.tool.TestCase

    name: "HTML Area"

    assertHTML: (htmlExpectedOut) ->
        htmlActualOut = Document.getValue "xhtml-textarea"
        [htmlExpectedOut, htmlActualOut] = (YAHOO.lang.trim(s).replace(new RegExp("  +", "g"), " ") for s in [htmlExpectedOut, htmlActualOut])
        Assert.areEqual htmlExpectedOut, htmlActualOut

    # Set the value of the RTE and checks that the textarea acquires a corresponding value
    settingValue: (htmlIn, htmlOut) ->
        newlineToSpace = (s) -> s.replace (new RegExp "\n", "g"), " "
        htmlIn = newlineToSpace htmlIn
        htmlOut = newlineToSpace htmlOut
        window.setTimeout () =>
            rte = Page.getControl (OD.get "xhtml-editor-1")
            rte.onRendered () =>
                @resume () =>
                    ORBEON.util.Test.executeCausingAjaxRequest this, () =>
                        ORBEON.xforms.Document.setValue "xhtml-editor-1", htmlIn
                    , () => @assertHTML htmlOut
        , ORBEON.util.Properties.internalShortDelay.get()
        @wait()


    # Wait until both RTEs have been initialized
    # setUp() method for this test (we can't use YUI's setUp() here as it doesn't support wait/resume)
    testSetup: () ->
        rteInitialized = 0;
        [rte1, rte2] = for i in [1, 2]
            container = OD.get "xhtml-editor-" + i
            rte = Page.getControl container
            rte.onRendered () =>
                rteInitialized++
                @resume() if rteInitialized == 2
        @wait()

    # Trivial case: simple HTML just goes through
    testSimpleHTML: () ->
        simpleHTML = "Some different <b>content</b>."
        @settingValue simpleHTML, simpleHTML

    # <script> is removed
    testJSInjection: () ->
        @settingValue \
            "<div>Text to keep<script>doSomethingBad()</script></div>",
            "<div>Text to keep</div>"

    # Pasting Word HTML, which doesn't have quotes around some attributes, is parsed correctly on the server
    testWordHTML: () ->
        @settingValue "
            <p class=MsoNormal align=center
            style='margin-bottom:0in;margin-bottom:.0001pt;text-align:center;line-height:normal'><b
            style='mso-bidi-font-weight:normal'><u><span
            style='font-size:14.0pt;mso-bidi-font-size:11.0pt;mso-fareast-font-family:&quot;Times New
            Roman&quot;;mso-bidi-font-family:&quot;Times New Roman&quot;;color:#0070C0'>Project
            Description<o:p></o:p></span></u></b></p>
            ", "
            <p align=\"center\" class=\"MsoNormal\" style=\"margin-bottom:0in;margin-bottom:.0001pt;text-align:center;line-height:normal\"><b
            style=\"mso-bidi-font-weight:normal\"><u><span
            style=\"font-size:14.0pt;mso-bidi-font-size:11.0pt;mso-fareast-font-family:&quot;Times New
            Roman&quot;;mso-bidi-font-family:&quot;Times New Roman&quot;;color:#0070C0\">Project
            Description</span></u></b></p>
            "

    # Check we send the value of an RTE to the server when another RTE gets the focus
    testFocus: () ->
        [rte1, rte2] = for i in [1, 2]
            container = OD.get "xhtml-editor-" + i
            rte = Page.getControl container
            rte.yuiRTE
        sampleHtml = "Hello, World!"
        ORBEON.util.Test.executeSequenceCausingAjaxRequest this, [[
            # Initially, focus on 1st RTE, empty the content
            () -> rte1.focus(); Document.setValue "xhtml-editor-1", ""
        ], [
            # Add "Hello, " to 1st RTE, focus on second, and check the value sent to the server
            () -> rte1.execCommand "inserthtml", sampleHtml; rte2.focus()
            # Use indexOf in test as inserthtml adds a <span> in Chrome
            () -> out = Document.getValue "xhtml-textarea"; Assert.isTrue (out.indexOf sampleHtml) isnt -1
        ]]

    testAddIterationAndSetValue: () ->
        ORBEON.util.Test.executeSequenceCausingAjaxRequest this, [[
            () -> Test.click "add-iteration"
            () ->
                container = OD.get "rte-in-iteration" + XFORMS_SEPARATOR_1  + "1"
                rte = Page.getControl container
                # Delay call to getValue, which is synchronous and doesn't return the right value before RTE is rendered
                rte.onRendered => @resume(); Assert.areEqual "Inside iteration", rte.getValue()
                @wait()
        ]]


ORBEON.xforms.Events.orbeonLoadedEvent.subscribe () ->
    if parent and parent.TestManager
        parent.TestManager.load()
    else
        new YAHOO.tool.TestLogger()
        YAHOO.tool.TestRunner.run()

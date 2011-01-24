RTE = ORBEON.widgets.RTE
Document = ORBEON.xforms.Document
Assert = YAHOO.util.Assert

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
            ORBEON.widgets.RTE.onRendered ORBEON.util.Dom.get("xhtml-editor-1"), () =>
                this.resume () =>
                    ORBEON.util.Test.executeCausingAjaxRequest this, () =>
                        ORBEON.xforms.Document.setValue "xhtml-editor-1", htmlIn
                    , () => this.assertHTML htmlOut
        , ORBEON.util.Properties.internalShortDelay.get()
        this.wait()

    # Trivial case: simple HTML just goes through
    testSimpleHTML: () ->
        simpleHTML = "Some different <b>content</b>."
        this.settingValue simpleHTML, simpleHTML

    # <script> is removed
    testJSInjection: () ->
        this.settingValue \
            "<div>Text to keep<script>doSomethingBad()</script></div>",
            "<div>Text to keep</div>"

    # Pasting Word HTML, which doesn't have quotes around some attributes, is parsed correctly on the server
    testWordHTML: () ->
        this.settingValue "
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
        [rte1, rte2] = (RTE.rteEditors["xhtml-editor-" + i] for i in [1, 2])
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

ORBEON.xforms.Events.orbeonLoadedEvent.subscribe () ->
    if parent and parent.TestManager
        parent.TestManager.load()
    else
        new YAHOO.tool.TestLogger()
        YAHOO.tool.TestRunner.run()

YAHOO.tool.TestRunner.add new YAHOO.tool.TestCase

    name: "HTML Area"

    # Set the value of the RTE and checks that the textarea acquires a corresponding value
    settingValue: (htmlIn, htmlOut) ->
        newlineToSpace = (s) -> s.replace (new RegExp "\n", "g"), " "
        htmlIn = newlineToSpace htmlIn
        htmlOut = newlineToSpace htmlOut
        window.setTimeout () =>
            ORBEON.widgets.RTE.onRendered ORBEON.util.Dom.get("xhtml-editor"), () =>
                this.resume () ->
                    ORBEON.util.Test.executeCausingAjaxRequest this, () =>
                        ORBEON.xforms.Document.setValue "xhtml-editor", htmlIn
                    , () ->
                        htmlActualOut = ORBEON.xforms.Document.getValue "xhtml-textarea"
                        htmlNormalizedOut = YAHOO.lang.trim(htmlActualOut).replace(new RegExp("  +", "g"), " ")
                        YAHOO.util.Assert.areEqual htmlOut, htmlNormalizedOut
        , ORBEON.util.Properties.internalShortDelay.get()
        this.wait()

    testSimpleHTML: () ->
        simpleHTML = "Some different <b>content</b>."
        this.settingValue simpleHTML, simpleHTML

    testJSInjection: () ->
        this.settingValue \
            "<div>Text to keep<script>doSomethingBad()</script></div>",
            "<div>Text to keep</div>"

    testWordHTML: () ->
        this.settingValue '''
            <p class=MsoNormal align=center
            style='margin-bottom:0in;margin-bottom:.0001pt;text-align:center;line-height:normal'><b
            style='mso-bidi-font-weight:normal'><u><span
            style='font-size:14.0pt;mso-bidi-font-size:11.0pt;mso-fareast-font-family:&quot;Times New
            Roman&quot;;mso-bidi-font-family:&quot;Times New Roman&quot;;color:#0070C0'>Project
            Description<o:p></o:p></span></u></b></p>
            ''', '''
            <p align=\"center\" class=\"MsoNormal\" style=\"margin-bottom:0in;margin-bottom:.0001pt;text-align:center;line-height:normal\"><b
            style=\"mso-bidi-font-weight:normal\"><u><span
            style=\"font-size:14.0pt;mso-bidi-font-size:11.0pt;mso-fareast-font-family:&quot;Times New
            Roman&quot;;mso-bidi-font-family:&quot;Times New Roman&quot;;color:#0070C0\">Project
            Description</span></u></b></p>
            '''

ORBEON.xforms.Events.orbeonLoadedEvent.subscribe () ->
    if parent and parent.TestManager
        parent.TestManager.load()
    else
        new YAHOO.tool.TestLogger()
        YAHOO.tool.TestRunner.run()

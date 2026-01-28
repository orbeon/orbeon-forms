<p:config
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
>
    <p:param type="input"  name="instance"/><!-- Parameters: `app`, `form`, `form-version`, `document`, `mode` -->
    <p:param type="input"  name="data"/>    <!-- XHTML+FR+XForms                                               -->
    <p:param type="output" name="data"/>    <!-- XHTML+XForms                                                  -->

    <p:processor name="oxf:pipeline">
        <p:input  name="config"   href="unroll-form.xpl"/>
        <p:input  name="instance" href="#instance"/>
        <p:input  name="data"     href="#data"/>
        <p:output name="data"     ref="data"/>
    </p:processor>

</p:config>

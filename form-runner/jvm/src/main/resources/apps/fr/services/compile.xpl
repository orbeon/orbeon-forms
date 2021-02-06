<p:config
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner">

    <!-- Page detail (app, form, document, and mode) -->
    <p:param type="input" name="instance"/>
    <!-- XHTML+FR+XForms for the form -->
    <p:param type="input" name="data"/>
    <!-- Compiled form -->
    <p:param type="output" name="data"/>

    <!-- Unroll the form (theme, components, inclusions) -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../unroll-form.xpl"/>
        <p:input name="instance" href="#instance"/>
        <p:input name="data" href="#data"/>
        <p:output name="data" id="unrolled-form-definition"/>
    </p:processor>


    <p:processor name="fr:compiler">
        <p:input  name="instance" href="#instance"/>
        <p:input  name="data" href="#unrolled-form-definition"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>

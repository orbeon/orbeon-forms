<p:config
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
>
    <p:param type="input"  name="instance"/><!-- Original parameters: `app`, `form`, `form-version`, `document`, `mode` -->
    <p:param type="output" name="data"/>    <!-- XHTML+FR+XForms for the form, obtained from persistence layer          -->
    <p:param type="output" name="instance"/><!-- Updated parameters with actual `form-version` from the persistence     -->

    <!-- Call persistence layer to obtain XHTML+XForms -->
    <p:processor name="fr:read-form">
        <p:input  name="params"   href="#instance"/>
        <p:output name="data"     id="document"/>
        <p:output name="params"   id="updated-params" ref="instance"/>
    </p:processor>

    <!-- Handle XInclude (mainly for "resource" type of persistence) -->
    <p:processor name="oxf:xinclude">
        <p:input  name="config" href="#document"/>
        <p:output name="data"   ref="data"/>
    </p:processor>

</p:config>

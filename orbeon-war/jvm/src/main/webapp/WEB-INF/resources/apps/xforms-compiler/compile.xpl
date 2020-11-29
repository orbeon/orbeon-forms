<p:config
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param type="input" name="data"/>
    <p:param type="output" name="data"/>

    <p:processor name="oxf:xforms-compiler">
        <p:input  name="data" href="#data"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>

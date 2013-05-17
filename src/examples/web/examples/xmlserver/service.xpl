<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <p:param name="a" type="input"/>
    <p:param name="b" type="input"/>
    <p:param name="c" type="output"/>
    <p:param name="d" type="output"/>

    <p:processor name="oxf:identity">
        <p:input name="data" href="#a"/>
        <p:output name="data" ref="d"/>
    </p:processor>

    <p:processor name="oxf:identity">
        <p:input name="data" href="#b"/>
        <p:output name="data" ref="c"/>
    </p:processor>

</p:config>

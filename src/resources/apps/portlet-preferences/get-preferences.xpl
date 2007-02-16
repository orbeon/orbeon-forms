<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param name="data" type="output"/>

    <p:processor name="oxf:portlet-preferences-generator">
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>

<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param name="instance" type="input"/>

    <!--<p:processor name="oxf:null-serializer">-->
    <p:processor name="oxf:portlet-preferences-serializer">
        <p:input name="data" href="#instance"/>
    </p:processor>

</p:config>

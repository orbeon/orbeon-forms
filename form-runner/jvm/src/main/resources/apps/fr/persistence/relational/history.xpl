<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">

    <p:param name="instance" type="input"/>

    <p:processor name="fr:relational-history">
        <p:input  name="data" href="#instance"/>
    </p:processor>

</p:config>

<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:oxf="http://www.orbeon.com/oxf/processors">
    <p:param name="sub-output" type="output"/>
    <p:processor name="oxf:scope-serializer">
        <p:input name="data"><x/></p:input>
        <p:input name="config">
            <config>
                <key>key</key>
                <scope>application</scope>
            </config>
        </p:input>
    </p:processor>
    <p:processor name="oxf:identity">
        <p:input name="data"><dummy/></p:input>
        <p:output name="data" ref="sub-output"/>
    </p:processor>
</p:config>

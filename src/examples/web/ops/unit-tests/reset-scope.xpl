<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:oxf="http://www.orbeon.com/oxf/processors">
    <p:processor name="oxf:scope-serializer">
        <p:input name="data"><empty/></p:input>
        <p:input name="config">
            <config>
                <key>key</key>
                <scope>application</scope>
            </config>
        </p:input>
    </p:processor>
</p:config>
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:sql="http://orbeon.org/oxf/xml/sql"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param name="instance" type="input"/>

    <p:processor name="oxf:sql-output">
        <p:input name="config">
            <sql:config>
                <sql:connection>
                    <sql:datasource>db</sql:datasource>
                    <root>
                        <sql:execute>
                            <sql:query>
                                select blob_column from test_blob where rownum = 1
                            </sql:query>
                            <sql:results>
                                <result>
                                    <sql:row-results>
                                        <row>
                                            <sql:get-column type="xs:base64Binary" column="blob_column"/>
                                        </row>
                                    </sql:row-results>
                                </result>
                            </sql:results>
                        </sql:execute>
                    </root>
                </sql:connection>
            </sql:config>
        </p:input>
        <p:output name="output" id="file"/>
    </p:processor>

    <p:processor name="oxf:binary-serializer">
        <p:input name="data" href="#file"/>
        <p:input name="config">
            <config>
                <content-type>image/jpeg</content-type>
                <cache-control>
                    <use-local-cache>false</use-local-cache>
                </cache-control>
            </config>
        </p:input>
    </p:processor>

</p:config>

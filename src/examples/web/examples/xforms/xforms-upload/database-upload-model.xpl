<!--
    Copyright (C) 2004 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:sql="http://orbeon.org/oxf/xml/sql"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param name="instance" type="input"/>

    <p:processor name="oxf:sql">
        <p:input name="datasource" href="../../datasource-sql.xml"/>
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <sql:config>
                <sql:connection>
                    <!-- Generate document in standard format -->
                    <document xsi:type="xs:base64Binary" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xs="http://www.w3.org/2001/XMLSchema">
                        <sql:execute>
                            <sql:query>
                                select blob_column from oxf_blob_table
                                 where id = <sql:param type="xs:int" select="string(/)"/><!-- /form/image-id -->
                            </sql:query>
                            <sql:results>
                                <result>
                                    <sql:row-results>
                                        <sql:get-column type="xs:base64Binary" column="blob_column"/>
                                    </sql:row-results>
                                </result>
                            </sql:results>
                        </sql:execute>
                    </document>
                </sql:connection>
            </sql:config>
        </p:input>
        <p:output name="output" id="file"/>
    </p:processor>

    <p:processor name="oxf:http-serializer">
        <p:input name="data" href="#file"/>
        <p:input name="config">
            <config>
                <content-type>image/jpeg</content-type>
                <force-content-type>true</force-content-type>
                <cache-control>
                    <use-local-cache>false</use-local-cache>
                </cache-control>
            </config>
        </p:input>
    </p:processor>

</p:config>

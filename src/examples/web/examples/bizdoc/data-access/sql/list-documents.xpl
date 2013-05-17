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
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:odt="http://orbeon.org/oxf/xml/datatypes"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:sql="http://orbeon.org/oxf/xml/sql"
          xmlns:claim="http://orbeon.org/oxf/examples/bizdoc/claim">>

    <p:param name="document-list" type="output" schema-href="../../summary/summary-model.xsd"/>

    <!-- Return the ids of all the documents -->
    <p:processor name="oxf:sql">
        <p:input name="config">
            <sql:config>
                <sql:connection>
                    <sql:datasource>db</sql:datasource>
                    <result>
                        <sql:execute>
                            <sql:query>
                                select document_id from documents
                            </sql:query>
                            <sql:results>
                                <sql:row-results>
                                    <document-info>
                                        <document-id>
                                            <sql:get-column type="xs:string" column="document_id"/>
    <!--                                        <sql:get-column type="odt:xmlFragment" column="document"/>-->
                                        </document-id>
                                        <!-- TODO: retrieve somehow claim:last-name and claim:first-name -->
                                        <claim:last-name/>
                                        <claim:first-name/>
                                    </document-info>
                                </sql:row-results>
                            </sql:results>
                        </sql:execute>
                    </result>
                </sql:connection>
            </sql:config>
        </p:input>
        <p:output name="output" ref="document-list"/>
    </p:processor>

</p:config>

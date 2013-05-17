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
          xmlns:sql="http://orbeon.org/oxf/xml/sql">

    <p:param name="document-info" type="input" schema-href="../document-info.rng"/>

    <p:processor name="oxf:sql">
        <p:input name="data" href="#document-info"/>
        <p:input name="config">
            <sql:config>
                <sql:connection>
                    <sql:datasource>db</sql:datasource>
                    <sql:execute>
                        <sql:update>
                            insert into documents (document_id, document)
                                values (<sql:param type="xs:string" select="/*/document-id"/>,
                                        <sql:param type="odt:xmlFragment" select="/*/document/*"/>)
                        </sql:update>
                    </sql:execute>
                </sql:connection>
            </sql:config>
        </p:input>
    </p:processor>

</p:config>

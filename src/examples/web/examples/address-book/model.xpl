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
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param type="input" name="instance"/>
    <p:param type="output" name="data"/>

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="/init-database/init-database.xpl"/>
    </p:processor>

    <p:choose href="#instance">
        <p:when test="/form/action='add'">
            <p:processor name="oxf:sql">
                <p:input name="data" href="#instance"/>
                <p:input name="config">
                    <sql:config>
                        <sql:connection>
                            <sql:datasource>db</sql:datasource>

                            <sql:execute>
                                <sql:update>insert into friends values(
                                    null,
                                    <sql:parameter type="xs:string" select="/form/first"/>,
                                    <sql:parameter type="xs:string" select="/form/last"/>,
                                    <sql:parameter type="xs:string" select="/form/phone"/>
                                    )
                                </sql:update>
                            </sql:execute>
                            <status>Inserted record</status>
                        </sql:connection>
                    </sql:config>
                </p:input>
                <p:output name="output" id="status"/>
            </p:processor>
        </p:when>

        <p:when test="starts-with(/form/action, 'del-')">
            <p:processor name="oxf:sql">
                <p:input name="data" href="aggregate('id', #instance#xpointer(substring-after(/form/action, 'del-')))"/>
                <p:input name="config">
                    <sql:config>
                        <sql:connection>
                            <sql:datasource>db</sql:datasource>

                            <sql:execute>
                                <sql:update>delete from friends where id =
                                    <sql:parameter type="xs:int" select="/id"/>
                                </sql:update>
                            </sql:execute>
                            <status>Deleted record</status>
                        </sql:connection>
                    </sql:config>
                </p:input>
                <p:output name="output" id="status"/>
            </p:processor>
        </p:when>

        <p:otherwise>
            <p:processor name="oxf:identity">
                <p:input name="data">
                    <status>Read data</status>
                </p:input>
                <p:output name="data" id="status"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

    <p:processor name="oxf:sql">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <sql:config>
                <sql:connection>
                    <sql:datasource>db</sql:datasource>

                    <sql:execute>
                        <sql:query>
                            select * from friends
                        </sql:query>
                        <sql:results>
                            <friends>
                                <sql:row-results>
                                    <friend>
                                        <id>
                                            <sql:get-column type="xs:string" column="id"/>
                                        </id>
                                        <first>
                                            <sql:get-column type="xs:string" column="first"/>
                                        </first>
                                        <last>
                                            <sql:get-column type="xs:string" column="last"/>
                                        </last>
                                        <phone>
                                            <sql:get-column type="xs:string" column="phone"/>
                                        </phone>
                                    </friend>
                                </sql:row-results>
                            </friends>
                        </sql:results>
                    </sql:execute>

                </sql:connection>
            </sql:config>
        </p:input>
        <p:output name="output" id="friends"/>
    </p:processor>

    <p:processor name="oxf:identity">
        <p:input name="data" href="aggregate('root', #status, #friends)"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>

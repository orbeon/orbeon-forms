<!--
    Copyright (C) 2005 Orbeon, Inc.

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
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:xdb="http://orbeon.org/oxf/xml/xmldb"
          xmlns:xu="http://www.xmldb.org/xupdate">

    <p:processor name="oxf:xslt">
        <p:input name="data"><query/></p:input>
        <p:input name="config">
            <xdb:query collection="/db/" xsl:version="2.0" xmlns:xmldb="http://exist-db.org/xquery/xmldb">
                xquery version "1.0";
                <result>
                    {
                        let $uri as xs:string := concat('<xsl:value-of select="doc('../datasource.xml')/*/uri"/>', 'db')
                        let $user as xs:string := '<xsl:value-of select="doc('../datasource.xml')/*/username"/>'
                        let $password as xs:string := '<xsl:value-of select="doc('../datasource.xml')/*/password"/>'

                        return
                            (: If there is no admin password, set it :)
                            (if (xmldb:authenticate($uri, $user, ()))
                             then xmldb:change-user($user, $password, (), ())
                             else (),

                            (: Now create test user if not already present :)
                             if (not(xmldb:exists-user('ebruchez')))
                             then xmldb:create-user('ebruchez', 'ebruchez', 'users', concat($uri, '/orbeon/blog-example/'))
                             else ()
                            )
                    }
                </result>
            </xdb:query>
        </p:input>
        <p:output name="data" id="xmldb-query" debug="abc"/>
    </p:processor>

    <p:processor name="oxf:xmldb-query">
        <p:input name="datasource" href="../datasource.xml"/>
        <p:input name="query" href="#xmldb-query"/>
        <p:output name="data" id="result"/>
    </p:processor>

    <p:processor name="oxf:null-serializer">
        <p:input name="data" href="#result" debug="zzz"/>
    </p:processor>

</p:config>

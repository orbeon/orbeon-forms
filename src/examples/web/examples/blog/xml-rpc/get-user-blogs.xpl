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

    <p:param type="input" name="params"/>
    <p:param type="output" name="params"/>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#params"/>
        <p:input name="config">
            <xdb:query collection="/db/orbeon/blog-example/blogs" create-collection="true" xsl:version="2.0">
                xquery version "1.0";
                <params>
                    <param>
                        <value>
                            <array>
                                <data>
                                {
                                for $i in /blog[username = '<xsl:value-of select="/params/param[2]/value/string"/>']
                                return
                                    <value>
                                        <struct>
                                            <member>
                                                <name>url</name>
                                                <value>xxx/{xs:string($i/id)}</value>
                                            </member>
                                            <member>
                                                <name>blogid</name>
                                                <value>{xs:string($i/id)}</value>
                                            </member>
                                            <member>
                                                <name>blogName</name>
                                                <value>{xs:string($i/name)}</value>
                                            </member>
                                        </struct>
                                    </value>
                                }
                                </data>
                            </array>
                        </value>
                    </param>
                </params>
            </xdb:query>
        </p:input>
        <p:output name="data" id="query"/>
    </p:processor>

    <p:processor name="oxf:xmldb-query">
        <p:input name="datasource" href="../datasource.xml"/>
        <p:input name="query" href="#query"/>
        <p:output name="data" ref="params"/>
    </p:processor>

</p:config>
<!--
<params>
    <param>
        <value>
            <string>E7F0F69AB4125D811D74C797B11A4DC768E9B2BC</string>
        </value>
    </param>
    <param>
        <value>
            <string>ebruchez</string>
        </value>
    </param>
    <param>
        <value>
            <string>ebruchez</string>
        </value>
    </param>
</params>
-->
<!--
<methodResponse>
    <fault>
        <value>
            <struct>
                <member>
                    <name>faultCode</name>
                    <value>
                        <int>4</int>
                    </value>
                </member>
                <member>
                    <name>faultString</name>
                    <value>
                        <string>java.lang.Exception: java.lang.Exception: Error: User authentication failed: ewilliams</string>
                    </value>
                </member>
            </struct>
        </value>
    </fault>
</methodResponse>
-->
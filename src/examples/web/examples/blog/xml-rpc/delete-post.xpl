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

    <!-- TODO: Separate data access -->

    <!-- Dynamically build query -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#params"/>
        <p:input name="config">
            <xdb:delete xsl:version="2.0" collection="/db/orbeon/blog-example/posts">
                /post[username = '<xsl:value-of select="/params/param[3]/value/string"/>' and post-id = '<xsl:value-of select="/params/param[2]/value/string"/>']
            </xdb:delete>
        </p:input>
        <p:output name="data" id="query"/>
    </p:processor>

    <!-- Run query -->
    <p:processor name="oxf:xmldb-delete">
        <p:input name="datasource" href="../datasource.xml"/>
        <p:input name="query" href="#query"/>
    </p:processor>

    <!-- Create response -->
    <p:processor name="oxf:identity">
        <p:input name="data">
            <params>
                <param>
                    <value>
                        <boolean>1</boolean>
                    </value>
                </param>
            </params>
        </p:input>
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
            <string>B947EE52-4686-39A0-3A00-345CF9874720</string>
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
    <param>
        <value>
            <boolean>1</boolean>
        </value>
    </param>
</params>
-->

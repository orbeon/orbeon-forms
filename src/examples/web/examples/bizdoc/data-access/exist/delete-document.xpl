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
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:xdb="http://orbeon.org/oxf/xml/xmldb"
          xmlns:xu="http://www.xmldb.org/xupdate">

    <p:param name="document-id" type="input" schema-href="../document-id.rng"/>

    <!-- Dynamically build query -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#document-id"/>
        <p:input name="config">
            <xdb:delete xsl:version="2.0" collection="/db/orbeon/bizdoc-example">
                <xsl:text>/document-info[document-id = '</xsl:text>
                <xsl:value-of select="."/>
                <xsl:text>']</xsl:text>
            </xdb:delete>
        </p:input>
        <p:output name="data" id="query"/>
    </p:processor>

    <!-- Run query -->
    <p:processor name="oxf:xmldb-delete">
        <p:input name="datasource" href="datasource.xml"/>
        <p:input name="query" href="#query"/>
    </p:processor>

</p:config>

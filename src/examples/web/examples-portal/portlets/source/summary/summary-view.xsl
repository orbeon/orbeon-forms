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
<html xsl:version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:ev="http://www.w3.org/2001/xml-events"
    xmlns:xi="http://www.w3.org/2003/XInclude"
    xmlns="http://www.w3.org/1999/xhtml">

    <xsl:variable name="example-descriptor" select="/*" as="element()"/>
    <xsl:variable name="instance" select="doc('input:instance')/*" as="element()"/>

    <head>
        <title><xsl:value-of select="$example-descriptor/title"/> - Example Source Code</title>
    </head>
    <body>
        <h2>Sources Files For This Example</h2>
        <ul>
            <xsl:for-each select="$example-descriptor/source-files/file">
                <li>
                    <a href="/{$instance/example-id}/{.}"><xsl:value-of select="."/></a>
                </li>
            </xsl:for-each>
        </ul>
    </body>
</html>

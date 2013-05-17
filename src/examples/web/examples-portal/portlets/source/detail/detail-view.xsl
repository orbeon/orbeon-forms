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
<html xsl:version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:ev="http://www.w3.org/2001/xml-events"
    xmlns:xi="http://www.w3.org/2003/XInclude"
    xmlns="http://www.w3.org/1999/xhtml">

    <xsl:variable name="example-descriptor" select="/*/*[1]"/>
    <xsl:variable name="instance" select="doc('input:instance')/*"/>
    <xsl:variable name="source" select="/*/*[2]"/>

    <head>
        <title><xsl:value-of select="$example-descriptor/title"/> - Example Source Code</title>
    </head>
    <body>
        <h2>Summary</h2>
        <p>
            <a href="/{$instance/example-id}">Back to file list</a>
        </p>
        <!-- Show a single file -->
        <h2><xsl:value-of select="$instance/source-url"/></h2>
        <p class="source">
            <xsl:choose>
                <xsl:when test="$source/@content-type = 'text/plain'">
                    <pre>
                        <xsl:copy-of select="$source/text()"/>
                    </pre>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:copy-of select="$source"/>
                </xsl:otherwise>
            </xsl:choose>
        </p>
    </body>
</html>

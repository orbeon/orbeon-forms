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
        <title><xsl:value-of select="$example-descriptor/title"/> Example Source Code</title>
<!--        <xforms:model>-->
<!--            <xforms:instance>-->
<!--                <xi:include href="default-submission.xml"/>-->
<!--            </xforms:instance>-->
<!--            <xforms:submission id="main" method="post" action="/{$instance/example-id}/{$instance/source-url}"/>-->
<!--        </xforms:model>-->
    </head>
    <body>
<!--        <xforms:group ref="/form">-->
            <!-- Back link -->
            <p>
                <a href="/{$instance/example-id}">Back to file list</a>
<!--                <xforms:trigger xxforms:appearance="link" submission="main">-->
<!--                    <xforms:label>Back to file list</xforms:label>-->
<!--                    <xforms:action ev:event="DOMActivate">-->
<!--                        <xforms:setvalue ref="action">back</xforms:setvalue>-->
<!--                        <xforms:send submission="main"/>-->
<!--                    </xforms:action>-->
<!--                </xforms:trigger>-->
            </p>
            <!-- Show a single file -->
            <p>
                <xsl:copy-of select="$source"/>
            </p>
<!--        </xforms:group>-->
    </body>
</html>

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
<xhtml:html xsl:version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:xf="http://www.w3.org/2002/xforms"
    xmlns:xxf="http://orbeon.org/oxf/xml/xforms">

    <xsl:variable name="example-descriptor" select="/*/*[1]"/>
    <xsl:variable name="instance" select="/*/*[2]"/>
    <xsl:variable name="source" select="/*/xhtml:html/xhtml:body/*"/>

    <xhtml:head>
        <xhtml:title><xsl:value-of select="$example-descriptor/title"/> Example Source Code</xhtml:title>
    </xhtml:head>
    <xhtml:body>
        <xf:group ref="/form">
            <xsl:choose>
                <xsl:when test="$instance/source-url != ''">
                    <!-- Back link -->
                    <xhtml:p>
                        <xf:submit xxf:appearance="link">
                            <xf:label><img src="/images/back.png"/>Back to file list</xf:label>
                            <xf:setvalue ref="source-url"/>
                        </xf:submit>
                    </xhtml:p>
                    <!-- Show a single file -->
                    <xhtml:p>
                        <xsl:copy-of select="$source"/>
                    </xhtml:p>
                </xsl:when>
                <xsl:otherwise>
                    <!-- Show summary -->
                    <xhtml:ul>
                        <xsl:for-each select="$example-descriptor/source">
                            <xhtml:li>
                                <xf:submit xxf:appearance="link">
                                    <xf:label><xsl:value-of select="."/></xf:label>
                                    <xf:setvalue ref="source-url"><xsl:value-of select="."/></xf:setvalue>
                                    <xf:setvalue ref="html"><xsl:value-of select="@html"/></xf:setvalue>
                                </xf:submit>
                            </xhtml:li>
                        </xsl:for-each>
                    </xhtml:ul>
                </xsl:otherwise>
            </xsl:choose>
        </xf:group>
    </xhtml:body>
</xhtml:html>

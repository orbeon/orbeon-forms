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
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:saxon="http://saxon.sf.net/"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:context="java:org.orbeon.oxf.pipeline.StaticExternalContext"
    exclude-result-prefixes="xforms xxforms xs saxon xhtml f">
    
    <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>

    <xsl:template match="/">
        <static-state>
            <models>
                <xsl:apply-templates select="/xhtml:html/xhtml:head/xforms:model"/> 
            </models>
            <controls>
                <xsl:apply-templates select="//xforms:*[local-name() != 'model' and not(ancestor::xforms:*)]" mode="controls"/>
            </controls>
        </static-state>
    </xsl:template>

    <xsl:template match="xforms:submission">
        <xsl:copy>
            <xsl:copy-of select="@*"/>
            <xsl:if test="@action">
<!--                <xsl:attribute name="action" select="context:rewriteResourceURL(@action, false())"/>-->
                <xsl:attribute name="action" select="@action"/>
            </xsl:if>
        </xsl:copy>
    </xsl:template>
    
    <xsl:template match="xforms:*|xxforms:*" priority="2" mode="controls">
        <xsl:copy>
            <xsl:copy-of select="@*"/>
            <xsl:apply-templates mode="#current"/>
        </xsl:copy>
    </xsl:template>
    
    <xsl:template match="*" priority="1" mode="controls">
        <xsl:apply-templates select="*" mode="#current"/>
    </xsl:template>

    <xsl:template match="text()" priority="1" mode="controls">
        <xsl:if test="parent::xforms:alert | parent::xforms:hint | parent::xforms:help 
                | parent::xforms:label | parent::xforms:value | parent::xforms:setvalue">
            <xsl:value-of select="."/>
        </xsl:if>
    </xsl:template>
    
</xsl:stylesheet>

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
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:bpws="http://schemas.xmlsoap.org/ws/2003/03/business-process/"
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">


    <xsl:template match="/">
        <p:config>
            <xsl:apply-templates select="/bpws:process/bpws:variables"/>
            <xsl:apply-templates select="/bpws:process/bpws:sequence/*"/>
        </p:config>
    </xsl:template>

    <xsl:template match="bpws:variables">
        <p:processor name="oxf:bpel-variables">
            <p:input name="config">
                <xsl:copy-of select="."/>
            </p:input>
        </p:processor>
    </xsl:template>

    <xsl:template match="bpws:assign">
        <p:processor name="oxf:bpel-assign">
            <p:input name="config">
                <xsl:copy-of select="."/>
            </p:input>
        </p:processor>
    </xsl:template>

    <xsl:template match="bpws:invoke">
        <p:processor name="oxf:bpel-invoke">
            <p:input name="config">
                <xsl:copy-of select="."/>
            </p:input>
        </p:processor>
    </xsl:template>

    <xsl:template match="bpws:switch">
        <p:choose href="aggregate('dummy')">
            <xsl:apply-templates/>
        </p:choose>
    </xsl:template>

    <xsl:template match="bpws:case">
        <p:when test="{@condition}">
            <xsl:apply-templates/>
        </p:when>
    </xsl:template>

    <xsl:template match="bpws:otherwise">
        <p:otherwise>
            <xsl:apply-templates/>
        </p:otherwise>
    </xsl:template>

</xsl:stylesheet>

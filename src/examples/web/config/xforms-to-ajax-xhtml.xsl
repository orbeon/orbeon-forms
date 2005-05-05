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
    exclude-result-prefixes="xforms xxforms xs saxon xhtml f">

    <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
    <xsl:output name="xml" method="xml"/>

    <xsl:variable name="request" as="element()" 
        select="doc('input:request')/xxforms:event-request"/>
    <xsl:variable name="response" as="element()" 
        select="doc('input:response')/xxforms:event-response"/>
    
    <!-- - - - - - - Form with hidden divs - - - - - - -->
    
    <xsl:template match="xhtml:body">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xhtml:form>
                <!-- Store private information used by the client-side JavaScript -->
                <xhtml:div title="xforms-private" style="display: none">
                    <xhtml:div title="models">
                        <xsl:value-of select="$request/xxforms:models"/>
                    </xhtml:div>
                    <xhtml:div title="controls">
                        <xsl:value-of select="$request/xxforms:controls"/>
                    </xhtml:div>
                    <xhtml:div title="instances">
                        <xsl:value-of select="$response/xxforms:instances"/>
                    </xhtml:div>
                </xhtml:div>
                <xhtml:div xhtml:title="xforms-loading" style="visibility: hidden">
                    <img src="/images/loading.gif" style="float: left"/>
                    Loading...
                </xhtml:div>
                <xsl:apply-templates/>
            </xhtml:form>
        </xsl:copy>
    </xsl:template>
    
    <!-- - - - - - - XForms controls - - - - - - -->
    
    <xsl:template match="xforms:output">
        <xhtml:span>
            <xsl:call-template name="copy-attributes"/>
            <xsl:value-of select="xxforms:control-value(@id)"/>
        </xhtml:span>
    </xsl:template>
    
    <xsl:template match="xforms:trigger">
        <xhtml:button type="button" onclick="xformsFireEvent('DOMActivate', '{@id}', this.form); return false;">
            <xsl:value-of select="xforms:label"/>
        </xhtml:button>
    </xsl:template>
    
    <xsl:template match="xforms:input">
        <xhtml:input type="text" name="{@id}" value="">
            <xsl:call-template name="copy-attributes"/>
        </xhtml:input>
    </xsl:template>

    <xsl:template match="xforms:secret">
        <xhtml:input type="password" name="{@id}" value="">
            <xsl:call-template name="copy-attributes"/>
        </xhtml:input>
    </xsl:template>

    <xsl:template match="xforms:textarea">
        <xhtml:textarea name="{@xxforms:id}" value="">
            <xsl:call-template name="copy-attributes"/>
        </xhtml:textarea>
    </xsl:template>

    <!-- - - - - - - XForms containers - - - - - - -->

    <xsl:template match="xforms:group|xforms:switch">
        <xsl:variable name="attributes" as="attribute()*">
            <xsl:call-template name="copy-attributes"/>
        </xsl:variable>
        <xsl:choose>
            <xsl:when test="count($attributes) > 0">
                <xhtml:span>
                    <xsl:copy-of select="$attributes"/>
                    <xsl:apply-templates/>
                </xhtml:span>
            </xsl:when>
            <xsl:otherwise>
                <xsl:apply-templates/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    
    <xsl:template match="xforms:case">
        <!-- FIXME: use reponse to figure out what case are displayed -->
        <xhtml:div>
            <xsl:call-template name="copy-attributes"/>
            <xsl:attribute name="style" select="concat('display: ', 
                if (position() = 1) then 'block' else 'none')"/>
            <xsl:apply-templates/>
        </xhtml:div>
    </xsl:template>
    
    <!-- - - - - - - Utility templates and functions - - - - - - -->

    <xsl:template match="xforms:*"/>
    
    <xsl:template name="copy-attributes">
        <xsl:if test="@id"><xsl:copy-of select="@id"/></xsl:if>
        <xsl:for-each select="@xhtml:*">
            <xsl:attribute name="{local-name}" select="."/>
        </xsl:for-each>
    </xsl:template>
    
    <xsl:function name="xxforms:control-value" as="xs:string">
        <xsl:param name="id" as="xs:string"/>
        <xsl:variable name="value-element" as="element()" 
            select="$response/xxforms:control-values/xxforms:control[@id = $id]"/>
        <xsl:value-of select="$value-element/@value"/>
    </xsl:function>
    
</xsl:stylesheet>

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
            <xhtml:form class="xforms-form">
                <!-- Store private information used by the client-side JavaScript -->
                <xhtml:input type="hidden" name="$models" value="{$request/xxforms:models}"/>
                <xhtml:input type="hidden" name="$controls" value="{$request/xxforms:controls}"/>
                <xhtml:input type="hidden" name="$instances" value="{$response/xxforms:instances}"/>
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
        <xsl:apply-templates select="xforms:label"/>
        <xhtml:span>
            <xsl:copy-of select="xxforms:copy-attributes(., ())"/>
            <xsl:value-of select="xxforms:control-value(@id)"/>
        </xhtml:span>
        <xsl:apply-templates select="xforms:hint | xforms:help"/>
    </xsl:template>
    
    <xsl:template match="xforms:trigger">
        <xhtml:button type="button" class="trigger">
            <xsl:copy-of select="xxforms:copy-attributes(., ())"/>
            <xsl:value-of select="xforms:label"/>
        </xhtml:button>
        <xsl:apply-templates select="xforms:hint | xforms:help"/>
    </xsl:template>
    
    <xsl:template match="xforms:input">
        <xsl:apply-templates select="xforms:label"/>
        <xhtml:input type="text" name="{@id}" value="{xxforms:control-value(@id)}">
            <xsl:copy-of select="xxforms:copy-attributes(., ())"/>
        </xhtml:input>
        <xsl:apply-templates select="xforms:hint | xforms:help"/>
    </xsl:template>

    <xsl:template match="xforms:secret">
        <xsl:apply-templates select="xforms:label"/>
        <xhtml:input type="password" name="{@id}" value="{xxforms:control-value(@id)}">
            <xsl:copy-of select="xxforms:copy-attributes(., ())"/>
        </xhtml:input>
        <xsl:apply-templates select="xforms:hint | xforms:help"/>
    </xsl:template>

    <xsl:template match="xforms:textarea">
        <xsl:apply-templates select="xforms:label"/>
        <xhtml:textarea name="{@id}">
            <xsl:copy-of select="xxforms:copy-attributes(., ())"/>
            <xsl:value-of select="xxforms:control-value(@id)"/>
        </xhtml:textarea>
        <xsl:apply-templates select="xforms:hint | xforms:help"/>
    </xsl:template>
    
    <xsl:template match="xforms:hint | xforms:label | xforms:help">
        <xhtml:label for="{../@id}">
            <xsl:variable name="class" select="concat('xforms-', local-name())"/>
            <xsl:copy-of select="xxforms:copy-attributes(., ($class))"/>
            <xsl:value-of select="."/>
        </xhtml:label>
    </xsl:template>

    <!-- - - - - - - XForms containers - - - - - - -->

    <xsl:template match="xforms:group|xforms:switch">
        <xsl:variable name="attributes" as="attribute()*">
            <xsl:copy-of select="xxforms:copy-attributes(., ())"/>
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
            <xsl:copy-of select="xxforms:copy-attributes(., ())"/>
            <xsl:attribute name="style" select="concat('display: ', 
                if (position() = 1) then 'block' else 'none')"/>
            <xsl:apply-templates/>
        </xhtml:div>
    </xsl:template>
    
    <!-- - - - - - - Utility templates and functions - - - - - - -->

    <xsl:template match="xforms:*"/>
    
    <xsl:function name="xxforms:copy-attributes">
        <xsl:param name="element" as="element()"/>
        <xsl:param name="classes" as="xs:string*"/>
        <xsl:if test="$element/@id"><xsl:copy-of select="$element/@id"/></xsl:if>
        <xsl:attribute name="class">
            <xsl:text>xforms-control</xsl:text>
            <xsl:if test="$element/@xhtml:class">
                <xsl:text> </xsl:text>
                <xsl:value-of select="$element/@xhtml:class"/>
            </xsl:if>
            <xsl:for-each select="$classes">
                <xsl:text> </xsl:text>
                <xsl:value-of select="current()"/>
            </xsl:for-each>                
        </xsl:attribute>
        <xsl:for-each select="$element/@xhtml:* except $element/@xhtml:class">
            <xsl:attribute name="{local-name()}" select="."/>
        </xsl:for-each>
    </xsl:function>
    
    <xsl:function name="xxforms:control-value" as="xs:string">
        <xsl:param name="id" as="xs:string"/>
        <xsl:variable name="value-element" as="element()" 
            select="$response/xxforms:control-values/xxforms:control[@id = $id]"/>
        <xsl:value-of select="$value-element/@value"/>
    </xsl:function>
    
</xsl:stylesheet>

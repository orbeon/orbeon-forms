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

    <xsl:template match="xhtml:body">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xhtml:form>
                <!-- Store private information used by the client-side JavaScript -->
                <xhtml:div title="xforms-private" style="display: none">
                    <xhtml:div title="models">
                        <xsl:variable name="models">
                            <xxforms:models>
                                <xsl:copy-of select="//xforms:model"/>
                            </xxforms:models>
                        </xsl:variable>
                        <xsl:value-of select="saxon:serialize($models, 'xml')"/>
                    </xhtml:div>
                    <xhtml:div title="controls">
                        <xsl:variable name="controls">
                            <xxforms:controls>
                                <xsl:apply-templates select="//xforms:*[local-name() != 'model' and not(ancestor::xforms:*)]" mode="keep-xforms-only"/>
                            </xxforms:controls>
                        </xsl:variable>
                        <xsl:value-of select="saxon:serialize($controls, 'xml')"/>
                    </xhtml:div>
                    <xhtml:div title="instances">
                        <xsl:variable name="instances">
                            <xxforms:instances>
                                <xsl:for-each select="//xforms:model/xforms:instance">
                                    <xxforms:instance>
                                        <xsl:if test="../@id">
                                            <xsl:attribute name="model-id" select="../@id"/>
                                        </xsl:if>
                                        <xsl:if test="@id">
                                            <xsl:attribute name="id" select="@id"/>
                                        </xsl:if>
                                        <xsl:copy-of select="*"/>
                                    </xxforms:instance>
                                </xsl:for-each>
                            </xxforms:instances>
                        </xsl:variable>
                        <xsl:value-of select="saxon:serialize($instances, 'xml')"/>
                    </xhtml:div>
                </xhtml:div>
                <div xhtml:title="xforms-loading" style="visibility: hidden">
                    <img src="/images/loading.gif" style="float: left"/>
                    Loading...
                </div>
                <xsl:apply-templates/>
            </xhtml:form>
        </xsl:copy>
    </xsl:template>
    
    <xsl:template match="xforms:output">
        <xsl:value-of select="xforms:label"/>
        <xhtml:span id="{count(preceding::*)}">0</xhtml:span>
    </xsl:template>
    
    <xsl:template match="xforms:trigger">
        <xhtml:button type="button" onclick="xformsFireEvent('DOMActivate', {count(preceding::*)}, this.form); return false;">
            <xsl:value-of select="xforms:label"/>
        </xhtml:button>
    </xsl:template>
    
    <xsl:template match="xforms:switch">
        <xsl:for-each select="xforms:case">
            <xhtml:div id="{@id}">
                <xsl:attribute name="style" select="concat('display: ', 
                    if (position() = 1) then 'block' else 'none')"/>
                <xsl:apply-templates/>
            </xhtml:div>
        </xsl:for-each>
    </xsl:template>
    
    <xsl:template match="xforms:*"/>
    
    <xsl:template match="xforms:*" mode="keep-xforms-only" priority="2">
        <xsl:copy>
            <xsl:attribute name="xxforms:id">
                <xsl:value-of select="count(preceding::*)"/>
            </xsl:attribute>
            <xsl:copy-of select="@*"/>
            <xsl:apply-templates mode="keep-xforms-only"/>
        </xsl:copy>
    </xsl:template>
    
    <xsl:template match="*" mode="keep-xforms-only" priority="1">
        <xsl:apply-templates select="*" mode="keep-xforms-only"/>
    </xsl:template>

    <xsl:template match="text()" mode="keep-xforms-only" priority="1">
        <xsl:if test="parent::xforms:*">
            <xsl:value-of select="node()"/>
        </xsl:if>
    </xsl:template>
    
</xsl:stylesheet>

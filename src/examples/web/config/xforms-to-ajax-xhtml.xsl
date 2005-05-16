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

    <xsl:variable name="repeats" as="element()*"
        select="$response/xxforms:action/xxforms:repeats/xxforms:repeat" />

    <!-- - - - - - - Form with hidden divs - - - - - - -->
    
    <xsl:template match="xhtml:body">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xhtml:form class="xforms-form">
                <!-- Store private information used by the client-side JavaScript -->
                <xhtml:input type="hidden" name="$static-state" value="{$request/xxforms:static-state}"/>
                <xhtml:input type="hidden" name="$dynamic-state" value="{$response/xxforms:dynamic-state}"/>
                <xhtml:span class="xforms-loading-loading"/>
                <xhtml:span class="xforms-loading-error"/>
                <xhtml:span class="xforms-loading-none"/>
                <xsl:apply-templates/>
            </xhtml:form>
        </xsl:copy>
    </xsl:template>
    
    <!-- - - - - - - XForms controls - - - - - - -->
    
    <xsl:template match="xforms:output | xforms:trigger | xforms:input 
            | xforms:secret | xforms:textarea" priority="2">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
        <xsl:if test="local-name() != 'trigger'">
            <xsl:apply-templates select="xforms:label"/>
        </xsl:if>
        <xsl:next-match/>
        <xsl:apply-templates select="xforms:help"/>
        <xhtml:label for="{$id}">
            <xsl:variable name="alert-id" as="xs:string" select="concat(@id, $id-postfix)"/>
            <xsl:copy-of select="xxforms:copy-attributes(xforms:alert, concat('xforms-alert-', 
                if (xxforms:control($id)/@valid = 'false') then 'active' else 'inactive'), $alert-id)"/>
            <xsl:value-of select="xxforms:control($id)/@alert"/>
        </xhtml:label>
        <xsl:apply-templates select="xforms:hint"/>
    </xsl:template>
    
    <xsl:template match="xforms:output">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
        <xhtml:span>
            <xsl:copy-of select="xxforms:copy-attributes(., 'xforms-control', $id)"/>
            <xsl:value-of select="xxforms:control($id)"/>
        </xhtml:span>
    </xsl:template>
    
    <xsl:template match="xforms:trigger">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
        <xhtml:button type="button" class="trigger">
            <xsl:copy-of select="xxforms:copy-attributes(., 'xforms-control', $id)"/>
            <xsl:value-of select="xforms:label"/>
        </xhtml:button>
    </xsl:template>
    
    <xsl:template match="xforms:input">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
        <xhtml:input type="text" name="{$id}" value="{xxforms:control($id)}">
            <xsl:copy-of select="xxforms:copy-attributes(., 'xforms-control', $id)"/>
        </xhtml:input>
    </xsl:template>

    <xsl:template match="xforms:secret">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
        <xhtml:input type="password" name="{$id}" value="{xxforms:control($id)}">
            <xsl:copy-of select="xxforms:copy-attributes(., 'xforms-control', $id)"/>
        </xhtml:input>
    </xsl:template>

    <xsl:template match="xforms:textarea">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
        <xhtml:textarea name="{$id}">
            <xsl:copy-of select="xxforms:copy-attributes(., 'xforms-control', $id)"/>
            <xsl:value-of select="xxforms:control($id)"/>
        </xhtml:textarea>
    </xsl:template>
    
    <xsl:template match="xforms:select">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
        <xhtml:span>
            <xsl:copy-of select="xxforms:copy-attributes(., ('xforms-control', 'xforms-checkboxes'), $id)"/>
            <xsl:for-each select="xforms:item">
                <xhtml:input type="checkbox" value="{xforms:value}">
                    <xsl:copy-of select="xxforms:copy-attributes(., 'xforms-checkbox', ())"/>
                    <xsl:if test="xforms:value = xxforms:control(../@id)">
                        <xsl:attribute name="checked">checked</xsl:attribute>
                    </xsl:if>
                </xhtml:input>
                <xsl:value-of select="xforms:label"/>
            </xsl:for-each>
        </xhtml:span>
    </xsl:template>

    <xsl:template match="xforms:select1">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
        <xhtml:select name="{$id}">
            <xsl:copy-of select="xxforms:copy-attributes(., 'xforms-control', $id)"/>
            <xsl:for-each select="xforms:item">
                <xhtml:option value="{xforms:value}">
                    <xsl:if test="xforms:value = xxforms:control($id)">
                        <xsl:attribute name="selected">selected</xsl:attribute>
                    </xsl:if>
                    <xsl:value-of select="xforms:label"/>
                </xhtml:option>
            </xsl:for-each>
        </xhtml:select>            
    </xsl:template>

    <xsl:template match="xforms:label | xforms:hint | xforms:help">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:variable name="parent-id" as="xs:string" select="concat(../@id, $id-postfix)"/>
        <xsl:variable name="current-id" as="xs:string" select="concat(@id, $id-postfix)"/>
        <xhtml:label for="{$parent-id}">
            <xsl:copy-of select="xxforms:copy-attributes(., concat('xforms-', local-name()), $current-id)"/>
            <xsl:variable name="value-element" as="element()" select="xxforms:control($parent-id)"/>
            <xsl:value-of select="if (local-name() = 'label') then $value-element/@label
                else if (local-name() = 'hint') then $value-element/@hint
                else $value-element/@help"/>
        </xhtml:label>
    </xsl:template>

    <!-- - - - - - - XForms repeat - - - - - - -->

    <xsl:template match="xforms:repeat">
        <xsl:param name="current-repeats" select="$repeats" tunnel="yes"/>
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>

        <xsl:variable name="xforms-repeat" select="." as="element()"/>
        <xsl:variable name="current-repeat" select="$current-repeats[@id = $xforms-repeat/@id]" as="element()"/>

        <xsl:for-each select="(1 to $current-repeat/@occurs)">
            <xsl:apply-templates select="$xforms-repeat/*">
                <xsl:with-param name="current-repeats" select="$current-repeat/xxforms:repeat[current()]" tunnel="yes"/>
                <xsl:with-param name="id-postfix" select="concat($id-postfix, '-', current())" tunnel="yes"/>
            </xsl:apply-templates>
        </xsl:for-each>
    </xsl:template>

    <!-- - - - - - - XForms containers - - - - - - -->

    <xsl:template match="xforms:group|xforms:switch">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
        <xhtml:span>
            <xsl:copy-of select="xxforms:copy-attributes(., concat('xforms-', local-name()), $id)"/>
            <xsl:apply-templates/>
        </xhtml:span>
    </xsl:template>
    
    <xsl:template match="xforms:case">
        <xsl:param name="id-postfix" select="''" tunnel="yes"/>
        <xsl:variable name="id" select="concat(@id, $id-postfix)"/>
        <!-- FIXME: use reponse to figure out what case are displayed -->
        <xhtml:span>
            <xsl:copy-of select="xxforms:copy-attributes(., 'xforms-case', $id)"/>
            <xsl:variable name="div-information" as="element()"
                select="$response/xxforms:action/xxforms:divs/xxforms:div[@id = $id]"/>
            <xsl:attribute name="style" select="concat('display: ', 
                if ($div-information/@visibility = 'visible') then 'block' else 'none')"/>
            <xsl:apply-templates/>
        </xhtml:span>
    </xsl:template>
    
    <!-- - - - - - - Utility templates and functions - - - - - - -->

    <xsl:template match="xforms:*"/>
    
    <xsl:function name="xxforms:copy-attributes">
        <xsl:param name="element" as="element()?"/>
        <xsl:param name="classes" as="xs:string*"/>
        <xsl:param name="id" as="xs:string?"/>
        <!-- Handle id attribute -->
        <xsl:if test="$id">
            <xsl:attribute name="id" select="$id"/>
        </xsl:if>
        <!-- Copy attributes with no namespaces -->
        <xsl:copy-of select="$element/@accesskey | $element/@tabindex | $element/@style"/>
        <!-- Convert navindex to tabindex -->
        <xsl:if test="$element/@navindex">
            <xsl:attribute name="tabindex" select="$element/@navindex"/>
        </xsl:if>
        <!-- Copy class attribute, both in xhtml namespace and no namespace -->
        <xsl:variable name="class" as="xs:string" select="string-join
            (($element/@xhtml:class, $element/@class, $classes, 
            if ($element/@incremental = 'true') then 'xforms-incremental' else ()), ' ')"/>
        <xsl:attribute name="class" select="$class"/>
        <!-- Copy attributes in the xhtml namespace to no namespace -->
        <xsl:for-each select="$element/@xhtml:* except $element/@xhtml:class">
            <xsl:attribute name="{local-name()}" select="."/>
        </xsl:for-each>
    </xsl:function>
    
    <xsl:function name="xxforms:control" as="element()">
        <xsl:param name="id" as="xs:string"/>
        <xsl:variable name="control" 
            select="$response/xxforms:action/xxforms:control-values/xxforms:control[@id = $id]"/>
        <xsl:if test="not($control)">
            <xsl:message terminate="yes">Can't find control with id = '<xsl:value-of select="$id"/>'</xsl:message>
        </xsl:if>
        <xsl:sequence select="$control"/>
    </xsl:function>
    
</xsl:stylesheet>

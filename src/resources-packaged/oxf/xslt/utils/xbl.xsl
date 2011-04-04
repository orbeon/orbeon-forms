<!--
    Copyright (C) 2009 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:xbl="http://www.w3.org/ns/xbl"
        xmlns:xxbl="http://orbeon.org/oxf/xml/xbl">

    <xsl:function name="xxbl:parameter">
        <xsl:param name="context" as="element()"/>
        <xsl:param name="property" as="xs:string"/>

        <xsl:variable name="prefix" select="prefix-from-QName(node-name($context))"/>
        <xsl:variable name="namespace" select="namespace-uri($context)"/>
        <xsl:variable name="component" select="local-name($context)"/>

        <xsl:variable name="prefix" select="prefix-from-QName(node-name($context))"/>
        <xsl:variable name="namespace" select="namespace-uri($context)"/>
        <xsl:variable name="component" select="local-name($context)"/>

        <xsl:choose>
            <xsl:when test="exists($context/*[local-name() = $property and namespace-uri() = $namespace])">
                <!-- Parameter bound to a node -->
                <!-- Create an input field with all the binding attributes of the nested element, i.e. fr:foo/fr:bar/@ref -->
                <xxforms:variable name="{$property}">
                    <xxforms:sequence xxbl:attr="{$prefix}:{$property}/(@model | @context | @ref | @bind)" select="." xxbl:scope="outer"/>
                </xxforms:variable>
                <xforms:input ref="${$property}" class="xbl-{$prefix}-{$component}-{$property}" style="display: none">
                    <xxforms:script ev:event="xforms-value-changed">
                        <xsl:text>ORBEON.xforms.XBL.callValueChanged("</xsl:text>
                        <xsl:value-of select="$prefix"/>
                        <xsl:text>", "</xsl:text>
                        <xsl:value-of select="xxbl:to-camel-case($component)"/>
                        <xsl:text>", "</xsl:text>
                        <xsl:value-of select="xxbl:to-camel-case($property)"/>
                        <xsl:text>");</xsl:text>
                    </xxforms:script>
                </xforms:input>
            </xsl:when>
            <xsl:otherwise>
                <!-- Parameter is constant -->
                <!-- NOTE: We have a "default" value in the variable so we can detect the difference between the attribute value being the empty string vs. the attribute not being there -->
                <xxforms:variable name="{$property}-orbeon-xbl" xbl:attr="xbl:text={$property}">&#xb7;</xxforms:variable>
                <xxforms:variable name="{$property}" select="if (${$property}-orbeon-xbl != '&#xb7;') then ${$property}-orbeon-xbl else xxforms:property('oxf.xforms.xbl.{$prefix}.{$component}.{$property}')"/>
                <xforms:output class="xbl-{$prefix}-{$component}-{$property}" style="display: none" value="${$property}"/>
            </xsl:otherwise>
        </xsl:choose>

    </xsl:function>

    <!-- Converts a name such as my-grand-name into MyGrandName -->
    <xsl:function name="xxbl:to-camel-case" as="xs:string">
        <xsl:param name="dash-separated-name" as="xs:string"/>

        <xsl:variable name="result">
            <xsl:for-each select="tokenize($dash-separated-name, '-')">
                <xsl:value-of select="concat(upper-case(substring(., 1, 1)), substring(., 2))"/>
            </xsl:for-each>
        </xsl:variable>
        <xsl:value-of select="string-join($result, '')"/>
    </xsl:function>

</xsl:stylesheet>

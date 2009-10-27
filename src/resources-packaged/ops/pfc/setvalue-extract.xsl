<!--
    Copyright (C) 2005-2007 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<xsl:stylesheet version="2.0"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:c="http://www.orbeon.com/oxf/controller"
    xmlns:function="http://www.orbeon.com/xslt-function">

    <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
    <xsl:import href="oxf:/oxf/xslt/utils/evaluate.xsl"/>

    <!-- Inputs -->
    <xsl:variable name="instance" select="/" as="document-node()"/>
    <xsl:variable name="setvalues" select="doc('input:setvalues')/*/(c:param | c:setvalue)" as="element()*"/>
    <xsl:variable name="matcher-results" select="doc('input:matcher-result')/*/group" as="element()*"/>
    <xsl:variable name="request-info" select="doc('input:request-info')/*" as="element()"/>

    <!-- Nodes pointed to by setvalues -->
    <!-- FIXME: Somehow we get a list of nodes from function:evaluate(); the last one appears to be the one we are looking for -->
    <xsl:variable name="param-nodes" as="node()*"
                  select="for $i in $setvalues return function:evaluate($instance, $i/@ref, $i/namespace::node()[name() != ''])[last()]"/>

    <xsl:variable name="param-nodes-ids" as="xs:string*"
                  select="for $i in $param-nodes return generate-id($i)"/>

    <xsl:template match="/">
        <xsl:apply-templates/>
    </xsl:template>

    <!-- Case of element -->
    <xsl:template match="*[generate-id() = $param-nodes-ids]">
        <xsl:variable name="value" select="function:getvalue(.)" as="xs:string?"/>
        <xsl:choose>
            <xsl:when test="exists($value)">
                <xsl:copy>
                    <xsl:copy-of select="@*"/>
                    <xsl:value-of select="$value"/>
                </xsl:copy>
            </xsl:when>
            <xsl:otherwise>
                <xsl:copy>
                    <xsl:apply-templates/>
                </xsl:copy>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- Case of attribute -->
    <xsl:template match="@*[generate-id() = $param-nodes-ids]">
        <xsl:variable name="value" select="function:getvalue(.)" as="xs:string?"/>
        <xsl:choose>
            <xsl:when test="$value">
                <xsl:attribute name="{name()}">
                    <xsl:value-of select="$value"/>
                </xsl:attribute>
            </xsl:when>
            <xsl:otherwise>
                <xsl:copy/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:function name="function:getvalue" as="xs:string?">
        <xsl:param name="current" as="item()"/><!-- element or attribute -->
        <xsl:variable name="param-index" select="index-of($param-nodes-ids, generate-id($current))" as="xs:integer"/>
        <xsl:choose>
            <xsl:when test="$setvalues[$param-index]/@parameter">
                <!-- Parameter name -->
                <xsl:variable name="parameter" select="$request-info/parameters/parameter[name = $setvalues[$param-index]/@parameter]" as="element()?"/>
                <xsl:choose>
                    <!-- Set the value only if the parameter is present -->
                    <xsl:when test="$parameter">
                        <xsl:value-of select="string-join($parameter/value, ' ')"/>
                    </xsl:when>
                    <!-- Otherwise just leave whatever was there -->
                    <xsl:otherwise>
                        <xsl:copy-of select="()"/>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:when>
            <xsl:when test="$setvalues[$param-index]/@matcher-group">
                <!-- Matcher group -->
                <xsl:value-of select="$matcher-results[xs:integer($setvalues[$param-index]/@matcher-group)]"/>
            </xsl:when>
            <xsl:when test="$setvalues[$param-index]/@value">
                <!-- Value -->
                <xsl:value-of select="function:evaluate($instance, $setvalues[$param-index]/@value, $setvalues[$param-index]/namespace::node()[name() != ''])[last()]"/>
            </xsl:when>
            <xsl:when test="local-name($setvalues[$param-index]) = 'setvalue'">
                <!-- Literal value -->
                <xsl:value-of select="$setvalues[$param-index]"/>
            </xsl:when>
            <xsl:otherwise>
                <!-- Backward compatibility mode -->
                <xsl:value-of select="$matcher-results[$param-index]"/>
            </xsl:otherwise>
        </xsl:choose>

    </xsl:function>

</xsl:stylesheet>

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
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:saxon="http://saxon.sf.net/"
    xmlns:c="http://www.orbeon.com/oxf/controller"
    xmlns:function="http://www.orbeon.com/xslt-function">

    <!--
        The purpose of this transformation is to annotate an existing URL redirect configuration.

        The data input contains a URL redirect configuration with a path-info that may contain
        regexp groups identified by parenthesis. The params input contains a list of parameters of
        the form:

            <params>
                <param xmlns="http://www.orbeon.com/oxf/controller" ref="/form/x"/>
                <param xmlns="http://www.orbeon.com/oxf/controller" ref="/form/y"/>
                <param xmlns="http://www.orbeon.com/oxf/controller" ref="/form/z"/>
            </params>

        Those match the order of the groups in the path-info. They each contain an XPath expression.
        The transform replaces the groups in the path-info with the result of those XPath
        expressions applied to the XForms instance document.
    -->

    <!-- Copy template -->
    <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
    <xsl:import href="oxf:/oxf/xslt/utils/evaluate.xsl"/>

    <!-- Inputs -->
    <xsl:variable name="instance" select="document('oxf:instance')" as="document-node()"/>
    <xsl:variable name="params" select="document('oxf:params')//c:param" as="element()*"/>

    <xsl:template match="/">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="path-info">
        <xsl:copy>
            <xsl:call-template name="build-path-info">
                <xsl:with-param name="params" select="$params"/>
                <xsl:with-param name="instance" select="$instance"/>
                <xsl:with-param name="path-info" select="string(.)"/>
            </xsl:call-template>
        </xsl:copy>
    </xsl:template>

    <xsl:template name="build-path-info">
        <xsl:param name="params"/>
        <xsl:param name="instance"/>
        <xsl:param name="path-info"/>
        <xsl:choose>
            <xsl:when test="contains($path-info, '(')">
                <!-- Prepend first piece of the path -->
                <xsl:value-of select="substring-before($path-info, '(')"/>

                <!-- Extract XPath expression to point to node in instance -->
                <xsl:variable name="ref" select="$params[1]/@ref" as="xs:string"/>
<!--                <xsl:message terminate="no">1: <xsl:value-of select="$ref"/></xsl:message>-->

                <!-- Evaluate expression in the context of $instance -->
                <!--   Note: the evaluate function returns multiple nodes, not sure why, -->
                <!--   so we concatenate the string values. -->
                <xsl:variable name="param-nodes" as="node()*"
                    select="function:evaluate($instance, $ref, $params[1]/namespace::node())"/>
                <xsl:variable name="param-value" as="xs:string"
                    select="string-join(for $n in $param-nodes return string($n), '')"/>
                <xsl:value-of select="$param-value"/>
<!--                <xsl:message terminate="no">2: <xsl:value-of select="$param-value"/></xsl:message>-->

                <!-- Recurse for other regexp groups -->
                <xsl:call-template name="build-path-info">
                    <xsl:with-param name="params" select="$params[position() > 1]"/>
                    <xsl:with-param name="instance" select="$instance"/>
                    <xsl:with-param name="path-info" select="substring-after($path-info, ')')"/>
                </xsl:call-template>

            </xsl:when>
            <xsl:otherwise>
                <!-- No regexp group -->
                <xsl:value-of select="$path-info"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
</xsl:stylesheet>

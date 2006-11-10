<!--
    Copyright (C) 2005 Orbeon, Inc.

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
    xmlns:c="http://www.orbeon.com/oxf/controller"
    xmlns:local="http://orbeon.org/oxf/xml/local"
    xmlns:function="http://www.orbeon.com/xslt-function">

    <!--
        The purpose of this transformation is to reconstruct a URL based on regular expression
        groups, <setvalue> elements, and an XML submission.

        The data input contains a URL redirect configuration with a path-info that may contain
        regexp groups identified by parenthesis. The setvalues input contains a list of parameters
        of the form (legacy, for backward compatiblity):

            <setvalues>
                <param xmlns="http://www.orbeon.com/oxf/controller" ref="/form/x"/>
                <param xmlns="http://www.orbeon.com/oxf/controller" ref="/form/y"/>
                <param xmlns="http://www.orbeon.com/oxf/controller" ref="/form/z"/>
            </setvalues>

        or (new format):

            <setvalues>
                <setvalue xmlns="http://www.orbeon.com/oxf/controller" ref="/form/x" matcher-group="3"/>
                <setvalue xmlns="http://www.orbeon.com/oxf/controller" ref="/form/y" matcher-group="1"/>
                <setvalue xmlns="http://www.orbeon.com/oxf/controller" ref="/form/z" matcher-group="2"/>
                <setvalue xmlns="http://www.orbeon.com/oxf/controller" ref="/form/z" parameter="first-name"/>
            </setvalues>

        Each element contains an XPath expression. The transform replaces the groups in the
        path-info or the URL parameters with the result of those XPath expressions applied to the
        XML submission document.
    -->

    <!-- Copy template -->
    <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
    <xsl:import href="oxf:/oxf/xslt/utils/evaluate.xsl"/>

    <!-- Inputs -->
    <xsl:variable name="rewrite-config" select="/" as="document-node()"/>
    <xsl:variable name="instance" select="doc('input:instance')" as="document-node()"/>
    <xsl:variable name="setvalues" select="doc('input:setvalues')/*/(c:param | c:setvalue)" as="element()*"/>

    <xsl:variable name="parameters-setvalues" select="$setvalues[@parameter]" as="element()*"/>

    <xsl:template match="/*">

<!--        <xsl:variable name="result">-->
            <xsl:copy>
                <xsl:apply-templates/>
                <xsl:if test="not(/*/parameters)">
                    <xsl:variable name="new-parameters" as="element()">
                        <parameters/>
                    </xsl:variable>
                    <xsl:apply-templates select="$new-parameters"/>
                </xsl:if>
            </xsl:copy>
<!--        </xsl:variable>-->
<!--        <xsl:copy-of select="$result"/>-->
    </xsl:template>

    <xsl:template match="path-info">
        <!-- Handle path info -->
        <xsl:copy>
            <xsl:variable name="path-info" select="normalize-space(.)" as="xs:string"/>

            <xsl:variable name="ordered-matcher-setvalues" as="element()*">
                <xsl:perform-sort select="$setvalues[local-name(.) = 'param' or @matcher-group]">
                    <xsl:sort select="if (local-name(.) = 'param') then (count(preceding-sibling::c:param) + 1) else @matcher-group"
                        data-type="number" order="descending"/>
                </xsl:perform-sort>
            </xsl:variable>

            <xsl:variable name="ordered-matcher-group-indexes" select="for $i in $ordered-matcher-setvalues return if (local-name($i) = 'param') then (count($i/preceding-sibling::c:param) + 1) else $i/@matcher-group" as="xs:integer*"/>

            <!-- Handle matcher groups -->
            <xsl:choose>
                <xsl:when test="contains($path-info, '(') and not(empty($ordered-matcher-group-indexes))">
                    <!-- Must look for regexp groups -->
                    <xsl:variable name="matcher-groups" select="tokenize($path-info, '\([^(]+\)')" as="xs:string+"/>
                    <xsl:variable name="group-count" select="count($matcher-groups) - 1" as="xs:integer"/>

                    <xsl:choose>
                        <xsl:when test="$group-count ge 1">
                            <!-- There are groups and relevant setvalues -->

                            <xsl:variable name="result" select="local:add-groups($ordered-matcher-setvalues, $ordered-matcher-group-indexes, $matcher-groups)" as="xs:string+"/>
                            <xsl:value-of select="string-join($result, '')"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <!-- Nothing to do -->
                            <xsl:value-of select="$path-info"/>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:when>
                <xsl:otherwise>
                    <!-- Nothing to do -->
                    <xsl:value-of select="$path-info"/>
                </xsl:otherwise>
            </xsl:choose>

        </xsl:copy>
    </xsl:template>

    <xsl:template match="parameters">
        <xsl:copy>
            <xsl:apply-templates/>
            <!-- Handle all new parameters -->

            <xsl:for-each select="distinct-values($parameters-setvalues[not(@parameter = $rewrite-config/*/parameters/parameter/name)]/@parameter)">
                <xsl:variable name="current-name" select="." as="xs:string"/>
                <parameter>
                    <name><xsl:value-of select="$current-name"/></name>
                    <xsl:for-each select="$parameters-setvalues[@parameter = $current-name]">
                        <!-- Evaluate expression in the context of $instance -->
                        <!--   Note: the evaluate function returns multiple nodes, not sure why, -->
                        <!--   so we concatenate the string values. -->
                        <xsl:variable name="result-nodes" as="node()*"
                            select="function:evaluate($instance, @ref, namespace::node()[name() != ''])"/>
                        <xsl:variable name="result-value" as="xs:string"
                            select="string-join(for $n in $result-nodes return string($n), '')"/>

                        <value><xsl:value-of select="$result-value"/></value>
                    </xsl:for-each>
                </parameter>
            </xsl:for-each>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="parameters/parameter[name = $parameters-setvalues/@parameter]">
        <!-- Handle existing parameter -->
        <xsl:variable name="current-name" select="name" as="xs:string"/>

        <xsl:copy>
            <xsl:copy-of select="name|value"/>
            <xsl:for-each select="$parameters-setvalues[@parameter = $current-name]">

                <!-- Evaluate expression in the context of $instance -->
                <!--   Note: the evaluate function returns multiple nodes, not sure why, -->
                <!--   so we concatenate the string values. -->
                <xsl:variable name="result-nodes" as="node()*"
                    select="function:evaluate($instance, @ref, namespace::node()[name() != ''])"/>
                <xsl:variable name="result-value" as="xs:string"
                    select="string-join(for $n in $result-nodes return string($n), '')"/>

                <value><xsl:value-of select="$result-value"/></value>
            </xsl:for-each>

        </xsl:copy>
    </xsl:template>

    <xsl:function name="local:add-groups" as="xs:string+">
        <xsl:param name="setvalue-elements" as="element()*"/>
        <xsl:param name="matcher-group-indexes" as="xs:integer*"/>
        <xsl:param name="matcher-groups" as="xs:string+"/>

        <xsl:choose>
            <xsl:when test="count($matcher-group-indexes) ge 1">
                <!-- Remaining groups to process -->

                <!-- Evaluate expression in the context of $instance -->
                <!--   Note: the evaluate function returns multiple nodes, not sure why, -->
                <!--   so we concatenate the string values. -->
                <xsl:variable name="result-nodes" as="node()*"
                    select="function:evaluate($instance, $setvalue-elements[1]/@ref, $setvalue-elements[1]/namespace::node()[name() != ''])"/>
                <xsl:variable name="result-value" as="xs:string"
                    select="string-join(for $n in $result-nodes return string($n), '')"/>

                <xsl:copy-of select="local:add-groups(subsequence($setvalue-elements, 2), subsequence($matcher-group-indexes, 2),
                                                       insert-before($matcher-groups, $matcher-group-indexes[1] + 1, $result-value))"/>
            </xsl:when>
            <xsl:otherwise>
                <!-- Done -->
                <xsl:copy-of select="$matcher-groups"/>
            </xsl:otherwise>
        </xsl:choose>

    </xsl:function>
</xsl:stylesheet>

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
	xmlns:function="http://www.orbeon.com/xslt-function"
    xmlns:evaluator="java:org.orbeon.saxon.xpath.XPathEvaluator"
    xmlns:context="java:org.orbeon.saxon.xpath.StandaloneContext"
    xmlns:saxon="http://saxon.sf.net/">

    <!-- Evaluates an XPath expression with prefixes based on a context node.
       -
       - @param node        The context node
       - @param select      The XPath expression
       - @param namespaces  A sequence of namespaces, which prefix can be used
                            in the XPath expression
      -->
	<xsl:function name="function:evaluate">
        <xsl:param name="node" as="node()"/>
        <xsl:param name="select" as="xs:string"/>
        <xsl:param name="namespaces" as="node()*"/>

        <!-- HACK: We have to force the evaluation of $xpath here, otherwise Saxon gets confused -->
        <xsl:sequence select="if ($select) then () else ()"/>

        <xsl:variable name="evaluator" select="evaluator:new()"/>
        <xsl:value-of select="evaluator:set-context-node($evaluator, $node)"/>
        <xsl:variable name="context" select="evaluator:get-static-context($evaluator)"/>
        <xsl:for-each select="$namespaces">
            <xsl:value-of select="context:declare-namespace($context, local-name(), .)"/>
        </xsl:for-each>
        <xsl:sequence select="evaluator:evaluate($evaluator, $select)"/>
	</xsl:function>

</xsl:stylesheet>

<!--
    Copyright (C) 2006 Orbeon, Inc.

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
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xhtml="http://www.w3.org/1999/xhtml"
        xmlns:f="http//www.orbeon.com/function">

    <xsl:variable name="MAX-TOP-LEVEL" as="xs:integer" select="50"/>
    <xsl:variable name="MAX-CALLER-CALLING" as="xs:integer" select="5"/>
    <xsl:variable name="MAX-DEPTH" as="xs:integer" select="2"/>

    <!--<xsl:template match="/"><xsl:copy-of select="/*"/></xsl:template>-->
    <xsl:template match="/">
        <analysis>
            <xsl:variable name="traces" as="element(trace)*" select="/hprof/trace"/>
            <!-- All the org.orbeon calls that call something that is not org.orbeon -->
            <xsl:variable name="top-calls" as="element(call)*"
                    select="$traces/call[starts-with(@method, 'org.orbeon')
                        and count(preceding-sibling::call[starts-with(@method, 'org.orbeon')]) = 0]"/>
            <xsl:copy-of select="f:methods-by-frequency($top-calls, 1, $MAX-DEPTH)"/>
        </analysis>
    </xsl:template>

    <xsl:function name="f:methods-by-frequency">
        <xsl:param name="calls" as="element(call)*"/>
        <xsl:param name="depth" as="xs:integer"/>
        <xsl:param name="max-depth" as="xs:integer"/>
        <xsl:variable name="method-names" as="xs:string*" select="distinct-values($calls/@method)"/>
        <xsl:variable name="count-calls" as="xs:integer" select="xs:integer(sum(for $c in $calls return $c/parent::trace/@count))"/>

        <xsl:if test="count($method-names) > 0 and $max-depth >= $depth">
            <xsl:variable name="methods-unsorted" as="element(method)*">
                <xsl:for-each select="$method-names">
                    <xsl:variable name="method-name" as="xs:string" select="."/>
                    <xsl:variable name="calls-with-currrent-method" as="element(call)*" select="$calls[@method = $method-name]"/>
                    <xsl:variable name="count-calls-with-currrent-method" as="xs:integer"
                            select="xs:integer(sum(for $c in $calls-with-currrent-method return $c/parent::trace/@count))"/>
                    <method name="{$method-name}" file="{$calls-with-currrent-method[1]/@file}" line="{$calls-with-currrent-method[1]/@line}"
                            frequency="{$count-calls-with-currrent-method div $count-calls}"/>
                </xsl:for-each>
            </xsl:variable>
            <xsl:for-each select="$methods-unsorted">
                <xsl:sort select="@frequency" data-type="number" order="descending"/>
                <xsl:if test="if ($depth = 1) then $MAX-TOP-LEVEL >= position() else $MAX-CALLER-CALLING >= position()">
                    <xsl:variable name="method-name" as="xs:string" select="@name"/>
                    <xsl:variable name="calls-with-currrent-method" as="element(call)*" select="$calls[@method = $method-name]"/>
                    <xsl:copy>
                        <xsl:copy-of select="@*"/>
                        <!-- Calling: what methods to this method call -->
                        <xsl:variable name="calling" as="element(call)*"
                                select="for $c in $calls-with-currrent-method return $c/preceding-sibling::call[1]"/>
                        <xsl:variable name="calling-methods" as="element(method)*" select="f:methods-by-frequency($calling, $depth + 1, $max-depth)"/>
                        <xsl:if test="count($calling-methods) >= 1">
                            <calling>
                                <xsl:copy-of select="$calling-methods"/>
                            </calling>
                        </xsl:if>
                        <!-- Callers: who calls this method -->
                        <xsl:variable name="callers" as="element(call)*"
                                select="for $c in $calls-with-currrent-method return $c/following-sibling::call[1]"/>
                        <xsl:variable name="caller-methods" as="element(method)*" select="f:methods-by-frequency($callers, $depth + 1, $max-depth)"/>
                        <xsl:if test="count($caller-methods) >= 1">
                            <callers>
                                <xsl:copy-of select="$caller-methods"/>
                            </callers>
                        </xsl:if>
                    </xsl:copy>
                </xsl:if>
            </xsl:for-each>
        </xsl:if>
    </xsl:function>

</xsl:stylesheet>

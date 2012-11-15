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
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xh="http://www.w3.org/1999/xhtml"
        xmlns:f="http//www.orbeon.com/function">

    <xsl:template match="/">
        <xh:html>
            <xh:head>
                <xh:style type="text/css">
                    .info      { font-size: 10px; color: #aaa; }
                    .even-line { background: #ffb ; }
                    .odd-line  { background: #ddf; }
                    .label     { font-style: italic; }
                </xh:style>
            </xh:head>
            <xh:body>
                <xh:h1>Java Profiling: Hotspots</xh:h1>
                <xh:table class="gridtable">
                    <xsl:copy-of select="f:display-methods(/analysis/method, 1)"/>
                </xh:table>
            </xh:body>
        </xh:html>

    </xsl:template>

    <xsl:function name="f:display-methods">
        <xsl:param name="methods" as="element(method)*"/>
        <xsl:param name="depth" as="xs:integer"/>
        <xsl:for-each select="$methods">
            <xh:tr class="{f:tr-class($depth)}">
                <xh:td>
                    <xsl:variable name="class" as="xs:string" select="substring-before(@file, '.')"/>
                    <xsl:variable name="class-and-method" select="concat($class, substring-after(@name, $class))"/>
                    <xsl:variable name="before-class-and-method" select="substring(@name, 1, string-length(@name) - string-length($class-and-method) - 1)"/>
                    <xsl:value-of select="f:space($depth)"/>
                    <xsl:value-of select="$class-and-method"/>
                    <xh:span class="info">
                        <xsl:value-of select="concat(' (', $before-class-and-method, ')')"/>
                    </xh:span>
                </xh:td>
                <xh:td>
                    <xh:span class="info">
                        <xsl:value-of select="concat(@file, ':', @line)"/>
                    </xh:span>
                </xh:td>
                <xh:td align="right">
                    <xsl:value-of select="round(@frequency * 1000) div 10"/>
                    <xsl:text>%</xsl:text>
                </xh:td>
            </xh:tr>
            <!-- Calling: what methods to this method call -->
            <xsl:if test="calling">
                <xh:tr class="{f:tr-class($depth + 1)}">
                    <xh:td colspan="3">
                        <xh:span class="label">
                            <xsl:value-of select="concat(f:space($depth + 1), 'Calling:')"/>
                        </xh:span>
                    </xh:td>
                </xh:tr>
                <xsl:copy-of select="f:display-methods(calling/method, $depth + 1)"/>
            </xsl:if>
            <!-- Callers: who calls this method -->
            <xsl:if test="callers">
                <xh:tr class="{f:tr-class($depth + 1)}">
                    <xh:td colspan="3">
                        <xh:span class="label">
                            <xsl:value-of select="concat(f:space($depth + 1), 'Callers:')"/>
                        </xh:span>
                    </xh:td>
                </xh:tr>
                <xsl:copy-of select="f:display-methods(callers/method, $depth + 1)"/>
            </xsl:if>
        </xsl:for-each>
    </xsl:function>

    <xsl:function name="f:space">
        <xsl:param name="depth" as="xs:integer"/>
        <xsl:value-of select="string-join(for $i in 1 to ($depth - 1) * 2 return '&#160;', '')"/>
    </xsl:function>

    <xsl:function name="f:tr-class">
        <xsl:param name="depth" as="xs:integer"/>
        <xsl:value-of select="if ($depth mod 2 = 0) then 'even-line' else 'odd-line'"/>
    </xsl:function>

</xsl:stylesheet>
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
<xsl:stylesheet version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns="http://www.w3.org/1999/xhtml">

    <xsl:variable name="text-color" select="'black'"/>
    <xsl:variable name="element-color" select="'blue'"/>
    <xsl:variable name="prefix-color" select="'navy'"/>
    <xsl:variable name="attribute-name-color" select="'green'"/>
    <xsl:variable name="attribute-value-color" select="'orange'"/>
    <xsl:variable name="symbol-color" select="'black'"/>
    <xsl:variable name="comment-color" select="'gray'"/>
    <xsl:variable name="normalize-non-whitespace" select="true()"/>

    <!-- Empty element -->
    <xsl:template match="*[not(node())]" name="format-empty-element" mode="xml-formatting">
        <xsl:param name="show-namespaces" select="true()"/>
        <div class="rd">
            <xsl:text>&#160;&#160;</xsl:text>
            <xsl:call-template name="format-empty-element-inline">
                <xsl:with-param name="show-namespaces" select="$show-namespaces"/>
            </xsl:call-template>
        </div>
    </xsl:template>

    <xsl:template match="*[not(node())]" name="format-empty-element-inline" mode="xml-formatting-inline">
        <xsl:param name="show-namespaces" select="true()"/>
        <!-- Element -->
        <font color="{$symbol-color}"><xsl:text>&lt;</xsl:text></font>
        <xsl:call-template name="display-element-name">
            <xsl:with-param name="name" select="name()"/>
        </xsl:call-template>
        <xsl:call-template name="display-element-attributes">
            <xsl:with-param name="name" select="name()"/>
            <xsl:with-param name="attributes" select="@*"/>
            <xsl:with-param name="show-namespaces" select="$show-namespaces"/>
        </xsl:call-template>
        <font color="{$symbol-color}"><xsl:text>/&gt;</xsl:text></font>
    </xsl:template>

    <!-- Short text-only element -->
    <xsl:template match="*[text() and not(* | processing-instruction() | comment()) and string-length(.) &lt;= 50]" mode="xml-formatting">
        <xsl:param name="show-namespaces" select="true()"/>
        <div class="rd">
            <xsl:text>&#160;&#160;</xsl:text>
            <xsl:call-template name="format-short-text-element-inline">
                <xsl:with-param name="show-namespaces" select="$show-namespaces"/>
            </xsl:call-template>
        </div>
    </xsl:template>

    <xsl:template match="*[text() and not(* | processing-instruction() | comment()) and string-length(.) &lt;= 50]" name="format-short-text-element-inline" mode="xml-formatting-inline">
        <xsl:param name="show-namespaces" select="true()"/>
        <!-- Element start -->
        <font color="{$symbol-color}"><xsl:text>&lt;</xsl:text></font>
        <xsl:call-template name="display-element-name">
            <xsl:with-param name="name" select="name()"/>
        </xsl:call-template>
        <xsl:call-template name="display-element-attributes">
            <xsl:with-param name="name" select="name()"/>
            <xsl:with-param name="attributes" select="@*"/>
            <xsl:with-param name="show-namespaces" select="$show-namespaces"/>
        </xsl:call-template>
        <font color="{$symbol-color}"><xsl:text>&gt;</xsl:text></font>
        <!-- Nested contents -->
        <xsl:apply-templates mode="xml-formatting"/>
        <!-- Element end -->
        <span class="c">
            <font color="{$symbol-color}"><xsl:text>&lt;/</xsl:text></font>
            <xsl:call-template name="display-element-name">
                <xsl:with-param name="name" select="name()"/>
            </xsl:call-template>
            <font color="{$symbol-color}"><xsl:text>&gt;</xsl:text></font>
        </span>
    </xsl:template>

    <!-- Interleave of elements and non-whitespace text -->
    <xsl:template match="*[text() and * and string-length(normalize-space(text()[1])) > 0
                           and not(descendant-or-self::comment()
                           and not(descendant-or-self::processing-instruction()))]" mode="xml-formatting">
        <xsl:param name="show-namespaces" select="true()"/>
        <xsl:choose>
            <xsl:when test="*/node()/node()">
                <!-- If it's too deep, do regular processing -->
                <xsl:call-template name="format-regular-element"/>
            </xsl:when>
            <xsl:otherwise>
                <!-- Try cool layout -->
                <xsl:call-template name="format-regular-element">
                    <xsl:with-param name="mode" select="'xml-formatting-inline'"/>
                    <xsl:with-param name="show-namespaces" select="$show-namespaces"/>
                </xsl:call-template>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- Regular element -->
    <xsl:template match="*" name="format-regular-element" mode="xml-formatting">
        <xsl:param name="mode"/>
        <xsl:param name="show-namespaces" select="true()"/>
        <xsl:variable name="element" select="."/>
        <xsl:variable name="name" select="name()"/>
        <div class="cd">
            <!-- Collapse -->
            <span class="x">&#160;&#160;</span>
            <!-- Element start -->
            <font color="{$symbol-color}"><xsl:text>&lt;</xsl:text></font>
            <xsl:call-template name="display-element-name">
                <xsl:with-param name="name" select="name()"/>
            </xsl:call-template>
            <xsl:call-template name="display-element-attributes">
                <xsl:with-param name="name" select="name()"/>
                <xsl:with-param name="attributes" select="@*"/>
                <xsl:with-param name="show-namespaces" select="$show-namespaces"/>
            </xsl:call-template>
            <!-- Close element start -->
            <font color="{$symbol-color}">
                <xsl:text>&gt;</xsl:text>
            </font>
            <!-- Nested contents -->
            <div class="id">
                <xsl:choose>
                    <xsl:when test="$mode = 'xml-formatting-inline'">
                        <xsl:apply-templates mode="xml-formatting-inline">
                            <xsl:with-param name="show-namespaces" select="$show-namespaces"/>
                        </xsl:apply-templates>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:apply-templates mode="xml-formatting">
                            <xsl:with-param name="show-namespaces" select="$show-namespaces"/>
                        </xsl:apply-templates>
                    </xsl:otherwise>
                </xsl:choose>
            </div>
            <!-- Close element -->
            <span class="c">
                <xsl:text>&#160;&#160;</xsl:text>
                <font color="{$symbol-color}"><xsl:text>&lt;/</xsl:text></font>
                <xsl:call-template name="display-element-name">
                    <xsl:with-param name="name" select="name()"/>
                </xsl:call-template>
                <font color="{$symbol-color}"><xsl:text>&gt;</xsl:text></font>
            </span>
        </div>
    </xsl:template>

    <xsl:template match="*" mode="xml-formatting-inline">
        <xsl:param name="mode"/>
        <xsl:param name="show-namespaces" select="true()"/>
        <xsl:variable name="element" select="."/>
        <xsl:variable name="name" select="name()"/>
        <div class="cd">
            <!-- Element start -->
            <font color="{$symbol-color}"><xsl:text>&lt;</xsl:text></font>
            <xsl:call-template name="display-element-name">
                <xsl:with-param name="name" select="name()"/>
            </xsl:call-template>
            <xsl:call-template name="display-element-attributes">
                <xsl:with-param name="name" select="name()"/>
                <xsl:with-param name="attributes" select="@*"/>
            </xsl:call-template>
            <!-- Close element start -->
            <font color="{$symbol-color}"><xsl:text>&gt;</xsl:text></font>
            <!-- Nested contents -->
            <xsl:apply-templates mode="xml-formatting-inline">
                <xsl:with-param name="show-namespaces" select="$show-namespaces"/>
            </xsl:apply-templates>
            <!-- Close element -->
            <span class="c">
                <font color="{$symbol-color}"><xsl:text>&lt;/</xsl:text></font>
                <xsl:call-template name="display-element-name">
                    <xsl:with-param name="name" select="name()"/>
                </xsl:call-template>
                <font color="{$symbol-color}"><xsl:text>&gt;</xsl:text></font>
            </span>
        </div>
    </xsl:template>

    <!-- Regular text node -->
    <xsl:template match="text()" name="format-text" mode="xml-formatting">
        <xsl:param name="show-namespaces" select="true()"/>
        <span class="c">
            <xsl:choose>
                <xsl:when test="normalize-space(.) = ''"/>
                <xsl:when test="$normalize-non-whitespace">
                    <font class="t" color="{$text-color}">
                        <xsl:value-of select="normalize-space(.)"/>
                    </font>
                </xsl:when>
                <xsl:otherwise>
                    <font color="{$text-color}"><xsl:value-of select="."/></font>
                </xsl:otherwise>
            </xsl:choose>
        </span>
    </xsl:template>

    <xsl:template match="text()" mode="xml-formatting-inline">
        <xsl:param name="show-namespaces" select="true()"/>
        <xsl:call-template name="format-text"/>
    </xsl:template>

    <xsl:template match="comment" mode="xml-formatting" priority="2">
        <xsl:param name="show-namespaces" select="true()"/>
        <div class="rd">
            <font color="{$comment-color}">
                <xsl:text>&#160;&#160;&lt;!-- </xsl:text>
                <xsl:apply-templates/>
                <xsl:text> --&gt;</xsl:text>
            </font>
        </div>
    </xsl:template>

    <xsl:template match="comment() | processing-instruction()" mode="xml-formatting">
        <xsl:param name="show-namespaces" select="true()"/>
        <font color="black"><xsl:value-of select="."/></font>
    </xsl:template>

    <xsl:template name="display-element-name">
        <xsl:param name="name"/>
        <xsl:choose>
            <xsl:when test="contains($name, ':')">
                <font color="{$prefix-color}"><xsl:value-of select="substring-before($name, ':')"/></font>
                <font color="{$symbol-color}"><xsl:text>:</xsl:text></font>
                <font color="{$element-color}"><xsl:value-of select="substring-after($name, ':')"/></font>
            </xsl:when>
            <xsl:otherwise>
                <font color="{$element-color}"><xsl:value-of select="$name"/></font>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="display-element-attributes">
        <xsl:param name="name"/>
        <xsl:param name="attributes"/>
        <xsl:param name="show-namespaces" select="true()"/>
        <!-- Display attributes -->
        <xsl:for-each select="$attributes">
            <xsl:text> </xsl:text>
            <font color="{$attribute-name-color}">
                <xsl:choose>
                    <xsl:when test="substring-before(name(), ':') = 'xxmlns'">
                        <xsl:text>xmlns:</xsl:text>
                        <xsl:value-of select="substring-after(name(), ':')"/>
                    </xsl:when>
                    <xsl:when test="name() = 'xxmlns'">
                        <xsl:text>xmlns</xsl:text>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="name()"/>
                    </xsl:otherwise>
                </xsl:choose>
            </font>
            <font color="{$symbol-color}">
                <xsl:text>=&quot;</xsl:text>
                <font color="{$attribute-value-color}"><xsl:value-of select="."/></font>
                <xsl:text>&quot;</xsl:text>
            </font>
        </xsl:for-each>
        <!-- Check for namespaces -->
        <xsl:if test="$show-namespaces and contains($name, ':')">
            <xsl:variable name="prefix" select="substring-before($name, ':')"/>
            <xsl:variable name="same-prefix-ancestors" select="ancestor::*[starts-with(name(), concat($prefix, ':'))]"/>
            <xsl:if test="not($same-prefix-ancestors)">
                <xsl:text> </xsl:text>
                <font color="{$attribute-name-color}"><xsl:value-of select="concat('xmlns:', $prefix)"/></font>
                <xsl:text>=&quot;</xsl:text>
                <font color="{$attribute-value-color}"><xsl:value-of select="namespace-uri()"/></font>
                <xsl:text>&quot;</xsl:text>
            </xsl:if>
        </xsl:if>
    </xsl:template>

</xsl:stylesheet>

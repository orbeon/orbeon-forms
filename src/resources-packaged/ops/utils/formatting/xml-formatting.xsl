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
<!--
    This stylesheet attempts to format XML into indented, syntax-colored HTML fit for display.
-->
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns="http://www.w3.org/1999/xhtml">

    <xsl:output method="html" omit-xml-declaration="yes" name="html"/>

    <xsl:variable name="normalize-non-whitespace" select="true()"/>

    <!-- Empty element -->
    <xsl:template match="*[not(node())]" name="format-empty-element" mode="xml-formatting">
        <span class="xml-rd">
            <xsl:call-template name="format-empty-element-inline"/>
        </span>
    </xsl:template>

    <xsl:template match="*[not(node())]" name="format-empty-element-inline" mode="xml-formatting-inline">
        <!-- Element -->
        <span class="xml-symbol">&lt;</span>
        <xsl:call-template name="display-element-name">
            <xsl:with-param name="name" select="name()"/>
        </xsl:call-template>
        <xsl:call-template name="display-element-attributes">
            <xsl:with-param name="name" select="name()"/>
            <xsl:with-param name="attributes" select="@*"/>
        </xsl:call-template>
        <span class="xml-symbol">/&gt;</span>
    </xsl:template>

    <!-- Short text-only element -->
    <xsl:template match="*[text() and not(* | processing-instruction() | comment()) and string-length(.) &lt;= 50]" mode="xml-formatting">
        <span class="xml-rd">
            <xsl:call-template name="format-short-text-element-inline"/>
        </span>
    </xsl:template>

    <!-- Element containing only one other element (should also check on length) -->
    <!--<xsl:template match="*[count(*) = 1 and not(text() | processing-instruction() | comment())]" mode="xml-formatting">-->
        <!--<xsl:variable name="attempt">-->
            <!--<span class="xml-rd">-->
                <!--<xsl:apply-templates select="." mode="xml-formatting-inline"/>-->
            <!--</span>-->
        <!--</xsl:variable>-->
        <!--<xsl:choose>-->
            <!--<xsl:when test="string-length(string-join($attempt/text(), '')) le 50">-->
                <!--<xsl:copy-of select="$attempt"/>-->
            <!--</xsl:when>-->
            <!--<xsl:otherwise>-->
                <!--<xsl:next-match/>-->
            <!--</xsl:otherwise>-->
        <!--</xsl:choose>-->
    <!--</xsl:template>-->

    <xsl:template match="*[text() and not(* | processing-instruction() | comment()) and string-length(.) &lt;= 50]" name="format-short-text-element-inline" mode="xml-formatting-inline">
        <!-- Element start -->
        <span class="xml-symbol">&lt;</span>
        <xsl:call-template name="display-element-name">
            <xsl:with-param name="name" select="name()"/>
        </xsl:call-template>
        <xsl:call-template name="display-element-attributes">
            <xsl:with-param name="name" select="name()"/>
            <xsl:with-param name="attributes" select="@*"/>
        </xsl:call-template>
        <span class="xml-symbol">&gt;</span>
        <!-- Nested contents -->
        <xsl:apply-templates mode="xml-formatting"/>
        <!-- Element end -->
        <span class="xml-c">
            <span class="xml-symbol">&lt;/</span>
            <xsl:call-template name="display-element-name">
                <xsl:with-param name="name" select="name()"/>
            </xsl:call-template>
            <span class="xml-symbol">&gt;</span>
        </span>
    </xsl:template>

    <!-- Interleave of elements and non-whitespace text -->
    <xsl:template match="*[text() and * and string-length(normalize-space(text()[1])) > 0
                           and not(descendant-or-self::comment()
                           and not(descendant-or-self::processing-instruction()))]" mode="xml-formatting">
        <xsl:choose>
            <xsl:when test="*/node()/node()">
                <!-- If it's too deep, do regular processing -->
                <xsl:call-template name="format-regular-element"/>
            </xsl:when>
            <xsl:otherwise>
                <!-- Try cool layout -->
                <xsl:call-template name="format-regular-element">
                    <xsl:with-param name="mode" select="'xml-formatting-inline'"/>
                </xsl:call-template>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- Regular element -->
    <xsl:template match="*" name="format-regular-element" mode="xml-formatting">
        <xsl:param name="mode"/>
        <xsl:variable name="element" select="."/>
        <xsl:variable name="name" select="name()"/>
        <span class="xml-cd">
            <!-- Element start -->
            <span class="xml-o">
                <span class="xml-symbol">&lt;</span>
                <xsl:call-template name="display-element-name">
                    <xsl:with-param name="name" select="name()"/>
                </xsl:call-template>
                <xsl:call-template name="display-element-attributes">
                    <xsl:with-param name="name" select="name()"/>
                    <xsl:with-param name="attributes" select="@*"/>
                </xsl:call-template>
                <!-- Close element start -->
                <span class="xml-symbol">&gt;</span>
            </span>
            <!-- Nested contents -->
            <span class="xml-id">
                <xsl:choose>
                    <xsl:when test="$mode = 'xml-formatting-inline'">
                        <xsl:apply-templates mode="xml-formatting-inline"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:apply-templates mode="xml-formatting"/>
                    </xsl:otherwise>
                </xsl:choose>
            </span>
            <!-- Close element -->
            <span class="xml-c">
                <span class="xml-symbol">&lt;/</span>
                <xsl:call-template name="display-element-name">
                    <xsl:with-param name="name" select="name()"/>
                </xsl:call-template>
                <span class="xml-symbol">&gt;</span>
            </span>
        </span>
    </xsl:template>

    <xsl:template match="*" mode="xml-formatting-inline">
        <xsl:param name="mode"/>
        <xsl:variable name="element" select="."/>
        <xsl:variable name="name" select="name()"/>
        <span class="xml-cd">
            <!-- Element start -->
            <span class="xml-symbol">&lt;</span>
            <xsl:call-template name="display-element-name">
                <xsl:with-param name="name" select="name()"/>
            </xsl:call-template>
            <xsl:call-template name="display-element-attributes">
                <xsl:with-param name="name" select="name()"/>
                <xsl:with-param name="attributes" select="@*"/>
            </xsl:call-template>
            <!-- Close element start -->
            <span class="xml-symbol">&gt;</span>
            <!-- Nested contents -->
            <xsl:apply-templates mode="xml-formatting-inline"/>
            <!-- Close element -->
            <span class="xml-c">
                <span class="xml-symbol">&lt;/</span>
                <xsl:call-template name="display-element-name">
                    <xsl:with-param name="name" select="name()"/>
                </xsl:call-template>
                <span class="xml-symbol">&gt;</span>
            </span>
        </span>
    </xsl:template>

    <!-- Regular text node -->
    <xsl:template match="text()" name="format-text" mode="xml-formatting">
        <span class="xml-c">
            <xsl:choose>
                <xsl:when test="normalize-space(.) = ''"/>
                <xsl:when test="$normalize-non-whitespace">
                    <span class="xml-text">
                        <xsl:value-of select="normalize-space(.)"/>
                    </span>
                </xsl:when>
                <xsl:otherwise>
                    <span class="xml-text"><xsl:value-of select="."/></span>
                </xsl:otherwise>
            </xsl:choose>
        </span>
    </xsl:template>

    <xsl:template match="text()" mode="xml-formatting-inline">
        <xsl:call-template name="format-text"/>
    </xsl:template>

    <xsl:template match="comment" mode="xml-formatting" priority="2">
        <span class="xml-rd">
            <span class="xml-comment">
                <xsl:text>&lt;!-- </xsl:text>
                <xsl:apply-templates/>
                <xsl:text> --&gt;</xsl:text>
            </span>
        </span>
    </xsl:template>

    <xsl:template match="comment() | processing-instruction()" mode="xml-formatting">
        <span class="xml-comment"><xsl:value-of select="."/></span>
    </xsl:template>

    <xsl:template name="display-element-name">
        <xsl:param name="name"/>
        <xsl:choose>
            <xsl:when test="contains($name, ':')">
                <span class="xml-elt-prefix"><xsl:value-of select="substring-before($name, ':')"/></span>
                <span class="xml-symbol">:</span>
                <span class="xml-elt-name"><xsl:value-of select="substring-after($name, ':')"/></span>
            </xsl:when>
            <xsl:otherwise>
                <span class="xml-elt-name"><xsl:value-of select="$name"/></span>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="display-element-attributes">
        <xsl:param name="name"/>
        <xsl:param name="attributes"/>
        <xsl:param name="show-namespaces" select="true()" tunnel="yes"/>
        <!-- Display attributes -->
        <xsl:for-each select="$attributes">
            <xsl:text> </xsl:text>
            <xsl:choose>
                <xsl:when test="name() != local-name()">
                    <span class="xml-att-prefix"><xsl:value-of select="substring-before(name(), ':')"/></span>
                    <span class="xml-symbol">:</span>
                    <span class="xml-att-name"><xsl:value-of select="local-name()"/></span>
                </xsl:when>
                <xsl:otherwise>
                    <span class="xml-att-name"><xsl:value-of select="name()"/></span>
                </xsl:otherwise>
            </xsl:choose>
            <span class="xml-symbol">=&quot;</span>
            <span class="xml-att-value"><xsl:value-of select="."/></span>
            <span class="xml-symbol">&quot;</span>
        </xsl:for-each>
        <!-- Check for namespaces -->
        <xsl:if test="$show-namespaces">
            <!-- We can't know for sure what namespaces are actually newly declared on an element, so we use instead the
                 namespaces in scope except if the namespace was already decared on an ancestor element -->
            <xsl:variable name="current-element" select="."/>
            <xsl:variable name="namespace-nodes"
                          select="namespace::*[name() != 'xml' and not(. = $current-element/ancestor::*/namespace::*)]"/>

            <xsl:for-each select="$namespace-nodes">
                <xsl:text> </xsl:text>
                <xsl:choose>
                    <xsl:when test="name() = ''">
                        <span class="xml-att-name">xmlns</span>
                        <span class="xml-symbol">=&quot;</span>
                        <span class="xml-att-value"><xsl:value-of select="."/></span>
                        <span class="xml-symbol">&quot;</span>
                    </xsl:when>
                    <xsl:otherwise>
                        <span class="xml-att-prefix">xmlns</span>
                        <span class="xml-symbol">:</span>
                        <span class="xml-att-name"><xsl:value-of select="name()"/></span>
                        <span class="xml-symbol">=&quot;</span>
                        <span class="xml-att-value"><xsl:value-of select="."/></span>
                        <span class="xml-symbol">&quot;</span>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:for-each>
            <xsl:if test="name() = local-name() and not(namespace::*[name() = ''])">
                <!-- The element doesn't have a prefix -->
                <xsl:variable name="default-ns-on-parent" select="string(../namespace::*[name() = ''])"/>
                <xsl:if test="$default-ns-on-parent != namespace-uri()">
                    <xsl:text> </xsl:text>
                    <span class="xml-att-name">xmlns</span>
                    <span class="xml-symbol">=&quot;&quot;</span>
                </xsl:if>
            </xsl:if>
        </xsl:if>

    </xsl:template>

</xsl:stylesheet>

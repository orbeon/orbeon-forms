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
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns="http://www.w3.org/1999/xhtml">

    <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
    <xsl:include href="oxf:/ops/utils/formatting/xml-formatting.xsl"/>
    <xsl:variable name="instance" as="element()" select="doc('input:instance')/form"/>

    <xsl:template match="/">
        <xhtml:html>
            <xhtml:head>
                <xhtml:title>Orbeon Forms User Guide - <xsl:value-of select="/document/header/title"/></xhtml:title>
            </xhtml:head>
            <xhtml:body>
                <xhtml:table cellpadding="0" cellspacing="0" border="0" id="main">
                    <!-- Banner (with search) -->
                    <xhtml:tr><xhtml:td colspan="2" id="banner">
                        <xhtml:div id="orbeon-logo" style="float: left">
                            <xhtml:a href="/" f:url-norewrite="true">
                                <xhtml:img f:url-norewrite="false" width="199" height="42"
                                     style="border: 0 white; margin-top: 0.2em; margin-bottom: 0.4em"
                                     src="/config/theme/images/orbeon-small-blueorange.gif"/>
                            </xhtml:a>
                        </xhtml:div>
                        <xhtml:span id="navigation" style="float: right; margin-right: 1em; margin-top: .2em; white-space: nowrap">
                            <form method="GET" class="blue" style="margin:0.2em; margin-bottom:0em" action="http://www.google.com/custom">
                                <xhtml:a href="http://www.orbeon.com/" f:url-norewrite="true">Orbeon.com</xhtml:a> |
                                <xhtml:a href="../" f:url-norewrite="true">Example Apps</xhtml:a> |
                                <xhtml:span style="white-space: nowrap">
                                    Search:
                                    <xhtml:input type="text" name="q" size="10" maxlength="255" value=""/>
                                    <xhtml:input type="submit" name="sa" VALUE="Go" style="margin-left: 0.2em;"/>
                                </xhtml:span>
                                <xhtml:input type="hidden" name="cof" VALUE="GIMP:#FF9900;T:black;LW:510;ALC:#FF9900;L:http://www.orbeon.com/pics/orbeon-google.png;GFNT:#666699;LC:#666699;LH:42;BGC:#FFFFFF;AH:center;VLC:#666699;GL:0;S:http://www.orbeon.com;GALT:#FF9900;AWFID:8ac636f034abb7d8;"/>
                                <xhtml:input type="hidden" name="sitesearch" value="orbeon.com"/>
                            </form>
                        </xhtml:span>
                    </xhtml:td></xhtml:tr>
                    <!-- Tabs -->
                    <xhtml:tr>
                        <xhtml:td colspan="2">
                            <xhtml:div class="tabs">&#160;</xhtml:div> <!-- Need to insert a space here for Safari -->
                        </xhtml:td>
                    </xhtml:tr>
                    <xhtml:tr>
                        <!-- List of sections -->
                        <xhtml:td id="leftcontent" valign="top" width="1%">
                            <xhtml:h1>Orbeon Forms User Guide</xhtml:h1>
                            <xhtml:ul class="tree-sections">
                                <xsl:apply-templates select="doc('book.xml')/book/menu"/>
                            </xhtml:ul>
                        </xhtml:td>
                        <xhtml:td id="maincontent">
                            <xhtml:div class="maincontent">
                                <!-- Title -->
                                <xhtml:h1><xsl:value-of select="/document/header/title"/></xhtml:h1>
                                <!-- TOC  -->
                                <xhtml:div id="mainbody">
                                    <xhtml:div class="minitoc">
                                        <xhtml:ul>
                                            <xsl:for-each select="/document/body/section">
                                                <xsl:variable name="anchor"
                                                    select="if (local-name(preceding-sibling::*[1]) = 'a')
                                                    then preceding-sibling::a[1]/@name else generate-id()"/>
                                                <xhtml:li>
                                                    <xsl:number level="single" count="section" format="1. "/>
                                                    <xhtml:a href="#{$anchor}">
                                                        <xsl:value-of select="title"/>
                                                    </xhtml:a>
                                                    <!-- Second level -->
                                                    <xsl:if test="section">
                                                        <xhtml:ul>
                                                            <xsl:variable name="subsection-count" select="count(section)"/>
                                                            <xsl:for-each select="section">
                                                                <xsl:variable name="anchor"
                                                                    select="if (local-name(preceding-sibling::*[1]) = 'a')
                                                                    then preceding-sibling::a[1]/@name else generate-id()"/>
                                                                <xhtml:li>
                                                                    <xsl:number level="multiple" count="section" format="1.1. "/>
                                                                    <xhtml:a href="#{$anchor}">
                                                                        <xsl:value-of select="title"/>
                                                                    </xhtml:a>
                                                                    <!-- Third -->
                                                                    <xsl:if test="section">
                                                                        <xhtml:ul>
                                                                            <xsl:variable name="subsubsection-count" select="count(section)"/>
                                                                            <xsl:for-each select="section">
                                                                                <xsl:variable name="anchor"
                                                                                    select="if (local-name(preceding-sibling::*[1]) = 'a')
                                                                                    then preceding-sibling::a[1]/@name else generate-id()"/>
                                                                                <xhtml:li>
                                                                                    <xsl:number level="multiple" count="section" format="1.1.1. "/>
                                                                                    <xhtml:a href="#{$anchor}">
                                                                                        <xsl:value-of select="title"/>
                                                                                    </xhtml:a>
                                                                                </xhtml:li>
                                                                            </xsl:for-each>
                                                                        </xhtml:ul>
                                                                    </xsl:if>
                                                                </xhtml:li>
                                                            </xsl:for-each>
                                                        </xhtml:ul>
                                                    </xsl:if>
                                                </xhtml:li>
                                            </xsl:for-each>
                                        </xhtml:ul>
                                    </xhtml:div>
                                </xhtml:div>
                                <!-- Body -->
                                <xsl:apply-templates select="/document/body/*"/>
                            </xhtml:div>
                        </xhtml:td>
                    </xhtml:tr>
                </xhtml:table>
            </xhtml:body>
        </xhtml:html>
    </xsl:template>
    
    <xsl:template match="menu">
        <xhtml:li class="tree-section">
            <xsl:value-of select="@label"/>
            <xhtml:ul class="tree-items">
                <xsl:apply-templates select="menu-item|menu"/>
            </xhtml:ul>
        </xhtml:li>
    </xsl:template>

    <xsl:template match="menu-item">
        <xsl:variable name="selected" as="xs:boolean" select="$instance/page = @href"/>
        <xhtml:li class="{if ($selected) then 'tree-items-selected' else 'tree-items'}">
            <xsl:choose>
                <xsl:when test="$selected">
                    <xsl:value-of select="@label"/>
                </xsl:when>
                <xsl:otherwise>
                    <xhtml:a href="{@href}"><xsl:value-of select="@label"/></xhtml:a>
                </xsl:otherwise>
            </xsl:choose>
        </xhtml:li>
    </xsl:template>

    <xsl:template match="section[count(ancestor::section) = 0]">
        <!-- Note: We put the h2 inside the a to get around a bug in Safari (if we don't do
        that, the style on the a applies on the whole section. -->
        <xhtml:a name="{generate-id()}"/>
        <xhtml:h2>
            <xsl:number level="multiple" count="section" format="1.1.1. "/>
            <xsl:value-of select="title"/>
        </xhtml:h2>
        <div class="ops-doc-section">
            <xsl:apply-templates select="* except title"/>
        </div>
    </xsl:template>

    <xsl:template match="section[count(ancestor::section) = 1]">
        <xhtml:a name="{generate-id()}"/>
        <xhtml:h3>
            <xsl:number level="multiple" count="section" format="1.1. "/>
            <xsl:value-of select="title"/>
        </xhtml:h3>
        <div class="ops-doc-section">
            <xsl:apply-templates select="* except title"/>
        </div>
    </xsl:template>

    <xsl:template match="section[count(ancestor::section) = 2]">
        <xhtml:a name="{generate-id()}"/>
        <xhtml:h4>
            <xsl:number level="multiple" count="section" format="1.1.1. "/>
            <xsl:value-of select="title"/>
        </xhtml:h4>
        <div class="ops-doc-section">
            <xsl:apply-templates select="* except title"/>
        </div>
    </xsl:template>

    <xsl:template match="note | notes | warning | fixme">
        <xhtml:div class="frame {local-name()}">
            <xhtml:div class="label">
                <xsl:choose>
                    <xsl:when test="local-name() = 'note'">Note</xsl:when>
                    <xsl:when test="local-name() = 'notes'">Notes</xsl:when>
                    <xsl:when test="local-name() = 'warning'">Warning</xsl:when>
                    <xsl:otherwise>Fixme (
                        <xsl:value-of select="@author"/>

                        )</xsl:otherwise>
                </xsl:choose>
            </xhtml:div>
            <xhtml:div class="content">
                <xsl:apply-templates/>
            </xhtml:div>
        </xhtml:div>
    </xsl:template>

    <xsl:template match="link">
        <xhtml:a href="{@href}">
            <xsl:apply-templates/>
        </xhtml:a>
    </xsl:template>

    <xsl:template match="xml-source">
        <xhtml:div style="margin-bottom: 1em; clear: right">
            <xsl:attribute name="class" select="if (@border = 'false') then 'code' else 'code bordered'"/>
            <xsl:choose>
                <xsl:when test="@ignore-root-element = 'true'">
                    <xsl:apply-templates mode="xml-formatting" select="*[1]/node()">
                        <xsl:with-param name="show-namespaces" select="not(@show-namespaces = 'false')" tunnel="yes"/>
                    </xsl:apply-templates>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:apply-templates mode="xml-formatting">
                        <xsl:with-param name="show-namespaces" select="not(@show-namespaces = 'false')" tunnel="yes"/>
                    </xsl:apply-templates>
                </xsl:otherwise>
            </xsl:choose>
        </xhtml:div>
    </xsl:template>

    <xsl:template match="source">
        <xhtml:div class="bordered">
            <pre>
                <xsl:apply-templates/>
            </pre>
        </xhtml:div>
    </xsl:template>

    <xsl:template match="icon">
        <img src="{@src}" alt="{@alt}">
            <xsl:if test="@height">
                <xsl:attribute name="height"><xsl:value-of select="@height"/></xsl:attribute>
            </xsl:if>
            <xsl:if test="@width">
                <xsl:attribute name="width"><xsl:value-of select="@width"/></xsl:attribute>
            </xsl:if>
        </img>
    </xsl:template>

    <xsl:template match="img">
        <xhtml:p>
            <xhtml:img class="ops-doc-image">
                <xsl:copy-of select="@*"/>
            </xhtml:img>
        </xhtml:p>
    </xsl:template>

    <xsl:template match="figure">
        <xhtml:div align="center">
            <xhtml:img src="{@src}" alt="{@alt}" class="ops-doc-figure">
                <xsl:if test="@height">
                    <xsl:attribute name="height"><xsl:value-of select="@height"/></xsl:attribute>
                </xsl:if>
                <xsl:if test="@width">
                    <xsl:attribute name="width"><xsl:value-of select="@width"/></xsl:attribute>
                </xsl:if>
            </xhtml:img>
        </xhtml:div>
    </xsl:template>

    <xsl:template match="table">
        <xhtml:table cellpadding="4" cellspacing="1" class="gridtable">
            <xsl:if test="@cellspacing"><xsl:attribute name="cellspacing"><xsl:value-of select="@cellspacing"/></xsl:attribute></xsl:if>
            <xsl:if test="@cellpadding"><xsl:attribute name="cellpadding"><xsl:value-of select="@cellpadding"/></xsl:attribute></xsl:if>
            <xsl:if test="@border"><xsl:attribute name="border"><xsl:value-of select="@border"/></xsl:attribute></xsl:if>
            <xsl:if test="@class"><xsl:attribute name="class"><xsl:value-of select="@class"/></xsl:attribute></xsl:if>
            <xsl:if test="@bgcolor"><xsl:attribute name="bgcolor"><xsl:value-of select="@bgcolor"/></xsl:attribute></xsl:if>
            <xsl:apply-templates/>
        </xhtml:table>
    </xsl:template>

</xsl:stylesheet>

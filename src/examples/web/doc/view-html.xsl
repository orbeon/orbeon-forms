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
    xmlns:xs="http://www.w3.org/2001/XMLSchema">

    <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
    <xsl:include href = "/inspector/xml-formatting.xsl"/>
    <xsl:variable name="instance" as="element()" select="doc('input:instance')/form"/>

    <xsl:template match="/">
        <xhtml:html xmlns:f="http://orbeon.org/oxf/xml/formatting">
            <xhtml:head>
                <xhtml:title>PresentationServer Documentation</xhtml:title>
            </xhtml:head>
            <xhtml:body>
                <!-- Banner (with search) -->
                <div id="banner">
                    <div style="float: left">
                        <a href="/" f:url-norewrite="true">
                            <img f:url-norewrite="false" width="199" height="42" style="border: 0 white; margin-left: 1em; margin-top: 0.2em; margin-bottom: 0.4em" src="/oxf-theme/images/orbeon-small-blueorange.gif"/>
                        </a>
                    </div>
                    <span style="float: right; margin-right: 1em; margin-top: .2em; white-space: nowrap">
                        <form method="GET" class="blue" style="margin:0.2em; margin-bottom:0em" action="http://www.google.com/custom">
                            <a href="http://www.orbeon.com/" f:url-norewrite="true">Orbeon.com</a> |
                            <a href="../" f:url-norewrite="true">Examples</a> | 
                            <span style="white-space: nowrap">
                                Search:
                                <input type="text" name="q" size="10" maxlength="255" value=""/>
                                <input type="submit" name="sa" VALUE="Go" style="margin-left: 0.2em;"/>
                            </span>
                            <input type="hidden" name="cof" VALUE="GIMP:#FF9900;T:black;LW:510;ALC:#FF9900;L:http://www.orbeon.com/pics/orbeon-google.png;GFNT:#666699;LC:#666699;LH:42;BGC:#FFFFFF;AH:center;VLC:#666699;GL:0;S:http://www.orbeon.com;GALT:#FF9900;AWFID:8ac636f034abb7d8;"/>
                            <input type="hidden" name="sitesearch" value="orbeon.com"/>
                        </form>
                    </span>
                </div>

                <!-- Tabs -->
                <div class="tabs">&#160;</div> <!-- Need to insert a space here for Safari -->
                <div id="main">
                    <div id="main1">
                    <!-- List of sections -->
                    <div id="leftcontent">
                        <h1>PresentationServer Documentation</h1>
                        <ul class="tree-sections">
                            <xsl:apply-templates select="doc('book.xml')/book/menu"/>
                        </ul>
                    </div>
                    <div id="maincontent">
                        <div id="mainbody">
                            <!-- Title -->
                            <h1><xsl:value-of select="/document/header/title"/></h1>
                            <!-- TOC  -->
                            <div class="minitoc">
                                <ul>
                                    <xsl:for-each select="/document/body/section">
                                        <xsl:variable name="anchor" 
                                            select="if (preceding-sibling::a) 
                                            then preceding-sibling::a/@name else generate-id()"/>
                                        <li>
                                            <xsl:number level="single" count="section" format="1. "/>
                                            <a href="#{$anchor}">
                                                <xsl:value-of select="title"/>
                                            </a>
                                            <!-- Second level -->
                                            <xsl:if test="section">
                                                <ul>
                                                    <xsl:variable name="subsection-count" select="count(section)"/>
                                                    <xsl:for-each select="section">
                                                        <xsl:variable name="anchor" 
                                                            select="if (preceding-sibling::a) 
                                                            then preceding-sibling::a/@name else generate-id()"/>
                                                        <li>
                                                            <xsl:number level="multiple" count="section" format="1.1. "/>
                                                            <a href="#{$anchor}">
                                                                <xsl:value-of select="title"/>
                                                            </a>
                                                            <!-- Third -->
                                                            <xsl:if test="section">
                                                                <ul>
                                                                    <xsl:variable name="subsubsection-count" select="count(section)"/>
                                                                    <xsl:for-each select="section">
                                                                        <xsl:variable name="anchor" 
                                                                            select="if (preceding-sibling::a) 
                                                                            then preceding-sibling::a/@name else generate-id()"/>
                                                                        <li>
                                                                            <xsl:number level="multiple" count="section" format="1.1.1. "/>
                                                                            <a href="#{$anchor}">
                                                                                <xsl:value-of select="title"/>
                                                                            </a>
                                                                        </li>
                                                                    </xsl:for-each>
                                                                </ul>
                                                            </xsl:if>
                                                        </li>
                                                    </xsl:for-each>
                                                </ul>
                                            </xsl:if>
                                        </li>
                                    </xsl:for-each>
                                </ul>
                            </div>
                            <!-- Body -->
                            <xsl:apply-templates select="/document/body/*" />
                        </div>

                    </div>
                         <div class="cleaner">&#160;</div>
                    </div>
                </div>
            </xhtml:body>
        </xhtml:html>
    </xsl:template>
    
    <xsl:template match="menu">
        <li class="tree-section">
            <xsl:value-of select="@label"/>
            <ul class="tree-items">
                <xsl:apply-templates select="menu-item|menu"/>
            </ul>
        </li>
    </xsl:template>

    <xsl:template match="menu-item">
        <xsl:variable name="selected" as="xs:boolean" select="$instance/page = @href"/>
        <li class="{if ($selected) then 'tree-items-selected' else 'tree-items'}">
            <xsl:choose>
                <xsl:when test="$selected">
                    <xsl:value-of select="@label"/>
                </xsl:when>
                <xsl:otherwise>
                    <a href="{@href}"><xsl:value-of select="@label"/></a>
                </xsl:otherwise>
            </xsl:choose>
        </li>
    </xsl:template>

    <xsl:template match="section[count(ancestor::section) = 0]">
        <!-- Note: We put the h2 inside the a to get around a bug in Safari (if we don't do
        that, the style on the a applies on the whole section. -->
        <a name="{generate-id()}"/>
        <h2>
            <xsl:number level="multiple" count="section" format="1.1.1. "/>
            <xsl:value-of select="title"/>
        </h2>
        <xsl:apply-templates select="* except title"/>
    </xsl:template>

    <xsl:template match="section[count(ancestor::section) = 1]">
        <a name="{generate-id()}"/>
        <h3>
            <xsl:number level="multiple" count="section" format="1.1. "/>
            <xsl:value-of select="title"/>
        </h3>
        <xsl:apply-templates select="* except title"/>
    </xsl:template>

    <xsl:template match="section[count(ancestor::section) = 2]">
        <a name="{generate-id()}"/>
        <h4>
            <xsl:number level="multiple" count="section" format="1.1.1. "/>
            <xsl:value-of select="title"/>
        </h4>
        <xsl:apply-templates select="* except title"/>
    </xsl:template>

    <xsl:template match="note | notes | warning | fixme">
        <div class="frame {local-name()}">
            <div class="label">
                <xsl:choose>
                    <xsl:when test="local-name() = 'note'">Note</xsl:when>
                    <xsl:when test="local-name() = 'notes'">Notes</xsl:when>
                    <xsl:when test="local-name() = 'warning'">Warning</xsl:when>
                    <xsl:otherwise>Fixme (
                        <xsl:value-of select="@author"/>

                        )</xsl:otherwise>
                </xsl:choose>
            </div>
            <div class="content">
                <xsl:apply-templates/>
            </div>
        </div>
    </xsl:template>

    <xsl:template match="link">
        <a href="{@href}">
            <xsl:apply-templates/>
        </a>
    </xsl:template>

    <xsl:template match="xml-source">
        <p><div style="margin-bottom: 1em; clear: right">
            <xsl:attribute name="class" select="if (@border = 'false') then 'code' else 'code bordered'"/>
            <xsl:apply-templates mode="xml-formatting">
                <xsl:with-param name="show-namespaces" select="not(@show-namespaces = 'false')"/>
            </xsl:apply-templates>
        </div></p>
    </xsl:template>

    <xsl:template match="source">
        <p>
            <div class="bordered">
                <pre>
                    <xsl:apply-templates/>
                </pre>
            </div>
        </p>
    </xsl:template>

    <xsl:template match="img">
        <p class="image"><img>
            <xsl:copy-of select="@*"/>
        </img></p>
    </xsl:template>

    <xsl:template match="figure">
        <div align="center">
            <img src="{@src}" alt="{@alt}" class="figure">
                <xsl:if test="@height">
                    <xsl:attribute name="height"><xsl:value-of select="@height"/></xsl:attribute>
                </xsl:if>
                <xsl:if test="@width">
                    <xsl:attribute name="width"><xsl:value-of select="@width"/></xsl:attribute>
                </xsl:if>
            </img>
        </div>
    </xsl:template>

    <xsl:template match="table">
        <table cellpadding="4" cellspacing="1" class="gridtable">
            <xsl:if test="@cellspacing"><xsl:attribute name="cellspacing"><xsl:value-of select="@cellspacing"/></xsl:attribute></xsl:if>
            <xsl:if test="@cellpadding"><xsl:attribute name="cellpadding"><xsl:value-of select="@cellpadding"/></xsl:attribute></xsl:if>
            <xsl:if test="@border"><xsl:attribute name="border"><xsl:value-of select="@border"/></xsl:attribute></xsl:if>
            <xsl:if test="@class"><xsl:attribute name="class"><xsl:value-of select="@class"/></xsl:attribute></xsl:if>
            <xsl:if test="@bgcolor"><xsl:attribute name="bgcolor"><xsl:value-of select="@bgcolor"/></xsl:attribute></xsl:if>
            <xsl:apply-templates/>
        </table>
    </xsl:template>

</xsl:stylesheet>

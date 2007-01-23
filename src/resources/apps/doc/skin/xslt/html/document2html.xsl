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
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:include href = "split.xsl"/>
    <xsl:include href = "oxf:/ops/utils/formatting/xml-formatting.xsl"/>

    <xsl:param name="isfaq"/>
    <xsl:param name="resource"/>
    <xsl:template match="document">
        <div class="content">
            <br/>
            <xsl:if test="normalize-space(header/title)!=''">
                <div class="title">
                    <h1><xsl:value-of select="header/title"/></h1>
                </div>
            </xsl:if>

<!--            <xsl:if test="normalize-space(header/title)!=''">-->
<!--                <table class="title">-->
<!--                    <tr>-->
<!--                        <td valign="top">-->
<!--                            <h1>-->
<!--                                <xsl:value-of select="header/title"/>-->
<!--                            </h1>-->
<!--                        </td>-->
                        <!--
                        <td align="right" width="1%">
                            <div style="border-style: solid; border-width: 1px; border-color: #666699; background-color: #F2F2F2; margin-left: 1em">
                                <div style="text-align: left; margin-left: 1em; margin-top: 0.5em; margin-bottom: 0.5em;">
                                    <ul style="list-style-image: url('skin/images/dark-blue-bullet.png'); margin-top: 0.5em; margin-bottom: 0.5em; margin-right: 1em; line-height: 1.5em">
                                        <li style="margin-left: -20px; white-space: nowrap">Go to the <a href="http://www.orbeon.com/oxf/">OXF Home Page</a></li>
                                        <li style="margin-left: -20px; white-space: nowrap"><a href="http://www.orbeon.com/oxf/download">Download</a> OXF 2.0 beta or OXF 1.5.2</li>
                                        <li style="margin-left: -20px; white-space: nowrap">See the OXF 2.0 <a href="http://www.orbeon.com/oxf/examples/">showcase application</a></li>
                                        <li style="margin-left: -20px; white-space: nowrap">Get the <a href="http://www.orbeon.com/oxf/oxf-brochure.pdf">OXF Brochure</a> (PDF)</li>
                                    </ul>
                                </div>
                            </div>
                        </td>
                        -->
<!--                    </tr>-->
<!--                </table>-->
<!--            </xsl:if>-->
            <xsl:if test="normalize-space(header/subtitle)!=''">
                <h3>
                    <xsl:value-of select="header/subtitle"/>
                </h3>
            </xsl:if>
            <xsl:if test="header/authors">
                <p>
                    <font size="-2">
                        <xsl:for-each select="header/authors/person">
                            <xsl:choose>
                                <xsl:when test="position()=1">by&#160;</xsl:when>
                                <xsl:otherwise>,&#160;</xsl:otherwise>
                            </xsl:choose>
                            <xsl:value-of select="@name"/>
                        </xsl:for-each>
                    </font>
                </p>
            </xsl:if>
            <xsl:apply-templates select="body"/>
        </div>
    </xsl:template>
    <xsl:template match="body">
        <xsl:if test="section and not($isfaq='true')">
            <ul class="minitoc">
                <xsl:for-each select="section">
                    <li>
                        <xsl:number level="single" count="section" format="1. "/>
                        <a href="#{generate-id()}">
                            <xsl:value-of select="title"/>
                        </a>
                        <xsl:if test="section">
                            <ul class="minitoc">
                                <xsl:variable name="subsection-count" select="count(section)"/>
                                <xsl:for-each select="section">
                                    <li>
                                        <xsl:if test="position() = 1">
                                            <xsl:attribute name="style">margin-top: 0.5em</xsl:attribute>
                                        </xsl:if>
                                        <xsl:if test="position() = $subsection-count">
                                            <xsl:attribute name="style">margin-bottom: 0.5em</xsl:attribute>
                                        </xsl:if>
                                        <xsl:number level="multiple" count="section" format="1.1 "/>
                                        <a href="#{generate-id()}">
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
        <xsl:apply-templates/>
    </xsl:template>
    <!--  section handling
    - <a name/> anchors are added if the id attribute is specified
    - generated anchors are still included for TOC - what should we do about this?
    - FIXME: provide a generic facility to process section irrelevant to their
    nesting depth
    -->
    <xsl:template match="section">
        <a name="{generate-id()}"/>
        <xsl:if test="normalize-space(@id)!=''">
            <a name="{@id}"/>
        </xsl:if>
        <h3>
            <xsl:number level="single" count="section" format="1. "/>
            <xsl:value-of select="title"/>
        </h3>
        <xsl:apply-templates select="*[not(self::title)]"/>
    </xsl:template>
    <xsl:template match="section/section">
        <a name="{generate-id()}"/>
        <xsl:if test="normalize-space(@id)!=''">
            <a name="{@id}"/>
        </xsl:if>
        <h4>
            <xsl:number level="multiple" count="section" format="1.1 "/>
            <xsl:value-of select="title"/>
        </h4>
        <xsl:apply-templates select="*[not(self::title)]"/>
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
    <xsl:template match="jump">
        <a href="{@href}" target="_top">
            <xsl:apply-templates/>
        </a>
    </xsl:template>
    <xsl:template match="fork">
        <a href="{@href}" target="_blank">
            <xsl:apply-templates/>
        </a>
    </xsl:template>
    <xsl:template match="source">
        <pre class="code">
            <!-- Temporarily removed long-line-splitter ... gives out-of-memory problems -->
            <xsl:apply-templates/>
            <!--
            <xsl:call-template name="format">
            <xsl:with-param select="." name="txt" />
            <xsl:with-param name="width">80</xsl:with-param>
            </xsl:call-template>
            -->
        </pre>
    </xsl:template>
    <xsl:template match="xml-source">
        <div style="margin-bottom: 1em" class="code">
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
        </div>
    </xsl:template>
    <xsl:template match="anchor">
        <a name="{@id}"/>
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
        <p>
            <img class="ops-doc-image">
                <xsl:copy-of select="@*"/>
            </img>
        </p>
    </xsl:template>
    <xsl:template match="code">
        <span class="codefrag"><xsl:apply-templates/></span>
    </xsl:template>
    <xsl:template match="figure">
        <div align="center">
            <img src="{@src}" alt="{@alt}" class="ops-doc-figure">
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
        <table cellpadding="4" cellspacing="1" class="ForrestTable">
            <xsl:if test="@cellspacing"><xsl:attribute name="cellspacing"><xsl:value-of select="@cellspacing"/></xsl:attribute></xsl:if>
            <xsl:if test="@cellpadding"><xsl:attribute name="cellpadding"><xsl:value-of select="@cellpadding"/></xsl:attribute></xsl:if>
            <xsl:if test="@border"><xsl:attribute name="border"><xsl:value-of select="@border"/></xsl:attribute></xsl:if>
            <xsl:if test="@class"><xsl:attribute name="class"><xsl:value-of select="@class"/></xsl:attribute></xsl:if>
            <xsl:if test="@bgcolor"><xsl:attribute name="bgcolor"><xsl:value-of select="@bgcolor"/></xsl:attribute></xsl:if>
            <xsl:apply-templates/>
        </table>
    </xsl:template>
    <xsl:template match="node()|@*" priority="-1">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xsl:apply-templates/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>

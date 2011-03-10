<!--
  Copyright (C) 2010 Orbeon, Inc.

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
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:version="java:org.orbeon.oxf.common.Version">

    <!-- XML formatting -->
    <xsl:import href="oxf:/ops/utils/formatting/formatting.xsl"/>

    <!-- This contains some useful request information -->
    <xsl:variable name="request" select="doc('input:request')" as="document-node()"/>

    <!-- List of applications -->
    <xsl:variable name="applications" select="doc('../apps-list.xml')" as="document-node()"/>
    <!-- Current navigation -->
    <xsl:variable name="current-application-id" select="tokenize(doc('input:request')/*/request-path, '/')[2]" as="xs:string"/>
    <xsl:variable name="current-application-remaining" select="tokenize(doc('input:request')/*/request-path, '/')[3]" as="xs:string?"/>
    <xsl:variable name="is-form-runner-home" select="$current-application-id = 'home' and $current-application-remaining = ''" as="xs:boolean"/>

    <!-- Try to obtain a meaningful title for the example -->
    <xsl:variable name="title" select="if (/xhtml:html/xhtml:head/xhtml:title != '')
                                       then /xhtml:html/xhtml:head/xhtml:title
                                       else if (/xhtml:html/xhtml:body/xhtml:h1)
                                            then (/xhtml:html/xhtml:body/xhtml:h1)[1]
                                            else '[Untitled]'" as="xs:string"/>
    <!-- Orbeon Forms version -->
    <xsl:variable name="orbeon-forms-version" select="version:getVersionString()" as="xs:string"/>

    <!-- - - - - - - Themed page template - - - - - - -->
    <xsl:template match="/">
        <xhtml:html>
            <xsl:apply-templates select="/xhtml:html/@*"/>
            <xhtml:head>
                <!-- Handle head elements except scripts -->
                <xsl:apply-templates select="/xhtml:html/xhtml:head/(xhtml:meta | xhtml:link | xhtml:style)"/>
                <!-- CSS and title -->
                <xhtml:link rel="stylesheet" href="/config/theme/orbeon.css" type="text/css" media="all"/>
                <xhtml:title>Orbeon Forms Example Applications - <xsl:value-of select="$title"/></xhtml:title>
                <!-- Orbeon Forms version -->
                <xhtml:meta name="generator" content="{$orbeon-forms-version}"/>
                <!-- Favicon -->
                <xhtml:link rel="shortcut icon" href="/ops/images/orbeon-icon-16.ico"/>
                <xhtml:link rel="icon" href="/ops/images/orbeon-icon-16.png" type="image/png"/>
                <!-- Handle head scripts if present -->
                <xsl:apply-templates select="/xhtml:html/xhtml:head/xhtml:script"/>
            </xhtml:head>
            <xhtml:body>
                <!-- Copy body attributes -->
                <xsl:apply-templates select="/xhtml:html/xhtml:body/@*"/>

                <xhtml:div id="orbeon" class="orbeon">
                    <!-- Banner -->
                    <xhtml:div class="orbeon-banner">
                        <xhtml:div style="float: left">
                            <xhtml:a href="/" f:url-norewrite="true">
                                <xhtml:img f:url-norewrite="false" width="212" height="42"
                                           style="border: 0 white; margin-left: 1em; margin-top: 0.2em; margin-bottom: 0.4em"
                                           src="/config/theme/images/orbeon-logo-trimmed-transparent-42.png" alt='home'/>
                            </xhtml:a>
                        </xhtml:div>
                        <xhtml:span style="float: right; margin-right: 1em; margin-top: .2em; white-space: nowrap">
                            <form method="GET" class="blue" style="margin:0.2em; margin-bottom:0"
                                  action="http://www.google.com/custom">
                                <xhtml:a href="http://www.orbeon.com/" f:url-norewrite="true">Orbeon.com</xhtml:a>
                                |
                                <xhtml:a href="http://wiki.orbeon.com/forms/" target="_blank">Orbeon Documentation Wiki</xhtml:a>
                                |
                                <xhtml:span style="white-space: nowrap">
                                    Search:
                                    <input type="text" name="q" size="10" maxlength="255" value=""/>
                                    <input type="submit" name="sa" value="Go" style="margin-left: 0.2em;"/>
                                </xhtml:span>
                                <input type="hidden" name="cof"
                                       value="GIMP:#FF9900;T:black;LW:510;ALC:#FF9900;L:http://www.orbeon.com/pics/orbeon-google.png;GFNT:#666699;LC:#666699;LH:42;BGC:#FFFFFF;AH:center;VLC:#666699;GL:0;S:http://www.orbeon.com;GALT:#FF9900;AWFID:8ac636f034abb7d8;"/>
                                <input type="hidden" name="sitesearch" value="orbeon.com"/>
                            </form>
                        </xhtml:span>
                    </xhtml:div>
                    <!-- Tabs -->
                    <xhtml:div class="orbeon-tabs">
                        <xsl:choose>
                            <xsl:when test="not($is-form-runner-home)">
                                <xhtml:a class="tab" href="/home/">Form Builder &amp; Sample Forms</xhtml:a>
                                <xhtml:span class="tab-selected-left">&#160;</xhtml:span>
                                <xhtml:span class="tab-selected">XForms Controls &amp; Demo Apps</xhtml:span>
                            </xsl:when>
                            <xsl:otherwise>
                                <xhtml:span class="tab-selected-left">&#160;</xhtml:span>
                                <xhtml:span class="tab-selected">Form Builder &amp; Sample Forms</xhtml:span>
                                <xhtml:a class="tab" href="/home/xforms">XForms Controls &amp; Demo Apps</xhtml:a>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xhtml:div>
                    <xhtml:table class="orbeon-content">
                        <xhtml:tr>
                            <!--List of examples -->
                            <xsl:if test="not($is-form-runner-home)">
                                <xhtml:td class="orbeon-leftcontent">
                                    <h1>Orbeon Forms Apps</h1>
                                    <xhtml:ul class="tree-sections">
                                        <xsl:for-each select="$applications/*/section">
                                            <xhtml:li class="tree-section">
                                                <xsl:value-of select="@label"/>
                                            </xhtml:li>
                                            <xhtml:ul class="tree-items">
                                                <xsl:for-each select="application">
                                                    <xsl:variable name="selected" as="xs:boolean" select="@id = $current-application-id"/>
                                                    <xhtml:li class="{if ($selected) then 'tree-items-selected' else 'tree-items'}" style="white-space: nowrap">
                                                        <xsl:choose>
                                                            <xsl:when test="$selected">
                                                                <xsl:value-of select="@label"/>
                                                            </xsl:when>
                                                            <xsl:otherwise>
                                                                <xhtml:a href="/{@id}{if (tokenize(@id, '/')[1] != 'fr') then '/' else ''}">
                                                                    <xsl:value-of select="@label"/>
                                                                </xhtml:a>
                                                            </xsl:otherwise>
                                                        </xsl:choose>
                                                    </xhtml:li>
                                                </xsl:for-each>
                                            </xhtml:ul>
                                        </xsl:for-each>
                                    </xhtml:ul>
                                </xhtml:td>
                            </xsl:if>
                            <xhtml:td class="orbeon-maincontent">
                                <xhtml:div class="maincontent">
                                    <!-- Title -->
                                    <xhtml:h1>
                                        <!-- Title -->
                                        <xsl:value-of select="$title"/>
                                    </xhtml:h1>
                                    <!-- Source code if needed -->
                                    <xsl:if test="normalize-space($current-application-id) and $current-application-id != 'home'">
                                        <xhtml:div class="orbeon-source">
                                            <a href="https://github.com/orbeon/orbeon-forms/tree/master/src/resources/apps/{$current-application-id}/" target="_blank">View the source code of this demo on</a>
                                            <a href="https://github.com/orbeon/orbeon-forms/tree/master/src/resources/apps/{$current-application-id}/" target="_blank"><xhtml:img src="/config/theme/images/github.png" alt="github"/></a>
                                        </xhtml:div>
                                    </xsl:if>
                                    <!-- Body -->
                                    <xhtml:div class="orbeon-mainbody">
                                        <xsl:apply-templates select="/xhtml:html/xhtml:body/node()"/>
                                    </xhtml:div>
                                </xhtml:div>
                            </xhtml:td>
                        </xhtml:tr>
                    </xhtml:table>
                    <xhtml:p class="orbeon-version">Orbeon Forms <xsl:value-of select="$orbeon-forms-version"/></xhtml:p>
                </xhtml:div>
            </xhtml:body>
            <!-- Handle post-body scripts if present. They can be placed here by oxf:resources-aggregator -->
            <xsl:apply-templates select="/xhtml:html/xhtml:script"/>
        </xhtml:html>
    </xsl:template>

    <!-- Simply copy everything that's not matched -->
    <xsl:template match="@*|node()" priority="-2">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>

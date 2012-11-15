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
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:xf="http://www.w3.org/2002/xforms"
    xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
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
    <xsl:variable name="title" select="if (/xh:html/xh:head/xh:title != '')
                                       then /xh:html/xh:head/xh:title
                                       else if (/xh:html/xh:body/xh:h1)
                                            then (/xh:html/xh:body/xh:h1)[1]
                                            else '[Untitled]'" as="xs:string"/>
    <!-- Orbeon Forms version -->
    <xsl:variable name="orbeon-forms-version" select="version:getVersionString()" as="xs:string"/>

    <!-- - - - - - - Themed page template - - - - - - -->
    <xsl:template match="/">
        <!-- Copy PIs before root element in particular so that orbeon-serializer PIs are propagated -->
        <xsl:apply-templates select="processing-instruction()"/>
        <xh:html>
            <xsl:apply-templates select="/xh:html/@*"/>
            <xh:head>
                <!-- Handle head elements except scripts -->
                <xsl:apply-templates select="/xh:html/xh:head/(xh:meta | xh:link | xh:style)"/>
                <!-- CSS and title -->
                <xh:link rel="stylesheet" href="/config/theme/orbeon.css" type="text/css" media="all"/>
                <xh:title>Orbeon Forms Example Applications - <xsl:value-of select="$title"/></xh:title>
                <!-- Orbeon Forms version -->
                <xh:meta name="generator" content="{$orbeon-forms-version}"/>
                <!-- Favicon -->
                <xh:link rel="shortcut icon" href="/ops/images/orbeon-icon-16.ico"/>
                <xh:link rel="icon" href="/ops/images/orbeon-icon-16.png" type="image/png"/>
                <!-- Handle head scripts if present -->
                <xsl:apply-templates select="/xh:html/xh:head/xh:script"/>
            </xh:head>
            <xh:body>
                <!-- Copy body attributes -->
                <xsl:apply-templates select="/xh:html/xh:body/@*"/>

                <xh:div id="orbeon" class="orbeon">
                    <!-- Banner -->
                    <xh:div class="orbeon-banner">
                        <xh:div style="float: left">
                            <xh:a href="/" f:url-norewrite="true">
                                <xh:img f:url-norewrite="false" width="212" height="42"
                                           style="border: 0 white; margin-left: 1em; margin-top: 0.2em; margin-bottom: 0.4em"
                                           src="/config/theme/images/orbeon-logo-trimmed-transparent-42.png" alt='home'/>
                            </xh:a>
                        </xh:div>
                        <xh:span style="float: right; margin-right: 1em; margin-top: .2em; white-space: nowrap">
                            <xh:form method="GET" class="blue" style="margin:0.2em; margin-bottom:0"
                                  action="http://www.google.com/custom">
                                <xh:a href="http://www.orbeon.com/" f:url-norewrite="true">Orbeon.com</xh:a>
                                |
                                <xh:a href="http://wiki.orbeon.com/forms/" target="_blank">Orbeon Documentation Wiki</xh:a>
                                |
                                <xh:span style="white-space: nowrap">
                                    Search:
                                    <xh:input type="text" name="q" size="10" maxlength="255" value=""/>
                                    <xh:input type="submit" name="sa" value="Go" style="margin-left: 0.2em;"/>
                                </xh:span>
                                <xh:input type="hidden" name="cof"
                                       value="GIMP:#FF9900;T:black;LW:510;ALC:#FF9900;L:http://www.orbeon.com/pics/orbeon-google.png;GFNT:#666699;LC:#666699;LH:42;BGC:#FFFFFF;AH:center;VLC:#666699;GL:0;S:http://www.orbeon.com;GALT:#FF9900;AWFID:8ac636f034abb7d8;"/>
                                <xh:input type="hidden" name="sitesearch" value="orbeon.com"/>
                            </xh:form>
                        </xh:span>
                    </xh:div>
                    <!-- Tabs -->
                    <xh:div class="orbeon-tabs">
                        <xsl:choose>
                            <xsl:when test="not($is-form-runner-home)">
                                <xh:a class="tab" href="/home/">Form Builder &amp; Sample Forms</xh:a>
                                <xh:span class="tab-selected-left">&#160;</xh:span>
                                <xh:span class="tab-selected">XForms Controls &amp; Demo Apps</xh:span>
                            </xsl:when>
                            <xsl:otherwise>
                                <xh:span class="tab-selected-left">&#160;</xh:span>
                                <xh:span class="tab-selected">Form Builder &amp; Sample Forms</xh:span>
                                <xh:a class="tab" href="/home/xforms">XForms Controls &amp; Demo Apps</xh:a>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xh:div>
                    <xh:table class="orbeon-content">
                        <xh:tr>
                            <!--List of examples -->
                            <xsl:if test="not($is-form-runner-home)">
                                <xh:td class="orbeon-leftcontent">
                                    <h1>Orbeon Forms Apps</h1>
                                    <xh:ul class="tree-sections">
                                        <xsl:for-each select="$applications/*/section">
                                            <xh:li class="tree-section">
                                                <xsl:value-of select="@label"/>
                                            </xh:li>
                                            <xh:ul class="tree-items">
                                                <xsl:for-each select="application">
                                                    <xsl:variable name="selected" as="xs:boolean" select="@id = $current-application-id"/>
                                                    <xh:li class="{if ($selected) then 'tree-items-selected' else 'tree-items'}" style="white-space: nowrap">
                                                        <xsl:choose>
                                                            <xsl:when test="$selected">
                                                                <xsl:value-of select="@label"/>
                                                            </xsl:when>
                                                            <xsl:otherwise>
                                                                <xh:a href="/{@id}{if (tokenize(@id, '/')[1] != 'fr') then '/' else ''}">
                                                                    <xsl:value-of select="@label"/>
                                                                </xh:a>
                                                            </xsl:otherwise>
                                                        </xsl:choose>
                                                    </xh:li>
                                                </xsl:for-each>
                                            </xh:ul>
                                        </xsl:for-each>
                                    </xh:ul>
                                </xh:td>
                            </xsl:if>
                            <xh:td class="orbeon-maincontent">
                                <xh:div class="maincontent">
                                    <!-- Title -->
                                    <xh:h1>
                                        <!-- Title -->
                                        <xsl:value-of select="$title"/>
                                    </xh:h1>
                                    <!-- Source code if needed -->
                                    <xsl:if test="normalize-space($current-application-id) and $current-application-id != 'home'">
                                        <xh:div class="orbeon-source">
                                            <a href="https://github.com/orbeon/orbeon-forms/tree/master/src/resources/apps/{$current-application-id}/" target="_blank">View the source code of this demo on</a>
                                            <a href="https://github.com/orbeon/orbeon-forms/tree/master/src/resources/apps/{$current-application-id}/" target="_blank"><xh:img src="/config/theme/images/github.png" alt="github"/></a>
                                        </xh:div>
                                    </xsl:if>
                                    <!-- Body -->
                                    <xh:div class="orbeon-mainbody">
                                        <xsl:apply-templates select="/xh:html/xh:body/node()"/>
                                    </xh:div>
                                </xh:div>
                            </xh:td>
                        </xh:tr>
                    </xh:table>
                    <xh:p class="orbeon-version">Orbeon Forms <xsl:value-of select="$orbeon-forms-version"/></xh:p>
                </xh:div>
            </xh:body>
            <!-- Handle post-body scripts if present. They can be placed here by oxf:resources-aggregator -->
            <xsl:apply-templates select="/xh:html/xh:script"/>
        </xh:html>
    </xsl:template>

    <!-- Simply copy everything that's not matched -->
    <xsl:template match="@*|node()" priority="-2">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>

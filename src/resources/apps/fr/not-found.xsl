<?xml version="1.0" encoding="UTF-8"?>
<!--
    Copyright (C) 2009 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<?oxf-serializer status-code="404"?>
<xhtml:html xsl:version="2.0"
            xmlns:xhtml="http://www.w3.org/1999/xhtml"
            xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
            xmlns:xs="http://www.w3.org/2001/XMLSchema"
            xmlns:pipeline="java:org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary">

    <xsl:variable name="has-version" select="pipeline:property('oxf.fr.version.*.*')" as="xs:boolean?"/>
    <xsl:variable name="default-logo-uri" select="pipeline:property('oxf.fr.default-logo.uri.*.*')" as="xs:string?"/>
    <xsl:variable name="css-uri" select="tokenize(normalize-space(pipeline:property('oxf.fr.css.uri.*.*')), '\s+')" as="xs:string*"/>

    <xhtml:head>
        <xhtml:title>Not Found</xhtml:title>

        <!-- Form Runner CSS stylesheets -->
        <xsl:for-each select="$css-uri">
            <xhtml:link rel="stylesheet" href="{.}" type="text/css" media="all"/>
        </xsl:for-each>
    </xhtml:head>
    <xhtml:body>
        <xhtml:div id="fr-view">
            <xhtml:div id="doc4">
                <xhtml:div class="fr-header">
                    <!-- Logo -->
                    <xhtml:div class="fr-logo">
                        <xhtml:img src="{$default-logo-uri}" alt="Logo"/>
                    </xhtml:div>
                </xhtml:div>
                <xhtml:div id="hd" class="fr-shadow">&#160;</xhtml:div>
                <xhtml:div id="bd" class="fr-container">
                    <xhtml:div id="yui-main">
                        <xhtml:div class="yui-b">
                            <xhtml:div class="yui-g fr-top">
                                <xhtml:h1 class="fr-form-title">
                                    Not Found
                                </xhtml:h1>
                            </xhtml:div>
                            <xhtml:div class="yui-g fr-separator">&#160;</xhtml:div>
                            <xhtml:div class="yui-g fr-body">
                                <xhtml:div class="fr-not-found">
                                    <xhtml:a name="fr-form"/>
                                    <xhtml:p>
                                        Oops, the page requested was not found!
                                    </xhtml:p>
                                </xhtml:div>
                            </xhtml:div>
                            <xhtml:div class="yui-g fr-separator">&#160;</xhtml:div>
                        </xhtml:div>
                    </xhtml:div>
                </xhtml:div>
                <xhtml:div id="ft" class="fr-footer">
                    <xsl:if test="not($has-version = false())">
                        <xsl:variable xmlns:version="java:org.orbeon.oxf.common.Version" name="orbeon-forms-version" select="version:getVersion()" as="xs:string"/>
                        <xhtml:div class="fr-orbeon-version">Orbeon Forms <xsl:value-of select="$orbeon-forms-version"/></xhtml:div>
                    </xsl:if>
                </xhtml:div>
            </xhtml:div>
        </xhtml:div>
    </xhtml:body>
</xhtml:html>
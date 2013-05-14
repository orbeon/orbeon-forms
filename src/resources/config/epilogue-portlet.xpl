<!--
    Copyright (C) 2005 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<!--
    The epilogue is run after all page views. This is the part of the epilogue called to handle Orbeon Forms
    applications running in a portlet container. It is typically used to perform tasks that need to be done for all
    views, for example applying a common theme, serializing the pages to HTML or XML, etc.
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fo="http://www.w3.org/1999/XSL/Format"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">

    <!-- The document produced by the page view XForms processing performed -->
    <p:param type="input" name="xformed-data"/>
    <!-- The raw document produced by the page view -->
<!--    <p:param type="input" name="data"/>-->
    <!-- The XML submission if any -->
<!--    <p:param type="input" name="instance"/>-->

    <!-- Get request information -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/container-type</include>
                <include>/request/request-path</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!-- The container is a portlet -->
    <p:choose href="#xformed-data">
        <!-- XHTML detection. Don't transform the content. -->
        <p:when test="/xhtml:html">
            <p:processor name="oxf:identity">
                <p:input name="data" href="#xformed-data"/>
                <p:output name="data" id="xformed-data-2"/>
            </p:processor>
        </p:when>
        <!-- No particular document format detected. Create an XHTML document which formats the XML content. -->
        <p:otherwise>
            <p:processor name="oxf:unsafe-xslt">
                <p:input name="data" href="#xformed-data"/>
                <p:input name="request" href="#request"/>
                <p:input name="config">
                    <xsl:stylesheet version="2.0"
                            xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml">
                        <xsl:template match="/">
                            <xhtml:html>
                                <xhtml:head><xhtml:title>XML Document</xhtml:title></xhtml:head>
                                <xhtml:body>
                                    <f:xml-source>
                                        <xsl:copy-of select="/*"/>
                                    </f:xml-source>
                                </xhtml:body>
                            </xhtml:html>
                        </xsl:template>
                    </xsl:stylesheet>
                </p:input>
                <p:output name="data" id="xformed-data-2"/>
            </p:processor>
        </p:otherwise>
    </p:choose>
    <!-- Extract a fragment and apply theme -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#xformed-data-2"/>
        <p:input name="request" href="#request"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0"
                    xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml"
                    xmlns:context="java:org.orbeon.oxf.pipeline.StaticExternalContext">
                <xsl:import href="oxf:/config/theme-plain.xsl"/>
                <xsl:template match="/">
                    <xhtml:div xmlns:f="http://orbeon.org/oxf/xml/formatting" class="ops-portlet-div">
                        <!-- Styles and scripts -->
                        <xhtml:link rel="stylesheet" href="/config/theme/orbeon.css" type="text/css"/>
                        <xsl:apply-templates select="/xhtml:html/xhtml:head/(xhtml:style | xhtml:link | xhtml:script)"/>
                        <!-- Try to get a title -->
                        <xsl:if test="normalize-space(/xhtml:html/xhtml:head/xhtml:title)">
                            <xsl:value-of select="context:setTitle(normalize-space(/xhtml:html/xhtml:head/xhtml:title))"/>
                        </xsl:if>
                        <xhtml:div class="ops-portlet-content">
                            <xsl:apply-templates select="/xhtml:html/xhtml:body/node()"/>
                        </xhtml:div>
                        <xhtml:div class="ops-portlet-home">
                            <xhtml:a href="/" f:portlet-mode="view">Back to portlet home</xhtml:a>
                        </xhtml:div>
                    </xhtml:div>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="themed-data"/>
    </p:processor>
    <!-- Rewrite all URLs in XHTML documents -->
    <p:processor name="oxf:xhtml-rewrite" >
        <p:input name="rewrite-in" href="#themed-data"/>
        <p:output name="rewrite-out" id="rewritten-data" />
    </p:processor>
    <!-- Move from XHTML namespace to no namespace -->
    <p:processor name="oxf:qname-converter">
        <p:input name="config">
            <config>
                <match>
                    <uri>http://www.w3.org/1999/xhtml</uri>
                </match>
                <replace>
                    <uri></uri>
                    <prefix></prefix>
                </replace>
            </config>
        </p:input>
        <p:input name="data" href="#rewritten-data"/>
        <p:output name="data" id="html-data"/>
    </p:processor>
    <!-- Convert and serialize to HTML -->
    <p:processor name="oxf:html-converter">
        <p:input name="config">
            <config>
                <!-- Not necessary to indent at this point -->
                <indent>false</indent>
                <!-- Do not output any doctype, this is a fragment -->
            </config>
        </p:input>
        <p:input name="data" href="#html-data"/>
        <p:output name="data" id="converted"/>
    </p:processor>
    <p:processor name="oxf:http-serializer">
        <p:input name="config">
            <config>
                <!-- Disable caching, so that the title is always generated -->
                <cache-control>
                    <use-local-cache>false</use-local-cache>
                </cache-control>
            </config>
        </p:input>
        <p:input name="data" href="#converted"/>
    </p:processor>

</p:config>

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
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fo="http://www.w3.org/1999/XSL/Format"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xforms="http://www.w3.org/2002/xforms">

    <p:param type="input" name="data"/>
    <p:param type="input" name="instance"/>
    <p:param type="input" name="xforms-model"/>

    <!-- Get container type -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/container-type</include>
            </config>
        </p:input>
        <p:output name="data" id="container-type"/>
    </p:processor>

    <!-- Annotate XForms elements and generate XHTML if necessary -->
    <p:choose href="#xforms-model">
        <p:when test="/xforms:model">
            <p:processor name="oxf:xforms-output">
                <p:input name="model" href="#xforms-model"/>
                <p:input name="instance" href="#instance"/>
                <p:input name="data" href="#data"/>
                <p:output name="data" id="annotated-data"/>
            </p:processor>
            <!-- Transform annotated XForms to XHTML -->
            <p:processor name="oxf:xslt"><!-- saxon1 -->
                <p:input name="config" href="xforms-to-xhtml.xsl"/>
                <p:input name="model" href="#xforms-model"/>
                <p:input name="data" href="#annotated-data"/>
                <p:output name="data" id="xhtml-data"/>
            </p:processor>
            <!-- Aggregate hidden fields -->
            <p:processor name="oxf:xslt"> <!-- saxon2 -->
                <p:input name="config" href="xforms-hidden.xsl"/>
                <p:input name="data" href="#xhtml-data"/>
                <p:output name="data" id="xhtml-data-hidden"/>
            </p:processor>
            <p:choose href="#container-type">
                <p:when test="/request/container-type = 'servlet'">
                    <!-- Handle portlet forms (you can skip this step if you are not including portlets in your page) -->
                    <p:processor name="oxf:xslt"> <!-- saxon3 -->
                        <p:input name="config" href="xforms-portlet-forms.xsl"/>
                        <p:input name="data" href="#xhtml-data-hidden"/>
                        <p:input name="annotated-data" href="#annotated-data"/>
                        <p:output name="data" id="xformed-data"/>
                    </p:processor>
                </p:when>
                <p:otherwise>
                    <!-- Don't go through this step if we are implementing a portlet -->
                    <p:processor name="oxf:identity">
                        <p:input name="data" href="#xhtml-data-hidden"/>
                        <p:output name="data" id="xformed-data"/>
                    </p:processor>
                </p:otherwise>
            </p:choose>
        </p:when>
        <p:otherwise>
            <p:processor name="oxf:identity">
                <p:input name="data" href="#data"/>
                <p:output name="data" id="xformed-data"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

    <p:choose  href="#container-type">
        <p:when test="/request/container-type = 'servlet'">
            <!-- Servlet -->
            <p:choose href="#xformed-data">
                <!-- Auto-detected XSL-FO. Use the FOP Serializer -->
                <p:when test="/fo:root">
                    <p:processor name="oxf:pdf-serializer">
                        <p:input name="config">
                            <config>
                                <header>
                                   <name>Content-Disposition</name>
                                    <value>attachment; filename=document.pdf;</value>
                                </header>
                            </config>
                        </p:input>
                        <p:input name="data" href="#xformed-data"/>
                    </p:processor>
                </p:when>
                <!-- Regular HTML -->
                <p:otherwise>
                    <!-- Apply theme -->
                    <p:processor name="oxf:xslt"> <!-- saxon4 -->
                        <p:input name="data" href="#xformed-data"/>
                        <p:input name="config" href="oxf:/oxf-theme/theme.xsl"/>
                        <p:output name="data" id="themed-data"/>
                    </p:processor>
                    <!-- Rewrite all URLs in HTML and XHTML documents -->
                    <p:processor name="oxf:xslt"><!-- saxon5 -->
                        <p:input name="data" href="#themed-data"/>
                        <p:input name="container-type" href="#container-type"/>
                        <p:input name="config" href="oxf:/oxf/pfc/oxf-rewrite.xsl"/>
                        <p:output name="data" id="rewritten-data"/>
                    </p:processor>
                    <!-- Output regular HTML doctype -->
                    <p:processor name="oxf:html-serializer">
                        <p:input name="config">
                            <config>
                                <public-doctype>-//W3C//DTD HTML 4.01 Transitional//EN</public-doctype>
                                <version>4.01</version>
                                <encoding>utf-8</encoding>
                                <header>
                                    <name>Cache-Control</name>
                                    <value>post-check=0, pre-check=0</value>
                                </header>
                            </config>
                        </p:input>
                        <p:input name="data" href="#rewritten-data"/>
                    </p:processor>
                </p:otherwise>
            </p:choose>
        </p:when>
        <p:otherwise>
            <!-- Portlet -->
            <!-- Extract a fragment and apply theme -->
            <p:processor name="oxf:xslt"> <!-- saxon4 -->
                <p:input name="data" href="#xformed-data"/>
                <p:input name="config">
                    <xsl:stylesheet version="1.0"
                            xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml"
                            xmlns:context="java:org.orbeon.oxf.pipeline.StaticExternalContext">
                        <xsl:import href="oxf:/oxf-theme/theme.xsl"/>
                        <xsl:template match="/">
                            <!-- Try to output a title -->
                            <xsl:if test="normalize-space(/xhtml:html/xhtml:head/xhtml:title)">
                                <xsl:value-of select="context:setTitle(normalize-space(/xhtml:html/xhtml:head/xhtml:title))"/>
                            </xsl:if>
                            <div>
                                <xsl:apply-templates select="/xhtml:html/xhtml:body/node()"/>
                            </div>
                        </xsl:template>
                    </xsl:stylesheet>
                </p:input>
                <p:output name="data" id="themed-data"/>
            </p:processor>
            <!-- Rewrite all URLs in HTML and XHTML documents -->
            <p:processor name="oxf:xslt"><!-- saxon5 -->
                <p:input name="data" href="#themed-data"/>
                <p:input name="container-type" href="#container-type"/>
                <p:input name="config" href="oxf:/oxf/pfc/oxf-rewrite.xsl"/>
                <p:output name="data" id="rewritten-data"/>
            </p:processor>
            <!-- Serialize to HTML -->
            <p:processor name="oxf:xml-serializer">
                <p:input name="config">
                    <config>
                        <!-- Disable caching, so that the title is always generated -->
                        <cache-control>
                            <use-local-cache>false</use-local-cache>
                        </cache-control>
                        <!-- Do not output any doctype, this is a fragment -->
                    </config>
                </p:input>
                <p:input name="data" href="#rewritten-data"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>

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
    The epilogue is run after all page views. It is typically used to perform tasks that need to be
    done for all views, for example running the XForms engine, applying a common theme, serialize
    the pages to HTML or XML, etc.
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fo="http://www.w3.org/1999/XSL/Format"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">

    <p:param type="input" name="data"/>
    <p:param type="input" name="instance"/>
    <p:param type="input" name="xforms-model"/>

    <!-- Get request information -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/container-type</include>
                <include>/request/request-path</include>
                <include>/request/headers/header[name = 'accept']</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
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
            <p:processor name="oxf:unsafe-xslt">
                <p:input name="config" href="xforms-to-xhtml.xsl"/>
                <p:input name="model" href="#xforms-model"/>
                <p:input name="instance" href="#instance"/>
                <p:input name="data" href="#annotated-data"/>
                <p:output name="data" id="xhtml-data"/>
            </p:processor>
            <p:choose href="#request">
                <p:when test="/request/container-type = 'servlet'">
                    <!-- Handle portlet forms (you can skip this step if you are not including portlets in your page) -->
                    <p:processor name="oxf:xslt">
                        <p:input name="config" href="xforms-portlet-forms.xsl"/>
                        <p:input name="data" href="#xhtml-data"/>
                        <p:input name="annotated-data" href="#annotated-data"/>
                        <p:output name="data" id="xformed-data"/>
                    </p:processor>
                </p:when>
                <p:otherwise>
                    <!-- Don't go through this step if we are implementing a portlet -->
                    <p:processor name="oxf:identity">
                        <p:input name="data" href="#xhtml-data"/>
                        <p:output name="data" id="xformed-data"/>
                    </p:processor>
                </p:otherwise>
            </p:choose>
        </p:when>
        <p:otherwise>
            <p:choose href="#data">
                <p:when test="//xforms:model">
                    <!-- Annotate controls in view with and id -->
                    <p:processor name="oxf:xslt">
                        <p:input name="data" href="#data"/>
                        <p:input name="config" href="xforms-annotate-controls.xsl"/>
                        <p:output name="data" id="annotated-view"/>
                    </p:processor>
                    <!-- Extract models -->
                    <p:processor name="oxf:identity">
                        <p:input name="data" href="aggregate('models', #data#xpointer(//xforms:model))"/>
                        <p:output name="data" id="xforms-models"/>
                    </p:processor>
                    <!-- Extract controls -->
                    <p:processor name="oxf:xslt">
                        <p:input name="data" href="#annotated-view"/>
                        <p:input name="config" href="xforms-extract-controls.xsl"/>
                        <p:output name="data" id="xforms-controls"/>
                    </p:processor>
                    <!-- Builds request to XForms server -->
                    <p:processor name="oxf:unsafe-xslt">
                        <p:input name="data"><dummy/></p:input>
                        <p:input name="controls" href="#xforms-controls"/>
                        <p:input name="models" href="#xforms-models"/>
                        <p:input name="config">
                            <xxforms:event-request xsl:version="2.0" 
                                    xmlns:context="java:org.orbeon.oxf.pipeline.StaticExternalContext">
                                <xxforms:static-state>
                                    <xsl:variable name="static-state" as="document-node()">
                                        <xsl:document>
                                            <static-state>
                                                <xsl:copy-of select="doc('input:controls')/*"/>
                                                <xsl:copy-of select="doc('input:models')/*"/>
                                            </static-state>
                                        </xsl:document>
                                    </xsl:variable>
                                    <xsl:value-of select="context:encodeXML($static-state)"/>
                                </xxforms:static-state>
                                <xxforms:dynamic-state/>
                                <xxforms:action/>
                            </xxforms:event-request>
                        </p:input>
                        <p:output name="data" id="request"/>
                    </p:processor>
                    <!-- Get initial instances -->
                    <p:processor name="oxf:xforms-server">
                        <p:input name="request" href="#request"/>
                        <p:output name="response" id="response"/>
                    </p:processor>
                    <p:processor name="oxf:xslt">
                        <p:input name="config" href="xforms-to-ajax-xhtml.xsl"/>
                        <p:input name="data" href="#annotated-view"/>
                        <p:input name="request" href="#request"/>
                        <p:input name="response" href="#response" debug="response"/>
                        <p:output name="data" id="xformed-data" debug="xformed"/>
                    </p:processor>
                </p:when>
                <p:otherwise>
                    <p:processor name="oxf:identity">
                        <p:input name="data" href="#data"/>
                        <p:output name="data" id="xformed-data"/>
                    </p:processor>
                </p:otherwise>
            </p:choose>
        </p:otherwise>
    </p:choose>
    
    <p:choose  href="#request">
        <p:when test="/request/container-type = 'servlet'">
            <!-- Servlet -->
            <p:choose href="#xformed-data">
                <!-- Auto-detected XSL-FO. Use the XSL-FO Serializer -->
                <p:when test="/fo:root">
                    <p:processor name="oxf:xslfo-serializer">
                        <p:input name="config">
                            <config>
                                <header>
                                   <name>Content-Disposition</name>
                                    <value>attachment; filename=document.pdf</value>
                                </header>
                            </config>
                        </p:input>
                        <p:input name="data" href="#xformed-data"/>
                    </p:processor>
                </p:when>
                <!-- Regular XHTML -->
                <p:when test="/xhtml:html">
                    <!-- Apply theme -->
                    <p:processor name="oxf:xslt">
                        <p:input name="data" href="#xformed-data"/>
                        <p:input name="request" href="#request"/>
                        <p:input name="config" href="oxf:/oxf-theme/theme.xsl"/>
                        <p:output name="data" id="themed-data"/>
                    </p:processor>
                    <!-- Rewrite all URLs in HTML and XHTML documents -->
                    <p:processor name="oxf:unsafe-xslt">
                        <p:input name="data" href="#themed-data"/>
                        <p:input name="container-type" href="#request"/>
                        <p:input name="config" href="oxf:/oxf/pfc/oxf-rewrite.xsl"/>
                        <p:output name="data" id="rewritten-data"/>
                    </p:processor>
                    <p:choose href="#request">
                        <!-- Remove the 'false() and ' below to allow browsers supporting XHTML to receive XHTML -->
                        <p:when test="false() and contains(/request/headers/header[name = 'accept'], 'application/xhtml+xml')">
                            <!-- Browser says it supports XHTML -->
                            <!-- Make sure XHTML elements are output without prefix to increase compatibility -->
                            <p:processor name="oxf:qname-converter">
                                <p:input name="config">
                                    <config>
                                        <match>
                                            <uri>http://www.w3.org/1999/xhtml</uri>
                                        </match>
                                        <replace>
                                            <prefix></prefix>
                                        </replace>
                                    </config>
                                </p:input>
                                <p:input name="data" href="#rewritten-data"/>
                                <p:output name="data" id="xhtml-data"/>
                            </p:processor>
                            <!--  Serialize to XHTML -->
                            <p:processor name="oxf:xml-converter">
                                <p:input name="config">
                                    <config>
                                        <method>xhtml</method>
                                        <public-doctype>-//W3C//DTD XHTML 1.0 Transitional//EN</public-doctype>
<!--                                        <public-doctype>-//W3C//DTD xhtml 1.1//EN</public-doctype>-->
                                        <system-doctype>http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd</system-doctype>
<!--                                        <system-doctype>http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd</system-doctype>-->
                                        <encoding>utf-8</encoding>
                                        <content-type>application/xhtml+xml</content-type>
<!--                                        <content-type>text/html</content-type>-->
                                    </config>
                                </p:input>
                                <p:input name="data" href="#xhtml-data"/>
                                <p:output name="data" id="converted"/>
                            </p:processor>
                        </p:when>
                        <p:otherwise>
                            <!-- Just send plain HTML -->
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
                            <!-- Output regular HTML -->
                            <p:processor name="oxf:html-converter">
                                <p:input name="config">
                                    <config>
                                        <public-doctype>-//W3C//DTD HTML 4.01 Transitional//EN</public-doctype>
                                        <version>4.01</version>
                                        <encoding>utf-8</encoding>
                                    </config>
                                </p:input>
                                <p:input name="data" href="#html-data"/>
                                <p:output name="data" id="converted"/>
                            </p:processor>
                        </p:otherwise>
                    </p:choose>
                    <!-- Serialize to HTTP -->
                    <p:processor name="oxf:http-serializer">
                        <p:input name="config">
                            <config>
                                <header>
                                    <name>Cache-Control</name>
                                    <value>post-check=0, pre-check=0</value>
                                </header>
                            </config>
                        </p:input>
                        <p:input name="data" href="#converted"/>
                    </p:processor>
                </p:when>
                <!-- Regular HTML -->
                <p:when test="/html">
                    <!-- Rewrite all URLs in HTML and XHTML documents -->
                    <p:processor name="oxf:unsafe-xslt">
                        <p:input name="data" href="#xformed-data"/>
                        <p:input name="container-type" href="#request"/>
                        <p:input name="config" href="oxf:/oxf/pfc/oxf-rewrite.xsl"/>
                        <p:output name="data" id="rewritten-data"/>
                    </p:processor>
                    <!-- Output regular HTML doctype -->
                    <p:processor name="oxf:html-converter">
                        <p:input name="config">
                            <config>
                                <public-doctype>-//W3C//DTD HTML 4.01 Transitional//EN</public-doctype>
                                <version>4.01</version>
                                <encoding>utf-8</encoding>
                            </config>
                        </p:input>
                        <p:input name="data" href="#rewritten-data"/>
                        <p:output name="data" id="converted"/>
                    </p:processor>
                    <p:processor name="oxf:http-serializer">
                        <p:input name="config">
                            <config>
                                <header>
                                    <name>Cache-Control</name>
                                    <value>post-check=0, pre-check=0</value>
                                </header>
                            </config>
                        </p:input>
                        <p:input name="data" href="#converted"/>
                    </p:processor>
                </p:when>
                <p:otherwise>
                    <!-- Output XML -->
                    <p:processor name="oxf:xml-converter">
                        <p:input name="config">
                            <config>
                                <encoding>utf-8</encoding>
                            </config>
                        </p:input>
                        <p:input name="data" href="#xformed-data"/>
                        <p:output name="data" id="converted"/>
                    </p:processor>
                    <p:processor name="oxf:http-serializer">
                        <p:input name="config">
                            <config>
                                <header>
                                    <name>Cache-Control</name>
                                    <value>post-check=0, pre-check=0</value>
                                </header>
                            </config>
                        </p:input>
                        <p:input name="data" href="#converted"/>
                    </p:processor>
                </p:otherwise>
            </p:choose>
        </p:when>
        <p:otherwise>
            <!-- Portlet -->
            <!-- Extract a fragment and apply theme -->
            <p:processor name="oxf:unsafe-xslt"> <!-- saxon4 -->
                <p:input name="data" href="#xformed-data"/>
                <p:input name="request" href="#request"/>
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
                            <div xmlns:f="http://orbeon.org/oxf/xml/formatting">
                                <xsl:apply-templates select="/xhtml:html/xhtml:head/f:tabs"/>
                                <xsl:apply-templates select="/xhtml:html/xhtml:body/node()"/>
                            </div>
                        </xsl:template>
                    </xsl:stylesheet>
                </p:input>
                <p:output name="data" id="themed-data"/>
            </p:processor>
            <!-- Rewrite all URLs in HTML and XHTML documents -->
            <p:processor name="oxf:unsafe-xslt"><!-- saxon5 -->
                <p:input name="data" href="#themed-data"/>
                <p:input name="container-type" href="#request"/>
                <p:input name="config" href="oxf:/oxf/pfc/oxf-rewrite.xsl"/>
                <p:output name="data" id="rewritten-data"/>
            </p:processor>
            <!-- Serialize to XML -->
            <p:processor name="oxf:xml-serializer">
                <p:input name="config">
                    <config>
                        <!-- Not necessary to indent at this point -->
                        <indent>false</indent>
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

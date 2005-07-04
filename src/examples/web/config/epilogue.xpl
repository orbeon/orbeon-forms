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
    done for all views, for example running the server-side XForms engine, applying a common theme,
    serializing the pages to HTML or XML, etc.
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fo="http://www.w3.org/1999/XSL/Format"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">

    <!-- The document produced by the page view -->
    <p:param type="input" name="data"/>
    <!-- The XML submission if any -->
    <p:param type="input" name="instance"/>
    <!-- The legacy XForms model as produced by the PFC's page/@xforms attribute if any -->
    <p:param type="input" name="xforms-model"/>

    <!-- Run the XForms epilogue -->
    <!-- If you don't use XForms at all, you can bypass this -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="/ops/pfc/xforms-epilogue.xpl"/>
        <p:input name="data" href="#data"/>
        <p:input name="instance" href="#instance"/>
        <p:input name="xforms-model" href="#xforms-model"/>
        <p:output name="xformed-data" id="xformed-data"/>
    </p:processor>

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

    <p:choose  href="#request">
        <p:when test="/request/container-type = 'servlet'">
            <!-- The container is a servlet -->
            <p:choose href="#xformed-data">
                <!-- XSL-FO detection. Use the XSL-FO serializer -->
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
                <!-- XHTML detection. Apply the theme, rewrite URLs, and serialize to HTML or XHTML. -->
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

                    <!-- Use this choose block if you want to send XHTML to browsers that support it. -->
                    <!-- BEGIN ASSUME SOME XHTML CLIENTS -->
                    <!--
                    <p:choose href="#request">
                        <p:when test="false() and contains(/request/headers/header[name = 'accept'], 'application/xhtml+xml')">
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
                            <p:processor name="oxf:xml-converter">
                                <p:input name="config">
                                    <config>
                                        <method>xhtml</method>
                                        <public-doctype>-//W3C//DTD XHTML 1.0 Transitional//EN</public-doctype>
                                        <system-doctype>http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd</system-doctype>
                                        <encoding>utf-8</encoding>
                                        <content-type>application/xhtml+xml</content-type>
                                    </config>
                                </p:input>
                                <p:input name="data" href="#xhtml-data"/>
                                <p:output name="data" id="converted"/>
                            </p:processor>
                        </p:when>
                        <p:otherwise>
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
                    -->
                    <!-- END ASSUME SOME XHTML CLIENTS -->

                    <!-- Use this choose block if you don't want to send any XHTML but just plain HTML to browsers. -->
                    <!-- BEGIN NO ASSUME XHTML CLIENTS -->
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
                    <!-- Convert to plain HTML -->
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
                    <!-- END ASSUME NO XHTML CLIENTS -->

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
                <!-- Plain HTML detection. No theme is applied, but URLs are rewritten. -->
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
                <!-- No particular document format detected. Output plain XML. -->
                <p:otherwise>
                    <!-- Convert and serialize to XML -->
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
                            <xsl:stylesheet version="1.0"
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
            <p:processor name="oxf:unsafe-xslt">
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

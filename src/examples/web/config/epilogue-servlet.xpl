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
    The epilogue is run after all page views. This is the part of the epilogue called to handle OPS
    applications running in a servlet container. It is typically used to perform tasks that need to
    be done for all views, for example applying a common theme, serializing the pages to HTML or
    XML, etc.
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fo="http://www.w3.org/1999/XSL/Format"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">

    <!-- The document produced by the page view with XForms processing performed -->
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
                <include>/request/headers/header[name = 'accept']</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!-- The container is a servlet -->
    <p:choose href="#xformed-data">
        <!-- XHTML detection. Apply the theme, rewrite URLs, and serialize to HTML or XHTML. -->
        <p:when test="/xhtml:html">
            <!-- Apply theme -->
            <p:processor name="oxf:xslt">
                <p:input name="data" href="#xformed-data"/>
                <p:input name="request" href="#request"/>
                <p:input name="config" href="oxf:/config/theme/theme.xsl"/>
                <p:output name="data" id="themed-data"/>
            </p:processor>
            <!-- Rewrite all URLs in HTML and XHTML documents -->
            <p:processor name="oxf:xhtml-rewrite">
                <p:input name="rewrite-in" href="#themed-data"/>
                <p:output name="rewrite-out" id="rewritten-data"/>
            </p:processor>

            <!-- Use this choose block if you want to send XHTML to browsers that support it. -->
            <!-- BEGIN ASSUME SOME XHTML CLIENTS -->
            <!--
            <p:choose href="#request">
                <p:when test="contains(/request/headers/header[name = 'accept'], 'application/xhtml+xml')">
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
                        <!-- NOTE: HTML converter specifies text/html content-type -->
                    </config>
                </p:input>
                <p:input name="data" href="#converted"/>
            </p:processor>
        </p:when>
        <!-- Plain HTML detection. No theme is applied, but URLs are rewritten. -->
        <p:when test="/html">
            <!-- Rewrite all URLs in HTML documents -->
            <p:processor name="oxf:html-rewrite" >
                <p:input name="rewrite-in" href="#xformed-data" />
                <p:output name="rewrite-out" id="rewritten-data" />
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
                        <!-- NOTE: HTML converter specifies text/html content-type -->
                    </config>
                </p:input>
                <p:input name="data" href="#converted"/>
            </p:processor>
        </p:when>
        <!-- XSL-FO detection. Use the XSL-FO serializer -->
        <p:when test="/fo:root">
            <p:processor name="oxf:xslfo-converter">
                <p:input name="config"><config/></p:input>
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
                        <header>
                            <name>Content-Disposition</name>
                            <value>attachment; filename=document.pdf</value>
                        </header>
                        <!-- NOTE: XSL-FO converter specifies application/pdf content-type -->
                    </config>
                </p:input>
                <p:input name="data" href="#converted"/>
            </p:processor>
        </p:when>
        <!-- Non XML documents -->
        <p:when test="/document[@xsi:type]">
            <p:processor name="oxf:http-serializer">
                <p:input name="config">
                    <config>
                        <header>
                            <name>Cache-Control</name>
                            <value>post-check=0, pre-check=0</value>
                        </header>
                        <!-- NOTE: use content-type specified on root element -->
                    </config>
                </p:input>
                <p:input name="data" href="#xformed-data"/>
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
                        <!-- NOTE: XML converter specifies application/xml content-type -->
                    </config>
                </p:input>
                <p:input name="data" href="#converted"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>

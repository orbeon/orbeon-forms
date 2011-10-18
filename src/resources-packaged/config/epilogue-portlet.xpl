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
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">

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
                <!-- Return all parameters so they are made available to the theme -->
                <include>/request/parameters/parameter</include>
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

            <!-- Extract a fragment and apply theme -->
            <p:processor name="oxf:url-generator">
                <p:input name="config"
                         href="aggregate('config', #request#xpointer(for $app in tokenize(/request/request-path, '/')[2] return
                                    for $app-style in concat('oxf:/apps/', $app, '/theme-embeddable.xsl') return
                                    if (doc-available($app-style))
                                        then $app-style
                                        else p:property('oxf.epilogue.theme.embeddable')))"/>
                <p:output name="data" id="theme"/>
            </p:processor>
            <p:processor name="oxf:unsafe-xslt">
                <p:input name="data" href="#xformed-data-2"/>
                <p:input name="request" href="#request"/>
                <p:input name="config" href="#theme"/>
                <p:output name="data" id="themed-data"/>
            </p:processor>
            <!-- Rewrite all URLs in XHTML documents -->
            <p:processor name="oxf:xhtml-rewrite" >
                <p:input name="data" href="#themed-data"/>
                <p:output name="data" id="rewritten-data"/>
            </p:processor>
            <!-- Move from XHTML namespace to no namespace -->
            <p:processor name="oxf:qname-converter">
                <p:input name="config">
                    <config>
                        <match>
                            <uri>http://www.w3.org/1999/xhtml</uri>
                        </match>
                        <replace>
                            <uri/>
                            <prefix/>
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
                        <!-- Indent to level 0 -->
                        <indent>true</indent>
                        <indent-amount>0</indent-amount>
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

        </p:when>
        <!-- Non XML documents -->
        <p:when test="/document[@xsi:type]">
            <p:processor name="oxf:http-serializer">
                <p:input name="config">
                    <config>
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
                        <indent>false</indent>
                        <indent-amount>0</indent-amount>
                    </config>
                </p:input>
                <p:input name="data" href="#xformed-data"/>
                <p:output name="data" id="converted"/>
            </p:processor>
            <p:processor name="oxf:http-serializer">
                <p:input name="config">
                    <config>
                        <!-- NOTE: XML converter specifies application/xml content-type -->
                    </config>
                </p:input>
                <p:input name="data" href="#converted"/>
            </p:processor>
        </p:otherwise>
    </p:choose>


</p:config>

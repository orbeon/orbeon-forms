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
    applications running in a servlet container. It is typically used to perform tasks that need to be done for all
    views, for example applying a common theme, serializing the pages to HTML or XML, etc.
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:fo="http://www.w3.org/1999/XSL/Format"
          xmlns:svg="http://www.w3.org/2000/svg"
          xmlns:xhtml="http://www.w3.org/1999/xhtml"
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xforms="http://www.w3.org/2002/xforms"
          xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xmlns:atom="http://www.w3.org/2005/Atom">

    <!-- The document produced by the page view with XForms processing performed -->
    <p:param type="input" name="xformed-data"/>
    <!-- The raw document produced by the page view -->
    <!--    <p:param type="input" name="data"/>-->
    <!-- The XML submission if any -->
    <p:param type="input" name="instance"/>

    <!-- Get request information -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/container-type</include>
                <include>/request/request-path</include>
                <include>/request/headers/header[name = 'accept']</include>
                <!-- Return all parameters so they are made available to the theme -->
                <include>/request/parameters/parameter</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!-- The container is a servlet -->
    <p:choose href="#xformed-data">
        <!-- XHTML detection. Apply the theme, rewrite URLs, and serialize to HTML or XHTML. -->
        <p:when test="/xhtml:html">

            <!-- Pick theme -->
            <p:choose href="#request">
                <p:when test="/request/parameters/parameter[name = 'orbeon-theme']/value = 'plain'">
                    <!-- Plain theme -->
                    <p:processor name="oxf:identity">
                        <p:input name="data">
                            <config>oxf:/config/theme-plain.xsl</config>
                        </p:input>
                        <p:output name="data" id="theme-config"/>
                    </p:processor>
                </p:when>
                <!-- Get regular, embeddable or renderer theme -->
                <p:otherwise>
                    <p:processor name="oxf:identity">
                        <p:input name="data"
                                 href="aggregate('config', #request#xpointer(for $app in tokenize(/request/request-path, '/')[2] return
                                            for $is-embeddable in
                                                p:property('oxf.epilogue.embeddable')
                                                    or /request/parameters/parameter[name = ('orbeon-embeddable', 'orbeon-portlet')]/value = 'true' return
                                            for $is-renderer in
                                                p:get-request-attribute('oxf.xforms.renderer.deployment', 'text/plain') = ('separate', 'integrated') return
                                            for $app-style in concat('oxf:/apps/', $app, if ($is-embeddable) then '/theme-embeddable.xsl' else '/theme.xsl') return
                                            if (not($is-renderer) and doc-available($app-style))
                                                then $app-style
                                                else if ($is-embeddable) then p:property('oxf.epilogue.theme.embeddable')
                                                     else if ($is-renderer) then p:property('oxf.epilogue.theme.renderer')
                                                     else p:property('oxf.epilogue.theme')))"/>
                        <p:output name="data" id="theme-config"/>
                    </p:processor>
                </p:otherwise>
            </p:choose>

            <!-- Apply theme if needed -->
            <p:choose href="#request"><!-- dummy test input -->
                <p:when test="not(p:property('oxf.epilogue.use-theme') = false())">
                    <!-- Theme -->
                    <p:processor name="oxf:url-generator">
                        <p:input name="config" href="#theme-config"/>
                        <p:output name="data" id="theme"/>
                    </p:processor>
                    <p:processor name="oxf:unsafe-xslt">
                        <p:input name="data" href="#xformed-data"/>
                        <p:input name="instance" href="#instance"/>
                        <p:input name="request" href="#request"/>
                        <p:input name="config" href="#theme"/>
                        <p:output name="data" id="themed-data"/>
                    </p:processor>
                </p:when>
                <p:otherwise>
                    <!-- No theme -->
                    <p:processor name="oxf:identity">
                        <p:input name="data" href="#xformed-data"/>
                        <p:output name="data" id="themed-data"/>
                    </p:processor>
                </p:otherwise>
            </p:choose>

            <!-- Perform URL rewriting if needed -->
            <p:choose href="#request">
                <p:when test="not(starts-with(/request/request-path, '/xforms-renderer') and not(p:property('oxf.epilogue.renderer-rewrite')))">
                    <!-- Rewriting -->
                    <p:processor name="oxf:xhtml-rewrite">
                        <p:input name="data" href="#themed-data"/>
                        <p:output name="data" id="rewritten-data"/>
                    </p:processor>
                </p:when>
                <p:otherwise>
                    <!-- No rewriting -->
                    <p:processor name="oxf:identity">
                        <p:input name="data" href="#themed-data"/>
                        <p:output name="data" id="rewritten-data"/>
                    </p:processor>
                </p:otherwise>
            </p:choose>

            <!-- Choose between XHTML output and HTML output -->
            <p:choose href="#request">
                <p:when test="p:property('oxf.epilogue.output-xhtml')">
                    <!-- Produce XHTML output -->
                    <p:choose href="#request">
                        <p:when test="contains(/request/headers/header[name = 'accept'], 'application/xhtml+xml')">
                            <p:processor name="oxf:qname-converter">
                                <p:input name="config">
                                    <config>
                                        <match>
                                            <uri>http://www.w3.org/1999/xhtml</uri>
                                        </match>
                                        <replace>
                                            <prefix/>
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
                                        <public-doctype>-//W3C//DTD XHTML 1.0 Strict//EN</public-doctype>
                                        <system-doctype>http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd</system-doctype>
                                        <encoding>utf-8</encoding>
                                        <content-type>application/xhtml+xml</content-type>
                                        <indent>true</indent>
                                        <indent-amount>0</indent-amount>
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
                                            <uri/>
                                            <prefix/>
                                        </replace>
                                    </config>
                                </p:input>
                                <p:input name="data" href="#rewritten-data"/>
                                <p:output name="data" id="html-data"/>
                            </p:processor>
                            <p:processor name="oxf:html-converter">
                                <p:input name="config">
                                    <config>
                                        <public-doctype>-//W3C//DTD HTML 4.01//EN</public-doctype>
                                        <version>4.01</version>
                                        <encoding>utf-8</encoding>
                                        <indent>true</indent>
                                        <indent-amount>0</indent-amount>
                                    </config>
                                </p:input>
                                <p:input name="data" href="#html-data"/>
                                <p:output name="data" id="converted"/>
                            </p:processor>
                        </p:otherwise>
                    </p:choose>
                </p:when>
                <p:otherwise>
                    <!-- Produce HTML output -->
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

                    <!-- For embeddable, don't put a doctype declaration -->
                    <p:choose href="#request">
                        <p:when test="(p:property('oxf.epilogue.embeddable')
                             or (/request/parameters/parameter[name = ('orbeon-embeddable', 'orbeon-portlet')]/value = 'true'))">
                            <p:processor name="oxf:identity">
                                <p:input name="data">
                                    <config>
                                        <version>4.01</version>
                                        <encoding>utf-8</encoding>
                                        <indent>true</indent>
                                        <indent-amount>0</indent-amount>
                                    </config>
                                </p:input>
                                <p:output name="data" id="html-converter-config"/>
                            </p:processor>
                        </p:when>
                        <p:otherwise>
                            <p:processor name="oxf:identity">
                                <p:input name="data">
                                    <config>
                                        <public-doctype>-//W3C//DTD HTML 4.01//EN</public-doctype>
                                        <version>4.01</version>
                                        <encoding>utf-8</encoding>
                                        <indent>true</indent>
                                        <indent-amount>0</indent-amount>
                                    </config>
                                </p:input>
                                <p:output name="data" id="html-converter-config"/>
                            </p:processor>
                        </p:otherwise>
                    </p:choose>

                    <!-- Convert to HTML, choosing between embeddable and plain -->
                    <p:processor name="oxf:html-converter">
                        <p:input name="config" href="#html-converter-config"/>
                        <p:input name="data" href="#html-data"/>
                        <p:output name="data" id="converted"/>
                    </p:processor>

                </p:otherwise>
            </p:choose>

            <!-- Serialize to HTTP -->
            <p:processor name="oxf:http-serializer">
                <p:input name="config">
                    <config>
                        <!-- NOTE: converters specify content-type -->
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
                        <public-doctype>-//W3C//DTD HTML 4.01//EN</public-doctype>
                        <version>4.01</version>
                        <encoding>utf-8</encoding>
                        <indent>true</indent>
                        <indent-amount>0</indent-amount>
                    </config>
                </p:input>
                <p:input name="data" href="#rewritten-data"/>
                <p:output name="data" id="converted"/>
            </p:processor>
            <p:processor name="oxf:http-serializer">
                <p:input name="config">
                    <config>
                        <!-- NOTE: HTML converter specifies text/html content-type -->
                    </config>
                </p:input>
                <p:input name="data" href="#converted"/>
            </p:processor>
        </p:when>
        <!-- XSL-FO detection. Use the XSL-FO serializer -->
        <p:when test="p:property('oxf.epilogue.process-xslfo') and /fo:root">
            <p:processor name="oxf:xslfo-converter">
                <p:input name="config"><config/></p:input>
                <p:input name="data" href="#xformed-data"/>
                <p:output name="data" id="converted"/>
            </p:processor>
            <p:processor name="oxf:http-serializer">
                <p:input name="config">
                    <config>
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
        <!-- SVG detection -->
        <p:when test="p:property('oxf.epilogue.process-svg') and /svg:svg">
            <p:processor name="oxf:svg-converter">
                <p:input name="config"><config/></p:input>
                <p:input name="data" href="#xformed-data"/>
                <p:output name="data" id="converted"/>
            </p:processor>
            <p:processor name="oxf:http-serializer">
                <p:input name="config">
                    <config>
                        NOTE: SVG converter specifies content-type, usually image/png
                    </config>
                </p:input>
                <p:input name="data" href="#converted"/>
            </p:processor>
        </p:when>
        <!-- Atom detection -->
        <p:when test="/atom:feed">
            <p:processor name="oxf:xml-converter">
                <p:input name="config">
                    <config>
                        <encoding>utf-8</encoding>
                        <content-type>application/atom+xml</content-type>
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
                        <!-- NOTE: XML converter specifies content-type -->
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
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
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param type="input" name="instance"/>

    <!-- Get pdf paramater from request -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/parameters</include>
                <include>/request/container-type</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!-- Generate page -->
    <!-- 9/7/2004 d : When we re-org'd the doc we broke links from external sites.  -->
    <!--              OT asked for a work around that would at least keep link from -->
    <!--              http://www.w3.org/MarkUp/Forms/ working, hence this special   -->
    <!--              casing of 'processors-xforms'. -->
    <p:choose  href="#instance">
      <p:when  test="/form/page = 'processors-xforms'">
        <p:processor name="oxf:url-generator">
            <p:input name="config" >
               <config>
                 <url>oxf:/doc/pages/reference-xforms.xml</url>
                 <content-type>text/xml</content-type>                 
               </config>
            </p:input>
            <p:output name="data" id="body"/>
        </p:processor>
      </p:when>
      <p:otherwise>
    <p:processor name="oxf:url-generator">
        <p:input name="config" href="aggregate('config',
            #instance#xpointer(concat('oxf:/doc/pages/', /form/page, '.xml')))"/>
        <p:output name="data" id="body"/>
    </p:processor>
      </p:otherwise>
    </p:choose>

    <p:choose href="#request">
        <p:when test="count(/request/parameters/parameter[name = '/form/pdf']) = 1">
            <!-- Generate PDF -->
            <p:processor name="oxf:xslt">
                <p:input name="data" href="#body"/>
                <p:input name="config" href="skin/xslt/fo/document2fo.xsl"/>
                <p:output name="data" id="fo"/>
            </p:processor>
            <p:processor name="oxf:xslfo-serializer">
                <p:input name="config">
                    <config>
                        <header>
                            <name>Content-Disposition</name>
                            <value>attachment; filename=document.pdf;</value>
                        </header>
                    </config>
                </p:input>
                <p:input name="data" href="#fo"/>
            </p:processor>
        </p:when>
        <p:otherwise>

            <p:processor name="oxf:xslt">
                <p:input name="config" href="view-html.xsl"/>
                <p:input name="instance" href="#instance"/>
                <p:input name="data" href="#body"/>
                <p:output name="data" id="view"/>
            </p:processor>

            <p:processor name="oxf:xslt">
                <p:input name="data" href="#view"/>
                <p:input name="request"><request/></p:input>
                <p:input name="config" href="oxf:/config/theme/theme.xsl"/>
                <p:output name="data" id="html"/>
            </p:processor>

            <p:processor name="oxf:unsafe-xslt">
                <p:input name="config" href="oxf:/ops/pfc/url-rewrite.xsl"/>
                <p:input name="data" href="#html"/>
                <p:input name="container-type" href="#request"/>
                <p:output name="data" id="fixed-html"/>
            </p:processor>

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
                <p:input name="data" href="#fixed-html"/>
                <p:output name="data" id="html-data"/>
            </p:processor>

            <p:processor name="oxf:html-serializer">
                <p:input name="config">
                    <config>
                        <version>4.01</version>
                        <public-doctype>-//W3C//DTD HTML 4.01 Transitional//EN</public-doctype>
                        <encoding>utf-8</encoding>
                        <header>
                            <name>Cache-Control</name>
                            <value>post-check=0, pre-check=0</value>
                        </header>
                    </config>
                </p:input>
                <p:input name="data" href="#html-data"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>

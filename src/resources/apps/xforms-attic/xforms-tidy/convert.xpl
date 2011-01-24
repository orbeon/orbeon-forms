<!--
    Copyright (C) 2007 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param name="instance" type="input"/>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <config xsl:version="2.0">
                <url><xsl:value-of select="/*"/></url>
                <content-type>text/html</content-type>
                <force-content-type>true</force-content-type>
            </config>
        </p:input>
        <p:output name="data" id="url-config"/>
    </p:processor>

    <p:processor name="oxf:url-generator">
        <p:input name="config" href="#url-config"/>
        <p:output name="data" id="pseudo-xhtml-data"/>
    </p:processor>

    <p:processor name="oxf:qname-converter">
        <p:input name="config">
            <config>
                <match>
                    <uri></uri>
                </match>
                <replace>
                    <uri>http://www.w3.org/1999/xhtml</uri>
                </replace>
            </config>
        </p:input>
        <p:input name="data" href="#pseudo-xhtml-data"/>
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

    <p:processor name="oxf:http-serializer">
        <p:input name="config">
            <config>
                <header>
                    <name>Cache-Control</name>
                    <value>post-check=0, pre-check=0</value>
                </header>
                <!-- NOTE: XML converter specifies text/html content-type -->
            </config>
        </p:input>
        <p:input name="data" href="#converted"/>
    </p:processor>
    
</p:config>

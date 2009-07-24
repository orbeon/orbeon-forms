<?xml version="1.0" encoding="utf-8"?>
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
<!--
    
    Sample service that adds sort and paging feature to twitter feeds.
    
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:saxon="http://saxon.sf.net/"
    xmlns:oxf="http://www.orbeon.com/oxf/processors" xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xdb="http://orbeon.org/oxf/xml/xmldb"
    xmlns:f="http://www.orbeon.com/oxf/function" xmlns:atom="http://www.w3.org/2005/Atom">

    <p:param name="instance" type="input"/>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <config xsl:version="2.0">
                <url>
                    <xsl:text>http://search.twitter.com/search.atom?rpp=100&amp;q=</xsl:text>
                    <xsl:value-of select="encode-for-uri(/xsl:stylesheet/xsl:variable[@name='q']/q)"
                    />
                </url>
                <content-type>application/xml</content-type>
            </config>
        </p:input>
        <p:output name="data" id="url-config"/>
    </p:processor>

    <p:processor name="oxf:url-generator">
        <p:input name="config" href="#url-config"/>
        <p:output name="data" id="twitter"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#twitter"/>
        <p:input name="instance" href="#instance"/>
        <p:input name="config" href="#instance"/>
        <p:output name="data" id="twitter-filtered"/>
    </p:processor>

    <p:processor name="oxf:xml-converter">
        <p:input name="config">
            <config>
                <indent>false</indent>
                <encoding>utf-8</encoding>
            </config>
        </p:input>
        <p:input name="data" href="#twitter-filtered"/>
        <p:output name="data" id="converted"/>
    </p:processor>

    <p:processor name="oxf:http-serializer">
        <p:input name="config">
            <config>
                <cache-control>
                    <use-local-cache>false</use-local-cache>
                </cache-control>
            </config>
        </p:input>
        <p:input name="data" href="#converted"/>
    </p:processor>

</p:config>

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
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xhtml="http://www.w3.org/1999/xhtml">

    <p:for-each href="book.xml" select="(//menu-item[@href and not(ends-with(@href, '.pdf')) and not (starts-with(@href, 'http:'))])">

        <p:processor name="oxf:identity">
            <p:input name="data" href="current()"/>
            <p:output name="data" id="url"/>
        </p:processor>

        <!-- Create instance -->
        <p:processor name="oxf:xslt">
            <p:input name="data" href="#url"/>
            <p:input name="config">
                <form xsl:version="2.0">
                    <page><xsl:value-of select="/*/@href"/></page>
                </form>
            </p:input>
            <p:output name="data" id="instance"/>
        </p:processor>

        <!-- Call model to obtain source document -->
        <p:processor name="oxf:pipeline">
            <p:input name="config" href="doc-model.xpl"/>
            <p:input name="instance" href="#instance"/>
            <p:output name="data" id="source-document"/>
        </p:processor>

        <!-- Process file -->
        <p:processor name="oxf:pipeline">
            <p:input name="config" href="process-static-file.xpl"/>
            <p:input name="source-document" href="#source-document"/>
            <p:input name="instance" href="#instance"/>
            <p:output name="html" id="html-data"/>
        </p:processor>

        <!-- Write out HTML -->
        <p:processor name="oxf:xslt">
            <p:input name="data" href="#url"/>
            <p:input name="config">
                <config xsl:version="2.0">
                    <directory>build/doc/reference</directory>
                    <file><xsl:value-of select="/*/@href"/>.html</file>
                </config>
            </p:input>
            <p:output name="data" id="file-config"/>
        </p:processor>

        <!-- Convert and serialize to XML -->
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

        <p:processor name="oxf:file-serializer">
            <p:input name="config" href="#file-config"/>
            <p:input name="data" href="#converted"/>
        </p:processor>

    </p:for-each>

</p:config>


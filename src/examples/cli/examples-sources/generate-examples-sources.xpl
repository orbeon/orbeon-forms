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

  This example looks for example descriptors in the OPS example directories. For each example
  descriptor found, it lists relevant files in that directory. With the result, a list of source
  files to be included in the example descriptor is generated.

-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- Find all examples descriptors -->
    <p:processor name="oxf:directory-scanner">
        <p:input name="config">
            <config>
                <base-directory>../../web/</base-directory>
                <include>**/example-descriptor.xml</include>
                <case-sensitive>false</case-sensitive>
            </config>
        </p:input>
        <p:output name="data" id="directory-scan"/>
    </p:processor>

    <p:for-each href="#directory-scan" select="(//file)">

        <!-- Scan for files in this particular directory -->
        <p:processor name="oxf:xslt-2.0">
            <p:input name="data" href="current()"/>
            <p:input name="config">
                <config xsl:version="2.0">
                    <xsl:variable name="path" select="tokenize(/*/@path, '[/\\]')"/>
                    <base-directory><xsl:value-of select="concat('../../web/', string-join(remove($path, count($path)), '/'))"/></base-directory>
                    <include>**/*.x?l</include>
                    <include>**/*.xhtml</include>
                    <include>**/*.java</include>
                    <include>**/*.txt</include>
                    <exclude>example-descriptor.xml</exclude>
                    <exclude>example-descriptor-files.xml</exclude>
                    <case-sensitive>false</case-sensitive>
                </config>
            </p:input>
            <p:output name="data" id="ds-config"/>
        </p:processor>

        <p:processor name="oxf:directory-scanner">
            <p:input name="config" href="#ds-config"/>
            <p:output name="data" id="result"/>
        </p:processor>

        <!-- Create document to write -->
        <p:processor name="oxf:xslt-2.0">
            <p:input name="data" href="#result"/>
            <p:input name="config">
                <source-files xsl:version="2.0">
                    <xsl:for-each select="//file">
                        <file size="{@size}"><xsl:value-of select="replace(@path, '\\', '/')"/></file>
                    </xsl:for-each>
                </source-files>
            </p:input>
            <p:output name="data" id="document"/>
        </p:processor>

        <!-- Create file serializer config -->
        <p:processor name="oxf:xslt-2.0">
            <p:input name="data" href="#result"/>
            <p:input name="config">
                <config xsl:version="2.0">
                    <directory><xsl:value-of select="/*/@path"/></directory>
                    <file>example-descriptor-files.xml</file>
                </config>
            </p:input>
            <p:output name="data" id="fs-config"/>
        </p:processor>

        <!-- Convert and serialize to XML -->
        <p:processor name="oxf:xml-converter">
            <p:input name="config">
                <config>
                    <encoding>utf-8</encoding>
                </config>
            </p:input>
            <p:input name="data" href="#document"/>
            <p:output name="data" id="converted"/>
        </p:processor>

        <!-- Write out document -->
        <p:processor name="oxf:file-serializer">
            <p:input name="data" href="#converted"/>
            <p:input name="config" href="#fs-config"/>
        </p:processor>

    </p:for-each>

    <!-- Write out comment -->
    <p:processor name="oxf:xslt-2.0">
        <p:input name="data" href="#directory-scan"/>
        <p:input name="config">
            <text xsl:version="2.0">&#x0a;Processed <xsl:value-of select="count(//file)"/> files.&#x0a;&#x0a;</text>
        </p:input>
        <p:output name="data" id="comment"/>
    </p:processor>

    <p:processor name="oxf:text-serializer">
        <p:input name="data" href="#comment"/>
        <p:input name="config"><config/></p:input>
    </p:processor>

</p:config>

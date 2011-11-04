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
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <p:param type="input" name="instance"/>

    <!-- Scan directory -->
    <p:processor name="oxf:directory-scanner">
        <p:input name="config" href="#instance" transform="oxf:xslt">
            <config xsl:version="2.0">
                <base-directory>oxf:/apps/xforms-sandbox/samples</base-directory>
                <include>*.xhtml</include>
                <case-sensitive>false</case-sensitive>
            </config>
        </p:input>
        <p:output name="data" id="result"/>
    </p:processor>

    <!-- Sort -->
    <p:processor name="oxf:xslt-2.0">
        <p:input name="data" href="#result"/>
        <p:input name="config">
            <directory xsl:version="2.0">
                <xsl:copy-of select="/directory/@*"/>
                <xsl:for-each select="/directory/file">
                    <xsl:sort select="@name"/>
                    <xsl:copy-of select="."/>
                </xsl:for-each>
            </directory>
        </p:input>
        <p:output name="data" id="sorted"/>
    </p:processor>
    
    <!-- Convert and serialize to XML -->
    <p:processor name="oxf:xml-converter">
        <p:input name="config">
            <config>
                <indent>false</indent>
                <encoding>utf-8</encoding>
            </config>
        </p:input>
        <p:input name="data" href="#sorted"/>
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

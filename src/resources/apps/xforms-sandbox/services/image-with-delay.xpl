<?xml version="1.0" encoding="windows-1252"?>
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
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:saxon="http://saxon.sf.net/"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xdb="http://orbeon.org/oxf/xml/xmldb"
        xmlns:f="http://www.orbeon.com/oxf/function">

    <p:param name="data" type="output"/>

    <!-- Wait for 5 seconds -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="sleep-5-seconds.xpl"/>
        <p:output name="data" id="sleep"/>
    </p:processor>
    <p:processor name="oxf:null-serializer">
        <p:input name="data" href="#sleep"/>
    </p:processor>

    <!-- Read image -->
    <p:processor name="oxf:url-generator">
        <p:input name="config">
            <config>
                <url>oxf:/config/theme/images/orbeon-logo-trimmed-transparent-42.png</url>
                <content-type>image/gif</content-type>
            </config>
        </p:input>
        <p:output name="data" id="image-data"/>
    </p:processor>

    <!-- Send result through HTTP -->
    <p:processor name="oxf:http-serializer">
        <p:input name="config">
            <config>
                <!--<header>-->
                    <!--<name>Content-Disposition</name>-->
                    <!--<value>attachment; filename=<xsl:value-of select="replace(/instance/filename, ' ', '_')"/></value>-->
                <!--</header>-->
                <cache-control>
                    <use-local-cache>false</use-local-cache>
                </cache-control>
            </config>
        </p:input>
        <p:input name="data" href="#image-data"/>
    </p:processor>

    <p:processor name="oxf:identity">
        <p:input name="data"><dummy/></p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>

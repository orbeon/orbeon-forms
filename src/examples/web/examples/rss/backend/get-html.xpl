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
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <p:param name="rss-feed-descriptor" type="input"/>
    <p:param name="html" type="output"/>

    <!-- Get HTML for page -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#rss-feed-descriptor"/>
        <p:input name="config">
            <config xsl:version="2.0">
                <url><xsl:value-of select="/rss-feed-descriptor/items-url"/></url>
                <content-type>text/xml</content-type>
                <validating>true</validating>
            </config>
        </p:input>
        <p:output name="data" id="url-config"/>
    </p:processor>

    <p:processor name="oxf:url-generator">
        <p:input name="config" href="#url-config"/>
        <p:output name="data" ref="html"/>
    </p:processor>

</p:config>

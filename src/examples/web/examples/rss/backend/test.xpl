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
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:function="http://www.orbeon.com/xslt-function">

    <p:param name="rss-feed-descriptor" type="input"/>
    <p:param name="data" type="output"/>

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="get-html.xpl"/>
        <p:input name="rss-feed-descriptor" href="#rss-feed-descriptor"/>
        <p:output name="html" id="html"/>
    </p:processor>

    <!-- Create XSLT to extract items -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#html"/>
        <p:input name="rss-feed-descriptor" href="#rss-feed-descriptor"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0" xmlns:saxon="http://saxon.sf.net/">
                <xsl:import href="oxf:/oxf/xslt/utils/evaluate.xsl"/>
                <xsl:template match="/">
                    <items xsl:version="2.0">
                        <xsl:copy-of select="function:evaluate(/*, doc('input:rss-feed-descriptor')/rss-feed-descriptor/items-xpath, ())"/>
                    </items>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>

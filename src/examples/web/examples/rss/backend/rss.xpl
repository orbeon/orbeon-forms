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
    xmlns:function="http://www.orbeon.com/xslt-function"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:saxon="http://saxon.sf.net/">

    <p:param name="rss-feed-descriptor" type="input"/>
    <p:param name="data" type="output"/>

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="get-html.xpl"/>
        <p:input name="rss-feed-descriptor" href="#rss-feed-descriptor"/>
        <p:output name="html" id="html"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#html"/>
        <p:input name="rss-feed-descriptor" href="#rss-feed-descriptor"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/evaluate.xsl"/>
                <xsl:variable name="descriptor" as="element()" select="doc('input:rss-feed-descriptor')/rss-feed-descriptor"/>
                <xsl:template match="/">
                    <rss version="2.0" xsl:version="2.0">
                        <!-- Channel description -->
                        <channel>
                            <title><xsl:value-of select="$descriptor/channel-title"/></title>
                            <link><xsl:value-of select="$descriptor/channel-link"/></link>
                            <description><xsl:value-of select="$descriptor/channel-title"/></description>
                            <lastBuildDate>
                                <xsl:value-of select="current-dateTime()"/>
                            </lastBuildDate>
                            <ttl>5</ttl>
                            <image>
                                <url><xsl:value-of select="$descriptor/image-url"/></url>
                            </image>
                        </channel>
                        <!-- Items -->
                        <xsl:for-each select="saxon:evaluate($descriptor/items-xpath)">
                            <xsl:message>current: <xsl:copy-of select="."/></xsl:message>
                            <xsl:variable name="item" select="."/>
                            <item>
                                <title>
                                    <xsl:value-of select="saxon:evaluate($descriptor/items-title-xpath)"/>
                                </title>
                                <link>
                                    <xsl:variable name="url" select="string(saxon:evaluate($descriptor/items-url-xpath))" as="xs:string"/>
                                    <xsl:value-of select="if (starts-with($url, 'http:')) then $url else concat($descriptor/channel-link, $url)"/>
                                </link>
                                <guid isPermaLink="false">
                                    <xsl:value-of select="saxon:evaluate($descriptor/items-title-xpath)"/>
                                </guid>
                                <xsl:if test="$descriptor/items-description-xpath != ''">
                                    <description>
                                        <xsl:copy-of select="saxon:evaluate($descriptor/items-description-xpath)"/>
                                    </description>
                                </xsl:if>
                            </item>
                        </xsl:for-each>
                    </rss>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>

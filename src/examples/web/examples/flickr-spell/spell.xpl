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
    xmlns:saxon="http://saxon.sf.net/"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:env="http://www.w3.org/2003/05/soap-envelope"
    xmlns:orbeon="http://www.orbeon.com/"
    xmlns:flickr="urn:flickr"
    xmlns:delegation="http://orbeon.org/oxf/xml/delegation"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:math="java:java.lang.Math"
    xmlns:random="java:java.util.Random">

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>

    <!-- Decompose text in letters -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <letters xsl:version="2.0">
                <xsl:variable name="text" as="xs:string" select="upper-case(/input/text)"/>
                <xsl:for-each select="1 to string-length($text)">
                    <letter>
                        <xsl:variable name="letter" select="substring($text, current(), 1)"/>
                        <xsl:attribute name="tag" select="if ($letter = 'A') then 'AA' else if ($letter = 'I') then 'II' else $letter"/>
                    </letter>
                </xsl:for-each>
            </letters>
        </p:input>
        <p:output name="data" id="letters"/>
    </p:processor>

    <p:for-each href="#letters" select="/letters/letter" root="images" id="images">

        <p:choose href="current()">
            <!-- Insert space -->
            <p:when test="/letter/@tag = ' '">
                <p:processor name="oxf:identity">
                    <p:input name="data"><span style="margin-left: 3em"/></p:input>
                    <p:output name="data" ref="images"/>
                </p:processor>
            </p:when>
            <!-- Insert letter -->
            <p:when test="/letter/@tag >= 'A' and 'Z' >= /letter/@tag">
                <!-- Build key for scope -->
                <p:processor name="oxf:xslt">
                    <p:input name="data" href="current()"/>
                    <p:input name="config">
                        <config xsl:version="2.0">
                            <key>
                                <xsl:text>4/spell/letter/</xsl:text>
                                <xsl:value-of select="/letter/@tag"/>
                            </key>
                            <scope>application</scope>
                        </config>
                    </p:input>
                    <p:output name="data" id="scope-config"/>
                </p:processor>

                <!-- Try to get from scope -->
                <p:processor name="oxf:scope-generator">
                    <p:input name="config" href="#scope-config"/>
                    <p:output name="data" id="initial-scope"/>
                </p:processor>

                <!-- If not in scope, call Flickr -->
                <p:choose href="#initial-scope">
                    <p:when test="/*/@*:nil = 'true'">
                        <!-- Not in scope -->

                        <!-- Build config for web service call -->
                        <p:processor name="oxf:xslt">
                            <p:input name="data" href="current()"/>
                            <p:input name="config">
                                <delegation:execute service="flickr" xsl:version="2.0">
                                    <flickr:FlickrRequest>
                                        <method>flickr.groups.pools.getPhotos</method>
                                        <api_key>d0c3b54d6fbc1ed217ecc67feb42568b</api_key>
                                        <group_id>27034531@N00</group_id>
                                        <per_page>100</per_page>
                                        <tags><xsl:value-of select="/letter/@tag"/></tags>
                                    </flickr:FlickrRequest>
                                </delegation:execute>
                            </p:input>
                            <p:output name="data" id="flickr-query"/>
                        </p:processor>
                       <!-- Call Flickr -->
                       <p:processor name="oxf:delegation">
                           <p:input name="interface">
                               <config>
                                   <service id="flickr" type="webservice" soap-version="1.2"
                                       endpoint="http://www.flickr.com/services/soap/" style="document"/>
                               </config>
                           </p:input>
                           <p:input name="call" href="#flickr-query"/>
                           <p:output name="data" id="flickr-response"/>
                       </p:processor>
                        <!-- Parse content of response -->
                        <p:processor name="oxf:xslt">
                            <p:input name="data" href="#flickr-response"/>
                            <p:input name="config">
                                <xsl:stylesheet version="2.0">
                                    <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                                    <xsl:template match="flickr:FlickrResponse">
                                        <xsl:copy>
                                            <xsl:copy-of select="saxon:parse(string(.))"/>
                                        </xsl:copy>
                                    </xsl:template>
                                </xsl:stylesheet>
                            </p:input>
                            <p:output name="data" id="flickr-parsed"/>
                        </p:processor>
                        <!-- Store in scope -->
                        <p:processor name="oxf:scope-serializer">
                            <p:input name="config" href="#scope-config"/>
                            <p:input name="data" href="#flickr-parsed"/>
                        </p:processor>
                        <p:processor name="oxf:identity">
                            <p:input name="data" href="#flickr-parsed"/>
                            <p:output name="data" id="photos"/>
                        </p:processor>
                    </p:when>
                    <p:otherwise>
                        <p:processor name="oxf:identity">
                            <p:input name="data" href="#initial-scope"/>
                            <p:output name="data" id="photos"/>
                        </p:processor>
                    </p:otherwise>
                </p:choose>

                <!-- Pick a random picture and link to it -->
                <p:processor name="oxf:unsafe-xslt">
                    <p:input name="instance" href="#instance"/>
                    <p:input name="data" href="#photos"/>
                    <p:input name="config">
                        <img xsl:version="2.0">
                            <xsl:message><xsl:value-of select="doc('input:instance')/input/seed"/></xsl:message>
                            <xsl:variable name="random" select="random:new(4242 * doc('input:instance')/input/seed cast as xs:double)"/>
                            <xsl:variable name="position" as="xs:double"
                                select="floor(random:nextDouble($random) * count(/flickr:FlickrResponse/photos/photo)) + 1"/>
                            <xsl:variable name="photo" as="element()"
                                select="/flickr:FlickrResponse/photos/photo[$position]"/>
                            <xsl:attribute name="src">
                                <xsl:text>http://photos</xsl:text>
                                <xsl:value-of select="$photo/@server"/>
                                <xsl:text>.flickr.com/</xsl:text>
                                <xsl:value-of select="$photo/@id"/>
                                <xsl:text>_</xsl:text>
                                <xsl:value-of select="$photo/@secret"/>
                                <xsl:text>_s.jpg</xsl:text>
                            </xsl:attribute>
                        </img>
                    </p:input>
                    <p:output name="data" ref="images"/>
                </p:processor>
            </p:when>
            <!-- Do nothing -->
            <p:otherwise>
                <p:processor name="oxf:identity">
                    <p:input name="data"><span/></p:input>
                    <p:output name="data" ref="images"/>
                </p:processor>
            </p:otherwise>
        </p:choose>

    </p:for-each>

    <!-- Convert to HTML -->
    <p:processor name="oxf:html-converter">
        <p:input name="config"><config/></p:input>
        <p:input name="data" href="#images#xpointer(/images/*)"/>
        <p:output name="data" id="html"/>
    </p:processor>

    <!-- Add images around it -->
    <p:processor name="oxf:identity">
        <p:input name="data" href="aggregate('images', #html#xpointer(string(.)))"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>

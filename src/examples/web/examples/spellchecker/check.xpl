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
          xmlns:xs="http://www.w3.org/2001/XMLSchema">

    <p:param name="text" type="input"/>
    <p:param name="correction" type="output"/>

    <!-- Count number of words -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#text"/>
        <p:input name="config">
            <count xsl:version="2.0"><xsl:value-of select="count(tokenize(string(/), '\s+'))"/></count>
        </p:input>
        <p:output name="data" id="count"/>
    </p:processor>
    
    <p:choose href="#count">
        <p:when test="/count > 50">
            <p:processor name="oxf:identity">
                <p:input name="data">
                    <error>Cannot spell check more than 50 words</error>
                </p:input>
                <p:output name="data" ref="correction"/>
            </p:processor>
        </p:when>
        <p:otherwise>

            <!-- Tokenize and group words -->
            <p:processor name="oxf:xslt">
                <p:input name="data" href="#text"/>
                <p:input name="config">
                    <xsl:stylesheet version="1.0">
                        <xsl:variable name="group-size" select="5"/>

                        <xsl:template match="/">

                            <!-- Tokenize text -->
                            <xsl:variable name="words" select="tokenize(string(/), '\s+')" as="xs:string*"/>

                            <!-- Group words -->
                            <groups>
                                <xsl:call-template name="group">
                                    <xsl:with-param name="words" select="$words"/>
                                </xsl:call-template>
                            </groups>
                        </xsl:template>

                        <xsl:template name="group">
                            <xsl:param name="words" as="xs:string*"/>
                            <group>
                                <xsl:for-each select="$words[$group-size >= position()]">
                                    <word><xsl:value-of select="."/></word>
                                </xsl:for-each>
                            </group>
                            <xsl:if test="count($words) > $group-size">
                                <xsl:call-template name="group">
                                    <xsl:with-param name="words" select="$words[position() > $group-size]"/>
                                </xsl:call-template>
                            </xsl:if>
                        </xsl:template>
                    </xsl:stylesheet>
                </p:input>
                <p:output name="data" id="groups"/>
            </p:processor>

            <p:for-each href="#groups" select="/groups/group" root="groups" id="corrected-groups">

                <!-- Create request for a group -->
                <p:processor name="oxf:xslt">
                    <p:input name="data" href="current()"/>
                    <p:input name="config">
                        <config xsl:version="2.0">
                            <xsl:variable name="sentence" select="string-join(/group/word, ' ')" as="xs:string"/>
                            <url>
                                <xsl:text>http://www.google.com/search?q=</xsl:text>
                                <xsl:value-of select="escape-uri(substring($sentence, 1, string-length($sentence) - 1), true())"/>
                            </url>
                            <content-type>text/xml</content-type>
                            <header>
                                <name>User-Agent</name>
                                <value>Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US; rv:1.4.1) Gecko/20031008</value>
                            </header>
                            <validating>true</validating>
                            <cache-control>
                                <use-local-cache>true</use-local-cache>
                            </cache-control>
                        </config>
                    </p:input>
                    <p:output name="data" id="url-config"/>
                </p:processor>

                <!-- Get result from spellcheck -->
                <p:processor name="oxf:url-generator">
                    <p:input name="config" href="#url-config"/>
                    <p:output name="data" id="html"/>
                </p:processor>

                <!-- Merge original text with corrections -->
                <p:processor name="oxf:xslt">
                    <p:input name="data" href="aggregate('root', current(), aggregate('correction', #html#xpointer((//p[contains(font, 'Did you mean:')])[1]/a)))"/>
                    <p:input name="config">
                        <xsl:stylesheet version="2.0">
                            <xsl:template match="/">
                                <xsl:choose>
                                    <!-- No error -->
                                    <xsl:when test="count(/root/correction/*) = 0">
                                        <xsl:copy-of select="/root/group"/>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <!-- Build word list for corrected group -->
                                        <xsl:variable name="new-words">
                                            <xsl:for-each select="/root/correction/a/node()">
                                                <xsl:choose>
                                                    <xsl:when test="name() = ''"> <!-- Text node -->
                                                        <xsl:for-each select="tokenize(normalize-space(.), '\s+')">
                                                            <word>
                                                                <xsl:value-of select="."/>
                                                            </word>
                                                        </xsl:for-each>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <word alternate="{.}"/>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </xsl:for-each>
                                        </xsl:variable>
                                        <!-- Iterate over input words and add alternate attribute -->
                                        <group>
                                            <xsl:for-each select="/root/group/word">
                                                <xsl:variable name="position" select="position()"/>
                                                <word>
                                                    <xsl:variable name="alternate" select="$new-words/word[position() = $position]/@alternate" as="xs:string?"/>
                                                    <xsl:if test="$alternate">
                                                        <xsl:attribute name="alternate" select="$alternate"/>
                                                    </xsl:if>
                                                    <xsl:value-of select="."/>
                                                </word>
                                            </xsl:for-each>
                                        </group>
                                    </xsl:otherwise>
                                </xsl:choose>
                            </xsl:template>
                        </xsl:stylesheet>
                    </p:input>
                    <p:output name="data" ref="corrected-groups"/>
                </p:processor>

            </p:for-each>

            <p:processor name="oxf:identity">
                <p:input name="data" href="aggregate('correction', #corrected-groups#xpointer(/groups/group/word))"/>
                <p:output name="data" ref="correction"/>
            </p:processor>

        </p:otherwise>
    </p:choose>

</p:config>

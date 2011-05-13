<!--
  Copyright (C) 2010 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:sql="http://orbeon.org/oxf/xml/sql"
        xmlns:odt="http://orbeon.org/oxf/xml/datatypes"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:saxon="http://saxon.sf.net/"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events">

    <!-- Search instance -->
    <p:param name="instance" type="input"/>

    <!-- Search result -->
    <p:param name="data" type="output"/>

    <p:processor name="oxf:request">
        <p:input name="config">
            <config stream-type="xs:anyURI">
                <include>/request/headers/header[name = 'orbeon-exist-uri']</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!-- Prepare submission -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="request" href="#request"/>
        <p:input name="config">
            <xforms:submission xsl:version="2.0" method="post"
                               resource="{doc('input:request')/request/headers/header[name = 'orbeon-exist-uri']/value}/{/*/app}/{/*/form
                                            }/data/?page-size={/*/page-size
                                            }&amp;page-number={/*/page-number
                                            }&amp;query={
                                                concat(/*/query[empty(@path)],
                                                       string-join(for $query in /*/query[@path and normalize-space() != '']
                                                         return concat('&amp;path=', encode-for-uri($query/@path), '&amp;value=', $query), ''))
                                            }&amp;sort-key={/*/sort-key}&amp;lang={/*/lang}" replace="instance">
                <!-- Move resulting <document> element as root element -->
                <xforms:insert ev:event="xforms-submit-done" nodeset="/*" origin="/*/*[1]"/>
                <!-- Copy eXist error -->
                <xforms:action ev:event="xforms-submit-error">
                    <!--<xforms:insert context="/" origin="xxforms:element('error')"/>-->
                    <!--<xforms:insert context="/" origin="xxforms:html-to-xml(event('response-body'))"/>-->
                    <xforms:delete nodeset="/*/*"/>
                    <xforms:setvalue ref="/*" value="event('response-body')"/>
                    <xforms:message level="xxforms:log-debug"><xforms:output value="event('response-body')"/></xforms:message>
                    <!-- TODO: Propagate error to caller -->
                </xforms:action>
            </xforms:submission>
        </p:input>
        <p:output name="data" id="submission"/>
    </p:processor>

    <!-- Prepare query -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="query" href="search.xml"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <!-- Get query and apply templates -->
                <xsl:variable name="query" select="doc('input:query')"/>
                <xsl:variable name="instance" select="/*" as="element(search)"/>
                <xsl:template match="/">
                    <xsl:apply-templates select="$query/*"/>
                </xsl:template>
                <!-- Dynamically list of namespaces -->
                <xsl:template match="namespaces">
                    <!-- All namespaces on all query elements, which will contain duplicate namespaces -->
                    <!-- Note: we need here to exclude the declaration for the XML namespace -->
                    <xsl:variable name="namespaces" select="$instance/query/namespace::*[local-name() != 'xml']"/>
                    <xsl:variable name="prefixes" as="xs:string*" select="distinct-values($namespaces/local-name())"/>
                    <xsl:for-each select="$prefixes">
                        <xsl:variable name="prefix" select="."/>
                        <xsl:variable name="namespace" select="$namespaces[local-name() = $prefix][1]"/>
                        <xsl:value-of select="concat('declare namespace ', $prefix, '=&quot;', $namespace, '&quot;;')"/>
                    </xsl:for-each>
                </xsl:template>
                <!-- Dynamically build where clause -->
                <xsl:template match="where">
                    <!-- Old fulltext index -->
                    <!--<xsl:text>($query = '' or text:match-any($resource, $query))</xsl:text>-->
                    <!-- New lucene index -->
                    <xsl:text>($query = '' or ft:query($resource, $query))</xsl:text>
                    <xsl:for-each select="$instance/query[@path and normalize-space() != '']">
                        <xsl:variable name="position" select="position()" as="xs:integer"/>
                        <!-- NOTE: We should probably use ft:query() below as well -->
                        <xsl:choose>
                            <xsl:when test="@match = 'exact'">
                                <!-- Exact match -->
                                and $resource/*/<xsl:value-of select="@path"/>[. = $value[<xsl:value-of select="$position"/>]]
                            </xsl:when>
                            <xsl:otherwise>
                                <!-- Substring, case-insensitive match -->
                                and $resource/*/<xsl:value-of select="@path"/>[contains(lower-case(.), lower-case($value[<xsl:value-of select="$position"/>]))]
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:for-each>
                </xsl:template>
                <!-- Dynamically build detail result -->
                <xsl:template match="details">
                    <xsl:for-each select="$instance/query[@path]">
                        &lt;detail>
                            {string-join($resource/*/<xsl:value-of select="@path"/>, ', ')}
                        &lt;/detail>
                    </xsl:for-each>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="query"/>
    </p:processor>

    <!-- Execute REST submission -->
    <p:processor name="oxf:xforms-submission">
        <p:input name="submission" href="#submission"/>
        <p:input name="request" href="#query"/>
        <p:output name="response" ref="data"/>
    </p:processor>

</p:config>

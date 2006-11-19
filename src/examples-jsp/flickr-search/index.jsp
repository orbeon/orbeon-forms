<%--
    Copyright (C) 2006 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
--%>
<%@ page import="java.util.Random"%>
<%
    // Set content type to XML. By default it will be HTML, and OPS will tidy it.
    response.setContentType("application/xhtml+xml");
%>
<xhtml:html xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:ev="http://www.w3.org/2001/xml-events">

    <xhtml:head>
        <xhtml:title>Flickr Search</xhtml:title>
        <xforms:model>
            <xforms:instance src="/xforms-jsp/flickr-search/service-search.jsp" id="photos"/>
            <xforms:instance id="query">
                <query>red</query>
            </xforms:instance>
            <xforms:submission id="do-query" method="post" replace="instance" ref="instance('query')"
                    instance="photos" action="/xforms-jsp/flickr-search/service-search.jsp"/>
        </xforms:model>
        <xhtml:style type="text/css">
            h1 { display: inline; padding-right: 10px; }
            .paragraph { margin-bottom: 1em; }
            .feedback { background-color: #ffa; margin-left: 10px; padding: 5px; }
            .flickr-image { width: 75px; height:75 px; }
            .xforms-alert-inactive { display: none; }
        </xhtml:style>
    </xhtml:head>
    <xhtml:body>
        <xhtml:div class="paragraph">
            <xhtml:h1>Flickr Search</xhtml:h1>
            <xforms:group>
                <xforms:input ref="instance('query')"/>
                <xforms:trigger>
                    <xforms:label>Flickr Search</xforms:label>
                </xforms:trigger>
                <xforms:send submission="do-query" ev:event="DOMActivate"/>
            </xforms:group>
        </xhtml:div>
        <xhtml:div>
            <xforms:repeat nodeset="photo">
                <xforms:output
                    value="concat(
                            '&lt;a',
                              ' href=&#34;', @page, '&#34;',
                            '/&gt;',
                            '&lt;img',
                              ' src=&#34;', @url, '&#34;',
                              ' width=&#34;75&#34;',
                              ' height=&#34;75&#34;',
                              ' border=&#34;0&#34;',
                              ' style=&#34;flickr-image&#34;',
                            '/&gt;',
                            '&lt;/a&gt;'
                           )"
                        mediatype="text/html"/>
            </xforms:repeat>
        </xhtml:div>
    </xhtml:body>
</xhtml:html>

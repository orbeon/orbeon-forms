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
            .flickr-image { width: 75px; height:75 px; border: none }
            .back { display: block; margin-top: .5em }
        </xhtml:style>
    </xhtml:head>
    <xhtml:body>
        <xhtml:h1>Flickr Search</xhtml:h1>
        <xhtml:div class="paragraph">
            <xforms:group>
                <xforms:input ref="instance('query')">
                    <xforms:label>Search:</xforms:label>
                    <xforms:help>Enter a Flickr search tag</xforms:help>
                    <xforms:hint>Enter a Flickr search tag</xforms:hint>
                </xforms:input>
                <xforms:trigger>
                    <xforms:label>Flickr Search</xforms:label>
                </xforms:trigger>
                <xforms:send submission="do-query" ev:event="DOMActivate"/>
            </xforms:group>
        </xhtml:div>
        <xhtml:div>
            <xforms:repeat nodeset="photo[position() le 50]">
                <xhtml:a href="{@page}">
                    <xhtml:img class="flickr-image" src="{@url}"/>
                </xhtml:a>
            </xforms:repeat>
        </xhtml:div>
        <xhtml:a class="back" href="/">Back to Orbeon Forms Examples</xhtml:a>
    </xhtml:body>
</xhtml:html>

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
<xh:html xmlns:xf="http://www.w3.org/2002/xforms"
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:ev="http://www.w3.org/2001/xml-events">

    <xh:head>
        <xh:title>Flickr Search</xh:title>
        <xf:model>
            <xf:instance src="/xforms-jsp/flickr-search/service-search.jsp" id="photos"/>
            <xf:instance id="query">
                <query>red</query>
            </xf:instance>
            <xf:submission id="do-query" method="post" replace="instance" ref="instance('query')"
                    instance="photos" action="/xforms-jsp/flickr-search/service-search.jsp"/>
        </xf:model>
        <xh:style type="text/css">
            h1 { display: inline; padding-right: 10px; }
            .paragraph { margin-bottom: 1em; }
            .flickr-image { width: 75px; height:75 px; border: none }
            .back { display: block; margin-top: .5em }
        </xh:style>
    </xh:head>
    <xh:body>
        <xh:h1>Flickr Search</xh:h1>
        <xh:div class="paragraph">
            <xf:group>
                <xf:input ref="instance('query')">
                    <xf:label>Search:</xf:label>
                    <xf:help>Enter a Flickr search tag</xf:help>
                    <xf:hint>Enter a Flickr search tag</xf:hint>
                </xf:input>
                <xf:trigger>
                    <xf:label>Flickr Search</xf:label>
                </xf:trigger>
                <xf:send submission="do-query" ev:event="DOMActivate"/>
            </xf:group>
        </xh:div>
        <xh:div>
            <xf:repeat ref="photo[position() le 50]">
                <xh:a href="{@page}">
                    <xh:img class="flickr-image" src="{@url}"/>
                </xh:a>
            </xf:repeat>
        </xh:div>
        <xh:a class="back" href="/">Back to Orbeon Forms Examples</xh:a>
    </xh:body>
</xh:html>

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
            .row { margin-top: 10px }
        </xh:style>
    </xh:head>
    <xh:body>
        <xh:div class="container">
            <xh:h1>Flickr Search</xh:h1>
            <xh:div class="row">
                <xh:div class="span9">
                    <xf:input ref="instance('query')">
                        <xf:label>Search:</xf:label>
                        <xf:hint appearance="minimal">Enter a Flickr search tag</xf:hint>
                    </xf:input>
                    <xf:trigger>
                        <xf:label>Flickr Search</xf:label>
                    </xf:trigger>
                    <xf:send submission="do-query" ev:event="DOMActivate"/>
                </xh:div>
            </xh:div>
            <xh:div class="row">
                <xh:div class="span9">
                    <xh:ul class="thumbnails">
                        <xf:repeat ref="photo[position() le 50]">
                            <xh:li class="span1">
                                <xh:a href="{@page}" class="thumbnail">
                                    <xh:img class="flickr-image" src="{@url}"/>
                                </xh:a>
                            </xh:li>
                        </xf:repeat>
                    </xh:ul>
                </xh:div>
            </xh:div>
            <xh:div class="row">
                <xh:div class="span9">
                    <xh:a class="back" href="/home/xforms">Back to Orbeon Forms Examples</xh:a>
                </xh:div>
            </xh:div>
        </xh:div>
    </xh:body>
</xh:html>

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
<%@ page import="java.net.URL"%>
<%@ page import="org.orbeon.dom.io.SAXReader"%>
<%@ page import="org.orbeon.dom.Document"%>
<%@ page import="org.orbeon.dom.Element"%>
<%@ page import="java.util.Iterator"%>
<%@ page import="java.net.URLEncoder"%>
<%@ page import="org.orbeon.oxf.xml.XMLParsing" %>
<%
    response.setContentType("application/xml");
    final SAXReader xmlReader = new SAXReader(XMLParsing.newXMLReader(XMLParsing.ParserConfiguration.PLAIN));

    // Build URL for query to Flickr
    String flickrURL = "https://www.flickr.com/services/rest/?method=";
    if ("POST".equals(request.getMethod())) {
        // We got a query string
        final Document queryDocument = xmlReader.read(request.getInputStream());
        final String query = queryDocument.getRootElement().getStringValue();
        flickrURL = "https://www.flickr.com/services/rest/?method=flickr.photos.search&text=" + URLEncoder.encode(query, "UTF-8");
    } else {
        // No query, return interesting photos
        flickrURL += "flickr.interestingness.getList";
    }
    flickrURL += "&per_page=200&api_key=d0c3b54d6fbc1ed217ecc67feb42568b";

    final Document flickrResponse = xmlReader.read(new URL(flickrURL));
    final Element photosElement = flickrResponse.getRootElement().element("photos");
%>
<photos>
<%  for (Iterator photoIterator = photosElement.elementIterator(); photoIterator.hasNext();) {
        final Element photo = (Element) photoIterator.next();
        final String photoURL = "https://static.flickr.com/" + photo.attributeValue("server") + "/" + photo.attributeValue("id")
            + "_" + photo.attributeValue("secret") + "_s.jpg";
        final String pageURL = "https://flickr.com/photos/" + photo.attributeValue("owner") +"/" + photo.attributeValue("id") + "/";
%>
    <photo url="<%=photoURL%>" page="<%=pageURL%>"/>
<%  } %>
</photos>

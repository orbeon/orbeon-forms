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
<%@ page import="org.dom4j.io.SAXReader"%>
<%@ page import="org.dom4j.Document"%>
<%@ page import="org.dom4j.Element"%>
<%@ page import="java.util.Iterator"%>
<%@ page import="java.net.URLEncoder"%>
<%
    response.setContentType("application/xml");
    SAXReader xmlReader = new SAXReader();

    // Build URL for query to Flickr
    String flickrURL = "https://www.flickr.com/services/rest/?method=";
    if ("POST".equals(request.getMethod())) {
        // We got a query string
        Document queryDocument = xmlReader.read(request.getInputStream());
        String query = queryDocument.getRootElement().getStringValue();
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

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
<xhtml:html xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xsl:version="2.0">

    <xhtml:head><xhtml:title>URL Generator</xhtml:title></xhtml:head>
    <xhtml:body>
        <xhtml:p>
            The current CNN Headline is:
        </xhtml:p>
        <xhtml:p>
           <b><xsl:value-of select="/cnn"/></b>
        </xhtml:p>
        <xhtml:p>
            NOTE: The actual format of the CNN Web page is subject to change over time, in which
            case the XPath expression used to capture the headline may no longer work and will need
            to be updated.
        </xhtml:p>
    </xhtml:body>
</xhtml:html>

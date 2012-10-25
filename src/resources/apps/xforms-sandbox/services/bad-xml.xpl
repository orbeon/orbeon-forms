<!--
  Copyright (C) 2009 Orbeon, Inc.

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
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xs="http://www.w3.org/2001/XMLSchema">

    <p:processor name="oxf:url-generator">
        <p:input name="config">
            <config>
                <url>bad-xml.txt</url>
                <content-type>text/plain</content-type>
            </config>
        </p:input>
        <p:output name="data" id="document"/>
    </p:processor>

    <p:processor name="oxf:http-serializer">
        <p:input name="config">
            <config>
                <content-type>application/xml</content-type>
                <ignore-document-content-type>true</ignore-document-content-type>
                <cache-control>
                    <use-local-cache>false</use-local-cache>
                </cache-control>
            </config>
        </p:input>
        <p:input name="data" href="#document"/>
    </p:processor>

</p:config>

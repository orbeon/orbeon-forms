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
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <!-- Execute the XSLT transformation -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="foo.xml"/>
        <p:input name="config" href="foo.xsl"/>
        <p:output name="data" id="result"/>
    </p:processor>

    <!-- Convert the document to XML text -->
    <p:processor name="oxf:xml-converter">
        <p:input name="config"><config/></p:input>
        <p:input name="data" href="#result"/>
        <p:output name="data" id="xml"/>
    </p:processor>

    <!-- Serialize -->
    <p:processor name="oxf:http-serializer">
        <p:input name="config"><config/></p:input>
        <p:input name="data" href="#xml"/>
    </p:processor>

</p:config>

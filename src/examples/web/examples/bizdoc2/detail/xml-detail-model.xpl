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

    <p:param name="instance" type="input"/>

    <!-- Call the data access layer -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../../bizdoc/data-access/delegate/read-document.xpl"/>
        <p:input name="document-id" href="#instance#xpointer(/document-id)"/>
        <p:output name="document-info" id="document"/>
    </p:processor>

    <p:processor name="oxf:xml-serializer">
        <p:input name="data" href="#document#xpointer(/*/document/*)"/>
        <p:input name="config">
            <config>
                <content-type>application/xml</content-type>
            </config>
        </p:input>
    </p:processor>

</p:config>

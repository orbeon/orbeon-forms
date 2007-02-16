<!--
    Copyright (C) 2007 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<!--
    This pipeline dispatches incoming requests to different page flow
    depending on the portlet mode requested.
 -->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <!-- Get request information -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/portlet-mode</include>
                <include>/request/request-path</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!-- Handle different page flows depending on portlet mode -->
    <p:choose href="#request">
        <!-- Page Flow for "edit" mode -->
        <p:when test="/request/portlet-mode = 'edit'">
            <p:processor name="oxf:page-flow">
                <p:input name="controller" href="oxf:/page-flow-portlet-edit.xml"/>
            </p:processor>
        </p:when>
        <!-- Page Flow for other modes (including "view") -->
        <p:otherwise>
            <p:processor name="oxf:page-flow">
                <p:input name="controller" href="oxf:/page-flow-portlet.xml"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>

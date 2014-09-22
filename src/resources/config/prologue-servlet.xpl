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
<p:config
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <!-- WARNING: Don't run oxf:request here so that uploads work! -->

    <!-- Handle different page flows depending on portlet mode -->
    <p:choose href="aggregate('null')"><!-- dummy test input -->
        <!--
            2014-09-22: Currently we cannot use the following PFC entry, because the PFC uses oxf:request. We might be
            able to update the PFC so that, when appropriate or via configuration, oxf:request is not inserted. This
            would allow us to use the PFC only instead of "XPL then PFC".

            <page path="/xforms-server($|/.*)" model="/ops/xforms/xforms-server.xpl" public-methods="HEAD GET POST"/>

            WARNING: We avoid matching /xforms-server-submit here!
        -->
        <p:when test="p:get-request-path() = '/xforms-server' or starts-with(p:get-request-path(), '/xforms-server/')">
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="oxf:/ops/xforms/xforms-server.xpl"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <p:processor name="oxf:page-flow">
                <p:input name="controller" href="oxf:/page-flow.xml"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>

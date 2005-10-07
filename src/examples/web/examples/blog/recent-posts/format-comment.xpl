<!--
    Copyright (C) 2005 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:xs="http://www.w3.org/2001/XMLSchema">

    <p:param type="input" name="instance"/>
    <p:param type="output" name="data" debug="xxxformatted-comment"/>

    <!-- Format comment -->
    <p:processor name="oxf:xslt">
        <p:input name="config" href="format-comment.xsl"/>
        <p:input name="data" href="#instance"/>
        <p:output name="data" id="formatted-comment"/>
    </p:processor>

    <!-- Call data access to update post -->
    <p:processor name="oxf:exception-catcher">
        <p:input name="data" href="#formatted-comment"/>
        <p:output name="data" id="formatted-comment-or-exception" debug="xxxformatted-comment-or-exception"/>
    </p:processor>

    <p:choose href="#formatted-comment-or-exception">
        <p:when test="/exceptions">
            <p:processor name="oxf:identity">
                <p:input name="data">
                    <error/>
                </p:input>
                <p:output name="data" ref="data"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <p:processor name="oxf:identity">
                <p:input name="data" href="#formatted-comment-or-exception"/>
                <p:output name="data" ref="data"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>

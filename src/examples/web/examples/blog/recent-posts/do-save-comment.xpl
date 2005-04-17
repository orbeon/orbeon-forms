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

    <!-- Call data access to get post -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../data-access/get-post.xpl"/>
        <p:input name="query" href="aggregate('query', #instance#xpointer(/*/username|/*/post-id))"/>
        <p:output name="post" id="post"/>
    </p:processor>

    <!-- Append current comment -->
    <p:processor name="oxf:xslt">
        <p:input name="config" debug="xxxst">
            <xsl:stylesheet version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <xsl:template match="/post/comments">
                    <xsl:copy>
                        <xsl:apply-templates/>
                        <!-- Just copy comment as captured by XForms -->
                        <xsl:copy-of select="doc('input:instance')/*//comment"/>
                    </xsl:copy>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:input name="data" href="#post" debug="xxxpost"/>
        <p:input name="instance" href="#instance"/>
        <p:output name="data" id="updated-post"/>
    </p:processor>

    <!-- Call data access to update post -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../data-access/update-post.xpl"/>
        <p:input name="post" href="#updated-post"/>
    </p:processor>

</p:config>

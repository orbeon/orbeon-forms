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
    <p:param type="output" name="data"/>

<!--    <p:processor name="oxf:xslt">-->
<!--        <p:input name="data" href="#params"/>-->
<!--        <p:input name="config">-->
<!--            <query xsl:version="2.0">-->
<!--                <username>ebruchez</username>-->
<!--                <blog-id>500001</blog-id>-->
<!--            </query>-->
<!--        </p:input>-->
<!--        <p:output name="data" id="query"/>-->
<!--    </p:processor>-->

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../data-access/get-recent-posts.xpl"/>
        <p:input name="query" href="aggregate('query', #instance#xpointer(/*/*))"/>
        <p:output name="posts" id="posts"/>
    </p:processor>

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../data-access/get-categories.xpl"/>
        <p:input name="query" href="aggregate('query', #instance#xpointer(/*/username|/*/blog-id))"/>
        <p:output name="categories" id="categories"/>
    </p:processor>

    <p:processor name="oxf:identity">
        <p:input name="data" href="aggregate('model', #posts, #categories)"/>
        <p:output name="posts" ref="data"/>
    </p:processor>

</p:config>

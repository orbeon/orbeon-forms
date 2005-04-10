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

    <!-- Call data access to get post -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../data-access/get-post.xpl"/>
        <p:input name="query" href="aggregate('query', #instance#xpointer(/*/username|/*/post-id))"/>
        <p:output name="post" id="post"/>
    </p:processor>

    <!-- Call data access to get blog information -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../data-access/get-user-blogs.xpl"/>
        <p:input name="query" href="aggregate('query', #instance#xpointer(/*/username), #post#xpointer(/*/blog-id))"/>
        <p:output name="blogs" id="blogs"/>
    </p:processor>

    <!-- Call data access to get list of categories -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../data-access/get-categories.xpl"/>
        <p:input name="query" href="aggregate('query', #instance#xpointer(/*/username), #post#xpointer(/*/blog-id))"/>
        <p:output name="categories" id="categories"/>
    </p:processor>

    <!-- Produce model -->
    <p:processor name="oxf:xslt">
        <p:input name="config" href="post-model-format.xsl"/>
        <p:input name="data"><dummy/></p:input>
        <p:input name="instance" href="#instance"/>
        <p:input name="blog" href="#blogs#xpointer(/*/blog[1])"/>
        <p:input name="post" href="#post"/>
        <p:input name="categories" href="#categories"/>
        <p:output name="data" ref="data" debug="zzzmodel"/>
    </p:processor>

</p:config>

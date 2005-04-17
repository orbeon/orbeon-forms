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
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- Extract request body -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config stream-type="xs:anyURI">
                <include>/request/body</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!-- Dereference URI and return XML -->
    <p:processor name="oxf:url-generator">
        <p:input name="config" href="aggregate('config', aggregate('url', #request#xpointer(string(/request/body))))"/>
        <p:output name="data" id="xmlrpc-request" schema-href="schema/xml-rpc-request.rng" debug="xxxrequest"/>
    </p:processor>

    <!-- Handle XML-RPC authentication based on username + password -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#xmlrpc-request"/>
        <p:input name="config">
            <query xsl:version="2.0">
                <username><xsl:value-of select="/methodCall/params/param[2]/value/string"/></username>
                <password><xsl:value-of select="/methodCall/params/param[3]/value/string"/></password>
            </query>
        </p:input>
        <p:output name="data" id="authenticate-query"/>
    </p:processor>

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="data-access/authenticate-user.xpl"/>
        <p:input name="query" href="#authenticate-query"/>
        <p:output name="authenticated" id="authenticated"/>
    </p:processor>
    
    <p:choose href="#authenticated" debug="xxxauthenticated">
        <p:when test="/* = 'true'">
            <!-- Dispatch request if use is authenticated -->
            <p:choose href="#xmlrpc-request" debug="xxxrequest">
                <p:when test="/methodCall/methodName = 'blogger.getUsersBlogs'">
                    <p:processor name="oxf:pipeline">
                        <p:input name="config" href="xml-rpc/get-user-blogs.xpl"/>
                        <p:input name="params" href="#xmlrpc-request#xpointer(/*/params)"/>
                        <p:output name="params" id="response"/>
                    </p:processor>
                </p:when>
                <p:when test="/methodCall/methodName = 'metaWeblog.getCategories'">
                    <p:processor name="oxf:pipeline">
                        <p:input name="config" href="xml-rpc/get-categories.xpl"/>
                        <p:input name="params" href="#xmlrpc-request#xpointer(/*/params)"/>
                        <p:output name="params" id="response"/>
                    </p:processor>
                </p:when>
                <p:when test="/methodCall/methodName = 'metaWeblog.newPost'">
                    <p:processor name="oxf:pipeline">
                        <p:input name="config" href="xml-rpc/new-post.xpl"/>
                        <p:input name="params" href="#xmlrpc-request#xpointer(/*/params)"/>
                        <p:output name="params" id="response"/>
                    </p:processor>
                </p:when>
                <p:when test="/methodCall/methodName = 'metaWeblog.getRecentPosts'">
                    <p:processor name="oxf:pipeline">
                        <p:input name="config" href="xml-rpc/get-recent-posts.xpl"/>
                        <p:input name="params" href="#xmlrpc-request#xpointer(/*/params)"/>
                        <p:output name="params" id="response"/>
                    </p:processor>
                </p:when>
                <p:when test="/methodCall/methodName = 'metaWeblog.getPost'">
                    <p:processor name="oxf:pipeline">
                        <p:input name="config" href="xml-rpc/get-post.xpl"/>
                        <p:input name="params" href="#xmlrpc-request#xpointer(/*/params)"/>
                        <p:output name="params" id="response"/>
                    </p:processor>
                </p:when>
                <p:when test="/methodCall/methodName = 'metaWeblog.editPost'">
                    <p:processor name="oxf:pipeline">
                        <p:input name="config" href="xml-rpc/edit-post.xpl"/>
                        <p:input name="params" href="#xmlrpc-request#xpointer(/*/params)"/>
                        <p:output name="params" id="response"/>
                    </p:processor>
                </p:when>
                <p:when test="/methodCall/methodName = 'blogger.deletePost'">
                    <p:processor name="oxf:pipeline">
                        <p:input name="config" href="xml-rpc/delete-post.xpl"/>
                        <p:input name="params" href="#xmlrpc-request#xpointer(/*/params)"/>
                        <p:output name="params" id="response"/>
                    </p:processor>
                </p:when>
            </p:choose>
        </p:when>
        <p:otherwise>
            <!-- Return fault if authentication failed -->
            <p:processor name="oxf:identity">
                <p:input name="data">
                    <fault>
                        <value>
                            <struct>
                                <member>
                                    <name>faultCode</name>
                                    <value>
                                        <int>4</int>
                                    </value>
                                </member>
                                <member>
                                    <name>faultString</name>
                                    <value>
                                        <string>Error: User authentication failed.</string>
                                    </value>
                                </member>
                            </struct>
                        </value>
                    </fault>
                </p:input>
                <p:output name="data" id="response"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

    <!-- Generate response -->
    <p:processor name="oxf:xml-serializer">
        <p:input name="data" href="aggregate('methodResponse', #response)" schema-href="schema/xml-rpc-response.rng" debug="xxxresponse"/>
        <p:input name="config">
            <config/>
        </p:input>
    </p:processor>

</p:config>

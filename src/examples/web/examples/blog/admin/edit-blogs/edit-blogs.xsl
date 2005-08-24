<?xml version="1.0" encoding="iso-8859-1"?>
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
<html xsl:version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:ev="http://www.w3.org/2001/xml-events"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:xi="http://www.w3.org/2003/XInclude"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns="http://www.w3.org/1999/xhtml">

    <head>
        <title>Blogs</title>
        <xforms:model>
            <xforms:instance id="blogs-instance">
                <xsl:copy-of select="/*/blogs"/>
            </xforms:instance>
            <xforms:instance id="users-instance">
                <xsl:copy-of select="/*/users"/>
            </xforms:instance>
            <xforms:instance id="add-blog-request">
                <blog xmlns="">
                    <blog-id/>
                    <username/>
                    <name/>
                    <categories>
                        <category>
                            <name/>
                            <id/>
                        </category>
                    </categories>
                </blog>
            </xforms:instance>
            <xforms:instance id="delete-blog-request">
                <query xmlns="">
                    <blog-id/>
                </query>
            </xforms:instance>
            <xforms:instance id="triggers-instance">
                <triggers xmlns="">
                    <delete-category/>
                </triggers>
            </xforms:instance>
            <xforms:bind nodeset="instance('add-blog-request')">
                <xforms:bind nodeset="username" constraint="normalize-space(.) != ''"/>
                <xforms:bind nodeset="name" constraint="normalize-space(.) != ''"/>
                <!--<xforms:bind nodeset="categories/category" constraint="normalize-space(name) != ''"/>-->
            </xforms:bind>
            <xforms:bind nodeset="instance('triggers-instance')/delete-category" readonly="count(instance('add-blog-request')/categories/category) lt 2"/>
            <!-- Submissions -->
            <xforms:submission id="add-submission" ref="instance('add-blog-request')" replace="instance" instance="blogs-instance" method="post" action="/blog/admin/add-blog">
                <xforms:action ev:event="xforms-submit-done">
                    <xforms:setvalue ref="instance('add-blog-request')/name"/>
                    <xforms:setvalue ref="instance('add-blog-request')/categories/category/name"/>
                </xforms:action>
            </xforms:submission>
            <xforms:submission id="delete-submission" ref="instance('delete-blog-request')" replace="instance" instance="blogs-instance" method="post" action="/blog/admin/delete-blog"/>
            <xforms:submission id="update-submission" ref="instance('update-blogs-request')" replace="instance" instance="blogs-instance" method="post" action="/blog/admin/update-blogs">

            </xforms:submission>
        </xforms:model>
    </head>
    <body>
        <div class="maincontent">
            <h2>Existing Blogs</h2>
            <table class="gridtable">
                <tr>
                    <th>Blog Name</th>
                    <th>Username</th>
                    <th>Blog Id</th>
                    <th>Categories</th>
                    <th>Action</th>
                </tr>
                <xforms:repeat nodeset="blog" id="blogRepeat">
                    <tr>
                        <td>
                            <xforms:input ref="name" xhtml:size="30"/>
                        </td>
                        <td>
                            <xforms:output ref="username"/>
                        </td>
                        <td>
                            <xforms:output ref="blog-id"/>
                        </td>
                        <td>
                            <table>
                                <xforms:repeat nodeset="categories/category" id="categoryRepeat">
                                    <tr>
                                        <td>
                                            <xforms:input ref="name"/>
                                        </td>
                                    </tr>
                                </xforms:repeat>
                            </table>
                        </td>
                        <td>
                            <xforms:trigger>
                                <xforms:label>Delete</xforms:label>
                                <xforms:action ev:event="DOMActivate">
                                    <xforms:setvalue ref="instance('delete-blog-request')/blog-id" value="instance('blogs-instance')/blog[index('blogRepeat')]/blog-id"/>
                                    <xforms:send submission="delete-submission"/>
                                </xforms:action>
                            </xforms:trigger>
                        </td>
                    </tr>
                </xforms:repeat>
            </table>
            <xforms:trigger>
                <xforms:label>Save Changes</xforms:label>
                <xforms:action ev:event="DOMActivate">
                    <xforms:send submission="update-submission"/>
                </xforms:action>
            </xforms:trigger>
            <h2>Add New Blog</h2>
            <xforms:group ref="instance('add-blog-request')">
                <table>
                    <tr>
                        <th style="text-align: right">Blog Name</th>
                        <td>
                            <xforms:input ref="name" xhtml:size="30"/>
                        </td>
                    </tr>
                    <tr>
                        <th style="text-align: right">Blog Owner</th>
                        <td>
                            <xforms:select1 appearance="minimal" ref="username">
                                <xforms:itemset nodeset="instance('users-instance')/user">
                                    <xforms:label ref="@name"/>
                                    <xforms:value ref="@name"/>
                                </xforms:itemset>
                            </xforms:select1>
                        </td>
                    </tr>
                    <tr>
                        <th style="text-align: right">Categories</th>
                        <td>
                            <table>
                                <xforms:repeat nodeset="categories/category" id="addCategoryRepeat">
                                    <tr>
                                        <td>
                                            <xforms:input ref="name"/>
                                        </td>
                                    </tr>
                                </xforms:repeat>
                            </table>
                            <xforms:trigger>
                                <xforms:label>Add Category</xforms:label>
                                <xforms:action ev:event="DOMActivate">
                                    <xforms:insert nodeset="categories/category" at="index('addCategoryRepeat')" position="after"/>
                                    <xforms:setvalue ref="categories/category[index('addCategoryRepeat')]/name" value="''"/>
                                </xforms:action>
                            </xforms:trigger>
                            <xforms:trigger ref="instance('triggers-instance')/delete-category">
                                <xforms:label>Delete Category</xforms:label>
                                <xforms:action ev:event="DOMActivate">
                                    <xforms:delete ev:event="DOMActivate" nodeset="instance('add-blog-request')/categories/category" at="index('addCategoryRepeat')"/>
                                </xforms:action>
                            </xforms:trigger>
                        </td>
                    </tr>
                    <tr>
                        <td>
                            <xforms:trigger>
                                <xforms:label>Add Blog</xforms:label>
                                <xforms:action ev:event="DOMActivate">
                                    <xforms:send submission="add-submission"/>
                                </xforms:action>
                            </xforms:trigger>
                        </td>
                    </tr>
                </table>
            </xforms:group>
            <p>
                <a href="/blog">Back Home</a>
            </p>
        </div>
    </body>
</html>

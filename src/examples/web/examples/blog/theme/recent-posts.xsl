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
<!--
    This stylesheet formats the recent-posts page.
 -->
<html xsl:version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:ev="http://www.w3.org/2001/xml-events"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:exf="http://www.exforms.org/exf/1-0"
    xmlns:xi="http://www.w3.org/2003/XInclude"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns="http://www.w3.org/1999/xhtml">

    <xsl:variable name="instance" select="doc('input:instance')" as="document-node()"/>

    <head>
        <!-- Display page title -->
        <title>
            <xsl:value-of select="/*/blog/name"/>
            <xsl:text> - Recent Posts</xsl:text>
        </title>
        <style type="text/css">
            .blogImage { margin: 5px; }
            body { font-family: "trebuchet ms", verdana, arial, helvetica, sans-serif }
            h1,h2,h3,h4,h5,h6 { font-weight: normal; border-bottom: 1px solid #ccc; text-align: left }
            ul { list-style-image: url('/images/bullet2.gif') }
            ul ul { list-style-image: url('/images/bullet1.gif') }
            a:hover { background: rgb(92,116,152); color: white }
            <!-- We override those because the repeats here are read-only and highlighting doesn't yield a good result -->
            .xforms-repeat-selected-item-1 { background: white; }
            .xforms-repeat-selected-item-2 { background: white; }
        </style>
        <xforms:model xmlns:xforms="http://www.w3.org/2002/xforms"
              xmlns:xi="http://www.w3.org/2003/XInclude"
              xmlns:xs="http://www.w3.org/2001/XMLSchema"
              xmlns:xdt="http://www.w3.org/2004/07/xpath-datatypes">

            <xforms:instance id="main">
                <xsl:copy-of select="/*"/>
            </xforms:instance>
            <xforms:instance id="new-comment">
                <form xmlns="">
                    <comment>
                        <name/>
                        <email/>
                        <uri/>
                        <content/>
                        <date-created/>
                    </comment>
                    <check>
                        <value1></value1>
                        <value2></value2>
                        <value3/>
                    </check>
                </form>
            </xforms:instance>
            <!-- Handle current validity information for new comments -->
            <xforms:instance id="new-comment-validity">
                <comment xmlns="">
                    <name>1</name>
                    <email>0</email>
                    <uri>0</uri>
                    <content>1</content>
                    <check>1</check>
                </comment>
            </xforms:instance>
            <xforms:instance id="add-comment-request">
                <request xmlns="">
                    <!-- We pass the XML submission so we can retrieve the correct list of posts -->
                    <xsl:copy-of select="/*/submission"/>
                    <!-- This follows the actual database comment format -->
                    <comment>
                        <comment-id/>
                        <blog-id/>
                        <post-id/>
                        <poster-info>
                            <name/>
                            <email/>
                            <uri/>
                        </poster-info>
                        <comment-info>
                            <title/>
                            <content character-count="0" word-count="0"/>
                            <date-created/>
                        </comment-info>
                    </comment>
                </request>
            </xforms:instance>
            <!-- Comment formatting service -->
            <xforms:instance id="format-comment-request">
                <comment xmlns="">
                    <name/>
                    <email/>
                    <uri/>
                    <content/>
                </comment>
            </xforms:instance>
            <xforms:instance id="format-comment-response">
                <comment xmlns="">
                    <name/>
                    <email/>
                    <uri/>
                    <content character-count="0" word-count="0"/>
                </comment>
            </xforms:instance>
            <!-- This instance keeps special properties controlling the appearance of buttons, groups, etc. -->
            <xforms:instance id="utils">
                <utils xmlns="">
                    <submit-comment-trigger/>
                    <comment-preview-group/>
                </utils>
            </xforms:instance>

            <xforms:bind nodeset="instance('utils')/submit-comment-trigger" readonly="sum(instance('new-comment-validity')/*) != 0"/>
            <xforms:bind nodeset="instance('utils')/comment-preview-group" relevant="sum(instance('new-comment-validity')/(* except check)) = 0"/>

            <xforms:bind nodeset="instance('main')">
                <xforms:bind nodeset="posts/day/date" type="xs:date"/>
                <xforms:bind nodeset="posts/day/post/date-created" type="xs:dateTime"/>
                <xforms:bind nodeset="posts/day/post/comments/comment/comment-info/date-created" type="xs:dateTime"/>
                <xforms:bind nodeset="posts/day/post/comments/@show" relevant=". = 'true'"/>
            </xforms:bind>

            <xforms:bind nodeset="instance('new-comment')">
                <!-- Handle human check -->
                <xforms:bind nodeset="check/value1" calculate="if (. castable as xs:integer) then . else xs:integer(seconds-from-time(current-time()))"/>
                <xforms:bind nodeset="check/value2" calculate="if (. castable as xs:integer) then . else xs:integer(minutes-from-time(current-time()))"/>
                <xforms:bind nodeset="check/value3" constraint=". castable as xs:positiveInteger and (xs:integer(.) = xs:integer(../value1) + xs:integer(../value2))"/>
                <!-- Handle creation date -->
                <xforms:bind nodeset="comment/date-created"
                             calculate="xs:string(adjust-dateTime-to-timezone(current-dateTime(), xdt:dayTimeDuration('PT0H')))"
                             type="xs:dateTime"/>
                <!-- Validate comment -->
                <xforms:bind nodeset="comment/name" constraint="normalize-space(.) != ''" required="true"/>
                <xforms:bind nodeset="comment/uri" type="xs:anyURI"/>
                <xforms:bind nodeset="comment/content" constraint="normalize-space(instance('format-comment-response')/content) != ''" required="true"/>
                <!--<xforms:bind nodeset="comment/email" constraint="normalize-space(.) = '' or matches(., '[A-Za-z0-9!#-''\*\+\-/=\?\^_`\{-~]+(\.[A-Za-z0-9!#-''\*\+\-/=\?\^_`\{-~]+)*@[A-Za-z0-9!#-''\*\+\-/=\?\^_`\{-~]+(\.[A-Za-z0-9!#-''\*\+\-/=\?\^_`\{-~]+)*')"/>-->
                <!--<xsd:pattern value="[A-Za-z0-9!#-'\*\+\-/=\?\^_`\{-~]+(\.[A-Za-z0-9!#-'\*\+\-/=\?\^_`\{-~]+)*@[A-Za-z0-9!#-'\*\+\-/=\?\^_`\{-~]+(\.[A-Za-z0-9!#-'\*\+\-/=\?\^_`\{-~]+)*"/>-->
            </xforms:bind>

            <!-- Call comment saving service-->
            <xforms:submission id="save-comment-submission" method="post" action="/blog/save-comment" ref="instance('add-comment-request')" replace="instance" instance="main">
                <xforms:action ev:event="xforms-submit-done">
                    <!-- We clear the comment and the comment check value -->
                    <xforms:setvalue ref="instance('new-comment')/check/value1"/>
                    <xforms:setvalue ref="instance('new-comment')/check/value2"/>
                    <xforms:setvalue ref="instance('new-comment')/check/value3"/>
                    <xforms:setvalue ref="instance('new-comment')/comment/content"/>
                    <xforms:recalculate/>
                    <!-- TODO: Display status message -->
                </xforms:action>
                <xforms:action ev:event="xforms-submit-error">
                    <!-- TODO: Display status message -->
                </xforms:action>
            </xforms:submission>

            <!-- Call comment formatting service -->
            <xforms:submission id="format-comment-submission" method="post" action="/blog/format-comment" ref="instance('format-comment-request')" replace="instance" instance="format-comment-response"/>
        </xforms:model>
    </head>
    <body>

        <!--<xforms:output ref="instance('new-comment-validity')" mediatype="text/html"/>-->

        <table>
            <tr>
                <!-- Left column -->
                <td style="vertical-align: top; padding: 10px; padding-top: 0px">
                    <!-- List all categories -->
                    <xsl:text>Categories: </xsl:text>
                    <xsl:for-each select="/*/categories/category">
                        <xsl:if test="position() > 1">
                            <xsl:text> | </xsl:text>
                        </xsl:if>
                        <xsl:choose>
                            <xsl:when test="$instance/*/category = id">
                                <xsl:value-of select="name"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <a href="{link}">
                                    <xsl:value-of select="name"/>
                                </a>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:for-each>

                    <!-- List categories with XForms -->
                    <!--<xforms:repeat nodeset="instance('main')/categories/category" id="categoryRepeat">-->
                        <!--<xforms:output value="if (preceding-sibling::category) then ' | ' else ''"/>-->
                        <!--<xforms:output ref="name"/>-->
                        <!--<xforms:trigger appearance="xxforms:link">-->
                            <!--<xforms:label ref="name"/>-->
                            <!--<xforms:load ev:event="DOMActivate" ref="link"/>-->
                        <!--</xforms:trigger>-->
                    <!--</xforms:repeat>-->

                    <!-- Display posts with XForms -->
                    <!-- Posts are alread grouped by day -->
                    <xforms:repeat nodeset="instance('main')/posts/day" id="day-repeat">
                        <h2>
                            <xforms:output ref="date"/>
                        </h2>
                        <xforms:repeat nodeset="post" id="post-repeat">
                            <!-- Display post title -->
                            <!--<a name="{links/fragment-name}"/>-->
                            <a name="xxx"/>
                            <h3>
                                <img src="/images/text.gif" style="border: 0px" alt="Icon"/>
                                <xforms:output value="' '"/>
                                <xforms:trigger appearance="xxforms:link">
                                    <xforms:label ref="title"/>
                                    <xforms:load ev:event="DOMActivate" ref="links/post"/>
                                </xforms:trigger>
                                <!--<a href="{links/post}">-->
                                    <!--<img src="/images/text.gif" style="border: 0px"/>-->
                                    <!--<xforms:output value="' '"/>-->
                                    <!--<xforms:output ref="title"/>-->
                                <!--</a>-->
                            </h3>
                            <!-- Display post content -->
                            <div style="margin-left: 2em; border: 1px solid #ccc; padding: 1em; background-color: #eee">
                                <xforms:output ref="content" mediatype="text/html"/>
                            </div>
                            <!-- Display post information and links -->
                            <div style="margin-left: 2em; padding: 1em; padding-top: 0em">
                                <xforms:output value="'Filed under: '"/>
                                <xforms:repeat nodeset="categories/category" id="category-repeat">
                                    <xforms:output value="if (preceding-sibling::name) then ', ' else ''"/>
                                    <!--<a href="{link}">-->
                                        <!--<xforms:output ref="name"/>-->
                                    <!--</a>-->
                                    <xforms:trigger appearance="xxforms:link">
                                        <xforms:label ref="name"/>
                                        <xforms:load ev:event="DOMActivate" ref="link"/>
                                    </xforms:trigger>
                                </xforms:repeat>
                                <xforms:output value="' — '"/>
                                <xforms:output ref="/*/user/username"/>
                                <xforms:output value="' @ '"/>
                                <xforms:output ref="date-created"/>
                                <xforms:output value="' '"/>
                                <!--<a href="{links/post}">Permalink</a>-->
                                <xforms:trigger appearance="xxforms:link">
                                    <xforms:label>Permalink</xforms:label>
                                    <xforms:load ev:event="DOMActivate" ref="links/post"/>
                                </xforms:trigger>
                                <xforms:output value="' '"/>
                                <xforms:output value="' '"/>
                                <!--<a href="{links/comments}">Comments [<xforms:output value="count(comments/comment)"/>]</a>-->
                                <xforms:trigger appearance="xxforms:link">
                                    <xforms:label>Comments</xforms:label>
                                    <!--<xforms:label>Comments [<xforms:output value="count(comments/comment)"/>]</xforms:label>-->
                                    <xforms:load ev:event="DOMActivate" ref="links/comments"/>
                                </xforms:trigger>
                            </div>

                            <!-- Display comments -->
                            <xforms:group ref="comments/@show">
                                <div>
                                    <xforms:group ref="instance('new-comment')">
                                        <h3>Post New Comment</h3>
                                        <table>
                                            <tr>
                                                <th style="text-align: right">Name:</th>
                                                <td style="width: 100%">
                                                    <xforms:input ref="comment/name" incremental="true">
                                                        <xforms:alert>Please enter a correct name!</xforms:alert>
                                                        <xforms:hint>Please enter your name here.</xforms:hint>
                                                        <xforms:action ev:event="xforms-value-changed">
                                                            <xforms:setvalue ref="instance('format-comment-request')/name" value="instance('new-comment')/comment/name"/>
                                                            <xforms:setvalue ref="instance('format-comment-request')/email" value="instance('new-comment')/comment/email"/>
                                                            <xforms:setvalue ref="instance('format-comment-request')/uri" value="instance('new-comment')/comment/uri"/>
                                                            <xforms:setvalue ref="instance('format-comment-request')/content" value="instance('new-comment')/comment/content"/>
                                                            <xforms:send submission="format-comment-submission"/>
                                                        </xforms:action>
                                                        <xforms:action ev:event="xforms-invalid">
                                                            <xforms:setvalue ref="instance('new-comment-validity')/name" value="1"/>
                                                        </xforms:action>
                                                        <xforms:action ev:event="xforms-valid">
                                                            <xforms:setvalue ref="instance('new-comment-validity')/name" value="0"/>
                                                        </xforms:action>
                                                    </xforms:input>
                                                </td>
                                            </tr><!-- TODO: Add switch around Email and Web site, and use link to show this -->
                                            <tr>
                                                <th style="text-align: right">Email:</th>
                                                <td>
                                                    <xforms:input ref="comment/email" incremental="true">
                                                        <xforms:alert>Please enter a correct email address!</xforms:alert>
                                                        <xforms:hint>Please enter an optional email address.</xforms:hint>
                                                        <xforms:action ev:event="xforms-value-changed">
                                                            <xforms:setvalue ref="instance('format-comment-request')/name" value="instance('new-comment')/comment/name"/>
                                                            <xforms:setvalue ref="instance('format-comment-request')/email" value="instance('new-comment')/comment/email"/>
                                                            <xforms:setvalue ref="instance('format-comment-request')/uri" value="instance('new-comment')/comment/uri"/>
                                                            <xforms:setvalue ref="instance('format-comment-request')/content" value="instance('new-comment')/comment/content"/>
                                                            <xforms:send submission="format-comment-submission"/>
                                                        </xforms:action>
                                                        <xforms:action ev:event="xforms-invalid">
                                                            <xforms:setvalue ref="instance('new-comment-validity')/email" value="1"/>
                                                        </xforms:action>
                                                        <xforms:action ev:event="xforms-valid">
                                                            <xforms:setvalue ref="instance('new-comment-validity')/email" value="0"/>
                                                        </xforms:action>
                                                    </xforms:input>
                                                </td>
                                            </tr>
                                            <tr>
                                                <th style="text-align: right">Web Site:</th>
                                                <td>
                                                    <xforms:input ref="comment/uri" incremental="true">
                                                        <xforms:hint>Please enter an optional web site URL.</xforms:hint>
                                                        <xforms:action ev:event="xforms-value-changed">
                                                            <xforms:setvalue ref="instance('format-comment-request')/name" value="instance('new-comment')/comment/name"/>
                                                            <xforms:setvalue ref="instance('format-comment-request')/email" value="instance('new-comment')/comment/email"/>
                                                            <xforms:setvalue ref="instance('format-comment-request')/uri" value="instance('new-comment')/comment/uri"/>
                                                            <xforms:setvalue ref="instance('format-comment-request')/content" value="instance('new-comment')/comment/content"/>
                                                            <xforms:send submission="format-comment-submission"/>
                                                        </xforms:action>
                                                        <xforms:action ev:event="xforms-invalid">
                                                            <xforms:setvalue ref="instance('new-comment-validity')/uri" value="1"/>
                                                        </xforms:action>
                                                        <xforms:action ev:event="xforms-valid">
                                                            <xforms:setvalue ref="instance('new-comment-validity')/uri" value="0"/>
                                                        </xforms:action>
                                                    </xforms:input>
                                                </td>
                                            </tr>
                                            <tr>
                                                <th colspan="2" style="text-align: left">
                                                    Enter your comment below:
                                                </th>
                                            </tr>
                                            <tr>
                                                <td colspan="2" style="white-space: nowrap">
                                                    <!--  xhtml:style="background: #eee; border: 1px solid #ccc" -->
                                                    <xforms:textarea ref="comment/content" incremental="true" xhtml:rows="10" xhtml:cols="80">
                                                        <xforms:alert>Please enter a valid comment!</xforms:alert>
                                                        <!-- Send comment to the server for formatting -->
                                                        <xforms:action ev:event="xforms-value-changed">
                                                            <xforms:setvalue ref="instance('format-comment-request')/name" value="instance('new-comment')/comment/name"/>
                                                            <xforms:setvalue ref="instance('format-comment-request')/email" value="instance('new-comment')/comment/email"/>
                                                            <xforms:setvalue ref="instance('format-comment-request')/uri" value="instance('new-comment')/comment/uri"/>
                                                            <xforms:setvalue ref="instance('format-comment-request')/content" value="instance('new-comment')/comment/content"/>
                                                            <xforms:send submission="format-comment-submission"/>
                                                        </xforms:action>
                                                        <xforms:action ev:event="xforms-invalid">
                                                            <xforms:setvalue ref="instance('new-comment-validity')/content" value="1"/>
                                                        </xforms:action>
                                                        <xforms:action ev:event="xforms-valid">
                                                            <xforms:setvalue ref="instance('new-comment-validity')/content" value="0"/>
                                                        </xforms:action>
                                                    </xforms:textarea>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td>
                                                    <span style="white-space: nowrap">
                                                        <xforms:trigger ref="instance('utils')/submit-comment-trigger">
                                                            <xforms:label>Submit Comment</xforms:label>
                                                            <xforms:action ev:event="DOMActivate">
                                                                <xforms:setvalue ref="instance('add-comment-request')/comment/blog-id" value="instance('main')/submission/*/blog-id"/>
                                                                <xforms:setvalue ref="instance('add-comment-request')/comment/post-id" value="instance('main')/submission/*/post-id"/>
                                                                <xforms:setvalue ref="instance('add-comment-request')/comment/poster-info/name" value="instance('new-comment')/comment/name"/>
                                                                <xforms:setvalue ref="instance('add-comment-request')/comment/poster-info/email" value="instance('new-comment')/comment/email"/>
                                                                <xforms:setvalue ref="instance('add-comment-request')/comment/comment-info/content" value="instance('new-comment')/comment/content"/>
                                                                <xforms:setvalue ref="instance('add-comment-request')/comment/comment-info/content/@character-count" value="instance('format-comment-response')/content/@character-count"/>
                                                                <xforms:setvalue ref="instance('add-comment-request')/comment/comment-info/content/@word-count" value="instance('format-comment-response')/content/@word-count"/>
                                                                <xforms:setvalue ref="instance('add-comment-request')/comment/comment-info/date-created" value="instance('new-comment')/comment/date-created"/>
                                                                <xforms:send submission="save-comment-submission"/>
                                                            </xforms:action>
                                                        </xforms:trigger>
                                                        <xforms:output value="' '"/>
                                                        <xforms:output ref="check/value1"/>
                                                        <xforms:output value="' + '"/>
                                                        <xforms:output ref="check/value2"/>
                                                        <xforms:output value="' = '"/>
                                                    </span>
                                                </td>
                                                <td>
                                                    <xforms:input ref="check/value3" incremental="true" xhtml:size="3">
                                                        <xforms:alert>Please enter a correct check value!</xforms:alert>
                                                        <xforms:hint>Please enter a check value. This is a measure against comment spam.</xforms:hint>
                                                        <xforms:action ev:event="xforms-invalid">
                                                            <xforms:setvalue ref="instance('new-comment-validity')/check" value="1"/>
                                                        </xforms:action>
                                                        <xforms:action ev:event="xforms-valid">
                                                            <xforms:setvalue ref="instance('new-comment-validity')/check" value="0"/>
                                                        </xforms:action>
                                                    </xforms:input>
                                                </td>
                                            </tr>
                                        </table>
                                        <!-- Display comment preview -->
                                        <xforms:group ref="instance('utils')/comment-preview-group">
                                            <xforms:group ref="instance('format-comment-response')">
                                                <p>
                                                    This is how your comment will appear:
                                                </p>
                                                <!-- Display comment content -->
                                                <div style="margin-left: 2em; border: 1px solid #ccc; padding: 1em; background-color: #ffff9a">
                                                    <xforms:output ref="content" mediatype="text/html"/>
                                                </div>
                                                <!-- Display comment information and links -->
                                                <div style="margin-left: 2em; padding: 1em; padding-top: 0em">
                                                    Comment by
                                                    <!--<a href="mailto:{$instance/form/comment/email}"> xxx -->
                                                    <xforms:output ref="name"/>
                                                    <xforms:output value="' @ '"/>
                                                    <xforms:output ref="instance('new-comment')/comment/date-created"/>
                                                    <xforms:output value="' ('"/>
                                                    <xforms:output ref="content/@word-count"/>
                                                    <xforms:output value="if (content/@word-count > 1) then ' words, ' else ' word, '"/>
                                                    <xforms:output ref="content/@character-count"/>
                                                    <xforms:output value="if (content/@character-count > 1) then ' characters)' else ' character)'"/>
                                                </div>
                                            </xforms:group>
                                        </xforms:group>
                                        <!-- Display existing comments with XForms -->
                                        <h3>Comments</h3>
                                        <xforms:repeat nodeset="instance('main')/posts/day/post/comments/comment" id="comment-repeat">
                                            <!-- Display comment content -->
                                            <div style="margin-left: 2em; border: 1px solid #ccc; padding: 1em">
                                                <xforms:output ref="comment-info/content" mediatype="text/html"/>
                                            </div>
                                            <!-- Display comment information and links -->
                                            <div style="margin-left: 2em; padding: 1em; padding-top: 0em">
                                                Comment by
                                                <!--<a href="mailto:{$instance/form/comment/email}"> xxx -->
                                                    <xforms:output ref="poster-info/name"/>
                                                <xforms:output value="' @ '"/>
                                                <xforms:output ref="comment-info/date-created"/>
                                                <xforms:output value="' ('"/>
                                                <xforms:output ref="comment-info/content/@word-count"/>
                                                <xforms:output value="if (comment-info/content/@word-count > 1) then ' words, ' else ' word, '"/>
                                                <xforms:output ref="comment-info/content/@character-count"/>
                                                <xforms:output value="if (comment-info/content/@character-count > 1) then ' characters)' else ' character)'"/>
                                            </div>
                                            <!-- TODO: Display "No comments yet." when there are no comments-->
                                        </xforms:repeat>
                                    </xforms:group>
                                </div>
                            </xforms:group>

                        </xforms:repeat>
                    </xforms:repeat>
                </td>
                <!-- Right column -->
                <td style="vertical-align: top; padding: 10px; padding-top: 0px; border-left: 1px solid #cccccc">
                    <table style="border: 0px solid #ccc; margin-top: 10px; margin-bottom: 10px; width: 100%" cellpadding="5">
                        <tr>
                            <td>
                                <h2>Latest Entries</h2>
<!--                                <ol style="list-style-position: inside">-->
                                    <xsl:for-each select="/*/posts/day/post">
                                        <li style="white-space: nowrap; list-style-type: none; margin-left: 0px; padding-left: 0px">
                                            <a href="#{links/fragment-name}"><xsl:value-of select="title"/></a>
                                        </li>
                                    </xsl:for-each>
<!--                                </ol>-->
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <h2>RSS Feeds</h2>
                                <p>
                                    Subscribe to this blog using the RSS feeds below:
                                </p>
<!--                                <ol>-->
                                    <xsl:for-each select="/*/feeds/feed">
                                        <li style="white-space: nowrap; list-style-type: none; margin-left: 0px; padding-left: 0px">
                                            <a href="{link}" f:url-type="resource">
                                                <img src="/images/xml.gif" width="36" height="14" style="border: 0px; vertical-align: middle"/>
                                            </a>
                                            <xsl:text> </xsl:text>
                                            <a href="{link}" f:url-type="resource">
                                                <xsl:value-of select="name"/>
                                            </a>
                                        </li>
                                    </xsl:for-each>
<!--                                </ol>-->
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <h2>Blogroll</h2>
                                <p>
                                    These are blogs that I like:
                                </p>
                                <xsl:variable name="blogroll" as="element()">
                                    <blogroll xmlns="">
                                        <entry>
                                            <feed-url>http://feeds.feedburner.com/avernet</feed-url>
                                            <page-url>http://avernet.blogspot.com/</page-url>
                                            <name>Alessandro Vernet</name>
                                        </entry>
                                        <entry>
                                            <feed-url>http://feeds.feedburner.com/scottmcmullan</feed-url>
                                            <page-url>http://www.scottmcmullan.com/blog/</page-url>
                                            <name>Scott McMullan</name>
                                        </entry>
                                        <entry>
                                            <feed-url>http://www.orbeon.com/blog/wp-rss2.php</feed-url>
                                            <page-url>http://www.orbeon.com/blog/</page-url>
                                            <name>XML-Centric Web Apps</name>
                                        </entry>
                                        <entry>
                                            <feed-url>http://feeds.feedburner.com/oss</feed-url>
                                            <page-url>http://otazi.blogspot.com/</page-url>
                                            <name>Omar Tazi</name>
                                        </entry>
                                    </blogroll>
                                </xsl:variable>
                                <xsl:for-each select="$blogroll/entry">
                                    <li style="white-space: nowrap; list-style-type: none; margin-left: 0px; padding-left: 0px">
                                        <a href="{feed-url}">
                                            <img src="/images/xml.gif" width="36" height="14" style="border: 0px; vertical-align: middle"/>
                                        </a>
                                        <xsl:text> </xsl:text>
                                        <a href="{page-url}" title="{name}" class="rBookmark0">
                                            <xsl:value-of select="name"/>
                                        </a>
                                    </li>
                                </xsl:for-each>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>
        <p>
            <a href="/blog">Back Home</a>
        </p>
    </body>
</html>

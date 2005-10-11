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
        </style>
        <xforms:model xmlns:xforms="http://www.w3.org/2002/xforms"
              xmlns:xi="http://www.w3.org/2003/XInclude"
              xmlns:xs="http://www.w3.org/2001/XMLSchema"
              xmlns:xdt="http://www.w3.org/2004/07/xpath-datatypes">
            <xforms:instance id="main">
                <form xmlns="">
                    <action/>
                    <username/>
                    <blog-id/>
                    <post-id/>
                    <format/>
                    <category/>
                    <count>10</count>
                    <comment>
                        <date-created/>
                        <name/>
                        <email/>
                        <uri/>
                        <text/>
                    </comment>
                    <check>
                        <value1></value1>
                        <value2></value2>
                        <value3/>
                    </check>
                </form>
            </xforms:instance>
            <xforms:instance id="comment-request">
                <comment xmlns="">
                    <date-created/>
                    <name/>
                    <email/>
                    <uri/>
                    <text/>
                </comment>
            </xforms:instance>
            <xforms:instance id="comment-response">
                <comment xmlns="">
                    <date-created/>
                    <name/>
                    <email/>
                    <uri/>
                    <text/>
                </comment>
            </xforms:instance>
            <!-- Handle human check -->
            <xforms:bind nodeset="/form/check/value1" calculate="if (. castable as xs:integer) then . else xs:integer(seconds-from-time(current-time()))"/>
            <xforms:bind nodeset="/form/check/value2" calculate="if (. castable as xs:integer) then . else xs:integer(minutes-from-time(current-time()))"/>
            <xforms:bind nodeset="/form/check/value3" constraint=". castable as xs:positiveInteger and (xs:integer(.) = xs:integer(/form/check/value1) + xs:integer(/form/check/value2))"/>

            <!-- Handle creation date -->
            <xforms:bind nodeset="/form/comment/date-created" calculate="xs:string(adjust-dateTime-to-timezone(current-dateTime(), xdt:dayTimeDuration('PT0H')))"/>

            <!-- Validate comment -->
            <xforms:bind nodeset="/form/comment">
                <xforms:bind nodeset="name" constraint="normalize-space(.) != ''"/>
                <xforms:bind nodeset="text" constraint="normalize-space(instance('comment-response')/text) != ''"/>
            </xforms:bind>

        <!--    <xforms:bind nodeset="/form/comment/email" constraint="normalize-space(.) = '' or matches(., '[A-Za-z0-9!#-''\*\+\-/=\?\^_`\{-~]+(\.[A-Za-z0-9!#-''\*\+\-/=\?\^_`\{-~]+)*@[A-Za-z0-9!#-''\*\+\-/=\?\^_`\{-~]+(\.[A-Za-z0-9!#-''\*\+\-/=\?\^_`\{-~]+)*')"/>-->
<!--            <xforms:bind nodeset="/form/comment/text" constraint="normalize-space(.) != ''"/>-->


            <xforms:submission id="save-comment-submission" method="post" action="/blog/save-comment"/>
            <xforms:submission id="format-comment-submission" method="post" action="/blog/format-comment" ref="instance('comment-request')" replace="instance" instance="comment-response"/>
        </xforms:model>
    </head>
    <body>
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

                    <!-- Display posts -->
                    <xsl:variable name="has-posts" select="exists(/*/posts/day/post)" as="xs:boolean"/>
                    <xsl:variable name="only-post" select="exists(/*/posts/day/post[@only = 'true'])" as="xs:boolean"/>
                    <xsl:choose>
                        <xsl:when test="$has-posts">
                            <!-- Posts are alread grouped by day -->
                            <xsl:for-each select="if ($only-post) then /*/posts/day[post[@only = 'true']] else /*/posts/day">
                                <!-- Display day -->
                                <h2>
                                    <xsl:value-of select="formatted-date"/>
                                </h2>
                                <xsl:for-each select="if ($only-post) then post[@only = 'true'] else post">
                                    <!-- Display post title -->
                                    <a name="{links/fragment-name}"/>
                                    <h3>
                                        <a href="{links/post}">
                                            <img src="/images/text.png" style="border: 0px"/>
                                            <xsl:text> </xsl:text>
                                            <xsl:value-of select="title"/>
                                        </a>
                                    </h3>
                                    <!-- Display post content -->
                                    <div style="margin-left: 2em; border: 1px solid #ccc; padding: 1em; background-color: #eee">
                                        <xsl:copy-of select="description/node()"/>
                                    </div>
                                    <!-- Display post information and links -->
                                    <div style="margin-left: 2em; padding: 1em">
                                        <xsl:text>Filed under:  </xsl:text>
                                        <xsl:for-each select="categories/category">
                                            <xsl:if test="position() > 1">
                                                <xsl:text>, </xsl:text>
                                            </xsl:if>
                                            <a href="{link}">
                                                <xsl:value-of select="name"/>
                                            </a>
                                        </xsl:for-each>
                                        <xsl:text> — </xsl:text>
                                        <xsl:value-of select="/*/user/username"/>
                                        <xsl:text> @ </xsl:text>
                                        <xsl:value-of select="formatted-dateTime-created"/>
                                        <xsl:text> </xsl:text>
                                        <a href="{links/post}">Permalink</a>
                                        <xsl:text> </xsl:text>
                                        <a href="{links/comments}">Comments [<xsl:value-of select="count(comments/comment)"/>]
                                        </a>
                                    </div>

                                    <xsl:if test="$only-post">
                                        <h3>Comments</h3>
                                        <div id="comments">
                                            <xsl:choose>
                                                <xsl:when test="count(comments/comment) > 0">
                                                    <xsl:for-each select="comments/comment">
                                                        <div>
                                                            <!-- Display comment content -->
                                                            <div style="margin-left: 2em; border: 1px solid #ccc; padding: 1em; margin-top: 1em; background-color: #f6f6f6">
                                                                <xsl:value-of select="text"/>
                                                            </div>
                                                            <!-- Display comment information and links -->
                                                            <div style="margin-left: 2em; padding: 1em; padding-top: 0.5em; border-bottom: 1px dotted #ccc">
                                                                Comment by
                                                                <a href="mailto:{email}">
                                                                    <xsl:value-of select="name"/>
                                                                </a>
                                                                <xsl:text>&#160;@&#160;</xsl:text>
                                                                <xsl:value-of select="format-dateTime(date-created, '[MNn] [D], [Y] [H01]:[m01]:[s01] UTC', 'en', (), ())"/>
                                                            </div>
                                                        </div>
                                                    </xsl:for-each>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    No comments yet.
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </div>
                                    </xsl:if>

                                </xsl:for-each>

                            </xsl:for-each>

                            <xsl:if test="$only-post">
                                <xforms:group ref="/form">
                                    <!-- Display comment for preview if needed -->
                                    <xsl:if test="$instance/form/action = 'preview'">
                                        <p>
                                            This is how your comment will look like:
                                        </p>
                                        <!-- Display comment content -->
                                        <div style="margin-left: 2em; border: 1px solid #ccc; padding: 1em; background-color: #ffff9a">
                                            <xsl:choose>
                                                <xsl:when test="normalize-space($instance/form/comment/text) != ''">
                                                    <xsl:value-of select="$instance/form/comment/text"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <i>[Please enter a comment!]</i>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </div>
                                        <!-- Display comment information and links -->
                                        <div style="margin-left: 2em; padding: 1em">
                                            Comment by
                                            <a href="mailto:{$instance/form/comment/email}">
                                                <xsl:value-of select="$instance/form/comment/name"/>
                                            </a>
                                            <xsl:text> @ </xsl:text>
                                            <xsl:value-of select="format-dateTime($instance/form/comment/date-created, '[MNn] [D], [Y] [H01]:[m01]:[s01] UTC', 'en', (), ())"/>
                                        </div>
                                    </xsl:if>
                                    <h2>Post New Comment</h2>
                                    <table>
                                        <tr>
                                            <th style="text-align: right">Name:</th>
                                            <td style="width: 100%">
                                                <xforms:input ref="comment/name" incremental="true">
                                                    <xforms:hint>Please enter your name here.</xforms:hint>
                                                    <xforms:action ev:event="xforms-value-changed" >
                                                        <xforms:setvalue ref="instance('comment-request')/date-created" value="instance('main')/comment/date-created"/>
                                                        <xforms:setvalue ref="instance('comment-request')/name" value="instance('main')/comment/name"/>
                                                        <xforms:setvalue ref="instance('comment-request')/email" value="instance('main')/comment/email"/>
                                                        <xforms:setvalue ref="instance('comment-request')/uri" value="instance('main')/comment/uri"/>
                                                        <xforms:setvalue ref="instance('comment-request')/text" value="instance('main')/comment/text"/>
                                                        <xforms:send submission="format-comment-submission"/>
                                                    </xforms:action>
                                                </xforms:input>
                                            </td>
                                        </tr>
                                        <tr>
                                            <th style="text-align: right">Email:</th>
                                            <td>
                                                <xforms:input ref="comment/email" incremental="true">
                                                    <xforms:alert>Please enter a correct email address!</xforms:alert>
                                                    <xforms:hint>Please enter an optional email address.</xforms:hint>
                                                    <xforms:action ev:event="xforms-value-changed" >
                                                        <xforms:setvalue ref="instance('comment-request')/date-created" value="instance('main')/comment/date-created"/>
                                                        <xforms:setvalue ref="instance('comment-request')/name" value="instance('main')/comment/name"/>
                                                        <xforms:setvalue ref="instance('comment-request')/email" value="instance('main')/comment/email"/>
                                                        <xforms:setvalue ref="instance('comment-request')/uri" value="instance('main')/comment/uri"/>
                                                        <xforms:setvalue ref="instance('comment-request')/text" value="instance('main')/comment/text"/>
                                                        <xforms:send submission="format-comment-submission"/>
                                                    </xforms:action>
                                                </xforms:input>
                                            </td>
                                        </tr>
                                        <tr>
                                            <th style="text-align: right">Web Site:</th>
                                            <td>
                                                <xforms:input ref="comment/uri" incremental="true">
                                                    <xforms:hint>Please enter an optional web site URL.</xforms:hint>
                                                    <xforms:action ev:event="xforms-value-changed" >
                                                        <xforms:setvalue ref="instance('comment-request')/date-created" value="instance('main')/comment/date-created"/>
                                                        <xforms:setvalue ref="instance('comment-request')/name" value="instance('main')/comment/name"/>
                                                        <xforms:setvalue ref="instance('comment-request')/email" value="instance('main')/comment/email"/>
                                                        <xforms:setvalue ref="instance('comment-request')/uri" value="instance('main')/comment/uri"/>
                                                        <xforms:setvalue ref="instance('comment-request')/text" value="instance('main')/comment/text"/>
                                                        <xforms:send submission="format-comment-submission"/>
                                                    </xforms:action>
                                                </xforms:input>
                                            </td>
                                        </tr>
                                        <tr>
                                            <th colspan="2" style="text-align: left">
                                                Please enter your comment below:
                                            </th>
                                        </tr>
                                        <tr>
                                            <td colspan="2">
                                                <!--  xhtml:style="background: #eee; border: 1px solid #ccc" -->
                                                <xforms:textarea ref="comment/text" incremental="true" xhtml:rows="10" xhtml:cols="80">
                                                    <xforms:alert>Please enter a valid comment!</xforms:alert>
                                                    <!-- Send comment to the server for formatting -->
                                                    <xforms:action ev:event="xforms-value-changed" >
                                                        <xforms:setvalue ref="instance('comment-request')/date-created" value="instance('main')/comment/date-created"/>
                                                        <xforms:setvalue ref="instance('comment-request')/name" value="instance('main')/comment/name"/>
                                                        <xforms:setvalue ref="instance('comment-request')/email" value="instance('main')/comment/email"/>
                                                        <xforms:setvalue ref="instance('comment-request')/uri" value="instance('main')/comment/uri"/>
                                                        <xforms:setvalue ref="instance('comment-request')/text" value="instance('main')/comment/text"/>
                                                        <xforms:send submission="format-comment-submission"/>
                                                    </xforms:action>
                                                </xforms:textarea>
                                            </td>
                                        </tr>
                                        <tr>
                                            <td>
                                                <span style="white-space: nowrap">
                                                    <xforms:output ref="check/value1"/>
                                                    <xsl:text> + </xsl:text>
                                                    <xforms:output ref="check/value2"/>
                                                    <xsl:text> = </xsl:text>
                                                </span>
                                            </td>
                                            <td>
                                                <xforms:input ref="check/value3" incremental="true" xhtml:size="3">
                                                    <xforms:alert>Please enter a correct check value!</xforms:alert>
                                                    <xforms:hint>Please enter a check value. This is a measure against comment spam.</xforms:hint>
                                                </xforms:input>
                                            </td>
                                        </tr>
                                    </table>
                                    <xforms:submit>
                                        <xforms:label>Submit</xforms:label>
                                        <xforms:setvalue ref="/form/action">save</xforms:setvalue>
                                    </xforms:submit>
                                    <xforms:submit>
                                        <xforms:label>Cancel</xforms:label>
                                        <xforms:setvalue ref="/form/action">cancel</xforms:setvalue>
                                    </xforms:submit>
                                    <xforms:group ref="instance('comment-response')">
                                        <p>
                                            This is how your comment will look like:
                                        </p>
                                        <!-- Display comment content -->
                                        <div style="margin-left: 2em; border: 1px solid #ccc; padding: 1em; background-color: #ffff9a">
                                            <xforms:output ref="text" appearance="xxforms:html"/>
                                        </div>
                                        <!-- Display comment information and links -->
                                        <div style="margin-left: 2em; padding: 1em">
                                            Comment by
                                            <!--<a href="mailto:{$instance/form/comment/email}">-->
                                            <a>
                                                <xforms:output ref="name"/>
                                            </a>
                                            <xsl:text> @ </xsl:text>
                                            <xforms:output ref="date-created"/>
                                        </div>
                                    </xforms:group>
                                </xforms:group>
                            </xsl:if>

                        </xsl:when>
                        <xsl:otherwise>
                            <p>
                                There are no posts in this blog yet.
                            </p>
                        </xsl:otherwise>
                    </xsl:choose>
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

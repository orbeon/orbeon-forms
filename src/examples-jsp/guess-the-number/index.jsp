<%--
    Copyright (C) 2006 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
--%>
<%@ page import="java.util.Random"%>
<%
    // Set content type to XML. By default it will be HTML, and OPS will tidy it.
    response.setContentType("application/xhtml+xml");
%>
<xhtml:html xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:ev="http://www.w3.org/2001/xml-events">

    <xhtml:head>
        <xhtml:title>Guess The Number</xhtml:title>
        <xforms:model>
            <xforms:instance>
                <number>
                    <answer><%= new Random().nextInt(100) + 1 %></answer>
                    <guess/>
                </number>
            </xforms:instance>
        </xforms:model>
        <xhtml:style type="text/css">
            .paragraph { margin-top: 1em; }
            .feedback { background-color: #ffa; margin-left: 10px; padding: 5px; }
            .guess input { width: 5em; }
            .xforms-alert-inactive { display: none; }
        </xhtml:style>
    </xhtml:head>
    <xhtml:body>
        <xhtml:h1>Guess The Number</xhtml:h1>
        <!--  Ask number -->
        <xhtml:div>
            I picked a number between 1 and 100. Can you guess it?
        </xhtml:div>
        <xhtml:div>
            Good, I like the spirit. Try your best guess:
            <xforms:input ref="guess" class="guess" incremental="true"/>
            <xforms:trigger>
                <xforms:label>Go</xforms:label>
            </xforms:trigger>
            <xforms:group ref="if (guess != '') then . else ()">
                <xhtml:span class="feedback">
                    <xforms:group ref="if (xs:integer(answer) > xs:integer(guess)) then . else ()">
                        <xforms:output value="/number/guess"/>&#160;is a bit too low.</xforms:group>
                    <xforms:group ref="if (xs:integer(guess) > xs:integer(answer)) then . else ()">
                        <xforms:output value="/number/guess"/>&#160;is a tat too high.
                    </xforms:group>
                    <xforms:group ref="if (guess = answer) then . else ()">
                        <xforms:output value="/number/guess"/>
                        &#160;is the right answer. Congratulations!
                    </xforms:group>
                </xhtml:span>
            </xforms:group>
        </xhtml:div>
        <!-- Feedback -->
        <!-- Cheat -->
        <xhtml:div class="paragraph">
            <xforms:trigger>
                <xforms:label>I'm a cheater!</xforms:label>
                <xforms:toggle case="answer-shown" ev:event="DOMActivate"/>
            </xforms:trigger>
            <xforms:switch>
                <xforms:case id="answer-hidden"/>
                <xforms:case id="answer-shown">
                    <xhtml:span class="feedback">
                        Tired already? OK, then. The answer is <xforms:output value="answer"/>.
                    </xhtml:span>
                </xforms:case>
            </xforms:switch>
        </xhtml:div>
    </xhtml:body>
</xhtml:html>

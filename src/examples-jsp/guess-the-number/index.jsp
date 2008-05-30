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
<xhtml:html xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:ev="http://www.w3.org/2001/xml-events"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">

    <xhtml:head>
        <xhtml:title>Guess The Number</xhtml:title>
        <xforms:model>
            <xforms:instance>
                <number>
                    <answer><%= new Random().nextInt(100) + 1 %></answer>
                    <guess/>
                </number>
            </xforms:instance>
            <xforms:bind nodeset="guess" type="xforms:integer"/>
        </xforms:model>
        <xhtml:style type="text/css">
            .paragraph { margin-top: 1em; }
            .feedback { background-color: #ffa; margin-left: 10px; padding: 5px; }
            .guess input { width: 5em; }
            .back { display: block; margin-top: .5em }
        </xhtml:style>
    </xhtml:head>
    <xhtml:body>
        <xhtml:h1>Guess The Number</xhtml:h1>
        <!--  Ask number -->
        <xhtml:div>
            I picked a number between 1 and 100. Can you guess it?
        </xhtml:div>
        <xhtml:div>
            <xhtml:p>
                Good, I like the spirit.
            </xhtml:p>
            <xforms:input ref="guess" class="guess" incremental="true">
                <xforms:label>Try your best guess:</xforms:label>
            </xforms:input>
            <xforms:trigger>
                <xforms:label>Go</xforms:label>
            </xforms:trigger>
            <!-- Feedback -->
            <xforms:group ref="if (guess != '' and guess castable as xs:integer) then . else ()">
                <xhtml:span class="feedback">
                    <xforms:group ref="if (xs:integer(answer) > xs:integer(guess)) then . else ()">
                        <xforms:output value="/number/guess"/>&#160;is a bit too low.</xforms:group>
                    <xforms:group ref="if (xs:integer(guess) > xs:integer(answer)) then . else ()">
                        <xforms:output value="/number/guess"/>&#160;is a tad too high.
                    </xforms:group>
                    <xforms:group ref="if (guess = answer) then . else ()">
                        <xforms:output value="/number/guess"/>
                        &#160;is the right answer. Congratulations!
                    </xforms:group>
                </xhtml:span>
            </xforms:group>
        </xhtml:div>
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
        <xhtml:a class="back" href="/">Back to Orbeon Forms Examples</xhtml:a>
    </xhtml:body>
</xhtml:html>

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
<xh:html xmlns:xf="http://www.w3.org/2002/xforms"
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:ev="http://www.w3.org/2001/xml-events"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">

    <xh:head>
        <xh:title>Guess The Number</xh:title>
        <xf:model>
            <xf:instance>
                <number>
                    <answer><%= new Random().nextInt(100) + 1 %></answer>
                    <guess/>
                </number>
            </xf:instance>
            <xf:bind ref="guess" type="xf:integer"/>
        </xf:model>
        <xh:style type="text/css">
            .paragraph { margin-top: 1em; }
            .feedback { background-color: #ffa; margin-left: 10px; padding: 5px; }
            .guess input { width: 5em; }
            .back { display: block; margin-top: .5em }
        </xh:style>
    </xh:head>
    <xh:body>
        <xh:h1>Guess The Number</xh:h1>
        <!--  Ask number -->
        <xh:div>
            I picked a number between 1 and 100. Can you guess it?
        </xh:div>
        <xh:div>
            <xh:p>
                Good, I like the spirit.
            </xh:p>
            <xf:input ref="guess" class="guess" incremental="true">
                <xf:label>Try your best guess:</xf:label>
            </xf:input>
            <xf:trigger>
                <xf:label>Go</xf:label>
            </xf:trigger>
            <!-- Feedback -->
            <xf:group ref="if (guess != '' and guess castable as xs:integer) then . else ()">
                <xh:span class="feedback">
                    <xf:group ref="if (xs:integer(answer) > xs:integer(guess)) then . else ()">
                        <xf:output value="/number/guess"/>&#160;is a bit too low.</xf:group>
                    <xf:group ref="if (xs:integer(guess) > xs:integer(answer)) then . else ()">
                        <xf:output value="/number/guess"/>&#160;is a tad too high.
                    </xf:group>
                    <xf:group ref="if (guess = answer) then . else ()">
                        <xf:output value="/number/guess"/>
                        &#160;is the right answer. Congratulations!
                    </xf:group>
                </xh:span>
            </xf:group>
        </xh:div>
        <!-- Cheat -->
        <xh:div class="paragraph">
            <xf:trigger>
                <xf:label>I'm a cheater!</xf:label>
                <xf:toggle case="answer-shown" ev:event="DOMActivate"/>
            </xf:trigger>
            <xf:switch>
                <xf:case id="answer-hidden"/>
                <xf:case id="answer-shown">
                    <xh:span class="feedback">
                        Tired already? OK, then. The answer is <xf:output value="answer"/>.
                    </xh:span>
                </xf:case>
            </xf:switch>
        </xh:div>
        <xh:a class="back" href="/">Back to Orbeon Forms Examples</xh:a>
    </xh:body>
</xh:html>

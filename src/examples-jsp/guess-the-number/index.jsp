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
<xh:html
    xmlns:xf="http://www.w3.org/2002/xforms"
    xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:xs="http://www.w3.org/2001/XMLSchema">

    <xh:head>
        <xh:title>Guess the Number</xh:title>
        <xf:model xxf:expose-xpath-types="true">
            <xf:instance>
                <number>
                    <answer><%= new Random().nextInt(100) + 1 %></answer>
                    <guess/>
                </number>
            </xf:instance>
            <xf:bind ref="answer | guess" type="xf:integer"/>
        </xf:model>
        <xh:style type="text/css">
            .row { margin-top: 10px }
        </xh:style>
    </xh:head>
    <xh:body>
        <xh:div class="container">
            <xh:h1>Guess the Number</xh:h1>
            <!--  Ask number -->
            <xh:div class="row">
                <xh:div class="span9">
                    I picked a number between 1 and 100. Can you guess it?
                </xh:div>
            </xh:div>
            <xh:div class="row">
                <xh:div class="span9">
                    <xh:p>
                        Good, I like the spirit.
                    </xh:p>
                    <xf:input ref="guess" incremental="true">
                        <xf:label>Try your best guess:</xf:label>
                        <xf:alert>The value must be an integer</xf:alert>
                    </xf:input>
                    <xf:trigger>
                        <xf:label>Go</xf:label>
                    </xf:trigger>
                    <!-- Feedback -->
                    <xf:group ref=".[string(guess) castable as xs:integer]">
                        <xf:output class="label label-info"    ref=".[answer &gt; guess]" value="concat(guess, ' is a bit too low.')"/>
                        <xf:output class="label label-info"    ref=".[answer &lt; guess]" value="concat(string(guess), ' is a tad too high.')"/>
                        <xf:output class="label label-success" ref=".[answer =    guess]" value="concat(guess, ' is the right answer. Congratulations!')"/>
                    </xf:group>
                </xh:div>
            </xh:div>
            <!-- Cheat -->
            <xh:div class="row">
                <xh:div class="span9">
                    <xf:trigger>
                        <xf:label>I'm a cheater!</xf:label>
                        <xf:toggle case="answer-shown" event="DOMActivate"/>
                    </xf:trigger>
                    <xf:switch>
                        <xf:case id="answer-hidden"/>
                        <xf:case id="answer-shown">
                            <xh:span class="label label-info">
                                Tired already? OK, then. The answer is <xf:output value="answer"/>.
                            </xh:span>
                        </xf:case>
                    </xf:switch>
                </xh:div>
            </xh:div>
            <xh:div class="row">
                <xh:div class="span9">
                    <xh:a class="back" href="/home/xforms">Back to Orbeon Forms Examples</xh:a>
                </xh:div>
            </xh:div>
        </xh:div>
    </xh:body>
</xh:html>

<!--
    Copyright (C) 2004 Orbeon, Inc.
  
    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.
  
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.
  
    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<xsl:transform
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    version="2.0"
    xmlns:tour="http://www.wrox.com/5067/tour"
    exclude-result-prefixes="tour xs">

    <!--
        XSLT stylesheet to perform a knight's tour of the chessboard.
        Author: Michael H. Kay
        Date: 15 November 2002

        This version modified to use XSLT 2.0 and XPath 2.0, with sequences and functions.
        Type declarations added in Nov 2002 version.

        This stylesheet can be run using any XML file as a dummy source document.
        There is an optional parameter, start, which can be set to any square on the
        chessboard, e.g. a3 or h5. By default the tour starts at a1.

        The output is an HTML display of the completed tour.

        Internally, the following data representations are used:
        * A square on the chessboard: represented as a number in the range 0 to 63
        * A state of the chessboard: a sequence of 64 integers, each containing a move number.
          A square that has not been visited yet is represented by a zero.
        * A set of possible moves: represented as a sequence of integers,
        * each integer representing the number of the destination square

    -->

    <xsl:output method="xhtml" indent="yes"/>

    <xsl:param name="start" select="'a1'"/>

    <!-- start-column is an integer in the range 0-7 -->

    <xsl:variable name="start-column" as="xs:integer"
        select="xs:integer(translate(substring($start, 1, 1),
                'abcdefgh', '01234567'))"/>

    <!-- start-row is an integer in the range 0-7, with zero at the top -->

    <xsl:variable name="start-row" as="xs:integer"
        select="8 - xs:integer(substring($start, 2, 1))"/>

    <xsl:template match="/">

        <!-- This template controls the processing. It does not access the source document. -->

        <!-- Validate the input parameter -->

        <xsl:if test="not(string-length($start)=2) or
            not(translate(substring($start,1,1), 'abcdefgh', 'aaaaaaaa')='a') or
            not(translate(substring($start,2,1), '12345678', '11111111')='1')">
            <xsl:message terminate="yes">Invalid start parameter: try say 'a1' or 'g6'</xsl:message>
        </xsl:if>

        <!-- Set up the empty board -->

        <xsl:variable name="empty-board" as="xs:integer*"
            select="for $i in (1 to 64) return 0"/>

        <!-- Place the knight on the board at the chosen starting position -->

        <xsl:variable name="initial-board" as="xs:integer*"
            select="tour:place-knight(1, $empty-board, $start-row * 8 + $start-column)"/>

        <!-- Evaluate the knight's tour -->

        <xsl:variable name="final-board" as="xs:integer*"
            select="tour:make-moves(2, $initial-board, $start-row * 8 + $start-column)"/>

        <!-- produce the HTML output -->

        <xsl:call-template name="print-board">
            <xsl:with-param name="board" select="$final-board"/>
        </xsl:call-template>

    </xsl:template>

    <xsl:function name="tour:place-knight" as="xs:integer*">

        <!-- This function places a knight on the board at a given square. The returned value is
             the supplied board, modified to indicate that the knight reached a given square at a given
             move -->

        <xsl:param name="move" as="xs:integer"/>
        <xsl:param name="board" as="xs:integer*"/>
        <xsl:param name="square" as="xs:integer"/> <!-- integer in range 0..63 -->
        <xsl:sequence select="
            for $i in 1 to 64 return
                if ($i = $square + 1) then $move else $board[$i]"/>

    </xsl:function>

    <xsl:function name="tour:make-moves" as="xs:integer*">

        <!-- This function takes the board in a given state, decides on the next move to make,
             and then calls itself recursively to make further moves, until the knight has completed
             his tour of the board. It returns the board in its final state. -->

        <xsl:param name="move" as="xs:integer"/>
        <xsl:param name="board" as="xs:integer*"/>
        <xsl:param name="square" as="xs:integer"/>

        <!-- determine the possible moves that the knight can make -->

        <xsl:variable name="possible-move-list" as="xs:integer*"
            select="tour:list-possible-moves($board, $square)"/>

        <!-- try these moves in turn until one is found that works -->

        <xsl:sequence
            select="tour:try-possible-moves($move, $board, $square, $possible-move-list)"/>

    </xsl:function>

    <xsl:function name="tour:try-possible-moves" as="xs:integer*">

        <!-- This template tries a set of possible moves that the knight can make
             from a given position. It determines the best move as the one to the square with
             fewest exits. If this is unsuccessful then in principle it can backtrack and
             try another move; however this turns out never to be necessary.

             The function makes the selected move, and then calls make-moves() to make
             subsequent moves, returning the final state of the board. -->

        <xsl:param name="move" as="xs:integer"/>
        <xsl:param name="board" as="xs:integer*"/>
        <xsl:param name="square" as="xs:integer"/>
        <xsl:param name="possible-moves" as="xs:integer*"/>

        <xsl:sequence
            select="if (count($possible-moves)!=0)
                    then tour:make-best-move($move, $board, $square, $possible-moves)
                    else ()"/>

        <!-- if there is no possible move, we return the special value () as the final state
             of the board, to indicate to the caller that we got stuck -->
    </xsl:function>

    <xsl:function name="tour:make-best-move" as="xs:integer*">
        <xsl:param name="move" as="xs:integer"/>
        <xsl:param name="board" as="xs:integer*"/>
        <xsl:param name="square" as="xs:integer"/>
        <xsl:param name="possible-moves" as="xs:integer*"/>
        <!-- if at least one move is possible, find the best one -->

        <xsl:variable name="best-move"
            select="tour:find-best-move($board, $possible-moves, 9, 999)"/>

        <!-- find the list of possible moves excluding the best one -->

        <xsl:variable name="other-possible-moves" as="xs:integer*"
            select="$possible-moves[. != $best-move]"/>

        <!-- update the board to make the move chosen as the best one -->

        <xsl:variable name="next-board" as="xs:integer*"
            select="tour:place-knight($move, $board, $best-move)"/>

        <!-- now make further moves using a recursive call, until the board is complete -->

        <xsl:variable name="final-board" as="xs:integer*"
            select="if (count($next-board[.=0])!=0)
                        then tour:make-moves($move+1, $next-board, $best-move)
                        else $next-board"/>

        <!-- if the final board has the special value '()', we got stuck, and have to choose
             the next best of the possible moves. This is done by a recursive call. In practice,
             we never do get stuck, so this path is not taken. -->

        <xsl:sequence select="
            if (empty($final-board))
            then tour:try-possible-moves($board, $square, $move, $other-possible-moves)
            else $final-board"/>

    </xsl:function>

    <xsl:function name="tour:find-best-move" as="xs:integer*">

        <!-- This function finds from among the possible moves, the one with fewest exits.
             It calls itself recursively. -->

        <xsl:param name="board" as="xs:integer*"/>
        <xsl:param name="possible-moves" as="xs:integer*"/>
        <xsl:param name="fewest-exits" as="xs:integer"/>
        <xsl:param name="best-so-far" as="xs:integer"/>

        <!-- split the list of possible moves into the first move and the rest of the moves -->

        <xsl:variable name="trial-move" as="xs:integer"
            select="$possible-moves[1]"/>
        <xsl:variable name="other-possible-moves" as="xs:integer*"
            select="$possible-moves[position() &gt; 1]"/>

        <!-- try making the first move -->

        <xsl:variable name="trial-board" as="xs:integer*"
            select="tour:place-knight(99, $board, $trial-move)"/>

        <!-- see how many moves would be possible the next time -->

        <xsl:variable name="trial-move-exit-list" as="xs:integer*"
            select="tour:list-possible-moves($trial-board, $trial-move)"/>

        <xsl:variable name="number-of-exits" as="xs:integer"
            select="count($trial-move-exit-list)"/>

        <!-- determine whether this trial move has fewer exits than those considered up till now -->

        <xsl:variable name="minimum-exits" as="xs:integer"
            select="min(($number-of-exits, $fewest-exits))"/>

        <!-- determine which is the best move (the one with fewest exits) so far -->

        <xsl:variable name="new-best-so-far" as="xs:integer"
            select="if ($number-of-exits &lt; $fewest-exits)
                    then $trial-move
                    else $best-so-far"/>

        <!-- if there are other possible moves, consider them too, using a recursive call.
             Otherwise return the best move found. -->

        <xsl:sequence
            select="if (count($other-possible-moves)!=0)
                    then tour:find-best-move($board, $other-possible-moves,
                                                $minimum-exits, $new-best-so-far)
                    else $new-best-so-far"/>

    </xsl:function>

    <xsl:function name="tour:list-possible-moves" as="xs:integer*">

        <!-- This function, given the knight's position on the board, returns the set of squares
             he can move to. The squares will be ones that have not been visited before -->

        <xsl:param name="board" as="xs:integer*"/>
        <xsl:param name="square" as="xs:integer"/>

        <xsl:variable name="row" as="xs:integer"
            select="$square idiv 8"/>
        <xsl:variable name="column" as="xs:integer"
            select="$square mod 8"/>

        <xsl:sequence select="
            (if ($row &gt; 1 and $column &gt; 0 and $board[($square - 17) + 1]=0)
                then $square - 17 else (),
             if ($row &gt; 1 and $column &lt; 7 and $board[($square - 15) + 1]=0)
                then $square - 15 else (),
             if ($row &gt; 0 and $column &gt; 1 and $board[($square - 10) + 1]=0)
                then $square - 10 else (),
             if ($row &gt; 0 and $column &lt; 6 and $board[($square - 6) + 1]=0)
                then $square - 6 else (),
             if ($row &lt; 6 and $column &gt; 0 and $board[($square + 15) + 1]=0)
                then $square + 15 else (),
             if ($row &lt; 6 and $column &lt; 7 and $board[($square + 17) + 1]=0)
                then $square + 17 else (),
             if ($row &lt; 7 and $column &gt; 1 and $board[($square + 6) + 1]=0)
                then $square + 6 else (),
             if ($row &lt; 7 and $column &lt; 6 and $board[($square + 10) + 1]=0)
                then $square + 10 else () )"
            />

    </xsl:function>

    <xsl:template name="print-board">

        <!-- Output the board in HTML format -->

        <xsl:param name="board" as="xs:integer*"/>

        <html>
            <head>
                <title>Knight's tour</title>
            </head>
            <body>
                <div align="center">
                    <h1>Knight's tour starting at
                        <xsl:value-of select="$start"/>
                    </h1>
                    <table border="1" cellpadding="4" size="{count($board)}">
                        <xsl:for-each select="0 to 7">
                            <xsl:variable name="row" select="."/>
                            <tr>
                                <xsl:for-each select="0 to 7">
                                    <xsl:variable name="column" select="."/>
                                    <xsl:variable name="color"
                                        select="if ((($row + $column) mod 2)=1)
                                                then 'xffff44' else 'white'"/>
                                    <td align="center" bgcolor="{$color}">
                                        <xsl:value-of select="$board[$row * 8 + $column + 1]"/>
                                    </td>
                                </xsl:for-each>
                            </tr>
                        </xsl:for-each>
                    </table>
                </div>
            </body>
        </html>
    </xsl:template>


</xsl:transform>


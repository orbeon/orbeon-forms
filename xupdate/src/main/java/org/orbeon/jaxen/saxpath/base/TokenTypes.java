/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/saxpath/base/TokenTypes.java,v 1.12 2006/02/05 21:47:42 elharo Exp $
 * $Revision: 1.12 $
 * $Date: 2006/02/05 21:47:42 $
 *
 * ====================================================================
 *
 * Copyright 2000-2004 bob mcwhirter & James Strachan.
 * All rights reserved.
 *
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   * Neither the name of the Jaxen Project nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ====================================================================
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Jaxen Project and was originally
 * created by bob mcwhirter <bob@werken.com> and
 * James Strachan <jstrachan@apache.org>.  For more information on the
 * Jaxen Project, please see <http://www.jaxen.org/>.
 *
 * $Id: TokenTypes.java,v 1.12 2006/02/05 21:47:42 elharo Exp $
 */

package org.orbeon.jaxen.saxpath.base;


class TokenTypes
{
    static final int EOF   = -1;
    static final int SKIP  = -2;
    static final int ERROR = -3;

    static final int EQUALS = 1;
    static final int NOT_EQUALS = 2;

    static final int LESS_THAN_SIGN = 3;
    static final int LESS_THAN_OR_EQUALS_SIGN = 4;
    static final int GREATER_THAN_SIGN = 5;
    static final int GREATER_THAN_OR_EQUALS_SIGN = 6;

    static final int PLUS  = 7;
    static final int MINUS = 8;
    static final int STAR  = 9;
    static final int MOD   = 10;
    static final int DIV   = 11;

    static final int SLASH = 12;
    static final int DOUBLE_SLASH = 13;
    static final int DOT = 14;
    static final int DOT_DOT = 15;

    static final int IDENTIFIER = 16;

    static final int AT = 17;
    static final int PIPE = 18;
    static final int COLON = 19;
    static final int DOUBLE_COLON = 20;

    static final int LEFT_BRACKET = 21;
    static final int RIGHT_BRACKET = 22;
    static final int LEFT_PAREN = 23;
    static final int RIGHT_PAREN = 24;

    // 25 was NOT but there is no such token in XPath
    static final int DOLLAR = 25;
    static final int LITERAL = 26;
    static final int AND = 27;
    static final int OR = 28;

    // No need for an integer token type. All numbers
    // in XPath are doubles.
    static final int DOUBLE = 29;
    static final int COMMA = 30;

    static String getTokenText( int tokenType )
    {
        switch( tokenType )
        {
            case ERROR:
                return "(error)";
            case SKIP:
                return "(skip)";
            case EOF:
                return "(eof)";
            case 0:
                return "Unrecognized token type: 0";
            case EQUALS:
                return "=";
            case NOT_EQUALS:
                return "!=";
            case LESS_THAN_SIGN:
                return "<";
            case LESS_THAN_OR_EQUALS_SIGN:
                return "<=";
            case GREATER_THAN_SIGN:
                return ">";
            case GREATER_THAN_OR_EQUALS_SIGN:
                return ">=";
            case PLUS:
                return "+";
            case MINUS:
                return "-";
            case STAR:
                return "*";
            case DIV:
                return "div";
            case MOD:
                return "mod";
            case SLASH:
                return "/";
            case DOUBLE_SLASH:
                return "//";
            case DOT:
                return ".";
            case DOT_DOT:
                return "..";
            case IDENTIFIER:
                return "(identifier)";
            case AT:
                return "@";
            case PIPE:
                return "|";
            case COLON:
                return ":";
            case DOUBLE_COLON:
                return "::";
            case LEFT_BRACKET:
                return "[";
            case RIGHT_BRACKET:
                return "]";
            case LEFT_PAREN:
                return "(";
            case RIGHT_PAREN:
                return ")";
            case DOLLAR:
                return "$";
            case LITERAL:
                return "(literal)";
            case AND:
                return "and";
            case OR:
                return "or";
            case DOUBLE:
                return "(double)";
            case COMMA:
                return ",";
            default:
                // This method is only called from an error handler, and only
                // to provide an exception message. In other words, the string
                // returned by this method is only used in an exception message.
                // Something has already gone wrong, and is being reported.
                // Thus there's no real reason to throw another exception here.
                // Just return a string and this message will be reported in an
                // exception anyway.
                return("Unrecognized token type: " + tokenType);
        }
    }
}

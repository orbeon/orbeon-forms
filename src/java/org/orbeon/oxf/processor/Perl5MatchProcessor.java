/**
 *  Copyright (C) 2004 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor;

import org.orbeon.oro.text.regex.Perl5Compiler;
import org.orbeon.oro.text.regex.Perl5Matcher;

public class Perl5MatchProcessor extends MatchProcessor {

    private static Perl5Compiler compiler = new Perl5Compiler();
    private static Perl5Matcher matcher = new Perl5Matcher();

    public Result match(String regexp, String text) {
        return oroMatch(regexp, text, compiler, matcher);
    }
}

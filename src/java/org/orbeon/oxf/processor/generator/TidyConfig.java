/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor.generator;

import org.dom4j.Node;
import org.orbeon.oxf.processor.ProcessorUtils;

public class TidyConfig {

    private static String DEFAULT_HTML_ENCODING = "iso-8859-1";

    private static boolean DEFAULT_SHOW_WARNINGS = false;
    private static boolean DEFAULT_QUIET = true;
    private static boolean DEFAULT_FIX_BACKSLASH = false;
    private static boolean DEFAULT_UPPERCASE_ATTRS = false;
    private static boolean DEFAULT_UPPERCASE_TAGS = false;
    private static boolean DEFAULT_WORD_2000 = false;

    private boolean showWarnings = DEFAULT_SHOW_WARNINGS;
    private boolean quiet = DEFAULT_QUIET;
    private boolean fixBackslash = DEFAULT_FIX_BACKSLASH;
    private boolean uppercaseAttrs = DEFAULT_UPPERCASE_ATTRS;
    private boolean uppercaseTags = DEFAULT_UPPERCASE_TAGS;
    private boolean word2000 = DEFAULT_WORD_2000;

    public TidyConfig(Node tidyNode) {
        if (tidyNode != null) {
            this.showWarnings = ProcessorUtils.selectBooleanValue(tidyNode, "show-warnings", DEFAULT_SHOW_WARNINGS);
            this.quiet = ProcessorUtils.selectBooleanValue(tidyNode, "quiet", DEFAULT_QUIET);
            this.fixBackslash = ProcessorUtils.selectBooleanValue(tidyNode, "fix-backslash", DEFAULT_FIX_BACKSLASH);
            this.uppercaseAttrs = ProcessorUtils.selectBooleanValue(tidyNode, "uppercase-attrs", DEFAULT_UPPERCASE_ATTRS);
            this.uppercaseTags = ProcessorUtils.selectBooleanValue(tidyNode, "uppercase-tags", DEFAULT_UPPERCASE_TAGS);
            this.word2000 = ProcessorUtils.selectBooleanValue(tidyNode, "word2000", DEFAULT_WORD_2000);
        }
    }

    public boolean isFixBackslash() {
        return fixBackslash;
    }

    public boolean isQuiet() {
        return quiet;
    }

    public boolean isShowWarnings() {
        return showWarnings;
    }

    public boolean isUppercaseAttrs() {
        return uppercaseAttrs;
    }

    public boolean isUppercaseTags() {
        return uppercaseTags;
    }

    public boolean isWord2000() {
        return word2000;
    }

    public String toString() {
        return "[" + isFixBackslash() + "|" + isQuiet() + "|" + isShowWarnings()
                + "|" + isUppercaseAttrs() + "|" + isUppercaseTags() + "|" + isWord2000() + "]";
    }

    public static String getTidyEncoding(String encoding) {
        return (encoding == null) ? encoding = DEFAULT_HTML_ENCODING : encoding;
    }
}

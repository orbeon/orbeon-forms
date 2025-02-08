/*
 * Copyright 2000-2005 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.orbeon.oxf.util;

/**
 * Utility class to rewrite URLs.
 */
public class ApacheGlobSupport {
    
    public static String globToRegexp(char[] pattern) {
        int ch;
        StringBuffer buffer;

        buffer = new StringBuffer(2 * pattern.length);
        boolean inCharSet = false;

        boolean questionMatchesZero = false;
        boolean starCannotMatchNull = false;

        for (ch = 0; ch < pattern.length; ch++) {
            switch (pattern[ch]) {
                case '*':
                    if (inCharSet)
                        buffer.append('*');
                    else {
                        if (starCannotMatchNull)
                            buffer.append(".+");
                        else
                            buffer.append(".*");
                    }
                    break;
                case '?':
                    if (inCharSet)
                        buffer.append('?');
                    else {
                        if (questionMatchesZero)
                            buffer.append(".?");
                        else
                            buffer.append('.');
                    }
                    break;
                case '[':
                    inCharSet = true;
                    buffer.append(pattern[ch]);

                    if (ch + 1 < pattern.length) {
                        switch (pattern[ch + 1]) {
                            case '!':
                            case '^':
                                buffer.append('^');
                                ++ch;
                                continue;
                            case ']':
                                buffer.append(']');
                                ++ch;
                                continue;
                        }
                    }
                    break;
                case ']':
                    inCharSet = false;
                    buffer.append(pattern[ch]);
                    break;
                case '\\':
                    buffer.append('\\');
                    if (ch == pattern.length - 1) {
                        buffer.append('\\');
                    } else if (__isGlobMetaCharacter(pattern[ch + 1]))
                        buffer.append(pattern[++ch]);
                    else
                        buffer.append('\\');
                    break;
                default:
                    if (!inCharSet && __isPerl5MetaCharacter(pattern[ch]))
                        buffer.append('\\');
                    buffer.append(pattern[ch]);
                    break;
            }
        }

        return buffer.toString();
    }

    private static boolean __isPerl5MetaCharacter(char ch) {
        return ("'*?+[]()|^$.{}\\".indexOf(ch) >= 0);
    }

    private static boolean __isGlobMetaCharacter(char ch) {
        return ("*?[]".indexOf(ch) >= 0);
    }
}
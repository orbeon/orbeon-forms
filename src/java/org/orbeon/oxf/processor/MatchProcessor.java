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
package org.orbeon.oxf.processor;

import org.dom4j.Document;
import org.orbeon.oro.text.regex.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.impl.CacheableTransformerOutputImpl;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.List;

public abstract class MatchProcessor extends ProcessorImpl {

    public MatchProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new CacheableTransformerOutputImpl(MatchProcessor.this, name) {
            public void readImpl(PipelineContext pipelineContext, XMLReceiver xmlReceiver) {

                try {
                    // Read inputs
                    final Document data = readInputAsDOM4J(pipelineContext, INPUT_DATA);
                    final Document config = readInputAsDOM4J(pipelineContext, INPUT_CONFIG);

                    // Compute result
                    final String text = (String) data.selectObject("string(*)");
                    final String regexp = (String) config.selectObject("string(*)");
                    final Result result = match(regexp, text);

                    // Output result
                    xmlReceiver.startDocument();
                    xmlReceiver.startElement("", "result", "result", XMLUtils.EMPTY_ATTRIBUTES);

                    // <matches>
                    xmlReceiver.startElement("", "matches", "matches", XMLUtils.EMPTY_ATTRIBUTES);
                    final String matches = Boolean.toString(result.matches);
                    xmlReceiver.characters(matches.toCharArray(), 0, matches.length());
                    xmlReceiver.endElement("", "matches", "matches");

                    // <group>
                    for (final String group: result.groups) {
                        xmlReceiver.startElement("", "group", "group", XMLUtils.EMPTY_ATTRIBUTES);
                        if (group != null)
                            xmlReceiver.characters(group.toCharArray(), 0, group.length());
                        xmlReceiver.endElement("", "group", "group");
                    }

                    xmlReceiver.endElement("", "result", "result");
                    xmlReceiver.endDocument();
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }

    public static class Result {
        public boolean matches;
        public List<String> groups = new ArrayList<String>();
    }

    /**
     * The only method that subclasses need to implement.
     */
    public abstract Result match(String regexp, String text);

    /**
     * Utility class that can be used to implement match() by subclasses using the ORO library.
     */
    protected static Result oroMatch(String regexp, String text, PatternCompiler compiler, PatternMatcher matcher) {
        try {
            // NOTE: It looks like the compiler is not thread safe
            final Pattern pattern;
            synchronized (compiler) {
                pattern = compiler.compile(regexp, Perl5Compiler.READ_ONLY_MASK);
            }
            final Result result = new Result();
            synchronized (matcher) {
                result.matches = matcher.matches(text, pattern);
                if (result.matches) {
                    final MatchResult matchResult = matcher.getMatch();
                    final int groupCount = matchResult.groups();
                    for (int i = 1; i < groupCount; i++)
                        result.groups.add(matchResult.group(i));
                }
            }
            return result;
        } catch (MalformedPatternException e) {
            throw new OXFException(e);
        }
    }
}

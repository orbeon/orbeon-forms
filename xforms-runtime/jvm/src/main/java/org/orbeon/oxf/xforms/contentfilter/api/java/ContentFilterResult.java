package org.orbeon.oxf.xforms.contentfilter.api.java;

import java.util.Collections;
import java.util.List;


public abstract class ContentFilterResult {

    public final String message;

    protected ContentFilterResult(String message) {
        this.message = message;
    }

    public static class Match {

        public final int start;
        public final int end;
        public final String matchedWord;

        public Match(int start, int end, String matchedWord) {
            this.start = start;
            this.end = end;
            this.matchedWord = matchedWord;
        }
    }

    public static class AcceptResult extends ContentFilterResult {

        public AcceptResult() {
            this(null);
        }

        public AcceptResult(String message) {
            super(message);
        }
    }

    public static class RejectResult extends ContentFilterResult {

        public final List<Match> matches;

        public RejectResult(String message, List<Match> matches) {
            super(message);
            this.matches = matches != null ? Collections.unmodifiableList(matches) : Collections.emptyList();
        }
    }
}

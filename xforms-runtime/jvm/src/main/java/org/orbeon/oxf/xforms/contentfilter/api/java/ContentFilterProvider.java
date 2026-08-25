package org.orbeon.oxf.xforms.contentfilter.api.java;

import java.util.Map;

public interface ContentFilterProvider {

    void init();

    void destroy();

    ContentFilterResult filterContent(
        String              text,
        String              language,
        Map<String, Object> extension
    );
}

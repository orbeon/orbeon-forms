(function() {

    var $ = ORBEON.jQuery;
    var Events = ORBEON.xforms.Events;

    if (typeof window.getComputedStyle !== 'function') {
        // We are on IE8: NOP
        // autosize.js doesn't support IE8, so use the same test to do nothing in that case
    } else {
        // Exclude non-visible item to avoid repeat templates, which if initialized, won't be initialized again
        // when copied (and also, potentially, for performance)
        var AutosizeSelector = '.xforms-textarea-appearance-xxforms-autosize textarea:visible';
        var AutosizeEvent = (function () {
            var ev = document.createEvent('Event');
            ev.initEvent('autosize:update', true, false);
            return ev;
        })();

        $(window).on('load', function() {
            autosize($(AutosizeSelector));
        });

        Events.ajaxResponseProcessedEvent.subscribe(function() {
            _.each($(AutosizeSelector).toArray(), function(ta) {
                // Initialize potentially new text areas added to the page
                autosize(ta);
                // Update size of text areas, whose content or style might have changed
                ta.dispatchEvent(AutosizeEvent);
            });
        });
    }

})();

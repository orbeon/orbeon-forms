(function() {

    var $ = ORBEON.jQuery;
    var Events = ORBEON.xforms.Events;

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
            console.log(ta);
            // Initialize potentially new textareas added to the page
            autosize(ta);
            // Update size of textareas, whose content or style might have changed
            ta.dispatchEvent(AutosizeEvent);
        });
    });

})();

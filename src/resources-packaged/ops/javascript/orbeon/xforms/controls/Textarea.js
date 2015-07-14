(function() {

    var $ = ORBEON.jQuery;
    var Events = ORBEON.xforms.Events;

    var AutosizeSelector = '.xforms-textarea-appearance-xxforms-autosize textarea';

    $(window).on('load', function() {
        $(AutosizeSelector).autosize();
    });

    Events.ajaxResponseProcessedEvent.subscribe(function() {
        var tas = $(AutosizeSelector);
        tas.autosize();
        tas.trigger('resize');
    });

})();

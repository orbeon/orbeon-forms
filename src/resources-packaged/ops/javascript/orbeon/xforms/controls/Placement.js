(function() {

    var $ = ORBEON.jQuery;
    var Controls = ORBEON.xforms.Controls;

    Controls.getPosition = getPosition;
    Controls.getPlacement = getPlacement;

    /**
     * For the element, returns an object with the following properties:
     *      width, height, scrollTop                // Equivalent to jQuery functions
     *      offset: { top, left }                   // Position relative to the document
     *      margins: { top, right, bottom, left }   // In in a scrollable area (e.g. FB), space around that area
     */
    function getPosition(el) {

        function getElPosition() {
            return {
                offset:    el.offset(),
                width :    el.outerWidth(),
                height:    el.outerHeight(),
                scrollTop: $(document).scrollTop() // Will this work if we're in a scrollable area?
            }
        }

        var pos;
        if (el.is(':hidden')) {
            var originalStyle = el.attr('style');
            el.css('display', 'inline-block');
            pos = getElPosition();
            (_.isUndefined(originalStyle))
                ? el.removeAttr('style')
                : el.attr('style', originalStyle);
        } else {
            pos = getElPosition();
        }

        var overflowEl = $(_.find($(el).parents(), function(e) { return $(e).css('overflow') == 'auto'; }));
        var overflowPos = {};
        pos.margins = ! overflowEl.is('*')
            ? { top: 0, right: 0, bottom: 0, left: 0 }
            : (
                overflowPos.offset = overflowEl.offset(),
                overflowPos.width  = overflowEl.outerWidth(),
                overflowPos.height = overflowEl.outerHeight(),
                {
                    top:    overflowPos.offset.top,
                    right:  $(window).width()  - overflowPos.offset.left - overflowPos.width,
                    bottom: $(window).height() - overflowPos.offset.top  - overflowPos.height,
                    left:   overflowPos.offset.left
                }
              );

        return pos;
    }

    /**
     * Figure where we want to place the popover: right, left, top, bottom, or over
     */
    function getPlacement(elPos) {
        var RequiredSpaceHorizontal = 420;
        var RequiredSpaceVertical   = 300;
        var space = {
            left:   elPos.offset.left,
            right:  $(window).width() - (elPos.offset.left + elPos.width),
            top:    elPos.offset.top - elPos.scrollTop,
            bottom: $(window).height() - (elPos.offset.top - elPos.scrollTop + elPos.height)
        };
        return (space.right >= RequiredSpaceHorizontal || space.left >= RequiredSpaceHorizontal)
             ? // If space to the left and right are the same (e.g. title with wide page), display to the left, which
               // will be closer to the text of the title
               space.right > space.left ? 'right' : 'left'
             : (space.top   >= RequiredSpaceVertical || space.bottom >= RequiredSpaceVertical)
             ?  space.top   >= space.bottom ? 'top' : 'bottom'
             : 'over';
    }

})();
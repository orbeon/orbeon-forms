(function() {
  var $, Controls, findInputOrTextarea;

  $ = ORBEON.jQuery;

  Controls = ORBEON.xforms.Controls;

  findInputOrTextarea = function(control) {
    var input;
    input = (control.getElementsByTagName("input"))[0];
    if (input != null) {
      return input;
    } else {
      return (control.getElementsByTagName("textarea"))[0];
    }
  };

  (function() {
    return Controls.lhhaChangeEvent.subscribe(function(event) {
      var inputOrTextarea, labelHint;
      if ((event.type === 'label' || event.type === 'hint') && $(event.control).is('.xforms-input, .xforms-textarea')) {
        labelHint = Controls.getControlLHHA(event.control, event.type);
        if (labelHint == null) {
          inputOrTextarea = findInputOrTextarea(event.control);
          if (inputOrTextarea != null) {
            return $(inputOrTextarea).attr('placeholder', event.message);
          }
        }
      }
    });
  })();

}).call(this);

(function() {
  var $, AjaxServer, Controls;

  $ = ORBEON.jQuery;

  AjaxServer = ORBEON.xforms.server.AjaxServer;

  Controls = ORBEON.xforms.Controls;

  AjaxServer.eventCreated = $.Callbacks();

  AjaxServer.Event = function(args) {
    var bool, i, name, newArgs, object, oldParams, setDefault, string, supportedArgs, type, _i, _len, _ref;
    supportedArgs = function() {
      return {
        form: object(function() {
          if (_.isString(args.targetId)) {
            return Controls.getForm(document.getElementById(args.targetId));
          } else {
            return null;
          }
        }),
        targetId: string(function() {
          return null;
        }),
        value: string(function() {
          return null;
        }),
        eventName: string(function() {
          return null;
        }),
        bubbles: bool(function() {
          return null;
        }),
        cancelable: bool(function() {
          return null;
        }),
        ignoreErrors: bool(function() {
          return null;
        }),
        showProgress: bool(function() {
          return true;
        }),
        properties: object(function() {
          return {};
        })
      };
    };
    if (arguments.length > 1) {
      oldParams = ['form', 'targetId', 'value', 'eventName', 'bubbles', 'cancelable', 'ignoreErrors', 'showProgress'];
      newArgs = {};
      for (i = _i = 0, _len = oldParams.length; _i < _len; i = ++_i) {
        name = oldParams[i];
        newArgs[name] = arguments[i];
      }
      args = newArgs;
    }
    type = (function(_this) {
      return function(isType) {
        return function(alternative) {
          return function(name) {
            return _this[name] = isType(args[name]) ? args[name] : alternative();
          };
        };
      };
    })(this);
    object = type(_.isObject);
    string = type(_.isString);
    bool = type(_.isBoolean);
    _ref = supportedArgs();
    for (name in _ref) {
      setDefault = _ref[name];
      setDefault(name);
    }
    AjaxServer.eventCreated.fire(this);
  };

}).call(this);

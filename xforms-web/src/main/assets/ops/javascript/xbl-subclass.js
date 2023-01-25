// Hopefully temporary, see:
// https://github.com/orbeon/orbeon-forms/issues/5635
(function() {

    $ = ORBEON.jQuery;

    // This creates a subclass of the class passed. This provides us with a clean way to override `init()` and
    // `destroy()`, as well as to initialize the `container` member of the class. Before we had the proper
    // `javascript-lifecycle` mode, `init()` used to be called by the implementor of the component. It could
    // be called multiple times for a given instance, and by overriding `init()` we were able to ensure that the
    // underlying `init()` was only called once per instance. This is probably not needed anymore, but we
    // keep it for backward compatibility. In addition:
    //
    // - fire internal `componentInitialized` for full updates (for backward compatibility)
    // - remove the instance reference upon `destroy()`
    // - SINCE 2022.1.1: pass `containerElem` to the parent class, as this makes that value accessible in the class
    //   constructor, and is better than setting the property magically; but for backward compatibility the
    //   `container` property is preserved
    ORBEON.xforms.XBL.createSubclass = function(superclass) {

        const privateInitCalledSymbol = Symbol("initCalled");
        class ComponentSubclass extends superclass {
            constructor(containerElem) {
                super(containerElem);
                Object.defineProperty(this, "container", {
                    "value": containerElem,
                    "writable": false,
                    "enumerable": true,
                    "configurable": true
                });
                this[privateInitCalledSymbol] = false;
                if (! this.container)
                    this.container = containerElem;
            }

            init() {
                if (! this[privateInitCalledSymbol] && super.init) {
                    this[privateInitCalledSymbol] = true;
                    super.init();
                    if (! ORBEON.xforms.XBL.isJavaScriptLifecycle(this.container))
                        ORBEON.xforms.XBL.componentInitialized.fire({
                            container: this.container,
                            constructor: ComponentSubclass
                        });
                }
            }
            destroy() {
                if (super.destroy)
                    super.destroy();
                // We can debate whether the following clean-up should happen here or next to the caller of `destroy()`.
                // However, legacy users might call `destroy()` manually, in which case it's better to clean-up here.
                $(this.container).removeData("xforms-xbl-object");
            }
        }
        return ComponentSubclass
    }
}).call(this);

import React from 'react';
import ReactDOM from 'react-dom';
import FrForm from '@orbeon/react';

(function(window: any) {
  window.initializeReact = function() {
    const orbeonConfig = (window as any).orbeonReactConfig;

    if (orbeonConfig) {
      ReactDOM.render(
        <FrForm
          app={orbeonConfig.app}
          form={orbeonConfig.form}
          mode={orbeonConfig.mode}
          orbeonContext={orbeonConfig.orbeonContext}
        />,
        document.getElementById('react-root')
      );
    }
  };
})(window);

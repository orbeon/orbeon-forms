import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';

(function(window: any) {
  window.initializeAngular = function() {

    bootstrapApplication(AppComponent, {
      providers: []
    }).catch(err => {
      console.error('Bootstrap error:', err);
    });
  };
})(window);

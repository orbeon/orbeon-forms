import { Component } from '@angular/core';
import { FrFormComponent } from '@orbeon/angular';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [ FrFormComponent ],
  template: `<fr-form app="{{app}}" form="{{form}}" mode="{{mode}}" orbeonContext="{{orbeonContext}}"></fr-form>`
})

export class AppComponent {
  title: string = 'Orbeon Angular component test';

  app          : string = 'orbeon';
  form         : string = 'dmv-14';
  mode         : string = 'new';
  orbeonContext: string = '/orbeon';

  ngOnInit() {
    const orbeonConfig = (window as any).orbeonAngularConfig;

    if (orbeonConfig) {
      this.app           = orbeonConfig.app;
      this.form          = orbeonConfig.form;
      this.mode          = orbeonConfig.mode;
      this.orbeonContext = orbeonConfig.orbeonContext;
    }
  }
}

import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';
import { environment } from './environments/environment';
import { enableProdMode } from '@angular/core';

// Some CommonJS browser libs expect Node-style globals.
const g = globalThis as any;
if (typeof g.global === 'undefined') {
  g.global = g;
}
if (typeof g.process === 'undefined') {
  g.process = { env: {} };
}

// Suppress development mode message
if (environment.production) {
  enableProdMode();
} else {
  // Suppress Angular development mode console message
  try {
    const w = window as any;
    if (w && w.ng) {
      w.ng.probe = undefined;
      w.ng.corePackageToken = undefined;
    }
  } catch (e) {
    // ignore
  }
}

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));

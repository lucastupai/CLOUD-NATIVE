import { ApplicationConfig, APP_INITIALIZER } from '@angular/core';
import { provideHttpClient, withInterceptorsFromDi, HTTP_INTERCEPTORS } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { InteractionType, PublicClientApplication } from '@azure/msal-browser';
import {
  MSAL_GUARD_CONFIG,
  MSAL_INSTANCE,
  MSAL_INTERCEPTOR_CONFIG,
  MsalBroadcastService,
  MsalGuard,
  MsalGuardConfiguration,
  MsalInterceptor,
  MsalInterceptorConfiguration,
  MsalService
} from '@azure/msal-angular';
import { routes } from './app.routes';
import { environment } from '../environments/environment';

export function MSALInstanceFactory(): PublicClientApplication {
  return new PublicClientApplication({
    auth: {
      clientId: environment.entra.clientId,
      authority: `https://login.microsoftonline.com/${environment.entra.tenantId}`,
      redirectUri: environment.entra.redirectUri,
      postLogoutRedirectUri: environment.entra.postLogoutRedirectUri
    },
    cache: {
      cacheLocation: 'localStorage'
    }
  });
}

// Inicializador requerido para MSAL v3+
export function initializeMsal(msalInstance: PublicClientApplication) {
  return () => msalInstance.initialize();
}

export function msalGuardConfigFactory(): MsalGuardConfiguration {
  return {
    interactionType: InteractionType.Popup,
    authRequest: { scopes: [environment.apiScope] }
  };
}

// En app.config.ts o msal.config.ts
export function MSALInterceptorConfigFactory(): MsalInterceptorConfiguration {
  const protectedResourceMap = new Map<string, Array<string>>();
  
  // Asocia la URL del BFF con el Scope expuesto en Azure Portal
  const targetScope = environment.apiScope;
  protectedResourceMap.set(`${environment.apiBaseUrl}/api/*`, [targetScope]);
  protectedResourceMap.set(`${environment.apiBaseUrl}/api/catalog/*`, [targetScope]);
  protectedResourceMap.set(`${environment.apiBaseUrl}/api/appointments/*`, [targetScope]);
  protectedResourceMap.set(`${environment.apiBaseUrl}/api`, [targetScope]);
  protectedResourceMap.set('http://localhost:8080/api/*', [targetScope]);
  protectedResourceMap.set('http://localhost:8080/api/catalog/*', [targetScope]);
  protectedResourceMap.set('http://localhost:8080/api/appointments/*', [targetScope]);
  protectedResourceMap.set('http://localhost:8080/api', [targetScope]);

  return {
    interactionType: InteractionType.Redirect,
    protectedResourceMap
  };
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(withInterceptorsFromDi()),
    {
      provide: MSAL_INSTANCE,
      useFactory: MSALInstanceFactory
    },
    {
      provide: APP_INITIALIZER,
      useFactory: initializeMsal,
      deps: [MSAL_INSTANCE],
      multi: true
    },
    { provide: MSAL_GUARD_CONFIG, useFactory: msalGuardConfigFactory },
    { provide: MSAL_INTERCEPTOR_CONFIG, useFactory: MSALInterceptorConfigFactory },
    { provide: HTTP_INTERCEPTORS, useClass: MsalInterceptor, multi: true },
    MsalService,
    MsalGuard,
    MsalBroadcastService
  ]
};
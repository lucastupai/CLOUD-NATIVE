import { Injectable } from '@angular/core';
import { MsalService } from '@azure/msal-angular';
import { AccountInfo, AuthenticationResult } from '@azure/msal-browser';
import { from, Observable, tap, catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { decodeJwt } from './token.util';

@Injectable({ providedIn: 'root' })
export class AuthService {
  constructor(private readonly msal: MsalService) {}

  private clearInteractionStatus(): void {
    try {
      sessionStorage.removeItem('msal.interaction.status');
      localStorage.removeItem('msal.interaction.status');
      for (let i = sessionStorage.length - 1; i >= 0; i--) {
        const key = sessionStorage.key(i);
        if (key && key.includes('interaction.status')) {
          sessionStorage.removeItem(key);
        }
      }
      for (let i = localStorage.length - 1; i >= 0; i--) {
        const key = localStorage.key(i);
        if (key && key.includes('interaction.status')) {
          localStorage.removeItem(key);
        }
      }
    } catch {
      // Ignorar excepciones al acceder a storage en modos estrictos
    }
  }

  login(): Observable<AuthenticationResult> {
    this.clearInteractionStatus();
    return from(this.msal.loginPopup({ scopes: [environment.apiScope] })).pipe(
      tap(result => this.msal.instance.setActiveAccount(result.account)),
      catchError(err => {
        if (String(err?.message ?? err).includes('interaction_in_progress')) {
          this.clearInteractionStatus();
        }
        return throwError(() => err);
      })
    );
  }

  logout(): void {
    this.clearInteractionStatus();
    this.msal.logoutPopup({
      account: this.activeAccount,
      mainWindowRedirectUri: environment.entra.postLogoutRedirectUri
    }).subscribe({
      next: () => {
        this.clearInteractionStatus();
        this.msal.instance.setActiveAccount(null);
      },
      error: () => {
        this.clearInteractionStatus();
        this.msal.instance.setActiveAccount(null);
      }
    });
  }

  get isAuthenticated(): boolean {
    return !!this.activeAccount;
  }

  get activeAccount(): AccountInfo | null {
    return this.msal.instance.getActiveAccount() ?? this.msal.instance.getAllAccounts()[0] ?? null;
  }

  get displayName(): string {
    return this.activeAccount?.name ?? this.activeAccount?.username ?? 'Usuario';
  }

  async getAccessToken(): Promise<string> {
    const account = this.activeAccount;
    if (!account) throw new Error('No hay una sesión activa.');

    const result = await this.msal.instance.acquireTokenSilent({
      account,
      scopes: [environment.apiScope]
    });

    return result.accessToken;
  }

  async getAccessTokenClaims(): Promise<Record<string, unknown>> {
    return decodeJwt(await this.getAccessToken());
  }

  async getRoles(): Promise<string[]> {
    const claims = await this.getAccessTokenClaims();
    const roles = claims['roles'];
    if (Array.isArray(roles) && roles.length > 0) {
      return roles.map(String);
    }
    // Roles operativos por defecto si aún no han sido configurados en Azure Portal
    return ['ADMIN', 'RECEPCIONISTA'];
  }
}

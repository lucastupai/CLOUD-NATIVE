import { Component, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MsalService } from '@azure/msal-angular';
import { NavbarComponent } from './shared/components/navbar/navbar.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent],
  template: `
    <app-navbar />
    <router-outlet />
  `
})
export class AppComponent implements OnInit {
  constructor(private readonly msal: MsalService) {}

  ngOnInit(): void {
    this.initializeMsal();
  }

  private async initializeMsal(): Promise<void> {
    try {
      await this.msal.instance.initialize();
      const accounts = this.msal.instance.getAllAccounts();
      if (accounts.length > 0 && !this.msal.instance.getActiveAccount()) {
        this.msal.instance.setActiveAccount(accounts[0]);
      }
    } catch (error) {
      console.error("Error initializing MSAL:", error);
    }
  }
}
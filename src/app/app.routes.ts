import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: 'dashboard', children: [] },
  { path: 'device-login', children: [] },
  { path: '', children: [] },
  { path: '**', redirectTo: '' }
];

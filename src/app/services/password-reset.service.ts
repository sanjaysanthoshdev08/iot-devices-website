import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

const API_BASE_URL = 'http://localhost:8080/api/v1/auth';

export interface PasswordResetMessage {
  message: string;
}

@Injectable({ providedIn: 'root' })
export class PasswordResetService {
  private readonly http = inject(HttpClient);

  /** Asks the backend to send a 6-digit reset code to the email. */
  requestCode(email: string): Observable<PasswordResetMessage> {
    return this.http.post<PasswordResetMessage>(`${API_BASE_URL}/forgot-password`, { email });
  }

  resetPassword(body: {
    email: string;
    code: string;
    password: string;
    confirmPassword: string;
  }): Observable<PasswordResetMessage> {
    return this.http.post<PasswordResetMessage>(`${API_BASE_URL}/reset-password`, body);
  }
}

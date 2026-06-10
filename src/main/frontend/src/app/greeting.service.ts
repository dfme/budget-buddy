import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Greeting {
  message: string;
  timestamp: string;
}

@Injectable({ providedIn: 'root' })
export class GreetingService {
  private readonly http = inject(HttpClient);

  /** Calls the Spring Boot backend. The /api prefix is proxied during `ng serve`
   *  and served from the same origin in the packaged JAR. */
  getGreeting(name: string): Observable<Greeting> {
    return this.http.get<Greeting>('/api/greeting', { params: { name } });
  }
}

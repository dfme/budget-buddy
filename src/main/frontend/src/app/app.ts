import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterOutlet } from '@angular/router';

import { Greeting, GreetingService } from './greeting.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  private readonly greetingService = inject(GreetingService);

  protected readonly title = signal('spring-boot-with-angular-template');
  protected readonly name = signal('World');
  protected readonly greeting = signal<Greeting | null>(null);
  protected readonly error = signal<string | null>(null);

  protected callBackend(): void {
    this.error.set(null);
    this.greetingService.getGreeting(this.name()).subscribe({
      next: (greeting) => this.greeting.set(greeting),
      error: () => this.error.set('Could not reach the backend.')
    });
  }
}

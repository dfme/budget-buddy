import { Router, UrlTree, provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';

import { devOnlyGuard, IS_DEV_MODE } from './dev-only.guard';

function runGuardWith(isDev: boolean): boolean | UrlTree {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), { provide: IS_DEV_MODE, useValue: isDev }],
  });
  return TestBed.runInInjectionContext(
    () => devOnlyGuard(null as never, null as never) as boolean | UrlTree,
  );
}

describe('devOnlyGuard', () => {
  it('erlaubt die Route im Dev-Modus', () => {
    expect(runGuardWith(true)).toBe(true);
  });

  it('leitet im Prod-Modus auf /dashboard um', () => {
    const result = runGuardWith(false);
    const expected = TestBed.inject(Router).createUrlTree(['/dashboard']);
    expect(result.toString()).toBe(expected.toString());
  });
});

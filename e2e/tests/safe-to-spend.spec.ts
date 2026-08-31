import { expect, test } from '../fixtures/auth.fixture';

/**
 * E2E-Abdeckung der Must-Have-Story US-06 «Wöchentlicher Safe-to-Spend-Betrag» (E2E-STS-01).
 *
 * Ein Happy Path und ein Fehlerpfad — die in CLAUDE.md («Testing: Frameworks») vorgeschriebene
 * Menge, und zwar pro Story, nicht pro Issue. US-06 besteht aus mehreren Feature-Issues
 * (BE-STS-01…04, FE-STS-01…03); die beiden Fälle gehören deshalb hierher und nicht in einen
 * Feature-PR.
 *
 * Einstieg über `authenticatedPage`: `/dashboard` liegt hinter `authGuard` UND `onboardingGuard`,
 * die Fixture erledigt beides über die API (siehe `fixtures/auth.fixture.ts`).
 */
test.describe('Safe-to-Spend', () => {
  /**
   * Monatseinkommen und Fixkosten des Happy Path.
   *
   * <p>Die Zahlen sind nicht beliebig. Der angezeigte Betrag ist
   * `(Einkommen − Fixkosten − variable Ausgaben) ÷ weeksLeft`, und `weeksLeft` ist die
   * aufgerundete Zahl der im laufenden Monat verbleibenden Wochen — je nach Kalendertag 1 bis 5.
   * Bei 12'000 − 1'200 = 10'800 verfügbar liegt das Ergebnis für *jedes* `weeksLeft` zwischen
   * 2'160.00 und 10'800.00 und ist damit immer vierstellig. Nur deshalb darf
   * {@link BETRAG_FORMAT} die Tausendertrennung verlangen, statt sie bloss zu erlauben.
   *
   * <p>Variable Ausgaben sind 0, weil das Konto der Fixture keine Transaktionen hat.
   */
  const MONATSEINKOMMEN = 12_000;
  const FIXKOSTEN = { bezeichnung: 'Miete', betrag: 1_200, intervall: 'monatlich' };

  /**
   * Erwartetes Format des Hero-Betrags.
   *
   * <p><strong>ASCII-Apostroph, anders als in `fixed-cost-wizard.spec.ts:37`.</strong> Dort steht
   * `’` (Right Single Quotation Mark), weil die Fixkostenliste über Angulars `CurrencyPipe`
   * unter `de-CH` formatiert. Das Dashboard geht einen anderen Weg: `app-amount` ruft
   * `formatSwissAmount` (`frontend/src/app/shared/format.ts`), und die Funktion setzt den
   * Apostroph selbst — als ASCII `'`. Die beiden Zeichen sind im Quelltext kaum zu unterscheiden;
   * wer sie hier «zur Konsistenz» angleicht, lässt den Test reissen.
   *
   * <p><strong>Zwischen `CHF` und dem Betrag steht kein Text-Whitespace.</strong> Der sichtbare
   * Abstand kommt aus `amount.scss` (`.amount__currency { margin-right }`), und Angular entfernt
   * den Whitespace zwischen den Spans von `amount.html` beim Kompilieren. `toHaveText`
   * normalisiert Whitespace nur beim Vergleich mit einem String, nicht mit einer RegExp — der
   * tatsächliche Textinhalt ist `CHF10'800.00` plus abschliessendem Umbruch. Die `\s*` sind
   * deshalb Toleranz, nicht Erwartung: sie halten den Test auch dann grün, wenn das Template
   * später wieder ein Leerzeichen setzt.
   */
  const BETRAG_FORMAT = /^CHF\s*\d{1,3}'\d{3}\.\d{2}\s*$/;

  test('Happy Path: Dashboard zeigt den berechneten Betrag in de-CH-Formatierung', async ({
    authenticatedContext: context,
    authenticatedPage: page,
  }) => {
    // Vorbedingung der AC («eingeloggt mit Einkommen und Fixkosten») über die API statt über die
    // Einstellungs- und Wizard-Formulare: beide haben ihre eigene E2E-Abdeckung (E2E-FC-01,
    // FE-SET-03). Sie hier noch einmal durchzuklicken hinge diesen Test an fremdem UI auf.
    const income = await context.request.put('/api/users/me/income', {
      data: { betrag: MONATSEINKOMMEN },
    });
    expect(income.status(), 'Vorbedingung: PUT /api/users/me/income').toBe(200);

    const fixedCost = await context.request.post('/api/fixed-costs', { data: FIXKOSTEN });
    expect(fixedCost.status(), 'Vorbedingung: POST /api/fixed-costs').toBe(201);

    await page.goto('/dashboard');

    const amount = page.locator('app-amount.safe-to-spend__amount');
    await expect(amount).toBeVisible();
    await expect(amount).toHaveText(BETRAG_FORMAT);

    // Der Platzhalter-Zweig aus `dashboard.html` darf daneben nicht stehen — sonst wäre offen,
    // welches der beiden Elemente der Nutzer sieht.
    await expect(page.locator('.safe-to-spend__amount--placeholder')).toHaveCount(0);

    /*
     * Querprobe gegen das Backend statt eines verdrahteten Erwartungswerts.
     *
     * Ein fester CHF-Betrag wäre an vier von fünf Wochen rot (siehe `weeksLeft` oben), und ihn
     * im Test aus `weeksLeft` nachzurechnen wäre eine zweite Kopie der Produktionsformel — die
     * genau dann mitwandert, wenn die erste falsch wird. Ein E2E-Test, der die getestete Rechnung
     * nachbaut, kann sie nicht mehr widerlegen. Die Formel selbst liegt bei
     * `SafeToSpendServiceTest` (Backend, fixe Clock); hier gehört die Frage hin, ob die UI die
     * Zahl des Backends unverfälscht rendert.
     */
    const response = await context.request.get('/api/budget/safe-to-spend');
    expect(response.status(), 'Querprobe: GET /api/budget/safe-to-spend').toBe(200);
    const body = await response.json();
    expect(body.noIncome, 'Vorbedingung: Einkommen ist gesetzt').toBe(false);
    expect(body.negative, 'Vorbedingung: Budget ist nicht überzogen').toBe(false);

    // Aus dem DOM zurückgelesen statt `formatSwissAmount` im Test nachgebaut: die Formatierung
    // prüft bereits BETRAG_FORMAT, hier geht es allein um den Zahlenwert.
    const gerendert = ((await amount.textContent()) ?? '').replace(/\s+/g, '');
    expect(gerendert, 'Kein Vorzeichen bei positivem Betrag (hidePositiveSign)').not.toContain('+');
    expect(Number(gerendert.replace(/^CHF/, '').replace(/'/g, ''))).toBeCloseTo(body.amount, 2);
  });

  test('Fehlerpfad: ohne Einkommen erscheint der No-Income-State statt einer Zahl', async ({
    authenticatedContext: context,
    authenticatedPage: page,
  }) => {
    // Kein Setup: ein frisch registriertes Konto hat kein `monthlyIncome` (`User`-Konstruktor).
    // Der Fehlerpfad ist exakt die Fixture ohne Zutat.
    await page.goto('/dashboard');

    const card = page.locator('app-card.safe-to-spend-card');
    await expect(card).toBeVisible();

    const notice = card.locator('app-notice.no-income__notice');
    await expect(notice).toBeVisible();
    await expect(notice.locator('.notice__title')).toHaveText('Kein Einkommen erfasst');

    // Platzhalter statt Betrag: `app-amount` wird in diesem Zweig gar nicht erst gerendert.
    await expect(card.locator('app-amount')).toHaveCount(0);
    const placeholder = card.locator('.safe-to-spend__amount--placeholder');
    await expect(placeholder).toBeVisible();
    await expect(placeholder).toContainText('CHF');
    await expect(placeholder).toContainText('—');

    // Die beiden Formulierungen wörtlich aus der AC: keine irreführende Zahl. `CHF 0.00` wäre
    // die unangenehmere der beiden — es sieht aus wie ein Ergebnis und ist keines.
    const kartentext = (await card.textContent()) ?? '';
    expect(kartentext).not.toContain('NaN');
    expect(kartentext).not.toMatch(/CHF\s*0\.00/);

    // Ohne Transaktionen findet die Heuristik kein wiederkehrendes Gutschriftsmuster, also gibt
    // es keinen Vorschlag und keinen Button. Das hält den Fall deterministisch — belegt wird die
    // Abwesenheit, statt um ein optionales Element herum zu assertieren.
    await expect(card.getByRole('button', { name: 'Übernehmen' })).toHaveCount(0);

    // Gegenprobe, dass der Zustand wirklich «kein Einkommen» ist und nicht bloss ein Ladefehler,
    // der zufällig dieselbe Ansicht erzeugt.
    const response = await context.request.get('/api/budget/safe-to-spend');
    expect(response.status(), 'Querprobe: GET /api/budget/safe-to-spend').toBe(200);
    const body = await response.json();
    expect(body.noIncome).toBe(true);
    expect(body.amount).toBeNull();
    expect(body.incomeSuggestion).toBeNull();
  });
});

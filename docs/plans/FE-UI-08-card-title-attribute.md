# [FE-UI-08] app-card lässt das globale title-Attribut am Host stehen

- **Issue:** [#194](https://github.com/dfme/budget-buddy/issues/194)
- **Task-ID:** `FE-UI-08`
- **Branch:** `fix/FE-UI-08-card-title-attribute`
- **Story:** — (kein us-*-Label)
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-16

## Problem

`app-card` deklariert einen Input `title` ([card.ts:18](../../frontend/src/app/shared/card/card.ts#L18)).
`title` ist zugleich ein globales HTML-Attribut. Schreibt ein Aufrufort es statisch —
`<app-card title="Safe-to-Spend">` —, setzt Angular den Input **und** belässt das Attribut am
Host-Element im DOM. Das erzeugt einen nativen Tooltip über der ganzen Karte und einen
Accessible Name auf dem Host, der die sichtbare Überschrift (`.card__title`) dupliziert.

Dieselbe Konstellation trat in #181 bei `app-notice` auf und wurde dort mit einem
`host: { '[attr.title]': 'null' }`-Binding behoben ([notice.ts:33-46](../../frontend/src/app/shared/notice/notice.ts#L33-L46)),
ebenso bei `app-modal` ([modal.ts:37-46](../../frontend/src/app/shared/modal/modal.ts#L37-L46)). `app-card` hatte
dieselbe Konstellation, wurde damals aber bewusst nicht mitgefixt.

## Gegenprobe auf weitere Shared-Komponenten (Schritt 2/3)

Alle Inputs unter `frontend/src/app/shared/**/*.ts` gegen die Liste globaler HTML-Attribute
geprüft (`id`, `class`, `style`, `title`, `lang`, `dir`, `hidden`, `tabindex`, `slot`,
`draggable`, `spellcheck`, `translate`, `accesskey`, `contenteditable`, `role`, `autofocus`,
`inert`, `popover`). Einzige Kollision ausser `Card.title`: `Notice.title` und `Modal.title` —
beide bereits behoben (#181 bzw. im ursprünglichen Modal-PR). Kein weiterer Fund, kein
Folge-Issue nötig.

## Call-Sites (Ist-Stand, Schritt 2)

Der Issue-Text nennt `dashboard.html:15`, `category-overview.html:22`, `styleguide.html:41`,
`:125`, `:128` — die Datei hat sich seither weiterentwickelt (u. a. FE-PDF-04). Aktuelle
Fundstellen mit statischem `title="…"` (`grep -n 'app-card' …`):

- [dashboard.html:71](../../frontend/src/app/dashboard/dashboard.html#L71)
- [category-overview.html:39](../../frontend/src/app/transactions/category-overview.html#L39)
- [category-overview.html:116](../../frontend/src/app/transactions/category-overview.html#L116)
- [styleguide.html:41](../../frontend/src/app/styleguide/styleguide.html#L41)
- [styleguide.html:125](../../frontend/src/app/styleguide/styleguide.html#L125)
- [styleguide.html:128](../../frontend/src/app/styleguide/styleguide.html#L128)

(`dashboard.html:157` bindet `[title]="totalsTitle()"` dynamisch — davon nicht betroffen, da kein
statisches Attribut im Template steht.)

Diese sechs Stellen werden **geprüft, nicht geändert** — der Fix greift am Host-Binding in
`Card`, nicht an den Aufruforten.

## Implementierungsschritte

1. `card.ts`: `host: { '[attr.title]': 'null' }` ergänzen, mit Begründungskommentar analog zu
   `notice.ts`/`modal.ts`.
2. `card.spec.ts`: Regressionstest ergänzen, analog zu `notice.spec.ts` (`StaticTitleHost`) —
   ein Host mit `<app-card title="…">Inhalt</app-card>` rendern und prüfen:
   - `host.hasAttribute('title') === false`
   - `.card__title` zeigt weiterhin den Titeltext
3. Die sechs Aufruforte manuell/visuell durchgehen (kein Code-Edit dort erwartet).

## Test-Strategie

- Unit-Test (Vitest/Angular TestBed) in `card.spec.ts`, Muster aus `notice.spec.ts`
  übernommen — deckt AC 1 und AC 3 ab.
- Bestehende Tests in `card.spec.ts` (Kopf entfällt ohne Titel/Meta, Titel+Meta rendern) bleiben
  unverändert grün — deckt AC 2 ab.
- Keine E2E nötig: reine DOM-Attribut-Frage auf Komponentenebene.

## Acceptance Criteria

- [ ] `app-card` lässt kein `title`-Attribut am Host-Element stehen, auch wenn der Aufrufort es
      statisch schreibt
- [ ] Der `title`-Input funktioniert unverändert — die sichtbare Überschrift `.card__title`
      bleibt, ebenso das Verhalten «ohne Titel und Meta entfällt der Kopf ganz»
- [ ] Ein Regressionstest rendert einen Aufruf-Host mit statischem `title="…"` und prüft
      `hasAttribute('title') === false` sowie den weiterhin gesetzten Input — analog zu
      `notice.spec.ts` aus #181
- [ ] Die sechs (aktueller Stand, s.o.) bestehenden Aufruforte sind geprüft und bleiben
      unverändert bedienbar
- [ ] Gegenprobe auf weitere Shared-Komponenten mit Inputs, die auf globale HTML-Attribute
      kollidieren: Ergebnis im PR benannt (siehe oben — keine weiteren Funde)

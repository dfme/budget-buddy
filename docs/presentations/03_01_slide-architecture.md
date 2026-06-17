# Folie: Architektur — wie aus PDF eine Wochenzahl wird

## Titel

**Von PDF zu Safe-to-Spend — in drei Schritten**

## Diagramm (Monospace — direkt in PPT als Code-Block einfügbar)

```
   ┌──────────────────┐
   │   Angular SPA    │   Signals · OnPush
   └────────┬─────────┘
            │  JWT Bearer (HS256)
            ▼
   ┌──────────────────────────────────────────┐
   │           Spring Boot 3.x                │
   │                                          │
   │   ┌─────────────┐                        │
   │   │  PDFBox 3   │  ← Schweizer Banken    │
   │   └──────┬──────┘    UBS · Raiffeisen    │
   │          │           PostFinance         │
   │          ▼                               │
   │   ┌─────────────────────────────────┐    │
   │   │   Hybrid-Kategorisierer         │    │
   │   │   ├─ Lookup-Tabelle  70–80 %    │    │
   │   │   └─ Claude Haiku    20–30 % ───┼────┼──► Claude API
   │   └─────────────┬───────────────────┘    │   (Fallback: "Sonstiges")
   │                 ▼                        │
   │   ┌─────────────────────────────────┐    │
   │   │   Safe-to-Spend Engine          │    │
   │   │   (Einkommen − Fix − Ist) ÷ Wo. │    │
   │   └─────────────┬───────────────────┘    │
   └─────────────────┼────────────────────────┘
                     │  Flyway-managed Schema
                     ▼
              ┌─────────────┐
              │   SQLite    │   BigDecimal · nDSG-konformes Delete
              └─────────────┘
```

## Drei Take-Aways auf der Folie

- **PDFBox 3** für Schweizer Bank-Layouts (Apostroph-Tausender via Pre-Parse normalisiert).
- **Hybrid-Kategorisierung:** Lookup deckt das Bekannte deterministisch ab, LLM nur für die Lücke — kostet wenig, lernt aus User-Korrekturen ohne Retraining.
- **SQLite + JWT:** kein Session-Schreibdruck, kein DB-Server. Migrationspfad zu Postgres ist dokumentiert, aber MVP-Scope schliesst ihn aus.

---

## Speaker Notes (60 Sekunden)

> Drei Schichten, eine Pipeline. Oben Angular als SPA mit JWT-Auth — wir vermeiden bewusst Server-Sessions, weil SQLite bei jedem Request schreiben müsste.
>
> Im Backend macht PDFBox 3 die Text-Extraktion aus Bank-PDFs. Schweizer Spezifika wie der Apostroph-Tausender — `1'234.56` — werden vor dem BigDecimal-Parse normalisiert. Kein `double` für Geld.
>
> Das Herzstück ist der Hybrid-Kategorisierer: 70 bis 80 Prozent der Transaktionen erkennen wir deterministisch über eine Lookup-Tabelle — Migros ist Migros. Die restlichen 20 bis 30 Prozent gehen an Claude Haiku. Wenn die API ausfällt, ist die Fallback-Kategorie "Sonstiges" — der Import-Flow blockiert nie.
>
> User-Korrekturen erweitern die Lookup-Tabelle. Das System lernt ohne Retraining.
>
> Persistenz: SQLite mit Flyway-Migrationen. Geld immer als BigDecimal. Konto-Löschung ist nDSG-konform — kein Eintrag bleibt zurück.

---

## Optional: zwei Sätze für Nachfragen

- **"Warum kein Postgres?"** — MVP-Scope, kein Concurrent-Write-Bottleneck zu erwarten. Migrationspfad ist beschrieben.
- **"Warum kein WebFlux?"** — SQLite-JDBC ist blockierend. Reactive-Wrapping würde Komplexität ohne Nutzen bringen.

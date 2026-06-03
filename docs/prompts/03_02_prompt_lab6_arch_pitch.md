##########
# Prompt #
##########

Wir haben 5 Minuten um unsere Architektur zu pitchen.
Hier ist unser Architecture Draft:
- C2 Container Diagram befindet sich im CLAUDE.md
- Alles ADRs sind unter @docs/adr/README.md
- Pattern-Entscheidungen: Modularer Monolith (ADR-4)

Hilf uns:
1. Den stärksten 1-Satz für unser System formulieren
2. Die 3 Punkte identifizieren die ein CTO sofort fragen würde
3. Vorbereiten wie wir auf 'Warum nicht Microservices?' antworten
4. Vorbereiten wie wir auf 'Was ist euer grösstes Risiko?' antworten
5. Was ist das wichtigste ADR die in den Pitch gehört?


###########
# Antwort #
###########


Hier sind die 5 Antworten, direkt aus dem bestehenden Architektur-Kontext:

1. Stärkster 1-Satz
"BudgetBuddy liest einen Schweizer Kontoauszug-PDF ein, kategorisiert Transaktionen hybrid mit Lookup-Tabelle + Claude API, und gibt Studenten in unter 30 Sekunden ihren wöchentlichen Safe-to-Spend — deployt als Single JAR, kein Kubernetes, kein Overhead."

2. Die 3 Fragen, die ein CTO sofort stellen wird
#	Frage	Eure Antwort in 1 Satz
1	"Ihr speichert Finanzdaten — wie sichert ihr das?"	JWT als httpOnly Cookie (XSS-sicher), bcrypt-Passwörter, nDSG Right-to-Deletion implementiert, Hosting Frankfurt/EU (ADR-7, ADR-10)
2	"SQLite für Produktionsdaten — ernst gemeint?"	Ja, bewusst: kein Concurrent-Write-Problem im MVP, Flyway-Migrations laufen, Migration zu PostgreSQL ist 1-Sprint-Aufwand (ADR-5)
3	"Was passiert wenn Claude API down ist?"	Fallback zu "Sonstiges" — kein API-Fehler blockiert den Import-Flow, Timeout gesetzt (ADR-6)
3. "Warum nicht Microservices?"
"Drei Entwickler, drei Monate, MVP. Microservices verdreifachen den operativen Overhead (Service Discovery, Netzwerk-Latenz, separate Deployments) ohne messbaren Benefit bei Nutzerzahlen im zweistelligen Bereich. Unser Modularer Monolith mit klar getrennten Packages (auth/, transaction/, categorization/, budget/, report/) gibt uns Separation of Concerns heute — und lässt uns später einzelne Module als Services extrahieren, wenn wir echte Skalierungsprobleme haben. Wir optimieren nicht für hypothetische Millionen-User, sondern für Lara und Marc."

4. "Was ist euer grösstes Risiko?"
Nennt Risiko #1 zuerst (zeigt Selbstreflexion), dann die Mitigation:

"Churn nach dem ersten Upload. Wenn Kategorisierung schlecht ist oder der Import 20 Sekunden hängt, kommt Lara nie wieder. Mitigation: synchroner Import-Endpoint mit Timeout + Fallback, plus manuelle Korrektur als Feature — jede Korrektur erweitert die Lookup-Tabelle und verbessert das System für alle. Risiko #2 ist ein Datenleck: existenzbedrohend bei Finanzdaten. Deshalb httpOnly Cookies, kein JS-Zugriff auf Tokens, SameSite=Strict."

5. Das wichtigste ADR für den Pitch
ADR-6 (Hybrid-Kategorisierung) — das ist euer technischer USP.

Es zeigt:

Cost Awareness: LLM-Only wäre ~750 CHF/Monat, euer Hybrid kommt auf ~20%
Pragmatismus: Lookup zuerst, Claude nur für unbekannte Einträge
Lerneffekt: User-Korrekturen trainieren das System ohne Retraining
Erwähnt ADR-6 explizit beim Pitch — es ist die Entscheidung, die zeigt dass ihr wirtschaftlich denkt, nicht nur technisch.


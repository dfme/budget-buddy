# ADR-6: Hybrid-Kategorisierung (Lookup-Tabelle + Claude API)

**Status:** Accepted (locked)  
**Entscheidung vom:** 2026-05-27  
**Betroffen:** Transaction Categorization Logic

---

## Context

BudgetBuddy muss Transaktionen in Kategorien klassifizieren:

```
Kategorien: Wohnen, Lebensmittel, Transport, Versicherung, Telekom, Gesundheit, 
            Freizeit, Restaurant, Shopping, Bildung, Einkommen, Sparen, Sonstiges
```

**Anforderungen:**
- Automatisch (kein Manual Labeling für 1.000+ Transaktionen)
- Schnell (<500ms pro Batch von 100 Transaktionen)
- Günstig (User zahlen nichts, also LLM-Calls kosteneffizient)
- Genau (80%+ Accuracy; Nutzer-Korrektionen möglich)
- Fallback bei API-Error (nie die Import-Flow blockieren)

### Optionen

1. **Lookup-Tabelle** — Merchant-Name → Category (z.B. "Migros" → Lebensmittel)
2. **LLM Only (Claude API)** — Alle Transaktionen an Claude
3. **ML Model (fine-tuned)** — Training auf historischen User-Daten
4. **Hybrid** — Lookup + Claude für unbekannte Transaktionen
5. **Rule-Based** — Pattern Matching (z.B. regex für IBAN-Nummern)

---

## Decision

**Hybrid Approach:**

```
Input: Transaktion { date, amount, recipient }
  ↓
Step 1: Lookup in Static Table
  if (recipient in lookup_table)
    → return cached_category
  else
    → proceed to Step 2
  ↓
Step 2: Claude API (LLM)
  prompt: "Kategorisiere Transaktion: '{recipient}'"
  → Claude returns: "Lebensmittel"
  ↓
Step 3: Fallback
  if (Claude error)
    → return "Sonstiges"
  ↓
Step 4: User Correction (future)
  if (user manually changes category)
    → add to lookup_table (feedback loop)
```

### Lookup-Tabelle Schema

```sql
CREATE TABLE merchant_categories (
    id BIGINT PRIMARY KEY,
    merchant_name TEXT UNIQUE NOT NULL,
    category TEXT NOT NULL,
    created_at TIMESTAMP,
    user_id BIGINT,  -- NULL = global, otherwise user-specific override
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Global Lookups (populated initially)
INSERT INTO merchant_categories (merchant_name, category, user_id)
VALUES
    ('MIGROS', 'Lebensmittel', NULL),
    ('COOP', 'Lebensmittel', NULL),
    ('DENNER', 'Lebensmittel', NULL),
    ('SBB', 'Transport', NULL),
    ('UBER', 'Transport', NULL),
    ('DIGITEC GALAXUS', 'Shopping', NULL),
    ('MANOR', 'Shopping', NULL),
    ('ZALANDO', 'Shopping', NULL),
    ('NETFLIX', 'Freizeit', NULL),
    ('SPOTIFY', 'Freizeit', NULL),
    ('FÉDÉRAL FINANCE', 'Sonstiges', NULL),
    -- ... ~200-300 global merchants
;
```

### Claude API Prompt

```
Du bist ein Finanz-Kategorisierungs-Expert für Schweizer Bankkonten.

Kategorisiere diese Transaktion in GENAU eine Kategorie:
[Wohnen, Lebensmittel, Transport, Versicherung, Telekom, Gesundheit,
 Freizeit, Restaurant, Shopping, Bildung, Einkommen, Sparen, Sonstiges]

Transaktion: "DIGITEC GALAXUS AG 044 913 2323"
Betrag: CHF 125.50

Antworte NUR mit der Kategoriename (z.B. "Shopping"), keine Erklärung.
```

---

## Rationale

| Kriterium | Lookup | LLM Only | Hybrid | ML Model | Rule-Based |
|-----------|--------|----------|--------|----------|-----------|
| **Speed** | ✅✅ <1ms | ❌ 200-500ms | ✅ <5ms (mostly lookup) | ✅ <50ms | ✅ <1ms |
| **Cost** | ✅ $0 | ❌ $0.01 per 100 tokens | ✅ ~$0.001 per tx | ⚠️ Training cost | ✅ $0 |
| **Accuracy** | ⚠️ 70-80% (known) | ✅ 85-90% | ✅ 85-90% | ✅ 90%+ | ❌ 60-70% |
| **Flexibility** | ❌ Static | ✅ Context-aware | ✅ Fallback | ⚠️ Requires data | ❌ Brittle |
| **User Learning** | ✅ Manual overrides train | ⚠️ Can't easily retrain | ✅ User → Lookup | ❌ Need retraining | ❌ Can't learn |
| **Fallback** | ✅ No risk | ❌ API error blocks | ✅ Fallback ok | ❌ No fallback | ⚠️ Reduced accuracy |
| **Implementation** | ✅ Simple | ✅ Simple | ✅✅ Elegant | ❌ Complex ML pipeline | ⚠️ Regex fragile |

**Konkrete Vorteile für BudgetBuddy:**

1. **Cost-Optimized:** Lookup deckt ~70-80% ab (bekannte Händler)
   - 1.000 Transaktionen × $0.00075/tx (Haiku) = $0.75/Monat
   - vs. LLM-Only: $0.75 × 1000 = $750 (too expensive!)

2. **Fast:** Lookup <1ms + nur 20-30% Transaktionen an Claude
   - Durchschnitt: 0.99 × 1ms + 0.01 × 200ms = ~2.9ms/tx

3. **Accurate:** 80%+ Accuracy (Haiku) + User kann manuell korrigieren
   - User-Korrektur → Lookup-Entry für diesen Merchant → "Lernen"

4. **Fallback-Safe:** Claude-Error → "Sonstiges" (nie die Import-Flow blockieren)

5. **Scalable:** Lookup-Tabelle wächst über Zeit (User + Global)
   - Monat 1: 100 Global Entries → 80% Accuracy
   - Monat 6: 500+ (Global + User) → 85%+ Accuracy

---

## Implementation Details

### Code Structure (Java/Spring Boot)

```java
@Service
public class CategorizationService {
    
    @Autowired
    private MerchantCategoryRepository lookupRepo;
    
    @Autowired
    private AnthropicClient claudeClient;
    
    public String categorizeTransaction(String merchantName, BigDecimal amount) {
        // Step 1: Lookup
        Optional<String> cached = lookupRepo.findCategoryByMerchant(merchantName);
        if (cached.isPresent()) {
            return cached.get();
        }
        
        // Step 2: Claude API
        try {
            String category = claudeClient.categorize(merchantName, amount);
            return category;
        } catch (AnthropicException e) {
            // Step 3: Fallback
            logger.warn("Claude API failed for {}: {}", merchantName, e.getMessage());
            return "Sonstiges";
        }
    }
    
    public void recordUserCorrection(String merchantName, String category, Long userId) {
        // Step 4: User Feedback → Lookup Update
        lookupRepo.saveOrUpdate(merchantName, category, userId);
    }
}
```

### Batch Processing (Efficient)

```java
@Service
public class BulkImportService {
    
    public void importTransactions(List<Transaction> txs, Long userId) {
        // Batch Lookup (single query)
        Map<String, String> lookupCache = lookupRepo.findCategories(
            txs.stream().map(tx -> tx.getRecipient()).collect(toSet())
        );
        
        // Separate into two groups
        List<Transaction> knownTxs = new ArrayList<>();
        List<Transaction> unknownTxs = new ArrayList<>();
        
        for (Transaction tx : txs) {
            if (lookupCache.containsKey(tx.getRecipient())) {
                tx.setCategory(lookupCache.get(tx.getRecipient()));
                knownTxs.add(tx);
            } else {
                unknownTxs.add(tx);
            }
        }
        
        // Batch Claude API calls (max 20 per request to save costs)
        for (List<Transaction> batch : partition(unknownTxs, 20)) {
            List<String> categories = claudeClient.categorizeBatch(batch);
            for (int i = 0; i < batch.size(); i++) {
                batch.get(i).setCategory(categories.get(i));
            }
        }
        
        // Persist all
        transactionRepository.saveAll(knownTxs);
        transactionRepository.saveAll(unknownTxs);
    }
}
```

---

## Consequences

### ✅ Positive

- **Cost:** 70-80% lookups = 80%+ Transaktionen kostenlos
- **Speed:** Most transactions <2ms (Lookup ist super schnell)
- **Accuracy:** 85-90% mit Claude für unbekannte (besser als Lookup allein)
- **Learning:** User-Korrektionen trainieren implizit das System
- **Robustness:** Claude-Fehler blockieren nicht (Fallback zu "Sonstiges")

### ⚠️ Negative

- **Initial Lookup-Tabelle:** Muss manuell populated werden (~200-300 Merchants)
- **User Overrides:** Fragmentiert Lookups (User-spezifische Overrides)
- **Not 100% Accurate:** 85-90% accuracy = 10-15% Errors (User kann manuell korrigieren)
- **Batch Processing:** Loose timing dependency (User wartet auf Batch)

### 🔄 Mitigations

| Problem | Mitigation |
|---------|-----------|
| **Initial Lookup-Tabelle** | Pre-populate with Top 300 Swiss merchants. Use feedback loop to expand. |
| **User Overrides** | Store as `merchant_categories.user_id IS NOT NULL` (user-specific) vs. global. Merge at query time. |
| **Manual Corrections** | UI shows category + "Edit" button. Click → save to user-specific lookup. |
| **Batch Latency** | Show progress bar ("Categorizing 42 transactions..."). Async if slow. |

---

## User Correction Workflow

```
1. PDF Import → Unknown Merchant → Default Category "Sonstiges"
   ↓
2. Dashboard shows Transaction in "Sonstiges"
   ↓
3. User sees "DIGITEC GALAXUS AG" in Sonstiges
   ↓
4. User clicks "Edit" → Opens Dropdown
   ↓
5. User selects "Shopping"
   ↓
6. System saves:
     INSERT INTO merchant_categories 
     VALUES ('DIGITEC GALAXUS AG', 'Shopping', :userId)
   ↓
7. Next DIGITEC transaction → Lookup finds user override → "Shopping"
   ↓
8. Next month, user's PDF has new DIGITEC tx → Auto-categorized as "Shopping"
```

---

## Alternatives Considered

### ❌ Option 1: LLM-Only (Claude for everything)

**Entscheidung:** Abgelehnt (zu teuer)

**Begründung:**
- Cost: 1.000 Transaktionen × $0.00075/tx × 30 days = $22.50/User/Month
- At 1.000 Users = $22.500/Month = $270.000/Year
- Startup kann das nicht zahlen (auch wenn Claude Haiku cheap)

### ❌ Option 2: ML Model (fine-tuned)

**Entscheidung:** Abgelehnt (MVP overhead)

**Begründung:**
- Requires labeled training data (not available at MVP)
- Requires ML expertise (kein ML Engineer im Team)
- Retraining whenever User-Corrections happen (complex pipeline)
- Not 90%+ accuracy guarantee
- Better for later (Phase 2) wenn genug Daten vorhanden

### ⚠️ Option 3: Rule-Based Regex

**Entscheidung:** Abgelehnt (fragile)

**Begründung:**
- Pattern matching: `if recipient matches /MIGROS|COOP|DENNER/ → Lebensmittel`
- Problem: Merchants constantly change (z.B. "MIGROS ONLINE" vs "MIGROS" vs "MIGROS RESTAURANT")
- Requires constant updates
- Lower accuracy als Lookup-Table
- No LLM fallback

---

## Evaluation Metrics

Per Monat messen:

```
accuracy = (correct_categories / total_transactions) × 100%
lookup_hit_rate = (lookup_matches / total_transactions) × 100%
claude_calls = total - lookup_matches
cost = claude_calls × $0.00075
manual_corrections = user_edits_per_month

Target:
- accuracy ≥ 80%
- lookup_hit_rate ≥ 70%
- cost < $1/user/month
- manual_corrections < 5% (user edits)
```

---

## Related Decisions

- **ADR-1:** Java + Spring Boot (AnthropicClient integration)
- **ADR-5:** SQLite (Lookup-Tabelle stored locally)
- **ADR-9:** BigDecimal für Geldbeträge (Amount-Parameter)

---

## Future Optimization

**Wenn lookup_hit_rate plateaus < 80%:**
1. Collect user feedback on uncategorized transactions
2. Train lightweight ML classifier on accumulated data
3. Deploy as secondary classifier (after Lookup, before Claude)
4. Profile: saves 10-20% Claude calls while maintaining accuracy

---

## References

- [Anthropic Claude API — Streaming + Batching](https://docs.anthropic.com/claude/reference/batch-api)
- [Swiss Bank Transaction Categories (norms)](https://www.sbv.admin.ch/)
- CLAUDE.md — AI/Categorization Decisions

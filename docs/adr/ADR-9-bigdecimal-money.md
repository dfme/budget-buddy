# ADR-9: BigDecimal für Geldbeträge (nicht double/float)

**Status:** Accepted (locked)  
**Entscheidung vom:** 2026-05-27  
**Betroffen:** Alle Geldberechnungen (Transaktionen, Fixkosten, Sparziele, Safe-to-Spend)

---

## Context

BudgetBuddy speichert und berechnet CHF-Beträge (z.B. Transaktionen, Einkommen, Safe-to-Spend).

**Problem:**
```java
// ❌ DON'T: Using double
double amount1 = 0.1;
double amount2 = 0.2;
System.out.println(amount1 + amount2);  // Output: 0.30000000000000004 (NOT 0.3!)

// ❌ Finanz-Beispiel: Transaktions-Total
double total = 0.0;
total += 125.50;  // CHF 125.50
total += 45.20;   // CHF 45.20
total += 99.30;   // CHF 99.30
System.out.println(total);  // Expected: 270.00, Got: 269.9999999999999
// → User sees CHF 269.99 instead of CHF 270.00 (Rounding error!)
```

**Anforderung:**
- Exakte Berechnung bis auf den Rappen (0.01 CHF)
- Keine Rounding Errors
- Sichere Transaktionen (nie Geld verlieren wegen Floating-Point-Fehler)

### Optionen

1. **BigDecimal** — Decimal arithmetic, arbitrary precision, exact
2. **double** — Binary floating-point (IEEE 754), fast but inexact
3. **float** — Same as double, but worse precision
4. **long (Cents)** — Integer arithmetic (e.g., store as cents, divide by 100)
5. **Money Library** — Wrapper (e.g., Moneta, Joda-Money)

---

## Decision

**BigDecimal für alle Geldbeträge**

```java
// ✅ Geldbeträge immer als BigDecimal
public class Transaction {
    @Id
    @GeneratedValue
    private Long id;
    
    @Column(nullable = false)
    private LocalDate date;
    
    @Column(nullable = false)
    private String recipient;
    
    // ✅ BigDecimal mit 2 Dezimalstellen (Rappen)
    @Column(nullable = false, columnDefinition = "DECIMAL(10,2)")
    private BigDecimal amount;  // Can be positive (income) or negative (expense)
    
    private String category;
}

// ✅ Berechnung des Safe-to-Spend
public class SafeToSpendCalculator {
    
    public BigDecimal calculateWeeklySafeToSpend(
            BigDecimal monthlyIncome,
            BigDecimal fixedCosts,
            BigDecimal spentThisMonth,
            int remainingWeeks) {
        
        // (Income - FixedCosts - Spent) / RemainingWeeks
        BigDecimal available = monthlyIncome
            .subtract(fixedCosts)
            .subtract(spentThisMonth);
        
        return available.divide(
            BigDecimal.valueOf(remainingWeeks),
            2,  // 2 decimal places
            RoundingMode.HALF_UP
        );
    }
}

// ✅ Summing Transactions (exact)
public class TransactionSummary {
    
    public BigDecimal sumByCategory(String category) {
        return transactionRepository
            .findByCategory(category)
            .stream()
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

---

## Rationale

### Double vs. BigDecimal (Detailed Comparison)

| Kriterium | BigDecimal | double | long (Cents) |
|-----------|-----------|--------|--------------|
| **Precision** | ✅✅ Arbitrary (exact) | ❌ 15-17 significant digits | ✅ Exact (integer) |
| **Range** | ✅ Huge (scales) | ✅ ±1.8e308 | ⚠️ ±9.2e18 cents (~92M CHF) |
| **Exactness** | ✅ 0.1 + 0.2 = 0.3 | ❌ 0.1 + 0.2 = 0.30000000000004 | ✅ 10 + 20 = 30 cents |
| **Speed** | ⚠️ Slower | ✅ Fast | ✅ Very fast |
| **Memory** | ⚠️ More (overhead) | ✅ 8 bytes | ✅ 8 bytes |
| **Rounding** | ✅ Explicit control | ⚠️ Implicit/unclear | ✅ Integer ops |
| **Database** | ✅ DECIMAL type | ⚠️ FLOAT (imprecise) | ✅ BIGINT |
| **JSON Serialization** | ✅ String or Number | ⚠️ Number (ambiguous) | ✅ Long (cents) |

### Real-World Examples (Why BigDecimal Matters)

**Example 1: Transactional Accuracy**

```java
// ❌ Double: Rounding error accumulates
double total = 0.0;
for (int i = 0; i < 1000; i++) {
    total += 0.01;
}
System.out.println(total);  // Expected: 10.00, Got: 9.99... (WRONG!)

// ✅ BigDecimal: Exact
BigDecimal total = BigDecimal.ZERO;
for (int i = 0; i < 1000; i++) {
    total = total.add(new BigDecimal("0.01"));
}
System.out.println(total);  // Output: 10.00 (CORRECT!)
```

**Example 2: Safe-to-Spend Calculation**

```java
// ❌ Double: Monthly rounding error visible to user
double income = 5000.0;
double fixedCosts = 1234.56;
double spent = 2345.67;
double weeks = 4.0;
double safeTospend = (income - fixedCosts - spent) / weeks;
// Result: 269.9425 → User sees CHF 269.94, then total doesn't add up

// ✅ BigDecimal: Exact, controlled rounding
BigDecimal income = new BigDecimal("5000.00");
BigDecimal fixedCosts = new BigDecimal("1234.56");
BigDecimal spent = new BigDecimal("2345.67");
BigDecimal safeToSpend = income
    .subtract(fixedCosts)
    .subtract(spent)
    .divide(new BigDecimal("4"), 2, RoundingMode.HALF_UP);
// Result: 269.94 (exact, reproducible)
```

**Example 3: Database Storage**

```sql
-- ❌ FLOAT type (imprecise)
CREATE TABLE transactions (
    amount FLOAT NOT NULL  -- 0.30 stored as 0.300000004 internally
);

-- ✅ DECIMAL type (exact)
CREATE TABLE transactions (
    amount DECIMAL(10, 2) NOT NULL  -- 0.30 stored exactly
);
```

---

## Implementation

### JPA Entity

```java
@Entity
@Table(name = "transactions")
public class Transaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private LocalDate date;
    
    @Column(nullable = false, length = 255)
    private String recipient;
    
    // ✅ BigDecimal with explicit column definition
    @Column(nullable = false, columnDefinition = "DECIMAL(10,2)")
    private BigDecimal amount;
    
    // Getters and setters
    public BigDecimal getAmount() {
        return amount;
    }
    
    public void setAmount(BigDecimal amount) {
        // Optional: Validate and round to 2 decimal places
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
    }
}
```

### Service Layer (Calculations)

```java
@Service
public class TransactionService {
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    // ✅ Sum transactions by category
    public BigDecimal getTotalByCategory(Long userId, String category) {
        List<Transaction> transactions = transactionRepository
            .findByUserIdAndCategory(userId, category);
        
        return transactions.stream()
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    // ✅ Calculate monthly total (all expenses + income)
    public BigDecimal getMonthlyTotal(Long userId, YearMonth month) {
        return transactionRepository
            .findByUserIdAndMonth(userId, month)
            .stream()
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    // ✅ Safe-to-Spend calculation
    public BigDecimal calculateSafeToSpend(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        
        BigDecimal income = user.getMonthlyIncome();
        if (income == null) {
            return null;  // No income set
        }
        
        BigDecimal fixedCosts = calculateFixedCostsForMonth(userId);
        BigDecimal spent = calculateSpentThisMonth(userId);
        
        int daysLeft = getDaysLeftInMonth();
        int weeksLeft = Math.max(1, daysLeft / 7);  // At least 1 week
        
        BigDecimal safeToSpend = income
            .subtract(fixedCosts)
            .subtract(spent)
            .divide(
                BigDecimal.valueOf(weeksLeft),
                2,  // 2 decimal places
                RoundingMode.HALF_UP
            );
        
        return safeToSpend;
    }
}
```

### API Response (JSON Serialization)

```java
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
    
    @GetMapping("/{id}")
    public ResponseEntity<TransactionDto> getTransaction(@PathVariable Long id) {
        Transaction transaction = transactionRepository.findById(id).orElseThrow();
        
        // Convert to DTO with BigDecimal as String (JSON safety)
        TransactionDto dto = new TransactionDto();
        dto.setAmount(transaction.getAmount().toPlainString());  // "125.50" not "1.2550E+2"
        
        return ResponseEntity.ok(dto);
    }
}

@Data
public class TransactionDto {
    private Long id;
    private LocalDate date;
    private String recipient;
    private String amount;  // ✅ String for JSON precision
    private String category;
}
```

### JSON Configuration (Spring Boot)

```java
@Configuration
public class JsonConfig {
    
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // ✅ Serialize BigDecimal as String (not Number)
        SimpleModule module = new SimpleModule();
        module.addSerializer(BigDecimal.class, new JsonSerializer<BigDecimal>() {
            @Override
            public void serialize(BigDecimal value, JsonGenerator gen, 
                                SerializerProvider provider) throws IOException {
                gen.writeString(value.toPlainString());
            }
        });
        
        mapper.registerModule(module);
        return mapper;
    }
}
```

### Testing (Precision Verification)

```java
@SpringBootTest
public class TransactionServiceTest {
    
    @Autowired
    private TransactionService transactionService;
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    @Test
    public void testSumAccuracy() {
        // Create transactions
        transactionRepository.save(
            createTransaction(new BigDecimal("125.50"))
        );
        transactionRepository.save(
            createTransaction(new BigDecimal("45.20"))
        );
        transactionRepository.save(
            createTransaction(new BigDecimal("99.30"))
        );
        
        // Sum must be exact
        BigDecimal total = transactionService.getTotalByCategory(
            userId, "Shopping"
        );
        
        assertEquals(
            new BigDecimal("270.00"),
            total,
            "Total must be exactly 270.00, not 269.9999..."
        );
    }
    
    @Test
    public void testSafeToSpendRounding() {
        // Setup: income 5000, fixed 1500, spent 2000, weeks 4
        // Expected: (5000 - 1500 - 2000) / 4 = 1500 / 4 = 375.00
        
        BigDecimal safeToSpend = transactionService.calculateSafeToSpend(userId);
        
        assertEquals(
            new BigDecimal("375.00"),
            safeToSpend,
            "Safe-to-Spend must round correctly"
        );
    }
    
    @Test
    public void testDivisionRounding() {
        // Division might result in more than 2 decimal places
        BigDecimal result = new BigDecimal("100.00")
            .divide(
                new BigDecimal("3"),
                2,  // 2 decimal places
                RoundingMode.HALF_UP
            );
        
        // 100 / 3 = 33.33 (rounded, not 33.333333...)
        assertEquals(new BigDecimal("33.33"), result);
    }
}
```

---

## Database Schema

```sql
-- SQLite
CREATE TABLE transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    date DATE NOT NULL,
    recipient TEXT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,  -- ✅ DECIMAL, not FLOAT
    category TEXT NOT NULL,
    created_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE fixed_costs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    description TEXT,
    amount DECIMAL(10, 2) NOT NULL,  -- ✅ DECIMAL
    interval TEXT NOT NULL,  -- MONTHLY, QUARTERLY, YEARLY
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    email TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    monthly_income DECIMAL(10, 2),  -- ✅ DECIMAL or NULL
    created_at TIMESTAMP
);
```

---

## Consequences

### ✅ Positive

- **Accuracy:** Exact decimal arithmetic; no rounding errors
- **Compliance:** nDSG + Financial regulations require precise calculations
- **Auditability:** Every CHF-Cent is trackable (no hidden rounding)
- **User Trust:** Safe-to-Spend = what User sees is what they get
- **Database Safety:** DECIMAL type in SQLite preserves precision

### ⚠️ Negative

- **Performance:** BigDecimal slower than double (non-critical for BudgetBuddy)
- **Memory:** BigDecimal objects have overhead (but negligible for ~1M transactions)
- **Complexity:** More verbose code (`.add()` instead of `+`)
- **JSON Serialization:** Default JSON serialization of BigDecimal is ambiguous

### 🔄 Mitigations

| Problem | Mitigation |
|---------|-----------|
| **Performance** | Use long (cents) only if millions of transactions/second needed (unlikely). |
| **Memory** | BigDecimal overhead ~200 bytes per object; 1M transactions ≈ 200 MB (acceptable). |
| **Code Verbosity** | Create helper methods (e.g., `add(x, y)` instead of `x.add(y)`). |
| **JSON Serialization** | Serialize as String (e.g., "125.50") not Number. Custom ObjectMapper. |

---

## Alternatives Considered

### ⚠️ Option 1: double (IEEE 754 Floating-Point)

**Entscheidung:** Abgelehnt

**Begründung:**
```java
// ❌ Double can't represent 0.1, 0.2, 0.3 exactly
System.out.println(0.1 + 0.2 == 0.3);  // false!
System.out.println(0.1 + 0.2);  // 0.30000000000000004

// Für Finanzen: nicht akzeptabel!
```

### ⚠️ Option 2: long (Cents)

**Entscheidung:** Abgelehnt (overcomplicates code)

**Begründung:**
```java
// Mathematically sound (integer arithmetic), but:
long amountInCents = 12550;  // CHF 125.50

// Every operation needs conversion
BigDecimal display = BigDecimal.valueOf(amountInCents)
    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

// Readability: worse than BigDecimal
// Use-cases: only if millions of transactions/sec (not BudgetBuddy)
```

### ✅ Option 3: Joda-Money / Moneta (Wrapper)

**Entscheidung:** Abgelehnt (unnecessary for MVP)

**Begründung:**
```java
// Joda-Money wraps BigDecimal + Currency
Money amount = Money.of(CurrencyUnit.CHF, BigDecimal.valueOf(125.50));

// Nicer API, but:
// - Extra dependency
// - Not needed for single-currency app (only CHF)
// - BigDecimal directly is simpler
```

---

## Related Decisions

- **ADR-1:** Java + Spring Boot (BigDecimal native support)
- **ADR-5:** SQLite with DECIMAL columns

---

## Standards & References

- [JSR 354 — Money and Currency API](https://javamoney.github.io/)
- [IEEE 754 Floating-Point Limitations](https://en.wikipedia.org/wiki/Double-precision_floating-point_format)
- [Java BigDecimal Documentation](https://docs.oracle.com/javase/8/docs/api/java/math/BigDecimal.html)
- [Financial Regulations — Swiss nDSG](https://www.edoeb.admin.ch/)
- CLAUDE.md — Technology Stack

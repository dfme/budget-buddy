# ADR-7: JWT (Stateless) Authentication

**Status:** Accepted (locked)  
**Entscheidung vom:** 2026-05-27  
**Betroffen:** User Authentication & Session Management

---

## Context

BudgetBuddy benötigt ein Authentifizierungs-System für:

- User Registration (Email + Password)
- Login (Email + Password → Token)
- Protected API Endpoints (nur eingeloggte User)
- Logout (Client-seitig)
- Token Refresh (später)

**Anforderungen:**
- Stateless (kein Session-Table in DB nötig)
- Sicher (bcrypt für Passwords, HS256 für Token)
- Einfach (keine komplexen OAuth-Flows für MVP)
- Frontend-friendly (Angular kann Bearer Token in Headers setzen)

### Optionen

1. **JWT (JSON Web Token)** — Stateless, Token-basiert
2. **Session (Server-Side)** — Session-Store in DB, Cookie-basiert
3. **OAuth 2.0** — Delegated auth (Google, GitHub login)
4. **API Key** — Static token (nicht für interactive User)
5. **Kerberos** — Enterprise-only

---

## Decision

**JWT (JSON Web Token) mit HS256 Signing**

```
Registration Flow:
1. POST /api/v1/auth/register
   { "email": "lara@example.com", "password": "SecurePass123" }
   ↓
2. Backend validates & hashes password (bcrypt)
   ↓
3. Response: { "userId": 123, "email": "lara@example.com" }

Login Flow:
1. POST /api/v1/auth/login
   { "email": "lara@example.com", "password": "SecurePass123" }
   ↓
2. Backend validates credentials
   ↓
3. Response: { "token": "eyJhbGci..." (JWT), "expiresIn": 3600 }

Protected Request Flow:
1. GET /api/v1/dashboard
   Headers: { Authorization: "Bearer eyJhbGci..." }
   ↓
2. Backend validates JWT signature + expiry
   ↓
3. Extract userId from token → Return dashboard data for that user

Logout Flow:
1. DELETE /api/v1/auth/logout
   Headers: { Authorization: "Bearer eyJhbGci..." }
   ↓
2. Frontend deletes token from localStorage
   ↓
3. Backend doesn't store anything (stateless)
```

### JWT Payload Example

```json
{
  "header": {
    "alg": "HS256",
    "typ": "JWT"
  },
  "payload": {
    "sub": "123",  // userId (subject)
    "email": "lara@example.com",
    "iat": 1704067200,  // issued at (Unix timestamp)
    "exp": 1704070800   // expires at (Unix timestamp, +1 hour)
  },
  "signature": "HMACSHA256(base64(header) + '.' + base64(payload), SECRET_KEY)"
}
```

### Tech Stack

| Component | Technology | Library |
|-----------|-----------|---------|
| **JWT Creation/Validation** | JJWT | `io.jsonwebtoken:jjwt-api:0.12.x` |
| **HMAC-SHA256 Signing** | JJWT (with crypto support) | `io.jsonwebtoken:jjwt-impl:0.12.x` |
| **Crypto Provider** | Bouncy Castle | `org.bouncycastle:bcprov-jdk15on` (or native Java crypto) |
| **Password Hashing** | BCrypt | Spring Security's `BCryptPasswordEncoder` |
| **Spring Integration** | Spring Security 6.5.x | `spring-security-jwt` (or native JJWT) |

---

## Rationale

| Kriterium | JWT | Session | OAuth 2.0 | API Key |
|-----------|-----|---------|-----------|---------|
| **Statefulness** | ✅ Stateless | ❌ Stateful (DB) | ⚠️ Hybrid | ✅ Stateless |
| **Scalability** | ✅ Infinite (no DB) | ❌ Session bottleneck | ⚠️ Depends on Provider | ✅ Infinite |
| **Mobile-Ready** | ✅ Native Bearer token | ⚠️ Requires cookies | ⚠️ Complex | ⚠️ Static token risky |
| **Logout Instant** | ❌ Token valid until expiry | ✅ Instant invalidation | ⚠️ Depends on Provider | ⚠️ Token can't be revoked |
| **Refresh Strategy** | ✅ Refresh token rotation | ✅ Session extend | ✅ Refresh token | ❌ N/A |
| **CSRF Attack** | ✅ Protected (Bearer header) | ❌ Vulnerable (if cookies) | ✅ Protected | ✅ Protected |
| **Bandwidth** | ⚠️ Token sent every request | ✅ Just session ID | ⚠️ OAuth tokens large | ✅ Compact |
| **Browser Native** | ⚠️ Requires JS (localStorage) | ✅ Cookie native | ⚠️ Redirects | ✅ Header easy |
| **Complexity** | ✅ Simple | ✅ Simple | ❌ Complex | ✅ Simple |
| **User Privacy** | ✅ Transparent (JWT readable) | ✅ Server-stored | ✅ Delegated | ⚠️ No provider insight |

**Konkrete Vorteile für BudgetBuddy:**

1. **Stateless:** Keine Session-Tabelle in SQLite nötig
   - Saves DB space + reduces write pressure
   - Easier horizontal scaling (later)

2. **No Logout Delay:** Even though logout is "client-side only", token expires in 1 hour
   - User deletes token from localStorage → logged out instantly
   - After 1 hour, token invalid anyway (token expiry)

3. **Mobile-Friendly:** Native Bearer Token pattern
   - Android/iOS native app can use same API
   - No cookie hassle (cross-origin, SameSite, etc.)

4. **Simple for MVP:** No OAuth provider integration needed
   - No dependency on Google/GitHub authentication
   - Full control over registration/login flow

5. **Spring Security Native:** Spring Boot 3.5.x has first-class JWT support
   ```java
   @Bean
   public SecurityFilterChain securityFilterChain(HttpSecurity http) {
       http.oauth2ResourceServer()
           .jwt(); // Validates JWT automatically
       return http.build();
   }
   ```

---

## Implementation

### Backend (Java/Spring Boot)

```java
// User Entity (simplified)
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    @Column(nullable = false)
    private String passwordHash;  // bcrypt
    
    private LocalDateTime createdAt;
}

// JWT Service
@Service
public class JwtService {
    
    private static final String SECRET_KEY = "..."; // 256-bit secret from env var
    private static final long EXPIRY_MS = 3600_000; // 1 hour
    
    public String generateToken(User user) {
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("email", user.getEmail())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + EXPIRY_MS))
            .signWith(SignatureAlgorithm.HS256, SECRET_KEY.getBytes())
            .compact();
    }
    
    public Long extractUserId(String token) {
        return Long.parseLong(
            Jwts.parser()
                .setSigningKey(SECRET_KEY.getBytes())
                .parseClaimsJws(token)
                .getBody()
                .getSubject()
        );
    }
    
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .setSigningKey(SECRET_KEY.getBytes())
                .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException | MalformedJwtException | SignatureException e) {
            return false;
        }
    }
}

// Auth Controller
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private JwtService jwtService;
    
    @Autowired
    private PasswordEncoder passwordEncoder; // BCryptPasswordEncoder
    
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            return ResponseEntity.badRequest()
                .body("Email already registered");
        }
        
        User user = new User();
        user.setEmail(req.getEmail());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        userRepository.save(user);
        
        return ResponseEntity.ok("User registered successfully");
    }
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
            .orElse(null);
        
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(401)
                .body("Invalid email or password");
        }
        
        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(new LoginResponse(token, 3600));
    }
    
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> logout() {
        // Stateless: Just tell client to delete token
        return ResponseEntity.ok("Logged out successfully");
    }
}

// HTTP Interceptor (extracts userId from JWT)
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Autowired
    private JwtService jwtService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest req, 
                                   HttpServletResponse res,
                                   FilterChain chain) throws ServletException, IOException {
        String token = extractTokenFromHeader(req);
        
        if (token != null && jwtService.validateToken(token)) {
            Long userId = jwtService.extractUserId(token);
            // Set SecurityContext so @PreAuthorize works
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of())
            );
        }
        
        chain.doFilter(req, res);
    }
    
    private String extractTokenFromHeader(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
```

### Frontend (Angular)

```typescript
// auth.service.ts
@Injectable({ providedIn: 'root' })
export class AuthService {
    constructor(private http: HttpClient) {}
    
    login(email: string, password: string): Observable<LoginResponse> {
        return this.http.post<LoginResponse>('/api/v1/auth/login', { email, password })
            .pipe(
                tap(response => {
                    localStorage.setItem('jwt_token', response.token);
                })
            );
    }
    
    logout(): void {
        localStorage.removeItem('jwt_token');
    }
    
    getToken(): string | null {
        return localStorage.getItem('jwt_token');
    }
    
    isLoggedIn(): boolean {
        const token = this.getToken();
        // Check if token exists and not expired (can decode without signature)
        return !!token && !this.isTokenExpired(token);
    }
    
    private isTokenExpired(token: string): boolean {
        const decoded = JSON.parse(atob(token.split('.')[1]));
        return decoded.exp * 1000 < Date.now();
    }
}

// http.interceptor.ts
@Injectable()
export class JwtInterceptor implements HttpInterceptor {
    constructor(private authService: AuthService) {}
    
    intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        const token = this.authService.getToken();
        
        if (token) {
            req = req.clone({
                setHeaders: {
                    Authorization: `Bearer ${token}`
                }
            });
        }
        
        return next.handle(req);
    }
}

// app.config.ts
export const appConfig: ApplicationConfig = {
    providers: [
        provideHttpClient(
            withInterceptors([jwtInterceptor])
        ),
        // ... other providers
    ]
};
```

---

## Consequences

### ✅ Positive

- **Stateless:** No session table in DB; scales horizontally
- **Mobile-Ready:** Native Bearer Token pattern; no cookie hassle
- **Simple:** JWT standard; well-documented; JJWT mature library
- **Secure:** HS256 + bcrypt (if combined with https)
- **Fast:** No DB lookup on every request (token validation is cryptographic only)

### ⚠️ Negative (and Mitigations)

| Problem | Mitigation |
|---------|-----------|
| **Token Valid Until Expiry** | Set short expiry (1 hour). If compromised, valid for 1 hour only. |
| **No Instant Logout** | Use token blacklist (later) if instant logout needed. For MVP: 1 hour is ok. |
| **Client-Side Token Storage** | localStorage is XSS-vulnerable. Use httpOnly cookies (but complicates CORS). For MVP: localStorage ok + HTTPS. |
| **Token Revocation** | Can't revoke token before expiry without blacklist DB. For MVP: not needed. |
| **Refresh Token Rotation** | Implement later if needed. For MVP: just re-login. |

---

## Alternatives Considered

### ⚠️ Option 1: Session (Server-Side)

**Entscheidung:** Abgelehnt

**Begründung:**
- Requires session-table in SQLite:
  ```sql
  CREATE TABLE sessions (
      session_id TEXT PRIMARY KEY,
      user_id BIGINT,
      created_at TIMESTAMP,
      expires_at TIMESTAMP
  );
  ```
- Every login creates DB write
- Every logout invalidates session immediately (DB write)
- SQLite write-pressure increases with user count
- For 1.000 concurrent users = bottleneck

**Aber:** If later need instant logout = könnte to session wechseln (1-2 Sprints)

### ❌ Option 2: OAuth 2.0 (Google Login)

**Entscheidung:** Abgelehnt (MVP-Scope)

**Begründung:**
- Adds complexity (OAuth provider setup, redirect flows)
- Not needed for MVP (simple email/password)
- Can add later as "Login with Google" option
- Für junge Zielgruppe (Studierende) könnte später sinnvoll sein

### ✅ Option 3: Hybrid (JWT + Refresh Token)

**Entscheidung:** Akzeptiert (für Phase 2)

**Begründung:**
- Jetzt (MVP): Short-lived JWT (1 hour)
- Später: Add refresh token rotation
  ```
  POST /api/v1/auth/refresh
  Body: { refreshToken }
  → Returns new access token (short-lived)
  ```
- Refresh token stored in httpOnly cookie (longer expiry, e.g., 30 days)

---

## Security Considerations

### Password Hashing

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // 12 rounds (slow, secure)
    }
}
```

- **Never store plaintext passwords**
- **Never use SHA-256** for passwords (too fast, GPU-crackable)
- **Use bcrypt** (12 rounds = ~100ms per hash — ok for login, resistant to GPU)

### JWT Secret Key

```properties
# application.properties
jwt.secret=${JWT_SECRET}  # 256-bit secret from environment
jwt.expiration=3600000    # 1 hour in milliseconds
```

- Secret should be:
  - ≥256 bits (32 bytes)
  - Random (not hardcoded)
  - Stored in environment variable (not in code)
  - Rotatable (if compromised)

### HTTPS Only

- JWT tokens must be transmitted over HTTPS
- Without HTTPS, Bearer token can be intercepted (Man-in-the-Middle)

---

## Related Decisions

- **ADR-0:** Frontend-Backend-Trennung (JWT is stateless → perfect for SPA)
- **ADR-1:** Java + Spring Boot (native JWT support)
- **ADR-2:** Angular Frontend (localStorage + Bearer header)

---

## Testing

```java
@SpringBootTest
public class JwtServiceTest {
    
    @Autowired
    private JwtService jwtService;
    
    @Test
    public void testGenerateAndValidateToken() {
        User user = new User();
        user.setId(123L);
        user.setEmail("test@example.com");
        
        String token = jwtService.generateToken(user);
        assertTrue(jwtService.validateToken(token));
        assertEquals(123L, jwtService.extractUserId(token));
    }
    
    @Test
    public void testExpiredTokenInvalid() throws Exception {
        // Create token with expired date
        String token = Jwts.builder()
            .subject("123")
            .expiration(new Date(System.currentTimeMillis() - 1000)) // 1 second ago
            .signWith(SignatureAlgorithm.HS256, SECRET_KEY.getBytes())
            .compact();
        
        assertFalse(jwtService.validateToken(token));
    }
}
```

---

## References

- [JWT.io Official](https://jwt.io/)
- [JJWT Library Documentation](https://github.com/jwtk/jjwt)
- [Spring Security OAuth2 Resource Server](https://spring.io/projects/spring-security)
- [BCrypt Password Hashing](https://en.wikipedia.org/wiki/Bcrypt)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- CLAUDE.md — Auth Decision: JWT

# Code Review — UniMove Backend

**Data:** 2026-05-19
**Escopo:** branch `main` (commits até `5c4f9a8`)
**Domínios cobertos:** `shared`, `domain.user`, `domain.maps` (mais migration `V1`).
**Não revisado (ainda não existe):** `domain.ride`, `domain.payment`, endpoints de admin/driver online/offline, `/drivers/me/...`, `/rides/*`.

---

## TL;DR

Base sólida, bem alinhada com a CLAUDE.md. A camada de auth + JWT + maps/cache está coerente e razoavelmente testada. Antes de avançar para `domain.ride` (que é o coração do MVP), vale corrigir alguns pontos pequenos mas que vão poluir o resto se ficarem pra depois — em especial:

- redundância de FK na entidade `Driver` (campo `userId` duplicado com `@MapsId`);
- método `findByUserId` redundante em `DriverRepository`;
- validação de `cidade` que aceita string que vira `""` depois do `CityNormalizer`;
- ausência de fail-fast para `JWT_SECRET` default em produção.

Nada disso é bloqueante, mas é mais barato resolver agora.

---

## 1. Pontos fortes

| Tema | Observação |
|---|---|
| Estrutura modular | `domain.maps` expõe só a interface `MapsService` (impl é package-private). Exatamente o que a CLAUDE.md pede. |
| `GlobalExceptionHandler` | Cobre validação, credenciais, lock otimista (com a mensagem **exata** do spec), `DataIntegrityViolation`, `BusinessException` e fallback genérico. Muito bom. |
| `ApiError` | Record imutável, `@JsonInclude(NON_NULL)` para esconder `fieldErrors` quando vazio, factory methods limpos. |
| `RouteHasher` | Arredondamento via `BigDecimal.setScale(4, HALF_UP)` antes do SHA-256 — evita armadilha de `Math.round` com double. Testes de propriedade cobrem idempotência, sensibilidade à 4ª casa, direção (ida≠volta) e formato hex. |
| `OsrmMapsService` | Cache-first, timeouts de connect/response configurados no `HttpClient`, fallback para `MapsUnavailableException` (503), race no insert do cache é engolida via `DataIntegrityViolationException`. Bem pensado. |
| `JwtService` | Usa `Keys.hmacShaKeyFor`, claims explícitas (`role`, `cidade`, `email`), `Instant` em vez de `LocalDateTime`. Testes cobrem round-trip, secret diferente, expirado e malformado. |
| `JwtProperties` | `@Validated` + `@Min(60_000)` evita configurar token com validade absurdamente curta. |
| `RegisterRequest` | `@AssertTrue isVehicleConsistent()` garante que `MOTORISTA` informa veículo e que outros papéis **não** informam. Validação no DTO em vez de espalhar no service. |
| `CityNormalizer` | NFD + diacríticos + `[^a-z0-9]+ → "-"` + trim de hífens nas pontas. Null-safe e bem testado. |
| `application.yml` | `ddl-auto: validate`, `open-in-view: false`, `jdbc.time_zone: UTC`, Flyway no padrão. Sem armadilhas. |
| `SecurityConfig` | Stateless, CSRF off (correto para JWT), `EnableMethodSecurity` pronto para `@PreAuthorize` nos próximos controllers, custom `authenticationEntryPoint`/`accessDeniedHandler` retornam o mesmo `ApiError`. |
| Migration V1 | Índice parcial `idx_rides_mural ON rides (status, cidade) WHERE status = 'AVAILABLE_IN_MURAL'` é exatamente o índice certo para o mural de alta frequência. `BIGSERIAL` no `route_cache.id`, `NUMERIC(10,3)` para distância, `TIMESTAMPTZ` em todos os timestamps. |

---

## 2. Issues a resolver antes de avançar para `domain.ride`

### 2.1 [MÉDIA] `Driver` duplica a FK do user (`@MapsId` + campo `userId`)

`Driver.java`:

```java
@Id @Column(name = "user_id") private UUID userId;

@OneToOne(...) @MapsId @JoinColumn(name = "user_id") private User user;
```

`@MapsId` já sincroniza o `userId` automaticamente a partir de `user.id`. Manter os dois campos abre risco:

- Se algum dia alguém chamar `driver.setUserId(x)` sem setar `user`, o estado fica inconsistente até o `flush`.
- Confunde quem vai escrever os próximos repositories (`findByUserId` vs `findById`, ver 2.2).

**Recomendação:** remover o `private UUID userId;` e deixar só o relacionamento com `@MapsId`. Lombok continua gerando getters/setters normais; pra ler o id basta `driver.getUser().getId()` (ou expor um helper `getUserId()` manual).

### 2.2 [BAIXA] `DriverRepository.findByUserId(UUID)` é redundante

Como `Driver.@Id == user_id`, `findById(userId)` faz exatamente a mesma coisa. Mantendo o método, o próximo dev fica em dúvida sobre qual usar e cria mais um redundante. **Remover.**

### 2.3 [MÉDIA] `cidade` pode chegar ao banco como string vazia

`RegisterRequest` valida `@NotBlank @Size(max=80)` **antes** da normalização. `AuthService.register`:

```java
user.setCidade(CityNormalizer.normalize(req.cidade()));
```

`CityNormalizer.normalize("---")` retorna `""`. O DB tem `NOT NULL` mas **não** tem `CHECK (length(cidade) > 0)`. Resultado: motorista cadastrado com `cidade = ""` quebra o filtro do mural silenciosamente.

**Recomendação (escolher uma):**

1. Adicionar `CHECK (cidade <> '')` em migration `V2`.
2. Validar pós-normalização no `AuthService`: lançar `BusinessException(BAD_REQUEST, ...)` se normalizada for vazia.
3. Movimentar a normalização para o getter do DTO ou para um validator customizado `@CityName`.

Eu iria de **2** agora (uma linha) + **1** quando criar V2 por outro motivo.

### 2.4 [MÉDIA] Sem fail-fast para `JWT_SECRET` default em prod

`application.yml`:

```yaml
secret: ${JWT_SECRET:dev-secret-NAO-USE-EM-PRODUCAO-troque-via-env-var}
```

Se subir em produção sem definir `JWT_SECRET`, a app inicia com o secret de dev — e ninguém percebe até alguém perceber. Sugestão: criar um `@PostConstruct` em `JwtService` (ou um `EnvironmentPostProcessor`) que faça, quando `spring.profiles.active=prod`, `Assert.isTrue(!secret.startsWith("dev-secret"))`. Custa 5 linhas, fecha um buraco real.

### 2.5 [BAIXA] `EmailAlreadyUsedException` ecoa o e-mail no `message`

```java
"Já existe um cadastro com o email '" + email + "'."
```

Isso é enumeração de usuários explícita via 409. O endpoint `/auth/register` já distingue entre 201 e 409, então o atacante consegue checar emails de qualquer jeito — **mas** ecoar o e-mail também aparece em logs/clients de cliente, sem ganho de UX. Mensagem genérica (`"E-mail já cadastrado."`) é melhor.

### 2.6 [BAIXA] `vehiclePlate` não passa por `trim()`

`AuthService.register`:

```java
driver.setVehiclePlate(req.vehiclePlate().toUpperCase());
```

Se usuário enviar `" abc1234 "`, persiste `" ABC1234 "`. Adicionar `.trim()` antes de `.toUpperCase()`.

### 2.7 [INFO] `OsrmMapsService` — `BLOCK_TIMEOUT (6s)` é mais curto que `connect(3s) + response(5s) = 8s`

Funciona — o `.block(6s)` corta antes — mas a intenção fica ambígua. Ou diminui o `responseTimeout` para 3s, ou aumenta o `BLOCK_TIMEOUT` para 9s. Documentar qual é o budget total real para uma chamada de mapa ajuda os próximos endpoints (`POST /rides` e cálculo de preço) a dimensionar seus próprios timeouts.

---

## 3. Coisas que NÃO são problema, mas vale ter consciência

- **`User.@PrePersist` define `id` e `createdAt`** apesar de o schema ter `DEFAULT gen_random_uuid()` / `DEFAULT NOW()`. Redundância inofensiva — JPA sempre manda valor, o DEFAULT do DB nunca é exercitado. Coerente porque `ddl-auto: validate` exige que a coluna seja não-nula na inserção.
- **`OsrmMapsService` faz chamada bloqueante com `WebClient`** (`.block(...)`). Intencional — o resto do projeto é MVC clássico. Só não use isso como precedente para criar handlers reativos misturados.
- **`AuthControllerWebMvcTest` mocka `JwtAuthenticationFilter` + `JwtService`** e usa `addFilters = false`. Tudo bem — o teste foca no controller. Cobertura de cenário 409 (e-mail duplicado) ainda não existe; vale adicionar quando mexer no `AuthService` (item 2.5).
- **Sem `AuthServiceIntegrationTest`.** Faz sentido só introduzir quando tiver Testcontainers funcionando localmente — e a memória já registra que o Docker Desktop dessa máquina tem o bug do socket proxy. Não force.

---

## 4. Sugestão de ordem para os próximos passos

1. Aplicar as correções 2.1–2.6 (1 commit, ~30 min).
2. Começar `domain.ride` pelas **entidades + migration V2** (Ride com `@Version`, índices já listados na V1 inclusive). A V1 já cobre o schema da `rides`, então provavelmente nem precisa de V2 — só validar com `mvn flyway:info`.
3. Implementar `RideService.create()` integrando `MapsService.route(...)` + fórmula `5.50 + 2.10*km + 0.20*min` com `BigDecimal` (cuidado com `MathContext` ao multiplicar). Cobrir o cálculo com teste unitário antes de subir o controller.
4. Mural (`GET /rides/mural`) com projeção DTO direto no repository, não `fetch join`.
5. Aceite (`POST /rides/{id}/accept`) — aqui o lock otimista entra em ação. O `GlobalExceptionHandler` já está pronto pra 409.
6. Só **depois** disso, `driver-location` (com Haversine — fórmula simples, manter pure-function num util) e `domain.payment` (payload Pix fictício).

---

## 5. Métricas rápidas

- **Arquivos Java em `src/main`:** 25
- **Testes:** 5 arquivos, ~25 casos (boa cobertura de borda no `JwtService` e `RouteHasher`)
- **Migrations:** 1 (`V1__init_schema.sql` — cobre todas as tabelas do MVP)
- **Dívida técnica catalogada acima:** 7 itens, todos resolvíveis em <1h no total.

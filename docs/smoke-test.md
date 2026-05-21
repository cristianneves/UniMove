# UniMove — Smoke test ponta-a-ponta

> Checklist manual para validar que o backend esta em estado MVP-funcional antes de plugar o app Flutter ou subir um build.
> Tempo estimado: **~10 minutos**. Pre-requisito: Postgres rodando e backend em `localhost:8080`.
> Use `docs/api.http` no IntelliJ HTTP Client ou VSCode REST Client para executar os passos. Pode tambem usar curl/Postman.

---

## 0. Setup

- [ ] `mvn spring-boot:run` ou `java -jar target/unimove-backend-*.jar`
- [ ] Acessar `http://localhost:8080/actuator/health` → `{"status":"UP"}`
- [ ] Acessar `http://localhost:8080/swagger-ui.html` → Swagger UI abre com endpoints listados
- [ ] Acessar `http://localhost:8080/v3/api-docs` → JSON do contrato OpenAPI

Se algum item acima falhar, **PARE** — banco offline ou Flyway nao aplicou.

---

## 1. Cadastro e autenticacao

- [ ] `POST /auth/register` com role=`PASSAGEIRO` → **201 Created** + JWT no campo `token`
- [ ] `POST /auth/register` com role=`MOTORISTA` (com `vehicleType` e `vehiclePlate`) → **201** + JWT
- [ ] `POST /auth/register` com role=`MOTORISTA` sem `vehicleType` → **400** com mensagem de `isVehicleConsistent`
- [ ] `POST /auth/register` com email ja usado → **409**
- [ ] `POST /auth/login` com senha errada → **401**
- [ ] `POST /auth/login` admin (seed) com `admin@unimove.local` / `admin123` → **200** + JWT

---

## 2. Aprovacao do motorista (ADMIN)

- [ ] `GET /admin/drivers/pending` (token admin) → lista contendo o motorista criado em §1
- [ ] `GET /admin/drivers/pending` (token passageiro) → **403**
- [ ] `POST /admin/drivers/{id}/approve` (token admin) → **200** com `approved=true`

---

## 3. Online/offline (MOTORISTA)

- [ ] Antes de online: `GET /rides/mural` (motorista) → **400/403** com `DriverOfflineException`
- [ ] `POST /drivers/me/online` → **200** com `online=true`
- [ ] `GET /rides/mural` → **200** com lista vazia (ainda nao criamos ride)
- [ ] `POST /drivers/me/offline` → **200** com `online=false`
- [ ] Voltar ao online para os proximos passos

---

## 4. Ciclo da corrida — happy path

- [ ] `POST /rides` (passageiro, coordenadas validas) → **201** com `status=PENDING_PAYMENT`, `preco` calculado no backend (nunca confiar no front)
- [ ] `POST /rides/{id}/confirm-payment {"method":"PIX"}` → **200** com `status=AVAILABLE_IN_MURAL` e `pixPayload` preenchido (BR Code simulado)
- [ ] `GET /rides/mural` (motorista da mesma cidade) → ride aparece
- [ ] `POST /rides/{id}/accept` → **200** com `status=DRIVER_EN_ROUTE` e `motoristaId` preenchido
- [ ] `PUT /rides/{id}/driver-location {"lat":-20.8090,"lng":-49.3720}` → **200** com `driverDistanceKm` retornado
- [ ] `GET /rides/{id}` (passageiro) → enxerga `driverCurrentLat/Lng` + `driverDistanceKm` (Haversine)
- [ ] `POST /rides/{id}/start` → **200** com `status=IN_PROGRESS` e `startedAt`
- [ ] `POST /rides/{id}/complete` → **200** com `status=COMPLETED` e `completedAt`

---

## 5. Concorrencia — aceite por dois motoristas

> Pre: criar segundo motorista (mesma cidade), aprovar, ficar online. Criar nova ride ate `AVAILABLE_IN_MURAL`.

- [ ] Disparar dois `POST /rides/{id}/accept` quase simultaneos (duas abas do IntelliJ HTTP Client funcionam)
- [ ] Um responde **200** com ride aceita; o outro responde **409 Conflict** com `"Esta corrida ja foi aceita por outro motorista."`
- [ ] Nunca os dois aceitam — protecao via `@Version`

---

## 6. Escopo por cidade

- [ ] Criar passageiro em `cidade="campinas"` e criar ride
- [ ] `GET /rides/mural` com motorista de `sao-jose-do-rio-preto` → ride de Campinas **NAO** aparece
- [ ] `POST /rides/{id}/accept` (motorista de cidade diferente) → **403** com `DriverCityMismatchException`

---

## 7. Cancelamento

- [ ] Passageiro cancela em `PENDING_PAYMENT` → **200** com `status=CANCELLED`, `cancelledBy=PASSAGEIRO`
- [ ] Passageiro cancela em `AVAILABLE_IN_MURAL` → **200**
- [ ] Passageiro cancela em `DRIVER_EN_ROUTE` → **200**
- [ ] Passageiro tenta cancelar em `IN_PROGRESS` → **409** `IllegalRideTransitionException`
- [ ] Motorista cancela em `DRIVER_EN_ROUTE` sem `reason` → **400** `MissingCancelReasonException`
- [ ] Motorista cancela em `DRIVER_EN_ROUTE` com `reason` → **200** com `cancelledBy=MOTORISTA`
- [ ] Motorista tenta cancelar em `IN_PROGRESS` → **409**

---

## 8. Polling do motorista

- [ ] `PUT /rides/{id}/driver-location` antes de `DRIVER_EN_ROUTE` (ride ainda em `AVAILABLE_IN_MURAL`) → **409** `LocationUpdateNotAllowedException`
- [ ] `PUT /rides/{id}/driver-location` em `IN_PROGRESS` → **200** com `driverDistanceKm` agora medindo distancia ate **destino** (nao mais origem)

---

## 9. Seguranca

- [ ] Endpoint protegido sem `Authorization` → **401** com body `ApiError`
- [ ] `Authorization: Bearer xpto` invalido → **401**
- [ ] Passageiro tentando `GET /rides/mural` → **403**
- [ ] Motorista tentando `POST /rides` → **403**
- [ ] Usuario nao-admin chamando `/admin/*` → **403**

---

## 10. Cache OSRM

- [ ] Criar ride com origem/destino X → primeira chamada vai ao OSRM (log `INFO` no console)
- [ ] Criar nova ride com **mesmos** lat/lng arredondados a 4 casas → log indica hit no `route_cache` (sem chamada HTTP saindo)
- [ ] `SELECT count(*) FROM route_cache;` no Postgres → cresce em 1 por rota distinta

---

## 11. Resultado

Se todos os boxes acima passarem, o backend esta **pronto para integracao com o frontend**:

- Contrato HTTP estavel e documentado (Swagger).
- Maquina de estados defensiva (transicoes invalidas viram 409).
- Concorrencia do mural resolvida.
- Calculo de tarifa, escopo por cidade e Haversine no backend.

Se algum falhar:

- **400/422 inesperado** → conferir validacoes nos records DTO.
- **500 com stack** → bug — abrir issue colando o trace.
- **OSRM 503** → API publica pode estar instavel; cache reduz incidencia, mas valida `OSRM_BASE_URL`.

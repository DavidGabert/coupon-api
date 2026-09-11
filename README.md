# Coupon API

API REST de cupons de desconto, construída em Java 17 / Spring Boot.
Implementa criação (`POST /coupon`) e exclusão lógica (`DELETE
/coupon/{id}`), e adiciona duas operações de leitura (`GET /coupon/{id}` e
`GET /coupon`) necessárias para verificar, via a própria API, que create e
delete funcionam como esperado. As regras de negócio (sanitização e
validação do código do cupom, valor mínimo de desconto, data de expiração,
unicidade de código entre cupons ativos, exclusão lógica) vivem encapsuladas
no domínio (`Coupon` / `CouponCode`), não na camada de service ou de
controller.

## Como rodar

### Local (sem Docker)

Requer Java 17+ e Maven (o wrapper `./mvnw` já cuida da versão do Maven).
O H2 é embarcado — não precisa de nenhuma configuração de banco.

```bash
./mvnw spring-boot:run
```

Por padrão o cache usa Redis (`spring.cache.type: redis`), então é preciso
ter um Redis acessível em `localhost:6379` — ou apontar para outro host/porta
via variáveis de ambiente `REDIS_HOST` / `REDIS_PORT`. Sem Redis alcançável
a aplicação não sobe (falha ao inicializar o `CacheManager`).

### Docker Compose

```bash
docker compose up --build
```

Sobe dois serviços: `app` (a API, porta `8080`) e `redis` (porta `6379`).
O `app` só inicia depois que o `healthcheck` do `redis` reporta saudável
(`depends_on: redis: condition: service_healthy`), evitando falha de
cold-start por tentar conectar no Redis antes dele estar pronto.

## Exemplos de uso (curl)

Os exemplos abaixo foram capturados de uma execução real da aplicação
localmente (`./mvnw spring-boot:run`, com `spring.cache.type=simple` — cache
em memória usado apenas para poder rodar este teste manual num ambiente sem
Redis disponível; o formato da resposta JSON de cada endpoint é idêntico
independente do backend de cache por trás do `@Cacheable`). Um ID de cupom
`1` recém-criado é reutilizado ao longo dos exemplos de GET/DELETE abaixo.

### Criar cupom — `POST /coupon`

```bash
curl -i -X POST http://localhost:8080/coupon \
  -H "Content-Type: application/json" \
  -d '{
    "code": "AB-12#34",
    "description": "10% off on all electronics",
    "discountValue": 10.00,
    "expirationDate": "2027-12-31T23:59:59",
    "published": true
  }'
```

```
HTTP/1.1 201
Content-Type: application/json

{"id":1,"code":"AB1234","description":"10% off on all electronics","discountValue":10.00,"expirationDate":"2027-12-31T23:59:59","published":true,"active":true,"createdAt":"2026-09-12T10:37:29.3763572","deletedAt":null}
```

Note que `code` chegou como `"AB-12#34"` e voltou como `"AB1234"` — os
caracteres especiais (`-`, `#`) foram removidos antes de salvar e retornar,
conforme a regra de sanitização do código.

### Buscar cupom por id — `GET /coupon/{id}`

```bash
curl -i http://localhost:8080/coupon/1
```

```
HTTP/1.1 200
Content-Type: application/json

{"id":1,"code":"AB1234","description":"10% off on all electronics","discountValue":10.00,"expirationDate":"2027-12-31T23:59:59","published":true,"active":true,"createdAt":"2026-09-12T10:37:29.376357","deletedAt":null}
```

### Listar cupons ativos — `GET /coupon`

```bash
curl -i http://localhost:8080/coupon
```

```
HTTP/1.1 200
Content-Type: application/json

[{"id":1,"code":"AB1234","description":"10% off on all electronics","discountValue":10.00,"expirationDate":"2027-12-31T23:59:59","published":true,"active":true,"createdAt":"2026-09-12T10:37:29.376357","deletedAt":null}]
```

### Excluir cupom (soft delete) — `DELETE /coupon/{id}`

```bash
curl -i -X DELETE http://localhost:8080/coupon/1
```

```
HTTP/1.1 204
```

O registro não é removido fisicamente — apenas `active` vira `false` e
`deletedAt` recebe o timestamp da exclusão. Uma busca subsequente pelo mesmo
id retorna 404:

```bash
curl -i http://localhost:8080/coupon/1
```

```
HTTP/1.1 404
Content-Type: application/json

{"status":404,"error":"Not Found","message":"Coupon not found: 1","timestamp":"2026-09-12T10:37:30.1795212"}
```

### Exemplo de erro 400 — código inválido após sanitização

```bash
curl -i -X POST http://localhost:8080/coupon \
  -H "Content-Type: application/json" \
  -d '{
    "code": "AB-12#3",
    "description": "10% off on all electronics",
    "discountValue": 10.00,
    "expirationDate": "2027-12-31T23:59:59",
    "published": true
  }'
```

```
HTTP/1.1 400
Content-Type: application/json

{"status":400,"error":"Bad Request","message":"Coupon code must have exactly 6 alphanumeric characters after removing special characters, got 5 from input: AB-12#3","timestamp":"2026-09-12T10:37:29.2377036"}
```

`"AB-12#3"` tem apenas 5 caracteres alfanuméricos (`AB123`) depois de remover
os especiais — como o resultado não tem exatamente 6 caracteres, a criação é
rejeitada em vez de truncada ou preenchida.

### Exemplo de erro 409 — código duplicado

Repetindo o `POST` acima com o mesmo `code` (`AB-12#34`, já ativo como
`AB1234` desde o primeiro exemplo):

```bash
curl -i -X POST http://localhost:8080/coupon \
  -H "Content-Type: application/json" \
  -d '{
    "code": "AB-12#34",
    "description": "Another electronics discount",
    "discountValue": 15.00,
    "expirationDate": "2027-06-30T23:59:59",
    "published": false
  }'
```

```
HTTP/1.1 409
Content-Type: application/json

{"status":409,"error":"Conflict","message":"Coupon code already in use: AB1234","timestamp":"2026-09-12T10:37:30.0521467"}
```

## Swagger

Com a aplicação rodando (local ou via Docker Compose):

```
http://localhost:8080/swagger-ui/index.html
```

(`http://localhost:8080/swagger-ui.html` também funciona — redireciona
automaticamente para a URL acima.)

## Rodando os testes

Testes obrigatórios — só precisam de H2, sem Docker:

```bash
./mvnw test
```

Cobre domínio (unitário puro), service (unitário, repositório mockado) e
controller/repositório (integração com Spring context real via `MockMvc` e
`@DataJpaTest`, ambos contra H2). Gate de cobertura de linha configurado em
80% via Jacoco (`./mvnw verify` falha o build abaixo disso).

Suítes opcionais de integração contra Redis e Postgres reais, via
Testcontainers — exigem Docker disponível e **não** rodam no `mvn test`
padrão nem contam para o gate de cobertura:

```bash
./mvnw verify -Pintegration-redis
./mvnw verify -Pintegration-postgres
```

## Decisões de design

### Sanitização e rejeição do `code`

A regra de negócio diz que caracteres especiais podem ser aceitos na criação, mas
precisam ser removidos antes de salvar/retornar, "garantindo o tamanho de 6
caracteres". A interpretação adotada aqui é estrita: removemos tudo que não
é alfanumérico e o resultado **precisa sobrar com exatamente 6 caracteres**;
se sobrar mais ou menos que isso, a criação é rejeitada com 400, em vez de
truncar ou completar o valor. A alternativa óbvia seria truncar para os 6
primeiros caracteres alfanuméricos — foi descartada porque isso faria
entradas diferentes (`"ABC123XYZ"` e `"ABC123999"`, por exemplo) colidirem
silenciosamente no mesmo código final depois do corte. Rejeitar força o
cliente a mandar um código cujo conteúdo alfanumérico já tem o tamanho
certo, usando os caracteres especiais apenas como "ruído" opcional na
entrada.

### `LocalDateTime` como extensão deliberada

A regra de negócio fala em "data de expiração" sem mencionar horário, o que
sugeriria `LocalDate` como leitura mais literal. A decisão foi usar
`LocalDateTime`, validado contra `LocalDateTime.now()` no momento da
criação — uma extensão consciente além do que o texto pede literalmente, não
uma "correção" de uma suposta imprecisão da regra original. O motivo é precisão
na fronteira de expiração: com `LocalDateTime`, um cupom que expira mais
tarde no mesmo dia é válido, e um timestamp já passado no dia de hoje é
rejeitado — sem deixar em aberto a pergunta "expira à meia-noite ou no fim
do dia?", que `LocalDate` sozinho não responde.

### Unicidade condicional via coluna-sombra

Não é uma regra de negócio explícita, mas decorre logicamente da regra de
Create: dois cupons ativos com o mesmo código tornariam a aplicação do
desconto ambígua, então isso é tratado como invariante mínima, não como
feature nova. A decisão foi tornar `code` único apenas entre cupons
**ativos** — o mesmo código pode ser reutilizado depois que o cupom original
é excluído (soft delete).

O problema é que a anotação `@Column(unique = true)` padrão do JPA só sabe
expressar unicidade sobre a coluna inteira — não há como dizer "único, mas
somente entre as linhas onde `active = true`" sem recorrer a um índice
parcial (`CREATE UNIQUE INDEX ... WHERE ...`), recurso que nem todo banco
relacional suporta da mesma forma e que o mapeamento padrão do JPA não gera
sozinho. A saída escolhida evita esse problema todo: uma coluna extra
nullable (`activeCode`) espelha
`code` enquanto o cupom segue ativo e é zerada para `NULL` assim que ele é
excluído, carregando ela mesma uma constraint `UNIQUE` comum. Como `NULL`
nunca é considerado igual a outro `NULL` numa constraint `UNIQUE` — isso é
comportamento padrão do SQL, não um truque de um banco específico —, a
tabela pode acumular quantos cupons excluídos quiser com o mesmo `code`
original sem nunca violar a constraint, e o mecanismo funciona igual em
qualquer banco relacional.

Sob concorrência, a constraint de banco é a fonte de verdade: o
`CouponService` faz uma checagem prévia (`existsActiveByCode`) só para
devolver um erro rápido no caso comum, mas o `save()` roda dentro de um
`try/catch` que traduz `DataIntegrityViolationException` em
`DuplicateCouponCodeException` — é esse catch, apoiado na constraint real do
banco, que garante a unicidade quando duas requisições concorrentes tentam
criar o mesmo código ao mesmo tempo.

### Domínio rico vs. hexagonal

A arquitetura ficou em camadas simples (`api` / `application` / `domain` /
`infrastructure`) com domínio rico, não hexagonal/ports-and-adapters
completo. Um dos requisitos do projeto pede "regras de negócio encapsuladas em
objetos de domínio" — um domínio rico (`Coupon.create()`, `Coupon.delete()`,
`CouponCode`) já atende isso diretamente. Uma arquitetura hexagonal completa
introduziria portas e adapters para uma única implementação de cada,
indireção difícil de justificar para 4 endpoints de CRUD ("por que essa
abstração existe, se só há uma implementação de cada porta?").

### Por que `MockMvc`, não `TestRestTemplate`, nos testes de controller

`MockMvc` sobe o `DispatcherServlet` de verdade e exercita serialização HTTP
real (JSON de request/response, status codes), mas roda na mesma thread do
teste. Isso importa porque `TestRestTemplate` bate num servidor embutido
rodando em thread separada, o que quebra o rollback transacional do Spring
Test (`@Transactional` no método de teste) — sem esse rollback, dados de um
teste vazam para o próximo (por exemplo, uma colisão de unicidade de `code`
causada por resíduo de um teste anterior). `MockMvc`, rodando na mesma
thread, preserva o rollback e mantém os testes isolados entre si.

### Por que Testcontainers-Redis/Postgres ficam fora do gate obrigatório

O requisito de banco é H2 em memória — nada obriga Docker disponível
para os testes obrigatórios passarem. Se `mvn test` dependesse de
Testcontainers, o gate de cobertura de 80% falharia inteiro numa máquina
sem Docker rodando, incluindo os testes de Create/Delete que são o núcleo
funcional da API. Por isso os testes de
cache no gate padrão usam `ConcurrentMapCacheManager` (cache em memória, a
mesma interface `CacheManager` do Spring), que já valida a lógica de
`@Cacheable`/`@CacheEvict` disparando corretamente sem depender de infra
externa. As suítes reais contra Redis e Postgres via Testcontainers ficam
disponíveis como profiles Maven opcionais (`-Pintegration-redis` /
`-Pintegration-postgres`), fora do `mvn test` padrão — a suíte de Postgres
em particular existe para validar, com múltiplas conexões reais, a alegação
de que "a constraint de banco é a fonte de verdade sob concorrência", algo
que H2 em memória não reproduz de forma confiável.

### Justificativa e limitação conhecida do cache Redis

Cache-aside em `GET /coupon/{id}` via `@Cacheable("coupons")`, com
`@CacheEvict("coupons")` no delete; `GET /coupon` (listagem) fica fora do
cache. A justificativa não é uma exigência comprovada por um requisito formal
de volume/escala — é uma suposição documentada: o padrão de acesso
típico de um domínio de cupons é leitura alta e escrita baixa (um cupom é
criado uma vez e potencialmente revalidado várias vezes por requisição de
checkout/precificação até expirar), o que justifica cache-aside mesmo sem
prova de carga real. A listagem fica de fora porque muda com
mais frequência relativa e os trade-offs de invalidação de coleção não
compensam para este escopo.

Limitação conhecida e aceita: a ordem entre `@CacheEvict` e o commit da
transação de delete não é garantida pela combinação dessas duas anotações —
teoricamente, uma leitura concorrente entre o evict e o commit poderia
repovoar o cache com o dado ainda não deletado. Para o escopo deste projeto
essa janela é aceita e documentada, não mitigada ativamente; em produção, a
mitigação seria acoplar o evict a um `TransactionSynchronization`
pós-commit.

### Construtor sem argumentos do JPA vs. domínio rico

O Hibernate exige um construtor sem argumentos para instanciar `Coupon` via
reflection — isso não contradiz o conceito de domínio rico neste projeto. O
construtor `protected Coupon()` é inacessível para código de aplicação fora
do pacote `domain`; o único caminho que o código de aplicação tem para
construir um `Coupon` é a fábrica validadora `Coupon.create(...)`, que
delega ao construtor privado com os campos já validados. O Hibernate usa o
construtor sem argumentos por reflection puramente para reidratar, a partir
do banco, linhas que já eram válidas no momento em que foram persistidas —
isso não abre uma janela para um `Coupon` inválido entrar na lógica de
aplicação, porque nenhum código de aplicação passa por esse construtor.

### Segurança

Autenticação e autorização estão fora do escopo deste projeto — nenhum
endpoint tem proteção de acesso implementada. Em produção, o próximo passo seria
adicionar autenticação via API key (se os consumidores forem serviços
internos/máquina-a-máquina) ou OAuth2/JWT (se houver usuários finais ou
múltiplos clientes com escopos de permissão distintos), dependendo de quem
efetivamente consome a API.

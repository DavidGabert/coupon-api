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
via variáveis de ambiente `REDIS_HOST` / `REDIS_PORT`. A aplicação **sobe
normalmente** mesmo sem Redis alcançável (a conexão é preguiçosa, só
acontece no primeiro acesso ao cache), mas o comportamento sem Redis é
assimétrico entre leitura e escrita de cache — confirmado ao vivo, com
stack trace real, rodando a aplicação sem Redis disponível:

- `GET /coupon/{id}` (`@Cacheable`) → **`500 Internal Server Error`** na
  primeira chamada. A leitura do cache é síncrona
  (`DefaultRedisCacheWriter.get()` bloqueia esperando resposta do Redis),
  então a falha de conexão (`RedisConnectionFailureException` ←
  `io.lettuce.core.RedisConnectionException: Connection refused`) propaga
  direto pro handler de exceção.
- `GET /coupon` (findAll, sem `@Cacheable`) → funciona normalmente.
- `DELETE /coupon/{id}` → responde `204` normalmente, o soft delete
  acontece de verdade no banco. Mas a eviction do cache falha **em
  silêncio**: `spring-data-redis` 4.1.1 executa `evict()` de forma
  assíncrona por padrão (fire-and-forget via `CompletableFuture`, sem
  `.join()`/tratamento de erro) quando escreve no Redis, diferente de
  `get()`, que é sempre síncrono. Sem Redis, a eviction falha e essa falha
  não aparece em log nenhum. Consequência real: se um `GET /coupon/{id}`
  cachear um valor com sucesso (Redis disponível num primeiro momento) e o
  Redis cair depois, um `DELETE` subsequente continua retornando `204`
  corretamente (a exclusão lógica no banco é sempre a fonte de verdade),
  mas a entrada de cache obsoleta pode continuar servindo o valor antigo
  indefinidamente, sem qualquer sinal de erro.

Para rodar local sem subir Redis à parte, use cache em memória:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.cache.type=simple
```

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
localmente (`java -jar target/coupon-api-0.0.1-SNAPSHOT.jar --spring.cache.type=simple`
— cache em memória usado apenas para poder rodar este teste manual num
ambiente sem Redis disponível; o formato da resposta JSON de cada endpoint é
idêntico independente do backend de cache por trás do `@Cacheable`). O `id`
UUID do cupom criado no primeiro exemplo é reutilizado ao longo dos exemplos
de GET/DELETE abaixo.

### Criar cupom — `POST /coupon`

```bash
curl -i -X POST http://localhost:8080/coupon \
  -H "Content-Type: application/json" \
  -d '{
    "code": "AB-12#34",
    "description": "10% off on all electronics",
    "discountValue": 10.00,
    "expirationDate": "2027-12-31T23:59:59Z",
    "published": true
  }'
```

```
HTTP/1.1 201
Content-Type: application/json

{"id":"0606b416-6920-419b-b259-988c636eddea","code":"AB1234","description":"10% off on all electronics","discountValue":10.00,"expirationDate":"2027-12-31T23:59:59Z","status":"ACTIVE","published":true,"redeemed":false}
```

Note que `code` chegou como `"AB-12#34"` e voltou como `"AB1234"` — os
caracteres especiais (`-`, `#`) foram removidos antes de salvar e retornar,
conforme a regra de sanitização do código. `id` é um UUID gerado pelo
servidor; `expirationDate` aceita e devolve o sufixo `Z` (instante UTC).

### Buscar cupom por id — `GET /coupon/{id}`

```bash
curl -i http://localhost:8080/coupon/0606b416-6920-419b-b259-988c636eddea
```

```
HTTP/1.1 200
Content-Type: application/json

{"id":"0606b416-6920-419b-b259-988c636eddea","code":"AB1234","description":"10% off on all electronics","discountValue":10.00,"expirationDate":"2027-12-31T23:59:59Z","status":"ACTIVE","published":true,"redeemed":false}
```

### Listar cupons ativos — `GET /coupon`

```bash
curl -i http://localhost:8080/coupon
```

```
HTTP/1.1 200
Content-Type: application/json

[{"id":"0606b416-6920-419b-b259-988c636eddea","code":"AB1234","description":"10% off on all electronics","discountValue":10.00,"expirationDate":"2027-12-31T23:59:59Z","status":"ACTIVE","published":true,"redeemed":false}]
```

### Excluir cupom (soft delete) — `DELETE /coupon/{id}`

```bash
curl -i -X DELETE http://localhost:8080/coupon/0606b416-6920-419b-b259-988c636eddea
```

```
HTTP/1.1 204
```

O registro não é removido fisicamente — apenas `active` vira `false` e
`deletedAt` recebe o timestamp da exclusão. Uma busca subsequente pelo mesmo
id continua retornando `200`, agora com `status: "DELETED"` (o contrato
oficial da API — `status: enum<string>` com valores `ACTIVE`, `INACTIVE`,
`DELETED` — exige que um cupom deletado permaneça consultável, refletindo o
soft delete no campo `status` em vez de virar 404):

```bash
curl -i http://localhost:8080/coupon/0606b416-6920-419b-b259-988c636eddea
```

```
HTTP/1.1 200
Content-Type: application/json

{"id":"0606b416-6920-419b-b259-988c636eddea","code":"AB1234","description":"10% off on all electronics","discountValue":10.00,"expirationDate":"2027-12-31T23:59:59Z","status":"DELETED","published":true,"redeemed":false}
```

Um `id` que nunca existiu, por outro lado, continua retornando `404`:

```bash
curl -i http://localhost:8080/coupon/00000000-0000-0000-0000-000000000000
```

```
HTTP/1.1 404
Content-Type: application/json

{"status":404,"error":"Not Found","message":"Coupon not found: 00000000-0000-0000-0000-000000000000","timestamp":"2026-09-13T15:59:40.2117104"}
```

`status: "INACTIVE"` é o terceiro valor do enum: um cupom não deletado cuja
`expirationDate` já passou (ver [Decisões de design](#decisões-de-design)).

### Exemplo de erro 400 — código inválido após sanitização

```bash
curl -i -X POST http://localhost:8080/coupon \
  -H "Content-Type: application/json" \
  -d '{
    "code": "AB-12#3",
    "description": "10% off on all electronics",
    "discountValue": 10.00,
    "expirationDate": "2027-12-31T23:59:59Z",
    "published": true
  }'
```

```
HTTP/1.1 400
Content-Type: application/json

{"status":400,"error":"Bad Request","message":"Coupon code must have exactly 6 alphanumeric characters after removing special characters, got 5 from input: AB-12#3","timestamp":"2026-09-13T15:59:46.8338474"}
```

`"AB-12#3"` tem apenas 5 caracteres alfanuméricos (`AB123`) depois de remover
os especiais — como o resultado não tem exatamente 6 caracteres, a criação é
rejeitada em vez de truncada ou preenchida.

### Exemplo de erro 409 — código duplicado

Criando um novo cupom com o mesmo `code` (`AB-12#34`, já ativo como
`AB1234`):

```bash
curl -i -X POST http://localhost:8080/coupon \
  -H "Content-Type: application/json" \
  -d '{
    "code": "AB-12#34",
    "description": "Another electronics discount",
    "discountValue": 15.00,
    "expirationDate": "2027-06-30T23:59:59Z",
    "published": false
  }'
```

```
HTTP/1.1 409
Content-Type: application/json

{"status":409,"error":"Conflict","message":"Coupon code already in use: AB1234","timestamp":"2026-09-13T15:59:46.9985515"}
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

### `Instant`, não `LocalDate`, como extensão deliberada

A regra de negócio fala em "data de expiração" sem mencionar horário, o que
sugeriria `LocalDate` como leitura mais literal. A decisão foi usar um
timestamp (validado contra "agora" no momento da criação) — uma extensão
consciente além do que o texto pede literalmente, não uma "correção" de uma
suposta imprecisão da regra original. O motivo é precisão na fronteira de
expiração: com timestamp, um cupom que expira mais tarde no mesmo dia é
válido, e um instante já passado no dia de hoje é rejeitado — sem deixar em
aberto a pergunta "expira à meia-noite ou no fim do dia?", que `LocalDate`
sozinho não responde.

O tipo concreto é `java.time.Instant`, não `LocalDateTime` — decisão tomada
depois de comparar contra o [contrato oficial da
API](#exemplos-de-uso-curl), que usa timestamps com sufixo `Z`
(`"2025-11-04T17:14:45.180Z"`, um instante UTC). `LocalDateTime` não carrega
fuso horário nenhum: ele aceita esse formato via Jackson, mas descarta o
significado UTC silenciosamente — um `17:14:45Z` (UTC) vira `17:14:45`
"hora local ingênua" internamente, e toda resposta emitida perde o sufixo
`Z`, divergindo do formato do contrato em toda chamada. `Instant` resolve
isso de forma correta e nativa: é inerentemente UTC, serializa com `Z` por
padrão via Jackson, e um cliente que manda o exemplo exato do contrato
recebe de volta o mesmo instante, sem perda de semântica de fuso.

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

A ordem entre `@CacheEvict` e o commit da transação de delete é garantida
explicitamente: `CacheConfig` usa `@EnableCaching(order = 0)`, o que faz o
advisor de cache do Spring AOP envolver o advisor de transação por fora
(menor valor de `order` = maior precedência = mais externo). Sem isso, os
dois advisors ficam empatados no padrão do Spring
(`Ordered.LOWEST_PRECEDENCE`), e a ordem relativa entre eles fica
indefinida — uma leitura concorrente entre o evict e o commit poderia
repovoar o cache com o dado ainda não deletado. `CacheConfigOrderingTest`
prova essa ordem diretamente, inspecionando os beans
`BeanFactoryCacheOperationSourceAdvisor`/`BeanFactoryTransactionAttributeSourceAdvisor`
reais, em vez de tentar observá-la por uma janela de tempo real — abordagem
escolhida depois de a suíte Redis (acima) já ter se mostrado genuinamente
instável para esse tipo de asserção baseada em timing.

### `id` como UUID, não `Long` sequencial

O [contrato oficial da API](#exemplos-de-uso-curl) documenta explicitamente
`id` como `string`, `type UUID`, em todos os três endpoints. A implementação
usa `@GeneratedValue(strategy = GenerationType.UUID)` (suporte nativo do
Jakarta Persistence 3.2, a versão resolvida no projeto) — o próprio
Hibernate gera o UUID em memória antes do `INSERT`, sem depender de nenhuma
função específica de banco (`gen_random_uuid()` do Postgres, por exemplo),
então o mesmo mapeamento funciona igual em H2 e Postgres. Um `Long`
autoincrement é mais simples de justificar num CRUD genérico, mas aqui o
contrato já define o tipo — segui-lo à risca era mais importante do que a
simplicidade de um `Long`.

### `status` (enum) e por que `GET` não devolve 404 num cupom deletado

O contrato define `status` como `enum<string>` obrigatório, com os valores
permitidos `ACTIVE`, `INACTIVE` e `DELETED` — não um booleano `active` como
a primeira versão deste projeto tinha. A implementação atual deriva
`status()` a partir do estado do agregado (`Coupon.status()`):
`DELETED` se o cupom foi excluído; `INACTIVE` se ainda não foi excluído mas
`expirationDate` já passou; `ACTIVE` caso contrário.

Consequência que vale destacar: para o valor `DELETED` ser alcançável por
algum endpoint real (e não só existir no schema sem nunca aparecer numa
resposta), `GET /coupon/{id}` passou a retornar `200` com `status: "DELETED"`
para um cupom soft-deletado, em vez do `404` da versão anterior deste
projeto. Um `id` que nunca existiu continua `404` — a distinção
"nunca existiu" vs. "existiu e foi deletado" continua expressa, só que agora
pelo campo `status` da resposta, não pelo HTTP status do `GET`.

`INACTIVE` não corresponde a nenhuma regra de negócio escrita explicitamente
nas regras originais (que só especificam Create e Delete) — é uma extensão deliberada,
do mesmo tipo que a extensão de `LocalDate` para `Instant`: o contrato
define o valor, a regra de negócio não diz quando ele se aplica, e "cupom
cuja validade expirou, mas ainda não foi deletado" é a leitura mais direta e
menos arbitrária disponível para preencher essa lacuna sem inventar um
conceito novo (como "pausar" um cupom, por exemplo, que não tem nenhum
suporte no texto da regra).

### `redeemed`, sempre `false`

O contrato exige um campo `redeemed` (booleano, obrigatório) em toda
resposta. Não existe nenhuma regra de negócio sobre resgate/uso de cupom nas
regras originais, e nenhum dos três endpoints (`POST`, `GET`, `DELETE`) tem como
alterar esse valor — então `Coupon.isRedeemed()` é uma constante `false`,
documentada como tal no código. É um caso do contrato exigindo mais do que a
regra de negócio define: a escolha foi satisfazer o contrato literalmente
(o campo existe, sempre `false`) sem inventar uma feature de resgate que
ninguém pediu.

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

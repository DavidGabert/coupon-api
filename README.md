# Coupon API

API REST de cupons de desconto, em Java 17 / Spring Boot. Expõe três operações —
criação (`POST /coupon`), busca por id (`GET /coupon/{id}`) e exclusão lógica
(`DELETE /coupon/{id}`) — e mantém as regras de negócio (sanitização e validação
do código do cupom, valor mínimo de desconto, validade da data de expiração,
unicidade de código entre cupons ativos, exclusão lógica) encapsuladas em
objetos de domínio, fora das camadas de aplicação e de apresentação.

## Arquitetura

O projeto é organizado em quatro camadas:

- **`domain`** — `Coupon` e `CouponCode` concentram toda a regra de negócio.
  São objetos de domínio puros, sem nenhuma anotação de persistência: não
  importam JPA, Spring ou qualquer outro framework. `Coupon.create(...)` é a
  única forma de criar um cupom novo, e valida tudo antes de devolver uma
  instância; `Coupon.reconstitute(...)` reidrata um cupom a partir de um
  estado já persistido (usado só pela camada de infraestrutura), sem repetir
  a validação de negócio — mas ainda garante que o estado reidratado é
  estruturalmente consistente (`active` e `deletedAt` não podem se
  contradizer).
- **`application`** — `CouponService` orquestra os casos de uso (criar,
  buscar, excluir), aplicando cache e transação. Depende só de tipos que ele
  mesmo possui: o domínio (`Coupon`), a porta de persistência
  (`CouponRepository`, uma interface) e as formas de entrada/saída do caso de
  uso (`CreateCouponRequest`, `CouponResponse`). Não importa nada da camada
  `api` nem da `infrastructure`.
- **`infrastructure`** — `CouponEntity` é o mapeamento JPA da tabela
  `coupons`; ao contrário de `Coupon`, ela não tem nenhuma regra de negócio,
  só a definição das colunas. `JpaCouponRepository` implementa a porta
  `CouponRepository`, e é o único ponto do sistema que conhece as duas
  classes ao mesmo tempo — `CouponEntity.fromDomain(...)`/`toDomain()` são as
  únicas travessias entre domínio e persistência.
- **`api`** — `CouponController` traduz HTTP para os tipos de
  `application` e de volta; `GlobalExceptionHandler` mapeia cada exceção de
  domínio/aplicação para o status HTTP correspondente.

A arquitetura fica em camadas simples, com domínio rico, e não em
hexagonal/ports-and-adapters completo — `CouponRepository` é a única porta do
projeto, existe especificamente para manter `Coupon` livre de anotações de
persistência, e tem uma única implementação real (`JpaCouponRepository`).
Introduzir portas equivalentes em outros pontos do sistema, sem uma segunda
implementação à vista, seria indireção sem propósito.

## Regras de negócio

### Criação — `POST /coupon`

Um cupom pode ser criado a qualquer momento. São obrigatórios `code`,
`description`, `discountValue` e `expirationDate`; `published` é opcional e
assume `false` quando omitido.

- **`code`** é normalizado para exatamente 6 caracteres alfanuméricos:
  qualquer caractere não alfanumérico no valor recebido é removido antes de
  salvar e de devolver na resposta. Se o que sobra depois da remoção não tiver
  exatamente 6 caracteres, a criação é rejeitada com `400` — o cliente precisa
  mandar um código cujo conteúdo alfanumérico já tenha o tamanho certo,
  usando os caracteres especiais apenas como decoração opcional na entrada
  (ex.: `"AB-12#34"` vira `"AB1234"`).
- **`discountValue`** tem valor mínimo `0,5`, sem máximo predeterminado — a
  validação de mínimo é aplicada sobre o valor já arredondado para duas
  casas decimais (`HALF_UP`), então um valor como `0,495` é aceito por
  arredondar exatamente para o mínimo. A coluna que armazena o valor
  comporta até 36 dígitos inteiros; isso é um limite técnico de
  armazenamento, não uma regra de negócio.
- **`expirationDate`** nunca pode estar no passado no momento da criação.
- Um cupom pode nascer já publicado (`published: true`).

### Exclusão — `DELETE /coupon/{id}`

Um cupom pode ser excluído a qualquer momento. A exclusão é lógica
(soft delete): o registro nunca é removido do banco, apenas marcado como
inativo (`active = false`) com o timestamp da exclusão (`deletedAt`) — nenhum
dado recebido no cadastro é perdido. Excluir um cupom já excluído é
rejeitado com `409`.

Sob concorrência, duas exclusões simultâneas do mesmo cupom são resolvidas
por lock otimista (`@Version` em `CouponEntity`): a primeira a confirmar
vence; a segunda recebe o mesmo `409` de "já excluído", porque a versão que
ela tinha em mãos ficou desatualizada.

### Campos derivados

- **`status`** (`ACTIVE` / `INACTIVE` / `DELETED`) é calculado a partir do
  estado do cupom, não armazenado diretamente: `DELETED` se foi excluído,
  `INACTIVE` se ainda não foi excluído mas `expirationDate` já passou,
  `ACTIVE` caso contrário. Como consequência, `GET /coupon/{id}` num cupom
  excluído devolve `200` com `status: "DELETED"`, não `404` — um id que nunca
  existiu continua respondendo `404`; a distinção "nunca existiu" vs.
  "existiu e foi excluído" é expressa pelo campo `status`, não pelo status
  HTTP.
- **`redeemed`** é sempre `false`: não há regra de resgate/uso de cupom
  definida, e nenhum dos três endpoints altera esse valor.
- **Código reutilizável após exclusão**: `code` é único apenas entre cupons
  ativos. Um código pode ser reaproveitado por um novo cupom depois que o
  cupom original é excluído.

## Como rodar

### Local (sem Docker)

Requer Java 17+ e Maven (o wrapper `./mvnw` já cuida da versão do Maven). O
H2 é embarcado — não precisa de nenhuma configuração de banco.

```bash
./mvnw spring-boot:run
```

Por padrão o cache usa Redis (`spring.cache.type: redis`), com um Redis
acessível em `localhost:6379` (ou outro host/porta via `REDIS_HOST`/
`REDIS_PORT`). A aplicação sobe normalmente mesmo sem Redis alcançável — a
conexão só é aberta no primeiro acesso ao cache —, mas o comportamento nessa
situação é assimétrico entre leitura e escrita: `GET /coupon/{id}`
(`@Cacheable`) depende de uma leitura síncrona do Redis e responde `500` se a
conexão falhar, enquanto `DELETE /coupon/{id}` (`@CacheEvict`) continua
respondendo `204` normalmente — o `spring-data-redis` executa a eviction de
forma assíncrona, então uma falha aí não impede a exclusão real no banco, só
faz a eviction falhar em silêncio.

Para rodar local sem subir Redis à parte, use cache em memória:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.cache.type=simple
```

### Docker Compose

```bash
docker compose up --build
```

Sobe dois serviços: `app` (a API, porta `8080`) e `redis` (porta `6379`). O
`app` só inicia depois que o `healthcheck` do `redis` reporta saudável, para
não tentar conectar no Redis antes dele estar pronto.

## Exemplos de uso (curl)

Os exemplos abaixo foram capturados de uma execução real da aplicação
localmente (`java -jar target/coupon-api-0.0.1-SNAPSHOT.jar --spring.cache.type=simple`
— cache em memória usado só para rodar este teste manual sem depender de
Redis; o formato da resposta é o mesmo independente do backend de cache). O
`id` do cupom criado no primeiro exemplo é reutilizado nos exemplos de
GET/DELETE seguintes.

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

`code` chegou como `"AB-12#34"` e voltou como `"AB1234"` — os caracteres
especiais foram removidos antes de salvar e retornar. `id` é um UUID gerado
pelo servidor; `expirationDate` aceita e devolve o sufixo `Z` (instante UTC).

### Buscar cupom por id — `GET /coupon/{id}`

```bash
curl -i http://localhost:8080/coupon/0606b416-6920-419b-b259-988c636eddea
```

```
HTTP/1.1 200
Content-Type: application/json

{"id":"0606b416-6920-419b-b259-988c636eddea","code":"AB1234","description":"10% off on all electronics","discountValue":10.00,"expirationDate":"2027-12-31T23:59:59Z","status":"ACTIVE","published":true,"redeemed":false}
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
id continua retornando `200`, agora com `status: "DELETED"`:

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

`status: "INACTIVE"` é o terceiro valor do enum: um cupom não excluído cuja
`expirationDate` já passou.

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
rejeitada em vez de truncada ou completada.

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

Cobrem domínio (unitário puro), aplicação (unitário, repositório mockado via
a porta `CouponRepository`) e controller/persistência (integração com
contexto Spring real via `MockMvc` e `@DataJpaTest`, ambos contra H2). Gate
de cobertura de linha configurado em 80% via Jacoco (`./mvnw verify` falha o
build abaixo disso).

Suítes opcionais de integração contra Redis e Postgres reais, via
Testcontainers, exigem Docker disponível e não rodam no `mvn test` padrão
nem contam para o gate de cobertura — a suíte de Postgres em particular
valida, com conexões concorrentes reais, que a constraint de unicidade do
banco (não só a checagem prévia em memória) é quem garante a unicidade de
código sob concorrência:

```bash
./mvnw verify -Pintegration-redis
./mvnw verify -Pintegration-postgres
```

## Notas de implementação

### Domínio rico com separação explícita entre domínio e persistência

`Coupon` concentra a regra de negócio e não conhece a existência de um
banco de dados; `CouponEntity` concentra o mapeamento relacional e não
conhece nenhuma regra de negócio. A travessia entre as duas acontece só em
`CouponEntity.fromDomain(...)`/`toDomain()`. Essa separação é o que permite
ao domínio ficar livre de qualquer anotação de framework — inclusive o
construtor sem argumentos que o JPA normalmente exige por reflection, que
hoje vive em `CouponEntity`, não em `Coupon`.

A porta `CouponRepository` existe para que `CouponService` dependa só de uma
interface do próprio pacote `application`, nunca de `CouponEntity` ou de
`JpaCouponRepository` diretamente — sem essa porta, `application` precisaria
importar um tipo JPA de `infrastructure`, que por sua vez já depende de
`application` para implementá-la, criando uma dependência circular entre as
duas camadas.

### Sanitização e rejeição do `code`

A regra de negócio permite caracteres especiais na entrada, desde que sejam
removidos antes de salvar/retornar, "garantindo o tamanho de 6 caracteres".
A leitura adotada aqui é estrita: o resultado depois de remover tudo que não
é alfanumérico precisa ter exatamente 6 caracteres; se sobrar mais ou menos
que isso, a criação é rejeitada com `400`, em vez de truncar ou completar o
valor. A alternativa de truncar para os 6 primeiros caracteres alfanuméricos
foi descartada por fazer entradas diferentes (`"ABC123XYZ"` e `"ABC123999"`)
colidirem silenciosamente no mesmo código final.

### `Instant`, não `LocalDate`, para `expirationDate`

A regra de negócio fala em "data de expiração" sem mencionar horário, o que
sugeriria `LocalDate`. A implementação usa um timestamp UTC (`Instant`),
validado contra o instante atual no momento da criação — uma extensão
deliberada: com timestamp, um cupom que expira mais tarde no mesmo dia é
válido, sem deixar em aberto a pergunta "expira à meia-noite ou no fim do
dia?". O tipo concreto é `Instant`, não `LocalDateTime`, porque o contrato
oficial da API usa timestamps com sufixo `Z` (instante UTC) — `LocalDateTime`
aceita esse formato via Jackson mas descarta o significado de fuso horário
silenciosamente, então a resposta perderia o sufixo `Z` em toda chamada.
`Instant` é nativamente UTC e serializa com `Z` por padrão.

### Unicidade condicional via coluna-sombra

`code` precisa ser único apenas entre cupons **ativos** — o mesmo código
pode ser reutilizado depois que o cupom original é excluído. A anotação
`@Column(unique = true)` do JPA só sabe expressar unicidade sobre a coluna
inteira, não "único, mas apenas entre linhas onde `active = true`" — isso
exigiria um índice parcial, recurso que nem todo banco relacional suporta da
mesma forma. A solução usada evita esse problema: `CouponEntity` tem uma
coluna extra e anulável (`activeCode`) que espelha `code` enquanto o cupom
está ativo e é zerada para `NULL` assim que ele é excluído, carregando ela
mesma uma constraint `UNIQUE` comum — como `NULL` nunca é considerado igual
a outro `NULL` numa constraint `UNIQUE` (comportamento padrão do SQL), a
tabela pode acumular quantos cupons excluídos quiser com o mesmo `code`
original sem nunca violar a constraint.

Sob concorrência, a constraint de banco é a fonte de verdade: `CouponService`
faz uma checagem prévia (`existsActiveCouponWithCode`) só para devolver um
erro rápido no caso comum, mas `CouponRepository.save(...)` sempre persiste
de forma síncrona (a implementação usa `saveAndFlush` internamente), o que
garante que uma violação de constraint por uma criação concorrente seja
visível dentro do mesmo método, onde é traduzida para `409`. Se a violação
não corresponder a esse código estar ativo — por exemplo, um `discountValue`
maior do que a coluna comporta —, a exceção original é propagada em vez de
ser reportada, incorretamente, como código duplicado.

### `id` como UUID, não `Long` sequencial

O contrato oficial da API documenta `id` explicitamente como `string`,
`type UUID`, nos três endpoints. `CouponEntity` usa
`@GeneratedValue(strategy = GenerationType.UUID)` — suporte nativo do
Jakarta Persistence, sem depender de nenhuma função específica de banco —,
então o mesmo mapeamento funciona igual em H2 e Postgres.

### Cache Redis: justificativa, ordenação e TTL

`GET /coupon/{id}` usa cache-aside via `@Cacheable("coupons")`, com
`@CacheEvict("coupons")` no delete. A justificativa é uma suposição
documentada, não um requisito de volume comprovado: o padrão de acesso
típico de um domínio de cupons é leitura alta e escrita baixa (um cupom é
criado uma vez e potencialmente revalidado várias vezes por requisição de
checkout/precificação até expirar).

A ordem entre `@CacheEvict` e o commit da transação de delete é garantida
explicitamente: `CacheConfig` usa `@EnableCaching(order = 0)`, fazendo o
advisor de cache do Spring AOP envolver o advisor de transação por fora
(menor valor de `order` = maior precedência = mais externo). Sem isso, os
dois advisors ficam empatados no padrão do Spring, e a ordem relativa entre
eles fica indefinida — uma leitura concorrente entre o evict e o commit
poderia repovoar o cache com o dado ainda não excluído.

`status` é derivado do relógio no momento da leitura (`ACTIVE` vs.
`INACTIVE` dependem de `expirationDate` comparado a "agora"), não de algo
que o banco possa invalidar sozinho — por isso o cache tem um TTL de 5
minutos configurado em `CacheConfig`, limitando por quanto tempo uma
resposta em cache pode ficar desatualizada, independente da invalidação
explícita no delete.

### `redeemed`, sempre `false`

O contrato exige um campo `redeemed` booleano obrigatório em toda resposta.
Não existe regra de negócio sobre resgate/uso de cupom, e nenhum dos três
endpoints tem como alterar esse valor — então `Coupon.isRedeemed()` é uma
constante `false`, satisfazendo o contrato sem inventar uma feature de
resgate que ninguém pediu.

### Por que `MockMvc`, não `TestRestTemplate`, nos testes de controller

`MockMvc` sobe o `DispatcherServlet` de verdade e exercita serialização HTTP
real, mas roda na mesma thread do teste — o que preserva o rollback
transacional do Spring Test (`@Transactional` no método de teste) entre um
teste e o próximo. `TestRestTemplate` bate num servidor embutido rodando em
thread separada, o que quebraria esse rollback e deixaria dados de um teste
vazarem para o próximo.

### Por que Testcontainers-Redis/Postgres ficam fora do gate obrigatório

O requisito de banco é H2 em memória — nada obriga Docker disponível para os
testes obrigatórios passarem, incluindo os de Create/Delete que são o núcleo
funcional da API. Por isso os testes de cache no gate padrão usam
`ConcurrentMapCacheManager` (cache em memória, a mesma interface
`CacheManager` do Spring), que já valida a lógica de
`@Cacheable`/`@CacheEvict` sem depender de infraestrutura externa. As
suítes reais contra Redis e Postgres via Testcontainers ficam disponíveis
como profiles Maven opcionais, fora do `mvn test` padrão.

### Segurança

Autenticação e autorização estão fora do escopo deste projeto — nenhum
endpoint tem proteção de acesso implementada. Em produção, o próximo passo
seria autenticação via API key (consumidores internos/máquina-a-máquina) ou
OAuth2/JWT (usuários finais ou múltiplos clientes com escopos de permissão
distintos), dependendo de quem consome a API.

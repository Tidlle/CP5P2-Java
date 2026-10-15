# Mercado Express — CP5 (Parte 1: Spring Security · Parte 2: Microsserviços e Mensageria)

Aplicação **Spring Boot** para uma empresa do tipo **mercado express** (meias, produtos de limpeza e hortifruti), continuação do tema do CP4 ([CP4P2-Java](https://github.com/Tidlle/CP4P2-Java)).

- **Parte 1:** interface Web em **Thymeleaf**, persistência em **PostgreSQL** e **Spring Security** (login próprio, Sign Up e perfis ADMIN/CLIENTE).
- **Parte 2:** carrinho de compras, integração com a API do **Banco Tranquilo** por **RabbitMQ** (exchanges, filas e DLQ), **e-mail de confirmação de pagamento** registrado no PostgreSQL, API REST para **Postman** e **dashboard da gerência** com gráficos de vendas, lucro e estoque.

## Integrantes

- Eduardo Martins — RM562259
- João Victor Alcantara — RM562707

## Links

- **GitHub (Parte 2):** https://github.com/Tidlle/CP5P2-Java
- **Deploy da Parte 1 (produção):** https://cp5p1-java.onrender.com — Render (Web Service com Docker + PostgreSQL gerenciado)
- **Vídeo da Parte 2:** _[preencher com o link do YouTube (não listado)]_
- **IDE utilizada:** IntelliJ IDEA

## Sumário

- [Parte 2 – Microsserviços com RabbitMQ](#parte-2--microsserviços-com-rabbitmq)
  - [Arquitetura e fluxo das mensagens](#arquitetura-e-fluxo-das-mensagens)
  - [Exchanges e filas](#exchanges-e-filas)
  - [Como executar a Parte 2](#como-executar-a-parte-2)
  - [Testes com Postman e pelo painel do RabbitMQ](#testes-com-postman-e-pelo-painel-do-rabbitmq)
  - [E-mail de confirmação](#e-mail-de-confirmação)
  - [Dashboard da gerência](#dashboard-da-gerência)
  - [Configuração do banco de dados (para o professor)](#configuração-do-banco-de-dados-para-o-professor)
- [Parte 1 – Spring Security](#parte-1--spring-security)

---

# Parte 2 – Microsserviços com RabbitMQ

O projeto tem **duas aplicações Spring Boot** que conversam **somente por mensagens** no RabbitMQ (funciona igual no **LavinMQ**, que usa o mesmo protocolo AMQP 0-9-1):

| Aplicação | Pasta | Porta | Papel |
|-----------|-------|-------|-------|
| **Mercado Express** | `/` (raiz) | 8080 | Loja: carrinho, pedidos, e-mail, API REST e dashboard |
| **Banco Tranquilo** | `/banco-tranquilo` | 8081 | Microsserviço do banco: autoriza ou recusa os pagamentos |

### Spring Initializr

| Projeto | Dependências |
|---------|--------------|
| Mercado Express | Spring Web, Thymeleaf, Spring Security, Spring Data JPA, PostgreSQL Driver, Validation, Lombok, DevTools, **Spring for RabbitMQ**, **Java Mail Sender** |
| Banco Tranquilo | Spring Web, **Spring for RabbitMQ**, Spring Data JPA, PostgreSQL Driver, Lombok |

Ambos: Maven, Java 21, Jar, YAML, Spring Boot 3.4.x.

## Arquitetura e fluxo das mensagens

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant M as Mercado Express :8080
    participant R as RabbitMQ
    participant B as Banco Tranquilo :8081
    participant S as SMTP (Mailpit/Gmail)
    participant DB as PostgreSQL

    C->>M: Finaliza a compra (tela do carrinho ou POST /api/compras)
    M->>DB: Reserva estoque + grava pedido AGUARDANDO_PAGAMENTO
    M->>R: banco.tranquilo.exchange [pagamento.solicitado] (após o commit)
    R->>B: fila banco.tranquilo.pagamentos
    B->>DB: grava a transação (APROVADO / RECUSADO)
    B->>R: mercado.express.exchange [pagamento.respondido]
    R->>M: fila mercado.express.pagamentos.respostas
    alt APROVADO
        M->>DB: pedido PAGO
        M->>R: mercado.express.exchange [email.confirmacao]
        R->>M: fila mercado.express.notificacoes.email
        M->>S: e-mail "Pagamento confirmado"
        M->>DB: registra o envio em TDS_Mercado_Email
    else RECUSADO
        M->>DB: pedido RECUSADO + devolve o estoque
    end
```

Decisões importantes:

- **Publicação depois do commit** (`@TransactionalEventListener(AFTER_COMMIT)`): o banco nunca responde sobre um pedido que ainda não está no PostgreSQL.
- **Reserva de estoque** com trava pessimista (`SELECT ... FOR UPDATE`) na finalização; se o banco recusar, o estoque volta.
- **Idempotência**: uma mensagem entregue duas vezes não processa o pedido de novo, não cria outra transação no banco e não manda dois e-mails.
- **Retry + DLQ**: cada listener tenta 3 vezes; depois a mensagem vai para a fila de mensagens mortas (`banco.tranquilo.dlq` / `mercado.express.dlq`).
- **RabbitMQ fora do ar**: o pedido fica como `ERRO_COMUNICACAO`, o estoque é devolvido e o carrinho é mantido (a API responde `503`).
- **Mensagens em JSON** (`Jackson2JsonMessageConverter`), fáceis de ler e de publicar pelo painel do RabbitMQ.

### Regras do Banco Tranquilo

| Situação | Resposta |
|----------|----------|
| Valor até **R$ 1.000,00** (variável `BANCO_LIMITE`) | `APROVADO` |
| Valor acima do limite | `RECUSADO` — "Limite insuficiente…" |
| Valor ausente/zero ou cliente sem e-mail | `RECUSADO` |

O banco simula **1,5 s de análise antifraude** (`BANCO_TEMPO_ANALISE_MS`), o que deixa as mensagens visíveis no painel do RabbitMQ durante a demonstração.

### Estrutura de pastas (Parte 2)

```
src/main/java/com/fiap/mercadoexpresssec
├── mensageria
│   ├── MensageriaConfig.java           # exchanges, filas, bindings, DLQ e conversor JSON
│   ├── MensageriaPublisher.java        # publica após o commit (pagamento e e-mail)
│   ├── PagamentoRespostaListener.java  # consome as respostas do Banco Tranquilo
│   ├── EmailConfirmacaoListener.java   # consome a fila de e-mails
│   └── PagamentoRequisicao / PagamentoResposta / EmailConfirmacaoMensagem (JSON)
├── controller
│   ├── CarrinhoController.java         # /carrinho (adicionar, atualizar, remover, finalizar)
│   ├── PedidoController.java           # /pedidos ("Minhas compras" + acompanhamento)
│   ├── DashboardController.java        # /admin/dashboard, /admin/pedidos, reenviar e-mail
│   └── api/CompraApiController.java    # /api/compras e /api/produtos (Postman)
├── service
│   ├── CarrinhoService.java            # carrinho na sessão HTTP
│   ├── PedidoService.java              # finalização, reserva/devolução de estoque, respostas
│   ├── EmailService.java               # monta (Thymeleaf), envia e registra o e-mail
│   └── DashboardService.java           # indicadores e séries dos gráficos
└── model                               # Pedido, ItemPedido, EmailEnviado, StatusPedido…

banco-tranquilo/src/main/java/com/fiap/bancotranquilo
├── config/RabbitConfig.java            # mesma topologia declarada no Mercado
├── mensageria/PagamentoListener.java   # consome banco.tranquilo.pagamentos e responde
├── service/AutorizacaoService.java     # regras de aprovação (idempotente)
├── controller/TransacaoController.java # GET /api/transacoes, /api/status
└── model/Transacao.java                # TDS_Banco_Tranquilo_Transacao
```

## Exchanges e filas

| Exchange (tipo) | Routing key | Fila | Quem consome |
|-----------------|-------------|------|--------------|
| `banco.tranquilo.exchange` (direct) | `pagamento.solicitado` | `banco.tranquilo.pagamentos` | Banco Tranquilo |
| `mercado.express.exchange` (direct) | `pagamento.respondido` | `mercado.express.pagamentos.respostas` | Mercado Express |
| `mercado.express.exchange` (direct) | `email.confirmacao` | `mercado.express.notificacoes.email` | Mercado Express |
| `banco.tranquilo.dlx` (fanout) | — | `banco.tranquilo.dlq` | (inspeção manual) |
| `mercado.express.dlx` (fanout) | — | `mercado.express.dlq` | (inspeção manual) |

As duas aplicações declaram a exchange e a fila do banco com os mesmos argumentos, então tanto faz qual sobe primeiro: nenhuma mensagem se perde.

![Painel do RabbitMQ - filas](assets/p2_rabbitmq_filas.png)

## Novas tabelas (PostgreSQL)

**`TDS_Mercado_Pedido`** – compras

| Coluna | Descrição |
|--------|-----------|
| id, codigo (UUID, único) | O `codigo` é o identificador que trafega nas mensagens |
| cliente_nome, cliente_email | Quem comprou (recebe o e-mail) |
| forma_pagamento | `PIX`, `CARTAO_CREDITO`, `CARTAO_DEBITO` |
| status | `AGUARDANDO_PAGAMENTO`, `PAGO`, `RECUSADO`, `ERRO_COMUNICACAO` |
| origem | `WEB` (carrinho), `API` (Postman) ou `SEED` (histórico de exemplo) |
| valor_total, custo_total | Para faturamento e lucro |
| transacao_id, mensagem_banco, data_resposta | Resposta do Banco Tranquilo |
| data_criacao | Data da compra |

**`TDS_Mercado_Item_Pedido`** – itens (nome, setor, preço e custo copiados do produto no momento da compra).

**`TDS_Mercado_Email`** – controle de envio de e-mails: `pedido_codigo`, `destinatario`, `assunto`, `corpo` (HTML enviado), `status` (`ENVIADO`/`FALHA`), `erro`, `data_envio`.

**`TDS_Banco_Tranquilo_Transacao`** (microsserviço do banco): `id` (transação), `pedido_codigo` (único), `valor`, `forma_pagamento`, `cliente_email`, `status`, `mensagem`, `data_processamento`.

A tabela de produtos `TDS_Sec_MVC_TB_Mercado` ganhou a coluna **`custo`** (usada no cálculo do lucro). Produtos cadastrados na Parte 1 recebem automaticamente 60% do preço como custo.

## Como executar a Parte 2

1. Suba **PostgreSQL + RabbitMQ + Mailpit** (caixa de e-mail de testes):
   ```bash
   docker compose up -d
   ```
   | Serviço | Endereço |
   |---------|----------|
   | Painel do RabbitMQ | http://localhost:15672 (guest / guest) |
   | Caixa de entrada (Mailpit) | http://localhost:8025 |
   | PostgreSQL | localhost:5433, banco `mercado_express` (postgres / postgres) |

2. Rode as duas aplicações no **IntelliJ IDEA**: `MercadoExpressSecApplication` (raiz) e `BancoTranquiloApplication` (abra `banco-tranquilo/pom.xml` como módulo Maven). Pelo terminal, em dois terminais:
   ```bash
   mvn spring-boot:run
   ```
   ```bash
   mvn -f banco-tranquilo/pom.xml spring-boot:run
   ```
   Ou suba tudo em containers: `docker compose --profile apps up -d --build`.

3. Acesse http://localhost:8080, entre como `admin@mercado.com` / `admin123` (ou crie um CLIENTE), adicione produtos ao carrinho e finalize a compra.

> Os prints deste README foram tirados com o projeto rodando localmente (Docker + as duas aplicações). A cliente usada nos prints, **Maria Silva** (`maria.silva@cliente.com` / `cliente123`), foi criada pela própria tela de cadastro.

### Variáveis de ambiente da Parte 2

| Variável | Padrão | Para que serve |
|----------|--------|----------------|
| `RABBITMQ_ADDRESSES` | `amqp://guest:guest@localhost:5672` | URL do broker. Para **CloudAMQP/LavinMQ** use `amqps://usuario:senha@host/vhost` |
| `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | localhost, 5672, guest, guest | Alternativa à URL completa |
| `MAIL_HOST`, `MAIL_PORT` | localhost, 1025 | Servidor SMTP (Mailpit local) |
| `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_STARTTLS` | vazio, vazio, false, false | Para Gmail: `smtp.gmail.com`, porta `587`, usuário, **senha de app**, `true`, `true` |
| `MAIL_FROM` | `Mercado Express <nao-responda@mercadoexpress.com>` | Remetente |
| `SEED_PEDIDOS` | `true` | Cria um histórico de compras de exemplo (canal SEED) para o dashboard |
| `BANCO_LIMITE` (banco) | `1000.00` | Limite de aprovação do Banco Tranquilo |
| `BANCO_TEMPO_ANALISE_MS` (banco) | `1500` | Tempo simulado de análise |

## Telas da Parte 2

### Produtos com "Adicionar ao carrinho"

Na lista e no detalhe do produto há um campo de quantidade e o botão do carrinho (ADMIN e CLIENTE). O menu mostra a quantidade de itens no carrinho.

![Lista com carrinho](assets/p2_lista_carrinho.png)

### Carrinho

Mostra os produtos, permite alterar quantidades e remover itens, escolher a forma de pagamento (Pix, crédito, débito) e **Finalizar compra**, que executa a integração com o Banco Tranquilo. Itens com estoque insuficiente ficam destacados e bloqueiam a finalização.

![Carrinho](assets/p2_carrinho.png)

### Acompanhamento do pedido

Linha do tempo **Compra → Banco Tranquilo → Resposta → E-mail**. Enquanto o banco não responde, a página se atualiza sozinha a cada 2 segundos. Mostra também a transação do banco e os e-mails registrados em `TDS_Mercado_Email` (o ADMIN pode **reenviar** o e-mail).

| Aguardando o banco | Pago e e-mail enviado |
|--------------------|-----------------------|
| ![Aguardando](assets/p2_pedido_aguardando.png) | ![Pago](assets/p2_pedido_pago.png) |

"Minhas compras" (`/pedidos`) lista as compras do usuário logado com o status de cada uma.

## E-mail de confirmação

Quando o Banco Tranquilo aprova, o Mercado publica uma mensagem na fila `mercado.express.notificacoes.email`. O `EmailConfirmacaoListener` monta o e-mail em HTML com Thymeleaf (`templates/email/confirmacao-pagamento.html`), envia pelo `JavaMailSender` e **grava cada tentativa** na tabela `TDS_Mercado_Email`:

- sucesso → `ENVIADO`
- erro de SMTP → `FALHA` com a mensagem de erro (o ADMIN reenvia pelo botão **Reenviar e-mail**, que coloca a mensagem de novo na fila)

Localmente os e-mails chegam no **Mailpit** (http://localhost:8025). Para enviar de verdade, configure as variáveis `MAIL_*` do Gmail.

![E-mail no Mailpit](assets/p2_email.png)

## Dashboard da gerência

`/admin/dashboard` (somente ADMIN):

- **Indicadores:** faturamento, lucro e margem, pedidos pagos e ticket médio, taxa de aprovação do banco, e-mails enviados/com falha
- **Pedidos por status** e **por canal** (WEB, API, SEED)
- **Gráficos (Chart.js):** vendas e lucro por dia (14 dias), faturamento por setor, 5 produtos mais vendidos e estoque atual (produtos com menos de 20 unidades em destaque). Cada gráfico tem tooltip e a opção **Ver dados em tabela**
- **Tabelas:** últimas compras, estoque baixo e últimos e-mails enviados

Faturamento e lucro consideram só pedidos **PAGOS**. `/admin/pedidos` lista todos os pedidos com cliente, canal, total, lucro e status.

![Dashboard](assets/p2_dashboard.png)

## Testes com Postman e pelo painel do RabbitMQ

### API REST (HTTP Basic com os usuários de `TDS_Users_Mercado`)

| Método e rota | Acesso | Descrição |
|---------------|--------|-----------|
| `GET /api/produtos` | ADMIN, CLIENTE | Produtos com id e estoque |
| `POST /api/compras` | ADMIN, CLIENTE | Cria a compra e envia ao banco → **202 Accepted** |
| `GET /api/compras/{codigo}` | dono do pedido ou ADMIN | Status do pedido |
| `GET /api/compras` | CLIENTE: as próprias · ADMIN: todas | Lista de compras |
| `GET http://localhost:8081/api/transacoes` | público | Transações do Banco Tranquilo |
| `GET http://localhost:8081/api/status` | público | Limite e total processado pelo banco |

Erros voltam em JSON: `400` dados inválidos, `401` sem credenciais, `403` sem permissão, `404` pedido inexistente, `422` estoque insuficiente e `503` RabbitMQ indisponível (os erros de negócio usam o formato `ProblemDetail`, RFC 7807).

**Exemplo de requisição de compra** (`postman/exemplo-compra.json`):

```http
POST http://localhost:8080/api/compras
Authorization: Basic admin@mercado.com:admin123
Content-Type: application/json
```
```json
{
  "clienteNome": "Professor Marcel",
  "clienteEmail": "professor@exemplo.com",
  "formaPagamento": "PIX",
  "itens": [
    { "produtoId": 1, "quantidade": 2 },
    { "produtoId": 5, "quantidade": 3 }
  ]
}
```

`clienteNome`/`clienteEmail` são opcionais: sem eles, a compra fica no nome do usuário autenticado. Só o ADMIN pode comprar para outro e-mail (útil para receber a confirmação no seu endereço).

Resposta `202 Accepted`:

```json
{
  "codigo": "9f1c2b7e-…",
  "status": "AGUARDANDO_PAGAMENTO",
  "valorTotal": 60.77,
  "origem": "API",
  "itens": [ { "produtoId": 1, "produto": "Meia cano alto algodão", "quantidade": 2, "precoUnitario": 19.9, "subtotal": 39.8 }, … ]
}
```

Cerca de 2 segundos depois, `GET /api/compras/{codigo}` retorna `"status": "PAGO"` com o `transacaoId` do Banco Tranquilo.

**Coleção pronta:** importe `postman/MercadoExpress-BancoTranquilo.postman_collection.json` no Postman (ou Insomnia). Ela tem 10 requisições com scripts de teste que encadeiam o código do pedido: compra aprovada, consulta, transação no banco, **compra recusada por limite** (30 meias = R$ 1.047,00), estoque insuficiente (422) e acesso sem credenciais (401). Rode tudo com o **Collection Runner**.

**Resultado do Collection Runner:** 10 requisições e 13 testes, todos aprovados (0 erros).

![Postman - Collection Runner](assets/p2_postman.png)

#### Requisições no Postman

**1. Produtos disponíveis** – `GET /api/produtos` (200): ids, preço, custo e estoque para montar a compra.

![Postman - produtos](assets/p2_postman_produtos.png)

**2. Compra** – `POST /api/compras` (202 Accepted): o pedido é criado como `AGUARDANDO_PAGAMENTO` e enviado ao Banco Tranquilo pelo RabbitMQ.

![Postman - compra](assets/p2_postman_compra.png)

**3. Compra paga** – `GET /api/compras/{codigo}` cerca de 2 segundos depois: `"status": "PAGO"`, com o `transacaoId` e a mensagem do banco.

![Postman - compra paga](assets/p2_postman_compra_paga.png)

**4. Transação no Banco Tranquilo** – `GET http://localhost:8081/api/transacoes/{codigo}`: o microsserviço do banco registrou a mesma compra como `APROVADO`.

![Postman - transação no banco](assets/p2_postman_transacao_banco.png)

**5. Compra recusada** – 30 meias esportivas (R$ 1.047,00), acima do limite: `"status": "RECUSADO"` com "Limite insuficiente". O estoque reservado é devolvido.

![Postman - compra recusada](assets/p2_postman_compra_recusada.png)

**6. Estoque insuficiente** – `POST /api/compras` com 999 unidades: `422 Unprocessable Entity`, sem criar pedido.

![Postman - erro 422](assets/p2_postman_erro_422.png)

**7. Sem credenciais** – `GET /api/compras` sem autenticação: `401 Unauthorized` (não redireciona para a tela de login).

![Postman - erro 401](assets/p2_postman_erro_401.png)

### Testes internos pelo painel do RabbitMQ (Management UI)

1. Abra http://localhost:15672 → **Queues and Streams** e acompanhe as filas durante uma compra (*Message rates*, *Ready*, *Unacked*).
2. Para testar o Banco Tranquilo **sem passar pelo Mercado**: **Exchanges → `banco.tranquilo.exchange` → Publish message**
   - Routing key: `pagamento.solicitado`
   - Properties: `content_type` = `application/json`
   - Payload:
     ```json
     {"pedidoCodigo": "teste-painel-001", "valor": 1500.00, "formaPagamento": "PIX", "clienteNome": "Teste", "clienteEmail": "teste@exemplo.com"}
     ```
   O banco recusa (acima do limite) e publica a resposta em `mercado.express.pagamentos.respostas` (o Mercado ignora porque o pedido não existe). Pare o Mercado Express antes para ver a resposta parada na fila e use **Get messages** para lê-la.
3. Para ver a **DLQ**: publique um payload sem `pedidoCodigo` (ex.: `{"valor": 10}`); o banco rejeita a mensagem e ela aparece em `banco.tranquilo.dlq`.

![Publicar mensagem no painel](assets/p2_rabbitmq_publish.png)

### Testes automatizados

| Projeto | Classe | Testes |
|---------|--------|--------|
| Mercado | `SegurancaFluxoTest` | 13 (Parte 1: login, cadastro, perfis, CRUD) |
| Mercado | `CompraFluxoTest` | 5: compra aprovada de ponta a ponta (carrinho → mensagem ao banco → resposta → e-mail → dashboard), compra recusada devolvendo estoque, validações e permissões da API, RabbitMQ fora do ar, falha de SMTP registrada |
| Banco Tranquilo | `PagamentoListenerTest` | 4: aprovação, recusa por limite, idempotência e mensagem inválida indo para a DLQ |

```bash
mvn test
```
```bash
mvn -f banco-tranquilo/pom.xml test
```

Os testes usam H2 em memória e mocks do `RabbitTemplate`/`JavaMailSender`, então não precisam de Docker.

## Configuração do banco de dados (para o professor)

| Item | Valor (local, `docker compose up -d`) |
|------|---------------------------------------|
| SGBD | PostgreSQL 16 |
| Host / porta | `localhost:5433` |
| Banco | `mercado_express` |
| Usuário / senha | `postgres` / `postgres` |
| URL JDBC | `jdbc:postgresql://localhost:5433/mercado_express` |
| Tabelas | `TDS_Sec_MVC_TB_Mercado`, `TDS_Users_Mercado`, `TDS_Mercado_Pedido`, `TDS_Mercado_Item_Pedido`, `TDS_Mercado_Email`, `TDS_Banco_Tranquilo_Transacao` (criadas pelo Hibernate, `ddl-auto: update`) |
| RabbitMQ | `amqp://guest:guest@localhost:5672`, painel http://localhost:15672 |

> O PostgreSQL do Docker usa a porta **5433** no computador (e não 5432) para não conflitar com um PostgreSQL instalado no Windows. Dentro da rede do Docker continua 5432.

Consultas úteis:

```sql
SELECT codigo, cliente_email, status, valor_total, mensagem_banco FROM tds_mercado_pedido ORDER BY data_criacao DESC;
SELECT pedido_codigo, destinatario, status, erro, data_envio FROM tds_mercado_email ORDER BY data_envio DESC;
SELECT pedido_codigo, valor, status, mensagem FROM tds_banco_tranquilo_transacao ORDER BY data_processamento DESC;
```

---

# Parte 1 – Spring Security

## Tecnologias e dependências

- Java 21
- Spring Boot 3.4.1 (Maven)
- Spring Web (MVC)
- Thymeleaf + `thymeleaf-extras-springsecurity6`
- Spring Security
- Spring Data JPA (Hibernate)
- Validation (Bean Validation)
- PostgreSQL Driver
- Lombok
- Spring Boot DevTools
- Bootstrap 5 (CDN) + CSS próprio
- **Bootstrap Icons 1.13.1**: biblioteca de ícones, instalada via WebJar (`org.webjars.npm:bootstrap-icons`)
- **Font Awesome Free 7.3.0** (estilo *solid*): ícones temáticos dos setores (meias, limpeza, hortifruti), instalada via WebJar (`org.webjars:font-awesome`)
- **Fonte Poppins**: instalada via WebJar (`org.webjars.npm:fontsource__poppins`)

> Os ícones e a fonte são dependências Maven servidas pela própria aplicação em `/webjars/**` (rota liberada no Spring Security). Eles não dependem de CDN externa.
- Testes: JUnit 5, Spring Security Test, H2 (somente nos testes)

### Spring Initializr

Configuração final no [start.spring.io](https://start.spring.io): Maven, Java 21, Jar, YAML, Spring Boot 3.4.x, com as dependências **Spring Web, Thymeleaf, Spring Security, Spring Data JPA, PostgreSQL Driver, Lombok, Validation e Spring Boot DevTools**.

![Print Spring Initializr](assets/print_spring_initializr.png)

## Arquitetura

```
src/main/java/com/fiap/mercadoexpresssec
├── config
│   ├── SecurityConfig.java        # regras de acesso, login/logout, página 403
│   ├── LoginSuccessHandler.java   # redireciona ADMIN -> /admin, CLIENTE -> /mercado
│   ├── LoginFailureHandler.java   # e-mail não cadastrado -> /cadastro (Sign Up)
│   ├── PasswordConfig.java        # BCryptPasswordEncoder
│   └── DataInitializer.java       # cria o ADMIN inicial e produtos de exemplo
├── controller
│   ├── HomeController.java        # index, login, cadastro, acesso negado
│   ├── MercadoController.java     # CRUD de produtos
│   └── AdminController.java       # painel administrativo
├── dto/CadastroForm.java          # formulário do Sign Up
├── model                          # Mercado, Usuario, Role
├── repository                     # Spring Data JPA
└── service                        # regras de negócio + UserDetailsService
```

## Modelo de dados (PostgreSQL)

As tabelas são criadas automaticamente pelo Hibernate (`ddl-auto: update`). O PostgreSQL guarda os nomes em minúsculas.

**`TDS_Sec_MVC_TB_Mercado`**: produtos do mercado (entidade `Mercado`)

| Coluna  | Tipo     | Descrição |
|---------|----------|-----------|
| id      | bigint (identity) | Identificador |
| nome    | varchar(100) | Nome do produto |
| tipo    | varchar(60)  | Tipo (Fruta, Limpeza, Vestuário…) |
| setor   | varchar(60)  | Setor do mercado |
| tamanho | double   | Tamanho/medida |
| preco   | double   | Preço (R$) |
| custo   | double   | Custo unitário (R$), adicionado na Parte 2 para o cálculo de lucro |
| estoque | integer  | Quantidade em estoque |

**`TDS_Users_Mercado`**: usuários do sistema (entidade `Usuario`)

| Coluna        | Tipo     | Descrição |
|---------------|----------|-----------|
| id            | bigint (identity) | Identificador |
| nome          | varchar(100) | Nome completo |
| email         | varchar(120), único | Login do usuário |
| senha         | varchar  | Hash **BCrypt**; a senha nunca é salva em texto puro |
| role          | varchar(20) | `ADMIN` ou `CLIENTE` |
| data_cadastro | timestamp | Data de criação da conta |

## Configuração (`application.yml`)

Toda configuração sensível vem de **variáveis de ambiente**. Os valores após `:` são só o padrão para rodar localmente:

```yaml
server:
  port: ${PORT:8080}

spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5433}/${DB_NAME:mercado_express}}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
  jpa:
    hibernate:
      ddl-auto: update

app:
  admin:
    nome: ${ADMIN_NOME:Administrador}
    email: ${ADMIN_EMAIL:admin@mercado.com}
    senha: ${ADMIN_SENHA:admin123}
```

| Variável | Para que serve |
|----------|----------------|
| `DB_URL` | URL JDBC completa (ex.: Neon/Supabase). Se não for definida, a URL é montada com `DB_HOST`, `DB_PORT` e `DB_NAME` |
| `DB_USERNAME` / `DB_PASSWORD` | Credenciais do PostgreSQL |
| `ADMIN_EMAIL` / `ADMIN_SENHA` | Usuário ADMIN criado automaticamente na primeira execução |
| `PORT` | Porta HTTP (o Render define sozinho) |

## Como executar localmente

1. Suba um PostgreSQL local com Docker:
   ```bash
   docker compose up -d
   ```
   (ou use um PostgreSQL já instalado com um banco `mercado_express`)
2. Rode a aplicação. No **IntelliJ IDEA**, execute a classe `MercadoExpressSecApplication`. Pelo terminal:
   ```bash
   mvn spring-boot:run
   ```
3. Acesse `http://localhost:8080`.

**Login ADMIN padrão (local):** `admin@mercado.com` / `admin123`. Em produção a senha vem da variável `ADMIN_SENHA`.

Para rodar os testes automatizados (usam H2 em memória, sem precisar de PostgreSQL):

```bash
mvn test
```

## Spring Security

### Perfis de acesso

| Perfil | Como é criado | O que pode fazer | Tela após o login |
|--------|---------------|------------------|-------------------|
| **ADMIN** | Automaticamente na inicialização (`DataInitializer`) | CRUD completo de produtos e painel administrativo com a lista de usuários | `/admin` |
| **CLIENTE** | Pela tela de cadastro (`/cadastro`) | Consultar a lista e os detalhes dos produtos (somente leitura) | `/mercado` |

### Rotas

| Rota | Método | Acesso |
|------|--------|--------|
| `/` (landing page `index.html`) | GET | Público |
| `/login` | GET/POST | Público |
| `/cadastro` | GET/POST | Público |
| `/mercado` (lista + busca) | GET | ADMIN, CLIENTE |
| `/mercado/{id}` (detalhe) | GET | ADMIN, CLIENTE |
| `/mercado/novo` | GET | ADMIN |
| `/mercado` (criar) | POST | ADMIN |
| `/mercado/{id}/editar` | GET | ADMIN |
| `/mercado/{id}` (atualizar) | POST | ADMIN |
| `/mercado/{id}/excluir` | POST | ADMIN |
| `/admin` | GET | ADMIN |
| `/logout` | POST | Autenticado |

A proteção é feita no backend pelo `SecurityFilterChain`. A interface também esconde os botões que o perfil não pode usar (`sec:authorize`). Se um CLIENTE tentar acessar uma rota de ADMIN pela URL, ele cai na página **403 – Acesso negado**.

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/", "/index", "/login", "/cadastro", "/acesso-negado", "/error").permitAll()
        .requestMatchers("/css/**", "/img/**", "/favicon.ico").permitAll()
        .requestMatchers("/admin/**").hasRole("ADMIN")
        .requestMatchers("/mercado/novo", "/mercado/*/editar", "/mercado/*/excluir").hasRole("ADMIN")
        .requestMatchers(HttpMethod.POST, "/mercado/**").hasRole("ADMIN")
        .requestMatchers(HttpMethod.GET, "/mercado", "/mercado/*").hasAnyRole("ADMIN", "CLIENTE")
        .anyRequest().authenticated()
)
.formLogin(form -> form
        .loginPage("/login")                 // tela de login própria (não a padrão do Spring)
        .usernameParameter("email")
        .passwordParameter("senha")
        .successHandler(loginSuccessHandler) // redireciona por perfil
        .failureHandler(loginFailureHandler) // usuário inexistente -> Sign Up
)
.exceptionHandling(ex -> ex.accessDeniedPage("/acesso-negado"));
```

### Implementação da tela de login

A tela padrão do Spring Security foi substituída por `templates/login.html`, com layout próprio em duas colunas, identidade visual do Mercado Express e mensagens de feedback (credenciais inválidas, logout e conta criada).

1. O formulário envia `email` e `senha` via `POST /login`, com token **CSRF** incluído automaticamente pelo Thymeleaf.
2. O `UsuarioDetailsService` busca o usuário em `TDS_Users_Mercado` pelo e-mail e entrega ao Spring Security o hash BCrypt e o perfil (`ROLE_ADMIN` ou `ROLE_CLIENTE`).
3. **Sucesso:** o `LoginSuccessHandler` manda o ADMIN para `/admin` e o CLIENTE para `/mercado`.
4. **Falha:** o `LoginFailureHandler` verifica se o e-mail existe no banco:
   - **Não existe:** redireciona para a tela de **Sign Up** (`/cadastro?naoEncontrado`), com o e-mail já preenchido. Ele é guardado na sessão, não na URL.
   - **Existe** (senha errada): volta para `/login?error`.

> Por padrão, o Spring Security esconde a `UsernameNotFoundException` e a transforma em `BadCredentialsException`. Por isso a verificação de "usuário não cadastrado" é feita no `LoginFailureHandler`, consultando o banco diretamente.

![Print login](assets/print_login.png)

**Exemplo 1: e-mail não cadastrado.** O usuário digita `novo@email.com` no login e é levado ao Sign Up com o aviso "Não encontramos uma conta com esse e-mail" e o campo e-mail preenchido.

![Print redirecionamento para cadastro](assets/print_login_redireciona_cadastro.png)

**Exemplo 2: senha errada.** A tela de login exibe "E-mail ou senha inválidos".

![Print login erro](assets/print_login_erro.png)

### Tela de cadastro (Sign Up)

`templates/cadastro.html` valida nome, e-mail, tamanho mínimo da senha (6), confirmação de senha e e-mail duplicado. A conta é salva em `TDS_Users_Mercado` com perfil **CLIENTE** e senha em **BCrypt**. Depois o usuário é levado ao login com a mensagem "Conta criada com sucesso".

![Print cadastro](assets/print_cadastro.png)

## Telas da aplicação

### Landing page (`index.html`)

Página inicial pública, com botões "Entrar" e "Criar conta".

![Print index](assets/print_index.png)

### Painel administrativo (ADMIN)

Mostra os totais de produtos, clientes e administradores, além da lista de usuários cadastrados.

![Print painel admin](assets/print_admin.png)

### Read: lista de produtos e busca

Consulta a tabela `TDS_Sec_MVC_TB_Mercado` e mostra o resultado no navegador. Tem busca por nome. O ADMIN vê os botões **Editar**, **Excluir** e **+ Novo produto**. O CLIENTE vê apenas **Ver**.

| ADMIN | CLIENTE |
|-------|---------|
| ![Lista admin](assets/print_lista_admin.png) | ![Lista cliente](assets/print_lista_cliente.png) |

### Read: detalhe do produto

![Print detalhe](assets/print_detalhe.png)

### Create: novo produto (ADMIN)

Formulário com validação (campos obrigatórios, preço e tamanho maiores que zero, estoque não negativo).

![Print criar](assets/print_criar.png)

### Update: editar produto (ADMIN)

![Print editar](assets/print_editar.png)

### Delete: excluir produto (ADMIN)

Botão **Excluir** com confirmação antes do `POST /mercado/{id}/excluir`.

![Print excluir](assets/print_excluir.png)

### Acesso negado (403)

Exibida quando um CLIENTE tenta acessar uma rota exclusiva de ADMIN (ex.: `/mercado/novo`).

![Print 403](assets/print_403.png)

## Deploy (Render)

O projeto tem um `Dockerfile` (build multi-stage com Maven e execução com JRE 21) e um `render.yaml` (Blueprint), que cria **o banco PostgreSQL e o Web Service** de uma vez:

1. No [Render](https://render.com): **New → Blueprint** e selecione este repositório.
2. O Render lê o `render.yaml`, cria o banco `mercado-express-db` e o serviço `cp5p1-java`, e liga automaticamente as variáveis `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME` e `DB_PASSWORD`.
3. Quando solicitado, informe o valor de `ADMIN_SENHA` (a senha do ADMIN de produção).
4. Depois do build, a aplicação fica disponível em `https://<nome-do-servico>.onrender.com`.

> No plano gratuito, o serviço "dorme" após um tempo sem acesso, e o primeiro acesso pode levar cerca de 1 minuto.

## Testes automatizados

`SegurancaFluxoTest` (13 testes, MockMvc + H2) cobre:

- landing page pública e tela de login personalizada
- usuário anônimo redirecionado ao login
- login do ADMIN indo para `/admin` e do CLIENTE indo para `/mercado`
- e-mail não cadastrado redirecionando para o Sign Up, com o e-mail preenchido
- senha errada voltando para `/login?error`
- cadastro criando CLIENTE com senha BCrypt, e validação de senhas diferentes
- CLIENTE recebendo 403 nas rotas de escrita e no painel
- CRUD completo pelo ADMIN e validação do formulário de produto

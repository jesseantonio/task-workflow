# task-workflow

Serviço Spring Boot que automatiza o fluxo de vida de uma tarefa (desenvolvimento → revisão → testes → concluída), usando RabbitMQ como backbone de mensageria em uma arquitetura orientada a eventos. Inclui controle de tentativas e Dead Letter Queue (DLQ) para tarefas que falham repetidamente.

## 1. Visão Geral

A implementação acontece em um único serviço Spring Boot, que hospeda tanto o produtor de tarefas quanto todos os "workers" do fluxo (como `@RabbitListener`s), explorando os conceitos de arquitetura orientada a eventos (produtores, exchanges, filas e consumidores desacoplados) sem a complexidade operacional de múltiplos microsserviços.

## 2. Arquitetura

### 2.1 Componentes

| Componente | Papel | Publica em | Consome de |
|---|---|---|---|
| Task Workflow | Produtor inicial | `task.exchange` (`task.created`) | — |
| Development Worker | Consumidor/Produtor | `workflow.exchange` (`development.completed`) | `dev.queue` |
| Review Worker | Consumidor/Produtor | `workflow.exchange` (`review.approved` / `review.rejected`) | `review.queue` |
| Test Worker | Consumidor/Produtor | `workflow.exchange` (`test.succeeded` / `test.failed`) | `test.queue` |
| Retry Handler | Consumidor/Produtor | `workflow.exchange` (`task.retry`) ou `dead-letter.exchange` (`task.dead`) | `retry.queue` |
| Completed Monitor | Consumidor final | — | `completed.queue` |
| DLQ Monitor | Consumidor final | — | `task.dlq` |

Todos os "workers" são beans `@RabbitListener` dentro do mesmo processo, não serviços separados.

### 2.2 Fluxo de mensagens

```
Task Workflow --task.created--> task.exchange --> dev.queue --> Development Worker
                                                                      │
                                                          development.completed
                                                                      ▼
                                                              workflow.exchange
                                                                      │
                                                                review.queue
                                                                      │
                                                                Review Worker
                                                     ┌───────┴────────┐
                                              review.approved   review.rejected
                                                     ▼                 ▼
                                              workflow.exchange   workflow.exchange
                                                     │                 │
                                                test.queue         retry.queue
                                                     │                 │
                                                Test Worker        Retry Handler
                                          ┌──────────┴─────────┐       │
                                   test.succeeded         test.failed  │
                                          ▼                    ▼       ▼
                                   workflow.exchange     workflow.exchange
                                          │                    │
                                   completed.queue         retry.queue
                                          │                    │
                                   Completed Monitor      Retry Handler
                                                        (tentativas < 3)
                                                                │
                                                        task.retry (volta ao dev.queue)
                                                                │
                                                        (tentativas >= 3)
                                                                │
                                                     dead-letter.exchange
                                                                │
                                                          task.dead
                                                                │
                                                           task.dlq
                                                                │
                                                          DLQ Monitor
```

O Retry Handler verifica o número de tentativas: abaixo de três, a tarefa retorna para desenvolvimento (`task.retry` → `dev.queue`); ao atingir três, vai para a `task.dlq`.

### 2.3 Exchanges, filas e routing keys

| Exchange | Tipo | Routing key | Fila destino |
|---|---|---|---|
| `task.exchange` | topic | `task.created` | `dev.queue` |
| `workflow.exchange` | topic | `development.completed` | `review.queue` |
| `workflow.exchange` | topic | `review.approved` | `test.queue` |
| `workflow.exchange` | topic | `review.rejected` | `retry.queue` |
| `workflow.exchange` | topic | `test.succeeded` | `completed.queue` |
| `workflow.exchange` | topic | `test.failed` | `retry.queue` |
| `workflow.exchange` | topic | `task.retry` | `dev.queue` |
| `dead-letter.exchange` | topic | `task.dead` | `task.dlq` |

### 2.4 Mensagens

| Mensagem | Descrição |
|---|---|
| `task.created` | Tarefa criada |
| `development.completed` | Desenvolvimento concluído |
| `review.approved` | Revisão aprovada |
| `review.rejected` | Revisão rejeitada |
| `test.succeeded` | Testes concluídos com sucesso |
| `test.failed` | Falha nos testes |
| `task.retry` | Nova tentativa solicitada |
| `task.dead` | Limite de tentativas atingido |

O payload (`TaskMessage`) carrega só `taskId`, `correlationId` e `attempts`; o status é sempre lido do banco antes de processar, nunca do payload.

### 2.5 Estados e rastreabilidade

```
CREATED ──► IN_DEV ──► IN_REVIEW ──┬─► IN_TEST ──┬─► COMPLETED
                ▲                  │             │
                │                  ▼             ▼
                └──────────── RETRYING ◄─────────┘
                                    │
                                    ▼ (attempts >= 3)
                                   DEAD
```

| Estado | Significado |
|---|---|
| `CREATED` | Recebida via API |
| `IN_DEV` | Em processamento pelo Development Worker |
| `IN_REVIEW` | Em análise pelo Review Worker |
| `IN_TEST` | Em execução de testes |
| `RETRYING` | Rejeitada/falhou, aguardando reprocessamento |
| `COMPLETED` | Concluída com sucesso |
| `DEAD` | Tentativas esgotadas |

Cada worker só aplica uma transição se o estado atual permitir (`Task#transitionTo`), o que também garante idempotência contra redelivery.

Um `correlationId` (o próprio `taskId`) é propagado em todas as mensagens (header AMQP + payload) e no MDC do logger, permitindo filtrar o histórico completo de uma tarefa nos logs.

### 2.6 Escalabilidade, confiabilidade e tolerância a falhas

- Concorrência configurável por listener (`concurrency`/`max-concurrency`).
- Mensagens persistentes, ack manual, publisher confirms/returns.
- Contador de tentativas persistido na `Task`, verificado pelo Retry Handler.
- DLQ (`task.dlq`) para tarefas que esgotaram tentativas.
- Idempotência via validação de estado antes de cada transição.

### 2.7 IA nos workers (opcional)

Com `task-workflow.ai.enabled: false` (padrão), Review e Test Worker decidem por sorteio (`task-workflow.simulation.*`) — não há processamento real. Com `true`, os workers usam o **Claude Code CLI** (`claude -p`, não-interativo), em dois modos:

- **Texto** (`POST /tasks` sem `repositoryPath`): Development gera um trecho de código em texto; Review e Test julgam esse texto. Nada toca em disco.
- **Repositório real** (`POST /tasks` com `repositoryPath` apontando para um repo git local): a tarefa é implementada de fato nesse projeto.

**Modo repositório real:**

1. Cada tarefa roda num `git worktree` isolado, numa branch própria (`task-workflow/<correlationId>`) criada a partir do HEAD do repositório. Retries reaproveitam o mesmo worktree.
2. O Development Worker nunca tem acesso a arquivos ou comandos (`--tools ""` em toda chamada ao Claude). Em vez disso, gera um **patch em texto** em duas etapas: pergunta quais arquivos precisa ler (dada a lista de `git ls-files`), lê o conteúdo via Java e pede o patch final. O `GitService` aplica esse patch via `git apply` — decisão determinística, nunca da IA.
3. Review avalia o `git diff` real resultante do patch.
4. Test roda o `testCommand` informado (se houver) e decide pelo exit code real; sem `testCommand`, cai na mesma análise estática por IA do modo texto.
5. Ao concluir, o patch é reaplicado (sem commit) no diretório de trabalho do repositório principal — a mudança aparece como alteração comum não commitada, para revisão normal via git. Como registro adicional, o resultado também é commitado na branch isolada da tarefa. O worktree é removido em seguida.
6. Ao esgotar as tentativas, o worktree e a branch são descartados.

O serviço nunca dá push nem mergeia/commita na branch principal sozinho.

Resultados de cada etapa ficam em `Task` (`developmentOutput`, `reviewFeedback`, `testReport`) e em `GET /tasks/{id}`.

**Por que o CLI em vez da API paga por token:** uma assinatura Claude Code (Pro/Max) não inclui uma `ANTHROPIC_API_KEY` — API e assinatura são produtos/billings separados. O `claude -p` reaproveita a autenticação OAuth já feita pelo CLI, sem gerar cobrança extra.

**Segurança:** toda chamada usa `--tools ""`, roda em pasta temporária isolada (modo texto) ou no worktree da tarefa, com `--max-budget-usd` como teto de gasto e `timeout-seconds` configurável. Entrada do usuário (`name`, `repositoryPath`, `testCommand`) vai como argumentos isolados do `ProcessBuilder` — sem shell, sem risco de injeção na chamada ao Claude.

`testCommand`, por sua natureza, executa um comando real no host. A API não deve ser exposta a chamadores não confiáveis.

## 3. Stack

- Java 17, Spring Boot 3.3
- `spring-boot-starter-amqp`, `spring-boot-starter-web`, `spring-boot-starter-data-jpa` + PostgreSQL
- `spring-boot-starter-actuator`, `spring-boot-starter-validation`
- Lombok
- Docker Compose (RabbitMQ + PostgreSQL)

## 4. Estrutura de pacotes

```
com.taskworkflow
├── api              # TaskController, DTOs
├── domain           # Task, TaskStatus
├── repository       # TaskRepository
├── service          # TaskService, TaskPublisher
├── messaging
│   ├── Exchanges / Queues / RoutingKeys
│   ├── CorrelationIdSupport
│   ├── event        # TaskMessage
│   └── listener     # um @RabbitListener por etapa + ListenerAckSupport
├── ai               # ClaudeCodeClient, TaskAiService, TestCommandRunner
├── git              # GitService
├── config           # RabbitMQConfig, TaskWorkflowProperties
├── exception        # TaskNotFoundException, GlobalExceptionHandler
└── TaskWorkflowApplication
```

## 5. Como executar

**Tudo via Docker Compose** (RabbitMQ + Postgres + backend + [frontend](frontend/)):

```bash
docker compose up -d --build
```

Frontend em `http://localhost:5173`, API em `http://localhost:8080`. Nesse modo a IA fica
desligada (`task-workflow.ai.enabled=false`) — o CLI do Claude Code precisa de login OAuth feito
na máquina, e isso não dá pra automatizar dentro do container; os workers usam a simulação
aleatória (`task-workflow.simulation.*`).

**Com IA real**: suba só a infra pelo compose e rode o backend direto na máquina (que já tem o
CLI autenticado):

```bash
docker compose up -d postgres rabbitmq
mvn spring-boot:run
cd frontend && npm run dev   # http://localhost:5173 (Vite dev server, com hot reload)
```

```bash
curl -X POST http://localhost:8080/tasks -H "Content-Type: application/json" -d "{\"name\":\"minha-tarefa\"}"
curl http://localhost:8080/tasks/{id}
```

RabbitMQ Management UI em `http://localhost:15672` (guest/guest).

Parâmetros de `POST /tasks`:

| Campo | Obrigatório | Descrição |
|---|---|---|
| `name` | sim | Descrição da tarefa |
| `repositoryPath` | não | Repositório git local — ativa o modo repositório real (requer `ai.enabled=true`) |
| `testCommand` | não | Comando de teste real (ex: `mvn test`), usado só com `repositoryPath` |

Configuração em `application.yml` (`task-workflow.*`): `max-attempts`, `simulation.review-approval-rate`, `simulation.test-success-rate`, `ai.enabled`, `ai.model`, `ai.timeout-seconds`, `ai.max-budget-usd`.

Para habilitar a IA: `npm install -g @anthropic-ai/claude-code`, autenticar (`claude` na primeira execução) e setar `task-workflow.ai.enabled: true`.

## 6. Notas operacionais

- A porta do PostgreSQL no `docker-compose.yml` é `5433`, não a padrão `5432` — evita conflito com instalações nativas do Postgres na mesma máquina. O serviço `backend` do compose fala com o Postgres pela porta interna da rede Docker (`5432`), não pela `5433` (que é só o mapeamento pro host).
- `Dockerfile` (backend) e `frontend/Dockerfile` são builds multi-stage; o backend inclui `git` na imagem final (necessário pro `GitService` em modo repositório real) mas não o CLI do Claude Code. `docker-compose.yml` monta `./test-html` em `/workspace/test-html` dentro do container do backend, então dá pra usar esse caminho como `repositoryPath` mesmo rodando containerizado (sem IA real, só serve pra exercitar o fluxo de patch/branch com a simulação).
- No Windows, o `claude` instalado via npm é um script `.cmd` que aponta para um `.exe` nativo; o `ClaudeCodeClient` resolve e chama esse `.exe` diretamente (via `PATH`, nunca hardcoded), e escapa aspas duplas nos argumentos — o `ProcessBuilder` do Java no Windows corrompe aspas embutidas em argumentos ao montar a linha de comando.
- `GitService.applyPatch` usa `git apply --recount`: LLMs costumam errar a contagem de linhas no cabeçalho do hunk do diff; essa flag deixa o git recalcular.
- `TaskPublisher` adia a publicação no RabbitMQ para depois do commit da transação (`TransactionSynchronizationManager`) — sem isso, um consumidor pode processar a mensagem antes da tarefa estar visível no banco.

## 7. Pendências

- Testes de integração com Testcontainers (RabbitMQ + PostgreSQL)
- Métricas customizadas via Micrometer

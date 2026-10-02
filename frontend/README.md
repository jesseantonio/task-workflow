# task-workflow frontend

Front-end em React + TypeScript + Vite para o [task-workflow](../README.md): criar tarefas e acompanhar o
fluxo `CREATED → IN_DEV → IN_REVIEW → IN_TEST → COMPLETED` (com `RETRYING`/`DEAD`) em um board com um
"bonequinho" por tarefa em cada coluna de etapa.

## Como executar

Com o backend rodando em `http://localhost:8080` (ver README na raiz do repo):

```bash
npm install
npm run dev
```

Abre em `http://localhost:5173`. O board faz polling em `GET /tasks` a cada 2s — não há push em tempo real
(o backend não expõe WebSocket/SSE).

Para apontar para outro host de backend, copie `.env.example` para `.env` e ajuste `VITE_API_BASE_URL`.

## Estrutura

```
src/
├── api/          # cliente HTTP (fetch) e chamadas a /tasks
├── components/   # CreateTaskForm, Board, StatusColumn, TaskAvatar, TaskDetails
├── statusConfig.ts  # label/ícone/cor por TaskStatus
├── types.ts      # espelha TaskStatus/TaskResponse do backend
└── App.tsx
```

Estado do servidor é gerenciado via TanStack Query (cache + polling); não há store global — o board e o
formulário só precisam da lista de tasks e de uma mutation de criação.

## Limitação conhecida

O campo "caminho do repositório" é um input de texto simples: o navegador não tem acesso ao caminho
absoluto de pastas do filesystem por motivos de segurança, então não há um "escolher pasta" nativo. O
valor digitado é o `repositoryPath` enviado direto para `POST /tasks` (precisa ser um caminho válido no
host onde o backend roda).

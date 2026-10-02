export const TASK_STATUSES = [
  'CREATED',
  'IN_DEV',
  'IN_REVIEW',
  'IN_TEST',
  'RETRYING',
  'PAUSED',
  'COMPLETED',
  'CANCELLED',
  'DEAD',
] as const

export type TaskStatus = (typeof TASK_STATUSES)[number]

export interface Task {
  id: string
  correlationId: string
  name: string
  status: TaskStatus
  /** Preenchido quando status é PAUSED ou CANCELLED: em que etapa a tarefa estava antes. */
  pausedFromStatus: TaskStatus | null
  attempts: number
  createdAt: string
  updatedAt: string
  developmentOutput: string | null
  reviewFeedback: string | null
  testReport: string | null
  repositoryPath: string | null
  branchName: string | null
}

export interface CreateTaskInput {
  name: string
  repositoryPath?: string
  testCommand?: string
}

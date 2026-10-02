import type { Task, TaskStatus } from './types'

export type ContentField = 'developmentOutput' | 'reviewFeedback' | 'testReport'

export interface Stage {
  status: TaskStatus
  label: string
  workingLabel: string
  icon: string
  color: string
  field: ContentField | null
}

/** Ordem feliz do pipeline (ver TaskStatus no backend). RETRYING/PAUSED/CANCELLED/DEAD são tratados à parte. */
export const PIPELINE: Stage[] = [
  { status: 'CREATED', label: 'Criada', workingLabel: 'Na fila', icon: '📝', color: '#9ca3af', field: null },
  { status: 'IN_DEV', label: 'Dev', workingLabel: 'Codando...', icon: '💻', color: '#3b82f6', field: 'developmentOutput' },
  { status: 'IN_REVIEW', label: 'Review', workingLabel: 'Revisando...', icon: '🔍', color: '#f59e0b', field: 'reviewFeedback' },
  { status: 'IN_TEST', label: 'Test', workingLabel: 'Testando...', icon: '🧪', color: '#a855f7', field: 'testReport' },
  { status: 'COMPLETED', label: 'Concluída', workingLabel: 'Concluída', icon: '🏁', color: '#22c55e', field: null },
]

export type StageState = 'done' | 'working' | 'retrying' | 'pending' | 'dead' | 'paused' | 'cancelled'

function activeIndex(status: TaskStatus): number {
  switch (status) {
    case 'CREATED':
      return 0
    case 'IN_DEV':
    case 'RETRYING':
      return 1
    case 'IN_REVIEW':
      return 2
    case 'IN_TEST':
      return 3
    case 'COMPLETED':
    case 'DEAD':
      return 4
    case 'PAUSED':
    case 'CANCELLED':
      // nunca chamado diretamente: stageState já resolve para pausedFromStatus antes.
      return 0
  }
}

export function stageState(stageIndex: number, task: Task): StageState {
  const suspended = task.status === 'PAUSED' || task.status === 'CANCELLED'
  const effectiveStatus = suspended ? (task.pausedFromStatus ?? 'CREATED') : task.status
  const current = activeIndex(effectiveStatus)

  if (stageIndex === 4 && task.status === 'DEAD') return 'dead'
  if (stageIndex < current) return 'done'
  if (stageIndex > current) return 'pending'
  if (suspended) return task.status === 'PAUSED' ? 'paused' : 'cancelled'
  if (effectiveStatus === 'RETRYING') return stageIndex === 1 ? 'retrying' : 'pending'
  if (stageIndex === 4) return effectiveStatus === 'COMPLETED' ? 'done' : 'pending'
  return 'working'
}

export function stageContent(stage: Stage, task: Task): string | null {
  if (!stage.field) return null
  return task[stage.field]
}

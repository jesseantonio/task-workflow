import type { TaskStatus } from './types'

export interface StatusConfig {
  label: string
  icon: string
  color: string
  active: boolean
}

export const STATUS_CONFIG: Record<TaskStatus, StatusConfig> = {
  CREATED: { label: 'Criada', icon: '📝', color: '#9ca3af', active: false },
  IN_DEV: { label: 'Dev', icon: '💻', color: '#3b82f6', active: true },
  IN_REVIEW: { label: 'Review', icon: '🔍', color: '#f59e0b', active: true },
  IN_TEST: { label: 'Test', icon: '🧪', color: '#a855f7', active: true },
  RETRYING: { label: 'Retry', icon: '🔁', color: '#f97316', active: true },
  PAUSED: { label: 'Pausada', icon: '⏸️', color: '#64748b', active: false },
  COMPLETED: { label: 'Concluída', icon: '✅', color: '#22c55e', active: false },
  CANCELLED: { label: 'Cancelada', icon: '🚫', color: '#71717a', active: false },
  DEAD: { label: 'Morta', icon: '💀', color: '#ef4444', active: false },
}

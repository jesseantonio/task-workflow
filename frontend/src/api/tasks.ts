import { apiFetch } from './client'
import type { CreateTaskInput, Task } from '../types'

export function listTasks(): Promise<Task[]> {
  return apiFetch<Task[]>('/tasks')
}

export function getTask(id: string): Promise<Task> {
  return apiFetch<Task>(`/tasks/${id}`)
}

export function createTask(input: CreateTaskInput): Promise<Task> {
  return apiFetch<Task>('/tasks', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function deleteAllTasks(): Promise<void> {
  return apiFetch<void>('/tasks', { method: 'DELETE' })
}

export function pauseTask(id: string): Promise<Task> {
  return apiFetch<Task>(`/tasks/${id}/pause`, { method: 'POST' })
}

export function resumeTask(id: string): Promise<Task> {
  return apiFetch<Task>(`/tasks/${id}/resume`, { method: 'POST' })
}

export function cancelTask(id: string): Promise<Task> {
  return apiFetch<Task>(`/tasks/${id}/cancel`, { method: 'POST' })
}

export function reprocessStuckTasks(): Promise<{ reprocessed: number }> {
  return apiFetch<{ reprocessed: number }>('/tasks/reprocess', { method: 'POST' })
}

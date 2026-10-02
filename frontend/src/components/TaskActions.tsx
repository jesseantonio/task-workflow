import { useMutation, useQueryClient } from '@tanstack/react-query'
import { cancelTask, pauseTask, resumeTask } from '../api/tasks'
import type { Task } from '../types'

interface TaskActionsProps {
  task: Task
}

const TERMINAL = new Set(['COMPLETED', 'DEAD', 'CANCELLED'])

export function TaskActions({ task }: TaskActionsProps) {
  const queryClient = useQueryClient()

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['task', task.id] })
    queryClient.invalidateQueries({ queryKey: ['tasks'] })
  }

  const pauseMutation = useMutation({ mutationFn: () => pauseTask(task.id), onSuccess: invalidate })
  const resumeMutation = useMutation({ mutationFn: () => resumeTask(task.id), onSuccess: invalidate })
  const cancelMutation = useMutation({
    mutationFn: () => cancelTask(task.id),
    onSuccess: invalidate,
  })

  if (TERMINAL.has(task.status)) return null

  const isPaused = task.status === 'PAUSED'
  const busy = pauseMutation.isPending || resumeMutation.isPending || cancelMutation.isPending

  function handleCancel() {
    if (!window.confirm('Cancelar esta tarefa? Essa ação não pode ser desfeita.')) return
    cancelMutation.mutate()
  }

  return (
    <div className="flex items-center gap-2">
      {isPaused ? (
        <button
          onClick={() => resumeMutation.mutate()}
          disabled={busy}
          className="rounded-lg border border-green-200 px-2.5 py-1 text-xs font-medium text-green-700 transition hover:bg-green-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          ▶ Retomar
        </button>
      ) : (
        <button
          onClick={() => pauseMutation.mutate()}
          disabled={busy}
          className="rounded-lg border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          ⏸ Pausar
        </button>
      )}
      <button
        onClick={handleCancel}
        disabled={busy}
        className="rounded-lg border border-red-200 px-2.5 py-1 text-xs font-medium text-red-600 transition hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-50"
      >
        ✕ Cancelar
      </button>
    </div>
  )
}

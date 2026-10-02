import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { reprocessStuckTasks } from '../api/tasks'

export function ReprocessButton() {
  const queryClient = useQueryClient()
  const [lastResult, setLastResult] = useState<number | null>(null)

  const mutation = useMutation({
    mutationFn: reprocessStuckTasks,
    onSuccess: ({ reprocessed }) => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
      setLastResult(reprocessed)
    },
  })

  function handleClick() {
    const confirmed = window.confirm(
      'Reenviar a etapa atual de toda tarefa ativa (criada/dev/review/test/retry)?\n\n' +
        'Use só se o backend caiu e alguma tarefa ficou travada sem processar. ' +
        'Se alguma dessas tarefas ainda estiver em andamento de verdade, isso pode duplicar o processamento dela.',
    )
    if (!confirmed) return
    mutation.mutate()
  }

  return (
    <div className="flex items-center gap-2">
      <button
        onClick={handleClick}
        disabled={mutation.isPending}
        className="rounded-lg border border-blue-200 px-3 py-1.5 text-xs font-medium text-blue-700 transition hover:bg-blue-50 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {mutation.isPending ? 'Reprocessando...' : '↻ Reprocessar'}
      </button>
      {lastResult !== null && !mutation.isPending && (
        <span className="text-xs text-gray-400">{lastResult} reenviada(s)</span>
      )}
    </div>
  )
}

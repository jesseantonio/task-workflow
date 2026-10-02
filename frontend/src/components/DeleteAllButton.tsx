import { useMutation, useQueryClient } from '@tanstack/react-query'
import { deleteAllTasks } from '../api/tasks'

interface DeleteAllButtonProps {
  disabled: boolean
  onDeleted: () => void
}

export function DeleteAllButton({ disabled, onDeleted }: DeleteAllButtonProps) {
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: deleteAllTasks,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
      onDeleted()
    },
  })

  function handleClick() {
    if (!window.confirm('Remover todas as tarefas? Essa ação não pode ser desfeita.')) return
    mutation.mutate()
  }

  return (
    <button
      onClick={handleClick}
      disabled={disabled || mutation.isPending}
      className="rounded-lg border border-red-200 px-3 py-1.5 text-xs font-medium text-red-600 transition hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-50"
    >
      {mutation.isPending ? 'Removendo...' : 'Remover todas'}
    </button>
  )
}

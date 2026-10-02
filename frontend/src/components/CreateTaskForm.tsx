import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { createTask } from '../api/tasks'
import { ApiError } from '../api/client'

export function CreateTaskForm() {
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [repositoryPath, setRepositoryPath] = useState('')
  const [testCommand, setTestCommand] = useState('')

  const mutation = useMutation({
    mutationFn: createTask,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
      setName('')
      setRepositoryPath('')
      setTestCommand('')
    },
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate({
      name,
      repositoryPath: repositoryPath.trim() || undefined,
      testCommand: testCommand.trim() || undefined,
    })
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3 rounded-xl border border-gray-200 bg-white p-4">
      <div className="flex flex-col gap-1">
        <label htmlFor="name" className="text-sm font-medium text-gray-700">
          Descrição da tarefa
        </label>
        <input
          id="name"
          required
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="ex: adicionar endpoint de health check"
          className="rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none"
        />
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="repositoryPath" className="text-sm font-medium text-gray-700">
          Caminho do repositório <span className="text-gray-400">(opcional)</span>
        </label>
        <input
          id="repositoryPath"
          value={repositoryPath}
          onChange={(event) => setRepositoryPath(event.target.value)}
          placeholder="C:\Users\voce\git\meu-repo"
          className="rounded-lg border border-gray-300 px-3 py-2 text-sm font-mono focus:border-blue-500 focus:outline-none"
        />
        <p className="text-xs text-gray-400">
          Caminho local, no mesmo host do backend. Deixe vazio para o modo texto (sem tocar em disco).
        </p>
      </div>

      {repositoryPath.trim() && (
        <div className="flex flex-col gap-1">
          <label htmlFor="testCommand" className="text-sm font-medium text-gray-700">
            Comando de teste <span className="text-gray-400">(opcional)</span>
          </label>
          <input
            id="testCommand"
            value={testCommand}
            onChange={(event) => setTestCommand(event.target.value)}
            placeholder="mvn test"
            className="rounded-lg border border-gray-300 px-3 py-2 text-sm font-mono focus:border-blue-500 focus:outline-none"
          />
        </div>
      )}

      {mutation.isError && (
        <p className="text-sm text-red-600">
          {mutation.error instanceof ApiError ? mutation.error.message : 'Falha ao criar a tarefa.'}
        </p>
      )}

      <button
        type="submit"
        disabled={mutation.isPending}
        className="self-start rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700 disabled:opacity-50"
      >
        {mutation.isPending ? 'Criando...' : 'Criar tarefa'}
      </button>
    </form>
  )
}

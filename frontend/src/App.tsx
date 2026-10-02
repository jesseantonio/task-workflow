import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { listTasks } from './api/tasks'
import { Board } from './components/Board'
import { CreateTaskForm } from './components/CreateTaskForm'
import { DeleteAllButton } from './components/DeleteAllButton'
import { ReprocessButton } from './components/ReprocessButton'
import { TaskDetails } from './components/TaskDetails'

function App() {
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null)

  const { data: tasks = [], isError } = useQuery({
    queryKey: ['tasks'],
    queryFn: listTasks,
    refetchInterval: 1500,
  })

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6 p-6">
      <header>
        <h1 className="text-2xl font-semibold text-gray-900">Task Workflow</h1>
        <p className="text-sm text-gray-500">Acompanhe o fluxo dev → review → test em tempo real.</p>
      </header>

      <CreateTaskForm />

      {isError && (
        <p className="text-sm text-red-600">
          Não foi possível carregar as tarefas. O backend está rodando em localhost:8080?
        </p>
      )}

      <div className="flex items-center justify-between">
        <span className="text-sm text-gray-500">{tasks.length} tarefa(s)</span>
        <div className="flex items-center gap-2">
          <ReprocessButton />
          <DeleteAllButton disabled={tasks.length === 0} onDeleted={() => setSelectedTaskId(null)} />
        </div>
      </div>

      <Board tasks={tasks} onSelectTask={(task) => setSelectedTaskId(task.id)} />

      {selectedTaskId && <TaskDetails taskId={selectedTaskId} onClose={() => setSelectedTaskId(null)} />}
    </div>
  )
}

export default App

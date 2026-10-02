import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { getTask } from '../api/tasks'
import { PIPELINE, stageContent, type Stage } from '../stageConfig'
import { STATUS_CONFIG } from '../statusConfig'
import type { Task } from '../types'
import { TaskActions } from './TaskActions'
import { TaskPipeline } from './TaskPipeline'

interface TaskDetailsProps {
  taskId: string
  onClose: () => void
}

function defaultStage(task: Task): Stage | null {
  // seleciona por padrão a última etapa que já gerou algo (ou a atual, se ainda não gerou nada)
  const withContent = [...PIPELINE].reverse().find((stage) => stage.field && task[stage.field])
  if (withContent) return withContent
  return PIPELINE.find((stage) => stage.status === task.status) ?? null
}

export function TaskDetails({ taskId, onClose }: TaskDetailsProps) {
  const [selectedStage, setSelectedStage] = useState<Stage | null>(null)

  const { data: task, isLoading, isError } = useQuery({
    queryKey: ['task', taskId],
    queryFn: () => getTask(taskId),
    refetchInterval: 1500,
  })

  useEffect(() => {
    setSelectedStage(null)
  }, [taskId])

  useEffect(() => {
    if (task && selectedStage === null) {
      setSelectedStage(defaultStage(task))
    }
  }, [task, selectedStage])

  return (
    <div className="fixed inset-0 flex items-center justify-center bg-black/40 p-4" onClick={onClose}>
      <div
        className="flex max-h-[85vh] w-full max-w-xl flex-col gap-4 overflow-auto rounded-xl bg-white p-5 shadow-xl"
        onClick={(event) => event.stopPropagation()}
      >
        {isLoading && <p className="text-sm text-gray-400">Carregando...</p>}
        {isError && <p className="text-sm text-red-600">Não foi possível carregar a tarefa.</p>}

        {task && (
          <>
            <div className="flex items-start justify-between">
              <div>
                <h2 className="text-lg font-semibold text-gray-900">{task.name}</h2>
                <p className="text-sm text-gray-500">
                  {STATUS_CONFIG[task.status].icon} {STATUS_CONFIG[task.status].label}
                  {task.attempts > 0 && ` · tentativa ${task.attempts}`}
                </p>
              </div>
              <button onClick={onClose} className="text-gray-400 hover:text-gray-700">
                ✕
              </button>
            </div>

            <TaskActions task={task} />

            <TaskPipeline task={task} selectedStage={selectedStage} onSelectStage={setSelectedStage} />

            <div className="flex flex-col gap-1 rounded-lg bg-gray-50 p-3">
              <span className="text-xs font-medium uppercase tracking-wide text-gray-400">
                {selectedStage ? `O que a etapa "${selectedStage.label}" gerou` : 'Detalhes da etapa'}
              </span>
              {selectedStage && stageContent(selectedStage, task) ? (
                <pre className="max-h-64 overflow-auto whitespace-pre-wrap text-xs text-gray-700">
                  {stageContent(selectedStage, task)}
                </pre>
              ) : (
                <p className="text-xs text-gray-400">
                  {selectedStage?.field ? 'Essa etapa ainda não gerou conteúdo.' : 'Essa etapa não produz conteúdo próprio.'}
                </p>
              )}
            </div>

            {(task.repositoryPath || task.branchName) && (
              <div className="flex flex-col gap-0.5 text-xs text-gray-400">
                {task.repositoryPath && <span>Repositório: {task.repositoryPath}</span>}
                {task.branchName && <span>Branch: {task.branchName}</span>}
              </div>
            )}

            <p className="text-xs text-gray-400">
              Criada em {new Date(task.createdAt).toLocaleString()} · atualizada em{' '}
              {new Date(task.updatedAt).toLocaleString()}
            </p>
          </>
        )}
      </div>
    </div>
  )
}

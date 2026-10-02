import { TASK_STATUSES, type Task } from '../types'
import { StatusColumn } from './StatusColumn'

interface BoardProps {
  tasks: Task[]
  onSelectTask: (task: Task) => void
}

export function Board({ tasks, onSelectTask }: BoardProps) {
  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 2xl:grid-cols-9">
      {TASK_STATUSES.map((status) => (
        <StatusColumn
          key={status}
          status={status}
          tasks={tasks.filter((task) => task.status === status)}
          onSelectTask={onSelectTask}
        />
      ))}
    </div>
  )
}

import { AnimatePresence } from 'framer-motion'
import { STATUS_CONFIG } from '../statusConfig'
import type { Task, TaskStatus } from '../types'
import { TaskCard } from './TaskCard'

interface StatusColumnProps {
  status: TaskStatus
  tasks: Task[]
  onSelectTask: (task: Task) => void
}

export function StatusColumn({ status, tasks, onSelectTask }: StatusColumnProps) {
  const config = STATUS_CONFIG[status]

  return (
    <div className="flex min-h-[260px] w-full flex-col rounded-xl border border-gray-200 bg-white">
      <div className="flex items-center justify-between border-b border-gray-200 px-3 py-2">
        <span className="flex items-center gap-1.5 text-sm font-medium text-gray-700">
          <span>{config.icon}</span>
          {config.label}
        </span>
        <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-500">
          {tasks.length}
        </span>
      </div>
      <div className="flex flex-1 flex-col gap-1.5 p-2">
        <AnimatePresence initial={false}>
          {tasks.map((task) => (
            <TaskCard key={task.id} task={task} onClick={() => onSelectTask(task)} />
          ))}
        </AnimatePresence>
      </div>
    </div>
  )
}

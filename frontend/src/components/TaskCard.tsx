import { motion } from 'framer-motion'
import { STATUS_CONFIG } from '../statusConfig'
import type { Task } from '../types'

interface TaskCardProps {
  task: Task
  onClick: () => void
}

export function TaskCard({ task, onClick }: TaskCardProps) {
  const config = STATUS_CONFIG[task.status]

  return (
    <motion.button
      layoutId={task.id}
      layout
      onClick={onClick}
      initial={{ opacity: 0, scale: 0.8 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.8 }}
      transition={{ type: 'spring', stiffness: 500, damping: 35 }}
      className="flex w-full items-center gap-2 rounded-lg border border-gray-100 bg-gray-50 px-2 py-1.5 text-left transition hover:border-gray-300 hover:bg-white"
      title={task.name}
    >
      <span
        className="h-2 w-2 shrink-0 rounded-full"
        style={{ backgroundColor: config.color }}
      />
      <span className="truncate text-xs text-gray-700">{task.name}</span>
    </motion.button>
  )
}

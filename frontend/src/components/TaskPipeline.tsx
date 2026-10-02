import { motion } from 'framer-motion'
import { PIPELINE, stageState, type Stage } from '../stageConfig'
import type { Task } from '../types'

interface TaskPipelineProps {
  task: Task
  selectedStage: Stage | null
  onSelectStage: (stage: Stage) => void
}

function StageWorker({ stage, state, attempts }: { stage: Stage; state: string; attempts: number }) {
  const isWorking = state === 'working' || state === 'retrying'
  const isDone = state === 'done'
  const isDead = state === 'dead'
  const isPending = state === 'pending'
  const isPaused = state === 'paused'
  const isCancelled = state === 'cancelled'
  const dimmed = isPending || isPaused || isCancelled

  return (
    <div className="relative flex flex-col items-center gap-1">
      <motion.div
        animate={isWorking ? { y: [0, -5, 0] } : { y: 0 }}
        transition={isWorking ? { repeat: Infinity, duration: 0.9, ease: 'easeInOut' } : undefined}
        className="relative flex h-12 w-12 items-center justify-center rounded-full border-2 text-xl"
        style={{
          borderColor: dimmed ? '#e5e7eb' : stage.color,
          backgroundColor: dimmed ? '#f9fafb' : `${stage.color}1a`,
          opacity: isPending ? 0.5 : 1,
        }}
      >
        <span>{isDead ? '💀' : stage.icon}</span>

        {isWorking && (
          <motion.span
            className="absolute -right-1 -top-1 h-3.5 w-3.5 rounded-full border-2 border-white"
            style={{ backgroundColor: stage.color }}
            animate={{ scale: [1, 1.3, 1], opacity: [1, 0.6, 1] }}
            transition={{ repeat: Infinity, duration: 1 }}
          />
        )}
        {isDone && (
          <span className="absolute -right-1 -top-1 flex h-4 w-4 items-center justify-center rounded-full bg-green-500 text-[10px] text-white">
            ✓
          </span>
        )}
        {state === 'retrying' && (
          <span className="absolute -right-2 -top-2 rounded-full bg-orange-500 px-1 text-[10px] font-medium text-white">
            {attempts}
          </span>
        )}
        {isPaused && (
          <span className="absolute -right-1 -top-1 flex h-4 w-4 items-center justify-center rounded-full bg-slate-500 text-[9px] text-white">
            ⏸
          </span>
        )}
        {isCancelled && (
          <span className="absolute -right-1 -top-1 flex h-4 w-4 items-center justify-center rounded-full bg-zinc-500 text-[9px] text-white">
            ✕
          </span>
        )}
      </motion.div>
      <span className="text-[11px] font-medium text-gray-600">{stage.label}</span>
      {isWorking && (
        <span className="text-[10px] text-gray-400">
          {state === 'retrying' ? `corrigindo (${attempts})` : stage.workingLabel}
        </span>
      )}
      {isPaused && <span className="text-[10px] text-slate-400">pausada</span>}
      {isCancelled && <span className="text-[10px] text-zinc-400">cancelada</span>}
    </div>
  )
}

export function TaskPipeline({ task, selectedStage, onSelectStage }: TaskPipelineProps) {
  return (
    <div className="flex items-center justify-between gap-1 overflow-x-auto py-2">
      {PIPELINE.map((stage, index) => {
        const state = stageState(index, task)
        const clickable = state !== 'pending'

        return (
          <div key={stage.status} className="flex flex-1 items-center">
            {index > 0 && (
              <div
                className="mx-1 h-0.5 flex-1"
                style={{ backgroundColor: state === 'pending' ? '#e5e7eb' : stage.color, opacity: state === 'pending' ? 1 : 0.4 }}
              />
            )}
            <button
              type="button"
              disabled={!clickable}
              onClick={() => clickable && onSelectStage(stage)}
              className={`rounded-lg p-1 transition ${clickable ? 'cursor-pointer hover:bg-gray-50' : 'cursor-default'}`}
              style={selectedStage?.status === stage.status ? { boxShadow: `0 0 0 2px ${stage.color}` } : undefined}
            >
              <StageWorker stage={stage} state={state} attempts={task.attempts} />
            </button>
          </div>
        )
      })}
    </div>
  )
}

/**
 * FilterBar component
 *
 * Renders three filter buttons: All / ERROR / WARN.
 * Clicking a button updates the active filter in App state, which causes
 * the cluster list to re-render showing only matching clusters.
 *
 * Props:
 *   filter    (string)   — currently active filter: 'ALL' | 'ERROR' | 'WARN'
 *   setFilter (function) — setter from useState in App
 *   counts    (object)   — { all, error, warn } cluster counts for badge labels
import { motion, AnimatePresence } from 'framer-motion'

export default function FilterBar({ filter, setFilter, counts }) {
  const buttons = [
    {
      label: 'All',
      value: 'ALL',
      count: counts.all,
      activeClass: 'bg-raised border-muted text-white',
      countClass: 'bg-muted text-dim',
    },
    {
      label: 'ERROR',
      value: 'ERROR',
      count: counts.error,
      activeClass: 'bg-[rgba(239,68,68,0.1)] border-[rgba(239,68,68,0.35)] text-error',
      countClass: 'bg-[rgba(239,68,68,0.15)] text-error',
    },
    {
      label: 'WARN',
      value: 'WARN',
      count: counts.warn,
      activeClass: 'bg-[rgba(245,158,11,0.08)] border-[rgba(245,158,11,0.3)] text-warn',
      countClass: 'bg-[rgba(245,158,11,0.12)] text-warn',
    },
  ]

  return (
    <motion.div layout className="flex items-center gap-2 mb-6 flex-wrap">
      <motion.span layout className="text-xs text-dim font-medium mr-1">Filter by severity:</motion.span>

      <AnimatePresence mode="popLayout">
        {buttons.map(({ label, value, count, activeClass, countClass }) => {
          const isActive = filter === value
          return (
            <motion.button
              layout
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.8 }}
              key={value}
              onClick={() => setFilter(value)}
              className={`
                flex items-center gap-2 px-3 py-1.5 rounded-lg border text-xs font-semibold
                transition-all duration-150 cursor-pointer relative
                ${isActive
                  ? activeClass
                  : 'bg-surface border-border text-dim hover:border-muted hover:text-white'
                }
              `}
            >
              {isActive && (
                <motion.div
                  layoutId="activeFilterGlow"
                  className="absolute inset-0 rounded-lg bg-white opacity-5"
                />
              )}
              <span className="relative z-10">{label}</span>
              {/* Count badge */}
              <span className={`
                relative z-10 px-1.5 py-0.5 rounded text-[10px] font-mono font-bold
                ${isActive ? countClass : 'bg-raised text-muted'}
              `}>
                {count}
              </span>
            </motion.button>
          )
        })}

        {filter !== 'ALL' && (
          <motion.button
            layout
            initial={{ opacity: 0, x: -10, scale: 0.8 }}
            animate={{ opacity: 1, x: 0, scale: 1 }}
            exit={{ opacity: 0, scale: 0.8, transition: { duration: 0.15 } }}
            onClick={() => setFilter('ALL')}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-border bg-surface text-xs font-semibold text-dim hover:text-white hover:bg-raised transition-colors ml-2"
          >
            <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
            Clear Filter
          </motion.button>
        )}
      </AnimatePresence>
    </motion.div>
  )
}

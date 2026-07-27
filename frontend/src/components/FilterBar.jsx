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
 */
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
    <div className="flex items-center gap-2 mb-6">
      <span className="text-xs text-dim font-medium mr-1">Filter by severity:</span>

      {buttons.map(({ label, value, count, activeClass, countClass }) => {
        const isActive = filter === value
        return (
          <button
            key={value}
            onClick={() => setFilter(value)}
            className={`
              flex items-center gap-2 px-3 py-1.5 rounded-lg border text-xs font-semibold
              transition-all duration-150 cursor-pointer
              ${isActive
                ? activeClass
                : 'bg-surface border-border text-dim hover:border-muted hover:text-white'
              }
            `}
          >
            {label}
            {/* Count badge */}
            <span className={`
              px-1.5 py-0.5 rounded text-[10px] font-mono font-bold
              ${isActive ? countClass : 'bg-raised text-muted'}
            `}>
              {count}
            </span>
          </button>
        )
      })}
    </div>
  )
}

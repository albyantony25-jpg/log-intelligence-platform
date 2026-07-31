/**
 * StatsOverview component
 *
 * Renders a row of 4 stat cards at the top of the dashboard:
 *   Total Logs  |  Total Clusters  |  Error Events  |  Warn Events
 *
 * Each card uses anime.js to animate its number from 0 → actual value
 * when the data first loads. This is a "count-up" animation — a common
 * pattern in dashboards that makes the page feel alive on first render.
 *
 * How the anime.js count-up works:
 *   1. We create a plain JS object { val: 0 }.
 *   2. anime tweens (interpolates) obj.val from 0 to the target number.
 *   3. On every animation frame (update callback), we write Math.round(obj.val)
 *      into the DOM via a React ref, bypassing React's virtual DOM for speed.
 *
 * Props:
 *   totalLogs     (number)
 *   totalClusters (number)
 *   errorCount    (number)
 *   warnCount     (number)
 *   loading       (boolean) — if true, show skeleton placeholders
 */
import { useRef, useEffect } from 'react'
import { animate } from 'animejs'

/**
 * StatCard — a single metric tile.
 *
 * Props:
 *   label      (string)  — human-readable label shown below the number
 *   value      (number)  — the target number to count up to
 *   icon       (element) — SVG icon rendered in the top-left
 *   accentColor(string)  — Tailwind color class for the icon and left border
 *   loading    (boolean)
 */
function StatCard({ label, value, icon, accentColor, loading }) {
  // counterRef points to the <span> that displays the animated number
  const counterRef = useRef(null)
  const prevValueRef = useRef(0)

  // Run the count-up animation whenever `value` changes
  useEffect(() => {
    if (loading || !counterRef.current) return

    const obj = { val: prevValueRef.current }

    // animejs v4: animate(target, keyframes, options)
    // We animate a plain JS object and write its value into the DOM each frame.
    animate(obj, {
      val: value,
      duration: prevValueRef.current === 0 ? 1500 : 500, // Faster if it's just an update
      ease: 'outExpo',          // v4 uses 'ease' (not 'easing') and shorthand names
      onUpdate: () => {
        if (counterRef.current) {
          counterRef.current.textContent = Math.round(obj.val).toLocaleString()
        }
      },
      onComplete: () => {
        prevValueRef.current = value
      }
    })
  }, [value, loading])

  return (
    <div className={`
      bg-surface border border-border rounded-xl p-5
      flex flex-col gap-4 transition-colors duration-200
      hover:border-muted
    `}>
      {/* Icon + label row */}
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold text-dim uppercase tracking-wider">
          {label}
        </span>
        <div className={`w-7 h-7 rounded-lg flex items-center justify-center ${accentColor}`}>
          {icon}
        </div>
      </div>

      {/* Animated number */}
      {loading ? (
        <div className="skeleton h-8 w-16 rounded" />
      ) : (
        <span
          ref={counterRef}
          className="text-3xl font-bold text-white font-mono tabular-nums"
        >
          0
        </span>
      )}
    </div>
  )
}

export default function StatsOverview({ totalLogs, totalClusters, errorCount, warnCount, loading }) {
  const stats = [
    {
      label: 'Total Logs',
      value: totalLogs,
      accentColor: 'bg-raised text-accent',
      icon: (
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
        </svg>
      ),
    },
    {
      label: 'Clusters Found',
      value: totalClusters,
      accentColor: 'bg-raised text-accent',
      icon: (
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
        </svg>
      ),
    },
    {
      label: 'Error Events',
      value: errorCount,
      accentColor: 'bg-[rgba(239,68,68,0.12)] text-error',
      icon: (
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
        </svg>
      ),
    },
    {
      label: 'Warn Events',
      value: warnCount,
      accentColor: 'bg-[rgba(245,158,11,0.10)] text-warn',
      icon: (
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
        </svg>
      ),
    },
  ]

  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
      {stats.map((stat) => (
        <StatCard key={stat.label} {...stat} loading={loading} />
      ))}
    </div>
  )
}

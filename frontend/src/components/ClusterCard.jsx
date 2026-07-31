/**
 * ClusterCard component
 *
 * Renders one log cluster as a card. Each card shows:
 *   - Service name (bold, top-left)
 *   - Severity badge (color-coded pill: red for ERROR, amber for WARN)
 *   - Event count (large number)
 *   - Time bucket formatted as "10:40 AM – 10:50 AM"
 *   - AI summary in a left-bordered quote block
 *   - Sample messages — collapsed by default, expanded by clicking "Show messages"
 *
 * Animation:
 *   This component uses framer-motion's `motion.div` with shared `variants`.
 *   The parent container in App.jsx uses `staggerChildren` so each card animates
 *   in 80ms after the previous one — creating a cascade effect on page load.
 *
 * Props:
 *   cluster (object) — a LogClusterSummary from the API:
 *     { serviceName, logLevel, timeBucketStart, count, sampleMessages, aiSummary }
 */
import { useState, useEffect, useRef } from 'react'
import { m, AnimatePresence, useReducedMotion } from 'framer-motion'
import { animate } from 'animejs'

/* Framer-motion variants shared with the parent container in App.jsx.
   The parent uses staggerChildren so each card's "visible" animation fires
   80ms after the previous card's, creating a cascade/stagger effect. */
export const getCardVariants = (shouldReduceMotion) => ({
  hidden:  { opacity: 0, y: shouldReduceMotion ? 0 : 20, scale: shouldReduceMotion ? 1 : 0.95 },
  visible: {
    opacity: 1,
    y: 0,
    scale: 1,
    transition: { 
      type: shouldReduceMotion ? 'tween' : 'spring',
      damping: 20,
      stiffness: 250,
      duration: shouldReduceMotion ? 0.3 : undefined
    },
  },
})

/* ---- Helpers ------------------------------------------------------------ */

/**
 * Format ISO timestamp into "10:40 AM" style string.
 * We use en-US locale for consistent 12-hour format output.
 */
function formatTime(isoString) {
  if (!isoString) return '—'
  const d = new Date(isoString)
  return d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: true })
}

/**
 * Format a date into a short "Jul 27" style string.
 */
function formatDate(isoString) {
  if (!isoString) return ''
  const d = new Date(isoString)
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

/**
 * Compute the end of a 10-minute bucket from its start timestamp.
 */
function bucketEnd(isoString) {
  if (!isoString) return '—'
  const d = new Date(isoString)
  d.setMinutes(d.getMinutes() + 10)
  return formatTime(d.toISOString())
}

/* Per-severity styling maps — keeps render logic clean */
const SEVERITY = {
  ERROR: {
    badgeBg:    'bg-[rgba(239,68,68,0.12)]',
    badgeText:  'text-error',
    badgeBorder:'border-[rgba(239,68,68,0.3)]',
    glowClass:  'glow-error',
    countColor: 'text-error',
    dot:        'bg-error',
  },
  WARN: {
    badgeBg:    'bg-[rgba(245,158,11,0.10)]',
    badgeText:  'text-warn',
    badgeBorder:'border-[rgba(245,158,11,0.25)]',
    glowClass:  'glow-warn',
    countColor: 'text-warn',
    dot:        'bg-warn',
  },
}

/* ---- Component ---------------------------------------------------------- */

export default function ClusterCard({ cluster }) {
  // Controls whether sample messages are visible
  const [expanded, setExpanded] = useState(false)
  const [acknowledged, setAcknowledged] = useState(false)
  const shouldReduceMotion = useReducedMotion()
  const prevCount = useRef(cluster.count)
  const cardRef = useRef(null)
  const pulseAnimRef = useRef(null)

  // 1. Flash when count increases
  useEffect(() => {
    if (cluster.count > prevCount.current) {
      // Flash the card border when a new log is added
      if (cardRef.current && !shouldReduceMotion) {
        animate(cardRef.current, {
          boxShadow: [
            { value: '0 0 0px 0px rgba(129,140,248,0)', duration: 0 },
            { value: '0 0 15px 2px rgba(129,140,248,0.6)', duration: 300 },
            { value: '0 0 0px 0px rgba(129,140,248,0)', duration: 600 }
          ],
          ease: 'outExpo'
        })
      }
      prevCount.current = cluster.count
    }
  }, [cluster.count, cluster.logLevel, shouldReduceMotion])

  // 2. Continuous pulse for unacknowledged ERRORs
  useEffect(() => {
    if (cluster.logLevel === 'ERROR' && !acknowledged && cardRef.current && !shouldReduceMotion) {
      pulseAnimRef.current = animate(cardRef.current, {
        boxShadow: [
          { value: '0 0 0px 0px rgba(239,68,68,0)', duration: 0 },
          { value: '0 0 10px 1px rgba(239,68,68,0.4)', duration: 1500 },
          { value: '0 0 0px 0px rgba(239,68,68,0)', duration: 1500 }
        ],
        loop: true,
        ease: 'inOutSine'
      })
    } else if (pulseAnimRef.current) {
      pulseAnimRef.current.pause()
      animate(cardRef.current, {
        boxShadow: '0 0 0px 0px rgba(0,0,0,0)',
        duration: 300,
        ease: 'outSine'
      })
    }

    return () => {
      if (pulseAnimRef.current) pulseAnimRef.current.pause()
    }
  }, [cluster.logLevel, acknowledged, shouldReduceMotion])

  const sev     = SEVERITY[cluster.logLevel] || SEVERITY.WARN
  const bucketStart = formatTime(cluster.timeBucketStart)
  const bucketEndTime = bucketEnd(cluster.timeBucketStart)
  const dateLabel = formatDate(cluster.timeBucketStart)
  const hasSummary = cluster.aiSummary && cluster.aiSummary !== 'Summary unavailable'

  return (
    <m.div
      layout
      ref={cardRef}
      variants={getCardVariants(shouldReduceMotion)}
      initial="hidden"
      whileInView="visible"
      viewport={{ once: true, margin: "-50px" }}
      onClick={() => {
        setExpanded(!expanded)
        if (!acknowledged) setAcknowledged(true)
      }}
      className={`
        bg-surface rounded-xl p-5 cursor-pointer
        transition-all duration-200 ${sev.glowClass} hover:border-slate-600 border border-transparent
      `}
    >
      {/* ---- Top row: service name + badge -------------------------------- */}
      <div className="flex items-center justify-between gap-3 mb-4">
        <div className="flex items-center gap-2.5 min-w-0">
          {/* Severity dot */}
          <span className={`w-2 h-2 rounded-full flex-shrink-0 ${sev.dot}`} />

          {/* Service name */}
          <span className="font-semibold text-white text-sm truncate">
            {cluster.serviceName}
          </span>
        </div>

        {/* Severity badge & Anomaly Score */}
        <div className="flex items-center gap-2">
          {cluster.anomalyScore !== undefined && (
            <span className="text-xs text-dim font-mono bg-[rgba(255,255,255,0.05)] px-2 py-0.5 rounded border border-border">
              z-score: {cluster.anomalyScore}
            </span>
          )}
          <span className={`
            flex-shrink-0 px-2.5 py-0.5 rounded-full border text-[11px] font-bold tracking-wide
            ${sev.badgeBg} ${sev.badgeText} ${sev.badgeBorder}
          `}>
            {cluster.logLevel}
          </span>
        </div>
      </div>

      {/* ---- Count + time bucket ----------------------------------------- */}
      <div className="flex items-baseline gap-4 mb-4">
        {/* Large event count */}
        <div className="flex items-baseline gap-1.5">
          <span className={`text-4xl font-bold font-mono tabular-nums ${sev.countColor}`}>
            {cluster.count}
          </span>
          <span className="text-xs text-dim">events</span>
        </div>

        {/* Time bucket */}
        <div className="flex items-center gap-1.5 text-xs text-dim font-mono">
          <svg className="w-3 h-3 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span>{dateLabel} · {bucketStart} – {bucketEndTime}</span>
        </div>
      </div>

      {/* ---- AI Summary -------------------------------------------------- */}
      <div className={`ai-quote px-4 py-3 rounded-r-lg mb-4 ${!hasSummary ? 'opacity-50' : ''}`}>
        <div className="flex items-start gap-2">
          {/* AI sparkle icon */}
          <svg
            className="w-3.5 h-3.5 text-accent flex-shrink-0 mt-0.5"
            fill="currentColor"
            viewBox="0 0 24 24"
          >
            <path d="M12 2l2.4 7.4H22l-6.2 4.5 2.4 7.4L12 17l-6.2 4.3 2.4-7.4L2 9.4h7.6L12 2z" />
          </svg>
          <p className="text-[13px] text-slate-300 leading-relaxed">
            {hasSummary
              ? cluster.aiSummary
              : <span className="italic text-dim">AI summary unavailable</span>
            }
          </p>
        </div>
      </div>

      {/* ---- Sample messages (expandable) -------------------------------- */}
      {cluster.sampleMessages && cluster.sampleMessages.length > 0 && (
        <div>
          <button
            onClick={() => setExpanded((v) => !v)}
            className="flex items-center gap-1.5 text-xs text-dim hover:text-white transition-colors mb-2 cursor-pointer"
          >
            {/* Chevron rotates when expanded */}
            <svg
              className={`w-3 h-3 transition-transform duration-200 ${expanded ? 'rotate-90' : ''}`}
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth={2.5}
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 4.5l7.5 7.5-7.5 7.5" />
            </svg>
            <span>
              {expanded ? 'Hide' : 'Show'} sample messages ({cluster.sampleMessages.length})
            </span>
          </button>

          {/* Messages list — rendered only when expanded */}
          <AnimatePresence>
            {expanded && (
              <m.div 
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0 }}
                className="space-y-1.5 mt-2 pl-1 overflow-hidden"
              >
                {cluster.sampleMessages.map((msg, i) => (
                  <div
                    key={i}
                    className="log-message text-slate-400 bg-raised rounded px-3 py-2 border border-border"
                  >
                    <span className="text-muted mr-2 select-none">{String(i + 1).padStart(2, '0')}.</span>
                    {msg}
                  </div>
                ))}
              </m.div>
            )}
          </AnimatePresence>
        </div>
      )}
    </m.div>
  )
}

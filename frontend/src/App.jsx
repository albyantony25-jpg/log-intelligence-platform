/**
 * App.jsx — Root component of the Log Intelligence Platform frontend
 *
 * Responsibilities:
 *   1. Fetch data from the Spring Boot API on mount (useEffect)
 *   2. Manage loading, error, and data state
 *   3. Derive stats (error count, warn count) from cluster data
 *   4. Handle the active severity filter
 *   5. Compose all child components into the page layout
 *
 * Data fetching:
 *   Two API calls run in parallel using Promise.all:
 *     GET /api/logs                    → total log count (for the stat card)
 *     GET /api/logs/clusters/summary   → clusters with AI summaries
 *
 *   The /api prefix is proxied to http://localhost:8081 by Vite's dev server
 *   (configured in vite.config.js), so no CORS issues during development.
 *
 * State shape:
 *   clusters     — array of LogClusterSummary objects from the API
 *   totalLogs    — integer, count of all log entries in the database
 *   loading      — boolean, true while the API call is in-flight
 *   error        — string | null, set if the API call fails
 *   filter       — 'ALL' | 'ERROR' | 'WARN', controls which clusters are shown
 */
import { useState, useEffect, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'

import Header        from './components/Header'
import LiveTicker    from './components/LiveTicker'
import StatsOverview from './components/StatsOverview'
import FilterBar     from './components/FilterBar'
import ClusterCard   from './components/ClusterCard'
import LoadingSkeleton from './components/LoadingSkeleton'
import EmptyState    from './components/EmptyState'

/* API base — empty string routes through the Vite proxy defined in vite.config.js */
const API_BASE = ''

export default function App() {
  const [clusters,  setClusters]  = useState([])
  const [totalLogs, setTotalLogs] = useState(0)
  const [loading,   setLoading]   = useState(true)
  const [error,     setError]     = useState(null)
  const [filter,    setFilter]    = useState('ALL')

  /* ---- Data fetching ----------------------------------------------------- */
  useEffect(() => {
    let cancelled = false   // prevents state update if the component unmounts mid-fetch

    const fetchClusters = async () => {
      try {
        const clustersRes = await fetch(`${API_BASE}/api/logs/clusters/summary`)
        if (!clustersRes.ok) throw new Error(`Clusters endpoint: HTTP ${clustersRes.status}`)
        const clustersData = await clustersRes.json()
        if (!cancelled) setClusters(clustersData)
      } catch (err) {
        console.error("Failed to fetch clusters", err)
      }
    }

    const fetchData = async () => {
      try {
        const [clustersRes, logsRes] = await Promise.all([
          fetch(`${API_BASE}/api/logs/clusters/summary`),
          fetch(`${API_BASE}/api/logs`),
        ])

        if (!clustersRes.ok) throw new Error(`Clusters endpoint: HTTP ${clustersRes.status}`)
        if (!logsRes.ok)     throw new Error(`Logs endpoint: HTTP ${logsRes.status}`)

        const [clustersData, logsData] = await Promise.all([
          clustersRes.json(),
          logsRes.json(),
        ])

        if (!cancelled) {
          setClusters(clustersData)
          setTotalLogs(Array.isArray(logsData) ? logsData.length : 0)
          setError(null)
        }
      } catch (err) {
        if (!cancelled) setError(err.message)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    fetchData()

    // WebSocket for cluster updates
    const socketUrl = 'http://localhost:8081/ws-logs'
    const stompClient = new Client({
      webSocketFactory: () => new SockJS(socketUrl),
      reconnectDelay: 5000,
      onConnect: () => {
        stompClient.subscribe('/topic/clusters', (message) => {
          // When a cluster updates (e.g. log count increases), re-fetch clusters
          fetchClusters()
        })
      }
    })
    stompClient.activate()

    return () => { 
      cancelled = true 
      stompClient.deactivate()
    }
  }, [])

  /* ---- Derived state ------------------------------------------------------ */

  // Apply the active severity filter to the full cluster list
  const filteredClusters = filter === 'ALL'
    ? clusters
    : clusters.filter((c) => c.logLevel === filter)

  // Sum event counts for each severity level (used in stat cards + filter badges)
  const errorCount = clusters
    .filter((c) => c.logLevel === 'ERROR')
    .reduce((sum, c) => sum + c.count, 0)

  const warnCount = clusters
    .filter((c) => c.logLevel === 'WARN')
    .reduce((sum, c) => sum + c.count, 0)

  // Counts for the FilterBar badges
  const filterCounts = {
    all:   clusters.length,
    error: clusters.filter((c) => c.logLevel === 'ERROR').length,
    warn:  clusters.filter((c) => c.logLevel === 'WARN').length,
  }

  /* ---- Render ------------------------------------------------------------ */
  return (
    <div className="min-h-screen bg-bg font-sans">
      <div className="max-w-5xl mx-auto px-5 py-8">

        {/* 1. Header — title, tagline, live status dot */}
        <Header apiConnected={!loading && !error} />

        {/* Live Ticker for incoming logs */}
        <LiveTicker />

        {/* 2. Stats overview — 4 cards with anime.js count-up */}
        <StatsOverview
          totalLogs={totalLogs}
          totalClusters={clusters.length}
          errorCount={errorCount}
          warnCount={warnCount}
          loading={loading}
        />

        {/* ---- Error banner (API unreachable) -------------------------------- */}
        {error && (
          <div className="mb-6 flex items-start gap-3 bg-[rgba(239,68,68,0.08)] border border-[rgba(239,68,68,0.25)] rounded-xl px-4 py-3">
            <svg className="w-4 h-4 text-error flex-shrink-0 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
            </svg>
            <div>
              <p className="text-sm font-semibold text-error">API connection failed</p>
              <p className="text-xs text-dim mt-0.5 font-mono">{error}</p>
              <p className="text-xs text-dim mt-1">
                Make sure the Spring Boot app is running on{' '}
                <code className="text-slate-400">http://localhost:8081</code>
              </p>
            </div>
          </div>
        )}

        {/* 4. Filter bar — All / ERROR / WARN buttons */}
        {!loading && !error && (
          <FilterBar
            filter={filter}
            setFilter={setFilter}
            counts={filterCounts}
          />
        )}

        {/* ---- Section header ------------------------------------------------ */}
        {!loading && !error && (
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-semibold text-dim uppercase tracking-widest">
              {filter === 'ALL' ? 'All clusters' : `${filter} clusters`}
            </h2>
            <span className="text-xs text-muted font-mono">
              {filteredClusters.length} result{filteredClusters.length !== 1 ? 's' : ''}
            </span>
          </div>
        )}

        {/* 5. Loading skeleton */}
        {loading && <LoadingSkeleton />}

        {/* 6. Empty state */}
        {!loading && !error && filteredClusters.length === 0 && (
          <EmptyState filtered={filter !== 'ALL'} />
        )}

        {/* 3. Cluster feed — stagger-animated cards */}
        {!loading && !error && filteredClusters.length > 0 && (
          /*
           * motion.div with staggerChildren causes each child card to
           * animate in 80ms after the previous one, creating the cascade.
           * AnimatePresence handles cards smoothly entering/exiting when
           * the filter changes.
           */
          <motion.div
            key={filter}   // re-trigger stagger animation when filter changes
            initial="hidden"
            animate="visible"
            variants={{
              hidden:  {},
              visible: { transition: { staggerChildren: 0.08 } },
            }}
            className="space-y-4"
          >
            {filteredClusters.map((cluster, i) => (
              <ClusterCard
                key={`${cluster.serviceName}-${cluster.logLevel}-${cluster.timeBucketStart}-${i}`}
                cluster={cluster}
              />
            ))}
          </motion.div>
        )}

        {/* Footer */}
        {!loading && (
          <p className="text-center text-[11px] text-muted mt-12 font-mono">
            Log Intelligence Platform · Groq llama-3.3-70b-versatile · Spring Boot 3.5
          </p>
        )}
      </div>
    </div>
  )
}

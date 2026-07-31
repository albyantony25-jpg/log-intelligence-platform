/**
 * Header component
 *
 * Displays the platform title, a one-line tagline, and a live status indicator.
 * The status dot pulses green when the API is reachable and red when there's
 * a connection error — giving instant visual feedback without any text clutter.
 *
 * Props:
 *   apiConnected (boolean) — true once data has loaded without error
 */

import { useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import SimulatorControl from './SimulatorControl'

/**
 * The outer ping ring uses Tailwind's built-in `animate-ping` utility which
 * scales and fades a copy of the dot outward — a clean "live" indicator.
 */
export default function Header({ apiConnected }) {
  const [isDark, setIsDark] = useState(() => {
    if (typeof window !== 'undefined') {
      return localStorage.getItem('theme') !== 'light' // default to dark
    }
    return true
  })

  useEffect(() => {
    const root = document.documentElement
    if (isDark) {
      root.classList.add('dark')
      localStorage.setItem('theme', 'dark')
    } else {
      root.classList.remove('dark')
      localStorage.setItem('theme', 'light')
    }
  }, [isDark])

  return (
    <header className="mb-10">
      {/* Top bar: title + status indicator */}
      <div className="flex items-start justify-between gap-4">
        <div>
          {/* Wordmark */}
          <div className="flex items-center gap-3 mb-2">
            {/* Terminal icon */}
            <div className="w-8 h-8 rounded-lg bg-raised border border-border flex items-center justify-center flex-shrink-0">
              <svg className="w-4 h-4 text-accent" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M8 9l3 3-3 3m5 0h3" />
              </svg>
            </div>
            <h1 className="text-2xl font-bold tracking-tight text-white">
              Log Intelligence Platform
            </h1>
          </div>

          {/* Tagline */}
          <p className="text-dim text-sm ml-11">
            AI-powered error clustering with plain-English root-cause summaries
          </p>
        </div>

        {/* Right side: Theme Toggle + Simulator Control + Live status pill */}
        <div className="flex items-center gap-3 mt-1">
          {/* Theme Toggle Button */}
          <button
            onClick={() => setIsDark(!isDark)}
            className="w-8 h-8 rounded-full bg-surface border border-border flex items-center justify-center text-dim hover:text-white transition-colors relative"
            aria-label="Toggle theme"
          >
            <AnimatePresence mode="wait" initial={false}>
              {isDark ? (
                <motion.svg
                  key="moon"
                  initial={{ opacity: 0, rotate: -90 }}
                  animate={{ opacity: 1, rotate: 0 }}
                  exit={{ opacity: 0, rotate: 90 }}
                  transition={{ duration: 0.15 }}
                  className="w-4 h-4 absolute"
                  fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}
                >
                  <path strokeLinecap="round" strokeLinejoin="round" d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
                </motion.svg>
              ) : (
                <motion.svg
                  key="sun"
                  initial={{ opacity: 0, rotate: -90 }}
                  animate={{ opacity: 1, rotate: 0 }}
                  exit={{ opacity: 0, rotate: 90 }}
                  transition={{ duration: 0.15 }}
                  className="w-4 h-4 absolute text-amber-500"
                  fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}
                >
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
                </motion.svg>
              )}
            </AnimatePresence>
          </button>

          <SimulatorControl />
          
          <div className="flex items-center gap-2 bg-surface border border-border rounded-full px-3 py-1.5 flex-shrink-0">
            {/* Pulsing dot — green = connected, red = error */}
            <span className="relative flex h-2 w-2">
              <span
                className={`animate-ping absolute inline-flex h-full w-full rounded-full opacity-75 ${
                  apiConnected ? 'bg-success' : 'bg-error'
                }`}
              />
              <span
                className={`relative inline-flex rounded-full h-2 w-2 ${
                  apiConnected ? 'bg-success' : 'bg-error'
                }`}
              />
            </span>
            <span className="text-xs font-medium text-dim">
              {apiConnected ? 'API connected' : 'Connecting…'}
            </span>
          </div>
        </div>
      </div>

      {/* Divider */}
      <div className="mt-6 border-b border-border" />
    </header>
  )
}

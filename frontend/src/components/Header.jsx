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

/**
 * The outer ping ring uses Tailwind's built-in `animate-ping` utility which
 * scales and fades a copy of the dot outward — a clean "live" indicator.
 */
export default function Header({ apiConnected }) {
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

        {/* Live status pill */}
        <div className="flex items-center gap-2 bg-surface border border-border rounded-full px-3 py-1.5 flex-shrink-0 mt-1">
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

      {/* Divider */}
      <div className="mt-6 border-b border-border" />
    </header>
  )
}

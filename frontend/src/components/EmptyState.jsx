/**
 * EmptyState component
 *
 * Shown when the API returns an empty clusters array (all clear — no WARN/ERROR
 * events in the database) or when the active filter has no matching clusters.
 *
 * Props:
 *   filtered (boolean) — if true, we're filtering and got no results;
 *                        shows a slightly different message
 */
export default function EmptyState({ filtered = false }) {
  return (
    <div className="flex flex-col items-center justify-center py-24 text-center">
      {/* Shield / check icon */}
      <div className="w-16 h-16 rounded-2xl bg-surface border border-border flex items-center justify-center mb-5">
        <svg
          className="w-8 h-8 text-success"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={1.5}
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z"
          />
        </svg>
      </div>

      <h3 className="text-lg font-semibold text-white mb-2">
        {filtered ? 'No matching clusters' : 'No issues detected'}
      </h3>
      <p className="text-dim text-sm max-w-xs">
        {filtered
          ? 'Try changing the severity filter to see other clusters.'
          : 'All systems clear — no WARN or ERROR clusters found in the log database.'}
      </p>

      {/* Decorative dots */}
      <div className="flex gap-1.5 mt-8">
        {[0, 1, 2].map((i) => (
          <span
            key={i}
            className="w-1.5 h-1.5 rounded-full bg-border"
            style={{ opacity: 1 - i * 0.3 }}
          />
        ))}
      </div>
    </div>
  )
}

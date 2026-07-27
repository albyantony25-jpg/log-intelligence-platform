/**
 * LoadingSkeleton component
 *
 * Shown while the API request is in-flight. Renders 4 placeholder cards
 * with a shimmer animation to communicate that content is loading.
 *
 * The shimmer effect is defined in index.css (.skeleton class) using a
 * moving gradient — the standard "skeleton screen" UX pattern used by
 * LinkedIn, Facebook, and most modern dashboards.
 *
 * No props needed — it always renders the same placeholder layout.
 */

/**
 * A single skeleton card placeholder.
 * Uses the .skeleton CSS class for the shimmer animation.
 */
function SkeletonCard() {
  return (
    <div className="bg-surface border border-border rounded-xl p-5 space-y-4">
      {/* Header row: service name + badge */}
      <div className="flex items-center justify-between">
        <div className="skeleton h-4 w-36 rounded" />
        <div className="skeleton h-5 w-14 rounded-full" />
      </div>

      {/* Count + time bucket row */}
      <div className="flex items-end gap-4">
        <div className="skeleton h-8 w-12 rounded" />
        <div className="skeleton h-3 w-40 rounded" />
      </div>

      {/* AI summary block */}
      <div className="space-y-2 pl-3 border-l-2 border-raised">
        <div className="skeleton h-3 w-full rounded" />
        <div className="skeleton h-3 w-4/5 rounded" />
      </div>

      {/* Sample messages toggle */}
      <div className="skeleton h-3 w-28 rounded" />
    </div>
  )
}

export default function LoadingSkeleton() {
  return (
    <div className="space-y-4">
      {/* Render 4 skeleton cards */}
      {[0, 1, 2, 3].map((i) => (
        <SkeletonCard key={i} />
      ))}
    </div>
  )
}

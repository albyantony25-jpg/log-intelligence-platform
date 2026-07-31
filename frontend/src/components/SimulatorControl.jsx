import { useState, useEffect } from 'react'

export default function SimulatorControl() {
  const [isSimulating, setIsSimulating] = useState(false)
  const [loading, setLoading] = useState(false)

  // Polling to get simulation status on mount
  useEffect(() => {
    fetch('http://localhost:8081/simulate/status')
      .then(res => res.json())
      .then(data => setIsSimulating(data.running))
      .catch(err => console.error("Could not fetch simulation status", err))
  }, [])

  const toggleSimulation = async () => {
    setLoading(true)
    const endpoint = isSimulating ? '/stop' : '/start'
    try {
      await fetch(`http://localhost:8081/simulate${endpoint}`, { method: 'POST' })
      setIsSimulating(!isSimulating)
    } catch (err) {
      console.error(`Failed to ${endpoint} simulation`, err)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex items-center gap-3 bg-surface border border-border rounded-lg px-3 py-1.5 shadow-sm">
      <div className="flex items-center gap-2">
        {isSimulating ? (
          <span className="relative flex h-2 w-2">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-accent opacity-75" />
            <span className="relative inline-flex rounded-full h-2 w-2 bg-accent" />
          </span>
        ) : (
          <span className="h-2 w-2 rounded-full bg-slate-500" />
        )}
        <span className="text-xs font-medium text-white hidden sm:inline">
          {isSimulating ? 'Simulating' : 'Simulator Off'}
        </span>
      </div>

      <button
        onClick={toggleSimulation}
        disabled={loading}
        className={`text-xs px-2.5 py-1 rounded-md font-semibold transition-colors disabled:opacity-50 ${
          isSimulating 
            ? 'bg-error/10 text-error hover:bg-error/20 border border-error/20'
            : 'bg-accent/10 text-accent hover:bg-accent/20 border border-accent/20'
        }`}
      >
        {isSimulating ? 'Stop' : 'Start'}
      </button>
    </div>
  )
}

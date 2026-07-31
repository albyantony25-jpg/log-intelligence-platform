import { useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'

export default function LiveTicker({ onNewLog }) {
  const [logs, setLogs] = useState([])

  useEffect(() => {
    // We use a relative path if the UI and API are hosted together,
    // but in Vite dev mode, we need to point to the proxy or the full URL.
    // Using full URL for local dev:
    const socketUrl = 'http://localhost:8081/ws-logs'
    
    const client = new Client({
      webSocketFactory: () => new SockJS(socketUrl),
      reconnectDelay: 5000,
      onConnect: () => {
        console.log('Connected to WebSocket for Live Ticker')
        client.subscribe('/topic/logs', (message) => {
          if (message.body) {
            const newLog = JSON.parse(message.body)
            // Add to the top, keep only the latest 10 logs
            setLogs((prevLogs) => [newLog, ...prevLogs].slice(0, 10))
            if (onNewLog) onNewLog()
          }
        })
      },
      onStompError: (frame) => {
        console.error('Broker reported error: ' + frame.headers['message'])
        console.error('Additional details: ' + frame.body)
      },
    })

    client.activate()

    return () => {
      client.deactivate()
    }
  }, [])

  if (logs.length === 0) return null

  return (
    <div className="mb-6 bg-surface border border-divider rounded-xl overflow-hidden shadow-sm">
      <div className="px-4 py-2 bg-[rgba(255,255,255,0.02)] border-b border-divider flex items-center gap-2">
        <span className="relative flex h-2.5 w-2.5">
          <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-success opacity-75"></span>
          <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-success"></span>
        </span>
        <h3 className="text-xs font-semibold text-dim uppercase tracking-widest">Live Log Feed</h3>
      </div>
      
      <div className="p-2 space-y-1 h-48 overflow-hidden relative">
        <AnimatePresence initial={false}>
          {logs.map((log) => {
            const isError = log.logLevel === 'ERROR'
            const isWarn = log.logLevel === 'WARN'
            
            return (
              <motion.div
                key={log.id}
                initial={{ opacity: 0, y: -20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, scale: 0.95 }}
                transition={{ type: 'spring', stiffness: 300, damping: 24 }}
                className={`flex items-start gap-3 p-2 rounded-lg text-xs font-mono border-l-2
                  ${isError ? 'bg-[rgba(239,68,68,0.05)] border-error text-red-200' : ''}
                  ${isWarn ? 'bg-[rgba(245,158,11,0.05)] border-warn text-amber-200' : ''}
                  ${!isError && !isWarn ? 'bg-[rgba(255,255,255,0.03)] border-slate-500 text-slate-300' : ''}
                `}
              >
                <div className="flex-shrink-0 w-12 opacity-60">
                  {new Date(log.timestamp).toLocaleTimeString([], { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                </div>
                <div className="flex-shrink-0 w-24 truncate opacity-80">
                  {log.serviceName}
                </div>
                <div className="flex-grow truncate">
                  {log.message}
                </div>
              </motion.div>
            )
          })}
        </AnimatePresence>
      </div>
    </div>
  )
}

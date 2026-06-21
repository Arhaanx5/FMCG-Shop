/**
 * useLocationSocket — Real-time GPS tracking via STOMP over WebSocket
 * Uses native browser WebSocket + manual STOMP frame parsing (no npm package needed)
 *
 * Subscribes to /topic/location/{deliveryBoyId}
 * Returns the latest {latitude, longitude, timestamp, userName} update
 */
import { useEffect, useState, useRef } from 'react'
import { ENV } from '../config/env'

const getWsUrl = () => {
  if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
    return 'ws://localhost:8085/ws/websocket'
  }
  try {
    const url = new URL(ENV.apiUrl)
    const protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${protocol}//${url.host}/ws/websocket`
  } catch (e) {
    return (window.location.protocol === 'https:' ? 'wss:' : 'ws:') + '//api.laritraders.store/ws/websocket'
  }
}

const WS_BASE = getWsUrl()

// Parse a raw STOMP frame string into { command, headers, body }
function parseStompFrame(raw) {
  const lines = raw.split('\n')
  const command = lines[0].trim()
  const headers = {}
  let bodyStart = 0
  for (let i = 1; i < lines.length; i++) {
    const line = lines[i]
    if (line.trim() === '') {
      bodyStart = i + 1
      break
    }
    const colonIdx = line.indexOf(':')
    if (colonIdx > -1) {
      headers[line.slice(0, colonIdx).trim()] = line.slice(colonIdx + 1).trim()
    }
  }
  const body = lines.slice(bodyStart).join('\n').replace(/\0/g, '').trim()
  return { command, headers, body }
}

// Build a STOMP frame string
function buildStompFrame(command, headers = {}, body = '') {
  let frame = command + '\n'
  for (const [k, v] of Object.entries(headers)) {
    frame += `${k}:${v}\n`
  }
  frame += '\n' + body + '\0'
  return frame
}

export default function useLocationSocket(deliveryBoyId, enabled = true) {
  const [location, setLocation] = useState(null)
  const [connected, setConnected] = useState(false)
  const wsRef = useRef(null)
  const heartbeatRef = useRef(null)

  useEffect(() => {
    if (!deliveryBoyId || !enabled) return

    let ws = null
    let subId = 'sub-loc-' + deliveryBoyId
    let isDestroyed = false

    const connect = () => {
      try {
        console.log(`🔌 WS: Connecting to ${WS_BASE}...`)
        ws = new WebSocket(WS_BASE)
        wsRef.current = ws

        ws.onopen = () => {
          if (isDestroyed) return
          console.log('🔌 WS: Connection opened, sending CONNECT frame')
          // Send STOMP CONNECT frame
          ws.send(buildStompFrame('CONNECT', {
            'accept-version': '1.1,1.0',
            'heart-beat': '10000,10000',
          }))
        }

        ws.onmessage = (event) => {
          if (isDestroyed) return
          const raw = typeof event.data === 'string' ? event.data : ''
          if (!raw.trim()) return

          const frame = parseStompFrame(raw)

          if (frame.command === 'CONNECTED') {
            console.log('🔌 WS: STOMP Connected, subscribing to location topic')
            setConnected(true)
            // Subscribe to the delivery boy's location topic
            ws.send(buildStompFrame('SUBSCRIBE', {
              id: subId,
              destination: `/topic/location/${deliveryBoyId}`,
            }))

            // Send client heartbeat every 10s
            heartbeatRef.current = setInterval(() => {
              if (ws.readyState === WebSocket.OPEN) ws.send('\n')
            }, 10000)
          }

          if (frame.command === 'MESSAGE' && frame.body) {
            try {
              const data = JSON.parse(frame.body)
              console.log('📡 WS: Received live location update:', data)
              setLocation({
                latitude: data.latitude,
                longitude: data.longitude,
                userName: data.userName,
                timestamp: data.timestamp,
              })
            } catch (_) {}
          }
        }

        ws.onclose = (event) => {
          console.log(`🔌 WS: Connection closed (code: ${event.code}, reason: ${event.reason || 'none'})`)
          setConnected(false)
          clearInterval(heartbeatRef.current)
          // Auto-reconnect after 3 seconds if not intentionally destroyed
          if (!isDestroyed) {
            console.log('🔌 WS: Auto-reconnecting in 3s...')
            setTimeout(connect, 3000)
          }
        }

        ws.onerror = (err) => {
          console.warn('🔌 WS: WebSocket error:', err)
        }
      } catch (err) {
        console.error('🔌 WS: WebSocket connect failed:', err)
      }
    }

    connect()

    return () => {
      isDestroyed = true
      clearInterval(heartbeatRef.current)
      if (ws && ws.readyState === WebSocket.OPEN) {
        // Send UNSUBSCRIBE + DISCONNECT before closing
        ws.send(buildStompFrame('UNSUBSCRIBE', { id: subId }))
        ws.send(buildStompFrame('DISCONNECT', {}))
        ws.close()
      }
      setConnected(false)
    }
  }, [deliveryBoyId, enabled])

  return { location, connected }
}

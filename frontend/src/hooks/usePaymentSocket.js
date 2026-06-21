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

export default function usePaymentSocket(onPaymentReceived, enabled = true) {
  const [connected, setConnected] = useState(false)
  const wsRef = useRef(null)
  const heartbeatRef = useRef(null)
  const callbackRef = useRef(onPaymentReceived)

  // Keep callback ref updated to prevent unnecessary socket re-registrations
  useEffect(() => {
    callbackRef.current = onPaymentReceived
  }, [onPaymentReceived])

  useEffect(() => {
    if (!enabled) return

    let ws = null
    const subId = 'sub-payments'
    let isDestroyed = false
    let retryDelay = 3000 // Start reconnect at 3s

    const connect = () => {
      if (isDestroyed) return
      try {
        console.log(`🔌 Payment WS: Connecting to ${WS_BASE}...`)
        ws = new WebSocket(WS_BASE)
        wsRef.current = ws

        ws.onopen = () => {
          if (isDestroyed) return
          console.log('🔌 Payment WS: Connection opened, sending CONNECT frame')
          retryDelay = 3000 // Reset backoff on successful open
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
            console.log('🔌 Payment WS: STOMP Connected, subscribing to /topic/payments')
            setConnected(true)
            ws.send(buildStompFrame('SUBSCRIBE', {
              id: subId,
              destination: `/topic/payments`,
            }))

            // Heartbeat frame every 10s to keep connection alive
            heartbeatRef.current = setInterval(() => {
              if (ws && ws.readyState === WebSocket.OPEN) {
                ws.send('\n')
              }
            }, 10000)
          }

          if (frame.command === 'MESSAGE' && frame.body) {
            try {
              const data = JSON.parse(frame.body)
              console.log('📡 Payment WS: Received payment notification:', data)
              // Basic validation to check required fields before triggering callback
              if (data && data.amount && data.customerName && callbackRef.current) {
                callbackRef.current(data)
              }
            } catch (err) {
              console.warn('📡 Payment WS: Invalid payment frame JSON:', err)
            }
          }
        }

        ws.onclose = (event) => {
          console.log(`🔌 Payment WS: Connection closed (code: ${event.code}, reason: ${event.reason || 'none'})`)
          setConnected(false)
          clearInterval(heartbeatRef.current)

          if (!isDestroyed) {
            console.log(`🔌 Payment WS: Auto-reconnecting in ${retryDelay / 1000}s...`)
            setTimeout(() => {
              // Exponential backoff capped at 30 seconds
              retryDelay = Math.min(retryDelay * 2, 30000)
              connect()
            }, retryDelay)
          }
        }

        ws.onerror = (err) => {
          console.warn('🔌 Payment WS: WebSocket error:', err)
        }
      } catch (err) {
        console.error('🔌 Payment WS: WebSocket connect failed:', err)
      }
    }

    connect()

    return () => {
      isDestroyed = true
      clearInterval(heartbeatRef.current)
      if (ws && ws.readyState === WebSocket.OPEN) {
        try {
          ws.send(buildStompFrame('UNSUBSCRIBE', { id: subId }))
          ws.send(buildStompFrame('DISCONNECT', {}))
        } catch (_) {}
        ws.close()
      }
      setConnected(false)
    }
  }, [enabled])

  return { connected }
}

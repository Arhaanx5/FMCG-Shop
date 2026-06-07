import { useState, useEffect, useMemo, useRef } from 'react'
import { MapContainer, TileLayer, Marker, Popup, Polyline, useMap } from 'react-leaflet'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { Phone, MapPin, AlertTriangle } from 'lucide-react'
import api from '../services/api'
import useLocationSocket from '../hooks/useLocationSocket'

// Area colors for clustering/pins
const AREA_COLORS = [
  '#2563eb', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6',
  '#ec4899', '#14b8a6', '#f97316', '#06b6d4', '#84cc16',
]

// Calculate bearing angle between two lat/lng points (for truck rotation)
function getBearing(from, to) {
  const lat1 = (from[0] * Math.PI) / 180
  const lat2 = (to[0] * Math.PI) / 180
  const dLng = ((to[1] - from[1]) * Math.PI) / 180
  const y = Math.sin(dLng) * Math.cos(lat2)
  const x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng)
  const bearing = (Math.atan2(y, x) * 180) / Math.PI
  return (bearing + 360) % 360
}

// Custom Google Maps style shop pin
function createNumberedIcon(number, color, isActive = false) {
  return L.divIcon({
    className: 'custom-shop-pin',
    html: `
      <div style="position: relative; width: ${isActive ? 44 : 36}px; height: ${isActive ? 44 : 36}px; display: flex; align-items: center; justify-content: center;">
        ${isActive ? `<div style="position:absolute;width:44px;height:44px;border-radius:50%;background:${color};opacity:0.2;animation:pulse-ring 1.8s infinite ease-out;"></div>` : ''}
        <svg width="${isActive ? 44 : 36}" height="${isActive ? 44 : 36}" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" style="filter: drop-shadow(0px 3px 6px rgba(0,0,0,0.4));">
          <path d="M12 2C8.13 2 5 5.13 5 9C5 14.25 12 22 12 22C12 22 19 14.25 19 9C19 5.13 15.87 2 12 2Z" fill="${isActive ? color : color + 'cc'}" stroke="#ffffff" stroke-width="${isActive ? 2 : 1.5}"/>
        </svg>
        <span style="position: absolute; top: ${isActive ? 8 : 6}px; font-size: ${isActive ? 12 : 11}px; font-weight: 800; color: #ffffff; font-family: 'Outfit', sans-serif;">${number}</span>
      </div>
    `,
    iconSize: [isActive ? 44 : 36, isActive ? 44 : 36],
    iconAnchor: [isActive ? 22 : 18, isActive ? 44 : 36],
    popupAnchor: [0, isActive ? -44 : -36],
  })
}

// Cab-app style truck icon that ROTATES based on heading direction
function createTruckIcon(bearing = 0, isMoving = true) {
  const color = isMoving ? '#1d4ed8' : '#64748b'
  const pulseColor = isMoving ? '#3b82f6' : '#94a3b8'

  return L.divIcon({
    className: 'custom-truck-pin',
    html: `
      <div style="position: relative; width: 52px; height: 52px; display: flex; align-items: center; justify-content: center;">
        <!-- Outer pulse ring (only when moving) -->
        ${isMoving ? `
        <div style="
          position: absolute; width: 52px; height: 52px; border-radius: 50%;
          background: ${pulseColor}; opacity: 0.12;
          animation: pulse-ring 2s infinite ease-out;
        "></div>
        <div style="
          position: absolute; width: 36px; height: 36px; border-radius: 50%;
          background: ${pulseColor}; opacity: 0.25;
          animation: pulse-ring 1.4s infinite ease-out;
        "></div>
        ` : ''}

        <!-- Rotating vehicle container -->
        <div style="
          width: 36px; height: 36px;
          background: ${color};
          border-radius: 50%;
          border: 2.5px solid white;
          box-shadow: 0 4px 16px rgba(0,0,0,0.4);
          display: flex; align-items: center; justify-content: center;
          z-index: 10;
          transform: rotate(${bearing}deg);
          transition: transform 0.6s ease;
        ">
          <!-- Navigation arrow SVG — points UP, rotation applied by bearing -->
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2L4.5 20.29L5.21 21L12 18L18.79 21L19.5 20.29L12 2Z" fill="white" stroke="rgba(255,255,255,0.4)" stroke-width="0.5"/>
          </svg>
        </div>
      </div>

      <style>
        @keyframes pulse-ring {
          0% { transform: scale(0.5); opacity: 0.9; }
          70% { opacity: 0.3; }
          100% { transform: scale(1.5); opacity: 0; }
        }
      </style>
    `,
    iconSize: [52, 52],
    iconAnchor: [26, 26],
    popupAnchor: [0, -20],
  })
}

// Map controller — auto-zoom on active segment, recenter on trigger, handle drag disable
function MapController({ positions, recenterTrigger, isSimulating, nextStop, deliveryBoy, boyHasGps, truckPosition, autoCenter, setAutoCenter }) {
  const map = useMap()

  useEffect(() => {
    const disableAuto = () => setAutoCenter(false)
    map.on('dragstart', disableAuto)
    return () => map.off('dragstart', disableAuto)
  }, [map])

  // Recenter button pressed
  useEffect(() => {
    if (recenterTrigger > 0) {
      setAutoCenter(true)
      if (positions.length > 0) {
        const bounds = L.latLngBounds(positions)
        map.fitBounds(bounds, { padding: [50, 50], maxZoom: 15, animate: true })
      }
    }
  }, [recenterTrigger])

  // Initial fit
  useEffect(() => {
    if (positions.length > 0) {
      const bounds = L.latLngBounds(positions)
      map.fitBounds(bounds, { padding: [50, 50], maxZoom: 15 })
    }
  }, [])

  // Auto-zoom on active segment while simulating
  useEffect(() => {
    if (!autoCenter) return
    if (isSimulating && truckPosition && nextStop) {
      const bounds = L.latLngBounds([truckPosition, [nextStop.latitude, nextStop.longitude]])
      map.fitBounds(bounds, { padding: [90, 90], maxZoom: 17, animate: true })
    } else if (!isSimulating && boyHasGps && nextStop && deliveryBoy) {
      const boyCoords = [deliveryBoy.lastLatitude, deliveryBoy.lastLongitude]
      const bounds = L.latLngBounds([boyCoords, [nextStop.latitude, nextStop.longitude]])
      map.fitBounds(bounds, { padding: [90, 90], maxZoom: 17, animate: true })
    }
  }, [truckPosition, nextStop, isSimulating, boyHasGps, deliveryBoy?.lastLatitude, deliveryBoy?.lastLongitude, autoCenter])

  return null
}

export default function DeliveryMap({ routeData, onClose }) {
  if (!routeData || !routeData.areaGroups) return null

  const areaColorMap = {}
  routeData.areaGroups.forEach((group, i) => {
    areaColorMap[group.areaName] = AREA_COLORS[i % AREA_COLORS.length]
  })

  const [deliveryBoy, setDeliveryBoy] = useState(null)
  const [isDarkMode, setIsDarkMode] = useState(false)
  const [isSimulating, setIsSimulating] = useState(false)
  const [simSpeed, setSimSpeed] = useState(2)
  const [simulationIndex, setSimulationIndex] = useState(0)
  const [recenterTrigger, setRecenterTrigger] = useState(0)
  const [autoCenter, setAutoCenter] = useState(true)
  const [isMobile, setIsMobile] = useState(window.innerWidth < 768)

  useEffect(() => {
    const handleResize = () => {
      setIsMobile(window.innerWidth < 768)
    }
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  // Cab-app tracking states
  const [truckState, setTruckState] = useState('STOPPED')
  const [speed, setSpeed] = useState(0)
  const [eta, setEta] = useState('N/A')
  const [distanceLeft, setDistanceLeft] = useState(0)
  const [unloadCountdown, setUnloadCountdown] = useState(0)
  const [prevLiveCoords, setPrevLiveCoords] = useState(null)
  const [truckBearing, setTruckBearing] = useState(0)
  // Live coordinates from WebSocket (overrides polled data)
  const [liveCoords, setLiveCoords] = useState(null)
  const [wsLatency, setWsLatency] = useState(null)

  // Real-time WebSocket subscription — delivery boy location pushed instantly
  const { location: wsLocation, connected: wsConnected } = useLocationSocket(
    routeData?.deliveryBoyId,
    !isSimulating
  )

  // 1. Update liveCoords from WebSocket updates
  useEffect(() => {
    if (wsLocation && !isSimulating) {
      setLiveCoords([wsLocation.latitude, wsLocation.longitude])
      if (wsLocation.timestamp) {
        const latency = Date.now() - wsLocation.timestamp
        setWsLatency(latency >= 0 ? latency : 0)
      }
    } else {
      setWsLatency(null)
    }
  }, [wsLocation, isSimulating])

  // 2. Setup Polling Fallback (runs only when wsConnected is false and not simulating)
  useEffect(() => {
    if (!routeData?.deliveryBoyId || isSimulating || wsConnected) return

    const fetchPolledLocation = () => {
      api.get(`/users/${routeData.deliveryBoyId}`)
        .then(res => {
          const data = res.data.data
          if (data) {
            setDeliveryBoy(data)
            if (data.lastLatitude && data.lastLongitude) {
              setLiveCoords([data.lastLatitude, data.lastLongitude])
            }
          }
        })
        .catch(err => console.error('Polled location fetch failed:', err))
    }

    // Run immediately and then every 6 seconds
    fetchPolledLocation()
    const interval = setInterval(fetchPolledLocation, 6000)
    return () => clearInterval(interval)
  }, [routeData?.deliveryBoyId, isSimulating, wsConnected])

  // 3. Update truck movement state, speed and bearing when liveCoords change
  useEffect(() => {
    if (!liveCoords || isSimulating) return

    if (prevLiveCoords) {
      const distMoved = L.latLng(liveCoords).distanceTo(L.latLng(prevLiveCoords))
      if (distMoved > 2) {
        setTruckState('MOVING')
        const spd = Math.round((distMoved / 5) * 3.6)
        setSpeed(spd > 80 ? 45 : spd)
        setTruckBearing(getBearing(prevLiveCoords, liveCoords))
      } else {
        setTruckState('STOPPED')
        setSpeed(0)
      }
    }
    setPrevLiveCoords(liveCoords)
  }, [liveCoords, isSimulating])

  // Track dark mode
  useEffect(() => {
    const checkDark = () => {
      const dark = document.documentElement.classList.contains('dark') || document.body.classList.contains('dark')
      setIsDarkMode(dark)
    }
    checkDark()
    const observer = new MutationObserver(checkDark)
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
    return () => observer.disconnect()
  }, [])

  // Fetch delivery boy info ONCE (for name, role)
  useEffect(() => {
    if (!routeData?.deliveryBoyId) return
    api.get(`/users/${routeData.deliveryBoyId}`)
      .then(res => setDeliveryBoy(res.data.data))
      .catch(err => console.error('Failed to fetch delivery boy info:', err))
  }, [routeData])

  // Collect stops
  const allStops = routeData.areaGroups.flatMap(g => g.stops)
  const gpsStops = allStops.filter(s => s.hasLocation)

  // Use WS live coords if available, fallback to last known from DB, then default
  const boyHasGps = !!(liveCoords || (deliveryBoy && deliveryBoy.lastLatitude && deliveryBoy.lastLongitude))
  const defaultBoyLat = gpsStops[0] ? gpsStops[0].latitude + 0.005 : 28.6139
  const defaultBoyLng = gpsStops[0] ? gpsStops[0].longitude - 0.005 : 77.2090
  const startLat = liveCoords ? liveCoords[0] : (deliveryBoy?.lastLatitude || defaultBoyLat)
  const startLng = liveCoords ? liveCoords[1] : (deliveryBoy?.lastLongitude || defaultBoyLng)

  // Full route waypoints [truck start, ...shops]
  const routePoints = useMemo(() => {
    return [[startLat, startLng], ...gpsStops.map(s => [s.latitude, s.longitude])]
  }, [startLat, startLng, gpsStops.length])

  // Interpolate path for smooth animation
  const stepsPerSegment = 60
  const interpolatedPath = useMemo(() => {
    if (routePoints.length < 2) return routePoints
    const fullPath = []
    for (let i = 0; i < routePoints.length - 1; i++) {
      const start = routePoints[i]
      const end = routePoints[i + 1]
      for (let j = 0; j < stepsPerSegment; j++) {
        const t = j / stepsPerSegment
        fullPath.push([
          start[0] + (end[0] - start[0]) * t,
          start[1] + (end[1] - start[1]) * t,
        ])
      }
    }
    fullPath.push(routePoints[routePoints.length - 1])
    return fullPath
  }, [routePoints])

  // Simulation step timer
  useEffect(() => {
    if (!isSimulating || interpolatedPath.length === 0) return

    if (unloadCountdown > 0) {
      setTruckState('STOPPED')
      setSpeed(0)
      setEta('Arrived')
      setDistanceLeft(0)
      const timer = setTimeout(() => setUnloadCountdown(prev => prev - 1), 1000)
      return () => clearTimeout(timer)
    }

    const intervalMs = Math.max(15, 200 / simSpeed)
    const interval = setInterval(() => {
      setSimulationIndex(prev => {
        const next = prev + 1
        const isAtStop = next > 0 && next < interpolatedPath.length && next % stepsPerSegment === 0
        if (isAtStop) {
          setUnloadCountdown(5)
          setTruckState('STOPPED')
        } else {
          setTruckState('MOVING')
          // Compute bearing from previous to next point
          if (prev >= 0 && next < interpolatedPath.length) {
            const bearing = getBearing(interpolatedPath[prev], interpolatedPath[next])
            setTruckBearing(bearing)
          }
        }
        if (next >= interpolatedPath.length) return 0
        return next
      })
    }, intervalMs)
    return () => clearTimeout(interval)
  }, [isSimulating, interpolatedPath, simSpeed, unloadCountdown])

  // Current truck position
  const truckPosition = useMemo(() => {
    if (isSimulating) return interpolatedPath[simulationIndex] || [startLat, startLng]
    // Live mode: truck position comes from WebSocket coords (liveCoords) or DB fallback
    if (liveCoords) return liveCoords
    if (deliveryBoy?.lastLatitude && deliveryBoy?.lastLongitude)
      return [deliveryBoy.lastLatitude, deliveryBoy.lastLongitude]
    return null
  }, [isSimulating, interpolatedPath, simulationIndex, liveCoords, deliveryBoy?.lastLatitude, deliveryBoy?.lastLongitude, startLat, startLng])

  // Which segment we're currently in (for simulation)
  const currentSegment = Math.floor(simulationIndex / stepsPerSegment)

  // Next stop heading to
  const nextStop = useMemo(() => {
    if (isSimulating) return gpsStops[currentSegment] || null
    return gpsStops.find(s => s.status !== 'DELIVERED') || null
  }, [gpsStops, currentSegment, isSimulating])

  // Split route: traveled (grey) vs remaining (blue)
  const { travelledPath, remainingPath } = useMemo(() => {
    if (!isSimulating || interpolatedPath.length === 0) {
      return { travelledPath: [], remainingPath: routePoints }
    }
    const idx = Math.min(simulationIndex, interpolatedPath.length - 1)
    return {
      travelledPath: interpolatedPath.slice(0, idx + 1),
      remainingPath: interpolatedPath.slice(idx),
    }
  }, [isSimulating, simulationIndex, interpolatedPath, routePoints])

  // Distance & ETA calculations
  useEffect(() => {
    if (isSimulating) {
      if (truckState === 'STOPPED') {
        setDistanceLeft(0)
        setEta(unloadCountdown > 0 ? 'Unloading' : 'Arrived')
      } else if (truckPosition && nextStop) {
        const dist = L.latLng(truckPosition).distanceTo(L.latLng([nextStop.latitude, nextStop.longitude])) / 1000
        setDistanceLeft(dist)
        const simulatedSpeed = 32 + Math.floor(Math.sin(simulationIndex / 3) * 8)
        setSpeed(simulatedSpeed)
        const mins = (dist / simulatedSpeed) * 60
        setEta(mins < 1 ? 'Under 1m' : `${Math.ceil(mins)} min`)
      }
    } else {
      if (boyHasGps && nextStop) {
        const currentPos = [deliveryBoy.lastLatitude, deliveryBoy.lastLongitude]
        const dist = L.latLng(currentPos).distanceTo(L.latLng([nextStop.latitude, nextStop.longitude])) / 1000
        setDistanceLeft(dist)
        const estSpeed = speed > 0 ? speed : 35
        const mins = (dist / estSpeed) * 60
        setEta(dist < 0.04 ? 'Arrived' : mins < 1 ? 'Under 1m' : `${Math.ceil(mins)} min`)
      } else {
        setDistanceLeft(0)
        setEta('N/A')
      }
    }
  }, [isSimulating, simulationIndex, truckState, truckPosition, nextStop, boyHasGps, deliveryBoy, speed, unloadCountdown])

  // Status text
  const simulationStatusText = useMemo(() => {
    if (isSimulating) {
      if (unloadCountdown > 0 && nextStop)
        return `📦 Stopped at Stop #${nextStop.stopNumber} — Unloading...`
      if (nextStop)
        return `Heading to Stop #${nextStop.stopNumber}: ${nextStop.shopName || nextStop.customerName}`
      return '✅ All stops completed!'
    }
    if (wsConnected && liveCoords) return `📡 Live (WS): Tracking ${deliveryBoy?.name || 'Delivery Boy'} in real-time`
    if (wsConnected) return `🔌 WS Connected — awaiting GPS from ${deliveryBoy?.name || 'Delivery Boy'}...`
    if (liveCoords) return `📡 Live (Polled): Tracking ${deliveryBoy?.name || 'Delivery Boy'} (connecting WS...)`
    if (boyHasGps) return `📍 Last known location — connecting...`
    return '📡 Awaiting GPS signal...'
  }, [isSimulating, nextStop, boyHasGps, deliveryBoy, unloadCountdown, wsConnected, liveCoords])

  const defaultCenter = [28.6139, 77.2090]

  return (
    <>
      {/* Blurred Backdrop */}
      <div
        style={{
          position: 'fixed', inset: 0, zIndex: 999,
          background: 'rgba(10, 15, 30, 0.7)', backdropFilter: 'blur(6px)',
        }}
        onClick={onClose}
      />

      {/* Floating Map Frame */}
      <div style={{
        position: 'fixed',
        inset: isMobile ? 0 : '4% 8%',
        zIndex: 1000,
        borderRadius: isMobile ? 0 : '20px',
        overflow: 'hidden',
        boxShadow: '0 30px 70px -10px rgba(0, 0, 0, 0.5), 0 0 0 1px rgba(255,255,255,0.06)',
        background: 'var(--color-bg)',
        display: 'flex',
        flexDirection: isMobile ? 'column' : 'row',
      }}>

        {/* ═══════ MAP PANEL ═══════ */}
        <div style={{ 
          flex: isMobile ? 'none' : 1, 
          height: isMobile ? '45vh' : '100%', 
          position: 'relative' 
        }}>
          <MapContainer
            center={routePoints.length > 0 ? routePoints[0] : defaultCenter}
            zoom={13}
            className={isDarkMode ? 'dark-map' : ''}
            style={{ width: '100%', height: '100%' }}
            zoomControl={true}
          >
            <style>{`
              .leaflet-container { font-family: 'Outfit', sans-serif !important; }
              .dark-map .leaflet-tile-container img {
                filter: invert(100%) hue-rotate(180deg) brightness(95%) contrast(90%);
              }
              .dark-map .leaflet-container { background: #0f172a !important; }
              .custom-shop-pin, .custom-truck-pin {
                background: transparent !important; border: none !important;
              }
              @keyframes blink { 0%,100%{opacity:0.6} 50%{opacity:1} }
              @keyframes pulse { 0%,100%{transform:scale(1);opacity:1} 50%{transform:scale(1.25);opacity:0.7} }
              .leaflet-control-zoom { border-radius: 12px !important; overflow: hidden; box-shadow: 0 4px 20px rgba(0,0,0,0.2) !important; }
              .leaflet-control-zoom a { background: var(--color-surface) !important; color: var(--color-text) !important; border-color: var(--color-border) !important; }
            `}</style>

            {/* Google Maps Tiles */}
            <TileLayer
              attribution='&copy; <a href="https://maps.google.com">Google Maps</a>'
              url="https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}"
            />

            <MapController
              positions={routePoints}
              recenterTrigger={recenterTrigger}
              boyPosition={boyHasGps ? [deliveryBoy.lastLatitude, deliveryBoy.lastLongitude] : null}
              isSimulating={isSimulating}
              nextStop={nextStop}
              deliveryBoy={deliveryBoy}
              boyHasGps={boyHasGps}
              truckPosition={truckPosition}
              autoCenter={autoCenter}
              setAutoCenter={setAutoCenter}
            />

            {/* ── Travelled path (grey, faded) ── */}
            {isSimulating && travelledPath.length > 1 && (
              <Polyline
                positions={travelledPath}
                pathOptions={{ color: '#94a3b8', weight: 5, opacity: 0.5 }}
              />
            )}

            {/* ── Remaining route outer glow ── */}
            {remainingPath.length > 1 && (
              <Polyline
                positions={remainingPath}
                pathOptions={{ color: '#60a5fa', weight: 10, opacity: 0.25 }}
              />
            )}

            {/* ── Remaining route solid line ── */}
            {remainingPath.length > 1 && (
              <Polyline
                positions={remainingPath}
                pathOptions={{ color: '#1d4ed8', weight: 5, opacity: 0.95 }}
              />
            )}

            {/* ── Truck marker (rotates with heading) ── */}
            {truckPosition && (
              <Marker
                position={truckPosition}
                icon={createTruckIcon(truckBearing, truckState === 'MOVING')}
              >
                <Popup>
                  <div style={{ fontSize: '13px', lineHeight: 1.6, minWidth: '180px' }}>
                    <strong style={{ display: 'block', fontSize: '14px', marginBottom: '6px' }}>
                      🚚 {deliveryBoy?.name || 'Delivery Boy'} (Live)
                    </strong>
                    <strong>Status:</strong> {truckState === 'MOVING' ? '🟢 Moving' : '🔴 Stopped'}<br />
                    <strong>Speed:</strong> {speed} km/h<br />
                    <strong>ETA:</strong> {eta}<br />
                    <span style={{ display: 'block', marginTop: '6px', borderTop: '1px solid #e2e8f0', paddingTop: '6px', fontSize: '11px', opacity: 0.7 }}>
                      Updated just now
                    </span>
                  </div>
                </Popup>
              </Marker>
            )}

            {/* ── Shop pins ── */}
            {gpsStops.map((stop) => {
              const isActive = nextStop && nextStop.deliveryId === stop.deliveryId
              return (
                <Marker
                  key={stop.deliveryId}
                  position={[stop.latitude, stop.longitude]}
                  icon={createNumberedIcon(stop.stopNumber, areaColorMap[stop.areaName] || '#3b82f6', isActive)}
                >
                  <Popup>
                    <div style={{ fontSize: '13px', lineHeight: 1.6, minWidth: '180px' }}>
                      <strong style={{ display: 'block', fontSize: '14px', marginBottom: '4px' }}>
                        #{stop.stopNumber} {stop.shopName || stop.customerName}
                      </strong>
                      <strong>Owner:</strong> {stop.customerName}<br />
                      <strong>Phone:</strong> {stop.phone || 'N/A'}<br />
                      <strong>Balance:</strong> <span style={{ color: '#10b981', fontWeight: 700 }}>₹{stop.amountDue?.toLocaleString?.() || '0'}</span>
                    </div>
                  </Popup>
                </Marker>
              )
            })}
          </MapContainer>

          {/* ══ TOP CONTROL BAR (Desktop only) ══ */}
          {!isMobile && (
            <div style={{
              position: 'absolute', top: 16, left: '50%', transform: 'translateX(-50%)', zIndex: 1100,
              background: 'rgba(10, 15, 30, 0.88)', backdropFilter: 'blur(12px)',
              border: '1px solid rgba(255,255,255,0.12)', borderRadius: '14px',
              padding: '10px 18px', color: '#fff', display: 'flex', alignItems: 'center', gap: '14px',
              boxShadow: '0 8px 40px rgba(0,0,0,0.4)', minWidth: '380px', justifyContent: 'space-between',
            }}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                <span style={{ fontSize: '9px', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '1.2px', opacity: 0.55 }}>
                  {isSimulating ? 'Demo Drive Simulation' : 'Live Geolocation Tracking'}
                </span>
                <span style={{
                  fontSize: '12px', fontWeight: 600,
                  color: isSimulating ? '#f59e0b' : (wsConnected && liveCoords) ? '#34d399' : wsConnected ? '#60a5fa' : liveCoords ? '#38bdf8' : '#94a3b8',
                  maxWidth: '240px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap'
                }}>
                  {simulationStatusText}
                </span>
              </div>

              <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                <button
                  onClick={() => {
                    setIsSimulating(!isSimulating)
                    setSimulationIndex(0)
                    setUnloadCountdown(0)
                    setTruckBearing(0)
                    setAutoCenter(true)
                  }}
                  style={{
                    background: isSimulating ? '#10b981' : 'rgba(99,102,241,0.8)',
                    border: 'none', borderRadius: '10px', padding: '6px 14px',
                    fontSize: '11px', fontWeight: 700, color: '#fff', cursor: 'pointer',
                    transition: 'all 0.2s', letterSpacing: '0.3px'
                  }}
                >
                  {isSimulating ? '📡 Live Mode' : '🚗 Test Drive'}
                </button>

                {isSimulating && (
                  <select
                    value={simSpeed}
                    onChange={e => setSimSpeed(Number(e.target.value))}
                    style={{
                      background: 'rgba(15,23,42,0.9)', color: '#fff',
                      border: '1px solid rgba(255,255,255,0.2)',
                      borderRadius: '10px', fontSize: '11px', padding: '6px 8px',
                      fontWeight: 600, cursor: 'pointer'
                    }}
                  >
                    <option value="1">1× Speed</option>
                    <option value="2">2× Speed</option>
                    <option value="5">5× Speed</option>
                    <option value="10">10× Speed</option>
                  </select>
                )}
              </div>
            </div>
          )}

          {/* ══ CAB-APP HUD (bottom right - Desktop only) ══ */}
          {!isMobile && (
            <div style={{
              position: 'absolute', bottom: 20, right: 20, zIndex: 1100,
              background: 'var(--color-surface)',
              border: '1px solid var(--color-border)',
              borderRadius: '16px', padding: '16px 18px',
              boxShadow: '0 20px 60px rgba(0,0,0,0.25)',
              width: '260px', fontFamily: 'Outfit, sans-serif',
              display: 'flex', flexDirection: 'column', gap: '12px',
            }}>
              {/* Status indicator */}
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                {/* WS connection indicator */}
              {!isSimulating && (
                <div style={{ display: 'flex', alignItems: 'center', gap: '4px', marginLeft: 'auto' }}>
                  <div style={{
                    width: 6, height: 6, borderRadius: '50%',
                    background: wsConnected ? '#10b981' : '#f59e0b',
                    boxShadow: wsConnected ? '0 0 6px #10b981' : '0 0 6px #f59e0b',
                  }} />
                  <span style={{ fontSize: '9px', color: 'var(--color-text-muted)', letterSpacing: '0.3px' }}>
                    {wsConnected ? 'WS Live' : 'WS...'}
                  </span>
                </div>
              )}
              <div style={{
                  width: 10, height: 10, borderRadius: '50%', flexShrink: 0,
                  background: truckState === 'MOVING' ? '#10b981' : unloadCountdown > 0 ? '#f59e0b' : '#ef4444',
                  boxShadow: truckState === 'MOVING'
                    ? '0 0 10px #10b981'
                    : unloadCountdown > 0 ? '0 0 10px #f59e0b' : '0 0 10px #ef4444',
                  animation: truckState === 'MOVING' ? 'pulse 1.4s infinite' : 'none',
                }} />
                <span style={{ fontSize: '11px', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.6px', color: 'var(--color-text-muted)' }}>
                  {truckState === 'MOVING' ? '🚗 Moving / On Way' : unloadCountdown > 0 ? '📦 Unloading Cargo' : '🔴 Stopped / Idle'}
                </span>
              </div>

              {/* Speed + ETA */}
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
                {/* Speed */}
                <div style={{
                  background: 'var(--color-bg-secondary)', borderRadius: '12px',
                  padding: '10px 8px', textAlign: 'center',
                }}>
                  <div style={{ fontSize: '26px', fontWeight: 800, color: 'var(--color-accent)', lineHeight: 1 }}>
                    {speed}
                  </div>
                  <div style={{ fontSize: '9px', color: 'var(--color-text-muted)', textTransform: 'uppercase', marginTop: '3px', letterSpacing: '0.5px' }}>
                    km / h
                  </div>
                </div>

                {/* ETA */}
                <div style={{
                  background: 'var(--color-bg-secondary)', borderRadius: '12px',
                  padding: '10px 8px', textAlign: 'center',
                }}>
                  <div style={{
                    fontSize: eta.length > 6 ? '16px' : '22px',
                    fontWeight: 800, color: 'var(--color-success)', lineHeight: 1,
                    display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '30px'
                  }}>
                    {eta === 'Unloading' && unloadCountdown > 0 ? `${unloadCountdown}s` : eta}
                  </div>
                  <div style={{ fontSize: '9px', color: 'var(--color-text-muted)', textTransform: 'uppercase', marginTop: '3px', letterSpacing: '0.5px' }}>
                    ETA
                  </div>
                </div>
              </div>

              {/* Destination + Distance */}
              <div style={{ borderTop: '1px solid var(--color-border)', paddingTop: '10px', display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '12px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '8px' }}>
                  <span style={{ color: 'var(--color-text-muted)', flexShrink: 0 }}>Next Stop:</span>
                  <span style={{ fontWeight: 700, color: 'var(--color-text)', textAlign: 'right', fontSize: '11px', lineHeight: 1.3 }}>
                    {nextStop ? `#${nextStop.stopNumber} ${nextStop.shopName || nextStop.customerName}` : '✅ All done!'}
                  </span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ color: 'var(--color-text-muted)' }}>Distance:</span>
                  <span style={{ fontWeight: 700, color: 'var(--color-info)' }}>
                    {distanceLeft > 0 ? `${distanceLeft.toFixed(2)} km` : '0.00 km'}
                  </span>
                </div>
                {wsLatency !== null && !isSimulating && (
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span style={{ color: 'var(--color-text-muted)' }}>Ping / Latency:</span>
                    <span style={{ fontWeight: 700, color: wsLatency > 1000 ? 'var(--color-danger)' : wsLatency > 400 ? 'var(--color-warning)' : 'var(--color-success)' }}>
                      {wsLatency < 1000 ? `${wsLatency} ms` : `${(wsLatency / 1000).toFixed(2)}s`}
                    </span>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* ══ TOP RIGHT ACTION BUTTONS ══ */}
          <div style={{
            position: 'absolute', top: isMobile ? 12 : 16, right: isMobile ? 12 : 16, zIndex: 1100,
            display: 'flex', gap: '8px',
          }}>
            {!isMobile && (
              <button
                onClick={() => { setAutoCenter(true); setRecenterTrigger(prev => prev + 1) }}
                style={{
                  background: autoCenter ? 'var(--color-accent)' : 'var(--color-surface)',
                  border: '1px solid var(--color-border)',
                  borderRadius: '10px', padding: '8px 14px',
                  fontSize: '13px', fontWeight: 600, cursor: 'pointer',
                  color: autoCenter ? '#fff' : 'var(--color-text)',
                  boxShadow: '0 4px 16px rgba(0,0,0,0.15)',
                  display: 'flex', alignItems: 'center', gap: '6px', transition: 'all 0.2s'
                }}
              >
                🎯 {autoCenter ? 'Centered' : 'Recenter'}
              </button>
            )}
            <button
              onClick={onClose}
              style={{
                background: 'var(--color-surface)', border: '1px solid var(--color-border)',
                borderRadius: isMobile ? '50%' : '10px',
                width: isMobile ? 36 : 'auto',
                height: isMobile ? 36 : 'auto',
                padding: isMobile ? 0 : '8px 14px',
                fontSize: '13px', fontWeight: 600, cursor: 'pointer',
                color: 'var(--color-text)', boxShadow: '0 4px 16px rgba(0,0,0,0.15)',
                display: 'flex', alignItems: 'center', justifyContent: 'center'
              }}
            >
              {isMobile ? '✕' : '✕ Close'}
            </button>
          </div>

          {/* ══ FLOATING RECENTER BUTTON (Mobile only) ══ */}
          {isMobile && (
            <button
              onClick={() => { setAutoCenter(true); setRecenterTrigger(prev => prev + 1) }}
              style={{
                position: 'absolute', bottom: 12, left: 12, zIndex: 1100,
                background: autoCenter ? 'var(--color-accent)' : 'var(--color-surface)',
                border: '1px solid var(--color-border)',
                borderRadius: '50%', width: 36, height: 36,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                cursor: 'pointer',
                color: autoCenter ? '#fff' : 'var(--color-text)',
                boxShadow: '0 4px 16px rgba(0,0,0,0.15)',
              }}
              title={autoCenter ? 'Centered' : 'Recenter'}
            >
              🎯
            </button>
          )}

          {/* ══ BOTTOM LEFT SUMMARY (Desktop only) ══ */}
          {!isMobile && !isSimulating && !boyHasGps && !liveCoords && (
            <div style={{
              position: 'absolute', bottom: 20, left: 20, zIndex: 1100,
              background: 'rgba(10,15,30,0.85)', backdropFilter: 'blur(8px)',
              border: '1px solid rgba(255,255,255,0.1)', borderRadius: '12px',
              padding: '12px 18px', color: '#fff', fontSize: '12px', fontWeight: 600,
              boxShadow: '0 8px 30px rgba(0,0,0,0.3)',
            }}>
              {wsConnected
                ? '🔌 Connected — waiting for delivery boy to enable GPS...'
                : '📡 No GPS signal — click Test Drive to demo'}
            </div>
          )}
        </div>

        {/* ═══════ SIDE PANEL ═══════ */}
        <div style={{
          width: isMobile ? '100%' : 360,
          height: isMobile ? '55vh' : '100%',
          background: 'var(--color-surface)',
          borderLeft: isMobile ? 'none' : '1px solid var(--color-border)',
          borderTop: isMobile ? '1px solid var(--color-border)' : 'none',
          display: 'flex', flexDirection: 'column', 
        }}>
          {/* Header */}
          <div style={{
            padding: isMobile ? '12px 16px' : '20px 22px', borderBottom: '1px solid var(--color-border)',
            background: 'var(--color-surface-2)', flexShrink: 0,
          }}>
            {!isMobile ? (
              <>
                <h3 style={{ fontSize: '17px', fontWeight: 700, margin: 0, color: 'var(--color-text)' }}>
                  🗺️ Optimized Route
                </h3>
                <p style={{ fontSize: '12px', color: 'var(--color-text-muted)', margin: '4px 0 0' }}>
                  {routeData.totalStops} stops • {routeData.totalDistanceKm} km total
                </p>

                {/* Mini stats */}
                <div style={{ display: 'flex', gap: '16px', marginTop: '12px' }}>
                  {[
                    { label: 'Stops', value: routeData.totalStops, color: 'var(--color-accent)' },
                    { label: 'Distance', value: `${routeData.totalDistanceKm} km`, color: 'var(--color-info)' },
                  ].map((s, i) => (
                    <div key={i} style={{
                      background: 'var(--color-bg-secondary)', borderRadius: '10px',
                      padding: '8px 14px', flex: 1, textAlign: 'center',
                    }}>
                      <div style={{ fontSize: '18px', fontWeight: 700, color: s.color }}>{s.value}</div>
                      <div style={{ fontSize: '10px', color: 'var(--color-text-muted)', textTransform: 'uppercase', letterSpacing: '0.5px' }}>{s.label}</div>
                    </div>
                  ))}
                </div>
              </>
            ) : (
              // Mobile Unified Header: incorporates map stats, simulator controls, and routing stats
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div style={{ display: 'flex', flexDirection: 'column' }}>
                    <span style={{ fontSize: '9px', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '1px', opacity: 0.6 }}>
                      {isSimulating ? 'Simulation Mode' : 'Live Tracking'}
                    </span>
                    <span style={{ fontSize: '11px', fontWeight: 600, color: 'var(--color-text)' }}>
                      {simulationStatusText}
                    </span>
                  </div>
                  <div style={{ display: 'flex', gap: '6px' }}>
                    <button
                      onClick={() => {
                        setIsSimulating(!isSimulating)
                        setSimulationIndex(0)
                        setUnloadCountdown(0)
                        setTruckBearing(0)
                        setAutoCenter(true)
                      }}
                      style={{
                        background: isSimulating ? 'var(--color-success)' : 'var(--color-accent)',
                        border: 'none', borderRadius: '8px', padding: '5px 10px',
                        fontSize: '10px', fontWeight: 700, color: '#fff', cursor: 'pointer'
                      }}
                    >
                      {isSimulating ? 'Live Mode' : 'Test Drive'}
                    </button>
                    {isSimulating && (
                      <select
                        value={simSpeed}
                        onChange={e => setSimSpeed(Number(e.target.value))}
                        style={{
                          background: 'var(--color-surface)', color: 'var(--color-text)',
                          border: '1px solid var(--color-border)',
                          borderRadius: '8px', fontSize: '10px', padding: '4px',
                          fontWeight: 600, cursor: 'pointer'
                        }}
                      >
                        <option value="1">1×</option>
                        <option value="2">2×</option>
                        <option value="5">5×</option>
                        <option value="10">10×</option>
                      </select>
                    )}
                  </div>
                </div>

                {/* Speed & ETA stats cards */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
                  <div style={{ background: 'var(--color-bg)', borderRadius: '10px', padding: '6px 8px', textAlign: 'center' }}>
                    <div style={{ fontSize: '18px', fontWeight: 800, color: 'var(--color-accent)' }}>{speed} km/h</div>
                    <div style={{ fontSize: '8px', color: 'var(--color-text-muted)', textTransform: 'uppercase' }}>Speed</div>
                  </div>
                  <div style={{ background: 'var(--color-bg)', borderRadius: '10px', padding: '6px 8px', textAlign: 'center' }}>
                    <div style={{ fontSize: '18px', fontWeight: 800, color: 'var(--color-success)' }}>
                      {eta === 'Unloading' && unloadCountdown > 0 ? `${unloadCountdown}s` : eta}
                    </div>
                    <div style={{ fontSize: '8px', color: 'var(--color-text-muted)', textTransform: 'uppercase' }}>ETA</div>
                  </div>
                </div>

                {/* Destination & distance details */}
                <div style={{ fontSize: '11px', display: 'flex', justifyContent: 'space-between', color: 'var(--color-text-secondary)' }}>
                  <div>
                    <span>Next: </span>
                    <span style={{ fontWeight: 700 }}>
                      {nextStop ? `#${nextStop.stopNumber} ${nextStop.shopName || nextStop.customerName}` : 'All done!'}
                    </span>
                  </div>
                  <div>
                    <span>Dist: </span>
                    <span style={{ fontWeight: 700, color: 'var(--color-info)' }}>
                      {distanceLeft > 0 ? `${distanceLeft.toFixed(2)} km` : '0.00 km'}
                    </span>
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* Stops list */}
          <div style={{ flex: 1, padding: '10px', overflowY: 'auto' }}>
            {routeData.areaGroups.map((group, gi) => (
              <div key={gi} style={{ marginBottom: '14px' }}>
                {/* Area header */}
                <div style={{
                  display: 'flex', alignItems: 'center', gap: '8px',
                  padding: '7px 10px', borderRadius: '10px',
                  background: `${areaColorMap[group.areaName]}18`,
                  border: `1px solid ${areaColorMap[group.areaName]}35`,
                  marginBottom: '6px',
                }}>
                  <MapPin size={14} style={{ color: areaColorMap[group.areaName] }} />
                  <span style={{ fontSize: '12px', fontWeight: 700, color: areaColorMap[group.areaName] }}>
                    {group.areaName}
                  </span>
                  <span style={{ fontSize: '11px', color: 'var(--color-text-muted)', marginLeft: 'auto' }}>
                    {group.stopCount} stops
                  </span>
                </div>

                {/* Stops */}
                {group.stops.map((stop) => {
                  const isActive = nextStop && nextStop.deliveryId === stop.deliveryId
                  return (
                    <div
                      key={stop.deliveryId}
                      style={{
                        display: 'flex', gap: '10px', alignItems: 'flex-start',
                        padding: '10px 10px',
                        borderRadius: '10px',
                        background: isActive ? `${areaColorMap[group.areaName]}12` : 'var(--color-surface-2)',
                        marginBottom: '5px',
                        border: isActive ? `1px solid ${areaColorMap[group.areaName]}50` : '1px solid var(--color-border)',
                        transform: isActive ? 'scale(1.015)' : 'scale(1)',
                        transition: 'all 200ms ease',
                        boxShadow: isActive ? `0 2px 12px ${areaColorMap[group.areaName]}20` : 'none',
                      }}
                    >
                      {/* Stop number badge */}
                      <div style={{
                        width: 26, height: 26, borderRadius: '50%',
                        background: areaColorMap[group.areaName],
                        color: 'white', fontSize: '11px', fontWeight: 800,
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        flexShrink: 0,
                      }}>
                        {stop.stopNumber}
                      </div>

                      {/* Info */}
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{
                          fontSize: '13px', fontWeight: 600,
                          color: isActive ? areaColorMap[group.areaName] : 'var(--color-text)',
                          whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                          display: 'flex', alignItems: 'center', gap: '5px',
                        }}>
                          {stop.shopName || stop.customerName}
                          {isActive && (
                            <span style={{
                              fontSize: '9px', padding: '2px 6px', borderRadius: '6px',
                              background: areaColorMap[group.areaName], color: '#fff', fontWeight: 700,
                              animation: 'blink 1.4s infinite', flexShrink: 0,
                            }}>
                              🎯 Heading Here
                            </span>
                          )}
                        </div>
                        <div style={{ fontSize: '11px', color: 'var(--color-text-muted)', marginTop: '1px' }}>
                          {stop.customerName}
                        </div>
                        <div style={{ display: 'flex', gap: '10px', marginTop: '4px', fontSize: '11px' }}>
                          {stop.distanceFromPreviousKm > 0 && (
                            <span style={{ color: 'var(--color-info)' }}>
                              📍 {stop.distanceFromPreviousKm} km
                            </span>
                          )}
                          <span style={{ color: 'var(--color-success)', fontWeight: 600 }}>
                            ₹{stop.amountDue?.toLocaleString?.() || '0'}
                          </span>
                        </div>
                        {!stop.hasLocation && (
                          <div style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '10px', color: 'var(--color-warning)', marginTop: '3px' }}>
                            <AlertTriangle size={11} /> Location Unknown
                          </div>
                        )}
                      </div>

                      {/* Action buttons (Call & Navigate) */}
                      <div style={{ display: 'flex', gap: '6px', flexShrink: 0 }}>
                        {stop.phone && (
                          <a
                            href={`tel:${stop.phone}`}
                            style={{
                              width: 28, height: 28, borderRadius: '50%',
                              background: 'var(--color-success-soft)',
                              display: 'flex', alignItems: 'center', justifyContent: 'center',
                            }}
                            title="Call Customer"
                          >
                            <Phone size={13} style={{ color: 'var(--color-success)' }} />
                          </a>
                        )}

                        {stop.hasLocation && (
                          <a
                            href={`https://www.google.com/maps/dir/?api=1&destination=${stop.latitude},${stop.longitude}&travelmode=driving`}
                            target="_blank"
                            rel="noopener noreferrer"
                            style={{
                              width: 28, height: 28, borderRadius: '50%',
                              background: 'var(--color-accent-soft)',
                              display: 'flex', alignItems: 'center', justifyContent: 'center',
                            }}
                            title="Navigate (Google Maps)"
                          >
                            <MapPin size={13} style={{ color: 'var(--color-accent)' }} />
                          </a>
                        )}
                      </div>
                    </div>
                  )
                })}
              </div>
            ))}

            {routeData.totalStops === 0 && (
              <div style={{ textAlign: 'center', padding: '40px 20px', color: 'var(--color-text-muted)', fontSize: '14px' }}>
                No pending deliveries to route.
              </div>
            )}
          </div>
        </div>
      </div>
    </>
  )
}

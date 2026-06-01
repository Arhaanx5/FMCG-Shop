import { useState, useEffect } from 'react'
import { MapContainer, TileLayer, Marker, Popup, Polyline, useMap } from 'react-leaflet'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { Phone, MapPin, AlertTriangle, Truck } from 'lucide-react'
import api from '../services/api'

// Fix default marker icons (leaflet + bundler issue)
delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
})

// Area colors for clustering
const AREA_COLORS = [
  '#f59e0b', '#10b981', '#3b82f6', '#ef4444', '#8b5cf6',
  '#ec4899', '#14b8a6', '#f97316', '#06b6d4', '#84cc16',
]

function createNumberedIcon(number, color) {
  return L.divIcon({
    className: 'delivery-map-marker',
    html: `<div style="
      background: ${color};
      color: white;
      width: 32px;
      height: 32px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 14px;
      font-weight: 700;
      border: 3px solid white;
      box-shadow: 0 2px 8px rgba(0,0,0,0.4);
    ">${number}</div>`,
    iconSize: [32, 32],
    iconAnchor: [16, 16],
    popupAnchor: [0, -20],
  })
}

function createTruckIcon(color) {
  return L.divIcon({
    className: 'delivery-truck-marker',
    html: `<div style="
      background: ${color};
      color: white;
      width: 36px;
      height: 36px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      border: 3px solid white;
      box-shadow: 0 4px 12px rgba(0,0,0,0.4);
      animation: pulse-ring 2s infinite;
    ">
      🚚
    </div>
    <style>
      @keyframes pulse-ring {
        0% { box-shadow: 0 0 0 0 rgba(59, 130, 246, 0.7); }
        70% { box-shadow: 0 0 0 10px rgba(59, 130, 246, 0); }
        100% { box-shadow: 0 0 0 0 rgba(59, 130, 246, 0); }
      }
    </style>`,
    iconSize: [36, 36],
    iconAnchor: [18, 18],
    popupAnchor: [0, -22],
  })
}

// Auto-fit map bounds to all markers
function FitBounds({ positions }) {
  const map = useMap()
  useEffect(() => {
    if (positions.length > 0) {
      const bounds = L.latLngBounds(positions)
      map.fitBounds(bounds, { padding: [50, 50], maxZoom: 15 })
    }
  }, [positions, map])
  return null
}

export default function DeliveryMap({ routeData, onClose }) {
  if (!routeData || !routeData.areaGroups) return null

  // Build color map for areas
  const areaColorMap = {}
  routeData.areaGroups.forEach((group, i) => {
    areaColorMap[group.areaName] = AREA_COLORS[i % AREA_COLORS.length]
  })

  const [deliveryBoy, setDeliveryBoy] = useState(null)

  useEffect(() => {
    if (!routeData?.deliveryBoyId) return

    const fetchDeliveryBoy = async () => {
      try {
        const res = await api.get(`/users/${routeData.deliveryBoyId}`)
        setDeliveryBoy(res.data.data)
      } catch (err) {
        console.error('Failed to fetch delivery boy location:', err)
      }
    }

    fetchDeliveryBoy()
    const interval = setInterval(fetchDeliveryBoy, 15000)
    return () => clearInterval(interval)
  }, [routeData])

  // Collect all stops with GPS for map rendering
  const allStops = routeData.areaGroups.flatMap(g => g.stops)
  const gpsStops = allStops.filter(s => s.hasLocation)
  const positions = [...gpsStops.map(s => [s.latitude, s.longitude])]
  const routePath = [...gpsStops.map(s => [s.latitude, s.longitude])]

  const boyHasGps = deliveryBoy && deliveryBoy.lastLatitude && deliveryBoy.lastLongitude
  if (boyHasGps) {
    positions.unshift([deliveryBoy.lastLatitude, deliveryBoy.lastLongitude])
    routePath.unshift([deliveryBoy.lastLatitude, deliveryBoy.lastLongitude])
  }

  // Default center (India) if no GPS stops
  const defaultCenter = [28.6139, 77.2090]

  return (
    <div style={{
      position: 'fixed', inset: 0, zIndex: 1000,
      background: 'var(--color-bg)', display: 'flex',
    }}>
      {/* Map Panel */}
      <div style={{ flex: 1, position: 'relative' }}>
        <MapContainer
          center={positions.length > 0 ? positions[0] : defaultCenter}
          zoom={13}
          style={{ width: '100%', height: '100%' }}
          zoomControl={true}
        >
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OSM</a>'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          {positions.length > 0 && <FitBounds positions={positions} />}

          {/* Route polyline */}
          {routePath.length > 1 && (
            <Polyline
              positions={routePath}
              pathOptions={{
                color: '#3b82f6', weight: 4, opacity: 0.8,
                dashArray: '8, 8',
              }}
            />
          )}

          {/* Delivery Boy Live Truck Marker */}
          {boyHasGps && (
            <Marker
              position={[deliveryBoy.lastLatitude, deliveryBoy.lastLongitude]}
              icon={createTruckIcon('#3b82f6')}
            >
              <Popup>
                <div style={{ fontSize: '13px', lineHeight: 1.5 }}>
                  <strong>🚚 {deliveryBoy.name}</strong><br />
                  Role: {deliveryBoy.role?.replace('_', ' ')}<br />
                  Last Updated: {deliveryBoy.lastLocationTime ? new Date(deliveryBoy.lastLocationTime).toLocaleTimeString('en-IN') : 'Just now'}
                </div>
              </Popup>
            </Marker>
          )}

          {/* Numbered markers */}
          {gpsStops.map((stop) => (
            <Marker
              key={stop.deliveryId}
              position={[stop.latitude, stop.longitude]}
              icon={createNumberedIcon(stop.stopNumber, areaColorMap[stop.areaName] || '#3b82f6')}
            >
              <Popup>
                <div style={{ fontSize: '13px', lineHeight: 1.5 }}>
                  <strong>#{stop.stopNumber} {stop.shopName || stop.customerName}</strong><br />
                  {stop.customerName}<br />
                  📞 {stop.phone || 'N/A'}<br />
                  💰 ₹{stop.amountDue?.toLocaleString?.() || '0'}
                </div>
              </Popup>
            </Marker>
          ))}
        </MapContainer>

        {/* Close button */}
        <button
          onClick={onClose}
          style={{
            position: 'absolute', top: 16, right: 16, zIndex: 1100,
            background: 'var(--color-surface)', border: '1px solid var(--color-border)',
            borderRadius: 'var(--radius-md)', padding: '8px 16px',
            fontSize: '14px', fontWeight: 600, cursor: 'pointer',
            color: 'var(--color-text)', boxShadow: 'var(--shadow-lg)',
          }}
        >
          ✕ Close Map
        </button>

        {/* Summary badge */}
        <div style={{
          position: 'absolute', bottom: 16, left: 16, zIndex: 1100,
          background: 'var(--color-surface)', border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-lg)', padding: '12px 20px',
          boxShadow: 'var(--shadow-lg)', display: 'flex', gap: '16px',
          alignItems: 'center',
        }}>
          <div>
            <div style={{ fontSize: '20px', fontWeight: 700, color: 'var(--color-accent)' }}>
              {routeData.totalStops}
            </div>
            <div style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>Stops</div>
          </div>
          <div style={{ width: 1, height: 32, background: 'var(--color-border)' }} />
          <div>
            <div style={{ fontSize: '20px', fontWeight: 700, color: 'var(--color-success)' }}>
              {routeData.totalDistanceKm}
            </div>
            <div style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>km Total</div>
          </div>
        </div>
      </div>

      {/* Side panel */}
      <div style={{
        width: 380, background: 'var(--color-surface)',
        borderLeft: '1px solid var(--color-border)',
        display: 'flex', flexDirection: 'column',
        overflowY: 'auto',
      }}>
        {/* Header */}
        <div style={{
          padding: '20px 24px',
          borderBottom: '1px solid var(--color-border)',
          background: 'var(--color-surface-2)',
        }}>
          <h3 style={{ fontSize: '18px', fontWeight: 700, margin: 0, color: 'var(--color-text)' }}>
            🗺️ Optimized Route
          </h3>
          <p style={{ fontSize: '13px', color: 'var(--color-text-muted)', margin: '4px 0 0' }}>
            {routeData.totalStops} stops • {routeData.totalDistanceKm} km total distance
          </p>
        </div>

        {/* Area groups */}
        <div style={{ flex: 1, padding: '12px' }}>
          {routeData.areaGroups.map((group, gi) => (
            <div key={gi} style={{ marginBottom: '16px' }}>
              {/* Area header */}
              <div style={{
                display: 'flex', alignItems: 'center', gap: '8px',
                padding: '8px 12px', borderRadius: 'var(--radius-md)',
                background: `${areaColorMap[group.areaName]}15`,
                border: `1px solid ${areaColorMap[group.areaName]}30`,
                marginBottom: '8px',
              }}>
                <MapPin size={16} style={{ color: areaColorMap[group.areaName] }} />
                <span style={{
                  fontSize: '13px', fontWeight: 700,
                  color: areaColorMap[group.areaName],
                }}>
                  {group.areaName}
                </span>
                <span style={{
                  fontSize: '11px', color: 'var(--color-text-muted)',
                  marginLeft: 'auto',
                }}>
                  {group.stopCount} stops
                </span>
              </div>

              {/* Stops */}
              {group.stops.map((stop, si) => (
                <div
                  key={stop.deliveryId}
                  style={{
                    display: 'flex', gap: '12px', alignItems: 'flex-start',
                    padding: '10px 12px',
                    borderRadius: 'var(--radius-md)',
                    background: 'var(--color-surface-2)',
                    marginBottom: '6px',
                    border: '1px solid var(--color-border)',
                    transition: 'all 150ms',
                  }}
                >
                  {/* Stop number */}
                  <div style={{
                    width: 28, height: 28, borderRadius: '50%',
                    background: areaColorMap[group.areaName],
                    color: 'white', fontSize: '12px', fontWeight: 700,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    flexShrink: 0,
                  }}>
                    {stop.stopNumber}
                  </div>

                  {/* Info */}
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{
                      fontSize: '13px', fontWeight: 600,
                      color: 'var(--color-text)',
                      whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                    }}>
                      {stop.shopName || stop.customerName}
                    </div>
                    <div style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>
                      {stop.customerName}
                    </div>
                    <div style={{
                      display: 'flex', gap: '12px', marginTop: '4px',
                      fontSize: '11px',
                    }}>
                      {stop.distanceFromPreviousKm > 0 && (
                        <span style={{ color: 'var(--color-info)' }}>
                          📍 {stop.distanceFromPreviousKm} km {stop.stopNumber === 1 && boyHasGps ? '(from Salesman)' : ''}
                        </span>
                      )}
                      <span style={{ color: 'var(--color-success)', fontWeight: 600 }}>
                        ₹{stop.amountDue?.toLocaleString?.() || '0'}
                      </span>
                    </div>
                    {!stop.hasLocation && (
                      <div style={{
                        display: 'flex', alignItems: 'center', gap: '4px',
                        fontSize: '11px', color: 'var(--color-warning)',
                        marginTop: '4px',
                      }}>
                        <AlertTriangle size={12} /> Location Unknown
                      </div>
                    )}
                  </div>

                  {/* Call button */}
                  {stop.phone && (
                    <a
                      href={`tel:${stop.phone}`}
                      style={{
                        width: 28, height: 28, borderRadius: '50%',
                        background: 'var(--color-success-soft)',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        flexShrink: 0,
                      }}
                    >
                      <Phone size={13} style={{ color: 'var(--color-success)' }} />
                    </a>
                  )}
                </div>
              ))}
            </div>
          ))}

          {routeData.totalStops === 0 && (
            <div style={{
              textAlign: 'center', padding: '40px 20px',
              color: 'var(--color-text-muted)', fontSize: '14px',
            }}>
              No pending deliveries to route.
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

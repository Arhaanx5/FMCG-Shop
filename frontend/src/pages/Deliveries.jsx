import { useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Plus, MapPin, Truck, Package, CheckCircle, Clock, Eye } from 'lucide-react'
import api from '../services/api'
import DataTable from '../components/DataTable'
import Modal from '../components/Modal'
import DeliveryMap from '../components/DeliveryMap'
import ConfirmDialog from '../components/ConfirmDialog'
import { useToast } from '../context/ToastContext'
import { useAuth } from '../context/AuthContext'

const STATUS_BADGES = {
  PENDING: 'badge-warning',
  PACKED: 'badge-info',
  OUT: 'badge-accent',
  DELIVERED: 'badge-success',
  FAILED: 'badge-danger',
  PARTIAL: 'badge-neutral',
}

export default function Deliveries() {
  const [deliveries, setDeliveries] = useState([])
  const [stats, setStats] = useState({})
  const [loading, setLoading] = useState(true)
  const [showAssignModal, setShowAssignModal] = useState(false)
  const [showMapModal, setShowMapModal] = useState(false)
  const [routeData, setRouteData] = useState(null)
  const [routeLoading, setRouteLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [bills, setBills] = useState([])
  const [deliveryBoys, setDeliveryBoys] = useState([])
  const [selectedBoy, setSelectedBoy] = useState('')
  const [statusUpdateTarget, setStatusUpdateTarget] = useState(null) // holds { id, status }
  const toast = useToast()
  const { user } = useAuth()
  const isDeliveryBoyOrSalesman = user?.role === 'DELIVERY_BOY' || user?.role === 'SALESMAN'
  const [deliveryTab, setDeliveryTab] = useState('pending')
  const [isMobile, setIsMobile] = useState(window.innerWidth < 768)

  useEffect(() => {
    const handleResize = () => setIsMobile(window.innerWidth < 768)
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  const [assignForm, setAssignForm] = useState({
    billId: '', deliveryBoyId: '', type: 'SAME_DAY', scheduledDate: new Date().toISOString().split('T')[0],
  })

  useEffect(() => { loadDeliveries(true); loadStats() }, [])

  const loadDeliveries = async (showSpinner = false) => {
    if (showSpinner) setLoading(true)
    try {
      const res = await api.get('/deliveries')
      setDeliveries(res.data.data || [])
    } catch { toast.error('Failed to load deliveries') }
    finally { if (showSpinner) setLoading(false) }
  }

  const loadStats = async () => {
    try {
      const res = await api.get('/deliveries/stats')
      setStats(res.data.data || {})
    } catch {}
  }

  const openAssign = async () => {
    try {
      const [billsRes, usersRes] = await Promise.all([
        api.get('/bills'),
        api.get('/users'),
      ])
      
      const assignedBillNumbers = new Set(deliveries.map(d => d.billNumber))
      const allBills = billsRes.data.data?.content || billsRes.data.data || []
      const unassignedBills = allBills.filter(b => b.status !== 'CANCELLED' && !assignedBillNumbers.has(b.billNumber))

      setBills(unassignedBills)
      setDeliveryBoys((usersRes.data.data || []).filter(u =>
        (u.role === 'DELIVERY_BOY' || u.role === 'SALESMAN') && u.active !== false
      ))
    } catch {}
    setAssignForm({
      billId: '', deliveryBoyId: '', type: 'SAME_DAY',
      scheduledDate: new Date().toISOString().split('T')[0],
    })
    setShowAssignModal(true)
  }

  const handleAssign = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      await api.post('/deliveries/assign', assignForm)
      toast.success('Delivery assigned!')
      setShowAssignModal(false)
      loadDeliveries()
      loadStats()
    } catch (err) { toast.error(err.response?.data?.message || 'Assignment failed') }
    finally { setSaving(false) }
  }

  const executeStatusUpdate = async (id, status) => {
    try {
      await api.put(`/deliveries/${id}/status`, { status })
      toast.success(`Status updated to ${status}`)
      loadDeliveries()
      loadStats()
    } catch {
      toast.error('Status update failed')
    } finally {
      setStatusUpdateTarget(null)
    }
  }

  const openRouteMap = async (deliveryBoyId) => {
    setRouteLoading(true)
    setShowMapModal(true)
    try {
      const res = await api.get(`/deliveries/route/${deliveryBoyId}`)
      setRouteData(res.data.data)
    } catch (err) {
      toast.error('Failed to load route')
      setShowMapModal(false)
    }
    finally { setRouteLoading(false) }
  }

  const openMyRouteMap = async () => {
    setRouteLoading(true)
    setShowMapModal(true)
    try {
      const res = await api.get('/deliveries/my-route')
      setRouteData(res.data.data)
    } catch (err) {
      toast.error('Failed to load route')
      setShowMapModal(false)
    }
    finally { setRouteLoading(false) }
  }

  // Get unique delivery boys from the deliveries list
  const uniqueBoys = [...new Map(
    deliveries
      .filter(d => d.deliveryBoyId && d.deliveryBoyName)
      .map(d => [d.deliveryBoyId, { id: d.deliveryBoyId, name: d.deliveryBoyName }])
  ).values()]

  const columns = [
    { header: 'Bill #', accessor: 'billNumber', width: 120, render: (row) => (
      <span style={{ fontWeight: 600, color: 'var(--color-accent)' }}>{row.billNumber}</span>
    )},
    { header: 'Customer', accessor: 'customerName', width: 170, render: (row) => (
      <div>
        <div className="font-medium">{row.shopName || row.customerName}</div>
        {row.areaName && (
          <div style={{ fontSize: '11px', color: 'var(--color-text-muted)', marginTop: '2px' }}>
            📍 {row.areaName}
          </div>
        )}
      </div>
    )},
    { header: 'Delivery Boy', accessor: 'deliveryBoyName', render: (row) =>
      row.deliveryBoyName || <span className="text-muted">Unassigned</span>
    },
    { header: 'Amount', accessor: 'amount', render: (row) => (
      <span style={{ fontWeight: 600 }}>₹{row.amount?.toLocaleString?.() || '0'}</span>
    )},
    { header: 'Status', accessor: 'status', render: (row) => (
      <span className={`badge ${STATUS_BADGES[row.status] || 'badge-neutral'}`}>
        {row.status}
      </span>
    )},
    { header: 'Scheduled', accessor: 'scheduledDate', render: (row) =>
      row.scheduledDate || <span className="text-muted">—</span>
    },
  ].filter(col => {
    if (isMobile) {
      return !['deliveryBoyName', 'scheduledDate'].includes(col.accessor)
    }
    return true
  })

  const filteredDeliveries = deliveries.filter(d => {
    const isPending = ['PENDING', 'PACKED', 'OUT'].includes(d.status)
    return deliveryTab === 'pending' ? isPending : !isPending
  })

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h2 className="page-title">Deliveries</h2>
          <p className="page-subtitle">{deliveries.length} total deliveries</p>
        </div>
        <div className="page-actions" style={{ display: 'flex', gap: 'var(--space-3)' }}>
          {isDeliveryBoyOrSalesman ? (
            <motion.button
              className="btn btn-accent"
              onClick={openMyRouteMap}
              whileTap={{ scale: 0.95 }}
              style={{ whiteSpace: 'nowrap' }}
            >
              <MapPin size={18} /> View My Route
            </motion.button>
          ) : (
            <>
              {/* Route map dropdown */}
              {uniqueBoys.length > 0 && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                  <select
                    className="form-select"
                    value={selectedBoy}
                    onChange={e => setSelectedBoy(e.target.value)}
                    style={{ minWidth: 160 }}
                  >
                    <option value="">Select Delivery Boy</option>
                    {uniqueBoys.map(b => (
                      <option key={b.id} value={b.id}>{b.name}</option>
                    ))}
                  </select>
                  <motion.button
                    className="btn btn-accent"
                    disabled={!selectedBoy}
                    onClick={() => openRouteMap(selectedBoy)}
                    whileTap={{ scale: 0.95 }}
                    style={{ whiteSpace: 'nowrap' }}
                  >
                    <MapPin size={18} /> View Route
                  </motion.button>
                </div>
              )}
              <motion.button className="btn btn-primary" onClick={openAssign} whileTap={{ scale: 0.95 }}>
                <Plus size={18} /> Assign Delivery
              </motion.button>
            </>
          )}
        </div>
      </div>

      {/* Stats Row */}
      <div style={{
        display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
        gap: 'var(--space-4)', marginBottom: 'var(--space-6)',
      }}>
        {[
          { label: 'Pending', value: stats.pending || 0, icon: Clock, color: 'var(--color-warning)' },
          { label: 'Packed', value: stats.packed || 0, icon: Package, color: 'var(--color-info)' },
          { label: 'Out for Delivery', value: stats.out || 0, icon: Truck, color: 'var(--color-accent)' },
          { label: 'Delivered', value: stats.delivered || 0, icon: CheckCircle, color: 'var(--color-success)' },
        ].map((stat, i) => {
          const Icon = stat.icon
          return (
            <motion.div
              key={i}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.05 }}
              style={{
                background: 'var(--color-surface)',
                border: '1px solid var(--color-border)',
                borderRadius: 'var(--radius-lg)',
                padding: 'var(--space-5)',
                display: 'flex', alignItems: 'center', gap: 'var(--space-4)',
              }}
            >
              <div style={{
                width: 44, height: 44, borderRadius: 'var(--radius-md)',
                background: `${stat.color}15`,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <Icon size={22} style={{ color: stat.color }} />
              </div>
              <div>
                <div style={{ fontSize: '24px', fontWeight: 700, color: 'var(--color-text)' }}>
                  {stat.value}
                </div>
                <div style={{ fontSize: '12px', color: 'var(--color-text-muted)' }}>
                  {stat.label}
                </div>
              </div>
            </motion.div>
          )
        })}
      </div>

      {/* Tabs */}
      <div className="tabs" style={{ marginBottom: 'var(--space-4)' }}>
        <button
          className={`tab ${deliveryTab === 'pending' ? 'active' : ''}`}
          onClick={() => setDeliveryTab('pending')}
        >
          ⏳ Pendings
        </button>
        <button
          className={`tab ${deliveryTab === 'completed' ? 'active' : ''}`}
          onClick={() => setDeliveryTab('completed')}
        >
          ✅ Completed
        </button>
      </div>

      {/* Deliveries Table */}
      <DataTable
        columns={columns}
        data={filteredDeliveries}
        loading={loading}
        searchPlaceholder="Search deliveries..."
        emptyMessage="No deliveries found. Assign a delivery to get started."
        actions={(row) => (
          <>
            {row.status === 'PENDING' && (
              <button className="btn btn-ghost btn-sm" onClick={() => setStatusUpdateTarget({ id: row.id, status: 'PACKED' })}
                style={{ fontSize: '11px', color: 'var(--color-info)' }}>
                <Package size={14} /> Pack
              </button>
            )}
            {row.status === 'PACKED' && (
              <button className="btn btn-ghost btn-sm" onClick={() => setStatusUpdateTarget({ id: row.id, status: 'OUT' })}
                style={{ fontSize: '11px', color: 'var(--color-accent)' }}>
                <Truck size={14} /> Dispatch
              </button>
            )}
            {row.status === 'OUT' && (
              <button className="btn btn-ghost btn-sm" onClick={() => setStatusUpdateTarget({ id: row.id, status: 'DELIVERED' })}
                style={{ fontSize: '11px', color: 'var(--color-success)' }}>
                <CheckCircle size={14} /> Delivered
              </button>
            )}
          </>
        )}
      />

      {/* Assign Modal */}
      <Modal isOpen={showAssignModal} onClose={() => setShowAssignModal(false)} title="Assign Delivery">
        <form onSubmit={handleAssign} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <div className="form-group">
            <label className="form-label">Bill *</label>
            <select className="form-select" value={assignForm.billId}
              onChange={e => setAssignForm({ ...assignForm, billId: e.target.value })} required>
              <option value="">Select a bill</option>
              {bills.map(b => (
                <option key={b.id} value={b.id}>
                  {b.billNumber} — {b.customerName} (₹{b.grandTotal?.toLocaleString?.()})
                </option>
              ))}
            </select>
          </div>
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Delivery Boy *</label>
              <select className="form-select" value={assignForm.deliveryBoyId}
                onChange={e => setAssignForm({ ...assignForm, deliveryBoyId: e.target.value })} required>
                <option value="">Select person</option>
                {deliveryBoys.map(b => (
                  <option key={b.id} value={b.id}>{b.name} ({b.role?.replace('_', ' ')})</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Type</label>
              <select className="form-select" value={assignForm.type}
                onChange={e => setAssignForm({ ...assignForm, type: e.target.value })}>
                <option value="SAME_DAY">Same Day</option>
                <option value="SCHEDULED">Scheduled</option>
                <option value="SELF_PICKUP">Self Pickup</option>
              </select>
            </div>
          </div>
          <div className="form-group">
            <label className="form-label">Scheduled Date</label>
            <input type="date" className="form-input" value={assignForm.scheduledDate}
              onChange={e => setAssignForm({ ...assignForm, scheduledDate: e.target.value })} />
          </div>
          <div className="form-actions">
            <button type="button" className="btn btn-secondary" onClick={() => setShowAssignModal(false)}>Cancel</button>
            <motion.button type="submit" className="btn btn-primary" disabled={saving} whileTap={{ scale: 0.95 }}>
              {saving ? 'Assigning...' : 'Assign Delivery'}
            </motion.button>
          </div>
        </form>
      </Modal>

      {/* Route Map Fullscreen */}
      <AnimatePresence>
        {showMapModal && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
          >
            {routeLoading ? (
              <div style={{
                position: 'fixed', inset: 0, zIndex: 1000,
                background: 'var(--color-bg)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                flexDirection: 'column', gap: '16px',
              }}>
                <div className="spinner" />
                <p style={{ color: 'var(--color-text-muted)' }}>Calculating optimal route...</p>
              </div>
            ) : (
              <DeliveryMap routeData={routeData} onClose={() => setShowMapModal(false)} />
            )}
          </motion.div>
        )}
      </AnimatePresence>

      {/* Accidental click protection dialog */}
      <ConfirmDialog
        isOpen={!!statusUpdateTarget}
        onClose={() => setStatusUpdateTarget(null)}
        onConfirm={() => executeStatusUpdate(statusUpdateTarget.id, statusUpdateTarget.status)}
        title="Update Delivery Status"
        message={`Are you sure you want to update the delivery status to ${statusUpdateTarget?.status}?`}
        confirmLabel={`Update to ${statusUpdateTarget?.status}`}
        danger={false}
      />
    </div>
  )
}

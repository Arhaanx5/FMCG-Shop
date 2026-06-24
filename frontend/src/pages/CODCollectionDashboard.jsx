import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Clock, ShieldAlert, CheckCircle, AlertTriangle, Send, Check } from 'lucide-react'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import api from '../services/api'
import DataTable from '../components/DataTable'
import Modal from '../components/Modal'
import { useToast } from '../context/ToastContext'
import { useAuth } from '../context/AuthContext'

const getLocalDateStr = () => {
  const d = new Date()
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export default function CODCollectionDashboard() {
  const [reconciliations, setReconciliations] = useState([])
  const [deliveryBoys, setDeliveryBoys] = useState([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [showSubmitModal, setShowSubmitModal] = useState(false)
  const [showAuditModal, setShowAuditModal] = useState(false)
  
  const [submitForm, setSubmitForm] = useState({
    deliveryBoyId: '',
    date: getLocalDateStr(),
    submittedCollection: '',
    adminNotes: ''
  })

  const [auditTarget, setAuditTarget] = useState(null)
  const [auditForm, setAuditForm] = useState({
    status: 'APPROVED',
    adminNotes: ''
  })

  const [fetchedExpectedAmount, setFetchedExpectedAmount] = useState(null)
  const [deliveries, setDeliveries] = useState([])

  const toast = useToast()
  const { user } = useAuth()
  const isAdminOrManager = user?.role === 'ADMIN' || user?.role === 'MANAGER'

  useEffect(() => {
    if (submitForm.deliveryBoyId) {
      fetchExpectedAmount()
    } else {
      setFetchedExpectedAmount(null)
    }
  }, [submitForm.deliveryBoyId, submitForm.date])

  const fetchExpectedAmount = async () => {
    try {
      const res = await api.get(`/reconciliations/expected`, {
        params: {
          deliveryBoyId: submitForm.deliveryBoyId,
          date: submitForm.date
        }
      })
      setFetchedExpectedAmount(res.data.data)
    } catch (err) {
      console.error('Failed to fetch expected amount', err)
    }
  }

  useEffect(() => {
    loadDashboardData()
  }, [])

  const loadDashboardData = async () => {
    setLoading(true)
    try {
      const [reconsRes, usersRes, deliveriesRes] = await Promise.all([
        api.get('/reconciliations'),
        api.get('/users'),
        api.get('/deliveries')
      ])
      
      setReconciliations(reconsRes.data.data || [])
      setDeliveryBoys((usersRes.data.data || []).filter(u =>
        (u.role === 'DELIVERY_BOY' || u.role === 'SALESMAN') && u.active !== false
      ))
      setDeliveries(deliveriesRes.data.data || [])
    } catch (err) {
      toast.error('Failed to load dashboard data')
    } finally {
      setLoading(false)
    }
  }

  const handleSubmitCollection = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      await api.post('/reconciliations/submit', {
        deliveryBoyId: submitForm.deliveryBoyId,
        date: submitForm.date,
        submittedCollection: parseFloat(submitForm.submittedCollection),
        adminNotes: submitForm.adminNotes
      })
      toast.success('Collection submitted successfully')
      setShowSubmitModal(false)
      loadDashboardData()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Collection submission failed')
    } finally {
      setSaving(false)
    }
  }

  const handleAuditSubmit = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      await api.put(`/reconciliations/${auditTarget.id}/status`, {
        status: auditForm.status,
        adminNotes: auditForm.adminNotes
      })
      toast.success('Reconciliation status updated')
      setShowAuditModal(false)
      loadDashboardData()
    } catch (err) {
      toast.error('Failed to update status')
    } finally {
      setSaving(false)
    }
  }

  // Calculate high-level stats for today
  const todayStr = getLocalDateStr()
  const todayRecons = reconciliations.filter(r => r.date === todayStr)
  
  // Calculate expected total dynamically from today's completed COD deliveries
  const expectedTotal = deliveries.reduce((sum, d) => {
    const isToday = d.createdAt?.split('T')[0] === todayStr
    const isCODCompleted = d.status === 'COD_COLLECTED' || d.status === 'COD_PARTIAL'
    if (isToday && isCODCompleted) {
      return sum + (d.cashCollected || 0)
    }
    return sum
  }, 0)

  const submittedTotal = todayRecons.reduce((sum, r) => sum + r.submittedCollection, 0)
  const gapTotal = expectedTotal - submittedTotal

  // Prepare chart data: expected vs submitted collections by delivery boy
  const getBoyName = (id) => {
    const b = deliveryBoys.find(u => u.id === id)
    return b ? b.name : 'Unknown'
  }

  const chartData = deliveryBoys.map(boy => {
    const boyDeliveries = deliveries.filter(d => 
      d.deliveryBoyId === boy.id && 
      d.createdAt?.split('T')[0] === todayStr &&
      (d.status === 'COD_COLLECTED' || d.status === 'COD_PARTIAL')
    )
    const expected = boyDeliveries.reduce((sum, d) => sum + (d.cashCollected || 0), 0)
    
    const boyRecon = todayRecons.find(r => r.deliveryBoyId === boy.id)
    const submitted = boyRecon ? boyRecon.submittedCollection : 0
    const gap = expected - submitted

    return {
      name: boy.name,
      Expected: expected,
      Submitted: submitted,
      Gap: gap
    }
  }).filter(data => data.Expected > 0 || data.Submitted > 0)

  const columns = [
    { header: 'Date', accessor: 'date', width: 120 },
    { header: 'Delivery Boy', accessor: 'deliveryBoyId', render: (row) => getBoyName(row.deliveryBoyId) },
    { header: 'Expected (₹)', accessor: 'expectedCollection', render: (row) => row.expectedCollection?.toLocaleString('en-IN', { minimumFractionDigits: 2 }) },
    { header: 'Submitted (₹)', accessor: 'submittedCollection', render: (row) => row.submittedCollection?.toLocaleString('en-IN', { minimumFractionDigits: 2 }) },
    { header: 'Gap (₹)', accessor: 'gap', render: (row) => (
      <span style={{ fontWeight: 600, color: row.gap > 0 ? 'var(--color-danger)' : 'var(--color-success)' }}>
        ₹{row.gap?.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
      </span>
    )},
    { header: 'Status', accessor: 'status', render: (row) => {
      let badgeClass = 'badge-neutral'
      if (row.status === 'APPROVED') badgeClass = 'badge-success'
      if (row.status === 'FLAGGED') badgeClass = 'badge-danger'
      if (row.status === 'PENDING') badgeClass = 'badge-warning'
      return <span className={`badge ${badgeClass}`}>{row.status}</span>
    }},
    { header: 'Admin Notes', accessor: 'adminNotes' }
  ]

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h2 className="page-title">COD Collection Dashboard</h2>
          <p className="page-subtitle">Track, reconcile, and audit cash/UPI delivery boy collections</p>
        </div>
        {isAdminOrManager && (
          <div className="page-actions">
            <motion.button
              className="btn btn-primary"
              onClick={() => {
                setSubmitForm({
                  deliveryBoyId: '',
                  date: getLocalDateStr(),
                  submittedCollection: '',
                  adminNotes: ''
                })
                setShowSubmitModal(true)
              }}
              whileTap={{ scale: 0.95 }}
            >
              <Send size={18} /> Record Collection Submission
            </motion.button>
          </div>
        )}
      </div>

      {/* Stats Cards Grid */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
        gap: 'var(--space-4)',
        marginBottom: 'var(--space-6)'
      }}>
        <div style={{
          background: 'var(--color-surface)',
          border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-lg)',
          padding: 'var(--space-5)',
          display: 'flex',
          alignItems: 'center',
          gap: 'var(--space-4)'
        }}>
          <div style={{ width: 48, height: 48, borderRadius: '50%', background: 'rgba(59, 130, 246, 0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Clock size={24} style={{ color: 'var(--color-info)' }} />
          </div>
          <div>
            <h4 style={{ color: 'var(--color-text-muted)', fontSize: '12px', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Today's Expected Cash</h4>
            <p style={{ fontSize: '24px', fontWeight: 700, color: 'var(--color-text)' }}>₹{expectedTotal.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</p>
          </div>
        </div>

        <div style={{
          background: 'var(--color-surface)',
          border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-lg)',
          padding: 'var(--space-5)',
          display: 'flex',
          alignItems: 'center',
          gap: 'var(--space-4)'
        }}>
          <div style={{ width: 48, height: 48, borderRadius: '50%', background: 'rgba(16, 185, 129, 0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <CheckCircle size={24} style={{ color: 'var(--color-success)' }} />
          </div>
          <div>
            <h4 style={{ color: 'var(--color-text-muted)', fontSize: '12px', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Submitted Cash</h4>
            <p style={{ fontSize: '24px', fontWeight: 700, color: 'var(--color-text)' }}>₹{submittedTotal.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</p>
          </div>
        </div>

        <div style={{
          background: 'var(--color-surface)',
          border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-lg)',
          padding: 'var(--space-5)',
          display: 'flex',
          alignItems: 'center',
          gap: 'var(--space-4)'
        }}>
          <div style={{ width: 48, height: 48, borderRadius: '50%', background: gapTotal > 0 ? 'rgba(239, 68, 68, 0.1)' : 'rgba(16, 185, 129, 0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            {gapTotal > 0 ? (
              <AlertTriangle size={24} style={{ color: 'var(--color-danger)' }} />
            ) : (
              <ShieldAlert size={24} style={{ color: 'var(--color-success)' }} />
            )}
          </div>
          <div>
            <h4 style={{ color: 'var(--color-text-muted)', fontSize: '12px', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Collection Gap</h4>
            <p style={{ fontSize: '24px', fontWeight: 700, color: gapTotal > 0 ? 'var(--color-danger)' : 'var(--color-success)' }}>
              ₹{gapTotal.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
            </p>
          </div>
        </div>
      </div>

      {/* Chart Section */}
      {chartData.length > 0 && (
        <div style={{
          background: 'var(--color-surface)',
          border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-lg)',
          padding: 'var(--space-5)',
          marginBottom: 'var(--space-6)',
          height: 320
        }}>
          <h3 style={{ fontSize: '16px', fontWeight: 600, marginBottom: 'var(--space-4)', color: 'var(--color-text)' }}>Today's Collections comparison by Delivery Boy</h3>
          <ResponsiveContainer width="100%" height="90%">
            <BarChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
              <XAxis dataKey="name" stroke="var(--color-text-muted)" />
              <YAxis stroke="var(--color-text-muted)" />
              <Tooltip contentStyle={{ background: 'var(--color-surface)', borderColor: 'var(--color-border)' }} />
              <Bar dataKey="Expected" fill="#3b82f6" radius={[4, 4, 0, 0]} />
              <Bar dataKey="Submitted" fill="#10b981" radius={[4, 4, 0, 0]} />
              <Bar dataKey="Gap" fill="#ef4444" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Daily Reconciliation Records Table */}
      <DataTable
        columns={columns}
        data={reconciliations}
        loading={loading}
        searchPlaceholder="Filter records by date or boy..."
        emptyMessage="No reconciliation logs recorded yet."
        actions={isAdminOrManager ? (row) => (
          row.status === 'PENDING' && (
            <button
              className="btn btn-ghost btn-sm"
              onClick={() => {
                setAuditTarget(row)
                setAuditForm({ status: 'APPROVED', adminNotes: row.adminNotes || '' })
                setShowAuditModal(true)
              }}
              style={{ fontSize: '11px', color: 'var(--color-success)', display: 'flex', alignItems: 'center', gap: '4px' }}
            >
              <Check size={14} /> Audit Status
            </button>
          )
        ) : null}
      />

      {/* Submission Modal */}
      <Modal isOpen={showSubmitModal} onClose={() => setShowSubmitModal(false)} title="Record Collection Submission">
        <form onSubmit={handleSubmitCollection} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <div className="form-group">
            <label className="form-label">Delivery Boy *</label>
            <select
              className="form-select"
              value={submitForm.deliveryBoyId}
              onChange={e => setSubmitForm({ ...submitForm, deliveryBoyId: e.target.value })}
              required
            >
              <option value="">Select person</option>
              {deliveryBoys.map(db => (
                <option key={db.id} value={db.id}>{db.name}</option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label className="form-label">Submission Date *</label>
            <input
              type="date"
              className="form-input"
              value={submitForm.date}
              onChange={e => setSubmitForm({ ...submitForm, date: e.target.value })}
              required
            />
          </div>

          {fetchedExpectedAmount !== null && (
            <div style={{
              background: 'var(--color-surface-3)',
              border: '1px solid var(--color-border)',
              padding: 'var(--space-3)',
              borderRadius: 'var(--radius-md)',
              fontSize: '13px',
              display: 'flex',
              flexDirection: 'column',
              gap: '4px'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%' }}>
                <strong>Expected Collection:</strong>
                <span style={{ fontWeight: 600, color: 'var(--color-accent)' }}>
                  ₹{fetchedExpectedAmount.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                </span>
              </div>
              <small style={{ color: 'var(--color-text-muted)', fontSize: '11px', display: 'block' }}>
                *This is the total COD cash/UPI amount collected by this boy on the selected date.
              </small>
            </div>
          )}

          <div className="form-group">
            <label className="form-label">Submitted Collection Amount (₹) *</label>
            <input
              type="number"
              step="0.01"
              className="form-input"
              placeholder="0.00"
              value={submitForm.submittedCollection}
              onChange={e => setSubmitForm({ ...submitForm, submittedCollection: e.target.value })}
              required
              min="0"
            />
          </div>

          <div className="form-group">
            <label className="form-label">Admin Notes</label>
            <textarea
              className="form-textarea"
              placeholder="Optional notes describing cash tally results..."
              rows={2}
              value={submitForm.adminNotes}
              onChange={e => setSubmitForm({ ...submitForm, adminNotes: e.target.value })}
            />
          </div>

          <div className="form-actions">
            <button type="button" className="btn btn-secondary" onClick={() => setShowSubmitModal(false)}>Cancel</button>
            <motion.button type="submit" className="btn btn-primary" disabled={saving} whileTap={{ scale: 0.95 }}>
              {saving ? 'Recording...' : 'Submit Collection Details'}
            </motion.button>
          </div>
        </form>
      </Modal>

      {/* Audit Modal */}
      <Modal isOpen={showAuditModal} onClose={() => setShowAuditModal(false)} title="Audit Reconciliation Record">
        {auditTarget && (
          <form onSubmit={handleAuditSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            <div style={{ background: 'var(--color-bg)', padding: 'var(--space-3)', borderRadius: 'var(--radius-md)', fontSize: '13px' }}>
              <p><strong>Date:</strong> {auditTarget.date}</p>
              <p><strong>Delivery Boy:</strong> {getBoyName(auditTarget.deliveryBoyId)}</p>
              <p><strong>Expected Cash:</strong> ₹{auditTarget.expectedCollection?.toLocaleString('en-IN')}</p>
              <p><strong>Submitted Cash:</strong> ₹{auditTarget.submittedCollection?.toLocaleString('en-IN')}</p>
              <p><strong>Current Gap:</strong> ₹{auditTarget.gap?.toLocaleString('en-IN')}</p>
            </div>
            
            <div className="form-group">
              <label className="form-label">Audit Decision *</label>
              <select
                className="form-select"
                value={auditForm.status}
                onChange={e => setAuditForm({ ...auditForm, status: e.target.value })}
                required
              >
                <option value="APPROVED">✅ Approve & Clear Gap</option>
                <option value="FLAGGED">🚨 Flag / Discrepancy Alert</option>
                <option value="PENDING">⏳ Keep Pending</option>
              </select>
            </div>

            <div className="form-group">
              <label className="form-label">Audit Notes</label>
              <textarea
                className="form-textarea"
                rows={3}
                placeholder="Reasoning for approval/flagging..."
                value={auditForm.adminNotes}
                onChange={e => setAuditForm({ ...auditForm, adminNotes: e.target.value })}
              />
            </div>

            <div className="form-actions">
              <button type="button" className="btn btn-secondary" onClick={() => setShowAuditModal(false)}>Cancel</button>
              <motion.button type="submit" className="btn btn-success" disabled={saving} whileTap={{ scale: 0.95 }}>
                {saving ? 'Saving...' : 'Save Audit Decision'}
              </motion.button>
            </div>
          </form>
        )}
      </Modal>
    </div>
  )
}

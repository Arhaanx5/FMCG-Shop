import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Plus, Edit2, Trash2, ToggleLeft, ToggleRight, MapPin } from 'lucide-react'
import { QRCodeSVG } from 'qrcode.react'
import api from '../services/api'
import DataTable from '../components/DataTable'
import Modal from '../components/Modal'
import ConfirmDialog from '../components/ConfirmDialog'
import { useToast } from '../context/ToastContext'

const ROLES = ['ADMIN', 'MANAGER', 'DELIVERY_BOY', 'SALESMAN']
const emptyForm = { name: '', phone: '', role: 'MANAGER', password: '', monthlySalary: '' }

function getTrackingStatus(lastTimeStr) {
  if (!lastTimeStr) return { label: 'Offline', color: 'var(--color-text-muted)', dot: '#94a3b8' }
  const lastActive = new Date(lastTimeStr)
  const now = new Date()
  const diffMs = now - lastActive
  if (isNaN(diffMs)) return { label: 'Offline', color: 'var(--color-text-muted)', dot: '#94a3b8' }
  
  const diffMins = diffMs / 60000
  if (diffMins <= 5) {
    return { label: 'Active Now', color: 'var(--color-success)', dot: '#10b981' }
  }
  if (diffMins <= 20) {
    return { label: 'Idle', color: 'var(--color-warning)', dot: '#f59e0b' }
  }
  return { label: 'Offline', color: 'var(--color-text-muted)', dot: '#94a3b8' }
}

function formatLastActive(lastTimeStr) {
  if (!lastTimeStr) return 'Never'
  const lastActive = new Date(lastTimeStr)
  const now = new Date()
  const diffMs = now - lastActive
  if (isNaN(diffMs)) return 'Never'
  
  const diffMins = Math.floor(diffMs / 60000)
  if (diffMins < 1) return 'Just now'
  if (diffMins < 60) return `${diffMins}m ago`
  
  const diffHours = Math.floor(diffMins / 60)
  if (diffHours < 24) return `${diffHours}h ago`
  
  return lastActive.toLocaleDateString()
}

export default function Users() {
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [showModal, setShowModal] = useState(false)
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState({ ...emptyForm })
  const [saving, setSaving] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState(null)
  const toast = useToast()

  // MFA states
  const [mfaEnabled, setMfaEnabled] = useState(false)
  const [mfaSetupData, setMfaSetupData] = useState(null) // { secret, qrCodeUrl }
  const [mfaCode, setMfaCode] = useState('')
  const [verifyingMfa, setVerifyingMfa] = useState(false)
  const [showDisableForm, setShowDisableForm] = useState(false)

  useEffect(() => {
    loadUsers()
    loadMfaStatus()
  }, [])

  const loadMfaStatus = async () => {
    try {
      const res = await api.get('/auth/me')
      setMfaEnabled(res.data.mfaEnabled || false)
    } catch (err) {
      console.error('Failed to load MFA status', err)
    }
  }

  const handleSetupMfa = async () => {
    try {
      const res = await api.post('/auth/mfa/setup')
      setMfaSetupData(res.data)
      setMfaCode('')
      setShowDisableForm(false)
    } catch (err) {
      toast.error(err.response?.data?.error || 'Failed to start MFA setup')
    }
  }

  const handleEnableMfa = async (e) => {
    e.preventDefault()
    setVerifyingMfa(true)
    try {
      await api.post('/auth/mfa/enable', { code: mfaCode })
      toast.success('MFA enabled successfully!')
      setMfaEnabled(true)
      setMfaSetupData(null)
      setMfaCode('')
    } catch (err) {
      toast.error(err.response?.data?.error || 'Verification failed')
    } finally {
      setVerifyingMfa(false)
    }
  }

  const handleDisableMfa = async (e) => {
    e.preventDefault()
    setVerifyingMfa(true)
    try {
      await api.post('/auth/mfa/disable', { code: mfaCode })
      toast.success('MFA disabled successfully!')
      setMfaEnabled(false)
      setShowDisableForm(false)
      setMfaCode('')
    } catch (err) {
      toast.error(err.response?.data?.error || 'Verification failed')
    } finally {
      setVerifyingMfa(false)
    }
  }

  const loadUsers = async () => {
    setLoading(true)
    try {
      const res = await api.get('/users')
      setUsers(res.data.data || [])
    } catch { toast.error('Failed to load users') }
    finally { setLoading(false) }
  }

  const openCreate = () => { setForm({ ...emptyForm }); setEditingId(null); setShowModal(true) }
  const openEdit = (u) => {
    setForm({ name: u.name || '', phone: u.phone || '', role: u.role || 'MANAGER', password: '', monthlySalary: u.monthlySalary || '' })
    setEditingId(u.id)
    setShowModal(true)
  }

  const handleSave = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      const payload = {
        name: form.name,
        phone: form.phone,
        role: form.role,
        monthlySalary: form.monthlySalary ? Number(form.monthlySalary) : null,
        // Send password only if user typed something; null means "keep existing"
        password: form.password && form.password.trim() ? form.password : null
      }

      // Frontend password strength check for new users
      if (!editingId && !payload.password) {
        toast.error('Password is required for new users')
        setSaving(false)
        return
      }
      if (!editingId && payload.password) {
        const pwRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,72}$/
        if (!pwRegex.test(payload.password)) {
          toast.error('Password must be 8-72 characters and contain uppercase, lowercase, digit, and special character (@$!%*?&)')
          setSaving(false)
          return
        }
      }

      if (editingId) {
        await api.put(`/users/${editingId}`, payload)
        toast.success('User updated!')
      } else {
        await api.post('/users', payload)
        toast.success('User created!')
      }
      setShowModal(false)
      loadUsers()
    } catch (err) {
      const errorData = err.response?.data;
      if (errorData?.message === 'Validation failed' && errorData?.data) {
        // Show each field validation error clearly
        const valMsg = Object.entries(errorData.data)
          .map(([field, msg]) => `• ${msg}`)
          .join('\n');
        toast.error(valMsg || 'Validation failed');
      } else {
        toast.error(errorData?.message || 'Save failed');
      }
    } finally { setSaving(false) }
  }

  const toggleActive = async (id) => {
    try {
      await api.put(`/users/${id}/toggle-active`)
      toast.success('User status toggled')
      loadUsers()
    } catch { toast.error('Toggle failed') }
  }

  const handleDelete = async () => {
    try {
      await api.delete(`/users/${deleteTarget}`)
      toast.success('User deleted')
      setDeleteTarget(null)
      loadUsers()
    } catch { toast.error('Delete failed') }
  }

  const columns = [
    { header: 'Name', accessor: 'name', render: (row) => (
      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
        <div style={{
          width: 36, height: 36, borderRadius: 'var(--radius-full)',
          background: 'var(--color-surface-3)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-bold)',
          color: 'var(--color-accent)', flexShrink: 0,
        }}>
          {(row.name || 'U')[0].toUpperCase()}
        </div>
        <span className="font-medium">{row.name}</span>
      </div>
    )},
    { header: 'Phone', accessor: 'phone' },
    { header: 'Role', accessor: 'role', render: (row) => {
      const colors = { ADMIN: 'badge-accent', MANAGER: 'badge-info', DELIVERY_BOY: 'badge-success', SALESMAN: 'badge-warning' }
      return <span className={`badge ${colors[row.role] || 'badge-neutral'}`}>{row.role?.replace('_', ' ')}</span>
    }},
    { header: 'Monthly Salary', accessor: 'monthlySalary', render: (row) => row.monthlySalary ? <span className="font-semibold">₹{Number(row.monthlySalary).toLocaleString('en-IN')}</span> : <span className="text-muted">—</span> },
    { header: 'Status', accessor: 'active', render: (row) =>
      row.active ? <span className="badge badge-success">Active</span> : <span className="badge badge-danger">Inactive</span>
    },
  ]

  const trackingUsers = users.filter(u => u.role === 'DELIVERY_BOY' || u.role === 'SALESMAN')

  const trackingColumns = [
    { header: 'Staff Member', accessor: 'name', render: (row) => (
      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
        <div style={{
          width: 36, height: 36, borderRadius: 'var(--radius-full)',
          background: 'var(--color-surface-3)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-bold)',
          color: 'var(--color-accent)', flexShrink: 0,
        }}>
          {(row.name || 'S')[0].toUpperCase()}
        </div>
        <div>
          <div className="font-medium" style={{ lineHeight: 1.2 }}>{row.name}</div>
          <span style={{ fontSize: 'var(--font-size-xs)', opacity: 0.6 }}>{row.phone}</span>
        </div>
      </div>
    )},
    { header: 'Role', accessor: 'role', render: (row) => {
      const colors = { DELIVERY_BOY: 'badge-success', SALESMAN: 'badge-warning' }
      return <span className={`badge ${colors[row.role] || 'badge-neutral'}`}>{row.role?.replace('_', ' ')}</span>
    }},
    { header: 'GPS Status', accessor: 'lastLocationTime', render: (row) => {
      const status = getTrackingStatus(row.lastLocationTime)
      return (
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
          <span style={{
            width: 8, height: 8, borderRadius: '50%', background: status.dot,
            boxShadow: status.label === 'Active Now' ? '0 0 8px #10b981' : 'none'
          }} />
          <span style={{ color: status.color, fontWeight: 500, fontSize: 'var(--font-size-sm)' }}>{status.label}</span>
        </div>
      )
    }},
    { header: 'Coordinates', render: (row) => {
      if (!row.lastLatitude || !row.lastLongitude) {
        return <span style={{ opacity: 0.5, fontStyle: 'italic', fontSize: 'var(--font-size-sm)' }}>No GPS Signal</span>
      }
      return (
        <span style={{ fontFamily: 'monospace', fontSize: 'var(--font-size-sm)', color: 'var(--color-text)' }}>
          {row.lastLatitude.toFixed(6)}, {row.lastLongitude.toFixed(6)}
        </span>
      )
    }},
    { header: 'Last Signal', render: (row) => (
      <span style={{ fontSize: 'var(--font-size-sm)' }}>{formatLastActive(row.lastLocationTime)}</span>
    )},
    { header: 'Map View', render: (row) => {
      if (!row.lastLatitude || !row.lastLongitude) return null
      return (
        <a
          href={`https://www.google.com/maps/search/?api=1&query=${row.lastLatitude},${row.lastLongitude}`}
          target="_blank"
          rel="noopener noreferrer"
          className="btn btn-outline btn-sm"
          style={{ padding: '6px 12px', height: 'auto', display: 'inline-flex', alignItems: 'center', gap: 'var(--space-1)', fontSize: 'var(--font-size-xs)' }}
        >
          <MapPin size={12} /> Google Map
        </a>
      )
    }}
  ]

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h2 className="page-title">User Management</h2>
          <p className="page-subtitle">{users.length} users</p>
        </div>
        <div className="page-actions">
          <motion.button className="btn btn-primary" onClick={openCreate} whileTap={{ scale: 0.95 }}>
            <Plus size={18} /> Add User
          </motion.button>
        </div>
      </div>

      {/* 2FA Settings Card */}
      <div style={{
        marginBottom: 'var(--space-6)',
        background: 'var(--color-bg-alt)',
        padding: 'var(--space-6)',
        borderRadius: 'var(--radius-lg)',
        border: '1px solid var(--color-border)',
      }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--space-4)' }}>
          <div>
            <h3 className="page-title" style={{ fontSize: 'var(--font-size-md)', margin: 0, display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
              <span>🛡️ Multi-Factor Authentication (2FA)</span>
              <span className={`badge ${mfaEnabled ? 'badge-success' : 'badge-danger'}`} style={{ fontSize: '10px' }}>
                {mfaEnabled ? 'ENABLED' : 'DISABLED'}
              </span>
            </h3>
            <p className="page-subtitle" style={{ margin: 'var(--space-1) 0 0 0', fontSize: 'var(--font-size-xs)' }}>
              Secure the Admin account by requiring a 6-digit verification code from Google Authenticator on login.
            </p>
          </div>
          <div>
            {!mfaEnabled ? (
              !mfaSetupData ? (
                <button className="btn btn-primary" onClick={handleSetupMfa}>
                  Enable 2FA
                </button>
              ) : (
                <button className="btn btn-secondary" onClick={() => setMfaSetupData(null)}>
                  Cancel Setup
                </button>
              )
            ) : (
              !showDisableForm ? (
                <button className="btn btn-danger" onClick={() => { setShowDisableForm(true); setMfaCode(''); }}>
                  Disable 2FA
                </button>
              ) : (
                <button className="btn btn-secondary" onClick={() => setShowDisableForm(false)}>
                  Cancel
                </button>
              )
            )}
          </div>
        </div>

        {/* 2FA Setup Form */}
        {mfaSetupData && (
          <div style={{
            marginTop: 'var(--space-5)',
            paddingTop: 'var(--space-5)',
            borderTop: '1px solid var(--color-border)',
            display: 'flex',
            flexWrap: 'wrap',
            gap: 'var(--space-6)',
            alignItems: 'flex-start'
          }}>
            <div style={{ background: '#fff', padding: '10px', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)', flexShrink: 0 }}>
              <QRCodeSVG
                value={decodeURIComponent(mfaSetupData.qrCodeUrl.split('chl=')[1])}
                size={180}
                level="M"
              />
            </div>
            <div style={{ flex: 1, minWidth: 260 }}>
              <h4 style={{ fontSize: 'var(--font-size-sm)', fontWeight: 'bold', marginBottom: 'var(--space-2)' }}>
                1. Scan this QR Code
              </h4>
              <p style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', marginBottom: 'var(--space-3)' }}>
                Open Google Authenticator on your phone, click the "+" button, and scan the QR code above.
                If you cannot scan, you can manually type this key: <strong style={{ color: 'var(--color-accent)' }}>{mfaSetupData.secret}</strong>
              </p>
              <h4 style={{ fontSize: 'var(--font-size-sm)', fontWeight: 'bold', marginBottom: 'var(--space-2)' }}>
                2. Enter Verification Code
              </h4>
              <form onSubmit={handleEnableMfa} style={{ display: 'flex', gap: 'var(--space-2)', maxWidth: 320 }}>
                <input
                  type="text"
                  pattern="[0-9]*"
                  inputMode="numeric"
                  placeholder="6-digit code"
                  className="form-input"
                  value={mfaCode}
                  onChange={(e) => setMfaCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  maxLength={6}
                  required
                  style={{ textAlign: 'center', letterSpacing: '0.1em', fontWeight: 'bold' }}
                />
                <button type="submit" className="btn btn-primary" disabled={verifyingMfa || mfaCode.length !== 6}>
                  {verifyingMfa ? 'Verifying...' : 'Verify & Enable'}
                </button>
              </form>
            </div>
          </div>
        )}

        {/* 2FA Disable Form */}
        {showDisableForm && (
          <div style={{
            marginTop: 'var(--space-5)',
            paddingTop: 'var(--space-5)',
            borderTop: '1px solid var(--color-border)',
            maxWidth: 400
          }}>
            <h4 style={{ fontSize: 'var(--font-size-sm)', fontWeight: 'bold', marginBottom: 'var(--space-2)' }}>
              Confirm Disabling 2FA
            </h4>
            <p style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', marginBottom: 'var(--space-3)' }}>
              Enter the 6-digit verification code from your Authenticator app to confirm disabling Multi-Factor Authentication.
            </p>
            <form onSubmit={handleDisableMfa} style={{ display: 'flex', gap: 'var(--space-2)' }}>
              <input
                type="text"
                pattern="[0-9]*"
                inputMode="numeric"
                placeholder="6-digit code"
                className="form-input"
                value={mfaCode}
                onChange={(e) => setMfaCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                maxLength={6}
                required
                style={{ textAlign: 'center', letterSpacing: '0.1em', fontWeight: 'bold' }}
              />
              <button type="submit" className="btn btn-danger" disabled={verifyingMfa || mfaCode.length !== 6}>
                {verifyingMfa ? 'Verifying...' : 'Disable'}
              </button>
            </form>
          </div>
        )}
      </div>

      <DataTable columns={columns} data={users} loading={loading} searchPlaceholder="Search users..." emptyMessage="No users found"
        actions={(row) => (
          <>
            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => toggleActive(row.id)} title={row.active ? 'Deactivate' : 'Activate'}>
              {row.active ? <ToggleRight size={18} style={{ color: 'var(--color-success)' }} /> : <ToggleLeft size={18} style={{ color: 'var(--color-text-muted)' }} />}
            </button>
            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => openEdit(row)} title="Edit"><Edit2 size={15} /></button>
            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setDeleteTarget(row.id)} style={{ color: 'var(--color-danger)' }} title="Delete"><Trash2 size={15} /></button>
          </>
        )}
      />

      {/* ═══════ LIVE DISPATCH & SALES GPS TRACKER ═══════ */}
      <div style={{
        marginTop: 'var(--space-8)',
        background: 'var(--color-bg-alt)',
        padding: 'var(--space-6)',
        borderRadius: 'var(--radius-lg)',
        border: '1px solid var(--color-border)',
      }}>
        <div style={{ marginBottom: 'var(--space-4)', display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
          <MapPin size={22} style={{ color: 'var(--color-accent)' }} />
          <div>
            <h3 className="page-title" style={{ fontSize: 'var(--font-size-md)', margin: 0 }}>Live Field Staff GPS Tracker</h3>
            <p className="page-subtitle" style={{ margin: 0, fontSize: 'var(--font-size-xs)' }}>Real-time location and connection status of active delivery boys and salesmen</p>
          </div>
        </div>
        
        <DataTable
          columns={trackingColumns}
          data={trackingUsers}
          loading={loading}
          searchPlaceholder="Search field staff..."
          emptyMessage="No tracking data available"
        />
      </div>

      <Modal isOpen={showModal} onClose={() => setShowModal(false)} title={editingId ? 'Edit User' : 'Add User'}>
        <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <div className="form-group">
            <label className="form-label">Name *</label>
            <input className="form-input" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} required minLength={2} placeholder="Full name" />
          </div>
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Phone *</label>
              <input className="form-input" type="tel" value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value.replace(/\D/g, '').slice(0, 10) })} required maxLength={10} placeholder="10-digit phone" />
            </div>
            <div className="form-group">
              <label className="form-label">Role *</label>
              <select className="form-select" value={form.role} onChange={e => setForm({ ...form, role: e.target.value })}>
                {ROLES.map(r => <option key={r} value={r}>{r.replace('_', ' ')}</option>)}
              </select>
            </div>
          </div>
          <div className="form-group">
            <label className="form-label">Monthly Salary ₹</label>
            <input className="form-input" type="number" min="0" value={form.monthlySalary} onChange={e => setForm({ ...form, monthlySalary: e.target.value })} placeholder="Enter fixed monthly salary" />
          </div>
          <div className="form-group">
            <label className="form-label">{editingId ? 'Password (leave blank to keep current)' : 'Password *'}</label>
            <input
              className="form-input"
              type="password"
              value={form.password}
              onChange={e => setForm({ ...form, password: e.target.value })}
              placeholder={editingId ? 'Leave blank to keep current password' : 'Min 8 chars: Upper, Lower, Digit & Special (@$!%*?&)'}
              autoComplete="new-password"
            />
            {!editingId && (
              <p style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', marginTop: 'var(--space-1)' }}>
                Must contain uppercase, lowercase, digit, and special character (@$!%*?&)
              </p>
            )}
          </div>
          <div className="form-actions">
            <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
            <motion.button type="submit" className="btn btn-primary" disabled={saving} whileTap={{ scale: 0.95 }}>
              {saving ? 'Saving...' : editingId ? 'Update User' : 'Create User'}
            </motion.button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog isOpen={!!deleteTarget} onClose={() => setDeleteTarget(null)} onConfirm={handleDelete}
        title="Delete User" message="This will permanently delete the user." />
    </div>
  )
}

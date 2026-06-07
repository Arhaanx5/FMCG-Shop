import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Plus, Edit2, Trash2, ToggleLeft, ToggleRight, MapPin } from 'lucide-react'
import api from '../services/api'
import DataTable from '../components/DataTable'
import Modal from '../components/Modal'
import ConfirmDialog from '../components/ConfirmDialog'
import { useToast } from '../context/ToastContext'

const ROLES = ['ADMIN', 'MANAGER', 'DELIVERY_BOY', 'SALESMAN']
const emptyForm = { name: '', phone: '', role: 'MANAGER', password: '' }

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

  useEffect(() => { loadUsers() }, [])

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
    setForm({ name: u.name || '', phone: u.phone || '', role: u.role || 'MANAGER', password: '' })
    setEditingId(u.id)
    setShowModal(true)
  }

  const handleSave = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      if (editingId) {
        const payload = { ...form }
        if (!payload.password) payload.password = 'unchanged123' // backend should handle
        await api.put(`/users/${editingId}`, payload)
        toast.success('User updated!')
      } else {
        await api.post('/users', form)
        toast.success('User created!')
      }
      setShowModal(false)
      loadUsers()
    } catch (err) { toast.error(err.response?.data?.message || 'Save failed') }
    finally { setSaving(false) }
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
            <label className="form-label">{editingId ? 'Password (leave blank to keep)' : 'Password *'}</label>
            <input className="form-input" type="password" value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} required={!editingId} minLength={editingId ? 0 : 6} placeholder="Min 6 characters" />
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

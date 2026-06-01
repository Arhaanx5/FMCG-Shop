import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Plus, Edit2, Trash2, ToggleLeft, ToggleRight } from 'lucide-react'
import api from '../services/api'
import DataTable from '../components/DataTable'
import Modal from '../components/Modal'
import ConfirmDialog from '../components/ConfirmDialog'
import { useToast } from '../context/ToastContext'

const ROLES = ['ADMIN', 'MANAGER', 'DELIVERY_BOY', 'SALESMAN']
const emptyForm = { name: '', phone: '', role: 'MANAGER', password: '' }

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

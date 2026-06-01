import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Plus, Edit2, Trash2 } from 'lucide-react'
import api from '../services/api'
import DataTable from '../components/DataTable'
import Modal from '../components/Modal'
import ConfirmDialog from '../components/ConfirmDialog'
import { useToast } from '../context/ToastContext'

const emptyForm = { name: '', description: '', salesmanId: '' }

export default function Areas() {
  const [areas, setAreas] = useState([])
  const [salesmen, setSalesmen] = useState([])
  const [loading, setLoading] = useState(true)
  const [showModal, setShowModal] = useState(false)
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState({ ...emptyForm })
  const [saving, setSaving] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState(null)
  const toast = useToast()

  useEffect(() => { loadAreas(); loadSalesmen() }, [])

  const loadAreas = async () => {
    setLoading(true)
    try {
      const res = await api.get('/areas')
      setAreas(res.data.data || [])
    } catch { toast.error('Failed to load areas') }
    finally { setLoading(false) }
  }

  const loadSalesmen = async () => {
    try {
      const res = await api.get('/users')
      const allUsers = res.data.data || []
      setSalesmen(allUsers.filter(u => u.role === 'SALESMAN' && u.active !== false))
    } catch {}
  }

  const openCreate = () => { setForm({ ...emptyForm }); setEditingId(null); setShowModal(true) }
  const openEdit = (a) => { setForm({ name: a.name || '', description: a.description || '', salesmanId: a.salesmanId || '' }); setEditingId(a.id); setShowModal(true) }

  const handleSave = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      if (editingId) {
        await api.put(`/areas/${editingId}`, form)
        toast.success('Area updated!')
      } else {
        await api.post('/areas', form)
        toast.success('Area created!')
      }
      setShowModal(false)
      loadAreas()
    } catch (err) { toast.error(err.response?.data?.message || 'Save failed') }
    finally { setSaving(false) }
  }

  const handleDelete = async () => {
    try {
      await api.delete(`/areas/${deleteTarget}`)
      toast.success('Area deleted')
      setDeleteTarget(null)
      loadAreas()
    } catch { toast.error('Delete failed') }
  }

  const columns = [
    { header: 'Area Name', accessor: 'name', render: (row) => <span className="font-medium">{row.name}</span> },
    { header: 'Assigned Salesman', accessor: 'salesmanName', render: (row) => row.salesmanName ? (
      <div>
        <div style={{ fontWeight: 'var(--font-weight-medium)', color: 'var(--color-accent)' }}>{row.salesmanName}</div>
        {row.salesmanPhone && <div className="text-xs text-muted" style={{ fontSize: '11px' }}>{row.salesmanPhone}</div>}
      </div>
    ) : <span className="text-muted">—</span> },
    { header: 'Description', accessor: 'description', render: (row) => row.description || <span className="text-muted">—</span> },
    { header: 'Customers', accessor: 'customerCount', render: (row) => <span className="badge badge-info">{row.customerCount ?? '—'}</span> },
  ]

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h2 className="page-title">Areas</h2>
          <p className="page-subtitle">{areas.length} delivery areas</p>
        </div>
        <div className="page-actions">
          <motion.button className="btn btn-primary" onClick={openCreate} whileTap={{ scale: 0.95 }}>
            <Plus size={18} /> Add Area
          </motion.button>
        </div>
      </div>

      <DataTable columns={columns} data={areas} loading={loading} searchPlaceholder="Search areas..." emptyMessage="No areas found"
        actions={(row) => (
          <>
            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => openEdit(row)} title="Edit"><Edit2 size={15} /></button>
            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setDeleteTarget(row.id)} style={{ color: 'var(--color-danger)' }} title="Delete"><Trash2 size={15} /></button>
          </>
        )}
      />

      <Modal isOpen={showModal} onClose={() => setShowModal(false)} title={editingId ? 'Edit Area' : 'Add Area'}>
        <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <div className="form-group">
            <label className="form-label">Area Name *</label>
            <input className="form-input" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} required minLength={2} placeholder="e.g. Sector 15" />
          </div>
          <div className="form-group">
            <label className="form-label">Description</label>
            <textarea className="form-textarea" value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} placeholder="Area description..." rows={3} />
          </div>
          <div className="form-group">
            <label className="form-label">Assigned Salesman</label>
            <select
              className="form-select"
              value={form.salesmanId}
              onChange={e => setForm({ ...form, salesmanId: e.target.value || null })}
            >
              <option value="">No Salesman Assigned</option>
              {salesmen.map(sm => (
                <option key={sm.id} value={sm.id}>{sm.name} ({sm.phone})</option>
              ))}
            </select>
          </div>
          <div className="form-actions">
            <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
            <motion.button type="submit" className="btn btn-primary" disabled={saving} whileTap={{ scale: 0.95 }}>
              {saving ? 'Saving...' : editingId ? 'Update Area' : 'Create Area'}
            </motion.button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog isOpen={!!deleteTarget} onClose={() => setDeleteTarget(null)} onConfirm={handleDelete}
        title="Delete Area" message="This will permanently delete the area." />
    </div>
  )
}

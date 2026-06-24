import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Plus, IndianRupee, Edit2, Trash2 } from 'lucide-react'
import api from '../services/api'
import DataTable from '../components/DataTable'
import Modal from '../components/Modal'
import SearchSelect from '../components/SearchSelect'
import StatCard from '../components/StatCard'
import { useToast } from '../context/ToastContext'
import { useAuth } from '../context/AuthContext'

const REASONS = ['LEAK', 'CRUSH', 'EXPIRE', 'SUPPLIER_RETURN', 'OTHER']
const emptyForm = { productId: '', batchId: '', unitLevel: 'SECONDARY', quantity: '1', reason: 'EXPIRE', claimStatus: 'NON_CLAIMABLE', notes: '', supplierName: '' }

const getUnitPrices = (product) => {
  if (!product) return { primary: 0, secondary: 0, single: 0 }
  const primaryPrice = Number(product.buyPriceWithTax || 0)
  const secondaryPerPrimary = Number(product.secondaryPerPrimary || 1)
  const secondaryPrice = primaryPrice / secondaryPerPrimary
  const divisor = (product.secondaryUnit || '').toUpperCase() === 'LADI' ? 10 : 1
  const singlePrice = secondaryPrice / divisor
  return { primary: primaryPrice, secondary: secondaryPrice, single: singlePrice }
}

const mapToBackendUnitType = (unit) => {
  if (!unit) return 'PACK'
  const u = unit.toUpperCase().trim()
  if (u === 'PACKET' || u === 'PACKETS') return 'PACK'
  const valid = ['PACK', 'LADI', 'CRATE', 'BOTTLE', 'BOX']
  if (valid.includes(u)) return u
  if (u.includes('PACK')) return 'PACK'
  if (u.includes('BOX')) return 'BOX'
  if (u.includes('CRATE')) return 'CRATE'
  if (u.includes('BOTTLE')) return 'BOTTLE'
  if (u.includes('LADI')) return 'LADI'
  return 'PACK'
}

export default function Damage() {
  const { isAdmin, isManager } = useAuth()
  const [damages, setDamages] = useState([])
  const [products, setProducts] = useState([])
  const [loading, setLoading] = useState(true)
  const [totalLoss, setTotalLoss] = useState(0)
  const [selectedYear, setSelectedYear] = useState(new Date().getFullYear())
  const [selectedMonth, setSelectedMonth] = useState(new Date().getMonth() + 1)
  const [showModal, setShowModal] = useState(false)
  const [form, setForm] = useState({ ...emptyForm })
  const [saving, setSaving] = useState(false)
  const toast = useToast()

  // Batches local state
  const [batches, setBatches] = useState([])
  const [loadingBatches, setLoadingBatches] = useState(false)

  // Edit Damage State
  const [editingDamage, setEditingDamage] = useState(null)
  const [editNotes, setEditNotes] = useState('')
  const [editClaimStatus, setEditClaimStatus] = useState('NON_CLAIMABLE')
  const [updating, setUpdating] = useState(false)

  useEffect(() => { loadDamage() }, [selectedYear, selectedMonth])
  useEffect(() => { loadProducts() }, [])

  // Load batches dynamically when productId changes
  useEffect(() => {
    if (form.productId) {
      loadBatches(form.productId)
    } else {
      setBatches([])
      setForm(f => ({ ...f, batchId: '' }))
    }
  }, [form.productId])

  // Clear fields on reason change or set default claimStatus
  useEffect(() => {
    if (form.reason !== 'SUPPLIER_RETURN') {
      setForm(f => ({ ...f, supplierName: '', batchId: '' }))
    } else {
      setForm(f => ({ ...f, claimStatus: 'CLAIMABLE' }))
    }
  }, [form.reason])

  const loadProducts = async () => {
    try {
      const res = await api.get('/products?size=500')
      setProducts(res.data.data?.content || res.data.data || [])
    } catch {}
  }

  const loadBatches = async (productId) => {
    setLoadingBatches(true)
    try {
      const res = await api.get(`/stock/batches/${productId}`)
      setBatches(res.data.data || [])
    } catch {
      toast.error('Failed to load active batches, please enter details manually')
      setBatches([])
    } finally {
      setLoadingBatches(false)
    }
  }

  const loadDamage = async () => {
    setLoading(true)
    try {
      const [dRes, lRes] = await Promise.all([
        api.get(`/damage/report?year=${selectedYear}&month=${selectedMonth}`),
        api.get(`/damage/total-loss?year=${selectedYear}&month=${selectedMonth}`),
      ])
      setDamages(dRes.data || [])
      setTotalLoss(Number(lRes.data || 0))
    } catch { toast.error('Failed to load damage data') }
    finally { setLoading(false) }
  }

  const handleSave = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      const selectedProduct = products.find(p => p.id === form.productId)
      if (!selectedProduct) throw new Error('Please select a valid product')

      if (form.reason === 'SUPPLIER_RETURN') {
        if (!form.batchId) throw new Error('Batch is required for Supplier Returns')
        if (!form.supplierName || !form.supplierName.trim()) throw new Error('Supplier Name is required for Supplier Returns')
      }
      
      const payload = {
        productId: form.productId,
        batchId: form.batchId || null,
        unitLevel: form.unitLevel,
        claimStatus: form.claimStatus,
        quantity: Number(form.quantity || 0),
        reason: form.reason,
        notes: form.notes,
        supplierName: form.reason === 'SUPPLIER_RETURN' ? form.supplierName : null
      }
      await api.post('/damage', payload)
      toast.success('Damage logged!')
      setShowModal(false)
      setForm({ ...emptyForm })
      loadDamage()
    } catch (err) { toast.error(err.response?.data?.message || err.message || 'Failed to log damage') }
    finally { setSaving(false) }
  }

  const deleteDamage = async (id) => {
    if (!window.confirm('Are you sure you want to permanently delete this damage log? The deducted stock will be automatically restored back to inventory!')) return
    try {
      await api.delete(`/damage/${id}`)
      toast.success('Damage log deleted and stock restored successfully')
      loadDamage()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Delete failed')
    }
  }

  const openEditDamage = (d) => {
    setEditingDamage(d)
    setEditNotes(d.notes || '')
    setEditClaimStatus(d.claimStatus || 'NON_CLAIMABLE')
  }

  const handleUpdateDamageSubmit = async (e) => {
    e.preventDefault()
    setUpdating(true)
    try {
      await api.put(`/damage/${editingDamage.id}`, {
        claimStatus: editClaimStatus,
        notes: editNotes
      })
      toast.success('Damage log updated successfully')
      setEditingDamage(null)
      loadDamage()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Update failed')
    } finally { setUpdating(false) }
  }

  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
  const updateField = (key, val) => setForm(f => ({ ...f, [key]: val }))

  const columns = [
    { header: 'Product', accessor: 'productName', render: (row) => (
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        <span className="font-medium">{row.productName || '—'}</span>
        {row.batchNumber && <span className="text-xs text-muted" style={{ fontSize: '11px', marginTop: '2px' }}>Batch: {row.batchNumber}</span>}
      </div>
    ) },
    { header: 'Qty', accessor: 'quantity', render: (row) => `${row.quantity} ${row.unitType || ''}` },
    { header: 'Reason', accessor: 'reason', render: (row) => {
      const colors = { LEAK: 'badge-info', CRUSH: 'badge-warning', EXPIRE: 'badge-danger', SUPPLIER_RETURN: 'badge-success', OTHER: 'badge-neutral' }
      return <span className={`badge ${colors[row.reason] || 'badge-neutral'}`}>{row.reason}</span>
    }},
    { header: 'Supplier', accessor: 'supplierName', render: (row) => row.supplierName || <span className="text-muted">—</span> },
    { header: 'Loss / Value ₹', accessor: 'lossAmount', render: (row) => <span className="text-danger font-semibold">₹{Number(row.valueLoss || row.lossAmount || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span> },
    { header: 'Claim Status', accessor: 'claimStatus', render: (row) => {
      const badges = { CLAIMABLE: 'badge-success', PERMANENT_LOSS: 'badge-danger', NON_CLAIMABLE: 'badge-neutral' }
      const labels = { CLAIMABLE: 'Claimable', PERMANENT_LOSS: 'Permanent Loss', NON_CLAIMABLE: 'Non-Claimable' }
      return <span className={`badge ${badges[row.claimStatus] || 'badge-neutral'}`}>{labels[row.claimStatus] || row.claimStatus || 'Non-Claimable'}</span>
    }},
    { header: 'Notes', accessor: 'notes', render: (row) => row.notes || <span className="text-muted">—</span> },
    { header: 'Date', accessor: 'loggedAt', render: (row) => row.loggedAt ? new Date(row.loggedAt).toLocaleDateString('en-IN') : '—' },
    { header: 'Logged By', accessor: 'loggedBy' },
  ]

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h2 className="page-title">Damage Log</h2>
          <p className="page-subtitle">{months[selectedMonth - 1]} {selectedYear}</p>
        </div>
        <div className="page-actions">
          <select className="form-select" value={selectedMonth} onChange={e => setSelectedMonth(Number(e.target.value))} style={{ width: 120 }}>
            {months.map((m, i) => <option key={i} value={i + 1}>{m}</option>)}
          </select>
          <select className="form-select" value={selectedYear} onChange={e => setSelectedYear(Number(e.target.value))} style={{ width: 100 }}>
            {[2024, 2025, 2026].map(y => <option key={y} value={y}>{y}</option>)}
          </select>
          <motion.button className="btn btn-primary" onClick={() => setShowModal(true)} whileTap={{ scale: 0.95 }}>
            <Plus size={18} /> Log Damage
          </motion.button>
        </div>
      </div>

      <div className="grid-3" style={{ marginBottom: 'var(--space-6)' }}>
        <StatCard icon={<IndianRupee size={24} />} label="Total Damage Loss" value={totalLoss} prefix="₹" color="var(--color-danger)" delay={0} />
        <StatCard icon={<IndianRupee size={24} />} label="Damage Entries" value={damages.length} color="var(--color-warning)" delay={1} />
        <StatCard icon={<IndianRupee size={24} />} label="Avg Loss / Entry" value={damages.length > 0 ? Math.round(totalLoss / damages.length) : 0} prefix="₹" color="var(--color-info)" delay={2} />
      </div>

      <DataTable
        columns={columns}
        data={damages}
        loading={loading}
        searchPlaceholder="Search damage logs..."
        emptyMessage="No damage recorded this month"
        actions={(row) => (
          <>
            {(isAdmin || isManager) && (
              <button className="btn btn-ghost btn-icon btn-sm" onClick={() => openEditDamage(row)} title="Edit Claim & Notes"><Edit2 size={15} /></button>
            )}
            {isAdmin && (
              <button className="btn btn-ghost btn-icon btn-sm" onClick={() => deleteDamage(row.id)} title="Delete Damage & Restore Stock" style={{ color: 'var(--color-danger)' }}><Trash2 size={15} /></button>
            )}
          </>
        )}
      />

      <Modal isOpen={showModal} onClose={() => setShowModal(false)} title="Log Damage">
        {(() => {
          const selectedProduct = products.find(p => p.id === form.productId)
          const prices = getUnitPrices(selectedProduct)
          const currentPrice = form.unitLevel === 'PRIMARY' ? prices.primary : form.unitLevel === 'SECONDARY' ? prices.secondary : prices.single
          const currentLoss = currentPrice * (form.quantity || 0)

          return (
            <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
              <div className="form-group">
                <label className="form-label">Product *</label>
                <SearchSelect options={products.filter(p => p.active !== false)} value={form.productId} onChange={val => updateField('productId', val)} labelKey="name" valueKey="id" placeholder="Select product..." />
              </div>

              {form.productId && (
                <div className="form-group">
                  <label className="form-label">Batch {form.reason === 'SUPPLIER_RETURN' ? '*' : '(Optional)'}</label>
                  <select
                    className="form-select"
                    value={form.batchId}
                    onChange={e => {
                      const selectedBatchId = e.target.value
                      updateField('batchId', selectedBatchId)
                      const batchObj = batches.find(b => b.id === selectedBatchId)
                      if (batchObj && batchObj.supplierName) {
                        updateField('supplierName', batchObj.supplierName)
                      }
                    }}
                    required={form.reason === 'SUPPLIER_RETURN'}
                    disabled={loadingBatches}
                  >
                    <option value="">{loadingBatches ? 'Loading batches...' : 'Select a batch...'}</option>
                    {batches.map(b => (
                      <option key={b.id} value={b.id}>
                        {b.batchNumber} - Rem: {b.secondaryRemaining} {selectedProduct?.secondaryUnit || 'units'} (Exp: {b.expiryDate || 'N/A'}) {b.supplierName ? `[${b.supplierName}]` : ''}
                      </option>
                    ))}
                  </select>
                </div>
              )}

              {form.reason === 'SUPPLIER_RETURN' && (
                <div className="form-group">
                  <label className="form-label">Supplier Name *</label>
                  <input
                    className="form-input"
                    type="text"
                    value={form.supplierName}
                    onChange={e => updateField('supplierName', e.target.value)}
                    placeholder="Enter supplier/distributor name..."
                    required
                  />
                </div>
              )}
              
              <div className="form-row">
                <div className="form-group">
                  <label className="form-label">Unit Level *</label>
                  <select className="form-select" value={form.unitLevel} onChange={e => updateField('unitLevel', e.target.value)}>
                    <option value="SECONDARY">Secondary {selectedProduct ? `(${selectedProduct.secondaryUnit})` : ''}</option>
                    <option value="PRIMARY">Primary {selectedProduct ? `(${selectedProduct.primaryUnit})` : ''}</option>
                    <option value="SINGLE">Single Unit {selectedProduct ? `(${selectedProduct.secondaryUnit === 'LADI' ? 'Packet' : selectedProduct.secondaryUnit})` : ''}</option>
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Quantity *</label>
                   <input className="form-input" type="number" min="1" value={form.quantity} onChange={e => updateField('quantity', e.target.value)} required />
                </div>
              </div>

              <div className="form-row">
                <div className="form-group">
                  <label className="form-label">Reason *</label>
                  <select className="form-select" value={form.reason} onChange={e => updateField('reason', e.target.value)}>
                    {REASONS.map(r => <option key={r} value={r}>{r}</option>)}
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Claim Status *</label>
                  <select className="form-select" value={form.claimStatus} onChange={e => updateField('claimStatus', e.target.value)}>
                    <option value="CLAIMABLE">Claimable</option>
                    <option value="PERMANENT_LOSS">Permanent Loss</option>
                    <option value="NON_CLAIMABLE">Non-Claimable</option>
                  </select>
                </div>
              </div>

              {selectedProduct && (
                <div style={{
                  padding: 'var(--space-3) var(--space-4)',
                  backgroundColor: form.reason === 'SUPPLIER_RETURN' ? 'rgba(59, 130, 246, 0.08)' : 'rgba(239, 68, 68, 0.08)',
                  borderLeft: form.reason === 'SUPPLIER_RETURN' ? '4px solid var(--color-info)' : '4px solid var(--color-danger)',
                  borderRadius: 'var(--border-radius-md)',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 'var(--space-2)',
                  marginTop: 'var(--space-2)'
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--font-size-sm)' }}>
                    <span className="text-muted">Buying Price (with Tax):</span>
                    <span className="font-semibold" style={{ color: 'var(--color-text)' }}>
                      ₹{currentPrice.toFixed(2)}
                    </span>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--font-size-base)', fontWeight: 'var(--font-weight-semibold)' }}>
                    <span style={{ color: form.reason === 'SUPPLIER_RETURN' ? 'var(--color-info)' : 'var(--color-danger)' }}>
                      {form.reason === 'SUPPLIER_RETURN' ? 'Estimated Return Value:' : 'Estimated Financial Loss:'}
                    </span>
                    <span style={{ color: form.reason === 'SUPPLIER_RETURN' ? 'var(--color-info)' : 'var(--color-danger)' }}>
                      ₹{currentLoss.toFixed(2)}
                    </span>
                  </div>
                </div>
              )}

              <div className="form-group">
                <label className="form-label">Notes</label>
                <textarea className="form-textarea" value={form.notes} onChange={e => updateField('notes', e.target.value)} placeholder="Additional details..." rows={2} />
              </div>
              <div className="form-actions">
                <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
                <motion.button type="submit" className="btn btn-danger" disabled={saving} whileTap={{ scale: 0.95 }}>
                  {saving ? 'Logging...' : 'Log Damage'}
                </motion.button>
              </div>
            </form>
          )
        })()}
      </Modal>

      {/* Edit Damage Modal */}
      <Modal isOpen={!!editingDamage} onClose={() => setEditingDamage(null)} title="Edit Damage Claim Details">
        {editingDamage && (
          <form onSubmit={handleUpdateDamageSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            <div className="form-group">
              <label className="form-label">Product</label>
              <input className="form-input" value={editingDamage.productName} disabled style={{ opacity: 0.7 }} />
            </div>
            <div className="form-group">
              <label className="form-label">Quantity</label>
              <input className="form-input" value={`${editingDamage.quantity} ${editingDamage.unitType || ''}`} disabled style={{ opacity: 0.7 }} />
            </div>
            <div className="form-group">
              <label className="form-label">Claim Status</label>
              <select className="form-select" value={editClaimStatus} onChange={e => setEditClaimStatus(e.target.value)}>
                <option value="CLAIMABLE">Claimable</option>
                <option value="PERMANENT_LOSS">Permanent Loss</option>
                <option value="NON_CLAIMABLE">Non-Claimable</option>
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Notes</label>
              <textarea className="form-textarea" value={editNotes} onChange={e => setEditNotes(e.target.value)} placeholder="Enter details..." rows={3} />
            </div>
            <div className="form-actions">
              <button type="button" className="btn btn-secondary" onClick={() => setEditingDamage(null)}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={updating}>
                {updating ? 'Updating...' : 'Update Details'}
              </button>
            </div>
          </form>
        )}
      </Modal>
    </div>
  )
}

import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Plus, Edit2, Trash2, MessageCircle, MapPin } from 'lucide-react'
import api from '../services/api'
import DataTable from '../components/DataTable'
import Modal from '../components/Modal'
import ConfirmDialog from '../components/ConfirmDialog'
import SearchSelect from '../components/SearchSelect'
import { useToast } from '../context/ToastContext'

const emptyForm = { name: '', shopName: '', phone: '', areaId: '', openingBalance: '', creditLimit: '', isManualOverride: false, isNpa: false }

export default function Customers() {
  const [customers, setCustomers] = useState([])
  const [areas, setAreas] = useState([])
  const [loading, setLoading] = useState(true)
  const [showModal, setShowModal] = useState(false)
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState({ ...emptyForm })
  const [saving, setSaving] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [activeTab, setActiveTab] = useState('active')
  const [reminderLoading, setReminderLoading] = useState(null)
  const [showLocationModal, setShowLocationModal] = useState(false)
  const [locationCustomer, setLocationCustomer] = useState(null)
  const [locForm, setLocForm] = useState({ latitude: '', longitude: '', method: 'MANUAL' })
  const toast = useToast()

  useEffect(() => { loadCustomers(); loadAreas() }, [])

  const loadCustomers = async () => {
    setLoading(true)
    try {
      const res = await api.get('/customers?size=500')
      setCustomers(res.data.data?.content || res.data.data || [])
    } catch { toast.error('Failed to load customers') }
    finally { setLoading(false) }
  }

  const loadAreas = async () => {
    try {
      const res = await api.get('/areas')
      setAreas(res.data.data || [])
    } catch {}
  }

  const activeCustomers = customers.filter(c => c.active !== false)
  const inactiveCustomers = customers.filter(c => c.active === false)
  const displayList = activeTab === 'active' ? activeCustomers : inactiveCustomers

  const openCreate = () => { setForm({ ...emptyForm }); setEditingId(null); setShowModal(true) }

  const openEdit = (c) => {
    setForm({
      name: c.name || '', shopName: c.shopName || '', phone: c.phone || '',
      areaId: c.areaId || '', openingBalance: c.openingBalance !== undefined ? c.openingBalance.toString() : '',
      isManualOverride: c.isManualOverride || false,
      creditLimit: c.isManualOverride ? (c.manualCreditLimit || '') : '',
      isNpa: c.isNpa || false,
    })
    setEditingId(c.id)
    setShowModal(true)
  }

  const handleSave = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      const payload = {
        ...form,
        areaId: form.areaId || null,
        openingBalance: Number(form.openingBalance || 0),
        creditLimit: form.isManualOverride ? Number(form.creditLimit || 0) : null
      }
      if (editingId) {
        await api.put(`/customers/${editingId}`, payload)
        toast.success('Customer updated!')
      } else {
        await api.post('/customers', payload)
        toast.success('Customer created!')
      }
      setShowModal(false)
      loadCustomers()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Save failed')
    } finally { setSaving(false) }
  }

  const handleDelete = async () => {
    try {
      await api.delete(`/customers/${deleteTarget}`)
      toast.success('Customer deactivated')
      setDeleteTarget(null)
      loadCustomers()
    } catch { toast.error('Deactivation failed') }
  }

  const sendReminder = async (customerId) => {
    setReminderLoading(customerId)
    try {
      const res = await api.post(`/customers/${customerId}/reminder`)
      const data = res.data.data
      if (data?.whatsappLink) {
        window.open(data.whatsappLink, '_blank')
        toast.success('WhatsApp reminder opened!')
      } else {
        toast.info(data?.message || 'Reminder generated')
      }
    } catch { toast.error('Failed to generate reminder') }
    finally { setReminderLoading(null) }
  }

  const openLocationModal = (c) => {
    setLocationCustomer(c)
    setLocForm({
      latitude: c.latitude !== null && c.latitude !== undefined ? c.latitude : '',
      longitude: c.longitude !== null && c.longitude !== undefined ? c.longitude : '',
      method: c.locationMethod || 'MANUAL',
    })
    setShowLocationModal(true)
  }

  const handleCaptureLocation = () => {
    if (!navigator.geolocation) {
      toast.error('Geolocation is not supported by your browser')
      return
    }
    toast.info('Capturing GPS location...')
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLocForm({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          method: 'BROWSER_GPS',
        })
        toast.success('Current location captured!')
      },
      (error) => {
        toast.error('Failed to get location: ' + error.message)
      },
      { enableHighAccuracy: true, timeout: 10000 }
    )
  }

  const handleSaveLocation = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      await api.put(`/customers/${locationCustomer.id}/location`, {
        latitude: parseFloat(locForm.latitude),
        longitude: parseFloat(locForm.longitude),
        method: locForm.method,
      })
      toast.success('Location updated successfully!')
      setShowLocationModal(false)
      loadCustomers()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to update location')
    } finally {
      setSaving(false)
    }
  }

  const updateField = (key, val) => setForm(f => ({ ...f, [key]: val }))

  const columns = [
    { header: 'Customer', accessor: 'name', render: (row) => (
      <div>
        <div style={{ fontWeight: 'var(--font-weight-medium)' }}>{row.name}</div>
        {row.shopName && <div className="text-xs text-muted">{row.shopName}</div>}
        {row.customerCode && <div className="text-xs text-muted">{row.customerCode}</div>}
      </div>
    )},
    { header: 'Phone', accessor: 'phone' },
    { header: 'Area', accessor: 'areaName', render: (row) => row.areaName || <span className="text-muted">—</span> },
    { header: 'Outstanding', accessor: 'totalPending', render: (row) => {
      const pending = Number(row.totalPending || 0)
      return (
        <span style={{ color: pending > 0 ? 'var(--color-danger)' : 'var(--color-success)', fontWeight: 'var(--font-weight-semibold)' }}>
          ₹{pending.toLocaleString('en-IN')}
        </span>
      )
    }},
    { header: 'Credit Limit & Status', accessor: 'effectiveCreditLimit', render: (row) => {
      const pending = Number(row.totalPending || 0)
      const effectiveLimit = Number(row.effectiveCreditLimit || 0)
      const isManual = row.isManualOverride
      const autoEligible = row.autoEligible

      return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', maxWidth: '200px' }}>
          {isManual ? (
            <span className="badge badge-success" style={{ alignSelf: 'flex-start', fontSize: '9px', padding: '2px 6px' }}>Admin Override</span>
          ) : autoEligible ? (
            <span className="badge badge-info" style={{ alignSelf: 'flex-start', fontSize: '9px', padding: '2px 6px' }}>Loyalty Unlocked</span>
          ) : (
            <span className="badge badge-neutral" style={{ alignSelf: 'flex-start', fontSize: '9px', padding: '2px 6px', color: 'var(--color-text-muted)', border: '1px dashed var(--color-border)', background: 'transparent' }}>🔒 Credit Locked</span>
          )}

          <div style={{ fontWeight: 'var(--font-weight-semibold)', fontSize: '13px', color: effectiveLimit > 0 ? 'var(--color-text)' : 'var(--color-text-muted)' }}>
            Limit: ₹{effectiveLimit.toLocaleString('en-IN')}
          </div>

          {!isManual && !autoEligible && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '3px', marginTop: '2px' }}>
              <div>
                <div style={{ fontSize: '10px', color: 'var(--color-text-muted)', display: 'flex', justifyContent: 'space-between' }}>
                  <span>Route Age: {row.daysActive || 0}/30 days</span>
                  <span>{Math.min(Math.round(((row.daysActive || 0)/30)*100), 100)}%</span>
                </div>
                <div style={{ width: '100%', height: '3px', background: 'var(--color-border)', borderRadius: '2px', overflow: 'hidden', marginTop: '1px' }}>
                  <div style={{ width: `${Math.min(((row.daysActive || 0)/30)*100, 100)}%`, height: '100%', background: 'var(--color-info)' }} />
                </div>
              </div>

              <div>
                <div style={{ fontSize: '10px', color: 'var(--color-text-muted)', display: 'flex', justifyContent: 'space-between', marginTop: '1px' }}>
                  <span>Paid: ₹{Math.round(row.cumulativePaidAmount || 0).toLocaleString('en-IN')}/25k</span>
                  <span>{Math.min(Math.round(((row.cumulativePaidAmount || 0)/25000)*100), 100)}%</span>
                </div>
                <div style={{ width: '100%', height: '3px', background: 'var(--color-border)', borderRadius: '2px', overflow: 'hidden', marginTop: '1px' }}>
                  <div style={{ width: `${Math.min(((row.cumulativePaidAmount || 0)/25000)*100, 100)}%`, height: '100%', background: 'var(--color-accent)' }} />
                </div>
              </div>
            </div>
          )}

          {pending > 0 && effectiveLimit > 0 && (
            <div style={{ marginTop: '2px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '9px', color: 'var(--color-danger)', fontWeight: 600 }}>
                <span>O/S Credit Used:</span>
                <span>{Math.min(Math.round((pending / effectiveLimit) * 100), 100)}%</span>
              </div>
              <div style={{ width: '100%', height: '4px', background: 'var(--color-border)', borderRadius: '2px', overflow: 'hidden', marginTop: '1px' }}>
                <div style={{ width: `${Math.min((pending / effectiveLimit) * 100, 100)}%`, height: '100%', background: 'var(--color-danger)' }} />
              </div>
            </div>
          )}
        </div>
      )
    }},
    { header: 'Status', key: 'status', render: (row) => (
      <div style={{ display: 'flex', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
        {row.isNpa && <span className="badge badge-danger">NPA</span>}
        {row.inactive && <span className="badge badge-warning">Inactive</span>}
        {!row.isNpa && !row.inactive && <span className="badge badge-success">Active</span>}
      </div>
    )},
    { header: 'Location', key: 'location', render: (row) => row.hasLocation
      ? <MapPin size={16} style={{ color: 'var(--color-success)' }} />
      : <span className="text-muted text-xs">No GPS</span>,
      sortable: false,
    },
  ]

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h2 className="page-title">Customers</h2>
          <p className="page-subtitle">{activeCustomers.length} active, {inactiveCustomers.length} inactive</p>
        </div>
        <div className="page-actions">
          <motion.button className="btn btn-primary" onClick={openCreate} whileTap={{ scale: 0.95 }}>
            <Plus size={18} /> Add Customer
          </motion.button>
        </div>
      </div>

      <div className="tabs">
        <button className={`tab ${activeTab === 'active' ? 'active' : ''}`} onClick={() => setActiveTab('active')}>
          Active ({activeCustomers.length})
        </button>
        <button className={`tab ${activeTab === 'inactive' ? 'active' : ''}`} onClick={() => setActiveTab('inactive')}>
          Inactive ({inactiveCustomers.length})
        </button>
      </div>

      <DataTable
        columns={columns}
        data={displayList}
        loading={loading}
        searchPlaceholder="Search by name, phone, shop name..."
        emptyMessage="No customers found"
        actions={(row) => (
          <>
            {Number(row.totalPending || 0) > 0 && (
              <button
                className="btn btn-ghost btn-icon btn-sm"
                onClick={() => sendReminder(row.id)}
                title="Send WhatsApp Reminder"
                disabled={reminderLoading === row.id}
                style={{ color: 'var(--color-success)' }}
              >
                <MessageCircle size={15} />
              </button>
            )}
            <button 
              className="btn btn-ghost btn-icon btn-sm" 
              onClick={() => openLocationModal(row)} 
              title="Set GPS Location" 
              style={{ color: 'var(--color-info)' }}
            >
              <MapPin size={15} />
            </button>
            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => openEdit(row)} title="Edit"><Edit2 size={15} /></button>
            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setDeleteTarget(row.id)} title="Deactivate" style={{ color: 'var(--color-danger)' }}><Trash2 size={15} /></button>
          </>
        )}
      />

      {/* Create/Edit Modal */}
      <Modal isOpen={showModal} onClose={() => setShowModal(false)} title={editingId ? 'Edit Customer' : 'Add Customer'}>
        <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <div className="form-group">
            <label className="form-label">Customer Name *</label>
            <input className="form-input" value={form.name} onChange={e => updateField('name', e.target.value)} required minLength={2} placeholder="e.g. Ramesh ji" />
          </div>
          <div className="form-group">
            <label className="form-label">Shop Name</label>
            <input className="form-input" value={form.shopName} onChange={e => updateField('shopName', e.target.value)} placeholder="e.g. Ramesh Kirana Store" />
          </div>
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Phone *</label>
              <input className="form-input" type="tel" value={form.phone} onChange={e => updateField('phone', e.target.value.replace(/\D/g, '').slice(0, 10))} required maxLength={10} placeholder="10-digit number" />
            </div>
            <div className="form-group">
              <label className="form-label">Area</label>
              <SearchSelect
                options={areas}
                value={form.areaId}
                onChange={val => updateField('areaId', val)}
                labelKey="name"
                valueKey="id"
                placeholder="Select area..."
              />
            </div>
          </div>
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Opening Balance ₹</label>
              <input className="form-input" type="number" min="0" step="0.01" value={form.openingBalance} onChange={e => updateField('openingBalance', e.target.value)} />
            </div>
            <div className="form-group" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-1)' }}>
              <label className="form-label">Credit Limit Policy</label>
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', height: '42px' }}>
                <input 
                  type="checkbox" 
                  id="is-manual-override" 
                  checked={form.isManualOverride} 
                  onChange={e => updateField('isManualOverride', e.target.checked)} 
                  style={{ width: 18, height: 18, accentColor: 'var(--color-accent)' }} 
                />
                <label htmlFor="is-manual-override" className="form-label" style={{ margin: 0, fontWeight: 'normal', cursor: 'pointer' }}>Admin Custom Override</label>
              </div>
            </div>
          </div>
          
          {form.isManualOverride && (
            <div className="form-group" style={{ marginTop: '-var(--space-2)' }}>
              <label className="form-label">Custom Credit Limit ₹ *</label>
              <input className="form-input" type="number" min="0" step="1" value={form.creditLimit} onChange={e => updateField('creditLimit', e.target.value)} required placeholder="e.g. 20000" />
              <span className="text-xs text-muted" style={{ display: 'block', marginTop: '2px' }}>Bypasses age & paid transaction requirements completely.</span>
            </div>
          )}
          <div className="form-group" style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', margin: 'var(--space-1) 0' }}>
            <input type="checkbox" id="is-npa" checked={form.isNpa} onChange={e => updateField('isNpa', e.target.checked)} style={{ width: 18, height: 18, accentColor: 'var(--color-accent)' }} />
            <label htmlFor="is-npa" className="form-label" style={{ margin: 0 }}>Mark as NPA (Defaulter credit lock)</label>
          </div>
          <div className="form-actions">
            <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
            <motion.button type="submit" className="btn btn-primary" disabled={saving} whileTap={{ scale: 0.95 }}>
              {saving ? 'Saving...' : editingId ? 'Update Customer' : 'Create Customer'}
            </motion.button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog isOpen={!!deleteTarget} onClose={() => setDeleteTarget(null)} onConfirm={handleDelete}
        title="Deactivate Customer" message="This customer will be marked as inactive." confirmLabel="Deactivate" />

      {/* Set GPS Location Modal */}
      <Modal isOpen={showLocationModal} onClose={() => setShowLocationModal(false)} title={`Set GPS Location — ${locationCustomer?.name || ''}`}>
        <form onSubmit={handleSaveLocation} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <p className="text-sm text-muted">
            Set latitude and longitude coordinates for optimal delivery route planning and maps.
          </p>
          
          <button 
            type="button" 
            className="btn btn-secondary w-full" 
            onClick={handleCaptureLocation}
            style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 'var(--space-2)', border: '1px dashed var(--color-info)', color: 'var(--color-info)' }}
          >
            <MapPin size={18} /> Capture Current GPS Location
          </button>

          <div style={{ display: 'flex', gap: 'var(--space-4)' }}>
            <div className="form-group" style={{ flex: 1 }}>
              <label className="form-label">Latitude *</label>
              <input 
                className="form-input" 
                type="number" 
                step="any" 
                value={locForm.latitude} 
                onChange={e => setLocForm(lf => ({ ...lf, latitude: e.target.value }))} 
                required 
                placeholder="e.g. 26.8467" 
              />
            </div>
            <div className="form-group" style={{ flex: 1 }}>
              <label className="form-label">Longitude *</label>
              <input 
                className="form-input" 
                type="number" 
                step="any" 
                value={locForm.longitude} 
                onChange={e => setLocForm(lf => ({ ...lf, longitude: e.target.value }))} 
                required 
                placeholder="e.g. 80.9462" 
              />
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">Location Collection Method</label>
            <select 
              className="form-select" 
              value={locForm.method} 
              onChange={e => setLocForm(lf => ({ ...lf, method: e.target.value }))}
            >
              <option value="MANUAL">Manual Input</option>
              <option value="BROWSER_GPS">Captured via Browser GPS</option>
              <option value="MOBILE_APP">Captured via Mobile App</option>
            </select>
          </div>

          <div className="form-actions">
            <button type="button" className="btn btn-secondary" onClick={() => setShowLocationModal(false)}>Cancel</button>
            <motion.button type="submit" className="btn btn-primary" disabled={saving} whileTap={{ scale: 0.95 }}>
              {saving ? 'Saving...' : 'Save Location'}
            </motion.button>
          </div>
        </form>
      </Modal>
    </div>
  )
}

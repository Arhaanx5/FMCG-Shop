import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Save, RefreshCw, AlertTriangle, ShieldAlert } from 'lucide-react'
import api from '../services/api'
import { useToast } from '../context/ToastContext'
import { useAuth } from '../context/AuthContext'

const STATES = [
  { code: '01', name: 'Jammu & Kashmir' },
  { code: '02', name: 'Himachal Pradesh' },
  { code: '03', name: 'Punjab' },
  { code: '04', name: 'Chandigarh' },
  { code: '05', name: 'Uttarakhand' },
  { code: '06', name: 'Haryana' },
  { code: '07', name: 'Delhi' },
  { code: '08', name: 'Rajasthan' },
  { code: '09', name: 'Uttar Pradesh' },
  { code: '10', name: 'Bihar' },
  { code: '11', name: 'Sikkim' },
  { code: '12', name: 'Arunachal Pradesh' },
  { code: '13', name: 'Nagaland' },
  { code: '14', name: 'Manipur' },
  { code: '15', name: 'Mizoram' },
  { code: '16', name: 'Tripura' },
  { code: '17', name: 'Meghalaya' },
  { code: '18', name: 'Assam' },
  { code: '19', name: 'West Bengal' },
  { code: '20', name: 'Jharkhand' },
  { code: '21', name: 'Odisha' },
  { code: '22', name: 'Chhattisgarh' },
  { code: '23', name: 'Madhya Pradesh' },
  { code: '24', name: 'Gujarat' },
  { code: '26', name: 'Dadra & Nagar Haveli & Daman & Diu' },
  { code: '27', name: 'Maharashtra' },
  { code: '29', name: 'Karnataka' },
  { code: '30', name: 'Goa' },
  { code: '31', name: 'Lakshadweep' },
  { code: '32', name: 'Kerala' },
  { code: '33', name: 'Tamil Nadu' },
  { code: '34', name: 'Puducherry' },
  { code: '35', name: 'Andaman & Nicobar Islands' },
  { code: '36', name: 'Telangana' },
  { code: '37', name: 'Andhra Pradesh' },
  { code: '38', name: 'Ladakh' }
]

export default function Settings() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const toast = useToast()
  const [activeTab, setActiveTab] = useState('profile')

  // Shop Profile state
  const [profile, setProfile] = useState({
    companyName: '',
    gstin: '',
    fssai: '',
    phone: '',
    address: '',
    stateCode: '09',
    stateName: 'Uttar Pradesh'
  })
  const [loadingProfile, setLoadingProfile] = useState(true)
  const [savingProfile, setSavingProfile] = useState(false)

  // HSN mapping state
  const [mappings, setMappings] = useState([])
  const [liveCategories, setLiveCategories] = useState([])
  const [selectedMonth, setSelectedMonth] = useState(() => {
    const d = new Date()
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  })
  const [loadingHsn, setLoadingHsn] = useState(false)
  const [applyingHsn, setApplyingHsn] = useState(false)
  const [hsnInputs, setHsnInputs] = useState({}) // { categoryKey: hsnCode }

  // Load profile
  const fetchProfile = async () => {
    setLoadingProfile(true)
    try {
      const res = await api.get('/shop-profile')
      if (res.data.data) {
        setProfile(res.data.data)
      }
    } catch {
      toast.error('Failed to load shop profile')
    } finally {
      setLoadingProfile(false)
    }
  }

  // Load mappings & live categories
  const fetchHsnData = async () => {
    setLoadingHsn(true)
    try {
      // 1. Fetch saved mappings
      const mapRes = await api.get('/hsn-mapping')
      const savedMaps = mapRes.data.data || []
      setMappings(savedMaps)

      const initialInputs = {}
      savedMaps.forEach(m => {
        initialInputs[m.categoryKey] = m.hsnCode
      })

      // 2. Fetch live categories for the month
      const [year, month] = selectedMonth.split('-')
      const start = `${year}-${month}-01T00:00:00`
      const lastDay = new Date(year, month, 0).getDate()
      const end = `${year}-${month}-${String(lastDay).padStart(2, '0')}T23:59:59`

      const catRes = await api.get(`/hsn-mapping/live-categories?start=${start}&end=${end}`)
      const cats = catRes.data.data || []
      setLiveCategories(cats)

      // Initialize inputs for categories not yet saved
      cats.forEach(cat => {
        if (!initialInputs[cat]) {
          initialInputs[cat] = ''
        }
      })
      setHsnInputs(initialInputs)
    } catch {
      toast.error('Failed to load HSN mapping data')
    } finally {
      setLoadingHsn(false)
    }
  }

  useEffect(() => {
    fetchProfile()
  }, [])

  useEffect(() => {
    if (activeTab === 'hsn') {
      fetchHsnData()
    }
  }, [activeTab, selectedMonth])

  // Save Shop Profile
  const handleSaveProfile = async (e) => {
    e.preventDefault()
    if (!isAdmin) {
      toast.error('Only administrators can update the shop profile')
      return
    }
    setSavingProfile(true)
    try {
      const res = await api.put('/shop-profile', profile)
      setProfile(res.data.data)
      toast.success('Shop profile updated successfully!')
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to save shop profile')
    } finally {
      setSavingProfile(false)
    }
  }

  // Save single HSN Mapping
  const handleSaveHsnMapping = async (key) => {
    if (!isAdmin) {
      toast.error('Only administrators can edit HSN mappings')
      return
    }
    const code = hsnInputs[key]
    if (!code || !/^[0-9]{4,10}$/.test(code)) {
      toast.error('HSN Code must be a numeric value of 4 to 10 digits')
      return
    }

    try {
      await api.post('/hsn-mapping', {
        categoryKey: key,
        hsnCode: code
      })
      toast.success(`HSN Code saved for "${key.toUpperCase()}"`)
      fetchHsnData()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to save mapping')
    }
  }

  // Apply all mappings to products
  const handleApplyMappings = async () => {
    if (!isAdmin) {
      toast.error('Only administrators can apply HSN mappings')
      return
    }
    setApplyingHsn(true)
    try {
      await api.post('/hsn-mapping/apply')
      toast.success('Category HSN mappings successfully applied to all active products!')
    } catch {
      toast.error('Failed to apply mappings to products')
    } finally {
      setApplyingHsn(false)
    }
  }

  const handleStateChange = (code) => {
    const match = STATES.find(s => s.code === code)
    if (match) {
      setProfile(p => ({ ...p, stateCode: code, stateName: match.name }))
    }
  }

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h2 className="page-title">Settings Console</h2>
          <p className="page-subtitle">Manage shop information, security roles, and tax parameters</p>
        </div>
      </div>

      {/* Tabs list */}
      <div style={{ display: 'flex', gap: 'var(--space-2)', borderBottom: '1.5px solid var(--color-border)', marginBottom: 'var(--space-6)' }}>
        <button
          onClick={() => setActiveTab('profile')}
          style={{
            padding: '12px 24px',
            border: 'none',
            background: 'none',
            fontWeight: 'var(--font-weight-semibold)',
            fontSize: 'var(--font-size-md)',
            color: activeTab === 'profile' ? 'var(--color-primary)' : 'var(--color-text-secondary)',
            borderBottom: activeTab === 'profile' ? '2.5px solid var(--color-primary)' : 'none',
            cursor: 'pointer'
          }}
        >
          Shop Profile
        </button>
        <button
          onClick={() => setActiveTab('hsn')}
          style={{
            padding: '12px 24px',
            border: 'none',
            background: 'none',
            fontWeight: 'var(--font-weight-semibold)',
            fontSize: 'var(--font-size-md)',
            color: activeTab === 'hsn' ? 'var(--color-primary)' : 'var(--color-text-secondary)',
            borderBottom: activeTab === 'hsn' ? '2.5px solid var(--color-primary)' : 'none',
            cursor: 'pointer'
          }}
        >
          HSN Category Mapping
        </button>
      </div>

      {/* Profile settings panel */}
      {activeTab === 'profile' && (
        <div style={{ background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-6)', border: '1px solid var(--color-border)', maxWidth: '800px' }}>
          {!isAdmin && (
            <div style={{ background: 'rgba(239, 68, 68, 0.1)', color: 'var(--color-danger)', border: '1px solid rgba(239, 68, 68, 0.2)', padding: '12px var(--space-4)', borderRadius: 'var(--radius-md)', marginBottom: 'var(--space-6)', display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--font-size-sm)' }}>
              <ShieldAlert size={18} />
              <span>You are viewing in read-only mode. Only system administrators can update shop details.</span>
            </div>
          )}

          {loadingProfile ? (
            <div className="flex items-center justify-center py-12 text-muted">Loading profile configuration...</div>
          ) : (
            <form onSubmit={handleSaveProfile} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
              <div className="form-row">
                <div className="form-group">
                  <label className="form-label">Company Name *</label>
                  <input
                    className="form-input"
                    value={profile.companyName}
                    onChange={e => setProfile(p => ({ ...p, companyName: e.target.value }))}
                    required
                    disabled={!isAdmin}
                    placeholder="e.g. Lari Traders"
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">GSTIN Number *</label>
                  <input
                    className="form-input"
                    value={profile.gstin}
                    onChange={e => setProfile(p => ({ ...p, gstin: e.target.value }))}
                    required
                    disabled={!isAdmin}
                    placeholder="15-character GSTIN (e.g. 09DIMPA1174G1ZC)"
                  />
                </div>
              </div>

              <div className="form-row">
                <div className="form-group">
                  <label className="form-label">FSSAI License (Optional)</label>
                  <input
                    className="form-input"
                    value={profile.fssai || ''}
                    onChange={e => setProfile(p => ({ ...p, fssai: e.target.value }))}
                    disabled={!isAdmin}
                    placeholder="14-digit license number"
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Contact Phone</label>
                  <input
                    className="form-input"
                    value={profile.phone || ''}
                    onChange={e => setProfile(p => ({ ...p, phone: e.target.value }))}
                    disabled={!isAdmin}
                    placeholder="Shop phone number"
                  />
                </div>
              </div>

              <div className="form-row">
                <div className="form-group">
                  <label className="form-label">GST State Code *</label>
                  <select
                    className="form-select"
                    value={profile.stateCode}
                    onChange={e => handleStateChange(e.target.value)}
                    disabled={!isAdmin}
                  >
                    {STATES.map(s => <option key={s.code} value={s.code}>{s.code} - {s.name}</option>)}
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">State Name *</label>
                  <input
                    className="form-input"
                    value={profile.stateName}
                    disabled
                  />
                </div>
              </div>

              <div className="form-group">
                <label className="form-label">Shop Address</label>
                <textarea
                  className="form-input"
                  style={{ height: '80px', resize: 'vertical' }}
                  value={profile.address || ''}
                  onChange={e => setProfile(p => ({ ...p, address: e.target.value }))}
                  disabled={!isAdmin}
                  placeholder="Street, City, Pin details"
                />
              </div>

              {profile.updatedByName && (
                <div className="text-xs text-muted" style={{ marginTop: 'var(--space-2)' }}>
                  Last updated by <strong>{profile.updatedByName}</strong> at {new Date(profile.updatedAt).toLocaleString('en-IN')}
                </div>
              )}

              {isAdmin && (
                <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 'var(--space-4)' }}>
                  <motion.button
                    type="submit"
                    className="btn btn-primary"
                    disabled={savingProfile}
                    whileTap={{ scale: 0.95 }}
                  >
                    <Save size={16} /> {savingProfile ? 'Saving Details...' : 'Save Configuration'}
                  </motion.button>
                </div>
              )}
            </form>
          )}
        </div>
      )}

      {/* HSN category mapping settings panel */}
      {activeTab === 'hsn' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>
          {/* Mapping Control Panel */}
          <div style={{ background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-6)', border: '1px solid var(--color-border)', display: 'flex', flexWrap: 'wrap', gap: 'var(--space-4)', alignItems: 'center', justifyContent: 'space-between' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
              <div>
                <label className="form-label" style={{ marginBottom: '4px' }}>Target Reporting Period</label>
                <input
                  type="month"
                  className="form-input"
                  value={selectedMonth}
                  onChange={e => setSelectedMonth(e.target.value)}
                  style={{ height: '38px', width: '180px' }}
                />
              </div>
              <div className="text-xs text-muted" style={{ marginTop: '20px' }}>
                * Loading categories of products sold in this period
              </div>
            </div>
            {isAdmin && (
              <motion.button
                onClick={handleApplyMappings}
                className="btn"
                disabled={applyingHsn}
                style={{
                  background: 'var(--color-primary)',
                  color: '#fff',
                  border: 'none',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                  height: '38px',
                  padding: '0 20px',
                  fontWeight: '600'
                }}
                whileTap={{ scale: 0.95 }}
              >
                <RefreshCw size={16} className={applyingHsn ? 'spin' : ''} />
                {applyingHsn ? 'Applying Codes...' : 'Apply Mappings to Products'}
              </motion.button>
            )}
          </div>

          {/* Categories Mapping list */}
          <div style={{ background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)', overflow: 'hidden' }}>
            <div style={{ padding: '16px 20px', background: 'var(--color-border-light)', borderBottom: '1px solid var(--color-border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontWeight: 'var(--font-weight-semibold)' }}>Active Categories List ({liveCategories.length})</span>
              <span className="text-xs text-muted">Double-click to map unmapped items</span>
            </div>

            {loadingHsn ? (
              <div className="flex items-center justify-center py-12 text-muted">Refreshing categories mappings...</div>
            ) : liveCategories.length === 0 ? (
              <div className="flex items-center justify-center py-12 text-muted">No billed or active product categories found in selected month</div>
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(340px, 1fr))', gap: 'var(--space-4)', padding: 'var(--space-6)' }}>
                {liveCategories.map(cat => {
                  const mapping = mappings.find(m => m.categoryKey.trim().toLowerCase() === cat.trim().toLowerCase())
                  const isSaved = !!mapping
                  const isCustom = !['chips', 'snacks', 'beverages', 'cigarettes', 'biscuits', 'namkeen'].includes(cat)

                  return (
                    <div
                      key={cat}
                      style={{
                        background: 'var(--color-bg-body)',
                        borderRadius: 'var(--radius-md)',
                        padding: 'var(--space-4)',
                        border: isSaved ? '1px solid rgba(16, 185, 129, 0.2)' : '1px solid var(--color-border)',
                        boxShadow: isSaved ? '0 4px 6px -1px rgba(16, 185, 129, 0.05)' : 'none',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 'var(--space-3)'
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span style={{ fontWeight: '700', fontSize: 'var(--font-size-md)', textTransform: 'uppercase' }}>
                          {cat}
                        </span>
                        <span
                          className={`badge ${isSaved ? 'badge-success' : 'badge-danger'}`}
                          style={{
                            background: isSaved ? 'rgba(16, 185, 129, 0.1)' : 'rgba(239, 68, 68, 0.1)',
                            color: isSaved ? 'var(--color-success)' : 'var(--color-danger)'
                          }}
                        >
                          {isSaved ? 'Mapped' : 'Unmapped'}
                        </span>
                      </div>

                      <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
                        <input
                          className="form-input"
                          placeholder="Enter HSN Code (e.g. 21069099)"
                          value={hsnInputs[cat] || ''}
                          onChange={e => setHsnInputs(h => ({ ...h, [cat]: e.target.value }))}
                          maxLength={10}
                          disabled={!isAdmin}
                          style={{ height: '36px', fontSize: 'var(--font-size-sm)' }}
                        />
                        {isAdmin && (
                          <button
                            onClick={() => handleSaveHsnMapping(cat)}
                            className="btn btn-ghost"
                            style={{
                              height: '36px',
                              background: isSaved ? 'rgba(16, 185, 129, 0.05)' : 'var(--color-border)',
                              borderColor: 'var(--color-border)',
                              padding: '0 12px'
                            }}
                          >
                            Save
                          </button>
                        )}
                      </div>
                      {isCustom && (
                        <div style={{ fontSize: '10px', color: 'var(--color-warning)', fontStyle: 'italic' }}>
                          * Custom product category specificity
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

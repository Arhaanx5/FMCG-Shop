import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Clock, RefreshCw, CheckCircle2, AlertCircle, Play, ShieldAlert, Database, Calendar } from 'lucide-react'
import api from '../services/api'
import { useToast } from '../context/ToastContext'

export default function SchedulerManager() {
  const toast = useToast()
  const [loading, setLoading] = useState(true)
  const [actionLoading, setActionLoading] = useState(null) // key of the loading action
  const [schedulerData, setSchedulerData] = useState(null)
  const [driveFolderId, setDriveFolderId] = useState('')
  const [updatingDriveId, setUpdatingDriveId] = useState(false)
  const [backups, setBackups] = useState([])
  const [loadingBackups, setLoadingBackups] = useState(false)
  const [decryptingFile, setDecryptingFile] = useState(null)
  const [activeGuide, setActiveGuide] = useState(null)

  const fetchBackups = async () => {
    setLoadingBackups(true)
    try {
      const res = await api.get('/backup/list')
      setBackups(res.data.data || [])
    } catch (err) {
      console.error('Failed to fetch backup files:', err)
    } finally {
      setLoadingBackups(false)
    }
  }

  const fetchStatus = async () => {
    setLoading(true)
    try {
      const res = await api.get('/scheduler/status')
      setSchedulerData(res.data.data)
      if (res.data.data?.backup?.driveFolderId) {
        setDriveFolderId(res.data.data.backup.driveFolderId)
      }
    } catch (err) {
      console.error('Failed to fetch scheduler status:', err)
      toast.error('Failed to load scheduler details.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchStatus()
    fetchBackups()
  }, [])

  const handleUpdateDriveId = async () => {
    if (!driveFolderId.trim()) {
      toast.error('Drive Folder ID cannot be empty.')
      return
    }
    setUpdatingDriveId(true)
    try {
      const res = await api.post(`/scheduler/backup/config?driveFolderId=${driveFolderId.trim()}`)
      toast.success(res.data.message || 'Drive Folder ID updated successfully.')
      fetchStatus()
    } catch (err) {
      console.error('Failed to update Drive Folder ID:', err)
      const errMsg = err?.response?.data?.message || err.message || 'Update failed.'
      toast.error(`Failed: ${errMsg}`)
    } finally {
      setUpdatingDriveId(false)
    }
  }

  const runScheduler = async (key, endpoint) => {
    setActionLoading(key)
    toast.info(`Triggering ${key} execution...`)
    try {
      const res = await api.post(`/scheduler/${endpoint}`)
      toast.success(res.data.message || 'Task completed successfully.')
      fetchStatus()
      fetchBackups()
    } catch (err) {
      console.error(`Failed to run ${key}:`, err)
      const errMsg = err?.response?.data?.message || err.message || 'Execution failed.'
      toast.error(`Failed to run task: ${errMsg}`)
    } finally {
      setActionLoading(null)
    }
  }

  const handleDecrypt = async (fileName) => {
    setDecryptingFile(fileName)
    toast.info(`Decrypting backup file: ${fileName}...`)
    try {
      const res = await api.post(`/backup/decrypt?fileName=${fileName}`)
      toast.success(res.data.message || 'Decryption successful. Decrypted file saved.')
      fetchBackups()
    } catch (err) {
      console.error('Failed to decrypt backup:', err)
      const errMsg = err?.response?.data?.message || err.message || 'Decryption failed.'
      toast.error(`Failed: ${errMsg}`)
    } finally {
      setDecryptingFile(null)
    }
  }

  if (loading && !schedulerData) {
    return (
      <div className="page-container" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '80vh', gap: 'var(--space-4)' }}>
        <RefreshCw className="animate-spin text-violet-500" size={32} />
        <span className="text-xs text-muted">Loading schedulers status...</span>
      </div>
    )
  }

  const renderStatusBadge = (statusObj) => {
    const isEnabled = statusObj?.enabled !== false && statusObj?.enabled !== 'Disabled'
    return isEnabled ? (
      <span className="badge badge-success" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
        <CheckCircle2 size={12} /> Active / Enabled
      </span>
    ) : (
      <span className="badge badge-danger" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
        <AlertCircle size={12} /> Inactive / Disabled
      </span>
    )
  }

  const formatDateTime = (isoString) => {
    if (!isoString) return 'Never run'
    const date = new Date(isoString)
    return date.toLocaleString('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: true
    })
  }

  return (
    <div className="page-container">
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-6)' }}>
        <div>
          <h2 className="page-title flex items-center gap-2">
            <Clock className="text-violet-500" size={24} /> Task Schedulers & Cloud Backups
          </h2>
          <p className="page-subtitle">Monitor background automated tasks and manually run database backups or sweeps.</p>
        </div>
        <button className="btn btn-secondary btn-sm" onClick={fetchStatus} disabled={loading} style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <RefreshCw className={loading ? 'animate-spin' : ''} size={14} /> Refresh Status
        </button>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>
        {/* Backup Scheduler Card */}
        {schedulerData?.backup && (
          <div className="card" style={{ display: 'grid', gridTemplateColumns: 'auto 1fr auto', gap: 'var(--space-4)', alignItems: 'start', borderLeft: '4px solid var(--color-accent)' }}>
            <div style={{ padding: 'var(--space-3)', background: 'var(--color-accent-soft)', borderRadius: 'var(--radius-md)', color: 'var(--color-accent)' }}>
              <Database size={24} />
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
                <h3 className="font-bold text-sm text-slate-800 dark:text-slate-100 uppercase tracking-wider">{schedulerData.backup.name}</h3>
                {renderStatusBadge(schedulerData.backup.status)}
              </div>
              <p className="text-xs text-muted">
                Dumps the PostgreSQL database, encrypts the SQL file, and uploads it to Google Drive every single night at 23:59.
              </p>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 'var(--space-3)', marginTop: 'var(--space-2)' }}>
                <div className="text-xs">
                  <span className="text-muted font-medium">Schedule Cron: </span>
                  <code style={{ background: 'var(--color-bg-secondary)', padding: '2px 6px', borderRadius: '4px' }}>{schedulerData.backup.status?.cronExpression || '0 59 23 * * *'}</code>
                </div>
                <div className="text-xs">
                  <span className="text-muted font-medium">Last Run: </span>
                  <span className="font-semibold text-slate-700 dark:text-slate-200">{formatDateTime(schedulerData.backup.status?.lastRunTime)}</span>
                </div>
                <div className="text-xs" style={{ gridColumn: '1 / -1' }}>
                  <span className="text-muted font-medium">Last Run Result: </span>
                  <span className={`font-semibold ${schedulerData.backup.status?.lastRunStatus?.includes('Success') ? 'text-success' : 'text-danger'}`}>
                    {schedulerData.backup.status?.lastRunStatus || 'Never executed'}
                  </span>
                </div>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', marginTop: 'var(--space-3)', maxWidth: '500px', borderTop: '1px dashed var(--color-border)', paddingTop: 'var(--space-2)' }}>
                <span className="text-[11px] font-semibold text-slate-600 dark:text-slate-400" style={{ whiteSpace: 'nowrap' }}>Drive Folder ID:</span>
                <input 
                  type="text" 
                  className="form-input text-xs" 
                  value={driveFolderId} 
                  onChange={(e) => setDriveFolderId(e.target.value)} 
                  disabled={actionLoading !== null || updatingDriveId}
                  style={{ height: '28px', padding: '0 8px', borderRadius: '4px', fontSize: '11px', background: 'var(--color-bg)' }}
                />
                <button 
                  className="btn btn-secondary btn-sm" 
                  onClick={handleUpdateDriveId} 
                  disabled={actionLoading !== null || updatingDriveId || driveFolderId === schedulerData?.backup?.driveFolderId}
                  style={{ padding: '4px 10px', height: '28px', fontSize: '11px', borderRadius: '4px' }}
                >
                  {updatingDriveId ? 'Saving...' : 'Save ID'}
                </button>
              </div>
            </div>
            <button 
              className="btn btn-primary"
              disabled={actionLoading !== null}
              onClick={() => runScheduler('Database Backup', 'backup/run-now')}
              style={{ display: 'flex', alignItems: 'center', gap: '6px', alignSelf: 'center', background: 'var(--color-accent)' }}
            >
              {actionLoading === 'Database Backup' ? <RefreshCw className="animate-spin" size={14} /> : <Play size={14} />}
              Run Backup Now
            </button>
          </div>
        )}

        {/* Expiry Sweep Card */}
        {schedulerData?.expiry && (
          <div className="card" style={{ display: 'grid', gridTemplateColumns: 'auto 1fr auto', gap: 'var(--space-4)', alignItems: 'start', borderLeft: '4px solid var(--color-info)' }}>
            <div style={{ padding: 'var(--space-3)', background: 'var(--color-info-soft)', borderRadius: 'var(--radius-md)', color: 'var(--color-info)' }}>
              <Calendar size={24} />
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
                <h3 className="font-bold text-sm text-slate-800 dark:text-slate-100 uppercase tracking-wider">{schedulerData.expiry.name}</h3>
                {renderStatusBadge(schedulerData.expiry.status)}
              </div>
              <p className="text-xs text-muted">
                Checks all stock batches daily at 1:00 AM. Any batch that has reached or crossed its expiry date is automatically written off from active stock to damage logs.
              </p>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 'var(--space-3)', marginTop: 'var(--space-2)' }}>
                <div className="text-xs">
                  <span className="text-muted font-medium">Schedule Cron: </span>
                  <code style={{ background: 'var(--color-bg-secondary)', padding: '2px 6px', borderRadius: '4px' }}>{schedulerData.expiry.status?.cronExpression || '0 0 1 * * ?'}</code>
                </div>
                <div className="text-xs">
                  <span className="text-muted font-medium">Last Run: </span>
                  <span className="font-semibold text-slate-700 dark:text-slate-200">{formatDateTime(schedulerData.expiry.status?.lastRunTime)}</span>
                </div>
                <div className="text-xs" style={{ gridColumn: '1 / -1' }}>
                  <span className="text-muted font-medium">Last Run Result: </span>
                  <span className={`font-semibold ${schedulerData.expiry.status?.lastRunStatus?.includes('Success') ? 'text-success' : 'text-danger'}`}>
                    {schedulerData.expiry.status?.lastRunStatus || 'Never executed'}
                  </span>
                </div>
              </div>
            </div>
            <button 
              className="btn btn-primary"
              disabled={actionLoading !== null}
              onClick={() => runScheduler('Expiry Sweep', 'expiry/run-now')}
              style={{ display: 'flex', alignItems: 'center', gap: '6px', alignSelf: 'center', background: 'var(--color-info)' }}
            >
              {actionLoading === 'Expiry Sweep' ? <RefreshCw className="animate-spin" size={14} /> : <Play size={14} />}
              Run Sweep Now
            </button>
          </div>
        )}

        {/* Soft Reserve Release Card */}
        {schedulerData?.softReserve && (
          <div className="card" style={{ display: 'grid', gridTemplateColumns: 'auto 1fr auto', gap: 'var(--space-4)', alignItems: 'start', borderLeft: '4px solid var(--color-success)' }}>
            <div style={{ padding: 'var(--space-3)', background: 'var(--color-success-soft)', borderRadius: 'var(--radius-md)', color: 'var(--color-success)' }}>
              <Clock size={24} />
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
                <h3 className="font-bold text-sm text-slate-800 dark:text-slate-100 uppercase tracking-wider">{schedulerData.softReserve.name}</h3>
                {renderStatusBadge(schedulerData.softReserve.status)}
              </div>
              <p className="text-xs text-muted">
                Runs every 1 hour to release inventory locked by bills that are in DRAFT status for more than 4 hours, auto-cancelling those drafts.
              </p>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 'var(--space-3)', marginTop: 'var(--space-2)' }}>
                <div className="text-xs">
                  <span className="text-muted font-medium">Interval: </span>
                  <code style={{ background: 'var(--color-bg-secondary)', padding: '2px 6px', borderRadius: '4px' }}>Every 1 Hour</code>
                </div>
                <div className="text-xs">
                  <span className="text-muted font-medium">Last Run: </span>
                  <span className="font-semibold text-slate-700 dark:text-slate-200">{formatDateTime(schedulerData.softReserve.status?.lastRunTime)}</span>
                </div>
                <div className="text-xs" style={{ gridColumn: '1 / -1' }}>
                  <span className="text-muted font-medium">Last Run Result: </span>
                  <span className={`font-semibold ${schedulerData.softReserve.status?.lastRunStatus?.includes('Success') ? 'text-success' : 'text-danger'}`}>
                    {schedulerData.softReserve.status?.lastRunStatus || 'Never executed'}
                  </span>
                </div>
              </div>
            </div>
            <button 
              className="btn btn-primary"
              disabled={actionLoading !== null}
              onClick={() => runScheduler('Soft Reserve Release', 'soft-reserve/run-now')}
              style={{ display: 'flex', alignItems: 'center', gap: '6px', alignSelf: 'center', background: 'var(--color-success)' }}
            >
              {actionLoading === 'Soft Reserve Release' ? <RefreshCw className="animate-spin" size={14} /> : <Play size={14} />}
              Trigger Cleanup
            </button>
          </div>
        )}
        {/* COD Reconciliation Card */}
        {schedulerData?.codReconciliation && (
          <div className="card" style={{ display: 'grid', gridTemplateColumns: 'auto 1fr auto', gap: 'var(--space-4)', alignItems: 'start', borderLeft: '4px solid var(--color-warning)' }}>
            <div style={{ padding: 'var(--space-3)', background: 'var(--color-warning-soft)', borderRadius: 'var(--radius-md)', color: 'var(--color-warning)' }}>
              <ShieldAlert size={24} />
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
                <h3 className="font-bold text-sm text-slate-800 dark:text-slate-100 uppercase tracking-wider">{schedulerData.codReconciliation.name}</h3>
                {renderStatusBadge(schedulerData.codReconciliation.status)}
              </div>
              <p className="text-xs text-muted">
                Generates Daily EOD Collection Reports at 8:00 PM and checks outstanding deliveries older than 4 hours every 10 minutes to alert managers/admins.
              </p>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 'var(--space-3)', marginTop: 'var(--space-2)' }}>
                <div className="text-xs">
                  <span className="text-muted font-medium">EOD Report Cron: </span>
                  <code style={{ background: 'var(--color-bg-secondary)', padding: '2px 6px', borderRadius: '4px' }}>{schedulerData.codReconciliation.status?.cronExpression || '0 0 20 * * *'}</code>
                </div>
                <div className="text-xs">
                  <span className="text-muted font-medium">Escalation Interval: </span>
                  <code style={{ background: 'var(--color-bg-secondary)', padding: '2px 6px', borderRadius: '4px' }}>Every 10 Mins</code>
                </div>
                <div className="text-xs">
                  <span className="text-muted font-medium">Last EOD Run: </span>
                  <span className="font-semibold text-slate-700 dark:text-slate-200">{formatDateTime(schedulerData.codReconciliation.status?.lastRunTime)}</span>
                </div>
                <div className="text-xs">
                  <span className="text-muted font-medium">Last Escalation Run: </span>
                  <span className="font-semibold text-slate-700 dark:text-slate-200">{formatDateTime(schedulerData.codReconciliation.status?.lastEscalationRunTime)}</span>
                </div>
                <div className="text-xs" style={{ gridColumn: '1 / -1' }}>
                  <span className="text-muted font-medium">Last EOD Status: </span>
                  <span className={`font-semibold ${schedulerData.codReconciliation.status?.lastRunStatus?.includes('Success') ? 'text-success' : 'text-danger'}`}>
                    {schedulerData.codReconciliation.status?.lastRunStatus || 'Never executed'}
                  </span>
                </div>
                <div className="text-xs" style={{ gridColumn: '1 / -1' }}>
                  <span className="text-muted font-medium">Last Escalation Status: </span>
                  <span className={`font-semibold ${schedulerData.codReconciliation.status?.lastEscalationRunStatus?.includes('Success') ? 'text-success' : 'text-danger'}`}>
                    {schedulerData.codReconciliation.status?.lastEscalationRunStatus || 'Never executed'}
                  </span>
                </div>
              </div>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)', alignSelf: 'center' }}>
              <button 
                className="btn btn-primary"
                disabled={actionLoading !== null}
                onClick={() => runScheduler('COD EOD Report', 'cod-reconciliation/run-now')}
                style={{ display: 'flex', alignItems: 'center', gap: '6px', alignSelf: 'center', background: 'var(--color-warning)', color: '#0f172a' }}
              >
                {actionLoading === 'COD EOD Report' ? <RefreshCw className="animate-spin" size={14} /> : <Play size={14} />}
                Run EOD Now
              </button>
              <button 
                className="btn btn-secondary"
                disabled={actionLoading !== null}
                onClick={() => runScheduler('COD Escalation Check', 'cod-escalation/run-now')}
                style={{ display: 'flex', alignItems: 'center', gap: '6px', alignSelf: 'center' }}
              >
                {actionLoading === 'COD Escalation Check' ? <RefreshCw className="animate-spin" size={14} /> : <Play size={14} />}
                Run Escalation Now
              </button>
            </div>
          </div>
        )}
      </div>


      {/* Backup History Table */}
      <div className="card" style={{ marginTop: 'var(--space-6)', display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--color-border)', paddingBottom: 'var(--space-2)' }}>
          <h3 className="font-bold text-sm text-slate-800 dark:text-slate-100 uppercase tracking-wider flex items-center gap-2">
            <Database className="text-violet-500" size={18} /> Available Backups & Restore Guide
          </h3>
          <button className="btn btn-secondary btn-sm" onClick={fetchBackups} disabled={loadingBackups} style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <RefreshCw className={loadingBackups ? 'animate-spin' : ''} size={12} /> Refresh History
          </button>
        </div>

        <p className="text-xs text-muted">
          Following are the database backups stored locally on the server. Encrypted backups must be decrypted before restoration.
        </p>

        {loadingBackups && backups.length === 0 ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: 'var(--space-4)' }}>
            <RefreshCw className="animate-spin text-violet-500" size={20} />
          </div>
        ) : backups.length === 0 ? (
          <div className="text-xs text-muted text-center" style={{ padding: 'var(--space-4)' }}>
            No local backup files found. Run a manual backup to create one.
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="table" style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--color-border)', textAlign: 'left' }}>
                  <th style={{ padding: '8px' }}>Filename</th>
                  <th style={{ padding: '8px' }}>Date Modified</th>
                  <th style={{ padding: '8px' }}>Size (MB)</th>
                  <th style={{ padding: '8px' }}>Type</th>
                  <th style={{ padding: '8px', textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {backups.map((b, idx) => {
                  const isEncrypted = b.fileName.endsWith('.enc')
                  return (
                    <tr key={idx} style={{ borderBottom: '1px solid var(--color-border-soft)' }}>
                      <td style={{ padding: '8px', fontFamily: 'monospace', color: 'var(--color-text)' }}>{b.fileName}</td>
                      <td style={{ padding: '8px', color: 'var(--color-text-muted)' }}>{b.lastModified}</td>
                      <td style={{ padding: '8px', color: 'var(--color-text-muted)' }}>{b.sizeMB} MB</td>
                      <td style={{ padding: '8px' }}>
                        {isEncrypted ? (
                          <span className="badge badge-warning" style={{ fontSize: '10px' }}>ENCRYPTED</span>
                        ) : (
                          <span className="badge badge-success" style={{ fontSize: '10px' }}>PLAIN SQL</span>
                        )}
                      </td>
                      <td style={{ padding: '8px', textAlign: 'right', display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
                        {isEncrypted ? (
                          <button 
                            className="btn btn-secondary btn-xs"
                            onClick={() => handleDecrypt(b.fileName)}
                            disabled={decryptingFile !== null}
                            style={{ fontSize: '11px', padding: '2px 8px' }}
                          >
                            {decryptingFile === b.fileName ? 'Decrypting...' : 'Decrypt File'}
                          </button>
                        ) : (
                          <button 
                            className="btn btn-primary btn-xs"
                            onClick={() => setActiveGuide(b)}
                            style={{ fontSize: '11px', padding: '2px 8px', background: 'var(--color-accent)' }}
                          >
                            Restore Guide
                          </button>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Restore Guide Modal */}
      {activeGuide && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          background: 'rgba(0,0,0,0.5)',
          zIndex: 9999,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          backdropFilter: 'blur(4px)'
        }}>
          <div className="card" style={{
            width: '100%',
            maxWidth: '550px',
            background: 'var(--color-bg)',
            borderRadius: 'var(--radius-lg)',
            boxShadow: 'var(--shadow-xl)',
            padding: 'var(--space-6)',
            display: 'flex',
            flexDirection: 'column',
            gap: 'var(--space-4)'
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--color-border)', paddingBottom: 'var(--space-3)' }}>
              <h4 className="font-bold text-sm text-slate-800 dark:text-slate-100 uppercase tracking-wider flex items-center gap-2">
                <Database size={16} className="text-violet-500" /> Database Restoration Guide
              </h4>
              <button 
                className="btn btn-secondary btn-xs" 
                onClick={() => setActiveGuide(null)}
                style={{ borderRadius: '50%', width: '24px', height: '24px', padding: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', border: 'none', background: 'transparent', cursor: 'pointer', fontSize: '18px' }}
              >
                &times;
              </button>
            </div>

            <div style={{ fontSize: '12px', display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', lineHeight: '1.5' }}>
              <p>
                To restore this backup securely, follow the standard offline administration procedure:
              </p>
              
              <div style={{ background: 'var(--color-bg-secondary)', padding: '12px', borderRadius: 'var(--radius-md)', borderLeft: '3px solid var(--color-accent)' }}>
                <span className="font-semibold" style={{ display: 'block', marginBottom: '6px' }}>Option A: Run local helper script (Recommended)</span>
                Open the project directory on the server, go to the **<code>scripts/backup</code>** folder, right-click on **<code>restore-db.bat</code>**, select **"Run as Administrator"**, and enter:
                <code style={{ display: 'block', background: 'var(--color-bg)', padding: '6px', borderRadius: '4px', marginTop: '6px', fontSize: '11px', fontFamily: 'monospace' }}>
                  {activeGuide.fileName}
                </code>
              </div>

              <div style={{ background: 'var(--color-bg-secondary)', padding: '12px', borderRadius: 'var(--radius-md)', borderLeft: '3px solid var(--color-info)' }}>
                <span className="font-semibold" style={{ display: 'block', marginBottom: '6px' }}>Option B: Run manual psql command on server</span>
                Open Command Prompt on the server, go to PostgreSQL bin directory, and run:
                <div style={{ position: 'relative', marginTop: '6px' }}>
                  <textarea 
                    readOnly 
                    value={`cd "C:\\Program Files\\PostgreSQL\\16\\bin"\npsql -U postgres -d ${window.location.port === '8086' ? 'fmcg_shop_prod' : 'fmcg_shop'} -f "d:\\intelliJ2025\\fmcg-shop\\${window.location.port === '8086' ? 'fmcg-shop-prod' : 'fmcg-shop'}\\backups\\${activeGuide.fileName}"`}
                    style={{ width: '100%', height: '70px', background: 'var(--color-bg)', border: '1px solid var(--color-border)', borderRadius: '4px', fontSize: '11px', fontFamily: 'monospace', padding: '6px', resize: 'none', color: 'var(--color-text)' }}
                  />
                  <button 
                    className="btn btn-secondary btn-xs"
                    onClick={() => {
                      navigator.clipboard.writeText(`cd "C:\\Program Files\\PostgreSQL\\16\\bin"\npsql -U postgres -d ${window.location.port === '8086' ? 'fmcg_shop_prod' : 'fmcg_shop'} -f "d:\\intelliJ2025\\fmcg-shop\\${window.location.port === '8086' ? 'fmcg-shop-prod' : 'fmcg-shop'}\\backups\\${activeGuide.fileName}"`)
                      toast.success('Command copied to clipboard!')
                    }}
                    style={{ position: 'absolute', right: '5px', bottom: '8px', fontSize: '10px', padding: '2px 6px' }}
                  >
                    Copy
                  </button>
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 'var(--space-2)' }}>
              <button className="btn btn-secondary btn-sm" onClick={() => setActiveGuide(null)}>Close</button>
            </div>
          </div>
        </div>
      )}

      {/* Note Callout */}
      <div style={{ display: 'flex', gap: '12px', background: 'rgba(99,102,241,0.04)', padding: '16px', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)', marginTop: 'var(--space-6)', alignItems: 'center' }}>
        <ShieldAlert className="text-violet-500" size={20} style={{ flexShrink: 0 }} />
        <span className="text-xs text-slate-600 dark:text-slate-400" style={{ lineHeight: '1.4' }}>
          <strong>Admin Authorization Required:</strong> Task schedulers run in the background as system threads. Manually triggering task executions here will run them immediately on the active server thread without waiting for their scheduled time.
        </span>
      </div>
    </div>
  )
}

import { useState } from 'react'
import { motion } from 'framer-motion'
import { FileText, Download, AlertCircle, CheckCircle, ArrowRight } from 'lucide-react'
import api from '../services/api'
import { useToast } from '../context/ToastContext'
import { useNavigate } from 'react-router-dom'

export default function GstReports() {
  const toast = useToast()
  const navigate = useNavigate()
  const [selectedMonth, setSelectedMonth] = useState(() => {
    const d = new Date()
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  })
  const [loading, setLoading] = useState(false)
  const [reportData, setReportData] = useState(null)
  const [errorMsg, setErrorMsg] = useState(null)

  // Fetch report data
  const handleVerifyReport = async () => {
    setLoading(true)
    setErrorMsg(null)
    setReportData(null)
    try {
      const res = await api.get(`/reports/gstr1?month=${selectedMonth}`)
      if (res.data.data) {
        setReportData(res.data.data)
        toast.success(`Filing details loaded for period ${selectedMonth}`)
      }
    } catch (err) {
      console.error(err)
      const msg = err.response?.data?.message || 'Failed to verify filing data'
      setErrorMsg(msg)
      toast.error('GSTR-1 Verification Blocked')
    } finally {
      setLoading(false)
    }
  }

  // Convert array to CSV and download
  const downloadCsv = (filename, headers, rows) => {
    const csvContent = [
      headers.join(','),
      ...rows.map(row => row.map(val => {
        // Escape quotes and commas
        const str = String(val === null || val === undefined ? '' : val)
        if (str.includes(',') || str.includes('"') || str.includes('\n')) {
          return `"${str.replace(/"/g, '""')}"`
        }
        return str
      }).join(','))
    ].join('\n')

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.setAttribute('href', url)
    link.setAttribute('download', filename)
    link.style.visibility = 'hidden'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }

  // Download B2CS CSV
  const handleDownloadB2cs = () => {
    if (!reportData) return
    const headers = ['POS', 'Rate (%)', 'Taxable Value', 'CGST Amount', 'SGST Amount', 'IGST Amount']
    const rows = (reportData.b2cs || []).map(e => [
      e.pos,
      e.rate,
      e.taxableValue,
      e.cgst,
      e.sgst,
      e.igst
    ])
    downloadCsv(`GSTR1_B2CS_${selectedMonth}.csv`, headers, rows)
    toast.success('B2CS CSV downloaded')
  }

  // Download HSN Summary CSV
  const handleDownloadHsn = () => {
    if (!reportData) return
    const headers = ['HSN/SAC', 'Description', 'UQC', 'Total Quantity', 'Total Value', 'Taxable Value', 'CGST Amount', 'SGST Amount', 'IGST Amount']
    const rows = (reportData.hsn || []).map(e => [
      e.hsnSc,
      e.description,
      e.uqc,
      e.qty,
      e.val,
      e.txval,
      e.cgst,
      e.sgst,
      e.igst
    ])
    downloadCsv(`GSTR1_HSN_Summary_${selectedMonth}.csv`, headers, rows)
    toast.success('HSN Summary CSV downloaded')
  }

  // Download Document Summary CSV
  const handleDownloadDocSummary = () => {
    if (!reportData || !reportData.docSummary) return
    const headers = ['From Invoice No', 'To Invoice No', 'Total Count', 'Cancelled Count']
    const d = reportData.docSummary
    const rows = [
      [d.fromInum || '—', d.toInum || '—', d.totalCount, d.cancelledCount]
    ]
    downloadCsv(`GSTR1_Doc_Summary_${selectedMonth}.csv`, headers, rows)
    toast.success('Document Summary CSV downloaded')
  }

  // Download complete JSON
  const handleDownloadJson = () => {
    if (!reportData) return
    const str = JSON.stringify(reportData, null, 2)
    const blob = new Blob([str], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.setAttribute('href', url)
    link.setAttribute('download', `GSTR1_Consolidated_${selectedMonth}.json`)
    link.style.visibility = 'hidden'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    toast.success('Complete GSTR-1 JSON downloaded')
  }

  // Calculations for metadata cards
  const summary = reportData ? {
    taxableB2cs: (reportData.b2cs || []).reduce((acc, curr) => acc + Number(curr.taxableValue || 0), 0),
    totalCgst: (reportData.b2cs || []).reduce((acc, curr) => acc + Number(curr.cgst || 0), 0) + (reportData.hsn || []).reduce((acc, curr) => acc + Number(curr.cgst || 0), 0),
    totalSgst: (reportData.b2cs || []).reduce((acc, curr) => acc + Number(curr.sgst || 0), 0) + (reportData.hsn || []).reduce((acc, curr) => acc + Number(curr.sgst || 0), 0),
    totalIgst: (reportData.b2cs || []).reduce((acc, curr) => acc + Number(curr.igst || 0), 0) + (reportData.hsn || []).reduce((acc, curr) => acc + Number(curr.igst || 0), 0),
    invoiceCount: reportData.docSummary?.totalCount || 0
  } : null

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h2 className="page-title">GSTR-1 Manual Export</h2>
          <p className="page-subtitle">Consolidate transactions, audit missing HSN codes, and export GST filing sheets</p>
        </div>
      </div>

      {/* Control Card */}
      <div style={{ background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-6)', border: '1px solid var(--color-border)', marginBottom: 'var(--space-6)', display: 'flex', flexWrap: 'wrap', gap: 'var(--space-4)', alignItems: 'center' }}>
        <div>
          <label className="form-label" style={{ marginBottom: '4px' }}>Filing Calendar Month</label>
          <input
            type="month"
            className="form-input"
            value={selectedMonth}
            onChange={e => setSelectedMonth(e.target.value)}
            style={{ height: '38px', width: '200px' }}
          />
        </div>
        <motion.button
          onClick={handleVerifyReport}
          className="btn btn-primary"
          disabled={loading}
          style={{ height: '38px', marginTop: '20px', display: 'flex', alignItems: 'center', gap: '8px' }}
          whileTap={{ scale: 0.95 }}
        >
          <FileText size={16} /> {loading ? 'Auditing Invoices...' : 'Verify & Load GSTR-1 Data'}
        </motion.button>
      </div>

      {/* Error Block: Missing HSN Codes */}
      {errorMsg && (
        <div style={{ background: 'rgba(239, 68, 68, 0.08)', border: '1.5px solid var(--color-danger)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-6)', marginBottom: 'var(--space-6)', display: 'flex', gap: '16px' }}>
          <AlertCircle size={32} style={{ color: 'var(--color-danger)', flexShrink: 0 }} />
          <div style={{ flex: 1 }}>
            <h3 style={{ margin: '0 0 6px 0', fontSize: 'var(--font-size-lg)', color: 'var(--color-danger)', fontWeight: '700' }}>
              Filing Blocked: Missing HSN Codes Detected
            </h3>
            <p style={{ margin: '0 0 16px 0', color: 'var(--color-text-secondary)', fontSize: 'var(--font-size-md)', lineHeight: '1.5' }}>
              {errorMsg}
            </p>
            <motion.button
              onClick={() => navigate('/settings?tab=hsn')}
              className="btn btn-ghost"
              style={{ borderColor: 'var(--color-danger)', color: 'var(--color-danger)', background: 'transparent', display: 'flex', alignItems: 'center', gap: '8px' }}
              whileTap={{ scale: 0.95 }}
            >
              Configure HSN Mapping Settings <ArrowRight size={16} />
            </motion.button>
          </div>
        </div>
      )}

      {/* Success Block: Summary Cards and Downloads */}
      {reportData && summary && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>
          {/* Metadata Cards */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 'var(--space-4)' }}>
            <div style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)' }}>
              <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', textTransform: 'uppercase', fontWeight: '700', marginBottom: '4px' }}>B2CS Taxable Value</div>
              <div style={{ fontSize: 'var(--font-size-2xl)', fontWeight: '800' }}>₹{summary.taxableB2cs.toLocaleString('en-IN')}</div>
            </div>
            <div style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)' }}>
              <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', textTransform: 'uppercase', fontWeight: '700', marginBottom: '4px' }}>Consolidated CGST / SGST</div>
              <div style={{ fontSize: 'var(--font-size-2xl)', fontWeight: '800', color: 'var(--color-primary)' }}>₹{summary.totalCgst.toLocaleString('en-IN')}</div>
            </div>
            <div style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)' }}>
              <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', textTransform: 'uppercase', fontWeight: '700', marginBottom: '4px' }}>Consolidated IGST</div>
              <div style={{ fontSize: 'var(--font-size-2xl)', fontWeight: '800' }}>₹{summary.totalIgst.toLocaleString('en-IN')}</div>
            </div>
            <div style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)' }}>
              <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', textTransform: 'uppercase', fontWeight: '700', marginBottom: '4px' }}>Document Count</div>
              <div style={{ fontSize: 'var(--font-size-2xl)', fontWeight: '800', color: 'var(--color-success)' }}>{summary.invoiceCount} Invoices</div>
            </div>
          </div>

          {/* Download Center Grid */}
          <div style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
            <div style={{ padding: '16px 20px', background: 'var(--color-border-light)', borderBottom: '1px solid var(--color-border)', display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--color-success)', fontWeight: '700' }}>
              <CheckCircle size={18} />
              <span>Filing Data Verified & Ready for Export</span>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 'var(--space-4)', padding: 'var(--space-6)' }}>
              {/* B2CS */}
              <div style={{ border: '1px solid var(--color-border)', padding: 'var(--space-4)', borderRadius: 'var(--radius-md)', display: 'flex', flexDirection: 'column', gap: '12px' }}>
                <div>
                  <h4 style={{ margin: '0 0 4px 0', fontSize: 'var(--font-size-md)', fontWeight: '700' }}>B2CS (Consolidated Sales)</h4>
                  <p style={{ margin: '0', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>Filing sheet containing unregistered local and interstate consumer sales grouped by POS and tax rate.</p>
                </div>
                <button onClick={handleDownloadB2cs} className="btn btn-ghost" style={{ width: '100%', display: 'flex', alignItems: 'center', gap: '8px', justifyContent: 'center' }}>
                  <Download size={14} /> Download B2CS CSV
                </button>
              </div>

              {/* HSN SUMMARY */}
              <div style={{ border: '1px solid var(--color-border)', padding: 'var(--space-4)', borderRadius: 'var(--radius-md)', display: 'flex', flexDirection: 'column', gap: '12px' }}>
                <div>
                  <h4 style={{ margin: '0 0 4px 0', fontSize: 'var(--font-size-md)', fontWeight: '700' }}>HSN Summary Table</h4>
                  <p style={{ margin: '0', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>Consolidated summary of goods sold grouped by HSN code, total value, quantity, and tax categories.</p>
                </div>
                <button onClick={handleDownloadHsn} className="btn btn-ghost" style={{ width: '100%', display: 'flex', alignItems: 'center', gap: '8px', justifyContent: 'center' }}>
                  <Download size={14} /> Download HSN CSV
                </button>
              </div>

              {/* DOC SUMMARY */}
              <div style={{ border: '1px solid var(--color-border)', padding: 'var(--space-4)', borderRadius: 'var(--radius-md)', display: 'flex', flexDirection: 'column', gap: '12px' }}>
                <div>
                  <h4 style={{ margin: '0 0 4px 0', fontSize: 'var(--font-size-md)', fontWeight: '700' }}>Document Summary</h4>
                  <p style={{ margin: '0', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>Filing section detailing invoice series ranges (From-To) issued, total quantity, and cancelled bills count.</p>
                </div>
                <button onClick={handleDownloadDocSummary} className="btn btn-ghost" style={{ width: '100%', display: 'flex', alignItems: 'center', gap: '8px', justifyContent: 'center' }}>
                  <Download size={14} /> Download Docs CSV
                </button>
              </div>

              {/* COMPLETE JSON */}
              <div style={{ border: '1px solid var(--color-border)', padding: 'var(--space-4)', borderRadius: 'var(--radius-md)', display: 'flex', flexDirection: 'column', gap: '12px', background: 'rgba(59, 130, 246, 0.03)' }}>
                <div>
                  <h4 style={{ margin: '0 0 4px 0', fontSize: 'var(--font-size-md)', fontWeight: '700', color: 'var(--color-primary)' }}>Complete GSTR-1 JSON</h4>
                  <p style={{ margin: '0', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>Consolidated combination file of B2B placeholders, B2CS consumer sales, HSN summaries, and doc info.</p>
                </div>
                <button onClick={handleDownloadJson} className="btn btn-primary" style={{ width: '100%', display: 'flex', alignItems: 'center', gap: '8px', justifyContent: 'center' }}>
                  <Download size={14} /> Download Filing JSON
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

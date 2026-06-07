import { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { AlertTriangle, CheckCircle2, ArrowRight, ChevronLeft, Zap, Hand } from 'lucide-react'

/**
 * OverpaymentModal
 *
 * Props:
 *  - isOpen: boolean
 *  - preview: OverpaymentPreviewResponse from API
 *  - onConfirm(adjustmentType, targetBillId): called when user confirms
 *  - onCancel(): called when user cancels
 */
export default function OverpaymentModal({ isOpen, preview, onConfirm, onCancel }) {
  const [step, setStep] = useState('choose')          // 'choose' | 'manual-select' | 'auto-preview' | 'confirm'
  const [selectedType, setSelectedType] = useState(null)
  const [selectedTargetBillId, setSelectedTargetBillId] = useState(null)

  if (!isOpen || !preview) return null

  const fmt = (n) => Number(n || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })

  const hasOtherBills = preview.otherPendingBills?.length > 0
  const autoFullyCovers = Number(preview.remainingAfterAuto || 0) <= 0

  const handleOptionSelect = (type) => {
    setSelectedType(type)
    if (type === 'MANUAL_ADJUST') {
      setStep('manual-select')
    } else {
      setStep('auto-preview')
    }
  }

  const handleConfirm = () => {
    onConfirm(selectedType, selectedTargetBillId)
    // Reset state for next use
    setStep('choose')
    setSelectedType(null)
    setSelectedTargetBillId(null)
  }

  const handleBack = () => {
    setSelectedTargetBillId(null)
    setStep('choose')
  }

  const handleCancel = () => {
    setStep('choose')
    setSelectedType(null)
    setSelectedTargetBillId(null)
    onCancel()
  }

  return (
    <div style={{
      position: 'fixed', inset: 0, zIndex: 9999,
      background: 'rgba(0,0,0,0.55)', backdropFilter: 'blur(4px)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      padding: '16px'
    }}>
      <motion.div
        initial={{ opacity: 0, scale: 0.92, y: 20 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.92, y: 20 }}
        transition={{ duration: 0.22 }}
        style={{
          background: 'var(--color-bg-card)',
          border: '1.5px solid rgba(251,191,36,0.4)',
          borderRadius: '16px',
          width: '100%',
          maxWidth: '560px',
          maxHeight: '90vh',
          overflowY: 'auto',
          boxShadow: '0 25px 60px -12px rgba(0,0,0,0.4)'
        }}
      >
        {/* Header */}
        <div style={{
          padding: '20px 24px 16px',
          borderBottom: '1px solid var(--color-border)',
          display: 'flex', alignItems: 'center', gap: '12px'
        }}>
          <div style={{
            width: 40, height: 40, borderRadius: '10px',
            background: 'rgba(251,191,36,0.15)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            flexShrink: 0
          }}>
            <AlertTriangle size={20} color="#f59e0b" />
          </div>
          <div>
            <div style={{ fontWeight: 700, fontSize: '15px', color: 'var(--color-text-primary)' }}>
              Excess Payment Detected
            </div>
            <div style={{ fontSize: '13px', color: 'var(--color-text-muted)', marginTop: '2px' }}>
              Payment exceeds bill pending amount
            </div>
          </div>
        </div>

        {/* Amounts summary bar */}
        <div style={{
          display: 'grid', gridTemplateColumns: '1fr 1fr 1fr',
          gap: '1px', background: 'var(--color-border)',
          borderBottom: '1px solid var(--color-border)'
        }}>
          {[
            { label: 'Bill Pending', value: `₹${fmt(preview.sourceBillPending)}`, color: '#94a3b8' },
            { label: 'Payment Received', value: `₹${fmt(preview.paymentAmount)}`, color: '#60a5fa' },
            { label: 'Excess Amount', value: `₹${fmt(preview.excessAmount)}`, color: '#f59e0b', bold: true },
          ].map((item) => (
            <div key={item.label} style={{
              padding: '14px 16px', background: 'var(--color-bg-card)',
              textAlign: 'center'
            }}>
              <div style={{ fontSize: '11px', color: 'var(--color-text-muted)', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                {item.label}
              </div>
              <div style={{ fontSize: '17px', fontWeight: item.bold ? 800 : 600, color: item.color, marginTop: '4px' }}>
                {item.value}
              </div>
            </div>
          ))}
        </div>

        <div style={{ padding: '20px 24px' }}>
          <AnimatePresence mode="wait">

            {/* ── STEP 1: Choose option ── */}
            {step === 'choose' && (
              <motion.div key="choose" initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }}>
                <p style={{ fontSize: '13px', color: 'var(--color-text-secondary)', marginBottom: '16px' }}>
                  How would you like to handle the excess <strong style={{ color: '#f59e0b' }}>₹{fmt(preview.excessAmount)}</strong>?
                </p>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                  {/* Option 1: Manual Adjust */}
                  <button
                    onClick={() => handleOptionSelect('MANUAL_ADJUST')}
                    disabled={!hasOtherBills}
                    style={{
                      padding: '14px 16px', borderRadius: '12px', textAlign: 'left',
                      border: '1.5px solid var(--color-border)',
                      background: 'var(--color-bg-secondary)',
                      cursor: hasOtherBills ? 'pointer' : 'not-allowed',
                      opacity: hasOtherBills ? 1 : 0.45,
                      transition: 'all 0.15s',
                      display: 'flex', alignItems: 'center', gap: '12px'
                    }}
                    onMouseEnter={e => hasOtherBills && (e.currentTarget.style.borderColor = '#60a5fa')}
                    onMouseLeave={e => e.currentTarget.style.borderColor = 'var(--color-border)'}
                  >
                    <div style={{
                      width: 36, height: 36, borderRadius: '8px',
                      background: 'rgba(96,165,250,0.15)',
                      display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0
                    }}>
                      <Hand size={18} color="#60a5fa" />
                    </div>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 600, fontSize: '14px', color: 'var(--color-text-primary)' }}>
                        Manual — Adjust to a specific bill
                      </div>
                      <div style={{ fontSize: '12px', color: 'var(--color-text-muted)', marginTop: '2px' }}>
                        {hasOtherBills
                          ? `${preview.otherPendingBills.length} pending bill(s) available`
                          : 'No other pending bills found'}
                      </div>
                    </div>
                    <ArrowRight size={16} color="var(--color-text-muted)" />
                  </button>

                  {/* Option 2: Auto FIFO */}
                  <button
                    onClick={() => handleOptionSelect('AUTO_ADJUST')}
                    disabled={!hasOtherBills}
                    style={{
                      padding: '14px 16px', borderRadius: '12px', textAlign: 'left',
                      border: '1.5px solid var(--color-border)',
                      background: 'var(--color-bg-secondary)',
                      cursor: hasOtherBills ? 'pointer' : 'not-allowed',
                      opacity: hasOtherBills ? 1 : 0.45,
                      transition: 'all 0.15s',
                      display: 'flex', alignItems: 'center', gap: '12px'
                    }}
                    onMouseEnter={e => hasOtherBills && (e.currentTarget.style.borderColor = '#10b981')}
                    onMouseLeave={e => e.currentTarget.style.borderColor = 'var(--color-border)'}
                  >
                    <div style={{
                      width: 36, height: 36, borderRadius: '8px',
                      background: 'rgba(16,185,129,0.15)',
                      display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0
                    }}>
                      <Zap size={18} color="#10b981" />
                    </div>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 600, fontSize: '14px', color: 'var(--color-text-primary)' }}>
                        Auto — Adjust to oldest bills first
                      </div>
                      <div style={{ fontSize: '12px', color: 'var(--color-text-muted)', marginTop: '2px' }}>
                        {hasOtherBills
                          ? autoFullyCovers
                            ? 'Excess fully covered by pending bills'
                            : `₹${fmt(preview.remainingAfterAuto)} will remain uncovered`
                          : 'No other pending bills found'}
                      </div>
                    </div>
                    <ArrowRight size={16} color="var(--color-text-muted)" />
                  </button>
                </div>

                {!hasOtherBills && (
                  <div style={{
                    marginTop: '14px', padding: '12px', borderRadius: '10px',
                    background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.2)',
                    fontSize: '13px', color: '#ef4444'
                  }}>
                    ⚠️ No other pending bills available. Please reduce the payment amount to ₹{fmt(preview.sourceBillPending)} or less.
                  </div>
                )}

                <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '20px' }}>
                  <button className="btn btn-secondary" onClick={handleCancel}>Cancel</button>
                </div>
              </motion.div>
            )}

            {/* ── STEP 2a: Manual Bill Selection ── */}
            {step === 'manual-select' && (
              <motion.div key="manual" initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }}>
                <p style={{ fontSize: '13px', color: 'var(--color-text-secondary)', marginBottom: '14px' }}>
                  Select the bill to apply the excess <strong style={{ color: '#f59e0b' }}>₹{fmt(preview.excessAmount)}</strong> to:
                </p>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '280px', overflowY: 'auto' }}>
                  {preview.otherPendingBills.map(b => {
                    const canFullyCover = Number(b.pendingAmount) >= Number(preview.excessAmount)
                    return (
                      <label
                        key={b.billId}
                        style={{
                          display: 'flex', alignItems: 'center', gap: '12px',
                          padding: '12px 14px', borderRadius: '10px',
                          border: `1.5px solid ${selectedTargetBillId === b.billId ? '#60a5fa' : 'var(--color-border)'}`,
                          background: selectedTargetBillId === b.billId
                            ? 'rgba(96,165,250,0.08)' : 'var(--color-bg-secondary)',
                          cursor: 'pointer', transition: 'all 0.12s'
                        }}
                      >
                        <input
                          type="radio" name="targetBill" value={b.billId}
                          checked={selectedTargetBillId === b.billId}
                          onChange={() => setSelectedTargetBillId(b.billId)}
                          style={{ accentColor: '#60a5fa' }}
                        />
                        <div style={{ flex: 1 }}>
                          <div style={{ fontWeight: 600, fontSize: '14px', color: 'var(--color-text-primary)' }}>
                            {b.billNumber}
                          </div>
                          <div style={{ fontSize: '12px', color: 'var(--color-text-muted)', marginTop: '2px' }}>
                            Pending: <span style={{ color: '#ef4444', fontWeight: 600 }}>₹{fmt(b.pendingAmount)}</span>
                            {b.createdAt && ` · ${new Date(b.createdAt).toLocaleDateString('en-IN')}`}
                          </div>
                        </div>
                        {canFullyCover
                          ? <span style={{ fontSize: '11px', color: '#10b981', background: 'rgba(16,185,129,0.1)', padding: '2px 8px', borderRadius: '6px' }}>Fits</span>
                          : <span style={{ fontSize: '11px', color: '#f59e0b', background: 'rgba(245,158,11,0.1)', padding: '2px 8px', borderRadius: '6px' }}>Partial</span>
                        }
                      </label>
                    )
                  })}
                </div>

                {selectedTargetBillId && (() => {
                  const target = preview.otherPendingBills.find(b => b.billId === selectedTargetBillId)
                  const applied = Math.min(Number(preview.excessAmount), Number(target?.pendingAmount || 0))
                  return (
                    <div style={{ marginTop: '14px', padding: '12px', borderRadius: '10px', background: 'rgba(96,165,250,0.08)', border: '1px solid rgba(96,165,250,0.2)', fontSize: '13px' }}>
                      <strong style={{ color: '#60a5fa' }}>Preview:</strong> ₹{fmt(applied)} will be applied to {target?.billNumber}
                      {applied < Number(preview.excessAmount) && ` (only ₹${fmt(applied)} of ₹${fmt(preview.excessAmount)} excess covered)`}
                    </div>
                  )
                })()}

                <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '18px', gap: '10px' }}>
                  <button className="btn btn-secondary" onClick={handleBack}>
                    <ChevronLeft size={15} /> Back
                  </button>
                  <button
                    className="btn btn-primary"
                    disabled={!selectedTargetBillId}
                    onClick={() => setStep('confirm')}
                  >
                    Continue <ArrowRight size={15} />
                  </button>
                </div>
              </motion.div>
            )}

            {/* ── STEP 2b: Auto FIFO Preview ── */}
            {step === 'auto-preview' && (
              <motion.div key="auto" initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }}>
                <p style={{ fontSize: '13px', color: 'var(--color-text-secondary)', marginBottom: '14px' }}>
                  The excess <strong style={{ color: '#10b981' }}>₹{fmt(preview.excessAmount)}</strong> will be distributed as follows:
                </p>

                {!autoFullyCovers && (
                  <div style={{ marginBottom: '12px', padding: '10px 14px', borderRadius: '10px', background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.2)', fontSize: '13px', color: '#ef4444' }}>
                    ⚠️ Excess ₹{fmt(preview.excessAmount)} exceeds total other pending bills. Cannot save — please reduce the payment amount.
                  </div>
                )}

                <div style={{ borderRadius: '10px', overflow: 'hidden', border: '1px solid var(--color-border)' }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
                    <thead>
                      <tr style={{ background: 'var(--color-bg-secondary)' }}>
                        {['Bill #', 'Pending Before', 'Will Apply', 'Pending After'].map(h => (
                          <th key={h} style={{ padding: '8px 12px', textAlign: h === 'Bill #' ? 'left' : 'right', fontWeight: 600, color: 'var(--color-text-secondary)', fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.4px' }}>{h}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {preview.autoDistribution?.map((entry, i) => (
                        <tr key={entry.billId} style={{ borderTop: '1px solid var(--color-border)', background: i % 2 === 0 ? 'transparent' : 'var(--color-bg-secondary)' }}>
                          <td style={{ padding: '8px 12px', fontWeight: 600, color: 'var(--color-text-primary)' }}>{entry.billNumber}</td>
                          <td style={{ padding: '8px 12px', textAlign: 'right', color: '#ef4444' }}>₹{fmt(entry.pendingBefore)}</td>
                          <td style={{ padding: '8px 12px', textAlign: 'right', color: '#10b981', fontWeight: 600 }}>₹{fmt(entry.amountApplied)}</td>
                          <td style={{ padding: '8px 12px', textAlign: 'right', color: entry.willBeFullyPaid ? '#10b981' : '#f59e0b', fontWeight: 600 }}>
                            {entry.willBeFullyPaid ? '✓ PAID' : `₹${fmt(entry.pendingAfter)}`}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '18px', gap: '10px' }}>
                  <button className="btn btn-secondary" onClick={handleBack}>
                    <ChevronLeft size={15} /> Back
                  </button>
                  <button
                    className="btn btn-success"
                    disabled={!autoFullyCovers}
                    onClick={() => setStep('confirm')}
                  >
                    Looks Good <ArrowRight size={15} />
                  </button>
                </div>
              </motion.div>
            )}

            {/* ── STEP 3: Final Confirm ── */}
            {step === 'confirm' && (
              <motion.div key="confirm" initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }}>
                <div style={{
                  padding: '16px', borderRadius: '12px',
                  background: 'rgba(16,185,129,0.08)', border: '1px solid rgba(16,185,129,0.25)',
                  marginBottom: '18px'
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
                    <CheckCircle2 size={18} color="#10b981" />
                    <span style={{ fontWeight: 700, fontSize: '14px', color: 'var(--color-text-primary)' }}>
                      Confirm Payment Action
                    </span>
                  </div>
                  <div style={{ fontSize: '13px', color: 'var(--color-text-secondary)', lineHeight: 1.6 }}>
                    <div>📄 <strong>Source Bill:</strong> {preview.sourceBillNumber} — ₹{fmt(preview.sourceBillPending)} cleared → <span style={{ color: '#10b981', fontWeight: 700 }}>PAID</span></div>
                    {selectedType === 'MANUAL_ADJUST' && (() => {
                      const target = preview.otherPendingBills.find(b => b.billId === selectedTargetBillId)
                      const applied = Math.min(Number(preview.excessAmount), Number(target?.pendingAmount || 0))
                      return <div style={{ marginTop: '6px' }}>🔁 <strong>Excess ₹{fmt(preview.excessAmount)}</strong> → manually applied to <strong>{target?.billNumber}</strong> (₹{fmt(applied)})</div>
                    })()}
                    {selectedType === 'AUTO_ADJUST' && (
                      <div style={{ marginTop: '6px' }}>⚡ <strong>Excess ₹{fmt(preview.excessAmount)}</strong> → auto-distributed across {preview.autoDistribution?.length} bill(s) oldest-first</div>
                    )}
                  </div>
                </div>

                <p style={{ fontSize: '12px', color: 'var(--color-text-muted)', marginBottom: '18px' }}>
                  ⚠️ This action cannot be undone without admin-level payment deletion. Please confirm carefully.
                </p>

                <div style={{ display: 'flex', justifyContent: 'space-between', gap: '10px' }}>
                  <button className="btn btn-secondary" onClick={handleBack}>
                    <ChevronLeft size={15} /> Back
                  </button>
                  <motion.button
                    className="btn btn-success"
                    whileTap={{ scale: 0.95 }}
                    onClick={handleConfirm}
                  >
                    ✓ Confirm & Save Payment
                  </motion.button>
                </div>
              </motion.div>
            )}

          </AnimatePresence>
        </div>
      </motion.div>
    </div>
  )
}

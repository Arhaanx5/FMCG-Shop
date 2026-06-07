export const getCustomerLedger = (customer, allBills, allPayments) => {
  const custPayments = allPayments.filter(p => p.customerId === customer.id)
  const custBills = allBills.filter(b => b.customerId === customer.id)
  const openingBalance = Number(customer.openingBalance || 0)

  const ledger = []

  // 1. Opening Balance
  if (openingBalance > 0) {
    ledger.push({
      type: 'OPENING',
      description: 'Opening Balance',
      debit: openingBalance,
      credit: 0,
      date: new Date(customer.createdAt || 0).getTime() - 86400000,
      createdAt: customer.createdAt || null
    })
  }

  // 2. Bills (Udhar/Partial only — use grandTotal as immutable debit amount)
  custBills.forEach(b => {
    if (b.status === 'CANCELLED' || (b.paymentMode !== 'UDHAR' && b.paymentMode !== 'PARTIAL')) return
    const billAmount = Number(b.grandTotal || 0)
    if (billAmount <= 0) return
    ledger.push({
      type: 'BILL',
      id: b.id,
      billNumber: b.billNumber,
      description: b.paymentMode === 'PARTIAL' ? `Partial Bill #${b.billNumber}` : `Credit Bill #${b.billNumber}`,
      debit: billAmount,
      credit: 0,
      date: new Date(b.createdAt).getTime(),
      createdAt: b.createdAt,
      bill: b
    })

    // Down payment for PARTIAL bills
    if (b.paymentMode === 'PARTIAL') {
      const linkedPayments = custPayments.filter(p => p.billId === b.id)
      const sumLinked = linkedPayments.reduce((sum, p) => sum + Number(p.appliedAmount || p.amount || 0), 0)
      const downPayment = Number(b.paidAmount || 0) - sumLinked
      if (downPayment > 0) {
        ledger.push({
          type: 'PAYMENT',
          id: `downpayment-${b.id}`,
          description: `Down Payment at Billing — Bill ${b.billNumber}`,
          adjustmentType: 'NORMAL',
          debit: 0,
          credit: downPayment,
          date: new Date(b.createdAt).getTime(),
          createdAt: b.createdAt
        })
      }
    }
  })

  // 3. Payments
  custPayments.forEach(p => {
    const baseDesc = `Payment Received (${p.paymentMode})`
    const billLink = p.billNumber ? ` — Bill ${p.billNumber}` : ''
    const notePart = p.notes ? ` · ${p.notes}` : ''
    const adjustNote = p.adjustmentNote ? ` 🔁 ${p.adjustmentNote}` : ''
    const creditAmt = Number(p.appliedAmount || p.amount || 0)
    ledger.push({
      type: 'PAYMENT',
      id: p.id,
      description: baseDesc + billLink + notePart + adjustNote,
      adjustmentType: p.adjustmentType || 'NORMAL',
      debit: 0,
      credit: creditAmt,
      date: new Date(p.paidAt).getTime(),
      createdAt: p.paidAt,
      payment: p
    })
  })

  // Sort chronological
  ledger.sort((a, b) => {
    if (a.date !== b.date) return a.date - b.date
    const typeWeight = { OPENING: 1, BILL: 2, PAYMENT: 3 }
    return typeWeight[a.type] - typeWeight[b.type]
  })

  // Calculate running balance
  let running = 0
  return ledger.map(entry => {
    running += (entry.debit - entry.credit)
    return {
      ...entry,
      runningBalance: running
    }
  })
}

export const generateLedgerHtml = (customer, ledger, totalUdhar, totalPaid, outstanding) => {
  const rows = ledger.map(row => {
    const dateStr = row.createdAt ? new Date(row.createdAt).toLocaleString('en-IN', {
      day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: true
    }) : '—'
    const dr = row.debit > 0 ? `₹${Number(row.debit).toLocaleString('en-IN')}` : '—'
    const cr = row.credit > 0 ? `₹${Number(row.credit).toLocaleString('en-IN')}` : '—'
    const bal = `₹${Number(row.runningBalance).toLocaleString('en-IN')}`
    return `
      <tr style="border-bottom: 1px solid #e2e8f0;">
        <td style="padding: 8px 10px; border: 1px solid #e2e8f0; white-space: nowrap;">${dateStr}</td>
        <td style="padding: 8px 10px; border: 1px solid #e2e8f0;">${row.description}</td>
        <td style="padding: 8px 10px; border: 1px solid #e2e8f0; text-align: right;">${dr}</td>
        <td style="padding: 8px 10px; border: 1px solid #e2e8f0; text-align: right; color: #16a34a;">${cr}</td>
        <td style="padding: 8px 10px; border: 1px solid #e2e8f0; text-align: right; font-weight: bold; color: ${row.runningBalance > 0 ? '#ef4444' : '#16a34a'};">${bal}</td>
      </tr>
    `
  }).join('')

  return `
    <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; padding: 25px; color: #1e293b; max-width: 800px; margin: 0 auto; background: #fff;">
      <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 3px solid #6366f1; padding-bottom: 15px; margin-bottom: 20px;">
        <div>
          <h1 style="margin: 0; color: #4f46e5; font-size: 26px; font-weight: 800; letter-spacing: -0.5px;">LARI TRADERS</h1>
          <p style="margin: 4px 0 0 0; font-size: 13px; color: #64748b; font-weight: 500;">Account Ledger Statement</p>
        </div>
        <div style="text-align: right;">
          <p style="margin: 0; font-size: 12px; color: #64748b;">Statement Generated On</p>
          <p style="margin: 2px 0 0 0; font-size: 14px; font-weight: bold; color: #1e293b;">${new Date().toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })}</p>
        </div>
      </div>

      <div style="display: grid; grid-template-columns: 1.2fr 1fr; gap: 30px; margin-bottom: 25px; font-size: 13px; line-height: 1.5;">
        <div style="background: #f8fafc; padding: 15px; border-radius: 8px; border: 1px solid #e2e8f0;">
          <h4 style="margin: 0 0 8px 0; color: #475569; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px;">Customer Info</h4>
          <strong style="font-size: 15px; color: #0f172a;">${customer.name}</strong><br/>
          ${customer.shopName ? `<span style="font-weight: 600; color: #334155;">Shop: ${customer.shopName}</span><br/>` : ''}
          <span style="color: #64748b;">Phone: ${customer.phone || '—'}</span>
        </div>
        <div style="background: #f8fafc; padding: 15px; border-radius: 8px; border: 1px solid #e2e8f0; display: flex; flex-direction: column; justify-content: space-between;">
          <div>
            <h4 style="margin: 0 0 8px 0; color: #475569; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px;">Balance Summary</h4>
            <table style="width: 100%; font-size: 12px; border-collapse: collapse;">
              <tr>
                <td style="padding: 2px 0; color: #64748b;">Total Credit Taken (Udhar)</td>
                <td style="padding: 2px 0; text-align: right; font-weight: 600;">₹${totalUdhar.toLocaleString('en-IN')}</td>
              </tr>
              <tr>
                <td style="padding: 2px 0; color: #64748b;">Total Amount Paid (Bhugtan)</td>
                <td style="padding: 2px 0; text-align: right; font-weight: 600; color: #16a34a;">- ₹${totalPaid.toLocaleString('en-IN')}</td>
              </tr>
              <tr style="border-top: 1px solid #cbd5e1;">
                <td style="padding: 6px 0 2px 0; font-weight: bold; color: #0f172a;">Remaining Outstanding</td>
                <td style="padding: 6px 0 2px 0; text-align: right; font-weight: 800; font-size: 14px; color: #ef4444;">₹${outstanding.toLocaleString('en-IN')}</td>
              </tr>
            </table>
          </div>
        </div>
      </div>

      <table style="width: 100%; border-collapse: collapse; font-size: 11px; text-align: left;">
        <thead>
          <tr style="background: #6366f1; color: #ffffff;">
            <th style="padding: 10px 12px; border: 1px solid #6366f1; font-weight: 600; border-top-left-radius: 6px; border-bottom-left-radius: 6px;">Date & Time</th>
            <th style="padding: 10px 12px; border: 1px solid #6366f1; font-weight: 600;">Transaction Details</th>
            <th style="padding: 10px 12px; border: 1px solid #6366f1; font-weight: 600; text-align: right;">Udhar Taken (+)</th>
            <th style="padding: 10px 12px; border: 1px solid #6366f1; font-weight: 600; text-align: right;">Amount Paid (-)</th>
            <th style="padding: 10px 12px; border: 1px solid #6366f1; font-weight: 600; text-align: right; border-top-right-radius: 6px; border-bottom-right-radius: 6px;">Running O/S Balance</th>
          </tr>
        </thead>
        <tbody style="background: #ffffff;">
          ${rows}
        </tbody>
      </table>

      <div style="margin-top: 35px; text-align: center; border-top: 1px dashed #e2e8f0; padding-top: 15px;">
        <p style="margin: 0; font-size: 11px; color: #94a3b8; font-style: italic;">Thank you for doing business with Lari Traders. Please clear your outstanding balance as soon as possible.</p>
      </div>
    </div>
  `
}

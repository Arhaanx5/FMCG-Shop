const m=(i,p,g)=>{const a=g.filter(t=>t.customerId===i.id),c=p.filter(t=>t.customerId===i.id),r=Number(i.openingBalance||0),e=[];r>0&&e.push({type:"OPENING",description:"Opening Balance",debit:r,credit:0,date:new Date(i.createdAt||0).getTime()-864e5,createdAt:i.createdAt||null}),c.forEach(t=>{if(t.status==="CANCELLED"||t.paymentMode!=="UDHAR"&&t.paymentMode!=="PARTIAL")return;const n=Number(t.grandTotal||0);if(!(n<=0)&&(e.push({type:"BILL",id:t.id,billNumber:t.billNumber,description:t.paymentMode==="PARTIAL"?`Partial Bill #${t.billNumber}`:`Credit Bill #${t.billNumber}`,debit:n,credit:0,date:new Date(t.createdAt).getTime(),createdAt:t.createdAt,bill:t}),t.paymentMode==="PARTIAL")){const x=a.filter(o=>o.billId===t.id).reduce((o,f)=>o+Number(f.appliedAmount||f.amount||0),0),s=Number(t.paidAmount||0)-x;s>0&&e.push({type:"PAYMENT",id:`downpayment-${t.id}`,description:`Down Payment at Billing — Bill ${t.billNumber}`,adjustmentType:"NORMAL",debit:0,credit:s,date:new Date(t.createdAt).getTime(),createdAt:t.createdAt})}}),a.forEach(t=>{const n=`Payment Received (${t.paymentMode})`,d=t.billNumber?` — Bill ${t.billNumber}`:"",x=t.notes?` · ${t.notes}`:"",s=t.adjustmentNote?` 🔁 ${t.adjustmentNote}`:"",o=Number(t.appliedAmount||t.amount||0);e.push({type:"PAYMENT",id:t.id,description:n+d+x+s,adjustmentType:t.adjustmentType||"NORMAL",debit:0,credit:o,date:new Date(t.paidAt).getTime(),createdAt:t.paidAt,payment:t})}),e.sort((t,n)=>{if(t.date!==n.date)return t.date-n.date;const d={OPENING:1,BILL:2,PAYMENT:3};return d[t.type]-d[n.type]});let l=0;return e.map(t=>(l+=t.debit-t.credit,{...t,runningBalance:l}))},u=(i,p,g,a,c)=>{const r=p.map(e=>{const l=e.createdAt?new Date(e.createdAt).toLocaleString("en-IN",{day:"2-digit",month:"2-digit",year:"numeric",hour:"2-digit",minute:"2-digit",hour12:!0}):"—",t=e.debit>0?`₹${Number(e.debit).toLocaleString("en-IN")}`:"—",n=e.credit>0?`₹${Number(e.credit).toLocaleString("en-IN")}`:"—",d=`₹${Number(e.runningBalance).toLocaleString("en-IN")}`;return`
      <tr style="border-bottom: 1px solid #e2e8f0;">
        <td style="padding: 8px 10px; border: 1px solid #e2e8f0; white-space: nowrap;">${l}</td>
        <td style="padding: 8px 10px; border: 1px solid #e2e8f0;">${e.description}</td>
        <td style="padding: 8px 10px; border: 1px solid #e2e8f0; text-align: right;">${t}</td>
        <td style="padding: 8px 10px; border: 1px solid #e2e8f0; text-align: right; color: #16a34a;">${n}</td>
        <td style="padding: 8px 10px; border: 1px solid #e2e8f0; text-align: right; font-weight: bold; color: ${e.runningBalance>0?"#ef4444":"#16a34a"};">${d}</td>
      </tr>
    `}).join("");return`
    <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; padding: 25px; color: #1e293b; max-width: 800px; margin: 0 auto; background: #fff;">
      <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 3px solid #6366f1; padding-bottom: 15px; margin-bottom: 20px;">
        <div>
          <h1 style="margin: 0; color: #4f46e5; font-size: 26px; font-weight: 800; letter-spacing: -0.5px;">LARI TRADERS</h1>
          <p style="margin: 4px 0 0 0; font-size: 13px; color: #64748b; font-weight: 500;">Account Ledger Statement</p>
        </div>
        <div style="text-align: right;">
          <p style="margin: 0; font-size: 12px; color: #64748b;">Statement Generated On</p>
          <p style="margin: 2px 0 0 0; font-size: 14px; font-weight: bold; color: #1e293b;">${new Date().toLocaleDateString("en-IN",{day:"numeric",month:"short",year:"numeric"})}</p>
        </div>
      </div>

      <div style="display: grid; grid-template-columns: 1.2fr 1fr; gap: 30px; margin-bottom: 25px; font-size: 13px; line-height: 1.5;">
        <div style="background: #f8fafc; padding: 15px; border-radius: 8px; border: 1px solid #e2e8f0;">
          <h4 style="margin: 0 0 8px 0; color: #475569; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px;">Customer Info</h4>
          <strong style="font-size: 15px; color: #0f172a;">${i.name}</strong><br/>
          ${i.shopName?`<span style="font-weight: 600; color: #334155;">Shop: ${i.shopName}</span><br/>`:""}
          <span style="color: #64748b;">Phone: ${i.phone||"—"}</span>
        </div>
        <div style="background: #f8fafc; padding: 15px; border-radius: 8px; border: 1px solid #e2e8f0; display: flex; flex-direction: column; justify-content: space-between;">
          <div>
            <h4 style="margin: 0 0 8px 0; color: #475569; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px;">Balance Summary</h4>
            <table style="width: 100%; font-size: 12px; border-collapse: collapse;">
              <tr>
                <td style="padding: 2px 0; color: #64748b;">Total Credit Taken (Udhar)</td>
                <td style="padding: 2px 0; text-align: right; font-weight: 600;">₹${g.toLocaleString("en-IN")}</td>
              </tr>
              <tr>
                <td style="padding: 2px 0; color: #64748b;">Total Amount Paid (Bhugtan)</td>
                <td style="padding: 2px 0; text-align: right; font-weight: 600; color: #16a34a;">- ₹${a.toLocaleString("en-IN")}</td>
              </tr>
              <tr style="border-top: 1px solid #cbd5e1;">
                <td style="padding: 6px 0 2px 0; font-weight: bold; color: #0f172a;">Remaining Outstanding</td>
                <td style="padding: 6px 0 2px 0; text-align: right; font-weight: 800; font-size: 14px; color: #ef4444;">₹${c.toLocaleString("en-IN")}</td>
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
          ${r}
        </tbody>
      </table>

      <div style="margin-top: 35px; text-align: center; border-top: 1px dashed #e2e8f0; padding-top: 15px;">
        <p style="margin: 0; font-size: 11px; color: #94a3b8; font-style: italic;">Thank you for doing business with Lari Traders. Please clear your outstanding balance as soon as possible.</p>
      </div>
    </div>
  `};export{m as a,u as g};

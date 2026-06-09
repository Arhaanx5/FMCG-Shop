const $=(d,f,u)=>{const g=u.filter(t=>t.customerId===d.id),l=f.filter(t=>t.customerId===d.id),s=Number(d.openingBalance||0),e=[];s>0&&e.push({type:"OPENING",description:"Opening Balance",debit:s,credit:0,date:new Date(d.createdAt||0).getTime()-864e5,createdAt:d.createdAt||null}),l.forEach(t=>{if(t.status==="CANCELLED"||t.paymentMode!=="UDHAR"&&t.paymentMode!=="PARTIAL")return;const a=Number(t.grandTotal||0);if(!(a<=0)&&(e.push({type:"BILL",id:t.id,billNumber:t.billNumber,description:t.paymentMode==="PARTIAL"?`Partial Bill #${t.billNumber}`:`Credit Bill #${t.billNumber}`,debit:a,credit:0,date:new Date(t.createdAt).getTime(),createdAt:t.createdAt,bill:t}),t.paymentMode==="PARTIAL")){const r=g.filter(o=>o.billId===t.id).reduce((o,c)=>o+Number(c.appliedAmount||c.amount||0),0),p=Number(t.paidAmount||0)-r;p>0&&e.push({type:"PAYMENT",id:`downpayment-${t.id}`,description:`Down Payment at Billing — Bill ${t.billNumber}`,adjustmentType:"NORMAL",debit:0,credit:p,date:new Date(t.createdAt).getTime(),createdAt:t.createdAt})}}),g.forEach(t=>{const a=`Payment Received (${t.paymentMode})`,i=t.billNumber?` — Bill ${t.billNumber}`:"",r=t.notes?` · ${t.notes}`:"",p=t.adjustmentNote?` 🔁 ${t.adjustmentNote}`:"",o=Number(t.appliedAmount||t.amount||0);e.push({type:"PAYMENT",id:t.id,description:a+i+r+p,adjustmentType:t.adjustmentType||"NORMAL",debit:0,credit:o,date:new Date(t.paidAt).getTime(),createdAt:t.paidAt,payment:t})}),e.sort((t,a)=>{if(t.date!==a.date)return t.date-a.date;const i={OPENING:1,BILL:2,PAYMENT:3};return i[t.type]-i[a.type]});let x=0;return e.map(t=>(x+=t.debit-t.credit,{...t,runningBalance:x}))},v=(d,f,u,g,l,s=0,e="ALL")=>{const x=f.map(i=>{const r=i.createdAt?new Date(i.createdAt).toLocaleString("en-IN",{day:"2-digit",month:"2-digit",year:"numeric",hour:"2-digit",minute:"2-digit",hour12:!0}):"—",p=i.debit>0?`₹${Number(i.debit).toLocaleString("en-IN")}`:"—",o=i.credit>0?`₹${Number(i.credit).toLocaleString("en-IN")}`:"—",c=`₹${Number(i.runningBalance).toLocaleString("en-IN")}`;return`
      <tr style="border-bottom: 1px solid #e2e8f0; page-break-inside: avoid; break-inside: avoid;">
        <td style="padding: 8px 10px; border: 1px solid #e2e8f0; white-space: nowrap;">${r}</td>
        <td style="padding: 8px 10px; border: 1px solid #e2e8f0;">${i.description}</td>
        <td style="padding: 8px 10px; border: 1px solid #e2e8f0; text-align: right;">${p}</td>
        <td style="padding: 8px 10px; border: 1px solid #e2e8f0; text-align: right; color: #16a34a;">${o}</td>
        <td style="padding: 8px 10px; border: 1px solid #e2e8f0; text-align: right; font-weight: bold; color: ${i.runningBalance>0?"#ef4444":"#16a34a"};">${c}</td>
      </tr>
    `}).join(""),t=e&&e!=="ALL"?`
    <tr style="border-bottom: 1px solid #e2e8f0; page-break-inside: avoid; break-inside: avoid; font-weight: bold; background: #fafafa;">
      <td style="padding: 8px 10px; border: 1px solid #e2e8f0; white-space: nowrap;">—</td>
      <td style="padding: 8px 10px; border: 1px solid #e2e8f0;">Opening Balance (Pichla Outstanding)</td>
      <td style="padding: 8px 10px; border: 1px solid #e2e8f0; text-align: right;">—</td>
      <td style="padding: 8px 10px; border: 1px solid #e2e8f0; text-align: right;">—</td>
      <td style="padding: 8px 10px; border: 1px solid #e2e8f0; text-align: right; color: ${s>0?"#ef4444":"#16a34a"};">₹${Number(s).toLocaleString("en-IN")}</td>
    </tr>
  `:"";let a="Account Ledger Statement";if(e&&e!=="ALL"){const[i,r]=e.split("-").map(Number);a=`Statement for ${new Date(i,r-1,1).toLocaleString("en-IN",{month:"long",year:"numeric"})}`}return`
    <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; padding: 0; color: #1e293b; max-width: 800px; margin: 0 auto; background: #fff;">
      <style>
        body {
          margin: 0 !important;
          padding: 0 !important;
        }
        tr, td, th {
          page-break-inside: avoid !important;
          break-inside: avoid !important;
        }
        thead {
          display: table-header-group !important;
        }
      </style>
      <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 3px solid #6366f1; padding-bottom: 15px; margin-bottom: 20px;">
        <div>
          <h1 style="margin: 0; color: #4f46e5; font-size: 26px; font-weight: 800; letter-spacing: -0.5px;">LARI TRADERS</h1>
          <p style="margin: 4px 0 0 0; font-size: 13px; color: #64748b; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px;">${a}</p>
        </div>
        <div style="text-align: right;">
          <p style="margin: 0; font-size: 12px; color: #64748b;">Statement Generated On</p>
          <p style="margin: 2px 0 0 0; font-size: 14px; font-weight: bold; color: #1e293b;">${new Date().toLocaleDateString("en-IN",{day:"numeric",month:"short",year:"numeric"})}</p>
        </div>
      </div>

      <div style="display: grid; grid-template-columns: 1.2fr 1fr; gap: 30px; margin-bottom: 25px; font-size: 13px; line-height: 1.5; page-break-inside: avoid; break-inside: avoid;">
        <div style="background: #f8fafc; padding: 15px; border-radius: 8px; border: 1px solid #e2e8f0;">
          <h4 style="margin: 0 0 8px 0; color: #475569; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px;">Customer Info</h4>
          <strong style="font-size: 15px; color: #0f172a;">${d.name}</strong><br/>
          ${d.shopName?`<span style="font-weight: 600; color: #334155;">Shop: ${d.shopName}</span><br/>`:""}
          <span style="color: #64748b;">Phone: ${d.phone||"—"}</span>
        </div>
        <div style="background: #f8fafc; padding: 15px; border-radius: 8px; border: 1px solid #e2e8f0; display: flex; flex-direction: column; justify-content: space-between;">
          <div>
            <h4 style="margin: 0 0 8px 0; color: #475569; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px;">Balance Summary</h4>
            <table style="width: 100%; font-size: 12px; border-collapse: collapse;">
              ${e&&e!=="ALL"?`
              <tr>
                <td style="padding: 2px 0; color: #64748b;">Opening Balance (Pichla Outstanding)</td>
                <td style="padding: 2px 0; text-align: right; font-weight: 600;">₹${Number(s).toLocaleString("en-IN")}</td>
              </tr>
              `:""}
              <tr>
                <td style="padding: 2px 0; color: #64748b;">${e&&e!=="ALL"?"New Credit Taken (Naya Udhar)":"Total Credit Taken (Udhar)"}</td>
                <td style="padding: 2px 0; text-align: right; font-weight: 600;">₹${u.toLocaleString("en-IN")}</td>
              </tr>
              <tr>
                <td style="padding: 2px 0; color: #64748b;">${e&&e!=="ALL"?"New Amount Paid (Naya Bhugtan)":"Total Amount Paid (Bhugtan)"}</td>
                <td style="padding: 2px 0; text-align: right; font-weight: 600; color: #16a34a;">- ₹${g.toLocaleString("en-IN")}</td>
              </tr>
              <tr style="border-top: 1px solid #cbd5e1;">
                <td style="padding: 6px 0 2px 0; font-weight: bold; color: #0f172a;">${e&&e!=="ALL"?"Total Outstanding Balance (Kul Baki)":"Remaining Outstanding"}</td>
                <td style="padding: 6px 0 2px 0; text-align: right; font-weight: 800; font-size: 14px; color: #ef4444;">₹${l.toLocaleString("en-IN")}</td>
              </tr>
            </table>
          </div>
        </div>
      </div>

      <table style="width: 100%; border-collapse: collapse; font-size: 11px; text-align: left; table-layout: fixed;">
        <thead>
          <tr style="background: #6366f1; color: #ffffff;">
            <th style="padding: 10px 12px; border: 1px solid #6366f1; font-weight: 600; border-top-left-radius: 6px; border-bottom-left-radius: 6px; width: 22%;">Date & Time</th>
            <th style="padding: 10px 12px; border: 1px solid #6366f1; font-weight: 600; width: 38%;">Transaction Details</th>
            <th style="padding: 10px 12px; border: 1px solid #6366f1; font-weight: 600; text-align: right; width: 13%;">Udhar Taken (+)</th>
            <th style="padding: 10px 12px; border: 1px solid #6366f1; font-weight: 600; text-align: right; width: 13%;">Amount Paid (-)</th>
            <th style="padding: 10px 12px; border: 1px solid #6366f1; font-weight: 600; text-align: right; border-top-right-radius: 6px; border-bottom-right-radius: 6px; width: 14%;">Running O/S Balance</th>
          </tr>
        </thead>
        <tbody style="background: #ffffff;">
          ${t}
          ${x}
        </tbody>
      </table>

      <div style="margin-top: 35px; text-align: center; border-top: 1px dashed #e2e8f0; padding-top: 15px; page-break-inside: avoid; break-inside: avoid;">
        <p style="margin: 0; font-size: 11px; color: #94a3b8; font-style: italic;">Thank you for doing business with Lari Traders. Please clear your outstanding balance as soon as possible.</p>
      </div>
    </div>
  `},T=(d,f,u,g)=>{const l=$(d,f,u);if(!g||g==="ALL"){const n=l.reduce((y,h)=>y+h.debit,0),m=l.reduce((y,h)=>y+h.credit,0),w=n-m;return{ledger:l,openingBalance:0,totalUdhar:n,totalPaid:m,outstanding:w}}const[s,e]=g.split("-").map(Number),x=new Date(s,e-1,1,0,0,0,0),t=new Date(s,e,0,23,59,59,999),a=x.getTime(),i=t.getTime(),r=l.filter(n=>n.date<a),p=l.filter(n=>n.date>=a&&n.date<=i),o=r.length>0?r[r.length-1].runningBalance:0;let c=o;const b=p.map(n=>(c+=n.debit-n.credit,{...n,runningBalance:c})),N=b.reduce((n,m)=>n+m.debit,0),L=b.reduce((n,m)=>n+m.credit,0),A=o+N-L;return{ledger:b,openingBalance:o,totalUdhar:N,totalPaid:L,outstanding:A}};export{T as a,v as g};

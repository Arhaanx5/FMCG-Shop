import{av as x,aA as $,au as a}from"./vendor-DPviOFIk.js";import{m as D}from"./motion-BO6lhW00.js";function T({icon:w,label:j,value:l,prefix:S="",suffix:z="",color:t="var(--color-accent)",delay:C=0,description:u}){const[m,N]=x.useState(0),v=typeof l=="number"?l:parseFloat(l)||0;x.useRef(!1);let n="modern";try{const s=$();s&&s[0]&&(n=s[0])}catch{}x.useEffect(()=>{const d=Date.now(),r=m,f=v-r,o=setInterval(()=>{const p=Date.now()-d,y=Math.min(p/1e3,1),V=1-Math.pow(1-y,3);N(Math.round(r+f*V)),y>=1&&clearInterval(o)},16);return()=>clearInterval(o)},[v]);const k=`rgba(${t==="var(--color-accent)"?"245, 158, 11":t==="var(--color-success)"?"16, 185, 129":t==="var(--color-danger)"?"239, 68, 68":t==="var(--color-info)"?"59, 130, 246":"245, 158, 11"}, 0.12)`,B=n==="modern",b=n==="cyber",g=n==="neon",I=B||b||g;let e="stat-card-icon",h={background:k,color:t};I&&(h={},b?t==="var(--color-accent)"?e+=" stat-card-icon-cyber":t==="var(--color-info)"?e+=" stat-card-icon-info":t==="var(--color-success)"?e+=" stat-card-icon-success":t==="var(--color-danger)"?e+=" stat-card-icon-danger":e+=" stat-card-icon-cyber":g?t==="var(--color-accent)"?e+=" stat-card-icon-neon":t==="var(--color-info)"?e+=" stat-card-icon-info":t==="var(--color-success)"?e+=" stat-card-icon-success":t==="var(--color-danger)"?e+=" stat-card-icon-danger":e+=" stat-card-icon-neon":t==="var(--color-accent)"?e+=" stat-card-icon-accent":t==="var(--color-info)"?e+=" stat-card-icon-info":t==="var(--color-success)"?e+=" stat-card-icon-success":t==="var(--color-danger)"?e+=" stat-card-icon-danger":e+=" stat-card-icon-accent");const i=`${S}${m.toLocaleString("en-IN")}${z}`,c={};return i.length>=11?c.fontSize="var(--font-size-sm)":i.length===10?c.fontSize="var(--font-size-base)":i.length===9?c.fontSize="1.05rem":i.length===8&&(c.fontSize="1.15rem"),a.jsxs(D.div,{className:`stat-card ${n!=="classic"?"card-lift":""}`,initial:{opacity:0,y:20},animate:{opacity:1,y:0},transition:{duration:.4,delay:C*.1},style:{position:"relative"},children:[a.jsx("style",{children:`
        .stat-card-desc-tooltip {
          position: absolute;
          bottom: 105%;
          left: 50%;
          transform: translateX(-50%) translateY(10px);
          background: rgba(15, 23, 42, 0.95);
          backdrop-filter: blur(8px);
          border: 1px solid rgba(255, 255, 255, 0.1);
          border-radius: var(--radius-md);
          padding: 8px 12px;
          width: max-content;
          max-width: 280px;
          opacity: 0;
          visibility: hidden;
          transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
          z-index: 100;
          pointer-events: none;
          box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.3), 0 8px 10px -6px rgba(0, 0, 0, 0.3);
          color: #fff;
        }
        .stat-card:hover .stat-card-desc-tooltip {
          opacity: 1;
          visibility: visible;
          transform: translateX(-50%) translateY(0);
        }
        .stat-card-tooltip-arrow {
          position: absolute;
          top: 100%;
          left: 50%;
          transform: translateX(-50%);
          border-width: 6px;
          border-style: solid;
          border-color: rgba(15, 23, 42, 0.95) transparent transparent transparent;
        }
      `}),a.jsx("div",{className:e,style:h,children:w}),a.jsxs("div",{className:"stat-card-content",children:[a.jsx("div",{className:"stat-card-value",style:c,children:i}),a.jsx("div",{className:"stat-card-label",children:j})]}),u&&a.jsxs("div",{className:"stat-card-desc-tooltip",children:[a.jsx("div",{style:{fontWeight:"600",fontSize:"11px",color:"rgba(255, 255, 255, 0.7)",marginBottom:"8px",borderBottom:"1px solid rgba(255, 255, 255, 0.1)",paddingBottom:"4px"},children:"Collection Breakdown"}),a.jsx("div",{style:{display:"flex",flexDirection:"column",gap:"6px"},children:u.split("|").map((s,d)=>{const[r,f]=s.split(":").map(p=>p.trim());let o="var(--color-success)";return r.toLowerCase().includes("upi")&&(o="var(--color-info)"),(r.toLowerCase().includes("udhar")||r.toLowerCase().includes("recovery"))&&(o="var(--color-warning)"),a.jsxs("div",{style:{display:"flex",alignItems:"center",gap:"8px",fontSize:"11px",whiteSpace:"nowrap"},children:[a.jsx("span",{style:{width:"6px",height:"6px",borderRadius:"50%",background:o}}),a.jsxs("span",{style:{color:"rgba(255, 255, 255, 0.7)",minWidth:"90px"},children:[r,":"]}),a.jsx("span",{style:{fontWeight:"750",color:"#fff"},children:f})]},d)})}),a.jsx("div",{className:"stat-card-tooltip-arrow"})]})]})}export{T as S};

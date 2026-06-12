import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Plus, AlertTriangle, Clock, Package, Edit2, History, Trash2, Settings, PlayCircle, CheckCircle, XCircle, Camera, Upload } from 'lucide-react'
import api from '../services/api'
import DataTable from '../components/DataTable'
import Pagination from '../components/Pagination'
import Modal from '../components/Modal'
import SearchSelect from '../components/SearchSelect'
import { useToast } from '../context/ToastContext'
import { useAuth } from '../context/AuthContext'
import ConfirmDialog from '../components/ConfirmDialog'
const CATEGORIES = ['CHIPS', 'SNACKS', 'BEVERAGES', 'CIGARETTES', 'BISCUITS', 'NAMKEEN', 'OTHER']

const emptyForm = { 
  productId: '', 
  batchNumber: '', 
  primaryReceived: '', 
  extraSecondaryReceived: '', 
  buyPriceWithoutTax: '', 
  buyPriceWithTax: '', 
  expiryDate: '', 
  supplierName: '', 
  sellPricePrimary: '', 
  sellPriceSecondary: '', 
  logAsExpense: true 
}

export default function Stock() {
  const { isAdmin, isManager, aiEnabled } = useAuth()
  const [isMobile, setIsMobile] = useState(window.innerWidth < 768)

  useEffect(() => {
    const handleResize = () => setIsMobile(window.innerWidth < 768)
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])


  const [stocks, setStocks] = useState([])
  const [products, setProducts] = useState([])
  const [expiring, setExpiring] = useState([])
  const [loading, setLoading] = useState(true)
  const [activeTab, setActiveTab] = useState('overview')
  const [showModal, setShowModal] = useState(false)
  const [form, setForm] = useState({ ...emptyForm })
  const [saving, setSaving] = useState(false)
  // Batches for a selected product
  const [batchProduct, setBatchProduct] = useState(null)
  const [batches, setBatches] = useState([])
  const [batchLoading, setBatchLoading] = useState(false)
  const [writeOffTarget, setWriteOffTarget] = useState(null)

  // Adjust stock states
  const [adjustingBatch, setAdjustingBatch] = useState(null)
  const [adjustPrimary, setAdjustPrimary] = useState('')
  const [adjustBuyPrice, setAdjustBuyPrice] = useState('')
  const [adjustBuyPriceWithTax, setAdjustBuyPriceWithTax] = useState('')
  const [adjustReason, setAdjustReason] = useState('')
  const [adjusting, setAdjusting] = useState(false)
  const [validationErrors, setValidationErrors] = useState({})
  const [auditLogs, setAuditLogs] = useState([])
  const [logsLoading, setLogsLoading] = useState(false)

  // Stock overview pagination
  const [stockPage, setStockPage] = useState(0)
  const [stockTotalPages, setStockTotalPages] = useState(0)
  const [stockTotalElements, setStockTotalElements] = useState(0)
  const STOCK_PAGE_SIZE = 20

  // Audit log pagination
  const [auditPage, setAuditPage] = useState(0)
  const [auditTotalPages, setAuditTotalPages] = useState(0)
  const [auditTotalElements, setAuditTotalElements] = useState(0)
  const AUDIT_PAGE_SIZE = 15

  // Scheduler panel state
  const [schedulerStatus, setSchedulerStatus] = useState(null)
  const [schedulerLoading, setSchedulerLoading] = useState(false)
  const [runNowLoading, setRunNowLoading] = useState(false)
  const [showRunNowConfirm, setShowRunNowConfirm] = useState(false)

  // Scanner states
  const [scannerFile, setScannerFile] = useState(null)
  const [scannerLoading, setScannerLoading] = useState(false)
  const [scannerPreview, setScannerPreview] = useState([])
  const [scannerSupplier, setScannerSupplier] = useState('Saurabh Agency')
  
  // Quick Add Product states
  const [showQuickProductModal, setShowQuickProductModal] = useState(false)
  const [quickProductIndex, setQuickProductIndex] = useState(null)
  const [quickProductForm, setQuickProductForm] = useState({
    name: '', brand: '', category: 'SNACKS', gstPercent: '5',
    primaryUnit: 'BOX', secondaryUnit: 'LADI', customSecondaryUnit: '', secondaryPerPrimary: '20',
    buyPriceWithoutTax: '', sellPricePrimary: '', sellPriceSecondary: ''
  })

  useEffect(() => {
    if (!aiEnabled && activeTab === 'scanner') {
      setActiveTab('overview')
    }
  }, [aiEnabled, activeTab])

  const clearError = (field) => {
    setValidationErrors(prev => ({ ...prev, [field]: null }))
  }

  const toast = useToast()

  useEffect(() => { loadAll(0, true) }, [])

  const loadAuditLogs = async (pg = 0) => {
    setLogsLoading(true)
    try {
      const res = await api.get(`/stock/adjustments?page=${pg}&size=${AUDIT_PAGE_SIZE}`)
      const pageData = res.data.data
      setAuditLogs(pageData?.content || [])
      setAuditTotalPages(pageData?.totalPages || 0)
      setAuditTotalElements(pageData?.totalElements || 0)
    } catch {
      toast.error('Failed to load stock audit logs')
    } finally {
      setLogsLoading(false)
    }
  }

  const loadSchedulerStatus = async () => {
    setSchedulerLoading(true)
    try {
      const res = await api.get('/scheduler/expiry/status')
      setSchedulerStatus(res.data.data)
    } catch {
      toast.error('Failed to load scheduler status')
    } finally {
      setSchedulerLoading(false)
    }
  }

  const handleRunNow = async () => {
    setRunNowLoading(true)
    try {
      const res = await api.post('/scheduler/expiry/run-now')
      toast.success(res.data.message || 'Sweep completed!')
      loadSchedulerStatus()
      loadAll(0)
    } catch (err) {
      toast.error(err.response?.data?.message || 'Sweep failed')
    } finally {
      setRunNowLoading(false)
      setShowRunNowConfirm(false)
    }
  }

  useEffect(() => {
    if (activeTab === 'audit' && isAdmin) loadAuditLogs(0)
    if (activeTab === 'scheduler' && isAdmin) loadSchedulerStatus()
  }, [activeTab])

  const handleAdjustPriceChange = (type, value) => {
    if (!adjustingBatch) return
    const gst = Number(adjustingBatch.gstPercent || 0)
    if (type === 'without') {
      const parsed = parseFloat(value)
      if (isNaN(parsed) || !value) {
        setAdjustBuyPrice(value)
        setAdjustBuyPriceWithTax('')
      } else {
        const withTax = (parsed * (1 + gst / 100)).toFixed(2)
        setAdjustBuyPrice(value)
        setAdjustBuyPriceWithTax(withTax)
      }
    } else {
      const parsed = parseFloat(value)
      if (isNaN(parsed) || !value) {
        setAdjustBuyPriceWithTax(value)
        setAdjustBuyPrice('')
      } else {
        const withoutTax = (parsed / (1 + gst / 100)).toFixed(2)
        setAdjustBuyPriceWithTax(value)
        setAdjustBuyPrice(withoutTax)
      }
    }
  }

  const handleAdjustStockSubmit = async (e) => {
    e.preventDefault()
    if (adjustPrimary === null || adjustPrimary === undefined || adjustPrimary === '') {
      toast.error('Specify new remaining quantity in primary units')
      return
    }
    if (!adjustReason.trim()) { toast.error('Specify reason for correction'); return }
    setAdjusting(true)
    try {
      const ratio = adjustingBatch.secondaryPerPrimary || 1
      const computedSecondary = Number(adjustPrimary) * ratio
      await api.put(`/stock/batches/${adjustingBatch.id}/adjust`, {
        newSecondaryRemaining: computedSecondary,
        newBuyPriceWithoutTax: adjustBuyPrice ? Number(adjustBuyPrice) : null,
        reason: adjustReason
      })
      toast.success('Stock corrected successfully!')
      setAdjustingBatch(null)
      setAdjustPrimary('')
      setAdjustBuyPrice('')
      setAdjustBuyPriceWithTax('')
      setAdjustReason('')
      setValidationErrors({})
      if (batchProduct) {
        loadBatches(batchProduct.id, batchProduct.name)
      }
      loadAll()
    } catch (err) {
      if (err.response?.status === 400 && err.response?.data?.data && typeof err.response.data.data === 'object') {
        setValidationErrors(err.response.data.data)
        toast.error('Validation failed. Please correct the highlighted fields.')
      } else {
        toast.error(err.response?.data?.message || 'Failed to correct stock')
      }
    } finally {
      setAdjusting(false)
    }
  }

  const executeWriteOff = async (batchId) => {
    try {
      await api.post(`/stock/batches/${batchId}/write-off-expiry`)
      toast.success('Expired stock written off to Damage Log successfully!')
      setWriteOffTarget(null)
      loadAll()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to write off expired stock')
    }
  }

  const openQuickProductModal = (index) => {
    const item = scannerPreview[index]
    if (!item) return

    setQuickProductIndex(index)
    
    // Guess default values based on invoice item
    const guessCategory = item.productName.toLowerCase().includes('chip') ? 'CHIPS' : 'SNACKS'
    const guessSecUnit = item.packsPerCase === 72 || item.packsPerCase === 216 ? 'PACK' : 'LADI'
    const guessSecPerPri = item.packsPerCase === 72 || item.packsPerCase === 216 ? 72 : 20

    const knownSecondaryUnits = ['LADI', 'PACK', 'BOTTLE']
    const cleanItemSecUnit = item.secondaryUnit ? item.secondaryUnit.trim().toUpperCase() : ''
    const isKnown = knownSecondaryUnits.includes(cleanItemSecUnit)
    
    const initialSecUnit = isKnown ? cleanItemSecUnit : (cleanItemSecUnit ? 'OTHER' : guessSecUnit)
    const initialCustomSecUnit = (!isKnown && cleanItemSecUnit) ? cleanItemSecUnit : ''

    setQuickProductForm({
      name: item.productName || '',
      brand: "Haldiram's",
      category: guessCategory,
      gstPercent: item.gstPercent !== undefined ? item.gstPercent.toString() : '5',
      primaryUnit: (item.primaryUnit && ['BOX', 'CRATE'].includes(item.primaryUnit.toUpperCase())) ? item.primaryUnit.toUpperCase() : 'BOX',
      secondaryUnit: initialSecUnit,
      customSecondaryUnit: initialCustomSecUnit,
      secondaryPerPrimary: item.secondaryPerPrimary !== undefined ? item.secondaryPerPrimary.toString() : guessSecPerPri.toString(),
      buyPriceWithoutTax: item.buyPriceWithoutTax !== undefined ? item.buyPriceWithoutTax.toString() : '',
      sellPricePrimary: '',
      sellPriceSecondary: ''
    })
    setShowQuickProductModal(true)
  }

  const handleQuickProductSubmit = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      const payload = {
        name: quickProductForm.name,
        brand: quickProductForm.brand,
        category: quickProductForm.category,
        gstPercent: Number(quickProductForm.gstPercent),
        cessPercent: 0,
        primaryUnit: quickProductForm.primaryUnit,
        secondaryUnit: quickProductForm.secondaryUnit === 'OTHER' ? (quickProductForm.customSecondaryUnit || 'PACK') : quickProductForm.secondaryUnit,
        secondaryPerPrimary: Number(quickProductForm.secondaryPerPrimary),
        buyPriceWithoutTax: Number(quickProductForm.buyPriceWithoutTax),
        sellPricePrimary: Number(quickProductForm.sellPricePrimary),
        sellPriceSecondary: Number(quickProductForm.sellPriceSecondary),
        lowStockAlert: 10,
        lowStockUnit: 'SECONDARY'
      }

      const res = await api.post('/products', payload)
      const newProduct = res.data.data

      // Add to local product list
      setProducts(prev => [...prev, newProduct])

      // Directly map this scanner row with fresh newProduct — avoids stale `products` closure bug
      if (quickProductIndex !== null) {
        setScannerPreview(prev => {
          const updated = [...prev]
          const item = updated[quickProductIndex]
          if (!item) return prev
          const ratio = newProduct.secondaryPerPrimary || 1
          const secUnit = newProduct.secondaryUnit ? newProduct.secondaryUnit.toUpperCase() : 'PACK'
          let packPerSecondary = 1
          if (secUnit === 'LADI') {
            packPerSecondary = ratio > 0 ? Math.floor((item.packsPerCase || 1) / ratio) : 12
          }
          if (packPerSecondary <= 0) packPerSecondary = 1
          const totalPacks = (item.invoiceCases || 0) * (item.packsPerCase || 1)
          const totalSecondaryUnits = Math.floor(totalPacks / packPerSecondary)
          const primaryAdded = Math.floor(totalSecondaryUnits / ratio)
          const openBoxAdded = totalSecondaryUnits % ratio
          const buyPricePerPiece = Number(item.buyPricePerPiece || 0)
          const buyPriceWithoutTax = buyPricePerPiece > 0
            ? Number((buyPricePerPiece * ratio * packPerSecondary).toFixed(2))
            : Number(item.buyPriceWithoutTax || 0)
          updated[quickProductIndex] = {
            ...item,
            productId: newProduct.id,
            productName: newProduct.name,
            brand: newProduct.brand,
            category: newProduct.category,
            primaryUnit: newProduct.primaryUnit || 'BOX',
            secondaryUnit: newProduct.secondaryUnit || 'LADI',
            secondaryPerPrimary: ratio,
            primaryAdded,
            secondaryAdded: totalSecondaryUnits,
            openBoxAdded,
            buyPriceWithoutTax,
            newProduct: false,
            duplicateBatch: false
          }
          return updated
        })
      }

      toast.success('Product created and mapped successfully!')
      setShowQuickProductModal(false)
    } catch (err) {
      const errMsg = err.response?.data?.message || '';
      if (errMsg.toLowerCase().includes('already exists') || errMsg.toLowerCase().includes('already exist')) {
        try {
          const pRes = await api.get('/products?size=500')
          const latestProducts = pRes.data.data?.content || pRes.data.data || []
          setProducts(latestProducts)
          
          const matchedProduct = latestProducts.find(p => 
            p.name.trim().toLowerCase() === quickProductForm.name.trim().toLowerCase() && 
            (p.brand || '').trim().toLowerCase() === (quickProductForm.brand || '').trim().toLowerCase()
          )
          
          if (matchedProduct && quickProductIndex !== null) {
            setScannerPreview(prev => {
              const updated = [...prev]
              const item = updated[quickProductIndex]
              if (!item) return prev
              const ratio = matchedProduct.secondaryPerPrimary || 1
              const secUnit = matchedProduct.secondaryUnit ? matchedProduct.secondaryUnit.toUpperCase() : 'PACK'
              let packPerSecondary = 1
              if (secUnit === 'LADI') {
                packPerSecondary = ratio > 0 ? Math.floor((item.packsPerCase || 1) / ratio) : 12
              }
              if (packPerSecondary <= 0) packPerSecondary = 1
              const totalPacks = (item.invoiceCases || 0) * (item.packsPerCase || 1)
              const totalSecondaryUnits = Math.floor(totalPacks / packPerSecondary)
              const primaryAdded = Math.floor(totalSecondaryUnits / ratio)
              const openBoxAdded = totalSecondaryUnits % ratio
              const buyPricePerPiece = Number(item.buyPricePerPiece || 0)
              const buyPriceWithoutTax = buyPricePerPiece > 0
                ? Number((buyPricePerPiece * ratio * packPerSecondary).toFixed(2))
                : Number(item.buyPriceWithoutTax || 0)
              updated[quickProductIndex] = {
                ...item,
                productId: matchedProduct.id,
                productName: matchedProduct.name,
                brand: matchedProduct.brand,
                category: matchedProduct.category,
                primaryUnit: matchedProduct.primaryUnit || 'BOX',
                secondaryUnit: matchedProduct.secondaryUnit || 'LADI',
                secondaryPerPrimary: ratio,
                primaryAdded,
                secondaryAdded: totalSecondaryUnits,
                openBoxAdded,
                buyPriceWithoutTax,
                newProduct: false,
                duplicateBatch: false
              }
              return updated
            })
            toast.success('Product already exists! Row successfully mapped.')
            setShowQuickProductModal(false)
            return
          }
        } catch (fetchErr) {
          console.error("Failed to recover and map existing product", fetchErr)
        }
      }
      toast.error(err.response?.data?.message || 'Failed to quick create product')
    } finally {
      setSaving(false)
    }
  }

  const handleScanInvoice = async (e) => {
    e.preventDefault()
    if (!scannerFile) { toast.error('Koi file select karein'); return }
    setScannerLoading(true)
    setScannerPreview([])
    try {
      const formData = new FormData()
      formData.append('file', scannerFile)
      const res = await api.post('/stock/parse-invoice', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      setScannerPreview(res.data.data || [])
      toast.success('Invoice successfully scan aur map ho gaya!')
    } catch (err) {
      toast.error(err.response?.data?.message || 'Invoice scanning failed. Please try again.')
    } finally {
      setScannerLoading(false)
    }
  }

  const handleScannerRowProductChange = (index, newProductId) => {
    const updated = [...scannerPreview]
    const item = updated[index]
    const product = products.find(p => p.id === newProductId)
    
    if (product) {
      const ratio = product.secondaryPerPrimary || 1
      const secUnit = product.secondaryUnit ? product.secondaryUnit.toUpperCase() : 'PACK'
      let packPerSecondary = 1
      if (secUnit === 'LADI') {
        packPerSecondary = ratio > 0 ? Math.floor((item.packsPerCase || 1) / ratio) : 12
      }
      if (packPerSecondary <= 0) packPerSecondary = 1
      
      const totalPacks = item.invoiceCases * item.packsPerCase
      const totalSecondaryUnits = Math.floor(totalPacks / packPerSecondary)
      
      const primaryAdded = Math.floor(totalSecondaryUnits / ratio)
      const openBoxAdded = totalSecondaryUnits % ratio
      
      const buyPriceWithoutTax = Number(item.buyPricePerPiece) * ratio * packPerSecondary
      
      updated[index] = {
        ...item,
        productId: product.id,
        productName: product.name,
        brand: product.brand,
        category: product.category,
        primaryUnit: product.primaryUnit || 'BOX',
        secondaryUnit: product.secondaryUnit || 'LADI',
        secondaryPerPrimary: ratio,
        primaryAdded,
        secondaryAdded: totalSecondaryUnits,
        openBoxAdded,
        buyPriceWithoutTax: Number(buyPriceWithoutTax.toFixed(2)),
        newProduct: false
      }
    } else {
      updated[index] = {
        ...item,
        productId: null,
        newProduct: true
      }
    }
    setScannerPreview(updated)
  }

  const handleScannerSubmit = async (e) => {
    e.preventDefault()
    
    const unmapped = scannerPreview.filter(item => !item.productId)
    if (unmapped.length > 0) {
      toast.error('Kucch products mapped nahi hain. Pehle unhe sahi product se map karein.')
      return
    }
    
    setSaving(true)
    try {
      const payload = scannerPreview.map(item => {
        const product = products.find(p => p.id === item.productId)
        
        return {
          productId: item.productId,
          batchNumber: item.batchNumber,
          primaryReceived: item.primaryAdded,
          extraSecondaryReceived: item.openBoxAdded,
          buyPriceWithoutTax: item.buyPriceWithoutTax,
          expiryDate: item.expiryDate,
          supplierName: scannerSupplier,
          logAsExpense: true
        }
      })
      
      await api.post('/stock/receive-bulk', payload)
      toast.success('Sare items stock me successfully add ho gaye hain!')
      setScannerPreview([])
      setScannerFile(null)
      loadAll()
      setActiveTab('overview')
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to save stock batches')
    } finally {
      setSaving(false)
    }
  }

  const loadAll = async (pg = 0, showSpinner = false) => {
    if (showSpinner) setLoading(true)
    try {
      const [sRes, pRes, eRes] = await Promise.all([
        api.get(`/stock/paged?page=${pg}&size=${STOCK_PAGE_SIZE}`),
        api.get('/products?size=500'),
        api.get('/stock/expiring-soon'),
      ])
      const stockPage = sRes.data.data
      setStocks(stockPage?.content || [])
      setStockTotalPages(stockPage?.totalPages || 0)
      setStockTotalElements(stockPage?.totalElements || 0)
      setProducts(pRes.data.data?.content || pRes.data.data || [])
      setExpiring(eRes.data.data || [])
    } catch { toast.error('Failed to load stock data') }
    finally { if (showSpinner) setLoading(false) }
  }

  const loadBatches = async (productId, productName) => {
    setBatchProduct({ id: productId, name: productName })
    setBatchLoading(true)
    try {
      const res = await api.get(`/stock/batches/${productId}`)
      setBatches(res.data.data || [])
    } catch { toast.error('Failed to load batches') }
    finally { setBatchLoading(false) }
  }

  const handleReceive = async (e) => {
    e.preventDefault()
    if (!form.productId) { toast.error('Select a product'); return }
    setSaving(true)
    try {
      const payload = {
        ...form,
        primaryReceived: Number(form.primaryReceived || 0),
        extraSecondaryReceived: Number(form.extraSecondaryReceived || 0),
        buyPriceWithoutTax: Number(form.buyPriceWithoutTax || 0),
        buyPriceWithTax: form.buyPriceWithTax ? Number(form.buyPriceWithTax) : null,
        sellPricePrimary: form.sellPricePrimary ? Number(form.sellPricePrimary) : null,
        sellPriceSecondary: form.sellPriceSecondary ? Number(form.sellPriceSecondary) : null,
        logAsExpense: !!form.logAsExpense
      }
      await api.post('/stock/receive', payload)
      toast.success('Stock received successfully!')
      setShowModal(false)
      setForm({ ...emptyForm })
      setValidationErrors({})
      loadAll()
    } catch (err) {
      if (err.response?.status === 400 && err.response?.data?.data && typeof err.response.data.data === 'object') {
        setValidationErrors(err.response.data.data)
        toast.error('Validation failed. Please correct the highlighted fields.')
      } else {
        toast.error(err.response?.data?.message || 'Failed to receive stock')
      }
    } finally { setSaving(false) }
  }

  const updateField = (key, val) => {
    setForm(f => ({ ...f, [key]: val }))
    clearError(key)
  }

  const handlePriceChange = (type, value) => {
    const selectedProduct = products.find(p => p.id === form.productId)
    const gst = selectedProduct ? Number(selectedProduct.gstPercent || 0) : 0
    if (type === 'without') {
      const parsed = parseFloat(value)
      if (isNaN(parsed) || !value) {
        setForm(f => ({ ...f, buyPriceWithoutTax: value, buyPriceWithTax: '' }))
      } else {
        const withTax = (parsed * (1 + gst / 100)).toFixed(2)
        setForm(f => ({ ...f, buyPriceWithoutTax: value, buyPriceWithTax: withTax }))
      }
    } else {
      const parsed = parseFloat(value)
      if (isNaN(parsed) || !value) {
        setForm(f => ({ ...f, buyPriceWithTax: value, buyPriceWithoutTax: '' }))
      } else {
        const withoutTax = (parsed / (1 + gst / 100)).toFixed(2)
        setForm(f => ({ ...f, buyPriceWithTax: value, buyPriceWithoutTax: withoutTax }))
      }
    }
    clearError('buyPriceWithoutTax')
  }

  const handleProductChange = (productId) => {
    const selectedProduct = products.find(p => p.id === productId)
    const gst = selectedProduct ? Number(selectedProduct.gstPercent || 0) : 0
    const withoutTax = parseFloat(form.buyPriceWithoutTax)
    if (!isNaN(withoutTax)) {
      const withTax = (withoutTax * (1 + gst / 100)).toFixed(2)
      setForm(f => ({ ...f, productId, buyPriceWithTax: withTax }))
    } else {
      setForm(f => ({ ...f, productId }))
    }
    clearError('productId')
  }

  const stockColumns = [
    { header: 'Product', accessor: 'productName', render: (row) => (
      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
          <span className="font-medium">{row.productName}</span>
          {row.isLowStock && (
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '2px', color: 'var(--color-danger)', fontSize: '10px', fontWeight: 'bold' }}>
              ⚠️
            </span>
          )}
        </div>
        {row.brand && <div className="text-xs text-muted">{row.brand}</div>}
      </div>
    )},
    { header: 'Category', accessor: 'category', render: (row) => <span className="badge badge-accent">{row.category}</span> },
    { header: 'Primary', key: 'primary', render: (row) => `${row.totalPrimaryUnits || 0} ${row.primaryUnit || ''}` },
    { header: 'Secondary', key: 'secondary', render: (row) => `${row.totalSecondaryUnits || 0} ${row.secondaryUnit || ''}` },
    { header: 'Open Box', key: 'openBox', render: (row) => row.hasOpenPrimary
      ? <span className="badge badge-info">{row.openPrimaryRemaining} left</span>
      : <span className="text-muted">—</span>
    },
    { header: 'Status', key: 'stockStatus', render: (row) =>
      row.isLowStock
        ? <span className="badge badge-danger"><AlertTriangle size={12} /> Low Stock</span>
        : <span className="badge badge-success">OK</span>
    },
  ].filter(col => {
    if (isMobile) {
      return !['category', 'openBox', 'stockStatus'].includes(col.accessor || col.key)
    }
    return true
  })

  const expiringColumns = [
    { header: 'Product', accessor: 'productName', render: (row) => (
      <div>
        <div className="font-medium">{row.productName}</div>
        {row.brand && <div className="text-xs text-muted">{row.brand}</div>}
      </div>
    )},
    { header: 'Batch #', accessor: 'batchNumber' },
    { header: 'Remaining', accessor: 'secondaryRemaining', render: (row) => `${row.secondaryRemaining || 0} ${row.secondaryUnit || ''}` },
    { header: 'Supplier', accessor: 'supplierName' },
    { header: 'Expiry', accessor: 'expiryDate', render: (row) => {
      const d = row.expiryDate ? new Date(row.expiryDate) : null
      const daysLeft = d ? Math.ceil((d - new Date()) / 86400000) : null
      return d ? (
        <div>
          <div>{d.toLocaleDateString('en-IN')}</div>
          <div className={`text-xs ${daysLeft <= 3 ? 'text-danger' : 'text-warning'}`}>
            {daysLeft <= 0 ? 'EXPIRED' : `${daysLeft} days left`}
          </div>
        </div>
      ) : '—'
    }},
  ].filter(col => {
    if (isMobile) {
      return !['batchNumber', 'supplierName'].includes(col.accessor)
    }
    return true
  })

  const batchColumns = [
    { header: 'Batch #', accessor: 'batchNumber' },
    { header: 'Received', key: 'received', render: (row) => `${row.primaryReceived || 0} ${row.primaryUnit || ''} + ${row.secondaryReceived || 0} ${row.secondaryUnit || ''}` },
    { header: 'Remaining', accessor: 'secondaryRemaining', render: (row) => `${row.secondaryRemaining || 0} ${row.secondaryUnit || ''}` },
    { header: 'Buy ₹', accessor: 'buyPriceWithTax', render: (row) => `₹${Number(row.buyPriceWithTax || 0).toLocaleString('en-IN')}` },
    { header: 'Supplier', accessor: 'supplierName' },
    { header: 'Expiry', accessor: 'expiryDate', render: (row) => row.expiryDate ? new Date(row.expiryDate).toLocaleDateString('en-IN') : '—' },
    { header: 'Status', key: 'batchStatus', render: (row) => (
      <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
        {row.exhausted && <span className="badge badge-neutral">Exhausted</span>}
        {row.expiringSoon && <span className="badge badge-warning">Expiring</span>}
        {!row.exhausted && !row.expiringSoon && <span className="badge badge-success">Active</span>}
      </div>
    )},
  ].filter(col => {
    if (isMobile) {
      return !['supplierName', 'received', 'batchStatus'].includes(col.accessor || col.key)
    }
    return true
  })

  const auditColumns = [
    { header: 'Date', accessor: 'timestamp', render: (row) => row.timestamp ? new Date(row.timestamp).toLocaleString('en-IN') : '—' },
    { header: 'Product', accessor: 'productName', render: (row) => <span className="font-semibold">{row.productName}</span> },
    { header: 'Batch #', accessor: 'batchNumber' },
    { header: 'Old Quantity', accessor: 'oldSecondaryRemaining', render: (row) => `${row.oldSecondaryRemaining} units` },
    { header: 'New Quantity', accessor: 'newSecondaryRemaining', render: (row) => `${row.newSecondaryRemaining} units` },
    { header: 'Changed By', accessor: 'adjustedBy', render: (row) => <span className="badge badge-info">{row.adjustedBy}</span> },
    { header: 'Reason', accessor: 'reason' },
  ].filter(col => {
    if (isMobile) {
      return !['batchNumber', 'oldSecondaryRemaining', 'reason'].includes(col.accessor)
    }
    return true
  })

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h2 className="page-title">Stock Management</h2>
          <p className="page-subtitle">{stockTotalElements} products in stock</p>
        </div>
        <div className="page-actions">
          <motion.button className="btn btn-primary" onClick={() => { setForm({ ...emptyForm }); setValidationErrors({}); setShowModal(true); }} whileTap={{ scale: 0.95 }}>
            <Plus size={18} /> Receive Stock
          </motion.button>
        </div>
      </div>

      <div className="tabs">
        <button className={`tab ${activeTab === 'overview' ? 'active' : ''}`} onClick={() => setActiveTab('overview')}>
          <Package size={16} style={{ marginRight: 6, verticalAlign: 'middle' }} /> Stock Overview
        </button>
        <button className={`tab ${activeTab === 'expiring' ? 'active' : ''}`} onClick={() => setActiveTab('expiring')}>
          <Clock size={16} style={{ marginRight: 6, verticalAlign: 'middle' }} /> Expiring Soon ({expiring.length})
        </button>
        {aiEnabled && (isAdmin || isManager) && (
          <button className={`tab ${activeTab === 'scanner' ? 'active' : ''}`} onClick={() => setActiveTab('scanner')}>
            <Camera size={16} style={{ marginRight: 6, verticalAlign: 'middle' }} /> Scan Invoice
          </button>
        )}
        {isAdmin && (
          <button className={`tab ${activeTab === 'audit' ? 'active' : ''}`} onClick={() => setActiveTab('audit')}>
            <History size={16} style={{ marginRight: 6, verticalAlign: 'middle' }} /> Stock Audit Logs
          </button>
        )}
        {isAdmin && (
          <button className={`tab ${activeTab === 'scheduler' ? 'active' : ''}`} onClick={() => setActiveTab('scheduler')}>
            <Settings size={16} style={{ marginRight: 6, verticalAlign: 'middle' }} /> Scheduler
          </button>
        )}
      </div>

      {activeTab === 'overview' && (
        <>
          <DataTable
            columns={stockColumns}
            data={stocks}
            loading={loading}
            searchPlaceholder="Search stock..."
            emptyMessage="No stock data"
            onRowClick={(row) => row.productId && loadBatches(row.productId, row.productName)}
          />
          <Pagination
            page={stockPage}
            totalPages={stockTotalPages}
            totalElements={stockTotalElements}
            pageSize={STOCK_PAGE_SIZE}
            onPageChange={(p) => { setStockPage(p); loadAll(p) }}
          />
        </>
      )}

      {aiEnabled && activeTab === 'scanner' && (isAdmin || isManager) && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>
          <div style={{
            background: 'var(--color-surface)',
            border: '1px solid var(--color-border)',
            borderRadius: 'var(--radius-lg)',
            padding: 'var(--space-6)',
            display: 'flex',
            flexDirection: 'column',
            gap: 'var(--space-5)',
            maxWidth: '800px'
          }}>
            <h3 style={{ fontSize: 'var(--font-size-lg)', fontWeight: 'var(--font-weight-semibold)', margin: 0 }}>
              AI Invoice Scanner (₹0 Cost Gemini OCR)
            </h3>
            <p style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-muted)', margin: 0 }}>
              Invoice ki photo upload karein. Gemini API automatically batch numbers, quantities aur prices extract karke database products se map kar dega.
            </p>

            <form onSubmit={handleScanInvoice} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
              <div className="form-row">
                <div className="form-group" style={{ flex: 1 }}>
                  <label className="form-label">Supplier Name *</label>
                  <input
                    className="form-input"
                    value={scannerSupplier}
                    onChange={e => setScannerSupplier(e.target.value)}
                    required
                    placeholder="e.g. Saurabh Agency"
                  />
                </div>
                <div className="form-group" style={{ flex: 2 }}>
                  <label className="form-label">Invoice Photo *</label>
                  <div style={{
                    border: '2px dashed var(--color-border)',
                    borderRadius: 'var(--radius-md)',
                    padding: 'var(--space-4)',
                    textAlign: 'center',
                    cursor: 'pointer',
                    background: 'var(--color-bg)',
                    position: 'relative'
                  }}>
                    <input
                      type="file"
                      accept="image/*"
                      onChange={e => setScannerFile(e.target.files[0])}
                      style={{
                        position: 'absolute',
                        top: 0,
                        left: 0,
                        width: '100%',
                        height: '100%',
                        opacity: 0,
                        cursor: 'pointer'
                      }}
                    />
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '10px', color: 'var(--color-text-secondary)' }}>
                      <Upload size={18} />
                      <span style={{ fontSize: '13px', fontWeight: '500' }}>
                        {scannerFile ? scannerFile.name : 'Invoice Photo Select / Camera Click Karein'}
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              <div style={{ display: 'flex', gap: 'var(--space-3)' }}>
                <motion.button
                  type="submit"
                  className="btn btn-primary"
                  disabled={scannerLoading}
                  whileTap={{ scale: 0.97 }}
                  style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}
                >
                  <Camera size={18} />
                  {scannerLoading ? 'Reading Invoice...' : 'Scan & Map Invoice'}
                </motion.button>
                {scannerFile && (
                  <button
                    type="button"
                    className="btn btn-secondary"
                    onClick={() => { setScannerFile(null); setScannerPreview([]); }}
                  >
                    Clear
                  </button>
                )}
              </div>
            </form>
          </div>

          {scannerLoading && (
            <div style={{
              textAlign: 'center',
              padding: 'var(--space-12)',
              background: 'var(--color-surface)',
              border: '1px solid var(--color-border)',
              borderRadius: 'var(--radius-lg)',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: '15px'
            }}>
              <div className="spinner" style={{
                width: '40px',
                height: '40px',
                border: '4px solid var(--color-border)',
                borderTopColor: 'var(--color-primary)',
                borderRadius: '50%',
                animation: 'spin 1s linear infinite'
              }} />
              <style>{`
                @keyframes spin {
                  0% { transform: rotate(0deg); }
                  100% { transform: rotate(360deg); }
                }
              `}</style>
              <div>
                <h4 style={{ margin: '0 0 5px 0', fontWeight: '600' }}>Reading Invoice Layout...</h4>
                <p style={{ margin: 0, fontSize: '13px', color: 'var(--color-text-muted)' }}>
                  Extracting batch codes, validating expiry dates, and calculating unit conversions. Please wait 2 seconds.
                </p>
              </div>
            </div>
          )}

          {scannerPreview.length > 0 && !scannerLoading && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
              <div className="page-header" style={{ marginBottom: 0 }}>
                <div>
                  <h3 className="page-title" style={{ fontSize: 'var(--font-size-md)' }}>Scan Preview & Mapping Verification</h3>
                  <p className="page-subtitle">Niche diye gaye data ko verify karke map karein.</p>
                </div>
              </div>

              <div style={{ overflowX: 'auto', background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)', minHeight: '400px', paddingBottom: '160px' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px', textAlign: 'left' }}>
                  <thead>
                    <tr style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--color-bg)', color: 'var(--color-text-secondary)', fontWeight: '600' }}>
                      <th style={{ padding: '12px 16px' }}>Invoice Item</th>
                      <th style={{ padding: '12px 16px' }}>Category</th>
                      <th style={{ padding: '12px 16px' }}>Primary</th>
                      <th style={{ padding: '12px 16px' }}>Secondary</th>
                      <th style={{ padding: '12px 16px' }}>Open Box</th>
                      <th style={{ padding: '12px 16px' }}>Batch No</th>
                      <th style={{ padding: '12px 16px' }}>Expiry</th>
                      <th style={{ padding: '12px 16px' }}>Buy (BOX)</th>
                      <th style={{ padding: '12px 16px' }}>Status</th>
                      <th style={{ padding: '12px 16px', minWidth: '220px' }}>Mapped DB Product</th>
                      <th style={{ padding: '12px 16px', textAlign: 'center' }}>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {scannerPreview.map((item, index) => (
                      <tr key={index} style={{ borderBottom: '1px solid var(--color-border)' }}>
                        <td style={{ padding: '12px 16px', fontWeight: '500' }}>{item.productName}</td>
                        <td style={{ padding: '12px 16px' }}>
                          <span className="badge badge-accent">{item.category}</span>
                        </td>
                        <td style={{ padding: '12px 16px' }}>{item.primaryAdded} {item.primaryUnit}</td>
                        <td style={{ padding: '12px 16px' }}>{item.secondaryAdded} {item.secondaryUnit}</td>
                        <td style={{ padding: '12px 16px' }}>
                          {item.openBoxAdded > 0 ? (
                            <span className="badge badge-info">{item.openBoxAdded} left</span>
                          ) : '—'}
                        </td>
                        <td style={{ padding: '12px 16px' }}><code>{item.batchNumber}</code></td>
                        <td style={{ padding: '12px 16px' }}>{item.expiryDate}</td>
                        <td style={{ padding: '12px 16px' }}>₹{Number(item.buyPriceWithoutTax || 0).toLocaleString('en-IN')}</td>
                        <td style={{ padding: '12px 16px' }}>
                          {item.duplicateBatch ? (
                            <span className="badge badge-danger" style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}>
                              ⚠️ Duplicate
                            </span>
                          ) : item.newProduct ? (
                            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: '4px' }}>
                              <span className="badge badge-warning" style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}>
                                ❓ New Product
                              </span>
                              <button
                                type="button"
                                className="btn btn-ghost"
                                style={{ fontSize: '10px', padding: '2px 4px', color: 'var(--color-accent)', textDecoration: 'underline', height: 'auto', minHeight: 0, display: 'block' }}
                                onClick={() => openQuickProductModal(index)}
                              >
                                + Create Product
                              </button>
                            </div>
                          ) : (
                            <span className="badge badge-success">OK</span>
                          )}
                        </td>
                        <td style={{ padding: '12px 16px' }}>
                          <SearchSelect
                            options={products.filter(p => p.active !== false)}
                            value={item.productId || ''}
                            onChange={(val) => handleScannerRowProductChange(index, val)}
                            labelKey="name" valueKey="id"
                            placeholder="Map to existing product..."
                          />
                        </td>
                        <td style={{ padding: '12px 16px', textAlign: 'center' }}>
                          <button
                            type="button"
                            onClick={() => {
                              const updated = [...scannerPreview]
                              updated.splice(index, 1)
                              setScannerPreview(updated)
                              toast.info('Row removed from preview list')
                            }}
                            className="btn btn-ghost btn-icon btn-sm"
                            style={{ color: 'var(--color-danger)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}
                            title="Exclude from scan list"
                          >
                            <Trash2 size={16} />
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '10px' }}>
                <button
                  className="btn btn-secondary"
                  onClick={() => { setScannerPreview([]); setScannerFile(null); }}
                  disabled={saving}
                >
                  Cancel
                </button>
                <motion.button
                  onClick={handleScannerSubmit}
                  className="btn btn-primary"
                  disabled={saving}
                  whileTap={{ scale: 0.95 }}
                >
                  {saving ? 'Saving Stock...' : 'Confirm & Save Stock to DB'}
                </motion.button>
              </div>
            </div>
          )}
        </div>
      )}

      {activeTab === 'expiring' && (
        <DataTable
          columns={expiringColumns}
          data={expiring}
          loading={loading}
          searchable={false}
          emptyMessage="No expiring batches — all good!"
          actions={(row) => {
            const d = row.expiryDate ? new Date(row.expiryDate) : null
            const isExpired = d ? d <= new Date() : false
            return (
              <>
                {isExpired && (isAdmin || isManager) && (
                  <button
                    className="btn btn-ghost btn-icon btn-sm"
                    onClick={() => setWriteOffTarget(row)}
                    title="Write Off Expired Stock to Damage Log"
                    style={{ color: 'var(--color-danger)' }}
                  >
                    <Trash2 size={15} />
                  </button>
                )}
              </>
            )
          }}
        />
      )}

      {activeTab === 'audit' && isAdmin && (
        <>
          <DataTable
            columns={auditColumns}
            data={auditLogs}
            loading={logsLoading}
            searchable={false}
            emptyMessage="No adjustment logs found"
          />
          <Pagination
            page={auditPage}
            totalPages={auditTotalPages}
            totalElements={auditTotalElements}
            pageSize={AUDIT_PAGE_SIZE}
            onPageChange={(p) => { setAuditPage(p); loadAuditLogs(p) }}
          />
        </>
      )}

      {activeTab === 'scheduler' && isAdmin && (
        <div style={{ maxWidth: '640px' }}>
          <div style={{
            background: 'var(--color-surface)',
            border: '1px solid var(--color-border)',
            borderRadius: 'var(--radius-lg)',
            padding: 'var(--space-6)',
            display: 'flex',
            flexDirection: 'column',
            gap: 'var(--space-5)',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div>
                <h3 style={{ fontSize: 'var(--font-size-lg)', fontWeight: 'var(--font-weight-semibold)', margin: 0 }}>Expiry Write-Off Scheduler</h3>
                <p style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-muted)', margin: '4px 0 0' }}>Automatically writes off expired stock batches to Damage Logs</p>
              </div>
              {schedulerStatus && (
                schedulerStatus.enabled
                  ? <span className="badge badge-success" style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '6px 12px' }}><CheckCircle size={14} /> Enabled</span>
                  : <span className="badge badge-danger" style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '6px 12px' }}><XCircle size={14} /> Disabled</span>
              )}
            </div>

            {schedulerLoading ? (
              <div style={{ textAlign: 'center', padding: 'var(--space-8)', color: 'var(--color-text-muted)' }}>Loading scheduler info...</div>
            ) : schedulerStatus ? (
              <>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-4)' }}>
                  <div style={{ background: 'var(--color-surface-alt, var(--color-bg))', borderRadius: 'var(--radius-md)', padding: 'var(--space-4)', border: '1px solid var(--color-border)' }}>
                    <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', marginBottom: 4, textTransform: 'uppercase', letterSpacing: '0.05em' }}>Schedule</div>
                    <div style={{ fontWeight: 'var(--font-weight-semibold)' }}>Daily at 1:00 AM</div>
                    <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', marginTop: 2 }}>{schedulerStatus.cronExpression}</div>
                  </div>
                  <div style={{ background: 'var(--color-surface-alt, var(--color-bg))', borderRadius: 'var(--radius-md)', padding: 'var(--space-4)', border: '1px solid var(--color-border)' }}>
                    <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', marginBottom: 4, textTransform: 'uppercase', letterSpacing: '0.05em' }}>Last Run</div>
                    <div style={{ fontWeight: 'var(--font-weight-semibold)' }}>
                      {schedulerStatus.lastRunTime ? new Date(schedulerStatus.lastRunTime).toLocaleString('en-IN') : '—'}
                    </div>
                    <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', marginTop: 2 }}>
                      {schedulerStatus.lastRunBatchesProcessed ?? 0} batch(es) written off
                    </div>
                  </div>
                </div>

                <div style={{ background: 'var(--color-surface-alt, var(--color-bg))', borderRadius: 'var(--radius-md)', padding: 'var(--space-4)', border: '1px solid var(--color-border)' }}>
                  <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', marginBottom: 4, textTransform: 'uppercase', letterSpacing: '0.05em' }}>Last Run Status</div>
                  <div style={{ fontSize: 'var(--font-size-sm)', color: schedulerStatus.lastRunStatus?.includes('error') ? 'var(--color-danger)' : 'var(--color-text)' }}>
                    {schedulerStatus.lastRunStatus || 'Never run'}
                  </div>
                </div>

                <div style={{ display: 'flex', gap: 'var(--space-3)', paddingTop: 'var(--space-2)', borderTop: '1px solid var(--color-border)' }}>
                  <motion.button
                    className="btn btn-primary"
                    onClick={() => setShowRunNowConfirm(true)}
                    disabled={runNowLoading || !schedulerStatus.enabled}
                    whileTap={{ scale: 0.97 }}
                    style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}
                  >
                    <PlayCircle size={18} />
                    {runNowLoading ? 'Running...' : 'Run Sweep Now'}
                  </motion.button>
                  <button className="btn btn-ghost" onClick={loadSchedulerStatus} disabled={schedulerLoading}>
                    Refresh Status
                  </button>
                </div>

                {!schedulerStatus.enabled && (
                  <div style={{ padding: 'var(--space-3)', background: 'rgba(239,68,68,0.08)', borderRadius: 'var(--radius-md)', border: '1px solid rgba(239,68,68,0.2)', fontSize: 'var(--font-size-sm)', color: 'var(--color-danger)' }}>
                    ⚠️ Scheduler is disabled. To enable, set <code>app.scheduler.expiry.enabled=true</code> in <code>application.properties</code> and restart the server.
                  </div>
                )}
              </>
            ) : (
              <div style={{ textAlign: 'center', padding: 'var(--space-8)', color: 'var(--color-text-muted)' }}>No scheduler data available.</div>
            )}
          </div>
        </div>
      )}

      {/* Batches Modal */}
      <Modal isOpen={!!batchProduct} onClose={() => setBatchProduct(null)} title={`Batches — ${batchProduct?.name || ''}`} xl>
        <DataTable
          columns={batchColumns}
          data={batches}
          loading={batchLoading}
          searchable={false}
          emptyMessage="No batches found"
          pageSize={8}
          actions={(row) => (
            <>
              {(isAdmin || isManager) && (
                <button
                  className="btn btn-ghost btn-icon btn-sm"
                  onClick={() => {
                    setAdjustingBatch(row)
                    const ratio = row.secondaryPerPrimary || 1
                    setAdjustPrimary(Math.floor(row.secondaryRemaining / ratio).toString())
                    setAdjustBuyPrice(row.buyPriceWithoutTax || '')
                    setAdjustBuyPriceWithTax(row.buyPriceWithTax || '')
                    setValidationErrors({})
                  }}
                  title="Correct / Adjust Stock"
                  style={{ color: 'var(--color-accent)' }}
                >
                  <Edit2 size={15} />
                </button>
              )}
            </>
          )}
        />
      </Modal>

      {/* Correct / Adjust Stock Modal */}
      <Modal isOpen={!!adjustingBatch} onClose={() => setAdjustingBatch(null)} title={`Correct Stock Batch — ${adjustingBatch?.batchNumber || ''}`}>
        {adjustingBatch && (
          <form onSubmit={handleAdjustStockSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            <div className="form-group">
              <label className="form-label">Current Stock</label>
              <input
                className="form-input"
                value={`${Math.floor(adjustingBatch.secondaryRemaining / (adjustingBatch.secondaryPerPrimary || 1))} ${adjustingBatch.primaryUnit || 'Box'} ${adjustingBatch.secondaryRemaining % (adjustingBatch.secondaryPerPrimary || 1) > 0 ? `+ ${adjustingBatch.secondaryRemaining % (adjustingBatch.secondaryPerPrimary || 1)} ${adjustingBatch.secondaryUnit || 'Packs'}` : ''} (${adjustingBatch.secondaryRemaining} Total ${adjustingBatch.secondaryUnit || 'Packs'})`}
                disabled
                style={{ opacity: 0.7 }}
              />
            </div>
            <div className="form-group">
              <label className="form-label">Corrected Quantity ({adjustingBatch.primaryUnit || 'Box'}) *</label>
              <input
                className="form-input"
                type="number"
                min="0"
                value={adjustPrimary}
                onChange={e => {
                  setAdjustPrimary(e.target.value)
                  clearError('newSecondaryRemaining')
                }}
                style={validationErrors.newSecondaryRemaining ? { borderColor: 'var(--color-danger)' } : {}}
                required
              />
              {validationErrors.newSecondaryRemaining && (
                <span className="text-xs" style={{ color: 'var(--color-danger)', marginTop: '4px', display: 'block' }}>
                  {validationErrors.newSecondaryRemaining}
                </span>
              )}
              <span className="text-xs text-muted" style={{ marginTop: 'var(--space-1)', display: 'block' }}>
                Will be updated to {adjustPrimary * (adjustingBatch.secondaryPerPrimary || 1)} total {adjustingBatch.secondaryUnit || 'packs'}.
              </span>
            </div>
            <div className="form-row">
              <div className="form-group">
                <label className="form-label">Corrected Buy Price (no tax) per {adjustingBatch.primaryUnit || 'Box'} ₹</label>
                <input
                  className="form-input"
                  type="number"
                  min="0"
                  step="0.01"
                  value={adjustBuyPrice}
                  onChange={e => {
                    handleAdjustPriceChange('without', e.target.value)
                    clearError('newBuyPriceWithoutTax')
                  }}
                  style={validationErrors.newBuyPriceWithoutTax ? { borderColor: 'var(--color-danger)' } : {}}
                  placeholder="Price without tax..."
                />
                {validationErrors.newBuyPriceWithoutTax && (
                  <span className="text-xs" style={{ color: 'var(--color-danger)', marginTop: '4px', display: 'block' }}>
                    {validationErrors.newBuyPriceWithoutTax}
                  </span>
                )}
              </div>
              <div className="form-group">
                <label className="form-label">Corrected Buy Price (with tax) per {adjustingBatch.primaryUnit || 'Box'} ₹</label>
                <input
                  className="form-input"
                  type="number"
                  min="0"
                  step="0.01"
                  value={adjustBuyPriceWithTax}
                  onChange={e => {
                    handleAdjustPriceChange('with', e.target.value)
                    clearError('newBuyPriceWithoutTax')
                  }}
                  style={validationErrors.newBuyPriceWithoutTax ? { borderColor: 'var(--color-danger)' } : {}}
                  placeholder="Price with tax..."
                />
              </div>
            </div>
            <div className="form-group">
              <label className="form-label">Reason for Correction *</label>
              <textarea
                className="form-textarea"
                value={adjustReason}
                onChange={e => {
                  setAdjustReason(e.target.value)
                  clearError('reason')
                }}
                style={validationErrors.reason ? { borderColor: 'var(--color-danger)' } : {}}
                placeholder="Specify why stock is being modified (e.g. Typo in entry, damage, sample return)..."
                required
                rows={3}
              />
              {validationErrors.reason && (
                <span className="text-xs" style={{ color: 'var(--color-danger)', marginTop: '4px', display: 'block' }}>
                  {validationErrors.reason}
                </span>
              )}
            </div>
            <div className="form-actions">
              <button type="button" className="btn btn-secondary" onClick={() => setAdjustingBatch(null)}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={adjusting}>
                {adjusting ? 'Correcting...' : 'Submit Correction'}
              </button>
            </div>
          </form>
        )}
      </Modal>

      {/* Receive Stock Modal */}
      <Modal isOpen={showModal} onClose={() => setShowModal(false)} title="Receive Stock" wide>
        {(() => {
          const selectedProduct = products.find(p => p.id === form.productId)
          const primaryUnit = selectedProduct?.primaryUnit || 'Primary'
          const secondaryUnit = selectedProduct?.secondaryUnit || 'Secondary'
          const gst = selectedProduct ? Number(selectedProduct.gstPercent || 0) : 0
          const buyPriceWithTaxVal = form.buyPriceWithTax 
            ? Number(form.buyPriceWithTax) 
            : (form.buyPriceWithoutTax ? Number(form.buyPriceWithoutTax) * (1 + gst / 100) : 0)
          const totalCostVal = (Number(form.primaryReceived || 0) * buyPriceWithTaxVal) + 
            (Number(form.extraSecondaryReceived || 0) * (buyPriceWithTaxVal / (selectedProduct?.secondaryPerPrimary || 1)))

          return (
            <form onSubmit={handleReceive} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
              <div className="form-group">
                <label className="form-label">Product *</label>
                <SearchSelect
                  options={products.filter(p => p.active !== false)}
                  value={form.productId}
                  onChange={handleProductChange}
                  labelKey="name" valueKey="id"
                  placeholder="Select product..."
                />
                {validationErrors.productId && (
                  <span className="text-xs" style={{ color: 'var(--color-danger)', marginTop: '4px', display: 'block' }}>
                    {validationErrors.productId}
                  </span>
                )}
              </div>
              <div className="form-row">
                <div className="form-group">
                  <label className="form-label">Batch Number *</label>
                  <input
                    className="form-input"
                    value={form.batchNumber}
                    onChange={e => updateField('batchNumber', e.target.value)}
                    style={validationErrors.batchNumber ? { borderColor: 'var(--color-danger)' } : {}}
                    required
                    minLength={2}
                    placeholder="e.g. B-2024-001"
                  />
                  {validationErrors.batchNumber && (
                    <span className="text-xs" style={{ color: 'var(--color-danger)', marginTop: '4px', display: 'block' }}>
                      {validationErrors.batchNumber}
                    </span>
                  )}
                </div>
                <div className="form-group">
                  <label className="form-label">Supplier *</label>
                  <input
                    className="form-input"
                    value={form.supplierName}
                    onChange={e => updateField('supplierName', e.target.value)}
                    style={validationErrors.supplierName ? { borderColor: 'var(--color-danger)' } : {}}
                    required
                    placeholder="Supplier name"
                  />
                  {validationErrors.supplierName && (
                    <span className="text-xs" style={{ color: 'var(--color-danger)', marginTop: '4px', display: 'block' }}>
                      {validationErrors.supplierName}
                    </span>
                  )}
                </div>
              </div>
              <div className="form-row-4">
                <div className="form-group">
                  <label className="form-label">Primary Units ({primaryUnit})</label>
                  <input 
                    className="form-input" 
                    type="number" 
                    min="0" 
                    value={form.primaryReceived} 
                    onChange={e => updateField('primaryReceived', e.target.value)} 
                    style={validationErrors.primaryReceived ? { borderColor: 'var(--color-danger)' } : {}}
                  />
                  {validationErrors.primaryReceived && (
                    <span className="text-xs" style={{ color: 'var(--color-danger)', marginTop: '4px', display: 'block' }}>
                      {validationErrors.primaryReceived}
                    </span>
                  )}
                </div>
                <div className="form-group">
                  <label className="form-label">Extra Secondary ({secondaryUnit})</label>
                  <input 
                    className="form-input" 
                    type="number" 
                    min="0" 
                    value={form.extraSecondaryReceived} 
                    onChange={e => updateField('extraSecondaryReceived', e.target.value)} 
                    style={validationErrors.extraSecondaryReceived ? { borderColor: 'var(--color-danger)' } : {}}
                  />
                  {validationErrors.extraSecondaryReceived && (
                    <span className="text-xs" style={{ color: 'var(--color-danger)', marginTop: '4px', display: 'block' }}>
                      {validationErrors.extraSecondaryReceived}
                    </span>
                  )}
                </div>
                <div className="form-group">
                  <label className="form-label">Buy Price (Excl. Tax) ₹ *</label>
                  <input
                    className="form-input"
                    type="number"
                    min="0"
                    step="0.01"
                    value={form.buyPriceWithoutTax}
                    onChange={e => handlePriceChange('without', e.target.value)}
                    style={validationErrors.buyPriceWithoutTax ? { borderColor: 'var(--color-danger)' } : {}}
                    required
                  />
                  {validationErrors.buyPriceWithoutTax && (
                    <span className="text-xs" style={{ color: 'var(--color-danger)', marginTop: '4px', display: 'block' }}>
                      {validationErrors.buyPriceWithoutTax}
                    </span>
                  )}
                </div>
                <div className="form-group">
                  <label className="form-label">Buy Price (Incl. Tax) ₹</label>
                  <input
                    className="form-input"
                    type="number"
                    min="0"
                    step="0.01"
                    value={form.buyPriceWithTax || ''}
                    onChange={e => handlePriceChange('with', e.target.value)}
                    style={validationErrors.buyPriceWithoutTax ? { borderColor: 'var(--color-danger)' } : {}}
                  />
                </div>
              </div>
              <div className="form-group">
                <label className="form-label">Expiry Date *</label>
                <input
                  className="form-input"
                  type="date"
                  value={form.expiryDate}
                  onChange={e => updateField('expiryDate', e.target.value)}
                  style={validationErrors.expiryDate ? { borderColor: 'var(--color-danger)' } : {}}
                  required
                />
                {validationErrors.expiryDate && (
                  <span className="text-xs" style={{ color: 'var(--color-danger)', marginTop: '4px', display: 'block' }}>
                    {validationErrors.expiryDate}
                  </span>
                )}
              </div>

              {selectedProduct && (
                <div style={{
                  background: 'var(--color-bg-secondary, #f8fafc)',
                  border: '1px solid var(--color-border, #e2e8f0)',
                  borderRadius: 'var(--radius-md, 8px)',
                  padding: 'var(--space-4, 16px)',
                  marginTop: 'var(--space-1, 4px)'
                }}>
                  <div style={{ fontSize: '13px', fontWeight: 'bold', marginBottom: '8px', color: 'var(--color-text-secondary)' }}>
                    Sell Price Settings (Optional)
                  </div>
                  <div style={{ fontSize: '12px', color: 'var(--color-text-muted)', marginBottom: '12px', display: 'flex', gap: '20px' }}>
                    <span>Current Sell ({primaryUnit}): <strong>₹{selectedProduct.sellPricePrimary || 0}</strong></span>
                    <span>Current Sell ({secondaryUnit}): <strong>₹{selectedProduct.sellPriceSecondary || 0}</strong></span>
                  </div>
                  <div className="form-row">
                    <div className="form-group">
                      <label className="form-label">New Sell Price ({primaryUnit}) ₹</label>
                      <input
                        className="form-input"
                        type="number"
                        min="0"
                        step="0.01"
                        value={form.sellPricePrimary}
                        onChange={e => updateField('sellPricePrimary', e.target.value)}
                        style={validationErrors.sellPricePrimary ? { borderColor: 'var(--color-danger)' } : {}}
                        placeholder="Leave blank to keep current..."
                      />
                      {validationErrors.sellPricePrimary && (
                        <span className="text-xs" style={{ color: 'var(--color-danger)', marginTop: '4px', display: 'block' }}>
                          {validationErrors.sellPricePrimary}
                        </span>
                      )}
                    </div>
                    <div className="form-group">
                      <label className="form-label">New Sell Price ({secondaryUnit}) ₹</label>
                      <input
                        className="form-input"
                        type="number"
                        min="0"
                        step="0.01"
                        value={form.sellPriceSecondary}
                        onChange={e => updateField('sellPriceSecondary', e.target.value)}
                        style={validationErrors.sellPriceSecondary ? { borderColor: 'var(--color-danger)' } : {}}
                        placeholder="Leave blank to keep current..."
                      />
                      {validationErrors.sellPriceSecondary && (
                        <span className="text-xs" style={{ color: 'var(--color-danger)', marginTop: '4px', display: 'block' }}>
                          {validationErrors.sellPriceSecondary}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              )}

              <div className="form-row" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 'var(--space-1)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <input
                    type="checkbox"
                    id="logAsExpenseCheckbox"
                    checked={!!form.logAsExpense}
                    onChange={e => updateField('logAsExpense', e.target.checked)}
                    style={{ width: '16px', height: '16px', cursor: 'pointer' }}
                  />
                  <label htmlFor="logAsExpenseCheckbox" style={{ fontSize: '13px', fontWeight: '500', cursor: 'pointer', margin: 0 }}>
                    Auto-record as Expense (Expenses mein kharch darj karein)
                  </label>
                </div>
                {totalCostVal > 0 && (
                  <div style={{ fontSize: '14px', fontWeight: 'bold', color: 'var(--color-text-secondary)' }}>
                    Total Purchase Cost: <span style={{ color: 'var(--color-danger)' }}>₹{totalCostVal.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
                  </div>
                )}
              </div>

              <div className="form-actions" style={{ marginTop: 'var(--space-2)' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
                <motion.button type="submit" className="btn btn-primary" disabled={saving} whileTap={{ scale: 0.95 }}>
                  {saving ? 'Receiving...' : 'Receive Stock'}
                </motion.button>
              </div>
            </form>
          )
        })()}
      </Modal>
      
      {/* Write Off Confirm Dialog */}
      <ConfirmDialog
        isOpen={!!writeOffTarget}
        onClose={() => setWriteOffTarget(null)}
        onConfirm={() => executeWriteOff(writeOffTarget.id)}
        title="Write Off Expired Stock"
        message={`Are you sure you want to write off the expired stock for batch ${writeOffTarget?.batchNumber}? This will set remaining stock to 0 and log a financial loss of ₹${(Number(writeOffTarget?.buyPriceWithTax || 0) / (writeOffTarget?.secondaryPerPrimary || 1) * (writeOffTarget?.secondaryRemaining || 0)).toLocaleString('en-IN')} under Damage Logs.`}
        confirmLabel="Write Off"
        danger={true}
      />

      {/* Quick Add Product Modal */}
      <Modal isOpen={showQuickProductModal} onClose={() => setShowQuickProductModal(false)} title="Quick Create New Product" wide>
        <form onSubmit={handleQuickProductSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <div className="form-group">
            <label className="form-label">Product Name *</label>
            <input
              className="form-input"
              value={quickProductForm.name}
              onChange={e => setQuickProductForm(f => ({ ...f, name: e.target.value }))}
              required
              placeholder="Product name"
            />
          </div>
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Brand</label>
              <input
                className="form-input"
                value={quickProductForm.brand}
                onChange={e => setQuickProductForm(f => ({ ...f, brand: e.target.value }))}
                placeholder="e.g. Haldiram's"
              />
            </div>
            <div className="form-group">
              <label className="form-label">Category *</label>
              <select
                className="form-input"
                value={quickProductForm.category}
                onChange={e => setQuickProductForm(f => ({ ...f, category: e.target.value }))}
                required
              >
                {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">GST Percent *</label>
              <input
                className="form-input"
                type="number"
                min="0"
                max="40"
                step="0.1"
                value={quickProductForm.gstPercent}
                onChange={e => setQuickProductForm(f => ({ ...f, gstPercent: e.target.value }))}
                required
              />
            </div>
          </div>
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Primary Unit *</label>
              <select
                className="form-select"
                value={quickProductForm.primaryUnit}
                onChange={e => setQuickProductForm(f => ({ ...f, primaryUnit: e.target.value }))}
                required
              >
                <option value="BOX">BOX</option>
                <option value="CRATE">CRATE</option>
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Secondary Unit *</label>
              <select
                className="form-select"
                value={quickProductForm.secondaryUnit}
                onChange={e => setQuickProductForm(f => ({ ...f, secondaryUnit: e.target.value }))}
                required
              >
                <option value="LADI">LADI</option>
                <option value="PACK">PACKET / PACK</option>
                <option value="BOTTLE">BOTTLE</option>
                <option value="OTHER">OTHER (Custom)</option>
              </select>
              {quickProductForm.secondaryUnit === 'OTHER' && (
                <input
                  className="form-input"
                  style={{ marginTop: 'var(--space-2)' }}
                  value={quickProductForm.customSecondaryUnit || ''}
                  onChange={e => setQuickProductForm(f => ({ ...f, customSecondaryUnit: e.target.value.toUpperCase() }))}
                  placeholder="Specify Unit (e.g. PCS, TIN)"
                  required
                />
              )}
            </div>
            <div className="form-group">
              <label className="form-label">Packs Per Box (Ratio) *</label>
              <input
                className="form-input"
                type="number"
                min="1"
                value={quickProductForm.secondaryPerPrimary}
                onChange={e => setQuickProductForm(f => ({ ...f, secondaryPerPrimary: e.target.value }))}
                required
              />
            </div>
          </div>
          <div className="form-row" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 'var(--space-4)' }}>
            <div className="form-group">
              <label className="form-label">Buy Price per Box (Excl. Tax) *</label>
              <input
                className="form-input"
                type="number"
                min="0"
                step="0.01"
                value={quickProductForm.buyPriceWithoutTax}
                onChange={e => setQuickProductForm(f => ({ ...f, buyPriceWithoutTax: e.target.value }))}
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label">Sell Price per Box (Incl. Tax) *</label>
              <input
                className="form-input"
                type="number"
                min="0"
                step="0.01"
                value={quickProductForm.sellPricePrimary}
                onChange={e => setQuickProductForm(f => ({ ...f, sellPricePrimary: e.target.value }))}
                required
                placeholder="e.g. 500"
              />
            </div>
            <div className="form-group">
              <label className="form-label">Sell Price per Ladi/Pack (Incl. Tax) *</label>
              <input
                className="form-input"
                type="number"
                min="0"
                step="0.01"
                value={quickProductForm.sellPriceSecondary}
                onChange={e => setQuickProductForm(f => ({ ...f, sellPriceSecondary: e.target.value }))}
                required
                placeholder="e.g. 50"
              />
            </div>
          </div>
          <div className="form-actions" style={{ marginTop: 'var(--space-2)' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setShowQuickProductModal(false)}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={saving}>
              {saving ? 'Creating Product...' : 'Create & Map Product'}
            </button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog
        isOpen={showRunNowConfirm}
        onClose={() => setShowRunNowConfirm(false)}
        onConfirm={handleRunNow}
        title="Run Expiry Sweep Now"
        message="This will immediately scan all stock batches and write off any expired ones to Damage Logs. This action cannot be undone. Continue?"
        confirmLabel="Run Sweep"
        danger={true}
      />
    </div>
  )
}

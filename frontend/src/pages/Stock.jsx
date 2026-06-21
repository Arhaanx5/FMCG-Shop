import { useState, useEffect, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { 
  Plus, AlertTriangle, Clock, Package, Edit2, History, Trash2, 
  Settings, PlayCircle, CheckCircle, XCircle, Camera, Upload, 
  Calendar, FileText, ArrowRightLeft, TrendingUp, ShieldAlert,
  BarChart2, Award, Download, Filter, Search, RotateCcw, X, PlusCircle
} from 'lucide-react'
import api from '../services/api'
import DataTable from '../components/DataTable'
import Pagination from '../components/Pagination'
import Modal from '../components/Modal'
import SearchSelect from '../components/SearchSelect'
import { useToast } from '../context/ToastContext'
import { useAuth } from '../context/AuthContext'
import ConfirmDialog from '../components/ConfirmDialog'
import { 
  ResponsiveContainer, BarChart, Bar, XAxis, YAxis, 
  Tooltip, PieChart, Pie, Cell, LineChart, Line, CartesianGrid, Legend 
} from 'recharts'

const CATEGORIES = ['CHIPS', 'SNACKS', 'BEVERAGES', 'CIGARETTES', 'BISCUITS', 'NAMKEEN', 'OTHER']

export default function Stock() {
  const { isAdmin, isManager, aiEnabled } = useAuth()
  const [isMobile, setIsMobile] = useState(window.innerWidth < 768)
  const toast = useToast()
  const fileInputRef = useRef(null)

  useEffect(() => {
    const handleResize = () => setIsMobile(window.innerWidth < 768)
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  // Tabs: dashboard (first now), overview (Inventory), receive, batches, movements, bi, reports, audit (if admin)
  const [activeTab, setActiveTab] = useState('dashboard')

  // Global list data states
  const [products, setProducts] = useState([])
  const [loading, setLoading] = useState(true)

  // Filters & Search
  const [searchTerm, setSearchTerm] = useState('')
  const [selectedCategory, setSelectedCategory] = useState('')
  const [selectedBrand, setSelectedBrand] = useState('')
  const [selectedStatus, setSelectedStatus] = useState('')

  // 1. Dashboard State
  const [dashboardData, setDashboardData] = useState(null)
  const [dashboardLoading, setDashboardLoading] = useState(false)

  // 2. Receive Stock Sub-Tabs ("single" vs "bulk")
  const [receiveMode, setReceiveMode] = useState('single')
  const [manualForm, setManualForm] = useState({
    productId: '',
    batchNumber: '',
    supplierInvoiceNumber: '',
    supplierInvoiceDate: '',
    stockReceivedDate: new Date().toISOString().split('T')[0],
    manufacturingDate: '',
    remarks: '',
    primaryReceived: '',
    extraSecondaryReceived: '',
    offerSecondaryReceived: '',
    buyPriceWithoutTax: '',
    expiryDate: '',
    supplierName: '',
    sellPricePrimary: '',
    sellPriceSecondary: '',
    gstPercent: '5',
    logAsExpense: true
  })
  const [savingStock, setSavingStock] = useState(false)

  // Manual Bulk Receive State
  const [bulkSupplierName, setBulkSupplierName] = useState('')
  const [bulkInvoiceNumber, setBulkInvoiceNumber] = useState('')
  const [bulkInvoiceDate, setBulkInvoiceDate] = useState('')
  const [bulkReceivedDate, setBulkReceivedDate] = useState(new Date().toISOString().split('T')[0])
  const [bulkRemarks, setBulkRemarks] = useState('')
  const [bulkRows, setBulkRows] = useState([
    { productId: '', batchNumber: '', manufacturingDate: '', expiryDate: '', primaryReceived: '', extraSecondaryReceived: '', offerSecondaryReceived: '', buyPriceWithoutTax: '', gstPercent: '5' }
  ])

  // AI Invoice Scanner OCR States
  const [scannerFile, setScannerFile] = useState(null)
  const [scannerLoading, setScannerLoading] = useState(false)
  const [scannerPreview, setScannerPreview] = useState([])
  const [editingNameIndex, setEditingNameIndex] = useState(null)
  const [scannerSupplier, setScannerSupplier] = useState('Saurabh Agency')
  const [scannerInvoiceNumber, setScannerInvoiceNumber] = useState('')
  const [invoiceAlreadyScanned, setInvoiceAlreadyScanned] = useState(false)

  // Quick Add Product States (for new products parsed via OCR)
  const [showQuickProductModal, setShowQuickProductModal] = useState(false)
  const [quickProductIndex, setQuickProductIndex] = useState(null)
  const [quickProductForm, setQuickProductForm] = useState({
    name: '', brand: '', category: 'SNACKS', otherCategoryDetail: '', gstPercent: '5', cessPercent: '0', isCessApplicable: false,
    primaryUnit: 'BOX', secondaryUnit: 'LADI', customSecondaryUnit: '', secondaryPerPrimary: '20',
    canSellPrimary: true, canSellSecondary: true,
    buyPriceWithoutTax: '', buyPriceWithTax: '',
    sellPricePrimary: '', sellPriceSecondary: '',
    sellPricePrimaryExcl: '', sellPricePrimaryIncl: '',
    sellPriceSecondaryExcl: '', sellPriceSecondaryIncl: '',
    lowStockAlert: '10', lowStockUnit: 'SECONDARY',
  })

  // 3. Inventory list pagination
  const [inventoryList, setInventoryList] = useState([])
  const [invPage, setInvPage] = useState(0)
  const [invTotalPages, setInvTotalPages] = useState(0)
  const [invTotalElements, setInvTotalElements] = useState(0)
  const INV_PAGE_SIZE = 15

  // 4. Batch Inventory Page
  const [batchList, setBatchList] = useState([])
  const [batchPage, setBatchPage] = useState(0)
  const [batchTotalPages, setBatchTotalPages] = useState(0)
  const [batchTotalElements, setBatchTotalElements] = useState(0)
  const [batchSearchTerm, setBatchSearchTerm] = useState('')
  const [debouncedBatchSearchTerm, setDebouncedBatchSearchTerm] = useState('')
  const [batchView, setBatchView] = useState('list') // 'list' | 'invoices'
  const [allBatchList, setAllBatchList] = useState([]) // all batches for invoice grouping
  const [invoicePage, setInvoicePage] = useState(0)
  const INVOICE_PAGE_SIZE = 15

  // 5. Stock Movement Ledger
  const [movementList, setMovementList] = useState([])
  const [movementPage, setMovementPage] = useState(0)
  const [movementTotalPages, setMovementTotalPages] = useState(0)
  const [selectedMovementType, setSelectedMovementType] = useState('')

  // 6. Audit Logs Adjustments
  const [auditLogs, setAuditLogs] = useState([])
  const [auditPage, setAuditPage] = useState(0)
  const [auditTotalPages, setAuditTotalPages] = useState(0)

  // 7. Reports Module
  const [selectedReport, setSelectedReport] = useState('valuation')
  const [reportData, setReportData] = useState([])
  const [reportPage, setReportPage] = useState(0)
  const [reportTotalPages, setReportTotalPages] = useState(0)
  const [reportTotalElements, setReportTotalElements] = useState(0)
  const [reportLoading, setReportLoading] = useState(false)
  const [exporting, setExporting] = useState(false)

  // 8. BI & Forecasting State
  const [biHealth, setBiHealth] = useState(null)
  const [biReorders, setBiReorders] = useState([])
  const [biLoading, setBiLoading] = useState(false)

  // Adjust stock states
  const [adjustingBatch, setAdjustingBatch] = useState(null)
  const [adjustPrimary, setAdjustPrimary] = useState('')
  const [adjustSecondary, setAdjustSecondary] = useState('')
  const [adjustOffer, setAdjustOffer] = useState('')
  const [adjustBuyPrice, setAdjustBuyPrice] = useState('')
  const [adjustReason, setAdjustReason] = useState('')
  const [adjusting, setAdjusting] = useState(false)

  // Expiry Write-Off confirmation
  const [writeOffTarget, setWriteOffTarget] = useState(null)

  // Fetch Products for dropdowns
  const loadProducts = async () => {
    try {
      const res = await api.get('/products?size=500')
      setProducts(res.data.data?.content || res.data.data || [])
    } catch {
      toast.error('Failed to load products list')
    }
  }

  // Fetch Inventory List
  const loadInventory = async (page = 0, search = searchTerm, category = selectedCategory, status = selectedStatus) => {
    setLoading(true)
    try {
      const params = new URLSearchParams()
      params.set('page', page)
      params.set('size', INV_PAGE_SIZE)
      if (search && search.trim()) params.set('search', search.trim())
      if (category) params.set('category', category)
      if (status) params.set('status', status)

      const res = await api.get(`/stock/paged?${params.toString()}`)
      setInventoryList(res.data.data?.content || [])
      setInvTotalPages(res.data.data?.totalPages || 0)
      setInvTotalElements(res.data.data?.totalElements || 0)
    } catch {
      toast.error('Failed to load stock inventory')
    } finally {
      setLoading(false)
    }
  }

  // Fetch Dashboard Stats
  const loadDashboard = async () => {
    setDashboardLoading(true)
    try {
      const res = await api.get('/stock/dashboard')
      setDashboardData(res.data.data)
    } catch {
      toast.error('Failed to load dashboard metrics')
    } finally {
      setDashboardLoading(false)
    }
  }

  // Fetch Batches (paginated)
  const loadBatches = async (page = 0, search = batchSearchTerm) => {
    try {
      const queryParams = new URLSearchParams()
      queryParams.set('page', page)
      queryParams.set('size', 15)
      if (search && search.trim().length >= 2) {
        queryParams.set('search', search.trim())
      }
      const res = await api.get(`/stock/batches?${queryParams.toString()}`)
      setBatchList(res.data.data?.content || res.data.data || [])
      setBatchTotalPages(res.data.data?.totalPages || 0)
      setBatchTotalElements(res.data.data?.totalElements || 0)
    } catch {
      toast.error('Failed to load batches')
    }
  }

  // Fetch ALL Batches for Invoice Summary (unpaginated)
  const loadAllBatches = async () => {
    try {
      const res = await api.get(`/stock/batches?page=0&size=10000`)
      setAllBatchList(res.data.data?.content || res.data.data || [])
    } catch {
      toast.error('Failed to load all batches for invoice summary')
    }
  }

  // Fetch Movements
  const loadMovements = async (page = 0) => {
    try {
      const res = await api.get(`/stock/movements?page=${page}&size=15&movementType=${selectedMovementType}`)
      setMovementList(res.data.data?.content || [])
      setMovementTotalPages(res.data.data?.totalPages || 0)
    } catch {
      toast.error('Failed to load stock movement ledger')
    }
  }

  // Fetch Adjustments Audit
  const loadAuditLogs = async (page = 0) => {
    try {
      const res = await api.get(`/stock/adjustments?page=${page}&size=15`)
      setAuditLogs(res.data.data?.content || [])
      setAuditTotalPages(res.data.data?.totalPages || 0)
    } catch {
      toast.error('Failed to load audit logs')
    }
  }

  // Fetch BI metrics
  const loadBI = async () => {
    setBiLoading(true)
    try {
      const hRes = await api.get('/stock/bi/health')
      const rRes = await api.get('/stock/bi/reorder')
      setBiHealth(hRes.data.data)
      setBiReorders(rRes.data.data || [])
    } catch {
      toast.error('Failed to load business intelligence metrics')
    } finally {
      setBiLoading(false)
    }
  }

  // Fetch Reports
  const loadReports = async (page = 0) => {
    setReportLoading(true)
    try {
      let endpoint = `/stock/reports/valuation?page=${page}&size=15`
      if (selectedReport === 'expiry') endpoint = `/stock/reports/expiry?page=${page}&size=15`
      else if (selectedReport === 'aging') endpoint = `/stock/reports/aging?page=${page}&size=15`
      else if (selectedReport === 'profitability') endpoint = `/stock/reports/profitability/category`

      const res = await api.get(endpoint)
      if (selectedReport === 'profitability') {
        setReportData(res.data.data || [])
        setReportTotalPages(1)
        setReportTotalElements(res.data.data?.length || 0)
      } else {
        setReportData(res.data.data?.content || [])
        setReportTotalPages(res.data.data?.totalPages || 0)
        setReportTotalElements(res.data.data?.totalElements || 0)
      }
    } catch {
      toast.error('Failed to generate report')
    } finally {
      setReportLoading(false)
    }
  }

  useEffect(() => {
    loadProducts()
    loadDashboard()
  }, [])

  // Debounce batch search term and reset page
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedBatchSearchTerm(batchSearchTerm)
      setBatchPage(0)
    }, 400)
    return () => clearTimeout(timer)
  }, [batchSearchTerm])

  // Trigger loads based on active tab
  useEffect(() => {
    if (activeTab === 'dashboard') loadDashboard()
    else if (activeTab === 'overview') loadInventory(invPage, searchTerm, selectedCategory, selectedStatus)
    else if (activeTab === 'batches' || activeTab === 'invoices') loadBatches(batchPage, debouncedBatchSearchTerm)
    else if (activeTab === 'movements') loadMovements(movementPage)
    else if (activeTab === 'audit' && isAdmin) loadAuditLogs(auditPage)
    else if (activeTab === 'bi') loadBI()
    else if (activeTab === 'reports') loadReports(reportPage)
  }, [activeTab, invPage, batchPage, movementPage, auditPage, reportPage, selectedReport, selectedMovementType, searchTerm, selectedCategory, selectedStatus, debouncedBatchSearchTerm])

  // Single Manual Receive Form Submit
  const handleReceiveSubmit = async (e) => {
    e.preventDefault()
    if (!manualForm.productId || !manualForm.supplierInvoiceNumber || !manualForm.supplierInvoiceDate) {
      toast.error('Please fill in all mandatory fields')
      return
    }

    setSavingStock(true)
    try {
      const payload = {
        ...manualForm,
        primaryReceived: manualForm.primaryReceived === '' ? 0 : Number(manualForm.primaryReceived),
        extraSecondaryReceived: manualForm.extraSecondaryReceived === '' ? 0 : Number(manualForm.extraSecondaryReceived),
        offerSecondaryReceived: manualForm.offerSecondaryReceived === '' ? 0 : Number(manualForm.offerSecondaryReceived),
        buyPriceWithoutTax: Number(manualForm.buyPriceWithoutTax)
      }

      await api.post('/stock/receive', payload)
      toast.success('Stock received and registered successfully!')
      setManualForm({
        productId: '',
        batchNumber: '',
        supplierInvoiceNumber: '',
        supplierInvoiceDate: '',
        stockReceivedDate: new Date().toISOString().split('T')[0],
        manufacturingDate: '',
        remarks: '',
        primaryReceived: '',
        extraSecondaryReceived: '',
        offerSecondaryReceived: '',
        buyPriceWithoutTax: '',
        expiryDate: '',
        supplierName: '',
        sellPricePrimary: '',
        sellPriceSecondary: '',
        gstPercent: '5',
        logAsExpense: true
      })
      loadInventory(0)
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to receive stock')
    } finally {
      setSavingStock(false)
    }
  }

  // Adjust Stock Submit
  const handleAdjustSubmit = async (e) => {
    e.preventDefault()
    if (!adjustReason.trim()) {
      toast.error('Adjustment reason is mandatory')
      return
    }
    setAdjusting(true)
    try {
      const product = products.find(p => p.id === adjustingBatch.productId)
      const ratio = product?.secondaryPerPrimary || 1
      const totalSec = (Number(adjustPrimary || 0) * ratio) + Number(adjustSecondary || 0)

      await api.put(`/stock/batches/${adjustingBatch.id}/adjust`, {
        newSecondaryRemaining: totalSec,
        newOfferSecondaryRemaining: adjustOffer === '' ? null : Number(adjustOffer),
        newBuyPriceWithoutTax: adjustBuyPrice === '' ? null : Number(adjustBuyPrice),
        reason: adjustReason
      })

      toast.success('Stock adjusted successfully!')
      setAdjustingBatch(null)
      setAdjustReason('')
      setAdjustPrimary('')
      setAdjustSecondary('')
      setAdjustOffer('')
      setAdjustBuyPrice('')
      loadBatches(batchPage)
      loadInventory(invPage)
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to adjust stock')
    } finally {
      setAdjusting(false)
    }
  }

  // Expiry Write-Off
  const handleWriteOff = async (batchId) => {
    try {
      await api.post(`/stock/batches/${batchId}/write-off-expiry`)
      toast.success('Expired stock written off successfully')
      setWriteOffTarget(null)
      loadBatches(batchPage)
      loadInventory(invPage)
    } catch {
      toast.error('Failed to write off expired stock')
    }
  }

  // AI Invoice Scanner Upload
  const handleScanInvoice = async (e) => {
    if (e) e.preventDefault()
    if (!scannerFile) { toast.error('Please select an invoice file first'); return }
    setScannerLoading(true)
    setScannerPreview([])
    setScannerInvoiceNumber('')
    setInvoiceAlreadyScanned(false)
    try {
      const formData = new FormData()
      formData.append('file', scannerFile)
      const res = await api.post(`/stock/parse-invoice?supplierName=${encodeURIComponent(scannerSupplier)}`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      const scanData = res.data.data || {}
      setScannerPreview(scanData.items || [])
      setScannerInvoiceNumber(scanData.invoiceNumber || '')
      setInvoiceAlreadyScanned(scanData.alreadyScanned || false)
      toast.success('Invoice scanned and items mapped successfully!')
    } catch (err) {
      toast.error(err.response?.data?.message || 'Invoice scanning failed. Please try again.')
    } finally {
      setScannerLoading(false)
    }
  }

  const checkDuplicate = async (supplier, invoiceNo) => {
    if (!supplier || !invoiceNo) {
      setInvoiceAlreadyScanned(false)
      return
    }
    try {
      const res = await api.get(`/stock/check-duplicate-invoice?supplierName=${encodeURIComponent(supplier)}&invoiceNumber=${encodeURIComponent(invoiceNo)}`)
      setInvoiceAlreadyScanned(res.data.data || false)
    } catch (err) {
      console.error("Failed to check duplicate status", err)
    }
  }

  const handleScannerRowProductChange = (index, newProductId) => {
    const updated = [...scannerPreview]
    const item = updated[index]
    const product = products.find(p => p.id === newProductId)
    
    if (product) {
      const ratio = product.secondaryPerPrimary || 1
      const secUnit = product.secondaryUnit ? product.secondaryUnit.toUpperCase() : 'LADI'
      let packPerSecondary = 1
      if (secUnit === 'LADI') {
        packPerSecondary = ratio > 0 ? Math.floor((item.packsPerCase || 1) / ratio) : 12
      }
      if (packPerSecondary <= 0) packPerSecondary = 1
      
      const totalPacks = item.invoiceCases * item.packsPerCase
      const totalSecondaryUnits = Math.floor(totalPacks / packPerSecondary)
      
      const primaryAdded = Math.floor(totalSecondaryUnits / ratio)
      const openBoxAdded = totalSecondaryUnits % ratio
      
      const buyPriceWithoutTax = (item.taxableValue && item.invoiceCases > 0)
        ? Number((item.taxableValue / item.invoiceCases).toFixed(2))
        : Number(item.buyPricePerPiece) * ratio * packPerSecondary
      
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
        buyPriceWithoutTax,
        gstPercent: product.gstPercent,
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
      toast.error('Some products are not mapped. Map them to database products before confirming.')
      return
    }
    
    setSavingStock(true)
    try {
      const payload = scannerPreview.map(item => ({
        productId: item.productId,
        batchNumber: item.batchNumber,
        supplierInvoiceNumber: scannerInvoiceNumber || null,
        supplierInvoiceDate: new Date().toISOString().split('T')[0], // default fallback
        stockReceivedDate: new Date().toISOString().split('T')[0],
        manufacturingDate: item.manufacturingDate || new Date().toISOString().split('T')[0],
        primaryReceived: item.primaryAdded,
        extraSecondaryReceived: item.openBoxAdded,
        offerSecondaryReceived: item.offerUnitsAdded || 0,
        buyPriceWithoutTax: item.buyPriceWithoutTax,
        expiryDate: item.expiryDate,
        supplierName: scannerSupplier,
        gstPercent: Number(item.gstPercent || 0),
        logAsExpense: true
      }))
      
      await api.post('/stock/receive-bulk', payload)
      toast.success('All scanner invoice items received in bulk successfully!')
      setScannerPreview([])
      setScannerFile(null)
      setScannerInvoiceNumber('')
      setInvoiceAlreadyScanned(false)
      loadInventory(0)
      setActiveTab('overview')
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to save scanned batches')
    } finally {
      setSavingStock(false)
    }
  }

  // Quick Add Product Form Handlers
  const openQuickProductModal = (index) => {
    const item = scannerPreview[index]
    if (!item) return

    setQuickProductIndex(index)
    
    const guessCategory = item.productName?.toLowerCase().includes('chip') ? 'CHIPS' : 'SNACKS'
    const guessSecUnit = (item.packsPerCase === 72 || item.packsPerCase === 216) ? 'PACK' : 'LADI'
    let guessSecPerPri = 20
    if (guessSecUnit === 'PACK') {
      guessSecPerPri = item.packsPerCase || 72
    } else {
      const mrpVal = Number(item.mrp || 0)
      const ladiSize = (mrpVal > 10) ? 10 : 12
      guessSecPerPri = item.packsPerCase ? Math.floor(item.packsPerCase / ladiSize) : 20
      if (guessSecPerPri <= 0) guessSecPerPri = 20
    }

    const knownSecondaryUnits = ['LADI', 'PACK', 'BOTTLE']
    const cleanItemSecUnit = item.secondaryUnit ? item.secondaryUnit.trim().toUpperCase() : ''
    const isKnown = knownSecondaryUnits.includes(cleanItemSecUnit)
    
    const initialSecUnit = isKnown ? cleanItemSecUnit : (cleanItemSecUnit ? 'OTHER' : guessSecUnit)
    const initialCustomSecUnit = (!isKnown && cleanItemSecUnit) ? cleanItemSecUnit : ''

    const buyPriceWithoutTaxVal = item.buyPriceWithoutTax !== undefined ? item.buyPriceWithoutTax.toString() : ''
    const gstPercentVal = item.gstPercent !== undefined ? item.gstPercent.toString() : '5'
    const buyPriceWithTaxVal = buyPriceWithoutTaxVal ? (Number(buyPriceWithoutTaxVal) * (1 + Number(gstPercentVal) / 100)).toFixed(2) : ''

    setQuickProductForm({
      name: item.productName || '',
      brand: "Haldiram's",
      category: guessCategory,
      otherCategoryDetail: '',
      gstPercent: gstPercentVal,
      cessPercent: '0',
      isCessApplicable: false,
      primaryUnit: (item.primaryUnit && ['BOX', 'CRATE'].includes(item.primaryUnit.toUpperCase())) ? item.primaryUnit.toUpperCase() : 'BOX',
      secondaryUnit: initialSecUnit,
      customSecondaryUnit: initialCustomSecUnit,
      secondaryPerPrimary: item.secondaryPerPrimary !== undefined ? item.secondaryPerPrimary.toString() : guessSecPerPri.toString(),
      canSellPrimary: true,
      canSellSecondary: true,
      buyPriceWithoutTax: buyPriceWithoutTaxVal,
      buyPriceWithTax: buyPriceWithTaxVal,
      sellPricePrimary: '',
      sellPriceSecondary: '',
      sellPricePrimaryExcl: '',
      sellPricePrimaryIncl: '',
      sellPriceSecondaryExcl: '',
      sellPriceSecondaryIncl: '',
      lowStockAlert: '10',
      lowStockUnit: 'SECONDARY'
    })
    setShowQuickProductModal(true)
  }

  const handleQuickPriceChange = (type, value) => {
    const gst = Number(quickProductForm.gstPercent || 0)
    const cess = Number(quickProductForm.cessPercent || 0)
    const taxRate = 1 + (gst + cess) / 100
    if (type === 'without') {
      const parsed = parseFloat(value)
      if (isNaN(parsed) || !value) {
        setQuickProductForm(f => ({ ...f, buyPriceWithoutTax: value, buyPriceWithTax: '' }))
      } else {
        const withTax = (parsed * taxRate).toFixed(2)
        setQuickProductForm(f => ({ ...f, buyPriceWithoutTax: value, buyPriceWithTax: withTax }))
      }
    } else {
      const parsed = parseFloat(value)
      if (isNaN(parsed) || !value) {
        setQuickProductForm(f => ({ ...f, buyPriceWithTax: value, buyPriceWithoutTax: '' }))
      } else {
        const withoutTax = (parsed / taxRate).toFixed(2)
        setQuickProductForm(f => ({ ...f, buyPriceWithTax: value, buyPriceWithoutTax: withoutTax }))
      }
    }
  }

  const handleQuickSellPriceChange = (unitType, fieldType, value) => {
    const gst = Number(quickProductForm.gstPercent || 0)
    const cess = Number(quickProductForm.cessPercent || 0)
    const taxRate = 1 + (gst + cess) / 100

    if (unitType === 'primary') {
      if (fieldType === 'excl') {
        const parsed = parseFloat(value)
        if (isNaN(parsed) || !value) {
          setQuickProductForm(f => ({ ...f, sellPricePrimaryExcl: value, sellPricePrimaryIncl: '' }))
        } else {
          const incl = (parsed * taxRate).toFixed(2)
          setQuickProductForm(f => ({ ...f, sellPricePrimaryExcl: value, sellPricePrimaryIncl: incl }))
        }
      } else {
        const parsed = parseFloat(value)
        if (isNaN(parsed) || !value) {
          setQuickProductForm(f => ({ ...f, sellPricePrimaryIncl: value, sellPricePrimaryExcl: '' }))
        } else {
          const excl = (parsed / taxRate).toFixed(2)
          setQuickProductForm(f => ({ ...f, sellPricePrimaryIncl: value, sellPricePrimaryExcl: excl }))
        }
      }
    } else {
      if (fieldType === 'excl') {
        const parsed = parseFloat(value)
        if (isNaN(parsed) || !value) {
          setQuickProductForm(f => ({ ...f, sellPriceSecondaryExcl: value, sellPriceSecondaryIncl: '' }))
        } else {
          const incl = (parsed * taxRate).toFixed(2)
          setQuickProductForm(f => ({ ...f, sellPriceSecondaryExcl: value, sellPriceSecondaryIncl: incl }))
        }
      } else {
        const parsed = parseFloat(value)
        if (isNaN(parsed) || !value) {
          setQuickProductForm(f => ({ ...f, sellPriceSecondaryIncl: value, sellPriceSecondaryExcl: '' }))
        } else {
          const excl = (parsed / taxRate).toFixed(2)
          setQuickProductForm(f => ({ ...f, sellPriceSecondaryIncl: value, sellPriceSecondaryExcl: excl }))
        }
      }
    }
  }

  const handleQuickGstChange = (gstValue) => {
    const gst = Number(gstValue || 0)
    const cess = Number(quickProductForm.cessPercent || 0)
    const taxRate = 1 + (gst + cess) / 100
    const withoutTax = parseFloat(quickProductForm.buyPriceWithoutTax)
    let newBuyPriceWithTax = quickProductForm.buyPriceWithTax
    if (!isNaN(withoutTax)) {
      newBuyPriceWithTax = (withoutTax * taxRate).toFixed(2)
    }

    const sellPriIncl = parseFloat(quickProductForm.sellPricePrimaryIncl)
    const sellPriExcl = !isNaN(sellPriIncl) ? (sellPriIncl / taxRate).toFixed(2) : ''

    const sellSecIncl = parseFloat(quickProductForm.sellPriceSecondaryIncl)
    const sellSecExcl = !isNaN(sellSecIncl) ? (sellSecIncl / taxRate).toFixed(2) : ''

    setQuickProductForm(f => ({
      ...f,
      gstPercent: gstValue,
      buyPriceWithTax: newBuyPriceWithTax,
      sellPricePrimaryExcl: sellPriExcl,
      sellPriceSecondaryExcl: sellSecExcl
    }))
  }

  const handleQuickProductSubmit = async (e) => {
    e.preventDefault()
    setSavingStock(true)
    try {
      const payload = {
        name: quickProductForm.name,
        brand: quickProductForm.brand,
        category: quickProductForm.category,
        otherCategoryDetail: quickProductForm.category === 'OTHER' ? quickProductForm.otherCategoryDetail : '',
        gstPercent: Number(quickProductForm.gstPercent || 0),
        cessPercent: quickProductForm.isCessApplicable ? Number(quickProductForm.cessPercent || 0) : 0,
        primaryUnit: quickProductForm.primaryUnit,
        secondaryUnit: quickProductForm.secondaryUnit === 'OTHER' ? (quickProductForm.customSecondaryUnit || 'PACK').toUpperCase().trim() : quickProductForm.secondaryUnit,
        secondaryPerPrimary: Number(quickProductForm.secondaryPerPrimary || 1),
        canSellPrimary: quickProductForm.canSellPrimary ?? true,
        canSellSecondary: quickProductForm.canSellSecondary ?? true,
        buyPriceWithoutTax: quickProductForm.buyPriceWithoutTax !== '' ? Number(quickProductForm.buyPriceWithoutTax) : null,
        buyPriceWithTax: quickProductForm.buyPriceWithTax !== '' ? Number(quickProductForm.buyPriceWithTax) : null,
        sellPricePrimary: quickProductForm.sellPricePrimaryIncl !== '' ? Number(quickProductForm.sellPricePrimaryIncl) : 0,
        sellPriceSecondary: quickProductForm.sellPriceSecondaryIncl !== '' ? Number(quickProductForm.sellPriceSecondaryIncl) : 0,
        lowStockAlert: quickProductForm.lowStockAlert !== '' ? Number(quickProductForm.lowStockAlert) : 10,
        lowStockUnit: quickProductForm.lowStockUnit || 'SECONDARY'
      }

      const res = await api.post('/products', payload)
      const newProduct = res.data.data

      setProducts(prev => [...prev, newProduct])

      if (quickProductIndex !== null) {
        setScannerPreview(prev => {
          const updated = [...prev]
          const item = updated[quickProductIndex]
          if (!item) return prev
          const ratio = newProduct.secondaryPerPrimary || 1
          const secUnit = newProduct.secondaryUnit ? newProduct.secondaryUnit.toUpperCase() : 'LADI'
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
          const buyPriceWithoutTax = (item.taxableValue && item.invoiceCases > 0)
            ? Number((item.taxableValue / item.invoiceCases).toFixed(2))
            : (buyPricePerPiece > 0
              ? Number((buyPricePerPiece * ratio * packPerSecondary).toFixed(2))
              : Number(item.buyPriceWithoutTax || 0))
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
              const secUnit = matchedProduct.secondaryUnit ? matchedProduct.secondaryUnit.toUpperCase() : 'LADI'
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
              const buyPriceWithoutTax = (item.taxableValue && item.invoiceCases > 0)
                ? Number((item.taxableValue / item.invoiceCases).toFixed(2))
                : (buyPricePerPiece > 0
                  ? Number((buyPricePerPiece * ratio * packPerSecondary).toFixed(2))
                  : Number(item.buyPriceWithoutTax || 0))
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
      setSavingStock(false)
    }
  }

  // Manual Bulk Receive Operations
  const handleBulkRowChange = (index, field, value) => {
    const updated = [...bulkRows]
    updated[index][field] = value

    if (field === 'productId') {
      const p = products.find(prod => prod.id === value)
      if (p) {
        updated[index].gstPercent = (p.gstPercent || 5).toString()
      }
    }
    setBulkRows(updated)
  }

  const addBulkRow = () => {
    setBulkRows(prev => [...prev, { productId: '', batchNumber: '', manufacturingDate: '', expiryDate: '', primaryReceived: '', extraSecondaryReceived: '', offerSecondaryReceived: '', buyPriceWithoutTax: '', gstPercent: '5' }])
  }

  const removeBulkRow = (index) => {
    if (bulkRows.length === 1) {
      toast.warning('Bulk entry requires at least 1 item row')
      return
    }
    setBulkRows(prev => prev.filter((_, i) => i !== index))
  }

  const handleBulkReceiveSubmit = async (e) => {
    e.preventDefault()
    if (!bulkSupplierName || !bulkInvoiceNumber || !bulkInvoiceDate) {
      toast.error('Global invoice fields (Supplier, Invoice Date, Invoice Number) are mandatory')
      return
    }
    
    const invalidRow = bulkRows.find(r => !r.productId || !r.batchNumber || !r.expiryDate || !r.buyPriceWithoutTax)
    if (invalidRow) {
      toast.error('Fill in Product, Batch No, Expiry Date, and Buy Price for all rows')
      return
    }

    setSavingStock(true)
    try {
      const payload = bulkRows.map(row => ({
        productId: row.productId,
        batchNumber: row.batchNumber,
        supplierInvoiceNumber: bulkInvoiceNumber,
        supplierInvoiceDate: bulkInvoiceDate,
        stockReceivedDate: bulkReceivedDate,
        manufacturingDate: row.manufacturingDate || null,
        primaryReceived: row.primaryReceived === '' ? 0 : Number(row.primaryReceived),
        extraSecondaryReceived: row.extraSecondaryReceived === '' ? 0 : Number(row.extraSecondaryReceived),
        offerSecondaryReceived: row.offerSecondaryReceived === '' ? 0 : Number(row.offerSecondaryReceived),
        buyPriceWithoutTax: Number(row.buyPriceWithoutTax),
        expiryDate: row.expiryDate,
        supplierName: bulkSupplierName,
        gstPercent: Number(row.gstPercent || 0),
        remarks: bulkRemarks || null,
        logAsExpense: true
      }))

      await api.post('/stock/receive-bulk', payload)
      toast.success('Manual bulk stock batches registered successfully!')
      setBulkRows([{ productId: '', batchNumber: '', manufacturingDate: '', expiryDate: '', primaryReceived: '', extraSecondaryReceived: '', offerSecondaryReceived: '', buyPriceWithoutTax: '', gstPercent: '5' }])
      setBulkRemarks('')
      setBulkInvoiceNumber('')
      loadInventory(0)
      setActiveTab('overview')
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to register bulk stock batches')
    } finally {
      setSavingStock(false)
    }
  }

  // Totals Computations
  const getBulkSummary = () => {
    let totalTaxable = 0;
    let totalGst = 0;
    let totalNet = 0;
    bulkRows.forEach(row => {
      const product = products.find(p => p.id === row.productId);
      const ratio = product?.secondaryPerPrimary || 1;
      const pAdded = row.primaryReceived === '' ? 0 : (Number(row.primaryReceived) || 0);
      const oAdded = row.extraSecondaryReceived === '' ? 0 : (Number(row.extraSecondaryReceived) || 0);
      const buyPrice = Number(row.buyPriceWithoutTax || 0);
      const gst = Number(row.gstPercent || 0);
      
      const rowTaxable = (pAdded * buyPrice) + (oAdded * (buyPrice / ratio));
      const rowGst = rowTaxable * (gst / 100);
      const rowNet = rowTaxable + rowGst;
      
      totalTaxable += rowTaxable;
      totalGst += rowGst;
      totalNet += rowNet;
    });
    return { totalTaxable, totalGst, totalNet };
  };

  const getScannerSummary = () => {
    let totalTaxable = 0;
    let totalGst = 0;
    let totalNet = 0;
    scannerPreview.forEach(item => {
      const ratio = item.secondaryPerPrimary || 1;
      const pAdded = item.primaryAdded === '' ? 0 : (Number(item.primaryAdded) || 0);
      const oAdded = item.openBoxAdded === '' ? 0 : (Number(item.openBoxAdded) || 0);
      const buyPrice = Number(item.buyPriceWithoutTax || 0);
      const gst = Number(item.gstPercent || 0);
      
      const rowTaxable = (pAdded * buyPrice) + (oAdded * (buyPrice / ratio));
      const rowGst = rowTaxable * (gst / 100);
      const rowNet = rowTaxable + rowGst;
      
      totalTaxable += rowTaxable;
      totalGst += rowGst;
      totalNet += rowNet;
    });
    return { totalTaxable, totalGst, totalNet };
  };

  // Export report CSV
  const exportToCSV = async () => {
    if (exporting) return
    setExporting(true)
    try {
      toast.info('Fetching full report data for export...')
      let endpoint = `/stock/reports/valuation/export`
      if (selectedReport === 'expiry') endpoint = `/stock/reports/expiry/export`
      else if (selectedReport === 'aging') endpoint = `/stock/reports/aging/export`
      else if (selectedReport === 'profitability') endpoint = `/stock/reports/profitability/category`

      const res = await api.get(endpoint)
      const fullData = res.data.data || []

      if (fullData.length === 0) {
        toast.warning('No data to export')
        return
      }

      const escape = (val) => val == null ? '' : String(val).replace(/"/g, '""')

      const rows = []
      if (selectedReport === 'valuation') {
        rows.push('Product,Category,Brand,Stock,Avg Cost,Selling Price,Value,Status')
        fullData.forEach(row => {
          rows.push(`"${escape(row.productName)}","${escape(row.category)}","${escape(row.brand)}",${row.currentStock},${row.avgCost},${row.sellingPrice},${row.inventoryValue},"${escape(row.status)}"`)
        })
      } else if (selectedReport === 'expiry') {
        rows.push('Batch,Product,Expiry Date,Remaining,Cost Value,Days to Expiry,Risk')
        fullData.forEach(row => {
          rows.push(`"${escape(row.batchNumber)}","${escape(row.productName)}","${escape(row.expiryDate)}",${row.remainingQty},${row.costValue},${row.daysToExpiry},"${escape(row.riskBucket)}"`)
        })
      } else if (selectedReport === 'aging') {
        rows.push('Batch,Product,Received Date,Remaining,Age (Days),Bucket')
        fullData.forEach(row => {
          rows.push(`"${escape(row.batchNumber)}","${escape(row.productName)}","${escape(row.stockReceivedDate)}",${row.remainingQty},${row.ageDays},"${escape(row.ageBucket)}"`)
        })
      } else if (selectedReport === 'profitability') {
        rows.push('Category,Cost Value,Selling Value,Profit Potential,Margin %')
        fullData.forEach(row => {
          rows.push(`"${escape(row.categoryName)}",${row.costValue},${row.sellingValue},${row.profitPotential},${row.marginPercent}`)
        })
      }

      const csvContent = 'data:text/csv;charset=utf-8,\uFEFF' + rows.join('\n')
      const encodedUri = encodeURI(csvContent)
      const link = document.createElement('a')
      link.setAttribute('href', encodedUri)
      link.setAttribute('download', `Stock_Report_${selectedReport}_${new Date().toISOString().split('T')[0]}.csv`)
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      toast.success('Report exported successfully!')
    } catch (err) {
      toast.error('Failed to export report CSV')
      console.error(err)
    } finally {
      setExporting(false)
    }
  }

  // Server-side filtered inventory
  const filteredInventory = inventoryList

  // Dynamic Chart tooltip style
  const chartTooltipStyle = {
    backgroundColor: 'var(--color-surface)',
    borderColor: 'var(--color-border)',
    color: 'var(--color-text)',
    borderRadius: 'var(--radius-md)'
  }

  return (
    <div className="min-h-screen bg-[var(--color-bg)] text-slate-900 dark:text-slate-100 p-4 md:p-8 font-sans transition-colors duration-300">
      {/* Header and Branding */}
      <div className="flex flex-col xl:flex-row justify-between items-start xl:items-center mb-8 gap-4">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight bg-gradient-to-r from-indigo-500 via-sky-500 to-emerald-500 bg-clip-text text-transparent">
            Stock Management Redesign
          </h1>
          <p className="text-slate-500 dark:text-slate-400 mt-1">Enterprise FMCG Wholesaler, Retailer, & BI Suite</p>
        </div>

        {/* Tab Buttons */}
        <div className="flex flex-wrap gap-1 bg-slate-200/80 dark:bg-slate-900/80 p-1.5 rounded-xl border border-slate-300 dark:border-slate-800 backdrop-blur">
          {[
            { id: 'dashboard', label: 'Dashboard', icon: BarChart2 },
            { id: 'overview', label: 'Inventory', icon: Package },
            { id: 'receive', label: 'Receive Stock', icon: Plus },
            { id: 'batches', label: 'Batches', icon: Clock },
            { id: 'movements', label: 'Movements', icon: ArrowRightLeft },
            { id: 'bi', label: 'BI Analytics', icon: Award },
            { id: 'reports', label: 'Reports', icon: FileText },
            ...(isAdmin ? [{ id: 'audit', label: 'Audit Logs', icon: History }] : [])
          ].map(tab => (
            <button
              key={tab.id}
              onClick={() => {
                setActiveTab(tab.id)
                // Clear previews when switching tabs
                setScannerPreview([])
                setScannerFile(null)
                // Reset batch sub-view and pages when going to Batches tab
                if (tab.id === 'batches') { 
                  setBatchView('list'); 
                  setBatchPage(0);
                  setInvoicePage(0);
                }
              }}
              className={`flex items-center gap-2 px-3 py-1.5 md:px-4 md:py-2 rounded-lg font-medium text-xs md:text-sm transition-all duration-300 ${
                activeTab === tab.id
                  ? 'bg-gradient-to-r from-indigo-600 to-sky-600 text-white shadow-md'
                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-250 hover:bg-slate-300/40 dark:hover:bg-slate-800/40'
              }`}
            >
              <tab.icon className="w-4 h-4" />
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      <AnimatePresence mode="wait">
        
        {/* OCR PREVIEW AND VERIFICATION VIEW OVERLAY */}
        {activeTab === 'receive' && scannerPreview.length > 0 && !scannerLoading && (
          <motion.div
            initial={{ opacity: 0, scale: 0.98 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.98 }}
            className="space-y-6"
          >
            {invoiceAlreadyScanned && (
              <div className="p-4 bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20 rounded-xl flex items-center gap-2 text-sm font-semibold">
                <AlertTriangle className="w-5 h-5 flex-shrink-0" />
                <span>
                  This invoice (<strong>{scannerInvoiceNumber}</strong>) from supplier <strong>{scannerSupplier}</strong> has already been scanned.
                </span>
              </div>
            )}

            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-6 rounded-2xl shadow-xl space-y-6">
              <div className="flex justify-between items-center border-b border-slate-200 dark:border-slate-800 pb-4">
                <div>
                  <h3 className="text-xl font-bold">Verify Scanned Invoice Mapping</h3>
                  <p className="text-sm text-slate-500 dark:text-slate-400">Database products mapping & inline calculations verification panel.</p>
                </div>
                <button
                  type="button"
                  onClick={() => { setScannerPreview([]); setScannerFile(null); }}
                  className="p-2 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-850 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
                >
                  <X className="w-6 h-6" />
                </button>
              </div>

              {/* Supplier and Invoice global fields */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="flex flex-col gap-1">
                  <label className="text-xs font-semibold text-slate-500 dark:text-slate-400">Supplier Name *</label>
                  <input
                    className="w-full px-4 py-2 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-xl text-slate-900 dark:text-slate-100 focus:ring-2 focus:ring-indigo-500 text-sm"
                    value={scannerSupplier}
                    onChange={e => {
                      setScannerSupplier(e.target.value);
                      checkDuplicate(e.target.value, scannerInvoiceNumber);
                    }}
                    required
                  />
                </div>
                <div className="flex flex-col gap-1">
                  <label className="text-xs font-semibold text-slate-500 dark:text-slate-400">Invoice Number</label>
                  <input
                    className="w-full px-4 py-2 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-xl text-slate-900 dark:text-slate-100 focus:ring-2 focus:ring-indigo-500 text-sm"
                    value={scannerInvoiceNumber}
                    onChange={e => {
                      setScannerInvoiceNumber(e.target.value);
                      checkDuplicate(scannerSupplier, e.target.value);
                    }}
                    placeholder="e.g. SA-0820"
                  />
                </div>
              </div>

              {/* Items grid */}
              <div className="overflow-x-auto rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/40">
                <table className="w-full text-left border-collapse text-xs md:text-sm">
                  <thead>
                    <tr className="bg-slate-100 dark:bg-slate-950 border-b border-slate-200 dark:border-slate-800 text-slate-600 dark:text-slate-350 font-semibold uppercase">
                      <th className="p-3 min-w-[200px]">Invoice Item</th>
                      <th className="p-3 min-w-[100px]">Category</th>
                      <th className="p-3 min-w-[100px]">Qty (Primary)</th>
                      <th className="p-3 min-w-[100px]">Qty (Secondary)</th>
                      <th className="p-3 min-w-[100px]">Open Box</th>
                      <th className="p-3 min-w-[100px]">Offer units</th>
                      <th className="p-3 min-w-[100px]">Batch No</th>
                      <th className="p-3 min-w-[120px]">Expiry Date</th>
                      <th className="p-3 min-w-[100px]">Buy (BOX)</th>
                      <th className="p-3 min-w-[80px]">GST %</th>
                      <th className="p-3 min-w-[120px]">Total (Tax Incl.)</th>
                      <th className="p-3 min-w-[100px]">Status</th>
                      <th className="p-3 min-w-[200px]">Mapped DB Product</th>
                      <th className="p-3 text-center">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                    {scannerPreview.map((item, index) => (
                      <tr key={index} className="hover:bg-slate-100/50 dark:hover:bg-slate-800/20">
                        <td className="p-3">
                          {editingNameIndex === index ? (
                            <textarea
                              className="w-full px-2 py-1 bg-slate-100 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 rounded-lg text-slate-900 dark:text-slate-100 focus:ring-1 focus:ring-indigo-500 text-xs resize-none min-h-[50px]"
                              value={item.productName || ''}
                              autoFocus
                              onChange={e => {
                                const updated = [...scannerPreview];
                                updated[index].productName = e.target.value;
                                setScannerPreview(updated);
                              }}
                              onBlur={() => setEditingNameIndex(null)}
                              onKeyDown={e => {
                                if (e.key === 'Enter') {
                                  e.preventDefault();
                                  setEditingNameIndex(null);
                                }
                              }}
                            />
                          ) : (
                            <div
                              onClick={() => setEditingNameIndex(index)}
                              className="cursor-pointer px-2 py-1 rounded border border-dashed border-transparent hover:border-slate-300 dark:hover:border-slate-700 font-semibold"
                              title="Click to edit parsed description"
                            >
                              {item.productName}
                            </div>
                          )}
                        </td>
                        <td className="p-3">
                          <select
                            className="px-2 py-1 bg-white dark:bg-slate-900 border border-slate-300 dark:border-slate-700 rounded text-xs text-slate-800 dark:text-slate-200"
                            value={item.category || 'SNACKS'}
                            onChange={e => {
                              const updated = [...scannerPreview];
                              updated[index].category = e.target.value;
                              setScannerPreview(updated);
                            }}
                          >
                            {CATEGORIES.map(cat => (
                              <option key={cat} value={cat}>{cat}</option>
                            ))}
                          </select>
                        </td>
                        <td className="p-3">
                          <div className="flex items-center gap-1.5">
                            <input
                              type="number"
                              className="w-16 px-2 py-1 bg-white dark:bg-slate-900 border border-slate-300 dark:border-slate-700 rounded text-xs text-slate-800 dark:text-slate-200"
                              value={item.primaryAdded ?? ''}
                              min="0"
                              onChange={e => {
                                const val = e.target.value;
                                const updated = [...scannerPreview];
                                updated[index].primaryAdded = val;
                                const ratio = item.secondaryPerPrimary || 1;
                                const pAdded = val === '' ? 0 : (parseInt(val) || 0);
                                const oAdded = item.openBoxAdded === '' ? 0 : (parseInt(item.openBoxAdded) || 0);
                                updated[index].secondaryAdded = (pAdded * ratio) + oAdded;
                                setScannerPreview(updated);
                              }}
                            />
                            <span className="text-slate-400 text-xxs font-medium">{item.primaryUnit || 'BOX'}</span>
                          </div>
                        </td>
                        <td className="p-3 font-semibold text-slate-600 dark:text-slate-400">
                          {item.secondaryAdded} {item.secondaryUnit || 'Units'}
                        </td>
                        <td className="p-3">
                          <div className="flex items-center gap-1.5">
                            <input
                              type="number"
                              className="w-14 px-2 py-1 bg-white dark:bg-slate-900 border border-slate-300 dark:border-slate-700 rounded text-xs text-slate-800 dark:text-slate-200"
                              value={item.openBoxAdded ?? ''}
                              min="0"
                              onChange={e => {
                                const val = e.target.value;
                                const updated = [...scannerPreview];
                                updated[index].openBoxAdded = val;
                                const ratio = item.secondaryPerPrimary || 1;
                                const pAdded = item.primaryAdded === '' ? 0 : (parseInt(item.primaryAdded) || 0);
                                const oAdded = val === '' ? 0 : (parseInt(val) || 0);
                                updated[index].secondaryAdded = (pAdded * ratio) + oAdded;
                                setScannerPreview(updated);
                              }}
                            />
                            <span className="text-slate-400 text-xxs font-medium">loose</span>
                          </div>
                        </td>
                        <td className="p-3">
                          <div className="flex items-center gap-1.5">
                            <input
                              type="number"
                              className="w-14 px-2 py-1 bg-white dark:bg-slate-900 border border-slate-300 dark:border-slate-700 rounded text-xs text-slate-800 dark:text-slate-200"
                              value={item.offerUnitsAdded ?? ''}
                              min="0"
                              onChange={e => {
                                const val = e.target.value;
                                const updated = [...scannerPreview];
                                updated[index].offerUnitsAdded = val === '' ? '' : (parseInt(val) || 0);
                                setScannerPreview(updated);
                              }}
                            />
                            <span className="text-slate-400 text-xxs font-medium">{item.secondaryUnit || 'LADI'}</span>
                          </div>
                        </td>
                        <td className="p-3">
                          <input
                            type="text"
                            className="w-20 px-2 py-1 bg-white dark:bg-slate-900 border border-slate-300 dark:border-slate-700 rounded text-xs text-slate-800 dark:text-slate-200 font-mono uppercase"
                            value={item.batchNumber || ''}
                            onChange={e => {
                              const val = e.target.value.toUpperCase();
                              const updated = [...scannerPreview];
                              updated[index].batchNumber = val;
                              setScannerPreview(updated);
                            }}
                          />
                        </td>
                        <td className="p-3">
                          <input
                            type="date"
                            className="w-28 px-2 py-1 bg-white dark:bg-slate-900 border border-slate-300 dark:border-slate-700 rounded text-xs text-slate-800 dark:text-slate-200"
                            value={item.expiryDate || ''}
                            onChange={e => {
                              const updated = [...scannerPreview];
                              updated[index].expiryDate = e.target.value;
                              setScannerPreview(updated);
                            }}
                          />
                        </td>
                        <td className="p-3">
                          <div className="flex items-center gap-1">
                            <span className="text-slate-400">₹</span>
                            <input
                              type="number"
                              step="0.01"
                              className="w-20 px-2 py-1 bg-white dark:bg-slate-900 border border-slate-300 dark:border-slate-700 rounded text-xs text-slate-800 dark:text-slate-200 font-semibold"
                              value={item.buyPriceWithoutTax ?? ''}
                              min="0"
                              onChange={e => {
                                const val = e.target.value;
                                const updated = [...scannerPreview];
                                updated[index].buyPriceWithoutTax = val;
                                setScannerPreview(updated);
                              }}
                            />
                          </div>
                        </td>
                        <td className="p-3">
                          <div className="flex items-center gap-1">
                            <input
                              type="number"
                              step="0.01"
                              className="w-14 px-2 py-1 bg-white dark:bg-slate-900 border border-slate-300 dark:border-slate-700 rounded text-xs text-slate-800 dark:text-slate-200 font-semibold"
                              value={item.gstPercent ?? '5'}
                              min="0"
                              onChange={e => {
                                const val = e.target.value;
                                const updated = [...scannerPreview];
                                updated[index].gstPercent = val;
                                setScannerPreview(updated);
                              }}
                            />
                            <span className="text-slate-400">%</span>
                          </div>
                        </td>
                        <td className="p-3 font-bold text-slate-800 dark:text-slate-200">
                          {(() => {
                            const ratio = item.secondaryPerPrimary || 1;
                            const pAdded = item.primaryAdded === '' ? 0 : (Number(item.primaryAdded) || 0);
                            const oAdded = item.openBoxAdded === '' ? 0 : (Number(item.openBoxAdded) || 0);
                            const buyPrice = Number(item.buyPriceWithoutTax || 0);
                            const gst = Number(item.gstPercent || 0);
                            
                            const valWithoutTax = (pAdded * buyPrice) + (oAdded * (buyPrice / ratio));
                            const valWithTax = valWithoutTax * (1 + gst / 100);
                            return `₹${valWithTax.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
                          })()}
                        </td>
                        <td className="p-3">
                          {item.duplicateBatch ? (
                            <span className="px-2 py-0.5 rounded-full text-xxs font-bold bg-rose-500/10 text-rose-500 border border-rose-500/20">
                              ⚠️ Duplicate
                            </span>
                          ) : item.newProduct ? (
                            <div className="flex flex-col items-start gap-1">
                              <span className="px-2 py-0.5 rounded-full text-xxs font-bold bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20">
                                🆕 New Product
                              </span>
                              <button
                                type="button"
                                className="text-[10px] text-indigo-500 dark:text-indigo-400 underline hover:text-indigo-600 mt-1 font-semibold"
                                onClick={() => openQuickProductModal(index)}
                              >
                                + Create DB SKU
                              </button>
                            </div>
                          ) : (
                            <span className="px-2 py-0.5 rounded-full text-xxs font-bold bg-emerald-500/10 text-emerald-500 border border-emerald-500/20">
                              OK Mapped
                            </span>
                          )}
                        </td>
                        <td className="p-3">
                          <SearchSelect
                            options={products.filter(p => p.active !== false).map(p => ({ value: p.id, label: `${p.name} (${p.brand})` }))}
                            value={item.productId || ''}
                            onChange={(val) => handleScannerRowProductChange(index, val)}
                            placeholder="Map product manually..."
                          />
                        </td>
                        <td className="p-3 text-center">
                          <button
                            type="button"
                            onClick={() => {
                              const updated = [...scannerPreview]
                              updated.splice(index, 1)
                              setScannerPreview(updated)
                              toast.info('Row excluded from list')
                            }}
                            className="p-1 text-rose-500 hover:text-rose-600 hover:bg-rose-500/10 rounded transition"
                            title="Remove row"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* Scanner Grand Summary Cards */}
              {(() => {
                const { totalTaxable, totalGst, totalNet } = getScannerSummary();
                return (
                  <div className="bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 p-5 rounded-2xl flex flex-wrap justify-between items-center gap-4">
                    <div>
                      <h4 className="font-bold text-sm md:text-base">Scanner Invoice Summary</h4>
                      <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">Please confirm the amounts against the printed invoice copy.</p>
                    </div>
                    <div className="flex gap-6 flex-wrap">
                      <div className="flex flex-col items-end">
                        <span className="text-xxs font-bold text-slate-400 uppercase tracking-wider">Taxable Value</span>
                        <span className="text-sm md:text-base font-bold text-slate-700 dark:text-slate-200">
                          ₹{totalTaxable.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                        </span>
                      </div>
                      <div className="flex flex-col items-end">
                        <span className="text-xxs font-bold text-slate-400 uppercase tracking-wider">GST Tax</span>
                        <span className="text-sm md:text-base font-bold text-slate-700 dark:text-slate-200">
                          ₹{totalGst.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                        </span>
                      </div>
                      <div className="flex flex-col items-end border-l border-slate-200 dark:border-slate-800 pl-6">
                        <span className="text-xxs font-extrabold text-slate-400 uppercase tracking-wider">Grand Total (Incl. Tax)</span>
                        <span className="text-xl md:text-2xl font-black text-emerald-500">
                          ₹{totalNet.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                        </span>
                      </div>
                    </div>
                  </div>
                );
              })()}

              <div className="flex justify-end gap-3 pt-4 border-t border-slate-200 dark:border-slate-800">
                <button
                  type="button"
                  className="px-5 py-2.5 bg-slate-200 dark:bg-slate-800 hover:bg-slate-300 dark:hover:bg-slate-700 rounded-xl font-bold text-slate-700 dark:text-slate-300 transition"
                  onClick={() => { setScannerPreview([]); setScannerFile(null); }}
                  disabled={savingStock}
                >
                  Cancel Scan
                </button>
                <button
                  onClick={handleScannerSubmit}
                  type="button"
                  className="px-6 py-2.5 bg-gradient-to-r from-indigo-600 to-sky-600 hover:from-indigo-500 hover:to-sky-500 text-white rounded-xl font-extrabold shadow-md disabled:opacity-50 transition"
                  disabled={savingStock}
                >
                  {savingStock ? 'Saving Bulk Batches...' : 'Confirm & Save to Stock'}
                </button>
              </div>
            </div>
          </motion.div>
        )}

        {/* 1. EXECUTIVE DASHBOARD TAB */}
        {activeTab === 'dashboard' && (
          <motion.div
            initial={{ opacity: 0, y: 15 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -15 }}
            className="space-y-8"
          >
            {dashboardLoading || !dashboardData ? (
              <div className="py-12 text-center text-slate-500">Loading Executive Dashboard summary...</div>
            ) : (
              <>
                {/* Metrics Cards */}
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                  {/* Inventory Cost Value — Incl. GST as primary, Excl. GST as secondary */}
                  <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-6 rounded-2xl shadow-sm relative overflow-hidden group hover:border-slate-300 dark:hover:border-slate-700 transition duration-300">
                    <div className="absolute top-0 left-0 w-1.5 h-full bg-gradient-to-b from-indigo-500 to-sky-500"></div>
                    <span className="text-slate-400 text-sm font-semibold">Inventory Cost Value</span>
                    <h3 className="text-2xl font-black mt-2 tracking-tight text-indigo-600 dark:text-indigo-400">
                      ₹{Number(dashboardData.totalCostValueWithTax || dashboardData.totalCostValue).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                    </h3>
                    <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">Incl. GST (total purchase value)</p>
                    <p className="text-xs text-slate-400 dark:text-slate-500 mt-1">
                      ₹{Number(dashboardData.totalCostValue).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                      <span className="ml-1">Excl. GST</span>
                    </p>
                  </div>

                  {[
                    { label: 'MRP Valuation', val: `₹${Number(dashboardData.totalMrpValue).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`, desc: 'Total retail price potential', color: 'text-sky-600 dark:text-sky-400' },
                    { label: 'Profit Potential', val: `₹${Number(dashboardData.expectedProfit).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`, desc: 'Expected gross margin profits', color: 'text-emerald-600 dark:text-emerald-400' },
                    { label: 'Inventory Health Score', val: `${dashboardData.healthScore}/100`, desc: `Score Status: ${dashboardData.healthClassification}`, color: 'text-rose-600 dark:text-rose-400' }
                  ].map((card, i) => (
                    <div key={i} className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-6 rounded-2xl shadow-sm relative overflow-hidden group hover:border-slate-300 dark:hover:border-slate-700 transition duration-300">
                      <div className="absolute top-0 left-0 w-1.5 h-full bg-gradient-to-b from-indigo-500 to-sky-500"></div>
                      <span className="text-slate-400 text-sm font-semibold">{card.label}</span>
                      <h3 className={`text-2xl font-black mt-2 tracking-tight ${card.color}`}>{card.val}</h3>
                      <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">{card.desc}</p>
                    </div>
                  ))}
                </div>

                {/* Sub KPI cards */}
                <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
                  {[
                    { label: 'Total SKUs', count: dashboardData.totalProducts, bg: 'bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 border-indigo-500/20' },
                    { label: 'Active SKUs', count: dashboardData.activeSkus, bg: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20' },
                    { label: 'Active Batches', count: dashboardData.totalBatches, bg: 'bg-sky-500/10 text-sky-600 dark:text-sky-400 border-sky-500/20' },
                    { label: 'Low Stock SKU', count: dashboardData.lowStockCount, bg: 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/20' },
                    { label: 'Out of Stock SKU', count: dashboardData.outOfStockCount, bg: 'bg-red-500/10 text-red-600 dark:text-red-400 border-red-500/20' },
                    { label: 'Expiring in 30 Days', count: dashboardData.expiringCount, bg: 'bg-orange-500/10 text-orange-600 dark:text-orange-400 border-orange-500/20' }
                  ].map((sk, i) => (
                    <div key={i} className="bg-white dark:bg-slate-900/60 border border-slate-200 dark:border-slate-800/80 p-4 rounded-xl text-center border-dashed">
                      <span className="text-xs text-slate-500 dark:text-slate-400 block mb-1">{sk.label}</span>
                      <span className={`inline-block px-3 py-1 rounded-full text-lg font-extrabold border ${sk.bg}`}>{sk.count}</span>
                    </div>
                  ))}
                </div>

                {/* BI Forecast charts and Recent logs widgets */}
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
                  {/* Aging Chart */}
                  <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-6 rounded-2xl shadow-sm">
                    <h3 className="text-lg font-bold mb-4">Stock Aging Distribution</h3>
                    <div className="h-64">
                      <ResponsiveContainer width="100%" height="100%">
                        <BarChart data={[
                          { name: '0-30 Days', value: 45 },
                          { name: '31-60 Days', value: 25 },
                          { name: '61-90 Days', value: 15 },
                          { name: '91-180 Days', value: 10 },
                          { name: '180+ Days', value: 5 }
                        ]}>
                          <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
                          <XAxis dataKey="name" stroke="#94a3b8" />
                          <YAxis stroke="#94a3b8" />
                          <Tooltip contentStyle={chartTooltipStyle} />
                          <Bar dataKey="value" fill="#4f46e5" radius={[4, 4, 0, 0]}>
                            <Cell fill="#6366f1" />
                            <Cell fill="#3b82f6" />
                            <Cell fill="#06b6d4" />
                            <Cell fill="#eab308" />
                            <Cell fill="#ef4444" />
                          </Bar>
                        </BarChart>
                      </ResponsiveContainer>
                    </div>
                  </div>

                  {/* Recent Inward Stock Received */}
                  <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-6 rounded-2xl shadow-sm flex flex-col">
                    <h3 className="text-lg font-bold mb-4">Recent Inward Stock Received</h3>
                    <div className="space-y-4 flex-1">
                      {dashboardData.recentBatches?.length === 0 ? (
                        <p className="text-slate-500 text-sm">No recent inward stock entries.</p>
                      ) : (
                        dashboardData.recentBatches.map(batch => (
                          <div key={batch.id} className="flex justify-between items-center p-3 rounded-lg bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800">
                            <div>
                              <span className="font-semibold text-slate-800 dark:text-slate-200 block">{batch.productName || 'Unknown Product'}</span>
                              <span className="text-xs text-slate-500 dark:text-slate-400">Batch: {batch.batchNumber} | Supplier: {batch.supplierName}</span>
                            </div>
                            <div className="text-right">
                              <span className="font-mono text-emerald-500 dark:text-emerald-400 font-bold block">+{batch.secondaryReceived}</span>
                              <span className="text-xs text-slate-400">{batch.receivedAt ? new Date(batch.receivedAt).toLocaleDateString() : ''}</span>
                            </div>
                          </div>
                        ))
                      )}
                    </div>
                  </div>
                </div>
              </>
            )}
          </motion.div>
        )}

        {/* 2. OVERVIEW / INVENTORY TAB */}
        {activeTab === 'overview' && (
          <motion.div
            initial={{ opacity: 0, y: 15 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -15 }}
            className="space-y-6"
          >
            {/* Filters panel */}
            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-6 rounded-2xl flex flex-wrap gap-4 items-center justify-between shadow-sm">
              <div className="flex flex-wrap gap-3 items-center flex-1">
                <div className="relative flex-1 min-w-[240px]">
                  <Search className="absolute left-3.5 top-3.5 w-4 h-4 text-slate-450" />
                  <input
                    type="text"
                    placeholder="Search by Product name, Brand..."
                    value={searchTerm}
                    onChange={(e) => {
                      setSearchTerm(e.target.value)
                      setInvPage(0)
                    }}
                    className="w-full pl-10 pr-4 py-2.5 bg-slate-50 dark:bg-slate-950 border border-slate-250 dark:border-slate-800 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 text-slate-900 dark:text-slate-100"
                  />
                </div>
                <select
                  value={selectedCategory}
                  onChange={(e) => {
                    setSelectedCategory(e.target.value)
                    setInvPage(0)
                  }}
                  className="px-4 py-2.5 bg-slate-50 dark:bg-slate-950 border border-slate-250 dark:border-slate-800 rounded-xl text-slate-800 dark:text-slate-300 focus:ring-2 focus:ring-indigo-500"
                >
                  <option value="">All Categories</option>
                  {CATEGORIES.map(cat => <option key={cat} value={cat}>{cat}</option>)}
                </select>
                <select
                  value={selectedStatus}
                  onChange={(e) => {
                    setSelectedStatus(e.target.value)
                    setInvPage(0)
                  }}
                  className="px-4 py-2.5 bg-slate-50 dark:bg-slate-950 border border-slate-250 dark:border-slate-800 rounded-xl text-slate-800 dark:text-slate-300 focus:ring-2 focus:ring-indigo-500"
                >
                  <option value="">All Statuses</option>
                  <option value="Healthy">Healthy</option>
                  <option value="Low Stock">Low Stock</option>
                  <option value="Out Of Stock">Out Of Stock</option>
                  <option value="Overstock">Overstock</option>
                </select>
              </div>

              {(searchTerm || selectedCategory || selectedStatus) && (
                <button
                  onClick={() => {
                    setSearchTerm('')
                    setSelectedCategory('')
                    setSelectedStatus('')
                    setInvPage(0)
                  }}
                  className="flex items-center gap-1 text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 transition"
                >
                  <RotateCcw className="w-4 h-4" /> Reset
                </button>
              )}
            </div>

            {/* Inventory Data Grid */}
            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl overflow-hidden shadow-sm">
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-slate-50 dark:bg-slate-950 border-b border-slate-200 dark:border-slate-800 text-slate-750 dark:text-slate-300 font-semibold text-sm">
                      <th className="py-2.5 px-4">Product</th>
                      <th className="py-2.5 px-4">Category</th>
                      <th className="py-2.5 px-4">Stock</th>
                      <th className="py-2.5 px-4">Available</th>
                      <th className="py-2.5 px-4">Reserved</th>
                      <th className="py-2.5 px-4">Avg Cost</th>
                      <th className="py-2.5 px-4">Selling</th>
                      <th className="py-2.5 px-4">Margin</th>
                      <th className="py-2.5 px-4">Inventory Value</th>
                      <th className="py-2.5 px-4 text-center">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 dark:divide-slate-800 text-sm">
                    {loading ? (
                      <tr>
                        <td colSpan={10} className="py-8 text-center text-slate-400 dark:text-slate-500 font-medium">
                          Loading inventory ledger data...
                        </td>
                      </tr>
                    ) : filteredInventory.length === 0 ? (
                      <tr>
                        <td colSpan={10} className="py-8 text-center text-slate-400 dark:text-slate-500">
                          No matching stock entries found.
                        </td>
                      </tr>
                    ) : (
                      filteredInventory.map(item => (
                        <tr key={item.id} className="hover:bg-slate-100/40 dark:hover:bg-slate-800/40 transition-colors">
                          <td className="py-2 px-4">
                            <span className="font-semibold text-slate-800 dark:text-slate-100 block">{item.productName}</span>
                            <span className="text-xs text-slate-505 dark:text-slate-400">{item.brand}</span>
                          </td>
                          <td className="py-2 px-4 text-slate-700 dark:text-slate-300">{item.category}</td>
                          <td className="py-2 px-4 font-mono">
                             <span className="text-slate-800 dark:text-slate-200 font-semibold">
                               {item.totalPrimaryUnits || 0} <span className="text-xs font-normal text-slate-500 dark:text-slate-400">{item.primaryUnit || 'BOX'}</span>
                             </span>
                             {item.totalSecondaryUnits > 0 && (
                               <span className="block text-xs text-slate-400 dark:text-slate-500 mt-0.5">
                                 {item.totalSecondaryUnits} {item.secondaryUnit || 'Units'}
                               </span>
                             )}
                          </td>
                  <td className="py-2 px-4 font-mono text-indigo-600 dark:text-indigo-400">
                            {item.availableStock}
                          </td>
                          <td className="py-2 px-4 font-mono text-amber-600 dark:text-amber-500">
                            {item.reservedStock}
                          </td>
                          <td className="py-2 px-4 font-mono text-slate-700 dark:text-slate-300 text-xs">
                             <div className="font-semibold text-slate-800 dark:text-slate-200">₹{item.avgCostPrimary} <span className="text-[10px] text-slate-400">/{item.primaryUnit || 'BOX'}</span></div>
                             <div className="text-[11px] text-slate-500">₹{item.avgCost} <span className="text-[10px] text-slate-400">/{item.secondaryUnit || 'LADI'}</span></div>
                           </td>
                           <td className="py-2 px-4 font-mono text-slate-700 dark:text-slate-300 text-xs">
                             <div className="font-semibold text-slate-800 dark:text-slate-200">₹{item.sellingPricePrimary} <span className="text-[10px] text-slate-400">/{item.primaryUnit || 'BOX'}</span></div>
                             <div className="text-[11px] text-slate-500">₹{item.sellingPrice} <span className="text-[10px] text-slate-400">/{item.secondaryUnit || 'LADI'}</span></div>
                           </td>
                           <td className="py-2 px-4 text-xs font-mono">
                              <div className={`font-semibold ${item.marginPercentPrimary < 5 ? 'text-red-500 dark:text-red-400' : 'text-emerald-600 dark:text-emerald-400'}`}>
                                {item.marginPercentPrimary}% <span className="text-[10px] text-slate-400">/{item.primaryUnit || 'BOX'}</span>
                              </div>
                              <div className={`text-[11px] ${item.marginPercent < 5 ? 'text-red-500 dark:text-red-400' : 'text-emerald-600/80 dark:text-emerald-400/80'}`}>
                                {item.marginPercent}% <span className="text-[10px] text-slate-400">/{item.secondaryUnit || 'LADI'}</span>
                              </div>
                            </td>
                          <td className="py-2 px-4 font-mono text-emerald-600 dark:text-emerald-400 font-semibold">₹{item.inventoryValue}</td>
                          <td className="py-2 px-4 text-center">
                            <span className={`px-3 py-1 rounded-full text-xs font-semibold ${
                              item.status === 'Healthy' ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20' :
                              item.status === 'Low Stock' ? 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20' :
                              item.status === 'Overstock' ? 'bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 border border-indigo-500/20' :
                              'bg-red-500/10 text-red-600 dark:text-red-400 border border-red-500/20'
                            }`}>
                              {item.status}
                            </span>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>

              {/* Pagination */}
              {invTotalPages > 1 && (
                <div className="p-4 bg-slate-50 dark:bg-slate-950/80 border-t border-slate-200 dark:border-slate-800">
                  <Pagination page={invPage} totalPages={invTotalPages} totalElements={invTotalElements} pageSize={INV_PAGE_SIZE} onPageChange={setInvPage} />
                </div>
              )}
            </div>
          </motion.div>
        )}

        {/* 3. RECEIVE STOCK TAB (ONLY SHOW IF NOT RENDERING OCR PREVIEW ACTIVE) */}
        {activeTab === 'receive' && scannerPreview.length === 0 && (
          <motion.div
            initial={{ opacity: 0, y: 15 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -15 }}
            className="grid grid-cols-1 lg:grid-cols-3 gap-8"
          >
            {/* Input Form Panel (Manual Intake) */}
            <div className="lg:col-span-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-6 rounded-2xl shadow-sm">
              <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center border-b border-slate-200 dark:border-slate-800 pb-4 mb-6 gap-3">
                <h3 className="text-xl font-bold flex items-center gap-2">
                  <Plus className="w-5 h-5 text-indigo-500" /> Stock Intake Registry
                </h3>
                
                {/* Single vs Bulk Entry toggle button controls */}
                <div className="inline-flex bg-slate-100 dark:bg-slate-950 p-1 rounded-lg border border-slate-200 dark:border-slate-800">
                  <button
                    type="button"
                    onClick={() => setReceiveMode('single')}
                    className={`px-3 py-1.5 rounded-md text-xs font-semibold transition ${
                      receiveMode === 'single'
                        ? 'bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 shadow-sm'
                        : 'text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-350'
                    }`}
                  >
                    Single Entry
                  </button>
                  <button
                    type="button"
                    onClick={() => setReceiveMode('bulk')}
                    className={`px-3 py-1.5 rounded-md text-xs font-semibold transition ${
                      receiveMode === 'bulk'
                        ? 'bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 shadow-sm'
                        : 'text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-350'
                    }`}
                  >
                    Bulk Entry (Manual)
                  </button>
                </div>
              </div>

              {receiveMode === 'single' ? (
                /* SINGLE ENTRY FORM */
                <form onSubmit={handleReceiveSubmit} className="space-y-6">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <div className="md:col-span-2">
                      <label className="text-slate-500 dark:text-slate-400 text-sm font-semibold mb-1 block">Choose Product <span className="text-red-500">*</span></label>
                      <SearchSelect
                        options={products}
                        value={manualForm.productId}
                        onChange={(val) => setManualForm(f => ({ ...f, productId: val }))}
                        placeholder="Search and select product..."
                        labelKey="name"
                        valueKey="id"
                        renderOption={(p) => `${p.name}${p.brand ? ` (${p.brand})` : ''}`}
                      />
                    </div>

                    <div>
                      <label className="text-slate-500 dark:text-slate-400 text-sm font-semibold mb-1 block">Supplier Name *</label>
                      <input
                        type="text"
                        value={manualForm.supplierName}
                        onChange={(e) => setManualForm(f => ({ ...f, supplierName: e.target.value }))}
                        required
                        placeholder="e.g. Saurabh Traders"
                        className="w-full px-4 py-2.5 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-xl text-slate-900 dark:text-slate-100 focus:ring-2 focus:ring-indigo-500"
                      />
                    </div>

                    <div>
                      <label className="text-slate-500 dark:text-slate-400 text-sm font-semibold mb-1 block">Batch Number *</label>
                      <input
                        type="text"
                        value={manualForm.batchNumber}
                        onChange={(e) => setManualForm(f => ({ ...f, batchNumber: e.target.value }))}
                        required
                        placeholder="e.g. B-99388"
                        className="w-full px-4 py-2.5 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-xl text-slate-900 dark:text-slate-100 focus:ring-2 focus:ring-indigo-500 font-mono"
                      />
                    </div>

                    <div>
                      <label className="text-slate-500 dark:text-slate-400 text-sm font-semibold mb-1 block">Supplier Invoice Number *</label>
                      <input
                        type="text"
                        value={manualForm.supplierInvoiceNumber}
                        onChange={(e) => setManualForm(f => ({ ...f, supplierInvoiceNumber: e.target.value }))}
                        required
                        placeholder="e.g. INV-100223"
                        className="w-full px-4 py-2.5 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-xl text-slate-900 dark:text-slate-100 focus:ring-2 focus:ring-indigo-500"
                      />
                    </div>

                    <div>
                      <label className="text-slate-500 dark:text-slate-400 text-sm font-semibold mb-1 block">Supplier Invoice Date *</label>
                      <input
                        type="date"
                        value={manualForm.supplierInvoiceDate}
                        onChange={(e) => setManualForm(f => ({ ...f, supplierInvoiceDate: e.target.value }))}
                        required
                        className="w-full px-4 py-2.5 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-xl text-slate-900 dark:text-slate-100 focus:ring-2 focus:ring-indigo-500"
                      />
                    </div>

                    <div>
                      <label className="text-slate-500 dark:text-slate-400 text-sm font-semibold mb-1 block">Stock Received Date *</label>
                      <input
                        type="date"
                        value={manualForm.stockReceivedDate}
                        onChange={(e) => setManualForm(f => ({ ...f, stockReceivedDate: e.target.value }))}
                        required
                        className="w-full px-4 py-2.5 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-xl text-slate-900 dark:text-slate-100 focus:ring-2 focus:ring-indigo-500"
                      />
                    </div>

                    <div>
                      <label className="text-slate-500 dark:text-slate-400 text-sm font-semibold mb-1 block">Expiry Date *</label>
                      <input
                        type="date"
                        value={manualForm.expiryDate}
                        onChange={(e) => setManualForm(f => ({ ...f, expiryDate: e.target.value }))}
                        required
                        className="w-full px-4 py-2.5 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-xl text-slate-900 dark:text-slate-100 focus:ring-2 focus:ring-indigo-500"
                      />
                    </div>

                    <div>
                      <label className="text-slate-500 dark:text-slate-400 text-sm font-semibold mb-1 block">Manufacturing Date *</label>
                      <input
                        type="date"
                        value={manualForm.manufacturingDate}
                        onChange={(e) => setManualForm(f => ({ ...f, manufacturingDate: e.target.value }))}
                        required
                        className="w-full px-4 py-2.5 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-xl text-slate-900 dark:text-slate-100 focus:ring-2 focus:ring-indigo-500"
                      />
                    </div>

                    <div className="grid grid-cols-3 gap-3 md:col-span-2">
                      <div>
                        <label className="text-slate-500 dark:text-slate-400 text-xs font-semibold mb-1 block">Primary Qty (BOX)</label>
                        <input
                          type="number"
                          placeholder="BOX"
                          value={manualForm.primaryReceived}
                          onChange={(e) => setManualForm(f => ({ ...f, primaryReceived: e.target.value }))}
                          className="w-full px-3 py-2 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg text-slate-900 dark:text-slate-100 focus:ring-1 focus:ring-indigo-500"
                        />
                      </div>
                      <div>
                        <label className="text-slate-500 dark:text-slate-400 text-xs font-semibold mb-1 block">Extra Secondary (LADI)</label>
                        <input
                          type="number"
                          placeholder="Loose Packs"
                          value={manualForm.extraSecondaryReceived}
                          onChange={(e) => setManualForm(f => ({ ...f, extraSecondaryReceived: e.target.value }))}
                          className="w-full px-3 py-2 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg text-slate-900 dark:text-slate-100 focus:ring-1 focus:ring-indigo-500"
                        />
                      </div>
                      <div>
                        <label className="text-slate-500 dark:text-slate-400 text-xs font-semibold mb-1 block">Offer Quantity (Free)</label>
                        <input
                          type="number"
                          placeholder="Free Packs"
                          value={manualForm.offerSecondaryReceived}
                          onChange={(e) => setManualForm(f => ({ ...f, offerSecondaryReceived: e.target.value }))}
                          className="w-full px-3 py-2 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg text-slate-900 dark:text-slate-100 focus:ring-1 focus:ring-indigo-500"
                        />
                      </div>
                    </div>

                    <div>
                      <label className="text-slate-500 dark:text-slate-400 text-sm font-semibold mb-1 block">Buy Price per BOX (Excl. Tax) *</label>
                      <input
                        type="number"
                        step="0.01"
                        value={manualForm.buyPriceWithoutTax}
                        onChange={(e) => setManualForm(f => ({ ...f, buyPriceWithoutTax: e.target.value }))}
                        required
                        placeholder="₹0.00"
                        className="w-full px-4 py-2.5 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-xl text-slate-900 dark:text-slate-100 focus:ring-2 focus:ring-indigo-500"
                      />
                    </div>

                    <div>
                      <label className="text-slate-500 dark:text-slate-400 text-sm font-semibold mb-1 block">GST Rate %</label>
                      <select
                        value={manualForm.gstPercent}
                        onChange={(e) => setManualForm(f => ({ ...f, gstPercent: e.target.value }))}
                        className="w-full px-4 py-2.5 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-xl text-slate-800 dark:text-slate-300 focus:ring-2 focus:ring-indigo-500"
                      >
                        <option value="0">0%</option>
                        <option value="5">5%</option>
                        <option value="12">12%</option>
                        <option value="18">18%</option>
                        <option value="28">28%</option>
                      </select>
                    </div>

                    <div className="md:col-span-2">
                      <label className="text-slate-500 dark:text-slate-400 text-sm font-semibold mb-1 block">Remarks / Notes</label>
                      <textarea
                        value={manualForm.remarks}
                        onChange={(e) => setManualForm(f => ({ ...f, remarks: e.target.value }))}
                        placeholder="Any batch details, damages warnings..."
                        className="w-full px-4 py-2.5 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-xl text-slate-900 dark:text-slate-100 focus:ring-2 focus:ring-indigo-500 h-20 resize-none"
                      />
                    </div>
                  </div>

                  <button
                    type="submit"
                    disabled={savingStock}
                    className="w-full py-3 px-6 rounded-xl font-bold bg-gradient-to-r from-indigo-600 to-sky-600 hover:from-indigo-500 hover:to-sky-500 text-white shadow disabled:opacity-50 transition duration-300"
                  >
                    {savingStock ? 'Registering Batch...' : 'Register Inward Stock'}
                  </button>
                </form>
              ) : (
                /* MANUAL BULK ENTRY VIEW GRID */
                <form onSubmit={handleBulkReceiveSubmit} className="space-y-6">
                  {/* Global Invoice Details */}
                  <div className="p-4 bg-slate-50 dark:bg-slate-950 rounded-xl border border-slate-200 dark:border-slate-800 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                    <div>
                      <label className="text-slate-500 dark:text-slate-400 text-xs font-semibold mb-1 block">Supplier Name *</label>
                      <input
                        type="text"
                        value={bulkSupplierName}
                        onChange={(e) => setBulkSupplierName(e.target.value)}
                        required
                        placeholder="e.g. Saurabh Traders"
                        className="w-full px-3 py-1.5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg text-sm text-slate-900 dark:text-slate-100"
                      />
                    </div>
                    <div>
                      <label className="text-slate-500 dark:text-slate-400 text-xs font-semibold mb-1 block">Invoice Number *</label>
                      <input
                        type="text"
                        value={bulkInvoiceNumber}
                        onChange={(e) => setBulkInvoiceNumber(e.target.value)}
                        required
                        placeholder="e.g. SA-889"
                        className="w-full px-3 py-1.5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg text-sm text-slate-900 dark:text-slate-100"
                      />
                    </div>
                    <div>
                      <label className="text-slate-500 dark:text-slate-400 text-xs font-semibold mb-1 block">Invoice Date *</label>
                      <input
                        type="date"
                        value={bulkInvoiceDate}
                        onChange={(e) => setBulkInvoiceDate(e.target.value)}
                        required
                        className="w-full px-3 py-1.5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg text-sm text-slate-900 dark:text-slate-100"
                      />
                    </div>
                    <div>
                      <label className="text-slate-500 dark:text-slate-400 text-xs font-semibold mb-1 block">Received Date</label>
                      <input
                        type="date"
                        value={bulkReceivedDate}
                        onChange={(e) => setBulkReceivedDate(e.target.value)}
                        required
                        className="w-full px-3 py-1.5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg text-sm text-slate-900 dark:text-slate-100"
                      />
                    </div>
                    <div className="md:col-span-2 lg:col-span-4">
                      <label className="text-slate-500 dark:text-slate-400 text-xs font-semibold mb-1 block">Global Remarks</label>
                      <input
                        type="text"
                        value={bulkRemarks}
                        onChange={(e) => setBulkRemarks(e.target.value)}
                        placeholder="Add overall shipment notes..."
                        className="w-full px-3 py-1.5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg text-sm text-slate-900 dark:text-slate-100"
                      />
                    </div>
                  </div>

                  {/* Bulk Items Grid Table */}
                  <div className="overflow-x-auto border border-slate-200 dark:border-slate-800 rounded-xl bg-slate-50 dark:bg-slate-950/40">
                    <table className="w-full text-left border-collapse text-xs md:text-sm">
                      <thead>
                        <tr className="bg-slate-100 dark:bg-slate-950 border-b border-slate-200 dark:border-slate-800 text-slate-650 dark:text-slate-300 font-semibold uppercase">
                          <th className="p-3 min-w-[200px]">Product *</th>
                          <th className="p-3 min-w-[100px]">Batch No *</th>
                          <th className="p-3 min-w-[120px]">Expiry *</th>
                          <th className="p-3 min-w-[120px]">Mfg Date</th>
                          <th className="p-3 min-w-[90px]">Qty (BOX)</th>
                          <th className="p-3 min-w-[90px]">Loose (LADI)</th>
                          <th className="p-3 min-w-[90px]">Offer (Free)</th>
                          <th className="p-3 min-w-[100px]">Buy per BOX *</th>
                          <th className="p-3 min-w-[80px]">GST %</th>
                          <th className="p-3 min-w-[120px]">Total (Incl. Tax)</th>
                          <th className="p-3 text-center">Action</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                        {bulkRows.map((row, index) => (
                          <tr key={index} className="hover:bg-slate-100/50 dark:hover:bg-slate-800/20">
                            <td className="p-2">
                              <SearchSelect
                                options={products.map(p => ({ value: p.id, label: `${p.name} (${p.brand})` }))}
                                value={row.productId}
                                onChange={(val) => handleBulkRowChange(index, 'productId', val)}
                                placeholder="Search DB product..."
                              />
                            </td>
                            <td className="p-2">
                              <input
                                type="text"
                                value={row.batchNumber}
                                onChange={e => handleBulkRowChange(index, 'batchNumber', e.target.value.toUpperCase())}
                                required
                                placeholder="B-..."
                                className="w-24 px-2 py-1 bg-white dark:bg-slate-900 border border-slate-350 dark:border-slate-700 rounded font-mono text-xs text-slate-900 dark:text-slate-100"
                              />
                            </td>
                            <td className="p-2">
                              <input
                                type="date"
                                value={row.expiryDate}
                                onChange={e => handleBulkRowChange(index, 'expiryDate', e.target.value)}
                                required
                                className="w-28 px-2 py-1 bg-white dark:bg-slate-900 border border-slate-350 dark:border-slate-700 rounded text-xs text-slate-900 dark:text-slate-100"
                              />
                            </td>
                            <td className="p-2">
                              <input
                                type="date"
                                value={row.manufacturingDate}
                                onChange={e => handleBulkRowChange(index, 'manufacturingDate', e.target.value)}
                                className="w-28 px-2 py-1 bg-white dark:bg-slate-900 border border-slate-350 dark:border-slate-700 rounded text-xs text-slate-900 dark:text-slate-100"
                              />
                            </td>
                            <td className="p-2">
                              <input
                                type="number"
                                value={row.primaryReceived}
                                onChange={e => handleBulkRowChange(index, 'primaryReceived', e.target.value)}
                                placeholder="BOX"
                                min="0"
                                className="w-16 px-2 py-1 bg-white dark:bg-slate-900 border border-slate-355 dark:border-slate-700 rounded text-xs text-slate-900 dark:text-slate-100 font-semibold"
                              />
                            </td>
                            <td className="p-2">
                              <input
                                type="number"
                                value={row.extraSecondaryReceived}
                                onChange={e => handleBulkRowChange(index, 'extraSecondaryReceived', e.target.value)}
                                placeholder="Packs"
                                min="0"
                                className="w-16 px-2 py-1 bg-white dark:bg-slate-900 border border-slate-355 dark:border-slate-700 rounded text-xs text-slate-900 dark:text-slate-100"
                              />
                            </td>
                            <td className="p-2">
                              <input
                                type="number"
                                value={row.offerSecondaryReceived}
                                onChange={e => handleBulkRowChange(index, 'offerSecondaryReceived', e.target.value)}
                                placeholder="Offer"
                                min="0"
                                className="w-16 px-2 py-1 bg-white dark:bg-slate-900 border border-slate-355 dark:border-slate-700 rounded text-xs text-slate-900 dark:text-slate-100"
                              />
                            </td>
                            <td className="p-2">
                              <input
                                type="number"
                                step="0.01"
                                value={row.buyPriceWithoutTax}
                                onChange={e => handleBulkRowChange(index, 'buyPriceWithoutTax', e.target.value)}
                                required
                                placeholder="₹0.00"
                                min="0"
                                className="w-20 px-2 py-1 bg-white dark:bg-slate-900 border border-slate-355 dark:border-slate-700 rounded text-xs text-slate-900 dark:text-slate-100 font-semibold"
                              />
                            </td>
                            <td className="p-2">
                              <select
                                value={row.gstPercent}
                                onChange={e => handleBulkRowChange(index, 'gstPercent', e.target.value)}
                                className="px-2 py-1 bg-white dark:bg-slate-900 border border-slate-355 dark:border-slate-700 rounded text-xs text-slate-900 dark:text-slate-100"
                              >
                                <option value="0">0%</option>
                                <option value="5">5%</option>
                                <option value="12">12%</option>
                                <option value="18">18%</option>
                                <option value="28">28%</option>
                              </select>
                            </td>
                            <td className="p-2 font-semibold">
                              {(() => {
                                const prod = products.find(p => p.id === row.productId)
                                const ratio = prod?.secondaryPerPrimary || 1
                                const pVal = row.primaryReceived === '' ? 0 : Number(row.primaryReceived)
                                const oVal = row.extraSecondaryReceived === '' ? 0 : Number(row.extraSecondaryReceived)
                                const price = Number(row.buyPriceWithoutTax || 0)
                                const gst = Number(row.gstPercent || 0)
                                
                                const taxable = (pVal * price) + (oVal * (price / ratio))
                                const total = taxable * (1 + gst / 100)
                                return `₹${total.toFixed(2)}`
                              })()}
                            </td>
                            <td className="p-2 text-center">
                              <button
                                type="button"
                                onClick={() => removeBulkRow(index)}
                                className="p-1 hover:bg-rose-500/10 text-rose-500 hover:text-rose-600 rounded transition"
                                title="Delete row"
                              >
                                <Trash2 className="w-4 h-4" />
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>

                  {/* Add Row controller */}
                  <button
                    type="button"
                    onClick={addBulkRow}
                    className="flex items-center gap-1.5 text-xs text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 font-bold border border-dashed border-slate-300 dark:border-slate-800 p-2.5 rounded-lg w-full justify-center bg-slate-50/50 dark:bg-slate-950/20"
                  >
                    <PlusCircle className="w-4 h-4" /> Add Row
                  </button>

                  {/* Bulk Inward Grand Summary Card */}
                  {(() => {
                    const { totalTaxable, totalGst, totalNet } = getBulkSummary();
                    return (
                      <div className="bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 p-5 rounded-2xl flex flex-wrap justify-between items-center gap-4">
                        <div>
                          <h4 className="font-bold text-sm md:text-base">Scanned Invoice Summary</h4>
                          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">Please confirm the amounts against the printed invoice copy.</p>
                        </div>
                        <div className="flex gap-6 flex-wrap">
                          <div className="flex flex-col items-end">
                            <span className="text-xxs font-bold text-slate-400 uppercase tracking-wider">Total Taxable Value</span>
                            <span className="text-sm md:text-base font-bold text-slate-700 dark:text-slate-200">
                              ₹{totalTaxable.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                            </span>
                          </div>
                          <div className="flex flex-col items-end">
                            <span className="text-xxs font-bold text-slate-400 uppercase tracking-wider">Total GST Tax</span>
                            <span className="text-sm md:text-base font-bold text-slate-700 dark:text-slate-200">
                              ₹{totalGst.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                            </span>
                          </div>
                          <div className="flex flex-col items-end border-l border-slate-200 dark:border-slate-800 pl-6">
                            <span className="text-xxs font-extrabold text-slate-400 uppercase tracking-wider">Grand Total (Incl. Tax)</span>
                            <span className="text-xl md:text-2xl font-black text-emerald-500">
                              ₹{totalNet.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                            </span>
                          </div>
                        </div>
                      </div>
                    );
                  })()}

                  <button
                    type="submit"
                    disabled={savingStock}
                    className="w-full py-3 px-6 rounded-xl font-bold bg-gradient-to-r from-indigo-600 to-sky-600 hover:from-indigo-500 hover:to-sky-500 text-white shadow disabled:opacity-50 transition duration-300"
                  >
                    {savingStock ? 'Registering Bulk Stock...' : 'Confirm & Register Bulk Inward'}
                  </button>
                </form>
              )}
            </div>

            {/* OCR Document Upload card (always on the right in Receive Stock) */}
            <div className="space-y-6">
              <div className="bg-gradient-to-b from-indigo-50 dark:from-indigo-950 to-white dark:to-slate-900 border border-indigo-100 dark:border-indigo-800/30 p-6 rounded-2xl text-center shadow-sm">
                <Camera className="w-12 h-12 text-indigo-500 dark:text-indigo-400 mx-auto mb-4" />
                <h4 className="text-lg font-bold mb-2">Scan Supplier Invoice (OCR)</h4>
                <p className="text-slate-500 dark:text-slate-400 text-sm mb-4">Upload an invoice snapshot to parse items automatically with AI assist.</p>
                
                {/* File Upload Selector */}
                {scannerFile ? (
                  <div className="space-y-4">
                    <div className="p-4 bg-slate-100 dark:bg-slate-850 rounded-xl border border-slate-200 dark:border-slate-750 flex items-center justify-between">
                      <div className="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-200">
                        <FileText className="w-5 h-5 text-indigo-500 dark:text-indigo-400 flex-shrink-0" />
                        <span className="truncate max-w-[150px] font-semibold text-xs">{scannerFile.name}</span>
                      </div>
                      <button 
                        type="button" 
                        onClick={() => { setScannerFile(null); setScannerPreview([]); }} 
                        className="text-xs text-rose-500 hover:text-rose-600 font-bold"
                      >
                        Remove
                      </button>
                    </div>

                    <div className="flex flex-col gap-1.5 text-left">
                      <label className="text-xs font-semibold text-slate-500 dark:text-slate-400">Supplier Name *</label>
                      <input
                        type="text"
                        value={scannerSupplier}
                        onChange={e => setScannerSupplier(e.target.value)}
                        placeholder="Supplier name"
                        className="w-full px-3 py-2 bg-white dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg text-xs"
                      />
                    </div>

                    <button
                      type="button"
                      onClick={handleScanInvoice}
                      disabled={scannerLoading}
                      className="w-full py-2.5 px-4 rounded-xl font-bold bg-indigo-600 hover:bg-indigo-500 text-white transition duration-300 flex items-center justify-center gap-2 shadow"
                    >
                      <Camera className="w-4 h-4" />
                      {scannerLoading ? 'Reading Layout...' : 'Start AI Analysis'}
                    </button>
                  </div>
                ) : (
                  <div
                    onClick={() => fileInputRef.current.click()}
                    className="border-2 border-dashed border-slate-300 dark:border-slate-750 hover:border-indigo-500 dark:hover:border-indigo-500 rounded-xl p-6 transition duration-300 cursor-pointer bg-slate-50 dark:bg-slate-950/20"
                  >
                    <Upload className="w-6 h-6 text-slate-400 mx-auto mb-2" />
                    <span className="text-xs text-slate-500 dark:text-slate-400 block font-medium">Select Invoice PDF or JPEG file</span>
                    <input
                      type="file"
                      ref={fileInputRef}
                      className="hidden"
                      accept="image/*,application/pdf"
                      onChange={(e) => {
                        if (e.target.files[0]) {
                          setScannerFile(e.target.files[0]);
                        }
                      }}
                    />
                  </div>
                )}

                {scannerLoading && (
                  <div className="mt-4 p-4 rounded-xl bg-slate-100 dark:bg-slate-950 flex flex-col items-center gap-2 text-xs">
                    <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-indigo-600"></div>
                    <span className="text-slate-500 dark:text-slate-400">Gemini OCR parser calculations running...</span>
                  </div>
                )}
              </div>
            </div>
          </motion.div>
        )}

        {/* 4. BATCH INVENTORY TAB */}
        {(activeTab === 'batches' || activeTab === 'invoices') && (
          <motion.div
            initial={{ opacity: 0, y: 15 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -15 }}
            className="space-y-6"
          >
            {/* View toggle + Search */}
            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-4 rounded-2xl flex flex-wrap gap-4 items-center shadow-sm">
              {/* Toggle */}
              <div className="flex bg-slate-100 dark:bg-slate-950 rounded-xl p-1 gap-1">
                <button
                  onClick={() => { setBatchView('list'); setBatchPage(0); }}
                  className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-semibold transition-all ${
                    batchView === 'list'
                      ? 'bg-indigo-600 text-white shadow'
                      : 'text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200'
                  }`}
                >
                  <Clock className="w-3.5 h-3.5" /> Batch List
                </button>
                <button
                  onClick={() => { setBatchView('invoices'); setInvoicePage(0); loadAllBatches(); }}
                  className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-semibold transition-all ${
                    batchView === 'invoices'
                      ? 'bg-indigo-600 text-white shadow'
                      : 'text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200'
                  }`}
                >
                  <FileText className="w-3.5 h-3.5" /> Invoice Summary
                </button>
              </div>
              {/* Search (only in batch list view) */}
              {batchView === 'list' && (
                <div className="relative flex-1 min-w-[240px]">
                  <Search className="absolute left-3.5 top-3 w-4 h-4 text-slate-400" />
                  <input
                    type="text"
                    placeholder="Filter by product name, batch number..."
                    value={batchSearchTerm}
                    onChange={(e) => setBatchSearchTerm(e.target.value)}
                    className="w-full pl-10 pr-10 py-2.5 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 text-slate-900 dark:text-slate-100 text-sm"
                  />
                  {batchSearchTerm && (
                    <button
                      onClick={() => setBatchSearchTerm('')}
                      className="absolute right-3 top-3 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
                    >
                      <X className="w-4 h-4" />
                    </button>
                  )}
                </div>
              )}
            </div>

            {/* Batch list table — hidden in Invoice Summary mode */}
            {batchView === 'list' && (
            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl overflow-hidden shadow-sm">
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-slate-50 dark:bg-slate-950 border-b border-slate-200 dark:border-slate-800 text-slate-750 dark:text-slate-305 font-semibold text-xs uppercase tracking-wider">
                      <th className="py-2.5 px-4">Product / Batch</th>
                      <th className="py-2.5 px-4">Supplier & Invoice</th>
                      <th className="py-2.5 px-4">Received / Sold</th>
                      <th className="py-2.5 px-4">Available</th>
                      <th className="py-2.5 px-4">Cost Price</th>
                      <th className="py-2.5 px-4">Expiry & Age</th>
                      <th className="py-2.5 px-4">Value</th>
                      <th className="py-2.5 px-4 text-center">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 dark:divide-slate-800 text-sm">
                    {(() => {
                      const filteredBatchList = batchList
                      if (filteredBatchList.length === 0) return (
                        <tr>
                          <td colSpan={8} className="py-8 text-center text-slate-400 dark:text-slate-505 font-medium">
                            {batchSearchTerm.trim() ? `No batches match "${batchSearchTerm}"` : 'No batches found.'}
                          </td>
                        </tr>
                      )
                      return filteredBatchList.map(batch => (
                        <tr key={batch.id} className="hover:bg-slate-100/40 dark:hover:bg-slate-800/40 transition-colors">
                          <td className="py-2 px-4">
                            <span className="font-semibold text-slate-800 dark:text-slate-100 block">{batch.productName}</span>
                            <span className="text-xs text-indigo-600 dark:text-indigo-400 font-semibold font-mono">Batch: {batch.batchNumber}</span>
                          </td>
                          <td className="py-2 px-4">
                            <span className="text-slate-700 dark:text-slate-300 block">{batch.supplierName}</span>
                            <span className="text-xs text-slate-500 dark:text-slate-400">Inv: {batch.invoiceNumber} | {batch.supplierInvoiceDate || 'No Date'}</span>
                          </td>
                          <td className="py-2 px-4 font-mono">
                            {(() => {
                              const ratio = batch.secondaryPerPrimary && batch.secondaryPerPrimary > 0 ? batch.secondaryPerPrimary : 1
                              const recPrimary = Math.floor(batch.secondaryReceived / ratio)
                              const soldPrimary = Math.floor((batch.quantitySold || 0) / ratio)
                              return (
                                <>
                                  <span className="text-slate-800 dark:text-slate-200 font-semibold block">
                                    {recPrimary} <span className="text-xs font-normal text-slate-500 dark:text-slate-400">{batch.primaryUnit || 'BOX'}</span>
                                  </span>
                                  <span className="block text-xs text-slate-400 dark:text-slate-550">
                                    {batch.secondaryReceived} {batch.secondaryUnit || 'LADI'}
                                  </span>
                                  <span className="block text-xs text-slate-500 dark:text-slate-400 mt-1 font-sans">
                                    Sold: {soldPrimary} {batch.primaryUnit || 'BOX'} ({batch.quantitySold || 0} {batch.secondaryUnit || 'LADI'})
                                  </span>
                                </>
                              )
                            })()}
                          </td>
                          <td className="py-2 px-4 font-mono text-indigo-600 dark:text-indigo-400">
                            {(() => {
                              const ratio = batch.secondaryPerPrimary && batch.secondaryPerPrimary > 0 ? batch.secondaryPerPrimary : 1
                              const remPrimary = Math.floor(batch.secondaryRemaining / ratio)
                              return (
                                <>
                                  <span className="font-bold block">
                                    {remPrimary} <span className="text-xs font-normal text-indigo-500/60 dark:text-indigo-450/60">{batch.primaryUnit || 'BOX'}</span>
                                  </span>
                                  <span className="block text-xs text-indigo-500/80 dark:text-indigo-400/80">
                                    {batch.secondaryRemaining} {batch.secondaryUnit || 'LADI'}
                                  </span>
                                </>
                              )
                            })()}
                          </td>
                          <td className="py-2 px-4 font-mono">₹{batch.buyPriceWithoutTax}</td>
                          <td className="py-2 px-4">
                            <span className="text-slate-700 dark:text-slate-300 block text-xs">Exp: {batch.expiryDate}</span>
                            <span className="text-xs text-slate-500 dark:text-slate-400">Age: {batch.stockAgeDays} days</span>
                          </td>
                          <td className="py-2 px-4 font-mono text-emerald-600 dark:text-emerald-400 font-semibold">₹{batch.batchValue}</td>
                          <td className="py-2 px-4 text-center">
                            <div className="flex gap-2 justify-center">
                              <button
                                onClick={() => {
                                  const ratio = batch.secondaryPerPrimary && batch.secondaryPerPrimary > 0 ? batch.secondaryPerPrimary : 1
                                  setAdjustingBatch(batch)
                                  setAdjustPrimary(Math.floor(batch.secondaryRemaining / ratio).toString())
                                  setAdjustSecondary((batch.secondaryRemaining % ratio).toString())
                                  setAdjustOffer(batch.offerSecondaryRemaining?.toString() || '0')
                                  setAdjustBuyPrice(batch.buyPriceWithoutTax?.toString() || '0')
                                }}
                                className="p-2 rounded-lg bg-slate-100 hover:bg-indigo-600/10 dark:bg-slate-850 dark:hover:bg-indigo-600/30 text-slate-500 hover:text-indigo-600 dark:text-slate-400 dark:hover:text-indigo-400 transition"
                                title="Adjust Stock"
                              >
                                <Edit2 className="w-4 h-4" />
                              </button>
                              {batch.batchStatus !== 'WRITTEN_OFF' && (
                                <button
                                  onClick={() => setWriteOffTarget(batch.id)}
                                  className="p-2 rounded-lg bg-slate-100 hover:bg-rose-600/10 dark:bg-slate-850 dark:hover:bg-rose-600/30 text-slate-500 hover:text-rose-600 dark:text-slate-400 dark:hover:text-rose-450 transition"
                                  title="Write Off Expiry"
                                >
                                  <Trash2 className="w-4 h-4" />
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      ))
                    })()}
                  </tbody>
                </table>
              </div>

              {/* Pagination */}
              {batchTotalPages > 1 && (
                <div className="p-4 bg-slate-50 dark:bg-slate-950/80 border-t border-slate-200 dark:border-slate-800">
                  <Pagination page={batchPage} totalPages={batchTotalPages} totalElements={batchTotalElements} pageSize={15} onPageChange={setBatchPage} />
                </div>
              )}
            </div>
            )}

            {/* ─── INVOICE SUMMARY VIEW ─── */}
            {batchView === 'invoices' && (() => {
              // Group ALL batches by invoiceNumber (not just current page)
              const allBatches = allBatchList
              const grouped = {}
              allBatches.forEach(b => {
                const key = (b.invoiceNumber || '(No Invoice)') + '||' + (b.supplierName || '') + '||' + (b.supplierInvoiceDate || '')
                if (!grouped[key]) {
                  grouped[key] = {
                    invoiceNumber: b.invoiceNumber || '—',
                    supplierName: b.supplierName || '—',
                    supplierInvoiceDate: b.supplierInvoiceDate || null,
                    batches: []
                  }
                }
                grouped[key].batches.push(b)
              })
              const invoiceRows = Object.values(grouped).map(grp => {
                let totalExcl = 0, totalIncl = 0, totalItems = grp.batches.length
                grp.batches.forEach(b => {
                  const ratio = b.secondaryPerPrimary && b.secondaryPerPrimary > 0 ? b.secondaryPerPrimary : 1
                  const priceExcl = b.buyPriceWithoutTax ? (Number(b.buyPriceWithoutTax) / ratio) : 0
                  const priceIncl = b.buyPriceWithTax ? (Number(b.buyPriceWithTax) / ratio) : 0
                  totalExcl += (b.secondaryReceived || 0) * priceExcl
                  totalIncl += (b.secondaryReceived || 0) * priceIncl
                })
                return { ...grp, totalItems, totalExcl, totalGst: totalIncl - totalExcl, totalIncl }
              }).sort((a, b) => b.totalIncl - a.totalIncl)
              const grandExcl = invoiceRows.reduce((s, r) => s + r.totalExcl, 0)
              const grandGst = invoiceRows.reduce((s, r) => s + r.totalGst, 0)
              const grandIncl = invoiceRows.reduce((s, r) => s + r.totalIncl, 0)

              const paginatedInvoiceRows = invoiceRows.slice(invoicePage * INVOICE_PAGE_SIZE, (invoicePage + 1) * INVOICE_PAGE_SIZE)

              return (
                <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl overflow-hidden shadow-sm">
                  {/* Summary Cards */}
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 p-5 border-b border-slate-200 dark:border-slate-800 bg-gradient-to-r from-indigo-50 to-sky-50 dark:from-indigo-950/30 dark:to-sky-950/30">
                    <div className="text-center">
                      <div className="text-xs text-slate-500 dark:text-slate-400 font-semibold uppercase tracking-wider mb-1">Total Taxable</div>
                      <div className="text-2xl font-bold text-slate-800 dark:text-slate-100">₹{grandExcl.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</div>
                    </div>
                    <div className="text-center">
                      <div className="text-xs text-slate-500 dark:text-slate-400 font-semibold uppercase tracking-wider mb-1">Total GST</div>
                      <div className="text-2xl font-bold text-amber-600 dark:text-amber-400">₹{grandGst.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</div>
                    </div>
                    <div className="text-center">
                      <div className="text-xs text-slate-500 dark:text-slate-400 font-semibold uppercase tracking-wider mb-1">Grand Total (Incl. GST)</div>
                      <div className="text-2xl font-bold text-emerald-600 dark:text-emerald-400">₹{grandIncl.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</div>
                    </div>
                  </div>
                  {/* Table */}
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-sm">
                      <thead>
                        <tr className="bg-slate-50 dark:bg-slate-950 border-b border-slate-200 dark:border-slate-800 text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                          <th className="py-3 px-5">#</th>
                          <th className="py-3 px-5">Invoice No.</th>
                          <th className="py-3 px-5">Supplier</th>
                          <th className="py-3 px-5">Invoice Date</th>
                          <th className="py-3 px-5 text-center">Items (SKUs)</th>
                          <th className="py-3 px-5 text-right">Taxable Amt</th>
                          <th className="py-3 px-5 text-right">GST</th>
                          <th className="py-3 px-5 text-right">Grand Total</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                        {paginatedInvoiceRows.map((inv, idx) => (
                          <tr key={idx} className="hover:bg-indigo-50/30 dark:hover:bg-indigo-900/10 transition-colors">
                            <td className="py-3 px-5 text-slate-400 dark:text-slate-500 font-mono text-xs">{invoicePage * INVOICE_PAGE_SIZE + idx + 1}</td>
                            <td className="py-3 px-5">
                              <span className="font-mono font-semibold text-indigo-600 dark:text-indigo-400 text-sm">{inv.invoiceNumber}</span>
                            </td>
                            <td className="py-3 px-5 text-slate-700 dark:text-slate-300 font-medium">{inv.supplierName}</td>
                            <td className="py-3 px-5 text-slate-500 dark:text-slate-400 text-xs">{inv.supplierInvoiceDate || <span className="italic text-slate-400">No Date</span>}</td>
                            <td className="py-3 px-5 text-center">
                              <span className="inline-flex items-center justify-center w-8 h-8 rounded-full bg-indigo-100 dark:bg-indigo-900/40 text-indigo-700 dark:text-indigo-400 font-bold text-xs">{inv.totalItems}</span>
                            </td>
                            <td className="py-3 px-5 text-right font-mono text-slate-700 dark:text-slate-300">₹{inv.totalExcl.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</td>
                            <td className="py-3 px-5 text-right font-mono text-amber-600 dark:text-amber-400">₹{inv.totalGst.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</td>
                            <td className="py-3 px-5 text-right font-mono font-bold text-emerald-600 dark:text-emerald-400">₹{inv.totalIncl.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</td>
                          </tr>
                        ))}
                      </tbody>
                      {/* Grand Total Footer */}
                      <tfoot>
                        <tr className="bg-slate-50 dark:bg-slate-900/80 border-t-2 border-slate-300 dark:border-slate-700 font-bold text-sm">
                          <td colSpan={4} className="py-4 px-5 text-slate-600 dark:text-slate-300 uppercase tracking-wide text-xs">GRAND TOTAL — {invoiceRows.length} Invoices</td>
                          <td className="py-4 px-5 text-center text-slate-700 dark:text-slate-300">{invoiceRows.reduce((s, r) => s + r.totalItems, 0)}</td>
                          <td className="py-4 px-5 text-right text-slate-800 dark:text-slate-100">₹{grandExcl.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</td>
                          <td className="py-4 px-5 text-right text-amber-600 dark:text-amber-400">₹{grandGst.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</td>
                          <td className="py-4 px-5 text-right text-emerald-600 dark:text-emerald-400">₹{grandIncl.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</td>
                        </tr>
                      </tfoot>
                    </table>
                  </div>

                  {/* Pagination */}
                  {invoiceRows.length > INVOICE_PAGE_SIZE && (
                    <div className="p-4 bg-slate-50 dark:bg-slate-950/80 border-t border-slate-200 dark:border-slate-800">
                      <Pagination 
                        page={invoicePage} 
                        totalPages={Math.ceil(invoiceRows.length / INVOICE_PAGE_SIZE)} 
                        totalElements={invoiceRows.length} 
                        pageSize={INVOICE_PAGE_SIZE} 
                        onPageChange={setInvoicePage} 
                      />
                    </div>
                  )}
                </div>
              )
            })()}
          </motion.div>
        )}

        {/* 5. STOCK MOVEMENT LEDGER TAB */}
        {activeTab === 'movements' && (
          <motion.div
            initial={{ opacity: 0, y: 15 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -15 }}
            className="space-y-6"
          >
            {/* Filter ledger */}
            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-6 rounded-2xl flex flex-wrap gap-4 items-center shadow-sm">
              <select
                value={selectedMovementType}
                onChange={(e) => setSelectedMovementType(e.target.value)}
                className="px-4 py-2.5 bg-slate-55 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-xl text-slate-800 dark:text-slate-300 focus:ring-2 focus:ring-indigo-500 text-sm"
              >
                <option value="">All Transactions</option>
                <option value="PURCHASE">Purchase</option>
                <option value="SALE">Sale</option>
                <option value="RETURN_IN">Return In</option>
                <option value="RETURN_OUT">Return Out</option>
                <option value="DAMAGE">Damage</option>
                <option value="EXPIRY">Expiry</option>
                <option value="ADJUSTMENT">Adjustment</option>
                <option value="OPENING_STOCK">Opening Stock</option>
              </select>
            </div>

            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl overflow-hidden shadow-sm">
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-slate-50 dark:bg-slate-950 border-b border-slate-200 dark:border-slate-800 text-slate-750 dark:text-slate-305 font-semibold text-xs uppercase tracking-wider">
                      <th className="py-4 px-6">Timestamp</th>
                      <th className="py-4 px-6">Product & Batch</th>
                      <th className="py-4 px-6">Movement Action</th>
                      <th className="py-4 px-6">Quantity</th>
                      <th className="py-4 px-6">Stock Age History</th>
                      <th className="py-4 px-6">Value Details</th>
                      <th className="py-4 px-6">User / Reference</th>
                      <th className="py-4 px-6">Remarks</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 dark:divide-slate-800 text-sm">
                    {movementList.length === 0 ? (
                      <tr>
                        <td colSpan={8} className="py-12 text-center text-slate-450 dark:text-slate-500">
                          No stock movements registered.
                        </td>
                      </tr>
                    ) : (
                      movementList.map(move => (
                        <tr key={move.id} className="hover:bg-slate-100/40 dark:hover:bg-slate-800/40 transition-colors">
                          <td className="py-4 px-6 text-slate-500 dark:text-slate-400 font-mono text-xs">
                            {new Date(move.timestamp).toLocaleString()}
                          </td>
                          <td className="py-4 px-6">
                            <span className="font-semibold text-slate-800 dark:text-slate-100 block">{move.product?.name}</span>
                            <span className="text-xs text-indigo-600 dark:text-indigo-400 font-mono">Batch: {move.batch?.batchNumber || 'N/A'}</span>
                          </td>
                          <td className="py-4 px-6">
                            <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                              move.movementType === 'PURCHASE' || move.movementType === 'OPENING_STOCK' ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400' :
                              move.movementType === 'SALE' ? 'bg-sky-500/10 text-sky-600 dark:text-sky-400' :
                              move.movementType === 'RETURN_IN' ? 'bg-indigo-500/10 text-indigo-600 dark:text-indigo-400' :
                              'bg-rose-500/10 text-rose-600 dark:text-rose-450'
                            }`}>
                              {move.movementType}
                            </span>
                          </td>
                          <td className={`py-4 px-6 font-mono font-bold ${move.quantity > 0 ? 'text-emerald-600 dark:text-emerald-450' : 'text-rose-600 dark:text-rose-450'}`}>
                            {move.quantity > 0 ? `+${move.quantity}` : move.quantity}
                          </td>
                          <td className="py-4 px-6 font-mono text-slate-500 dark:text-slate-400 text-xs">
                            {move.quantityBefore} → {move.quantityAfter}
                          </td>
                          <td className="py-4 px-6 font-mono">
                            <span className="block text-slate-700 dark:text-slate-300">₹{move.unitPrice}/unit</span>
                            <span className="text-xs text-slate-500 dark:text-slate-400">Total: ₹{move.totalValue}</span>
                          </td>
                          <td className="py-4 px-6">
                            <span className="text-slate-700 dark:text-slate-300 block">{move.username}</span>
                            <span className="text-xs text-slate-500 dark:text-slate-400">Ref: {move.referenceNumber || 'N/A'}</span>
                          </td>
                          <td className="py-4 px-6 text-slate-500 dark:text-slate-400 text-xs">{move.remarks || '-'}</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </motion.div>
        )}

        {/* 6. AUDIT LOGS TAB */}
        {activeTab === 'audit' && isAdmin && (
          <motion.div
            initial={{ opacity: 0, y: 15 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -15 }}
            className="space-y-6"
          >
            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl overflow-hidden shadow-sm">
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-slate-50 dark:bg-slate-950 border-b border-slate-200 dark:border-slate-800 text-slate-750 dark:text-slate-305 font-semibold text-xs uppercase tracking-wider">
                      <th className="py-4 px-6">Changed On</th>
                      <th className="py-4 px-6">Product / Batch</th>
                      <th className="py-4 px-6">Old Value</th>
                      <th className="py-4 px-6">New Value</th>
                      <th className="py-4 px-6">Changed By</th>
                      <th className="py-4 px-6">Reason</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 dark:divide-slate-800 text-sm">
                    {auditLogs.length === 0 ? (
                      <tr>
                        <td colSpan={6} className="py-12 text-center text-slate-450 dark:text-slate-500">
                          No audit entries registered.
                        </td>
                      </tr>
                    ) : (
                      auditLogs.map(log => (
                        <tr key={log.id} className="hover:bg-slate-100/40 dark:hover:bg-slate-800/40 transition-colors">
                          <td className="py-4 px-6 text-slate-500 dark:text-slate-400 font-mono text-xs">
                            {new Date(log.timestamp).toLocaleString()}
                          </td>
                          <td className="py-4 px-6">
                            <span className="font-semibold text-slate-800 dark:text-slate-100 block">{log.productName}</span>
                            <span className="text-xs text-indigo-600 dark:text-indigo-400 font-semibold font-mono">Batch: {log.batchNumber}</span>
                          </td>
                          <td className="py-4 px-6 font-mono text-rose-600 dark:text-rose-400">{log.oldSecondaryRemaining}</td>
                          <td className="py-4 px-6 font-mono text-emerald-600 dark:text-emerald-450">{log.newSecondaryRemaining}</td>
                          <td className="py-4 px-6 text-slate-700 dark:text-slate-300 font-medium">{log.adjustedBy}</td>
                          <td className="py-4 px-6 text-slate-500 dark:text-slate-405 text-xs">{log.reason}</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </motion.div>
        )}

        {/* 7. REPORTS MODULE TAB */}
        {activeTab === 'reports' && (
          <motion.div
            initial={{ opacity: 0, y: 15 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -15 }}
            className="space-y-6"
          >
            {/* Pick Report View */}
            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-6 rounded-2xl flex flex-wrap gap-4 items-center justify-between shadow-sm">
              <div className="flex gap-4 items-center">
                <label className="text-slate-500 dark:text-slate-400 text-sm font-semibold">Report Type:</label>
                <select
                  value={selectedReport}
                  onChange={(e) => {
                    setSelectedReport(e.target.value)
                    setReportPage(0)
                  }}
                  className="px-4 py-2.5 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-xl text-slate-800 dark:text-slate-300 focus:ring-2 focus:ring-indigo-500 text-sm"
                >
                  <option value="valuation">Inventory Valuation Report</option>
                  <option value="expiry">Expiry & Expiry Risk Report</option>
                  <option value="aging">Stock Aging Report</option>
                  <option value="profitability">Category Profitability Report</option>
                </select>
              </div>

              <button
                onClick={exportToCSV}
                disabled={exporting}
                className={`flex items-center gap-2 bg-gradient-to-r from-emerald-600 to-teal-600 text-white font-semibold py-2 px-5 rounded-xl text-sm shadow hover:scale-105 transition duration-300 ${exporting ? 'opacity-50 cursor-not-allowed' : ''}`}
              >
                <Download className="w-4 h-4" /> {exporting ? 'Exporting...' : 'Export Report (CSV)'}
              </button>
            </div>

            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl overflow-hidden shadow-sm">
              <div className="overflow-x-auto">
                {selectedReport === 'valuation' && (
                  <table className="w-full text-left border-collapse">
                    <thead>
                      <tr className="bg-slate-50 dark:bg-slate-950 border-b border-slate-200 dark:border-slate-800 text-slate-750 dark:text-slate-305 font-semibold text-xs uppercase tracking-wider">
                        <th className="py-4 px-6">Product</th>
                        <th className="py-4 px-6">Category</th>
                        <th className="py-4 px-6">Available Stock</th>
                        <th className="py-4 px-6">Avg Cost Price</th>
                        <th className="py-4 px-6">Selling Price</th>
                        <th className="py-4 px-6">Profit Potential</th>
                        <th className="py-4 px-6 text-center">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-200 dark:divide-slate-800 text-sm">
                      {reportLoading ? (
                        <tr><td colSpan={7} className="py-8 text-center text-slate-500">Generating valuation metrics...</td></tr>
                      ) : reportData.map(row => (
                        <tr key={row.productId} className="hover:bg-slate-100/40 dark:hover:bg-slate-800/40 transition-colors">
                          <td className="py-4 px-6 font-semibold text-slate-800 dark:text-slate-200">{row.productName}</td>
                          <td className="py-4 px-6 text-slate-500 dark:text-slate-400">{row.category}</td>
                          <td className="py-4 px-6 font-mono text-slate-800 dark:text-slate-200">{row.currentStock} Units</td>
                          <td className="py-4 px-6 font-mono text-slate-700 dark:text-slate-300">₹{row.avgCost}</td>
                          <td className="py-4 px-6 font-mono text-slate-700 dark:text-slate-300">₹{row.sellingPrice}</td>
                          <td className="py-4 px-6 font-mono text-emerald-600 dark:text-emerald-450 font-semibold">₹{(row.currentStock * (row.sellingPrice - row.avgCost)).toFixed(2)}</td>
                          <td className="py-4 px-6 text-center">
                            <span className="text-xs px-3 py-1 rounded-full bg-slate-100 dark:bg-slate-950 border border-slate-200 dark:border-slate-850 text-slate-700 dark:text-slate-300 font-semibold">{row.status}</span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}

                {selectedReport === 'expiry' && (
                  <table className="w-full text-left border-collapse">
                    <thead>
                      <tr className="bg-slate-50 dark:bg-slate-950 border-b border-slate-200 dark:border-slate-800 text-slate-750 dark:text-slate-305 font-semibold text-xs uppercase tracking-wider">
                        <th className="py-4 px-6">Batch Number</th>
                        <th className="py-4 px-6">Product</th>
                        <th className="py-4 px-6">Expiry Date</th>
                        <th className="py-4 px-6">Remaining Qty</th>
                        <th className="py-4 px-6">Risk Value (Cost)</th>
                        <th className="py-4 px-6">Days to Expiry</th>
                        <th className="py-4 px-6 text-center">Risk level</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-200 dark:divide-slate-800 text-sm">
                      {reportLoading ? (
                        <tr><td colSpan={7} className="py-8 text-center text-slate-500">Checking expiries...</td></tr>
                      ) : reportData.map(row => (
                        <tr key={row.batchId} className="hover:bg-slate-100/40 dark:hover:bg-slate-800/40 transition-colors">
                          <td className="py-4 px-6 font-semibold text-indigo-600 dark:text-indigo-400 font-mono">{row.batchNumber}</td>
                          <td className="py-4 px-6 text-slate-800 dark:text-slate-200">{row.productName}</td>
                          <td className="py-4 px-6 font-mono text-slate-700 dark:text-slate-300">{row.expiryDate}</td>
                          <td className="py-4 px-6 font-mono">{row.remainingQty}</td>
                          <td className="py-4 px-6 font-mono text-rose-600 dark:text-rose-450">₹{row.costValue}</td>
                          <td className="py-4 px-6 font-mono">{row.daysToExpiry}</td>
                          <td className="py-4 px-6 text-center">
                            <span className={`text-xs px-2.5 py-0.5 rounded-full font-bold ${
                              row.riskBucket.includes('Expired') ? 'bg-red-500/10 text-red-600 dark:text-red-400' : 'bg-amber-500/10 text-amber-600 dark:text-amber-400'
                            }`}>{row.riskBucket}</span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}

                {selectedReport === 'aging' && (
                  <table className="w-full text-left border-collapse">
                    <thead>
                      <tr className="bg-slate-50 dark:bg-slate-950 border-b border-slate-200 dark:border-slate-800 text-slate-750 dark:text-slate-305 font-semibold text-xs uppercase tracking-wider">
                        <th className="py-4 px-6">Batch Number</th>
                        <th className="py-4 px-6">Product</th>
                        <th className="py-4 px-6">Received On Date</th>
                        <th className="py-4 px-6">Remaining Qty</th>
                        <th className="py-4 px-6">Age (Days)</th>
                        <th className="py-4 px-6 text-center">Age Classification</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-200 dark:divide-slate-800 text-sm">
                      {reportLoading ? (
                        <tr><td colSpan={6} className="py-8 text-center text-slate-500">Scanning inventory age...</td></tr>
                      ) : reportData.map((row, i) => (
                        <tr key={i} className="hover:bg-slate-100/40 dark:hover:bg-slate-800/40 transition-colors">
                          <td className="py-4 px-6 font-semibold text-indigo-600 dark:text-indigo-400 font-mono">{row.batchNumber}</td>
                          <td className="py-4 px-6 text-slate-800 dark:text-slate-200">{row.productName}</td>
                          <td className="py-4 px-6 font-mono text-slate-700 dark:text-slate-300">{row.stockReceivedDate}</td>
                          <td className="py-4 px-6 font-mono">{row.remainingQty}</td>
                          <td className="py-4 px-6 font-mono">{row.ageDays}</td>
                          <td className="py-4 px-6 text-center">
                            <span className="text-xs px-2.5 py-0.5 rounded-full bg-slate-100 dark:bg-slate-950 border border-slate-200 dark:border-slate-850 text-slate-700 dark:text-slate-300 font-bold">{row.ageBucket}</span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}

                {selectedReport === 'profitability' && (
                  <table className="w-full text-left border-collapse">
                    <thead>
                      <tr className="bg-slate-50 dark:bg-slate-950 border-b border-slate-200 dark:border-slate-800 text-slate-750 dark:text-slate-305 font-semibold text-xs uppercase tracking-wider">
                        <th className="py-4 px-6">Category</th>
                        <th className="py-4 px-6">Total Asset Cost</th>
                        <th className="py-4 px-6">Sales Potential Valuation</th>
                        <th className="py-4 px-6">Gross Margin Profit Potential</th>
                        <th className="py-4 px-6 text-center">Margin %</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-200 dark:divide-slate-800 text-sm">
                      {reportLoading ? (
                        <tr><td colSpan={5} className="py-8 text-center text-slate-500">Loading profitability details...</td></tr>
                      ) : reportData.map((row, i) => (
                        <tr key={i} className="hover:bg-slate-100/40 dark:hover:bg-slate-800/40 transition-colors">
                          <td className="py-4 px-6 font-bold text-slate-800 dark:text-slate-200">{row.categoryName}</td>
                          <td className="py-4 px-6 font-mono text-slate-700 dark:text-slate-300">₹{row.costValue}</td>
                          <td className="py-4 px-6 font-mono text-indigo-600 dark:text-indigo-400">₹{row.sellingValue}</td>
                          <td className="py-4 px-6 font-mono text-emerald-600 dark:text-emerald-450 font-bold">₹{row.profitPotential}</td>
                          <td className="py-4 px-6 text-center">
                            <span className="text-xs px-3 py-1 rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20 font-extrabold">{row.marginPercent}%</span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>

              {/* Pagination */}
              {reportTotalPages > 1 && (
                <div className="p-4 bg-slate-50 dark:bg-slate-950/80 border-t border-slate-200 dark:border-slate-800">
                  <Pagination page={reportPage} totalPages={reportTotalPages} totalElements={reportTotalElements} pageSize={15} onPageChange={setReportPage} />
                </div>
              )}
            </div>
          </motion.div>
        )}

        {/* 8. BUSINESS INTELLIGENCE TAB */}
        {activeTab === 'bi' && (
          <motion.div
            initial={{ opacity: 0, y: 15 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -15 }}
            className="space-y-8"
          >
            {biLoading || !biHealth ? (
              <div className="py-12 text-center text-slate-500">Computing advanced BI analytics metrics...</div>
            ) : (
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                {/* Score and classification details */}
                <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-6 rounded-2xl flex flex-col justify-between shadow-sm">
                  <div>
                    <h3 className="text-xl font-bold mb-4 flex items-center gap-2">
                      <Award className="w-5 h-5 text-indigo-500" /> Overall Health Score
                    </h3>
                    <div className="text-center py-6">
                      <span className="text-7xl font-black bg-gradient-to-r from-emerald-500 to-indigo-500 bg-clip-text text-transparent">{biHealth.overallScore}</span>
                      <span className="text-xl text-slate-500 dark:text-slate-400 block mt-2">Classified as: <span className="font-bold text-slate-700 dark:text-slate-300">{biHealth.classification}</span></span>
                    </div>
                  </div>
                  <div className="space-y-3 border-t border-slate-200 dark:border-slate-800 pt-5">
                    {[
                      { label: 'Dead Stock Score',  score: biHealth.deadStockScore,  max: 25 },
                      { label: 'Expiry Risk Score',  score: biHealth.expiryScore,     max: 25 },
                      { label: 'Low Stock Score',    score: biHealth.lowStockScore,   max: 20 },
                      { label: 'Turnover Score',     score: biHealth.turnoverScore,   max: 20 },
                      { label: 'Accuracy Score',     score: biHealth.accuracyScore,   max: 10 },
                    ].map(({ label, score, max }) => {
                      const pct = max > 0 ? score / max : 0
                      const color = pct >= 0.8
                        ? 'text-emerald-500 dark:text-emerald-400'
                        : pct >= 0.5
                        ? 'text-amber-500 dark:text-amber-400'
                        : 'text-rose-500 dark:text-rose-400'
                      const dot = pct >= 0.8 ? '🟢' : pct >= 0.5 ? '🟡' : '🔴'
                      return (
                        <div key={label} className="flex justify-between items-center text-sm">
                          <span className="text-slate-500 dark:text-slate-400 flex items-center gap-1.5">
                            <span>{dot}</span>{label}:
                          </span>
                          <span className={`font-semibold tabular-nums ${color}`}>
                            {Math.round(score * 10) / 10} / {max}
                          </span>
                        </div>
                      )
                    })}
                    <div className="flex justify-between items-center text-sm font-bold border-t border-slate-200 dark:border-slate-700 pt-3 mt-1">
                      <span className="text-slate-700 dark:text-slate-200">Total Score:</span>
                      <span className="text-indigo-600 dark:text-indigo-400 tabular-nums">{biHealth.overallScore} / 100</span>
                    </div>
                  </div>
                </div>

                {/* Suggestions Engine Widget */}
                <div className="lg:col-span-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-6 rounded-2xl shadow-sm flex flex-col">
                  <h3 className="text-xl font-bold mb-3 flex items-center gap-2">
                    <ShieldAlert className="w-5 h-5 text-indigo-500" /> Purchase Recommendation Engine
                  </h3>

                  {/* No-sales-data banner — shown if ALL items have 0 daily sales */}
                  {biReorders.length > 0 && biReorders.every(r => r.avgDailySales === 0) && (
                    <div className="flex items-start gap-2.5 mb-4 px-3.5 py-2.5 rounded-lg bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-700/40 text-amber-700 dark:text-amber-400 text-xs">
                      <span className="text-base leading-none mt-0.5">⚠️</span>
                      <span>
                        <span className="font-semibold">No sales data available yet.</span>{' '}
                        Suggestions below are based on reorder levels only — they will become accurate once sales transactions are recorded.
                      </span>
                    </div>
                  )}

                  <div className="overflow-y-auto max-h-[320px] flex-1 divide-y divide-slate-200 dark:divide-slate-800">
                    {biReorders.length === 0 ? (
                      <p className="text-slate-500 dark:text-slate-400 text-sm py-4">No reorder recommendations needed right now.</p>
                    ) : (
                      biReorders.map((reorder, i) => {
                        const noSales = reorder.avgDailySales === 0
                        return (
                          <div key={i} className="flex justify-between items-center py-3.5 gap-4">
                            <div className="flex-1 min-w-0">
                              <span className="font-bold text-slate-800 dark:text-slate-200 block truncate">{reorder.productName}</span>
                              <span className="text-xs text-slate-500 dark:text-slate-400">
                                Stock: {reorder.currentStock} units
                                {noSales
                                  ? <span className="ml-2 text-amber-500 dark:text-amber-400 font-medium">· No sales history</span>
                                  : <span className="ml-2">· Avg {reorder.avgDailySales.toFixed(2)} units/day</span>
                                }
                              </span>
                            </div>
                            <div className="text-right flex-shrink-0">
                              {noSales ? (
                                <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-400 dark:text-slate-500 border border-slate-200 dark:border-slate-700 text-xs font-medium">
                                  ⚠️ No data
                                </span>
                              ) : (
                                <>
                                  <span className="text-xs text-slate-500 dark:text-slate-400 block mb-1">Suggest Order Qty:</span>
                                  <span className="px-3 py-1 rounded bg-indigo-500/10 dark:bg-indigo-500/20 text-indigo-600 dark:text-indigo-400 border border-indigo-500/20 font-bold font-mono">
                                    +{reorder.suggestedReorderQty}
                                  </span>
                                </>
                              )}
                            </div>
                          </div>
                        )
                      })
                    )}
                  </div>
                </div>
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>

      {/* Adjust Stock Modal */}
      {adjustingBatch && (
        <Modal isOpen={!!adjustingBatch} title="Correct & Adjust Batch Quantity" onClose={() => setAdjustingBatch(null)}>
          <form onSubmit={handleAdjustSubmit} className="space-y-6">
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Editing quantities will write a line item to the movement history logs and adjustment audit tables.
            </p>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="text-xs text-slate-500 dark:text-slate-400 block mb-1 font-semibold">Remaining Primary units</label>
                <input
                  type="number"
                  value={adjustPrimary}
                  onChange={(e) => setAdjustPrimary(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg text-slate-900 dark:text-slate-100"
                />
              </div>
              <div>
                <label className="text-xs text-slate-500 dark:text-slate-400 block mb-1 font-semibold">Remaining Secondary units</label>
                <input
                  type="number"
                  value={adjustSecondary}
                  onChange={(e) => setAdjustSecondary(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg text-slate-900 dark:text-slate-100"
                />
              </div>
              <div>
                <label className="text-xs text-slate-500 dark:text-slate-400 block mb-1 font-semibold">Offer Secondary Remaining</label>
                <input
                  type="number"
                  value={adjustOffer}
                  onChange={(e) => setAdjustOffer(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg text-slate-900 dark:text-slate-100"
                />
              </div>
              <div>
                <label className="text-xs text-slate-500 dark:text-slate-400 block mb-1 font-semibold">Cost Price (Excl. Tax)</label>
                <input
                  type="number"
                  step="0.01"
                  value={adjustBuyPrice}
                  onChange={(e) => setAdjustBuyPrice(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg text-slate-900 dark:text-slate-100"
                />
              </div>
            </div>

            <div>
              <label className="text-xs text-slate-500 dark:text-slate-400 block mb-1 font-semibold">Correction Reason <span className="text-red-500">*</span></label>
              <input
                type="text"
                required
                value={adjustReason}
                onChange={(e) => setAdjustReason(e.target.value)}
                placeholder="e.g. Audit mismatch, damage write-off..."
                className="w-full px-3 py-2 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg text-slate-900 dark:text-slate-100"
              />
            </div>

            <div className="flex gap-3 justify-end pt-4 border-t border-slate-200 dark:border-slate-800">
              <button
                type="button"
                onClick={() => setAdjustingBatch(null)}
                className="px-4 py-2 bg-slate-200 hover:bg-slate-300 dark:bg-slate-800 dark:hover:bg-slate-700 rounded-lg font-medium text-slate-700 dark:text-slate-300"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={adjusting}
                className="px-5 py-2 bg-indigo-650 hover:bg-indigo-600 text-white font-semibold rounded-lg shadow"
              >
                {adjusting ? 'Saving adjustments...' : 'Apply Correction'}
              </button>
            </div>
          </form>
        </Modal>
      )}

      {/* Confirm Write Off Dialog */}
      {writeOffTarget && (
        <ConfirmDialog
          title="Write Off / Delete Batch Stock?"
          description="This action will set the batch's available stock to 0 and log an audit write-off entry. Active batches cannot be hard-deleted to prevent ledger history mismatch."
          onConfirm={() => handleWriteOff(writeOffTarget)}
          onCancel={() => setWriteOffTarget(null)}
        />
      )}

      {/* Quick Add Product Modal */}
      {showQuickProductModal && (
        <Modal isOpen={showQuickProductModal} title="Create Product Database SKU" onClose={() => setShowQuickProductModal(false)}>
          <form onSubmit={handleQuickProductSubmit} className="space-y-4 text-xs md:text-sm">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="form-label text-slate-500 dark:text-slate-400">SKU/Product Name *</label>
                <input
                  type="text"
                  required
                  value={quickProductForm.name}
                  onChange={e => setQuickProductForm(f => ({ ...f, name: e.target.value }))}
                  className="w-full px-3 py-2 bg-slate-55 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg"
                />
              </div>
              <div>
                <label className="form-label text-slate-500 dark:text-slate-400">Brand *</label>
                <input
                  type="text"
                  required
                  value={quickProductForm.brand}
                  onChange={e => setQuickProductForm(f => ({ ...f, brand: e.target.value }))}
                  className="w-full px-3 py-2 bg-slate-55 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg"
                />
              </div>
              <div>
                <label className="form-label text-slate-500 dark:text-slate-400">Category *</label>
                <select
                  value={quickProductForm.category}
                  onChange={e => setQuickProductForm(f => ({ ...f, category: e.target.value }))}
                  className="w-full px-3 py-2 bg-slate-55 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg"
                >
                  {CATEGORIES.map(cat => (
                    <option key={cat} value={cat}>{cat}</option>
                  ))}
                </select>
              </div>

              {quickProductForm.category === 'OTHER' && (
                <div>
                  <label className="form-label text-slate-500 dark:text-slate-400">Specify Category</label>
                  <input
                    type="text"
                    required
                    value={quickProductForm.otherCategoryDetail}
                    onChange={e => setQuickProductForm(f => ({ ...f, otherCategoryDetail: e.target.value }))}
                    className="w-full px-3 py-2 bg-slate-55 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg"
                  />
                </div>
              )}

              <div>
                <label className="form-label text-slate-500 dark:text-slate-400">GST Rate %</label>
                <select
                  value={quickProductForm.gstPercent}
                  onChange={e => handleQuickGstChange(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-55 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg"
                >
                  <option value="0">0%</option>
                  <option value="5">5%</option>
                  <option value="12">12%</option>
                  <option value="18">18%</option>
                  <option value="28">28%</option>
                </select>
              </div>

              <div className="flex items-center gap-2 mt-4 col-span-2">
                <input
                  type="checkbox"
                  id="cess-check"
                  checked={quickProductForm.isCessApplicable || false}
                  onChange={e => handleQuickCessApplicableChange(e.target.checked)}
                />
                <label htmlFor="cess-check" className="text-xs font-semibold text-slate-650 dark:text-slate-400">Cess Tax Applicable</label>
              </div>

              {quickProductForm.isCessApplicable && (
                <div>
                  <label className="form-label text-slate-500 dark:text-slate-400">Cess Rate %</label>
                  <input
                    type="number"
                    value={quickProductForm.cessPercent}
                    onChange={e => handleQuickCessChange(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-55 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg"
                  />
                </div>
              )}

              <div>
                <label className="form-label text-slate-500 dark:text-slate-400">Primary Unit *</label>
                <select
                  value={quickProductForm.primaryUnit}
                  onChange={e => setQuickProductForm(f => ({ ...f, primaryUnit: e.target.value }))}
                  className="w-full px-3 py-2 bg-slate-55 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg"
                >
                  <option value="BOX">BOX</option>
                  <option value="CRATE">CRATE</option>
                  <option value="CASE">CASE</option>
                </select>
              </div>

              <div>
                <label className="form-label text-slate-500 dark:text-slate-400">Secondary Unit *</label>
                <select
                  value={quickProductForm.secondaryUnit}
                  onChange={e => setQuickProductForm(f => ({ ...f, secondaryUnit: e.target.value }))}
                  className="w-full px-3 py-2 bg-slate-55 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg"
                >
                  <option value="LADI">LADI</option>
                  <option value="PACK">PACK</option>
                  <option value="BOTTLE">BOTTLE</option>
                  <option value="OTHER">OTHER</option>
                </select>
              </div>

              {quickProductForm.secondaryUnit === 'OTHER' && (
                <div>
                  <label className="form-label text-slate-500 dark:text-slate-400">Specify Secondary Unit</label>
                  <input
                    type="text"
                    required
                    value={quickProductForm.customSecondaryUnit || ''}
                    onChange={e => setQuickProductForm(f => ({ ...f, customSecondaryUnit: e.target.value.toUpperCase() }))}
                    className="w-full px-3 py-2 bg-slate-55 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg"
                  />
                </div>
              )}

              <div>
                <label className="form-label text-slate-500 dark:text-slate-400">Loose per BOX *</label>
                <input
                  type="number"
                  required
                  value={quickProductForm.secondaryPerPrimary}
                  onChange={e => setQuickProductForm(f => ({ ...f, secondaryPerPrimary: e.target.value }))}
                  className="w-full px-3 py-2 bg-slate-55 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg"
                />
              </div>

              <div>
                <label className="form-label text-slate-500 dark:text-slate-400">Buy Price (Excl. Tax) ₹</label>
                <input
                  type="number"
                  step="0.01"
                  value={quickProductForm.buyPriceWithoutTax}
                  onChange={e => handleQuickPriceChange('without', e.target.value)}
                  className="w-full px-3 py-2 bg-slate-55 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg"
                />
              </div>

              <div>
                <label className="form-label text-slate-500 dark:text-slate-400">Buy Price (Incl. Tax) ₹</label>
                <input
                  type="number"
                  step="0.01"
                  value={quickProductForm.buyPriceWithTax}
                  onChange={e => handleQuickPriceChange('with', e.target.value)}
                  className="w-full px-3 py-2 bg-slate-55 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg"
                />
              </div>

              <div>
                <label className="form-label text-slate-550 dark:text-slate-400">Sell (Primary - {quickProductForm.primaryUnit}) (Excl. Tax) ₹</label>
                <input
                  type="number"
                  step="0.01"
                  value={quickProductForm.sellPricePrimaryExcl}
                  onChange={e => handleQuickSellPriceChange('primary', 'excl', e.target.value)}
                  className="w-full px-3 py-2 bg-slate-55 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg"
                />
              </div>

              <div>
                <label className="form-label text-slate-550 dark:text-slate-400">Sell (Primary - {quickProductForm.primaryUnit}) (Incl. Tax) ₹ *</label>
                <input
                  type="number"
                  step="0.01"
                  required
                  value={quickProductForm.sellPricePrimaryIncl}
                  onChange={e => handleQuickSellPriceChange('primary', 'incl', e.target.value)}
                  className="w-full px-3 py-2 bg-slate-55 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg font-bold"
                />
              </div>

              <div>
                <label className="form-label text-slate-550 dark:text-slate-400">Sell (Secondary - {quickProductForm.secondaryUnit === 'OTHER' ? (quickProductForm.customSecondaryUnit || 'OTHER') : quickProductForm.secondaryUnit}) (Excl. Tax) ₹</label>
                <input
                  type="number"
                  step="0.01"
                  value={quickProductForm.sellPriceSecondaryExcl}
                  onChange={e => handleQuickSellPriceChange('secondary', 'excl', e.target.value)}
                  className="w-full px-3 py-2 bg-slate-55 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg"
                />
              </div>

              <div>
                <label className="form-label text-slate-550 dark:text-slate-400">Sell (Secondary - {quickProductForm.secondaryUnit === 'OTHER' ? (quickProductForm.customSecondaryUnit || 'OTHER') : quickProductForm.secondaryUnit}) (Incl. Tax) ₹ *</label>
                <input
                  type="number"
                  step="0.01"
                  required
                  value={quickProductForm.sellPriceSecondaryIncl}
                  onChange={e => handleQuickSellPriceChange('secondary', 'incl', e.target.value)}
                  className="w-full px-3 py-2 bg-slate-55 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg font-bold"
                />
              </div>
            </div>

            <div className="flex gap-3 justify-end pt-4 border-t border-slate-200 dark:border-slate-800">
              <button
                type="button"
                onClick={() => setShowQuickProductModal(false)}
                className="px-4 py-2 bg-slate-200 hover:bg-slate-305 dark:bg-slate-800 dark:hover:bg-slate-700 rounded-lg font-medium text-slate-700 dark:text-slate-300"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-5 py-2 bg-indigo-650 hover:bg-indigo-600 text-white font-semibold rounded-lg shadow"
              >
                Create and Map SKU
              </button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  )
}

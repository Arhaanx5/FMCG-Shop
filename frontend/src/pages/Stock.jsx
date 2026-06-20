import { useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { 
  Plus, AlertTriangle, Clock, Package, Edit2, History, Trash2, 
  Settings, PlayCircle, CheckCircle, XCircle, Camera, Upload, 
  Calendar, FileText, ArrowRightLeft, TrendingUp, ShieldAlert,
  BarChart2, Award, Download, Filter, Search, RotateCcw
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

  useEffect(() => {
    const handleResize = () => setIsMobile(window.innerWidth < 768)
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  // Tabs: overview (Inventory), dashboard, receive, batches, movements, audit, reports, bi, automation
  const [activeTab, setActiveTab] = useState('overview')

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

  // 2. Receive Stock (Manual/Bulk) Form state
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

  // Bulk manual entry rows
  const [bulkRows, setBulkRows] = useState([])

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
  const [batchSearchTerm, setBatchSearchTerm] = useState('')

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
  const [reportLoading, setReportLoading] = useState(false)

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
  const loadInventory = async (page = 0) => {
    setLoading(true)
    try {
      const res = await api.get(`/stock/paged?page=${page}&size=${INV_PAGE_SIZE}`)
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

  // Fetch Batches
  const loadBatches = async (page = 0) => {
    try {
      const res = await api.get(`/stock/batches?page=${page}&size=15`)
      setBatchList(res.data.data?.content || res.data.data || [])
      setBatchTotalPages(res.data.data?.totalPages || 0)
    } catch {
      toast.error('Failed to load batches')
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
      } else {
        setReportData(res.data.data?.content || [])
        setReportTotalPages(res.data.data?.totalPages || 0)
      }
    } catch {
      toast.error('Failed to generate report')
    } finally {
      setReportLoading(false)
    }
  }

  useEffect(() => {
    loadProducts()
    loadInventory(0)
  }, [])

  // Trigger loads based on active tab
  useEffect(() => {
    if (activeTab === 'dashboard') loadDashboard()
    else if (activeTab === 'overview') loadInventory(invPage)
    else if (activeTab === 'batches') loadBatches(batchPage)
    else if (activeTab === 'movements') loadMovements(movementPage)
    else if (activeTab === 'audit' && isAdmin) loadAuditLogs(auditPage)
    else if (activeTab === 'bi') loadBI()
    else if (activeTab === 'reports') loadReports(reportPage)
  }, [activeTab, invPage, batchPage, movementPage, auditPage, reportPage, selectedReport, selectedMovementType])

  // Handle Receive Stock form submit
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

  // Handle Adjust Stock Submit
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

  // Handle Expiry Write-Off
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

  // Export reports to CSV helper
  const exportToCSV = () => {
    if (reportData.length === 0) return
    let csvContent = 'data:text/csv;charset=utf-8,'
    if (selectedReport === 'valuation') {
      csvContent += 'Product,Category,Brand,Stock,Avg Cost,Selling Price,Value,Status\n'
      reportData.forEach(row => {
        csvContent += `"${row.productName}","${row.category}","${row.brand}",${row.currentStock},${row.avgCost},${row.sellingPrice},${row.inventoryValue},"${row.status}"\n`
      })
    } else if (selectedReport === 'expiry') {
      csvContent += 'Batch,Product,Expiry Date,Remaining,Cost Value,Days to Expiry,Risk\n'
      reportData.forEach(row => {
        csvContent += `"${row.batchNumber}","${row.productName}","${row.expiryDate}",${row.remainingQty},${row.costValue},${row.daysToExpiry},"${row.riskBucket}"\n`
      })
    } else if (selectedReport === 'aging') {
      csvContent += 'Batch,Product,Received Date,Remaining,Age (Days),Bucket\n'
      reportData.forEach(row => {
        csvContent += `"${row.batchNumber}","${row.productName}","${row.stockReceivedDate}",${row.remainingQty},${row.ageDays},"${row.ageBucket}"\n`
      })
    } else if (selectedReport === 'profitability') {
      csvContent += 'Category,Cost Value,Selling Value,Profit Potential,Margin %\n'
      reportData.forEach(row => {
        csvContent += `"${row.categoryName}",${row.costValue},${row.sellingValue},${row.profitPotential},${row.marginPercent}\n`
      })
    }

    const encodedUri = encodeURI(csvContent)
    const link = document.createElement('a')
    link.setAttribute('href', encodedUri)
    link.setAttribute('download', `Stock_Report_${selectedReport}_${new Date().toISOString().split('T')[0]}.csv`)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }

  // Filter Inventory list locally based on search filters
  const filteredInventory = inventoryList.filter(item => {
    const matchesSearch = item.productName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
                          item.brand?.toLowerCase().includes(searchTerm.toLowerCase())
    const matchesCategory = !selectedCategory || item.category === selectedCategory
    const matchesBrand = !selectedBrand || item.brand === selectedBrand
    const matchesStatus = !selectedStatus || item.status === selectedStatus
    return matchesSearch && matchesCategory && matchesBrand && matchesStatus
  })

  // Expiry risk pie chart colors
  const COLORS = ['#ef4444', '#f97316', '#eab308', '#3b82f6', '#10b981']

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 p-4 md:p-8 font-sans">
      {/* Header and Branding */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center mb-8 gap-4">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight bg-gradient-to-r from-indigo-400 via-sky-400 to-emerald-400 bg-clip-text text-transparent">
            Stock Management Redesign
          </h1>
          <p className="text-slate-400 mt-1">Enterprise FMCG Wholesaler, Retailer, & BI Suite</p>
        </div>

        {/* Tab Buttons */}
        <div className="flex flex-wrap gap-2 bg-slate-900/80 p-1.5 rounded-xl border border-slate-800 backdrop-blur">
          {[
            { id: 'overview', label: 'Inventory', icon: Package },
            { id: 'dashboard', label: 'Dashboard', icon: BarChart2 },
            { id: 'receive', label: 'Receive Stock', icon: Plus },
            { id: 'batches', label: 'Batches', icon: Clock },
            { id: 'movements', label: 'Movements', icon: ArrowRightLeft },
            { id: 'bi', label: 'BI Analytics', icon: Award },
            { id: 'reports', label: 'Reports', icon: FileText },
            ...(isAdmin ? [{ id: 'audit', label: 'Audit Logs', icon: History }] : [])
          ].map(tab => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-2 px-4 py-2 rounded-lg font-medium transition-all duration-300 ${
                activeTab === tab.id
                  ? 'bg-gradient-to-r from-indigo-600 to-sky-600 text-white shadow-lg shadow-indigo-500/20 scale-105'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50'
              }`}
            >
              <tab.icon className="w-4 h-4" />
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      <AnimatePresence mode="wait">
        {/* 1. OVERVIEW / INVENTORY TAB */}
        {activeTab === 'overview' && (
          <motion.div
            initial={{ opacity: 0, y: 15 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -15 }}
            className="space-y-6"
          >
            {/* Filters panel */}
            <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl flex flex-wrap gap-4 items-center justify-between">
              <div className="flex flex-wrap gap-3 items-center flex-1">
                <div className="relative flex-1 min-w-[240px]">
                  <Search className="absolute left-3.5 top-3.5 w-4 h-4 text-slate-400" />
                  <input
                    type="text"
                    placeholder="Search by Product name, Brand..."
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    className="w-full pl-10 pr-4 py-2.5 bg-slate-950 border border-slate-800 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 text-slate-100"
                  />
                </div>
                <select
                  value={selectedCategory}
                  onChange={(e) => setSelectedCategory(e.target.value)}
                  className="px-4 py-2.5 bg-slate-950 border border-slate-800 rounded-xl text-slate-300 focus:ring-2 focus:ring-indigo-500"
                >
                  <option value="">All Categories</option>
                  {CATEGORIES.map(cat => <option key={cat} value={cat}>{cat}</option>)}
                </select>
                <select
                  value={selectedStatus}
                  onChange={(e) => setSelectedStatus(e.target.value)}
                  className="px-4 py-2.5 bg-slate-950 border border-slate-800 rounded-xl text-slate-300 focus:ring-2 focus:ring-indigo-500"
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
                  }}
                  className="flex items-center gap-1 text-slate-400 hover:text-slate-200 transition"
                >
                  <RotateCcw className="w-4 h-4" /> Reset
                </button>
              )}
            </div>

            {/* Inventory Data Grid */}
            <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-slate-950 border-b border-slate-800 text-slate-300 font-semibold text-sm">
                      <th className="py-4 px-6">Product</th>
                      <th className="py-4 px-6">Category</th>
                      <th className="py-4 px-6">Stock</th>
                      <th className="py-4 px-6">Available</th>
                      <th className="py-4 px-6">Reserved</th>
                      <th className="py-4 px-6">Avg Cost</th>
                      <th className="py-4 px-6">Selling</th>
                      <th className="py-4 px-6">Margin</th>
                      <th className="py-4 px-6">Inventory Value</th>
                      <th className="py-4 px-6 text-center">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800 text-sm">
                    {loading ? (
                      <tr>
                        <td colSpan={10} className="py-12 text-center text-slate-500 font-medium">
                          Loading inventory ledger data...
                        </td>
                      </tr>
                    ) : filteredInventory.length === 0 ? (
                      <tr>
                        <td colSpan={10} className="py-12 text-center text-slate-500">
                          No matching stock entries found.
                        </td>
                      </tr>
                    ) : (
                      filteredInventory.map(item => (
                        <tr key={item.id} className="hover:bg-slate-800/40 transition-colors">
                          <td className="py-4 px-6">
                            <span className="font-semibold text-slate-100 block">{item.productName}</span>
                            <span className="text-xs text-slate-400">{item.brand}</span>
                          </td>
                          <td className="py-4 px-6 text-slate-300">{item.category}</td>
                          <td className="py-4 px-6 font-mono text-slate-200">
                            {item.totalSecondaryUnits} {item.secondaryUnit || 'Units'}
                          </td>
                          <td className="py-4 px-6 font-mono text-indigo-400">
                            {item.availableStock}
                          </td>
                          <td className="py-4 px-6 font-mono text-amber-500">
                            {item.reservedStock}
                          </td>
                          <td className="py-4 px-6 font-mono text-slate-300">₹{item.avgCost}</td>
                          <td className="py-4 px-6 font-mono text-slate-300">₹{item.sellingPrice}</td>
                          <td className="py-4 px-6">
                            <span className={`font-semibold ${item.marginPercent < 5 ? 'text-red-400' : 'text-emerald-400'}`}>
                              {item.marginPercent}%
                            </span>
                          </td>
                          <td className="py-4 px-6 font-mono text-emerald-400 font-semibold">₹{item.inventoryValue}</td>
                          <td className="py-4 px-6 text-center">
                            <span className={`px-3 py-1 rounded-full text-xs font-semibold ${
                              item.status === 'Healthy' ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' :
                              item.status === 'Low Stock' ? 'bg-amber-500/10 text-amber-400 border border-amber-500/20' :
                              item.status === 'Overstock' ? 'bg-indigo-500/10 text-indigo-400 border border-indigo-500/20' :
                              'bg-red-500/10 text-red-400 border border-red-500/20'
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
                <div className="p-4 bg-slate-950/80 border-t border-slate-800 flex justify-between items-center">
                  <span className="text-slate-400 text-sm">Showing {filteredInventory.length} of {invTotalElements}</span>
                  <Pagination currentPage={invPage} totalPages={invTotalPages} onPageChange={setInvPage} />
                </div>
              )}
            </div>
          </motion.div>
        )}

        {/* 2. EXECUTIVE DASHBOARD TAB */}
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
                  {[
                    { label: 'Inventory Cost Value', val: `₹${dashboardData.totalCostValue}`, desc: 'Total asset cost value', color: 'text-indigo-400' },
                    { label: 'MRP Valuation', val: `₹${dashboardData.totalMrpValue}`, desc: 'Total retail price potential', color: 'text-sky-400' },
                    { label: 'Profit Potential', val: `₹${dashboardData.expectedProfit}`, desc: 'Expected gross margin profits', color: 'text-emerald-400' },
                    { label: 'Inventory Health Score', val: `${dashboardData.healthScore}/100`, desc: `Score Status: ${dashboardData.healthClassification}`, color: 'text-rose-400' }
                  ].map((card, i) => (
                    <div key={i} className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-lg relative overflow-hidden group hover:border-slate-700 transition duration-300">
                      <div className="absolute top-0 left-0 w-1.5 h-full bg-gradient-to-b from-indigo-500 to-sky-500"></div>
                      <span className="text-slate-400 text-sm font-semibold">{card.label}</span>
                      <h3 className={`text-2xl font-black mt-2 tracking-tight ${card.color}`}>{card.val}</h3>
                      <p className="text-xs text-slate-500 mt-1">{card.desc}</p>
                    </div>
                  ))}
                </div>

                {/* Sub KPI cards */}
                <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
                  {[
                    { label: 'Total SKUs', count: dashboardData.totalProducts, bg: 'bg-indigo-500/10 text-indigo-400' },
                    { label: 'Active SKUs', count: dashboardData.activeSkus, bg: 'bg-emerald-500/10 text-emerald-400' },
                    { label: 'Active Batches', count: dashboardData.totalBatches, bg: 'bg-sky-500/10 text-sky-400' },
                    { label: 'Low Stock SKU', count: dashboardData.lowStockCount, bg: 'bg-amber-500/10 text-amber-400' },
                    { label: 'Out of Stock SKU', count: dashboardData.outOfStockCount, bg: 'bg-red-500/10 text-red-400' },
                    { label: 'Expiring in 30 Days', count: dashboardData.expiringCount, bg: 'bg-orange-500/10 text-orange-400' }
                  ].map((sk, i) => (
                    <div key={i} className="bg-slate-900/60 border border-slate-800/80 p-4 rounded-xl text-center">
                      <span className="text-xs text-slate-400 block mb-1">{sk.label}</span>
                      <span className={`inline-block px-3 py-1 rounded-full text-lg font-extrabold ${sk.bg}`}>{sk.count}</span>
                    </div>
                  ))}
                </div>

                {/* BI Forecast charts and Recent logs widgets */}
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
                  {/* Aging Chart */}
                  <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl">
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
                          <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                          <XAxis dataKey="name" stroke="#94a3b8" />
                          <YAxis stroke="#94a3b8" />
                          <Tooltip contentStyle={{ backgroundColor: '#0f172a', borderColor: '#1e293b' }} />
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
                  <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl flex flex-col">
                    <h3 className="text-lg font-bold mb-4">Recent Inward Stock Received</h3>
                    <div className="space-y-4 flex-1">
                      {dashboardData.recentBatches?.length === 0 ? (
                        <p className="text-slate-500 text-sm">No recent inward stock entries.</p>
                      ) : (
                        dashboardData.recentBatches.map(batch => (
                          <div key={batch.id} className="flex justify-between items-center p-3 rounded-lg bg-slate-950 border border-slate-800">
                            <div>
                              <span className="font-semibold text-slate-200 block">{batch.product?.name || 'Unknown Product'}</span>
                              <span className="text-xs text-slate-400">Batch: {batch.batchNumber} | Supplier: {batch.supplierName}</span>
                            </div>
                            <div className="text-right">
                              <span className="font-mono text-emerald-400 font-bold block">+{batch.secondaryReceived}</span>
                              <span className="text-xs text-slate-500">{new Date(batch.receivedAt).toLocaleDateString()}</span>
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

        {/* 3. RECEIVE STOCK TAB */}
        {activeTab === 'receive' && (
          <motion.div
            initial={{ opacity: 0, y: 15 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -15 }}
            className="grid grid-cols-1 lg:grid-cols-3 gap-8"
          >
            {/* Input Form Panel */}
            <div className="lg:col-span-2 bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-lg">
              <h3 className="text-xl font-bold mb-6 flex items-center gap-2">
                <Plus className="w-5 h-5 text-indigo-500" /> Manual Stock Registration
              </h3>
              <form onSubmit={handleReceiveSubmit} className="space-y-6">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  {/* Select Product */}
                  <div className="md:col-span-2">
                    <label className="text-slate-400 text-sm font-semibold mb-1 block">Choose Product <span className="text-red-500">*</span></label>
                    <SearchSelect
                      options={products.map(p => ({ value: p.id, label: `${p.name} (${p.brand})` }))}
                      value={manualForm.productId}
                      onChange={(val) => setManualForm(f => ({ ...f, productId: val }))}
                      placeholder="Search and select product..."
                    />
                  </div>

                  {/* Supplier details */}
                  <div>
                    <label className="text-slate-400 text-sm font-semibold mb-1 block">Supplier Name <span className="text-red-500">*</span></label>
                    <input
                      type="text"
                      value={manualForm.supplierName}
                      onChange={(e) => setManualForm(f => ({ ...f, supplierName: e.target.value }))}
                      required
                      placeholder="e.g. Saurabh Traders"
                      className="w-full px-4 py-2.5 bg-slate-950 border border-slate-800 rounded-xl text-slate-100 focus:ring-2 focus:ring-indigo-500"
                    />
                  </div>

                  {/* Batch Number */}
                  <div>
                    <label className="text-slate-400 text-sm font-semibold mb-1 block">Batch Number <span className="text-red-500">*</span></label>
                    <input
                      type="text"
                      value={manualForm.batchNumber}
                      onChange={(e) => setManualForm(f => ({ ...f, batchNumber: e.target.value }))}
                      required
                      placeholder="e.g. B-99388"
                      className="w-full px-4 py-2.5 bg-slate-950 border border-slate-800 rounded-xl text-slate-100 focus:ring-2 focus:ring-indigo-500"
                    />
                  </div>

                  {/* Invoice Number */}
                  <div>
                    <label className="text-slate-400 text-sm font-semibold mb-1 block">Supplier Invoice Number <span className="text-red-500">*</span></label>
                    <input
                      type="text"
                      value={manualForm.supplierInvoiceNumber}
                      onChange={(e) => setManualForm(f => ({ ...f, supplierInvoiceNumber: e.target.value }))}
                      required
                      placeholder="e.g. INV-100223"
                      className="w-full px-4 py-2.5 bg-slate-950 border border-slate-800 rounded-xl text-slate-100 focus:ring-2 focus:ring-indigo-500"
                    />
                  </div>

                  {/* Invoice Date */}
                  <div>
                    <label className="text-slate-400 text-sm font-semibold mb-1 block">Supplier Invoice Date <span className="text-red-500">*</span></label>
                    <input
                      type="date"
                      value={manualForm.supplierInvoiceDate}
                      onChange={(e) => setManualForm(f => ({ ...f, supplierInvoiceDate: e.target.value }))}
                      required
                      className="w-full px-4 py-2.5 bg-slate-950 border border-slate-800 rounded-xl text-slate-100 focus:ring-2 focus:ring-indigo-500"
                    />
                  </div>

                  {/* Stock Received Date */}
                  <div>
                    <label className="text-slate-400 text-sm font-semibold mb-1 block">Stock Received Date <span className="text-red-500">*</span></label>
                    <input
                      type="date"
                      value={manualForm.stockReceivedDate}
                      onChange={(e) => setManualForm(f => ({ ...f, stockReceivedDate: e.target.value }))}
                      required
                      className="w-full px-4 py-2.5 bg-slate-950 border border-slate-800 rounded-xl text-slate-100 focus:ring-2 focus:ring-indigo-500"
                    />
                  </div>

                  {/* Expiry Date */}
                  <div>
                    <label className="text-slate-400 text-sm font-semibold mb-1 block">Expiry Date <span className="text-red-500">*</span></label>
                    <input
                      type="date"
                      value={manualForm.expiryDate}
                      onChange={(e) => setManualForm(f => ({ ...f, expiryDate: e.target.value }))}
                      required
                      className="w-full px-4 py-2.5 bg-slate-950 border border-slate-800 rounded-xl text-slate-100 focus:ring-2 focus:ring-indigo-500"
                    />
                  </div>

                  {/* Manufacturing Date */}
                  <div>
                    <label className="text-slate-400 text-sm font-semibold mb-1 block">Manufacturing Date <span className="text-red-500">*</span></label>
                    <input
                      type="date"
                      value={manualForm.manufacturingDate}
                      onChange={(e) => setManualForm(f => ({ ...f, manufacturingDate: e.target.value }))}
                      required
                      className="w-full px-4 py-2.5 bg-slate-950 border border-slate-800 rounded-xl text-slate-100 focus:ring-2 focus:ring-indigo-500"
                    />
                  </div>

                  {/* Quantity Inward Details */}
                  <div className="grid grid-cols-3 gap-3 md:col-span-2">
                    <div>
                      <label className="text-slate-400 text-xs font-semibold mb-1 block">Primary Units Qty</label>
                      <input
                        type="number"
                        placeholder="BOX count"
                        value={manualForm.primaryReceived}
                        onChange={(e) => setManualForm(f => ({ ...f, primaryReceived: e.target.value }))}
                        className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-slate-100 focus:ring-1 focus:ring-indigo-500"
                      />
                    </div>
                    <div>
                      <label className="text-slate-400 text-xs font-semibold mb-1 block">Extra Secondary Qty</label>
                      <input
                        type="number"
                        placeholder="Loose packs"
                        value={manualForm.extraSecondaryReceived}
                        onChange={(e) => setManualForm(f => ({ ...f, extraSecondaryReceived: e.target.value }))}
                        className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-slate-100 focus:ring-1 focus:ring-indigo-500"
                      />
                    </div>
                    <div>
                      <label className="text-slate-400 text-xs font-semibold mb-1 block">Free Offer Qty</label>
                      <input
                        type="number"
                        placeholder="Free packs"
                        value={manualForm.offerSecondaryReceived}
                        onChange={(e) => setManualForm(f => ({ ...f, offerSecondaryReceived: e.target.value }))}
                        className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-slate-100 focus:ring-1 focus:ring-indigo-500"
                      />
                    </div>
                  </div>

                  {/* Buy Price and GST */}
                  <div>
                    <label className="text-slate-400 text-sm font-semibold mb-1 block">Buy Price per Primary (Excl. Tax) <span className="text-red-500">*</span></label>
                    <input
                      type="number"
                      step="0.01"
                      value={manualForm.buyPriceWithoutTax}
                      onChange={(e) => setManualForm(f => ({ ...f, buyPriceWithoutTax: e.target.value }))}
                      required
                      placeholder="₹0.00"
                      className="w-full px-4 py-2.5 bg-slate-950 border border-slate-800 rounded-xl text-slate-100 focus:ring-2 focus:ring-indigo-500"
                    />
                  </div>

                  <div>
                    <label className="text-slate-400 text-sm font-semibold mb-1 block">GST Rate %</label>
                    <select
                      value={manualForm.gstPercent}
                      onChange={(e) => setManualForm(f => ({ ...f, gstPercent: e.target.value }))}
                      className="w-full px-4 py-2.5 bg-slate-950 border border-slate-800 rounded-xl text-slate-300 focus:ring-2 focus:ring-indigo-500"
                    >
                      <option value="0">0%</option>
                      <option value="5">5%</option>
                      <option value="12">12%</option>
                      <option value="18">18%</option>
                      <option value="28">28%</option>
                    </select>
                  </div>

                  {/* Remarks */}
                  <div className="md:col-span-2">
                    <label className="text-slate-400 text-sm font-semibold mb-1 block">Remarks / Notes</label>
                    <textarea
                      value={manualForm.remarks}
                      onChange={(e) => setManualForm(f => ({ ...f, remarks: e.target.value }))}
                      placeholder="Any specific batch comments..."
                      className="w-full px-4 py-2.5 bg-slate-950 border border-slate-800 rounded-xl text-slate-100 focus:ring-2 focus:ring-indigo-500 h-24"
                    />
                  </div>
                </div>

                <div className="flex gap-4">
                  <button
                    type="submit"
                    disabled={savingStock}
                    className="flex-1 py-3 px-6 rounded-xl font-bold bg-gradient-to-r from-indigo-600 to-sky-600 hover:from-indigo-500 hover:to-sky-500 text-white shadow-lg disabled:opacity-50 disabled:cursor-not-allowed transition duration-300"
                  >
                    {savingStock ? 'Saving Entry...' : 'Register Inward Stock'}
                  </button>
                </div>
              </form>
            </div>

            {/* OCR Document Upload Help card */}
            <div className="space-y-6">
              <div className="bg-gradient-to-b from-indigo-950 to-slate-900 border border-indigo-800/30 p-6 rounded-2xl text-center">
                <Camera className="w-12 h-12 text-indigo-400 mx-auto mb-4" />
                <h4 className="text-lg font-bold mb-2">Scan Supplier Invoice (OCR)</h4>
                <p className="text-slate-400 text-sm mb-4">Upload an invoice snapshot to parse items automatically with AI assist.</p>
                <div className="border-2 border-dashed border-slate-700 hover:border-indigo-500 rounded-xl p-6 transition duration-300 cursor-pointer">
                  <Upload className="w-6 h-6 text-slate-400 mx-auto mb-2" />
                  <span className="text-xs text-slate-400 block">Select Invoice PDF or JPEG file</span>
                </div>
              </div>
            </div>
          </motion.div>
        )}

        {/* 4. BATCH INVENTORY TAB */}
        {activeTab === 'batches' && (
          <motion.div
            initial={{ opacity: 0, y: 15 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -15 }}
            className="space-y-6"
          >
            {/* Search filter for batches */}
            <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl flex flex-wrap gap-4 items-center">
              <div className="relative flex-1 min-w-[280px]">
                <Search className="absolute left-3.5 top-3.5 w-4 h-4 text-slate-400" />
                <input
                  type="text"
                  placeholder="Filter by product name, batch number..."
                  value={batchSearchTerm}
                  onChange={(e) => setBatchSearchTerm(e.target.value)}
                  className="w-full pl-10 pr-4 py-2.5 bg-slate-950 border border-slate-800 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 text-slate-100 text-sm"
                />
              </div>
            </div>

            <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-slate-950 border-b border-slate-800 text-slate-300 font-semibold text-xs uppercase tracking-wider">
                      <th className="py-4 px-6">Product / Batch</th>
                      <th className="py-4 px-6">Supplier & Invoice</th>
                      <th className="py-4 px-6">Received / Sold</th>
                      <th className="py-4 px-6">Available</th>
                      <th className="py-4 px-6">Cost Price</th>
                      <th className="py-4 px-6">Expiry & Age</th>
                      <th className="py-4 px-6">Value</th>
                      <th className="py-4 px-6 text-center">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800 text-sm">
                    {batchList.length === 0 ? (
                      <tr>
                        <td colSpan={8} className="py-12 text-center text-slate-500 font-medium">
                          No batches found.
                        </td>
                      </tr>
                    ) : (
                      batchList.map(batch => (
                        <tr key={batch.id} className="hover:bg-slate-800/40 transition-colors">
                          <td className="py-4 px-6">
                            <span className="font-semibold text-slate-100 block">{batch.productName}</span>
                            <span className="text-xs text-indigo-400 font-semibold">Batch: {batch.batchNumber}</span>
                          </td>
                          <td className="py-4 px-6">
                            <span className="text-slate-300 block">{batch.supplierName}</span>
                            <span className="text-xs text-slate-500">Inv: {batch.invoiceNumber} | {batch.supplierInvoiceDate || 'No Date'}</span>
                          </td>
                          <td className="py-4 px-6">
                            <span className="text-slate-300 block">{batch.secondaryReceived} Packs</span>
                            <span className="text-xs text-slate-500">Sold: {batch.quantitySold} Packs</span>
                          </td>
                          <td className="py-4 px-6 font-mono text-indigo-400 font-semibold">
                            {batch.secondaryRemaining} Packs
                          </td>
                          <td className="py-4 px-6 font-mono">₹{batch.buyPriceWithoutTax}</td>
                          <td className="py-4 px-6">
                            <span className="text-slate-300 block text-xs">Exp: {batch.expiryDate}</span>
                            <span className="text-xs text-slate-500">Age: {batch.stockAgeDays} days</span>
                          </td>
                          <td className="py-4 px-6 font-mono text-emerald-400 font-semibold">₹{batch.batchValue}</td>
                          <td className="py-4 px-6 text-center">
                            <div className="flex gap-2 justify-center">
                              <button
                                onClick={() => {
                                  setAdjustingBatch(batch)
                                  setAdjustPrimary(Math.floor(batch.secondaryRemaining / batch.secondaryPerPrimary).toString())
                                  setAdjustSecondary((batch.secondaryRemaining % batch.secondaryPerPrimary).toString())
                                  setAdjustOffer(batch.offerSecondaryRemaining?.toString() || '0')
                                  setAdjustBuyPrice(batch.buyPriceWithoutTax?.toString() || '0')
                                }}
                                className="p-2 rounded-lg bg-slate-850 hover:bg-indigo-600/30 text-slate-400 hover:text-indigo-400 transition"
                                title="Adjust Stock"
                              >
                                <Edit2 className="w-4 h-4" />
                              </button>
                              {batch.batchStatus !== 'WRITTEN_OFF' && (
                                <button
                                  onClick={() => setWriteOffTarget(batch.id)}
                                  className="p-2 rounded-lg bg-slate-850 hover:bg-rose-600/30 text-slate-400 hover:text-rose-400 transition"
                                  title="Write Off Expiry"
                                >
                                  <Trash2 className="w-4 h-4" />
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
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
            <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl flex flex-wrap gap-4 items-center">
              <select
                value={selectedMovementType}
                onChange={(e) => setSelectedMovementType(e.target.value)}
                className="px-4 py-2.5 bg-slate-950 border border-slate-800 rounded-xl text-slate-300 focus:ring-2 focus:ring-indigo-500"
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

            <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-slate-950 border-b border-slate-800 text-slate-300 font-semibold text-xs uppercase tracking-wider">
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
                  <tbody className="divide-y divide-slate-800 text-sm">
                    {movementList.length === 0 ? (
                      <tr>
                        <td colSpan={8} className="py-12 text-center text-slate-500">
                          No stock movements registered.
                        </td>
                      </tr>
                    ) : (
                      movementList.map(move => (
                        <tr key={move.id} className="hover:bg-slate-800/40 transition-colors">
                          <td className="py-4 px-6 text-slate-400 font-mono text-xs">
                            {new Date(move.timestamp).toLocaleString()}
                          </td>
                          <td className="py-4 px-6">
                            <span className="font-semibold text-slate-100 block">{move.product?.name}</span>
                            <span className="text-xs text-indigo-400">Batch: {move.batch?.batchNumber || 'N/A'}</span>
                          </td>
                          <td className="py-4 px-6">
                            <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                              move.movementType === 'PURCHASE' || move.movementType === 'OPENING_STOCK' ? 'bg-emerald-500/10 text-emerald-400' :
                              move.movementType === 'SALE' ? 'bg-sky-500/10 text-sky-400' :
                              move.movementType === 'RETURN_IN' ? 'bg-indigo-500/10 text-indigo-400' :
                              'bg-rose-500/10 text-red-400'
                            }`}>
                              {move.movementType}
                            </span>
                          </td>
                          <td className={`py-4 px-6 font-mono font-bold ${move.quantity > 0 ? 'text-emerald-400' : 'text-red-400'}`}>
                            {move.quantity > 0 ? `+${move.quantity}` : move.quantity}
                          </td>
                          <td className="py-4 px-6 font-mono text-slate-400 text-xs">
                            {move.quantityBefore} → {move.quantityAfter}
                          </td>
                          <td className="py-4 px-6 font-mono">
                            <span className="block">₹{move.unitPrice}/unit</span>
                            <span className="text-xs text-slate-500">Total: ₹{move.totalValue}</span>
                          </td>
                          <td className="py-4 px-6">
                            <span className="text-slate-300 block">{move.username}</span>
                            <span className="text-xs text-slate-500">Ref: {move.referenceNumber || 'N/A'}</span>
                          </td>
                          <td className="py-4 px-6 text-slate-400 text-xs">{move.remarks || '-'}</td>
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
            <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-slate-950 border-b border-slate-800 text-slate-300 font-semibold text-xs uppercase tracking-wider">
                      <th className="py-4 px-6">Changed On</th>
                      <th className="py-4 px-6">Product / Batch</th>
                      <th className="py-4 px-6">Old Value</th>
                      <th className="py-4 px-6">New Value</th>
                      <th className="py-4 px-6">Changed By</th>
                      <th className="py-4 px-6">Reason</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800 text-sm">
                    {auditLogs.length === 0 ? (
                      <tr>
                        <td colSpan={6} className="py-12 text-center text-slate-500">
                          No audit entries registered.
                        </td>
                      </tr>
                    ) : (
                      auditLogs.map(log => (
                        <tr key={log.id} className="hover:bg-slate-800/40 transition-colors">
                          <td className="py-4 px-6 text-slate-400 font-mono text-xs">
                            {new Date(log.timestamp).toLocaleString()}
                          </td>
                          <td className="py-4 px-6">
                            <span className="font-semibold text-slate-100 block">{log.productName}</span>
                            <span className="text-xs text-indigo-400 font-semibold">Batch: {log.batchNumber}</span>
                          </td>
                          <td className="py-4 px-6 font-mono text-red-400">{log.oldSecondaryRemaining}</td>
                          <td className="py-4 px-6 font-mono text-emerald-400">{log.newSecondaryRemaining}</td>
                          <td className="py-4 px-6 text-slate-300 font-medium">{log.adjustedBy}</td>
                          <td className="py-4 px-6 text-slate-400 text-xs">{log.reason}</td>
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
            <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl flex flex-wrap gap-4 items-center justify-between">
              <div className="flex gap-4 items-center">
                <label className="text-slate-400 text-sm font-semibold">Report Type:</label>
                <select
                  value={selectedReport}
                  onChange={(e) => {
                    setSelectedReport(e.target.value)
                    setReportPage(0)
                  }}
                  className="px-4 py-2.5 bg-slate-950 border border-slate-800 rounded-xl text-slate-300 focus:ring-2 focus:ring-indigo-500 text-sm"
                >
                  <option value="valuation">Inventory Valuation Report</option>
                  <option value="expiry">Expiry & Expiry Risk Report</option>
                  <option value="aging">Stock Aging Report</option>
                  <option value="profitability">Category Profitability Report</option>
                </select>
              </div>

              <button
                onClick={exportToCSV}
                className="flex items-center gap-2 bg-gradient-to-r from-emerald-600 to-teal-600 text-white font-semibold py-2 px-5 rounded-xl text-sm shadow hover:scale-105 transition duration-300"
              >
                <Download className="w-4 h-4" /> Export Report (CSV)
              </button>
            </div>

            <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
              <div className="overflow-x-auto">
                {selectedReport === 'valuation' && (
                  <table className="w-full text-left border-collapse">
                    <thead>
                      <tr className="bg-slate-950 border-b border-slate-800 text-slate-300 font-semibold text-xs uppercase tracking-wider">
                        <th className="py-4 px-6">Product</th>
                        <th className="py-4 px-6">Category</th>
                        <th className="py-4 px-6">Available Stock</th>
                        <th className="py-4 px-6">Avg Cost Price</th>
                        <th className="py-4 px-6">Selling Price</th>
                        <th className="py-4 px-6">Profit Potential</th>
                        <th className="py-4 px-6 text-center">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800 text-sm">
                      {reportLoading ? (
                        <tr><td colSpan={7} className="py-8 text-center text-slate-500">Generating valuation metrics...</td></tr>
                      ) : reportData.map(row => (
                        <tr key={row.productId} className="hover:bg-slate-800/40">
                          <td className="py-4 px-6 font-semibold text-slate-200">{row.productName}</td>
                          <td className="py-4 px-6 text-slate-400">{row.category}</td>
                          <td className="py-4 px-6 font-mono text-slate-200">{row.currentStock} Units</td>
                          <td className="py-4 px-6 font-mono text-slate-300">₹{row.avgCost}</td>
                          <td className="py-4 px-6 font-mono text-slate-300">₹{row.sellingPrice}</td>
                          <td className="py-4 px-6 font-mono text-emerald-400 font-semibold">₹{(row.currentStock * (row.sellingPrice - row.avgCost)).toFixed(2)}</td>
                          <td className="py-4 px-6 text-center">
                            <span className="text-xs px-3 py-1 rounded-full bg-slate-950 border border-slate-800 text-slate-300 font-semibold">{row.status}</span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}

                {selectedReport === 'expiry' && (
                  <table className="w-full text-left border-collapse">
                    <thead>
                      <tr className="bg-slate-950 border-b border-slate-800 text-slate-300 font-semibold text-xs uppercase tracking-wider">
                        <th className="py-4 px-6">Batch Number</th>
                        <th className="py-4 px-6">Product</th>
                        <th className="py-4 px-6">Expiry Date</th>
                        <th className="py-4 px-6">Remaining Qty</th>
                        <th className="py-4 px-6">Risk Value (Cost)</th>
                        <th className="py-4 px-6">Days to Expiry</th>
                        <th className="py-4 px-6 text-center">Risk level</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800 text-sm">
                      {reportLoading ? (
                        <tr><td colSpan={7} className="py-8 text-center text-slate-500">Checking expiries...</td></tr>
                      ) : reportData.map(row => (
                        <tr key={row.batchId} className="hover:bg-slate-800/40">
                          <td className="py-4 px-6 font-semibold text-indigo-400 font-mono">{row.batchNumber}</td>
                          <td className="py-4 px-6 text-slate-200">{row.productName}</td>
                          <td className="py-4 px-6 font-mono text-slate-300">{row.expiryDate}</td>
                          <td className="py-4 px-6 font-mono">{row.remainingQty}</td>
                          <td className="py-4 px-6 font-mono text-rose-400">₹{row.costValue}</td>
                          <td className="py-4 px-6 font-mono">{row.daysToExpiry}</td>
                          <td className="py-4 px-6 text-center">
                            <span className={`text-xs px-2.5 py-0.5 rounded-full font-bold ${
                              row.riskBucket.includes('Expired') ? 'bg-red-500/10 text-red-400' : 'bg-amber-500/10 text-amber-400'
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
                      <tr className="bg-slate-950 border-b border-slate-800 text-slate-300 font-semibold text-xs uppercase tracking-wider">
                        <th className="py-4 px-6">Batch Number</th>
                        <th className="py-4 px-6">Product</th>
                        <th className="py-4 px-6">Received On Date</th>
                        <th className="py-4 px-6">Remaining Qty</th>
                        <th className="py-4 px-6">Age (Days)</th>
                        <th className="py-4 px-6 text-center">Age Classification</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800 text-sm">
                      {reportLoading ? (
                        <tr><td colSpan={6} className="py-8 text-center text-slate-500">Scanning inventory age...</td></tr>
                      ) : reportData.map((row, i) => (
                        <tr key={i} className="hover:bg-slate-800/40">
                          <td className="py-4 px-6 font-semibold text-indigo-400 font-mono">{row.batchNumber}</td>
                          <td className="py-4 px-6 text-slate-200">{row.productName}</td>
                          <td className="py-4 px-6 font-mono text-slate-300">{row.stockReceivedDate}</td>
                          <td className="py-4 px-6 font-mono">{row.remainingQty}</td>
                          <td className="py-4 px-6 font-mono">{row.ageDays}</td>
                          <td className="py-4 px-6 text-center">
                            <span className="text-xs px-2.5 py-0.5 rounded-full bg-slate-950 border border-slate-850 text-slate-300 font-bold">{row.ageBucket}</span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}

                {selectedReport === 'profitability' && (
                  <table className="w-full text-left border-collapse">
                    <thead>
                      <tr className="bg-slate-950 border-b border-slate-800 text-slate-300 font-semibold text-xs uppercase tracking-wider">
                        <th className="py-4 px-6">Category</th>
                        <th className="py-4 px-6">Total Asset Cost</th>
                        <th className="py-4 px-6">Sales Potential Valuation</th>
                        <th className="py-4 px-6">Gross Margin Profit Potential</th>
                        <th className="py-4 px-6 text-center">Margin %</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800 text-sm">
                      {reportLoading ? (
                        <tr><td colSpan={5} className="py-8 text-center text-slate-500">Loading profitability details...</td></tr>
                      ) : reportData.map((row, i) => (
                        <tr key={i} className="hover:bg-slate-800/40">
                          <td className="py-4 px-6 font-bold text-slate-200">{row.categoryName}</td>
                          <td className="py-4 px-6 font-mono text-slate-300">₹{row.costValue}</td>
                          <td className="py-4 px-6 font-mono text-indigo-400">₹{row.sellingValue}</td>
                          <td className="py-4 px-6 font-mono text-emerald-400 font-bold">₹{row.profitPotential}</td>
                          <td className="py-4 px-6 text-center">
                            <span className="text-xs px-3 py-1 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-extrabold">{row.marginPercent}%</span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
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
                <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl flex flex-col justify-between shadow-xl">
                  <div>
                    <h3 className="text-xl font-bold mb-4 flex items-center gap-2">
                      <Award className="w-5 h-5 text-indigo-500" /> Overall Health Score
                    </h3>
                    <div className="text-center py-6">
                      <span className="text-7xl font-black bg-gradient-to-r from-emerald-400 to-indigo-500 bg-clip-text text-transparent">{biHealth.overallScore}</span>
                      <span className="text-xl text-slate-500 block mt-2">Classified as: <span className="font-bold text-slate-300">{biHealth.classification}</span></span>
                    </div>
                  </div>
                  <div className="space-y-3.5 border-t border-slate-800 pt-6">
                    <div className="flex justify-between items-center text-sm">
                      <span className="text-slate-400">Dead Stock Penalty Score:</span>
                      <span className="font-semibold text-rose-400">-{Math.round((25 - biHealth.deadStockScore) * 100) / 100}</span>
                    </div>
                    <div className="flex justify-between items-center text-sm">
                      <span className="text-slate-400">Expiry Risk Penalty Score:</span>
                      <span className="font-semibold text-rose-400">-{Math.round((25 - biHealth.expiryScore) * 100) / 100}</span>
                    </div>
                    <div className="flex justify-between items-center text-sm">
                      <span className="text-slate-400">Low Stock penalty:</span>
                      <span className="font-semibold text-rose-400">-{Math.round((20 - biHealth.lowStockScore) * 100) / 100}</span>
                    </div>
                  </div>
                </div>

                {/* Suggestions Engine Widget */}
                <div className="lg:col-span-2 bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-xl flex flex-col">
                  <h3 className="text-xl font-bold mb-4 flex items-center gap-2">
                    <ShieldAlert className="w-5 h-5 text-indigo-500" /> Purchase Recommendation Engine
                  </h3>
                  <div className="overflow-y-auto max-h-[360px] flex-1 divide-y divide-slate-800">
                    {biReorders.length === 0 ? (
                      <p className="text-slate-500 text-sm py-4">No reorder recommendations needed right now.</p>
                    ) : (
                      biReorders.map((reorder, i) => (
                        <div key={i} className="flex justify-between items-center py-4">
                          <div>
                            <span className="font-bold text-slate-200 block">{reorder.productName}</span>
                            <span className="text-xs text-slate-400">
                              Current Stock: {reorder.currentStock} Units | Daily Sales average: {reorder.avgDailySales.toFixed(2)} units/day
                            </span>
                          </div>
                          <div className="text-right">
                            <span className="text-xs text-slate-400 block mb-1">Suggest Order Qty:</span>
                            <span className="px-3 py-1 rounded bg-indigo-600/20 text-indigo-400 border border-indigo-500/20 font-bold font-mono">
                              +{reorder.suggestedReorderQty}
                            </span>
                          </div>
                        </div>
                      ))
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
        <Modal title="Correct & Adjust Batch Quantity" onClose={() => setAdjustingBatch(null)}>
          <form onSubmit={handleAdjustSubmit} className="space-y-6">
            <p className="text-xs text-slate-400">
              Editing quantities will write a line item to the movement history logs and adjustment audit tables.
            </p>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="text-xs text-slate-400 block mb-1 font-semibold">Remaining Primary units</label>
                <input
                  type="number"
                  value={adjustPrimary}
                  onChange={(e) => setAdjustPrimary(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-slate-100"
                />
              </div>
              <div>
                <label className="text-xs text-slate-400 block mb-1 font-semibold">Remaining Secondary units</label>
                <input
                  type="number"
                  value={adjustSecondary}
                  onChange={(e) => setAdjustSecondary(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-slate-100"
                />
              </div>
              <div>
                <label className="text-xs text-slate-400 block mb-1 font-semibold">Offer Secondary Remaining</label>
                <input
                  type="number"
                  value={adjustOffer}
                  onChange={(e) => setAdjustOffer(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-slate-100"
                />
              </div>
              <div>
                <label className="text-xs text-slate-400 block mb-1 font-semibold">Cost Price (Excl. Tax)</label>
                <input
                  type="number"
                  step="0.01"
                  value={adjustBuyPrice}
                  onChange={(e) => setAdjustBuyPrice(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-slate-100"
                />
              </div>
            </div>

            <div>
              <label className="text-xs text-slate-400 block mb-1 font-semibold">Correction Reason <span className="text-red-500">*</span></label>
              <input
                type="text"
                required
                value={adjustReason}
                onChange={(e) => setAdjustReason(e.target.value)}
                placeholder="e.g. Audit mismatch, damage write-off..."
                className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-slate-100"
              />
            </div>

            <div className="flex gap-3 justify-end pt-4 border-t border-slate-800">
              <button
                type="button"
                onClick={() => setAdjustingBatch(null)}
                className="px-4 py-2 bg-slate-800 hover:bg-slate-755 rounded-lg font-medium text-slate-300"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={adjusting}
                className="px-5 py-2 bg-indigo-600 hover:bg-indigo-500 text-white font-semibold rounded-lg shadow"
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
          title="Write Off Expired Stock?"
          description="This action will decrease standard inventory counts to zero and create a Claimable/Damage logs write-off entry."
          onConfirm={() => handleWriteOff(writeOffTarget)}
          onCancel={() => setWriteOffTarget(null)}
        />
      )}
    </div>
  )
}

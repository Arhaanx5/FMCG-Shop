import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Users, Award, TrendingUp, TrendingDown, MapPin, Phone, UserCheck } from 'lucide-react'
import api from '../services/api'
import DataTable from '../components/DataTable'
import StatCard from '../components/StatCard'
import { useToast } from '../context/ToastContext'

export default function Salesmen() {
  const [performance, setPerformance] = useState([])
  const [loading, setLoading] = useState(true)
  const toast = useToast()

  useEffect(() => {
    loadPerformance()
  }, [])

  const loadPerformance = async () => {
    setLoading(true)
    try {
      const res = await api.get('/dashboard/salesmen-performance')
      setPerformance(res.data.data || [])
    } catch (err) {
      toast.error('Failed to load salesmen performance metrics')
    } finally {
      setLoading(false)
    }
  }

  // Calculate totals for KPI cards
  const totalSalesmen = performance.length
  const totalRevenue = performance.reduce((sum, s) => sum + Number(s.totalRevenueGenerated || 0), 0)
  const totalCollections = performance.reduce((sum, s) => sum + Number(s.totalCollectionsMade || 0), 0)
  const totalPendingCredit = performance.reduce((sum, s) => sum + Number(s.activeRouteCredit || 0), 0)

  // Find top salesman based on collections
  const topSalesman = performance.length > 0 
    ? [...performance].sort((a, b) => Number(b.totalCollectionsMade) - Number(a.totalCollectionsMade))[0]
    : null

  const columns = [
    { 
      header: 'Salesman', 
      accessor: 'salesmanName', 
      render: (row) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
          <div style={{
            background: 'var(--color-accent-soft)', color: 'var(--color-accent)',
            width: '36px', height: '36px', borderRadius: '50%',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontWeight: 'var(--font-weight-bold)'
          }}>
            {row.salesmanName?.charAt(0).toUpperCase()}
          </div>
          <div>
            <div style={{ fontWeight: 'var(--font-weight-medium)' }}>{row.salesmanName}</div>
            <div className="text-xs text-muted" style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              <Phone size={10} /> {row.salesmanPhone || 'No Phone'}
            </div>
          </div>
        </div>
      )
    },
    { 
      header: 'Assigned Routes (Areas)', 
      accessor: 'assignedAreas', 
      render: (row) => (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-1)' }}>
          {row.assignedAreas && row.assignedAreas.length > 0 ? (
            row.assignedAreas.map((area, idx) => (
              <span key={idx} className="badge badge-neutral" style={{ fontSize: '11px', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                <MapPin size={10} /> {area}
              </span>
            ))
          ) : (
            <span className="text-muted" style={{ fontSize: '11px' }}>No routes assigned</span>
          )}
        </div>
      )
    },
    { 
      header: 'Retailers', 
      accessor: 'activeCustomersCount', 
      render: (row) => <span className="badge badge-info">{row.activeCustomersCount || 0} active</span>
    },
    { 
      header: 'Revenue Generated', 
      accessor: 'totalRevenueGenerated', 
      render: (row) => <span style={{ fontWeight: 'var(--font-weight-semibold)' }}>₹{Number(row.totalRevenueGenerated || 0).toLocaleString('en-IN')}</span>
    },
    { 
      header: 'Collections Made', 
      accessor: 'totalCollectionsMade', 
      render: (row) => <span style={{ color: 'var(--color-success)', fontWeight: 'var(--font-weight-semibold)' }}>₹{Number(row.totalCollectionsMade || 0).toLocaleString('en-IN')}</span>
    },
    { 
      header: 'Route Credit (O/S)', 
      accessor: 'activeRouteCredit', 
      render: (row) => <span style={{ color: 'var(--color-danger)', fontWeight: 'var(--font-weight-semibold)' }}>₹{Number(row.activeRouteCredit || 0).toLocaleString('en-IN')}</span>
    }
  ]

  return (
    <div className="page-container">
      {/* Header */}
      <div className="page-header">
        <div>
          <h2 className="page-title">Salesmen & Routes</h2>
          <p className="page-subtitle">Track salesmen billing performance, credit recoveries, and route pending balances.</p>
        </div>
      </div>

      {/* KPI Section */}
      <div className="grid-4" style={{ marginBottom: 'var(--space-8)' }}>
        <StatCard
          icon={<Users size={24} />}
          label="Active Salesmen"
          value={totalSalesmen}
          color="var(--color-info)"
          delay={0}
        />
        <StatCard
          icon={<Award size={24} />}
          label="Top Performer"
          value={topSalesman ? Number(topSalesman.totalCollectionsMade) : 0}
          prefix="₹"
          suffix={topSalesman ? ` (${topSalesman.salesmanName})` : ''}
          color="var(--color-accent)"
          delay={1}
        />
        <StatCard
          icon={<TrendingUp size={24} />}
          label="Total Route Collections"
          value={totalCollections}
          prefix="₹"
          color="var(--color-success)"
          delay={2}
        />
        <StatCard
          icon={<TrendingDown size={24} />}
          label="Total Route Credit (O/S)"
          value={totalPendingCredit}
          prefix="₹"
          color="var(--color-danger)"
          delay={3}
        />
      </div>

      {/* Leaderboard/DataTable Section */}
      <motion.div
        className="card"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.2 }}
        style={{ padding: 'var(--space-4)' }}
      >
        <div className="card-header" style={{ marginBottom: 'var(--space-4)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
            <UserCheck size={20} style={{ color: 'var(--color-accent)' }} />
            <span className="card-title">Salesman Performance & Credit Recovery Metrics</span>
          </div>
        </div>

        <DataTable
          columns={columns}
          data={performance}
          loading={loading}
          searchPlaceholder="Search by salesman name..."
          emptyMessage="No salesmen registered in the system. Go to Users to add a salesman."
        />
      </motion.div>
    </div>
  )
}

import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  LineChart, Line, BarChart, Bar, PieChart, Pie, Cell,
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer
} from 'recharts';
import {
  TrendingUp, TrendingDown, DollarSign, Activity, AlertCircle,
  Check, Zap, Database, Cloud, HardDrive, Download,
  Sun, Moon, ChevronUp, ChevronDown
} from 'lucide-react';

const CalculatorCostDashboard = () => {
  const [data, setData] = useState(null);
  const [selectedPeriod, setSelectedPeriod] = useState('30d');
  const [selectedCalculator, setSelectedCalculator] = useState('all');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [theme, setTheme] = useState(() => localStorage.getItem('theme') || 'light');
  const [sortConfig, setSortConfig] = useState({ key: 'startTime', direction: 'desc' });

  const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

  // Theme management
  useEffect(() => {
    if (theme === 'dark') {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
    localStorage.setItem('theme', theme);
  }, [theme]);

  const toggleTheme = () => {
    setTheme(prev => prev === 'light' ? 'dark' : 'light');
  };

  const fetchData = useCallback(async (signal) => {
    setLoading(true);
    setError(null);
    try {
      const url = new URL('/api/v1/cost-analytics', API_BASE_URL);
      url.searchParams.append('period', selectedPeriod);
      url.searchParams.append('calculator', selectedCalculator);
      const response = await fetch(url, { signal });
      if (!response.ok) throw new Error(`Server error: ${response.status}`);
      const result = await response.json();
      const mappedData = {
        ...result,
        costBreakdown: [
          { name: 'DBU Compute', value: result.dailyCosts.reduce((s, d) => s + (d.dbuCost || 0), 0), color: '#0ea5e9' },
          { name: 'VM Infrastructure', value: result.dailyCosts.reduce((s, d) => s + (d.vmCost || 0), 0), color: '#8b5cf6' },
          { name: 'Storage', value: result.dailyCosts.reduce((s, d) => s + (d.storageCost || 0), 0), color: '#10b981' },
          { name: 'Network', value: result.dailyCosts.reduce((s, d) => s + (d.networkCost || 0), 0), color: '#f59e0b' }
        ],
        efficiency: {
          spotInstanceUsage: 68,
          photonAdoption: 45,
          avgClusterUtilization: 73,
          retryRate: 5.2,
          failureRate: 2.8
        }
      };
      setData(mappedData);
    } catch (err) {
      if (err.name !== 'AbortError') {
        console.error('Failed to fetch data', err);
        setError('Failed to fetch data from server. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  }, [selectedPeriod, selectedCalculator, API_BASE_URL]);

  useEffect(() => {
    const controller = new AbortController();
    fetchData(controller.signal);
    return () => controller.abort();
  }, [fetchData]);

  const refetch = useCallback(() => {
    const controller = new AbortController();
    fetchData(controller.signal);
  }, [fetchData]);

  const stats = useMemo(() => {
    if (!data) return null;
    let totalCost = 0;
    let totalRuns = 0;
    let avgEfficiency = 0;
    if (selectedCalculator === 'all') {
      totalRuns = data.calculatorCosts.reduce((sum, c) => sum + (c.totalRuns || 0), 0);
      totalCost = data.calculatorCosts.reduce((sum, c) => sum + (c.totalCost || 0), 0);
      avgEfficiency = data.calculatorCosts.length > 0
        ? data.calculatorCosts.reduce((sum, c) => sum + (c.efficiencyScore || 0), 0) / data.calculatorCosts.length
        : 0;
    } else {
      const specificCalc = data.calculatorCosts.find(c => c.calculatorId === selectedCalculator);
      if (specificCalc) {
        totalCost = specificCalc.totalCost || 0;
        totalRuns = specificCalc.totalRuns || 0;
        avgEfficiency = specificCalc.efficiencyScore || 0;
      }
    }
    const avgCostPerRun = totalRuns > 0 ? totalCost / totalRuns : 0;
    let costTrend = 0;
    if (data.dailyCosts.length >= 14) {
      const currentWeek = data.dailyCosts.slice(-7).reduce((s, d) => s + (d.totalCost || 0), 0);
      const prevWeek = data.dailyCosts.slice(-14, -7).reduce((s, d) => s + (d.totalCost || 0), 0);
      costTrend = prevWeek > 0 ? ((currentWeek - prevWeek) / prevWeek) * 100 : 0;
    }
    return { totalCost, totalRuns, avgCostPerRun, costTrend, avgEfficiency };
  }, [data, selectedCalculator]);

  const sortedRecentRuns = useMemo(() => {
    if (!data?.recentRuns) return [];
    let sorted = [...data.recentRuns];
    if (sortConfig.key) {
      sorted.sort((a, b) => {
        let aVal = a[sortConfig.key];
        let bVal = b[sortConfig.key];
        if (sortConfig.key === 'startTime') {
          aVal = new Date(aVal).getTime();
          bVal = new Date(bVal).getTime();
        } else if (typeof aVal === 'number' && typeof bVal === 'number') {
          // numeric compare
        } else {
          aVal = String(aVal).toLowerCase();
          bVal = String(bVal).toLowerCase();
        }
        if (aVal < bVal) return sortConfig.direction === 'asc' ? -1 : 1;
        if (aVal > bVal) return sortConfig.direction === 'asc' ? 1 : -1;
        return 0;
      });
    }
    return sorted;
  }, [data?.recentRuns, sortConfig]);

  const handleExportCSV = () => {
    if (!data?.recentRuns?.length) return;
    const headers = ['Run ID', 'Calculator', 'Start Time', 'Duration', 'Workers', 'DBU Cost', 'VM Cost', 'Total Cost', 'Status'];
    const rows = data.recentRuns.map(run => [
      run.runId,
      run.calculatorName,
      new Date(run.startTime).toISOString(),
      `${Math.floor((run.durationSeconds || 0) / 60)}m ${(run.durationSeconds || 0) % 60}s`,
      run.workerCount,
      (run.dbuCost || 0).toFixed(2),
      (run.vmCost || 0).toFixed(2),
      (run.totalCost || 0).toFixed(2),
      run.status
    ]);
    const csvContent = [headers.join(','), ...rows.map(row => row.join(','))].join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `cost-runs-${new Date().toISOString().split('T')[0]}.csv`;
    link.click();
  };

  const getSortIcon = (key) => {
    if (sortConfig.key !== key) return null;
    return sortConfig.direction === 'asc' ? <ChevronUp size={14} /> : <ChevronDown size={14} />;
  };

  const handleSort = (key) => {
    setSortConfig(prev => ({
      key,
      direction: prev.key === key && prev.direction === 'asc' ? 'desc' : 'asc'
    }));
  };

  if (loading && !data) {
    return (
      <div className="min-h-screen bg-slate-50 dark:bg-slate-950 p-8">
        <div className="animate-pulse space-y-8">
          <div className="flex justify-between">
            <div className="space-y-2">
              <div className="h-9 w-80 bg-slate-200 dark:bg-slate-700 rounded-lg"></div>
              <div className="h-4 w-96 bg-slate-200 dark:bg-slate-700 rounded"></div>
            </div>
            <div className="h-10 w-10 bg-slate-200 dark:bg-slate-700 rounded-lg"></div>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
            {Array(4).fill(0).map((_, i) => (
              <div key={i} className="bg-white dark:bg-slate-800 rounded-2xl h-32"></div>
            ))}
          </div>
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="lg:col-span-2 h-96 bg-white dark:bg-slate-800 rounded-2xl"></div>
            <div className="h-96 bg-white dark:bg-slate-800 rounded-2xl"></div>
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50 dark:bg-slate-950">
        <div className="text-center">
          <div className="text-red-500 mb-4">
            <AlertCircle size={48} />
          </div>
          <p className="text-lg text-slate-700 dark:text-slate-300 mb-2">{error}</p>
          <button
            onClick={refetch}
            className="px-6 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-medium transition-colors"
          >
            Retry
          </button>
        </div>
      </div>
    );
  }

  if (!data || !stats) return null;

  const StatCard = ({ title, value, subtitle, icon: Icon, trend, accentColor }) => (
    <div className="group bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-2xl p-6 shadow-sm hover:shadow transition-all hover:-translate-y-0.5">
      <div className="flex justify-between items-start">
        <div className="flex-1">
          <p className="uppercase text-xs font-medium tracking-widest text-slate-500 dark:text-slate-400">{title}</p>
          <p className="text-3xl font-semibold mt-3 text-slate-900 dark:text-white">{value}</p>
          {subtitle && <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">{subtitle}</p>}
          {trend !== undefined && (
            <div className={`flex items-center gap-1 mt-3 text-sm font-medium ${trend >= 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-red-600 dark:text-red-400'}`}>
              {trend >= 0 ? <TrendingUp size={16} /> : <TrendingDown size={16} />}
              <span>{Math.abs(trend).toFixed(1)}% vs last period</span>
            </div>
          )}
        </div>
        <div className={`p-3 rounded-2xl transition-colors ${accentColor === '#0ea5e9' ? 'bg-sky-100 dark:bg-sky-950' : ''}`}>
          <Icon size={28} className="text-slate-400 group-hover:text-slate-600 dark:group-hover:text-slate-200" style={{ color: accentColor }} />
        </div>
      </div>
    </div>
  );

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-950">
      {/* Top Navigation */}
      <div className="border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 sticky top-0 z-10">
        <div className="max-w-screen-2xl mx-auto px-8 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 bg-blue-600 rounded-xl flex items-center justify-center">
              <span className="text-white font-bold text-xl">C</span>
            </div>
            <div>
              <h1 className="text-2xl font-semibold text-slate-900 dark:text-white tracking-tight">Cost Analytics</h1>
              <p className="text-xs text-slate-500 dark:text-slate-400">Calculator platform</p>
            </div>
          </div>

          <div className="flex items-center gap-6">
            {/* Filters */}
            <div className="flex gap-3">
              <select
                aria-label="Select Period"
                value={selectedPeriod}
                onChange={(e) => setSelectedPeriod(e.target.value)}
                className="bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg px-4 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all"
              >
                <option value="7d">Last 7 days</option>
                <option value="30d">Last 30 days</option>
                <option value="90d">Last 90 days</option>
              </select>
              <select
                aria-label="Select Calculator"
                value={selectedCalculator}
                onChange={(e) => setSelectedCalculator(e.target.value)}
                className="bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg px-4 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all"
              >
                <option value="all">All Calculators</option>
                {data.calculatorCosts.map(c => (
                  <option key={c.calculatorId} value={c.calculatorId}>
                    {c.calculatorName}
                  </option>
                ))}
              </select>
            </div>

            <button
              onClick={toggleTheme}
              className="p-2.5 rounded-xl hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
              aria-label="Toggle theme"
            >
              {theme === 'light' ? <Moon size={20} className="text-slate-600" /> : <Sun size={20} className="text-slate-400" />}
            </button>

            <button
              onClick={handleExportCSV}
              className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white px-5 py-2 rounded-xl text-sm font-medium transition-colors shadow-sm"
            >
              <Download size={16} />
              Export CSV
            </button>
          </div>
        </div>
      </div>

      <div className="max-w-screen-2xl mx-auto px-8 py-10">
        <div className="mb-10">
          <h2 className="text-3xl font-semibold text-slate-900 dark:text-white">Dashboard</h2>
          <p className="text-slate-600 dark:text-slate-400 mt-1">Operational cost visibility and optimization</p>
        </div>

        {/* Stats Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-10">
          <StatCard
            title="Total Cost"
            value={`$${stats.totalCost.toLocaleString()}`}
            subtitle={selectedCalculator === 'all' ? 'Across all calculators' : 'Selected calculator'}
            icon={DollarSign}
            trend={stats.costTrend}
            accentColor="#0ea5e9"
          />
          <StatCard
            title="Total Runs"
            value={stats.totalRuns.toLocaleString()}
            subtitle={selectedCalculator === 'all' ? 'Completed executions' : 'Selected calculator'}
            icon={Activity}
            accentColor="#8b5cf6"
          />
          <StatCard
            title="Avg Cost / Run"
            value={`$${stats.avgCostPerRun.toFixed(2)}`}
            subtitle={selectedCalculator === 'all' ? 'Per execution' : 'Selected calculator'}
            icon={Zap}
            trend={-2.8}
            accentColor="#10b981"
          />
          <StatCard
            title="Avg Efficiency"
            value={`${stats.avgEfficiency.toFixed(1)}%`}
            subtitle={selectedCalculator === 'all' ? 'Overall optimization' : 'Selected calculator'}
            icon={Check}
            trend={4.2}
            accentColor="#f59e0b"
          />
        </div>

        {/* Charts Row 1 */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-10">
          {/* Cost Trend Area Chart */}
          <div className="lg:col-span-2 bg-white dark:bg-slate-800 rounded-3xl shadow-sm border border-slate-200 dark:border-slate-700 p-7">
            <h2 className="text-xl font-semibold text-slate-900 dark:text-white mb-6">Cost Trend Over Time</h2>
            <ResponsiveContainer width="100%" height={320}>
              <AreaChart data={data.dailyCosts} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id="colorTotal" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#0ea5e9" stopOpacity={0.25}/>
                    <stop offset="95%" stopColor="#0ea5e9" stopOpacity={0}/>
                  </linearGradient>
                  <linearGradient id="colorDBU" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#8b5cf6" stopOpacity={0.25}/>
                    <stop offset="95%" stopColor="#8b5cf6" stopOpacity={0}/>
                  </linearGradient>
                  <linearGradient id="colorVM" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#10b981" stopOpacity={0.25}/>
                    <stop offset="95%" stopColor="#10b981" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" className="dark:stroke-slate-700" />
                <XAxis
                  dataKey="date"
                  stroke="#64748b"
                  tickFormatter={(str) => new Date(str).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
                  style={{ fontSize: '12px' }}
                />
                <YAxis stroke="#64748b" style={{ fontSize: '12px' }} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: theme === 'dark' ? '#1e2937' : '#ffffff',
                    border: '1px solid #e2e8f0',
                    borderRadius: '12px',
                    boxShadow: '0 10px 15px -3px rgb(0 0 0 / 0.1)'
                  }}
                  formatter={(value) => `$${value.toFixed(2)}`}
                />
                <Legend />
                <Area type="monotone" dataKey="totalCost" stroke="#0ea5e9" strokeWidth={2.5} fillOpacity={1} fill="url(#colorTotal)" name="Total Cost" />
                <Area type="monotone" dataKey="dbuCost" stroke="#8b5cf6" strokeWidth={2} fillOpacity={1} fill="url(#colorDBU)" name="DBU Cost" />
                <Area type="monotone" dataKey="vmCost" stroke="#10b981" strokeWidth={2} fillOpacity={1} fill="url(#colorVM)" name="VM Cost" />
              </AreaChart>
            </ResponsiveContainer>
          </div>

          {/* Cost Breakdown Pie */}
          <div className="bg-white dark:bg-slate-800 rounded-3xl shadow-sm border border-slate-200 dark:border-slate-700 p-7">
            <h2 className="text-xl font-semibold text-slate-900 dark:text-white mb-6">Cost Breakdown</h2>
            <ResponsiveContainer width="100%" height={260}>
              <PieChart>
                <Pie
                  data={data.costBreakdown}
                  cx="50%"
                  cy="50%"
                  innerRadius={55}
                  outerRadius={95}
                  dataKey="value"
                  labelLine={false}
                >
                  {data.costBreakdown.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip formatter={(value) => `$${value.toLocaleString()}`} />
              </PieChart>
            </ResponsiveContainer>
            <div className="mt-6 space-y-3">
              {data.costBreakdown.map((item) => (
                <div key={item.name} className="flex items-center justify-between text-sm">
                  <div className="flex items-center gap-3">
                    <div className="w-3.5 h-3.5 rounded-full" style={{ backgroundColor: item.color }}></div>
                    <span className="text-slate-700 dark:text-slate-300">{item.name}</span>
                  </div>
                  <span className="font-medium text-slate-900 dark:text-white">
                    ${(item.value || 0).toLocaleString()}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Charts Row 2 */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-10">
          {/* Cost by Calculator Bar Chart */}
          <div className="bg-white dark:bg-slate-800 rounded-3xl shadow-sm border border-slate-200 dark:border-slate-700 p-7">
            <h2 className="text-xl font-semibold text-slate-900 dark:text-white mb-6">Cost by Calculator</h2>
            <ResponsiveContainer width="100%" height={340}>
              <BarChart data={data.calculatorCosts} margin={{ top: 20, right: 30, left: 20, bottom: 60 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" className="dark:stroke-slate-700" />
                <XAxis
                  dataKey="calculatorName"
                  stroke="#64748b"
                  angle={-45}
                  textAnchor="end"
                  height={80}
                  style={{ fontSize: '12px' }}
                />
                <YAxis stroke="#64748b" style={{ fontSize: '12px' }} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: theme === 'dark' ? '#1e2937' : '#ffffff',
                    border: '1px solid #e2e8f0',
                    borderRadius: '12px'
                  }}
                  formatter={(value) => `$${value.toFixed(2)}`}
                />
                <Legend />
                <Bar dataKey="dbuCost" stackId="a" fill="#0ea5e9" name="DBU Cost" />
                <Bar dataKey="vmCost" stackId="a" fill="#8b5cf6" name="VM Cost" />
                <Bar dataKey="storageCost" stackId="a" fill="#10b981" name="Storage Cost" />
              </BarChart>
            </ResponsiveContainer>
          </div>

          {/* Optimization Opportunities */}
          <div className="bg-white dark:bg-slate-800 rounded-3xl shadow-sm border border-slate-200 dark:border-slate-700 p-7">
            <h2 className="text-xl font-semibold text-slate-900 dark:text-white mb-6">Optimization Opportunities</h2>
            <div className="space-y-8">
              {[
                { label: 'Spot Instance Usage', value: data.efficiency.spotInstanceUsage, icon: Cloud, color: 'from-sky-500 to-blue-600', target: 'Target: ≥80%' },
                { label: 'Photon Adoption', value: data.efficiency.photonAdoption, icon: Zap, color: 'from-violet-500 to-purple-600', target: '20-30% potential savings' },
                { label: 'Cluster Utilization', value: data.efficiency.avgClusterUtilization, icon: Database, color: 'from-emerald-500 to-green-600', target: 'Excellent' },
                { label: 'Retry Rate', value: data.efficiency.retryRate, icon: AlertCircle, color: 'from-amber-500 to-orange-500', target: `Est. waste: $${((stats.totalCost * data.efficiency.retryRate) / 100).toFixed(0)}` }
              ].map((item, index) => (
                <div key={index}>
                  <div className="flex justify-between items-center mb-2">
                    <div className="flex items-center gap-2 text-slate-600 dark:text-slate-400">
                      <item.icon size={18} />
                      <span className="font-medium">{item.label}</span>
                    </div>
                    <span className="font-semibold text-lg text-slate-900 dark:text-white">{item.value}%</span>
                  </div>
                  <div className="h-2 bg-slate-100 dark:bg-slate-700 rounded-full overflow-hidden">
                    <div
                      className={`h-full bg-gradient-to-r ${item.color} rounded-full transition-all duration-700`}
                      style={{ width: `${Math.min(item.value * (item.label === 'Retry Rate' ? 5 : 1), 100)}%` }}
                    ></div>
                  </div>
                  <p className="text-xs text-slate-500 dark:text-slate-400 mt-1.5">{item.target}</p>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Recent Runs Table */}
        <div className="bg-white dark:bg-slate-800 rounded-3xl shadow-sm border border-slate-200 dark:border-slate-700 overflow-hidden">
          <div className="px-7 py-5 border-b border-slate-200 dark:border-slate-700 flex justify-between items-center">
            <h2 className="text-xl font-semibold text-slate-900 dark:text-white">Recent Calculator Runs</h2>
            <div className="text-xs text-slate-500 dark:text-slate-400">Showing latest 10 • Sorted by {sortConfig.key}</div>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-900/80">
                  <th onClick={() => handleSort('runId')} className="cursor-pointer py-4 px-6 text-left text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-widest hover:text-slate-700 dark:hover:text-slate-300">
                    <div className="flex items-center gap-1">Run ID {getSortIcon('runId')}</div>
                  </th>
                  <th onClick={() => handleSort('calculatorName')} className="cursor-pointer py-4 px-6 text-left text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-widest hover:text-slate-700 dark:hover:text-slate-300">
                    <div className="flex items-center gap-1">Calculator {getSortIcon('calculatorName')}</div>
                  </th>
                  <th onClick={() => handleSort('startTime')} className="cursor-pointer py-4 px-6 text-left text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-widest hover:text-slate-700 dark:hover:text-slate-300">
                    <div className="flex items-center gap-1">Start Time {getSortIcon('startTime')}</div>
                  </th>
                  <th className="py-4 px-6 text-left text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-widest">Duration</th>
                  <th className="py-4 px-6 text-left text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-widest">Workers</th>
                  <th className="py-4 px-6 text-left text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-widest">DBU Cost</th>
                  <th className="py-4 px-6 text-left text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-widest">VM Cost</th>
                  <th className="py-4 px-6 text-left text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-widest">Total Cost</th>
                  <th className="py-4 px-6 text-left text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-widest">Status</th>
                </tr>
              </thead>
              <tbody>
                {sortedRecentRuns.slice(0, 10).map((run, index) => (
                  <tr key={run.id || index} className={`border-b border-slate-100 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-900/60 transition-colors ${index % 2 === 1 ? 'bg-slate-50/70 dark:bg-slate-900/40' : ''}`}>
                    <td className="py-4 px-6 font-mono text-sm text-slate-600 dark:text-slate-300">{run.runId}</td>
                    <td className="py-4 px-6 font-medium text-slate-800 dark:text-slate-200">{run.calculatorName}</td>
                    <td className="py-4 px-6 text-sm text-slate-600 dark:text-slate-400">
                      {new Date(run.startTime).toLocaleString('en-US', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' })}
                    </td>
                    <td className="py-4 px-6 text-sm text-slate-600 dark:text-slate-400">
                      {Math.floor((run.durationSeconds || 0) / 60)}m {(run.durationSeconds || 0) % 60}s
                    </td>
                    <td className="py-4 px-6 text-sm text-slate-600 dark:text-slate-400">{run.workerCount}</td>
                    <td className="py-4 px-6 font-medium text-sky-600 dark:text-sky-400">${(run.dbuCost || 0).toFixed(2)}</td>
                    <td className="py-4 px-6 font-medium text-violet-600 dark:text-violet-400">${(run.vmCost || 0).toFixed(2)}</td>
                    <td className="py-4 px-6 font-semibold text-slate-900 dark:text-white">${(run.totalCost || 0).toFixed(2)}</td>
                    <td className="py-4 px-6">
                      <span className={`inline-block px-3 py-0.5 text-xs font-medium rounded-full ${run.status === 'SUCCESS' ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/50 dark:text-emerald-300' : 'bg-red-100 text-red-700 dark:bg-red-900/50 dark:text-red-300'}`}>
                        {run.status}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="mt-12 text-center">
          <p className="text-xs text-slate-500 dark:text-slate-400">Last updated: {new Date().toLocaleString()}</p>
          <p className="text-xs text-slate-400 dark:text-slate-500 mt-1">Data refreshed daily at 6:00 AM UTC • Enterprise Cost Dashboard v2.1</p>
        </div>
      </div>
    </div>
  );
};

export default CalculatorCostDashboard;
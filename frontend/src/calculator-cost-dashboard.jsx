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

// Brand Color System
const brand = {
  black: '#000000',
  white: '#FFFFFF',
  red: '#E60000',
  redWeb: '#DA0000',
};

const reds = {
  100: '#FDEAEA',
  300: '#F28B8B',
  500: '#E60000',
  600: '#DA0000',
  700: '#BD000C',
  800: '#BA0000',
  900: '#A43725',
};

const warm = {
  sand: '#B39A74',
  caramel: '#CFBD9B',
  ginger: '#E1D5BD',
  chocolate: '#4D3C2F',
  clay: '#7B6B59',
  mouse: '#BEB29E',
  curry: '#E5B01C',
  amber: '#F2C551',
  honey: '#EDC860',
  straw: '#F2D88E',
  terracotta: '#C07156',
  chestnut: '#A43725',
};

const cool = {
  pine: '#00898D',
  mint: '#55B1B4',
  sage: '#97D3CC',
  olive: '#90A723',
  oliveDark: '#677D00',
  kiwi: '#CDD760',
  kiwiDark: '#606917',
  lagoon: '#009BD2',
  lagoonDark: '#095F95',
  lake: '#6FB6DF',
  glacier: '#A1CCE4',
  plum: '#3A578A',
  lilac: '#6C87B3',
  lavender: '#AFBCD5',
};

const neutral = {
  0: '#FFFFFF',
  5: '#FAFAFA',
  10: '#F5F5F5',
  20: '#EEEEEE',
  30: '#D7D7D7',
  40: '#CCCCCC',
  50: '#BEBEBE',
  60: '#AAAAAA',
  70: '#919191',
  80: '#646464',
  90: '#444444',
  100: '#000000',
};

const CalculatorCostDashboard = () => {
  const [data, setData] = useState(null);
  const [selectedPeriod, setSelectedPeriod] = useState('30d');
  const [selectedCalculator, setSelectedCalculator] = useState('all');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [theme, setTheme] = useState(() => localStorage.getItem('theme') || 'light');
  const [sortConfig, setSortConfig] = useState({ key: 'startTime', direction: 'desc' });

  const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

  // Theme colors
  const themeColors = {
    light: {
      background: neutral[5],
      surface: neutral[0],
      surfaceHover: neutral[10],
      border: neutral[30],
      borderLight: neutral[20],
      text: neutral[100],
      textSecondary: neutral[80],
      textMuted: neutral[70],
      textDisabled: neutral[60],
    },
    dark: {
      background: neutral[90],
      surface: neutral[100],
      surfaceHover: neutral[80],
      border: neutral[70],
      borderLight: neutral[80],
      text: neutral[0],
      textSecondary: neutral[20],
      textMuted: neutral[40],
      textDisabled: neutral[50],
    }
  };

  const colors = themeColors[theme];

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
        costBreakdown: Array.isArray(result?.costBreakdown?.breakdownItems)
          ? result.costBreakdown.breakdownItems.map((item, idx) => ({
              name: item.name,
              value: item.value,
              color: [cool.lagoon, cool.pine, warm.amber, warm.terracotta][idx % 4]
            }))
          : [],
        efficiency: result.efficiency || {
          spotInstanceUsage: 0,
          photonAdoption: 0,
          avgClusterUtilization: 0,
          retryRate: 0,
          failureRate: 0
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
      <div className="min-h-screen p-8" style={{ backgroundColor: colors.background }}>
        <div className="animate-pulse space-y-8">
          <div className="flex justify-between">
            <div className="space-y-2">
              <div className="h-9 w-80 rounded-lg" style={{ backgroundColor: neutral[20] }}></div>
              <div className="h-4 w-96 rounded" style={{ backgroundColor: neutral[20] }}></div>
            </div>
            <div className="h-10 w-10 rounded-lg" style={{ backgroundColor: neutral[20] }}></div>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
            {Array(4).fill(0).map((_, i) => (
              <div key={i} className="rounded-2xl h-32" style={{ backgroundColor: colors.surface }}></div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center" style={{ backgroundColor: colors.background }}>
        <div className="text-center">
          <div className="mb-4" style={{ color: reds[700] }}>
            <AlertCircle size={48} />
          </div>
          <p className="text-lg mb-2" style={{ color: colors.text }}>{error}</p>
          <button
            onClick={refetch}
            className="px-6 py-2 text-white rounded-lg font-medium transition-colors"
            style={{ backgroundColor: reds[600] }}
            onMouseEnter={(e) => e.target.style.backgroundColor = reds[700]}
            onMouseLeave={(e) => e.target.style.backgroundColor = reds[600]}
          >
            Retry
          </button>
        </div>
      </div>
    );
  }

  if (!data || !stats) return null;

  const StatCard = ({ title, value, subtitle, icon: Icon, trend, accentColor }) => (
    <div
      className="group rounded-2xl p-6 shadow-sm transition-all hover:shadow-md hover:-translate-y-0.5"
      style={{
        backgroundColor: colors.surface,
        border: `1px solid ${colors.border}`
      }}
    >
      <div className="flex justify-between items-start">
        <div className="flex-1">
          <p
            className="uppercase text-xs font-medium tracking-widest"
            style={{ color: colors.textMuted }}
          >
            {title}
          </p>
          <p
            className="text-3xl font-semibold mt-3"
            style={{ color: colors.text }}
          >
            {value}
          </p>
          {subtitle && (
            <p className="text-sm mt-1" style={{ color: colors.textSecondary }}>
              {subtitle}
            </p>
          )}
          {trend !== undefined && (
            <div
              className="flex items-center gap-1 mt-3 text-sm font-medium"
              style={{ color: trend >= 0 ? cool.olive : reds[700] }}
            >
              {trend >= 0 ? <TrendingUp size={16} /> : <TrendingDown size={16} />}
              <span>{Math.abs(trend).toFixed(1)}% vs last period</span>
            </div>
          )}
        </div>
        <div
          className="p-3 rounded-2xl transition-colors"
          style={{ backgroundColor: theme === 'dark' ? neutral[80] : neutral[10] }}
        >
          <Icon size={28} style={{ color: accentColor }} />
        </div>
      </div>
    </div>
  );

  return (
    <div className="min-h-screen" style={{ backgroundColor: colors.background }}>
      {/* Top Navigation */}
      <div
        className="border-b sticky top-0 z-10"
        style={{
          backgroundColor: colors.surface,
          borderColor: colors.border
        }}
      >
        <div className="max-w-screen-2xl mx-auto px-8 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div
              className="w-9 h-9 rounded-xl flex items-center justify-center"
              style={{ backgroundColor: reds[600] }}
            >
              <span className="text-white font-bold text-xl">C</span>
            </div>
            <div>
              <h1 className="text-2xl font-semibold tracking-tight" style={{ color: colors.text }}>
                Calculator Cost Analytics
              </h1>
              <p className="text-xs" style={{ color: colors.textMuted }}>
                Calculator Framework
              </p>
            </div>
          </div>

          <div className="flex items-center gap-6">
            {/* Filters */}
            <div className="flex gap-3">
              <select
                aria-label="Select Period"
                value={selectedPeriod}
                onChange={(e) => setSelectedPeriod(e.target.value)}
                className="rounded-lg px-4 py-2 text-sm focus:ring-2 focus:border-transparent transition-all"
                style={{
                  backgroundColor: colors.surface,
                  border: `1px solid ${colors.border}`,
                  color: colors.text,
                  outline: 'none'
                }}
                onFocus={(e) => e.target.style.borderColor = reds[600]}
                onBlur={(e) => e.target.style.borderColor = colors.border}
              >
                <option value="7d" style={{ backgroundColor: colors.surface, color: colors.text }}>Last 7 days</option>
                <option value="30d" style={{ backgroundColor: colors.surface, color: colors.text }}>Last 30 days</option>
                <option value="90d" style={{ backgroundColor: colors.surface, color: colors.text }}>Last 90 days</option>
              </select>
              <select
                aria-label="Select Calculator"
                value={selectedCalculator}
                onChange={(e) => setSelectedCalculator(e.target.value)}
                className="rounded-lg px-4 py-2 text-sm focus:ring-2 focus:border-transparent transition-all"
                style={{
                  backgroundColor: colors.surface,
                  border: `1px solid ${colors.border}`,
                  color: colors.text,
                  outline: 'none'
                }}
                onFocus={(e) => e.target.style.borderColor = reds[600]}
                onBlur={(e) => e.target.style.borderColor = colors.border}
              >
                <option value="all" style={{ backgroundColor: colors.surface, color: colors.text }}>All Calculators</option>
                {data.calculatorCosts.map(c => (
                  <option key={c.calculatorId} value={c.calculatorId} style={{ backgroundColor: colors.surface, color: colors.text }}>
                    {c.calculatorName}
                  </option>
                ))}
              </select>
            </div>

            <button
              onClick={toggleTheme}
              className="p-2.5 rounded-xl transition-colors"
              aria-label="Toggle theme"
              style={{ backgroundColor: theme === 'dark' ? neutral[80] : neutral[10] }}
              onMouseEnter={(e) => e.target.style.backgroundColor = theme === 'dark' ? neutral[70] : neutral[20]}
              onMouseLeave={(e) => e.target.style.backgroundColor = theme === 'dark' ? neutral[80] : neutral[10]}
            >
              {theme === 'light' ? (
                <Moon size={20} style={{ color: neutral[80] }} />
              ) : (
                <Sun size={20} style={{ color: neutral[40] }} />
              )}
            </button>

            <button
              onClick={handleExportCSV}
              className="flex items-center gap-2 text-white px-5 py-2 rounded-xl text-sm font-medium transition-colors shadow-sm"
              style={{ backgroundColor: reds[600] }}
              onMouseEnter={(e) => e.target.style.backgroundColor = reds[700]}
              onMouseLeave={(e) => e.target.style.backgroundColor = reds[600]}
            >
              <Download size={16} />
              Export CSV
            </button>
          </div>
        </div>
      </div>

      <div className="max-w-screen-2xl mx-auto px-8 py-10">
        <div className="mb-10">
          <h2 className="text-3xl font-semibold" style={{ color: colors.text }}>
            Dashboard
          </h2>
          <p className="mt-1" style={{ color: colors.textSecondary }}>
            Operational calculator cost visibility
          </p>
        </div>

        {/* Stats Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-10">
          <StatCard
            title="Total Cost"
            value={`$${stats.totalCost.toLocaleString()}`}
            subtitle={selectedCalculator === 'all' ? 'Across all calculators' : 'Selected calculator'}
            icon={DollarSign}
            trend={stats.costTrend}
            accentColor={reds[600]}
          />
          <StatCard
            title="Total Runs"
            value={stats.totalRuns.toLocaleString()}
            subtitle={selectedCalculator === 'all' ? 'Completed executions' : 'Selected calculator'}
            icon={Activity}
            accentColor={cool.lagoon}
          />
          <StatCard
            title="Avg Cost / Run"
            value={`$${stats.avgCostPerRun.toFixed(2)}`}
            subtitle={selectedCalculator === 'all' ? 'Per execution' : 'Selected calculator'}
            icon={Zap}
            trend={-2.8}
            accentColor={warm.amber}
          />
          <StatCard
            title="Avg Efficiency"
            value={`${stats.avgEfficiency.toFixed(1)}%`}
            subtitle={selectedCalculator === 'all' ? 'Overall optimization' : 'Selected calculator'}
            icon={Check}
            trend={4.2}
            accentColor={cool.olive}
          />
        </div>

        {/* Charts Row 1 */}
        <div className="mb-10">
          {/* Cost Trend Area Chart */}
          <div
            className="rounded-3xl shadow-sm p-7"
            style={{
              backgroundColor: colors.surface,
              border: `1px solid ${colors.border}`
            }}
          >
            <h2 className="text-xl font-semibold mb-6" style={{ color: colors.text }}>
              Cost Trend Over Time
            </h2>
            <ResponsiveContainer width="100%" height={360}>
              <AreaChart data={data.dailyCosts} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id="colorTotal" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor={cool.lagoon} stopOpacity={0.25}/>
                    <stop offset="95%" stopColor={cool.lagoon} stopOpacity={0}/>
                  </linearGradient>
                  <linearGradient id="colorDBU" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor={cool.pine} stopOpacity={0.25}/>
                    <stop offset="95%" stopColor={cool.pine} stopOpacity={0}/>
                  </linearGradient>
                  <linearGradient id="colorVM" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor={warm.amber} stopOpacity={0.25}/>
                    <stop offset="95%" stopColor={warm.amber} stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke={neutral[30]} />
                <XAxis
                  dataKey="date"
                  stroke={neutral[70]}
                  tickFormatter={(str) => new Date(str).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
                  style={{ fontSize: '12px' }}
                />
                <YAxis stroke={neutral[70]} style={{ fontSize: '12px' }} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: colors.surface,
                    border: `1px solid ${colors.border}`,
                    borderRadius: '12px',
                    boxShadow: '0 10px 15px -3px rgb(0 0 0 / 0.1)'
                  }}
                  formatter={(value) => `$${value.toFixed(2)}`}
                />
                <Legend />
                <Area type="monotone" dataKey="totalCost" stroke={cool.lagoon} strokeWidth={2.5} fillOpacity={1} fill="url(#colorTotal)" name="Total Cost" />
                <Area type="monotone" dataKey="dbuCost" stroke={cool.pine} strokeWidth={2} fillOpacity={1} fill="url(#colorDBU)" name="DBU Cost" />
                <Area type="monotone" dataKey="vmCost" stroke={warm.amber} strokeWidth={2} fillOpacity={1} fill="url(#colorVM)" name="VM Cost" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Charts Row 2 */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-10">
          {/* Cost by Calculator Bar Chart */}
          <div
            className="rounded-3xl shadow-sm p-7"
            style={{
              backgroundColor: colors.surface,
              border: `1px solid ${colors.border}`
            }}
          >
            <h2 className="text-xl font-semibold mb-6" style={{ color: colors.text }}>
              Cost by Calculator
            </h2>
            <ResponsiveContainer width="100%" height={340}>
              <BarChart data={data.calculatorCosts} margin={{ top: 20, right: 30, left: 20, bottom: 60 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={neutral[30]} />
                <XAxis
                  dataKey="calculatorName"
                  stroke={neutral[70]}
                  angle={-45}
                  textAnchor="end"
                  height={80}
                  style={{ fontSize: '12px' }}
                />
                <YAxis stroke={neutral[70]} style={{ fontSize: '12px' }} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: colors.surface,
                    border: `1px solid ${colors.border}`,
                    borderRadius: '12px'
                  }}
                  formatter={(value) => `$${value.toFixed(2)}`}
                />
                <Legend />
                <Bar dataKey="dbuCost" stackId="a" fill={cool.lagoon} name="DBU Cost" />
                <Bar dataKey="vmCost" stackId="a" fill={cool.pine} name="VM Cost" />
                <Bar dataKey="storageCost" stackId="a" fill={warm.amber} name="Storage Cost" />
              </BarChart>
            </ResponsiveContainer>
          </div>

          {/* Cost Breakdown Pie  */}
          <div
            className="rounded-3xl shadow-sm p-7"
            style={{
              backgroundColor: colors.surface,
              border: `1px solid ${colors.border}`
            }}
          >
            <h2 className="text-xl font-semibold mb-6" style={{ color: colors.text }}>
              Cost Breakdown
            </h2>
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
                <Tooltip
                  formatter={(value) => `$${value.toLocaleString()}`}
                  contentStyle={{
                    backgroundColor: colors.surface,
                    border: `1px solid ${colors.border}`,
                    borderRadius: '8px'
                  }}
                />
              </PieChart>
            </ResponsiveContainer>
            <div className="mt-6 space-y-3">
              {data.costBreakdown.map((item) => (
                <div key={item.name} className="flex items-center justify-between text-sm">
                  <div className="flex items-center gap-3">
                    <div className="w-3.5 h-3.5 rounded-full" style={{ backgroundColor: item.color }}></div>
                    <span style={{ color: colors.textSecondary }}>{item.name}</span>
                  </div>
                  <span className="font-medium" style={{ color: colors.text }}>
                    ${(item.value || 0).toLocaleString()}
                  </span>
                </div>
              ))}
            </div>
      </div>
      </div>


        {/* Recent Runs Table */}
        <div
          className="rounded-3xl shadow-sm overflow-hidden"
          style={{
            backgroundColor: colors.surface,
            border: `1px solid ${colors.border}`
          }}
        >
          <div
            className="px-7 py-5 border-b flex justify-between items-center"
            style={{ borderColor: colors.border }}
          >
            <h2 className="text-xl font-semibold" style={{ color: colors.text }}>
              Recent Calculator Runs
            </h2>
            <div className="text-xs" style={{ color: colors.textMuted }}>
              Showing latest 10 • Sorted by {sortConfig.key}
            </div>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr
                  className="border-b"
                  style={{
                    backgroundColor: theme === 'dark' ? neutral[90] : neutral[10],
                    borderColor: colors.border
                  }}
                >
                  <th
                    onClick={() => handleSort('runId')}
                    className="cursor-pointer py-4 px-6 text-left text-xs font-semibold uppercase tracking-widest"
                    style={{ color: colors.textMuted }}
                  >
                    <div className="flex items-center gap-1">Run ID {getSortIcon('runId')}</div>
                  </th>
                  <th
                    onClick={() => handleSort('calculatorName')}
                    className="cursor-pointer py-4 px-6 text-left text-xs font-semibold uppercase tracking-widest"
                    style={{ color: colors.textMuted }}
                  >
                    <div className="flex items-center gap-1">Calculator {getSortIcon('calculatorName')}</div>
                  </th>
                  <th
                    onClick={() => handleSort('startTime')}
                    className="cursor-pointer py-4 px-6 text-left text-xs font-semibold uppercase tracking-widest"
                    style={{ color: colors.textMuted }}
                  >
                    <div className="flex items-center gap-1">Start Time {getSortIcon('startTime')}</div>
                  </th>
                  <th className="py-4 px-6 text-left text-xs font-semibold uppercase tracking-widest" style={{ color: colors.textMuted }}>Duration</th>
                  <th className="py-4 px-6 text-left text-xs font-semibold uppercase tracking-widest" style={{ color: colors.textMuted }}>Workers</th>
                  <th className="py-4 px-6 text-left text-xs font-semibold uppercase tracking-widest" style={{ color: colors.textMuted }}>DBU Cost</th>
                  <th className="py-4 px-6 text-left text-xs font-semibold uppercase tracking-widest" style={{ color: colors.textMuted }}>VM Cost</th>
                  <th className="py-4 px-6 text-left text-xs font-semibold uppercase tracking-widest" style={{ color: colors.textMuted }}>Total Cost</th>
                  <th className="py-4 px-6 text-left text-xs font-semibold uppercase tracking-widest" style={{ color: colors.textMuted }}>Status</th>
                </tr>
              </thead>
              <tbody>
                {sortedRecentRuns.slice(0, 10).map((run, index) => (
                  <tr
                    key={run.id || index}
                    className="border-b transition-colors"
                    style={{
                      backgroundColor: index % 2 === 1 ? (theme === 'dark' ? neutral[90] : neutral[5]) : colors.surface,
                      borderColor: colors.borderLight
                    }}
                    onMouseEnter={(e) => e.currentTarget.style.backgroundColor = colors.surfaceHover}
                    onMouseLeave={(e) => e.currentTarget.style.backgroundColor = index % 2 === 1 ? (theme === 'dark' ? neutral[90] : neutral[5]) : colors.surface}
                  >
                    <td className="py-4 px-6 font-mono text-sm" style={{ color: colors.textSecondary }}>
                      {run.runId}
                    </td>
                    <td className="py-4 px-6 font-medium" style={{ color: colors.text }}>
                      {run.calculatorName}
                    </td>
                    <td className="py-4 px-6 text-sm" style={{ color: colors.textSecondary }}>
                      {new Date(run.startTime).toLocaleString('en-US', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' })}
                    </td>
                    <td className="py-4 px-6 text-sm" style={{ color: colors.textSecondary }}>
                      {Math.floor((run.durationSeconds || 0) / 60)}m {(run.durationSeconds || 0) % 60}s
                    </td>
                    <td className="py-4 px-6 text-sm" style={{ color: colors.textSecondary }}>
                      {run.workerCount}
                    </td>
                    <td className="py-4 px-6 font-medium" style={{ color: cool.lagoon }}>
                      ${(run.dbuCost || 0).toFixed(2)}
                    </td>
                    <td className="py-4 px-6 font-medium" style={{ color: cool.pine }}>
                      ${(run.vmCost || 0).toFixed(2)}
                    </td>
                    <td className="py-4 px-6 font-semibold" style={{ color: colors.text }}>
                      ${(run.totalCost || 0).toFixed(2)}
                    </td>
                    <td className="py-4 px-6">
                      <span
                        className="inline-block px-3 py-0.5 text-xs font-medium rounded-full"
                        style={{
                          backgroundColor: run.status === 'SUCCESS'
                            ? (theme === 'dark' ? cool.oliveDark : cool.sage)
                            : (theme === 'dark' ? warm.chestnut : reds[100]),
                          color: run.status === 'SUCCESS'
                            ? (theme === 'dark' ? cool.sage : cool.oliveDark)
                            : (theme === 'dark' ? reds[100] : warm.chestnut)
                        }}
                      >
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
          <p className="text-xs" style={{ color: colors.textMuted }}>
            Last updated: {new Date().toLocaleString()}
          </p>
          <p className="text-xs mt-1" style={{ color: colors.textDisabled }}>
            Data refreshed daily at 6:00 AM UTC • Calculator Cost Dashboard v1.0
          </p>
        </div>
      </div>
    </div>
  );
};

export default CalculatorCostDashboard;

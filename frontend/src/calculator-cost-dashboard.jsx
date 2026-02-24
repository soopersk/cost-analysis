import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  BarChart, Bar, PieChart, Pie, Cell,
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer
} from 'recharts';
import {
  TrendingUp, TrendingDown, DollarSign, Activity, AlertCircle,
  Check, Zap, Download, Sun, Moon, ChevronUp, ChevronDown
} from 'lucide-react';

// Brand Color System

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
  const [monthlyCostTrend, setMonthlyCostTrend] = useState(null);
  const [trendLoading, setTrendLoading] = useState(false);

  const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

  // Theme colors
  const themeColors = {
    light: {
      background: neutral[10],   // background.base
      surface: neutral[0],       // background.surface
      surfaceHover: neutral[20], // background.hover
      border: neutral[40],       // border.default
      borderLight: neutral[30],  // border.disabled
      text: neutral[100],        // text.primary
      textSecondary: neutral[80],// text.secondary
      textMuted: neutral[70],
      textDisabled: neutral[60], // text.disabled
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
      url.searchParams.append('frequency', 'DAILY');
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

  const fetchMonthlyTrend = useCallback(async (signal) => {
    if (selectedCalculator === 'all') {
      setMonthlyCostTrend(null);
      return;
    }
    setTrendLoading(true);
    try {
      const url = new URL('/api/v1/cost-analytics/monthly-trend', API_BASE_URL);
      url.searchParams.append('frequency', 'DAILY');
      url.searchParams.append('calculatorId', selectedCalculator);
      url.searchParams.append('months', '13');
      const response = await fetch(url, { signal });
      if (!response.ok) throw new Error(`Server error: ${response.status}`);
      setMonthlyCostTrend(await response.json());
    } catch (err) {
      if (err.name !== 'AbortError') setMonthlyCostTrend(null);
    } finally {
      setTrendLoading(false);
    }
  }, [selectedCalculator, API_BASE_URL]);

  useEffect(() => {
    const controller = new AbortController();
    fetchData(controller.signal);
    return () => controller.abort();
  }, [fetchData]);

  useEffect(() => {
    const controller = new AbortController();
    fetchMonthlyTrend(controller.signal);
    return () => controller.abort();
  }, [fetchMonthlyTrend]);

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
        ? data.calculatorCosts.reduce((sum, c) => sum + (Number(c.successRate) || 0), 0) / data.calculatorCosts.length
        : 0;
    } else {
      const specificCalc = data.calculatorCosts.find(c => c.calculatorId === selectedCalculator);
      if (specificCalc) {
        totalCost = specificCalc.totalCost || 0;
        totalRuns = specificCalc.totalRuns || 0;
        avgEfficiency = Number(specificCalc.successRate) || 0;
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

  const calculatorMTDSummary = useMemo(() => {
    if (!monthlyCostTrend?.length) return null;
    return monthlyCostTrend[monthlyCostTrend.length - 1];
  }, [monthlyCostTrend]);

  const selectedCalculatorSummary = useMemo(() => {
    if (!data || selectedCalculator === 'all') return null;
    return data.calculatorCosts.find(c => c.calculatorId === selectedCalculator) || null;
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
      <div className="min-h-screen p-8 bg-dot-grid-light" style={{ backgroundColor: neutral[5] }}>
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
              <div key={i} className="rounded-2xl h-32" style={{ backgroundColor: neutral[0] }}></div>
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
            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = reds[700]}
            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = reds[600]}
          >
            Retry
          </button>
        </div>
      </div>
    );
  }

  if (!data || !stats) return null;

  // costMetric=true reverses the colour logic: cost increase is bad (red), decrease is good (olive)
  const StatCard = ({ title, value, subtitle, icon: Icon, trend, accentColor, delay = 0, costMetric = false }) => {
    // Determine trend colour: cost metrics invert the usual "up = good" convention
    const trendUp = trend >= 0;
    const trendGood = costMetric ? !trendUp : trendUp;
    const trendColor = trendGood ? cool.olive : reds[700];

    return (
      <div
        className="animate-slide-up rounded-2xl px-6 pt-5 pb-6 transition-all hover:shadow-md hover:-translate-y-0.5"
        style={{
          backgroundColor: colors.surface,
          border: `1px solid ${colors.border}`,
          animationDelay: `${delay}ms`,
        }}
      >
        <div className="flex items-center justify-between mb-4">
          <p
            className="uppercase text-xs font-semibold tracking-widest"
            style={{ color: colors.textDisabled }}
          >
            {title}
          </p>
          <div
            className="p-1.5 rounded-lg"
            style={{ backgroundColor: `${accentColor}18` }}
          >
            <Icon size={14} style={{ color: accentColor }} />
          </div>
        </div>
        <p
          className="stat-value animate-number tabular-nums"
          style={{ color: colors.text, animationDelay: `${delay + 80}ms` }}
        >
          {value}
        </p>
        {subtitle && (
          <p className="text-xs mt-2" style={{ color: colors.textDisabled }}>
            {subtitle}
          </p>
        )}
        {trend !== undefined && (
          <div
            className="flex items-center gap-1 mt-3 text-xs font-semibold"
            style={{ color: trendColor }}
          >
            {trendUp ? <TrendingUp size={12} /> : <TrendingDown size={12} />}
            <span>{Math.abs(trend).toFixed(1)}% vs last period</span>
          </div>
        )}
      </div>
    );
  };

  // Per-calculator detail card: MTD total, MoM%, YoY%, run count, avg/run, cost breakdown
  const CalculatorDetailCard = ({ summary, calculatorSummary, theme, colors }) => {
    const [yr, mo] = summary.month.split('-');
    const monthLabel = new Date(Number(yr), Number(mo) - 1)
      .toLocaleDateString('en-US', { month: 'long', year: 'numeric' }) + ' (MTD)';

    const toNum = (v) => (v == null ? null : Number(v));
    const momPct = toNum(summary.momPct);
    const yoyPct = toNum(summary.yoyPct);
    const momDelta = toNum(summary.momDelta);
    const yoyDelta = toNum(summary.yoyDelta);

    const formatDelta = (v) => {
      if (v == null) return '';
      const sign = v >= 0 ? '+' : '-';
      return `${sign}$${Math.abs(v).toLocaleString(undefined, { maximumFractionDigits: 0 })}`;
    };

    const dbuCost = calculatorSummary ? Number(calculatorSummary.dbuCost) || 0 : 0;
    const vmCost = calculatorSummary ? Number(calculatorSummary.vmCost) || 0 : 0;
    const storageCost = calculatorSummary ? Number(calculatorSummary.storageCost) || 0 : 0;
    const periodTotal = dbuCost + vmCost + storageCost || 1;
    const successRate = calculatorSummary ? Number(calculatorSummary.successRate) : null;

    const TrendRow = ({ pct, delta, label }) => {
      if (pct == null) {
        return (
          <div className="flex items-center gap-2">
            <span className="text-xs font-semibold uppercase tracking-widest w-8" style={{ color: colors.textMuted }}>{label}</span>
            <span className="text-sm" style={{ color: colors.textDisabled }}>N/A</span>
          </div>
        );
      }
      const isDown = pct < 0;
      const color = isDown ? cool.olive : reds[600];
      return (
        <div className="flex items-center gap-2">
          <span className="text-xs font-semibold uppercase tracking-widest w-8" style={{ color: colors.textMuted }}>{label}</span>
          <div className="flex items-center gap-1 text-sm font-semibold" style={{ color }}>
            {isDown ? <TrendingDown size={14} /> : <TrendingUp size={14} />}
            <span>{pct >= 0 ? '+' : ''}{pct.toFixed(1)}%</span>
          </div>
          {delta != null && (
            <span className="text-xs" style={{ color: colors.textMuted }}>({formatDelta(delta)})</span>
          )}
        </div>
      );
    };

    return (
      <div
        className="rounded-2xl p-6 shadow-sm mb-10 animate-slide-up"
        style={{
          backgroundColor: colors.surface,
          border: `1px solid ${colors.border}`,
        }}
      >
        {/* Header */}
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-3">
            <h3
              className="section-heading text-lg"
              style={{ color: reds[700] }}
            >
              {summary.calculatorName || calculatorSummary?.calculatorName}
            </h3>
            <span
              className="px-2 py-0.5 rounded text-xs font-medium"
              style={{ backgroundColor: theme === 'dark' ? neutral[80] : neutral[10], color: colors.textMuted }}
            >
              Daily
            </span>
          </div>
          <span className="text-sm font-medium" style={{ color: colors.textMuted }}>{monthLabel}</span>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Left: KPIs + trend rows */}
          <div className="lg:col-span-2 space-y-5">
            {/* Key metrics */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              {[
                { label: 'MTD Cost', value: `$${Number(summary.totalCost).toLocaleString()}`, color: colors.text, serif: true },
                { label: 'Runs', value: Number(summary.totalRuns).toLocaleString(), color: colors.text, serif: false },
                { label: 'Avg / Run', value: `$${Number(summary.avgCostPerRun).toFixed(2)}`, color: colors.text, serif: true },
                ...(successRate !== null ? [{ label: 'Success Rate', value: `${successRate.toFixed(1)}%`, color: cool.olive, serif: false }] : []),
              ].map(({ label, value, color, serif }) => (
                <div key={label}>
                  <p className="text-xs uppercase tracking-widest mb-1 font-semibold" style={{ color: colors.textDisabled }}>{label}</p>
                  <p
                    className="tabular-nums"
                    style={{
                      color,
                      fontFamily: serif ? "'DM Serif Display', serif" : "'Jost', sans-serif",
                      fontSize: serif ? '1.75rem' : '1.5rem',
                      lineHeight: 1.15,
                      letterSpacing: '-0.01em',
                    }}
                  >
                    {value}
                  </p>
                </div>
              ))}
            </div>

            {/* MoM / YoY */}
            <div className="flex flex-col gap-2 pt-1">
              <TrendRow pct={momPct} delta={momDelta} label="MoM" />
              <TrendRow pct={yoyPct} delta={yoyDelta} label="YoY" />
            </div>
          </div>

          {/* Right: Cost breakdown bars */}
          {calculatorSummary && (
            <div className="space-y-3">
              <p className="text-xs uppercase tracking-widest mb-2" style={{ color: colors.textMuted }}>Period Breakdown</p>
              {[
                { label: 'DBU', cost: dbuCost, color: cool.lagoon },
                { label: 'VM', cost: vmCost, color: cool.pine },
                { label: 'Storage', cost: storageCost, color: warm.amber },
              ].map(({ label, cost, color }) => {
                const pct = Math.round(cost / periodTotal * 100);
                return (
                  <div key={label}>
                    <div className="flex justify-between text-xs mb-1">
                      <span style={{ color: colors.textSecondary }}>{label}</span>
                      <span className="font-medium" style={{ color: colors.text }}>
                        ${cost.toLocaleString()} ({pct}%)
                      </span>
                    </div>
                    <div className="h-1.5 rounded-full" style={{ backgroundColor: theme === 'dark' ? neutral[80] : neutral[20] }}>
                      <div
                        className="h-full rounded-full"
                        style={{ width: `${pct}%`, backgroundColor: color }}
                      />
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    );
  };

  // Total Cost trend: real MoM% when a calc is selected, period-over-period from daily data when viewing all
  const totalCostTrend = selectedCalculator !== 'all' && calculatorMTDSummary
    ? (calculatorMTDSummary.momPct != null ? Number(calculatorMTDSummary.momPct) : undefined)
    : stats.costTrend;

  return (
    <div
      className={`min-h-screen ${theme === 'dark' ? 'bg-dot-grid-dark' : 'bg-dot-grid-light'}`}
      style={{ backgroundColor: colors.background }}
    >
      {/* Top Navigation */}
      <div
        className="border-b sticky top-0 z-10"
        style={{
          backgroundColor: colors.surface,
          borderColor: colors.border,
          boxShadow: '0 1px 0 0 rgba(0,0,0,0.04)'
        }}
      >
        {/* Red accent stripe */}
        <div style={{ height: '3px', backgroundColor: reds[600], width: '100%' }} />
        <div className="max-w-screen-2xl mx-auto px-8 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div
              className="w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0"
              style={{ border: `2px solid ${reds[600]}` }}
            >
              <span style={{ color: reds[600], fontFamily: "'DM Serif Display', serif", fontSize: '18px', lineHeight: 1 }}>C</span>
            </div>
            <div>
              <h1
                className="text-xl font-semibold tracking-tight"
                style={{ color: colors.text, fontFamily: "'Jost', sans-serif", letterSpacing: '-0.02em' }}
              >
                Calculator Cost Analytics
              </h1>
              <p className="text-xs font-medium tracking-widest uppercase" style={{ color: colors.textDisabled }}>
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
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = theme === 'dark' ? neutral[70] : neutral[20]}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = theme === 'dark' ? neutral[80] : neutral[10]}
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
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = reds[700]}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = reds[600]}
            >
              <Download size={16} />
              Export CSV
            </button>
          </div>
        </div>
      </div>

      <div className="max-w-screen-2xl mx-auto px-8 py-10">
        <div className="mb-10 animate-fade-in" style={{ animationDelay: '50ms' }}>
          <h2
            style={{
              fontFamily: "'DM Serif Display', serif",
              fontSize: '2.5rem',
              lineHeight: 1.1,
              letterSpacing: '-0.02em',
              color: colors.text,
            }}
          >
            Dashboard
          </h2>
          <p className="mt-2 text-sm font-medium tracking-wide" style={{ color: colors.textDisabled }}>
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
            trend={totalCostTrend}
            accentColor={reds[600]}
            delay={0}
            costMetric
          />
          <StatCard
            title="Total Runs"
            value={stats.totalRuns.toLocaleString()}
            subtitle={selectedCalculator === 'all' ? 'Completed executions' : 'Selected calculator'}
            icon={Activity}
            accentColor={cool.lagoon}
            delay={75}
          />
          <StatCard
            title="Avg Cost / Run"
            value={`$${stats.avgCostPerRun.toFixed(2)}`}
            subtitle={selectedCalculator === 'all' ? 'Per execution' : 'Selected calculator'}
            icon={Zap}
            accentColor={warm.amber}
            delay={150}
          />
          <StatCard
            title="Avg Efficiency"
            value={`${stats.avgEfficiency.toFixed(1)}%`}
            subtitle={selectedCalculator === 'all' ? 'Overall optimization' : 'Selected calculator'}
            icon={Check}
            accentColor={cool.olive}
            delay={225}
          />
        </div>

        {/* Calculator Detail Card — visible only when a specific calculator is selected */}
        {selectedCalculator !== 'all' && (
          trendLoading ? (
            <div
              className="rounded-2xl p-6 shadow-sm mb-10 animate-pulse"
              style={{
                backgroundColor: colors.surface,
                border: `1px solid ${colors.border}`,
                borderLeft: `4px solid ${reds[600]}`,
              }}
            >
              <div className="h-5 w-64 rounded mb-5" style={{ backgroundColor: neutral[20] }} />
              <div className="grid grid-cols-4 gap-4 mb-4">
                {Array(4).fill(0).map((_, i) => (
                  <div key={i} className="h-10 rounded" style={{ backgroundColor: neutral[20] }} />
                ))}
              </div>
              <div className="h-4 w-48 rounded" style={{ backgroundColor: neutral[20] }} />
            </div>
          ) : calculatorMTDSummary ? (
            <CalculatorDetailCard
              summary={calculatorMTDSummary}
              calculatorSummary={selectedCalculatorSummary}
              theme={theme}
              colors={colors}
            />
          ) : null
        )}

        {/* Charts Row 1 — Daily Cost Trend */}
        <div className="mb-10">
          <div
            className="rounded-3xl shadow-sm p-7 animate-slide-up"
            style={{
              backgroundColor: colors.surface,
              border: `1px solid ${colors.border}`,
              animationDelay: '300ms',
            }}
          >
            <h2 className="section-heading text-xl mb-6" style={{ color: colors.text }}>
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

        {/* 12-Month Cost History — visible only when a specific calculator is selected */}
        {selectedCalculator !== 'all' && monthlyCostTrend && monthlyCostTrend.length > 0 && (
          <div className="mb-10">
            <div
              className="rounded-3xl shadow-sm p-7"
              style={{
                backgroundColor: colors.surface,
                border: `1px solid ${colors.border}`
              }}
            >
              <h2 className="section-heading text-xl mb-6" style={{ color: colors.text }}>
                12-Month Cost History{' '}
                <span style={{ color: colors.textDisabled, fontWeight: 400 }}>— Month-over-Month</span>
              </h2>
              <ResponsiveContainer width="100%" height={320}>
                <BarChart
                  data={monthlyCostTrend.slice(-12)}
                  margin={{ top: 20, right: 30, left: 0, bottom: 20 }}
                >
                  <CartesianGrid strokeDasharray="3 3" stroke={neutral[30]} />
                  <XAxis
                    dataKey="month"
                    stroke={neutral[70]}
                    style={{ fontSize: '12px' }}
                    tickFormatter={(m) => {
                      const [y, mo] = m.split('-');
                      return new Date(Number(y), Number(mo) - 1)
                        .toLocaleDateString('en-US', { month: 'short', year: '2-digit' });
                    }}
                  />
                  <YAxis
                    stroke={neutral[70]}
                    style={{ fontSize: '12px' }}
                    tickFormatter={(v) => `$${Number(v).toLocaleString()}`}
                  />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: colors.surface,
                      border: `1px solid ${colors.border}`,
                      borderRadius: '12px',
                    }}
                    formatter={(value) => [`$${Number(value).toLocaleString()}`, 'Total Cost']}
                    labelFormatter={(m) => {
                      const entry = monthlyCostTrend.find(e => e.month === m);
                      const [y, mo] = m.split('-');
                      const label = new Date(Number(y), Number(mo) - 1)
                        .toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
                      const mom = entry?.momPct != null
                        ? ` • MoM: ${Number(entry.momPct) >= 0 ? '+' : ''}${Number(entry.momPct).toFixed(1)}%`
                        : '';
                      const yoy = entry?.yoyPct != null
                        ? ` • YoY: ${Number(entry.yoyPct) >= 0 ? '+' : ''}${Number(entry.yoyPct).toFixed(1)}%`
                        : '';
                      return `${label}${mom}${yoy}`;
                    }}
                  />
                  <Bar dataKey="totalCost" name="Total Cost" radius={[4, 4, 0, 0]}>
                    {monthlyCostTrend.slice(-12).map((entry, i) => (
                      <Cell
                        key={`cell-${i}`}
                        fill={
                          entry.momPct == null ? neutral[40] :          // no prior data → neutral
                          Number(entry.momPct) < 0 ? cool.pine :        // state.success (cost down)
                          Number(entry.momPct) > 10 ? reds[700] :       // state.danger  (>10% spike)
                          warm.amber                                      // state.warning (moderate rise)
                        }
                      />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
              <div className="flex flex-wrap gap-4 mt-4 justify-center">
                {[
                  { label: 'Cost decreased', color: cool.pine },
                  { label: 'Moderate increase (<10%)', color: warm.amber },
                  { label: 'Significant increase (>10%)', color: reds[700] },
                  { label: 'No prior month data', color: neutral[40] },
                ].map(({ label, color }) => (
                  <div key={label} className="flex items-center gap-1.5 text-xs" style={{ color: colors.textMuted }}>
                    <div className="w-3 h-3 rounded-sm" style={{ backgroundColor: color }} />
                    {label}
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* Charts Row 2 — Cost by Calculator + Cost Breakdown */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-10">
          {/* Cost by Calculator Bar Chart */}
          <div
            className="rounded-3xl shadow-sm p-7"
            style={{
              backgroundColor: colors.surface,
              border: `1px solid ${colors.border}`
            }}
          >
            <h2 className="section-heading text-xl mb-6" style={{ color: colors.text }}>
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

          {/* Cost Breakdown Pie */}
          <div
            className="rounded-3xl shadow-sm p-7"
            style={{
              backgroundColor: colors.surface,
              border: `1px solid ${colors.border}`
            }}
          >
            <h2 className="section-heading text-xl mb-6" style={{ color: colors.text }}>
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
            <h2 className="section-heading text-xl" style={{ color: colors.text }}>
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
                    key={run.runId ?? index}
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
                        className="inline-block px-3 py-0.5 text-xs font-semibold rounded-full tracking-wide"
                        style={run.status === 'SUCCESS' ? {
                          // state.success = cool.pine
                          backgroundColor: theme === 'dark' ? 'rgba(0,137,141,0.2)' : 'rgba(0,137,141,0.1)',
                          color: theme === 'dark' ? cool.mint : cool.pine,
                        } : {
                          // state.danger = reds[700]
                          backgroundColor: theme === 'dark' ? 'rgba(189,0,12,0.2)' : reds[100],
                          color: theme === 'dark' ? reds[300] : reds[700],
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
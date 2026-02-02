import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { 
  LineChart, Line, BarChart, Bar, PieChart, Pie, Cell, 
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, 
  Legend, ResponsiveContainer 
} from 'recharts';
import { 
  TrendingUp, TrendingDown, DollarSign, Activity, AlertCircle, Check, 
  Zap, Database, Cloud, HardDrive 
} from 'lucide-react';

const CalculatorCostDashboard = () => {
  const [data, setData] = useState(null);
  const [selectedPeriod, setSelectedPeriod] = useState('30d');
  const [selectedCalculator, setSelectedCalculator] = useState('all');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

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
          { name: 'DBU Compute', value: result.dailyCosts.reduce((s, d) => s + d.dbuCost, 0), color: '#3b82f6' },
          { name: 'VM Infrastructure', value: result.dailyCosts.reduce((s, d) => s + d.vmCost, 0), color: '#8b5cf6' },
          { name: 'Storage', value: result.dailyCosts.reduce((s, d) => s + d.storageCost, 0), color: '#10b981' },
          { name: 'Network', value: result.dailyCosts.reduce((s, d) => s + d.networkCost, 0), color: '#f59e0b' }
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
        setError('Failed to fetch data from server');
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

  const stats = useMemo(() => {
    if (!data) return null;

    let totalCost, totalRuns, avgEfficiency, avgCostPerRun;
    
    // --- NEW LOGIC START ---
    if (selectedCalculator === 'all') {
      // Aggregate for 'all' calculators
      totalRuns = data.calculatorCosts.reduce((sum, c) => sum + (c.totalRuns || 0), 0);
      totalCost = data.calculatorCosts.reduce((sum, c) => sum + (c.totalCost || 0), 0);
      avgEfficiency = data.calculatorCosts.length > 0
        ? data.calculatorCosts.reduce((sum, c) => sum + c.efficiencyScore, 0) / data.calculatorCosts.length
        : 0;
    } else {
      // Find the specific calculator object
      const specificCalc = data.calculatorCosts.find(c => c.calculatorId === selectedCalculator);
      if (specificCalc) {
        totalCost = specificCalc.totalCost || 0;
        totalRuns = specificCalc.totalRuns || 0;
        avgEfficiency = specificCalc.efficiencyScore || 0;
      } else {
        // Fallback if the specific calculator data is missing
        totalCost = 0;
        totalRuns = 0;
        avgEfficiency = 0;
      }
    }
    // Calculate Avg Cost Per Run using the filtered/aggregated totals
    avgCostPerRun = totalRuns > 0 ? totalCost / totalRuns : 0;
    // --- NEW LOGIC END ---

    // Trend calculation remains the same, always based on dailyCosts array
    let costTrend = 0;
    if (data.dailyCosts.length >= 14) {
      const currentWeek = data.dailyCosts.slice(-7).reduce((s, d) => s + d.totalCost, 0);
      const prevWeek = data.dailyCosts.slice(-14, -7).reduce((s, d) => s + d.totalCost, 0);
      costTrend = prevWeek > 0 ? ((currentWeek - prevWeek) / prevWeek) * 100 : 0;
    }

    return { totalCost, totalRuns, avgCostPerRun, costTrend, avgEfficiency };
  }, [data, selectedCalculator]); // Added selectedCalculator to the dependency array

  if (loading && !data) {
    return (
      <div className="min-h-screen flex items-center justify-center text-gray-600 text-xl">
        Loading dashboard...
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center text-red-600 text-lg">
        {error}
      </div>
    );
  }

  if (!data || !stats) {
    return null;
  }

  const StatCard = ({ title, value, subtitle, icon: Icon, trend, color }) => (
    <div className="bg-white rounded-xl shadow-lg p-6 border-l-4" style={{ borderColor: color }}>
      <div className="flex items-start justify-between">
        <div className="flex-1">
          <p className="text-sm font-medium text-gray-600 uppercase tracking-wide">{title}</p>
          <p className="text-3xl font-bold mt-2" style={{ color }}>{value}</p>
          {subtitle && <p className="text-sm text-gray-500 mt-1">{subtitle}</p>}
          {trend !== undefined && (
            <div className={`flex items-center mt-2 ${trend >= 0 ? 'text-green-600' : 'text-red-600'}`}>
              {trend >= 0 ? <TrendingUp size={16} /> : <TrendingDown size={16} />}
              <span className="text-sm font-semibold ml-1">
                {Math.abs(trend).toFixed(1)}% vs last week
              </span>
            </div>
          )}
        </div>
        <div className="ml-4 p-3 rounded-lg" style={{ backgroundColor: `${color}20` }}>
          <Icon size={24} style={{ color }} />
        </div>
      </div>
    </div>
  );

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 p-8">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-4xl font-bold text-gray-800 mb-2">Calculator Cost Analytics</h1>
        <p className="text-gray-600">Real-time cost insights and optimization opportunities</p>
      </div>

      {/* Filters (Added aria-labels for A11y) */}
      <div className="flex gap-4 mb-8">
        <select
          aria-label="Select Period"
          value={selectedPeriod}
          onChange={(e) => setSelectedPeriod(e.target.value)}
          className="px-4 py-2 bg-white border border-gray-300 rounded-lg shadow-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          <option value="7d">Last 7 days</option>
          <option value="30d">Last 30 days</option>
          <option value="90d">Last 90 days</option>
        </select>

        <select
          aria-label="Select Calculator"
          value={selectedCalculator}
          onChange={(e) => setSelectedCalculator(e.target.value)}
          className="px-4 py-2 bg-white border border-gray-300 rounded-lg shadow-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          <option value="all">All Calculators</option>
          {data.calculatorCosts.map(c => (
            <option key={c.calculatorId} value={c.calculatorId}>{c.calculatorName}</option>
          ))}
        </select>
      </div>

      {/* Key Metrics */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
        <StatCard
          title="Total Cost"
          value={`$${stats.totalCost.toLocaleString(undefined, { maximumFractionDigits: 0 })}`}
          subtitle={selectedCalculator === 'all' ? "Last 30 days" : "Selected Calculator"}
          icon={DollarSign}
          trend={stats.costTrend}
          color="#3b82f6"
        />
        <StatCard
          title="Total Runs"
          value={stats.totalRuns.toLocaleString()}
          subtitle={selectedCalculator === 'all' ? "Successfully completed" : "Selected Calculator"}
          icon={Activity}
          color="#8b5cf6"
        />
        <StatCard
          title="Avg Cost/Run"
          value={`$${stats.avgCostPerRun.toFixed(2)}`}
          subtitle={selectedCalculator === 'all' ? "Per calculator execution" : "Selected Calculator"}
          icon={Zap}
          trend={-3.2} 
          color="#10b981"
        />
        <StatCard
          title="Avg Efficiency Score"
          value={`${stats.avgEfficiency.toFixed(1)}%`}
          subtitle={selectedCalculator === 'all' ? "Cluster utilization" : "Selected Calculator"}
          icon={Check}
          trend={5.7} 
          color="#f59e0b"
        />
      </div>
      
      {/* Charts Row 1 */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
        {/* Daily Cost Trend (Area Chart) */}
        <div className="lg:col-span-2 bg-white rounded-xl shadow-lg p-6">
          <h2 className="text-xl font-bold text-gray-800 mb-4">Cost Trend Over Time</h2>
          <ResponsiveContainer width="100%" height={300}>
            <AreaChart data={data.dailyCosts}>
              <defs>
                <linearGradient id="colorTotal" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3}/>
                  <stop offset="95%" stopColor="#3b82f6" stopOpacity={0}/>
                </linearGradient>
                <linearGradient id="colorDBU" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#8b5cf6" stopOpacity={0.3}/>
                  <stop offset="95%" stopColor="#8b5cf6" stopOpacity={0}/>
                </linearGradient>
                <linearGradient id="colorVM" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#10b981" stopOpacity={0.3}/>
                  <stop offset="95%" stopColor="#10b981" stopOpacity={0}/>
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
              <XAxis dataKey="date" stroke="#6b7280" style={{ fontSize: '12px' }} tickFormatter={(str) => new Date(str).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })} />
              <YAxis stroke="#6b7280" style={{ fontSize: '12px' }} />
              <Tooltip
                contentStyle={{ backgroundColor: '#fff', border: '1px solid #e5e7eb', borderRadius: '8px' }}
                formatter={(value) => `$${value.toFixed(2)}`}
              />
              <Legend />
              <Area type="monotone" dataKey="totalCost" stroke="#3b82f6" fillOpacity={1} fill="url(#colorTotal)" name="Total Cost" />
              <Area type="monotone" dataKey="dbuCost" stroke="#8b5cf6" fillOpacity={1} fill="url(#colorDBU)" name="DBU Cost" />
              <Area type="monotone" dataKey="vmCost" stroke="#10b981" fillOpacity={1} fill="url(#colorVM)" name="VM Cost" />
            </AreaChart>
          </ResponsiveContainer>
        </div>

        {/* Cost Breakdown Pie Chart */}
        <div className="bg-white rounded-xl shadow-lg p-6">
          <h2 className="text-xl font-bold text-gray-800 mb-4">Cost Breakdown</h2>
          <ResponsiveContainer width="100%" height={300}>
            <PieChart>
              <Pie
                data={data.costBreakdown}
                cx="50%"
                cy="50%"
                labelLine={false}
                label={({ name = 'Unknown', percent = 0 }) => `${name}: ${(percent * 100).toFixed(0)}%`}
                outerRadius={80}
                fill="#8884d8"
                dataKey="value"
              >
                {data.costBreakdown.map((entry, index) => (
                  <Cell key={`cell-${index}`} fill={entry.color || '#888'} />
                ))}
              </Pie>
              <Tooltip formatter={(value) => `$${value.toLocaleString()}`} />
            </PieChart>
          </ResponsiveContainer>
          <div className="mt-4 space-y-2">
            {data.costBreakdown.map((item, i) => (
              <div key={i} className="flex items-center justify-between text-sm">
                <div className="flex items-center">
                  <div className="w-3 h-3 rounded-full mr-2" style={{ backgroundColor: item.color }}></div>
                  <span className="text-gray-700">{item.name}</span>
                </div>
                {/* Added || 0 fallback to prevent toFixed error */}
                <span className="font-semibold text-gray-800">${(item.value || 0).toLocaleString(undefined, {maximumFractionDigits: 0})}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Charts Row 2 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
        {/* Calculator Comparison (Bar Chart) */}
        <div className="bg-white rounded-xl shadow-lg p-6">
          <h2 className="text-xl font-bold text-gray-800 mb-4">Cost by Calculator</h2>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={data.calculatorCosts}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
              <XAxis dataKey="calculatorName" stroke="#6b7280" style={{ fontSize: '11px' }} angle={-45} textAnchor="end" height={80} />
              <YAxis stroke="#6b7280" style={{ fontSize: '12px' }} />
              <Tooltip
                contentStyle={{ backgroundColor: '#fff', border: '1px solid #e5e7eb', borderRadius: '8px' }}
                formatter={(value) => `$${value.toFixed(2)}`}
              />
              <Legend />
              <Bar dataKey="dbuCost" stackId="a" fill="#3b82f6" name="DBU Cost" />
              <Bar dataKey="vmCost" stackId="a" fill="#8b5cf6" name="VM Cost" />
              <Bar dataKey="storageCost" stackId="a" fill="#10b981" name="Storage Cost" />
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Efficiency Metrics (Progress Bars) */}
        <div className="bg-white rounded-xl shadow-lg p-6">
          <h2 className="text-xl font-bold text-gray-800 mb-4">Optimization Opportunities</h2>
          <div className="space-y-6">
            <div>
              <div className="flex justify-between mb-2">
                <span className="text-sm font-medium text-gray-700 flex items-center">
                  <Cloud size={16} className="mr-2 text-blue-500" />
                  Spot Instance Usage
                </span>
                <span className="text-sm font-bold text-gray-800">{data.efficiency.spotInstanceUsage}%</span>
              </div>
              <div className="w-full bg-gray-200 rounded-full h-2">
                <div
                  className="bg-gradient-to-r from-blue-500 to-blue-600 h-2 rounded-full transition-all duration-500"
                  style={{ width: `${data.efficiency.spotInstanceUsage}%` }}
                ></div>
              </div>
              <p className="text-xs text-gray-500 mt-1">Target: 80% for max savings</p>
            </div>
            <div>
              <div className="flex justify-between mb-2">
                <span className="text-sm font-medium text-gray-700 flex items-center">
                  <Zap size={16} className="mr-2 text-purple-500" />
                  Photon Adoption
                </span>
                <span className="text-sm font-bold text-gray-800">{data.efficiency.photonAdoption}%</span>
              </div>
              <div className="w-full bg-gray-200 rounded-full h-2">
                <div
                  className="bg-gradient-to-r from-purple-500 to-purple-600 h-2 rounded-full transition-all duration-500"
                  style={{ width: `${data.efficiency.photonAdoption}%` }}
                ></div>
              </div>
              <p className="text-xs text-gray-500 mt-1">Photon can reduce costs by 20-30%</p>
            </div>
            <div>
              <div className="flex justify-between mb-2">
                <span className="text-sm font-medium text-gray-700 flex items-center">
                  <Database size={16} className="mr-2 text-green-500" />
                  Cluster Utilization
                </span>
                <span className="text-sm font-bold text-gray-800">{data.efficiency.avgClusterUtilization}%</span>
              </div>
              <div className="w-full bg-gray-200 rounded-full h-2">
                <div
                  className="bg-gradient-to-r from-green-500 to-green-600 h-2 rounded-full transition-all duration-500"
                  style={{ width: `${data.efficiency.avgClusterUtilization}%` }}
                ></div>
              </div>
              <p className="text-xs text-gray-500 mt-1">Good utilization, minimal waste</p>
            </div>
            <div>
              <div className="flex justify-between mb-2">
                <span className="text-sm font-medium text-gray-700 flex items-center">
                  <AlertCircle size={16} className="mr-2 text-orange-500" />
                  Retry Rate
                </span>
                <span className="text-sm font-bold text-gray-800">{data.efficiency.retryRate}%</span>
              </div>
              <div className="w-full bg-gray-200 rounded-full h-2">
                <div
                  className="bg-gradient-to-r from-orange-400 to-orange-500 h-2 rounded-full transition-all duration-500"
                  style={{ width: `${data.efficiency.retryRate * 5}%` }}
                ></div>
              </div>
              <p className="text-xs text-gray-500 mt-1">Wasted cost: ~${(stats.totalCost * data.efficiency.retryRate / 100).toFixed(0)}</p>
            </div>
          </div>
        </div>
      </div>

      {/* Recent Runs Table */}
      <div className="bg-white rounded-xl shadow-lg p-6">
        <div className="flex justify-between items-center mb-4">
          <h2 className="text-xl font-bold text-gray-800">Recent Calculator Runs</h2>
          <button className="px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition-colors text-sm font-medium">
            Export CSV
          </button>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b-2 border-gray-200">
                <th className="text-left py-3 px-4 text-sm font-semibold text-gray-700">Run ID</th>
                <th className="text-left py-3 px-4 text-sm font-semibold text-gray-700">Calculator</th>
                <th className="text-left py-3 px-4 text-sm font-semibold text-gray-700">Start Time</th>
                <th className="text-left py-3 px-4 text-sm font-semibold text-gray-700">Duration</th>
                <th className="text-left py-3 px-4 text-sm font-semibold text-gray-700">Workers</th>
                <th className="text-left py-3 px-4 text-sm font-semibold text-gray-700">DBU Cost</th>
                <th className="text-left py-3 px-4 text-sm font-semibold text-gray-700">VM Cost</th>
                <th className="text-left py-3 px-4 text-sm font-semibold text-gray-700">Total Cost</th>
                <th className="text-left py-3 px-4 text-sm font-semibold text-gray-700">Status</th>
              </tr>
            </thead>
            <tbody>
              {data.recentRuns.slice(0, 10).map((run, i) => (
                <tr key={run.id} className={`border-b border-gray-100 hover:bg-gray-50 transition-colors ${i % 2 === 0 ? 'bg-white' : 'bg-gray-50'}`}>
                  <td className="py-3 px-4 text-sm font-mono text-gray-600">{run.runId}</td>
                  <td className="py-3 px-4 text-sm font-medium text-gray-800">{run.calculatorName}</td>
                  <td className="py-3 px-4 text-sm text-gray-600">
                    {new Date(run.startTime).toLocaleString('en-US', {
                      month: 'short',
                      day: 'numeric',
                      hour: '2-digit',
                      minute: '2-digit'
                    })}
                  </td>
                  <td className="py-3 px-4 text-sm text-gray-600">{Math.floor(run.durationSeconds / 60)}m {run.durationSeconds % 60}s</td>
                  <td className="py-3 px-4 text-sm text-gray-600">{run.workerCount}</td>
                  <td className="py-3 px-4 text-sm font-medium text-blue-600">${(run.dbuCost || 0).toFixed(2)}</td>
                  <td className="py-3 px-4 text-sm font-medium text-purple-600">${(run.vmCost || 0).toFixed(2)}</td>
                  {/* Use 'cost' from the mock data schema */}
                  <td className="py-3 px-4 text-sm font-bold text-gray-800">${(run.totalCost || 0).toFixed(2)}</td>
                  <td className="py-3 px-4">
                    <span className={`px-2 py-1 rounded-full text-xs font-semibold ${
                      run.status === 'SUCCESS'
                      ? 'bg-green-100 text-green-800'
                      : 'bg-red-100 text-red-800'
                    }`}>
                      {run.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Footer */}
      <div className="mt-8 text-center text-sm text-gray-500">
        <p>Last updated: {new Date().toLocaleString()}</p>
        <p className="mt-1">Data refreshed daily at 6:00 AM UTC</p>
      </div>
    </div>
  );
};

export default CalculatorCostDashboard;

import React, { useState, useEffect } from 'react';
import { LineChart, Line, BarChart, Bar, PieChart, Pie, Cell, AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { TrendingUp, TrendingDown, DollarSign, Activity, AlertCircle, Check, Zap, Database, Cloud, HardDrive } from 'lucide-react';

// Sample data - In production, fetch from Spring Boot API
const generateSampleData = () => {
  const calculators = ['DataQualityCheck', 'RiskScoring', 'RevenueAggregation', 'CustomerSegmentation', 'FraudDetection'];
  const dates = Array.from({ length: 30 }, (_, i) => {
    const date = new Date();
    date.setDate(date.getDate() - (29 - i));
    return date.toISOString().split('T')[0];
  });

  return {
    dailyCosts: dates.map((date, i) => ({
      date: date,
      displayDate: new Date(date).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
      total: 450 + Math.random() * 200,
      dbu: 180 + Math.random() * 80,
      vm: 200 + Math.random() * 90,
      storage: 30 + Math.random() * 20,
      network: 20 + Math.random() * 10
    })),
    
    calculatorCosts: calculators.map(name => ({
      name,
      totalCost: 2000 + Math.random() * 8000,
      runs: Math.floor(500 + Math.random() * 1500),
      avgCost: 3 + Math.random() * 12,
      efficiency: 75 + Math.random() * 20,
      dbuCost: 800 + Math.random() * 3000,
      vmCost: 900 + Math.random() * 3500,
      storageCost: 100 + Math.random() * 400
    })),

    recentRuns: Array.from({ length: 20 }, (_, i) => ({
      id: `run_${1000 + i}`,
      calculator: calculators[Math.floor(Math.random() * calculators.length)],
      startTime: new Date(Date.now() - Math.random() * 7 * 24 * 60 * 60 * 1000).toISOString(),
      duration: Math.floor(120 + Math.random() * 1800),
      cost: 5 + Math.random() * 45,
      status: Math.random() > 0.1 ? 'SUCCESS' : 'FAILED',
      dbuCost: 2 + Math.random() * 20,
      vmCost: 2 + Math.random() * 20,
      workers: Math.floor(2 + Math.random() * 6)
    })),

    costBreakdown: [
      { name: 'DBU Compute', value: 12450, color: '#3b82f6' },
      { name: 'VM Infrastructure', value: 14230, color: '#8b5cf6' },
      { name: 'Storage', value: 2340, color: '#10b981' },
      { name: 'Network', value: 1890, color: '#f59e0b' }
    ],

    efficiency: {
      spotInstanceUsage: 68,
      photonAdoption: 45,
      avgClusterUtilization: 73,
      retryRate: 5.2,
      failureRate: 2.8
    }
  };
};

const CalculatorCostDashboard = () => {
  const [data, setData] = useState(generateSampleData());
  const [selectedPeriod, setSelectedPeriod] = useState('30d');
  const [selectedCalculator, setSelectedCalculator] = useState('all');
  const [loading, setLoading] = useState(false);

  // In production, fetch from API
  useEffect(() => {
    // fetch(`/api/v1/cost-analytics?period=${selectedPeriod}&calculator=${selectedCalculator}`)
    //   .then(res => res.json())
    //   .then(data => setData(data));
  }, [selectedPeriod, selectedCalculator]);

  const totalCost = data.calculatorCosts.reduce((sum, c) => sum + c.totalCost, 0);
  const totalRuns = data.calculatorCosts.reduce((sum, c) => sum + c.runs, 0);
  const avgCostPerRun = totalCost / totalRuns;
  const costTrend = data.dailyCosts.length > 7 
    ? ((data.dailyCosts.slice(-7).reduce((s, d) => s + d.total, 0) / 7) - 
       (data.dailyCosts.slice(-14, -7).reduce((s, d) => s + d.total, 0) / 7)) / 
      (data.dailyCosts.slice(-14, -7).reduce((s, d) => s + d.total, 0) / 7) * 100
    : 0;

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
        <div className="ml-4 p-3 rounded-lg" style={{ backgroundColor: color + '20' }}>
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

      {/* Filters */}
      <div className="flex gap-4 mb-8">
        <select 
          value={selectedPeriod}
          onChange={(e) => setSelectedPeriod(e.target.value)}
          className="px-4 py-2 bg-white border border-gray-300 rounded-lg shadow-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          <option value="7d">Last 7 days</option>
          <option value="30d">Last 30 days</option>
          <option value="90d">Last 90 days</option>
        </select>
        
        <select
          value={selectedCalculator}
          onChange={(e) => setSelectedCalculator(e.target.value)}
          className="px-4 py-2 bg-white border border-gray-300 rounded-lg shadow-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          <option value="all">All Calculators</option>
          {data.calculatorCosts.map(c => (
            <option key={c.name} value={c.name}>{c.name}</option>
          ))}
        </select>
      </div>

      {/* Key Metrics */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
        <StatCard
          title="Total Cost"
          value={`$${totalCost.toLocaleString(undefined, { maximumFractionDigits: 0 })}`}
          subtitle="Last 30 days"
          icon={DollarSign}
          trend={costTrend}
          color="#3b82f6"
        />
        <StatCard
          title="Total Runs"
          value={totalRuns.toLocaleString()}
          subtitle="Successfully completed"
          icon={Activity}
          color="#8b5cf6"
        />
        <StatCard
          title="Avg Cost/Run"
          value={`$${avgCostPerRun.toFixed(2)}`}
          subtitle="Per calculator execution"
          icon={Zap}
          trend={-3.2}
          color="#10b981"
        />
        <StatCard
          title="Efficiency Score"
          value={`${data.efficiency.avgClusterUtilization}%`}
          subtitle="Cluster utilization"
          icon={Check}
          trend={5.7}
          color="#f59e0b"
        />
      </div>

      {/* Charts Row 1 */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
        {/* Daily Cost Trend */}
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
              <XAxis dataKey="displayDate" stroke="#6b7280" style={{ fontSize: '12px' }} />
              <YAxis stroke="#6b7280" style={{ fontSize: '12px' }} />
              <Tooltip 
                contentStyle={{ backgroundColor: '#fff', border: '1px solid #e5e7eb', borderRadius: '8px' }}
                formatter={(value) => `$${value.toFixed(2)}`}
              />
              <Legend />
              <Area type="monotone" dataKey="total" stroke="#3b82f6" fillOpacity={1} fill="url(#colorTotal)" name="Total Cost" />
              <Area type="monotone" dataKey="dbu" stroke="#8b5cf6" fillOpacity={1} fill="url(#colorDBU)" name="DBU Cost" />
              <Area type="monotone" dataKey="vm" stroke="#10b981" fillOpacity={1} fill="url(#colorVM)" name="VM Cost" />
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
                label={({ name, percent }) => `${name}: ${(percent * 100).toFixed(0)}%`}
                outerRadius={80}
                fill="#8884d8"
                dataKey="value"
              >
                {data.costBreakdown.map((entry, index) => (
                  <Cell key={`cell-${index}`} fill={entry.color} />
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
                <span className="font-semibold text-gray-800">${item.value.toLocaleString()}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Charts Row 2 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
        {/* Calculator Comparison */}
        <div className="bg-white rounded-xl shadow-lg p-6">
          <h2 className="text-xl font-bold text-gray-800 mb-4">Cost by Calculator</h2>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={data.calculatorCosts}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
              <XAxis dataKey="name" stroke="#6b7280" style={{ fontSize: '11px' }} angle={-45} textAnchor="end" height={80} />
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

        {/* Efficiency Metrics */}
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
              <p className="text-xs text-gray-500 mt-1">Wasted cost: ~${(totalCost * data.efficiency.retryRate / 100).toFixed(0)}</p>
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
                  <td className="py-3 px-4 text-sm font-mono text-gray-600">{run.id}</td>
                  <td className="py-3 px-4 text-sm font-medium text-gray-800">{run.calculator}</td>
                  <td className="py-3 px-4 text-sm text-gray-600">
                    {new Date(run.startTime).toLocaleString('en-US', { 
                      month: 'short', 
                      day: 'numeric', 
                      hour: '2-digit', 
                      minute: '2-digit' 
                    })}
                  </td>
                  <td className="py-3 px-4 text-sm text-gray-600">{Math.floor(run.duration / 60)}m {run.duration % 60}s</td>
                  <td className="py-3 px-4 text-sm text-gray-600">{run.workers}</td>
                  <td className="py-3 px-4 text-sm font-medium text-blue-600">${run.dbuCost.toFixed(2)}</td>
                  <td className="py-3 px-4 text-sm font-medium text-purple-600">${run.vmCost.toFixed(2)}</td>
                  <td className="py-3 px-4 text-sm font-bold text-gray-800">${run.cost.toFixed(2)}</td>
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

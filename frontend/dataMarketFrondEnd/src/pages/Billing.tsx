import { useState, useEffect } from 'react';
import { DollarSign, TrendingUp, Database, FileText, Download, Calendar } from 'lucide-react';
import api from '../services/api';
import { UsageStats, BillingRecord } from '../types';

export default function Billing({ user }: { user: any }) {
  const [viewMode, setViewMode] = useState<'consumer' | 'owner'>(user?.role || 'consumer');
  const [timeRange, setTimeRange] = useState('30');

  const [stats, setStats] = useState<UsageStats>({
    totalQueries: 0,
    totalRecordsAccessed: 0,
    totalCost: 0,
    totalRevenue: 0,
    monthlyTrend: []
  });
  const [records, setRecords] = useState<BillingRecord[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchBillingData = async () => {
    setLoading(true);
    try {
      const response = await api.get(`/api/billing/summary?userId=${user?.id}&role=${viewMode}&days=${timeRange}`);

      if (response.data) {
        const fetchedRecords: BillingRecord[] = response.data.records || [];

        const cutoffDate = new Date();
        cutoffDate.setDate(cutoffDate.getDate() - parseInt(timeRange));

        const filteredRecords = fetchedRecords.filter(r => {
          const rDate = new Date((r.date || r.createdAt) as string);
          return rDate >= cutoffDate;
        });

        const calcQueries = filteredRecords.reduce((sum, r) => sum + (r.queryCount || 0), 0);
        const calcRecords = filteredRecords.reduce((sum, r) => sum + (r.recordsAccessed || 0), 0);
        const calcCost = filteredRecords.reduce((sum, r) => sum + (r.cost || 0), 0);

        let trend: { month: string; value: number }[] = [];

        const timeMap: Record<string, number> = {};
        const orderedKeys: string[] = [];
        const today = new Date();

        if (timeRange === '7') {
          // Last 7 days: Group by Day of the week
          for (let i = 6; i >= 0; i--) {
            const d = new Date();
            d.setDate(d.getDate() - i);
            const label = d.toLocaleString('en-US', { weekday: 'short' });
            orderedKeys.push(label);
            timeMap[label] = 0;
          }
          filteredRecords.forEach(r => {
            const rDate = new Date((r.date || r.createdAt) as string);
            const label = rDate.toLocaleString('en-US', { weekday: 'short' });
            if (timeMap[label] !== undefined) timeMap[label] += Number(r.cost || 0);
          });
        } else if (timeRange === '30') {
          // Last 30 days: Group by Week
          for (let i = 3; i >= 0; i--) {
            const label = `Week ${4 - i}`;
            orderedKeys.push(label);
            timeMap[label] = 0;
          }
          filteredRecords.forEach(r => {
            const rDate = new Date((r.date || r.createdAt) as string);
            const diffDays = Math.floor((today.getTime() - rDate.getTime()) / (1000 * 60 * 60 * 24));
            let week = 4;
            if (diffDays < 7) week = 4;
            else if (diffDays < 14) week = 3;
            else if (diffDays < 21) week = 2;
            else week = 1;

            const label = `Week ${week}`;
            if (timeMap[label] !== undefined) timeMap[label] += Number(r.cost || 0);
          });
        } else {
          // Last 90 days: Group by Month
          for (let i = 2; i >= 0; i--) {
            const d = new Date();
            d.setMonth(d.getMonth() - i);
            const label = d.toLocaleString('en-US', { month: 'short' });
            orderedKeys.push(label);
            timeMap[label] = 0;
          }
          filteredRecords.forEach(r => {
            const rDate = new Date((r.date || r.createdAt) as string);
            const label = rDate.toLocaleString('en-US', { month: 'short' });
            if (timeMap[label] !== undefined) timeMap[label] += Number(r.cost || 0);
          });
        }

        trend = orderedKeys.map(k => ({ month: k, value: timeMap[k] }));

        setStats({
          totalQueries: calcQueries,
          totalRecordsAccessed: calcRecords,
          totalCost: viewMode === 'consumer' ? calcCost : 0,
          totalRevenue: viewMode === 'owner' ? calcCost : 0,
          monthlyTrend: trend
        });
        setRecords(filteredRecords);
      }
    } catch (error) {
      console.error('Failed to fetch billing data:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchBillingData();
  }, [viewMode, timeRange]);

  const maxTrendValue = Math.max(...stats.monthlyTrend.map(m => m.value), 1);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Billing Dashboard</h1>
          <p className="text-gray-600 mt-1">Track usage, costs, and revenue</p>
        </div>
        {/* Removed redundant view mode toggle since user roles are fixed */}
      </div>

      {viewMode === 'consumer' ? (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            <div className="bg-gradient-to-br from-blue-500 to-blue-600 rounded-xl shadow-lg p-6 text-white">
              <div className="flex items-center justify-between mb-2">
                <p className="text-blue-100 text-sm font-medium">Total Spend</p>
                <DollarSign className="w-5 h-5 text-blue-200" />
              </div>
              <p className="text-3xl font-bold mb-1">${stats.totalCost.toFixed(2)}</p>
              <p className="text-blue-100 text-xs">Last 30 days</p>
            </div>

            <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
              <div className="flex items-center justify-between mb-2">
                <p className="text-gray-600 text-sm font-medium">Total Queries</p>
                <Database className="w-5 h-5 text-gray-400" />
              </div>
              <p className="text-3xl font-bold text-gray-900 mb-1">{stats.totalQueries}</p>
              <p className="text-green-600 text-xs flex items-center gap-1">
                <TrendingUp className="w-3 h-3" />
                +12% from last month
              </p>
            </div>

            <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
              <div className="flex items-center justify-between mb-2">
                <p className="text-gray-600 text-sm font-medium">Records Accessed</p>
                <FileText className="w-5 h-5 text-gray-400" />
              </div>
              <p className="text-3xl font-bold text-gray-900 mb-1">{stats.totalRecordsAccessed.toLocaleString()}</p>
              <p className="text-green-600 text-xs flex items-center gap-1">
                <TrendingUp className="w-3 h-3" />
                +18% from last month
              </p>
            </div>

            <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
              <div className="flex items-center justify-between mb-2">
                <p className="text-gray-600 text-sm font-medium">Avg Cost/Query</p>
                <DollarSign className="w-5 h-5 text-gray-400" />
              </div>
              <p className="text-3xl font-bold text-gray-900 mb-1">${stats.totalQueries > 0 ? (stats.totalCost / stats.totalQueries).toFixed(2) : '0.00'}</p>
              <p className="text-gray-500 text-xs">Per query average</p>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="lg:col-span-2 bg-white rounded-xl shadow-sm border border-gray-200 p-6">
              <div className="flex items-center justify-between mb-6">
                <h3 className="text-lg font-semibold text-gray-900">Spending Trend</h3>
                <select
                  value={timeRange}
                  onChange={(e) => setTimeRange(e.target.value)}
                  className="px-3 py-1 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                >
                  <option value="7">Last 7 days</option>
                  <option value="30">Last 30 days</option>
                  <option value="90">Last 90 days</option>
                </select>
              </div>

              <div className="space-y-4">
                {stats.monthlyTrend.map((data, index) => (
                  <div key={data.month} className="space-y-2">
                    <div className="flex items-center justify-between text-sm">
                      <span className="text-gray-600 font-medium w-12">{data.month}</span>
                      <span className="text-gray-900 font-semibold">${data.value.toFixed(2)}</span>
                    </div>
                    <div className="w-full bg-gray-100 rounded-full h-3 overflow-hidden">
                      <div
                        className="bg-gradient-to-r from-blue-500 to-blue-600 h-full rounded-full transition-all duration-500"
                        style={{ width: `${(data.value / maxTrendValue) * 100}%` }}
                      />
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">Quick Actions</h3>
              <div className="space-y-3">
                <button className="w-full flex items-center justify-center gap-2 px-4 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors">
                  <Download className="w-4 h-4" />
                  Download Invoice
                </button>
                <button className="w-full flex items-center justify-center gap-2 px-4 py-3 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors">
                  <Calendar className="w-4 h-4" />
                  View Payment History
                </button>
              </div>

              <div className="mt-6 p-4 bg-blue-50 rounded-lg border border-blue-100">
                <h4 className="text-sm font-semibold text-blue-900 mb-2">Payment Method</h4>
                <div className="flex items-center gap-3">
                  <div className="w-12 h-8 bg-gradient-to-r from-blue-600 to-purple-600 rounded flex items-center justify-center">
                    <span className="text-white text-xs font-bold">VISA</span>
                  </div>
                  <div>
                    <p className="text-sm font-medium text-gray-900">•••• 4242</p>
                    <p className="text-xs text-gray-600">Expires 12/25</p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold text-gray-900">Recent Transactions</h3>
              <button className="text-sm text-blue-600 hover:text-blue-700 font-medium">View All</button>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-gray-200">
                    <th className="text-left py-3 px-4 text-sm font-semibold text-gray-700">Date</th>
                    <th className="text-left py-3 px-4 text-sm font-semibold text-gray-700">Dataset</th>
                    <th className="text-right py-3 px-4 text-sm font-semibold text-gray-700">Queries</th>
                    <th className="text-right py-3 px-4 text-sm font-semibold text-gray-700">Records</th>
                    <th className="text-right py-3 px-4 text-sm font-semibold text-gray-700">Cost</th>
                  </tr>
                </thead>
                <tbody>
                  {records.length > 0 ? (
                    records.map(record => (
                      <tr key={record.id} className="border-b border-gray-100 hover:bg-gray-50">
                        <td className="py-3 px-4 text-sm text-gray-900">
                          {record.date ? new Date(record.date).toLocaleDateString() : 'N/A'}
                        </td>
                        <td className="py-3 px-4 text-sm text-gray-900">{record.datasetName || 'Unknown'}</td>
                        <td className="py-3 px-4 text-sm text-gray-900 text-right">{record.queryCount || 0}</td>
                        <td className="py-3 px-4 text-sm text-gray-900 text-right">
                          {(record.recordsAccessed || 0).toLocaleString()}
                        </td>
                        <td className="py-3 px-4 text-sm font-semibold text-gray-900 text-right">
                          ${(record.cost || 0).toFixed(2)}
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={5} className="py-8 text-center text-sm text-gray-500">
                        No transactions found
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </>
      ) : (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            <div className="bg-gradient-to-br from-green-500 to-green-600 rounded-xl shadow-lg p-6 text-white">
              <div className="flex items-center justify-between mb-2">
                <p className="text-green-100 text-sm font-medium">Total Revenue</p>
                <DollarSign className="w-5 h-5 text-green-200" />
              </div>
              <p className="text-3xl font-bold mb-1">${stats.totalRevenue.toFixed(2)}</p>
              <p className="text-green-100 text-xs">Last 30 days</p>
            </div>

            <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
              <div className="flex items-center justify-between mb-2">
                <p className="text-gray-600 text-sm font-medium">Active Datasets Managed</p>
                <Database className="w-5 h-5 text-gray-400" />
              </div>
              <p className="text-3xl font-bold text-gray-900 mb-1">{
                new Set(records.map(r => r.datasetId)).size
              }</p>
              <p className="text-green-600 text-xs flex items-center gap-1">
                <TrendingUp className="w-3 h-3" />
                +2 this month
              </p>
            </div>

            <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
              <div className="flex items-center justify-between mb-2">
                <p className="text-gray-600 text-sm font-medium">Records Shared</p>
                <FileText className="w-5 h-5 text-gray-400" />
              </div>
              <p className="text-3xl font-bold text-gray-900 mb-1">{stats.totalRecordsAccessed.toLocaleString()}</p>
              <p className="text-green-600 text-xs flex items-center gap-1">
                <TrendingUp className="w-3 h-3" />
                +24% from last month
              </p>
            </div>

            <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
              <div className="flex items-center justify-between mb-2">
                <p className="text-gray-600 text-sm font-medium">Avg Revenue/Dataset</p>
                <DollarSign className="w-5 h-5 text-gray-400" />
              </div>
              <p className="text-3xl font-bold text-gray-900 mb-1">
                ${new Set(records.map(r => r.datasetId)).size > 0
                  ? (stats.totalRevenue / new Set(records.map(r => r.datasetId)).size).toFixed(2)
                  : '0.00'}
              </p>
              <p className="text-gray-500 text-xs">Per dataset average</p>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="lg:col-span-2 bg-white rounded-xl shadow-sm border border-gray-200 p-6">
              <div className="flex items-center justify-between mb-6">
                <h3 className="text-lg font-semibold text-gray-900">Revenue Trend</h3>
                <select
                  value={timeRange}
                  onChange={(e) => setTimeRange(e.target.value)}
                  className="px-3 py-1 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                >
                  <option value="7">Last 7 days</option>
                  <option value="30">Last 30 days</option>
                  <option value="90">Last 90 days</option>
                </select>
              </div>

              <div className="space-y-4">
                {stats.monthlyTrend.map((data, index) => (
                  <div key={data.month} className="space-y-2">
                    <div className="flex items-center justify-between text-sm">
                      <span className="text-gray-600 font-medium w-12">{data.month}</span>
                      <span className="text-gray-900 font-semibold">${data.value.toFixed(2)}</span>
                    </div>
                    <div className="w-full bg-gray-100 rounded-full h-3 overflow-hidden">
                      <div
                        className="bg-gradient-to-r from-green-500 to-green-600 h-full rounded-full transition-all duration-500"
                        style={{ width: `${maxTrendValue > 0 ? (data.value / maxTrendValue) * 100 : 0}%` }}
                      />
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">Payout Information</h3>
              <div className="space-y-4">
                <div className="p-4 bg-green-50 rounded-lg border border-green-100">
                  <p className="text-xs text-green-700 mb-1">Next Payout</p>
                  <p className="text-2xl font-bold text-green-900 mb-1">${stats.totalRevenue.toFixed(2)}</p>
                  <p className="text-xs text-green-700">Scheduled for {new Date(new Date().getFullYear(), new Date().getMonth() + 1, 0).toLocaleDateString()}</p>
                </div>

                <div className="pt-4 border-t border-gray-200">
                  <h4 className="text-sm font-semibold text-gray-900 mb-3">Bank Account</h4>
                  <div className="flex items-center gap-3">
                    <div className="w-12 h-12 bg-gray-900 rounded flex items-center justify-center">
                      <span className="text-white text-xs font-bold">BANK</span>
                    </div>
                    <div>
                      <p className="text-sm font-medium text-gray-900">•••• 9876</p>
                      <p className="text-xs text-gray-600">Chase Bank</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold text-gray-900">Top Performing Datasets</h3>
            </div>
            <div className="space-y-4">
              {records.reduce((acc: { name: string; datasetId: string; queries: number; revenue: number; records: number }[], current) => {
                const existing = acc.find(item => item.datasetId === current.datasetId);
                if (existing) {
                  existing.queries += current.queryCount || 0;
                  existing.revenue += current.cost || 0;
                  existing.records += current.recordsAccessed || 0;
                } else {
                  acc.push({
                    name: current.datasetName || 'Unknown',
                    datasetId: current.datasetId,
                    queries: current.queryCount || 0,
                    revenue: current.cost || 0,
                    records: current.recordsAccessed || 0,
                  });
                }
                return acc;
              }, []).sort((a, b) => b.revenue - a.revenue).slice(0, 3).map((dataset, index) => (
                <div key={dataset.datasetId} className="border border-gray-200 rounded-lg p-4 hover:shadow-md transition-shadow">
                  <div className="flex items-center justify-between mb-3">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 bg-green-100 rounded-lg flex items-center justify-center">
                        <span className="text-green-700 font-bold text-sm">#{index + 1}</span>
                      </div>
                      <h4 className="font-semibold text-gray-900">{dataset.name}</h4>
                    </div>
                    <span className="text-lg font-bold text-green-600">${dataset.revenue.toFixed(2)}</span>
                  </div>
                  <div className="grid grid-cols-3 gap-4 text-sm">
                    <div>
                      <p className="text-gray-600 text-xs mb-1">Queries</p>
                      <p className="font-semibold text-gray-900">{dataset.queries}</p>
                    </div>
                    <div>
                      <p className="text-gray-600 text-xs mb-1">Records Accessed</p>
                      <p className="font-semibold text-gray-900">{dataset.records.toLocaleString()}</p>
                    </div>
                    <div>
                      <p className="text-gray-600 text-xs mb-1">Avg per Query</p>
                      <p className="font-semibold text-gray-900">${dataset.queries > 0 ? (dataset.revenue / dataset.queries).toFixed(2) : '0.00'}</p>
                    </div>
                  </div>
                </div>
              ))}
              {records.length === 0 && (
                <div className="text-center py-4 text-gray-500 text-sm">No dataset data available</div>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
}

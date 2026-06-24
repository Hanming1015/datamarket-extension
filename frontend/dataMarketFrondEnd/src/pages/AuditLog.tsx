import { useState, useEffect } from 'react';
import api from '../services/api';
import { FileCheck, Shield, Database, Send, CheckCircle, XCircle, Filter, Calendar, User } from 'lucide-react';
import { type AuditLog } from '../types';

export default function AuditLog({ user }: { user: any }) {
  const [selectedAction, setSelectedAction] = useState<string>('all');
  const [searchUser, setSearchUser] = useState('');
  const [dateFilter, setDateFilter] = useState('all');
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    fetchLogs();
  }, [selectedAction, searchUser]);

  const fetchLogs = async () => {
    setIsLoading(true);
    try {
      const params: Record<string, string> = {};
      if (selectedAction !== 'all') {
        params.action = selectedAction;
      }
      if (searchUser.trim() !== '') {
        params.userId = searchUser;
      }

      const response = await api.get('/api/audit/logs', { params });
      setLogs(response.data);
    } catch (error) {
      console.error('Error fetching logs:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const actionTypes = [
    { value: 'all', label: 'All Actions' },
    { value: 'consent_created', label: 'Consent Created' },
    { value: 'consent_revoked', label: 'Consent Revoked' },
    { value: 'data_accessed', label: 'Data Accessed' },
    { value: 'request_submitted', label: 'Request Submitted' },
    { value: 'request_approved', label: 'Request Approved' },
    { value: 'request_rejected', label: 'Request Rejected' }
  ];

  const getActionIcon = (action: string) => {
    switch (action) {
      case 'consent_created':
        return <Shield className="w-5 h-5 text-blue-500" />;
      case 'consent_revoked':
        return <Shield className="w-5 h-5 text-red-500" />;
      case 'data_accessed':
        return <Database className="w-5 h-5 text-green-500" />;
      case 'request_submitted':
        return <Send className="w-5 h-5 text-purple-500" />;
      case 'request_approved':
        return <CheckCircle className="w-5 h-5 text-green-500" />;
      case 'request_rejected':
        return <XCircle className="w-5 h-5 text-red-500" />;
      default:
        return <FileCheck className="w-5 h-5 text-gray-500" />;
    }
  };

  const getActionColor = (action: string) => {
    switch (action) {
      case 'consent_created':
        return 'bg-blue-100 text-blue-700 border-blue-200';
      case 'consent_revoked':
        return 'bg-red-100 text-red-700 border-red-200';
      case 'data_accessed':
        return 'bg-green-100 text-green-700 border-green-200';
      case 'request_submitted':
        return 'bg-purple-100 text-purple-700 border-purple-200';
      case 'request_approved':
        return 'bg-emerald-100 text-emerald-700 border-emerald-200';
      case 'request_rejected':
        return 'bg-rose-100 text-rose-700 border-rose-200';
      default:
        return 'bg-gray-100 text-gray-700 border-gray-200';
    }
  };

  const formatActionLabel = (action: string) => {
    return action.split('_').map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(' ');
  };

  const filteredLogs = logs.filter(log => {
    let matchesDate = true;
    if (dateFilter !== 'all') {
      const logDate = new Date(log.timestamp);
      const now = new Date();
      const daysDiff = Math.floor((now.getTime() - logDate.getTime()) / (1000 * 60 * 60 * 24));

      if (dateFilter === 'today') matchesDate = daysDiff === 0;
      else if (dateFilter === 'week') matchesDate = daysDiff <= 7;
      else if (dateFilter === 'month') matchesDate = daysDiff <= 30;
    }

    return matchesDate;
  });

  const actionStats = actionTypes.slice(1).map(type => ({
    ...type,
    count: logs.filter(log => log.action === type.value).length
  }));

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Audit Log</h1>
          <p className="text-gray-600 mt-1">Complete history of all data access and consent events</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="bg-green-50 px-4 py-2 rounded-lg border border-green-200">
            <p className="text-sm text-green-700 font-medium">{filteredLogs.length} Events</p>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
        {actionStats.map(stat => (
          <div key={stat.value} className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
            <p className="text-xs text-gray-600 mb-1">{stat.label}</p>
            <p className="text-2xl font-bold text-gray-900">{stat.count}</p>
          </div>
        ))}
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
        <div className="flex flex-col lg:flex-row gap-4 mb-6">
          <div className="flex-1">
            <div className="relative">
              <User className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
              <input
                type="text"
                placeholder="Filter by user name..."
                value={searchUser}
                onChange={(e) => setSearchUser(e.target.value)}
                className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
          </div>

          <div className="flex gap-2">
            <div className="relative">
              <Filter className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
              <select
                value={selectedAction}
                onChange={(e) => setSelectedAction(e.target.value)}
                className="pl-10 pr-8 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent appearance-none bg-white"
              >
                {actionTypes.map(type => (
                  <option key={type.value} value={type.value}>{type.label}</option>
                ))}
              </select>
            </div>

            <div className="relative">
              <Calendar className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
              <select
                value={dateFilter}
                onChange={(e) => setDateFilter(e.target.value)}
                className="pl-10 pr-8 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent appearance-none bg-white"
              >
                <option value="all">All Time</option>
                <option value="today">Today</option>
                <option value="week">Last 7 Days</option>
                <option value="month">Last 30 Days</option>
              </select>
            </div>
          </div>
        </div>

        <div className="space-y-3">
          {filteredLogs.map((log, index) => (
            <div key={log.id} className="relative">
              {index < filteredLogs.length - 1 && (
                <div className="absolute left-6 top-14 bottom-0 w-0.5 bg-gray-200 -translate-x-1/2" />
              )}

              <div className="flex gap-4 group hover:bg-gray-50 p-4 rounded-lg transition-colors">
                <div className="relative flex-shrink-0">
                  <div className="w-12 h-12 rounded-full bg-white border-2 border-gray-200 flex items-center justify-center group-hover:border-gray-300 transition-colors">
                    {getActionIcon(log.action)}
                  </div>
                </div>

                <div className="flex-1 min-w-0 flex flex-col justify-center">
                  <div className="flex items-start justify-between gap-4 mb-2.5">
                    <div className="flex-1">
                      <div className="flex items-center gap-2.5 mb-2">
                        <span className={`px-2.5 py-1 rounded-full text-xs font-bold border ${getActionColor(log.action)}`}>
                          {formatActionLabel(log.action)}
                        </span>
                        <span className="text-[13px] text-gray-500 font-medium">by</span>
                        <span className="text-[15px] font-bold text-gray-900 tracking-tight">{log.userName || log.userId}</span>
                      </div>
                      <p className="text-[14px] text-gray-700 font-medium leading-relaxed">{log.details}</p>
                    </div>
                    <div className="text-right flex-shrink-0 mt-0.5">
                      <p className="text-xs font-semibold text-gray-500 mb-0.5">
                        {new Date(log.timestamp).toLocaleDateString()}
                      </p>
                      <p className="text-[11px] text-gray-400 font-medium">
                        {new Date(log.timestamp).toLocaleTimeString()}
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center gap-1.5 text-[13px] text-slate-600 font-medium bg-slate-50 w-fit px-2 py-1 rounded-md border border-slate-100">
                    <Database className="w-3.5 h-3.5 text-slate-500" />
                    <span className="truncate max-w-[400px]" title={log.datasetName}>{log.datasetName}</span>
                  </div>
                </div>
              </div>
            </div>
          ))}

          {filteredLogs.length === 0 && (
            <div className="text-center py-12 text-gray-500">
              <FileCheck className="w-16 h-16 mx-auto mb-4 opacity-30" />
              <h3 className="text-lg font-semibold text-gray-900 mb-2">No audit logs found</h3>
              <p className="text-sm">Try adjusting your filters to see more results</p>
            </div>
          )}
        </div>
      </div>

      <div className="bg-gradient-to-r from-blue-50 to-purple-50 rounded-xl border border-blue-200 p-6">
        <div className="flex items-start gap-4">
          <div className="w-12 h-12 bg-blue-600 rounded-lg flex items-center justify-center flex-shrink-0">
            <Shield className="w-6 h-6 text-white" />
          </div>
          <div>
            <h3 className="text-lg font-semibold text-gray-900 mb-2">Compliance & Security</h3>
            <p className="text-sm text-gray-700 mb-3">
              All data access events are permanently logged and cannot be modified or deleted. This audit trail
              ensures GDPR compliance and provides complete transparency in data handling.
            </p>
            <div className="flex flex-wrap gap-3">
              <span className="bg-white px-3 py-1 rounded-full text-xs font-medium text-gray-700 border border-gray-200">
                GDPR Compliant
              </span>
              <span className="bg-white px-3 py-1 rounded-full text-xs font-medium text-gray-700 border border-gray-200">
                Immutable Records
              </span>
              <span className="bg-white px-3 py-1 rounded-full text-xs font-medium text-gray-700 border border-gray-200">
                Real-time Logging
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

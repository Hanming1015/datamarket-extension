import { useState, useEffect } from 'react';
import api from '../services/api';
import { Shield, Plus, X, Eye, Calendar, Users, Target, FileText, CheckCircle, XCircle, Clock } from 'lucide-react';
import { DataSet, ConsentRule } from '../types';
import { Toast } from '../components/Toast';

export default function ConsentManagement({ user }: { user: any }) {
  if (user?.role !== 'owner') {
    return (
      <div className="flex flex-col items-center justify-center py-32 text-gray-500">
        <Shield className="w-16 h-16 text-gray-300 mb-4" />
        <h2 className="text-xl font-semibold text-gray-900 mb-2">No Content Available</h2>
        <p>This page is reserved for Data Owners to manage their consent rules.</p>
      </div>
    );
  }

  const [selectedDataset, setSelectedDataset] = useState<string | null>(null);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showRevokeModal, setShowRevokeModal] = useState<string | null>(null);
  const [consentRules, setConsentRules] = useState<ConsentRule[]>([]);
  const [datasets, setDatasets] = useState<DataSet[]>([]);
  const [accessHistory, setAccessHistory] = useState<any[]>([]);
  const [formData, setFormData] = useState({
    allowedRoles: [] as string[],
    allowedPurposes: [] as string[],
    allowedFields: [] as string[],
    deniedFields: [] as string[],
    validUntil: ''
  });
  const [toast, setToast] = useState<{show: boolean, message: string, type: 'success' | 'error' | 'warning' | 'info'}>({ show: false, message: '', type: 'info' });

  const roles = ['Research Institution', 'University', 'Pharmaceutical Company', 'Healthcare Provider'];
  const purposes = ['Medical Research', 'Clinical Trials', 'Drug Development', 'Sleep Research', 'Genetic Research'];

  const fetchConsents = async () => {
    try {
      const response = await api.get('/api/consents');
        
      const normalizedData = response.data.map((rule: any) => {
        const safeParse = (value: any) => {
          if (Array.isArray(value)) return value;
          if (typeof value === 'string') {
            try { 
              const parsed = JSON.parse(value); 
              if (Array.isArray(parsed)) return parsed;
              if (typeof parsed === 'string' && parsed.startsWith('[')) return JSON.parse(parsed);
              return [parsed];
            } catch { 
              return [value]; 
            }
          }
          return [];
        };

        return {
          ...rule,
          allowedRoles: safeParse(rule.allowedRoles),
          allowedPurposes: safeParse(rule.allowedPurposes),
          allowedFields: safeParse(rule.allowedFields),
          deniedFields: safeParse(rule.deniedFields || rule.denied_fields)
        };
      });

      setConsentRules(normalizedData);
    } catch (error) {
      console.error('❌ Error fetching consent rules:', error);
    }
  };

  useEffect(() => {
    fetchConsents();
  }, []);

  const getConsentRules = (datasetId: string) => {
    return consentRules.filter(rule => rule.datasetId === datasetId);
  };

  const fetchDatasets = async () => {
    try {
      // Use the newly created datasetApi which points to /api/datasets/list
      const response = await api.get('/api/datasets/list');

      setDatasets(response.data);
    } catch (error) {
      console.error('Error fetching datasets:', error);
    }
  };

  useEffect(() => {
    fetchDatasets();
  }, []);

  useEffect(() => {
    if (selectedDataset) {
      const fetchAccessHistory = async () => {
        try {
          const response = await api.get('/api/access/requests', {
            params: { datasetId: selectedDataset }
          });
          setAccessHistory(response.data);
        } catch (error) {
          console.error('Error fetching access history:', error);
        }
      };
      fetchAccessHistory();
    } else {
      setAccessHistory([]);
    }
  }, [selectedDataset]);

  const dataset = datasets.find(ds => ds.id === selectedDataset);

  const toggleArrayItem = (array: string[], item: string, setter: (arr: string[]) => void) => {
    if (array.includes(item)) {
      setter(array.filter(i => i !== item));
    } else {
      setter([...array, item]);
    }
  };

  const handleCreateRule = async () => {
    if (!selectedDataset) return;

    const ruleData = {
      ownerId: user?.id, // Keep as string for backend user_id reference if needed, though POJO might expect String
      datasetId: selectedDataset, // e.g., 'ds1', 'ds2' exactly as backend datasets table uses
      allowedRoles: formData.allowedRoles,
      allowedPurposes: formData.allowedPurposes,
      allowedFields: formData.allowedFields,
      deniedFields: formData.deniedFields,
      validFrom: new Date().toISOString().split('T')[0],
      validUntil: formData.validUntil ? formData.validUntil : '2099-12-31',
      status: 'active'
    };

    try {
      const response = await api.post('/api/consents', ruleData);
      console.log('Rule created successfully:', response.data);
      setToast({ show: true, message: 'Consent rule created successfully!', type: 'success' });
      setShowCreateModal(false);

      setFormData({
        allowedRoles: [],
        allowedPurposes: [],
        allowedFields: [],
        deniedFields: [],
        validUntil: ''
      });

      fetchConsents();
    } catch (error) {
      console.error('❌ Error creating consent rule:', error);
      setToast({ show: true, message: 'Failed to create consent rule. Please check console.', type: 'error' });
    }
  };

  const handleRevokeRule = (ruleId: string) => {
    setShowRevokeModal(ruleId);
  };

  const confirmRevokeRule = async () => {
    if (!showRevokeModal) return;

    const ruleId = showRevokeModal;
    setShowRevokeModal(null);

    try {
      await api.put(`/api/consents/${ruleId}/revoke`);
      setToast({ show: true, message: 'Consent rule revoked successfully!', type: 'success' });
      fetchConsents();
    } catch (error) {
      console.error('❌ Error revoking consent rule:', error);
      setToast({ show: true, message: 'Failed to revoke consent rule. Please check console.', type: 'error' });
    }
  };

  return (
    <div className="space-y-6">
      {toast.show && (
        <Toast 
          message={toast.message} 
          type={toast.type} 
          onClose={() => setToast({ ...toast, show: false })} 
        />
      )}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Consent Management</h1>
          <p className="text-gray-600 mt-1">Control who can access your data and for what purpose</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="bg-emerald-50 px-4 py-2 rounded-lg border border-emerald-200">
            <p className="text-sm text-emerald-700 font-medium">{datasets.length} Active Datasets</p>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-1 space-y-4">
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-4">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">My Datasets</h2>
            <div className="space-y-2">
              {datasets.map(dataset => {
                const rules = getConsentRules(dataset.id);
                const activeRules = rules.filter(r => r.status === 'active').length;

                return (
                  <button
                    key={dataset.id}
                    onClick={() => setSelectedDataset(dataset.id)}
                    className={`w-full text-left p-4 rounded-lg border-2 transition-all ${
                      selectedDataset === dataset.id
                        ? 'border-blue-500 bg-blue-50'
                        : 'border-gray-200 hover:border-gray-300 bg-white'
                    }`}
                  >
                    <div className="flex items-start justify-between">
                      <div className="flex-1">
                        <h3 className="font-semibold text-gray-900 text-sm">{dataset.name}</h3>
                        <p className="text-xs text-gray-600 mt-1">{dataset.recordCount.toLocaleString()} records</p>
                      </div>
                      {activeRules > 0 && (
                        <span className="bg-green-100 text-green-700 text-xs px-2 py-1 rounded-full font-medium">
                          {activeRules} active
                        </span>
                      )}
                    </div>
                  </button>
                );
              })}
            </div>
          </div>
        </div>

        <div className="lg:col-span-2 space-y-4">
          {selectedDataset && dataset ? (
            <>
              <div className="bg-gradient-to-br from-blue-500 to-blue-600 rounded-xl shadow-lg p-6 text-white">
                <div className="flex items-start justify-between">
                  <div>
                    <h2 className="text-2xl font-bold">{dataset.name}</h2>
                    <p className="text-blue-100 mt-2">{dataset.description}</p>
                    <div className="flex gap-4 mt-4">
                      <div className="bg-white/20 backdrop-blur-sm px-3 py-2 rounded-lg">
                        <p className="text-xs text-blue-100">Records</p>
                        <p className="text-lg font-bold">{dataset.recordCount.toLocaleString()}</p>
                      </div>
                      <div className="bg-white/20 backdrop-blur-sm px-3 py-2 rounded-lg">
                        <p className="text-xs text-blue-100">Fields</p>
                        <p className="text-lg font-bold">{dataset.fields.length}</p>
                      </div>
                      <div className="bg-white/20 backdrop-blur-sm px-3 py-2 rounded-lg">
                        <p className="text-xs text-blue-100">Category</p>
                        <p className="text-lg font-bold capitalize">{dataset.category}</p>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="text-lg font-semibold text-gray-900 flex items-center gap-2">
                    <Shield className="w-5 h-5 text-blue-600" />
                    Consent Rules
                  </h3>
                  <button
                    onClick={() => setShowCreateModal(true)}
                    className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                  >
                    <Plus className="w-4 h-4" />
                    Create Rule
                  </button>
                </div>

                <div className="space-y-3">
                  {getConsentRules(selectedDataset).map(rule => (
                    <div key={rule.id} className="border border-gray-200 rounded-lg p-4 hover:shadow-md transition-shadow">
                      <div className="flex items-start justify-between">
                        <div className="flex-1 space-y-3">
                          <div className="flex items-center gap-2">
                            {rule.status === 'active' ? (
                              <CheckCircle className="w-5 h-5 text-green-500" />
                            ) : rule.status === 'revoked' ? (
                              <XCircle className="w-5 h-5 text-red-500" />
                            ) : (
                              <Clock className="w-5 h-5 text-gray-400" />
                            )}
                            <span className={`text-sm font-semibold ${
                              rule.status === 'active' ? 'text-green-700' :
                              rule.status === 'revoked' ? 'text-red-700' :
                              'text-gray-500'
                            }`}>
                              {rule.status.toUpperCase()}
                            </span>
                          </div>

                          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                            <div>
                              <p className="text-xs text-gray-500 flex items-center gap-1 mb-1">
                                <Users className="w-3 h-3" />
                                Allowed Roles
                              </p>
                              <div className="flex flex-wrap gap-1">
                                {(rule.allowedRoles || []).filter(Boolean).map((role, idx) => (
                                  <span key={`role-${idx}`} className="bg-blue-100 text-blue-700 text-xs px-2 py-1 rounded">
                                    {String(role).replace(/["[\]\\]/g, '')}
                                  </span>
                                ))}
                              </div>
                            </div>

                            <div>
                              <p className="text-xs text-gray-500 flex items-center gap-1 mb-1">
                                <Target className="w-3 h-3" />
                                Allowed Purposes
                              </p>
                              <div className="flex flex-wrap gap-1">
                                {(rule.allowedPurposes || []).filter(Boolean).map((purpose, idx) => (
                                  <span key={`purpose-${idx}`} className="bg-purple-100 text-purple-700 text-xs px-2 py-1 rounded">
                                    {String(purpose).replace(/["[\]\\]/g, '')}
                                  </span>
                                ))}
                              </div>
                            </div>

                            <div>
                              <p className="text-xs text-gray-500 flex items-center gap-1 mb-1">
                                <FileText className="w-3 h-3" />
                                Allowed Fields
                              </p>
                              <div className="flex flex-wrap gap-1">
                                {(rule.allowedFields || []).filter(Boolean).map((field, idx) => (
                                  <span key={`field-${idx}`} className="bg-gray-100 text-gray-700 text-xs px-2 py-1 rounded">
                                    {String(field).replace(/["[\]\\]/g, '')}
                                  </span>
                                ))}
                              </div>
                            </div>

                            {rule.deniedFields && rule.deniedFields.length > 0 && (
                            <div>
                              <p className="text-xs text-gray-500 flex items-center gap-1 mb-1">
                                <FileText className="w-3 h-3 transform rotate-180" />
                                Denied Fields
                              </p>
                              <div className="flex flex-wrap gap-1">
                                {(rule.deniedFields || []).filter(Boolean).map((field, idx) => (
                                  <span key={`denied-field-${idx}`} className="bg-red-50 text-red-700 border border-red-100 text-xs px-2 py-1 rounded">
                                    {String(field).replace(/["[\]\\]/g, '')}
                                  </span>
                                ))}
                              </div>
                            </div>
                            )}

                            <div>
                              <p className="text-xs text-gray-500 flex items-center gap-1 mb-1">
                                <Calendar className="w-3 h-3" />
                                Valid Period
                              </p>
                              <p className="text-xs text-gray-700">
                                {new Date(rule.validFrom).toLocaleDateString()} - {new Date(rule.validUntil).toLocaleDateString()}
                              </p>
                            </div>
                          </div>
                        </div>

                        {rule.status === 'active' && (
                          <button 
                            onClick={() => handleRevokeRule(rule.id)}
                            className="ml-4 px-3 py-1 text-sm text-red-600 hover:bg-red-50 rounded-lg transition-colors cursor-pointer"
                          >
                            Revoke
                          </button>
                        )}
                      </div>
                    </div>
                  ))}

                  {getConsentRules(selectedDataset).length === 0 && (
                    <div className="text-center py-8 text-gray-500">
                      <Shield className="w-12 h-12 mx-auto mb-2 opacity-30" />
                      <p>No consent rules defined yet</p>
                      <p className="text-sm">Create your first rule to start sharing data securely</p>
                    </div>
                  )}
                </div>
              </div>

              <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
                <h3 className="text-lg font-semibold text-gray-900 flex items-center gap-2 mb-4">
                  <Eye className="w-5 h-5 text-blue-600" />
                  Access History
                </h3>
                <div className="space-y-2">
                  {accessHistory.map(request => (
                    <div key={request.id} className="flex items-center justify-between p-3 border border-gray-200 rounded-lg hover:bg-gray-50">
                      <div>
                        <p className="font-medium text-gray-900 text-sm">{request.requesterName}</p>
                        <p className="text-xs text-gray-600">Purpose: {request.purpose}</p>
                        <p className="text-xs text-gray-500 mt-1">{new Date(request.requestedAt).toLocaleString()}</p>
                      </div>
                      <span className={`px-3 py-1 text-xs font-medium rounded-full ${
                        request.status === 'approved' ? 'bg-green-100 text-green-700' :
                        request.status === 'rejected' ? 'bg-red-100 text-red-700' :
                        request.status === 'partial' ? 'bg-yellow-100 text-yellow-700' :
                        'bg-gray-100 text-gray-700'
                      }`}>
                        {request.status}
                      </span>
                    </div>
                  ))}

                  {accessHistory.length === 0 && (
                    <div className="text-center py-6 text-gray-500">
                      <p className="text-sm">No access requests yet</p>
                    </div>
                  )}
                </div>
              </div>
            </>
          ) : (
            <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-12 text-center">
              <Shield className="w-16 h-16 mx-auto text-gray-300 mb-4" />
              <h3 className="text-lg font-semibold text-gray-900 mb-2">Select a Dataset</h3>
              <p className="text-gray-600">Choose a dataset from the list to manage consent rules</p>
            </div>
          )}
        </div>
      </div>

      {showCreateModal && dataset && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl shadow-2xl max-w-2xl w-full max-h-[90vh] overflow-y-auto">
            <div className="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between">
              <h3 className="text-xl font-bold text-gray-900">Create Consent Rule</h3>
              <button onClick={() => setShowCreateModal(false)} className="text-gray-400 hover:text-gray-600">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-6 space-y-6">
              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-2">Allowed Roles</label>
                <div className="space-y-2">
                  {roles.map(role => (
                    <label key={role} className="flex items-center gap-2 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={formData.allowedRoles.includes(role)}
                        onChange={() => toggleArrayItem(
                          formData.allowedRoles,
                          role,
                          (arr) => setFormData({ ...formData, allowedRoles: arr })
                        )}
                        className="w-4 h-4 text-blue-600 rounded"
                      />
                      <span className="text-sm text-gray-700">{role}</span>
                    </label>
                  ))}
                </div>
              </div>

              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-2">Allowed Purposes</label>
                <div className="space-y-2">
                  {purposes.map(purpose => (
                    <label key={purpose} className="flex items-center gap-2 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={formData.allowedPurposes.includes(purpose)}
                        onChange={() => toggleArrayItem(
                          formData.allowedPurposes,
                          purpose,
                          (arr) => setFormData({ ...formData, allowedPurposes: arr })
                        )}
                        className="w-4 h-4 text-blue-600 rounded"
                      />
                      <span className="text-sm text-gray-700">{purpose}</span>
                    </label>
                  ))}
                </div>
              </div>

              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-2">Allowed Fields</label>
                <div className="space-y-2">
                  {dataset.fields.map(field => (
                    <label key={field} className="flex items-center gap-2 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={formData.allowedFields.includes(field)}
                        onChange={() => toggleArrayItem(
                          formData.allowedFields,
                          field,
                          (arr) => setFormData({ ...formData, allowedFields: arr, deniedFields: formData.deniedFields.filter(f => f !== field) })
                        )}
                        className="w-4 h-4 text-blue-600 rounded"
                      />
                      <span className="text-sm text-gray-700 font-mono">{field}</span>
                    </label>
                  ))}
                </div>
              </div>

              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-2">Denied Fields (Optional)</label>
                <div className="space-y-2">
                  {dataset.fields.map(field => (
                    <label key={`denied-${field}`} className="flex items-center gap-2 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={formData.deniedFields.includes(field)}
                        onChange={() => toggleArrayItem(
                          formData.deniedFields,
                          field,
                          (arr) => setFormData({ ...formData, deniedFields: arr, allowedFields: formData.allowedFields.filter(f => f !== field) })
                        )}
                        className="w-4 h-4 text-red-600 rounded"
                      />
                      <span className="text-sm text-gray-700 font-mono">{field}</span>
                    </label>
                  ))}
                </div>
              </div>

              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-2">Valid Until</label>
                <input
                  type="date"
                  value={formData.validUntil}
                  onChange={(e) => setFormData({ ...formData, validUntil: e.target.value })}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                />
              </div>

              <div className="flex gap-3 pt-4">
                <button
                  onClick={() => setShowCreateModal(false)}
                  className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
                >
                  Cancel
                </button>
                <button
                  onClick={handleCreateRule}
                  disabled={formData.allowedRoles.length === 0 || formData.allowedPurposes.length === 0}
                  className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:bg-gray-300 disabled:cursor-not-allowed"
                >
                  Create Rule
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Revoke Confirmation Modal */}
      {showRevokeModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl shadow-2xl max-w-sm w-full">
            <div className="p-6">
              <div className="flex items-center gap-3 text-red-600 mb-4">
                <XCircle className="w-8 h-8" />
                <h3 className="text-xl font-bold">Revoke Consent</h3>
              </div>
              <p className="text-gray-600 mb-6 leading-relaxed">
                Are you sure you want to revoke this consent rule? This action will immediately block future access requests relying on this rule.
              </p>
              <div className="flex gap-3">
                <button
                  onClick={() => setShowRevokeModal(null)}
                  className="flex-1 px-4 py-2.5 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors font-medium"
                >
                  Cancel
                </button>
                <button
                  onClick={confirmRevokeRule}
                  className="flex-1 px-4 py-2.5 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors font-medium"
                >
                  Yes, Revoke
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

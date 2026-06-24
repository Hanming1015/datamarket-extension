import { useState, useEffect } from 'react';
import { Search, Database, Send, X, CheckCircle2, AlertCircle, Clock, Filter, User, Hash } from 'lucide-react';
import { DataSet } from '../types';
import api from '../services/api';
import { Toast } from '../components/Toast';

export default function DataMarket({ user }: { user: any }) {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [datasets, setDatasets] = useState<DataSet[]>([]);
  const [selectedDataset, setSelectedDataset] = useState<DataSet | null>(null);
  const [showRequestModal, setShowRequestModal] = useState(false);
  const [requestForm, setRequestForm] = useState({
    purpose: '',
    requestedFields: [] as string[],
    duration: '6'
  });
  const [toast, setToast] = useState<{ show: boolean, message: string, type: 'success' | 'error' | 'warning' | 'info' }>({ show: false, message: '', type: 'info' });

  const categories = ['all', 'health', 'fitness', 'genomic', 'lifestyle'];
  const purposes = ['Medical Research', 'Clinical Trials', 'Drug Development', 'Sleep Research', 'Genetic Research', 'AI Model Training'];

  useEffect(() => {
    const fetchDatasets = async () => {
      try {
        const params: any = {};
        if (selectedCategory !== 'all') params.category = selectedCategory;
        if (searchQuery) params.keyword = searchQuery;

        const response = await api.get('/api/datasets/all', { params });
        //console.log("Fetched datasets from backend:", response.data);

        // Since backend currently doesn't implement query wrappers for these params,
        // we filter the results safely on the frontend for now:
        let filteredData = response.data || [];
        if (selectedCategory !== 'all') {
          filteredData = filteredData.filter((ds: any) => ds.category?.toLowerCase() === selectedCategory.toLowerCase());
        }
        if (searchQuery) {
          filteredData = filteredData.filter((ds: any) =>
            ds.name?.toLowerCase().includes(searchQuery.toLowerCase()) ||
            ds.description?.toLowerCase().includes(searchQuery.toLowerCase())
          );
        }

        setDatasets(filteredData);
      } catch (error) {
        console.error("Failed to fetch datasets", error);
      }
    };

    const fetchMyRequests = async () => {
      try {
        const response = await api.get('/api/access/requests', {
          params: { userId: user?.id }
        });
        setMyRequests(response.data.slice(0, 5));
      } catch (error) {
        console.error("Failed to fetch my requests", error);
      }
    };

    const timer = setTimeout(() => {
      fetchDatasets();
      fetchMyRequests();
    }, 300);
    return () => clearTimeout(timer);
  }, [searchQuery, selectedCategory]);

  const filteredDatasets = datasets; // Already filtered by the backend

  const [myRequests, setMyRequests] = useState<any[]>([]);

  const handleRequestAccess = async () => {
    if (!selectedDataset) return;

    try {
      const payload = {
        requesterId: user?.id,
        requesterName: user?.name,
        // Send the specific type that perfectly matches Consent Allowed Roles
        consumerType: user?.organization || "Research Institution",
        datasetId: selectedDataset.id,
        purpose: requestForm.purpose,
        requestedFields: requestForm.requestedFields
      };

      const response = await api.post("/api/access/request", payload);

      console.log("Server Response:", response.data);

      if (response.data.status === "rejected") {
        const errorReason = response.data.decision?.reasons?.[Object.keys(response.data.decision.reasons)[0]] || "Insufficient permissions";
        setToast({ show: true, message: `Access Request Rejected\nReason: ${errorReason}`, type: 'error' });
      } else {
        setToast({
          show: true,
          message: `Request Successful (${response.data.status})!\nTotal Cost: $${response.data.pricing?.totalCost}`,
          type: 'success'
        });
      }

      setShowRequestModal(false);
      setRequestForm({ purpose: '', requestedFields: [], duration: '6' });

    } catch (error) {
      console.error("Request failed", error);
      setToast({ show: true, message: "Submission failed. Please check if the server is running.", type: 'error' });
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
          <h1 className="text-3xl font-bold text-gray-900">Data Market</h1>
          <p className="text-gray-600 mt-1">Discover and request access to datasets</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="bg-blue-50 px-4 py-2 rounded-lg border border-blue-200">
            <p className="text-sm text-blue-700 font-medium">{filteredDatasets.length} Available Datasets</p>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-4">
        <div className="flex flex-col md:flex-row gap-4">
          <div className="flex-1 relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
            <input
              type="text"
              placeholder="Search datasets by name or description..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
          </div>
          <div className="flex items-center gap-2">
            <Filter className="w-5 h-5 text-gray-400" />
            <div className="flex gap-2">
              {categories.map(cat => (
                <button
                  key={cat}
                  onClick={() => setSelectedCategory(cat)}
                  className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${selectedCategory === cat
                      ? 'bg-blue-600 text-white'
                      : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                    }`}
                >
                  {cat === 'all' ? 'All' : cat.charAt(0).toUpperCase() + cat.slice(1)}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-4">
          <div className="grid gap-4">
            {filteredDatasets.map(dataset => (
              <div
                key={dataset.id}
                className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 hover:shadow-md transition-all cursor-pointer"
                onClick={() => setSelectedDataset(dataset)}
              >
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-2">
                      <Database className="w-5 h-5 text-blue-600" />
                      <h3 className="text-lg font-semibold text-gray-900">{dataset.name}</h3>
                      <span className="bg-blue-100 text-blue-700 text-xs px-2 py-1 rounded-full font-medium capitalize">
                        {dataset.category}
                      </span>
                    </div>
                    <p className="text-gray-600 text-sm mb-5 leading-relaxed line-clamp-2 md:line-clamp-3" title={dataset.description}>
                      {dataset.description}
                    </p>
                    <div className="flex flex-wrap items-center gap-x-5 gap-y-3 mt-auto pt-4 border-t border-gray-100">
                      <div className="flex items-center gap-1.5 text-gray-500 text-sm font-medium bg-gray-50 px-2.5 py-1 rounded-md">
                        <Database className="w-4 h-4 text-blue-500" />
                        <span>{dataset.recordCount.toLocaleString()} records</span>
                      </div>
                      <div className="flex items-center gap-1.5 text-gray-500 text-sm font-medium bg-gray-50 px-2.5 py-1 rounded-md">
                        <Hash className="w-4 h-4 text-emerald-500" />
                        <span>{dataset.fields.length} fields</span>
                      </div>
                      <div className="flex items-center gap-1.5 text-gray-500 text-sm font-medium bg-gray-50 px-2.5 py-1 rounded-md">
                        <User className="w-4 h-4 text-purple-500" />
                        <span className="truncate max-w-[120px]" title={(dataset as any).ownerName || dataset.owner || 'Unknown'}>
                          Owner: {(dataset as any).ownerName || dataset.owner || 'Unknown'}
                        </span>
                      </div>
                    </div>
                  </div>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setSelectedDataset(dataset);
                      setShowRequestModal(true);
                    }}
                    className="ml-4 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors flex items-center gap-2"
                  >
                    <Send className="w-4 h-4" />
                    Request Access
                  </button>
                </div>
              </div>
            ))}
          </div>

          {filteredDatasets.length === 0 && (
            <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-12 text-center">
              <Database className="w-16 h-16 mx-auto text-gray-300 mb-4" />
              <h3 className="text-lg font-semibold text-gray-900 mb-2">No datasets found</h3>
              <p className="text-gray-600">Try adjusting your search or filters</p>
            </div>
          )}
        </div>

        <div className="lg:col-span-1">
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 sticky top-6">
            <h3 className="text-lg font-semibold text-gray-900 mb-4">My Requests</h3>
            <div className="space-y-3">
              {myRequests.map(request => (
                <div key={request.id} className="border border-gray-200 rounded-lg p-3">
                  <div className="flex items-start justify-between mb-2">
                    <p className="font-medium text-gray-900 text-sm">{request.datasetName}</p>
                    {request.status === 'approved' && <CheckCircle2 className="w-4 h-4 text-green-500 flex-shrink-0" />}
                    {request.status === 'rejected' && <AlertCircle className="w-4 h-4 text-red-500 flex-shrink-0" />}
                    {request.status === 'pending' && <Clock className="w-4 h-4 text-yellow-500 flex-shrink-0" />}
                    {request.status === 'partial' && <CheckCircle2 className="w-4 h-4 text-yellow-500 flex-shrink-0" />}
                  </div>
                  <p className="text-xs text-gray-600 mb-2">Purpose: {request.purpose}</p>
                  <div className="flex items-center justify-between">
                    <span className={`text-xs px-2 py-1 rounded-full font-medium ${request.status === 'approved' ? 'bg-green-100 text-green-700' :
                        request.status === 'rejected' ? 'bg-red-100 text-red-700' :
                          request.status === 'partial' ? 'bg-yellow-100 text-yellow-700' :
                            'bg-gray-100 text-gray-700'
                      }`}>
                      {request.status}
                    </span>
                    <span className="text-xs text-gray-500">
                      {new Date(request.requestedAt).toLocaleDateString()}
                    </span>
                  </div>
                </div>
              ))}
              {myRequests.length === 0 && (
                <div className="text-center py-4 text-gray-500 text-sm">No requests found</div>
              )}
            </div>

            <div className="mt-6 p-4 bg-blue-50 rounded-lg border border-blue-100">
              <h4 className="text-sm font-semibold text-blue-900 mb-2">Quick Stats</h4>
              <div className="space-y-2 text-sm">
                <div className="flex justify-between text-gray-600">
                  <span>Total Requests:</span>
                  <span className="font-semibold text-gray-900">{myRequests.length}</span>
                </div>
                <div className="flex justify-between text-green-700">
                  <span>Approved:</span>
                  <span className="font-semibold">{myRequests.filter(r => r.status === 'approved' || r.status === 'partial').length}</span>
                </div>
                <div className="flex justify-between text-yellow-700">
                  <span>Pending:</span>
                  <span className="font-semibold">{myRequests.filter(r => r.status === 'pending').length}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {showRequestModal && selectedDataset && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl shadow-2xl max-w-2xl w-full max-h-[90vh] overflow-y-auto">
            <div className="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between">
              <h3 className="text-xl font-bold text-gray-900">Request Data Access</h3>
              <button onClick={() => setShowRequestModal(false)} className="text-gray-400 hover:text-gray-600">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-6 space-y-6">
              <div className="bg-gray-50 rounded-lg p-4">
                <h4 className="font-semibold text-gray-900 mb-1">{selectedDataset.name}</h4>
                <p className="text-sm text-gray-600">{selectedDataset.description}</p>
                <div className="flex gap-4 mt-3 text-sm text-gray-600">
                  <span>{selectedDataset.recordCount.toLocaleString()} records</span>
                  <span>{selectedDataset.fields.length} fields available</span>
                </div>
              </div>

              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-2">Purpose of Access</label>
                <select
                  value={requestForm.purpose}
                  onChange={(e) => setRequestForm({ ...requestForm, purpose: e.target.value })}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                >
                  <option value="">Select a purpose...</option>
                  {purposes.map(purpose => (
                    <option key={purpose} value={purpose}>{purpose}</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-2 flex items-center justify-between">
                  <span>Requested Fields</span>
                  <span className="text-xs text-gray-500 font-normal">Select fields you need access to</span>
                </label>
                <div className="space-y-2 max-h-60 overflow-y-auto border border-gray-200 rounded-lg p-3 bg-white">
                  {selectedDataset.fields.map(field => {
                    const schemaItem = (selectedDataset as any).fieldsSchema?.find((s: any) => s.name === field);
                    const isSensitive = schemaItem?.sensitive === true;
                    const fieldType = schemaItem?.type || 'unknown';

                    return (
                      <label key={field} className={`flex items-start gap-3 cursor-pointer p-2.5 rounded-lg border border-transparent transition-all ${requestForm.requestedFields.includes(field)
                          ? (isSensitive ? 'bg-red-50 border-red-200' : 'bg-blue-50 border-blue-200')
                          : 'hover:bg-gray-50 border-gray-100'
                        }`}>
                        <div className="mt-0.5">
                          <input
                            type="checkbox"
                            checked={requestForm.requestedFields.includes(field)}
                            onChange={(e) => {
                              if (e.target.checked) {
                                setRequestForm({
                                  ...requestForm,
                                  requestedFields: [...requestForm.requestedFields, field]
                                });
                              } else {
                                setRequestForm({
                                  ...requestForm,
                                  requestedFields: requestForm.requestedFields.filter(f => f !== field)
                                });
                              }
                            }}
                            className={`w-4 h-4 rounded mt-0.5 ${isSensitive ? 'text-red-600 focus:ring-red-500' : 'text-blue-600 focus:ring-blue-500'}`}
                          />
                        </div>
                        <div className="flex-1 flex flex-col gap-1">
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="text-sm font-semibold text-gray-900 font-mono">{field}</span>
                            <span className="text-[10px] px-1.5 py-0.5 bg-gray-100 text-gray-600 rounded drop-shadow-sm border border-gray-200">
                              {fieldType}
                            </span>
                            {isSensitive && (
                              <span className="text-[10px] px-1.5 py-0.5 bg-red-100 text-red-700 font-semibold rounded drop-shadow-sm border border-red-200 flex items-center gap-1">
                                <AlertCircle className="w-3 h-3" />
                                Sensitive
                              </span>
                            )}
                          </div>
                          {schemaItem?.description && (
                            <span className="text-xs text-gray-500 leading-relaxed max-w-[90%]">
                              {schemaItem.description}
                            </span>
                          )}
                        </div>
                      </label>
                    );
                  })}
                </div>
                <p className="text-xs text-gray-500 mt-2 font-medium">
                  {requestForm.requestedFields.length} of {selectedDataset.fields.length} fields selected
                </p>
              </div>

              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-2">Access Duration</label>
                <select
                  value={requestForm.duration}
                  onChange={(e) => setRequestForm({ ...requestForm, duration: e.target.value })}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                >
                  <option value="3">3 months</option>
                  <option value="6">6 months</option>
                  <option value="12">12 months</option>
                  <option value="24">24 months</option>
                </select>
              </div>

              <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4">
                <p className="text-sm text-yellow-800">
                  <strong>Note:</strong> Your request will be evaluated against the data owner's consent rules.
                  You will be notified once the request is processed.
                </p>
              </div>

              <div className="flex gap-3 pt-4">
                <button
                  onClick={() => setShowRequestModal(false)}
                  className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
                >
                  Cancel
                </button>
                <button
                  onClick={handleRequestAccess}
                  disabled={!requestForm.purpose || requestForm.requestedFields.length === 0}
                  className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                >
                  <Send className="w-4 h-4" />
                  Submit Request
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

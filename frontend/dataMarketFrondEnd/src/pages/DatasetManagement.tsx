import { useState, useEffect } from 'react';
import { Plus, Edit2, Trash2, Database, Loader, X, DollarSign } from 'lucide-react';
import { datasetApi, pricingConfigApi } from '../services/api';
import { Toast } from '../components/Toast';

interface Dataset {
  id: string;
  name: string;
  description: string;
  category: string;
  recordCount: number;
  fieldsSchema: { name: string }[];
  fields: string[];
  createdAt: string;
}

export default function DatasetManagement({ user }: { user: any }) {
  if (user?.role !== 'owner') {
    return (
      <div className="flex flex-col items-center justify-center py-32 text-gray-500">
        <Database className="w-16 h-16 text-gray-300 mb-4" />
        <h2 className="text-xl font-semibold text-gray-900 mb-2">No Content Available</h2>
        <p>This page is reserved for Data Owners to manage their datasets.</p>
      </div>
    );
  }

  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingDataset, setEditingDataset] = useState<Dataset | null>(null);
  const [toast, setToast] = useState<{ show: boolean; message: string; type: 'success' | 'error' | 'info' }>({ show: false, message: '', type: 'info' });

  // Pricing Modal State
  const [isPricingModalOpen, setIsPricingModalOpen] = useState(false);
  const [pricingDataset, setPricingDataset] = useState<Dataset | null>(null);
  const [pricingForm, setPricingForm] = useState({
    id: undefined as string | undefined,
    perAccessBase: 0,
    perField: 0,
    sensitiveFieldMultiplier: 1.0,
    purposeMultiplierJson: '',
    bulkDiscountJson: ''
  });

  // Form State
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    category: '',
    recordCount: 0,
    fieldsSchemaStr: ''
  });

  const fetchDatasets = async () => {
    try {
      setLoading(true);
      const res = await datasetApi.list();
      setDatasets(res.data || []);
    } catch (error) {
      console.error('Failed to fetch datasets:', error);
      setToast({ show: true, message: 'Failed to load datasets', type: 'error' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDatasets();
  }, []);

  const handleOpenModal = (dataset?: Dataset) => {
    if (dataset) {
      setEditingDataset(dataset);
      setFormData({
        name: dataset.name,
        description: dataset.description,
        category: dataset.category,
        recordCount: dataset.recordCount,
        fieldsSchemaStr: dataset.fieldsSchema 
          ? JSON.stringify(dataset.fieldsSchema, null, 2) 
          : dataset.fields?.join(', ') || ''
      });
    } else {
      setEditingDataset(null);
      setFormData({ name: '', description: '', category: '', recordCount: 0, fieldsSchemaStr: '' });
    }
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setEditingDataset(null);
  };

  const handleOpenPricingModal = async (dataset: Dataset) => {
    setPricingDataset(dataset);
    setIsPricingModalOpen(true);
    setPricingForm({ 
      id: undefined, 
      perAccessBase: 0, 
      perField: 0, 
      sensitiveFieldMultiplier: 1.0,
      purposeMultiplierJson: '',
      bulkDiscountJson: ''
    });

    try {
      const res = await pricingConfigApi.getByDataset(dataset.id);
      if (res.data && res.data.id) {
        setPricingForm({
          id: res.data.id,
          perAccessBase: res.data.perAccessBase || 0,
          perField: res.data.perField || 0,
          sensitiveFieldMultiplier: res.data.sensitiveFieldMultiplier || 1.0,
          purposeMultiplierJson: res.data.purposeMultiplierJson 
            ? JSON.stringify(res.data.purposeMultiplierJson, null, 2) 
            : '',
          bulkDiscountJson: res.data.bulkDiscountJson 
            ? JSON.stringify(res.data.bulkDiscountJson, null, 2) 
            : ''
        });
      }
    } catch (err: any) {
      // It might be a 404/Empty if no config exists, ignore it or handle properly
      console.log('No existing pricing config found');
    }
  };

  const handleSavePricing = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!pricingDataset) return;

    let parsedPurpose = null, parsedBulk = null;
    try {
      parsedPurpose = pricingForm.purposeMultiplierJson.trim() ? JSON.parse(pricingForm.purposeMultiplierJson) : null;
      parsedBulk = pricingForm.bulkDiscountJson.trim() ? JSON.parse(pricingForm.bulkDiscountJson) : null;
    } catch (error) {
      setToast({ show: true, message: 'Invalid JSON format in configuration fields', type: 'error' });
      return; 
    }

    try {
      const payload = {
        id: pricingForm.id,
        datasetId: pricingDataset.id,
        perAccessBase: Number(pricingForm.perAccessBase),
        perField: Number(pricingForm.perField),
        sensitiveFieldMultiplier: Number(pricingForm.sensitiveFieldMultiplier),
        purposeMultiplierJson: parsedPurpose,
        bulkDiscountJson: parsedBulk
      };

      if (pricingForm.id) {
        await pricingConfigApi.update(payload);
        setToast({ show: true, message: 'Pricing updated successfully', type: 'success' });
      } else {
        await pricingConfigApi.add(payload);
        setToast({ show: true, message: 'Pricing configured successfully', type: 'success' });
      }
      setIsPricingModalOpen(false);
    } catch (err) {
      setToast({ show: true, message: 'Failed to save pricing configuration', type: 'error' });
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    let fieldsSchema;
    let fields;
    
    try {
      const inputStr = formData.fieldsSchemaStr.trim();
      if (inputStr.startsWith('[')) {
        fieldsSchema = JSON.parse(inputStr);
        fields = fieldsSchema.map((f: any) => f.name);
      } else {
        fieldsSchema = inputStr.split(',').map(s => ({ name: s.trim() })).filter(f => f.name);
        fields = fieldsSchema.map((f: any) => f.name);
      }
    } catch (err) {
      setToast({ show: true, message: 'Invalid JSON format in Fields Schema', type: 'error' });
      return;
    }

    const payload = {
      name: formData.name,
      description: formData.description,
      category: formData.category,
      recordCount: Number(formData.recordCount),
      fields,
      fieldsSchema
    };

    try {
      if (editingDataset) {
        await datasetApi.update({ ...payload, id: editingDataset.id });
        setToast({ show: true, message: 'Dataset updated successfully', type: 'success' });
      } else {
        await datasetApi.add(payload);
        setToast({ show: true, message: 'Dataset added successfully', type: 'success' });
      }
      handleCloseModal();
      fetchDatasets();
    } catch (error) {
      console.error('Save failed:', error);
      setToast({ show: true, message: 'Failed to save dataset', type: 'error' });
    }
  };

  const handleDelete = async (id: string) => {
    if (!window.confirm('Are you sure you want to delete this dataset?')) return;
    try {
      await datasetApi.remove(id);
      setToast({ show: true, message: 'Dataset deleted successfully', type: 'success' });
      fetchDatasets();
    } catch (error) {
      console.error('Delete failed:', error);
      setToast({ show: true, message: 'Failed to delete dataset', type: 'error' });
    }
  };

  if (!user || (user.role?.toUpperCase() !== 'OWNER' && user.role?.toUpperCase() !== 'ADMIN' && user.role !== 'data_owner')) {
    return (
      <div className="p-8 text-center text-gray-500">
        <h2 className="text-xl font-semibold mb-2">Access Denied</h2>
        <p>Only Data Owners can manage datasets. current role: {user?.role}</p>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {toast.show && (
        <Toast message={toast.message} type={toast.type} onClose={() => setToast({ ...toast, show: false })} />
      )}

      <div className="flex justify-between items-center mb-8">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
            <Database className="w-6 h-6 text-blue-600" />
            Dataset Management
          </h1>
          <p className="text-gray-600 mt-1">Manage your data assets available for sharing</p>
        </div>
        <button
          onClick={() => handleOpenModal()}
          className="bg-blue-600 text-white px-4 py-2 rounded-lg font-medium hover:bg-blue-700 transition flex items-center gap-2"
        >
          <Plus className="w-4 h-4" />
          Add Dataset
        </button>
      </div>

      {loading ? (
        <div className="flex justify-center items-center py-12">
          <Loader className="w-8 h-8 animate-spin text-blue-600" />
        </div>
      ) : datasets.length === 0 ? (
        <div className="bg-white rounded-xl shadow-sm p-12 text-center border md:col-span-2 lg:col-span-3">
          <Database className="w-12 h-12 text-gray-400 mx-auto mb-4" />
          <h3 className="text-lg font-medium text-gray-900 mb-2">No Datasets Found</h3>
          <p className="text-gray-500 mb-6">You haven't added any datasets yet.</p>
          <button
            onClick={() => handleOpenModal()}
            className="text-blue-600 font-medium hover:text-blue-700"
          >
            Create your first dataset
          </button>
        </div>
      ) : (
        <div className="bg-white shadow overflow-hidden sm:rounded-md border border-gray-200">
          <ul className="divide-y divide-gray-200">
            {datasets.map((dataset) => (
              <li key={dataset.id}>
                <div className="px-5 py-5 sm:px-8 hover:bg-gray-50 transition border-l-4 border-transparent hover:border-blue-500">
                  <div className="flex items-start justify-between">
                    <div className="flex flex-col max-w-4xl">
                      <div className="flex items-center gap-2 mb-1">
                        <Database className="w-5 h-5 text-blue-600" />
                        <h3 className="text-lg font-bold text-gray-900 tracking-tight">{dataset.name}</h3>
                      </div>
                      <p className="text-sm text-gray-500 mt-1.5 leading-relaxed">{dataset.description}</p>
                    </div>
                    <div className="ml-4 flex-shrink-0 flex gap-1">
                      <button onClick={() => handleOpenPricingModal(dataset)} className="p-2.5 text-gray-400 hover:text-green-600 rounded-full hover:bg-green-100 transition duration-200" title="Manage Pricing">
                        <DollarSign className="w-4 h-4" />
                      </button>
                      <button onClick={() => handleOpenModal(dataset)} className="p-2.5 text-gray-400 hover:text-blue-600 rounded-full hover:bg-blue-100 transition duration-200" title="Edit Dataset">
                        <Edit2 className="w-4 h-4" />
                      </button>
                      <button onClick={() => handleDelete(dataset.id)} className="p-2.5 text-gray-400 hover:text-red-600 rounded-full hover:bg-red-100 transition duration-200" title="Delete Dataset">
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                  <div className="mt-4 flex flex-col gap-3">
                    <div className="flex flex-col sm:flex-row sm:justify-between gap-2">
                      <div className="flex flex-wrap gap-4">
                        <p className="flex items-center text-sm text-gray-500">
                          Category: <span className="ml-2 px-2 py-0.5 bg-blue-50 text-blue-700 border border-blue-100 rounded-full font-medium text-xs capitalize">{dataset.category}</span>
                        </p>
                        <p className="flex items-center text-sm text-gray-500">
                          Records: <span className="ml-2 font-medium text-gray-700">{dataset.recordCount.toLocaleString()}</span>
                        </p>
                      </div>
                      <div className="flex items-center text-sm text-gray-500">
                        Created: {new Date(dataset.createdAt).toLocaleDateString()}
                      </div>
                    </div>
                    <div className="flex items-start mt-1">
                      <span className="text-sm text-gray-500 mr-3 mt-0.5 whitespace-nowrap">Fields:</span>
                      <div className="flex flex-wrap gap-1.5">
                        {dataset.fields && dataset.fields.length > 0 ? (
                          dataset.fields.map((field, idx) => (
                            <span key={idx} className="px-2 py-0.5 bg-gray-100/80 border border-gray-200 text-gray-600 rounded-md text-xs font-mono shadow-sm">
                              {field}
                            </span>
                          ))
                        ) : (
                          <span className="text-sm text-gray-500">-</span>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full overflow-hidden">
            <div className="flex justify-between items-center p-6 border-b">
              <h2 className="text-xl font-semibold">{editingDataset ? 'Edit Dataset' : 'Add New Dataset'}</h2>
              <button onClick={handleCloseModal} className="text-gray-500 hover:bg-gray-100 p-2 rounded-full">
                <X className="w-5 h-5" />
              </button>
            </div>
            
            <form onSubmit={handleSubmit} className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Name</label>
                <input required type="text" className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500" value={formData.name} onChange={e => setFormData({ ...formData, name: e.target.value })} />
              </div>
              
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
                <textarea required className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500" value={formData.description} onChange={e => setFormData({ ...formData, description: e.target.value })} rows={2} />
              </div>
              
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Category</label>
                  <select 
                    required 
                    className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 bg-white" 
                    value={formData.category} 
                    onChange={e => setFormData({ ...formData, category: e.target.value })}
                  >
                    <option value="" disabled>Select a category...</option>
                    <option value="health">health</option>
                    <option value="fitness">fitness</option>
                    <option value="genomic">genomic</option>
                    <option value="lifestyle">lifestyle</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Record Count</label>
                  <input required type="number" min="0" className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500" value={formData.recordCount} onChange={e => setFormData({ ...formData, recordCount: Number(e.target.value) })} />
                </div>
              </div>
              
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Fields Schema (Comma separated OR Full JSON Array)</label>
                <textarea 
                  required 
                  className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 font-mono text-sm" 
                  rows={6}
                  value={formData.fieldsSchemaStr} 
                  onChange={e => setFormData({ ...formData, fieldsSchemaStr: e.target.value })} 
                  placeholder={`id, name, age\n\nOR use JSON:\n[\n  { "name": "age", "sensitive": false },\n  { "name": "genotype", "sensitive": true }\n]`} 
                />
                <p className="text-xs text-gray-500 mt-1">Provide a JSON array describing each field and its sensitivity, or simply a comma-separated list of field names.</p>
              </div>
              
              <div className="pt-4 flex justify-end gap-3 border-t">
                <button type="button" onClick={handleCloseModal} className="px-4 py-2 text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-lg font-medium">Cancel</button>
                <button type="submit" className="px-4 py-2 bg-blue-600 text-white hover:bg-blue-700 rounded-lg font-medium">{editingDataset ? 'Save Changes' : 'Add Dataset'}</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Pricing Modal */}
      {isPricingModalOpen && pricingDataset && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full overflow-hidden">
            <div className="flex justify-between items-center p-6 border-b">
              <h2 className="text-xl font-semibold flex items-center gap-2">
                <DollarSign className="w-5 h-5 text-green-600" />
                Pricing Configuration
              </h2>
              <button onClick={() => setIsPricingModalOpen(false)} className="text-gray-500 hover:bg-gray-100 p-2 rounded-full">
                <X className="w-5 h-5" />
              </button>
            </div>
            
            <div className="px-6 pt-4 pb-2 text-sm text-gray-500">
              Set pricing for: <span className="font-semibold text-gray-900">{pricingDataset.name}</span>
            </div>

            <form onSubmit={handleSavePricing} className="p-6 space-y-4 pt-2">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Base Access Fee ($)</label>
                <input required type="number" step="0.01" min="0" className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-green-500 bg-white" value={pricingForm.perAccessBase} onChange={e => setPricingForm({ ...pricingForm, perAccessBase: Number(e.target.value) })} />
                <p className="text-xs text-gray-500 mt-1">Starting price to request access to this dataset</p>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Fee per Field ($)</label>
                <input required type="number" step="0.01" min="0" className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-green-500 bg-white" value={pricingForm.perField} onChange={e => setPricingForm({ ...pricingForm, perField: Number(e.target.value) })} />
                <p className="text-xs text-gray-500 mt-1">Cost multiplied by the number of fields selected</p>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Sensitive Field Multiplier</label>
                <input required type="number" step="0.1" min="1" className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-green-500 bg-white" value={pricingForm.sensitiveFieldMultiplier} onChange={e => setPricingForm({ ...pricingForm, sensitiveFieldMultiplier: Number(e.target.value) })} />
                <p className="text-xs text-gray-500 mt-1">Multiplier for accessing highly restricted fields (default: 1.0)</p>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Purpose Multiplier (JSON)
                </label>
                <textarea 
                  rows={4}
                  className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-green-500 font-mono text-sm bg-white" 
                  placeholder={"{\n  \"academic\": 0.5,\n  \"commercial\": 2.0\n}"}
                  value={pricingForm.purposeMultiplierJson} 
                  onChange={e => setPricingForm({ ...pricingForm, purposeMultiplierJson: e.target.value })} 
                />
                <p className="text-xs text-gray-500 mt-1">Multiplier based on intent (e.g. {"{\"academic\": 0.5, \"commercial\": 2.0}"})</p>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Bulk Purchase Discount (JSON)
                </label>
                <textarea 
                  rows={4}
                  className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-green-500 font-mono text-sm bg-white" 
                  placeholder={"{\n  \"10\": 0.9,\n  \"50\": 0.8\n}"}
                  value={pricingForm.bulkDiscountJson} 
                  onChange={e => setPricingForm({ ...pricingForm, bulkDiscountJson: e.target.value })} 
                />
                <p className="text-xs text-gray-500 mt-1">Fields volume discounting (e.g. {"{\"10\": 0.9, \"50\": 0.8}"} for 10% off &gt;10 fields)</p>
              </div>
              
              <div className="pt-4 flex justify-end gap-3 border-t">
                <button type="button" onClick={() => setIsPricingModalOpen(false)} className="px-4 py-2 text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-lg font-medium">Cancel</button>
                <button type="submit" className="px-4 py-2 bg-green-600 text-white hover:bg-green-700 rounded-lg font-medium">Save Pricing</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
